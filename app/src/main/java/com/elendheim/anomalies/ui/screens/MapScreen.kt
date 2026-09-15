package com.elendheim.anomalies.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.MeterBar
import com.elendheim.anomalies.ui.common.Pill
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.common.dialogFieldColors
import com.elendheim.anomalies.ui.common.dialogSliderColors
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.game.StopDraft
import com.elendheim.anomalies.ui.map.MapBackdrop
import com.elendheim.anomalies.ui.map.MapCamera
import com.elendheim.anomalies.ui.map.MapProjection
import com.elendheim.anomalies.ui.theme.theme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Where something ended up on screen, so drawing and tapping use the same numbers. */
private data class Marker(val position: Offset, val spawn: Spawn?, val stop: StopEntity?)

/**
 * The home screen. Real map tiles under the game, with the player at the centre, the
 * radius anomalies appear in drawn at its true size, and the stops that are close enough
 * to walk to. Pinch to zoom, twist to turn, drag to look around.
 */
@Composable
fun MapScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.stopDraft.collectAsStateWithLifecycle()
    val colors = theme
    val density = LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> viewModel.onPermissionResult(granted.values.any { it }) }

    // Tracking only runs while this screen is on show, which keeps the battery cost of
    // the app the same as the time actually spent looking at it.
    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose { viewModel.stopTracking() }
    }

    LaunchedEffect(Unit) {
        // Waiting out the splash means the system permission dialog never lands on top
        // of the opening word.
        delay(PERMISSION_PROMPT_DELAY_MILLIS)
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val fix = state.fix
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var camera by remember { mutableStateOf<MapCamera?>(null) }

    // The camera rides along with the player until the map is dragged, and then it stays
    // where it was put until the recentre button is pressed.
    LaunchedEffect(fix?.lat, fix?.lng, canvasSize, state.effects.spawnRadiusMeters) {
        val position = fix ?: return@LaunchedEffect
        if (canvasSize == Size.Zero) return@LaunchedEffect
        val current = camera
        camera = when {
            current == null -> MapCamera(
                centerLat = position.lat,
                centerLng = position.lng,
                zoom = MapProjection.zoomFittingRadius(
                    lat = position.lat,
                    radiusMeters = state.effects.spawnRadiusMeters,
                    canvas = canvasSize,
                    devicePixelRatio = density.density,
                ),
                devicePixelRatio = density.density,
            )
            current.followPlayer -> current.copy(centerLat = position.lat, centerLng = position.lng)
            else -> current
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        val liveCamera = camera
        if (fix == null || liveCamera == null) {
            NoPositionState(
                hasPermission = state.hasLocationPermission,
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                },
            )
            return@Box
        }

        MapBackdrop(
            camera = liveCamera,
            detailed = state.settings.detailedMap,
            biome = state.biome,
        )

        // Stops further away than this are left off entirely. It keeps the screen honest
        // about what is actually walkable, and keeps the drawing cheap when a lot of
        // stops have been placed over time.
        val nearbyStops = remember(state.stops, fix.lat, fix.lng) {
            state.stops.filter {
                Geo.distanceMeters(fix.lat, fix.lng, it.lat, it.lng) <= STOP_VISIBLE_RANGE_METERS
            }
        }
        val hiddenStops = state.stops.size - nearbyStops.size

        val markers = remember(state.spawns, nearbyStops, liveCamera, canvasSize) {
            if (canvasSize == Size.Zero) {
                emptyList()
            } else {
                state.spawns.map {
                    Marker(MapProjection.toScreen(liveCamera, it.lat, it.lng, canvasSize), it, null)
                } + nearbyStops.map {
                    Marker(MapProjection.toScreen(liveCamera, it.lat, it.lng, canvasSize), null, it)
                }
            }
        }

        val placing = draft != null
        val markerScale = MapProjection.markerScale(liveCamera.zoom)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(liveCamera, canvasSize, placing) {
                    // Pinch, twist and drag all arrive together, so one handler keeps
                    // the three of them consistent with each other.
                    detectTransformGestures { _, pan, pinch, rotationChange ->
                        val current = camera ?: return@detectTransformGestures
                        val moved = pan.getDistance() > PAN_BREAKS_FOLLOW_PX
                        camera = MapProjection.panned(current, pan)
                            .copy(followPlayer = current.followPlayer && !moved)
                            .withZoom(current.zoom + MapProjection.zoomSteps(pinch))
                            // Twisting the fingers clockwise should turn the map clockwise,
                            // and the map turns clockwise as the bearing goes down.
                            .withBearing(current.bearingDegrees - rotationChange)
                    }
                }
                .pointerInput(markers, placing, liveCamera, canvasSize) {
                    detectTapGestures { tap ->
                        if (placing) {
                            // Anywhere on the map is a valid spot, so a tap simply moves
                            // the marker there and the panel handles the rest.
                            val (lat, lng) = MapProjection.toWorld(liveCamera, tap, canvasSize)
                            viewModel.movePlacement(lat, lng)
                            return@detectTapGestures
                        }
                        val hit = markers.minByOrNull { (it.position - tap).getDistance() }
                        if (hit == null || (hit.position - tap).getDistance() > TAP_SLOP_PX) return@detectTapGestures
                        hit.spawn?.let { viewModel.beginCatch(it) }
                        hit.stop?.let { viewModel.spinStop(it.id) }
                    }
                }
        ) {
            val playerPosition = MapProjection.toScreen(liveCamera, fix.lat, fix.lng, canvasSize)

            // The ring is a real distance, so it grows and shrinks with the zoom exactly
            // as the ground does.
            val ringRadius = MapProjection.metersToPixels(liveCamera, state.effects.spawnRadiusMeters)
            drawCircle(colors.accent.copy(alpha = 0.10f), ringRadius, playerPosition)
            drawCircle(colors.accent.copy(alpha = 0.35f), ringRadius, playerPosition, style = Stroke(width = 1.5f))

            markers.forEach { marker ->
                val stop = marker.stop ?: return@forEach
                val catchment = MapProjection.metersToPixels(liveCamera, stop.radiusMeters.toDouble())
                drawCircle(colors.accent.copy(alpha = 0.07f), catchment, marker.position)
                val half = STOP_HALF_PX * markerScale
                rotate(45f, marker.position) {
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = marker.position - Offset(half, half),
                        size = Size(half * 2, half * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                    )
                    drawRoundRect(
                        color = colors.backgroundDeep,
                        topLeft = marker.position - Offset(half / 2, half / 2),
                        size = Size(half, half),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                    )
                }
            }

            // The player, facing whichever way the map is turned.
            val playerRadius = PLAYER_RADIUS_PX * markerScale
            drawCircle(colors.textMid, playerRadius, playerPosition)
            drawCircle(colors.backgroundDeep, playerRadius, playerPosition, style = Stroke(width = 3f))

            // The stop being placed, shown with the catchment it will actually have.
            draft?.let { pending ->
                val position = MapProjection.toScreen(liveCamera, pending.lat, pending.lng, canvasSize)
                val catchment = MapProjection.metersToPixels(liveCamera, pending.radiusMeters.toDouble())
                drawCircle(colors.gold.copy(alpha = 0.12f), catchment, position)
                drawCircle(colors.gold.copy(alpha = 0.6f), catchment, position, style = Stroke(width = 2f))
                val half = STOP_HALF_PX * markerScale
                rotate(45f, position) {
                    drawRoundRect(
                        color = colors.gold,
                        topLeft = position - Offset(half, half),
                        size = Size(half * 2, half * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                    )
                }
                drawLine(colors.gold, position - Offset(22f, 0f), position + Offset(22f, 0f), 1.5f)
                drawLine(colors.gold, position - Offset(0f, 22f), position + Offset(0f, 22f), 1.5f)
            }
        }

        if (!placing) {
            // Creature sprites sit above the canvas so they can carry the idle motion.
            markers.forEach { marker ->
                val spawn = marker.spawn ?: return@forEach
                val def = viewModel.definitionOf(spawn.creatureId) ?: return@forEach
                val sizeDp = SPRITE_BASE_DP.dp * markerScale
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

            StopLabels(markers, state.settings.showDistances, fix.lat, fix.lng, markerScale)

            CompanionChip(viewModel, Modifier.align(Alignment.TopStart))
            MapControls(
                camera = liveCamera,
                biome = state.biome.label,
                band = state.timeBand.label,
                hiddenStops = hiddenStops,
                onZoom = { step -> camera = liveCamera.withZoom(liveCamera.zoom + step) },
                onNorthUp = { camera = liveCamera.withBearing(0f) },
                onRecentre = {
                    camera = liveCamera.copy(
                        centerLat = fix.lat,
                        centerLng = fix.lng,
                        followPlayer = true,
                    )
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
            MapCredit(Modifier.align(Alignment.BottomStart))
        }

        draft?.let { pending ->
            PlacementPanel(
                draft = pending,
                onName = { viewModel.editDraft(name = it) },
                onRadius = { viewModel.editDraft(radiusMeters = it) },
                onCooldown = { viewModel.editDraft(cooldownMinutes = it) },
                onConfirm = { viewModel.confirmPlacement() },
                onCancel = { viewModel.cancelPlacement() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
        ElCard(modifier = Modifier.width(186.dp), contentPadding = 9.dp) {
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

/** Zoom, north up and recentre, plus what the world is doing right now. */
@Composable
private fun MapControls(
    camera: MapCamera,
    biome: String,
    band: String,
    hiddenStops: Int,
    onZoom: (Float) -> Unit,
    onNorthUp: () -> Unit,
    onRecentre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.statusBarsPadding().padding(Sizes.gutter),
        horizontalAlignment = Alignment.End,
    ) {
        ElCard(modifier = Modifier.width(104.dp), contentPadding = 8.dp, borderColor = theme.borderDim) {
            Text(biome, style = MaterialTheme.typography.labelMedium, color = theme.textMid)
            Text(band, style = MaterialTheme.typography.labelSmall, color = theme.textDim)
            Text(
                "zoom ${"%.1f".format(camera.zoom)}, ${"%.2f".format(camera.metersPerPixel)} m per pixel",
                style = MaterialTheme.typography.labelSmall,
                color = theme.borderDim,
            )
        }
        Spacer(Modifier.height(Sizes.gap))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (camera.bearingDegrees != 0f) {
                Compass(bearingDegrees = camera.bearingDegrees, onClick = onNorthUp)
            }
            Pill("zoom in", selected = false, onClick = { onZoom(ZOOM_BUTTON_STEP) })
            Pill("zoom out", selected = false, onClick = { onZoom(-ZOOM_BUTTON_STEP) })
            if (!camera.followPlayer) {
                Pill("recentre", selected = true, onClick = onRecentre)
            }
            if (hiddenStops > 0) {
                Text(
                    "$hiddenStops stop${if (hiddenStops == 1) "" else "s"} further than " +
                        Geo.format(STOP_VISIBLE_RANGE_METERS),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}

/**
 * A needle showing which way north has gone once the map has been turned, and a way back.
 * It only appears while the map is actually turned, so a north up map stays uncluttered.
 */
@Composable
private fun Compass(bearingDegrees: Float, onClick: () -> Unit) {
    val colors = theme
    Canvas(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(Sizes.hairline, colors.border, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Map turned ${bearingDegrees.roundToInt()} degrees, tap for north up"
            }
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val reach = size.minDimension * 0.30f
        // The needle points where north actually is, so turning the map turns the needle
        // the opposite way to the ground.
        rotate(-bearingDegrees, centre) {
            drawPath(
                path = Path().apply {
                    moveTo(centre.x, centre.y - reach)
                    lineTo(centre.x - reach * 0.5f, centre.y + reach * 0.62f)
                    lineTo(centre.x, centre.y + reach * 0.28f)
                    close()
                },
                color = colors.accent,
            )
            drawPath(
                path = Path().apply {
                    moveTo(centre.x, centre.y - reach)
                    lineTo(centre.x + reach * 0.5f, centre.y + reach * 0.62f)
                    lineTo(centre.x, centre.y + reach * 0.28f)
                    close()
                },
                color = colors.textDim,
            )
        }
    }
}

/** The map data has to be credited wherever it is shown. */
@Composable
private fun MapCredit(modifier: Modifier = Modifier) {
    Text(
        "Map data from OpenStreetMap contributors",
        style = MaterialTheme.typography.labelSmall,
        color = theme.textDim,
        modifier = modifier.padding(start = Sizes.gutter, bottom = 6.dp),
    )
}

/** Stop names sit under their markers, with a distance when that setting is on. */
@Composable
private fun StopLabels(
    markers: List<Marker>,
    showDistances: Boolean,
    lat: Double,
    lng: Double,
    markerScale: Float,
) {
    markers.forEach { marker ->
        val stop = marker.stop ?: return@forEach
        val distance = Geo.distanceMeters(lat, lng, stop.lat, stop.lng)
        Box(
            Modifier.offset {
                IntOffset(
                    (marker.position.x - 60f).roundToInt(),
                    (marker.position.y + 14f * markerScale).roundToInt(),
                )
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

/**
 * The panel that sits over the map while a stop is being positioned. It asks for a spot
 * first and only offers Confirm once one has been chosen.
 */
@Composable
private fun PlacementPanel(
    draft: StopDraft,
    onName: (String) -> Unit,
    onRadius: (Int) -> Unit,
    onCooldown: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onCancel() }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(Sizes.gutter),
    ) {
        ElCard(borderColor = theme.gold) {
            Text(
                if (draft.placed) "Drag to nudge it, or tap somewhere else" else "Tap the map where this stop goes",
                style = MaterialTheme.typography.titleSmall,
                color = theme.text,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (draft.placed) {
                    "${draft.radiusMeters} m catchment, ${draft.cooldownMinutes} min cooldown"
                } else {
                    "It can go anywhere, not just where you are standing"
                },
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
            )

            if (draft.placed) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onName,
                    label = { Text("Name") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    if (expanded) "Hide the dials" else "Adjust radius and cooldown",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.accent,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(vertical = 6.dp),
                )

                if (expanded) {
                    Text(
                        "Radius ${draft.radiusMeters} m",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textMid,
                    )
                    Slider(
                        value = draft.radiusMeters.toFloat(),
                        onValueChange = { onRadius(it.roundToInt()) },
                        valueRange = 25f..250f,
                        colors = dialogSliderColors(),
                    )
                    Text(
                        "Cooldown ${draft.cooldownMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textMid,
                    )
                    Slider(
                        value = draft.cooldownMinutes.toFloat(),
                        onValueChange = { onCooldown(it.roundToInt()) },
                        valueRange = 1f..60f,
                        colors = dialogSliderColors(),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (draft.placed) "Place it here" else "Pick a spot first",
                modifier = Modifier.weight(1f),
                enabled = draft.placed,
                tone = theme.gold,
                onClick = onConfirm,
            )
            GhostButton("Cancel", Modifier.weight(1f)) { onCancel() }
        }
    }
}

/** Anything beyond this is left off the map until you are closer to it. */
const val STOP_VISIBLE_RANGE_METERS = 500.0

private const val TAP_SLOP_PX = 70f
private const val PLAYER_RADIUS_PX = 13f
private const val STOP_HALF_PX = 11f
private const val SPRITE_BASE_DP = 40
private const val ZOOM_BUTTON_STEP = 0.75f
private const val PAN_BREAKS_FOLLOW_PX = 6f
private const val PERMISSION_PROMPT_DELAY_MILLIS = 1500L
