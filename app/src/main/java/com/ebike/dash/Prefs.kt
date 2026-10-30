package com.ebike.dash

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ridedash", Context.MODE_PRIVATE)

    /** 超速提醒阈值（km/h）。国标 GB17761 电动自行车最高 25 km/h */
    var speedLimit: Int
        get() = sp.getInt("limit", 25)
        set(v) = sp.edit().putInt("limit", v).apply()

    var gaugeMax: Int
        get() = sp.getInt("gaugeMax", 60)
        set(v) = sp.edit().putInt("gaugeMax", v).apply()

    var imperial: Boolean
        get() = sp.getBoolean("imperial", false)
        set(v) = sp.edit().putBoolean("imperial", v).apply()

    /** 0 = 自动（按时间）, 1 = 日间, 2 = 夜间 */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var voice: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) = sp.edit().putBoolean("voice", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

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
