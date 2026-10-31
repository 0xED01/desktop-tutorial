package com.ebike.dash

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.random.Random

/** 点亮城市：把足迹经过的地方按 150 米小格子“点亮”，统计城市被点亮的比例 */
object LitCity {
    const val CELL_M = 150.0
    const val RADIUS_KM = 15.0

    class Result(val centerLat: Double, val centerLon: Double, val litCells: Int, val percent: Double, val areaKm2: Double)

    /** 城市中心：设置了“家”就用家，否则用所有足迹的中心 */
    fun center(segments: List<List<DoubleArray>>, home: DoubleArray?): DoubleArray? {
        if (home != null) return home
        var n = 0; var la = 0.0; var lo = 0.0
        for (s in segments) for (p in s) { la += p[0]; lo += p[1]; n++ }
        return if (n == 0) null else doubleArrayOf(la / n, lo / n)
    }

    fun compute(segments: List<List<DoubleArray>>, center: DoubleArray): Result {
        val acc = Accumulator(center)
        for (seg in segments) {
            acc.newSegment()
            for (p in seg) acc.add(p)
        }
        return acc.result()
    }

    /** 逐点累加（延时回放时，城市一点点被点亮） */
    class Accumulator(private val center: DoubleArray) {
        private val dLat = CELL_M / 111_320.0
        private val dLon = CELL_M / (111_320.0 * cos(Math.toRadians(center[0])))
        private val cells = HashSet<Long>()
        private var prev: DoubleArray? = null
        private val total = PI * (RADIUS_KM * 1000) * (RADIUS_KM * 1000) / (CELL_M * CELL_M)

        fun newSegment() { prev = null }

        fun add(p: DoubleArray) {
            mark(p[0], p[1])
            // 两点之间每 50 米补一个点，避免高速时漏掉格子
            prev?.let { q ->
                val d = Geo.distance(q[0], q[1], p[0], p[1])
                val steps = (d / 50).toInt()
                for (k in 1 until steps) {
                    val f = k.toDouble() / steps
                    mark(q[0] + (p[0] - q[0]) * f, q[1] + (p[1] - q[1]) * f)
                }
            }
            prev = p
        }

        fun result() = Result(center[0], center[1], cells.size, cells.size / total * 100, cells.size * CELL_M * CELL_M / 1e6)

        private fun mark(lat: Double, lon: Double) {
            if (Geo.distance(lat, lon, center[0], center[1]) > RADIUS_KM * 1000) return
            val y = floor(lat / dLat).toLong()
            val x = floor(lon / dLon).toLong()
            cells.add((y shl 32) xor (x and 0xffffffffL))
        }
    }

    /** 从地址里找出城市名，例如“杭州市” */
    fun cityName(address: String?, name: String?): String {
        val text = listOfNotNull(address, name).joinToString(" ")
        // 每个“市”往前取 2~4 个汉字，遇到省 / 区 / 县 / 市等就停
        val stops = "省区县市盟旗 ,，"
        var found: String? = null
        for (i in text.indices) {
            if (text[i] != '市') continue
            var j = i
            while (j > 0 && i - j < 4 && text[j - 1] !in stops && text[j - 1].code in 0x4E00..0x9FA5) j--
            if (i - j >= 2) found = text.substring(j, i + 1)
        }
        return found ?: "我的城市"
    }
}

/** 骑向未知：在一定距离上，挑一个离你所有足迹最远（没去过）的地方 */
object BlindBox {
    fun pick(here: DoubleArray, distanceKm: Double, segments: List<List<DoubleArray>>, rnd: Random = Random.Default): DoubleArray {
        val pts = segments.flatMap { s -> s.filterIndexed { i, _ -> i % 5 == 0 } }
        val candidates = (0 until 36).map {
            val bearing = rnd.nextDouble(0.0, 360.0)
            val dist = distanceKm * 1000 * rnd.nextDouble(0.75, 1.25)
            Geo.offset(here[0], here[1], bearing, dist)
        }
        if (pts.isEmpty()) return candidates.first()
        val scored = candidates.map { c ->
            var best = Double.MAX_VALUE
            for (p in pts) {
                val d = Geo.distance(c[0], c[1], p[0], p[1])
                if (d < best) best = d
            }
            c to best
        }.sortedByDescending { it.second }
        // 在“最陌生”的前三个里随机挑一个，每次结果都不一样
        return scored[rnd.nextInt(minOf(3, scored.size))].first
    }

    /** 离最近足迹的距离（米），用于判断“你从没去过” */
    fun distanceToTracks(p: DoubleArray, segments: List<List<DoubleArray>>): Double {
        var best = Double.MAX_VALUE
        for (s in segments) for (q in s) {
            val d = Geo.distance(p[0], p[1], q[0], q[1])
            if (d < best) best = d
        }
        return best
    }
}

/** 月度报告：某个月的骑行统计 */
object MonthReport {
    class Stats(
        val year: Int,
        val month: Int,
        val meters: Double,
        val rides: Int,
        val longestMeters: Double,
        /** 最晚出发时刻（分钟数，从 0 点算；凌晨 0~5 点算作“很晚”），-1 = 无 */
        val latestMinute: Int,
        val segments: List<List<DoubleArray>>,
        val topSong: String?,
    )

    fun monthKey(year: Int, month: Int) = String.format(java.util.Locale.US, "%04d%02d", year, month)

    fun compute(track: TrackStore, year: Int, month: Int, topSong: String?): Stats {
        val cal = Calendar.getInstance()
        val segs = track.segments.filter { s ->
            val t = track.segmentStart[s] ?: 0L
            if (t <= 0L) return@filter false
            cal.timeInMillis = t
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) + 1 == month
        }
        var total = 0.0; var longest = 0.0; var rides = 0; var latest = -1
        for (s in segs) {
            val len = track.lengthOf(s)
            total += len
            if (len > longest) longest = len
            if (len >= 300) rides++
            cal.timeInMillis = track.segmentStart[s] ?: continue
            // 让凌晨（0~5 点）排在深夜之后：按“从早上 6 点开始的分钟数”比较
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val shifted = (m - 360 + 1440) % 1440
            val curShifted = if (latest < 0) -1 else (latest - 360 + 1440) % 1440
            if (shifted > curShifted) latest = m
        }
        return Stats(year, month, total, rides, longest, latest, segs, topSong)
    }
}
