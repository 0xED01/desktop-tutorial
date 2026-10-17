package com.shiji.footprint.util

import com.shiji.footprint.data.TrackPoint
import java.io.BufferedReader
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 解析 CSV 轨迹。按表头自动识别列（中英文均可，兼容一生足迹等应用的导出格式）：
 *  - 纬度：latitude / lat / 纬度
 *  - 经度：longitude / lon / lng / long / 经度
 *  - 时间：dataTime / time / timestamp / date / datetime / 时间 / 日期（Unix 秒、毫秒或日期字符串）
 *  - 可选：altitude / ele / 海拔，accuracy / 精度，speed / 速度
 * 没有表头时按数值自动推断经纬度和时间列。
 * 时间相隔超过 30 分钟的点会拆成不同的轨迹段。
 */
object Csv {
    private val LAT = setOf("latitude", "lat", "纬度", "y")
    private val LON = setOf("longitude", "lon", "lng", "long", "经度", "x")
    private val TIME = setOf(
        "datatime", "time", "timestamp", "date", "datetime", "geotime", "loctime",
        "locationtime", "recordtime", "createtime", "时间", "日期", "时间戳", "定位时间"
    )
    private val ALT = setOf("altitude", "alt", "ele", "elevation", "海拔", "高度")
    private val ACC = setOf("accuracy", "acc", "horizontalaccuracy", "精度")
    private val SPEED = setOf("speed", "速度")

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy/MM/dd HH:mm:ss", "yyyy/M/d H:mm:ss",
        "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy/M/d H:mm", "yyyy年M月d日 HH:mm:ss",
    ).map { DateTimeFormatter.ofPattern(it) }

    private const val GAP_MS = 30 * 60_000L

    private class Cols(val lat: Int, val lon: Int, val time: Int, val alt: Int = -1, val acc: Int = -1, val speed: Int = -1)

    fun parse(input: InputStream): List<List<TrackPoint>> =
        parse(input.bufferedReader(Charsets.UTF_8))

    fun parse(reader: BufferedReader): List<List<TrackPoint>> {
        val lines = reader.lineSequence().map { it.removePrefix("﻿").trim() }.filter { it.isNotEmpty() }.iterator()
        if (!lines.hasNext()) return emptyList()
        val first = lines.next()
        val sep = listOf(',', '\t', ';', '|').maxBy { c -> first.count { it == c } }
        val head = split(first, sep)

        var cols = fromHeader(head)
        val points = ArrayList<TrackPoint>()
        var pending: List<String>? = null
        if (cols == null) {
            cols = guess(head) ?: return emptyList()
            pending = head
        }
        var seq = 0L
        fun add(f: List<String>) {
            val c = cols
            val lat = f.getOrNull(c.lat)?.toDoubleOrNull() ?: return
            val lon = f.getOrNull(c.lon)?.toDoubleOrNull() ?: return
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) return
            var t = if (c.time >= 0) parseTime(f.getOrNull(c.time)) else 0L
            if (t <= 0) t = System.currentTimeMillis() + seq++ * 1000 // 无时间：按顺序编号
            points.add(
                TrackPoint(
                    t = t, lat = lat, lon = lon,
                    acc = f.getOrNull(c.acc)?.toFloatOrNull()?.takeIf { it >= 0 } ?: 0f,
                    speed = f.getOrNull(c.speed)?.toFloatOrNull()?.takeIf { it >= 0 } ?: 0f,
                    alt = f.getOrNull(c.alt)?.toDoubleOrNull() ?: 0.0,
                )
            )
        }
        pending?.let { add(it) }
        while (lines.hasNext()) add(split(lines.next(), sep))

        points.sortBy { it.t }
        val segs = ArrayList<List<TrackPoint>>()
        var cur = ArrayList<TrackPoint>()
        for (p in points) {
            if (cur.isNotEmpty() && p.t - cur.last().t > GAP_MS) {
                segs.add(cur); cur = ArrayList()
            }
            cur.add(p)
        }
        if (cur.isNotEmpty()) segs.add(cur)
        return segs
    }

    private fun norm(s: String) = s.trim().lowercase().replace(" ", "").replace("_", "").replace("-", "")
        .substringBefore('(').substringBefore('（')

    private fun fromHeader(head: List<String>): Cols? {
        val n = head.map(::norm)
        fun idx(keys: Set<String>) = n.indexOfFirst { it in keys }
        val lat = idx(LAT)
        val lon = idx(LON)
        if (lat < 0 || lon < 0) return null
        return Cols(lat, lon, idx(TIME), idx(ALT), idx(ACC), idx(SPEED))
    }

    /** 无表头：时间列取像 Unix 时间戳的数，经纬度取范围合理的两列（中国经度 > 90，可区分）。 */
    private fun guess(row: List<String>): Cols? {
        val nums = row.map { it.toDoubleOrNull() }
        val time = nums.indexOfFirst { it != null && it > 1e9 }
            .takeIf { it >= 0 } ?: row.indexOfFirst { parseTime(it) > 0 && it.toDoubleOrNull() == null }
        val cand = nums.indices.filter { it != time && nums[it] != null && nums[it]!! in -180.0..180.0 && nums[it] != 0.0 }
        if (cand.size < 2) return null
        val a = cand[0]
        val b = cand[1]
        val (lat, lon) = if (kotlin.math.abs(nums[a]!!) > 90) b to a else a to b
        return Cols(lat, lon, time)
    }

    fun parseTime(raw: String?): Long {
        val s = raw?.trim()?.trim('"') ?: return 0L
        if (s.isEmpty()) return 0L
        s.toDoubleOrNull()?.let { v ->
            return when {
                v > 1e14 -> (v / 1000).toLong() // 微秒
                v > 1e11 -> v.toLong()          // 毫秒
                v > 1e8 -> (v * 1000).toLong()  // 秒
                else -> 0L
            }
        }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        for (f in DATE_FORMATS) {
            runCatching { return LocalDateTime.parse(s, f).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        }
        return 0L
    }

    /** 拆分一行，支持双引号包裹的字段。 */
    private fun split(line: String, sep: Char): List<String> {
        if ('"' !in line) return line.split(sep).map { it.trim() }
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { out.add(sb.toString().trim()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }
}
