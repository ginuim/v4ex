package com.v4ex.android.data

/**
 * 分享出去的内容。纯 Kotlin，不碰 Android，方便单测。
 * text() 固定三行：标题 / 「摘要」 / 链接，摘要为空时只有两行。
 */
data class SharePayload(
    val title: String,
    val link: String,
    val excerpt: String? = null,
    val markdown: String? = null,
) {
    fun text(): String {
        val ex = excerpt?.trim().orEmpty()
        return buildString {
            append(title)
            if (ex.isNotEmpty()) {
                append('\n')
                append('「').append(ex).append('」')
            }
            append('\n')
            append(link)
        }
    }
}

/** 主题分享：链接用主题原始地址，摘要取正文。 */
fun topicSharePayload(topic: Topic): SharePayload = SharePayload(
    title = topic.title,
    link = topic.url,
    excerpt = cleanShareExcerpt(topic.content),
    markdown = topic.content,
)

/** 回复分享：链接锚到楼层 #replyN，摘要取该楼内容。 */
fun replySharePayload(topic: Topic, reply: Reply): SharePayload = SharePayload(
    title = topic.title,
    link = topic.url.trimEnd('#') + "#reply${reply.floor}",
    excerpt = cleanShareExcerpt(reply.content),
    markdown = reply.content,
)

// ---- Markdown → 一行纯文本 -------------------------------------------------

private val MD_IMAGE = Regex("""!\[[^\]]*]\(\s*[^)]*\)""")
private val MD_LINK = Regex("""\[([^\]\n]*)]\(\s*[^)]*\)""")
private val BARE_IMAGE_URL = Regex(
    """https?://[^\s)]+?\.(?:png|jpe?g|gif|webp|bmp|svg)\b""",
    RegexOption.IGNORE_CASE,
)
private val QUOTE_PREFIX = Regex("""(?m)^[ \t]*>+[ \t]?""")
private val WHITESPACE = Regex("""\s+""")

private const val EXCERPT_MAX = 90

/**
 * 分享摘要清洗：图片整段丢掉、Markdown 链接只留文字、去强调/代码/引用标记，
 * 换行和连续空白压成一个空格，超过 90 字截断。清洗后为空则返回 null。
 */
private fun cleanShareExcerpt(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var s = raw
    s = MD_IMAGE.replace(s, " ")           // 图片先删，否则会被链接规则拆成 "!alt"
    s = MD_LINK.replace(s) { it.groupValues[1] }
    s = BARE_IMAGE_URL.replace(s, " ")     // 裸图片地址也删
    s = s.replace("**", "")
    s = s.replace("`", "")
    s = QUOTE_PREFIX.replace(s, "")
    s = WHITESPACE.replace(s, " ").trim()
    if (s.isEmpty()) return null
    if (s.length > EXCERPT_MAX) {
        s = s.take(EXCERPT_MAX).trimEnd() + "…"
    }
    return s
}
