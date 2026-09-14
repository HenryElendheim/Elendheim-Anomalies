package com.elendheim.anomalies.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.ItemRarity
import com.elendheim.anomalies.ui.common.Dot
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.game.SpinSession
import com.elendheim.anomalies.ui.theme.LocalReduceMotion
import com.elendheim.anomalies.ui.theme.theme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The spin. The stop marker is thrown into a fast rotation, slows down against the drag
 * of its own weight, lands with a thump, and then hands the drops over one at a time
 * with the plainest first, so the good one is always the one you are still waiting for.
 */
@Composable
fun SpinOverlay(viewModel: GameViewModel, session: SpinSession) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val quick = state.settings.quickSpins || reduceMotion
    val haptics = LocalHapticFeedback.current
    val colors = theme

    val drops = remember(session) { session.reward.revealOrder }
    val spin = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }
    var revealed by remember { mutableIntStateOf(0) }

    LaunchedEffect(session) {
        if (quick) {
            spin.snapTo(SPIN_TURNS)
            landing.snapTo(1f)
            revealed = drops.size
            return@LaunchedEffect
        }
        // One long throw that decelerates the whole way, which is what makes it read as
        // weight rather than as a loop that simply stops.
        spin.animateTo(SPIN_TURNS, tween(SPIN_MILLIS, easing = LinearOutSlowInEasing))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        landing.animateTo(1f, tween(LANDING_MILLIS, easing = FastOutSlowInEasing))
        drops.forEachIndexed { index, drop ->
            // A rarer drop gets a longer beat in front of it, so the pause itself is a tell.
            delay(if (drop.first.rarity.ordinal >= ItemRarity.RARE.ordinal) RARE_BEAT else BEAT)
            revealed = index + 1
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val finished = revealed >= drops.size
    BackHandler(enabled = finished) { viewModel.dismissSpin() }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.backgroundDeep.copy(alpha = 0.94f))
            .then(if (finished) Modifier.clickable { viewModel.dismissSpin() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Sizes.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                session.stopName,
                style = MaterialTheme.typography.headlineSmall,
                color = theme.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Canvas(Modifier.size(140.dp)) {
                drawSpinner(
                    turns = spin.value,
                    landed = landing.value,
                    accent = colors.accent,
                    ground = colors.backgroundDeep,
                    ring = colors.borderDim,
                )
            }

            Spacer(Modifier.height(22.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Sizes.gap),
            ) {
                drops.forEachIndexed { index, (type, count) ->
                    DropRow(type = type, count = count, shown = index < revealed, quick = quick)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (finished) {
                PrimaryButton("Take it all", Modifier.width(220.dp)) { viewModel.dismissSpin() }
            } else {
                Text(
                    "opening",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** One drop, popping in at its own moment with a flourish sized by its rarity. */
@Composable
private fun DropRow(type: CapsuleType, count: Int, shown: Boolean, quick: Boolean) {
    val pop = remember(type) { Animatable(if (quick) 1f else 0f) }
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        if (quick) {
            pop.snapTo(1f)
        } else {
            // Overshooting past one and easing back is what gives the card its snap.
            pop.animateTo(1.12f, tween(130, easing = LinearOutSlowInEasing))
            pop.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
        }
    }
    if (!shown) {
        // The slot is held open so the list never jumps as each drop lands.
        Spacer(Modifier.fillMaxWidth().height(54.dp))
        return
    }

    val glow = rarityGlow(type.rarity)
    ElCard(
        modifier = Modifier.scale(pop.value).alpha(pop.value.coerceAtMost(1f)),
        borderColor = glow,
        contentPadding = 11.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Color(type.colorHex), 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleSmall, color = theme.text)
                Spacer(Modifier.height(2.dp))
                Text(
                    type.rarity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = glow,
                )
            }
            Text(
                "x$count",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textBright,
            )
        }
    }
}

/** Rarer drops get a warmer edge, so the good ones are obvious without reading a word. */
@Composable
private fun rarityGlow(rarity: ItemRarity): Color = when (rarity) {
    ItemRarity.STANDARD -> theme.border
    ItemRarity.UNCOMMON -> theme.textMid
    ItemRarity.RARE -> theme.accent
    ItemRarity.EPIC -> theme.gold
}

/**
 * The spinning marker. It is the same diamond the map uses for a stop, turned on its
 * centre, with a shadow underneath that squashes as it comes down.
 */
private fun DrawScope.drawSpinner(
    turns: Float,
    landed: Float,
    accent: Color,
    ground: Color,
    ring: Color,
) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val half = size.minDimension * 0.26f

    // A track behind the marker so the rotation has something to read against.
    drawCircle(ring, size.minDimension * 0.44f, centre, style = Stroke(width = 2f))

    // The shadow flattens out as the marker settles, which sells the landing.
    drawOval(
        color = ground,
        topLeft = Offset(centre.x - half * (0.6f + landed * 0.8f), size.height * 0.84f),
        size = Size(half * 2 * (0.6f + landed * 0.8f), size.height * 0.09f),
    )

    rotate(turns * 360f, centre) {
        drawRoundRect(
            color = accent,
            topLeft = centre - Offset(half, half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(half * 0.5f),
        )
        drawRoundRect(
            color = ground,
            topLeft = centre - Offset(half / 2, half / 2),
            size = Size(half, half),
            cornerRadius = CornerRadius(half * 0.3f),
        )
    }

    // A burst of short spokes on landing, fading out as the marker comes to rest.
    if (landed > 0f) {
        val burst = (1f - landed).coerceIn(0f, 1f)
        if (burst > 0.02f) {
            repeat(SPOKES) { index ->
                val angle = index * 2.0 * PI / SPOKES
                val inner = size.minDimension * (0.30f + 0.18f * landed)
                val outer = inner + size.minDimension * 0.10f * burst
                drawLine(
                    color = accent.copy(alpha = burst * 0.8f),
                    start = centre + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                    end = centre + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                    strokeWidth = 3f,
                )
            }
        }
    }
}

private const val SPIN_TURNS = 6.5f
private const val SPIN_MILLIS = 1500
private const val LANDING_MILLIS = 320
private const val BEAT = 280L
private const val RARE_BEAT = 620L
private const val SPOKES = 8
