package com.v4ex.android.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 只用 v1（不需要 token）+ 两个 v2 接口（member / notifications）。
 * 读：v1 一次能拿回全部楼层；v1 的 topics/latest 不支持翻页，所以首页分「热门 / 最新」两段。
 */
class Api(private val tokenProvider: () -> String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val base = "https://edge.v2ex.com"

    private fun get(path: String, auth: Boolean = false): String {
        val builder = Request.Builder()
            .url(base + path)
            .header("User-Agent", "v4ex-android/0.1")
            .header("Accept", "application/json")
        if (auth) {
            val token = tokenProvider().trim()
            if (token.isEmpty()) throw ApiException("需要先在「我的」里填写个人访问令牌")
            builder.header("Authorization", "Bearer $token")
        }
        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(
                    when {
                        resp.code == 401 -> "令牌无效或已过期"
                        !msg.isNullOrBlank() -> msg
                        else -> "网络错误 ${resp.code}"
                    }
                )
            }
            return body
        }
    }

    // ---------------------------------------------------------------- v1
    fun hot(): List<Topic> = parseTopics(get("/api/topics/hot.json"))

    fun latest(): List<Topic> = parseTopics(get("/api/topics/latest.json"))

    fun topic(id: Int): Topic {
        val arr = JSONArray(get("/api/topics/show.json?id=$id"))
        if (arr.length() == 0) throw ApiException("这个主题不存在")
        return parseTopic(arr.getJSONObject(0))
    }

    fun replies(topicId: Int): List<Reply> {
        val arr = JSONArray(get("/api/replies/show.json?topic_id=$topicId"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val m = o.optJSONObject("member")
            Reply(
                id = o.optInt("id"),
                floor = i + 1,
                content = o.optString("content"),
                created = o.optLong("created") * 1000,
                member = UserBrief(
                    username = m?.optString("username").orEmpty(),
                    avatar = m?.optString("avatar_normal"),
                ),
            )
        }
    }

    fun nodes(): List<VNode> {
        val arr = JSONArray(get("/api/nodes/all.json"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VNode(
                name = o.optString("name"),
                title = o.optString("title"),
                topics = o.optInt("topics"),
                header = o.optString("header"),
                avatar = o.optString("avatar_normal").ifBlank { null },
            )
        }.sortedByDescending { it.topics }
    }

    fun nodeTopics(node: String): List<Topic> =
        parseTopics(get("/api/topics/show.json?node_name=$node"))

    /** v2 才有翻页（20 条/页）；返回 (这一页, 总页数)。没有令牌时会抛错。 */
    fun latestPage(page: Int): Pair<List<Topic>, Int> =
        paged(get("/api/v2/topics/latest?p=$page", auth = true), page)

    fun nodeTopicsPage(node: String, page: Int): Pair<List<Topic>, Int> =
        paged(get("/api/v2/nodes/$node/topics?p=$page", auth = true), page)

    private fun paged(body: String, fallbackPage: Int): Pair<List<Topic>, Int> {
        val obj = JSONObject(body)
        val arr = obj.optJSONArray("result") ?: JSONArray()
        val pages = obj.optJSONObject("pagination")?.optInt("pages") ?: fallbackPage
        return parseTopicsArray(arr) to pages
    }

    /** 别人的资料页（v1，免令牌）。v1 的 topics?username= 已经返回空列表，
     *  所以这里只拿资料，主题列表让用户跳浏览器看。 */
    fun member(username: String): MemberProfile {
        val o = JSONObject(get("/api/members/show.json?username=$username"))
        return MemberProfile(
            id = o.optInt("id"),
            username = o.text("username", username),
            avatar = o.text("avatar_large").ifBlank { o.text("avatar_normal") }.ifBlank { null },
            tagline = o.text("tagline"),
            bio = o.text("bio"),
            location = o.text("location"),
            website = o.text("website"),
            github = o.text("github"),
            pro = o.optInt("pro") == 1,
            created = o.optLong("created") * 1000,
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

    // ---------------------------------------------------------------- v2
    fun me(): Me {
        val r = result(get("/api/v2/member", auth = true))
        val t = runCatching { result(get("/api/v2/token", auth = true)) }.getOrNull()
        return Me(
            id = r.optInt("id"),
            username = r.text("username"),
            avatar = r.text("avatar_normal").ifBlank { null },
            tagline = r.text("tagline"),
            pro = r.optInt("pro") == 1,
            created = r.optLong("created") * 1000,
            tokenDays = t?.optDouble("good_for_days") ?: 0.0,
            tokenScope = t?.text("scope", "-") ?: "-",
        )
    }

    fun notices(): List<Notice> {
        val r = get("/api/v2/notifications", auth = true)
        val obj = JSONObject(r)
        val arr = obj.optJSONArray("result") ?: return emptyList()
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
        return rows.map { it.copy(avatar = avatarCache[it.member]) }
    }

    /** 通知正文里形如 <a href="/t/1182736#reply1">标题</a>，从中抠出主题 id。 */
    private fun topicIdOf(html: String): Int? =
        TOPIC_LINK_RE.find(html)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 把一条通知标记为已读（= 服务端删除）。
     * 重复删会返回 404 "Notification not found"，这种情况按成功处理，保证可重入。
     */
    fun clearNotice(id: Int): Boolean {
        val token = tokenProvider().trim()
        if (token.isEmpty()) throw ApiException("需要先在「我的」里填写个人访问令牌")
        val req = Request.Builder()
            .url("$base/api/v2/notifications/$id")
            .header("User-Agent", "v4ex-android/0.1")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) return true
            if (resp.code == 404) return true // 已经没了，视为已读
            val body = resp.body?.string().orEmpty()
            val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
            throw ApiException(
                when {
                    resp.code == 401 -> "令牌无效或已过期"
                    !msg.isNullOrBlank() -> msg
                    else -> "网络错误 ${resp.code}"
                }
            )
        }
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
                        val o = JSONObject(get("/api/members/show.json?username=$name"))
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
        val TOPIC_LINK_RE = Regex("""href=["']/t/(\d+)""")
        const val AVATAR_FETCH_LIMIT = 12
    }

    // ---------------------------------------------------------------- parse
    private fun result(body: String): JSONObject {
        val obj = JSONObject(body)
        if (!obj.optBoolean("success", true)) throw ApiException(obj.optString("message", "请求失败"))
        return obj.optJSONObject("result") ?: JSONObject()
    }

    private fun parseTopics(body: String): List<Topic> = parseTopicsArray(JSONArray(body))

    private fun parseTopicsArray(arr: JSONArray): List<Topic> =
        (0 until arr.length()).map { parseTopic(arr.getJSONObject(it)) }

    private fun parseTopic(o: JSONObject): Topic {
        val node = o.optJSONObject("node")
        val mem = o.optJSONObject("member")
        return Topic(
            id = o.optInt("id"),
            title = o.optString("title"),
            url = o.optString("url").ifBlank { "https://www.v2ex.com/t/${o.optInt("id")}" },
            replies = o.optInt("replies"),
            created = o.optLong("created") * 1000,
            lastTouched = o.optLong("last_touched") * 1000,
            content = o.optString("content"),
            node = NodeBrief(node?.optString("name").orEmpty(), node?.optString("title").orEmpty()),
            member = UserBrief(
                username = mem?.optString("username").orEmpty(),
                avatar = mem?.optString("avatar_normal").orEmpty()
                    .ifBlank { mem?.optString("avatar").orEmpty() }
                    .ifBlank { null },
            ),
            lastReplyBy = o.optString("last_reply_by"),
        )
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
