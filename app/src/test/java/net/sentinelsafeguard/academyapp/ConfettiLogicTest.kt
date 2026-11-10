package net.sentinelsafeguard.academyapp

import net.sentinelsafeguard.academyapp.data.CONFETTI_GRAVITY
import net.sentinelsafeguard.academyapp.data.CONFETTI_MAX_PIECES
import net.sentinelsafeguard.academyapp.data.confettiBurst
import net.sentinelsafeguard.academyapp.data.confettiStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The certificate-celebration physics (owner confetti ruling
 * 2026-11-08): ≤18 pieces hard-capped in the plan itself, real
 * gravity (velocity grows downward under stepping), palette
 * indices bounded to the estate set. The composable half
 * (ui/Confetti.kt) additionally renders NOTHING under
 * reduced-motion and never appears on the issue surface.
 */
class ConfettiLogicTest {

    @Test
    fun burstNeverExceedsEighteenPieces() {
        assertEquals(CONFETTI_MAX_PIECES, confettiBurst(100f, 100f, 500, Random(7)).size)
        assertEquals(18, confettiBurst(100f, 100f, 18, Random(7)).size)
        assertEquals(5, confettiBurst(100f, 100f, 5, Random(7)).size)
        assertEquals(0, confettiBurst(100f, 100f, 0, Random(7)).size)
    }

    @Test
    fun burstLaunchesUpwardFromOrigin() {
        val pieces = confettiBurst(200f, 50f, 18, Random(42))
        assertTrue(pieces.all { it.x == 200f && it.y == 50f })
        assertTrue("every piece launches upward", pieces.all { it.vy < 0f })
        assertTrue(pieces.all { it.colorIndex in 0 until net.sentinelsafeguard.academyapp.data.CONFETTI_COLOR_COUNT })
    }

    @Test
    fun gravityPullsPiecesDownOverTime() {
        val p0 = confettiBurst(0f, 0f, 1, Random(3)).single()
        var p = p0
        repeat(120) { p = confettiStep(p, 1f / 60f) } // two seconds
        assertTrue("vy must end positive (falling) under gravity", p.vy > 0f)
        assertTrue("piece must have fallen below its start", p.y > p0.y)
        // Sanity on the constant itself: real gravity, not a drift.
        assertTrue(CONFETTI_GRAVITY > 900f)
    }

    @Test
    fun stepAdvancesRotation() {
        val p0 = confettiBurst(0f, 0f, 1, Random(11)).single()
        val p1 = confettiStep(p0, 0.5f)
        assertEquals(p0.angleDeg + p0.spinDegPerSec * 0.5f, p1.angleDeg, 0.01f)
    }
}
