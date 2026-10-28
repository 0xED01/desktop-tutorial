package com.zen.sudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zen.sudoku.data.Game
import com.zen.sudoku.data.GameStatus
import com.zen.sudoku.data.MAX_HINTS
import com.zen.sudoku.data.MAX_MISTAKES
import com.zen.sudoku.data.Settings
import com.zen.sudoku.data.Stats
import com.zen.sudoku.data.Store
import com.zen.sudoku.engine.Daily
import com.zen.sudoku.engine.Difficulty
import com.zen.sudoku.engine.Generator
import com.zen.sudoku.engine.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

class SudokuViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var settings by mutableStateOf(store.loadSettings()); private set
    var stats by mutableStateOf(store.loadStats()); private set
    var dailyDone by mutableStateOf(store.loadDailyDone()); private set
    var game by mutableStateOf(store.loadGame()); private set

    var selected by mutableIntStateOf(-1); private set
    var notesMode by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    var generating by mutableStateOf(false); private set
    var newRecord by mutableStateOf(false); private set
    /** Incremented on every mistake so the UI can shake / vibrate. */
    var mistakeSignal by mutableIntStateOf(0); private set
    var canUndo by mutableStateOf(false); private set

    private class Snapshot(val values: IntArray, val notes: IntArray, val selected: Int)
    private val history = ArrayList<Snapshot>()

    val hasActiveGame get() = game?.status == GameStatus.PLAYING

    fun statsFor(d: Difficulty) = stats[d] ?: Stats()

    fun updateSettings(block: (Settings) -> Settings) {
        settings = block(settings)
        store.saveSettings(settings)
    }

    fun resetStats() {
        stats = Difficulty.entries.associateWith { Stats() }
        store.saveStats(stats)
    }

    // ---- Game lifecycle ----

    fun newGame(d: Difficulty) = start(d, null)

    fun startDaily(date: LocalDate) {
        val g = game
        if (g != null && g.status == GameStatus.PLAYING && g.daily == date.toString()) {
            paused = false
            return
        }
        start(Daily.difficulty(date), date)
    }

    private fun start(d: Difficulty, date: LocalDate?) {
        abandonCurrent()
        game = null
        generating = true
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) {
                Generator.generate(d, date?.let { Random(Daily.seed(it)) } ?: Random.Default)
            }
            resetUi()
            game = Game.from(puzzle, date?.toString())
            editStats(d) { it.copy(played = it.played + 1) }
            generating = false
            save()
        }
    }

    private fun resetUi() {
        selected = -1
        notesMode = false
        paused = false
        newRecord = false
        history.clear()
        canUndo = false
    }

    /** Leaving an unfinished game counts as a loss for the streak. */
    private fun abandonCurrent() {
        val g = game ?: return
        if (g.status != GameStatus.WON) editStats(g.difficulty) { it.copy(streak = 0) }
        store.clearGame()
    }

    fun restart() {
        val g = game ?: return
        resetUi()
        game = g.copy(
            values = g.givens.copyOf(), notes = IntArray(81),
            mistakes = 0, hintsUsed = 0, elapsed = 0, status = GameStatus.PLAYING,
        )
        save()
    }

    fun secondChance() {
        val g = game ?: return
        game = g.copy(status = GameStatus.PLAYING, mistakes = MAX_MISTAKES - 1)
        save()
    }

    fun giveUp() {
        abandonCurrent()
        game = null
        resetUi()
    }

    /** Drops a finished game from memory once the player leaves it. */
    fun closeFinished() {
        if (game?.status == GameStatus.WON) {
            game = null
            resetUi()
        }
    }

    fun tick() {
        val g = game ?: return
        if (g.status == GameStatus.PLAYING && !paused) game = g.copy(elapsed = g.elapsed + 1)
    }

    fun togglePause() { paused = !paused }

    fun pauseIfPlaying() {
        if (hasActiveGame) paused = true
        save()
    }

    fun save() {
        val g = game ?: return
        if (g.status == GameStatus.PLAYING) store.saveGame(g)
    }

    // ---- Moves ----

    fun select(i: Int) {
        if (!paused) selected = if (selected == i) -1 else i
    }

    fun toggleNotes() { notesMode = !notesMode }

    private fun canEdit(g: Game, i: Int): Boolean =
        g.status == GameStatus.PLAYING && !paused && i >= 0 && g.givens[i] == 0 &&
            !(settings.autoCheck && g.values[i] != 0 && g.values[i] == g.solution[i])

    private fun pushHistory(g: Game) {
        history += Snapshot(g.values, g.notes, selected)
        if (history.size > 200) history.removeAt(0)
        canUndo = true
    }

    fun input(d: Int) {
        val g = game ?: return
        val i = selected
        if (!canEdit(g, i)) return
        if (notesMode) {
            if (g.values[i] != 0) return
            pushHistory(g)
            val notes = g.notes.copyOf()
            notes[i] = notes[i] xor (1 shl d)
            game = g.copy(notes = notes)
            save()
            return
        }
        if (g.values[i] == d) return
        pushHistory(g)
        val values = g.values.copyOf().also { it[i] = d }
        val notes = g.notes.copyOf().also { it[i] = 0 }
        val wrong = d != g.solution[i]
        if (settings.autoRemoveNotes && !(settings.autoCheck && wrong)) clearPeerNotes(notes, i, d)
        var mistakes = g.mistakes
        var status = g.status
        if (settings.autoCheck && wrong) {
            mistakes++
            mistakeSignal++
            if (settings.mistakeLimit && mistakes >= MAX_MISTAKES) status = GameStatus.LOST
        }
        commit(g.copy(values = values, notes = notes, mistakes = mistakes, status = status))
    }

    fun erase() {
        val g = game ?: return
        val i = selected
        if (!canEdit(g, i) || (g.values[i] == 0 && g.notes[i] == 0)) return
        pushHistory(g)
        game = g.copy(
            values = g.values.copyOf().also { it[i] = 0 },
            notes = g.notes.copyOf().also { it[i] = 0 },
        )
        save()
    }

    fun hint() {
        val g = game ?: return
        if (g.status != GameStatus.PLAYING || paused || g.hintsUsed >= MAX_HINTS) return
        val sel = selected
        val target = if (sel >= 0 && g.givens[sel] == 0 && g.values[sel] != g.solution[sel]) {
            sel
        } else {
            // Prefer the cell with the fewest candidates: the most "findable" next move.
            val open = (0 until 81).filter { g.values[it] != g.solution[it] }
            val grid = IntArray(81) { if (g.values[it] == g.solution[it]) g.values[it] else 0 }
            open.minByOrNull { Integer.bitCount(Sudoku.candidates(grid, it)) } ?: return
        }
        pushHistory(g)
        val d = g.solution[target]
        val values = g.values.copyOf().also { it[target] = d }
        val notes = g.notes.copyOf().also { it[target] = 0 }
        clearPeerNotes(notes, target, d)
        selected = target
        commit(g.copy(values = values, notes = notes, hintsUsed = g.hintsUsed + 1))
    }

    fun undo() {
        val g = game ?: return
        if (g.status != GameStatus.PLAYING || paused) return
        val s = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        selected = s.selected
        game = g.copy(values = s.values, notes = s.notes)
        save()
    }

    private fun clearPeerNotes(notes: IntArray, i: Int, d: Int) {
        val mask = (1 shl d).inv()
        for (p in Sudoku.PEERS[i]) notes[p] = notes[p] and mask
    }

    private fun commit(next: Game) {
        val g = if (next.status == GameStatus.PLAYING && next.isSolved) {
            next.copy(status = GameStatus.WON)
        } else next
        game = g
        when (g.status) {
            GameStatus.WON -> onWin(g)
            GameStatus.LOST -> store.clearGame()
            GameStatus.PLAYING -> save()
        }
    }

    private fun onWin(g: Game) {
        selected = -1
        val old = statsFor(g.difficulty)
        newRecord = old.bestTime == 0L || g.elapsed < old.bestTime
        editStats(g.difficulty) {
            it.copy(
                won = it.won + 1,
                totalTime = it.totalTime + g.elapsed,
                bestTime = if (newRecord) g.elapsed else it.bestTime,
                streak = it.streak + 1,
                bestStreak = maxOf(it.bestStreak, it.streak + 1),
                perfect = it.perfect + if (g.mistakes == 0 && g.hintsUsed == 0) 1 else 0,
            )
        }
        g.daily?.let {
            dailyDone = dailyDone + it
            store.saveDailyDone(dailyDone)
        }
        store.clearGame()
    }

    private fun editStats(d: Difficulty, block: (Stats) -> Stats) {
        stats = stats + (d to block(statsFor(d)))
        store.saveStats(stats)
    }
}
