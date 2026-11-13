package net.sentinelsafeguard.academyapp.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.sentinelsafeguard.academyapp.R
import kotlinx.coroutines.delay
import net.sentinelsafeguard.academyapp.data.Course
import net.sentinelsafeguard.academyapp.data.Track
import net.sentinelsafeguard.academyapp.data.courseEnterable
import net.sentinelsafeguard.academyapp.data.finalUnlocked
import net.sentinelsafeguard.academyapp.data.trackProgress
import net.sentinelsafeguard.academyapp.data.weeklyGoalFraction
import net.sentinelsafeguard.academyapp.ui.theme.SentinelAmber
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainerHigh
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted

// ---------------------------------------------------------------------------
// Home (streak + my tracks), the catalog (all tracks), and the
// road (one track, in the server's order: core courses, then the
// final, then electives — contract §4.2).
//
// The road's open/complete/locked states come from the SERVER's
// overlay on the catalog payload (computed with the same
// functions the web road uses). The app reads state; it never
// re-derives it. Quiz/final taps go to the paper protocol, whose
// gates the server enforces again at paper time.
// ---------------------------------------------------------------------------

@Composable
fun StreakCard(vm: AcademyViewModel) {
    val s = vm.streaks ?: return
    SentinelCard(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_streak_title))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.streak_current, s.current),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.streak_longest, s.longest),
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressLine(weeklyGoalFraction(s.lessonsThisWeek, s.weeklyGoal))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.streak_this_week, s.lessonsThisWeek, s.weeklyGoal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.streak_goal_guide, s.weeklyGoal),
            style = MaterialTheme.typography.bodySmall,
            color = SentinelMuted,
        )
    }
}

@Composable
fun TrackCard(
    track: Track,
    vm: AcademyViewModel,
    reduced: Boolean,
    entrance: ListEntrance,
    index: Int,
    onOpenRoad: (Track) -> Unit,
) {
    val totalLessons = track.courses.sumOf { it.lessonCount }
    val doneLessons = track.courses.sumOf { c -> c.modules.sumOf { m -> m.lessons.count { it.done == true } } }
    val enrolled = track.enrolled == true || (vm.me?.enrolled?.contains(track.slug) == true)
    SentinelCard(
        modifier = Modifier
            .fillMaxWidth()
            .listEntrance(entrance, index),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.kicker.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = SentinelGreen,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SentinelContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // The chip renders the payload's access value
                // (audit 2026-11-11): "free" today on every
                // track, localized; any future value displays
                // as the server states it instead of a hardcoded
                // promise the payload didn't make.
                Text(
                    text = if (track.access == "free") {
                        stringResource(R.string.track_free)
                    } else {
                        track.access.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = SentinelMuted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (track.pitch.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.pitch,
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.track_courses, track.courses.size) + " · " +
                stringResource(R.string.track_lessons, totalLessons) +
                (track.final?.let {
                    " · " + stringResource(R.string.track_final_info, it.draw, it.minutes, it.passPct)
                } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = SentinelMuted,
        )
        if (enrolled) {
            Spacer(Modifier.height(10.dp))
            ProgressLine(trackProgress(doneLessons, totalLessons))
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.track_progress,
                    if (totalLessons == 0) 0 else (100 * doneLessons) / totalLessons,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = SentinelMuted,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.track_view_road),
                reduced = reduced,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenRoad(track) },
            )
        } else if (vm.me != null) {
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = stringResource(R.string.track_enroll),
                reduced = reduced,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.enroll(track.slug) },
            )
        }
    }
}

@Composable
fun HomeScreen(
    vm: AcademyViewModel,
    reduced: Boolean,
    onOpenRoad: (Track) -> Unit,
) {
    val entrance = rememberListEntrance(active = vm.catalog != null, reduced = reduced)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_greeting, vm.me?.user?.displayName ?: ""),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))
            StreakCard(vm)
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.home_my_tracks))
            Spacer(Modifier.height(10.dp))
        }
        val mine = vm.catalog?.tracks?.filter {
            it.enrolled == true || vm.me?.enrolled?.contains(it.slug) == true
        } ?: emptyList()
        if (vm.catalog == null) {
            // Loading vs failed are different truths (audit
            // 2026-11-11): a failed catalog fetch must not render
            // as "not enrolled in a track yet", and must offer a
            // way back.
            item {
                if (vm.catalogFailed) {
                    ErrorLine(stringResource(R.string.error_network))
                    Spacer(Modifier.height(8.dp))
                    GhostButton(stringResource(R.string.common_retry), onClick = { vm.refreshAll() }, reduced = reduced)
                } else {
                    LoadingBlock()
                }
                Spacer(Modifier.height(8.dp))
            }
        } else if (mine.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_no_tracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelMuted,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(mine) { idx, track ->
            TrackCard(track, vm, reduced, entrance, idx, onOpenRoad)
            Spacer(Modifier.height(12.dp))
        }
        vm.homeError?.let { msg ->
            item {
                ErrorLine(if (msg == AcademyViewModel.NETWORK_ERROR) stringResource(R.string.error_network) else msg)
                Spacer(Modifier.height(8.dp))
                GhostButton(stringResource(R.string.common_retry), onClick = { vm.refreshAll() }, reduced = reduced)
            }
        }
    }
}

