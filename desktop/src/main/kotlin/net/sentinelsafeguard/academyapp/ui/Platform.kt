package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.DesktopStore
import net.sentinelsafeguard.academyapp.desktop.DesktopStrings
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainerHigh
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted
import net.sentinelsafeguard.academyapp.ui.theme.SentinelOnGreen
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap

// ---------------------------------------------------------------------------
// Platform.kt — DESKTOP implementations of the port seams.
// Signatures are identical to the Android Platform.kt; behavior
// notes live on each function where desktop genuinely differs.
// ---------------------------------------------------------------------------

/** The desktop store instance backing desktop preferences (the
 *  credential store itself is created with the view model; both
 *  instances address the same OS/file backend). */
private val prefsStore: DesktopStore by lazy { DesktopStore() }

private object MotionPrefs {
    var enabled: Boolean by mutableStateOf(prefsStore.pref("reduce_motion") == "1")
        private set

    fun set(value: Boolean) {
        enabled = value
        prefsStore.setPref("reduce_motion", if (value) "1" else "0")
    }
}

private fun stringsFor(locale: Locale): Map<Int, String> =
    if (locale.language == "es") DesktopStrings.ES else DesktopStrings.EN

@Composable
fun stringResource(id: Int): String =
    stringsFor(Locale.getDefault())[id] ?: DesktopStrings.EN[id] ?: ""

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    val template = stringResource(id)
    return runCatching { String.format(Locale.getDefault(), template, *formatArgs) }
        .getOrDefault(template)
}

@Composable
fun painterResource(id: Int): Painter {
    val path = when (id) {
        R.drawable.academy_mark -> "images/academy_mark.png"
        R.drawable.sentinel_logo -> "images/sentinel_logo.jpg"
        else -> "images/academy_mark.png"
    }
    return remember(path) {
        val bytes = requireNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(path),
        ) { "missing desktop resource $path" }.readAllBytes()
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }
}

/** Desktop reduced motion is the app's own setting (Settings →
 *  Reduce motion, persisted in the desktop store); there is no
 *  desktop animator-scale equivalent to read. */
@Composable
fun rememberReducedMotion(): Boolean = MotionPrefs.enabled

/** Desktop has no system back gesture; detail screens carry
 *  their own Back controls (the shared Lesson/Exam surfaces do),
 *  and window close is the window's own chrome. Honest no-op. */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

/** Window-focus twin of the Android lifecycle law: losing window
 *  focus during a live paper counts as a backgrounding (the
 *  score call's honest `blurs`); regaining focus with the lock
 *  armed re-locks. */
@Composable
fun PlatformLifecycleEffects(vm: AcademyViewModel) {
    val focused = LocalWindowInfo.current.isWindowFocused
    var wasFocused by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(focused) {
        val prev = wasFocused
        wasFocused = focused
        if (prev == null) return@LaunchedEffect
        if (!focused) {
            vm.noteBackgrounded()
        } else {
            vm.relockIfArmed()
        }
    }
}

@Composable
fun rememberAcademyViewModel(): AcademyViewModel {
    val vm = remember {
        AcademyViewModel(
            store = DesktopStore(),
            catalogCacheDir = File(DesktopStore.configDir(), "catalog_cache"),
            userAgent = "SentinelAcademyApp/1.0.0 (Desktop)",
        )
    }
    DisposableEffect(vm) {
        onDispose { vm.close() }
    }
    return vm
}

@Composable
fun appVersionName(): String = "1.0.0"

@Composable
fun rememberUrlOpener(): (String) -> Unit = remember {
    { url: String ->
        runCatching {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            } else {
                ProcessBuilder("xdg-open", url).start()
            }
        }
        Unit
    }
}

/** Desktop-only settings section: the Reduce motion switch (the
 *  setting rememberReducedMotion honors) and the honest
 *  credential-storage label for THIS machine — OS key storage
 *  when it is actually in use, the labeled file fallback when
 *  it is not. */
@Composable
fun PlatformExtraSettings() {
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.settings_reduce_motion),
        style = MaterialTheme.typography.labelMedium,
        color = SentinelMuted,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                stringResource(R.string.settings_reduce_motion),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.settings_reduce_motion_body),
                style = MaterialTheme.typography.bodySmall,
                color = SentinelMuted,
            )
        }
        Switch(
            checked = MotionPrefs.enabled,
            onCheckedChange = { MotionPrefs.set(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SentinelOnGreen,
                checkedTrackColor = SentinelGreen,
                checkedBorderColor = SentinelGreen,
                uncheckedThumbColor = SentinelMuted,
                uncheckedTrackColor = SentinelContainer,
                uncheckedBorderColor = SentinelContainerHigh,
            ),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(
            if (DesktopStore.osKeyStorageActive) {
                R.string.about_storage_os
            } else {
                R.string.about_storage_fallback
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = SentinelMuted,
    )
}
