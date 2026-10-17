package com.gomoku.lan

const val N = 15
const val EMPTY = 0
const val BLACK = 1
const val WHITE = 2

fun other(color: Int) = if (color == BLACK) WHITE else BLACK

/** 自由五子棋：五子及以上连珠即胜，黑先。 */
class Game {
    val board = Array(N) { IntArray(N) }
    val moves = ArrayList<Int>()
    var winner = EMPTY
        private set
    var resigned = false
        private set
    /** 连珠两端 (r1, c1, r2, c2)。 */
    var winLine: IntArray? = null
        private set

    val turn get() = if (moves.size % 2 == 0) BLACK else WHITE
    val isDraw get() = winner == EMPTY && moves.size == N * N
    val over get() = winner != EMPTY || isDraw
    val lastMove get() = if (moves.isEmpty()) -1 else moves[moves.size - 1]

    fun at(r: Int, c: Int) = board[r][c]

    fun canPlay(r: Int, c: Int) = !over && r in 0 until N && c in 0 until N && board[r][c] == EMPTY

    fun play(r: Int, c: Int): Boolean {
        if (!canPlay(r, c)) return false
        val color = turn
        board[r][c] = color
        moves.add(r * N + c)
        checkWin(r, c, color)
        return true
    }

    fun hasMoveOf(color: Int) = moves.indices.any { (if (it % 2 == 0) BLACK else WHITE) == color }

    /** 撤回到 [color] 方重新落子：撤掉其最后一手以及之后的所有手。返回撤回手数。 */
    fun undoFor(color: Int): Int {
        if (over || !hasMoveOf(color)) return 0
        var n = 0
        while (moves.isNotEmpty()) {
            val m = moves.removeAt(moves.size - 1)
            val col = board[m / N][m % N]
            board[m / N][m % N] = EMPTY
            n++
            if (col == color) break
        }
        return n
    }

    fun resign(color: Int) {
        if (over) return
        winner = other(color)
        resigned = true
    }

    fun reset() {
        for (row in board) row.fill(EMPTY)
        moves.clear()
        winner = EMPTY
        resigned = false
        winLine = null
    }

    private fun checkWin(r: Int, c: Int, color: Int) {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (d in dirs) {
            var r1 = r; var c1 = c
            while (inside(r1 - d[0], c1 - d[1]) && board[r1 - d[0]][c1 - d[1]] == color) { r1 -= d[0]; c1 -= d[1] }
            var r2 = r; var c2 = c
            while (inside(r2 + d[0], c2 + d[1]) && board[r2 + d[0]][c2 + d[1]] == color) { r2 += d[0]; c2 += d[1] }
            val len = maxOf(Math.abs(r2 - r1), Math.abs(c2 - c1)) + 1
            if (len >= 5) {
                winner = color
                winLine = intArrayOf(r1, c1, r2, c2)
                return
            }
        }
    }

    private fun inside(r: Int, c: Int) = r in 0 until N && c in 0 until N
}
