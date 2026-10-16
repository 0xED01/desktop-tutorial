package com.shiji.footprint.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordMode(
    val title: String,
    val desc: String,
    val intervalMs: Long,
    val minDistance: Float,
    val maxAccuracy: Float,
) {
    PRECISE("高精度", "2 秒 / 5 米，轨迹最细腻", 2_000, 5f, 40f),
    BALANCED("均衡", "5 秒 / 10 米，推荐日常使用", 5_000, 10f, 60f),
    SAVING("省电", "15 秒 / 30 米，适合长途出行", 15_000, 30f, 120f);
}

object Prefs {
    private lateinit var sp: SharedPreferences

    private val _recordMode = MutableStateFlow(RecordMode.BALANCED)
    private val _mapStyle = MutableStateFlow("amap")
    private val _fogEnabled = MutableStateFlow(true)
    private val _fogAlpha = MutableStateFlow(0.8f)
    private val _recording = MutableStateFlow(false)

    val recordMode: StateFlow<RecordMode> = _recordMode
    val mapStyle: StateFlow<String> = _mapStyle
    val fogEnabled: StateFlow<Boolean> = _fogEnabled
    val fogAlpha: StateFlow<Float> = _fogAlpha
    /** 用户是否希望处于记录状态（用于进程被杀后自动恢复）。 */
    val recording: StateFlow<Boolean> = _recording

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _recordMode.value = RecordMode.entries.getOrElse(sp.getInt("record_mode", 1)) { RecordMode.BALANCED }
        _mapStyle.value = sp.getString("map_style", "amap") ?: "amap"
        _fogEnabled.value = sp.getBoolean("fog", true)
        _fogAlpha.value = sp.getFloat("fog_alpha", 0.8f)
        _recording.value = sp.getBoolean("recording", false)
    }

    fun setRecordMode(m: RecordMode) { _recordMode.value = m; sp.edit().putInt("record_mode", m.ordinal).apply() }
    fun setMapStyle(id: String) { _mapStyle.value = id; sp.edit().putString("map_style", id).apply() }
    fun setFogEnabled(v: Boolean) { _fogEnabled.value = v; sp.edit().putBoolean("fog", v).apply() }
    fun setFogAlpha(v: Float) { _fogAlpha.value = v; sp.edit().putFloat("fog_alpha", v).apply() }
    fun setRecording(v: Boolean) { _recording.value = v; sp.edit().putBoolean("recording", v).apply() }

    fun saveCamera(lat: Double, lon: Double, zoom: Double) {
        sp.edit().putString("cam", "$lat,$lon,$zoom").apply()
    }

    fun camera(): DoubleArray? = sp.getString("cam", null)?.split(",")
        ?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 3 }?.toDoubleArray()
}
