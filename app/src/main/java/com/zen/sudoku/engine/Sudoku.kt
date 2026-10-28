package com.zen.sudoku.engine

enum class Difficulty(
    val label: String,
    val description: String,
    /** Number of givens the generator aims for. */
    val targetClues: Int,
    /** Require the puzzle to be solvable with naked/hidden singles only. */
    val singlesOnly: Boolean,
    /** Remove cells in point-symmetric pairs (looks nicer, can't go as low). */
    val symmetric: Boolean,
) {
    EASY("简单", "轻松入门，适合热身", 38, true, true),
    MEDIUM("中等", "需要一点观察力", 32, true, true),
    HARD("困难", "考验逻辑推理", 28, false, true),
    EXPERT("专家", "高手进阶之选", 25, false, false),
    MASTER("大师", "极限挑战，耐心为王", 22, false, false),
}

class Puzzle(val givens: IntArray, val solution: IntArray, val difficulty: Difficulty)

object Sudoku {
    /** Bits 1..9 set: every digit is a candidate. */
    const val ALL = 0x3FE

    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** The 20 cells sharing a row, column or box with each cell. */
    val PEERS: Array<IntArray> = Array(81) { i ->
        (0 until 81).filter { j ->
            j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i))
        }.toIntArray()
    }

    /** The 27 units: 9 rows, 9 columns, 9 boxes. */
    val UNITS: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> (0 until 81).filter { box(it) == u - 18 }.toIntArray()
        }
    }

    /** Digits still possible in cell [i] given the current [grid]. */
    fun candidates(grid: IntArray, i: Int): Int {
        if (grid[i] != 0) return 0
        var used = 0
        for (p in PEERS[i]) used = used or (1 shl grid[p])
        return ALL and used.inv()
    }
}
