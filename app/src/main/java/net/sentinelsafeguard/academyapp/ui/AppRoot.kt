package net.sentinelsafeguard.academyapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.Track
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted
import net.sentinelsafeguard.academyapp.ui.theme.SentinelSurfaceVariant

// ---------------------------------------------------------------------------
// AppRoot — the whole app, phase 2: the real Academy, section by
// section against the LIVE API (contract BUILD RECORD 2026-11-10).
//
// Phase machine: BOOT (stored session checked) -> LOCKED (PIN set
// + session stored) -> AUTH / PENDING_CONFIRM -> MAIN.
// MAIN = four tabs (Home, Tracks, Certificates, Settings) with
// detail surfaces (road, lesson, exam) above them.
//
// Lifecycle law wired here:
// - ON_STOP during a live exam paper increments the VM's honest
//   backgrounding count (the score call's `blurs`, contract §4.4).
// - Return from background with the lock armed re-locks: the PIN
//   protects the stored session on this device.
// ---------------------------------------------------------------------------

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    TRACKS(R.string.nav_tracks, Icons.Filled.School),
    CERTS(R.string.nav_certs, Icons.Filled.WorkspacePremium),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

private sealed interface Detail {
    data class Road(val trackSlug: String) : Detail
    data class Lesson(val track: String, val course: String, val lesson: String) : Detail
    data class Exam(val track: String, val kind: String, val course: String?, val mode: String?) : Detail
}

@Composable
fun AppRoot() {
    val vm: AcademyViewModel = viewModel()
    val reduced = rememberReducedMotion()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var detail by remember { mutableStateOf<Detail?>(null) }

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

    BackHandler(enabled = detail != null) {
        detail = when (val d = detail) {
            is Detail.Lesson -> Detail.Road(d.track)
            is Detail.Exam -> {
                vm.clearExam()
                Detail.Road(d.track)
            }
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (vm.phase) {
            AppPhase.BOOT -> LoadingBlock()
            AppPhase.LOCKED -> LockScreen(vm, reduced)
            AppPhase.AUTH -> AuthScreen(vm, reduced)
            AppPhase.PENDING_CONFIRM -> PendingConfirmScreen(vm)
            AppPhase.MAIN -> {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val d = detail) {
                            is Detail.Road -> TrackRoadScreen(
                                vm = vm,
                                trackSlug = d.trackSlug,
                                reduced = reduced,
                                onOpenLesson = { t, c, l ->
                                    vm.openLesson(t, c, l)
                                    detail = Detail.Lesson(t, c, l)
                                },
                                onStartQuiz = { t, c ->
                                    detail = Detail.Exam(t, "quiz", c, null)
                                },
                                onStartFinal = { t, mode ->
                                    detail = Detail.Exam(t, "final", null, mode)
                                },
                            )
                            is Detail.Lesson -> LessonScreen(
                                vm = vm,
                                reduced = reduced,
                                onNavigateLesson = { t, c, l ->
                                    vm.openLesson(t, c, l)
                                    detail = Detail.Lesson(t, c, l)
                                },
                                onBack = { detail = Detail.Road(d.track) },
                            )
                            is Detail.Exam -> ExamScreen(
                                vm = vm,
                                track = d.track,
                                kind = d.kind,
                                course = d.course,
                                mode = d.mode,
                                reduced = reduced,
                                onDone = { detail = Detail.Road(d.track) },
                                onGoCerts = {
                                    detail = null
                                    tab = Tab.CERTS
                                },
                            )
                            null -> when (tab) {
                                Tab.HOME -> HomeScreen(vm, reduced) { t: Track ->
                                    detail = Detail.Road(t.slug)
                                }
                                Tab.TRACKS -> TracksScreen(vm, reduced) { t: Track ->
                                    detail = Detail.Road(t.slug)
                                }
                                Tab.CERTS -> CertificatesScreen(vm, reduced)
                                Tab.SETTINGS -> SettingsScreen(vm, reduced)
                            }
                        }
                    }
                    if (detail == null) {
                        BottomBar(tab) { tab = it }
                    } else if (detail is Detail.Road) {
                        BottomBar(tab) {
                            tab = it
                            detail = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SentinelSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) SentinelContainer else SentinelSurfaceVariant)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = t.icon,
                    contentDescription = stringResource(t.labelRes),
                    tint = if (selected) SentinelGreen else SentinelMuted,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(t.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) SentinelGreen else SentinelMuted,
                )
            }
        }
    }
}
