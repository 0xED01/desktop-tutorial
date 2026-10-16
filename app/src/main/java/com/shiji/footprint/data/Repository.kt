package com.shiji.footprint.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import com.shiji.footprint.geo.CellIndex
import com.shiji.footprint.geo.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * 足迹数据仓库。所有写操作都在单独的 IO 线程顺序执行；读操作可在任意后台线程调用。
 */
object Repository {
    private lateinit var helper: Db
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val io = Executors.newSingleThreadExecutor { Thread(it, "footprint-io") }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var resolver: PlaceResolver

    val cells = CellIndex()
    val stats = MutableStateFlow(Stats())
    val places = MutableStateFlow<List<Place>>(emptyList())
    val today = MutableStateFlow<List<TrackPoint>>(emptyList())
    val lastLocation = MutableStateFlow<TrackPoint?>(null)
    /** 数据有变化时递增，用于触发地图重绘与界面刷新。 */
    val version = MutableStateFlow(0L)
    val ready = MutableStateFlow(false)

    private var last: TrackPoint? = null
    private var totalMeters = 0.0
    private var pointCount = 0L
    private var segment = System.currentTimeMillis()
    private var rejected = 0

    fun init(ctx: Context) {
        helper = Db(ctx.applicationContext)
        resolver = PlaceResolver(ctx.applicationContext) { key, place ->
            io.execute { savePlace(key, place) }
        }
        io.execute { load() }
    }

    private fun load() {
        db.rawQuery("SELECT k FROM cells", null).use { c ->
            while (c.moveToNext()) cells.add(c.getLong(0))
        }
        totalMeters = getMeta("total_m")?.toDoubleOrNull() ?: 0.0
        pointCount = DatabaseUtils.queryNumEntries(db, "points")
        last = db.rawQuery("SELECT $COLS FROM points ORDER BY t DESC LIMIT 1", null).use {
            if (it.moveToFirst()) readPoint(it) else null
        }
        lastLocation.value = last
        val keys = ArrayList<Long>()
        db.rawQuery("SELECT k FROM geokeys", null).use { c -> while (c.moveToNext()) keys.add(c.getLong(0)) }
        resolver.markDone(keys)
        reloadToday()
        refreshPlaces()
        refreshStats()
        ready.value = true
        bump()
    }

    // ---------------- 写入 ----------------

    fun newSegment() = io.execute { segment = System.currentTimeMillis() }

