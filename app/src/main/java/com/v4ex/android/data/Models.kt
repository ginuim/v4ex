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
    val parentNodeName: String? = null,
    val root: Boolean = false,
)

/**
 * `GET /nodes/:name`（V2）。比 V1 的 `nodes/all.json` 多了 footer / stars /
 * title_alternative / 创建时间，需要令牌才能取。
 */
data class NodeDetail(
    val id: Int,
    val name: String,
    val title: String,
    val titleAlternative: String,
    val header: String,
    val footer: String,
    val topics: Int,
    val stars: Int,
    val created: Long,
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
    /** GET /token 里的 total_used，令牌累计用过多少次 */
    val tokenTotalUsed: Int = 0,
    /** 令牌过期时间（毫秒），0 = 未知 */
    val tokenExpiration: Long = 0,
)

/**
 * V2 分页信息。分页接口在响应**顶层**返回 `{ "per_page": 20, "total": 372, "pages": 19 }`，
 * 没有「当前页」字段，当前页就是请求时传的 p。
 */
data class PageInfo(
    val total: Int = 0,
    val pages: Int = 1,
    val perPage: Int = 20,
) {
    fun hasMore(page: Int): Boolean = page in 1 until pages
}

/**
 * API 限流信息，来自响应头：
 * `X-Rate-Limit-Limit` / `X-Rate-Limit-Remaining` / `X-Rate-Limit-Reset`。
 * 默认每个 IP 每小时 600 次；CDN 命中的请求不消耗配额。
 */
data class RateLimit(
    val limit: Int = 0,
    val remaining: Int = 0,
    /** 配额重置时间（毫秒），0 = 未知 */
    val resetAt: Long = 0,
)

/**
 * Personal Access Token。`GET /token` 返回当前令牌信息；
 * `POST /token`（文档也写作 `POST /tokens`）创建新令牌时，`token` 字段就是新令牌本身——
 * 只在创建那一次返回，之后拿不到，必须当场展示给用户复制。
 */
data class TokenInfo(
    val token: String = "",
    val scope: String = "",
    /** 过期时间（毫秒），0 = 未知 */
    val expiration: Long = 0,
    /** 距离过期还有多少天 */
    val goodForDays: Double = 0.0,
    /** 累计使用次数 */
    val totalUsed: Int = 0,
)

/**
 * 帖子页上的写操作（置顶 / 放到首页）的瞬时状态。
 * `loading` 时按钮转圈；`message` 非空时显示一条结果提示（成功或失败都走这里，
 * PRO 权限不足会原样显示服务端说明）。
 */
data class TopicAction(
    val loading: Boolean = false,
    val message: String? = null,
    val ok: Boolean = false,
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

/**
 * 接口错误。message 直接面向用户（已经翻译成人话），`status` 是 HTTP 状态码，
 * 0 表示还没拿到响应（比如没有令牌、网络失败）。
 */
class ApiException(message: String, val status: Int = 0) : Exception(message)

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
    val url: String = "",
    val twitter: String = "",
    val psn: String = "",
    val btc: String = "",
    val lastModified: Long = 0,
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
