package com.elendheim.anomalies.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.game.SpriteShape
import com.elendheim.anomalies.ui.theme.LocalReduceMotion
import com.elendheim.anomalies.ui.theme.theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every creature in the app is drawn by this one function. Art is code rather than image
 * files, which means a new roster entry costs a line of JSON and no assets at all, and a
 * shiny is the same silhouette through a hue shift.
 */
fun DrawScope.drawCreature(
    center: Offset,
    radius: Float,
    shape: SpriteShape,
    bodyColor: Color,
    eyeColor: Color,
    silhouette: Boolean = false,
) {
    val body = bodyColor
    translate(center.x, center.y) {
        when (shape) {
            SpriteShape.BOX -> drawRoundRect(
                color = body,
                topLeft = Offset(-radius * 0.82f, -radius * 0.82f),
                size = Size(radius * 1.64f, radius * 1.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.34f),
            )

            SpriteShape.ROUND -> {
                drawCircle(body, radius * 0.92f, Offset.Zero)
                // Two small ears so the round silhouette is not just a plain dot.
                drawLine(body, Offset(-radius, -radius * 0.85f), Offset(-radius * 0.5f, -radius * 1.45f), radius * 0.22f)
                drawLine(body, Offset(radius, -radius * 0.85f), Offset(radius * 0.5f, -radius * 1.45f), radius * 0.22f)
            }

            SpriteShape.BLOB -> drawPath(
                path = Path().apply {
                    moveTo(-radius * 0.9f, radius * 0.45f)
                    quadraticBezierTo(0f, -radius * 1.5f, radius * 0.9f, radius * 0.45f)
                    quadraticBezierTo(radius * 0.45f, radius * 1.1f, 0f, radius * 0.9f)
                    quadraticBezierTo(-radius * 0.45f, radius * 1.1f, -radius * 0.9f, radius * 0.45f)
                    close()
                },
                color = body,
            )

            SpriteShape.DIAMOND -> rotate(45f, Offset.Zero) {
                drawRoundRect(
                    color = body,
                    topLeft = Offset(-radius * 0.72f, -radius * 0.72f),
                    size = Size(radius * 1.44f, radius * 1.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.22f),
                )
            }

            SpriteShape.SPIKE -> drawPath(
                path = Path().apply {
                    moveTo(-radius * 0.95f, radius * 0.8f)
                    lineTo(0f, -radius * 1.15f)
                    lineTo(radius * 0.95f, radius * 0.8f)
                    close()
                },
                color = body,
            )

            SpriteShape.WISP -> drawPath(
                path = Path().apply {
                    moveTo(0f, -radius * 1.2f)
                    quadraticBezierTo(radius * 1.0f, -radius * 0.1f, radius * 0.55f, radius * 0.85f)
                    quadraticBezierTo(0f, radius * 1.25f, -radius * 0.55f, radius * 0.85f)
                    quadraticBezierTo(-radius * 1.0f, -radius * 0.1f, 0f, -radius * 1.2f)
                    close()
                },
                color = body,
            )

            SpriteShape.CRESCENT -> {
                // Built from two arcs rather than by erasing a disc, which means it draws
                // correctly on top of whatever is behind it.
                val outer = radius * 1.0f
                val inner = radius * 0.78f
                drawPath(
                    path = Path().apply {
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(Offset(-outer, -outer), Size(outer * 2, outer * 2)),
                            startAngleDegrees = 55f,
                            sweepAngleDegrees = 250f,
                            forceMoveTo = true,
                        )
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                Offset(-inner + radius * 0.34f, -inner - radius * 0.12f),
                                Size(inner * 2, inner * 2),
                            ),
                            startAngleDegrees = 305f,
                            sweepAngleDegrees = -250f,
                            forceMoveTo = false,
                        )
                        close()
                    },
                    color = body,
                )
            }

            SpriteShape.STAR -> {
                val path = Path()
                val points = 5
                for (index in 0 until points * 2) {
                    val pointRadius = if (index % 2 == 0) radius * 1.15f else radius * 0.48f
                    val angle = -PI / 2 + index * PI / points
                    val x = (cos(angle) * pointRadius).toFloat()
                    val y = (sin(angle) * pointRadius).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, body)
            }
        }

        if (!silhouette) {
            val eyeRadius = radius * 0.17f
            val eyeY = if (shape == SpriteShape.SPIKE || shape == SpriteShape.STAR) radius * 0.18f else -radius * 0.08f
            drawCircle(eyeColor, eyeRadius, Offset(-radius * 0.33f, eyeY))
            drawCircle(eyeColor, eyeRadius, Offset(radius * 0.33f, eyeY))
        }
    }
}

/** The composable wrapper. Set [bob] for the gentle idle motion on the map. */
@Composable
fun CreatureSprite(
    def: CreatureDef,
    isShiny: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    bob: Boolean = false,
    silhouette: Boolean = false,
) {
    val reduceMotion = LocalReduceMotion.current
    val dim = theme.borderDim
    val bobOffset = if (bob && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bobOffset",
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2.6f
        drawCreature(
            center = Offset(this.size.width / 2, this.size.height / 2 + bobOffset * radius * 0.09f),
            radius = radius,
            shape = def.shape,
            bodyColor = if (silhouette) dim else shinyTint(def.bodyColor, isShiny),
            eyeColor = def.eyeTint,
            silhouette = silhouette,
        )
    }
}

/** A shiny is the same creature with its hue turned, so it costs nothing extra to draw. */
fun shinyTint(base: Color, isShiny: Boolean): Color {
    if (!isShiny) return base
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[0] = (hsv[0] + 155f) % 360f
    hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f)
    hsv[2] = (hsv[2] * 1.08f).coerceAtMost(1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The gold ring and mark that tells a shiny apart at a glance in the Codex. */
fun DrawScope.drawShinyMark(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius * 0.24f, center + Offset(radius * 0.95f, -radius * 0.95f))
    drawCircle(color, radius, center, style = Stroke(width = radius * 0.06f))
}
