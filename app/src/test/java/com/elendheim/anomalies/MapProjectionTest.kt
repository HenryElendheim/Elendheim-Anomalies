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

    private val density = 2.75f

    private fun camera(zoom: Float = 16f, bearing: Float = 0f) =
        MapCamera(
            centerLat = lat,
            centerLng = lng,
            zoom = zoom,
            bearingDegrees = bearing,
            devicePixelRatio = density,
        )

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
        val wide = MapProjection.metersPerPixel(lat, 16f, density)
        val close = MapProjection.metersPerPixel(lat, 17f, density)
        assertEquals(wide / 2, close, wide * 0.0001)
    }

    @Test
    fun `a denser screen packs the same ground into more pixels`() {
        val plain = MapProjection.metersPerPixel(lat, 16f, 1f)
        val dense = MapProjection.metersPerPixel(lat, 16f, 3f)
        // Three times the pixels for the same piece of ground means a third of the
        // ground behind each one. Getting this wrong is what makes the map slide out
        // from under the markers.
        assertEquals(plain / 3, dense, plain * 0.0001)
    }

    @Test
    fun `the scale matches the tile renderer's own reckoning`() {
        // The renderer measures a zoom step against a tile five hundred and twelve
        // pixels wide, so at zoom zero on a plain screen the whole equator spans that.
        val atZoomZero = MapProjection.metersPerPixel(0.0, 0f, 1f)
        assertEquals(40_075_016.686 / 512.0, atZoomZero, 0.001)
    }

    @Test
    fun `the opening zoom puts the spawn ring at a workable size`() {
        val canvas = Size(1080f, 2100f)
        val zoom = MapProjection.zoomFittingRadius(lat, 80.0, canvas, density)
        val fitted = camera(zoom = zoom)
        val ringPixels = MapProjection.metersToPixels(fitted, 80.0)
        val share = ringPixels / minOf(canvas.width, canvas.height)
        assertTrue("the ring should take up a useful share, was $share", share in 0.2f..0.45f)
    }

    @Test
    fun `dragging moves the ground the same way as the finger`() {
        val start = camera()
        val dragged = MapProjection.panned(start, Offset(120f, 0f))
        val point = MapProjection.toScreen(dragged, start.centerLat, start.centerLng, canvas)
        // The old centre should now sit a hundred and twenty pixels to the right, which
        // is the ground having followed the finger rather than run away from it.
        assertEquals(canvas.width / 2 + 120f, point.x, 1f)
        assertEquals(canvas.height / 2, point.y, 1f)
    }

    @Test
    fun `dragging a turned map still follows the screen rather than north`() {
        val start = camera(bearing = 90f)
        val dragged = MapProjection.panned(start, Offset(0f, 90f))
        val point = MapProjection.toScreen(dragged, start.centerLat, start.centerLng, canvas)
        assertEquals(canvas.width / 2, point.x, 1f)
        assertEquals(canvas.height / 2 + 90f, point.y, 1f)
    }

    @Test
    fun `a pinch converts into whole zoom steps`() {
        assertEquals(1f, MapProjection.zoomSteps(2f), 0.0001f)
        assertEquals(-1f, MapProjection.zoomSteps(0.5f), 0.0001f)
        assertEquals(0f, MapProjection.zoomSteps(1f), 0.0001f)
    }

    @Test
    fun `a real distance takes up more pixels the further you zoom in`() {
        val near = MapProjection.metersToPixels(camera(zoom = 18f), 80.0)
        val far = MapProjection.metersToPixels(camera(zoom = 14f), 80.0)
        assertTrue(near > far)
    }

    @Test
    fun `zoom and metres per pixel convert back to each other`() {
        val zoom = MapProjection.zoomForMetersPerPixel(
            lat,
            MapProjection.metersPerPixel(lat, 16.4f, density),
            density,
        )
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
