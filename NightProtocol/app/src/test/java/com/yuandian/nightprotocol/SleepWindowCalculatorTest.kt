package com.yuandian.nightprotocol

import com.yuandian.nightprotocol.data.SleepSchedule
import com.yuandian.nightprotocol.schedule.SleepWindowCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class SleepWindowCalculatorTest {
    private val sh = ZoneId.of("Asia/Shanghai")
    private val def = SleepSchedule()
    private fun at(s: String, z: ZoneId = sh) = LocalDateTime.parse(s).atZone(z).toInstant()

    @Test fun beforeStart_notInWindow() {
        assertNull(SleepWindowCalculator.windowContaining(at("2026-11-01T23:29:59"), def, sh))
    }

    @Test fun atStart_inWindow_endsNextMorning() {
        val w = SleepWindowCalculator.windowContaining(at("2026-11-01T23:30:00"), def, sh)!!
        assertEquals(at("2026-11-02T07:30:00"), w.end)
    }

    @Test fun afterMidnight_belongsToPreviousNightWindow() {
        val w = SleepWindowCalculator.windowContaining(at("2026-11-02T03:00:00"), def, sh)!!
        assertEquals(at("2026-11-01T23:30:00"), w.start)
        assertEquals(at("2026-11-02T07:30:00"), w.end)
    }

    @Test fun atEnd_isExclusive() {
        assertNull(SleepWindowCalculator.windowContaining(at("2026-11-02T07:30:00"), def, sh))
    }

    @Test fun nextWindow_fromMorning_isTonight() {
        val w = SleepWindowCalculator.nextWindow(at("2026-11-02T07:30:00"), def, sh)!!
        assertEquals(at("2026-11-02T23:30:00"), w.start)
    }

    @Test fun nextWindow_fromInsideWindow_isTomorrowNight() {
        val w = SleepWindowCalculator.nextWindow(at("2026-11-02T01:00:00"), def, sh)!!
        assertEquals(at("2026-11-02T23:30:00"), w.start)
    }

    @Test fun sameDayWindow() {
        val s = SleepSchedule(60, 6 * 60)
        assertNotNull(SleepWindowCalculator.windowContaining(at("2026-11-02T05:59:00"), s, sh))
        assertNull(SleepWindowCalculator.windowContaining(at("2026-11-02T06:00:00"), s, sh))
        assertNull(SleepWindowCalculator.windowContaining(at("2026-11-02T00:59:00"), s, sh))
    }

    @Test fun invalidSchedule_neverLocks() {
        val s = SleepSchedule(600, 600)
        assertNull(SleepWindowCalculator.windowContaining(at("2026-11-02T10:00:00"), s, sh))
        assertNull(SleepWindowCalculator.nextWindow(at("2026-11-02T10:00:00"), s, sh))
    }

    @Test fun dstSpringForward_windowIsOneHourShorter() {
        val ny = ZoneId.of("America/New_York") // 2026-03-08 02:00 → 03:00
        val w = SleepWindowCalculator.windowContaining(at("2026-03-08T03:30:00", ny), def, ny)!!
        assertEquals(Duration.ofHours(7), Duration.between(w.start, w.end))
    }

    @Test fun dstGap_startInsideGap_isShiftedForward() {
        val ny = ZoneId.of("America/New_York")
        val s = SleepSchedule(2 * 60 + 30, 7 * 60) // 02:30 不存在于 2026-03-08
        val w = SleepWindowCalculator.windowStartingOn(java.time.LocalDate.parse("2026-03-08"), s, ny)
        assertEquals(at("2026-03-08T03:30:00", ny), w.start)
    }

    @Test fun timezoneChange_recomputedByWallClock() {
        // 同一绝对时刻：上海 00:00（窗口内）= 伦敦 16:00（窗口外）
        val instant = at("2026-11-02T00:00:00")
        assertNotNull(SleepWindowCalculator.windowContaining(instant, def, sh))
        assertNull(SleepWindowCalculator.windowContaining(instant, def, ZoneId.of("Europe/London")))
    }
}
