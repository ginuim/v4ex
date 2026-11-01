package com.v4ex.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 通知的本地留存。
 *
 * V2EX 的 `/api/v2/notifications` 只返回未读；标记已读 = 服务端 DELETE，删完就再也取不到。
 * 所以要同时展示已读，只能在本机记住「看过的通知」。
 *
 * 按令牌分账户存（owner = 令牌摘要），换账号不会串历史。纯函数，不碰 Android API，方便单测。
 */
object NoticeHistory {

    /** 每个账户最多留存的条数（未读 + 已读）。 */
    const val MAX = 300

    /** 令牌摘要，用作 DataStore 的分账户 key；不保存令牌明文。 */
    fun ownerOf(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray())
        .take(8)
        .joinToString("") { "%02x".format(it) }

    /**
     * 第一页能判断「未读全集」的时间下界。
     * 不满一页说明服务端已经给全了，下界取最小值（所有本地未读都要对账）；
     * 满一页则只能对账到这一页最早那条之后的通知。
     */
    fun reconcileFloor(fetched: List<Notice>, pageSize: Int): Long? =
        if (fetched.size < pageSize) Long.MIN_VALUE
        else fetched.minOfOrNull { it.created }

    /**
     * 把一次拉取结果并入本地列表。
     *
     * - fetched 一律记为未读（服务端只返回未读），同 id 覆盖，头像取新值、没有则保留旧值；
     * - 本地未读、不在 fetched 里、且 created >= [reconcileFrom] 的，说明已经在网页端读过了，记为已读；
     *   [reconcileFrom] 为 null 表示这次拉取不足以判断（翻页），跳过对账；
     * - 按时间倒序，最多保留 [MAX] 条。
     */
    fun merge(cached: List<Notice>, fetched: List<Notice>, reconcileFrom: Long?): List<Notice> {
        val fetchedIds = fetched.map { it.id }.toSet()
        val fetchedById = fetched.associateBy { it.id }
        val reconciled = cached.map { n ->
            when {
                n.id in fetchedIds -> {
                    val f = fetchedById.getValue(n.id)
                    f.copy(read = false, avatar = f.avatar ?: n.avatar)
                }
                reconcileFrom != null && !n.read && n.created >= reconcileFrom -> n.copy(read = true)
                else -> n
            }
        }
        val known = reconciled.map { it.id }.toSet()
        val added = fetched.filter { it.id !in known }
        return (reconciled + added)
            .sortedByDescending { it.created }
            .take(MAX)
    }

    fun markRead(list: List<Notice>, ids: Set<Int>): List<Notice> =
        list.map { if (it.id in ids) it.copy(read = true) else it }

    fun markUnread(list: List<Notice>, ids: Set<Int>): List<Notice> =
        list.map { if (it.id in ids) it.copy(read = false) else it }

    /** JSON 数组编码。文本可能含换行和分隔符，JSON 天然安全。 */
    fun encode(list: List<Notice>): String {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("text", n.text)
                put("payload", n.payload)
                put("created", n.created)
                put("member", n.member)
                put("avatar", n.avatar ?: JSONObject.NULL)
                put("topicId", n.topicId ?: JSONObject.NULL)
                put("read", n.read)
            })
        }
        return arr.toString()
    }

    /** 解析失败的条目直接丢弃，不让一条坏数据拖垮整个列表。 */
    fun decode(raw: String): List<Notice> {
        if (raw.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!o.has("id")) return@mapNotNull null
            Notice(
                id = o.optInt("id"),
                text = o.optString("text"),
                payload = o.optString("payload"),
                created = o.optLong("created"),
                member = o.optString("member"),
                avatar = o.optNullableString("avatar"),
                topicId = if (o.isNull("topicId")) null else o.optInt("topicId"),
                read = o.optBoolean("read", false),
            )
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
