package com.ebike.dash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    /** 导航语音播报 */
    var voice: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) = sp.edit().putBoolean("voice", v).apply()

    /** 选择的播报声音（空 = 自动，优先台湾口音） */
    var voiceName: String?
        get() = sp.getString("voiceName", null)
        set(v) = sp.edit().putString("voiceName", v).apply()

    /** 播报音调（“甜度”），1.0 为原声 */
    var voicePitch: Float
        get() = sp.getFloat("voicePitch", 1.2f)
        set(v) = sp.edit().putFloat("voicePitch", v).apply()

    /** 是否展开迷你播放器 */
    var showMusic: Boolean
        get() = sp.getBoolean("showMusic", false)
        set(v) = sp.edit().putBoolean("showMusic", v).apply()

    var askedMusicAccess: Boolean
        get() = sp.getBoolean("askedMusic", false)
        set(v) = sp.edit().putBoolean("askedMusic", v).apply()

    /** 锁屏 / 切到其它 App 时是否继续记录 */
    var backgroundRecord: Boolean
        get() = sp.getBoolean("bgRecord", true)
        set(v) = sp.edit().putBoolean("bgRecord", v).apply()

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

    // ---------------------------------------------------------------- 地点：最近搜索 / 家 / 公司

    fun getPlaces(key: String): List<Place> {
        val arr = try { JSONArray(sp.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(o.getString("n"), o.optString("a"), o.getDouble("lat"), o.getDouble("lon"), o.optString("k"))
        }
    }

    private fun putPlaces(key: String, list: List<Place>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("n", it.name).put("a", it.address).put("lat", it.lat).put("lon", it.lon).put("k", it.kind))
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    fun getPlace(key: String): Place? = getPlaces(key).firstOrNull()
    fun putPlace(key: String, p: Place?) = putPlaces(key, listOfNotNull(p))

    private fun same(a: Place, b: Place) = a.name == b.name && Geo.distance(a.lat, a.lon, b.lat, b.lon) < 100

    fun addHistory(p: Place) {
        putPlaces(HISTORY, (listOf(p) + getPlaces(HISTORY).filterNot { same(it, p) }).take(10))
    }

    fun removeHistory(p: Place) = putPlaces(HISTORY, getPlaces(HISTORY).filterNot { same(it, p) })

    companion object {
        const val HISTORY = "history"
        const val HOME = "home"
        const val WORK = "work"
    }
}
