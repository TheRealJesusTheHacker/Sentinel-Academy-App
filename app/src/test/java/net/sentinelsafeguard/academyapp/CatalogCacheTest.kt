package net.sentinelsafeguard.academyapp

import net.sentinelsafeguard.academyapp.data.CatalogCache
import net.sentinelsafeguard.academyapp.data.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * CatalogCache (CATALOG-ETAG 2026-11-11): the anonymous
 * catalog's on-device copy is trusted only while its bytes hash
 * to its stored ETag — torn, truncated, or tampered pairs are
 * dropped, never served.
 */
class CatalogCacheTest {

    private fun tempCache(): Pair<CatalogCache, File> {
        val dir = Files.createTempDirectory("catalog-cache-test").toFile()
        return CatalogCache(dir) to dir
    }

    private fun etagFor(body: String): String =
        "\"" + sha256Hex(body.toByteArray(Charsets.UTF_8)) + "\""

    @Test
    fun roundTripStoresAndLoads() {
        val (cache, _) = tempCache()
        val body = "{\"tracks\":[{\"slug\":\"best-practice\"}]}"
        cache.store(etagFor(body), body)
        val entry = cache.load()
        assertEquals(etagFor(body), entry?.etag)
        assertEquals(body, entry?.body)
    }

    @Test
    fun emptyCacheLoadsNull() {
        val (cache, _) = tempCache()
        assertNull(cache.load())
    }

    @Test
    fun overwriteReplacesTheEntryWholesale() {
        val (cache, _) = tempCache()
        val first = "{\"tracks\":[]}"
        val second = "{\"tracks\":[{\"slug\":\"expert\"}]}"
        cache.store(etagFor(first), first)
        cache.store(etagFor(second), second)
        val entry = cache.load()
        assertEquals(etagFor(second), entry?.etag)
        assertEquals(second, entry?.body)
    }

    @Test
    fun tamperedBodyFailsIntegrityAndIsDropped() {
        val (cache, dir) = tempCache()
        val body = "{\"tracks\":[]}"
        cache.store(etagFor(body), body)
        // Corrupt the stored body after the fact.
        File(dir, CatalogCache.BODY_NAME).writeText("{\"tracks\":[{\"slug\":\"forged\"}]}")
        assertNull(cache.load())
        // The pair is gone, not served later either.
        assertTrue(!File(dir, CatalogCache.BODY_NAME).exists())
        assertTrue(!File(dir, CatalogCache.ETAG_NAME).exists())
    }

    @Test
    fun tornPairLoadsNull() {
        val (cache, dir) = tempCache()
        val body = "{\"tracks\":[]}"
        cache.store(etagFor(body), body)
        // Simulate a torn write: the validator never landed.
        File(dir, CatalogCache.ETAG_NAME).delete()
        assertNull(cache.load())
    }

    @Test
    fun mismatchedEtagLoadsNull() {
        val (cache, _) = tempCache()
        cache.store(etagFor("{\"other\":true}"), "{\"tracks\":[]}")
        assertNull(cache.load())
    }
}
