package com.ebike.dash

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6371000.0

    /** 两点间球面距离（米） */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 方位角，0 = 正北，顺时针，0..360 */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    /** 沿方位角前进 meters 米后的坐标 */
    fun offset(lat: Double, lon: Double, bearingDeg: Double, meters: Double): DoubleArray {
        val d = meters / R
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = Math.asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return doubleArrayOf(Math.toDegrees(p2), Math.toDegrees(l2))
    }

    /**
     * 点 P 投影到线段 AB（局部平面近似）。
     * 返回 [到线段的距离(米), 线段上的比例 t(0..1)]
     */
    fun projectToSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double,
    ): DoubleArray {
        val k = Math.toRadians(1.0) * R
        val c = cos(Math.toRadians(pLat))
        val ax = (aLon - pLon) * k * c
        val ay = (aLat - pLat) * k
        val bx = (bLon - pLon) * k * c
        val by = (bLat - pLat) * k
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0.0) 0.0 else -(ax * dx + ay * dy) / len2
        t = t.coerceIn(0.0, 1.0)
        val qx = ax + t * dx
        val qy = ay + t * dy
        return doubleArrayOf(sqrt(qx * qx + qy * qy), t)
    }

    fun compass(bearing: Float): String {
        val names = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        return names[(((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
