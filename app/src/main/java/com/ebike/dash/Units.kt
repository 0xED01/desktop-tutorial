package com.ebike.dash

import java.util.Locale

/** 统一处理公制 / 英制显示 */
class Units(var imperial: Boolean = false) {
    val speedUnit get() = if (imperial) "mph" else "km/h"
    val distUnit get() = if (imperial) "mi" else "km"

    fun speed(kmh: Double): Double = if (imperial) kmh * 0.621371 else kmh

    /** 公里/英里数值（用于里程） */
    fun bigDist(meters: Double): Double = if (imperial) meters / 1609.344 else meters / 1000.0

    /** 导航用的友好距离："350 米" / "1.2 公里" */
    fun navDist(meters: Double): String {
        if (imperial) {
            val ft = meters * 3.28084
            return if (ft < 1000) "${(ft / 10).toInt() * 10} ft" else String.format(Locale.US, "%.1f mi", meters / 1609.344)
        }
        return when {
            meters < 10 -> "现在"
            meters < 1000 -> "${(meters / 10).toInt() * 10} 米"
            meters < 10000 -> String.format(Locale.US, "%.1f 公里", meters / 1000)
            else -> "${(meters / 1000).toInt()} 公里"
        }
    }

    /** 语音播报用的距离 */
    fun spokenDist(meters: Double): String = when {
        meters < 1000 -> "${((meters / 10).toInt() * 10).coerceAtLeast(10)}米"
        else -> String.format(Locale.US, "%.1f公里", meters / 1000)
    }
}
