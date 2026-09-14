package com.elendheim.anomalies.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn as line art in code. Keeping the glyphs here rather than pulling in
 * an icon library means one visual language and nothing extra in the download.
 */
enum class Glyph { MAP, CODEX, STOP, ITEMS, PROFILE, PLUS, BACK }

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val width = size.minDimension
    val unit = width / 24f
    val stroke = Stroke(width = unit * 1.8f, cap = StrokeCap.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), unit * 1.8f, StrokeCap.Round)

    when (glyph) {
        Glyph.MAP -> {
            // A folded map: three panels with two creases.
            drawPath(
                Path().apply {
                    moveTo(3 * unit, 6 * unit); lineTo(9 * unit, 3.5f * unit)
                    lineTo(15 * unit, 6.5f * unit); lineTo(21 * unit, 4 * unit)
                    lineTo(21 * unit, 18 * unit); lineTo(15 * unit, 20.5f * unit)
                    lineTo(9 * unit, 17.5f * unit); lineTo(3 * unit, 20 * unit); close()
                },
                color, style = stroke,
            )
            line(9f, 3.5f, 9f, 17.5f)
            line(15f, 6.5f, 15f, 20.5f)
        }

        Glyph.CODEX -> {
            // An open book.
            drawPath(
                Path().apply {
                    moveTo(3.5f * unit, 5 * unit); lineTo(11.5f * unit, 7 * unit)
                    lineTo(11.5f * unit, 19.5f * unit); lineTo(3.5f * unit, 17.5f * unit); close()
                },
                color, style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(20.5f * unit, 5 * unit); lineTo(12.5f * unit, 7 * unit)
                    lineTo(12.5f * unit, 19.5f * unit); lineTo(20.5f * unit, 17.5f * unit); close()
                },
                color, style = stroke,
            )
        }

        Glyph.STOP -> {
            // The cyan diamond marker the map uses for a placed stop.
            drawPath(
                Path().apply {
                    moveTo(12 * unit, 2.5f * unit); lineTo(21.5f * unit, 12 * unit)
                    lineTo(12 * unit, 21.5f * unit); lineTo(2.5f * unit, 12 * unit); close()
                },
                color, style = stroke,
            )
            drawCircle(color, unit * 2.4f, Offset(12 * unit, 12 * unit))
        }

        Glyph.ITEMS -> {
            // A carry bag.
            drawPath(
                Path().apply {
                    moveTo(4 * unit, 8 * unit); lineTo(20 * unit, 8 * unit)
                    lineTo(18.5f * unit, 21 * unit); lineTo(5.5f * unit, 21 * unit); close()
                },
                color, style = stroke,
            )
            drawArc(
                color = color,
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(8 * unit, 2.5f * unit),
                size = Size(8 * unit, 8 * unit),
                style = stroke,
            )
        }

        Glyph.PROFILE -> {
            drawCircle(color, unit * 4f, Offset(12 * unit, 8.5f * unit), style = stroke)
            drawArc(
                color = color,
                startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(3.5f * unit, 13 * unit),
                size = Size(17 * unit, 15 * unit),
                style = stroke,
            )
        }

        Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }

        Glyph.BACK -> { line(14f, 5f, 7f, 12f); line(7f, 12f, 14f, 19f) }
    }
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    size: Dp = 20.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        drawGlyph(glyph, tint)
    }
}

/** Kept so the dashed throw guide on the catch screen has one definition. */
val dashedGuide: PathEffect get() = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), 0f)
