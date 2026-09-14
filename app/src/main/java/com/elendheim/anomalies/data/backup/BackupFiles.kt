package com.elendheim.anomalies.data.backup

import android.content.Context
import android.net.Uri
import com.elendheim.anomalies.data.repo.BackupSnapshot
import com.elendheim.anomalies.data.repo.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What an export or an import finished as, so the settings screen can report it plainly. */
sealed interface BackupResult {
    data class Exported(val creatures: Int, val stops: Int) : BackupResult
    data class Imported(val creatures: Int, val stops: Int) : BackupResult
    data class Failed(val reason: String) : BackupResult
}

/**
 * Writes the save to a file the user picked and reads one back. The app never reaches
 * into storage on its own, which means the export lands exactly where it was asked to.
 */
class BackupFiles(
    private val context: Context,
    private val repository: GameRepository,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = repository.snapshot()
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray())
            } ?: return@runCatching BackupResult.Failed("Could not open that file for writing")
            BackupResult.Exported(snapshot.creatures.size, snapshot.stops.size)
        }.getOrElse { BackupResult.Failed(it.message ?: "Export failed") }
    }

    suspend fun importFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            } ?: return@runCatching BackupResult.Failed("Could not open that file for reading")

            val snapshot = json.decodeFromString(BackupSnapshot.serializer(), text)
            if (snapshot.format > BackupSnapshot.FORMAT_VERSION) {
                return@runCatching BackupResult.Failed("That backup was written by a newer version")
            }
            repository.restore(snapshot)
            BackupResult.Imported(snapshot.creatures.size, snapshot.stops.size)
        }.getOrElse { BackupResult.Failed(it.message ?: "That file is not a valid backup") }
    }

    companion object {
        /** A dated name so successive exports sit side by side instead of overwriting. */
        fun suggestedFileName(): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
            return "elendheim-anomalies-$stamp.json"
        }

        const val MIME_TYPE = "application/json"
    }
}
