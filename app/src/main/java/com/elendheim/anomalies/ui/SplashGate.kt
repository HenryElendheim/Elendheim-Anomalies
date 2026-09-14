package com.elendheim.anomalies.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.elendheim.anomalies.R
import com.elendheim.anomalies.ui.theme.LocalFontScale
import com.elendheim.anomalies.ui.theme.LocalReduceMotion
import com.elendheim.anomalies.ui.theme.theme
import kotlinx.coroutines.delay

/**
 * Holds one word on screen for a second and then fades away to reveal the app underneath.
 * Nothing but the word, by design.
 */
@Composable
fun SplashGate(content: @Composable () -> Unit) {
    var showing by remember { mutableStateOf(true) }
    val reduceMotion = LocalReduceMotion.current
    val fadeMillis = if (reduceMotion) 0 else FADE_MILLIS

    val alpha by animateFloatAsState(
        targetValue = if (showing) 1f else 0f,
        animationSpec = tween(durationMillis = fadeMillis),
        label = "splashFade",
    )

    LaunchedEffect(Unit) {
        delay(HOLD_MILLIS)
        showing = false
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .alpha(alpha)
                    .background(
                        Brush.linearGradient(
                            listOf(theme.backgroundDeep, theme.background, theme.surface)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.splash_word),
                    color = theme.text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (30 * LocalFontScale.current).sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (4 * LocalFontScale.current).sp,
                    ),
                )
            }
        }
    }
}

private const val HOLD_MILLIS = 1000L
private const val FADE_MILLIS = 420
