package com.hydro.water.data

import android.content.Context
import android.content.SharedPreferences
import com.hydro.water.reminder.Notifications
import com.hydro.water.reminder.ReminderScheduler
import com.hydro.water.widget.WaterWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

object WaterRepo {
    private lateinit var app: Context
    private lateinit var db: WaterDb
    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _today = MutableStateFlow<List<Record>>(emptyList())
    val today: StateFlow<List<Record>> = _today

    /** 每次数据变化 +1，统计页据此刷新 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val _nextReminder = MutableStateFlow(0L)
    val nextReminder: StateFlow<Long> = _nextReminder

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        db = WaterDb(app)
        prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = loadSettings()
        _nextReminder.value = prefs.getLong("next_reminder", 0L)
        refresh()
    }

    fun refresh() {
        val k = LocalDate.now().key()
        _today.value = db.recordsBetween(k, k)
    }

    fun todayTotal(): Int = _today.value.sumOf { it.effective }

    fun add(amount: Int, type: Int = _settings.value.defaultType, ts: Long = System.currentTimeMillis()) {
        val before = todayTotal()
        db.insert(ts, amount, type)
        changed()
        val goal = _settings.value.goal
        if (before < goal && todayTotal() >= goal) Notifications.showGoalReached(app)
        if (_settings.value.smartDelay) ReminderScheduler.schedule(app)
    }

    fun delete(record: Record) {
        db.delete(record.id)
        changed()
    }

    fun restore(record: Record) {
        db.insert(record.ts, record.amount, record.type, record.id)
        changed()
    }

    fun clearAll() {
        db.clear()
        changed()
    }

    fun recordsBetween(from: LocalDate, to: LocalDate) = db.recordsBetween(from.key(), to.key())
    fun dailyTotals(): Map<Int, Int> = db.dailyTotals()
    fun totalCount(): Int = db.totalCount()

    /** 截至今天（今天未达标则从昨天算起）的连续达标天数 */
    fun streak(totals: Map<Int, Int> = dailyTotals()): Int {
        val goal = _settings.value.goal
        var d = LocalDate.now()
        if ((totals[d.key()] ?: 0) < goal) d = d.minusDays(1)
        var n = 0
        while ((totals[d.key()] ?: 0) >= goal) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun changed() {
        refresh()
        _version.value++
        Notifications.cancelReminder(app)
        WaterWidget.updateAll(app)
    }

    fun setNextReminder(ts: Long) {
        _nextReminder.value = ts
        prefs.edit().putLong("next_reminder", ts).apply()
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = _settings.value
        val s = transform(old)
        _settings.value = s
        saveSettings(s)
        if (old.reminderOn != s.reminderOn || old.interval != s.interval ||
            old.startMin != s.startMin || old.endMin != s.endMin
        ) ReminderScheduler.schedule(app)
        if (old.goal != s.goal || old.cups != s.cups) WaterWidget.updateAll(app)
        if (old.goal != s.goal) _version.value++
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            goal = prefs.getInt("goal", d.goal),
            weight = prefs.getInt("weight", d.weight),
            activity = prefs.getInt("activity", d.activity),
            cups = prefs.getString("cups", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 4 } ?: d.cups,
            defaultType = prefs.getInt("defaultType", d.defaultType),
            reminderOn = prefs.getBoolean("reminderOn", d.reminderOn),
            interval = prefs.getInt("interval", d.interval),
            startMin = prefs.getInt("startMin", d.startMin),
            endMin = prefs.getInt("endMin", d.endMin),
            stopWhenDone = prefs.getBoolean("stopWhenDone", d.stopWhenDone),
            smartDelay = prefs.getBoolean("smartDelay", d.smartDelay),
            notifyAmount = prefs.getInt("notifyAmount", d.notifyAmount),
            themeMode = prefs.getInt("themeMode", d.themeMode),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
        )
    }

    private fun saveSettings(s: Settings) {
        prefs.edit()
            .putInt("goal", s.goal)
            .putInt("weight", s.weight)
            .putInt("activity", s.activity)
            .putString("cups", s.cups.joinToString(","))
            .putInt("defaultType", s.defaultType)
            .putBoolean("reminderOn", s.reminderOn)
            .putInt("interval", s.interval)
            .putInt("startMin", s.startMin)
            .putInt("endMin", s.endMin)
            .putBoolean("stopWhenDone", s.stopWhenDone)
            .putBoolean("smartDelay", s.smartDelay)
            .putInt("notifyAmount", s.notifyAmount)
            .putInt("themeMode", s.themeMode)
            .putBoolean("dynamicColor", s.dynamicColor)
            .apply()
    }
}
