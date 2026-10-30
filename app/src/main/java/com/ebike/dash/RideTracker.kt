package com.ebike.dash

import android.location.Location

/** 骑行数据统计：本次里程、骑行时间、均速、最高速、总里程 */
class RideTracker {
    var tripMeters = 0.0; private set
    var movingMs = 0L; private set
    var maxKmh = 0.0; private set
    var odoMeters = 0.0; private set

    private var last: Location? = null

    val avgKmh: Double get() = if (movingMs < 1000) 0.0 else tripMeters / (movingMs / 1000.0) * 3.6

    fun update(loc: Location, speedKmh: Double) {
        val prev = last
        if (loc.accuracy > 35f) return
        last = loc
        if (prev == null) return
        val dt = loc.time - prev.time
        if (dt <= 0 || dt > 10_000) return
        if (speedKmh < 2.0) return // 静止时 GPS 漂移不计入
        val d = Geo.distance(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
        if (d > 80) return // 异常跳点
        tripMeters += d
        odoMeters += d
        movingMs += dt
        if (speedKmh > maxKmh) maxKmh = speedKmh
    }

    fun resetTrip() {
        tripMeters = 0.0
        movingMs = 0
        maxKmh = 0.0
    }

    fun save(p: Prefs) {
        p.putDouble("trip", tripMeters)
        p.putLong("moving", movingMs)
        p.putDouble("max", maxKmh)
        p.putDouble("odo", odoMeters)
    }

    fun restore(p: Prefs) {
        tripMeters = p.getDouble("trip")
        movingMs = p.getLong("moving")
        maxKmh = p.getDouble("max")
        odoMeters = p.getDouble("odo")
    }
}
