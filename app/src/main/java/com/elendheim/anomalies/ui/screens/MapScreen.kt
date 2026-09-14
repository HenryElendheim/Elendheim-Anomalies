package com.elendheim.anomalies.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.game.Spawn
import com.elendheim.anomalies.ui.common.CreatureSprite
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.MeterBar
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Where something ended up on screen, so drawing and tapping use the same numbers. */
private data class Marker(val position: Offset, val spawn: Spawn?, val stop: StopEntity?, val clamped: Boolean)

/**
 * The home screen. A stylised local map centred on the player, showing the radius
 * anomalies appear in, the stops nearby and whatever is currently standing around.
 */
@Composable
fun MapScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = theme
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.onPermissionResult(granted.values.any { it })
    }

    // Tracking only runs while this screen is on show, which keeps the battery cost of
    // the app the same as the time actually spent looking at it.
    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose { viewModel.stopTracking() }
    }

    LaunchedEffect(Unit) {
        if (!state.hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val fix = state.fix
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    // The pattern of roads is anchored to the first fix of the session, so walking makes
    // the ground slide past instead of the world reshuffling under the player.
    val anchor = remember(fix != null) { fix }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (fix == null) {
            NoPositionState(
                hasPermission = state.hasLocationPermission,
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                },
            )
        } else {
            val radiusMeters = state.effects.spawnRadiusMeters
            val markers = remember(state.spawns, state.stops, fix, canvasSize, radiusMeters) {
                buildMarkers(state.spawns, state.stops, fix.lat, fix.lng, canvasSize, radiusMeters)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(markers) {
                        detectTapGestures { tap ->
                            val hit = markers.minByOrNull { (it.position - tap).getDistance() }
                            if (hit == null || (hit.position - tap).getDistance() > TAP_SLOP_PX) return@detectTapGestures
                            hit.spawn?.let { viewModel.beginCatch(it) }
                            hit.stop?.let { viewModel.spinStop(it.id) }
                        }
                    }
            ) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val pixelsPerMeter = (size.minDimension * RADIUS_SCREEN_SHARE) / radiusMeters.toFloat()

                drawGround(
                    anchorLat = anchor?.lat ?: fix.lat,
                    anchorLng = anchor?.lng ?: fix.lng,
                    lat = fix.lat,
                    lng = fix.lng,
                    pixelsPerMeter = pixelsPerMeter,
                    road = colors.road,
                    water = colors.water,
                    isWaterBiome = state.biome == com.elendheim.anomalies.game.Biome.WATER,
                )

                // The ring showing how far out anomalies can turn up.
                val ringRadius = radiusMeters.toFloat() * pixelsPerMeter
                drawCircle(colors.accent.copy(alpha = 0.10f), ringRadius, centre)
                drawCircle(colors.accent.copy(alpha = 0.35f), ringRadius, centre, style = Stroke(width = 1.5f))

                markers.forEach { marker ->
                    val stop = marker.stop
                    if (stop != null) {
                        rotate(45f, marker.position) {
                            drawRoundRect(
                                color = colors.accent,
                                topLeft = marker.position - Offset(STOP_HALF_PX, STOP_HALF_PX),
                                size = androidx.compose.ui.geometry.Size(STOP_HALF_PX * 2, STOP_HALF_PX * 2),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                            )
                            drawRoundRect(
                                color = colors.backgroundDeep,
                                topLeft = marker.position - Offset(STOP_HALF_PX / 2, STOP_HALF_PX / 2),
                                size = androidx.compose.ui.geometry.Size(STOP_HALF_PX, STOP_HALF_PX),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                            )
                        }
                    }
                }

                // The player sits in the middle and the world moves around them.
                drawCircle(colors.textMid, PLAYER_RADIUS_PX, centre)
                drawCircle(colors.backgroundDeep, PLAYER_RADIUS_PX, centre, style = Stroke(width = 3f))
            }

            // Creature sprites sit above the canvas so they can carry the idle motion.
            markers.forEach { marker ->
                val spawn = marker.spawn ?: return@forEach
                val def = remember(spawn.creatureId) { viewModel.definitionOf(spawn.creatureId) } ?: return@forEach
                val sizeDp = 40.dp
                val halfPx = with(density) { sizeDp.toPx() } / 2f
                Box(
                    Modifier.offset {
                        IntOffset(
                            (marker.position.x - halfPx).roundToInt(),
                            (marker.position.y - halfPx).roundToInt(),
                        )
                    }
                ) {
                    CreatureSprite(def = def, isShiny = spawn.isShiny, size = sizeDp, bob = true)
                }
            }

            StopLabels(markers, state.settings.showDistances, fix.lat, fix.lng)
        }

        CompanionChip(viewModel, Modifier.align(Alignment.TopStart))
        BiomeChip(
            biome = state.biome.label,
            band = state.timeBand.label,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** The chip showing what is out and how close it is to its next form. */
@Composable
private fun CompanionChip(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = state.companion ?: return
    val def = viewModel.definitionOf(companion.creatureId) ?: return
    val progress = if (def.metamorphXp > 0) companion.xp.toFloat() / def.metamorphXp else 1f

    Box(modifier.statusBarsPadding().padding(Sizes.gutter)) {
        ElCard(modifier = Modifier.width(190.dp), contentPadding = 9.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreatureSprite(def, companion.isShiny, 30.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        companion.nickname ?: def.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (def.metamorphoses) "stage ${companion.stage + 1}" else "stage ${companion.stage + 1}, settled",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim,
                    )
                    if (def.metamorphoses) {
                        Spacer(Modifier.height(4.dp))
                        MeterBar(progress, label = "Companion progress to next form")
                    }
                }
            }
        }
    }
}

