package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯函数测试：通知本地留存的合并、已读对账、编解码。不碰 Android API。 */
class NoticeHistoryTest {

    private fun notice(id: Int, created: Long = id.toLong(), read: Boolean = false, avatar: String? = null) = Notice(
        id = id,
        text = "text $id",
        payload = "",
        created = created,
        member = "alice",
        avatar = avatar,
        topicId = 100 + id,
        read = read,
    )

    @Test
    fun fetchedItemsAreAlwaysUnread() {
        val merged = NoticeHistory.merge(
            cached = listOf(notice(1, read = true)),
            fetched = listOf(notice(1)),
            reconcileFrom = null,
        )
        assertEquals(false, merged.single().read)
    }

    @Test
    fun cachedUnreadMissingFromFirstPageBecomesReadWhenCovered() {
        // 第一页满（20 条）时，只能对账到第一页最早那条之后的通知。
        val cached = listOf(notice(5, created = 500), notice(1, created = 100))
        val fetched = listOf(notice(9, created = 900), notice(6, created = 600))

        val merged = NoticeHistory.merge(cached, fetched, reconcileFrom = 600)

        // 5 早于 600 不在覆盖范围内，仍是未读；1 同理
        assertEquals(false, merged.first { it.id == 5 }.read)
        assertEquals(false, merged.first { it.id == 1 }.read)

        val merged2 = NoticeHistory.merge(
            cached = listOf(notice(7, created = 700), notice(1, created = 100)),
            fetched = listOf(notice(9, created = 900), notice(6, created = 600)),
            reconcileFrom = 600,
        )
        // 7 在覆盖范围内且不在第一页 → 网页上读过了
        assertTrue(merged2.first { it.id == 7 }.read)
        assertEquals(false, merged2.first { it.id == 1 }.read)
    }

    @Test
    fun alreadyReadStaysReadWhenNotFetched() {
        val merged = NoticeHistory.merge(
            cached = listOf(notice(3, created = 300, read = true)),
            fetched = emptyList(),
            reconcileFrom = Long.MIN_VALUE,
        )
        assertTrue(merged.single().read)
    }

    @Test
    fun emptyFirstPageMarksAllCachedUnreadAsRead() {
        val floor = NoticeHistory.reconcileFloor(emptyList(), pageSize = 20)
        val merged = NoticeHistory.merge(
            cached = listOf(notice(1), notice(2)),
            fetched = emptyList(),
            reconcileFrom = floor,
        )
        assertTrue(merged.all { it.read })
    }

    @Test
    fun partialFirstPageCoversEverything() {
        assertEquals(Long.MIN_VALUE, NoticeHistory.reconcileFloor(listOf(notice(1)), pageSize = 20))
    }

    @Test
    fun fullFirstPageFloorIsOldestFetched() {
        val page = (1..20).map { notice(it, created = it * 10L) }
        assertEquals(10L, NoticeHistory.reconcileFloor(page, pageSize = 20))
    }

    @Test
    fun avatarFallsBackToCachedWhenFetchedHasNone() {
        val merged = NoticeHistory.merge(
            cached = listOf(notice(1, avatar = "old.png")),
            fetched = listOf(notice(1, avatar = null)),
            reconcileFrom = null,
        )
        assertEquals("old.png", merged.single().avatar)
    }

    @Test
    fun mergeSortsNewestFirstAndCapsSize() {
        val many = (1..(NoticeHistory.MAX + 50)).map { notice(it, created = it.toLong()) }
        val merged = NoticeHistory.merge(emptyList(), many, reconcileFrom = null)
        assertEquals(NoticeHistory.MAX, merged.size)
        assertEquals(NoticeHistory.MAX + 50, merged.first().id)
        assertTrue(merged.zipWithNext().all { (a, b) -> a.created >= b.created })
    }

    @Test
    fun markReadAndUnreadOnlyTouchListedIds() {
        val list = listOf(notice(1), notice(2))
        val read = NoticeHistory.markRead(list, setOf(2))
        assertEquals(listOf(false, true), read.map { it.read })
        val back = NoticeHistory.markUnread(read, setOf(2))
        assertEquals(listOf(false, false), back.map { it.read })
    }

    @Test
    fun roundTripKeepsEveryFieldIncludingNewlinesAndNulls() {
        val list = listOf(
            notice(2, read = true).copy(text = "第一行\n第二行 | 带分隔符", payload = "payload\n\"quoted\""),
            notice(1, avatar = "https://x/a.png").copy(topicId = null),
        )
        assertEquals(list, NoticeHistory.decode(NoticeHistory.encode(list)))
    }

    @Test
    fun decodeSkipsBrokenEntriesAndBlankInput() {
        assertTrue(NoticeHistory.decode("").isEmpty())
        assertTrue(NoticeHistory.decode("not json").isEmpty())
        val raw = """[{"text":"no id"},{"id":7,"text":"ok","created":70,"member":"bob","read":true}]"""
        val decoded = NoticeHistory.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(7, decoded.single().id)
        assertTrue(decoded.single().read)
        assertNull(decoded.single().topicId)
    }

    @Test
    fun ownerIsStableAndDiffersPerToken() {
        assertEquals(NoticeHistory.ownerOf("abc"), NoticeHistory.ownerOf("abc"))
        assertNotEquals(NoticeHistory.ownerOf("abc"), NoticeHistory.ownerOf("abd"))
        // 只保留摘要，不能包含令牌明文
        assertTrue(!NoticeHistory.ownerOf("secret-token").contains("secret"))
    }
}
