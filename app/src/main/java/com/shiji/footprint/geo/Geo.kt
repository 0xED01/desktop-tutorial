package com.shiji.footprint.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** 地理计算：距离、Web 墨卡托网格、WGS84 → GCJ-02 坐标转换。 */
object Geo {
    /** 探索网格的最细层级（墨卡托瓦片 z=19，中纬度约 60 米见方）。 */
    const val CELL_LEVEL = 19
    const val MIN_LEVEL = 4
    private const val EARTH_CIRC_KM = 40075.016686
    private const val MAX_LAT = 85.05112878

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val s1 = sin(dLat / 2)
        val s2 = sin(dLon / 2)
        val a = s1 * s1 + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * s2 * s2
        return 2 * 6371008.8 * asin(sqrt(min(1.0, a)))
    }

    fun lonToX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)

    fun latToY(lat: Double, z: Int): Double {
        val l = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(l) + 1.0 / cos(l)) / PI) / 2.0 * (1 shl z)
    }

    fun xToLon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0

    fun yToLat(y: Double, z: Int): Double = Math.toDegrees(atan(sinh(PI - 2.0 * PI * y / (1 shl z))))

    fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    fun keyX(k: Long): Int = (k ushr 32).toInt()
    fun keyY(k: Long): Int = k.toInt()

    fun cellOf(lat: Double, lon: Double, z: Int = CELL_LEVEL): Long {
        val max = (1 shl z) - 1
        val x = floor(lonToX(lon, z)).toInt().coerceIn(0, max)
        val y = floor(latToY(lat, z)).toInt().coerceIn(0, max)
        return key(x, y)
    }

    fun cellAreaKm2(y: Int, z: Int = CELL_LEVEL): Double {
        val lat = yToLat(y + 0.5, z)
        val s = EARTH_CIRC_KM * cos(Math.toRadians(lat)) / (1 shl z)
        return s * s
    }

    // ---------- GCJ-02（高德/腾讯地图坐标系） ----------
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun outOfChina(lat: Double, lon: Double) =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgsToGcj(lat: Double, lon: Double): DoubleArray {
        if (outOfChina(lat, lon)) return doubleArrayOf(lat, lon)
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return doubleArrayOf(lat + dLat, lon + dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        r += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return r
    }

    private fun transformLon(x: Double, y: Double): Double {
        var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        r += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return r
    }
}
