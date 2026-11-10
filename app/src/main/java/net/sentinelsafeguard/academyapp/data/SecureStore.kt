package net.sentinelsafeguard.academyapp.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

// ---------------------------------------------------------------------------
// SecureStore — the family's on-device secret store, phase-1
// scaffolding.
//
// PHASE-1 STATUS, stated plainly: this class is PRESENT but
// deliberately NOT WIRED to any surface. The foundation build has
// no sign-in and no lock screen — there is nothing to protect yet,
// and a lock gate guarding an empty app would be theatre. Phase 2
// wires the lock (idle re-lock + PIN) on top of this store when
// the Academy account and its data arrive.
//
// What is here is the half that does not depend on the API
// contract — the app-lock PIN verifier, ported from the family's
// audited store:
// - The PIN itself is never stored. What is stored is a SHA-256
//   hash of (salt || PIN), the salt 16 random bytes per PIN,
//   inside EncryptedSharedPreferences (AES-256-GCM values,
//   AES-256-SIV keys) whose master key lives in the Android
//   Keystore. Verification compares in constant time
//   (MessageDigest.isEqual) — a mistyped PIN and a tampered store
//   are indistinguishable from outside, on purpose.
// - The account-secret half of the family store (API credentials
//   / tokens) arrives in phase 2, shaped by the real contract in
//   docs/API-CONTRACT.md — it is not guessed here.
// - Backup and device transfer EXCLUDE this store (see
//   res/xml/backup_rules.xml + data_extraction_rules.xml): the
//   verifier is device-bound by design.
// ---------------------------------------------------------------------------
class SecureStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ---- App-lock PIN (verifier only; the PIN is never stored) ----

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)

    /** Sets (or replaces) the app-lock PIN, storing only verifier
     *  material: a fresh random salt and the salted SHA-256. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hashPin(salt, pin), Base64.NO_WRAP))
            .apply()
    }

    /** Constant-time verifier check; false on any missing or
     *  malformed stored material. */
    fun checkPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, hashPin(salt, pin))
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).apply()
    }

    /** Full wipe of the store — lock verifier and, in phase 2,
     *  whatever credential material the contract brings. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun hashPin(salt: ByteArray, pin: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val FILE_NAME = "sentinel_academy_secure_store"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
    }
}
