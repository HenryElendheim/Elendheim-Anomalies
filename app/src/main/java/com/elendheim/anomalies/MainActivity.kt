package com.elendheim.anomalies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elendheim.anomalies.ui.SplashGate
import com.elendheim.anomalies.ui.nav.AppRoot
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.ElendheimTheme
import com.elendheim.anomalies.ui.theme.theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.Factory)
            val state by gameViewModel.state.collectAsStateWithLifecycle()

            // The theme reads the accessibility settings, so a change on the settings
            // screen reaches every other screen immediately.
            ElendheimTheme(
                highContrast = state.settings.highContrast,
                fontScale = state.settings.fontScale,
                reduceMotion = state.settings.reduceMotion,
            ) {
                Box(Modifier.fillMaxSize().background(theme.backgroundDeep)) {
                    SplashGate {
                        AppRoot(gameViewModel)
                    }
                }
            }
        }
    }
}
