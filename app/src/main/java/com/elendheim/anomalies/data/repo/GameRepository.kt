package com.elendheim.anomalies.data.repo

import com.elendheim.anomalies.data.db.CatchLogEntity
import com.elendheim.anomalies.data.db.OwnedCreatureEntity
import com.elendheim.anomalies.data.db.PlayerStateEntity
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.game.CapsuleType
import kotlinx.coroutines.flow.Flow

/** What a stop handed over when it was spun. */
data class SpinReward(
    val items: Map<CapsuleType, Int>,
    val bonusSpawn: Boolean,
    val playerXp: Int,
) {
    /** Drops in the order they are revealed: plainest first, so the best one lands last. */
    val revealOrder: List<Pair<CapsuleType, Int>>
        get() = items.entries
            .sortedBy { it.key.rarity.ordinal }
            .map { it.key to it.value }

    /** The best thing in the haul, which decides how loud the reveal gets. */
    val best: CapsuleType? get() = items.keys.maxByOrNull { it.rarity.ordinal }

    val totalItems: Int get() = items.values.sum()
}

/** What landing a catch produced, so the result card can report all of it at once. */
data class CatchOutcome(
    val owned: OwnedCreatureEntity,
    val playerXp: Int,
    val powderGained: Int,
    val levelledUpTo: Int?,
    val isNewToCodex: Boolean,
)

/** What happened to the companion after some experience was handed to it. */
data class CompanionProgress(
    val xpGained: Int,
    val metamorphosedInto: String?,
)

/**
 * Every read and write the game does goes through this interface. Keeping the screens
 * on an interface rather than on Room means a different store could be slotted in later
 * without any screen changing.
 */
interface GameRepository {
    fun observePlayer(): Flow<PlayerStateEntity>
    fun observeOwned(): Flow<List<OwnedCreatureEntity>>
    fun observeStops(): Flow<List<StopEntity>>
    fun observeItems(): Flow<Map<CapsuleType, Int>>

    suspend fun ensureSeeded()

    suspend fun player(): PlayerStateEntity
    suspend fun owned(id: Long): OwnedCreatureEntity?
    suspend fun itemCount(type: CapsuleType): Int
    suspend fun grantItems(items: Map<CapsuleType, Int>)
    suspend fun consumeItem(type: CapsuleType): Boolean

    suspend fun addStop(stop: StopEntity): Long
    suspend fun updateStop(stop: StopEntity)
    suspend fun deleteStop(id: Long)
    suspend fun spinStop(stopId: Long, extraItems: Int): SpinReward?

    suspend fun recordCatch(
        creatureId: String,
        isShiny: Boolean,
        capsule: CapsuleType,
        skillXp: Int,
        lat: Double,
        lng: Double,
        place: String,
    ): CatchOutcome?

    suspend fun recordMiss(creatureId: String, capsule: CapsuleType, lat: Double, lng: Double)
    suspend fun recordFlee(rarityIsNotable: Boolean)

    suspend fun setCompanion(ownedId: Long?)
    suspend fun renameOwned(ownedId: Long, nickname: String?)
    suspend fun releaseOwned(ownedId: Long)
    suspend fun addDistance(meters: Double): CompanionProgress
    suspend fun addCompanionXp(amount: Int): CompanionProgress
    suspend fun feedPowder(ownedId: Long, amount: Int): Boolean

    suspend fun recentCatches(limit: Int): List<CatchLogEntity>
    suspend fun snapshot(): BackupSnapshot
    suspend fun restore(snapshot: BackupSnapshot)
}
