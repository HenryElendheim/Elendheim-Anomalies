package com.elendheim.anomalies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.ui.common.EmptyState
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.Glyph
import com.elendheim.anomalies.ui.common.GlyphIcon
import com.elendheim.anomalies.ui.common.MeterBar
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.common.dialogFieldColors
import com.elendheim.anomalies.ui.common.dialogSliderColors
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The stops, sorted so anything ready to spin is a large square at the top and anything
 * counting down is a small grey strip underneath. The shape alone tells you what is
 * worth walking to without reading a single number.
 */
@Composable
fun StopsScreen(viewModel: GameViewModel, onPlaceStop: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StopEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    // A once a second tick so every countdown on screen runs down live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val ready = state.stops.filter { it.isReadyAt(now) }
    val waiting = state.stops.filterNot { it.isReadyAt(now) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = "Stops",
            subtitle = "${state.stops.size} placed, ${ready.size} ready, ${state.stops.sumOf { it.spins }} total spins",
            trailing = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.surface)
                        .clickable { onPlaceStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.PLUS, theme.accent, 18.dp, contentDescription = "Place a new stop")
                }
            },
        )

        if (state.stops.isEmpty()) {
            EmptyState(
                title = "No stops yet",
                message = "Put one down where you actually wait: home, the platform, the gate. " +
                    "Each one hands over capsules every few minutes.",
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.padding(Sizes.gutter)) {
                PrimaryButton("Place your first stop") { onPlaceStop() }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = Sizes.gutter, end = Sizes.gutter, bottom = Sizes.gutter),
                horizontalArrangement = Arrangement.spacedBy(Sizes.gap),
                verticalArrangement = Arrangement.spacedBy(Sizes.gap),
            ) {
                items(ready, key = { "ready-${it.id}" }) { stop ->
                    ReadyStopTile(
                        stop = stop,
                        distanceMeters = state.fix?.let { Geo.distanceMeters(it.lat, it.lng, stop.lat, stop.lng) },
                        showDistance = state.settings.showDistances,
                        onSpin = { viewModel.spinStop(stop.id) },
                        onEdit = { editing = stop },
                    )
                }
                if (waiting.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Counting down",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    }
                }
                items(waiting, span = { GridItemSpan(maxLineSpan) }, key = { "waiting-${it.id}" }) { stop ->
                    WaitingStopStrip(
                        stop = stop,
                        now = now,
                        dateFormat = dateFormat,
                        onEdit = { editing = stop },
                    )
                }
            }
        }
    }

    editing?.let { stop ->
        EditStopDialog(
            stop = stop,
            onDelete = {
                viewModel.deleteStop(stop.id)
                editing = null
            },
            onDismiss = { editing = null },
            onConfirm = { name, radius, cooldown ->
                viewModel.updateStop(stop.copy(name = name, radiusMeters = radius, cooldownMinutes = cooldown))
                editing = null
            },
        )
    }
}

/** True when the cooldown has run out and the stop can be spun again. */
private fun StopEntity.isReadyAt(now: Long): Boolean =
    now - lastSpunAt >= cooldownMinutes * 60_000L

/** Milliseconds still to wait, never below zero. */
private fun StopEntity.remainingAt(now: Long): Long =
    (cooldownMinutes * 60_000L - (now - lastSpunAt)).coerceAtLeast(0L)

/** A ready stop: a full square, lit in the accent colour, with the spin button on it. */
@Composable
private fun ReadyStopTile(
    stop: StopEntity,
    distanceMeters: Double?,
    showDistance: Boolean,
    onSpin: () -> Unit,
    onEdit: () -> Unit,
) {
    val inRange = distanceMeters == null || distanceMeters <= stop.radiusMeters
    val shape = RoundedCornerShape(Sizes.corner)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(theme.card)
            .border(Sizes.hairline, theme.accent, shape)
            .clickable(onClick = onEdit)
            .padding(12.dp),
    ) {
        Text(
            stop.name,
            style = MaterialTheme.typography.titleSmall,
            color = theme.text,
            maxLines = 2,
        )
        Spacer(Modifier.height(3.dp))
        Text("ready", style = MaterialTheme.typography.labelMedium, color = theme.accent)

        Spacer(Modifier.weight(1f))

        Text(
            "${stop.spins} spins",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMid,
        )
        Text(
            "${stop.itemsEarned} items",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
        )
        if (showDistance && distanceMeters != null) {
            Text(
                Geo.format(distanceMeters),
                style = MaterialTheme.typography.labelSmall,
                color = if (inRange) theme.accent else theme.textDim,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (inRange) "Spin" else "Too far",
            enabled = inRange,
            onClick = onSpin,
        )
    }
}

/** A waiting stop: a short grey strip carrying the countdown and a bar that drains. */
@Composable
private fun WaitingStopStrip(
    stop: StopEntity,
    now: Long,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
) {
    val remaining = stop.remainingAt(now)
    val total = (stop.cooldownMinutes * 60_000L).coerceAtLeast(1L)
    // Everything here is dimmed on purpose, so an unavailable stop reads as switched off.
    val dim = theme.textDim
    val shape = RoundedCornerShape(Sizes.cornerSmall)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surface)
            .border(Sizes.hairline, theme.borderDim, shape)
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleSmall, color = dim, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${stop.spins} spins, placed ${dateFormat.format(Date(stop.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.borderDim,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                formatCountdown(remaining),
                style = MaterialTheme.typography.titleMedium,
                color = dim,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(7.dp))
        MeterBar(
            progress = 1f - (remaining.toFloat() / total.toFloat()),
            color = dim,
            track = theme.borderDim,
            height = 3.dp,
            label = "${stop.name} ready in ${formatCountdown(remaining)}",
        )
    }
}

/** Minutes and seconds while it is close, whole minutes while it is not. */
private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Renaming and retuning an existing stop. Placement itself happens on the map. */
@Composable
private fun EditStopDialog(
    stop: StopEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(stop.name) }
    var radius by remember { mutableStateOf(stop.radiusMeters.toFloat()) }
    var cooldown by remember { mutableStateOf(stop.cooldownMinutes.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        titleContentColor = theme.text,
        textContentColor = theme.textMid,
        title = { Text(stop.name, style = MaterialTheme.typography.titleMedium, color = theme.text) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Radius ${radius.roundToInt()} m",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMid,
                )
                androidx.compose.material3.Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 25f..250f,
                    colors = dialogSliderColors(),
                )
                Text(
                    "Cooldown ${cooldown.roundToInt()} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMid,
                )
                androidx.compose.material3.Slider(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    valueRange = 1f..60f,
                    colors = dialogSliderColors(),
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Delete this stop", tint = theme.danger) { onDelete() }
            }
        },
        confirmButton = {
            DialogAction("Save", theme.accent) {
                onConfirm(name.ifBlank { "Stop" }, radius.roundToInt(), cooldown.roundToInt())
            }
        },
        dismissButton = { DialogAction("Cancel", theme.textDim, onDismiss) },
    )
}

/** The plain text buttons both dialogs use, so they always look the same. */
@Composable
internal fun DialogAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = tint,
        modifier = Modifier.clickable(onClick = onClick).padding(10.dp),
    )
}
