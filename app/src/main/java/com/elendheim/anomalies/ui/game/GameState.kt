package com.elendheim.anomalies.ui.game

import com.elendheim.anomalies.data.db.OwnedCreatureEntity
import com.elendheim.anomalies.data.db.PlayerStateEntity
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.data.prefs.AppSettings
import com.elendheim.anomalies.data.repo.CatchOutcome
import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.FieldEffects
import com.elendheim.anomalies.game.Spawn
import com.elendheim.anomalies.game.ThrowResult
import com.elendheim.anomalies.game.TimeBand
import com.elendheim.anomalies.location.Fix

/** Everything the five main screens read from. One state object, one source of truth. */
data class GameUiState(
    val player: PlayerStateEntity = PlayerStateEntity(),
    val owned: List<OwnedCreatureEntity> = emptyList(),
    val stops: List<StopEntity> = emptyList(),
    val items: Map<CapsuleType, Int> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val fix: Fix? = null,
    val hasLocationPermission: Boolean = false,
    val spawns: List<Spawn> = emptyList(),
    val biome: Biome = Biome.URBAN,
    val timeBand: TimeBand = TimeBand.DAY,
    val effects: FieldEffects = FieldEffects.NONE,
) {
    /** The creature currently out in the field, or nothing when none is set. */
    val companion: OwnedCreatureEntity?
        get() = owned.firstOrNull { it.id == player.companionId }

    /** How many slots are taken by items and creatures together. */
    val slotsUsed: Int get() = items.values.sum() + owned.size
}

/** Where a catch attempt has got to. */
enum class CatchPhase { AIMING, FLYING, RESOLVED, CAUGHT, FLED, OUT_OF_CAPSULES }

/** One encounter, from the tap on the map to the creature being caught or fleeing. */
data class CatchSession(
    val spawn: Spawn,
    val def: CreatureDef,
    val capsule: CapsuleType = CapsuleType.CAPTURE,
    val attempt: Int = 1,
    val phase: CatchPhase = CatchPhase.AIMING,
    /** Full turns the capsule tumbles through, so the animation and the score agree. */
    val spinTurns: Float = 0f,
    val curveDirection: Float = 0f,
    val throwResult: ThrowResult? = null,
    val succeeded: Boolean = false,
    val capsuleRefunded: Boolean = false,
    val outcome: CatchOutcome? = null,
) {
    val attemptsLeft: Int get() = com.elendheim.anomalies.game.MAX_CATCH_ATTEMPTS - attempt + 1
}
