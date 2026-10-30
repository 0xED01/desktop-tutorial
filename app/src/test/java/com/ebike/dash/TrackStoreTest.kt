package com.ebike.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackStoreTest {
    private fun step(p: DoubleArray, m: Double) = Geo.offset(p[0], p[1], 0.0, m)

    @Test
    fun recordsSegmentsAndReloads() {
        val f = File.createTempFile("track", ".txt").apply { delete() }
        val t = TrackStore(f)
        var p = doubleArrayOf(30.0, 120.0)
        assertTrue(t.add(p[0], p[1], 0))           // 第一段开始
        p = step(p, 3.0)
        assertFalse(t.add(p[0], p[1], 1000))       // 太近，不记录
        for (i in 1..10) {
            p = step(p, 20.0)
            assertFalse(t.add(p[0], p[1], 1000L + i * 2000))
        }
        assertEquals(1, t.segments.size)
        // 停了 2 分钟后再出发，另起一段
        p = step(p, 20.0)
        assertTrue(t.add(p[0], p[1], 200_000))
        p = step(p, 20.0)
        t.add(p[0], p[1], 202_000)
        t.close()
        assertEquals(2, t.segments.size)

        val again = TrackStore(f).apply { load() }
        assertEquals(2, again.segments.size)
        assertEquals(t.totalMeters, again.totalMeters, 1.0)
        assertEquals(223.0, again.totalMeters, 3.0)
        f.delete()
    }

    @Test
    fun geoAndUnits() {
        val e = Geo.offset(39.9, 116.4, 90.0, 1000.0)
        assertEquals(1000.0, Geo.distance(39.9, 116.4, e[0], e[1]), 1.0)
        assertEquals("东北", Geo.compass(44f))
        assertEquals("mph", Units(true).speedUnit)
    }
}
