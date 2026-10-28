package com.zen.sudoku.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeneratorTest {

    private fun isValidSolution(g: IntArray) = Sudoku.UNITS.all { unit ->
        unit.map { g[it] }.sorted() == (1..9).toList()
    }

    @Test
    fun everyDifficultyProducesUniqueConsistentPuzzle() {
        for (d in Difficulty.entries) {
            repeat(3) { n ->
                val start = System.nanoTime()
                val p = Generator.generate(d, Random(n * 31 + d.ordinal))
                val ms = (System.nanoTime() - start) / 1_000_000
                val clues = p.givens.count { it != 0 }
                println("$d clues=$clues ${ms}ms")
                assertTrue(isValidSolution(p.solution))
                assertEquals(1, Solver.countSolutions(p.givens))
                for (i in 0 until 81) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
                if (d.singlesOnly) assertTrue(Logic.solvableWithSingles(p.givens))
                assertTrue("$d has $clues clues", clues <= d.targetClues + 4)
            }
        }
    }

    @Test
    fun dailyPuzzleIsDeterministic() {
        val a = Generator.generate(Difficulty.HARD, Random(20261028))
        val b = Generator.generate(Difficulty.HARD, Random(20261028))
        assertArrayEquals(a.givens, b.givens)
    }
}
