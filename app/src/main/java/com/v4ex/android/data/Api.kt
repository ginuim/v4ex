package com.v4ex.android.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * V2EX 客户端。分两层：
 *  - v1：`/api/` 下免令牌的 .json 接口，负责「不登录也能看」；v1 的 topics/latest 不支持翻页，
 *    但 v1 的 replies 一次返回全部楼层，没有令牌时只能靠它。
 *  - v2：`/api/v2/` 全部需要 Personal Access Token，提供分页、节点详情、令牌管理，
 *    以及发帖、回帖、置顶 / 放到首页写接口。会员、权限与费用由服务端校验。
 *
 * @param tokenProvider 每次请求现取令牌；用户在「我的」里改完立刻生效
 * @param onRateLimit 响应头带 `X-Rate-Limit-*` 时回调，给「我的」页展示本小时配额
 */
class Api(
    private val tokenProvider: () -> String,
    private val onRateLimit: (RateLimit) -> Unit = {},
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // 写操作不自动重试或跟随重定向，避免重复发布或重复扣费。
    private val writes = http.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()

    private val base = "https://edge.v2ex.com"

    /** 最近一次响应里的限流信息；一次请求都没发过时是 null */
    @Volatile
    var rateLimit: RateLimit? = null
        private set

    // ---------------------------------------------------------------- http
    /**
     * 唯一的出口。GET 用 [path] 现拼 query，POST/DELETE 走这里的方法参数；
     * 非 2xx 一律翻译成 [ApiException]（message 是给用户看的中文）。
     */
    private fun exec(
        path: String,
        auth: Boolean = false,
        method: String = "GET",
        json: JSONObject? = null,
    ): String {
        val token = tokenProvider().trim()
        if (auth && token.isEmpty()) throw ApiException("需要先在「我的」里填写个人访问令牌")

        val builder = Request.Builder()
            .url(base + path)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
        if (auth) builder.header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> Unit
            "DELETE" -> builder.delete()
            "POST" -> builder.post((json?.toString() ?: "{}").toRequestBody(JSON_MEDIA))
            else -> builder.method(method, null)
        }

        (if (method == "GET") http else writes).newCall(builder.build()).execute().use { resp ->
            readRateLimit(resp)
            val body = resp.body?.string().orEmpty()
            if (resp.isSuccessful) return body
            throw ApiException(errorMessage(resp.code, body), resp.code)
        }
    }

    private fun readRateLimit(resp: okhttp3.Response) {
        val limit = resp.header("X-Rate-Limit-Limit")?.toIntOrNull() ?: return
        val info = RateLimit(
            limit = limit,
            remaining = resp.header("X-Rate-Limit-Remaining")?.toIntOrNull() ?: 0,
            // 响应头是 Unix 秒
            resetAt = (resp.header("X-Rate-Limit-Reset")?.toLongOrNull() ?: 0L) * 1000,
        )
        rateLimit = info
        onRateLimit(info)
    }

    /**
     * HTTP 状态 + 服务端 message → 用户能看懂的一句话。
     * V2EX 的 message 有时是英文（"Token not found"）、有时直接是中文说明，
     * 中文说明优先原样展示，英文才换成我们自己的话。
     */
    private fun errorMessage(code: Int, body: String): String {
        val msg = runCatching {
            val o = JSONObject(body)
            o.optString("message").ifBlank { o.optString("error") }
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

        return when {
            code == 401 -> "令牌无效或已过期，请到「我的」重新填写"
            code == 403 -> msg ?: "没有权限执行这个操作"
            code == 404 -> msg ?: "这个对象不存在"
            code == 405 -> msg ?: "服务端不接受这个方法"
            code == 429 -> "请求太频繁，已触发限流（每 IP 每小时 600 次），过一会儿再试"
            code in 500..599 -> "V2EX 服务器出错了（$code），稍后再试"
            msg != null -> msg
            else -> "网络错误 $code"
        }
    }

    // ---------------------------------------------------------------- v1
    fun hot(): List<Topic> = parseTopics(exec("/api/topics/hot.json"))

    fun latest(): List<Topic> = parseTopics(exec("/api/topics/latest.json"))

    fun topic(id: Int): Topic {
        val arr = JSONArray(exec("/api/topics/show.json?id=$id"))
        if (arr.length() == 0) throw ApiException("这个主题不存在")
        return parseTopic(arr.getJSONObject(0))
    }

    /** v1 回复：一次返回全部楼层，没有 parent 字段（对话视图靠正文推断）。 */
    fun replies(topicId: Int): List<Reply> {
        val arr = JSONArray(exec("/api/replies/show.json?topic_id=$topicId"))
        return (0 until arr.length()).map { i -> parseReply(arr.getJSONObject(i), i + 1) }
    }

    fun nodes(): List<VNode> {
        val arr = JSONArray(exec("/api/nodes/all.json"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VNode(
                name = o.optString("name"),
                title = o.optString("title"),
                topics = o.optInt("topics"),
                parentNodeName = o.text("parent_node_name").takeIf { it.isNotBlank() },
                root = o.optBoolean("root"),
                header = o.text("header"),
                avatar = o.text("avatar_normal").ifBlank { null },
            )
        }.sortedByDescending { it.topics }
    }

    fun nodeTopics(node: String): List<Topic> =
        parseTopics(exec("/api/topics/show.json?node_name=$node"))

    /** v1 的别人的资料页（免令牌）。v1 的 topics?username= 已经返回空列表，所以只拿资料。 */
    fun member(username: String): MemberProfile {
        val o = JSONObject(exec("/api/members/show.json?username=$username"))
        return MemberProfile(
            id = o.optInt("id"),
            username = o.text("username", username),
            avatar = o.text("avatar_large").ifBlank { o.text("avatar_normal") }.ifBlank { null },
            tagline = o.text("tagline"),
            bio = o.text("bio"),
            location = o.text("location"),
            website = o.text("website"),
            github = o.text("github"),
            pro = o.optBoolean("pro", false) || o.optLong("pro", 0L) > 0L,
            url = o.text("url"),
            twitter = o.text("twitter"),
            psn = o.text("psn"),
            btc = o.text("btc"),
            lastModified = o.optLong("last_modified") * 1000,
            created = o.optLong("created") * 1000,
        )
    }

    // ------------------------------------------------------------ v2 读取
    /** 最新主题第 [page] 页（20 条/页）。v1 没有翻页参数，翻页只能走 v2。 */
    fun latestPage(page: Int): Pair<List<Topic>, PageInfo> = pagedTopics(
        "/api/v2/topics/latest?p=$page", page,
    )

    fun nodeTopicsPage(node: String, page: Int): Pair<List<Topic>, PageInfo> = pagedTopics(
        "/api/v2/nodes/$node/topics?p=$page", page,
    )

    private fun pagedTopics(path: String, page: Int): Pair<List<Topic>, PageInfo> {
        val env = v2(path)
        val arr = env.optJSONArray("result") ?: JSONArray()
        return parseTopicsArray(arr) to pageInfo(env, arr.length(), page)
    }

    /** 主题详情（v2）。字段和 v1 基本一致，多了 stars / thanks / supplements。 */
    fun topicV2(id: Int): Topic = parseTopic(v2Result(v2("/api/v2/topics/$id")))

    /**
     * 主题回复（v2，20 条/页）。注意 v2 回复**没有楼层号**，
     * 楼层只能按「第几页 × 每页条数 + 页内序号」推算。
     */
    fun repliesPage(topicId: Int, page: Int): Pair<List<Reply>, PageInfo> {
        val env = v2("/api/v2/topics/$topicId/replies?p=$page")
        val arr = env.optJSONArray("result") ?: JSONArray()
        val info = pageInfo(env, arr.length(), page)
        val offset = (page - 1) * info.perPage
        val list = (0 until arr.length()).map { i ->
            parseReply(arr.getJSONObject(i), offset + i + 1)
        }
        return list to info
    }

    /** 节点详情（v2）：比 v1 多了 footer / stars / title_alternative / 创建时间。 */
    fun node(name: String): NodeDetail {
        val r = v2Result(v2("/api/v2/nodes/$name"))
        val avatar = r.text("avatar_normal").ifBlank { r.text("avatar_large") }.ifBlank { null }
        return NodeDetail(
            id = r.optInt("id"),
            name = r.text("name", name),
            title = r.text("title"),
            titleAlternative = r.text("title_alternative"),
            header = r.text("header"),
            footer = r.text("footer"),
            topics = r.optInt("topics"),
            stars = r.optInt("stars"),
            created = r.optLong("created") * 1000,
            avatar = avatar,
        )
    }

    // ------------------------------------------------------------ v2 账号
    fun me(): Me {
        val r = v2Result(v2("/api/v2/member"))
        // token 拿不到不算致命：资料照常显示，只是少了令牌信息
        val t = runCatching { token() }.getOrNull()
        return Me(
            id = r.optInt("id"),
            username = r.text("username"),
            avatar = r.text("avatar_normal").ifBlank { r.text("avatar_large") }.ifBlank { null },
            tagline = r.text("tagline"),
            pro = r.optBoolean("pro", false) || r.optLong("pro", 0L) > 0L,
            created = r.optLong("created") * 1000,
            tokenDays = t?.goodForDays ?: 0.0,
            tokenScope = t?.scope?.ifBlank { "-" } ?: "-",
            tokenTotalUsed = t?.totalUsed ?: 0,
            tokenExpiration = t?.expiration ?: 0,
        )
    }

    /** 当前令牌的信息。 */
    fun token(): TokenInfo {
        val r = v2Result(v2("/api/v2/token"))
        return TokenInfo(
            token = r.text("token"),
            scope = r.text("scope"),
            expiration = r.optLong("expiration") * 1000,
            goodForDays = r.optDouble("good_for_days", 0.0),
            totalUsed = r.optInt("total_used"),
        )
    }

    /**
     * 创建新令牌。文档写的是 `POST /tokens`，但真正常用的路径是 `POST /token`，
     * 所以先打 /token，只有 404/405 才退回 /tokens。新令牌的明文只在这次响应里出现。
     *
     * @param scope everything（可再创建令牌）或 regular（只能读写）
     * @param expirationSeconds 2592000 / 5184000 / 7776000 / 15552000 = 30 / 60 / 90 / 180 天
     */
    fun createToken(scope: String, expirationSeconds: Int): TokenInfo {
        val payload = JSONObject().put("scope", scope).put("expiration", expirationSeconds)
        val body = try {
            exec("/api/v2/token", auth = true, method = "POST", json = payload)
        } catch (e: ApiException) {
            if (e.status == 404 || e.status == 405) {
                exec("/api/v2/tokens", auth = true, method = "POST", json = payload)
            } else {
                throw e
            }
        }
        val env = JSONObject(body)
        if (!env.optBoolean("success", true)) {
            throw ApiException(env.optString("message").ifBlank { "创建令牌失败" })
        }
        val raw = env.opt("result")
        val r = raw as? JSONObject ?: JSONObject()
        // 有些版本 result 直接就是令牌字符串
        val plain = r.text("token").ifBlank { raw as? String ?: "" }
        return TokenInfo(
            token = plain,
            scope = r.text("scope", scope),
            expiration = r.optLong("expiration") * 1000,
            goodForDays = r.optDouble("good_for_days", 0.0),
            totalUsed = r.optInt("total_used"),
        )
    }

    // ------------------------------------------------------------ v2 提醒
    /**
     * 最新提醒（v2，20 条/页）。返回 (这一页, 分页信息)。
     * 提醒接口不一定带 pagination，满一页就当「可能还有」，下一页空了自然停。
     */
    fun notices(page: Int = 1): Pair<List<Notice>, PageInfo> {
        val env = v2("/api/v2/notifications?p=$page")
        val arr = env.optJSONArray("result") ?: return emptyList<Notice>() to PageInfo(pages = page)
        val rows = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val rawText = o.optString("text")
            Notice(
                id = o.optInt("id"),
                text = Html.strip(rawText),
                payload = Html.strip(o.optString("payload")),
                created = o.optLong("created") * 1000,
                member = o.optJSONObject("member")?.optString("username").orEmpty(),
                topicId = topicIdOf(rawText),
            )
        }
        // 头像要额外问 v1，一次性并发取掉；取不到就退回首字母，不影响列表出现。
        prefetchAvatars(rows.map { it.member }.filter { it.isNotBlank() }.distinct())
        return rows.map { it.copy(avatar = avatarCache[it.member]) } to pageInfo(env, arr.length(), page)
    }

    /** 通知正文里形如 <a href="/t/1182736#reply1">标题</a>，从中抠出主题 id。 */
    private fun topicIdOf(html: String): Int? =
        TOPIC_LINK_RE.find(html)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 把一条通知标记为已读（= 服务端删除）。
     * 重复删会返回 404 "Notification not found"，这种情况按成功处理，保证可重入。
     */
    fun clearNotice(id: Int): Boolean {
        try {
            exec("/api/v2/notifications/$id", auth = true, method = "DELETE")
            return true
        } catch (e: ApiException) {
            if (e.status == 404) return true // 已经没了，视为已读
            throw e
        }
    }

    // ------------------------------------------------------------ v2 写操作
    /** PRO 发帖；服务端仍校验节点权限、频率及铜币。 */
    fun createTopic(nodeName: String, title: String, content: String, syntax: String = "markdown"): Topic {
        val node = nodeName.trim()
        val heading = title.trim()
        require(node.matches(Regex("[A-Za-z0-9_-]+"))) { "请填写有效的节点名称" }
        require(heading.isNotBlank() && heading.codePointCount(0, heading.length) <= 120) { "标题需为 1–120 个字符" }
        require(content.codePointCount(0, content.length) <= 20000) { "正文最多 20000 个字符" }
        require(syntax in setOf("default", "markdown")) { "不支持的正文格式" }
        val env = v2("/api/v2/topics", method = "POST", json = JSONObject()
            .put("node_name", node).put("title", heading).put("content", content).put("syntax", syntax))
        val result = env.optJSONObject("result")
        if (result == null || result.optInt("id") <= 0) {
            throw ApiException("发布请求已被接受，但未取得主题编号。请先到 V2EX 确认，勿重复发布。")
        }
        return parseTopic(result)
    }

    /** PRO 回帖；只确认服务端成功，楼层等信息由后续刷新获取。 */
    fun createReply(topicId: Int, content: String) {
        require(topicId > 0) { "无效的主题编号" }
        require(content.isNotBlank() && content.codePointCount(0, content.length) <= 10000) { "回复需为 1–10000 个字符" }
        v2("/api/v2/topics/$topicId/replies", method = "POST", json = JSONObject().put("content", content))
    }

    /**
     * 置顶自己的主题。**需要 PRO**（服务端校验），没开会员会拿到 403。
     * @param duration 15min / 1hr / 8hr，默认 15min
     */
    fun setSticky(topicId: Int, duration: String = "15min"): String {
        val env = v2("/api/v2/topics/$topicId/set-sticky?duration=$duration", method = "POST")
        return env.optString("message").ifBlank { "已置顶，$duration" }
    }

    /**
     * 把自己的主题放到首页。需要 PRO / 5 万 token / 1 万铜币三者之一，每次花 100 铜币，
     * 发布年份越早费用越高。费用和门槛都由服务端校验，这里只负责把结果/错误带回去。
     */
    fun boost(topicId: Int): String {
        val env = v2("/api/v2/topics/$topicId/boost", method = "POST")
        return env.optString("message").ifBlank { "已放到首页" }
    }

    // ---------------------------------------------------------------- 解析
    /** v2 的统一信封：`{success, message, result, pagination}`。 */
    private fun v2(path: String, method: String = "GET", json: JSONObject? = null): JSONObject {
        val env = JSONObject(exec(path, auth = true, method = method, json = json))
        if (!env.optBoolean("success", true)) {
            throw ApiException(env.optString("message").ifBlank { "请求失败" })
        }
        return env
    }

    private fun v2Result(env: JSONObject): JSONObject = env.optJSONObject("result") ?: JSONObject()

    /**
     * 分页信息。分页接口在响应顶层给 `{per_page,total,pages}`，没有 current；
     * 像 notifications 这种不带 pagination 的，满一页才算「可能还有」。
     */
    private fun pageInfo(env: JSONObject, size: Int, page: Int): PageInfo {
        val p = env.optJSONObject("pagination")
        if (p != null) {
            return PageInfo(
                total = p.optInt("total", size),
                pages = p.optInt("pages", page),
                perPage = p.optInt("per_page", PAGE_SIZE),
            )
        }
        return PageInfo(
            total = size * page,
            pages = if (size >= PAGE_SIZE) page + 1 else page,
            perPage = PAGE_SIZE,
        )
    }

    /**
     * optString 遇到 JSON 的 null 会返回字符串 "null"（V2EX 的空字段就是真 null），
     * 所以统一走这里，把 null / JSONObject.NULL 都当成空串。
     */
    private fun JSONObject.text(key: String, fallback: String = ""): String {
        val v = opt(key) ?: return fallback
        if (v == JSONObject.NULL) return fallback
        return v.toString().ifBlank { fallback }
    }

    private fun parseTopics(body: String): List<Topic> = parseTopicsArray(JSONArray(body))

    private fun parseTopicsArray(arr: JSONArray): List<Topic> =
        (0 until arr.length()).map { parseTopic(arr.getJSONObject(it)) }

    private fun parseTopic(o: JSONObject): Topic {
        val node = o.optJSONObject("node")
        val mem = o.optJSONObject("member")
        val created = o.optLong("created")
        return Topic(
            id = o.optInt("id"),
            title = o.optString("title"),
            url = o.optString("url").ifBlank { "https://www.v2ex.com/t/${o.optInt("id")}" },
            replies = o.optInt("replies"),
            created = created * 1000,
            // v2 有时不给 last_touched，退到 last_modified / created
            lastTouched = (o.optLong("last_touched").takeIf { it > 0 }
                ?: o.optLong("last_modified").takeIf { it > 0 }
                ?: created) * 1000,
            content = o.optString("content"),
            node = NodeBrief(node?.optString("name").orEmpty(), node?.optString("title").orEmpty()),
            member = UserBrief(
                username = mem?.optString("username").orEmpty(),
                avatar = mem?.optString("avatar_normal").orEmpty()
                    .ifBlank { mem?.optString("avatar").orEmpty() }
                    .ifBlank { mem?.optString("avatar_large").orEmpty() }
                    .ifBlank { null },
            ),
            lastReplyBy = o.optString("last_reply_by"),
            stars = if (o.has("stars") && !o.isNull("stars")) o.optInt("stars") else null,
            thanks = if (o.has("thanks") && !o.isNull("thanks")) o.optInt("thanks") else null,
        )
    }

    private fun parseReply(o: JSONObject, floor: Int): Reply {
        val m = o.optJSONObject("member")
        return Reply(
            id = o.optInt("id"),
            floor = floor,
            content = o.optString("content"),
            created = o.optLong("created") * 1000,
            member = UserBrief(
                username = m?.optString("username").orEmpty(),
                avatar = m?.optString("avatar_normal").orEmpty()
                    .ifBlank { m?.optString("avatar").orEmpty() }
                    .ifBlank { m?.optString("avatar_large").orEmpty() }
                    .ifBlank { null },
            ),
        )
    }

    /**
     * v2 通知只给 username，不给头像。头像路径带账号哈希，推不出来，只能问一次 v1。
     * 并发 + 结果缓存：同一个人多条提醒只请求一次；超过上限就不再补头像，避免拖慢列表。
     */
    private fun prefetchAvatars(usernames: List<String>) {
        val todo = usernames.filter { !avatarCache.containsKey(it) }.take(AVATAR_FETCH_LIMIT)
        if (todo.isEmpty()) return
        val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(4, todo.size))
        try {
            val futures = todo.map { name ->
                pool.submit {
                    runCatching {
                        val o = JSONObject(exec("/api/members/show.json?username=$name"))
                        o.optString("avatar_large").ifBlank { o.optString("avatar_normal") }
                    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { avatarCache[name] = it }
                }
            }
            futures.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
    }

    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private companion object {
        const val UA = "v4ex-android/0.1"
        const val PAGE_SIZE = 20
        const val AVATAR_FETCH_LIMIT = 12
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val TOPIC_LINK_RE = Regex("""href=["']/t/(\d+)""")
    }
}

/** V2EX 返回的提醒 text 里带 <a> 标签，去掉标签、保留文字。 */
object Html {
    private val tag = Regex("<[^>]+>")
    private val spaces = Regex("[ \\t]*\\n[ \\t]*")

    fun strip(html: String): String = html
        .replace("<br />", "\n")
        .replace("<br/>", "\n")
        .replace(tag, "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(spaces, "\n")
        .trim()
}

object Time {
    fun ago(ms: Long): String {
        if (ms <= 0) return ""
        val diff = System.currentTimeMillis() - ms
        val min = diff / 60_000
        return when {
            min < 1 -> "刚刚"
            min < 60 -> "${min} 分钟前"
            min < 60 * 24 -> "${min / 60} 小时前"
            min < 60 * 24 * 7 -> "${min / (60 * 24)} 天前"
            else -> java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).format(java.util.Date(ms))
        }
    }

    fun date(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(ms))

    fun greeting(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            h < 6 -> "夜深了，"
            h < 11 -> "早上好，"
            h < 13 -> "中午好，"
            h < 18 -> "下午好，"
            else -> "晚上好，"
        }
    }
}
