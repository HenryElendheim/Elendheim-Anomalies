package com.elendheim.anomalies

import android.content.Context
import com.elendheim.anomalies.data.backup.BackupFiles
import com.elendheim.anomalies.data.db.AppDatabase
import com.elendheim.anomalies.data.prefs.SettingsStore
import com.elendheim.anomalies.data.repo.GameRepository
import com.elendheim.anomalies.data.repo.RoomGameRepository
import com.elendheim.anomalies.data.roster.CreatureRoster
import com.elendheim.anomalies.game.SpawnEngine
import com.elendheim.anomalies.location.LocationProvider
import com.elendheim.anomalies.location.PlaceNamer

/**
 * The one place the app's pieces are built and handed out. Screens ask the container for
 * what they need, which means there is a single wiring diagram instead of each screen
 * reaching for a database of its own.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val roster: CreatureRoster by lazy { CreatureRoster.load(appContext) }
    val repository: GameRepository by lazy { RoomGameRepository(AppDatabase.get(appContext), roster) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val spawnEngine: SpawnEngine by lazy { SpawnEngine(roster) }
    val location: LocationProvider by lazy { LocationProvider(appContext) }
    val placeNamer: PlaceNamer by lazy { PlaceNamer(appContext) }
    val backups: BackupFiles by lazy { BackupFiles(appContext, repository) }
}
