package com.yuandian.quote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Green = Color(0xFF1F9D6B)
val GreenDark = Color(0xFF167A52)
val GreenSoft = Color(0xFFE8F6EF)
val TextPrimary = Color(0xFF1F2329)
val TextSecondary = Color(0xFF646A73)
val TextTertiary = Color(0xFF8F959E)
val Divider = Color(0xFFEBEDF0)
val PageGray = Color(0xFFF5F6F7)
val Danger = Color(0xFFD83931)
val Warning = Color(0xFFE38B0C)

private val colors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenDark,
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = GreenSoft,
    onSecondaryContainer = GreenDark,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = PageGray,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = PageGray,
    outline = Color(0xFFD0D3D6),
    outlineVariant = Divider,
    error = Danger,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun YuandianTheme(content: @Composable () -> Unit) {
    // 界面固定为浅色主题，保证商务场景下展示效果一致
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
