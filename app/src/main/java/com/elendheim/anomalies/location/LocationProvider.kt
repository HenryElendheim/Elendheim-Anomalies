package com.elendheim.anomalies.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One position fix, reduced to only what the game needs. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)

/**
 * Streams the device position while a screen is watching and stops the moment it is not.
 * Updates only run in the foreground, which keeps the battery cost of an open map honest.
 */
class LocationProvider(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun fixes(): Flow<Fix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_MOVE_METERS)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                // Poor fixes are dropped so indoor drift cannot fake travelled distance.
                if (location.accuracy > MAX_USABLE_ACCURACY_METERS) return
                trySend(Fix(location.latitude, location.longitude, location.accuracy, location.time))
            }
        }
        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        const val UPDATE_INTERVAL_MILLIS = 5_000L
        const val MIN_MOVE_METERS = 5f
        const val MAX_USABLE_ACCURACY_METERS = 60f
    }
}
