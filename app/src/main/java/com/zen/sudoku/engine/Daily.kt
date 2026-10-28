package com.zen.sudoku.engine

import java.time.DayOfWeek
import java.time.LocalDate

/** Deterministic daily puzzles: same date gives the same puzzle on every device. */
object Daily {
    fun difficulty(date: LocalDate): Difficulty = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> Difficulty.EASY
        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> Difficulty.MEDIUM
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }

    fun seed(date: LocalDate): Long =
        date.year * 10_000L + date.monthValue * 100 + date.dayOfMonth

    /** Consecutive completed days ending today (or yesterday if today isn't done yet). */
    fun streak(done: Set<String>, today: LocalDate = LocalDate.now()): Int {
        var day = if (today.toString() in done) today else today.minusDays(1)
        var n = 0
        while (day.toString() in done) {
            n++
            day = day.minusDays(1)
        }
        return n
    }
}