@Composable
fun TracksScreen(
    vm: AcademyViewModel,
    reduced: Boolean,
    onOpenRoad: (Track) -> Unit,
) {
    val entrance = rememberListEntrance(active = vm.catalog != null, reduced = reduced)
    val tracks = vm.catalog?.tracks ?: emptyList()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        itemsIndexed(tracks) { idx, track ->
            TrackCard(track, vm, reduced, entrance, idx, onOpenRoad)
            Spacer(Modifier.height(12.dp))
        }
        if (tracks.isEmpty()) {
            item {
                if (vm.catalogFailed) {
                    ErrorLine(stringResource(R.string.error_network))
                    Spacer(Modifier.height(8.dp))
                    GhostButton(stringResource(R.string.common_retry), onClick = { vm.refreshAll() }, reduced = reduced)
                } else {
                    LoadingBlock()
                }
            }
        }
    }
}

// ---------------- The road ----------------

@Composable
fun TrackRoadScreen(
    vm: AcademyViewModel,
    trackSlug: String,
    reduced: Boolean,
    onOpenLesson: (track: String, course: String, lesson: String) -> Unit,
    onStartQuiz: (track: String, course: String) -> Unit,
    onStartFinal: (track: String, mode: String) -> Unit,
) {
    val track = vm.catalog?.tracks?.firstOrNull { it.slug == trackSlug }
    LaunchedEffect(trackSlug) { vm.refreshCooldown(trackSlug) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        if (track == null) {
            item {
                if (vm.catalogFailed) {
                    ErrorLine(stringResource(R.string.error_network))
                    Spacer(Modifier.height(8.dp))
                    GhostButton(stringResource(R.string.common_retry), onClick = { vm.refreshAll() }, reduced = reduced)
                } else {
                    LoadingBlock()
                }
            }
            return@LazyColumn
        }
        item {
            Text(
                text = track.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.certName,
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.road_core))
            Spacer(Modifier.height(10.dp))
        }
        val core = track.courses.filter { !it.elective }
        val electives = track.courses.filter { it.elective }
        itemsIndexed(core) { _, course ->
            CourseRoadCard(vm, track, course, reduced, onOpenLesson, onStartQuiz)
            Spacer(Modifier.height(12.dp))
        }
        item {
            FinalRoadCard(vm, track, reduced, onStartFinal)
            Spacer(Modifier.height(16.dp))
        }
        if (electives.isNotEmpty()) {
            item {
                SectionLabel(stringResource(R.string.road_electives))
                Spacer(Modifier.height(10.dp))
            }
            itemsIndexed(electives) { _, course ->
                CourseRoadCard(vm, track, course, reduced, onOpenLesson, onStartQuiz)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CourseRoadCard(
    vm: AcademyViewModel,
    track: Track,
    course: Course,
    reduced: Boolean,
    onOpenLesson: (String, String, String) -> Unit,
    onStartQuiz: (String, String) -> Unit,
) {
    val enterable = courseEnterable(course.state)
    val quizBest = vm.me?.quizzes?.get("${track.slug}--${course.slug}")
    val allLessonsDone = course.modules.all { m -> m.lessons.all { it.done == true } } &&
        course.modules.any { it.lessons.isNotEmpty() }
    SentinelCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            StateChip(course.state)
        }
        if (course.tagline.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(course.tagline, style = MaterialTheme.typography.bodySmall, color = SentinelMuted)
        }
        Spacer(Modifier.height(10.dp))
        course.modules.forEach { module ->
            if (module.title.isNotBlank() && course.modules.size > 1) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelMuted,
                )
                Spacer(Modifier.height(4.dp))
            }
            module.lessons.forEach { lesson ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enterable) {
                            onOpenLesson(track.slug, course.slug, lesson.slug)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (lesson.done == true) SentinelGreen else SentinelContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (lesson.done == true) {
                            DrawnTick(modifier = Modifier.size(14.dp), playKey = 1, reduced = reduced)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enterable) MaterialTheme.colorScheme.onBackground else SentinelMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.common_minutes, lesson.minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = SentinelMuted,
                    )
                }
            }
        }
        // The course quiz step — after the lessons, as on the road.
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SentinelContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.road_quiz),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (quizBest?.passed == true) {
                    Text(
                        text = stringResource(R.string.road_quiz_passed, quizBest.pct),
                        style = MaterialTheme.typography.bodySmall,
                        color = SentinelGreen,
                    )
                } else if (!allLessonsDone) {
                    // Say WHY there is no button (audit 2026-11-11):
                    // a silently absent CTA reads as a dead control.
                    Text(
                        text = stringResource(R.string.road_quiz_locked_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = SentinelMuted,
                    )
                }
            }
            // The quiz step opens when the course's lessons are
            // done (it is then the frontier, or complete if passed
            // — retakes are allowed any time, contract §4.3). The
            // server re-checks at paper time regardless.
            if (enterable && allLessonsDone) {
                GhostButton(
                    text = stringResource(R.string.road_quiz_cta),
                    reduced = reduced,
                    onClick = { onStartQuiz(track.slug, course.slug) },
                )
            }
        }
    }
}

