package com.elendheim.anomalies.game

import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.data.roster.CreatureRoster
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One anomaly currently standing in the world, waiting to be tapped. */
data class Spawn(
    val key: Long,
    val creatureId: String,
    val isShiny: Boolean,
    val lat: Double,
    val lng: Double,
    val biome: Biome,
    val bornAt: Long,
    val expiresAt: Long,
)

/**
 * Rolls what appears, where, and whether it glitters. Nothing here touches storage, so
 * the whole spawn behaviour can be reasoned about and tested as plain arithmetic.
 */
class SpawnEngine(
    private val roster: CreatureRoster,
    private val random: Random = Random.Default,
) {

    /**
     * Picks a creature for the current conditions.
     *
     * [pity] is how many rare or better catches the player has gone without. It nudges
     * the notable tiers upward a little at a time, which means a long dry run gets
     * shorter without the jackpot ever becoming routine.
     */
    fun rollCreature(biome: Biome, band: TimeBand, effects: FieldEffects, pity: Int): CreatureDef {
        val pityBoost = 1.0 + (pity * PITY_STEP).coerceAtMost(PITY_CEILING)
        val table = roster.weightedTable(biome, band).map { (creature, weight) ->
            val boosted = if (creature.rarity.isNotable) {
                weight * effects.notableWeightBoost * pityBoost
            } else {
                weight
            }
            creature to boosted
        }
        val total = table.sumOf { it.second }
        if (total <= 0.0) return roster.spawnable.first()
        var cursor = random.nextDouble(total)
        for ((creature, weight) in table) {
            cursor -= weight
            if (cursor <= 0.0) return creature
        }
        return table.last().first
    }

    /** The shiny roll is independent of rarity, so any spawn at all can come up shiny. */
    fun rollShiny(effects: FieldEffects): Boolean =
        random.nextDouble() < BASE_SHINY_ODDS * effects.shinyMultiplier

    /** Places a spawn at a random bearing and distance inside the current radius. */
    fun placeNear(lat: Double, lng: Double, radiusMeters: Double): Pair<Double, Double> {
        val bearing = random.nextDouble(0.0, 2 * PI)
        // Square rooting the roll spreads spawns evenly over the disc instead of
        // bunching them around the player.
        val distance = radiusMeters * sqrt(random.nextDouble())
        val northMeters = distance * cos(bearing)
        val eastMeters = distance * sin(bearing)
        return Geo.offset(lat, lng, northMeters, eastMeters)
    }

    /** Builds a complete spawn ready to drop onto the map. */
    fun spawnAt(
        lat: Double,
        lng: Double,
        effects: FieldEffects,
        pity: Int,
        now: Long = System.currentTimeMillis(),
        forcedBiome: Biome? = null,
    ): Spawn {
        val biome = forcedBiome ?: BiomeSensor.biomeAt(lat, lng)
        val band = BiomeSensor.timeBandNow(now)
        val creature = rollCreature(biome, band, effects, pity)
        val (spawnLat, spawnLng) = placeNear(lat, lng, effects.spawnRadiusMeters)
        return Spawn(
            key = now * 1000 + random.nextInt(1000),
            creatureId = creature.id,
            isShiny = rollShiny(effects),
            lat = spawnLat,
            lng = spawnLng,
            biome = biome,
            bornAt = now,
            expiresAt = now + LIFETIME_MILLIS,
        )
    }

    companion object {
        const val BASE_SHINY_ODDS = 1.0 / 200.0
        const val LIFETIME_MILLIS = 5 * 60 * 1000L
        /** How long between spawn rolls while the map is open. */
        const val ROLL_INTERVAL_MILLIS = 11_000L
        const val MAX_ACTIVE_SPAWNS = 6
        private const val PITY_STEP = 0.03
        private const val PITY_CEILING = 2.5
    }
}
