package com.v4ex.android.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 首页列表的本地缓存。
 *
 * 只存列表要显示的字段，`content` 一律不存——正文只在打开帖子时才需要，
 * 存进来会让缓存从几十 KB 涨到几百 KB，而收益是零。
 *
 * 用 org.json（项目里 Api.kt 已经在用），不引 kotlinx.serialization。
 */
object HomeCacheCodec {

    fun encode(hot: List<Topic>, latest: List<Topic>): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("hot", encodeTopics(hot))
        root.put("latest", encodeTopics(latest))
        return root.toString()
    }

    /** 解析失败一律当作没有缓存，不要因为缓存坏了让首页起不来。 */
    fun decode(raw: String): Pair<List<Topic>, List<Topic>>? = runCatching {
        if (raw.isBlank()) return null
        val root = JSONObject(raw)
        root.optJSONArray("hot").toTopics() to root.optJSONArray("latest").toTopics()
    }.getOrNull()

    private fun encodeTopics(list: List<Topic>): JSONArray {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("url", t.url)
                    put("replies", t.replies)
                    put("created", t.created)
                    put("lastTouched", t.lastTouched)
                    put("nodeName", t.node.name)
                    put("nodeTitle", t.node.title)
                    put("author", t.member.username)
                    put("avatar", t.member.avatar ?: "")
                    put("lastReplyBy", t.lastReplyBy)
                }
            )
        }
        return arr
    }

    private fun JSONArray?.toTopics(): List<Topic> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val o = optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("id", 0)
            if (id == 0) return@mapNotNull null
            Topic(
                id = id,
                title = o.optString("title"),
                url = o.optString("url"),
                replies = o.optInt("replies"),
                created = o.optLong("created"),
                lastTouched = o.optLong("lastTouched"),
                // 缓存里没有正文，打开帖子会重新拉
                content = "",
                node = NodeBrief(o.optString("nodeName"), o.optString("nodeTitle")),
                member = UserBrief(
                    username = o.optString("author"),
                    avatar = o.optString("avatar").ifBlank { null },
                ),
                lastReplyBy = o.optString("lastReplyBy"),
            )
        }
    }
}
