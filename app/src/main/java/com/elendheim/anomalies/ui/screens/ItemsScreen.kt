package com.elendheim.anomalies.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.ItemRarity
import com.elendheim.anomalies.game.Progression
import com.elendheim.anomalies.ui.common.Dot
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.MeterBar
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme

/** What the player is carrying, and how much of the shared pool is left. */
@Composable
fun ItemsScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val level = Progression.levelForPlayerXp(state.player.xp)
    val capacity = Progression.inventorySlots(level)
    val used = state.slotsUsed

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(title = "Items", subtitle = "$used of $capacity slots used")

        Column(Modifier.padding(horizontal = Sizes.gutter)) {
            MeterBar(
                progress = if (capacity > 0) used.toFloat() / capacity else 0f,
                color = if (used >= capacity) theme.gold else theme.textMid,
                height = 4.dp,
                label = "Inventory $used of $capacity slots used",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Creatures and items share the pool. Each level adds fifty slots.",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Sizes.gutter, end = Sizes.gutter, top = 12.dp, bottom = Sizes.gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(Sizes.gap),
        ) {
            items(CapsuleType.entries.size) { index ->
                val type = CapsuleType.entries[index]
                CapsuleRow(type, state.items[type] ?: 0)
            }
            item {
                Spacer(Modifier.height(Sizes.gap))
                ElCard(borderColor = theme.borderDim) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(theme.gold, 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Empower Powder", style = MaterialTheme.typography.titleSmall, color = theme.text)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "given to a creature to make it grow faster",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textDim,
                            )
                        }
                        Text(
                            "x${state.player.powder}",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textBright,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleRow(type: CapsuleType, count: Int) {
    ElCard(borderColor = if (count > 0) theme.border else theme.borderDim) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Color(type.colorHex), 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    type.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (count > 0) theme.text else theme.textDim,
                )
                Spacer(Modifier.height(2.dp))
                Text(type.description, style = MaterialTheme.typography.labelSmall, color = theme.textDim)
                Spacer(Modifier.height(2.dp))
                Text(
                    type.rarity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (type.rarity) {
                        ItemRarity.STANDARD -> theme.textDim
                        ItemRarity.UNCOMMON -> theme.textMid
                        ItemRarity.RARE -> theme.accent
                        ItemRarity.EPIC -> theme.gold
                    },
                )
            }
            Text(
                "x$count",
                style = MaterialTheme.typography.titleMedium,
                color = if (count > 0) theme.textBright else theme.textDim,
            )
        }
    }
}
