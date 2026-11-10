package net.sentinelsafeguard.academyapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import net.sentinelsafeguard.academyapp.ui.AppRoot
import net.sentinelsafeguard.academyapp.ui.theme.SentinelTheme

// The single activity. Phase 1 hosts the branded start screen
// only; phase 2 grows the real navigation inside AppRoot.
//
// FLAG_SECURE is set from the first frame, as in the family's
// other apps: when lessons, progress, and certificates arrive,
// screenshots and the recents preview must never expose a
// student's record. The window is protected before there is
// anything in it worth protecting, so the habit ships with the
// scaffold rather than being retrofitted.
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            SentinelTheme {
                AppRoot()
            }
        }
    }
}
