package com.elendheim.anomalies.data.roster

import android.content.Context
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.TimeBand
import kotlinx.serialization.json.Json

/**
 * The in memory view of the roster. It is read once from the asset and then answered
 * from maps, which means lookups on the map and in the Codex never touch storage.
 */
class CreatureRoster private constructor(val all: List<CreatureDef>) {

    private val byId: Map<String, CreatureDef> = all.associateBy { it.id }

    /** Only these can appear in the world. Evolved forms are earned, never found. */
    val spawnable: List<CreatureDef> = all.filter { it.spawnable }

    operator fun get(id: String): CreatureDef? = byId[id]

    /** Used wherever a missing id must still render something rather than crash. */
    fun require(id: String): CreatureDef = byId[id] ?: all.first()

    /** The spawn table for the current conditions, as creature paired with its weight. */
    fun weightedTable(biome: Biome, band: TimeBand): List<Pair<CreatureDef, Double>> =
        spawnable.map { it to it.weightIn(biome, band) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        @Volatile private var instance: CreatureRoster? = null

        /** Loaded once per process and shared from then on. */
        fun load(context: Context): CreatureRoster = instance ?: synchronized(this) {
            instance ?: run {
                val text = context.assets.open("creatures.json").bufferedReader().use { it.readText() }
                CreatureRoster(json.decodeFromString<RosterFile>(text).creatures).also { instance = it }
            }
        }
    }
}
