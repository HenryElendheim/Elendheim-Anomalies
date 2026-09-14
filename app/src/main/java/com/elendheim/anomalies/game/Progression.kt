package com.elendheim.anomalies.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Every experience and levelling rule in the app lives here, which means the map,
 * the catch screen and the profile screen all agree on the numbers by construction.
 */
object Progression {

    /** Total player XP needed to reach the given level. Growth is gentle, then steady. */
    fun playerXpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (120.0 * (level - 1).toDouble().pow(1.45)).roundToInt()
    }

    /** The level a total XP amount corresponds to. */
    fun levelForPlayerXp(totalXp: Int): Int {
        var level = 1
        while (level < MAX_PLAYER_LEVEL && playerXpForLevel(level + 1) <= totalXp) level++
        return level
    }

    /** How far through the current level the player is, as a value between zero and one. */
    fun levelProgress(totalXp: Int): Float {
        val level = levelForPlayerXp(totalXp)
        if (level >= MAX_PLAYER_LEVEL) return 1f
        val floor = playerXpForLevel(level)
        val ceiling = playerXpForLevel(level + 1)
        if (ceiling <= floor) return 1f
        return ((totalXp - floor).toFloat() / (ceiling - floor)).coerceIn(0f, 1f)
    }

    /** Total inventory slots unlocked at a level. */
    fun inventorySlots(level: Int): Int = INVENTORY_BASE_SLOTS + (level - 1) * INVENTORY_SLOTS_PER_LEVEL

    /** Player XP handed out for landing a catch, before capsule and ring bonuses. */
    fun catchXp(rarity: Rarity, isShiny: Boolean): Int {
        val base = 40 + rarity.ordinal * 55
        return if (isShiny) base * 2 else base
    }

    /** Player XP from spinning a stop. */
    const val SPIN_XP = 12

    /** Bonus player XP a Spark capsule adds on top of the catch. */
    const val SPARK_BONUS_XP = 60

    /** Empower Powder granted by an Essence capsule catch. Powder speeds a companion up. */
    const val ESSENCE_POWDER = 3

    /**
     * Companion XP earned from distance. Travel is deliberately uncapped, which means a
     * train or a flight counts in full rather than being filtered out as cheating.
     */
    fun companionXpForDistance(meters: Double): Int = (meters / 20.0).toInt()

    /** Companion XP for a catch made while it was active. */
    fun companionXpForCatch(rarity: Rarity): Int = 25 + rarity.ordinal * 20

    /** Companion XP for spinning a stop while it was active. */
    const val COMPANION_SPIN_XP = 8

    /** Each measure of Empower Powder raises the companion XP rate by this much. */
    const val POWDER_RATE_STEP = 0.05

    /** The rate multiplier stored powder produces, capped so it stays a boost. */
    fun powderRate(spent: Int): Double = (1.0 + spent * POWDER_RATE_STEP).coerceAtMost(3.0)

    const val MAX_PLAYER_LEVEL = 60
}
