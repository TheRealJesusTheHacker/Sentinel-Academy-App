package net.sentinelsafeguard.academyapp.data

import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// AcademyLogic — the app's pure rules, no Android, no JSON, unit
// tested. Everything here mirrors a server rule the contract
// states; where the server is the authority (road states, grading,
// streaks), this file holds only the CLIENT-side presentation math
// and pre-flight checks — never a re-implementation that could
// drift from the server's answer.
// ---------------------------------------------------------------------------

/** Certificate code format gate (contract §6.2): the normalized
 *  code must match ^SA[A-Z0-9]{6,18}$. The app validates BEFORE
 *  spending one of the shared 60/hour verify lookups — a malformed
 *  code never leaves the device, and is presented identically to
 *  a never-issued code, exactly as every public face presents it. */
private val CERT_CODE_RE = Regex("^SA[A-Z0-9]{6,18}$")

/** norm_code, client twin: strip non-[A-Z0-9], uppercase. */
fun normalizeCertCode(raw: String): String =
    raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

fun certCodeWellFormed(raw: String): Boolean = CERT_CODE_RE.matches(normalizeCertCode(raw))

/** Password policy (contract §1.1): 10..200 characters, enforced
 *  server-side at signup; the app checks first so the learner
 *  hears it before the round trip. Returns a reason key or null. */
fun signupPasswordProblem(password: String): String? = when {
    password.length < 10 -> "short"
    password.length > 200 -> "long"
    else -> null
}

/** Session expiry (contract §1.2): fixed 7-day TTL, no sliding
 *  renewal. The app treats a stored token as expired when now is
 *  at/past the server-issued expiry and takes the re-login path. */
fun sessionExpired(expiresAtEpochSec: Long, nowEpochSec: Long): Boolean =
    expiresAtEpochSec <= 0L || nowEpochSec >= expiresAtEpochSec

/** Weekly-goal progress for the streak card: the goal is framed
 *  as "a guide, not a requirement" (contract §5.1) everywhere it
 *  appears; this is only the fill fraction for the quiet bar. */
fun weeklyGoalFraction(lessonsThisWeek: Int, weeklyGoal: Int): Float =
    if (weeklyGoal <= 0) 0f else (lessonsThisWeek.toFloat() / weeklyGoal).coerceIn(0f, 1f)

/** Exam timer math (contract §4.3): a paper carries limit_seconds
 *  and the server enforces it (grace 60s, then "late", unscored).
 *  The app counts down from the limit at paper receipt. */
fun examRemainingSeconds(limitSeconds: Int, elapsedSeconds: Long): Int =
    (limitSeconds - elapsedSeconds).coerceAtLeast(0).toInt()

fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Road rule (contract §4.2), presentation twin: a course the
 *  server marks "complete" or "open" may be entered; "locked"
 *  may not. The server computes state — this only reads it. */
fun courseEnterable(state: String?): Boolean = state == "open" || state == "complete"

/** Fraction of a track's lessons done, for progress display —
 *  computed from the catalog overlay the server sent (done flags
 *  it computed), never from a client-side road re-derivation. */
fun trackProgress(doneLessons: Int, totalLessons: Int): Float =
    if (totalLessons <= 0) 0f else (doneLessons.toFloat() / totalLessons).coerceIn(0f, 1f)

/** Cooldown decomposition for display (audit 2026-11-11): a raw
 *  m:ss clock misreads past an hour — a 7-day invalidation
 *  cooldown rendered "10080:00". Returns (days, hours, minutes,
 *  seconds); the UI composes the largest units with localized
 *  unit labels. Negative input reads as zero. */
fun durationParts(totalSeconds: Int): List<Int> {
    val s = totalSeconds.coerceAtLeast(0)
    return listOf(s / 86_400, (s % 86_400) / 3_600, (s % 3_600) / 60, s % 60)
}

/** Final availability from the road payload itself (audit
 *  2026-11-11): the final unlocks when every core course's
 *  server-computed state is "complete". The server stays the
 *  authority — this only spares the learner a doomed paper
 *  request and an unexplained locked CTA. An absent overlay
 *  (null states) or an empty course list reads locked. */
fun finalUnlocked(courseStates: List<String?>): Boolean =
    courseStates.isNotEmpty() && courseStates.all { it == "complete" }

// ---------------------------------------------------------------------------
// Catalog validator cache (CATALOG-ETAG, server gift 2026-11-11).
//
// The ANONYMOUS catalog is a byte-uniform public payload, and the
// server now serves it under a strong ETag — the quoted SHA-256
// hex of the exact body bytes — with Cache-Control: private,
// no-cache, answering a matching If-None-Match with a 304. The
// AUTHENTICATED catalog carries the caller's overlay and stays
// no-store with no validator by design: it is never cached, here
// or anywhere. These pure rules pin the client half: when a
// stored copy may stand in for a network body, and how a stored
// copy proves it is the bytes its ETag names before it is used
// or revalidated — a torn or tampered cache can never present
// bytes the server did not confirm.
// ---------------------------------------------------------------------------