    /** 实时记录的新定位点。 */
    fun addPoint(raw: TrackPoint) = io.execute {
        val p = if (raw.seg == 0L) raw.copy(seg = segment) else raw
        val prev = last
        if (prev != null) {
            if (p.t <= prev.t) return@execute
            if (prev.seg == p.seg) {
                val dt = (p.t - prev.t) / 1000.0
                val d = Geo.distance(prev.lat, prev.lon, p.lat, p.lon)
                // 过滤 GPS 漂移跳点（连续多次则认为是真实位置变化）
                if (dt < 120 && d / dt > 90 && rejected < 3) {
                    rejected++
                    return@execute
                }
            }
        }
        rejected = 0
        db.beginTransaction()
        try {
            if (!store(p, prev)) return@execute
            putMeta("total_m", totalMeters.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        last = p
        lastLocation.value = p
        val day = dayOf(p.t)
        val list = today.value
        today.value = if (list.isNotEmpty() && dayOf(list.first().t) == day) list + p
        else if (day == LocalDate.now()) listOf(p) else emptyList()
        resolver.request(p.lat, p.lon)
        refreshStats()
        bump()
    }

    /** 存储一个点并更新网格、里程、每日统计。调用方负责事务。 */
    private fun store(p: TrackPoint, prev: TrackPoint?): Boolean {
        val cv = ContentValues(7).apply {
            put("t", p.t); put("lat", p.lat); put("lon", p.lon)
            put("acc", p.acc); put("spd", p.speed); put("alt", p.alt); put("seg", p.seg)
        }
        if (db.insertWithOnConflict("points", null, cv, SQLiteDatabase.CONFLICT_IGNORE) == -1L) return false
        pointCount++
        var dist = 0.0
        if (prev != null && prev.seg == p.seg && p.t > prev.t && p.t - prev.t < 30 * 60_000) {
            val d = Geo.distance(prev.lat, prev.lon, p.lat, p.lon)
            if (d < 20_000) {
                dist = d
                markLine(prev, p, d)
            } else markCell(p.lat, p.lon)
        } else markCell(p.lat, p.lon)
        totalMeters += dist
        updateDay(p, dist)
        return true
    }

    private fun markLine(a: TrackPoint, b: TrackPoint, d: Double) {
        val steps = ceil(d / 25.0).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val f = i.toDouble() / steps
            markCell(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        }
    }

    private fun markCell(lat: Double, lon: Double) {
        val k = Geo.cellOf(lat, lon)
        if (cells.add(k)) {
            db.execSQL("INSERT OR IGNORE INTO cells(k) VALUES(?)", arrayOf<Any>(k))
        }
    }

    private fun updateDay(p: TrackPoint, dist: Double) {
        val d = dayOf(p.t).toString()
        val st = db.compileStatement(
            "UPDATE days SET dist=dist+?, n=n+1, first=min(first,?), last=max(last,?) WHERE d=?"
        )
        st.bindDouble(1, dist); st.bindLong(2, p.t); st.bindLong(3, p.t); st.bindString(4, d)
        if (st.executeUpdateDelete() == 0) {
            db.execSQL("INSERT INTO days(d,dist,n,first,last) VALUES(?,?,1,?,?)", arrayOf<Any>(d, dist, p.t, p.t))
        }
        st.close()
    }

    private fun savePlace(key: Long, place: Place) {
        db.execSQL("INSERT OR IGNORE INTO geokeys(k) VALUES(?)", arrayOf<Any>(key))
        db.execSQL(
            "INSERT OR IGNORE INTO places(country,province,city,t) VALUES(?,?,?,?)",
            arrayOf<Any>(place.country, place.province, place.city, System.currentTimeMillis())
        )
        refreshPlaces()
        refreshStats()
        bump()
    }

    /** 导入轨迹（如 GPX）。每个子列表是一段连续轨迹。 */
    fun importSegments(segments: List<List<TrackPoint>>, done: (Int) -> Unit) = io.execute {
        var added = 0
        val geo = LinkedHashMap<Long, TrackPoint>()
        db.beginTransaction()
        try {
            segments.forEachIndexed { i, seg ->
                val segId = -(System.currentTimeMillis() * 1000 + i)
                var prev: TrackPoint? = null
                for (raw in seg.sortedBy { it.t }) {
                    val p = raw.copy(seg = segId)
                    if (store(p, prev)) {
                        added++
                        prev = p
                        geo.putIfAbsent(resolver.geoKey(p.lat, p.lon), p)
                    }
                }
            }
            putMeta("total_m", totalMeters.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        geo.values.forEach { resolver.request(it.lat, it.lon) }
        last = db.rawQuery("SELECT $COLS FROM points ORDER BY t DESC LIMIT 1", null).use {
            if (it.moveToFirst()) readPoint(it) else null
        }
        if (lastLocation.value == null) lastLocation.value = last
        reloadToday()
        refreshStats()
        bump()
        main.post { done(added) }
    }

    fun clearAll(done: () -> Unit) = io.execute {
        listOf("points", "cells", "days", "places", "geokeys", "meta").forEach { db.delete(it, null, null) }
        cells.clear()
        resolver.reset()
        totalMeters = 0.0
        pointCount = 0
        last = null
        lastLocation.value = null
        today.value = emptyList()
        places.value = emptyList()
        refreshStats()
        bump()
        main.post(done)
    }

    // ---------------- 读取 ----------------

    fun pointsBetween(start: Long, end: Long): List<TrackPoint> =
        db.rawQuery(
            "SELECT $COLS FROM points WHERE t>=? AND t<? ORDER BY t",
            arrayOf(start.toString(), end.toString())
        ).use { c ->
            val list = ArrayList<TrackPoint>(c.count)
            while (c.moveToNext()) list.add(readPoint(c))
            list
        }

    fun pointsOfDay(date: LocalDate): List<TrackPoint> {
        val zone = ZoneId.systemDefault()
        return pointsBetween(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    fun monthDays(month: YearMonth): Map<LocalDate, DayStat> =
        queryDays("SELECT d,dist,n,first,last FROM days WHERE d LIKE ?", arrayOf("$month-%"))
            .associateBy { it.date }

    fun recentDays(n: Int): Map<LocalDate, DayStat> {
        val from = LocalDate.now().minusDays(n - 1L).toString()
        return queryDays("SELECT d,dist,n,first,last FROM days WHERE d>=?", arrayOf(from)).associateBy { it.date }
    }

    private fun queryDays(sql: String, args: Array<String>): List<DayStat> =
        db.rawQuery(sql, args).use { c ->
            val list = ArrayList<DayStat>()
            while (c.moveToNext()) {
                list.add(
                    DayStat(LocalDate.parse(c.getString(0)), c.getDouble(1) / 1000.0, c.getInt(2), c.getLong(3), c.getLong(4))
                )
            }
            list
        }

    /** 导出全部轨迹为 GPX 1.1。 */
    fun exportGpx(out: OutputStream): Int {
        val w = out.bufferedWriter()
        val fmt = DateTimeFormatter.ISO_INSTANT
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        w.write("<gpx version=\"1.1\" creator=\"拾迹 Footprint\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n<trk><name>拾迹足迹</name>\n")
        var n = 0
        var seg: Long? = null
        db.rawQuery("SELECT $COLS FROM points ORDER BY t", null).use { c ->
            while (c.moveToNext()) {
                val p = readPoint(c)
                if (p.seg != seg) {
                    if (seg != null) w.write("</trkseg>\n")
                    w.write("<trkseg>\n")
                    seg = p.seg
                }
                w.write("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><ele>${"%.1f".format(java.util.Locale.US, p.alt)}</ele><time>${fmt.format(Instant.ofEpochMilli(p.t))}</time></trkpt>\n")
                n++
            }
        }
        if (seg != null) w.write("</trkseg>\n")
        w.write("</trk>\n</gpx>\n")
        w.flush()
        return n
    }

    // ---------------- 内部工具 ----------------

    private fun reloadToday() {
        today.value = pointsOfDay(LocalDate.now())
    }

    private fun refreshPlaces() {
        places.value = db.rawQuery("SELECT country,province,city,t FROM places ORDER BY t", null).use { c ->
            val list = ArrayList<Place>()
            while (c.moveToNext()) list.add(Place(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            list
        }
    }

    private fun refreshStats() {
        val pl = places.value
        val todayM = db.rawQuery("SELECT dist FROM days WHERE d=?", arrayOf(LocalDate.now().toString())).use {
            if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
        stats.value = Stats(
            totalKm = totalMeters / 1000.0,
            todayKm = todayM / 1000.0,
            areaKm2 = cells.areaKm2,
            cells = cells.size,
            days = DatabaseUtils.queryNumEntries(db, "days").toInt(),
            points = pointCount,
            countries = pl.map { it.country }.distinct().size,
            provinces = pl.map { it.country + "|" + it.province }.distinct().size,
            cities = pl.map { it.country + "|" + it.province + "|" + it.city }.distinct().size,
        )
    }

    private fun bump() {
        version.value = version.value + 1
    }

    private fun getMeta(k: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putMeta(k: String, v: String) {
        db.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)", arrayOf<Any>(k, v))
    }

    private const val COLS = "t,lat,lon,acc,spd,alt,seg"

    private fun readPoint(c: Cursor) = TrackPoint(
        t = c.getLong(0), lat = c.getDouble(1), lon = c.getDouble(2),
        acc = c.getFloat(3), speed = c.getFloat(4), alt = c.getDouble(5), seg = c.getLong(6),
    )

    fun dayOf(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
}
