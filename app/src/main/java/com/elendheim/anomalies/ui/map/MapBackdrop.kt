package com.elendheim.anomalies.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.elendheim.anomalies.game.Biome
import com.elendheim.anomalies.ui.theme.theme

/**
 * Whatever is drawn under the markers.
 *
 * When the detailed map is switched on and the renderer is working, that is real map
 * tiles with a dark wash over them so the streets read in the app's own colours rather
 * than as a bright white page. When it is off, or the renderer failed to start, the
 * same space is filled by a plain drawn ground that follows the very same camera, so
 * the game plays identically either way.
 */
@Composable
fun MapBackdrop(
    camera: MapCamera,
    detailed: Boolean,
    biome: Biome,
    modifier: Modifier = Modifier,
) {
    // Once the renderer has failed there is no point asking it again this session.
    var rendererFailed by remember { mutableStateOf(false) }
    val useTiles = detailed && !rendererFailed
    val colors = theme

    Box(modifier.fillMaxSize().background(colors.background)) {
        if (useTiles) {
            MapLibreHost(
                camera = camera,
                modifier = Modifier.fillMaxSize(),
                onUnavailable = { rendererFailed = true },
            )
            // The wash is what turns any basemap into this app's map. Roads and water
            // still read through it, in the greens and teals the rest of the app uses.
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = TILE_WASH_ALPHA)))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawPlainGround(camera, biome, colors.road, colors.water)
            }
        }
    }
}

/**
 * The drawn stand in. Streets are laid out on a fixed grid in real world metres, so they
 * slide, turn and scale with the camera exactly as real tiles would, and walking still
 * feels like walking.
 */
private fun DrawScope.drawPlainGround(camera: MapCamera, biome: Biome, road: Color, water: Color) {
    val spacingMeters = BLOCK_METERS
    val spacingPixels = MapProjection.metersToPixels(camera, spacingMeters)
    // Below a few pixels a grid is just noise, so it is dropped rather than drawn.
    if (spacingPixels < MIN_BLOCK_PIXELS) return

    val centre = Offset(size.width / 2f, size.height / 2f)
    // The grid is pinned to the world rather than to the screen, so it slides under the
    // player as they walk instead of travelling along with them.
    val eastMeters = Math.toRadians(camera.centerLng) * EARTH_RADIUS_METERS *
        kotlin.math.cos(Math.toRadians(camera.centerLat))
    val northMeters = Math.toRadians(camera.centerLat) * EARTH_RADIUS_METERS
    val phaseX = (eastMeters.mod(spacingMeters) / spacingMeters).toFloat()
    val phaseY = (northMeters.mod(spacingMeters) / spacingMeters).toFloat()

    val thickness = (spacingPixels * ROAD_WIDTH_SHARE).coerceIn(1.5f, 18f)
    val reach = (kotlin.math.hypot(size.width, size.height) / spacingPixels).toInt() + 2

    rotate(-camera.bearingDegrees, centre) {
        for (step in -reach..reach) {
            val x = centre.x + (step - phaseX) * spacingPixels
            drawLine(road, Offset(x, -size.height), Offset(x, size.height * 2), thickness, StrokeCap.Butt)
        }
        for (step in -reach..reach) {
            val y = centre.y + (step + phaseY) * spacingPixels
            drawLine(road, Offset(-size.width, y), Offset(size.width * 2, y), thickness, StrokeCap.Butt)
        }
        if (biome == Biome.WATER) {
            // A stretch of water so a water biome is readable even without real tiles.
            drawOval(
                color = water,
                topLeft = Offset(centre.x + spacingPixels * 0.6f, centre.y + spacingPixels * 0.9f),
                size = Size(spacingPixels * 3.2f, spacingPixels * 1.6f),
            )
        }
    }
}

/** Roughly a city block, so the drawn grid sits at a believable real world scale. */
private const val BLOCK_METERS = 90.0
private const val MIN_BLOCK_PIXELS = 14f
private const val ROAD_WIDTH_SHARE = 0.08f
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val TILE_WASH_ALPHA = 0.55f
