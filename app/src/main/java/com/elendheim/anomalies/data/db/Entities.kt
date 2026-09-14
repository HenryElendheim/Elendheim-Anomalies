package com.elendheim.anomalies.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One creature the player actually owns. Stats are rolled at the moment of the catch,
 * which means a second Vekkli is still worth catching.
 */
@Entity(tableName = "owned_creature", indices = [Index("creatureId")])
data class OwnedCreatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creatureId: String,
    val isShiny: Boolean,
    val statPower: Int,
    val statGrace: Int,
    val statWard: Int,
    val xp: Int = 0,
    val stage: Int = 0,
    val powderSpent: Int = 0,
    val caughtAt: Long,
    val caughtLat: Double,
    val caughtLng: Double,
    val caughtPlace: String,
    val nickname: String? = null,
) {
    /** The three rolls as a share of their maximum, which is what the Codex shows. */
    val statPercent: Int get() = ((statPower + statGrace + statWard) * 100) / (3 * MAX_STAT)

    companion object { const val MAX_STAT = 31 }
}

/** A place the player put down by hand. Stops persist until deleted. */
@Entity(tableName = "stop")
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int = 60,
    val cooldownMinutes: Int = 5,
    val lastSpunAt: Long = 0,
    val createdAt: Long,
    val spins: Int = 0,
    val itemsEarned: Int = 0,
    val bonusSpawns: Int = 0,
)

/** How many of one capsule type the player is holding. */
@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val type: String,
    val quantity: Int,
)

/** The single row holding everything about the player. */
@Entity(tableName = "player")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val xp: Int = 0,
    val companionId: Long? = null,
    val totalCatches: Int = 0,
    val distanceMeters: Double = 0.0,
    val shinies: Int = 0,
    val stopSpins: Int = 0,
    val metamorphoses: Int = 0,
    val powder: Int = 0,
    val perfectThrows: Int = 0,
    val placesCsv: String = "",
    /** Rises with every rare or better catch missed, and resets when one lands. */
    val pityCounter: Int = 0,
    val createdAt: Long = 0,
) {
    /** Distinct places the player has caught something in, used by the profile screen. */
    val places: List<String> get() = placesCsv.split('|').filter { it.isNotBlank() }

    companion object { const val SINGLETON_ID = 1 }
}

/** Every throw, landed or not. Kept so the profile numbers stay honest. */
@Entity(tableName = "catch_log")
data class CatchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creatureId: String,
    val success: Boolean,
    val capsule: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
)
