package com.ebike.dash

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ridedash", Context.MODE_PRIVATE)

    /** 是否展开骑行数据面板 */
    var showInfo: Boolean
        get() = sp.getBoolean("showInfo", false)
        set(v) = sp.edit().putBoolean("showInfo", v).apply()

    var imperial: Boolean
        get() = sp.getBoolean("imperial", false)
        set(v) = sp.edit().putBoolean("imperial", v).apply()

    /** 0 = 自动（按时间）, 1 = 日间, 2 = 夜间 */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = sp.edit().putBoolean("labels", v).apply()

    /** 是否展开迷你播放器 */
    var showMusic: Boolean
        get() = sp.getBoolean("showMusic", false)
        set(v) = sp.edit().putBoolean("showMusic", v).apply()

    var askedMusicAccess: Boolean
        get() = sp.getBoolean("askedMusic", false)
        set(v) = sp.edit().putBoolean("askedMusic", v).apply()

    /** 地图上是否显示足迹 */
    var showTrack: Boolean
        get() = sp.getBoolean("showTrack", true)
        set(v) = sp.edit().putBoolean("showTrack", v).apply()

    var lastLat: Double
        get() = Double.fromBits(sp.getLong("lastLat", 39.9087.toRawBits()))
        set(v) = sp.edit().putLong("lastLat", v.toRawBits()).apply()

    var lastLon: Double
        get() = Double.fromBits(sp.getLong("lastLon", 116.3975.toRawBits()))
        set(v) = sp.edit().putLong("lastLon", v.toRawBits()).apply()

    fun getDouble(key: String) = Double.fromBits(sp.getLong(key, 0.0.toRawBits()))
    fun putDouble(key: String, v: Double) = sp.edit().putLong(key, v.toRawBits()).apply()
    fun getLong(key: String) = sp.getLong(key, 0L)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
}
