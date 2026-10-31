package com.ebike.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import kotlin.random.Random

class ExploreTest {
    private val home = doubleArrayOf(30.27, 120.16)

    private fun line(from: DoubleArray, bearing: Double, km: Double): List<DoubleArray> =
        (0..(km * 20).toInt()).map { Geo.offset(from[0], from[1], bearing, it * 50.0) }

    @Test
    fun litCityCountsCellsAlongRides() {
        // 向东骑 3 公里：约 20 个 150 米格子
        val r = LitCity.compute(listOf(line(home, 90.0, 3.0)), home)
        assertEquals(21.0, r.litCells.toDouble(), 3.0)
        assertTrue(r.percent > 0 && r.percent < 1)
        assertEquals(r.litCells * 0.0225, r.areaKm2, 0.001)
        assertEquals("杭州市", LitCity.cityName("浙江省杭州市西湖区", null))
        assertEquals("我的城市", LitCity.cityName(null, "Somewhere"))
    }

    @Test
    fun blindBoxAvoidsExploredDirection() {
        // 足迹全在东边，盲盒应当选在离足迹较远的地方
        val tracks = listOf(line(home, 90.0, 8.0))
        val p = BlindBox.pick(home, 6.0, tracks, Random(7))
        val d = Geo.distance(home[0], home[1], p[0], p[1])
        assertTrue(d in 4000.0..8000.0)
        assertTrue(BlindBox.distanceToTracks(p, tracks) > 2000)
    }

    @Test
    fun monthReportUsesSegmentDates() {
        val f = File.createTempFile("month", ".txt").apply { delete() }
        val t = TrackStore(f)
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 22, 30, 0) }
        var time = cal.timeInMillis
        line(home, 0.0, 2.0).forEach { t.add(it[0], it[1], time); time += 5000 }
        val late = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 20, 1, 10, 0) }.timeInMillis
        time = late
        line(home, 180.0, 1.0).forEach { t.add(it[0], it[1], time); time += 5000 }
        t.close()
        val s = MonthReport.compute(TrackStore(f).apply { load() }, 2026, 10, "晴天")
        assertEquals(2, s.rides)
        assertEquals(3000.0, s.meters, 60.0)
        assertEquals(2000.0, s.longestMeters, 40.0)
        assertEquals(70, s.latestMinute) // 凌晨 1:10 比晚上 22:30 更“晚”
        f.delete()
    }
}
