package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePayloadTest {

    // ---- 主题分享 ----------------------------------------------------------

    @Test
    fun topicPayload_usesTopicUrlAndCleanedContent() {
        val p = topicSharePayload(
            topic(
                title = "周末去哪",
                url = "https://www.v2ex.com/t/1",
                content = "正文 **加粗** 部分",
            ),
        )
        assertEquals("周末去哪", p.title)
        assertEquals("https://www.v2ex.com/t/1", p.link)
        assertEquals("正文 加粗 部分", p.excerpt)
        assertEquals(
            "周末去哪\n「正文 加粗 部分」\nhttps://www.v2ex.com/t/1",
            p.text(),
        )
    }

    @Test
    fun topicPayload_blankContentHasNoExcerptLine() {
        val p = topicSharePayload(topic(content = "   \n  "))
        assertNull(p.excerpt)
        assertEquals("周末去哪\nhttps://www.v2ex.com/t/1", p.text())
        assertEquals(2, p.text().lines().size)
    }

    // ---- 回复分享 ----------------------------------------------------------

    @Test
    fun replyPayload_linksToReplyFloor() {
        val p = replySharePayload(
            topic(url = "https://www.v2ex.com/t/9"),
            reply(floor = 2, content = "同意"),
        )
        assertEquals("周末去哪", p.title)
        assertEquals("https://www.v2ex.com/t/9#reply2", p.link)
        assertEquals("同意", p.excerpt)
        assertEquals(
            "周末去哪\n「同意」\nhttps://www.v2ex.com/t/9#reply2",
            p.text(),
        )
    }

    @Test
    fun replyPayload_trimsTrailingHashBeforeAnchor() {
        val p = replySharePayload(
            topic(url = "https://www.v2ex.com/t/9#"),
            reply(floor = 17, content = "好"),
        )
        assertEquals("https://www.v2ex.com/t/9#reply17", p.link)
    }

    // ---- Markdown 清洗 -----------------------------------------------------

    @Test
    fun markdownIsFlattenedToOneLine() {
        val raw = """
            看看这个 [V2EX 链接](https://www.v2ex.com/t/1) ，
            ![截图](https://i.imgur.com/abc.png) **重点** 和 `inline` 代码
            > 引用一句
            https://example.com/pic.JPG
        """.trimIndent()
        val p = topicSharePayload(topic(content = raw))
        assertEquals(
            "看看这个 V2EX 链接 ， 重点 和 inline 代码 引用一句",
            p.excerpt,
        )
        assertFalse(p.excerpt!!.contains('\n'))
        assertFalse(p.excerpt!!.contains("**"))
        assertFalse(p.excerpt!!.contains("http"))
    }

    // ---- 90 字截断 ---------------------------------------------------------

    @Test
    fun excerptIsTruncatedToNinetyChars() {
        val p = topicSharePayload(topic(content = "字".repeat(120)))
        val excerpt = p.excerpt
        assertNotNull(excerpt)
        assertEquals("字".repeat(90) + "…", excerpt)
        assertEquals(91, excerpt!!.length)
    }

    @Test
    fun shortExcerptIsNotTruncated() {
        val p = topicSharePayload(topic(content = "只有一句话"))
        assertEquals("只有一句话", p.excerpt)
        assertTrue(p.text().startsWith("周末去哪\n「只有一句话」\n"))
    }

    // ---- test fixtures -----------------------------------------------------

    private fun topic(
        title: String = "周末去哪",
        url: String = "https://www.v2ex.com/t/1",
        content: String = "",
    ) = Topic(
        id = 1,
        title = title,
        url = url,
        replies = 0,
        created = 0L,
        lastTouched = 0L,
        content = content,
        node = NodeBrief("go", "Go"),
        member = UserBrief("alice", null),
        lastReplyBy = "",
    )

    private fun reply(floor: Int = 1, content: String = "") = Reply(
        id = floor,
        floor = floor,
        content = content,
        created = 0L,
        member = UserBrief("bob", null),
    )
}
