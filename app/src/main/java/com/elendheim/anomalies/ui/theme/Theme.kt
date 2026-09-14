package com.elendheim.anomalies.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The full palette the app draws with. Screens never hard code a colour, they read
 * one of these roles, which means swapping in the high contrast set changes the
 * whole app from a single place.
 */
@Immutable
data class ElendheimColors(
    val backgroundDeep: Color,
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val borderDim: Color,
    val text: Color,
    val textBright: Color,
    val textMid: Color,
    val textDim: Color,
    val accent: Color,
    val gold: Color,
    val water: Color,
    val road: Color,
    val danger: Color,
)

/** The default look: dark green, cyan accent, white brushstroke headings. */
private val StandardColors = ElendheimColors(
    backgroundDeep = Color(0xFF0A1811),
    background = Color(0xFF0D1F17),
    surface = Color(0xFF10261C),
    card = Color(0xFF163324),
    border = Color(0xFF2A5C44),
    borderDim = Color(0xFF1D3A2C),
    text = Color(0xFFE1F5EE),
    textBright = Color(0xFF9FE1CB),
    textMid = Color(0xFF5DCAA5),
    textDim = Color(0xFF4D7A63),
    accent = Color(0xFF0AF5C8),
    gold = Color(0xFFEAB308),
    water = Color(0xFF123240),
    road = Color(0xFF1D3A2C),
    danger = Color(0xFFFF6B6B),
)

/**
 * The high contrast look: near black grounds and much brighter foregrounds, which
 * means text clears the accessibility contrast bar on a phone screen in daylight.
 */
private val HighContrastColors = ElendheimColors(
    backgroundDeep = Color(0xFF000000),
    background = Color(0xFF000000),
    surface = Color(0xFF07110C),
    card = Color(0xFF0C1D14),
    border = Color(0xFF6BE8BC),
    borderDim = Color(0xFF3C7A60),
    text = Color(0xFFFFFFFF),
    textBright = Color(0xFFD8FFF2),
    textMid = Color(0xFF8CF3D2),
    textDim = Color(0xFFA9C9BC),
    accent = Color(0xFF4DFFDA),
    gold = Color(0xFFFFD43B),
    water = Color(0xFF0B2C3A),
    road = Color(0xFF2C5847),
    danger = Color(0xFFFF9A9A),
)

val LocalElendheimColors = staticCompositionLocalOf { StandardColors }

/** How large every piece of text is drawn, as a multiplier the settings screen owns. */
val LocalFontScale = staticCompositionLocalOf { 1f }

/** When true, every animation in the app collapses to an instant change. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Short hand so screens can write `theme.accent` instead of the full local lookup. */
val theme: ElendheimColors
    @Composable @ReadOnlyComposable get() = LocalElendheimColors.current

@Composable
fun ElendheimTheme(
    highContrast: Boolean = false,
    fontScale: Float = 1f,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (highContrast) HighContrastColors else StandardColors
    // Material components are mapped onto the same palette so dialogs, switches and
    // sliders match the hand drawn parts of the app instead of fighting them.
    val material = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.backgroundDeep,
        secondary = colors.textMid,
        onSecondary = colors.backgroundDeep,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.card,
        onSurface = colors.text,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.textBright,
        outline = colors.border,
        error = colors.danger,
    )
    CompositionLocalProvider(
        LocalElendheimColors provides colors,
        LocalFontScale provides fontScale,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = elendheimTypography(fontScale, colors),
            content = content,
        )
    }
}

/** Kept so a caller that wants the system setting can still ask for it. */
@Composable
fun systemPrefersDark(): Boolean = isSystemInDarkTheme()
