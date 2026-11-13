package net.sentinelsafeguard.academyapp.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

// ---------------------------------------------------------------------------
// SecureStore — the family's on-device secret store.
//
// PHASE 2: the account half has arrived, shaped by the real
// contract (docs/API-CONTRACT.md, BUILD RECORD 2026-11-10): the
// Academy session token IS a server-side `sessions` row — SHA-256
// of the token at rest server-side, 7-day fixed TTL — and this
// store is where the raw token lives on the device: inside
// EncryptedSharedPreferences (AES-256-GCM values, AES-256-SIV
// keys) whose master key lives in the Android Keystore. The
// contract's client-storage expectation is stated in its §7:
// "the session token belongs in Android Keystore-backed storage,
// never in plaintext preferences or logs." This is that storage.
//
// Also stored here:
// - the token's server-issued expiry (epoch seconds), so the app
//   can take the clean re-login path at the 7-day boundary
//   instead of discovering it mid-lesson;
// - the stable per-install device id sent as the `sa-bid` cookie
//   on API traffic (contract §7 — HARD requirement: without it,
//   every app install shares the server's "no cookie" exam
//   identity and strangers' cooldowns and collision checks
//   couple). 128 bits of SecureRandom, generated once, hex.
//   It is an exam-integrity binding, NOT an analytics or tracking
//   identifier: it is sent only to the Academy itself, only in
//   the slot the server's own web client uses.
//
// The app-lock PIN verifier (phase-1 scaffolding, now wired):
// - The PIN itself is never stored. What is stored is a SHA-256
//   hash of (salt || PIN), the salt 16 random bytes per PIN,
//   compared in constant time (MessageDigest.isEqual).
// - The lock protects the stored session on this device — it is
//   a device gate, not an account credential, and the app's copy
//   says exactly that.
//
// Backup and device transfer EXCLUDE this store (see
// res/xml/backup_rules.xml + data_extraction_rules.xml): token,
// device id, and verifier are device-bound by design.
// ---------------------------------------------------------------------------
class SecureStore(context: Context) : AppStore {

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

    // ---- Academy session (the token IS a sessions row) ----

    override fun saveSession(token: String, expiresAtEpochSec: Long) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRES, expiresAtEpochSec)
            .apply()
    }

    override fun sessionToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Server-issued expiry of the stored token (epoch seconds),
     *  or 0 when no session is stored. */
    override fun sessionExpiresAt(): Long = prefs.getLong(KEY_TOKEN_EXPIRES, 0L)

    override fun clearSession() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_EXPIRES).apply()
    }

    // ---- Per-install device id (the sa-bid cookie value) ----

    /** The stable per-install device id, generated once on first
     *  ask and persisted with the install. 32 lowercase hex chars
     *  (128 bits), the same shape the server's web client carries
     *  in its `sa-bid` cookie (secrets.token_hex(16)). */
    override fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { if (it.length == 32) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    // ---- App-lock PIN (verifier only; the PIN is never stored) ----

    override fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)

    /** Sets (or replaces) the app-lock PIN, storing only verifier
     *  material: a fresh random salt and the salted SHA-256. */
    override fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hashPin(salt, pin), Base64.NO_WRAP))
            .apply()
    }

    /** Constant-time verifier check; false on any missing or
     *  malformed stored material. */
    override fun checkPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, hashPin(salt, pin))
    }

    override fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).apply()
    }

    /** Full wipe of the store — session, device id, and lock
     *  verifier. (A fresh device id is minted on next ask; the
     *  server treats it as a new browser, which is the honest
     *  consequence of wiping the install's identity.) */
    override fun clearAll() {
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
        private const val KEY_TOKEN = "session_token"
        private const val KEY_TOKEN_EXPIRES = "session_expires_at"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
