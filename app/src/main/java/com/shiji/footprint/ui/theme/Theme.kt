package com.shiji.footprint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Indigo = Color(0xFF4F6BED)
val Mint = Color(0xFF12B886)
val Coral = Color(0xFFFF6B3D)
val Amber = Color(0xFFFFB020)
val Rose = Color(0xFFF0506E)

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E7FF),
    onPrimaryContainer = Color(0xFF15205E),
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F4E6),
    onSecondaryContainer = Color(0xFF00382A),
    tertiary = Coral,
    tertiaryContainer = Color(0xFFFFE2D8),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151824),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF151824),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF5D6375),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F2F7),
    surfaceContainerHighest = Color(0xFFE9EBF2),
    outline = Color(0xFFC9CDD9),
    outlineVariant = Color(0xFFE3E5EC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DA2FF),
    onPrimary = Color(0xFF0B1650),
    primaryContainer = Color(0xFF2A3A8F),
    onPrimaryContainer = Color(0xFFE2E7FF),
    secondary = Color(0xFF4FD8A8),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF0F4E3C),
    onSecondaryContainer = Color(0xFFD2F4E6),
    tertiary = Color(0xFFFF9670),
    tertiaryContainer = Color(0xFF5C2A17),
    background = Color(0xFF10131C),
    onBackground = Color(0xFFE6E8F0),
    surface = Color(0xFF10131C),
    onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFFA6ACBE),
    surfaceContainerLowest = Color(0xFF0B0E15),
    surfaceContainerLow = Color(0xFF181C27),
    surfaceContainer = Color(0xFF1A1E2A),
    surfaceContainerHigh = Color(0xFF222736),
    surfaceContainerHighest = Color(0xFF2B3142),
    outline = Color(0xFF454C60),
    outlineVariant = Color(0xFF2E3446),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val NumberStyle = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun FootprintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
