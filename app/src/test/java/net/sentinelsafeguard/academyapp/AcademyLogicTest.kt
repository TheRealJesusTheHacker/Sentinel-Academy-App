package net.sentinelsafeguard.academyapp

import net.sentinelsafeguard.academyapp.data.certCodeWellFormed
import net.sentinelsafeguard.academyapp.data.courseEnterable
import net.sentinelsafeguard.academyapp.data.examRemainingSeconds
import net.sentinelsafeguard.academyapp.data.formatClock
import net.sentinelsafeguard.academyapp.data.normalizeCertCode
import net.sentinelsafeguard.academyapp.data.sessionExpired
import net.sentinelsafeguard.academyapp.data.signupPasswordProblem
import net.sentinelsafeguard.academyapp.data.trackProgress
import net.sentinelsafeguard.academyapp.data.weeklyGoalFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-2 pure rules (data/AcademyLogic.kt). Each group mirrors a
 * contract rule; the server remains the authority everywhere —
 * these pin the CLIENT-side presentation/pre-flight twins so they
 * cannot quietly drift from the contract's stated semantics.
 */
class AcademyLogicTest {

    // Certificate format gate (contract §6.2): ^SA[A-Z0-9]{6,18}$
    // on the normalized form; validation happens BEFORE a lookup
    // is spent, malformed ≡ never-issued in presentation.
    @Test
    fun certCodeNormalizationMatchesServerNorm() {
        assertEquals("SAABC123XYZ9", normalizeCertCode("sa-abc123-xyz9"))
        assertEquals("SAABC123", normalizeCertCode(" SA abc 123 "))
        assertEquals("", normalizeCertCode("---"))
    }

    @Test
    fun certCodeWellFormedGate() {
        assertTrue(certCodeWellFormed("SA-ABCDEF-123456")) // SA + 12 = 14 chars
        assertTrue(certCodeWellFormed("saabcdef")) // SA + 6, lowercase normalizes
        assertFalse(certCodeWellFormed("SA-12345")) // too short (< 6 after SA)
        assertFalse(certCodeWellFormed("XX-ABCDEF-123456")) // wrong prefix
        assertFalse(certCodeWellFormed("SA" + "A".repeat(19))) // too long (> 18)
        assertFalse(certCodeWellFormed(""))
        assertFalse(certCodeWellFormed("hello world"))
    }

    // Password policy (contract §1.1): 10..200.
    @Test
    fun signupPasswordPolicy() {
        assertEquals("short", signupPasswordProblem("123456789"))
        assertNull(signupPasswordProblem("1234567890"))
        assertNull(signupPasswordProblem("x".repeat(200)))
        assertEquals("long", signupPasswordProblem("x".repeat(201)))
    }

    // Session TTL (contract §1.2): fixed 7 days, expiry at the
    // boundary — at/past expires_at the re-login path is taken.
    @Test
    fun sessionExpiryBoundary() {
        val exp = 1_700_000_000L
        assertFalse(sessionExpired(exp, exp - 1))
        assertTrue(sessionExpired(exp, exp))
        assertTrue(sessionExpired(exp, exp + 3600))
        assertTrue(sessionExpired(0L, exp)) // no stored expiry = no session
    }

    // Streak card math: the fill fraction only; the goal framing
    // ("a guide, not a requirement") is copy, pinned in strings.
    @Test
    fun weeklyGoalFractionClamps() {
        assertEquals(0f, weeklyGoalFraction(0, 3), 0.001f)
        assertEquals(2f / 3f, weeklyGoalFraction(2, 3), 0.001f)
        assertEquals(1f, weeklyGoalFraction(3, 3), 0.001f)
        assertEquals(1f, weeklyGoalFraction(9, 3), 0.001f)
        assertEquals(0f, weeklyGoalFraction(1, 0), 0.001f)
    }

    // Exam timer (contract §4.3): countdown from limit_seconds,
    // never negative; clock format m:ss.
    @Test
    fun examTimerMath() {
        assertEquals(1800, examRemainingSeconds(1800, 0))
        assertEquals(1740, examRemainingSeconds(1800, 60))
        assertEquals(0, examRemainingSeconds(1800, 1800))
        assertEquals(0, examRemainingSeconds(1800, 9999))
        assertEquals("30:00", formatClock(1800))
        assertEquals("1:05", formatClock(65))
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-5))
    }

    // Road presentation twin (contract §4.2): only the server's
    // "open"/"complete" states are enterable.
    @Test
    fun courseEnterableReadsServerStateOnly() {
        assertTrue(courseEnterable("open"))
        assertTrue(courseEnterable("complete"))
        assertFalse(courseEnterable("locked"))
        assertFalse(courseEnterable(null))
        assertFalse(courseEnterable("anything-else"))
    }

    @Test
    fun trackProgressFractionClamps() {
        assertEquals(0f, trackProgress(0, 10), 0.001f)
        assertEquals(0.5f, trackProgress(5, 10), 0.001f)
        assertEquals(1f, trackProgress(10, 10), 0.001f)
        assertEquals(0f, trackProgress(3, 0), 0.001f)
    }
}
