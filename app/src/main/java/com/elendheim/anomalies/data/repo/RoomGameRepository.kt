package com.elendheim.anomalies.data.repo

import com.elendheim.anomalies.data.db.AppDatabase
import com.elendheim.anomalies.data.db.CatchLogEntity
import com.elendheim.anomalies.data.db.ItemEntity
import com.elendheim.anomalies.data.db.OwnedCreatureEntity
import com.elendheim.anomalies.data.db.PlayerStateEntity
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.data.roster.CreatureRoster
import com.elendheim.anomalies.game.CapsuleBonus
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.Progression
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/** The Room backed implementation. All the rules about what a write means live here. */
class RoomGameRepository(
    private val db: AppDatabase,
    private val roster: CreatureRoster,
    private val random: Random = Random.Default,
) : GameRepository {

    override fun observePlayer(): Flow<PlayerStateEntity> =
        db.player().observe(PlayerStateEntity.SINGLETON_ID).map { it ?: PlayerStateEntity() }

    override fun observeOwned(): Flow<List<OwnedCreatureEntity>> = db.ownedCreatures().observeAll()

    override fun observeStops(): Flow<List<StopEntity>> = db.stops().observeAll()

    override fun observeItems(): Flow<Map<CapsuleType, Int>> =
        db.items().observeAll().map { rows -> rows.toCapsuleMap() }

    private fun List<ItemEntity>.toCapsuleMap(): Map<CapsuleType, Int> =
        mapNotNull { row -> CapsuleType.fromNameOrNull(row.type)?.let { it to row.quantity } }.toMap()

    override suspend fun ensureSeeded() {
        val now = System.currentTimeMillis()
        db.player().insertIfMissing(PlayerStateEntity(createdAt = now))
        if (db.items().all().isEmpty()) {
            // A new player starts with enough capsules to get through a first wait.
            grantItems(mapOf(CapsuleType.CAPTURE to 15, CapsuleType.SPARK to 2))
        }
    }

    override suspend fun player(): PlayerStateEntity =
        db.player().get(PlayerStateEntity.SINGLETON_ID) ?: PlayerStateEntity().also { db.player().insertIfMissing(it) }

    override suspend fun owned(id: Long): OwnedCreatureEntity? = db.ownedCreatures().byId(id)

    override suspend fun itemCount(type: CapsuleType): Int = db.items().quantityOf(type.name) ?: 0

    /** How many slots are already spoken for by items and creatures together. */
    private suspend fun slotsUsed(): Int =
        db.items().all().sumOf { it.quantity } + db.ownedCreatures().all().size

    private suspend fun slotsFree(): Int {
        val level = Progression.levelForPlayerXp(player().xp)
        return Progression.inventorySlots(level) - slotsUsed()
    }

    override suspend fun grantItems(items: Map<CapsuleType, Int>) {
        var room = slotsFree()
        for ((type, amount) in items) {
            if (room <= 0) break
            val take = minOf(amount, room)
            if (take <= 0) continue
            val current = db.items().quantityOf(type.name) ?: 0
            db.items().upsert(ItemEntity(type.name, current + take))
            room -= take
        }
    }

    override suspend fun consumeItem(type: CapsuleType): Boolean {
        val current = db.items().quantityOf(type.name) ?: 0
        if (current <= 0) return false
        db.items().upsert(ItemEntity(type.name, current - 1))
        return true
    }

    override suspend fun addStop(stop: StopEntity): Long = db.stops().insert(stop)

    override suspend fun updateStop(stop: StopEntity) = db.stops().update(stop)

    override suspend fun deleteStop(id: Long) = db.stops().delete(id)

    override suspend fun spinStop(stopId: Long, extraItems: Int): SpinReward? {
        val stop = db.stops().byId(stopId) ?: return null
        val now = System.currentTimeMillis()
        if (now - stop.lastSpunAt < stop.cooldownMinutes * 60_000L) return null

        // A short cooldown means small drops, which keeps a single capsule worth something.
        val drop = buildMap {
            put(CapsuleType.CAPTURE, 1 + random.nextInt(2) + extraItems)
            if (random.nextInt(100) < 12) put(CapsuleType.ENHANCED, 1)
            if (random.nextInt(100) < 7) put(CapsuleType.SPARK, 1)
            if (random.nextInt(100) < 5) put(CapsuleType.ESSENCE, 1)
            if (random.nextInt(100) < 2) put(CapsuleType.PRIME, 1)
        }
        val bonusSpawn = random.nextInt(100) < 10
        grantItems(drop)

        db.stops().update(
            stop.copy(
                lastSpunAt = now,
                spins = stop.spins + 1,
                itemsEarned = stop.itemsEarned + drop.values.sum(),
                bonusSpawns = stop.bonusSpawns + if (bonusSpawn) 1 else 0,
            )
        )
        val state = player()
        db.player().update(
            state.copy(xp = state.xp + Progression.SPIN_XP, stopSpins = state.stopSpins + 1)
        )
        addCompanionXp(Progression.COMPANION_SPIN_XP)
        return SpinReward(drop, bonusSpawn, Progression.SPIN_XP)
    }

    override suspend fun recordCatch(
        creatureId: String,
        isShiny: Boolean,
        capsule: CapsuleType,
        skillXp: Int,
        lat: Double,
        lng: Double,
        place: String,
    ): CatchOutcome? {
        if (slotsFree() <= 0) return null
        val def = roster[creatureId] ?: return null
        val now = System.currentTimeMillis()
        val before = player()
        val isNewToCodex = db.ownedCreatures().all().none { it.creatureId == creatureId }

        val entity = OwnedCreatureEntity(
            creatureId = creatureId,
            isShiny = isShiny,
            statPower = random.nextInt(OwnedCreatureEntity.MAX_STAT + 1),
            statGrace = random.nextInt(OwnedCreatureEntity.MAX_STAT + 1),
            statWard = random.nextInt(OwnedCreatureEntity.MAX_STAT + 1),
            caughtAt = now,
            caughtLat = lat,
            caughtLng = lng,
            caughtPlace = place,
        )
        val newId = db.ownedCreatures().insert(entity)

        var xpGain = Progression.catchXp(def.rarity, isShiny) + skillXp
        var ljosGain = 0
        when (capsule.bonus) {
            CapsuleBonus.PLAYER_XP -> xpGain += Progression.SPARK_BONUS_XP
            CapsuleBonus.LJOS -> ljosGain = Progression.ESSENCE_LJOS
            null -> Unit
        }

        val levelBefore = Progression.levelForPlayerXp(before.xp)
        val xpAfter = before.xp + xpGain
        val levelAfter = Progression.levelForPlayerXp(xpAfter)

        val places = (before.places + place.takeIf { it.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.distinct()
        db.player().update(
            before.copy(
                xp = xpAfter,
                totalCatches = before.totalCatches + 1,
                shinies = before.shinies + if (isShiny) 1 else 0,
                ljos = before.ljos + ljosGain,
                placesCsv = places.joinToString("|"),
                // A notable catch resets the pity counter, anything else leaves it climbing.
                pityCounter = if (def.rarity.isNotable) 0 else before.pityCounter + 1,
            )
        )
        if (levelAfter > levelBefore) {
            // Each level hands over a starter bundle alongside the extra slots.
            grantItems(mapOf(CapsuleType.CAPTURE to 8, CapsuleType.ENHANCED to 2, CapsuleType.SPARK to 1))
        }
        db.catchLog().insert(CatchLogEntity(0, creatureId, true, capsule.name, now, lat, lng))
        addCompanionXp(Progression.companionXpForCatch(def.rarity))

        return CatchOutcome(
            owned = entity.copy(id = newId),
            playerXp = xpGain,
            ljosGained = ljosGain,
            levelledUpTo = if (levelAfter > levelBefore) levelAfter else null,
            isNewToCodex = isNewToCodex,
        )
    }

    override suspend fun recordMiss(creatureId: String, capsule: CapsuleType, lat: Double, lng: Double) {
        db.catchLog().insert(
            CatchLogEntity(0, creatureId, false, capsule.name, System.currentTimeMillis(), lat, lng)
        )
    }

    override suspend fun recordFlee(rarityIsNotable: Boolean) {
        // A notable creature that got away still moves the pity counter, so a bad run
        // shortens the wait for the next one instead of being pure loss.
        if (!rarityIsNotable) return
        val state = player()
        db.player().update(state.copy(pityCounter = state.pityCounter + 1))
    }

    override suspend fun setCompanion(ownedId: Long?) {
        val state = player()
        db.player().update(state.copy(companionId = ownedId))
    }

    override suspend fun renameOwned(ownedId: Long, nickname: String?) {
        val owned = db.ownedCreatures().byId(ownedId) ?: return
        db.ownedCreatures().update(owned.copy(nickname = nickname?.takeIf { it.isNotBlank() }))
    }

    override suspend fun releaseOwned(ownedId: Long) {
        val state = player()
        if (state.companionId == ownedId) db.player().update(state.copy(companionId = null))
        db.ownedCreatures().delete(ownedId)
    }

    override suspend fun addDistance(meters: Double): CompanionProgress {
        if (meters <= 0) return CompanionProgress(0, null)
        val state = player()
        db.player().update(state.copy(distanceMeters = state.distanceMeters + meters))
        return addCompanionXp(Progression.companionXpForDistance(meters))
    }

    override suspend fun addCompanionXp(amount: Int): CompanionProgress {
        if (amount <= 0) return CompanionProgress(0, null)
        val state = player()
        val companionId = state.companionId ?: return CompanionProgress(0, null)
        val companion = db.ownedCreatures().byId(companionId) ?: return CompanionProgress(0, null)
        val def = roster[companion.creatureId] ?: return CompanionProgress(0, null)

        // Ljós fed to a creature makes it gain experience faster, it never skips the journey.
        val boosted = (amount * Progression.ljosRate(companion.ljosSpent)).toInt().coerceAtLeast(1)
        val newXp = companion.xp + boosted

        if (def.metamorphoses && newXp >= def.metamorphXp) {
            val nextId = def.nextFormId
            val nextDef = nextId?.let { roster[it] }
            if (nextDef != null) {
                db.ownedCreatures().update(
                    companion.copy(creatureId = nextDef.id, stage = companion.stage + 1, xp = 0)
                )
                val after = player()
                db.player().update(after.copy(metamorphoses = after.metamorphoses + 1))
                return CompanionProgress(boosted, nextDef.name)
            }
        }
        db.ownedCreatures().update(companion.copy(xp = newXp))
        return CompanionProgress(boosted, null)
    }

    override suspend fun feedLjos(ownedId: Long, amount: Int): Boolean {
        val state = player()
        if (amount <= 0 || state.ljos < amount) return false
        val owned = db.ownedCreatures().byId(ownedId) ?: return false
        db.ownedCreatures().update(owned.copy(ljosSpent = owned.ljosSpent + amount))
        db.player().update(state.copy(ljos = state.ljos - amount))
        return true
    }

    override suspend fun recentCatches(limit: Int): List<CatchLogEntity> = db.catchLog().recent(limit)

    override suspend fun snapshot(): BackupSnapshot {
        val state = player()
        val owned = db.ownedCreatures().all()
        return BackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            player = PlayerBackup(
                xp = state.xp,
                // The companion travels as a position in the list, because row ids are
                // rewritten on import and a stored id would point at the wrong creature.
                companionIndex = owned.indexOfFirst { it.id == state.companionId },
                totalCatches = state.totalCatches,
                distanceMeters = state.distanceMeters,
                shinies = state.shinies,
                stopSpins = state.stopSpins,
                metamorphoses = state.metamorphoses,
                ljos = state.ljos,
                perfectThrows = state.perfectThrows,
                places = state.places,
                pityCounter = state.pityCounter,
                createdAt = state.createdAt,
            ),
            creatures = owned.map {
                OwnedBackup(
                    it.creatureId, it.isShiny, it.statPower, it.statGrace, it.statWard,
                    it.xp, it.stage, it.ljosSpent, it.caughtAt, it.caughtLat, it.caughtLng,
                    it.caughtPlace, it.nickname,
                )
            },
            stops = db.stops().all().map {
                StopBackup(
                    it.name, it.elendianName, it.lat, it.lng, it.radiusMeters, it.cooldownMinutes,
                    it.lastSpunAt, it.createdAt, it.spins, it.itemsEarned, it.bonusSpawns,
                )
            },
            items = db.items().all().associate { it.type to it.quantity },
        )
    }

    override suspend fun restore(snapshot: BackupSnapshot) {
        db.ownedCreatures().clear()
        db.stops().clear()
        db.items().clear()
        db.catchLog().clear()

        val newIds = snapshot.creatures.map { backup ->
            db.ownedCreatures().insert(
                OwnedCreatureEntity(
                    creatureId = backup.creatureId,
                    isShiny = backup.isShiny,
                    statPower = backup.statPower,
                    statGrace = backup.statGrace,
                    statWard = backup.statWard,
                    xp = backup.xp,
                    stage = backup.stage,
                    ljosSpent = backup.ljosSpent,
                    caughtAt = backup.caughtAt,
                    caughtLat = backup.caughtLat,
                    caughtLng = backup.caughtLng,
                    caughtPlace = backup.caughtPlace,
                    nickname = backup.nickname,
                )
            )
        }
        snapshot.stops.forEach { backup ->
            db.stops().insert(
                StopEntity(
                    name = backup.name,
                    elendianName = backup.elendianName,
                    lat = backup.lat,
                    lng = backup.lng,
                    radiusMeters = backup.radiusMeters,
                    cooldownMinutes = backup.cooldownMinutes,
                    lastSpunAt = backup.lastSpunAt,
                    createdAt = backup.createdAt,
                    spins = backup.spins,
                    itemsEarned = backup.itemsEarned,
                    bonusSpawns = backup.bonusSpawns,
                )
            )
        }
        snapshot.items
            .filterKeys { CapsuleType.fromNameOrNull(it) != null }
            .forEach { (type, quantity) -> db.items().upsert(ItemEntity(type, quantity)) }

        val backupPlayer = snapshot.player
        db.player().insertIfMissing(PlayerStateEntity())
        db.player().update(
            PlayerStateEntity(
                xp = backupPlayer.xp,
                companionId = newIds.getOrNull(backupPlayer.companionIndex),
                totalCatches = backupPlayer.totalCatches,
                distanceMeters = backupPlayer.distanceMeters,
                shinies = backupPlayer.shinies,
                stopSpins = backupPlayer.stopSpins,
                metamorphoses = backupPlayer.metamorphoses,
                ljos = backupPlayer.ljos,
                perfectThrows = backupPlayer.perfectThrows,
                placesCsv = backupPlayer.places.joinToString("|"),
                pityCounter = backupPlayer.pityCounter,
                createdAt = backupPlayer.createdAt,
            )
        )
    }
}
