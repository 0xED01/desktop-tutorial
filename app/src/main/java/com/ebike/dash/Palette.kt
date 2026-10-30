package com.ebike.dash

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper

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

    val dark: Boolean get() = this === DARK

    /**
     * 按这套配色（而不是系统的深浅模式）生成界面用的 Context，
     * 悬浮面板里的 @color/fg、@color/chip 等颜色都从这里取，保证和地图、主界面一致。
     */
    fun themed(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return ContextThemeWrapper(base.createConfigurationContext(cfg), R.style.Theme_Driver)
    }

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
