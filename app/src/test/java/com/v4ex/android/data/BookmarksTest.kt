package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯函数测试：收藏的编解码、切换、删除。不碰 Android API。 */
class BookmarksTest {

    private fun bookmark(id: Int, savedAt: Long = id.toLong(), title: String = "t$id") = Bookmark(
        id = id,
        title = title,
        nodeName = "python",
        nodeTitle = "Python",
        author = "alice",
        replies = 3,
        savedAt = savedAt,
    )

    @Test
    fun emptyRawDecodesToEmptyList() {
        assertTrue(Bookmarks.decode("").isEmpty())
    }

    @Test
    fun roundTripKeepsEveryField() {
        val list = listOf(bookmark(2, savedAt = 200), bookmark(1, savedAt = 100))

        assertEquals(list, Bookmarks.decode(Bookmarks.encode(list)))
    }

    @Test
    fun verticalBarsInTitleSurviveRoundTrip() {
        // 竖线在标题里很常见；分隔符用的是 \u001F 而不是竖线，所以要原样还原
        val withBars = bookmark(7, title = "A | B | 末尾")

        assertEquals(listOf(withBars), Bookmarks.decode(Bookmarks.encode(listOf(withBars))))
    }

    @Test
    fun newlinesAndSeparatorCharsAreFlattenedSoOneLineStaysOneBookmark() {
        val messy = bookmark(9, title = "第一行\n第二行\u001F尾").copy(author = "bob\r\nx")

        val decoded = Bookmarks.decode(Bookmarks.encode(listOf(messy, bookmark(10))))

        assertEquals(2, decoded.size)
        assertEquals("第一行 第二行 尾", decoded[0].title)
        assertEquals("bob  x", decoded[0].author)
        assertEquals(10, decoded[1].id)
    }

    @Test
    fun malformedLinesAreSkippedNotFatal() {
        val good = Bookmarks.encode(listOf(bookmark(1)))
        val raw = "not-a-line\n\n" + "abc\u001F1\u001Fa\u001Fb\u001Fc\u001F0\u001Fd\n" + good

        assertEquals(listOf(1), Bookmarks.decode(raw).map { it.id })
    }

    @Test
    fun toggleAddsToFrontWhenAbsent() {
        val list = listOf(bookmark(1))

        assertEquals(listOf(2, 1), Bookmarks.toggle(list, bookmark(2)).map { it.id })
    }

    @Test
    fun toggleRemovesWhenAlreadyBookmarked() {
        val list = listOf(bookmark(2), bookmark(1))

        assertEquals(listOf(1), Bookmarks.toggle(list, bookmark(2)).map { it.id })
    }

    @Test
    fun removeDropsOnlyTheMatchingId() {
        val list = listOf(bookmark(3), bookmark(2), bookmark(1))

        assertEquals(listOf(3, 1), Bookmarks.remove(list, 2).map { it.id })
        assertEquals(list, Bookmarks.remove(list, 99))
    }
}
