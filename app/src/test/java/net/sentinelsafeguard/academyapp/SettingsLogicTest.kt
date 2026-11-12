package net.sentinelsafeguard.academyapp

import net.sentinelsafeguard.academyapp.data.AccountSettings
import net.sentinelsafeguard.academyapp.data.UserInfo
import net.sentinelsafeguard.academyapp.data.accountSettingsFromMap
import net.sentinelsafeguard.academyapp.data.capSettingsName
import net.sentinelsafeguard.academyapp.data.notificationsPayload
import net.sentinelsafeguard.academyapp.data.profileInputProblem
import net.sentinelsafeguard.academyapp.data.profilePayload
import net.sentinelsafeguard.academyapp.data.settingsOf
import net.sentinelsafeguard.academyapp.data.signupPasswordProblem
import net.sentinelsafeguard.academyapp.data.socialPayload
import net.sentinelsafeguard.academyapp.data.withSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G4 settings twins (SETTINGS-TWINS 2026-11-11) — the pure half:
 * request payload construction and read-back parsing, including
 * the refusal pre-flights. The load-bearing semantics are the
 * server's checkbox rules: a social save states BOTH switches
 * (an absent switch would read as OFF server-side, so the client
 * never relies on absence), and a read-back degrades missing
 * values to a truthful OFF/empty, never a phantom ON.
 */
class SettingsLogicTest {

    // ---------------- Profile payload + pre-flight ----------------

    @Test
    fun profilePayloadStripsAndCarriesBothNames() {
        val p = profilePayload("  Ada Lovelace  ", " A. Lovelace ")
        assertEquals(mapOf("display_name" to "Ada Lovelace", "cert_name" to "A. Lovelace"), p)
    }

    @Test
    fun profileProblemIsTheServerReasonForAnyBlankName() {
        assertEquals("names_required", profileInputProblem("", "Ada"))
        assertEquals("names_required", profileInputProblem("Ada", ""))
        assertEquals("names_required", profileInputProblem("   ", " \t "))
        assertNull(profileInputProblem("Ada", "Ada"))
    }

    @Test
    fun settingsNameCapMirrorsTheServerCap() {
        val exact = "x".repeat(60)
        assertEquals(exact, capSettingsName(exact))
        assertEquals(exact, capSettingsName("x".repeat(61)))
        assertEquals("Ada", capSettingsName("Ada"))
    }

    // DELTA AUDIT 2026-11-11: the server's transform is strip,
    // THEN cap at 60 CODE POINTS (production probes A/B/C). The
    // first two tests below fail against the shipped 1.1.2 code
    // (raw String.take(60), UTF-16 units) and pin the fix.

    @Test
    fun settingsNameCapCountsCodePointsAndNeverSeversASurrogatePair() {
        val emoji = "🎓"
        val sixtyPoints = "C".repeat(59) + emoji // 60 code points, 61 UTF-16 units
        assertEquals(sixtyPoints, capSettingsName(sixtyPoints))
        // One more character over: the cap drops the newcomer and
        // keeps the astral character whole — no lone surrogate.
        assertEquals(sixtyPoints, capSettingsName(sixtyPoints + "x"))
        assertEquals(sixtyPoints, profilePayload(sixtyPoints + "x", "Ada")["display_name"])
    }

    @Test
    fun settingsNameCapLandsAfterTheStripLikeTheServer() {
        // Padding does not eat the letter budget: probe B's input
        // keeps all 58 letters client-side too.
        val padded = "     " + "B".repeat(58)
        assertEquals("B".repeat(58), capSettingsName(padded).trim())
        assertEquals("B".repeat(58), profilePayload(padded, "Ada")["display_name"])
        // And the payload applies the server's transform outright:
        // a 61-letter name goes on the wire as the 60 the server
        // will store (probe A), not as a refusal or a longer body.
        assertEquals("A".repeat(60), profilePayload("A".repeat(61), "Ada")["display_name"])
        assertEquals("Ada", profilePayload("  Ada  ", "Ada")["display_name"])
    }

