package com.shiji.footprint

import com.shiji.footprint.geo.CellIndex
import com.shiji.footprint.geo.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun distanceBeijingShanghai() {
        val d = Geo.distance(39.9042, 116.4074, 31.2304, 121.4737)
        assertEquals(1_067_000.0, d, 5_000.0)
    }

    @Test
    fun gcjOffsetInsideChinaOnly() {
        val g = Geo.wgsToGcj(39.9042, 116.4074)
        val shift = Geo.distance(39.9042, 116.4074, g[0], g[1])
        assertTrue(shift in 100.0..800.0)
        val tokyo = Geo.wgsToGcj(35.68, 139.76)
        assertEquals(35.68, tokyo[0], 0.0)
    }

    @Test
    fun mercatorRoundTrip() {
        val y = Geo.latToY(30.25, 19)
        assertEquals(30.25, Geo.yToLat(y, 19), 1e-9)
        val x = Geo.lonToX(120.15, 19)
        assertEquals(120.15, Geo.xToLon(x, 19), 1e-9)
    }

    @Test
    fun cellIndexAggregatesLevels() {
        val idx = CellIndex()
        val k = Geo.cellOf(30.25, 120.15)
        assertTrue(idx.add(k))
        assertFalse(idx.add(k))
        assertEquals(1, idx.size)
        assertEquals(1, idx.cells(10).size)
        assertTrue(idx.areaKm2 in 0.002..0.006)
    }
}
