package com.elendheim.anomalies

import com.elendheim.anomalies.data.roster.CreatureRoster
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.BiomeSensor
import com.elendheim.anomalies.game.FieldEffects
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.game.Rarity
import com.elendheim.anomalies.game.SpawnEngine
import com.elendheim.anomalies.game.TimeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpawnEngineTest {

    private val roster = CreatureRoster.of(TestRoster.all)
    private val engine = SpawnEngine(roster, Random(1234))

    @Test
    fun `nothing that only comes from metamorphosis can be found in the world`() {
        val evolvedIds = TestRoster.all.filterNot { it.spawnable }.map { it.id }.toSet()
        repeat(2000) {
            val rolled = engine.rollCreature(Biome.URBAN, TimeBand.DAY, FieldEffects.NONE, pity = 0)
            assertTrue("$rolled should not be spawnable", rolled.id !in evolvedIds)
        }
    }

    @Test
    fun `a favoured biome turns a creature up more often without locking the others out`() {
        fun share(biome: Biome): Double {
            var hits = 0
            repeat(SAMPLES) {
                if (engine.rollCreature(biome, TimeBand.DAY, FieldEffects.NONE, 0).id == "water") hits++
            }
            return hits.toDouble() / SAMPLES
        }
        val inWater = share(Biome.WATER)
        val inDry = share(Biome.DRY)
        assertTrue("water creature should favour water", inWater > inDry)
        assertTrue("water creature should still appear in dry places", inDry > 0.0)
    }

    @Test
    fun `the pity counter lifts the notable tiers`() {
        fun notableShare(pity: Int): Double {
            var hits = 0
            repeat(SAMPLES) {
                if (engine.rollCreature(Biome.URBAN, TimeBand.DAY, FieldEffects.NONE, pity).rarity.isNotable) hits++
            }
            return hits.toDouble() / SAMPLES
        }
        assertTrue(notableShare(60) > notableShare(0))
    }

    @Test
    fun `a spawn always lands inside the radius it was given`() {
        repeat(500) {
            val (lat, lng) = engine.placeNear(59.9139, 10.7522, radiusMeters = 80.0)
            val distance = Geo.distanceMeters(59.9139, 10.7522, lat, lng)
            assertTrue("landed $distance m away", distance <= 80.5)
        }
    }

    @Test
    fun `the biome holds steady in one place and can differ a few streets over`() {
        val here = BiomeSensor.biomeAt(59.9139, 10.7522)
        assertEquals(here, BiomeSensor.biomeAt(59.91392, 10.75221))

        val spread = (0 until 400).map { step ->
            BiomeSensor.biomeAt(59.9139 + step * 0.002, 10.7522)
        }.toSet()
        assertTrue("every biome should be reachable by walking", spread.size > 1)
    }

    @Test
    fun `rarer tiers carry less weight than common ones`() {
        Rarity.entries.zipWithNext { common, rarer ->
            assertTrue(rarer.weight < common.weight)
            assertTrue(rarer.catchBase < common.catchBase)
        }
    }

    private companion object { const val SAMPLES = 20_000 }
}
