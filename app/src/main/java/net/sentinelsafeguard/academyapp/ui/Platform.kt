package net.sentinelsafeguard.academyapp.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import net.sentinelsafeguard.academyapp.BuildConfig
import net.sentinelsafeguard.academyapp.data.SecureStore
import androidx.activity.compose.BackHandler as AndroidBackHandler
import androidx.compose.ui.res.painterResource as AndroidPainterResource
import androidx.compose.ui.res.stringResource as AndroidStringResource

// ---------------------------------------------------------------------------
// Platform.kt — ANDROID implementations of the desktop-port seams
// (2026-11-13). Every function here has an identical signature in
// the desktop module's Platform.kt; the shared UI sources call
// only these. Android behavior is exactly what the pre-port code
// did inline — these are moves, not changes.
// ---------------------------------------------------------------------------

@Composable
fun stringResource(id: Int): String = AndroidStringResource(id)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String =
    AndroidStringResource(id, *formatArgs)

@Composable
fun painterResource(id: Int): Painter = AndroidPainterResource(id)

/** True when the system asks for no animation (animator scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val scale = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    return reducedMotion(scale)
}

@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    AndroidBackHandler(enabled = enabled, onBack = onBack)
}

/** Lifecycle law: ON_STOP during a live exam paper increments the
 *  honest backgrounding count; return from background with the
 *  lock armed re-locks. */
@Composable
fun PlatformLifecycleEffects(vm: AcademyViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stopped = true
                    vm.noteBackgrounded()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (stopped) {
                        stopped = false
                        vm.relockIfArmed()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Retained holder so the plain AcademyViewModel survives
 *  configuration changes exactly as the old AndroidViewModel did. */
class AcademyViewModelHolder(app: Application) : AndroidViewModel(app) {
    val vm: AcademyViewModel = AcademyViewModel(
        store = SecureStore(app),
        catalogCacheDir = File(app.filesDir, "catalog_cache"),
        userAgent = "SentinelAcademyApp/" + BuildConfig.VERSION_NAME + " (Android)",
    )

    override fun onCleared() {
        vm.close()
    }
}

@Composable
fun rememberAcademyViewModel(): AcademyViewModel {
    val holder: AcademyViewModelHolder = viewModel()
    return holder.vm
}

@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
}

@Composable
fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            Unit
        }
    }
}

/** Android has no extra platform settings section: reduced motion
 *  is the system's animator scale (above) and the credential store
 *  is the Android Keystore — both already stated in the app's own
 *  copy. The desktop edition renders its own section here. */
@Composable
fun PlatformExtraSettings() {
}
