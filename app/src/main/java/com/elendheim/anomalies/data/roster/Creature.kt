package com.elendheim.anomalies.data.roster

import androidx.compose.ui.graphics.Color
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.FieldPower
import com.elendheim.anomalies.game.Rarity
import com.elendheim.anomalies.game.SpriteShape
import com.elendheim.anomalies.game.TimeBand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One roster entry as it is written in assets/creatures.json. Adding creature number
 * twenty one means editing that file only, which means the collection can keep growing
 * without a single code change.
 */
@Serializable
data class CreatureDef(
    val id: String,
    val name: String,
    val family: String,
    val rarity: Rarity,
    val shape: SpriteShape,
    val color: String,
    val eyeColor: String,
    val power: FieldPower,
    val biome: Map<Biome, Double> = emptyMap(),
    val time: Map<TimeBand, Double> = emptyMap(),
    val flavor: String,
    val spawnable: Boolean = true,
    val nextFormId: String? = null,
    val metamorphXp: Int = 0,
) {
    /** A creature only metamorphoses when the roster gives it somewhere to go. */
    val metamorphoses: Boolean get() = nextFormId != null && metamorphXp > 0

    val bodyColor: Color get() = hexToColor(color)
    val eyeTint: Color get() = hexToColor(eyeColor)

    /** How much more or less often this creature turns up in the given conditions. */
    fun weightIn(currentBiome: Biome, band: TimeBand): Double =
        rarity.weight * (biome[currentBiome] ?: 1.0) * (time[band] ?: 1.0)
}

@Serializable
data class RosterFile(
    @SerialName("rosterVersion") val version: Int = 1,
    val creatures: List<CreatureDef> = emptyList(),
)

/** Turns a "#RRGGBB" string from the roster file into a colour, falling back to grey. */
fun hexToColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF8FA3B8))
