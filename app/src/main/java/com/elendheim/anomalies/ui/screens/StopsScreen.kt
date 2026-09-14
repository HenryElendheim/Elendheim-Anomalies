package com.elendheim.anomalies.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.EmptyState
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.Glyph
import com.elendheim.anomalies.ui.common.GlyphIcon
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Places the player put down by hand, with the lifetime numbers for each one. */
@Composable
fun StopsScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StopEntity?>(null) }
    var placing by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = "Stops",
            subtitle = "${state.stops.size} placed, ${state.stops.sumOf { it.spins }} total spins",
            trailing = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.surface)
                        .clickable { placing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.PLUS, theme.accent, 18.dp, contentDescription = "Place a stop here")
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
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Sizes.gutter, end = Sizes.gutter, bottom = Sizes.gutter,
                ),
                verticalArrangement = Arrangement.spacedBy(Sizes.gap),
            ) {
                items(state.stops, key = { it.id }) { stop ->
                    StopCard(
                        stop = stop,
                        distanceMeters = state.fix?.let {
                            Geo.distanceMeters(it.lat, it.lng, stop.lat, stop.lng)
                        },
                        showDistance = state.settings.showDistances,
                        dateFormat = dateFormat,
                        onSpin = { viewModel.spinStop(stop.id) },
                        onEdit = { editing = stop },
                    )
                }
            }
        }
    }

    if (placing) {
        StopDialog(
            title = "Place a stop here",
            initial = null,
            confirmLabel = "Place",
            onDismiss = { placing = false },
            onConfirm = { name, elendian, radius, cooldown ->
                viewModel.placeStop(name, elendian, radius, cooldown)
                placing = false
            },
        )
    }

    editing?.let { stop ->
        StopDialog(
            title = stop.name,
            initial = stop,
            confirmLabel = "Save",
            onDelete = {
                viewModel.deleteStop(stop.id)
                editing = null
            },
            onDismiss = { editing = null },
            onConfirm = { name, elendian, radius, cooldown ->
                viewModel.updateStop(
                    stop.copy(
                        name = name,
                        elendianName = elendian,
                        radiusMeters = radius,
                        cooldownMinutes = cooldown,
                    )
                )
                editing = null
            },
        )
    }
}

@Composable
private fun StopCard(
    stop: StopEntity,
    distanceMeters: Double?,
    showDistance: Boolean,
    dateFormat: SimpleDateFormat,
    onSpin: () -> Unit,
    onEdit: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val readyIn = stop.cooldownMinutes * 60_000L - (now - stop.lastSpunAt)
    val ready = readyIn <= 0
    val inRange = distanceMeters == null || distanceMeters <= stop.radiusMeters

    ElCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleSmall, color = theme.text)
                stop.elendianName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = theme.textMid)
                }
            }
            Text(
                if (ready) "ready" else "${(readyIn / 60_000L) + 1} min",
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) theme.accent else theme.gold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("created ")
                append(dateFormat.format(Date(stop.createdAt)))
                append(", ")
                append(stop.radiusMeters)
                append(" m radius")
                if (showDistance && distanceMeters != null) {
                    append(", ")
                    append(Geo.format(distanceMeters))
                    append(" away")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "${stop.spins} spins, ${stop.itemsEarned} items, ${stop.bonusSpawns} bonus spawns",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMid,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = when {
                !inRange -> "Too far away"
                !ready -> "On cooldown"
                else -> "Spin"
            },
            enabled = ready && inRange,
            onClick = onSpin,
        )
    }
}

/** One dialog used for both placing and editing, so the two can never drift apart. */
@Composable
private fun StopDialog(
    title: String,
    initial: StopEntity?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Int, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var elendian by remember { mutableStateOf(initial?.elendianName.orEmpty()) }
    var radius by remember { mutableStateOf((initial?.radiusMeters ?: 60).toFloat()) }
    var cooldown by remember { mutableStateOf((initial?.cooldownMinutes ?: 5).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        titleContentColor = theme.text,
        textContentColor = theme.textMid,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = theme.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = elendian,
                    onValueChange = { elendian = it },
                    label = { Text("Elendian name, optional") },
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
                Slider(
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
                Slider(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    valueRange = 1f..60f,
                    colors = dialogSliderColors(),
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Delete this stop", tint = theme.danger) { onDelete() }
                }
            }
        },
        confirmButton = {
            Text(
                confirmLabel,
                style = MaterialTheme.typography.titleSmall,
                color = theme.accent,
                modifier = Modifier
                    .clickable {
                        onConfirm(
                            name.ifBlank { "Stop" },
                            elendian.takeIf { it.isNotBlank() },
                            radius.roundToInt(),
                            cooldown.roundToInt(),
                        )
                    }
                    .padding(10.dp),
            )
        },
        dismissButton = {
            Text(
                "Cancel",
                style = MaterialTheme.typography.titleSmall,
                color = theme.textDim,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp),
            )
        },
    )
}

@Composable
private fun dialogFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = theme.surface,
    unfocusedContainerColor = theme.surface,
    focusedTextColor = theme.text,
    unfocusedTextColor = theme.text,
    focusedIndicatorColor = theme.accent,
    unfocusedIndicatorColor = theme.border,
    focusedLabelColor = theme.accent,
    unfocusedLabelColor = theme.textDim,
    cursorColor = theme.accent,
)

@Composable
private fun dialogSliderColors() = SliderDefaults.colors(
    thumbColor = theme.accent,
    activeTrackColor = theme.accent,
    inactiveTrackColor = theme.borderDim,
)
