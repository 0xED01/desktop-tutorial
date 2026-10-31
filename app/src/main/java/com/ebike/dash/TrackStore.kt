package com.ebike.dash

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

/**
 * 足迹：把骑过的路按段记录下来。
 * 文件格式很简单：每行 “纬度,经度”，以 “S” 开头的一行表示新的一段，
 * 新版会写成 “S,开始时间(毫秒)”，旧文件里只有 “S” 的段没有时间。
 * file 为 null 时只存在内存里（演示模式用）。
 */
class TrackStore(private val file: File?) {
    /** 所有段，最后一段可能是正在记录的当前段 */
    val segments = ArrayList<MutableList<DoubleArray>>()
    /** 每段的开始时间（毫秒），0 表示未知（旧数据） */
    val segmentStart = java.util.IdentityHashMap<List<DoubleArray>, Long>()
    var totalMeters = 0.0; private set

    private var current: MutableList<DoubleArray>? = null
    private var lastTime = 0L
    private var writer: BufferedWriter? = null
    private var unflushed = 0

    val currentSegment: List<DoubleArray>? get() = current

    fun load() {
        segments.clear()
        segmentStart.clear()
        totalMeters = 0.0
        val f = file ?: return
        if (!f.exists()) return
        var seg: MutableList<DoubleArray>? = null
        f.forEachLine { line ->
            if (line == "S" || line.startsWith("S,")) {
                val t = line.substringAfter("S,", "0").toLongOrNull() ?: 0L
                seg = ArrayList<DoubleArray>().also { segments.add(it); segmentStart[it] = t }
                return@forEachLine
            }
            val c = line.indexOf(',')
            if (c <= 0) return@forEachLine
            val lat = line.substring(0, c).toDoubleOrNull() ?: return@forEachLine
            val lon = line.substring(c + 1).toDoubleOrNull() ?: return@forEachLine
            val s = seg ?: ArrayList<DoubleArray>().also { seg = it; segments.add(it) }
            s.lastOrNull()?.let { totalMeters += Geo.distance(it[0], it[1], lat, lon) }
            s.add(doubleArrayOf(lat, lon))
        }
        segments.filter { it.size < 2 }.forEach { segmentStart.remove(it) }
        segments.removeAll { it.size < 2 }
    }

    /** 一段的长度（米） */
    fun lengthOf(seg: List<DoubleArray>): Double {
        var d = 0.0
        for (i in 1 until seg.size) d += Geo.distance(seg[i - 1][0], seg[i - 1][1], seg[i][0], seg[i][1])
        return d
    }

    /**
     * 记录一个点。距离上个点不足 MIN_STEP 米不记；
     * 停下超过 1 分钟或跳点过远时另起一段。
     * 返回 true 表示开始了新的一段（需要重绘历史足迹）。
     */
    fun add(lat: Double, lon: Double, timeMs: Long): Boolean {
        val cur = current
        val last = cur?.lastOrNull()
        val d = if (last != null) Geo.distance(last[0], last[1], lat, lon) else 0.0
        var started = false
        if (cur == null || last == null || timeMs - lastTime > GAP_MS || d > JUMP_M) {
            current = ArrayList<DoubleArray>().also { segments.add(it); segmentStart[it] = timeMs }
            write("S,$timeMs")
            started = true
        } else {
            if (d < MIN_STEP) return false
            totalMeters += d
        }
        current!!.add(doubleArrayOf(lat, lon))
        lastTime = timeMs
        write(String.format(Locale.US, "%.6f,%.6f", lat, lon))
        return started
    }

    /** 结束当前段（例如停车、退出） */
    fun endSegment() {
        current?.let { if (it.size < 2) { segments.remove(it); segmentStart.remove(it) } }
        current = null
        flush()
    }

    fun clear() {
        close()
        segments.clear()
        segmentStart.clear()
        current = null
        totalMeters = 0.0
        file?.delete()
    }

    fun flush() {
        writer?.flush()
        unflushed = 0
    }

    fun close() {
        flush()
        writer?.close()
        writer = null
    }

    private fun write(line: String) {
        val f = file ?: return
        val w = writer ?: BufferedWriter(FileWriter(f, true)).also { writer = it }
        w.write(line)
        w.newLine()
        if (++unflushed >= 20) flush()
    }

    companion object {
        const val MIN_STEP = 8.0
        const val JUMP_M = 300.0
        const val GAP_MS = 60_000L
    }
}
