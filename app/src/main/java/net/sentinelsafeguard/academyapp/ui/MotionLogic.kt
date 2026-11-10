package net.sentinelsafeguard.academyapp.ui

// ---------------------------------------------------------------------------
// Motion timing law — the numbers behind Motion.kt, pure and unit
// tested. Ported from the Sentinel family motion layer (Motion
// Wave M, owner-approved 2026-11-07/08): this app inherits the
// family's feel, it does not invent its own. Motion in this app
// serves the learning core — transitions explain where a tap took
// you, staggered entrances scan top-down like reading, and the
// drawn tick confirms a finished lesson or a passed check.
//
// The feel: quick, weighted, never bouncy. Nothing in this app
// overshoots, springs, or spins for decoration. Every figure was
// chosen ON THE CURVES on a real device, not from a default:
// - Presses sink to 95% — a surface going DOWN reads as mass; a
//   fade reads as a glitch.
// - The radio-tick ease (0.2, 0, 0, 1) is how paper moves: all
//   the acceleration up front, a long settle after.
// - Two-beat pauses are 60/40, never even: an even pause reads
//   mechanical, the family rhythm is a heartbeat.
// - Rejected input shakes and decays like something struck —
//   keyframes at irrational fractions so it never looks looped.
//
// Reduced motion: when the system animator scale is 0 the app
// runs a REDUCED path — crossfades only, zero positional travel.
// It is a mode, not an afterthought, and every motion below
// resolves through that flag.
// ---------------------------------------------------------------------------

/** Master switch for the reduced-motion path. The system animator
 *  scale is 0 when the user (or a battery saver) has asked for no
 *  animation; the app honours that by REPLACING motion, not just
 *  shortening it. */
fun reducedMotion(animatorScale: Float): Boolean = animatorScale <= 0f

// M-09 — screen-list stagger: each row 70 ms behind the one above,
// capped at ten rows so a long list never trickles. offsetFor()
/** The stagger plan for a screen-list entrance (M-09): how many
 *  rows get a beat of their own, and each row's entrance offset. */
fun staggerCount(itemCount: Int): Int = itemCount.coerceIn(0, STAGGER_CAP)

fun staggerDelayMs(index: Int): Int =
    if (index < 0) 0 else index.coerceAtMost(STAGGER_CAP - 1) * STAGGER_STEP_MS

// M-13 — error shake (the family "wrong way" signal): keyframe
// offsets as FRACTIONS of the total travel, so the offsets decay
// like a struck object settling, not a sine loop.
/** Keyframe times (fractions of duration) for the error shake. */
fun shakeKeyframeTimes(): List<Float> = SHAKE_TIMES

/** Keyframe offsets (fractions of full travel) for the error shake. */
fun shakeKeyframeOffsets(): List<Float> = SHAKE_OFFSETS

// M-06 — drawn-tick two-beat: arm one, pause, arm two. The pause
// split is 60/40 (below), and the total is long enough to read
// as drawn by hand.
// M-08 — press-and-hold fill (hold-to-confirm surfaces): 1100 ms
// is the family constant: short enough to not frustrate, long
// enough that a stray touch cannot complete it.
//
// Families (for tests and call sites, not magic numbers):
const val PRESS_SINK: Float = 0.95f // M-01
const val PRESS_MS: Int = 120
const val STAGGER_STEP_MS: Int = 70 // M-09
const val STAGGER_CAP: Int = 10 // M-09
const val LIST_FADE_MS: Int = 180 // M-09
const val SHAKE_MS: Int = 360 // M-13
const val SHAKE_TRAVEL_DP: Float = 8f // M-13
const val TICK_TOTAL_MS: Int = 470 // M-06
const val TICK_ARM1_SHARE: Float = 0.38f // M-06
const val TICK_BEAT_SHARE: Float = 0.22f // M-06 (60/40 pauses)
const val DRAWN_LINE_MS: Int = 650
const val HOLD_FULL_MS: Int = 1100 // M-08
const val REDUCED_FADE_MS: Int = 120
const val SCREEN_FADE_MS: Int = 220
const val SCREEN_FADE_MS_REDUCED: Int = 120
const val SCREEN_SLIDE_MS: Int = 300

private val SHAKE_TIMES = listOf(0f, 0.18f, 0.38f, 0.57f, 0.76f, 0.9f, 1f)
private val SHAKE_OFFSETS = listOf(0f, 1f, -0.72f, 0.45f, -0.22f, 0.07f, 0f)
