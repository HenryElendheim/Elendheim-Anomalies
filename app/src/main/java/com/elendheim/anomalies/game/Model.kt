package com.elendheim.anomalies.game

/**
 * The six rarity tiers. [weight] is the base share of a spawn roll before the biome
 * multiplies it, and [catchBase] is the starting chance a standard capsule lands.
 */
enum class Rarity(val label: String, val weight: Double, val catchBase: Double) {
    COMMON("common", 60.0, 0.62),
    UNCOMMON("uncommon", 25.0, 0.48),
    RARE("rare", 10.0, 0.34),
    EPIC("epic", 4.0, 0.22),
    LEGENDARY("legendary", 0.9, 0.13),
    MYTHIC("mythic", 0.1, 0.07);

    /** Anything from rare upwards feeds the pity counter and reads as a real find. */
    val isNotable: Boolean get() = ordinal >= RARE.ordinal
}

/**
 * The four world flavours a spawn roll is weighted by. A creature is never locked out
 * of a biome, the biome only changes how often it turns up.
 */
enum class Biome(val label: String) {
    URBAN("urban"),
    WATER("water"),
    GREEN("green"),
    DRY("dry"),
}

/** Day, dusk and night shift the roll on top of the biome. */
enum class TimeBand(val label: String) {
    DAY("day"),
    DUSK("dusk"),
    NIGHT("night"),
}

/**
 * Capsules split on two axes. [catchMultiplier] above one makes the throw land more
 * often, and a non null [bonus] pays out something extra when the throw succeeds.
 */
enum class CapsuleType(
    val label: String,
    val description: String,
    val catchMultiplier: Double,
    val bonus: CapsuleBonus?,
    val colorHex: Long,
) {
    CAPTURE("Capture capsule", "standard catch rate", 1.0, null, 0xFF0AF5C8),
    ENHANCED("Enhanced capsule", "higher catch rate", 1.45, null, 0xFFAFA9EC),
    PRIME("Prime capsule", "highest catch rate", 2.1, null, 0xFF85B7EB),
    SPARK("Spark capsule", "standard rate, bonus player XP", 1.0, CapsuleBonus.PLAYER_XP, 0xFFFAC775),
    ESSENCE("Essence capsule", "standard rate, bonus Ljós", 1.0, CapsuleBonus.LJOS, 0xFFF0997B);

    companion object {
        /** Parsing never throws, which means a corrupt backup file degrades instead of crashing. */
        fun fromNameOrNull(raw: String?): CapsuleType? = entries.firstOrNull { it.name == raw }
    }
}

/** What a specialty capsule hands over on a successful catch. */
enum class CapsuleBonus { PLAYER_XP, LJOS }

/**
 * What the active companion changes about the world while it is out. Powers get
 * stronger as a creature metamorphoses, which is handled by [scaleFor].
 */
enum class FieldPower(val label: String, val description: String) {
    SCOUT("Scout", "widens the radius anomalies appear in"),
    LURE("Lure", "raises the odds of rare and better spawns"),
    TINKER("Tinker", "chance to get a missed capsule back"),
    LUCK("Luck", "small bump to shiny odds"),
    COURIER("Courier", "extra items from every stop spin");

    /** Stage zero is the base strength and each metamorphosis adds half again. */
    fun scaleFor(stage: Int): Double = 1.0 + 0.5 * stage
}

/** The silhouettes creatures are drawn from. Art is code, so a new creature costs no assets. */
enum class SpriteShape { BOX, ROUND, BLOB, DIAMOND, SPIKE, WISP, CRESCENT, STAR }

/** How well the shrinking ring was timed. */
enum class RingHit(val label: String, val multiplier: Double, val playerXp: Int) {
    MISS("wide", 0.85, 0),
    GRAZE("graze", 1.1, 4),
    GOOD("good", 1.35, 9),
    PERFECT("perfect", 1.75, 20),
}

/** A creature gives up after this many failed capsules, whatever its rarity. */
const val MAX_CATCH_ATTEMPTS = 3

/** Every item and creature shares one pool, and the pool grows only by levelling. */
const val INVENTORY_SLOTS_PER_LEVEL = 50
const val INVENTORY_BASE_SLOTS = 100
