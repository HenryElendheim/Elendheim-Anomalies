package com.elendheim.anomalies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.elendheim.anomalies.game.Geo
import com.elendheim.anomalies.ui.map.MapCamera
import com.elendheim.anomalies.ui.map.MapProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The maths the whole map rests on, checked without a screen or a renderer. */
class MapProjectionTest {

    private val canvas = Size(1080f, 1920f)
    private val lat = 59.9139
    private val lng = 10.7522

    private fun camera(zoom: Float = 17f, bearing: Float = 0f) =
        MapCamera(centerLat = lat, centerLng = lng, zoom = zoom, bearingDegrees = bearing)

    @Test
    fun `the camera centre lands in the middle of the screen`() {
        val point = MapProjection.toScreen(camera(), lat, lng, canvas)
        assertEquals(canvas.width / 2, point.x, 0.01f)
        assertEquals(canvas.height / 2, point.y, 0.01f)
    }

    @Test
    fun `screen and world are inverses of one another at every angle`() {
        for (bearing in listOf(0f, 37f, 90f, 180f, 285f)) {
            val cam = camera(bearing = bearing)
            val point = Offset(730f, 410f)
            val (worldLat, worldLng) = MapProjection.toWorld(cam, point, canvas)
            val back = MapProjection.toScreen(cam, worldLat, worldLng, canvas)
            assertEquals("x at bearing $bearing", point.x, back.x, 0.5f)
            assertEquals("y at bearing $bearing", point.y, back.y, 0.5f)
        }
    }

    @Test
    fun `north sits up the screen when the map is not turned`() {
        val (northLat, northLng) = Geo.offset(lat, lng, northMeters = 120.0, eastMeters = 0.0)
        val point = MapProjection.toScreen(camera(), northLat, northLng, canvas)
        assertEquals(canvas.width / 2, point.x, 1f)
        assertTrue("north should be above the centre", point.y < canvas.height / 2)
    }

    @Test
    fun `turning the map to ninety degrees puts east up the screen`() {
        val (eastLat, eastLng) = Geo.offset(lat, lng, northMeters = 0.0, eastMeters = 120.0)
        val point = MapProjection.toScreen(camera(bearing = 90f), eastLat, eastLng, canvas)
        assertEquals(canvas.width / 2, point.x, 1f)
        assertTrue("east should be above the centre once turned", point.y < canvas.height / 2)
    }

    @Test
    fun `zooming in one step halves the ground each pixel covers`() {
        val wide = MapProjection.metersPerPixel(lat, 16f)
        val close = MapProjection.metersPerPixel(lat, 17f)
        assertEquals(wide / 2, close, wide * 0.0001)
    }

    @Test
    fun `a real distance takes up more pixels the further you zoom in`() {
        val near = MapProjection.metersToPixels(camera(zoom = 18f), 80.0)
        val far = MapProjection.metersToPixels(camera(zoom = 15f), 80.0)
        assertTrue(near > far)
    }

    @Test
    fun `zoom and metres per pixel convert back to each other`() {
        val zoom = MapProjection.zoomForMetersPerPixel(lat, MapProjection.metersPerPixel(lat, 16.4f))
        assertEquals(16.4f, zoom, 0.001f)
    }

    @Test
    fun `zoom is held inside the range the map can actually draw`() {
        assertEquals(MapCamera.MAX_ZOOM, camera().withZoom(99f).zoom, 0.001f)
        assertEquals(MapCamera.MIN_ZOOM, camera().withZoom(-4f).zoom, 0.001f)
    }

    @Test
    fun `bearing always comes back as a positive angle under a full turn`() {
        assertEquals(350f, camera().withBearing(-10f).bearingDegrees, 0.001f)
        assertEquals(20f, camera().withBearing(380f).bearingDegrees, 0.001f)
        assertEquals(0f, camera().withBearing(720f).bearingDegrees, 0.001f)
    }

    @Test
    fun `markers grow with zoom but stay within sane bounds`() {
        val small = MapProjection.markerScale(MapCamera.MIN_ZOOM)
        val large = MapProjection.markerScale(MapCamera.MAX_ZOOM)
        assertTrue(large > small)
        assertTrue("markers should never vanish", small > 0.3f)
        assertTrue("markers should never swallow the screen", large < 2f)
    }

    @Test
    fun `a point a known distance away lands the right number of pixels out`() {
        val cam = camera(zoom = 17f)
        val (farLat, farLng) = Geo.offset(lat, lng, northMeters = 0.0, eastMeters = 100.0)
        val point = MapProjection.toScreen(cam, farLat, farLng, canvas)
        val expected = MapProjection.metersToPixels(cam, 100.0)
        assertEquals(expected, point.x - canvas.width / 2, 1f)
    }
}
