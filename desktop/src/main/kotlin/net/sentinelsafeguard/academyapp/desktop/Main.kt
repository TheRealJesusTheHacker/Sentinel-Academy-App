package net.sentinelsafeguard.academyapp.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.sentinelsafeguard.academyapp.ui.AppRoot
import net.sentinelsafeguard.academyapp.ui.painterResource
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.ui.theme.SentinelTheme

// Sentinel Academy Desktop 1.0.0 — the Compose Multiplatform port
// of the Android app, against the same production Academy API,
// the same session-token model, and its own stable per-install
// sa-bid device id (see DesktopStore).
fun main() = application {
    val state = rememberWindowState(width = 1100.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Sentinel Academy",
        icon = painterResource(R.drawable.academy_mark),
    ) {
        SentinelTheme {
            AppRoot()
        }
    }
}
