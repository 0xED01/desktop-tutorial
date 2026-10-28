package com.zen.sudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class Accent(val name: String, val light: Color, val dark: Color)

val Accents = listOf(
    Accent("靛蓝", Color(0xFF4F6BED), Color(0xFF8FA2FF)),
    Accent("青碧", Color(0xFF12957F), Color(0xFF5FD0BA)),
    Accent("珊瑚", Color(0xFFE0603F), Color(0xFFFF9C82)),
    Accent("紫罗兰", Color(0xFF7C5CE0), Color(0xFFB7A2FF)),
    Accent("石墨", Color(0xFF3D4756), Color(0xFFB8C2D0)),
)

private val LightBg = Color(0xFFF5F6F8)
private val DarkBg = Color(0xFF0F1115)
private val DarkSurface = Color(0xFF191C22)

private fun lightScheme(a: Color) = lightColorScheme(
    primary = a,
    onPrimary = Color.White,
    primaryContainer = a.copy(alpha = 0.12f).compositeOver(Color.White),
    onPrimaryContainer = a,
    secondaryContainer = a.copy(alpha = 0.16f).compositeOver(Color.White),
    onSecondaryContainer = a,
    background = LightBg,
    onBackground = Color(0xFF1C1F26),
    surface = Color.White,
    onSurface = Color(0xFF1C1F26),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF9FAFB),
    surfaceContainerHighest = Color(0xFFECEEF2),
    outline = Color(0xFFC9CED6),
    outlineVariant = Color(0xFFE3E6EB),
    error = Color(0xFFE5484D),
)

private fun darkScheme(a: Color) = darkColorScheme(
    primary = a,
    onPrimary = DarkBg,
    primaryContainer = a.copy(alpha = 0.16f).compositeOver(DarkSurface),
    onPrimaryContainer = a,
    secondaryContainer = a.copy(alpha = 0.22f).compositeOver(DarkSurface),
    onSecondaryContainer = a,
    background = DarkBg,
    onBackground = Color(0xFFE6E8EC),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF232730),
    onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF1F232A),
    surfaceContainerHighest = Color(0xFF2A2F38),
    outline = Color(0xFF454B57),
    outlineVariant = Color(0xFF2C313A),
    error = Color(0xFFFF6B6F),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun SudokuTheme(dark: Boolean, accent: Int, content: @Composable () -> Unit) {
    val a = Accents[accent.coerceIn(Accents.indices)]
    MaterialTheme(
        colorScheme = if (dark) darkScheme(a.dark) else lightScheme(a.light),
        typography = AppTypography,
        content = content,
    )
}

