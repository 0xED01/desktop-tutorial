package com.islandpet.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 全局唯一的存档。App 界面、灵动岛悬浮窗、通知按钮都通过它读写。
 * 每次修改都会生成一个新的 GameState 对象，界面据此刷新。
 */
object PetRepo {
    private const val KEY = "state.v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** 互动反馈 / 成就提示 */
    val messages = _messages.asSharedFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("islandpet", Context.MODE_PRIVATE)
        val loaded = prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<GameState>(it) }.getOrNull() }
        _state.value = (loaded ?: GameState()).also { it.tick() }
    }

    /** 修改存档。返回反馈文字（若解锁成就则返回成就提示）。 */
    @Synchronized
    fun act(change: (GameState) -> String?): String? {
        val s = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), _state.value))
        s.tick()
        val message = change(s)
        s.tick()
        val unlocked = s.checkAchievements()
        prefs.edit().putString(KEY, json.encodeToString(GameState.serializer(), s)).apply()
        _state.value = s
        val shown = unlocked.firstOrNull()?.let { "🏆 解锁成就「${it.title}」+${it.reward} 金币" } ?: message
        if (!shown.isNullOrEmpty()) _messages.tryEmit(shown)
        return shown
    }

    fun refresh() {
        act { null }
    }

    fun reset() {
        act { s ->
            val fresh = GameState()
            s.pet = null; s.coins = fresh.coins; s.inventory = fresh.inventory
            s.ownedAccessories = fresh.ownedAccessories; s.ownedThemes = fresh.ownedThemes; s.theme = fresh.theme
            s.achievements = fresh.achievements; s.diary = fresh.diary; s.counters = fresh.counters
            s.wish = null; s.lastMessage = null; s.lastMessageAt = null; s.lastEventRoll = null
            null
        }
    }
}
