package com.zen.sudoku.engine

/** Human-style deductions used to grade puzzles and pick good hints. */
object Logic {

    /** True when [grid] can be completed using only naked and hidden singles. */
    fun solvableWithSingles(grid: IntArray): Boolean {
        val g = grid.copyOf()
        while (true) {
            if (g.all { it != 0 }) return true
            if (!applySingles(g)) return false
        }
    }

    /** Applies one round of singles to [g]; returns false when stuck or contradictory. */
    private fun applySingles(g: IntArray): Boolean {
        val cand = IntArray(81) { Sudoku.candidates(g, it) }
        var progress = false
        for (i in 0 until 81) {
            if (g[i] != 0) continue
            if (cand[i] == 0) return false
            if (Integer.bitCount(cand[i]) == 1) {
                g[i] = Integer.numberOfTrailingZeros(cand[i])
                progress = true
            }
        }
        if (progress) return true
        for (unit in Sudoku.UNITS) {
            for (d in 1..9) {
                val bit = 1 shl d
                var where = -1
                var count = 0
                for (i in unit) {
                    if (g[i] == d) { count = -1; break }
                    if (g[i] == 0 && cand[i] and bit != 0) { where = i; count++ }
                }
                if (count == 1) {
                    g[where] = d
                    progress = true
                }
            }
        }
        return progress
    }
}
