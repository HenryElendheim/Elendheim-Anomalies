package com.elendheim.anomalies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elendheim.anomalies.ui.SplashGate
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.nav.AppRoot
import com.elendheim.anomalies.ui.theme.ElendheimTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.Factory)
            val state by gameViewModel.state.collectAsStateWithLifecycle()

            // The theme reads the accessibility settings, so a change on the settings
            // screen reaches every other screen immediately. The window already paints
            // the darkest background, which means nothing flashes white before this runs.
            ElendheimTheme(
                highContrast = state.settings.highContrast,
                fontScale = state.settings.fontScale,
                reduceMotion = state.settings.reduceMotion,
            ) {
                SplashGate {
                    AppRoot(gameViewModel)
                }
            }
        }
    }
}
