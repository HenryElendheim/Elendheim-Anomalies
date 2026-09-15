package com.elendheim.anomalies.ui.game

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elendheim.anomalies.appContainer
import com.elendheim.anomalies.data.backup.BackupResult
import com.elendheim.anomalies.data.db.StopEntity
import com.elendheim.anomalies.game.BiomeSensor
import com.elendheim.anomalies.game.CatchMath
import com.elendheim.anomalies.game.CapsuleType
import com.elendheim.anomalies.game.FieldEffects
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.game.MAX_CATCH_ATTEMPTS
import com.elendheim.anomalies.game.Progression
import com.elendheim.anomalies.game.Spawn
import com.elendheim.anomalies.game.SpawnEngine
import com.elendheim.anomalies.game.ThrowResult
import com.elendheim.anomalies.location.Fix
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The single view model the whole app runs on. Keeping one state holder means the map,
 * the Codex and the profile can never disagree about what the player owns.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.repository
    private val roster = container.roster
    private val spawnEngine = container.spawnEngine
    private val random = Random.Default

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _catch = MutableStateFlow<CatchSession?>(null)
    val catchSession: StateFlow<CatchSession?> = _catch.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _spin = MutableStateFlow<SpinSession?>(null)
    val spinSession: StateFlow<SpinSession?> = _spin.asStateFlow()

    private val _draft = MutableStateFlow<StopDraft?>(null)
    val stopDraft: StateFlow<StopDraft?> = _draft.asStateFlow()

    private var locationJob: Job? = null
    private var spawnJob: Job? = null
    private var lastFix: Fix? = null

    init {
        viewModelScope.launch { repository.ensureSeeded() }

        // Four stores folded into one state object, so a screen subscribes once.
        combine(
            repository.observePlayer(),
            repository.observeOwned(),
            repository.observeStops(),
            repository.observeItems(),
            container.settings.settings,
        ) { player, owned, stops, items, settings ->
            val companion = owned.firstOrNull { it.id == player.companionId }
            val def = companion?.let { roster[it.creatureId] }
            _state.value.copy(
                player = player,
                owned = owned,
                stops = stops,
                items = items,
                settings = settings,
                effects = FieldEffects.of(def?.power, companion?.stage ?: 0),
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    // Location and the spawning loop -------------------------------------------------

    fun onPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = granted)
        if (granted) startTracking() else stopTracking()
    }

    fun startTracking() {
        if (!container.location.hasPermission()) {
            _state.value = _state.value.copy(hasLocationPermission = false)
            return
        }
        _state.value = _state.value.copy(hasLocationPermission = true)
        if (locationJob?.isActive != true) {
            locationJob = container.location.fixes()
                .onEach { onFix(it) }
                .launchIn(viewModelScope)
        }
        if (spawnJob?.isActive != true) {
            spawnJob = viewModelScope.launch {
                while (true) {
                    delay(SpawnEngine.ROLL_INTERVAL_MILLIS)
                    rollSpawn()
                }
            }
        }
    }

    fun stopTracking() {
        locationJob?.cancel()
        spawnJob?.cancel()
        locationJob = null
        spawnJob = null
    }

    private fun onFix(fix: Fix) {
        val previous = lastFix
        lastFix = fix
        _state.value = _state.value.copy(
            fix = fix,
            biome = BiomeSensor.biomeAt(fix.lat, fix.lng),
            timeBand = BiomeSensor.timeBandNow(),
        )
        if (previous != null) {
            val moved = Geo.distanceMeters(previous.lat, previous.lng, fix.lat, fix.lng)
            // Travel is uncapped on purpose, so a train or a flight counts in full.
            if (moved >= 1.0) {
                viewModelScope.launch {
                    val progress = repository.addDistance(moved)
                    progress.metamorphosedInto?.let { announce("Your companion became $it") }
                }
            }
        }
    }

    private fun rollSpawn() {
        val current = _state.value
        // Anything past its lifetime goes first, so the map clears itself whether or not
        // there is room for something new.
        val alive = current.spawns.filter { it.expiresAt > System.currentTimeMillis() }
        val fix = current.fix
        if (fix == null || alive.size >= SpawnEngine.MAX_ACTIVE_SPAWNS) {
            if (alive.size != current.spawns.size) _state.value = current.copy(spawns = alive)
            return
        }
        val spawn = spawnEngine.spawnAt(fix.lat, fix.lng, current.effects, current.player.pityCounter)
        _state.value = current.copy(spawns = alive + spawn)
    }

    /** A stop spin can shake something loose right where the player is standing. */
    private fun spawnAtStop(lat: Double, lng: Double) {
        val current = _state.value
        val spawn = spawnEngine.spawnAt(lat, lng, current.effects, current.player.pityCounter)
        _state.value = current.copy(spawns = current.spawns + spawn)
    }

    // Stops --------------------------------------------------------------------------

    /**
     * Opens the placement flow. The draft starts unplaced and sitting on the player, so
     * the map can ask for a spot and then let the marker be dragged until it is right.
     */
    fun beginPlacement() {
        val fix = _state.value.fix
        if (fix == null) {
            announce("A position is needed before a stop can be placed")
            return
        }
        _draft.value = StopDraft(lat = fix.lat, lng = fix.lng)
    }

    /** Called on every tap and every drag of the marker while a draft is open. */
    fun movePlacement(lat: Double, lng: Double) {
        _draft.value = _draft.value?.copy(lat = lat, lng = lng, placed = true)
    }

    fun editDraft(
        name: String? = null,
        radiusMeters: Int? = null,
        cooldownMinutes: Int? = null,
    ) {
        val draft = _draft.value ?: return
        _draft.value = draft.copy(
            name = name ?: draft.name,
            radiusMeters = radiusMeters ?: draft.radiusMeters,
            cooldownMinutes = cooldownMinutes ?: draft.cooldownMinutes,
        )
    }

    fun cancelPlacement() {
        _draft.value = null
    }

    fun confirmPlacement() {
        val draft = _draft.value ?: return
        _draft.value = null
        viewModelScope.launch {
            repository.addStop(
                StopEntity(
                    name = draft.name.ifBlank { "Stop" },
                    lat = draft.lat,
                    lng = draft.lng,
                    radiusMeters = draft.radiusMeters,
                    cooldownMinutes = draft.cooldownMinutes,
                    createdAt = System.currentTimeMillis(),
                )
            )
            announce("${draft.name.ifBlank { "Stop" }} placed")
        }
    }

    fun updateStop(stop: StopEntity) = viewModelScope.launch { repository.updateStop(stop) }

    fun deleteStop(id: Long) = viewModelScope.launch { repository.deleteStop(id) }

    fun spinStop(stopId: Long) {
        viewModelScope.launch {
            val stop = _state.value.stops.firstOrNull { it.id == stopId } ?: return@launch
            val fix = _state.value.fix
            if (fix != null) {
                val distance = Geo.distanceMeters(fix.lat, fix.lng, stop.lat, stop.lng)
                if (distance > stop.radiusMeters) {
                    announce("Too far from ${stop.name}, ${Geo.format(distance)} away")
                    return@launch
                }
            }
            val reward = repository.spinStop(stopId, _state.value.effects.extraStopItems)
            if (reward == null) {
                announce("${stop.name} is still on cooldown")
                return@launch
            }
            if (reward.totalItems == 0) {
                announce("No room left to carry anything")
                return@launch
            }
            // The overlay owns the spin and the reveal from here, and closes itself.
            _spin.value = SpinSession(stop.name, reward)
            if (reward.bonusSpawn) spawnAtStop(stop.lat, stop.lng)
        }
    }

    fun dismissSpin() {
        _spin.value = null
    }

    // The catch ----------------------------------------------------------------------

    fun beginCatch(spawn: Spawn) {
        val def = roster[spawn.creatureId] ?: return
        val firstAvailable = CapsuleType.entries.firstOrNull { (_state.value.items[it] ?: 0) > 0 }
        _catch.value = CatchSession(
            spawn = spawn,
            def = def,
            capsule = firstAvailable ?: CapsuleType.CAPTURE,
            phase = if (firstAvailable == null) CatchPhase.OUT_OF_CAPSULES else CatchPhase.AIMING,
        )
    }

    fun selectCapsule(type: CapsuleType) {
        val session = _catch.value ?: return
        if (session.phase != CatchPhase.AIMING) return
        if ((_state.value.items[type] ?: 0) <= 0) return
        _catch.value = session.copy(capsule = type)
    }

    fun abandonCatch() {
        _catch.value = null
    }

    /**
     * Called the instant the flick leaves the finger. Everything the throw will be scored
     * on is decided here, which means the capsule the player watches tumble is the same
     * capsule the result is taken from.
     *
     * [ringFraction] is how wide the timing ring was, [flickStrength] how hard the flick
     * was, and [sidewaysFraction] how much of it went across rather than up.
     */
    fun releaseThrow(ringFraction: Float, flickStrength: Float, sidewaysFraction: Float) {
        val session = _catch.value ?: return
        if (session.phase != CatchPhase.AIMING) return

        val spinTurns = CatchMath.spinTurnsFor(flickStrength)
        val favoured = BiomeSensor.isFavoured(session.def.biome[session.spawn.biome] ?: 1.0)
        val result = ThrowResult(
            ring = CatchMath.gradeRing(ringFraction),
            curved = CatchMath.isCurve(sidewaysFraction),
            upright = CatchMath.landsUpright(spinTurns),
            favouredBiome = favoured,
        )
        _catch.value = session.copy(
            phase = CatchPhase.FLYING,
            spinTurns = spinTurns,
            curveDirection = sidewaysFraction,
            throwResult = result,
        )
    }

    /**
     * Called once the capsule has finished its flight, which is when the roll is made.
     *
     * The outcome is decided and written down here, but the screen is only moved as far
     * as SEALING. What it gets alongside that is a wobble count, and that is the whole
     * trick: a catch always rocks three times before it clicks, and a miss rocks a
     * number of times drawn from how close the roll actually came. So three wobbles
     * genuinely means it nearly held, and watching the third one is worth something.
     */
    fun settleThrow() {
        val session = _catch.value ?: return
        if (session.phase != CatchPhase.FLYING) return
        val result = session.throwResult ?: return

        viewModelScope.launch {
            val consumed = repository.consumeItem(session.capsule)
            if (!consumed) {
                _catch.value = session.copy(phase = CatchPhase.OUT_OF_CAPSULES)
                return@launch
            }
            val chance = CatchMath.chance(session.def, session.capsule, result)
            val roll = random.nextDouble()
            val caught = roll < chance
            val fix = _state.value.fix

            if (caught) {
                val place = fix?.let { container.placeNamer.nameFor(it.lat, it.lng) }.orEmpty()
                val outcome = repository.recordCatch(
                    creatureId = session.def.id,
                    isShiny = session.spawn.isShiny,
                    capsule = session.capsule,
                    skillXp = CatchMath.skillXp(result),
                    lat = fix?.lat ?: session.spawn.lat,
                    lng = fix?.lng ?: session.spawn.lng,
                    place = place,
                )
                if (outcome == null) {
                    announce("No space left, level up or release something first")
                    _catch.value = session.copy(phase = CatchPhase.RESOLVED, succeeded = false)
                    return@launch
                }
                removeSpawn(session.spawn)
                _catch.value = session.copy(
                    phase = CatchPhase.SEALING,
                    settledPhase = CatchPhase.CAUGHT,
                    succeeded = true,
                    outcome = outcome,
                    wobbles = CatchSession.MAX_WOBBLES,
                )
                return@launch
            }

            repository.recordMiss(session.def.id, session.capsule, fix?.lat ?: 0.0, fix?.lng ?: 0.0)
            // A Tinker companion sometimes hands the capsule straight back.
            val refunded = random.nextDouble() < _state.value.effects.capsuleRefundChance
            if (refunded) repository.grantItems(mapOf(session.capsule to 1))

            val settled = if (session.attempt >= MAX_CATCH_ATTEMPTS) {
                repository.recordFlee(session.def.rarity.isNotable)
                removeSpawn(session.spawn)
                CatchPhase.FLED
            } else {
                CatchPhase.RESOLVED
            }
            _catch.value = session.copy(
                phase = CatchPhase.SEALING,
                settledPhase = settled,
                succeeded = false,
                capsuleRefunded = refunded,
                wobbles = CatchMath.wobblesForMiss(roll, chance, CatchSession.MAX_WOBBLES),
            )
        }
    }

    /** Called by the screen once the capsule has stopped rocking. */
    fun finishSealing() {
        val session = _catch.value ?: return
        if (session.phase != CatchPhase.SEALING) return
        _catch.value = session.copy(phase = session.settledPhase)
    }

    /** Moves on to the next attempt after a miss, keeping the same creature in front of us. */
    fun nextAttempt() {
        val session = _catch.value ?: return
        if (session.phase != CatchPhase.RESOLVED) return
        val stillHolding = CapsuleType.entries.firstOrNull { (_state.value.items[it] ?: 0) > 0 }
        if (stillHolding == null) {
            _catch.value = session.copy(phase = CatchPhase.OUT_OF_CAPSULES)
            return
        }
        val capsule = if ((_state.value.items[session.capsule] ?: 0) > 0) session.capsule else stillHolding
        _catch.value = session.copy(
            attempt = session.attempt + 1,
            phase = CatchPhase.AIMING,
            capsule = capsule,
            throwResult = null,
            spinTurns = 0f,
            capsuleRefunded = false,
            wobbles = 0,
            settledPhase = CatchPhase.RESOLVED,
        )
    }

    private fun removeSpawn(spawn: Spawn) {
        _state.value = _state.value.copy(spawns = _state.value.spawns.filterNot { it.key == spawn.key })
    }

    // The collection -----------------------------------------------------------------

    fun setCompanion(ownedId: Long?) = viewModelScope.launch { repository.setCompanion(ownedId) }

    fun renameOwned(ownedId: Long, nickname: String?) =
        viewModelScope.launch { repository.renameOwned(ownedId, nickname) }

    fun releaseOwned(ownedId: Long) = viewModelScope.launch { repository.releaseOwned(ownedId) }

    fun feedPowder(ownedId: Long, amount: Int) {
        viewModelScope.launch {
            if (repository.feedPowder(ownedId, amount)) {
                announce("Powder given, this one will grow faster now")
            } else {
                announce("Not enough Empower Powder")
            }
        }
    }

    // Settings and backups -----------------------------------------------------------

    fun setHighContrast(value: Boolean) = viewModelScope.launch { container.settings.setHighContrast(value) }
    fun setFontScale(value: Float) = viewModelScope.launch { container.settings.setFontScale(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { container.settings.setReduceMotion(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { container.settings.setHaptics(value) }
    fun setShowDistances(value: Boolean) = viewModelScope.launch { container.settings.setShowDistances(value) }
    fun setLargeTouchTargets(value: Boolean) = viewModelScope.launch { container.settings.setLargeTouchTargets(value) }
    fun setQuickSpins(value: Boolean) = viewModelScope.launch { container.settings.setQuickSpins(value) }
    fun setQuickCatches(value: Boolean) = viewModelScope.launch { container.settings.setQuickCatches(value) }
    fun setDetailedMap(value: Boolean) = viewModelScope.launch { container.settings.setDetailedMap(value) }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            announce(
                when (val result = container.backups.exportTo(uri)) {
                    is BackupResult.Exported -> "Saved ${result.creatures} creatures and ${result.stops} stops"
                    is BackupResult.Failed -> result.reason
                    else -> "Done"
                }
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            announce(
                when (val result = container.backups.importFrom(uri)) {
                    is BackupResult.Imported -> "Loaded ${result.creatures} creatures and ${result.stops} stops"
                    is BackupResult.Failed -> result.reason
                    else -> "Done"
                }
            )
        }
    }

    // Messages -----------------------------------------------------------------------

    private fun announce(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** The roster entry behind an id, so screens never reach for the roster themselves. */
    fun definitionOf(creatureId: String) = roster[creatureId]

    /** The whole roster, used by the Codex to show what has not been found yet. */
    fun allDefinitions() = roster.all

    /** Slots unlocked at the current level, read by the items and profile screens. */
    fun slotCapacity(): Int = Progression.inventorySlots(Progression.levelForPlayerXp(_state.value.player.xp))

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                GameViewModel(app)
            }
        }
    }
}
