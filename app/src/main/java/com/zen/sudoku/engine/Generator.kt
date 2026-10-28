package com.zen.sudoku.engine

import kotlin.random.Random

object Generator {

    fun generate(difficulty: Difficulty, random: Random = Random.Default): Puzzle {
        val attempts = if (difficulty.symmetric) 3 else 8
        var best: Puzzle? = null
        var bestClues = Int.MAX_VALUE
        repeat(attempts) {
            val solution = Solver.randomFilledGrid(random)
            val givens = carve(solution, difficulty, random)
            val clues = givens.count { it != 0 }
            if (clues < bestClues) {
                best = Puzzle(givens, solution, difficulty)
                bestClues = clues
            }
            if (clues <= difficulty.targetClues) return best!!
        }
        return best!!
    }

    private fun carve(solution: IntArray, d: Difficulty, random: Random): IntArray {
        val p = solution.copyOf()
        var clues = 81
        for (i in (0 until 81).shuffled(random)) {
            if (clues <= d.targetClues) break
            val cells = if (d.symmetric && i != 80 - i) intArrayOf(i, 80 - i) else intArrayOf(i)
            if (cells.any { p[it] == 0 }) continue
            for (c in cells) p[c] = 0
            val ok = Solver.countSolutions(p) == 1 &&
                (!d.singlesOnly || Logic.solvableWithSingles(p))
            if (ok) clues -= cells.size else for (c in cells) p[c] = solution[c]
        }
        return p
    }
}
