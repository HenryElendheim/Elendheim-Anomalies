package com.elendheim.anomalies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.game.Progression
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.GhostButton
import com.elendheim.anomalies.ui.common.MeterBar
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.common.StatTile
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme

/** The numbers. Level, travel, catches, everything worth keeping score of. */
@Composable
fun ProfileScreen(viewModel: GameViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = state.player
    val level = Progression.levelForPlayerXp(player.xp)
    val progress = Progression.levelProgress(player.xp)
    val nextLevelXp = Progression.playerXpForLevel(level + 1)

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(title = "Profile")

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Sizes.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(theme.card)
                    .border(2.dp, theme.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$level", style = MaterialTheme.typography.titleLarge, color = theme.accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "level $level, ${player.xp} of $nextLevelXp XP",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
            )
            Spacer(Modifier.height(8.dp))
            MeterBar(progress, Modifier.width(200.dp), label = "Progress to level ${level + 1}")
            Spacer(Modifier.height(6.dp))
            Text(
                "next level: a bundle of capsules and fifty more slots",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(18.dp))

        // Two columns of numbers, built from one list so adding a stat is one line.
        val stats = listOf(
            "Total catches" to player.totalCatches.toString(),
            "Distance" to Geo.format(player.distanceMeters),
            "Places" to player.places.size.toString(),
            "Shinies" to player.shinies.toString(),
            "Stop spins" to player.stopSpins.toString(),
            "Metamorphoses" to player.metamorphoses.toString(),
            "Creatures held" to state.owned.size.toString(),
            "Ljós" to player.ljos.toString(),
        )
        Column(
            modifier = Modifier.padding(horizontal = Sizes.gutter),
            verticalArrangement = Arrangement.spacedBy(Sizes.gap),
        ) {
            stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Sizes.gap)) {
                    pair.forEach { (label, value) ->
                        StatTile(
                            label = label,
                            value = value,
                            modifier = Modifier.weight(1f),
                            valueColor = if (label == "Shinies" && value != "0") theme.gold else theme.text,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(Modifier.padding(horizontal = Sizes.gutter)) {
            val companion = state.companion
            val def = companion?.let { viewModel.definitionOf(it.creatureId) }
            ElCard(borderColor = theme.borderDim) {
                Text("Companion", style = MaterialTheme.typography.titleSmall, color = theme.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (def == null) {
                        "Nothing out. Pick one in the Codex and it will grow as you travel."
                    } else {
                        "${companion?.nickname ?: def.name}, ${def.power.label}. ${def.power.description}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                )
            }
            Spacer(Modifier.height(Sizes.gap))
            GhostButton("Settings") { onOpenSettings() }
            Spacer(Modifier.height(Sizes.gutter))
        }
    }
}
