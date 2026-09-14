package com.elendheim.anomalies.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Small distance helpers shared by the map, the stops and the travel experience counter. */
object Geo {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Straight line distance between two points on the surface, in metres. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(max(0.0, 1 - a)))
    }

    /** Moves a point by a number of metres north and east and gives the new coordinates. */
    fun offset(lat: Double, lng: Double, northMeters: Double, eastMeters: Double): Pair<Double, Double> {
        val newLat = lat + Math.toDegrees(northMeters / EARTH_RADIUS_METERS)
        val shrink = cos(Math.toRadians(lat)).let { if (abs(it) < 1e-6) 1e-6 else it }
        val newLng = lng + Math.toDegrees(eastMeters / (EARTH_RADIUS_METERS * shrink))
        return newLat to newLng
    }

    /** How far north and east a point sits relative to an origin, used to draw the map. */
    fun toLocalMeters(originLat: Double, originLng: Double, lat: Double, lng: Double): Pair<Double, Double> {
        val north = Math.toRadians(lat - originLat) * EARTH_RADIUS_METERS
        val east = Math.toRadians(lng - originLng) * EARTH_RADIUS_METERS * cos(Math.toRadians(originLat))
        return north to east
    }

    /** Compass bearing from one point to another, in radians clockwise from north. */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val (north, east) = toLocalMeters(lat1, lng1, lat2, lng2)
        return (atan2(east, north) + 2 * PI) % (2 * PI)
    }

    /** A short human reading of a distance, so every screen formats it the same way. */
    fun format(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> String.format("%.1f km", meters / 1000)
        else -> "${(meters / 1000).roundToInt()} km"
    }
}
