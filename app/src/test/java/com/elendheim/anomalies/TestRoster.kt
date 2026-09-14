package com.elendheim.anomalies

import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.FieldPower
import com.elendheim.anomalies.game.Rarity
import com.elendheim.anomalies.game.SpriteShape

/** A small stand in roster so the tests do not need the asset file or a device. */
object TestRoster {

    private fun def(
        id: String,
        rarity: Rarity,
        biome: Map<Biome, Double> = emptyMap(),
        spawnable: Boolean = true,
        nextFormId: String? = null,
    ) = CreatureDef(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        family = id,
        rarity = rarity,
        shape = SpriteShape.BLOB,
        color = "#4FD1C5",
        eyeColor = "#063A35",
        power = FieldPower.SCOUT,
        biome = biome,
        time = emptyMap(),
        flavor = "test",
        spawnable = spawnable,
        nextFormId = nextFormId,
        metamorphXp = if (nextFormId != null) 500 else 0,
    )

    val common = def("common", Rarity.COMMON)
    val mythic = def("mythic", Rarity.MYTHIC)

    val all: List<CreatureDef> = listOf(
        common,
        def("uncommon", Rarity.UNCOMMON, nextFormId = "evolved"),
        def("water", Rarity.RARE, biome = mapOf(Biome.WATER to 4.0, Biome.DRY to 0.5)),
        def("epic", Rarity.EPIC),
        def("legendary", Rarity.LEGENDARY),
        mythic,
        def("evolved", Rarity.RARE, spawnable = false),
    )

    fun withRarity(rarity: Rarity) = def("test-${rarity.name.lowercase()}", rarity)
}