@Composable
private fun BiomeChip(biome: String, band: String, modifier: Modifier = Modifier) {
    Box(modifier.statusBarsPadding().padding(Sizes.gutter)) {
        ElCard(modifier = Modifier.width(96.dp), contentPadding = 8.dp, borderColor = theme.borderDim) {
            Text(biome, style = MaterialTheme.typography.labelMedium, color = theme.textMid)
            Text(band, style = MaterialTheme.typography.labelSmall, color = theme.textDim)
        }
    }
}

/** Stop names sit under their markers, with a distance when that setting is on. */
@Composable
private fun StopLabels(markers: List<Marker>, showDistances: Boolean, lat: Double, lng: Double) {
    markers.forEach { marker ->
        val stop = marker.stop ?: return@forEach
        val distance = Geo.distanceMeters(lat, lng, stop.lat, stop.lng)
        Box(
            Modifier.offset {
                IntOffset((marker.position.x - 60f).roundToInt(), (marker.position.y + 16f).roundToInt())
            }
        ) {
            Column(
                modifier = Modifier.width(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stop.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMid,
                    textAlign = TextAlign.Center,
                )
                if (showDistances) {
                    Text(
                        Geo.format(distance),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (distance <= stop.radiusMeters) theme.accent else theme.textDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoPositionState(hasPermission: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Sizes.gutter * 2),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasPermission) "Looking for you" else "The map needs your position",
            style = MaterialTheme.typography.headlineSmall,
            color = theme.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasPermission) {
                "Hold still for a moment. Anomalies appear once there is a fix."
            } else {
                "Location is used only on this device, to place stops and to find what is nearby."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            textAlign = TextAlign.Center,
        )
        if (!hasPermission) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Allow location", Modifier.width(220.dp)) { onRequest() }
        }
    }
}

/** Works out where every spawn and stop lands on screen, clamping far stops to the edge. */
private fun buildMarkers(
    spawns: List<Spawn>,
    stops: List<StopEntity>,
    lat: Double,
    lng: Double,
    canvas: Offset,
    radiusMeters: Double,
): List<Marker> {
    if (canvas == Offset.Zero) return emptyList()
    val centre = Offset(canvas.x / 2f, canvas.y / 2f)
    val pixelsPerMeter = (minOf(canvas.x, canvas.y) * RADIUS_SCREEN_SHARE) / radiusMeters.toFloat()
    val limit = minOf(canvas.x, canvas.y) / 2f - EDGE_MARGIN_PX

    fun place(pointLat: Double, pointLng: Double): Pair<Offset, Boolean> {
        val (north, east) = Geo.toLocalMeters(lat, lng, pointLat, pointLng)
        var x = (east * pixelsPerMeter).toFloat()
        var y = (-north * pixelsPerMeter).toFloat()
        val length = hypot(x, y)
        var clamped = false
        if (length > limit && length > 0f) {
            x *= limit / length
            y *= limit / length
            clamped = true
        }
        return centre + Offset(x, y) to clamped
    }

    val spawnMarkers = spawns.map { spawn ->
        val (position, clamped) = place(spawn.lat, spawn.lng)
        Marker(position, spawn, null, clamped)
    }
    val stopMarkers = stops.map { stop ->
        val (position, clamped) = place(stop.lat, stop.lng)
        Marker(position, null, stop, clamped)
    }
    return spawnMarkers + stopMarkers
}

/** Draws the stylised ground: a few roads and, in a water biome, a stretch of water. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGround(
    anchorLat: Double,
    anchorLng: Double,
    lat: Double,
    lng: Double,
    pixelsPerMeter: Float,
    road: Color,
    water: Color,
    isWaterBiome: Boolean,
) {
    val (north, east) = Geo.toLocalMeters(anchorLat, anchorLng, lat, lng)
    // The ground slides opposite to the player, which is what gives a sense of walking.
    val shiftX = (-east * pixelsPerMeter).toFloat()
    val shiftY = (north * pixelsPerMeter).toFloat()

    if (isWaterBiome) {
        drawOval(
            color = water,
            topLeft = Offset(size.width * 0.42f + shiftX, size.height * 0.60f + shiftY),
            size = androidx.compose.ui.geometry.Size(size.width * 0.85f, size.height * 0.34f),
        )
    }

    val spacing = size.minDimension / 3.1f
    val thickness = size.minDimension / 52f
    // Two sets of lines crossing at a slight angle read as streets without pretending
    // to be real map data.
    for (index in -2..3) {
        val y = spacing * index + (shiftY % spacing)
        drawLine(
            road,
            Offset(0f, y + size.height * 0.18f),
            Offset(size.width, y + size.height * 0.30f),
            thickness,
            StrokeCap.Round,
        )
    }
    for (index in -1..3) {
        val x = spacing * index + (shiftX % spacing)
        drawLine(
            road,
            Offset(x + size.width * 0.14f, 0f),
            Offset(x + size.width * 0.24f, size.height),
            thickness * 0.85f,
            StrokeCap.Round,
        )
    }
}

private const val RADIUS_SCREEN_SHARE = 0.30f
private const val TAP_SLOP_PX = 70f
private const val PLAYER_RADIUS_PX = 13f
private const val STOP_HALF_PX = 11f
private const val EDGE_MARGIN_PX = 42f
