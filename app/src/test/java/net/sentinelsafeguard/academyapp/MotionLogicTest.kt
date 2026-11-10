package net.sentinelsafeguard.academyapp

import net.sentinelsafeguard.academyapp.ui.reducedMotion
import net.sentinelsafeguard.academyapp.ui.shakeKeyframeOffsets
import net.sentinelsafeguard.academyapp.ui.shakeKeyframeTimes
import net.sentinelsafeguard.academyapp.ui.staggerCount
import net.sentinelsafeguard.academyapp.ui.staggerDelayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the motion layer's timing law (family standard). */
class MotionLogicTest {

    @Test
    fun reducedMotionOnlyAtZeroScale() {
        assertTrue(reducedMotion(0f))
        assertTrue(!reducedMotion(0.5f))
        assertTrue(!reducedMotion(1f))
    }

    @Test
    fun staggerCapsAtTenRows() {
        assertEquals(0, staggerCount(0))
        assertEquals(4, staggerCount(4))
        assertEquals(10, staggerCount(10))
        assertEquals(10, staggerCount(40))
        assertEquals(0, staggerDelayMs(0))
        assertEquals(70, staggerDelayMs(1))
        assertEquals(630, staggerDelayMs(9))
        assertEquals(630, staggerDelayMs(25))
        assertEquals(0, staggerDelayMs(-1))
    }

    @Test
    fun shakeDecaysAndSettlesAtRest() {
        val times = shakeKeyframeTimes()
        val offsets = shakeKeyframeOffsets()
        assertEquals(times.size, offsets.size)
        assertEquals(0f, times.first(), 0.001f)
        assertEquals(1f, times.last(), 0.001f)
        assertEquals(0f, offsets.first(), 0.001f)
        // Ends exactly at rest — a shake must never leave a surface
        // displaced.
        assertEquals(0f, offsets.last(), 0.001f)
        // Peaks decay: each successive extreme is smaller.
        val extremes = offsets.drop(1).filter { it != 0f }.map { kotlin.math.abs(it) }
        assertTrue(extremes.zipWithNext().all { (a, b) -> b < a })
    }

    @Test
    fun tickDrawsInTwoBeatsAndCompletes() {
        // Beat one ends at the 38% arm share, the pause holds it,
        // beat two completes the path.
        assertEquals(0f, net.sentinelsafeguard.academyapp.ui.tickDrawnFraction(0f), 0.001f)
        assertEquals(0.38f, net.sentinelsafeguard.academyapp.ui.tickDrawnFraction(0.38f), 0.001f)
        assertEquals(0.38f, net.sentinelsafeguard.academyapp.ui.tickDrawnFraction(0.5f), 0.001f)
        assertEquals(1f, net.sentinelsafeguard.academyapp.ui.tickDrawnFraction(1f), 0.001f)
        // Monotonic: the check never undraws.
        var prev = 0f
        var t = 0f
        while (t <= 1f) {
            val f = net.sentinelsafeguard.academyapp.ui.tickDrawnFraction(t)
            assertTrue(f >= prev)
            prev = f
            t += 0.05f
        }
    }
}
