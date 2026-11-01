package com.v4ex.android.data

/**
 * 收藏的帖子。
 *
 * V2EX API 没有收藏端点（网页上的「收藏」是带登录 cookie 的表单请求，不在 API 里），
 * 所以收藏只存在本机。只保存列表展示需要的字段，打开时仍按 id 重新拉全文。
 */
data class Bookmark(
    val id: Int,
    val title: String,
    val nodeName: String,
    val nodeTitle: String,
    val author: String,
    val replies: Int,
    /** 收藏时间（毫秒），最近收藏的排在前面 */
    val savedAt: Long,
)

/**
 * 收藏列表的编解码与增删。纯函数（不碰 Android API），方便单测。
 *
 * 一行一条，字段用 \u001F 分隔：标题里可能有 | 之类的字符，用竖线会解析错位。
 * 字段：id, savedAt, nodeName, nodeTitle, author, replies, title
 */
object Bookmarks {

    private const val SEP = '\u001F'
    private val SEP_STR = SEP.toString()

    fun decode(raw: String): List<Bookmark> = raw
        .split('\n')
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size < 7) return@mapNotNull null
            val id = f[0].toIntOrNull() ?: return@mapNotNull null
            Bookmark(
                id = id,
                savedAt = f[1].toLongOrNull() ?: 0L,
                nodeName = f[2],
                nodeTitle = f[3],
                author = f[4],
                replies = f[5].toIntOrNull() ?: 0,
                // 标题可能含分隔符，剩下的重新拼回去
                title = f.drop(6).joinToString(SEP_STR),
            )
        }

    fun encode(list: List<Bookmark>): String = list.joinToString("\n") { b ->
        listOf(
            b.id.toString(),
            b.savedAt.toString(),
            b.nodeName.clean(), b.nodeTitle.clean(), b.author.clean(), b.replies.toString(), b.title.clean(),
        ).joinToString(SEP_STR)
    }

    /** 已收藏则取消，否则放到最前。 */
    fun toggle(list: List<Bookmark>, bookmark: Bookmark): List<Bookmark> =
        if (list.any { it.id == bookmark.id }) remove(list, bookmark.id)
        else listOf(bookmark) + list

    fun remove(list: List<Bookmark>, id: Int): List<Bookmark> = list.filterNot { it.id == id }

    /** 换行和分隔符会破坏一行一条的格式，统一替换成空格。 */
    private fun String.clean(): String = replace('\n', ' ').replace('\r', ' ').replace(SEP, ' ')
}