@Composable
private fun StateChip(state: String?) {
    val (label, color) = when (state) {
        "complete" -> stringResource(R.string.road_done) to SentinelGreen
        "locked" -> stringResource(R.string.road_locked) to SentinelAmber
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SentinelContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun PracticeFinalButton(
    track: Track,
    reduced: Boolean,
    onStartFinal: (String, String) -> Unit,
) {
    if (track.slug == "expert" || track.slug == "expert-es") {
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = stringResource(R.string.road_practice_cta),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onStartFinal(track.slug, "practice") },
        )
    }
}

@Composable
private fun FinalRoadCard(
    vm: AcademyViewModel,
    track: Track,
    reduced: Boolean,
    onStartFinal: (String, String) -> Unit,
) {
    val progress = vm.me?.tracks?.get(track.slug)
    val passed = progress?.finalPassed == true
    val enrolled = track.enrolled == true || vm.me?.enrolled?.contains(track.slug) == true
    val unlocked = finalUnlocked(track.courses.filter { !it.elective }.map { it.state })

    // Cooldown (audit 2026-11-11): /api/status gives a snapshot;
    // the card counts it down locally each second and re-asks
    // the server when the snapshot runs out — the server's clock
    // stays the authority, and long cooldowns read as durations
    // ("7d 0h"), never as a raw m:ss clock ("10080:00").
    var cooldownBase by remember(track.slug) { mutableStateOf(0) }
    var cooldownAtMs by remember(track.slug) { mutableStateOf(0L) }
    LaunchedEffect(vm.cooldownSeconds) {
        cooldownBase = vm.cooldownSeconds
        cooldownAtMs = System.currentTimeMillis()
    }
    var tick by remember(track.slug) { mutableStateOf(0) }
    LaunchedEffect(cooldownBase, cooldownAtMs) {
        if (cooldownBase > 0) {
            while ((System.currentTimeMillis() - cooldownAtMs) < cooldownBase * 1000L) {
                delay(1000)
                tick++
            }
            vm.refreshCooldown(track.slug)
        }
    }
    val cooldown = if (cooldownBase > 0) {
        // Reading tick subscribes this card to the 1s countdown.
        val elapsedSec = ((System.currentTimeMillis() - cooldownAtMs) / 1000).toInt()
        if (tick >= 0) (cooldownBase - elapsedSec).coerceAtLeast(0) else 0
    } else {
        0
    }

    SentinelCard(modifier = Modifier.fillMaxWidth(), container = SentinelContainer) {
        SectionLabel(stringResource(R.string.road_final))
        Spacer(Modifier.height(8.dp))
        track.final?.let {
            Text(
                text = stringResource(R.string.track_final_info, it.draw, it.minutes, it.passPct),
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
            Spacer(Modifier.height(10.dp))
        }
        // Scores the payloads already carry (audit 2026-11-11,
        // capability gap): /api/me reports the final best and
        // the practice best per track; show them where the
        // learner decides whether to retake.
        progress?.finalPct?.let {
            Text(
                text = stringResource(R.string.road_final_best, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
        }
        progress?.practicePct?.let {
            Text(
                text = stringResource(R.string.road_practice_best, it),
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
            Spacer(Modifier.height(4.dp))
        }
        when {
            passed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DrawnTick(modifier = Modifier.size(22.dp), playKey = 1, reduced = reduced)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.road_final_passed),
                        style = MaterialTheme.typography.titleMedium,
                        color = SentinelGreen,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.road_cert_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = SentinelMuted,
                )
            }
            cooldown > 0 -> {
                Text(
                    text = stringResource(R.string.road_cooldown, durationText(cooldown)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelAmber,
                )
                // The cooldown binds EXAM mode only — the
                // server's own refusal message says lessons,
                // quizzes and practice stay open, so the
                // practice route stays on the card too.
                if (enrolled) {
                    PracticeFinalButton(track, reduced, onStartFinal)
                }
            }
            !unlocked -> {
                // No live CTA into a guaranteed refusal (audit
                // 2026-11-11): the payload's own course states
                // say the final is locked, so the card says so.
                // Practice stays available on Expert — it is how
                // you prepare — and the server re-checks anyway.
                Text(
                    text = stringResource(R.string.road_final_locked_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelMuted,
                )
                if (enrolled) {
                    PracticeFinalButton(track, reduced, onStartFinal)
                }
            }
            enrolled -> {
                PrimaryButton(
                    text = stringResource(R.string.road_final_cta),
                    reduced = reduced,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onStartFinal(track.slug, "exam") },
                )
                PracticeFinalButton(track, reduced, onStartFinal)
            }
            else -> {
                Text(
                    text = stringResource(R.string.road_final_locked_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelMuted,
                )
            }
        }
    }
}
