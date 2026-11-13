package net.sentinelsafeguard.academyapp.data

// ---------------------------------------------------------------------------
// AppStore — the platform seam for the on-device secret store
// (desktop port, 2026-11-13). The Android implementation is
// SecureStore (Keystore-backed EncryptedSharedPreferences); the
// desktop implementation is DesktopStore (OS key storage where
// available — Secret Service / DPAPI — with a labeled encrypted
// file fallback). Every consumer (AcademyApi, AcademyViewModel)
// depends on this interface only, so the session/PIN/device-id
// semantics are identical on both platforms by construction:
// - the session token + its server-issued expiry;
// - the stable per-install device id (sa-bid, 128-bit, generated
//   once, sent only to the Academy);
// - the app-lock PIN verifier (salted SHA-256; the PIN itself is
//   never stored on any platform).
// ---------------------------------------------------------------------------
interface AppStore {
    fun saveSession(token: String, expiresAtEpochSec: Long)
    fun sessionToken(): String?
    fun sessionExpiresAt(): Long
    fun clearSession()
    fun deviceId(): String
    fun hasPin(): Boolean
    fun setPin(pin: String)
    fun checkPin(pin: String): Boolean
    fun clearPin()
    fun clearAll()
}
