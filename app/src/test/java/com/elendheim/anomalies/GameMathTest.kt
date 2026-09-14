package com.elendheim.anomalies

import com.elendheim.anomalies.data.repo.SpinReward
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.ItemRarity
import com.elendheim.anomalies.game.CatchMath
import com.elendheim.anomalies.game.FieldEffects
import com.elendheim.anomalies.game.FieldPower
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.game.Progression
import com.elendheim.anomalies.game.Rarity
import com.elendheim.anomalies.game.RingHit
import com.elendheim.anomalies.game.ThrowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules the whole game rests on, checked as plain arithmetic. */
class GameMathTest {

    @Test
    fun `levels climb and never run backwards`() {
        var previous = -1
        for (level in 1..Progression.MAX_PLAYER_LEVEL) {
            val needed = Progression.playerXpForLevel(level)
            assertTrue("level $level should need more than level ${level - 1}", needed > previous)
            previous = needed
        }
    }

    @Test
    fun `experience maps back to the level it was earned at`() {
        for (level in 1 until Progression.MAX_PLAYER_LEVEL) {
            val atFloor = Progression.playerXpForLevel(level)
            assertEquals(level, Progression.levelForPlayerXp(atFloor))
            assertEquals(level, Progression.levelForPlayerXp(Progression.playerXpForLevel(level + 1) - 1))
        }
    }

    @Test
    fun `slots grow by fifty a level`() {
        assertEquals(
            Progression.inventorySlots(4) + 50,
            Progression.inventorySlots(5),
        )
    }

    @Test
    fun `a better ring is always worth more than a worse one`() {
        val order = listOf(RingHit.MISS, RingHit.GRAZE, RingHit.GOOD, RingHit.PERFECT)
        order.zipWithNext { worse, better ->
            assertTrue(better.multiplier > worse.multiplier)
            assertTrue(better.playerXp >= worse.playerXp)
        }
    }

    @Test
    fun `the ring is graded from tight to wide`() {
        assertEquals(RingHit.PERFECT, CatchMath.gradeRing(0.2f))
        assertEquals(RingHit.GOOD, CatchMath.gradeRing(0.45f))
        assertEquals(RingHit.GRAZE, CatchMath.gradeRing(0.7f))
        assertEquals(RingHit.MISS, CatchMath.gradeRing(0.95f))
    }

    @Test
    fun `skill raises the odds and rarity lowers them`() {
        val creature = TestRoster.common
        val plain = ThrowResult(RingHit.MISS, curved = false, upright = false, favouredBiome = false)
        val perfect = ThrowResult(RingHit.PERFECT, curved = true, upright = true, favouredBiome = true)

        val plainChance = CatchMath.chance(creature, CapsuleType.CAPTURE, plain)
        val skilledChance = CatchMath.chance(creature, CapsuleType.CAPTURE, perfect)
        assertTrue(skilledChance > plainChance)

        val mythicChance = CatchMath.chance(TestRoster.mythic, CapsuleType.CAPTURE, perfect)
        assertTrue(mythicChance < skilledChance)
    }

    @Test
    fun `the catch chance is never certain and never hopeless`() {
        val best = ThrowResult(RingHit.PERFECT, curved = true, upright = true, favouredBiome = true)
        val worst = ThrowResult(RingHit.MISS, curved = false, upright = false, favouredBiome = false)
        for (rarity in Rarity.entries) {
            val creature = TestRoster.withRarity(rarity)
            assertTrue(CatchMath.chance(creature, CapsuleType.PRIME, best) < 1.0)
            assertTrue(CatchMath.chance(creature, CapsuleType.CAPTURE, worst) > 0.0)
        }
    }

