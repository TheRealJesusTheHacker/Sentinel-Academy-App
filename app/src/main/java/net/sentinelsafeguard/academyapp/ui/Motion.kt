package net.sentinelsafeguard.academyapp.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen

// ---------------------------------------------------------------------------
// The Sentinel motion layer (Motion Wave M, owner-approved
// 2026-11-07/08 for the app family) — the composable half; the
// timing law and its tests live in MotionLogic.kt. Ported in full
// from the family's canonical implementation so the Academy App
// moves like its siblings from day one; phase 2's surfaces are
// built on these helpers, never on ad-hoc animation.
//
// Includes the estate's confirmation language: when an action
// completes, the control itself becomes the receipt — it draws
// the estate's checkmark in two beats and says SAVED, then
// restores. The receipt lives inside the control the user pressed
// because the confirmation must be looked at where the action
// happened. Trust moments in this app — a submitted answer, a
// claimed certificate — end on the morph, never on confetti.
// ---------------------------------------------------------------------------

/** True when the system asks for no animation (animator scale 0).
 *  Every motion in this layer resolves through this flag: reduced
 *  means crossfades only, zero positional travel. */
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

/** The radio-tick ease: all acceleration up front, long settle.
 *  One curve across the app so surfaces feel related. */
val SentinelEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// ---------------------------------------------------------------------------
// M-01 — Pressable: the press-sink scale shared by every pressable
// surface. A pressed surface sinks to [PRESS_SINK] — down reads as
// mass; a fade reads as a glitch. Reduced motion: no sink, the
// state change alone carries the feedback.
// ---------------------------------------------------------------------------
@Stable
class PressableState internal constructor(
    val interaction: MutableInteractionSource,
    val scale: Float,
)

/** Scale + interaction pair for a pressable surface. Use as:
 *  `val press = rememberPressable(reduced)`; hand
 *  `press.interaction` to the clickable, and `Modifier.scale(press.scale)`
 *  to the surface. */
@Composable
fun rememberPressable(reduced: Boolean): PressableState {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed && !reduced) PRESS_SINK else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(PRESS_MS, easing = SentinelEase),
        label = "pressSink",
    )
    return PressableState(interaction, if (reduced) 1f else scale)
}

// ---------------------------------------------------------------------------
// M-09 — List entrance: rows settle top-down in a staggered fade,
// each row 70 ms behind the one above, capped at ten rows. A card
// containing many rows animates as ONE item (pass the same index
// for the card, not per-row), so heavy lists never trickle.
// Reduced motion collapses the stagger to a single crossfade.
// ---------------------------------------------------------------------------
/** Stagger bookkeeping for a list entrance: when the owner flips
 *  `active` true (usually when content has loaded), each row's
 *  alpha animates in behind its delay. */
@Stable
class ListEntrance internal constructor(
    private val active: Boolean,
    private val reduced: Boolean,
) {
    @Composable
    fun offsetFor(index: Int): Float {
        var shown by remember { mutableStateOf(!active) }
        LaunchedEffect(active, index) {
            if (!active) {
                shown = false
            } else if (reduced) {
                shown = true
            } else {
                delay(staggerDelayMs(index).toLong())
                shown = true
            }
        }
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(
                if (reduced) REDUCED_FADE_MS else LIST_FADE_MS,
                easing = SentinelEase,
            ),
            label = "listEntrance$index",
        )
        // Reduced: crossfade only. Full: fade alone — positional
        // travel on data rows read as sliding in rehearsal and was
        // cut; the stagger IS the motion.
        return alpha
    }
}

@Composable
fun rememberListEntrance(active: Boolean, reduced: Boolean): ListEntrance =
    remember(active, reduced) { ListEntrance(active, reduced) }

/** Convenience: a row wrapper applying the entrance alpha for [index]. */
@Composable
fun Modifier.listEntrance(entrance: ListEntrance, index: Int): Modifier =
    this.alpha(entrance.offsetFor(index))

// ---------------------------------------------------------------------------
// M-13 — Error shake: when an answer or input is rejected, the
// surface shakes and decays like something struck — keyframes at
// irrational fractions so it never reads as a loop. Reduced
// motion: a single alpha pulse instead (no travel, same signal).
// ---------------------------------------------------------------------------
/** Plays the shake once per [trigger] change. Returns the
 *  horizontal offset to apply via Modifier.offset / graphicsLayer. */