    // ---------------- Social / notifications payloads ----------------

    @Test
    fun socialPayloadAlwaysStatesBothSwitches() {
        // The checkbox rule pinned for all four combinations:
        // both keys present, values exactly as the user set them.
        for (r in listOf(false, true)) for (l in listOf(false, true)) {
            val p = socialPayload(r, l)
            assertEquals(setOf("registry", "leaderboard"), p.keys)
            assertEquals(r, p["registry"])
            assertEquals(l, p["leaderboard"])
        }
    }

    @Test
    fun notificationsPayloadStatesTheOneSwitch() {
        assertEquals(mapOf("cert_anniversary" to true), notificationsPayload(true))
        assertEquals(mapOf("cert_anniversary" to false), notificationsPayload(false))
    }

    // ---------------- Read-back parsing ----------------

    @Test
    fun settingsParseReadsTheFreshSettingsShape() {
        val s = accountSettingsFromMap(
            mapOf(
                "display_name" to "Ada Lovelace",
                "cert_name" to "A. Lovelace",
                "registry" to true,
                "leaderboard" to false,
                "notify_cert_anniversary" to true,
            ),
        )
        assertEquals(
            AccountSettings("Ada Lovelace", "A. Lovelace", true, false, true),
            s,
        )
    }

    @Test
    fun settingsParseDegradesMissingValuesToOffAndEmpty() {
        val s = accountSettingsFromMap(emptyMap())
        assertEquals(AccountSettings("", "", false, false, false), s)
        // A wrong-typed value is not a value: still OFF, not a crash.
        val odd = accountSettingsFromMap(mapOf("registry" to "yes", "display_name" to 42))
        assertFalse(odd.registry)
        assertEquals("", odd.displayName)
    }

    @Test
    fun meUserAndSettingsRoundTripThroughTheSameFiveFields() {
        val user = UserInfo(
            displayName = "Ada", certName = "A. Lovelace", email = "ada@example.com",
            promo = true, registry = true, leaderboard = false, notifyCertAnniversary = true,
        )
        val s = settingsOf(user)
        assertEquals(AccountSettings("Ada", "A. Lovelace", true, false, true), s)
        // Folding a confirmed save back changes only the five
        // settings fields; identity fields (email, promo) stand.
        val back = user.withSettings(s.copy(displayName = "Ada L.", leaderboard = true))
        assertEquals("Ada L.", back.displayName)
        assertTrue(back.leaderboard)
        assertEquals("ada@example.com", back.email)
        assertTrue(back.promo)
    }

    // ---------------- Password pre-flight ----------------

    @Test
    fun newPasswordPreflightMirrorsTheServerPolicy() {
        // The change twin's reasons are password_short /
        // password_long over the same 10..200 rule signup uses;
        // the client twin must agree at the boundaries.
        assertEquals("short", signupPasswordProblem("x".repeat(9)))
        assertNull(signupPasswordProblem("x".repeat(10)))
        assertNull(signupPasswordProblem("x".repeat(200)))
        assertEquals("long", signupPasswordProblem("x".repeat(201)))
    }

    @Test
    fun newPasswordPreflightCountsCodePointsLikeTheServer() {
        // DELTA AUDIT probes D/E: the server counts code points.
        // 8 letters + one astral character = 9 code points (10
        // UTF-16 units): the server refuses password_short, and
        // the pre-flight must say so too — the shipped UTF-16
        // count waved it through.
        assertEquals("short", signupPasswordProblem("D".repeat(8) + "🎓"))
        assertNull(signupPasswordProblem("D".repeat(9) + "🎓")) // 10 code points
        // 199 letters + one astral character = 200 code points
        // (201 UTF-16 units): the server ACCEPTS it — a client
        // "long" refusal here would refuse what the server takes.
        assertNull(signupPasswordProblem("E".repeat(199) + "🎓"))
        assertEquals("long", signupPasswordProblem("E".repeat(200) + "🎓")) // 201 code points
    }
}
