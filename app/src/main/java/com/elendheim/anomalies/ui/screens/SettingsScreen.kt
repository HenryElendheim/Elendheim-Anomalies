package com.elendheim.anomalies.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elendheim.anomalies.BuildConfig
import com.elendheim.anomalies.data.backup.BackupFiles
import com.elendheim.anomalies.data.prefs.SettingsStore
import com.elendheim.anomalies.ui.common.ActionRow
import com.elendheim.anomalies.ui.common.ElCard
import com.elendheim.anomalies.ui.common.Glyph
import com.elendheim.anomalies.ui.common.GlyphIcon
import com.elendheim.anomalies.ui.common.Pill
import com.elendheim.anomalies.ui.common.ScreenHeader
import com.elendheim.anomalies.ui.common.SectionLabel
import com.elendheim.anomalies.ui.common.Sizes
import com.elendheim.anomalies.ui.common.ToggleRow
import com.elendheim.anomalies.ui.game.GameViewModel
import com.elendheim.anomalies.ui.theme.theme

/**
 * Every comfort and accessibility setting, plus the two buttons that move a save in and
 * out of the app. The version sits at the bottom so it is always easy to check.
 */
@Composable
fun SettingsScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val large = settings.largeTouchTargets

    BackHandler { onBack() }

    // The file pickers hand back a location the user chose, so an export lands exactly
    // where it was asked to and nothing is written anywhere else.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME_TYPE)
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Settings",
            trailing = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.surface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.BACK, theme.textMid, 18.dp, contentDescription = "Back")
                }
            },
        )

        SectionLabel("Reading")

        Column(Modifier.padding(horizontal = Sizes.gutter)) {
            Text("Text size", style = MaterialTheme.typography.titleSmall, color = theme.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.gap)) {
                SettingsStore.FONT_SCALES.forEach { scale ->
                    Pill(
                        text = SettingsStore.fontScaleLabel(scale),
                        selected = settings.fontScale == scale,
                        onClick = { viewModel.setFontScale(scale) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Every screen follows this, including the numbers on the profile.",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
            )
        }

        Spacer(Modifier.height(6.dp))

        ToggleRow(
            title = "High contrast",
            description = "Deeper grounds and brighter text",
            checked = settings.highContrast,
            largeTargets = large,
            onCheckedChange = { viewModel.setHighContrast(it) },
        )

        SectionLabel("Motion and touch")

        ToggleRow(
            title = "Reduce motion",
            description = "Stops the splash fade, the idle bob and the shrinking ring. " +
                "The catch is then scored on the flick alone.",
            checked = settings.reduceMotion,
            largeTargets = large,
            onCheckedChange = { viewModel.setReduceMotion(it) },
        )
        ToggleRow(
            title = "Large touch targets",
            description = "Taller rows and a roomier bottom bar",
            checked = settings.largeTouchTargets,
            largeTargets = large,
            onCheckedChange = { viewModel.setLargeTouchTargets(it) },
        )
        ToggleRow(
            title = "Vibration",
            description = "A short buzz on a landed catch",
            checked = settings.hapticsEnabled,
            largeTargets = large,
            onCheckedChange = { viewModel.setHaptics(it) },
        )

        SectionLabel("Map")

        ToggleRow(
            title = "Show distances",
            description = "Metres and kilometres beside stops",
            checked = settings.showDistances,
            largeTargets = large,
            onCheckedChange = { viewModel.setShowDistances(it) },
        )

        SectionLabel("Your save")

        ActionRow(
            title = "Export to a file",
            description = "Writes the whole collection as readable JSON wherever you choose",
            largeTargets = large,
            onClick = { exportLauncher.launch(BackupFiles.suggestedFileName()) },
        )
        ActionRow(
            title = "Import from a file",
            description = "Replaces what is here with the contents of a backup",
            largeTargets = large,
            onClick = { importLauncher.launch(arrayOf(BackupFiles.MIME_TYPE, "text/plain", "*/*")) },
        )

        Column(Modifier.padding(Sizes.gutter)) {
            ElCard(borderColor = theme.borderDim) {
                Text(
                    "Everything lives on this device and nothing is sent anywhere. " +
                        "An export is the only copy that exists, so it is worth making one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
        )
    }
}
