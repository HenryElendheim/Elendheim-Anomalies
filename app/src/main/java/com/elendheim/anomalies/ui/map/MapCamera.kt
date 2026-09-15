package com.elendheim.anomalies.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.elendheim.anomalies.game.Geo
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the map is looking. One camera drives everything: the tile backdrop underneath,
 * the markers drawn on top, and the maths that turns a tap back into coordinates. Because
 * there is only one of these, the backdrop and the overlay can never drift apart.
 *
 * [zoom] means exactly what it means to the tile renderer, so the two agree by
 * construction. [bearingDegrees] is the compass direction shown pointing up the screen,
 * and [devicePixelRatio] is the screen density, which the renderer needs because it
 * measures zoom in density independent pixels while the overlay draws in real ones.
 */
@Immutable
data class MapCamera(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Float = DEFAULT_ZOOM,
    val bearingDegrees: Float = 0f,
    val devicePixelRatio: Float = 1f,
    /** While true the camera rides along with the player instead of staying put. */
    val followPlayer: Boolean = true,
) {
    fun withZoom(value: Float): MapCamera = copy(zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun withBearing(value: Float): MapCamera = copy(bearingDegrees = normaliseBearing(value))

    /** How many real metres one screen pixel covers at this camera. */
    val metersPerPixel: Double
        get() = MapProjection.metersPerPixel(centerLat, zoom, devicePixelRatio)

    companion object {
        const val MIN_ZOOM = 11f
        const val MAX_ZOOM = 19f
        /** Only used before the screen has been measured and a fitting zoom worked out. */
        const val DEFAULT_ZOOM = 16f

        fun normaliseBearing(value: Float): Float = ((value % 360f) + 360f) % 360f
    }
}

/**
 * Turning coordinates into screen points and back.
 *
 * The scale has to match the tile renderer exactly or the map slides out from under the
 * markers. Two things decide it, and both are easy to get wrong: the renderer measures a
 * zoom step against a tile [TILE_PIXELS] wide, not the 256 that older slippy maps used,
 * and it counts in density independent pixels while this overlay draws in real device
 * pixels. Both corrections live in [metersPerPixel] and nowhere else.
 */
object MapProjection {

    /** How far round the earth is at the equator, in metres. */
    private const val WORLD_CIRCUMFERENCE_METERS = 40_075_016.686

    /** The tile size the renderer measures a zoom step against. */
    private const val TILE_PIXELS = 512.0

    fun metersPerPixel(lat: Double, zoom: Float, devicePixelRatio: Float): Double {
        val density = if (devicePixelRatio <= 0f) 1.0 else devicePixelRatio.toDouble()
        return WORLD_CIRCUMFERENCE_METERS * cos(Math.toRadians(lat)) /
            (TILE_PIXELS * 2.0.pow(zoom.toDouble()) * density)
    }

    /** The zoom at which one device pixel covers the given number of metres. */
    fun zoomForMetersPerPixel(lat: Double, metersPerPixel: Double, devicePixelRatio: Float): Float {
        val density = if (devicePixelRatio <= 0f) 1.0 else devicePixelRatio.toDouble()
        val ratio = WORLD_CIRCUMFERENCE_METERS * cos(Math.toRadians(lat)) /
            (TILE_PIXELS * metersPerPixel * density)
        return (ln(ratio) / ln(2.0)).toFloat()
    }

    /**
     * The zoom that makes a real world radius fill a comfortable share of the screen.
     * Working it out from the actual screen rather than picking a number means the map
     * opens at the same useful scale on a small phone and a large one.
     */
    fun zoomFittingRadius(
        lat: Double,
        radiusMeters: Double,
        canvas: Size,
        devicePixelRatio: Float,
    ): Float {
        val shortest = minOf(canvas.width, canvas.height)
        if (shortest <= 0f || radiusMeters <= 0.0) return MapCamera.DEFAULT_ZOOM
        val target = shortest * RADIUS_SCREEN_SHARE
        return zoomForMetersPerPixel(lat, radiusMeters / target, devicePixelRatio)
            .coerceIn(MapCamera.MIN_ZOOM, MapCamera.MAX_ZOOM)
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
     * Turns a drag across the screen into a move of the camera centre. The drag is
     * un-rotated first, so dragging always follows the screen rather than following north.
     */
    fun panned(camera: MapCamera, pan: Offset): MapCamera {
        val scale = camera.metersPerPixel
        val bearing = Math.toRadians(camera.bearingDegrees.toDouble())
        // The world moves the opposite way to the finger, which is what makes the ground
        // feel like it is being dragged rather than pushed.
        val screenEast = -pan.x * scale
        val screenNorth = pan.y * scale
        val east = screenEast * cos(bearing) + screenNorth * sin(bearing)
        val north = -screenEast * sin(bearing) + screenNorth * cos(bearing)
        val (lat, lng) = Geo.offset(camera.centerLat, camera.centerLng, north, east)
        return camera.copy(centerLat = lat, centerLng = lng)
    }

    /** A pinch reports a ratio, and zoom steps are powers of two, so it converts across. */
    fun zoomSteps(pinchRatio: Float): Float {
        if (pinchRatio <= 0f) return 0f
        return (ln(pinchRatio.toDouble()) / ln(2.0)).toFloat()
    }

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

    /** How much of the shorter screen edge the spawn radius should take up by default. */
    const val RADIUS_SCREEN_SHARE = 0.30f

    private const val MIN_MARKER_SCALE = 0.55f
    private const val MAX_MARKER_SCALE = 1.5f
}
