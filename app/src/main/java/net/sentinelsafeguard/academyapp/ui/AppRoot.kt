package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted

// ---------------------------------------------------------------------------
// AppRoot — the whole app, phase 1: a branded start screen.
//
// What is on it is all real: the Academy mark (the app's own
// owner-approved icon artwork), the app's name, the real Sentinel
// Safeguard logo, an honest status line, and the version. What is
// NOT on it matters just as much: no sign-in button (there is no
// account session to open yet), no course cards (there is no
// catalog behind them yet), no streak ring (no data). A control
// with nothing real behind it does not ship in this family —
// phase 2 replaces this screen with the real Academy, section by
// section, against docs/API-CONTRACT.md.
// ---------------------------------------------------------------------------
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // The Academy product mark (icon artwork; the mark is the
        // app icon only — company surfaces below use the real logo).
        Image(
            painter = painterResource(R.drawable.academy_mark),
            contentDescription = stringResource(R.string.academy_mark_description),
            modifier = Modifier.size(168.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.start_status),
            style = MaterialTheme.typography.bodyMedium,
            color = SentinelMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        // The real Sentinel Safeguard logo — the company surface.
        Image(
            painter = painterResource(R.drawable.sentinel_logo),
            contentDescription = stringResource(R.string.logo_description),
            modifier = Modifier.width(168.dp),
        )
        Spacer(Modifier.height(14.dp))
        if (versionName.isNotBlank()) {
            Text(
                text = stringResource(R.string.start_version, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = SentinelMuted,
            )
        }
    }
}
