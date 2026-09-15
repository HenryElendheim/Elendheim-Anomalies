package com.elendheim.anomalies.ui.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The real map underneath everything else.
 *
 * The renderer is deliberately kept dumb. It draws tiles and nothing else: its own
 * gestures are switched off and its camera is pushed from [MapCamera], so the camera the
 * rest of the app already uses stays the only source of truth. Every marker is still
 * drawn by the app's own projection on top, which means no overlay code has to know a
 * tile renderer exists at all, and the plain drawn map behaves identically.
 *
 * If any part of it throws, [onUnavailable] fires and the caller swaps back to the drawn
 * map. A plainer map is an acceptable outcome, a dead app is not.
 */
@Composable
fun MapLibreHost(
    camera: MapCamera,
    modifier: Modifier = Modifier,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder() }

    val mapView = remember {
        runCatching {
            MapLibre.getInstance(context)
            MapView(context).also { view ->
                view.onCreate(null)
                view.addOnDidFailLoadingMapListener {
                    // A style that will not load is worth one more try on the next one
                    // before giving up on the detailed map altogether.
                    val map = holder.map
                    if (map != null && holder.advanceStyle()) {
                        runCatching {
                            map.setStyle(Style.Builder().fromUri(holder.styleUrl())) { holder.push() }
                        }
                    } else {
                        onUnavailable()
                    }
                }
                view.getMapAsync { map ->
                    runCatching {
                        holder.map = map
                        map.uiSettings.apply {
                            // Gestures are handled by the overlay above, so the renderer
                            // must not react to them as well or the two would fight.
                            setAllGesturesEnabled(false)
                            isAttributionEnabled = false
                            isLogoEnabled = false
                            isCompassEnabled = false
                        }
                        map.setStyle(Style.Builder().fromUri(holder.styleUrl())) {
                            holder.push()
                        }
                        holder.apply(camera)
                    }.onFailure {
                        Log.w(TAG, "the detailed map could not be set up", it)
                        onUnavailable()
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "the detailed map could not start", it)
            onUnavailable()
            null
        }
    }

    if (mapView == null) return

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { holder.apply(camera) },
    )

    // The renderer holds native resources, so every lifecycle step has to reach it.
    DisposableEffect(lifecycleOwner, mapView) {
        // Composition can begin with the screen already resumed, in which case no start
        // event is ever delivered, so the current state is replayed by hand first.
        val state = lifecycleOwner.lifecycle.currentState
        runCatching {
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        }

        val observer = LifecycleEventObserver { _, event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Tearing down twice would crash, so the first teardown wins and the rest
            // are ignored.
            if (holder.claimTeardown()) {
                runCatching {
                    mapView.onPause()
                    mapView.onStop()
                    mapView.onDestroy()
                }
            }
        }
    }
}

/** Holds the live map and the style it is currently trying, away from composition. */
private class MapHolder {
    var map: MapLibreMap? = null
    private var styleIndex = 0
    private val tornDown = AtomicBoolean(false)

    fun styleUrl(): String = STYLE_URLS[styleIndex.coerceIn(0, STYLE_URLS.lastIndex)]

    /** True when there was another style left to try. */
    fun advanceStyle(): Boolean {
        if (styleIndex + 1 >= STYLE_URLS.size) return false
        styleIndex += 1
        return true
    }

    fun claimTeardown(): Boolean = tornDown.compareAndSet(false, true)

    /**
     * The camera the app wants, remembered so it can be re-applied whenever the renderer
     * reaches a point where it will actually accept one.
     */
    private var pending: MapCamera? = null

    fun apply(camera: MapCamera) {
        pending = camera
        push()
    }

    /**
     * Pushes the remembered camera onto the renderer. This goes through moveCamera
     * rather than assigning the camera position, because that is the call the renderer
     * actually acts on, and it moves rather than animates so the map tracks a gesture
     * exactly instead of lagging a step behind it.
     */
    fun push() {
        val target = map ?: return
        val camera = pending ?: return
        runCatching {
            target.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(camera.centerLat, camera.centerLng))
                        .zoom(camera.zoom.toDouble())
                        .bearing(camera.bearingDegrees.toDouble())
                        .tilt(0.0)
                        .build()
                )
            )
        }.onFailure { Log.w(TAG, "could not move the map camera", it) }
    }
}

/**
 * Styles are tried in order. The first is the quiet grey one that suits the dark wash
 * best, and the rest are there so a service change never leaves a blank screen.
 */
private val STYLE_URLS = listOf(
    "https://tiles.openfreemap.org/styles/positron",
    "https://tiles.openfreemap.org/styles/bright",
    "https://tiles.openfreemap.org/styles/liberty",
)

private const val TAG = "ElendheimMap"
