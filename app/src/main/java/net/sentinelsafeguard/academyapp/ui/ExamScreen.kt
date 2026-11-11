package net.sentinelsafeguard.academyapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.AcademyApi
import net.sentinelsafeguard.academyapp.data.Paper
import net.sentinelsafeguard.academyapp.data.ScoreResult
import net.sentinelsafeguard.academyapp.data.examRemainingSeconds
import net.sentinelsafeguard.academyapp.data.formatClock
import net.sentinelsafeguard.academyapp.ui.theme.SentinelAmber
import net.sentinelsafeguard.academyapp.ui.theme.SentinelDanger
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted

// ---------------------------------------------------------------------------
// Exams — the paper protocol, reused as-is (contract §4.3/§4.4):
// /api/paper draws a sampled, answer-free paper; the learner
// answers positions in the SHUFFLED choices as presented;
// /api/score grades entirely server-side. The app submits answer
// positions and its honest backgrounding count (blurs) — it can
// submit nothing else: no score, no pct, no pass.
//
// Terminal outcomes are presented as themselves, never dressed
// as errors: passed / not passed / late (time expired, unscored)
// / invalidated (the A-1 integrity gate's hard resolution, with
// the server's own message) / cooldown (from /api/status).
// Finals show the countdown from the paper's limit_seconds; the
// server enforces it (60s grace, then "late") — when the clock
// runs out the app submits what it holds rather than stranding
// the attempt.
//
// CERTIFICATE ISSUE is a trust-adjacent moment: the claim button
// and its confirmation stay morph/tick per the family confetti
// rule. The celebration lives on the certificates screen.
// ---------------------------------------------------------------------------

@Composable
fun ExamScreen(
    vm: AcademyViewModel,
    track: String,
    kind: String,
    course: String?,
    mode: String?,
    reduced: Boolean,
    onDone: () -> Unit,
    onGoCerts: () -> Unit,
) {
    LaunchedEffect(track, kind, course, mode) {
        if (vm.paper == null && vm.scoreResult == null && vm.examError == null) {
            vm.startExam(track, kind, course, mode)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        when {
            vm.examError != null && vm.paper == null && vm.scoreResult == null -> {
                Column(Modifier.padding(20.dp)) {
                    GhostButton(stringResource(R.string.common_back), onClick = { vm.clearExam(); onDone() }, reduced = reduced)
                    Spacer(Modifier.height(16.dp))
                    ErrorLine(
                        if (vm.examError == AcademyViewModel.NETWORK_ERROR) {
                            stringResource(R.string.error_network)
                        } else vm.examError!!,
                    )
                    if (vm.cooldownSeconds > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.road_cooldown, durationText(vm.cooldownSeconds)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SentinelAmber,
                        )
                    }
                }
            }
            vm.scoreResult != null -> ScoreResultView(vm, vm.scoreResult!!, track, reduced, onDone, onGoCerts)
            vm.paper != null -> PaperView(vm, vm.paper!!, reduced)
            else -> LoadingBlock()
        }
    }
}

