package net.sentinelsafeguard.academyapp.data

// ---------------------------------------------------------------------------
// Wire models for the Academy App API (contract of record:
// ~/workspace/sentinel-live-deploy/ACADEMY-APP-API-CONTRACT-2026-11-10.md,
// BUILD RECORD 2026-11-10 — all endpoints LIVE in production).
//
// These mirror the server's payloads field for field. Two
// absences are deliberate and load-bearing:
// - LessonPayload.CheckQ / PracticeQ have NO answer and NO explain
//   field — the server strips them and the app never grades
//   client-side; explanations arrive only inside a /api/app/check
//   response AFTER the server grades.
// - Track.access is display-only ("free" on every track today);
//   there are no price/commerce fields anywhere in this file
//   because none exist in the contract.
// ---------------------------------------------------------------------------

data class UserInfo(
    val displayName: String,
    val certName: String,
    val email: String,
    val promo: Boolean,
    val registry: Boolean,
    val leaderboard: Boolean,
    val notifyCertAnniversary: Boolean,
)

/** The five account settings the G4 twins carry (SETTINGS-TWINS
 *  2026-11-11): every /api/app/profile|social|notifications
 *  mutation answers with the caller's FRESH set in exactly this
 *  shape, and GET /api/me's user object carries the same five
 *  fields (display_name, cert_name, registry, leaderboard,
 *  notify_cert_anniversary) — so settings render cold from /api/me
 *  and every save is confirmed by the server's own read-back, not
 *  by the values the client sent. */
data class AccountSettings(
    val displayName: String,
    val certName: String,
    val registry: Boolean,
    val leaderboard: Boolean,
    val notifyCertAnniversary: Boolean,
)

/** GET /api/me — the authoritative user/progress object. */
data class Me(
    val user: UserInfo,
    val enrolled: List<String>,
    val imported: Boolean,
    /** Completed lesson ids ("track--course--lesson"). */
    val lessons: Set<String>,
    /** courseId ("track--course") -> (pct, passed). */
    val quizzes: Map<String, QuizBest>,
    /** track slug -> final state. */
    val tracks: Map<String, TrackProgress>,
    /** track slug -> certificate code. */
    val certs: Map<String, String>,
    val certList: List<CertEntry>,
)

data class QuizBest(val pct: Int, val passed: Boolean)

data class TrackProgress(val finalPassed: Boolean, val finalPct: Int?, val practicePct: Int?)

data class CertEntry(
    val code: String,
    val track: String,
    /** Display string as issued (e.g. "November 10, 2026") — shown as given. */
    val issued: String,
    val status: String,
)

data class Catalog(
    val tracks: List<Track>,
    /** True when the response carried the session overlay
     *  (enrolled/state/done). Anonymous catalogs have none. */
    val hasOverlay: Boolean,
)

data class Track(
    val slug: String,
    val lang: String,
    val name: String,
    val short: String,
    val kicker: String,
    val pitch: String,
    val pitchLong: String,
    val certName: String,
    /** Display-only access label ("free" today). Gates nothing. */
    val access: String,
    val enrolled: Boolean?,
    val final: FinalInfo?,
    val courses: List<Course>,
)

data class FinalInfo(val bankQuestions: Int, val draw: Int, val minutes: Int, val passPct: Int)

data class Course(
    val slug: String,
    val title: String,
    val tagline: String,
    val summary: String,
    val certLabel: String,
    val difficulty: String,
    val estMinutes: Int,
    val elective: Boolean,
    val lessonCount: Int,
    val quiz: QuizInfo?,
    /** "complete" | "open" | "locked" with session; null anonymous. */
    val state: String?,
    val modules: List<Module>,
)

data class QuizInfo(val draw: Int, val passPct: Int, val pool: Int)

data class Module(val title: String, val lessons: List<LessonRef>)

data class LessonRef(
    val slug: String,
    val id: String,
    val title: String,
    val minutes: Int,
    val done: Boolean?,
)

/** GET /api/app/lesson — one lesson, blocks verbatim, check and
 *  practice WITHOUT answer/explain (stripped server-side). */
