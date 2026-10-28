package com.zen.sudoku.data

import android.content.Context
import com.zen.sudoku.engine.Difficulty
import org.json.JSONArray
import org.json.JSONObject

/** Persists settings, statistics, daily progress and the game in progress. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("sudoku", Context.MODE_PRIVATE)

    private fun json(key: String): JSONObject? =
        prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun put(key: String, value: String?) = prefs.edit().putString(key, value).apply()

    fun loadSettings(): Settings {
        val j = json(KEY_SETTINGS) ?: return Settings()
        val d = Settings()
        return Settings(
            theme = runCatching { ThemeMode.valueOf(j.getString("theme")) }.getOrDefault(d.theme),
            accent = j.optInt("accent", d.accent),
            autoCheck = j.optBoolean("autoCheck", d.autoCheck),
            mistakeLimit = j.optBoolean("mistakeLimit", d.mistakeLimit),
            showTimer = j.optBoolean("showTimer", d.showTimer),
            autoRemoveNotes = j.optBoolean("autoRemoveNotes", d.autoRemoveNotes),
            hideCompleted = j.optBoolean("hideCompleted", d.hideCompleted),
            highlightSame = j.optBoolean("highlightSame", d.highlightSame),
            highlightRegion = j.optBoolean("highlightRegion", d.highlightRegion),
            haptics = j.optBoolean("haptics", d.haptics),
        )
    }

    fun saveSettings(s: Settings) = put(KEY_SETTINGS, JSONObject().apply {
        put("theme", s.theme.name)
        put("accent", s.accent)
        put("autoCheck", s.autoCheck)
        put("mistakeLimit", s.mistakeLimit)
        put("showTimer", s.showTimer)
        put("autoRemoveNotes", s.autoRemoveNotes)
        put("hideCompleted", s.hideCompleted)
        put("highlightSame", s.highlightSame)
        put("highlightRegion", s.highlightRegion)
        put("haptics", s.haptics)
    }.toString())

    fun loadStats(): Map<Difficulty, Stats> = Difficulty.entries.associateWith { d ->
        val j = json(KEY_STATS + d.name) ?: return@associateWith Stats()
        Stats(
            played = j.optInt("played"),
            won = j.optInt("won"),
            bestTime = j.optLong("bestTime"),
            totalTime = j.optLong("totalTime"),
            streak = j.optInt("streak"),
            bestStreak = j.optInt("bestStreak"),
            perfect = j.optInt("perfect"),
        )
    }

    fun saveStats(stats: Map<Difficulty, Stats>) {
        val e = prefs.edit()
        for ((d, s) in stats) {
            e.putString(KEY_STATS + d.name, JSONObject().apply {
                put("played", s.played)
                put("won", s.won)
                put("bestTime", s.bestTime)
                put("totalTime", s.totalTime)
                put("streak", s.streak)
                put("bestStreak", s.bestStreak)
                put("perfect", s.perfect)
            }.toString())
        }
        e.apply()
    }

    fun loadDailyDone(): Set<String> = prefs.getStringSet(KEY_DAILY, emptySet())!!.toSet()

    fun saveDailyDone(done: Set<String>) = prefs.edit().putStringSet(KEY_DAILY, done).apply()

    fun loadGame(): Game? {
        val j = json(KEY_GAME) ?: return null
        return runCatching {
            val notes = j.getJSONArray("n")
            Game(
                difficulty = Difficulty.valueOf(j.getString("d")),
                givens = j.getString("g").toDigits(),
                solution = j.getString("s").toDigits(),
                values = j.getString("v").toDigits(),
                notes = IntArray(81) { notes.getInt(it) },
                mistakes = j.optInt("m"),
                hintsUsed = j.optInt("h"),
                elapsed = j.optLong("t"),
                daily = j.optString("daily").ifEmpty { null },
            )
        }.getOrNull()
    }

    fun saveGame(g: Game) = put(KEY_GAME, JSONObject().apply {
        put("d", g.difficulty.name)
        put("g", g.givens.joinToString(""))
        put("s", g.solution.joinToString(""))
        put("v", g.values.joinToString(""))
        put("n", JSONArray(g.notes.toList()))
        put("m", g.mistakes)
        put("h", g.hintsUsed)
        put("t", g.elapsed)
        g.daily?.let { put("daily", it) }
    }.toString())

    fun clearGame() = put(KEY_GAME, null)

    private fun String.toDigits(): IntArray {
        require(length == 81)
        return IntArray(81) { this[it] - '0' }
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_STATS = "stats_"
        const val KEY_DAILY = "daily_done"
        const val KEY_GAME = "game"
    }
}
