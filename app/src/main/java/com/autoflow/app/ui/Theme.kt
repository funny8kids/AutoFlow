package com.autoflow.app.ui

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

/** 品牌色板：深空蓝底 + 电光紫 + 薄荷青。 */
object Brand {
    val Primary = Color(0xFF6C7CFF)
    val Secondary = Color(0xFF33E1C4)
    val Tertiary = Color(0xFFFFB86B)
    val Danger = Color(0xFFFF6B81)

    val DarkBg = Color(0xFF0B1020)
    val DarkSurface = Color(0xFF121829)
    val DarkSurfaceHigh = Color(0xFF1B2237)
    val DarkOutline = Color(0xFF2C3550)
    val OnDark = Color(0xFFE7EAF6)
    val OnDarkMuted = Color(0xFF9AA3BF)

    val LightBg = Color(0xFFF6F7FC)
    val LightSurface = Color(0xFFFFFFFF)
    val OnLight = Color(0xFF11142A)
    val OnLightMuted = Color(0xFF5B6280)

    val accents = listOf(Primary, Secondary, Tertiary, Danger, Color(0xFFB57BFF), Color(0xFF4FC3F7))
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Color(0xFF070B18),
    primaryContainer = Color(0xFF2A3373),
    onPrimaryContainer = Color(0xFFDCE0FF),
    secondary = Brand.Secondary,
    onSecondary = Color(0xFF04231D),
    secondaryContainer = Color(0xFF0E4A3E),
    onSecondaryContainer = Color(0xFFB8F5E8),
    tertiary = Brand.Tertiary,
    background = Brand.DarkBg,
    onBackground = Brand.OnDark,
    surface = Brand.DarkSurface,
    onSurface = Brand.OnDark,
    surfaceVariant = Brand.DarkSurfaceHigh,
    onSurfaceVariant = Brand.OnDarkMuted,
    outline = Brand.DarkOutline,
    error = Brand.Danger,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4C5AE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF11164A),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Color(0xFFD98014),
    background = Brand.LightBg,
    onBackground = Brand.OnLight,
    surface = Brand.LightSurface,
    onSurface = Brand.OnLight,
    surfaceVariant = Color(0xFFECEEF7),
    onSurfaceVariant = Brand.OnLightMuted,
    outline = Color(0xFFD3D7E6),
    error = Color(0xFFD33A4E),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun AutoFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