@Composable
private fun PaperView(vm: AcademyViewModel, paper: Paper, reduced: Boolean) {
    val answers = remember(paper.paper) { mutableStateOf(List<Int?>(paper.total) { null }) }
    var remaining by remember(paper.paper) { mutableStateOf(paper.limitSeconds) }
    var submitted by remember(paper.paper) { mutableStateOf(false) }

    LaunchedEffect(paper.paper) {
        if (paper.limitSeconds > 0) {
            while (remaining > 0) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - vm.paperStartedAtMs) / 1000
                remaining = examRemainingSeconds(paper.limitSeconds, elapsed)
            }
            if (!submitted) {
                submitted = true
                vm.submitExam(answers.value.map { it ?: 0 })
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        paper.kind == "final" && paper.mode == "practice" -> stringResource(R.string.exam_practice)
                        paper.kind == "final" -> stringResource(R.string.exam_final)
                        else -> stringResource(R.string.exam_quiz)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (paper.limitSeconds > 0) {
                    Text(
                        text = formatClock(remaining),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remaining <= 60) SentinelAmber else SentinelMuted,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.exam_attempt, paper.attemptNo, paper.code),
                style = MaterialTheme.typography.labelMedium,
                color = SentinelMuted,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(paper.questions.size) { qi ->
            val q = paper.questions[qi]
            Column(Modifier.padding(bottom = 18.dp)) {
                Text(
                    text = stringResource(R.string.exam_question_of, qi + 1, paper.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinelMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(q.q, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                q.choices.forEachIndexed { ci, choice ->
                    ChoiceRow(
                        index = ci, text = choice,
                        selected = answers.value[qi] == ci,
                        reduced = reduced,
                        onClick = {
                            answers.value = answers.value.toMutableList().also { it[qi] = ci }
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        item {
            val allAnswered = answers.value.all { it != null }
            if (!allAnswered) {
                Text(
                    text = stringResource(R.string.exam_answer_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = SentinelMuted,
                )
                Spacer(Modifier.height(8.dp))
            }
            // A failed score call keeps the paper on screen; its
            // error renders here, above the button it belongs to
            // (audit 2026-11-11 — it used to be set on the VM and
            // rendered nowhere while a paper was live).
            vm.examError?.let { err ->
                ErrorLine(
                    if (err == AcademyViewModel.NETWORK_ERROR) {
                        stringResource(R.string.error_network)
                    } else err,
                )
                Spacer(Modifier.height(8.dp))
            }
            PrimaryButton(
                text = stringResource(R.string.exam_submit),
                enabled = allAnswered,
                busy = vm.busy,
                reduced = reduced,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    submitted = true
                    vm.submitExam(answers.value.map { it ?: 0 })
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScoreResultView(
    vm: AcademyViewModel,
    result: ScoreResult,
    track: String,
    reduced: Boolean,
    onDone: () -> Unit,
    onGoCerts: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            when {
                result.late -> {
                    Text(stringResource(R.string.exam_late_title), style = MaterialTheme.typography.headlineSmall, color = SentinelAmber)
                    Spacer(Modifier.height(8.dp))
                    result.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                result.invalidated -> {
                    Text(stringResource(R.string.exam_invalidated_title), style = MaterialTheme.typography.headlineSmall, color = SentinelDanger)
                    Spacer(Modifier.height(8.dp))
                    result.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                result.scored -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (result.passed) {
                            DrawnTick(modifier = Modifier.size(30.dp), playKey = 1, reduced = reduced)
                            Spacer(Modifier.height(0.dp))
                        }
                        Text(
                            text = stringResource(if (result.passed) R.string.exam_pass else R.string.exam_fail),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (result.passed) SentinelGreen else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.exam_score_line, result.score, result.total, result.pct),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.exam_pass_mark, result.passPct),
                        style = MaterialTheme.typography.bodySmall,
                        color = SentinelMuted,
                    )
                }
                else -> {
                    result.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            result.autoNote?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = SentinelAmber)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (result.breakdown.isNotEmpty()) {
            item {
                SectionLabel(stringResource(R.string.exam_breakdown))
                Spacer(Modifier.height(8.dp))
            }
            items(result.breakdown.size) { i ->
                val d = result.breakdown[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(d.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    Text("${d.correct}/${d.total} · ${d.pct}%", style = MaterialTheme.typography.labelMedium, color = SentinelMuted)
                }
            }
        }
        if (result.missed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.exam_missed))
                Spacer(Modifier.height(8.dp))
            }
            items(result.missed.size) { i ->
                val m = result.missed[i]
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(m.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(m.note, style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
                }
            }
        }
        if (result.review.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.exam_review))
                Spacer(Modifier.height(8.dp))
            }
            items(result.review.size) { i ->
                val r = result.review[i]
                // Full review (audit 2026-11-11, capability gap):
                // the VM retains the scored paper and the
                // submitted answers, so each item can show the
                // question itself, what the learner answered, and
                // — on a miss — the correct answer, all revealed
                // by the server only after grading. When the
                // retained paper is unavailable (e.g. state was
                // cleared), the item degrades to the number +
                // explanation rather than disappearing.
                val q = vm.reviewPaper?.questions?.getOrNull(i)
                val yourIdx = vm.reviewAnswers.getOrNull(i)
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.exam_question_of, i + 1, result.review.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (r.correct) SentinelGreen else SentinelDanger,
                    )
                    if (q != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(q.q, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        if (yourIdx != null && q.choices.indices.contains(yourIdx)) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.exam_your_answer, q.choices[yourIdx]),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (r.correct) SentinelGreen else SentinelDanger,
                            )
                        }
                        if (!r.correct && q.choices.indices.contains(r.answer)) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.exam_correct_was, q.choices[r.answer]),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SentinelGreen,
                            )
                        }
                    }
                    r.explain?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
                    }
                }
            }
        }

        // Certificate claim — final exam pass with a live token.
        // Morph/tick only on this surface (trust-adjacent).
        val passToken = result.token
        if (result.passed && result.kind == "final" && passToken != null && !result.invalidated) {
            item {
                Spacer(Modifier.height(18.dp))
                when {
                    vm.issueResult != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DrawnTick(modifier = Modifier.size(26.dp), playKey = 1, reduced = reduced)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.exam_issued_title) + " · " + vm.issueResult!!.code,
                                style = MaterialTheme.typography.titleMedium,
                                color = SentinelGreen,
                            )
                        }
                        // No button without a destination (audit
                        // 2026-11-11): the certificate URL comes
                        // from the issue response; an empty one
                        // would open the bare base URL.
                        if (vm.issueResult!!.url.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            GhostButton(
                                text = stringResource(R.string.exam_open_certificate),
                                reduced = reduced,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val url = AcademyApi.BASE_URL + vm.issueResult!!.url
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = stringResource(R.string.nav_certs),
                            reduced = reduced,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { vm.clearExam(); vm.clearIssue(); onGoCerts() },
                        )
                    }
                    else -> {
                        vm.issueError?.let { ErrorLine(it); Spacer(Modifier.height(8.dp)) }
                        PrimaryButton(
                            text = stringResource(R.string.exam_issue_cta),
                            busy = vm.busy,
                            reduced = reduced,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { vm.issueCertificate(track, passToken) },
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            GhostButton(
                text = stringResource(R.string.exam_done),
                reduced = reduced,
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.clearExam(); vm.clearIssue(); onDone() },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
