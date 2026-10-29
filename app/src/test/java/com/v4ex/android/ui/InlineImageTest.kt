package com.v4ex.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯函数测试：段落里的图片占位、行内表情判定、图片尺寸。 */
class InlineImageTest {

    @Test
    fun placeholderRangesFindsEverySlotInOrder() {
        val text = "a${EMOJI_PLACEHOLDER}b${EMOJI_PLACEHOLDER}"
        assertEquals(listOf(1..1, 3..3), placeholderRanges(text))
    }

    @Test
    fun noPlaceholderMeansNoRanges() {
        assertTrue(placeholderRanges("纯文字，没有图").isEmpty())
    }

    @Test
    fun foldBindsNthImageToNthSlot() {
        val slots = placeholderRanges("a${EMOJI_PLACEHOLDER}b${EMOJI_PLACEHOLDER}c")
        val marks = foldInlineImages(slots, listOf("https://x/1.png", "https://x/2.png"))

        assertEquals(2, marks.size)
        assertEquals("https://x/1.png", marks[0].url)
        assertEquals(1, marks[0].placeholderStart)
        assertEquals("https://x/2.png", marks[1].url)
        assertEquals(3, marks[1].placeholderStart)
    }

    @Test
    fun extraImagesAreDroppedInsteadOfMisplaced() {
        val slots = placeholderRanges("只有${EMOJI_PLACEHOLDER}一个占位")
        val marks = foldInlineImages(slots, listOf("a.png", "b.png"))

        assertEquals(1, marks.size)
        assertEquals("a.png", marks.single().url)
    }

    @Test
    fun missingSlotsMeansNothingToRender() {
        assertTrue(foldInlineImages(emptyList(), listOf("a.png")).isEmpty())
    }

    @Test
    fun parseKeepsTextAndImageInOneInlineBlock() {
        // V2EX 回复里的典型形态：文字后面跟一张 20px 的表情图
        val blocks = parseMarkdown("看起来也不像刚需 https://i.imgur.com/agAJ0Rd.jpeg")

        val inline = blocks.filterIsInstance<MdBlock.InlineImage>()
        assertEquals(1, inline.size)
        assertTrue(inline.single().text.text.contains("刚需"))
        assertEquals("https://i.imgur.com/agAJ0Rd.jpeg", inline.single().url)
        // 图文混排的段落不再被拆成独立的正文图片块
        assertTrue(blocks.filterIsInstance<MdBlock.Image>().isEmpty())
    }

    @Test
    fun parseMarkdownImageAloneStaysABlockImage() {
        val blocks = parseMarkdown("![截图](https://cdn.example.com/a.png)")

        val image = blocks.filterIsInstance<MdBlock.Image>()
        assertEquals(1, image.size)
        assertEquals("https://cdn.example.com/a.png", image.single().url)
        assertTrue(blocks.filterIsInstance<MdBlock.InlineImage>().isEmpty())
    }

    @Test
    fun parsePlainTextHasNoImageBlocks() {
        val blocks = parseMarkdown("就是一段普通文字，没有任何图片。")

        assertTrue(blocks.filterIsInstance<MdBlock.Image>().isEmpty())
        assertTrue(blocks.filterIsInstance<MdBlock.InlineImage>().isEmpty())
        // 占位符不能漏到正文里
        assertFalse(blocks.filterIsInstance<MdBlock.Para>().single().text.text.contains(EMOJI_PLACEHOLDER))
    }

    @Test
    fun parseTextWithTwoImagesKeepsBothInOrder() {
        val blocks = parseMarkdown("前 https://a.example.com/1.png 中 https://b.example.com/2.png 后")

        val inline = blocks.filterIsInstance<MdBlock.InlineImage>()
        assertEquals(listOf("https://a.example.com/1.png", "https://b.example.com/2.png"), inline.map { it.url })
        // 占位符位置递增，说明没有错位
        assertTrue(inline[0].start < inline[1].start)
    }

    @Test
    fun smallImageIsAnEmoji() {
        // 真机上那张表情的实际尺寸是 20x20
        assertTrue(isInlineEmoji(20))
        assertTrue(isInlineEmoji(EmojiMaxWidthPx))
    }

    @Test
    fun normalImageIsNotAnEmoji() {
        assertFalse(isInlineEmoji(EmojiMaxWidthPx + 1))
        assertFalse(isInlineEmoji(240)) // 表情包那种几百像素的图
        assertFalse(isInlineEmoji(1080)) // 手机截图
    }

    @Test
    fun unknownSizeIsNotAnEmoji() {
        // 尺寸未知时宁可按正文图片处理，也不要错判成行内表情
        assertFalse(isInlineEmoji(null))
        assertFalse(isInlineEmoji(0))
        assertFalse(isInlineEmoji(-1))
    }
    @Test
    fun probeCannotShrinkNormalImagesIntoEmojiRange() {
        assertTrue(EmojiProbeSizePx > EmojiMaxWidthPx)
        // 本帖截图按 FIT 缩到探测尺寸后，至少一边仍超过表情阈值。
        assertFalse(isInlineEmoji(128, 63)) // 1920 × 945
        assertFalse(isInlineEmoji(76, 128)) // 1183 × 2000
        assertFalse(isInlineEmoji(20, 128)) // 窄长图也不能当表情
        assertTrue(isInlineEmoji(20, 20))
    }

}
