package com.yuandian.nightprotocol.schedule

import com.yuandian.nightprotocol.data.SleepSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class SleepWindow(val start: Instant, val end: Instant) {
    operator fun contains(t: Instant): Boolean = !t.isBefore(start) && t.isBefore(end)
}

/**
 * 纯函数：根据本地墙钟计划计算睡眠窗口。
 * - 支持跨午夜（23:30→07:30）与同日窗口（01:00→06:00）。
 * - 夏令时：不存在的本地时间按 java.time 规则顺延，重叠时间取较早偏移。
 * - 每次都基于“当前时间 + 当前时区”重新计算，因此时间/时区变化后重新核对即可得到正确结果。
 */
object SleepWindowCalculator {

    fun windowStartingOn(date: LocalDate, s: SleepSchedule, zone: ZoneId): SleepWindow {
        val start = at(date, s.startMinute, zone)
        val endDate = if (s.endMinute <= s.startMinute) date.plusDays(1) else date
        val end = at(endDate, s.endMinute, zone)
        return SleepWindow(start.toInstant(), end.toInstant())
    }

    /** 包含 now 的窗口；不在睡眠时间内则返回 null。 */
    fun windowContaining(now: Instant, s: SleepSchedule, zone: ZoneId): SleepWindow? {
        if (!s.isValid) return null
        val today = now.atZone(zone).toLocalDate()
        for (d in listOf(today.minusDays(1), today)) {
            val w = windowStartingOn(d, s, zone)
            if (now in w) return w
        }
        return null
    }

    /** 严格晚于 now 的下一个窗口。 */
    fun nextWindow(now: Instant, s: SleepSchedule, zone: ZoneId): SleepWindow? {
        if (!s.isValid) return null
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..2L) {
            val w = windowStartingOn(today.plusDays(offset), s, zone)
            if (w.start.isAfter(now)) return w
        }
        return null
    }

    private fun at(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60)), zone)
}
