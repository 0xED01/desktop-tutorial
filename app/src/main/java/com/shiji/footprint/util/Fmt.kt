package com.shiji.footprint.util

import java.util.Locale

object Fmt {
    fun km(v: Double): String = when {
        v < 10 -> String.format(Locale.US, "%.2f", v)
        v < 1000 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%,.0f", v)
    }

    fun area(v: Double): String = when {
        v < 1 -> String.format(Locale.US, "%.3f", v)
        v < 100 -> String.format(Locale.US, "%.2f", v)
        else -> String.format(Locale.US, "%,.1f", v)
    }

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h${m}m" else "${m}min"
    }

    fun clock(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    fun count(v: Long): String = String.format(Locale.US, "%,d", v)
}