/** Lowercase hex SHA-256 of [bytes]. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

/** True when [etag] names exactly [body]: the validator is the
 *  quoted SHA-256 hex of the body bytes (a W/ prefix and
 *  surrounding whitespace are tolerated, as the server's own
 *  If-None-Match parsing tolerates them). Compared in constant
 *  time; anything malformed is simply not a match. */
fun etagMatchesBody(etag: String?, body: ByteArray): Boolean {
    var v = etag?.trim() ?: return false
    if (v.startsWith("W/")) v = v.substring(2).trim()
    v = v.trim('"')
    if (v.length != 64) return false
    val expected = sha256Hex(body)
    return MessageDigest.isEqual(
        v.toByteArray(Charsets.US_ASCII),
        expected.toByteArray(Charsets.US_ASCII),
    )
}

/** When the stored catalog copy may be used instead of a fresh
 *  network body: on a 304 (the server just confirmed those exact
 *  bytes are current) and on a transport failure with no answer
 *  at all (status 0 — the offline case, where the last
 *  server-confirmed copy is the honest fallback). Any real HTTP
 *  answer — including errors — is presented as itself, never
 *  papered over with the cache. */
fun catalogUseStored(status: Int, hasStored: Boolean): Boolean =
    hasStored && (status == 304 || status == 0)

// ---------------------------------------------------------------------------
// Certificate-celebration confetti — the pure physics half.
//
// Owner ruling 2026-11-08 (estate-wide): confetti is permitted
// ONLY outside trust moments, at most 18 pieces, estate colors
// only, real gravity, none under reduced-motion. In this app the
// certificate ISSUE moment is trust-adjacent and stays morph/tick
// only; the celebration, when it fires at all, fires on the
// post-issue certificates screen. The composable half lives in
// ui/Confetti.kt and refuses to run under reduced motion; this
// half is the gravity sim it steps.
// ---------------------------------------------------------------------------

const val CONFETTI_MAX_PIECES: Int = 18
const val CONFETTI_GRAVITY: Float = 1500f // px/s^2, downward
const val CONFETTI_DRAG: Float = 0.12f // per-second velocity retention loss

data class ConfettiPiece(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val angleDeg: Float, val spinDegPerSec: Float,
    val colorIndex: Int,
    val widthPx: Float, val heightPx: Float,
)

/** Burst origin plan: pieces launch upward/outward from a point
 *  and fall under gravity — a celebration, not a fountain loop.
 *  [random] is injected so tests can pin the plan. */
fun confettiBurst(originX: Float, originY: Float, count: Int, random: Random): List<ConfettiPiece> {
    val n = count.coerceIn(0, CONFETTI_MAX_PIECES)
    return (0 until n).map { i ->
        val spread = (i.toFloat() / maxOf(1, n - 1)) - 0.5f // -0.5..0.5 across the burst
        val angle = -PI / 2 + spread * 1.5 // up, fanned
        val speed = 620f + random.nextFloat() * 380f
        ConfettiPiece(
            x = originX, y = originY,
            vx = (cos(angle) * speed).toFloat(),
            vy = (sin(angle) * speed).toFloat(),
            angleDeg = random.nextFloat() * 360f,
            spinDegPerSec = (random.nextFloat() - 0.5f) * 540f,
            colorIndex = i % CONFETTI_COLOR_COUNT,
            widthPx = 14f + random.nextFloat() * 8f,
            heightPx = 8f + random.nextFloat() * 6f,
        )
    }
}

/** One physics step: gravity pulls vy down, drag bleeds speed,
 *  rotation advances. Pure — the composable calls it per frame. */
fun confettiStep(p: ConfettiPiece, dtSeconds: Float): ConfettiPiece {
    val dragFactor = (1f - CONFETTI_DRAG * dtSeconds).coerceIn(0f, 1f)
    val nvx = p.vx * dragFactor
    val nvy = p.vy * dragFactor + CONFETTI_GRAVITY * dtSeconds
    return p.copy(
        x = p.x + nvx * dtSeconds,
        y = p.y + nvy * dtSeconds,
        vx = nvx, vy = nvy,
        angleDeg = p.angleDeg + p.spinDegPerSec * dtSeconds,
    )
}

/** Palette size for the burst — estate colors only, indexed so
 *  the pure half never names an Android Color. The composable
 *  maps indices 0..3 to emerald / dim emerald / soft emerald /
 *  parchment text tone. No amber (attention-only in this theme),
 *  no purple (ever). */
const val CONFETTI_COLOR_COUNT: Int = 4
