package net.sentinelsafeguard.academyapp.data

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

// ---------------------------------------------------------------------------
// DesktopStore — the desktop AppStore (desktop port, 2026-11-13).
//
// Same semantics as Android's SecureStore (session token + expiry,
// stable 128-bit per-install sa-bid device id, salted SHA-256 PIN
// verifier — the PIN itself is never stored), behind three
// backends, chosen at runtime in this order:
//  1. Windows: DPAPI (CryptProtectData via JNA-platform) — values
//     are stored DPAPI-protected in a plain file; only the same
//     Windows user can decrypt them.
//  2. Linux: Secret Service via libsecret (JNA) when the library
//     is present AND a secret service actually answers a probe.
//  3. Fallback (either OS): AES-256-GCM under a random key kept in
//     a 0600-permissions key file in the per-user config dir.
//     This posture is LABELED: [osKeyStorageActive] is false and
//     the app's About section says, in the user's language, that
//     OS key storage is unavailable and credentials are encrypted
//     with a key stored in a permissions-locked file.
// ---------------------------------------------------------------------------

private interface KVBackend {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

/** libsecret-1 direct mapping (only the password calls we use). */
private interface LibSecret : Library {
    fun secret_password_store_sync(
        schema: Pointer?, flags: Int, collection: String?, label: String?,
        password: String, cancellable: Pointer?, error: PointerByReference?,
        vararg attributes: Any?,
    ): Boolean

    fun secret_password_lookup_sync(
        schema: Pointer?, cancellable: Pointer?, error: PointerByReference?,
        vararg attributes: Any?,
    ): String?

    fun secret_password_clear_sync(
        schema: Pointer?, cancellable: Pointer?, error: PointerByReference?,
        vararg attributes: Any?,
    ): Boolean

    fun secret_schema_new(
        name: String, flags: Int, vararg attributes: Any?,
    ): Pointer?
}

private object SecretServiceBackend : KVBackend {
    private const val APP_ATTR = "application"
    private const val APP_VAL = "net.sentinelsafeguard.academy.desktop"
    private const val KEY_ATTR = "key"
    private val lib: LibSecret? = runCatching {
        Native.load("secret-1", LibSecret::class.java)
    }.getOrNull()
    private val schema: Pointer? = runCatching {
        lib?.secret_schema_new(
            "net.sentinelsafeguard.academy.desktop.password", 0,
            APP_ATTR, 0, KEY_ATTR, 0, null,
        )
    }.getOrNull()

    /** True only when the library loads and a real secret service
     *  answers a lookup without error. Probed once. */
    val available: Boolean by lazy {
        val l = lib
        if (l == null || schema == null) {
            false
        } else {
            runCatching {
                val err = PointerByReference()
                l.secret_password_lookup_sync(
                    schema, null, err,
                    APP_ATTR, APP_VAL, KEY_ATTR, "__probe__", null,
                )
                err.value == null
            }.getOrDefault(false)
        }
    }

    override fun get(key: String): String? = runCatching {
        lib?.secret_password_lookup_sync(
            schema, null, null,
            APP_ATTR, APP_VAL, KEY_ATTR, key, null,
        )
    }.getOrNull()

    override fun put(key: String, value: String) {
        runCatching {
            lib?.secret_password_store_sync(
                schema, 0, null, "Sentinel Academy: $key", value, null, null,
                APP_ATTR, APP_VAL, KEY_ATTR, key, null,
            )
        }
    }

    override fun remove(key: String) {
        runCatching {
            lib?.secret_password_clear_sync(
                schema, null, null,
                APP_ATTR, APP_VAL, KEY_ATTR, key, null,
            )
        }
    }

