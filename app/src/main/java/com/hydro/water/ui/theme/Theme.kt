package com.hydro.water.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF2E7CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0B3A85),
    secondary = Color(0xFF16B6C9),
    secondaryContainer = Color(0xFFD5F5F9),
    background = Color(0xFFF5F8FC),
    onBackground = Color(0xFF1A2433),
    surface = Color(0xFFF5F8FC),
    onSurface = Color(0xFF1A2433),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF3FA),
    surfaceContainerHigh = Color(0xFFE8EEF7),
    surfaceVariant = Color(0xFFE6ECF5),
    onSurfaceVariant = Color(0xFF6B7A90),
    outlineVariant = Color(0xFFDCE3EE),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6AA6FF),
    onPrimary = Color(0xFF00285F),
    primaryContainer = Color(0xFF173B73),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF4FD5E4),
    secondaryContainer = Color(0xFF0D4A52),
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFEEF3FA),
    surface = Color(0xFF0F141B),
    onSurface = Color(0xFFEEF3FA),
    surfaceContainerLowest = Color(0xFF151B24),
    surfaceContainerLow = Color(0xFF171E28),
    surfaceContainer = Color(0xFF1B222C),
    surfaceContainerHigh = Color(0xFF232B37),
    surfaceVariant = Color(0xFF232B37),
    onSurfaceVariant = Color(0xFF9AA8BB),
    outlineVariant = Color(0xFF2A3341),
    error = Color(0xFFFF6B6F),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun isDark(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun WaterTheme(themeMode: Int, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(themeMode)
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

val Mono = TextStyle(fontFeatureSettings = "tnum")
