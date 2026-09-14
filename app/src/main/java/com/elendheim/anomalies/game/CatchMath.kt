package com.elendheim.anomalies.game

import com.elendheim.anomalies.data.roster.CreatureDef
import kotlin.math.abs
import kotlin.random.Random

/**
 * Everything the flick is graded on, captured at the moment the capsule lands.
 */
data class ThrowResult(
    val ring: RingHit,
    val curved: Boolean,
    val upright: Boolean,
    val favouredBiome: Boolean,
)

/**
 * The catch chance. Every bonus is a multiplier on the last, which means skill keeps
 * mattering on a mythic and a good capsule never makes a bad throw irrelevant.
 */
object CatchMath {

    const val CURVE_BONUS = 1.18
    const val UPRIGHT_BONUS = 1.3
    const val FAVOURED_BIOME_BONUS = 1.1

    /** Never a certainty and never hopeless, so every throw stays worth making. */
    private const val FLOOR = 0.02
    private const val CEILING = 0.95

    fun chance(creature: CreatureDef, capsule: CapsuleType, result: ThrowResult): Double {
        var chance = creature.rarity.catchBase * capsule.catchMultiplier * result.ring.multiplier
        if (result.curved) chance *= CURVE_BONUS
        if (result.upright) chance *= UPRIGHT_BONUS
        if (result.favouredBiome) chance *= FAVOURED_BIOME_BONUS
        return chance.coerceIn(FLOOR, CEILING)
    }

    fun succeeds(creature: CreatureDef, capsule: CapsuleType, result: ThrowResult, random: Random = Random.Default): Boolean =
        random.nextDouble() < chance(creature, capsule, result)

    /** Bonus player experience the throw itself earned, before the catch is scored. */
    fun skillXp(result: ThrowResult): Int {
        var xp = result.ring.playerXp
        if (result.curved) xp += 6
        if (result.upright) xp += 12
        return xp
    }

    /**
     * Grades the shrinking ring. [ringFraction] is the ring radius as a share of its
     * widest size at the instant of release, so a smaller number is a tighter throw.
     */
    fun gradeRing(ringFraction: Float): RingHit = when {
        ringFraction <= 0.34f -> RingHit.PERFECT
        ringFraction <= 0.54f -> RingHit.GOOD
        ringFraction <= 0.76f -> RingHit.GRAZE
        else -> RingHit.MISS
    }

    /**
     * How fast the capsule tumbles, taken from how hard the flick was. Learning the
     * flick strength that lands the cylinder on its base is the skill the whole catch
     * screen is built around.
     */
    fun spinTurnsFor(flickStrength: Float): Float = 0.6f + flickStrength * 3.4f

    /**
     * The capsule lands upright when its tumble finishes near a whole or half turn.
     * The window is generous enough to be learnable and tight enough to feel earned.
     */
    fun landsUpright(spinTurns: Float, tolerance: Float = UPRIGHT_TOLERANCE): Boolean {
        val fractionOfTurn = spinTurns - spinTurns.toInt()
        val distanceToFlat = minOf(
            fractionOfTurn,
            abs(fractionOfTurn - 0.5f),
            1f - fractionOfTurn,
        )
        return distanceToFlat <= tolerance
    }

    /** A flick with real sideways travel counts as a curve. */
    fun isCurve(sidewaysFraction: Float): Boolean = abs(sidewaysFraction) >= CURVE_THRESHOLD

    const val UPRIGHT_TOLERANCE = 0.07f
    const val CURVE_THRESHOLD = 0.28f
}
