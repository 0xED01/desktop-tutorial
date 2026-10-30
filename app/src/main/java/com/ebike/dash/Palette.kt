package com.ebike.dash

import android.graphics.Color

/** 浅色 / 深色两套配色，界面只用黑白灰，红色只用于超速 */
class Palette(
    val surface: Int,
    val onSurface: Int,
    val dim: Int,
    val chip: Int,
    /** 足迹线颜色 */
    val trackColor: String,
    val mapStyle: String,
) {
    val danger = Color.parseColor("#E5484D")

    companion object {
        val LIGHT = Palette(
            surface = Color.WHITE,
            onSurface = Color.parseColor("#0D0D0D"),
            dim = Color.parseColor("#8E8E8E"),
            chip = Color.parseColor("#F0F0F0"),
            trackColor = "#0D0D0D",
            mapStyle = "https://tiles.openfreemap.org/styles/positron",
        )
        val DARK = Palette(
            surface = Color.parseColor("#212121"),
            onSurface = Color.parseColor("#ECECEC"),
            dim = Color.parseColor("#9A9A9A"),
            chip = Color.parseColor("#303030"),
            trackColor = "#FFFFFF",
            mapStyle = "https://tiles.openfreemap.org/styles/dark",
        )
    }
}