@Composable
fun rememberShakeOffset(trigger: Int, reduced: Boolean, travel: Dp = SHAKE_TRAVEL_DP.dp): Offset {
    val density = LocalDensity.current
    val travelPx = with(density) { travel.toPx() }
    var progress by remember { mutableStateOf(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress = 0f
            val steps = 36
            repeat(steps) { i ->
                progress = (i + 1).toFloat() / steps
                delay((SHAKE_MS / steps).toLong())
            }
            progress = 1f
        }
    }
    if (reduced) {
        // Alpha-only signal: dip and restore.
        val dip = if (progress < 0.5f) 1f - progress * 0.6f else 0.7f + (progress - 0.5f) * 0.6f
        return Offset(dip, 0f) // x carries alpha factor in reduced mode; see ErrorShakeBox usage
    }
    val times = shakeKeyframeTimes()
    val offsets = shakeKeyframeOffsets()
    val p = progress.coerceIn(0f, 1f)
    var seg = 0
    while (seg < times.size - 2 && p > times[seg + 1]) seg++
    val span = (times[seg + 1] - times[seg]).takeIf { it > 0f } ?: 1f
    val local = ((p - times[seg]) / span).coerceIn(0f, 1f)
    val fraction = offsets[seg] + (offsets[seg + 1] - offsets[seg]) * local
    return Offset(fraction * travelPx, 0f)
}

// ---------------------------------------------------------------------------
// M-06 — DrawnTick: the estate's confirmation glyph. A checkmark
// that DRAWS itself in two beats — arm one (0→38% of the total),
// a heartbeat pause, arm two to the point (62%→100%) — then rests.
// The two-beat rhythm is the brand's pulse: it appears wherever
// the app confirms something real (finished lesson, passed
// check, submitted answer). Trust contexts get the tick and
// nothing else.
//
// Drawn on Canvas in the caller's color; the path is the estate's
// check proportions (short arm down-right, long arm up-right),
// stroked round-cap at 9% of size.
// ---------------------------------------------------------------------------
/** Two-beat reveal schedule for the drawn tick: returns the drawn
 *  fraction (0..1 of total path length) at normalized time [t].
 *  Kept pure so the schedule is reviewable at a glance. */
fun tickDrawnFraction(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val pauseStart = TICK_ARM1_SHARE
    val pauseEnd = TICK_ARM1_SHARE + TICK_BEAT_SHARE
    return when {
        x <= pauseStart -> TICK_ARM1_SHARE * (x / pauseStart)
        x < pauseEnd -> TICK_ARM1_SHARE
        else -> TICK_ARM1_SHARE + (1f - TICK_ARM1_SHARE) *
            ((x - pauseEnd) / (1f - pauseEnd))
    }
}

@Composable
fun DrawnTick(
    modifier: Modifier = Modifier,
    color: Color = SentinelGreen,
    playKey: Int = 1,
    reduced: Boolean = false,
) {
    var fraction by remember(playKey) { mutableStateOf(if (reduced) 1f else 0f) }
    LaunchedEffect(playKey, reduced) {
        if (reduced) {
            fraction = 1f
            return@LaunchedEffect
        }
        fraction = 0f
        val steps = 28
        repeat(steps) { i ->
            fraction = tickDrawnFraction((i + 1).toFloat() / steps)
            delay((TICK_TOTAL_MS / steps).toLong())
        }
        fraction = 1f
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Estate check proportions.
        val a = Offset(w * 0.10f, h * 0.55f)
        val b = Offset(w * 0.40f, h * 0.86f)
        val c = Offset(w * 0.92f, h * 0.16f)
        val arm1 = (b - a).getDistance()
        val arm2 = (c - b).getDistance()
        val total = arm1 + arm2
        val draw = total * fraction
        val path = Path()
        path.moveTo(a.x, a.y)
        if (draw <= arm1) {
            val t = if (arm1 == 0f) 0f else draw / arm1
            path.lineTo(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        } else {
            path.lineTo(b.x, b.y)
            val t = if (arm2 == 0f) 0f else (draw - arm1) / arm2
            path.lineTo(b.x + (c.x - b.x) * t, b.y + (c.y - b.y) * t)
        }
        drawPath(path, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// M-08 — Hold-to-confirm progress: a linear fill that charges
// over [HOLD_FULL_MS] while held. Call sites drive `held` from a
// pointerInput detector; this returns the 0..1 charge to render.
// Releasing early decays the charge quickly — a hold is a
// commitment, letting go is an undo, and the fill shows both.
// ---------------------------------------------------------------------------
/** Charge level (0..1) for a hold-to-confirm surface. */
@Composable
fun rememberHoldProgress(held: Boolean, reduced: Boolean): Float {
    var charge by remember { mutableStateOf(0f) }
    LaunchedEffect(held) {
        if (held) {
            val stepMs = 16L
            val steps = (HOLD_FULL_MS / stepMs).toInt().coerceAtLeast(1)
            var i = (charge * steps).toInt()
            while (i < steps) {
                delay(stepMs)
                i++
                charge = i.toFloat() / steps
            }
            charge = 1f
        } else if (charge > 0f) {
            // Decay: 4x faster than the charge — releasing is easy.
            val stepMs = 16L
            var current = charge
            while (current > 0f) {
                delay(stepMs)
                current -= stepMs.toFloat() / (HOLD_FULL_MS / 4f)
                charge = current.coerceAtLeast(0f)
            }
            charge = 0f
        }
    }
    return charge
}
