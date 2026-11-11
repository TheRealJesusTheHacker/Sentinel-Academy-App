package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.durationParts
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainerHigh
import net.sentinelsafeguard.academyapp.ui.theme.SentinelDanger
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted
import net.sentinelsafeguard.academyapp.ui.theme.SentinelOnGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelSurfaceVariant

// ---------------------------------------------------------------------------
// Shared surfaces — the Emerald Depth building blocks every screen
// composes from, so depth/layering reads consistently: base ->
// surface -> raised card -> tonal container -> emerald signal.
// Chrome carries the bright emerald; content rides the text tones
// (theme law, Theme.kt).
// ---------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = SentinelMuted,
        modifier = modifier,
    )
}

@Composable
fun SentinelCard(
    modifier: Modifier = Modifier,
    container: Color = SentinelSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(16.dp),
        content = content,
    )
}

/** The primary action: filled emerald, near-black text, press
 *  sink (M-01). Busy swaps the label for a spinner in place —
 *  the control becomes its own status, like the family's morphs. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    reduced: Boolean = false,
) {
    val press = rememberPressable(reduced)
    val active = enabled && !busy
    Box(
        modifier = modifier
            .scale(press.scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) SentinelGreen else SentinelContainer)
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = active,
                onClick = onClick,
            )
            .padding(vertical = 13.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SentinelGreen,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) SentinelOnGreen else SentinelMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduced: Boolean = false,
) {
    val press = rememberPressable(reduced)
    Box(
        modifier = modifier
            .scale(press.scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SentinelContainerHigh, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 11.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else SentinelMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = SentinelDanger,
        modifier = modifier,
    )
}

/** One selectable answer choice. Selected = tonal container +
 *  emerald edge + emerald index chip; correct/incorrect states
 *  after server grading are layered by the call sites. */
@Composable
fun ChoiceRow(
    index: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null,
    reduced: Boolean = false,
) {
    val press = rememberPressable(reduced)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(press.scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SentinelContainer else SentinelSurfaceVariant)
            .border(
                1.dp,
                accent ?: if (selected) SentinelGreen else SentinelContainer,
                RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) SentinelGreen else SentinelContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ('A' + index).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) SentinelOnGreen else SentinelMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Thin progress line — chrome-quiet: dim emerald fill on a tonal
 *  track. The percentage is the data; the bar never shouts. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SentinelContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SentinelGreen),
        )
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SentinelGreen, strokeWidth = 2.dp)
    }
}

/** Humanized duration for cooldowns (audit 2026-11-11): the
 *  two largest units from durationParts, with localized unit
 *  labels — "7d 0h", "5h 12min", "12min", "45s". Replaces the
 *  raw m:ss clock on cooldown surfaces, where it misread badly
 *  past an hour (a 7-day invalidation cooldown showed
 *  "10080:00"). Exam countdowns keep formatClock — minutes and
 *  seconds are the honest units there. */
@Composable
fun durationText(totalSeconds: Int): String {
    val parts = durationParts(totalSeconds)
    val d = parts[0]; val h = parts[1]; val m = parts[2]; val s = parts[3]
    val day = stringResource(R.string.unit_day)
    val hour = stringResource(R.string.unit_hour)
    val min = stringResource(R.string.unit_min)
    val sec = stringResource(R.string.unit_sec)
    return when {
        d > 0 -> "$d$day $h$hour"
        h > 0 -> "$h$hour $m$min"
        m > 0 -> "$m$min"
        else -> "$s$sec"
    }
}