    override fun clear() {
        // Secret Service has no per-schema bulk clear through the
        // simple password API; the store's own key list is cleared
        // one by one by DesktopStore.clearAll via remove().
    }
}

/** AES-GCM encrypted-file backend (the labeled fallback, and the
 *  value container under DPAPI on Windows). */
private class EncryptedFileBackend(
    private val dir: File,
    private val useDpapi: Boolean,
) : KVBackend {
    private val dataFile = File(dir, "store.enc")
    private val keyFile = File(dir, "store.key")

    private fun key(): SecretKeySpec {
        dir.mkdirs()
        if (!keyFile.exists()) {
            val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.writeText(Base64.getEncoder().encodeToString(k))
            runCatching {
                keyFile.setReadable(false, false); keyFile.setWritable(false, false)
                keyFile.setReadable(true, true); keyFile.setWritable(true, true)
            }
        }
        return SecretKeySpec(Base64.getDecoder().decode(keyFile.readText().trim()), "AES")
    }

    private fun protect(plain: ByteArray): ByteArray =
        if (useDpapi) {
            WinDpapi.protect(plain)
        } else {
            plain
        }

    private fun unprotect(data: ByteArray): ByteArray =
        if (useDpapi) {
            WinDpapi.unprotect(data)
        } else {
            data
        }

    @Synchronized
    private fun loadMap(): MutableMap<String, String> {
        if (!dataFile.exists()) return mutableMapOf()
        return runCatching {
            val raw = unprotect(Base64.getDecoder().decode(dataFile.readText().trim()))
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ct), Charsets.UTF_8))
            val map = mutableMapOf<String, String>()
            for (k in json.keys()) map[k] = json.getString(k)
            map
        }.getOrElse { mutableMapOf() }
    }

    @Synchronized
    private fun saveMap(map: Map<String, String>) {
        dir.mkdirs()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(JSONObject(map as Map<*, *>).toString().toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "store.enc.tmp")
        tmp.writeText(Base64.getEncoder().encodeToString(protect(iv + ct)))
        runCatching {
            tmp.setReadable(false, false); tmp.setReadable(true, true)
            tmp.setWritable(false, false); tmp.setWritable(true, true)
        }
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(tmp.readText()); tmp.delete()
        }
    }

    override fun get(key: String): String? = loadMap()[key]

    override fun put(key: String, value: String) {
        val m = loadMap(); m[key] = value; saveMap(m)
    }

    override fun remove(key: String) {
        val m = loadMap(); if (m.remove(key) != null) saveMap(m)
    }

    override fun clear() {
        runCatching { dataFile.delete() }
    }
}

/** Minimal DPAPI mapping on core JNA (CryptProtectData /
 *  CryptUnprotectData with CRYPTPROTECT_UI_FORBIDDEN). Only ever
 *  loaded on Windows. */
private object WinDpapi {
    class DataBlob : com.sun.jna.Structure() {
        @JvmField var cbData: Int = 0
        @JvmField var pbData: Pointer? = null
        override fun getFieldOrder(): List<String> = listOf("cbData", "pbData")
    }

    private interface Crypt32 : Library {
        fun CryptProtectData(
            dataIn: DataBlob, dataDescr: String?, optionalEntropy: Pointer?,
            reserved: Pointer?, promptStruct: Pointer?, flags: Int, dataOut: DataBlob,
        ): Boolean

        fun CryptUnprotectData(
            dataIn: DataBlob, dataDescr: PointerByReference?, optionalEntropy: Pointer?,
            reserved: Pointer?, promptStruct: Pointer?, flags: Int, dataOut: DataBlob,
        ): Boolean
    }

    private interface Kernel32 : Library {
        fun LocalFree(mem: Pointer?): Pointer?
    }

    private val crypt32: Crypt32 by lazy { Native.load("crypt32", Crypt32::class.java) }
    private val kernel32: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    private fun blobOf(bytes: ByteArray): DataBlob {
        val blob = DataBlob()
        val mem = com.sun.jna.Memory(bytes.size.toLong())
        mem.write(0, bytes, 0, bytes.size)
        blob.cbData = bytes.size
        blob.pbData = mem
        blob.write()
        return blob
    }

    private fun readAndFree(blob: DataBlob): ByteArray {
        val out = blob.pbData!!.getByteArray(0, blob.cbData)
        kernel32.LocalFree(blob.pbData)
        return out
    }

