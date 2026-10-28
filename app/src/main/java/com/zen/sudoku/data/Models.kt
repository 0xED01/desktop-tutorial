package com.zen.sudoku.data

import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.engine.Puzzle

enum class GameStatus { PLAYING, WON, LOST }

const val MAX_MISTAKES = 3
const val MAX_HINTS = 3

/** Immutable game state; every move produces a new instance. */
data class Game(
    val difficulty: Difficulty,
    val givens: IntArray,
    val solution: IntArray,
    /** Current digits including givens; 0 = empty. */
    val values: IntArray,
    /** Pencil marks per cell as a bitmask of digits 1..9. */
    val notes: IntArray,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val elapsed: Long = 0,
    val status: GameStatus = GameStatus.PLAYING,
    /** ISO date for daily challenges, null for regular games. */
    val daily: String? = null,
) {
    val isSolved get() = (0 until 81).all { values[it] == solution[it] }
    val filledCount get() = values.count { it != 0 }

    companion object {
        fun from(puzzle: Puzzle, daily: String? = null) = Game(
            difficulty = puzzle.difficulty,
            givens = puzzle.givens,
            solution = puzzle.solution,
            values = puzzle.givens.copyOf(),
            notes = IntArray(81),
            daily = daily,
        )
    }
}

data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val bestTime: Long = 0,
    val totalTime: Long = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Wins without any mistake or hint. */
    val perfect: Int = 0,
) {
    val winRate get() = if (played == 0) 0 else won * 100 / played
    val averageTime get() = if (won == 0) 0L else totalTime / won
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = 0,
    val autoCheck: Boolean = true,
    val mistakeLimit: Boolean = true,
    val showTimer: Boolean = true,
    val autoRemoveNotes: Boolean = true,
    val hideCompleted: Boolean = true,
    val highlightSame: Boolean = true,
    val highlightRegion: Boolean = true,
    val haptics: Boolean = true,
)

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