data class LessonPayload(
    val id: String,
    val track: String,
    val lang: String,
    val courseSlug: String,
    val courseTitle: String,
    val moduleTitle: String?,
    val title: String,
    val intro: String,
    val minutes: Int,
    val blocks: List<Block>,
    val check: CheckQ,
    val practice: List<CheckQ>,
    val prev: LessonNav?,
    val next: LessonNav?,
    val done: Boolean,
)

data class LessonNav(val course: String, val lesson: String)

/** A question as the app may see it: stem + choices, nothing else. */
data class CheckQ(val q: String, val choices: List<String>)

sealed class Block {
    data class Paragraph(val x: String) : Block()
    data class Heading(val x: String) : Block()
    data class BulletList(val items: List<String>) : Block()
    data class Steps(val items: List<String>) : Block()
    /** kind ∈ "tip" | "key" | "warn" (server vocabulary). */
    data class Callout(val kind: String, val x: String) : Block()
    data class Exercise(val x: String, val items: List<String>) : Block()
    /** A block type this build does not know. The contract
     *  enumerates exactly six types; an unknown one renders as
     *  nothing rather than crashing the lesson (forward-compat
     *  posture, never silently misrendered as a paragraph). */
    data class Unknown(val t: String) : Block()
}

/** POST /api/app/check result. explain is present only when the
 *  server revealed it (on a correct answer). */
data class CheckResult(val correct: Boolean, val explain: String?)

/** POST /api/paper — a sampled, answer-free exam paper. */
data class Paper(
    val paper: String,
    val code: String,
    val kind: String,
    val track: String,
    val course: String?,
    val mode: String?,
    val attemptNo: Int,
    val total: Int,
    val limitSeconds: Int,
    val expiresAt: Long,
    val questions: List<CheckQ>,
)

/** POST /api/score result — every terminal outcome is data, not
 *  an error (contract §4.4): late, invalidated, auto_note, flags. */
data class ScoreResult(
    val scored: Boolean,
    val late: Boolean = false,
    val message: String? = null,
    val score: Int = 0,
    val total: Int = 0,
    val pct: Int = 0,
    val passed: Boolean = false,
    val passPct: Int = 0,
    val flags: List<String> = emptyList(),
    val mode: String? = null,
    val kind: String? = null,
    val invalidated: Boolean = false,
    val autoNote: String? = null,
    /** Pass token for /api/issue (final exam pass only). */
    val token: String? = null,
    val review: List<ReviewItem> = emptyList(),
    val breakdown: List<DomainBreakdown> = emptyList(),
    val missed: List<MissedNote> = emptyList(),
)

data class ReviewItem(val correct: Boolean, val answer: Int, val explain: String?)
data class DomainBreakdown(val domain: String, val label: String, val correct: Int, val total: Int, val pct: Int)
data class MissedNote(val domain: String, val label: String, val note: String)

/** POST /api/issue result. */
data class IssueResult(val code: String, val url: String, val status: String)

/** GET /api/app/streaks — exactly these four fields (contract §5.2). */
data class Streaks(
    val current: Int,
    val longest: Int,
    val lessonsThisWeek: Int,
    val weeklyGoal: Int,
)

/** GET /api/status — final-exam cooldown display. */
data class ExamStatus(val attempts: Int, val cooldownSeconds: Int)

/** GET /api/app/verify — found carries the public record only;
 *  not-found and malformed are the identical shape server-side.
 *  [lookupError] (audit 2026-11-11) separates "no lookup
 *  happened" from "lookup happened, nothing matched": the twin
 *  answers 429 {"error": "rate limited — slow down"} when the
 *  shared budget is spent, BEFORE any lookup, and a transport
 *  failure likewise performs none. Presenting either as
 *  "no certificate matches" would be a false statement, so the
 *  UI renders them as their own states: "rate" | "net". */
data class VerifyResult(
    val found: Boolean,
    val name: String? = null,
    val track: String? = null,
    val trackName: String? = null,
    val certificate: String? = null,
    val issued: String? = null,
    val status: String,
    val valid: Boolean,
    val lookupError: String? = null,
)
