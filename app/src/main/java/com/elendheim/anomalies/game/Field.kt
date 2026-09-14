package com.elendheim.anomalies.game

import kotlin.math.roundToInt

/**
 * Everything the active companion changes about the world. One value per effect, so a
 * screen asks for the number it needs instead of knowing which power produced it.
 */
data class FieldEffects(
    val spawnRadiusMeters: Double = BASE_SPAWN_RADIUS,
    val notableWeightBoost: Double = 1.0,
    val capsuleRefundChance: Double = 0.0,
    val shinyMultiplier: Double = 1.0,
    val extraStopItems: Int = 0,
) {
    companion object {
        const val BASE_SPAWN_RADIUS = 80.0

        /** No companion out means the plain world with no modifiers at all. */
        val NONE = FieldEffects()

        /** Turns a power and how far it has metamorphosed into concrete world changes. */
        fun of(power: FieldPower?, stage: Int): FieldEffects {
            if (power == null) return NONE
            val scale = power.scaleFor(stage)
            return when (power) {
                FieldPower.SCOUT -> NONE.copy(spawnRadiusMeters = BASE_SPAWN_RADIUS * (1.0 + 0.35 * scale))
                FieldPower.LURE -> NONE.copy(notableWeightBoost = 1.0 + 0.5 * scale)
                FieldPower.TINKER -> NONE.copy(capsuleRefundChance = (0.18 * scale).coerceAtMost(0.6))
                FieldPower.LUCK -> NONE.copy(shinyMultiplier = 1.0 + 0.6 * scale)
                FieldPower.COURIER -> NONE.copy(extraStopItems = (0.8 * scale).roundToInt().coerceAtLeast(1))
            }
        }
    }
}
