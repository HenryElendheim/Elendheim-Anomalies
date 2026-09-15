package com.elendheim.anomalies.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.elendheim.anomalies.game.Geo
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the map is looking. One camera drives everything: the tile backdrop underneath,
 * the markers drawn on top, and the maths that turns a tap back into coordinates. Because
 * there is only one of these, the backdrop and the overlay can never drift apart.
 *
 * [zoom] is the usual web map zoom, so it means the same thing here as it does to the
 * tile renderer. [bearingDegrees] is the compass direction shown pointing up the screen.
 */
@Immutable
data class MapCamera(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Float = DEFAULT_ZOOM,
    val bearingDegrees: Float = 0f,
    /** While true the camera rides along with the player instead of staying put. */
    val followPlayer: Boolean = true,
) {
    fun withZoom(value: Float): MapCamera = copy(zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun withBearing(value: Float): MapCamera = copy(bearingDegrees = normaliseBearing(value))

    /** How many real metres one screen pixel covers at this camera. */
    val metersPerPixel: Double get() = MapProjection.metersPerPixel(centerLat, zoom)

    companion object {
        const val MIN_ZOOM = 13f
        const val MAX_ZOOM = 19.5f
        const val DEFAULT_ZOOM = 17f

        fun normaliseBearing(value: Float): Float = ((value % 360f) + 360f) % 360f
    }
}

/**
 * Turning coordinates into screen points and back. Everything is worked out against the
 * camera centre in metres first, which keeps it accurate over the couple of kilometres
 * the game ever actually shows.
 */
object MapProjection {

    /** The width of the whole world in pixels at zoom zero, as every web map defines it. */
    private const val EQUATOR_METERS_PER_PIXEL = 156_543.03392804097

    fun metersPerPixel(lat: Double, zoom: Float): Double =
        EQUATOR_METERS_PER_PIXEL * cos(Math.toRadians(lat)) / 2.0.pow(zoom.toDouble())

    /** The zoom at which one pixel covers the given number of metres. */
    fun zoomForMetersPerPixel(lat: Double, metersPerPixel: Double): Float {
        val ratio = EQUATOR_METERS_PER_PIXEL * cos(Math.toRadians(lat)) / metersPerPixel
        return (Math.log(ratio) / Math.log(2.0)).toFloat()
    }

    /** Where a coordinate lands on screen, with the camera rotation applied. */
    fun toScreen(camera: MapCamera, lat: Double, lng: Double, canvas: Size): Offset {
        val (north, east) = Geo.toLocalMeters(camera.centerLat, camera.centerLng, lat, lng)
        val bearing = Math.toRadians(camera.bearingDegrees.toDouble())
        // Rotating the world into the camera frame is what makes the whole map turn.
        val rotatedEast = east * cos(bearing) - north * sin(bearing)
        val rotatedNorth = east * sin(bearing) + north * cos(bearing)
        val scale = camera.metersPerPixel
        return Offset(
            (canvas.width / 2.0 + rotatedEast / scale).toFloat(),
            (canvas.height / 2.0 - rotatedNorth / scale).toFloat(),
        )
    }

    /** The other direction, which is how a tap on the map becomes a place. */
    fun toWorld(camera: MapCamera, point: Offset, canvas: Size): Pair<Double, Double> {
        val scale = camera.metersPerPixel
        val rotatedEast = (point.x - canvas.width / 2.0) * scale
        val rotatedNorth = -(point.y - canvas.height / 2.0) * scale
        val bearing = Math.toRadians(camera.bearingDegrees.toDouble())
        val east = rotatedEast * cos(bearing) + rotatedNorth * sin(bearing)
        val north = -rotatedEast * sin(bearing) + rotatedNorth * cos(bearing)
        return Geo.offset(camera.centerLat, camera.centerLng, north, east)
    }

    /** Turns a real world distance into a length on screen at this camera. */
    fun metersToPixels(camera: MapCamera, meters: Double): Float =
        (meters / camera.metersPerPixel).toFloat()

    /**
     * How much bigger or smaller to draw the markers at this zoom. Sprites and stop
     * markers grow as you zoom in so they stay part of the world, but the range is
     * clamped so they never vanish or swallow the screen.
     */
    fun markerScale(zoom: Float): Float {
        val span = MapCamera.MAX_ZOOM - MapCamera.MIN_ZOOM
        val position = ((zoom - MapCamera.MIN_ZOOM) / span).coerceIn(0f, 1f)
        return MIN_MARKER_SCALE + (MAX_MARKER_SCALE - MIN_MARKER_SCALE) * position
    }

    private const val MIN_MARKER_SCALE = 0.55f
    private const val MAX_MARKER_SCALE = 1.5f
}
