package com.elendheim.anomalies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.data.db.OwnedCreatureEntity
import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.ui.common.CreatureSprite
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.PrimaryButton
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.LocalFontScale
import com.elendheim.anomalies.ui.theme.serifFlavor
import com.elendheim.anomalies.ui.theme.theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The collection. Everything in the roster is listed, found or not. */
@Composable
fun CodexScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roster = remember { viewModel.allDefinitions() }
    var openId by remember { mutableStateOf<String?>(null) }

    val ownedByCreature = remember(state.owned) { state.owned.groupBy { it.creatureId } }
    val discovered = ownedByCreature.keys.size
    val shinySpecies = remember(state.owned) { state.owned.filter { it.isShiny }.map { it.creatureId }.distinct().size }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = "Codex",
            subtitle = "$discovered of ${roster.size} discovered, $shinySpecies shiny",
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Sizes.gutter, end = Sizes.gutter, bottom = Sizes.gutter,
            ),
            horizontalArrangement = Arrangement.spacedBy(Sizes.gap),
            verticalArrangement = Arrangement.spacedBy(Sizes.gap),
        ) {
            items(roster, key = { it.id }) { def ->
                val owned = ownedByCreature[def.id].orEmpty()
                CodexTile(
                    def = def,
                    owned = owned,
                    selected = openId == def.id,
                    onClick = { openId = if (openId == def.id) null else def.id },
                )
            }
        }

        val open = openId?.let { id -> roster.firstOrNull { it.id == id } }
        if (open != null) {
            CodexDetail(
                def = open,
                owned = ownedByCreature[open.id].orEmpty(),
                companionId = state.player.companionId,
                powder = state.player.powder,
                onSetCompanion = { viewModel.setCompanion(it) },
                onFeedPowder = { viewModel.feedPowder(it, 1) },
                onRelease = { viewModel.releaseOwned(it) },
            )
        }
    }
}

@Composable
private fun CodexTile(def: CreatureDef, owned: List<OwnedCreatureEntity>, selected: Boolean, onClick: () -> Unit) {
    val found = owned.isNotEmpty()
    val hasShiny = owned.any { it.isShiny }
    val shape = RoundedCornerShape(Sizes.cornerSmall)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (found) theme.card else theme.surface)
            .border(
                Sizes.hairline,
                when {
                    selected -> theme.accent
                    hasShiny -> theme.gold
                    found -> theme.border
                    else -> theme.borderDim
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CreatureSprite(
            def = def,
            isShiny = hasShiny,
            size = 42.dp,
            silhouette = !found,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (found) def.name else "unknown",
            style = MaterialTheme.typography.labelSmall,
            color = if (found) theme.textBright else theme.borderDim,
            textAlign = TextAlign.Center,
        )
        if (found && owned.size > 1) {
            Text(
                "x${owned.size}",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
            )
        }
    }
}

/** The panel under the grid describing whichever entry is open. */
@Composable
private fun CodexDetail(
    def: CreatureDef,
    owned: List<OwnedCreatureEntity>,
    companionId: Long?,
    powder: Int,
    onSetCompanion: (Long) -> Unit,
    onFeedPowder: (Long) -> Unit,
    onRelease: (Long) -> Unit,
) {
    val found = owned.isNotEmpty()
    val best = owned.maxByOrNull { it.statPercent }
    val first = owned.minByOrNull { it.caughtAt }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    Column(Modifier.padding(horizontal = Sizes.gutter).padding(bottom = Sizes.gutter)) {
        ElCard {
            Text(
                buildString {
                    append(def.name)
                    append(", ")
                    append(def.rarity.label)
                    if (def.metamorphoses) append(", metamorphoses")
                },
                style = MaterialTheme.typography.titleSmall,
                color = theme.accent,
            )
            Spacer(Modifier.height(5.dp))
            Text(def.flavor, style = serifFlavor(LocalFontScale.current, theme))

            if (!found) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (def.spawnable) {
                        "Not found yet. More likely in a ${favouredBiomeName(def)} place."
                    } else {
                        "Earned through metamorphosis, never found in the world."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                )
            } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "first caught ${first?.caughtPlace.orEmpty().ifBlank { "somewhere" }}, " +
                    dateFormat.format(Date(first?.caughtAt ?: 0)),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "owned ${owned.size}, best stats ${best?.statPercent ?: 0} percent, total XP ${owned.sumOf { it.xp }}",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
            )

            Spacer(Modifier.height(10.dp))
            owned.take(MAX_INDIVIDUALS_SHOWN).forEach { individual ->
                IndividualRow(
                    individual = individual,
                    isCompanion = individual.id == companionId,
                    canFeed = powder > 0,
                    dateFormat = dateFormat,
                    onSetCompanion = { onSetCompanion(individual.id) },
                    onFeedPowder = { onFeedPowder(individual.id) },
                    onRelease = { onRelease(individual.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (owned.size > MAX_INDIVIDUALS_SHOWN) {
                Text(
                    "and ${owned.size - MAX_INDIVIDUALS_SHOWN} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                )
            }
            }
        }
    }
}

@Composable
private fun IndividualRow(
    individual: OwnedCreatureEntity,
    isCompanion: Boolean,
    canFeed: Boolean,
    dateFormat: SimpleDateFormat,
    onSetCompanion: () -> Unit,
    onFeedPowder: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizes.cornerSmall))
            .background(theme.surface)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(individual.nickname ?: "stage ${individual.stage + 1}")
                        if (individual.isShiny) append(", shiny")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (individual.isShiny) theme.gold else theme.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${individual.statPercent} percent, ${individual.xp} XP, caught ${dateFormat.format(Date(individual.caughtAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                )
            }
            if (isCompanion) {
                Text("out now", style = MaterialTheme.typography.labelMedium, color = theme.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!isCompanion) {
                PrimaryButton("Take along", Modifier.weight(1f)) { onSetCompanion() }
            }
            if (canFeed) {
                GhostButton("Empower", Modifier.weight(1f)) { onFeedPowder() }
            }
            GhostButton("Release", Modifier.weight(1f), tint = theme.danger) { onRelease() }
        }
    }
}

/** The biome a creature likes best, used in the hint for something not yet found. */
private fun favouredBiomeName(def: CreatureDef): String =
    def.biome.maxByOrNull { it.value }?.key?.label ?: "quiet"

private const val MAX_INDIVIDUALS_SHOWN = 4
