package net.sentinelsafeguard.academyapp.data

import java.io.File

// ---------------------------------------------------------------------------
// CatalogCache — the on-device copy of the ANONYMOUS catalog and
// its strong validator (CATALOG-ETAG 2026-11-11).
//
// What lives here is public content only: the anonymous catalog
// body (the same facts the service publishes as public HTML) and
// the ETag the server issued for those exact bytes. The
// AUTHENTICATED catalog — the caller's enrolled/state/done
// overlay — is never stored: the server sends it no-store, and
// keeping it on disk would pin one learner's progress into the
// next account's reach (the audit's session-leak rule, D9).
//
// Integrity: a stored entry is trusted only while
// etagMatchesBody(etag, body) holds — the ETag IS the SHA-256 of
// the body, so a torn write, a truncated file, or tampering
// fails the check and the pair is dropped, never served. Writes
// land body-first via a temp file + rename, with the ETag file
// as the commit marker: a crash mid-store leaves at worst a
// body whose validator is absent or stale, which the integrity
// check refuses.
//
// The ETag doubles as the entry's content-version key: a 304
// from the server confirms the stored version is current, and
// any 200 with a new ETag replaces the entry wholesale.
// ---------------------------------------------------------------------------
class CatalogCache(private val dir: File) {

    data class Entry(val etag: String, val body: String)

    private val bodyFile: File get() = File(dir, BODY_NAME)
    private val etagFile: File get() = File(dir, ETAG_NAME)

    /** The stored entry, or null when absent or failed integrity
     *  (a failed pair is deleted, so the next fetch starts clean
     *  and unconditional). */
    fun load(): Entry? {
        val etag = runCatching { etagFile.readText().trim() }.getOrNull()
        val body = runCatching { bodyFile.readText() }.getOrNull()
        if (etag.isNullOrBlank() || body == null) return null
        if (!etagMatchesBody(etag, body.toByteArray(Charsets.UTF_8))) {
            clear()
            return null
        }
        return Entry(etag, body)
    }

    /** Stores a server-issued (etag, body) pair. The caller has
     *  already parsed the body; the pair is only trusted on later
     *  loads that re-verify the hash. */
    fun store(etag: String, body: String) {
        dir.mkdirs()
        writeAtomic(bodyFile, body)
        writeAtomic(etagFile, etag)
    }

    fun clear() {
        runCatching { bodyFile.delete() }
        runCatching { etagFile.delete() }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            // Same-directory rename is atomic on the filesystems
            // Android ships; if one ever refuses, fall back to a
            // direct write rather than losing the entry.
            target.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        const val BODY_NAME = "catalog.json"
        const val ETAG_NAME = "catalog.etag"
    }
}
