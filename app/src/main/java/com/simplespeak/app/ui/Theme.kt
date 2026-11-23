package com.simplespeak.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.simplespeak.app.R

// "Simple, yet refined": deep teal and soft indigo on warm neutrals, gentle gradients, no harsh colours.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECEA),
    onPrimaryContainer = Color(0xFF0B3433),
    secondary = Color(0xFF4D5AA0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E6F7),
    onSecondaryContainer = Color(0xFF1C2350),
    tertiary = Color(0xFFC77A2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE8D3),
    onTertiaryContainer = Color(0xFF4A2A06),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1A2223),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2223),
    surfaceVariant = Color(0xFFEEF0EC),
    onSurfaceVariant = Color(0xFF59625F),
    surfaceContainer = Color(0xFFF1F2EE),
    outline = Color(0xFFC9CFCB),
    outlineVariant = Color(0xFFE2E6E2),
    error = Color(0xFF9C4A3C),
    errorContainer = Color(0xFFF5DDD6),
    onErrorContainer = Color(0xFF4A1C12),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D0CC),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF184E4D),
    onPrimaryContainer = Color(0xFFD6ECEA),
    secondary = Color(0xFFB8C0F2),
    onSecondary = Color(0xFF1C2350),
    secondaryContainer = Color(0xFF353F78),
    onSecondaryContainer = Color(0xFFE3E6F7),
    tertiary = Color(0xFFF2B77A),
    onTertiary = Color(0xFF4A2A06),
    tertiaryContainer = Color(0xFF5E3B12),
    onTertiaryContainer = Color(0xFFFBE8D3),
    background = Color(0xFF111617),
    onBackground = Color(0xFFE2E7E5),
    surface = Color(0xFF192022),
    onSurface = Color(0xFFE2E7E5),
    surfaceVariant = Color(0xFF242C2D),
    onSurfaceVariant = Color(0xFFB4BDBA),
    surfaceContainer = Color(0xFF1D2526),
    outline = Color(0xFF46504F),
    outlineVariant = Color(0xFF2E3738),
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
    val flame: Color,
    /** Signature gradient for hero cards and the main button. */
    val hero: Brush,
    val heroText: Color,
    /** Very soft wash behind the top of screens. */
    val wash: Brush,
    val orbInner: Color,
    val orbOuter: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF2F7A47),
    successContainer = Color(0xFFDDF0E0),
    onSuccessContainer = Color(0xFF12391E),
    highlight = Color(0xFFF7E1AE),
    onHighlight = Color(0xFF4F3600),
    flame = Color(0xFFE07B2D),
    hero = Brush.linearGradient(listOf(Color(0xFF1F6F6E), Color(0xFF3E5E9A), Color(0xFF5B57A6))),
    heroText = Color.White,
    wash = Brush.verticalGradient(listOf(Color(0xFFE4F1EF), Color(0xFFF7F6F2))),
    orbInner = Color(0xFF7FD3CC),
    orbOuter = Color(0xFF6B6FD0),
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF94D4A3),
    successContainer = Color(0xFF1F4429),
    onSuccessContainer = Color(0xFFDDF0E0),
    highlight = Color(0xFF5E4A1A),
    onHighlight = Color(0xFFF7E1AE),
    flame = Color(0xFFF2A35E),
    hero = Brush.linearGradient(listOf(Color(0xFF15514F), Color(0xFF2C4374), Color(0xFF3F3C80))),
    heroText = Color(0xFFF2F6F5),
    wash = Brush.verticalGradient(listOf(Color(0xFF162423), Color(0xFF111617))),
    orbInner = Color(0xFF6CC4BD),
    orbOuter = Color(0xFF5A5EC0),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0f) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

// Generous sizes for easy reading.
private val AppTypography = Typography(
    displaySmall = style(36, 44, FontWeight.ExtraBold, -0.5f),
    headlineLarge = style(32, 40, FontWeight.ExtraBold, -0.4f),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.3f),
    headlineSmall = style(24, 32, FontWeight.Bold, -0.2f),
    titleLarge = style(22, 30, FontWeight.Bold),
    titleMedium = style(19, 26, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(19, 29, FontWeight.Medium),
    bodyMedium = style(17, 25, FontWeight.Medium),
    bodySmall = style(15, 21, FontWeight.Medium),
    labelLarge = style(18, 24, FontWeight.Bold),
    labelMedium = style(14, 19, FontWeight.SemiBold, 0.2f),
    labelSmall = style(12, 16, FontWeight.Bold, 0.6f),
)

@Composable
fun SimpleSpeakTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
