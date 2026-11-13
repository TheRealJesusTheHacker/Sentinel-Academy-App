package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.Block
import net.sentinelsafeguard.academyapp.data.CheckResult
import net.sentinelsafeguard.academyapp.data.LessonPayload
import net.sentinelsafeguard.academyapp.ui.theme.SentinelAmber
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelDanger
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreenSoft
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted

// ---------------------------------------------------------------------------
// The lesson screen — native renderer for the contract's complete
// block vocabulary (exactly six types, §3.2): paragraph, heading,
// bullet list, numbered steps, callout (tip / key / warn — three
// visually distinct tones), and the hands-on exercise box. Text
// renders as TEXT, never as HTML (the server escapes for the same
// reason). Unknown block types render as nothing (Models.Block.
// Unknown) rather than being misrendered.
//
// The check and the practice questions are graded by the SERVER
// (/api/app/check): the payload the app holds contains no answer
// and no explanation, so there is nothing here to grade against —
// the explanation renders only when the server returns it after
// grading.
// ---------------------------------------------------------------------------

@Composable
fun LessonScreen(
    vm: AcademyViewModel,
    reduced: Boolean,
    onNavigateLesson: (track: String, course: String, lesson: String) -> Unit,
    onBack: () -> Unit,
) {
    val lesson = vm.lesson
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            GhostButton(stringResource(R.string.common_back), onClick = onBack, reduced = reduced)
            Spacer(Modifier.height(16.dp))
        }
        when {
            vm.lessonLoading -> item { LoadingBlock() }
            vm.lessonError != null -> item {
                ErrorLine(
                    if (vm.lessonError == AcademyViewModel.NETWORK_ERROR) {
                        stringResource(R.string.error_network)
                    } else vm.lessonError!!,
                )
            }
            lesson == null -> item { LoadingBlock() }
            else -> {
                item { LessonBody(vm, lesson, reduced, onNavigateLesson) }
            }
        }
    }
}

