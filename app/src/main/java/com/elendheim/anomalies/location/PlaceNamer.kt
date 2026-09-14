package com.elendheim.anomalies.location

import android.content.Context
import android.location.Geocoder
import com.elendheim.anomalies.game.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Turns coordinates into something readable for the Codex line that says where a
 * creature was first caught. It falls back to the raw coordinates rather than failing,
 * which means the feature keeps working with no network and no geocoder on the device.
 */
class PlaceNamer(private val context: Context) {

    suspend fun nameFor(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val address = results?.firstOrNull() ?: return@runCatching null
            listOfNotNull(address.locality ?: address.subAdminArea, address.countryName)
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback(lat, lng)
    }

    private fun fallback(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.3f, %.3f", lat, lng)

    /** Shared so the map and the stops list read distances the same way. */
    fun distanceLabel(meters: Double): String = Geo.format(meters)
}
