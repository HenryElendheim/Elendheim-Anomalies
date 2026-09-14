package com.elendheim.anomalies.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Every text size in the app comes from here multiplied by the user font scale,
 * which means the accessibility setting reaches every screen without each screen
 * having to know about it.
 */
fun elendheimTypography(scale: Float, colors: ElendheimColors): Typography {
    fun style(size: Float, weight: FontWeight, lineHeight: Float, family: FontFamily = FontFamily.SansSerif) =
        TextStyle(
            fontSize = (size * scale).sp,
            lineHeight = (lineHeight * scale).sp,
            fontWeight = weight,
            fontFamily = family,
            color = colors.text,
        )

    return Typography(
        // Screen titles, the brushstroke headings.
        headlineMedium = style(22f, FontWeight.Medium, 28f),
        headlineSmall = style(17f, FontWeight.Medium, 22f),
        // Big numbers on the stat tiles.
        titleLarge = style(19f, FontWeight.Medium, 24f),
        titleMedium = style(14f, FontWeight.Medium, 19f),
        titleSmall = style(12f, FontWeight.Medium, 16f),
        bodyLarge = style(14f, FontWeight.Normal, 20f),
        bodyMedium = style(12f, FontWeight.Normal, 17f),
        bodySmall = style(11f, FontWeight.Normal, 15f),
        labelLarge = style(12f, FontWeight.Medium, 16f),
        labelMedium = style(11f, FontWeight.Normal, 14f),
        labelSmall = style(10f, FontWeight.Normal, 13f),
    )
}

/** Flavor text is set in a serif face so it reads as a different voice. */
fun serifFlavor(scale: Float, colors: ElendheimColors) = TextStyle(
    fontSize = (11.5f * scale).sp,
    lineHeight = (17f * scale).sp,
    fontFamily = FontFamily.Serif,
    color = colors.textMid,
)
