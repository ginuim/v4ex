package com.v4ex.android.data

data class UserBrief(val username: String, val avatar: String?)
data class NodeBrief(val name: String, val title: String)

data class Topic(
    val id: Int,
    val title: String,
    val url: String,
    val replies: Int,
    val created: Long,
    val lastTouched: Long,
    val content: String,
    val node: NodeBrief,
    val member: UserBrief,
    val lastReplyBy: String,
)

data class Reply(
    val id: Int,
    val floor: Int,
    val content: String,
    val created: Long,
    val member: UserBrief,
)

data class VNode(
    val name: String,
    val title: String,
    val topics: Int,
    val header: String,
    val avatar: String?,
)

data class Me(
    val id: Int,
    val username: String,
    val avatar: String?,
    val tagline: String,
    val pro: Boolean,
    val created: Long,
    val tokenDays: Double,
    val tokenScope: String,
)

data class Notice(
    val id: Int,
    val text: String,
    val payload: String,
    val created: Long,
    val member: String,
    val avatar: String? = null,
    /** 通知指向的主题；解析不出来就是 null（点消息时降级到浏览器） */
    val topicId: Int? = null,
)

class ApiException(message: String) : Exception(message)

data class MemberProfile(
    val id: Int,
    val username: String,
    val avatar: String?,
    val tagline: String,
    val bio: String,
    val location: String,
    val website: String,
    val github: String,
    val pro: Boolean,
    val created: Long,
)

/**
 * 浏览历史条目。只存展示必需的字段，不依赖网络也能列出历史，
 * 点进去再按 id 重新拉取全文。
 */
data class HistoryEntry(
    val id: Int,
    val title: String,
    val nodeName: String,
    val nodeTitle: String,
    val author: String,
    val replies: Int,
    /** 最后一次浏览时间（毫秒） */
    val viewedAt: Long,
)
