package com.v4ex.android.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 浏览历史按天整理：同一天归成一组，组间按日期倒序，组内保持最近在前。
 * 纯函数（不碰 Android API），方便单测。
 */
object HistoryGroup {

    /** 一天：日期（当 LazyColumn 的 key 用）+ 显示标签 + 当天的条目。 */
    data class Day(
        val date: LocalDate,
        val label: String,
        val items: List<HistoryEntry>,
    )

    fun group(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<Day> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return entries
            .sortedByDescending { it.viewedAt }
            .groupBy { Instant.ofEpochMilli(it.viewedAt).atZone(zone).toLocalDate() }
            .map { (date, dayItems) -> Day(date, label(date, today), dayItems) }
    }

    /** 今天 / 昨天 / 10 月 26 日；不是今年的带上年份。 */
    fun label(date: LocalDate, today: LocalDate): String = when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> "${date.monthValue} 月 ${date.dayOfMonth} 日"
        else -> "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"
    }
}