    fun protect(plain: ByteArray): ByteArray {
        val out = DataBlob()
        check(crypt32.CryptProtectData(blobOf(plain), null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
            "CryptProtectData failed"
        }
        out.read()
        return readAndFree(out)
    }

    fun unprotect(data: ByteArray): ByteArray {
        val out = DataBlob()
        check(crypt32.CryptUnprotectData(blobOf(data), null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
            "CryptUnprotectData failed"
        }
        out.read()
        return readAndFree(out)
    }
}

class DesktopStore : AppStore {

    companion object {
        /** Per-user config dir: %APPDATA% on Windows,
         *  $XDG_CONFIG_HOME or ~/.config elsewhere. */
        fun configDir(): File {
            val os = System.getProperty("os.name").lowercase()
            return if (os.contains("win")) {
                File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "SentinelAcademy")
            } else {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                File(if (!xdg.isNullOrBlank()) xdg else System.getProperty("user.home") + "/.config", "SentinelAcademy")
            }
        }

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_TOKEN = "session_token"
        private const val KEY_TOKEN_EXPIRES = "session_expires_at"
        private const val KEY_DEVICE_ID = "device_id"

        /** Process-wide posture flag for the About label: true
         *  when an OS key store (DPAPI / Secret Service) protects
         *  the credentials, false for the labeled file fallback. */
        @Volatile
        var osKeyStorageActive: Boolean = false
            private set
    }

    private val backend: KVBackend = run {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> {
                osKeyStorageActive = true
                EncryptedFileBackend(configDir(), useDpapi = true)
            }
            SecretServiceBackend.available -> {
                osKeyStorageActive = true
                SecretServiceBackend
            }
            else -> {
                osKeyStorageActive = false
                EncryptedFileBackend(configDir(), useDpapi = false)
            }
        }
    }

    /** Generic string slot — also used for desktop preferences
     *  (e.g. the reduce-motion setting). */
    fun pref(key: String): String? = backend.get("pref:$key")
    fun setPref(key: String, value: String) = backend.put("pref:$key", value)

    override fun saveSession(token: String, expiresAtEpochSec: Long) {
        backend.put(KEY_TOKEN, token)
        backend.put(KEY_TOKEN_EXPIRES, expiresAtEpochSec.toString())
    }

    override fun sessionToken(): String? = backend.get(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    override fun sessionExpiresAt(): Long = backend.get(KEY_TOKEN_EXPIRES)?.toLongOrNull() ?: 0L

    override fun clearSession() {
        backend.remove(KEY_TOKEN); backend.remove(KEY_TOKEN_EXPIRES)
    }

    override fun deviceId(): String {
        backend.get(KEY_DEVICE_ID)?.let { if (it.length == 32) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = bytes.joinToString("") { "%02x".format(it) }
        backend.put(KEY_DEVICE_ID, id)
        return id
    }

    override fun hasPin(): Boolean = backend.get(KEY_PIN_HASH) != null && backend.get(KEY_PIN_SALT) != null

    override fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        backend.put(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
        backend.put(KEY_PIN_HASH, Base64.getEncoder().encodeToString(hashPin(salt, pin)))
    }

    override fun checkPin(pin: String): Boolean {
        val saltB64 = backend.get(KEY_PIN_SALT) ?: return false
        val hashB64 = backend.get(KEY_PIN_HASH) ?: return false
        val salt = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(hashB64) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, hashPin(salt, pin))
    }

    override fun clearPin() {
        backend.remove(KEY_PIN_HASH); backend.remove(KEY_PIN_SALT)
    }

    override fun clearAll() {
        // Remove every known key explicitly (Secret Service has no
        // bulk clear), then clear the file backends wholesale.
        for (k in listOf(KEY_PIN_HASH, KEY_PIN_SALT, KEY_TOKEN, KEY_TOKEN_EXPIRES, KEY_DEVICE_ID)) {
            backend.remove(k)
        }
        backend.clear()
    }

    private fun hashPin(salt: ByteArray, pin: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }
}
