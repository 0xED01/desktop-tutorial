package com.zen.sudoku.engine

import kotlin.random.Random

/** Bitmask backtracking solver with most-constrained-cell heuristic. */
object Solver {

    /** Counts solutions of [grid], stopping once [limit] is reached. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val s = SearchState.from(grid) ?: return 0
        var count = 0
        fun search(): Boolean {
            val i = s.mostConstrained()
            if (i == FULL) return ++count >= limit
            if (i == DEAD) return false
            var m = s.candidates(i)
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                s.place(i, bit)
                if (search()) return true
                s.unplace(i, bit)
            }
            return false
        }
        search()
        return count
    }

    /** Produces a random completely filled valid grid. */
    fun randomFilledGrid(random: Random): IntArray {
        val s = SearchState.from(IntArray(81))!!
        fun search(): Boolean {
            val i = s.mostConstrained()
            if (i == FULL) return true
            if (i == DEAD) return false
            val digits = (1..9).filter { s.candidates(i) and (1 shl it) != 0 }.shuffled(random)
            for (d in digits) {
                val bit = 1 shl d
                s.place(i, bit)
                if (search()) return true
                s.unplace(i, bit)
            }
            return false
        }
        search()
        return s.grid
    }

    private const val FULL = -1
    private const val DEAD = -2

    private class SearchState(val grid: IntArray) {
        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)

        fun candidates(i: Int) =
            Sudoku.ALL and (rows[i / 9] or cols[i % 9] or boxes[Sudoku.box(i)]).inv()

        fun place(i: Int, bit: Int) {
            grid[i] = Integer.numberOfTrailingZeros(bit)
            rows[i / 9] = rows[i / 9] or bit
            cols[i % 9] = cols[i % 9] or bit
            val b = Sudoku.box(i)
            boxes[b] = boxes[b] or bit
        }

        fun unplace(i: Int, bit: Int) {
            grid[i] = 0
            rows[i / 9] = rows[i / 9] and bit.inv()
            cols[i % 9] = cols[i % 9] and bit.inv()
            val b = Sudoku.box(i)
            boxes[b] = boxes[b] and bit.inv()
        }

        fun mostConstrained(): Int {
            var best = FULL
            var bestCount = 10
            for (i in 0 until 81) {
                if (grid[i] != 0) continue
                val n = Integer.bitCount(candidates(i))
                if (n == 0) return DEAD
                if (n < bestCount) {
                    best = i
                    bestCount = n
                    if (n == 1) break
                }
            }
            return best
        }

        companion object {
            fun from(grid: IntArray): SearchState? {
                val s = SearchState(grid.copyOf())
                for (i in 0 until 81) {
                    val v = grid[i]
                    if (v == 0) continue
                    val bit = 1 shl v
                    if (s.candidates(i) and bit == 0) return null
                    s.place(i, bit)
                }
                return s
            }
        }
    }
}
