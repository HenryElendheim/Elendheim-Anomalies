package com.elendheim.anomalies.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elendheim.anomalies.ui.common.Glyph
import com.elendheim.anomalies.ui.common.GlyphIcon
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.screens.CatchScreen
import com.elendheim.anomalies.ui.screens.CodexScreen
import com.elendheim.anomalies.ui.screens.ItemsScreen
import com.elendheim.anomalies.ui.screens.MapScreen
import com.elendheim.anomalies.ui.screens.ProfileScreen
import com.elendheim.anomalies.ui.screens.SpinOverlay
import com.elendheim.anomalies.ui.screens.SettingsScreen
import com.elendheim.anomalies.ui.screens.StopsScreen
import com.elendheim.anomalies.ui.theme.theme
import kotlinx.coroutines.delay

/** The five tabs along the bottom, in the order they appear. */
enum class Destination(val route: String, val label: String, val glyph: Glyph) {
    MAP("map", "Map", Glyph.MAP),
    CODEX("codex", "Codex", Glyph.CODEX),
    STOPS("stops", "Stops", Glyph.STOP),
    ITEMS("items", "Items", Glyph.ITEMS),
    PROFILE("profile", "Profile", Glyph.PROFILE),
}

const val SETTINGS_ROUTE = "settings"

@Composable
fun AppRoot(viewModel: GameViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.catchSession.collectAsStateWithLifecycle()
    val spin by viewModel.spinSession.collectAsStateWithLifecycle()
    val draft by viewModel.stopDraft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.MAP.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Destination.MAP.route) { MapScreen(viewModel) }
                    composable(Destination.CODEX.route) { CodexScreen(viewModel) }
                    composable(Destination.STOPS.route) {
                        StopsScreen(viewModel) {
                            // Placing a stop happens on the map, so the plus button opens
                            // the draft and then sends you there to choose the spot.
                            viewModel.beginPlacement()
                            navController.switchTo(Destination.MAP)
                        }
                    }
                    composable(Destination.ITEMS.route) { ItemsScreen(viewModel) }
                    composable(Destination.PROFILE.route) {
                        ProfileScreen(viewModel, onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
                    }
                    composable(SETTINGS_ROUTE) {
                        SettingsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
            if (currentRoute != SETTINGS_ROUTE && draft == null) {
                BottomBar(
                    current = currentRoute,
                    largeTargets = state.settings.largeTouchTargets,
                    onSelect = { destination -> navController.switchTo(destination) },
                )
            }
        }

        // The catch takes over the whole screen rather than becoming a route, which keeps
        // the encounter out of the back stack and lets a flee close it cleanly.
        AnimatedVisibility(
            visible = session != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            session?.let { CatchScreen(viewModel, it) }
        }

        AnimatedVisibility(
            visible = spin != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            spin?.let { SpinOverlay(viewModel, it) }
        }

        MessageBanner(message) { viewModel.consumeMessage() }
    }
}

/** Tapping a tab always returns to the top of that tab rather than stacking copies. */
internal fun NavHostController.switchTo(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: String?, largeTargets: Boolean, onSelect: (Destination) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundDeep)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = if (largeTargets) 14.dp else 10.dp),
    ) {
        Destination.entries.forEach { destination ->
            val selected = current == destination.route
            val tint = if (selected) theme.accent else theme.textDim
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(destination) }
                    .padding(vertical = if (largeTargets) 8.dp else 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlyphIcon(destination.glyph, tint, size = if (largeTargets) 24.dp else 21.dp, contentDescription = destination.label)
                Spacer(Modifier.height(3.dp))
                Text(destination.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** A short line at the top of the screen for drops, warnings and backup results. */
@Composable
private fun MessageBanner(message: String?, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(Sizes.gutter),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Sizes.corner))
                    .background(theme.card)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textBright,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
