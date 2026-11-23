package com.simplespeak.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Calm, low-stimulation palette: muted teal and soft blue on warm neutrals. No bright reds.
private val Light = lightColorScheme(
    primary = Color(0xFF3E7C7D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8E6),
    onPrimaryContainer = Color(0xFF113637),
    secondary = Color(0xFF5E7299),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE5F2),
    onSecondaryContainer = Color(0xFF1B2A44),
    background = Color(0xFFF5F6F3),
    onBackground = Color(0xFF1E2828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2828),
    surfaceVariant = Color(0xFFE9EDEA),
    onSurfaceVariant = Color(0xFF465352),
    outline = Color(0xFFB9C4C2),
    error = Color(0xFF9C4A3C),
    errorContainer = Color(0xFFF5DDD6),
    onErrorContainer = Color(0xFF4A1C12),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DC7C6),
    onPrimary = Color(0xFF0B3132),
    primaryContainer = Color(0xFF26504F),
    onPrimaryContainer = Color(0xFFD3E8E6),
    secondary = Color(0xFFAFC1E3),
    onSecondary = Color(0xFF1B2A44),
    secondaryContainer = Color(0xFF34435F),
    onSecondaryContainer = Color(0xFFDEE5F2),
    background = Color(0xFF141A1A),
    onBackground = Color(0xFFE0E6E4),
    surface = Color(0xFF1B2222),
    onSurface = Color(0xFFE0E6E4),
    surfaceVariant = Color(0xFF283131),
    onSurfaceVariant = Color(0xFFB9C4C2),
    outline = Color(0xFF52605F),
    error = Color(0xFFE3A497),
    errorContainer = Color(0xFF5A2B21),
    onErrorContainer = Color(0xFFF5DDD6),
)

@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val highlight: Color,
    val onHighlight: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF3F7A4B),
    successContainer = Color(0xFFDCEEDB),
    onSuccessContainer = Color(0xFF173A20),
    highlight = Color(0xFFF7E3B5),
    onHighlight = Color(0xFF4F3800),
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF9BD1A4),
    successContainer = Color(0xFF244A2C),
    onSuccessContainer = Color(0xFFDCEEDB),
    highlight = Color(0xFF5E4A1A),
    onHighlight = Color(0xFFF7E3B5),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

// Larger-than-default text for easier reading.
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SimpleSpeakTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