    @Test
    fun `a near miss rocks the capsule more than a hopeless one`() {
        // A roll landing just past the chance is the heartbreaker and rocks the full set.
        assertEquals(3, CatchMath.wobblesForMiss(roll = 0.51, chance = 0.50, maxWobbles = 3))
        // The worst possible roll barely moves it at all.
        assertEquals(0, CatchMath.wobblesForMiss(roll = 0.99, chance = 0.10, maxWobbles = 3))
        // And the count never runs backwards as the roll gets worse.
        var previous = 3
        for (step in 0..20) {
            val count = CatchMath.wobblesForMiss(roll = 0.3 + step * 0.035, chance = 0.3, maxWobbles = 3)
            assertTrue("wobbles should not climb as the roll worsens", count <= previous)
            previous = count
        }
    }

    @Test
    fun `a flat tumble counts as upright and a half turn off does not`() {
        assertTrue(CatchMath.landsUpright(2.0f))
        assertTrue(CatchMath.landsUpright(2.5f))
        assertTrue(!CatchMath.landsUpright(2.25f))
    }

    @Test
    fun `field powers only change their own effect`() {
        val scout = FieldEffects.of(FieldPower.SCOUT, stage = 0)
        assertTrue(scout.spawnRadiusMeters > FieldEffects.BASE_SPAWN_RADIUS)
        assertEquals(1.0, scout.shinyMultiplier, 0.0001)

        val luckyStageTwo = FieldEffects.of(FieldPower.LUCK, stage = 2)
        val luckyStageZero = FieldEffects.of(FieldPower.LUCK, stage = 0)
        assertTrue(luckyStageTwo.shinyMultiplier > luckyStageZero.shinyMultiplier)
    }

    @Test
    fun `a refund chance never becomes a certainty`() {
        val tinker = FieldEffects.of(FieldPower.TINKER, stage = 10)
        assertTrue(tinker.capsuleRefundChance <= 0.6)
    }

    @Test
    fun `distance between two points is symmetric and sane`() {
        val oneWay = Geo.distanceMeters(59.9139, 10.7522, 59.9239, 10.7522)
        val other = Geo.distanceMeters(59.9239, 10.7522, 59.9139, 10.7522)
        assertEquals(oneWay, other, 0.001)
        // A hundredth of a degree of latitude is a bit over a kilometre anywhere on earth.
        assertTrue(oneWay in 1000.0..1200.0)
    }

    @Test
    fun `offsetting by a distance and measuring it back agrees`() {
        val (lat, lng) = Geo.offset(59.9139, 10.7522, northMeters = 250.0, eastMeters = 0.0)
        assertEquals(250.0, Geo.distanceMeters(59.9139, 10.7522, lat, lng), 1.0)
    }

    @Test
    fun `powder speeds a companion up without letting it run away`() {
        assertEquals(1.0, Progression.powderRate(0), 0.0001)
        assertTrue(Progression.powderRate(5) > Progression.powderRate(0))
        assertTrue(Progression.powderRate(1000) <= 3.0)
    }

    @Test
    fun `a better capsule is a rarer capsule`() {
        assertEquals(ItemRarity.STANDARD, CapsuleType.CAPTURE.rarity)
        assertEquals(ItemRarity.EPIC, CapsuleType.PRIME.rarity)
        // The catch tiers line up with the rarity tiers, so the reveal never oversells.
        assertTrue(CapsuleType.PRIME.catchMultiplier > CapsuleType.ENHANCED.catchMultiplier)
        assertTrue(CapsuleType.ENHANCED.rarity.ordinal > CapsuleType.CAPTURE.rarity.ordinal)
    }

    @Test
    fun `a spin reveals its plainest drop first and its best one last`() {
        val reward = SpinReward(
            items = mapOf(
                CapsuleType.PRIME to 1,
                CapsuleType.CAPTURE to 2,
                CapsuleType.ENHANCED to 1,
            ),
            bonusSpawn = false,
            playerXp = 12,
        )
        assertEquals(
            listOf(CapsuleType.CAPTURE, CapsuleType.ENHANCED, CapsuleType.PRIME),
            reward.revealOrder.map { it.first },
        )
        assertEquals(CapsuleType.PRIME, reward.best)
        assertEquals(4, reward.totalItems)
    }
}
