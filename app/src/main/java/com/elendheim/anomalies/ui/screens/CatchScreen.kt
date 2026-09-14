package com.elendheim.anomalies.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.CatchMath
import com.elendheim.anomalies.game.MAX_CATCH_ATTEMPTS
import com.elendheim.anomalies.game.RingHit
import com.elendheim.anomalies.ui.common.CreatureSprite
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.Pill
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.common.drawCreature
import com.elendheim.anomalies.ui.common.shinyTint
import com.elendheim.anomalies.ui.game.CatchPhase
import com.elendheim.anomalies.ui.game.CatchSession
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.LocalReduceMotion
import com.elendheim.anomalies.ui.theme.serifFlavor
import com.elendheim.anomalies.ui.theme.LocalFontScale
import com.elendheim.anomalies.ui.theme.theme
import kotlin.math.abs

/**
 * The encounter. Drag the capsule and flick it: how tight the ring is when it leaves your
 * finger, how far across the flick travels, and how hard you flick all feed the result,
 * and the tumble the capsule is given is the same tumble the score is read from.
 */
@Composable
fun CatchScreen(viewModel: GameViewModel, session: CatchSession) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val colors = theme

    BackHandler { viewModel.abandonCatch() }

    // The shrinking ring. With reduce motion on it holds still at a fair size, which
    // takes the timing out of the throw rather than making it impossible to read.
    val ringFraction = if (reduceMotion) {
        STEADY_RING_FRACTION
    } else {
        val transition = rememberInfiniteTransition(label = "ring")
        transition.animateFloat(
            initialValue = RING_WIDEST,
            targetValue = RING_TIGHTEST,
            animationSpec = infiniteRepeatable(
                animation = tween(RING_CYCLE_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ringFraction",
        ).value
    }

    // A slow bob so the creature feels alive while it is being aimed at.
    val bob = if (reduceMotion) {
        0f
    } else {
        rememberInfiniteTransition(label = "creatureBob").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bobOffset",
        ).value
    }

    val flight = remember { Animatable(0f) }
    var dragTotal by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    LaunchedEffect(session.phase, session.spinTurns) {
        if (session.phase == CatchPhase.FLYING) {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(if (reduceMotion) 0 else FLIGHT_MILLIS, easing = LinearEasing))
            viewModel.settleThrow()
        } else if (session.phase == CatchPhase.AIMING) {
            flight.snapTo(0f)
            dragTotal = Offset.Zero
        }
    }

    val backdrop = biomeBackdrop(session.spawn.biome, colors.background)

    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session.phase) {
                    if (session.phase != CatchPhase.AIMING) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragTotal = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            dragTotal += amount
                        },
                        onDragEnd = {
                            val travel = dragTotal
                            // Only an upward flick counts as a throw.
                            if (travel.y < -MIN_FLICK_PX) {
                                val strength = (abs(travel.y) / (size.height * FLICK_FULL_SHARE))
                                    .coerceIn(0f, 1f)
                                val sideways = (travel.x / (abs(travel.y) + 1f)).coerceIn(-1f, 1f)
                                viewModel.releaseThrow(ringFraction, strength, sideways)
                            }
                            dragTotal = Offset.Zero
                        },
                    )
                }
        ) {
            val creatureCentre = Offset(size.width / 2f, size.height * CREATURE_HEIGHT_SHARE)
            val capsuleHome = Offset(size.width / 2f, size.height * CAPSULE_HEIGHT_SHARE)
            val outerRadius = size.minDimension * RING_OUTER_SHARE

            // The ground shadow under the creature.
            drawOval(
                color = colors.backgroundDeep.copy(alpha = 0.45f),
                topLeft = Offset(size.width * 0.18f, size.height * 0.90f),
                size = Size(size.width * 0.64f, size.height * 0.16f),
            )

            // The creature is drawn by the same canvas as the ring, which means the two
            // can never drift apart on screen.
            drawCreature(
                center = creatureCentre + Offset(0f, bob * size.minDimension * 0.006f),
                radius = size.minDimension * CREATURE_RADIUS_SHARE,
                shape = session.def.shape,
                bodyColor = shinyTint(session.def.bodyColor, session.spawn.isShiny),
                eyeColor = session.def.eyeTint,
            )

            if (session.phase == CatchPhase.AIMING) {
                // The widest ring stays faint as a reference for how far it has left to go.
                drawCircle(colors.accent.copy(alpha = 0.22f), outerRadius, creatureCentre, style = Stroke(2f))
                val liveRadius = outerRadius * ringFraction
                val hit = CatchMath.gradeRing(ringFraction)
                drawCircle(ringColor(hit, colors.accent, colors.gold), liveRadius, creatureCentre, style = Stroke(3f))
            }

            // The capsule sits at the bottom, follows the drag, then flies.
            val progress = flight.value
            val capsulePosition = when (session.phase) {
                CatchPhase.AIMING -> capsuleHome + Offset(dragTotal.x * 0.4f, dragTotal.y * 0.25f)
                CatchPhase.FLYING -> flightPoint(capsuleHome, creatureCentre, session.curveDirection, progress, size.width)
                else -> creatureCentre
            }
            val capsuleScale = when (session.phase) {
                CatchPhase.FLYING -> 1f - 0.45f * progress
                CatchPhase.AIMING -> 1f
                else -> 0f
            }
            val rotationTurns = if (session.phase == CatchPhase.FLYING) session.spinTurns * progress else 0f

            if (capsuleScale > 0f) {
                drawCapsule(
                    centre = capsulePosition,
                    halfHeight = size.minDimension * CAPSULE_SIZE_SHARE * capsuleScale,
                    turns = rotationTurns,
                    body = Color(session.capsule.colorHex),
                    core = colors.backgroundDeep,
                )
            }

            if (session.phase == CatchPhase.AIMING) {
                // A dashed hint showing the arc the flick is aiming along.
                drawLine(
                    color = colors.textMid.copy(alpha = 0.5f),
                    start = capsuleHome,
                    end = Offset(capsuleHome.x, capsuleHome.y - size.height * 0.10f),
                    strokeWidth = 2f,
                    pathEffect = com.elendheim.anomalies.ui.common.dashedGuide,
                )
            }
        }

        EncounterHeader(session, Modifier.align(Alignment.TopStart))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (session.phase == CatchPhase.AIMING) {
                Text(
                    if (reduceMotion) "Flick the capsule upward" else "Flick upward when the ring is tight",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                CapsuleTray(state.items, session.capsule) { viewModel.selectCapsule(it) }
            }
        }

        if (session.phase != CatchPhase.AIMING && session.phase != CatchPhase.FLYING) {
            ResultSheet(
                session = session,
                onThrowAgain = { viewModel.nextAttempt() },
                onClose = { viewModel.abandonCatch() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun EncounterHeader(session: CatchSession, modifier: Modifier = Modifier) {
    Column(
        modifier
            .statusBarsPadding()
            .padding(Sizes.gutter)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.def.name, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            Spacer(Modifier.width(8.dp))
            Text(session.def.rarity.label, style = MaterialTheme.typography.labelMedium, color = theme.textMid)
            if (session.spawn.isShiny) {
                Spacer(Modifier.width(8.dp))
                Text("shiny", style = MaterialTheme.typography.labelMedium, color = theme.gold)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "${session.spawn.biome.label} biome, attempt ${session.attempt} of $MAX_CATCH_ATTEMPTS",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
        )
    }
}

/** The swipeable row of capsule types, showing how many of each are left. */
@Composable
private fun CapsuleTray(items: Map<CapsuleType, Int>, selected: CapsuleType, onSelect: (CapsuleType) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Sizes.gutter),
    ) {
        items(CapsuleType.entries.size) { index ->
            val type = CapsuleType.entries[index]
            val count = items[type] ?: 0
            if (count > 0) {
                Pill(
                    text = "${type.label.substringBefore(' ')} x$count",
                    selected = type == selected,
                    accent = Color(type.colorHex),
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

/** What happened, shown as a panel over the bottom of the encounter. */
@Composable
private fun ResultSheet(
    session: CatchSession,
    onThrowAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(Sizes.gutter),
    ) {
        ElCard(borderColor = if (session.succeeded) theme.accent else theme.border) {
            when (session.phase) {
                CatchPhase.CAUGHT -> CaughtBody(session)
                CatchPhase.FLED -> {
                    Text("It slipped away", style = MaterialTheme.typography.titleMedium, color = theme.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${session.def.name} is gone. Another will turn up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                    )
                }
                CatchPhase.OUT_OF_CAPSULES -> {
                    Text("No capsules left", style = MaterialTheme.typography.titleMedium, color = theme.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Spin a stop to restock, then come back for the next one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                    )
                }
                else -> {
                    Text("It broke free", style = MaterialTheme.typography.titleMedium, color = theme.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        throwSummary(session) + ", ${session.attemptsLeft - 1} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                    )
                    if (session.capsuleRefunded) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Your companion caught the capsule on the way back",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMid,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.phase == CatchPhase.RESOLVED) {
                PrimaryButton("Throw again", Modifier.weight(1f)) { onThrowAgain() }
                GhostButton("Leave", Modifier.weight(1f)) { onClose() }
            } else {
                PrimaryButton("Done", Modifier.weight(1f)) { onClose() }
            }
        }
    }
}

@Composable
private fun CaughtBody(session: CatchSession) {
    val outcome = session.outcome
    Row(verticalAlignment = Alignment.CenterVertically) {
        CreatureSprite(session.def, session.spawn.isShiny, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${session.def.name} joined the Codex",
                style = MaterialTheme.typography.titleMedium,
                color = if (session.spawn.isShiny) theme.gold else theme.text,
            )
            Spacer(Modifier.height(3.dp))
            Text(throwSummary(session), style = MaterialTheme.typography.bodySmall, color = theme.textMid)
        }
    }
    if (outcome != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            "stats ${outcome.owned.statPercent} percent, ${outcome.playerXp} player XP" +
                if (outcome.ljosGained > 0) ", ${outcome.ljosGained} Ljós" else "",
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
        )
        if (outcome.isNewToCodex) {
            Spacer(Modifier.height(3.dp))
            Text("First of its kind for you", style = MaterialTheme.typography.bodySmall, color = theme.accent)
        }
        outcome.levelledUpTo?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                "Level $it reached, more slots and a bundle of capsules",
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            session.def.flavor,
            style = serifFlavor(LocalFontScale.current, theme),
        )
    }
}

/** One sentence describing how the throw was graded, reused by both result bodies. */
private fun throwSummary(session: CatchSession): String {
    val result = session.throwResult ?: return "no throw"
    return buildList {
        add(result.ring.label)
        if (result.curved) add("curved")
        if (result.upright) add("upright")
        if (result.favouredBiome) add("at home here")
    }.joinToString(", ")
}

/** The capsule is a cylinder seen side on, so the tumble reads clearly. */
private fun DrawScope.drawCapsule(centre: Offset, halfHeight: Float, turns: Float, body: Color, core: Color) {
    val halfWidth = halfHeight * 0.62f
    rotate(turns * 360f, centre) {
        drawRoundRect(
            color = body,
            topLeft = Offset(centre.x - halfWidth, centre.y - halfHeight),
            size = Size(halfWidth * 2, halfHeight * 2),
            cornerRadius = CornerRadius(halfWidth * 0.45f),
        )
        // The band marks which way up the cylinder is, which is what makes an upright
        // landing something you can actually see rather than just be told about.
        drawRoundRect(
            color = core,
            topLeft = Offset(centre.x - halfWidth, centre.y - halfHeight * 0.18f),
            size = Size(halfWidth * 2, halfHeight * 0.36f),
            cornerRadius = CornerRadius(halfWidth * 0.16f),
        )
        drawCircle(body, halfWidth * 0.3f, centre)
    }
}

/** The arc the capsule travels, bent sideways by how much the flick curved. */
private fun flightPoint(
    from: Offset,
    to: Offset,
    curve: Float,
    progress: Float,
    width: Float,
): Offset {
    val straightX = from.x + (to.x - from.x) * progress
    val straightY = from.y + (to.y - from.y) * progress
    // A sine bulge gives a smooth arc that starts and ends on the straight line.
    val bulge = kotlin.math.sin(progress * Math.PI).toFloat()
    return Offset(straightX + curve * width * CURVE_TRAVEL_SHARE * bulge, straightY)
}

private fun ringColor(hit: RingHit, accent: Color, gold: Color): Color = when (hit) {
    RingHit.PERFECT -> gold
    RingHit.GOOD -> accent
    else -> accent.copy(alpha = 0.6f)
}

/** Each biome tints the backdrop, so where you are is visible during the encounter. */
private fun biomeBackdrop(biome: Biome, fallback: Color): Color = when (biome) {
    Biome.WATER -> Color(0xFF123240)
    Biome.GREEN -> Color(0xFF102A1B)
    Biome.DRY -> Color(0xFF2A2312)
    Biome.URBAN -> fallback
}

private const val RING_CYCLE_MILLIS = 1500
private const val RING_WIDEST = 1.0f
private const val RING_TIGHTEST = 0.2f
private const val STEADY_RING_FRACTION = 0.5f
private const val RING_OUTER_SHARE = 0.34f
private const val CREATURE_HEIGHT_SHARE = 0.40f
private const val CREATURE_RADIUS_SHARE = 0.11f
private const val CAPSULE_HEIGHT_SHARE = 0.80f
private const val CAPSULE_SIZE_SHARE = 0.055f
private const val FLIGHT_MILLIS = 620
private const val MIN_FLICK_PX = 40f
private const val FLICK_FULL_SHARE = 0.42f
private const val CURVE_TRAVEL_SHARE = 0.22f
