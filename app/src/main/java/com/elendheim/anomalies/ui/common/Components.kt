package com.elendheim.anomalies.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elendheim.anomalies.ui.theme.theme

/** The corner and spacing values every surface in the app is built from. */
object Sizes {
    val corner = 12.dp
    val cornerSmall = 10.dp
    val gutter = 14.dp
    val gap = 8.dp
    val hairline = 1.dp
    val rowHeight = 46.dp
    val rowHeightLarge = 60.dp
}

/** The bordered panel every list row, tile and detail card is made of. */
@Composable
fun ElCard(
    modifier: Modifier = Modifier,
    borderColor: Color = theme.border,
    background: Color = theme.card,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Sizes.corner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(Sizes.hairline, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** A screen title with an optional count line under it. Used by every top level page. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Sizes.gutter, end = Sizes.gutter, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = theme.text)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
        }
        trailing?.invoke()
    }
}

/** A small label above a group of settings or stats. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = theme.textDim,
        modifier = modifier.padding(start = Sizes.gutter, end = Sizes.gutter, top = 18.dp, bottom = 6.dp),
    )
}

/** The thin progress bar used for player level, companion experience and slot usage. */
@Composable
fun MeterBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = theme.accent,
    track: Color = theme.borderDim,
    height: Dp = 5.dp,
    label: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
            .then(
                if (label != null) Modifier.semantics { contentDescription = label } else Modifier
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

/** One number with its name under it, as used across the profile grid. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = theme.text) {
    ElCard(modifier = modifier, borderColor = theme.borderDim, contentPadding = 11.dp) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = theme.textDim)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
    }
}

/** The rounded chip used for capsule selection, filters and status words. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = theme.accent,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(theme.card)
            .border(Sizes.hairline, if (selected) accent else theme.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else theme.textDim,
        )
    }
}

/** A settings row carrying a switch, sized by the large touch targets preference. */
@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    largeTargets: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (largeTargets) Sizes.rowHeightLarge else Sizes.rowHeight)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Sizes.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = theme.text)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.backgroundDeep,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = theme.textDim,
                uncheckedTrackColor = theme.surface,
                uncheckedBorderColor = theme.border,
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** A row that opens something, sized by the same large touch targets preference. */
@Composable
fun ActionRow(
    title: String,
    description: String? = null,
    trailing: String? = null,
    largeTargets: Boolean = false,
    tint: Color = theme.text,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (largeTargets) Sizes.rowHeightLarge else Sizes.rowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Sizes.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = theme.textMid)
        }
    }
}

/** The full width call to action button, in one place so every screen matches. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Color = theme.accent,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Sizes.cornerSmall)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) tone else theme.surface)
            .border(Sizes.hairline, if (enabled) tone else theme.borderDim, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) theme.backgroundDeep else theme.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** The quieter button used beside the primary one. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = theme.textMid,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Sizes.cornerSmall)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(Sizes.hairline, tint, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = tint, textAlign = TextAlign.Center)
    }
}

/** Shown wherever a list has nothing in it yet, so a blank screen still explains itself. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Sizes.gutter * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = theme.textMid, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = theme.textDim, textAlign = TextAlign.Center)
    }
}

/** Text field colours, shared so every field in the app matches. */
@Composable
fun dialogFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = theme.surface,
    unfocusedContainerColor = theme.surface,
    focusedTextColor = theme.text,
    unfocusedTextColor = theme.text,
    focusedIndicatorColor = theme.accent,
    unfocusedIndicatorColor = theme.border,
    focusedLabelColor = theme.accent,
    unfocusedLabelColor = theme.textDim,
    cursorColor = theme.accent,
)

/** Slider colours, shared for the same reason. */
@Composable
fun dialogSliderColors() = SliderDefaults.colors(
    thumbColor = theme.accent,
    activeTrackColor = theme.accent,
    inactiveTrackColor = theme.borderDim,
)

/** A coloured dot used in legends and item rows. */
@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(color))
}
