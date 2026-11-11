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
}
