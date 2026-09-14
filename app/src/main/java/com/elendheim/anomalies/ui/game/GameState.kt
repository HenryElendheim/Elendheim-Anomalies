package com.elendheim.anomalies.ui.game

import com.elendheim.anomalies.data.db.OwnedCreatureEntity
import com.elendheim.anomalies.data.db.PlayerStateEntity
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.data.prefs.AppSettings
import com.elendheim.anomalies.data.repo.CatchOutcome
import com.elendheim.anomalies.data.repo.SpinReward
import com.elendheim.anomalies.data.roster.CreatureDef
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.FieldEffects
import com.elendheim.anomalies.game.MAX_CATCH_ATTEMPTS
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

/**
 * Where a catch attempt has got to.
 *
 * SEALING is the part that matters. The roll has already been made and written down, but
 * the capsule has not stopped moving yet, so the screen can rock it once, twice, three
 * times before saying which way it went.
 */
enum class CatchPhase { AIMING, FLYING, SEALING, RESOLVED, CAUGHT, FLED, OUT_OF_CAPSULES }

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
    /** How many times the capsule rocks before it settles. Three means it very nearly held. */
    val wobbles: Int = 0,
    /** Where the screen goes once the capsule has finished rocking. */
    val settledPhase: CatchPhase = CatchPhase.RESOLVED,
) {
    val attemptsLeft: Int get() = MAX_CATCH_ATTEMPTS - attempt + 1

    /** A three wobble escape is the near miss, and the screen says so. */
    val wasCloseCall: Boolean get() = !succeeded && wobbles >= MAX_WOBBLES

    companion object {
        /** A sealed capsule always rocks the full set, so three rocks means hope. */
        const val MAX_WOBBLES = 3
    }
}

/** A stop being spun, held open until the reveal has finished playing. */
data class SpinSession(
    val stopName: String,
    val reward: SpinReward,
)

/**
 * A stop being positioned on the map. It exists from the moment the plus button is
 * pressed until it is confirmed or thrown away, which is what lets the marker be nudged
 * around until it sits in the right place.
 */
data class StopDraft(
    val lat: Double,
    val lng: Double,
    val name: String = "",
    val radiusMeters: Int = 60,
    val cooldownMinutes: Int = 5,
    /** False until the map has been tapped once, so the panel can ask for a spot first. */
    val placed: Boolean = false,
)
