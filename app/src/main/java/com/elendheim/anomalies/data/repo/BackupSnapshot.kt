package com.elendheim.anomalies.data.repo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * The whole save file, in the shape it is written to disk. Plain readable JSON on
 * purpose, which means a backup stays useful even without the app that wrote it.
 */
@Serializable
data class BackupSnapshot(
    val format: Int = FORMAT_VERSION,
    val app: String = "Elendheim Anomalies",
    val exportedAt: Long = 0,
    val player: PlayerBackup = PlayerBackup(),
    val creatures: List<OwnedBackup> = emptyList(),
    val stops: List<StopBackup> = emptyList(),
    val items: Map<String, Int> = emptyMap(),
) {
    companion object { const val FORMAT_VERSION = 2 }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlayerBackup(
    val xp: Int = 0,
    val companionIndex: Int = -1,
    val totalCatches: Int = 0,
    val distanceMeters: Double = 0.0,
    val shinies: Int = 0,
    val stopSpins: Int = 0,
    val metamorphoses: Int = 0,
    // A version one file called this ljos, so both spellings are accepted and a backup
    // written before the rename still restores its full amount.
    @JsonNames("ljos") val powder: Int = 0,
    val perfectThrows: Int = 0,
    val places: List<String> = emptyList(),
    val pityCounter: Int = 0,
    val createdAt: Long = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OwnedBackup(
    val creatureId: String,
    val isShiny: Boolean = false,
    val statPower: Int = 0,
    val statGrace: Int = 0,
    val statWard: Int = 0,
    val xp: Int = 0,
    val stage: Int = 0,
    @JsonNames("ljosSpent") val powderSpent: Int = 0,
    val caughtAt: Long = 0,
    val caughtLat: Double = 0.0,
    val caughtLng: Double = 0.0,
    val caughtPlace: String = "",
    val nickname: String? = null,
)

@Serializable
data class StopBackup(
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int = 60,
    val cooldownMinutes: Int = 5,
    val lastSpunAt: Long = 0,
    val createdAt: Long = 0,
    val spins: Int = 0,
    val itemsEarned: Int = 0,
    val bonusSpawns: Int = 0,
)
