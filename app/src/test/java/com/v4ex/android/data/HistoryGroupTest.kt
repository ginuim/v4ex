package com.v4ex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 纯函数测试：浏览历史按天分组。不碰 Android API。 */
class HistoryGroupTest {

    private val zone = ZoneId.systemDefault()

    private fun entry(id: Int, viewedOn: LocalDate, hour: Int = 12): HistoryEntry = HistoryEntry(
        id = id,
        title = "t$id",
        nodeName = "n",
        nodeTitle = "N",
        author = "u",
        replies = 0,
        viewedAt = viewedOn.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun emptyHistoryHasNoDays() {
        assertTrue(HistoryGroup.group(emptyList()).isEmpty())
    }

    @Test
    fun groupsByDayNewestFirstKeepingOrderInsideDay() {
        val today = LocalDate.now(zone)
        val entries = listOf(
            entry(1, today.minusDays(2), hour = 20),
            entry(2, today, hour = 18),
            entry(3, today.minusDays(1), hour = 8),
            entry(4, today, hour = 9),
        )

        val days = HistoryGroup.group(entries)

        assertEquals(
            listOf("今天", "昨天", HistoryGroup.label(today.minusDays(2), today)),
            days.map { it.label },
        )
        // 同一天内保持「最近在前」
        assertEquals(listOf(2, 4), days[0].items.map { it.id })
        assertEquals(listOf(3), days[1].items.map { it.id })
        assertEquals(listOf(1), days[2].items.map { it.id })
    }

    @Test
    fun labelUsesYearForOlderHistory() {
        val today = LocalDate.of(2026, 10, 28)

        assertEquals("今天", HistoryGroup.label(today, today))
        assertEquals("昨天", HistoryGroup.label(LocalDate.of(2026, 10, 27), today))
        assertEquals("10 月 20 日", HistoryGroup.label(LocalDate.of(2026, 10, 20), today))
        assertEquals("2025 年 12 月 31 日", HistoryGroup.label(LocalDate.of(2025, 12, 31), today))
    }
}
