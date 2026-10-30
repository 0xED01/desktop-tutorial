package com.ebike.dash


/** 统一处理公制 / 英制显示 */
class Units(var imperial: Boolean = false) {
    val speedUnit get() = if (imperial) "mph" else "km/h"
    val distUnit get() = if (imperial) "mi" else "km"

    fun speed(kmh: Double): Double = if (imperial) kmh * 0.621371 else kmh

    /** 公里/英里数值（用于里程） */
    fun bigDist(meters: Double): Double = if (imperial) meters / 1609.344 else meters / 1000.0
}