@Composable
private fun LessonBody(
    vm: AcademyViewModel,
    lesson: LessonPayload,
    reduced: Boolean,
    onNavigateLesson: (String, String, String) -> Unit,
) {
    Column {
        Text(
            text = lesson.courseTitle.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = SentinelGreen,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = lesson.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.common_minutes, lesson.minutes),
            style = MaterialTheme.typography.labelMedium,
            color = SentinelMuted,
        )
        if (lesson.done) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawnTick(modifier = Modifier.size(18.dp), playKey = 1, reduced = reduced)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lesson_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelGreen,
                )
            }
        }
        if (lesson.intro.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = lesson.intro,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        lesson.blocks.forEach { block -> BlockView(block) }

        // Practice questions (Expert lessons) — study aid, graded
        // server-side, never records progress.
        if (lesson.practice.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.lesson_practice_title))
            Spacer(Modifier.height(10.dp))
            lesson.practice.forEachIndexed { idx, q ->
                PracticeQuestion(vm, idx, q.q, q.choices, reduced)
                Spacer(Modifier.height(14.dp))
            }
        }

        // The check — one per lesson; correct completes the lesson.
        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.lesson_check_title))
        Spacer(Modifier.height(10.dp))
        CheckQuestion(vm, lesson, reduced)

        // Prev / next within the course (server-computed).
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            lesson.prev?.let { prev ->
                GhostButton(
                    text = stringResource(R.string.lesson_prev),
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateLesson(lesson.track, prev.course, prev.lesson) },
                )
                Spacer(Modifier.width(10.dp))
            }
            lesson.next?.let { next ->
                PrimaryButton(
                    text = stringResource(R.string.lesson_next),
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateLesson(lesson.track, next.course, next.lesson) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Paragraph -> {
            Text(
                text = block.x,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
        is Block.Heading -> {
            Spacer(Modifier.height(10.dp))
            Text(
                text = block.x,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        is Block.BulletList -> {
            Column(Modifier.padding(vertical = 4.dp)) {
                block.items.forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyLarge, color = SentinelGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(item, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        is Block.Steps -> {
            Column(Modifier.padding(vertical = 4.dp)) {
                block.items.forEachIndexed { i, item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SentinelGreen,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(item, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        is Block.Callout -> {
            val (label, accent) = when (block.kind) {
                "key" -> stringResource(R.string.callout_key) to SentinelGreen
                "warn" -> stringResource(R.string.callout_warn) to SentinelAmber
                else -> stringResource(R.string.callout_tip) to SentinelGreen
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (block.kind == "warn") SentinelContainer else SentinelGreenSoft)
                    .padding(14.dp),
            ) {
                Column {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        block.x,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        is Block.Exercise -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SentinelContainer)
                    .padding(14.dp),
            ) {
                Column {
                    SectionLabel(stringResource(R.string.lesson_exercise))
                    if (block.x.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(block.x, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    block.items.forEachIndexed { i, item ->
                        Row(Modifier.padding(top = 4.dp)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = SentinelGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
        is Block.Unknown -> Unit // forward-compat: never misrender
    }
}

@Composable
private fun CheckQuestion(vm: AcademyViewModel, lesson: LessonPayload, reduced: Boolean) {
    var selected by remember(lesson.id) { mutableStateOf<Int?>(null) }
    var result by remember(lesson.id) { mutableStateOf<CheckResult?>(null) }
    var shakeTrigger by remember(lesson.id) { mutableStateOf(0) }
    // Feedback for the two silent paths (audit 2026-11-11): a
    // blank submit used to only shake, and a failed round trip
    // (no grading happened) used to change nothing on screen at
    // all. Both now say what happened.
    var blankNudge by remember(lesson.id) { mutableStateOf(false) }
    var submitFailed by remember(lesson.id) { mutableStateOf(false) }
    val shake = rememberShakeOffset(shakeTrigger, reduced)
    val completed = lesson.done || result?.correct == true

    Column {
        Text(lesson.check.q, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        lesson.check.choices.forEachIndexed { i, choice ->
            val accent = when {
                result?.correct == true && selected == i -> SentinelGreen
                result?.correct == false && selected == i -> SentinelDanger
                else -> null
            }
            ChoiceRow(
                index = i, text = choice, selected = selected == i,
                accent = accent, reduced = reduced,
                // A completed lesson's rows lock (audit
                // 2026-11-11): they used to stay tappable with
                // the submit button already replaced by the tick
                // — selection changed, nothing could be sent.
                enabled = !completed,
                onClick = { selected = i; result = null; blankNudge = false },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (result?.correct == false) {
            ErrorLine(stringResource(R.string.lesson_incorrect))
            Spacer(Modifier.height(8.dp))
        }
        if (blankNudge && selected == null) {
            ErrorLine(stringResource(R.string.lesson_select_first))
            Spacer(Modifier.height(8.dp))
        }
        if (submitFailed) {
            ErrorLine(stringResource(R.string.error_network))
            Spacer(Modifier.height(8.dp))
        }
        if (completed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DrawnTick(modifier = Modifier.size(22.dp), playKey = shakeTrigger + 1, reduced = reduced)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lesson_correct), style = MaterialTheme.typography.titleMedium, color = SentinelGreen)
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.lesson_submit),
                reduced = reduced,
                modifier = Modifier
                    .fillMaxWidth()
                    // Reduced motion: shake.x carries the alpha
                    // factor (Motion.kt's documented contract);
                    // full motion: it carries pixel travel.
                    .graphicsLayer {
                        if (reduced) alpha = shake.x else translationX = shake.x
                    },
                onClick = {
                    val s = selected
                    if (s == null) {
                        blankNudge = true
                        shakeTrigger++
                    } else {
                        vm.submitCheck(null, s) { r ->
                            if (r == null) {
                                submitFailed = true
                                shakeTrigger++
                            } else {
                                submitFailed = false
                                result = r
                                if (!r.correct) shakeTrigger++
                            }
                        }
                    }
                },
            )
        }
        result?.explain?.let { explain ->
            Spacer(Modifier.height(10.dp))
            Text(
                explain,
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
        }
    }
}

@Composable
private fun PracticeQuestion(
    vm: AcademyViewModel,
    index: Int,
    q: String,
    choices: List<String>,
    reduced: Boolean,
) {
    var selected by remember(index, q) { mutableStateOf<Int?>(null) }
    var result by remember(index, q) { mutableStateOf<CheckResult?>(null) }
    // A failed grading round trip used to be invisible here
    // (audit 2026-11-11): the tap selected the row and nothing
    // else happened. It now says so under the question.
    var submitFailed by remember(index, q) { mutableStateOf(false) }
    Column {
        Text(q, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        choices.forEachIndexed { i, choice ->
            val accent = when {
                result != null && selected == i && result!!.correct -> SentinelGreen
                result != null && selected == i && !result!!.correct -> SentinelDanger
                else -> null
            }
            ChoiceRow(
                index = i, text = choice, selected = selected == i,
                accent = accent, reduced = reduced,
                enabled = result?.correct != true,
                onClick = {
                    selected = i
                    submitFailed = false
                    vm.submitCheck(index, i) { r ->
                        if (r == null) submitFailed = true else result = r
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (submitFailed && result == null) {
            ErrorLine(stringResource(R.string.error_network))
            Spacer(Modifier.height(8.dp))
        }
        result?.explain?.let { explain ->
            Text(
                (if (result!!.correct) stringResource(R.string.lesson_correct) + " " else "") + explain,
                style = MaterialTheme.typography.bodyMedium,
                color = SentinelMuted,
            )
        }
    }
}
