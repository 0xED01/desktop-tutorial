package com.yuandian.nightprotocol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NightColors {
    val Bg = Color(0xFF0B1020)          // 深蓝黑
    val Surface = Color(0xFF121A2E)
    val SurfaceHigh = Color(0xFF18223A)
    val Text = Color(0xFFE9E2D3)        // 暖白
    val TextDim = Color(0xFF9A9486)
    val Accent = Color(0xFF7D8FB8)
    val Ok = Color(0xFF8DB38B)
    val Warn = Color(0xFFD1A867)
    val Bad = Color(0xFFC7786E)
}

@Composable
fun NightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = NightColors.Bg,
            surface = NightColors.Surface,
            surfaceVariant = NightColors.SurfaceHigh,
            surfaceContainer = NightColors.Surface,
            surfaceContainerHigh = NightColors.SurfaceHigh,
            surfaceContainerHighest = NightColors.SurfaceHigh,
            onBackground = NightColors.Text,
            onSurface = NightColors.Text,
            onSurfaceVariant = NightColors.TextDim,
            primary = NightColors.Accent,
            onPrimary = NightColors.Bg,
            secondary = NightColors.Accent,
            error = NightColors.Bad,
            outline = NightColors.SurfaceHigh,
        ),
        content = content,
    )
}
