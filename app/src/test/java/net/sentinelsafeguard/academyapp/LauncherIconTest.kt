package net.sentinelsafeguard.academyapp

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Sentinel Academy App's icon package.
 *
 * OWNER DECISION, 2026-11-10: the Academy App gets a mark of its
 * OWN (the Command and Mail Apps have theirs; the Academy was not
 * to reuse one). Three concepts were generated and presented;
 * Skyler picked **concept C — the academy hall**: a triangular
 * pediment over two columns with an open book between them,
 * emerald line work on near-black. The master artwork is
 * preserved at `brand/sentinel-academy-icon-master.png`; every
 * shipped icon is a mechanical derivation of it (decode → center
 * crop to the artwork's tile interior → resize/inset/silhouette)
 * by tools/generate_academy_icon.py — never redrawn, never
 * re-composed. The mark follows the family scoping: it is the
 * Academy PRODUCT icon (launcher + notification) only; in-app
 * company surfaces use the real Sentinel Safeguard logo,
 * byte-pinned by the last test below.
 *
 * These byte pins make any silent artwork swap — regenerated,
 * recompressed, or "improved" by a future session — fail the
 * suite until a human re-pins them deliberately, with the new
 * hashes recorded in the program record.
 */
class LauncherIconTest {

    private fun resDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("res dir not found from ${File("").absolutePath}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun adaptiveIconHasNoDrawnVectorForeground() {
        val xml = File(resDir(), "mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue("adaptive icon xml missing", xml.isFile)
        val text = xml.readText()
        assertFalse(
            "the adaptive foreground must be the approved artwork, " +
                "never a drawn vector",
            text.contains("<vector"),
        )
        assertTrue(text.contains("@mipmap/ic_launcher_foreground"))
        assertTrue(text.contains("@color/ic_launcher_background"))
    }

    @Test
    fun launcherPngsMatchApprovedArtwork() {
        val expected = mapOf(
            "mipmap-mdpi/ic_launcher.png" to
                "b8c7c7e75b5b174068c2bba9f8a3c440c95734f98004ef1e73418097d983f5bf",
            "mipmap-mdpi/ic_launcher_foreground.png" to
                "f10beb7665b8dd60e3a1226d0130f06a6ddfff60e34c9fd1062def9ce31f2fd2",
            "mipmap-hdpi/ic_launcher.png" to
                "fd97238f32369239170d624a11b502a320963936d22d934ca1b161c0e9d48d76",
            "mipmap-hdpi/ic_launcher_foreground.png" to
                "44cdeac82798688a48d1fe29a2af05e9c940f994efb83a8b4b697961d0276030",
            "mipmap-xhdpi/ic_launcher.png" to
                "025a332ab204cd3534997b7fb27c31bca81f5e53c9266d8c822f64045fc3361b",
            "mipmap-xhdpi/ic_launcher_foreground.png" to
                "e0e382a41b9cd45b26aa61fc96dccfc663b290d2d8761b12e4d62d2a1024bcba",
            "mipmap-xxhdpi/ic_launcher.png" to
                "bec4ea7c574b3956e47b6b709d69be6ce0e86160113dfe9df60009737dcf98e3",
            "mipmap-xxhdpi/ic_launcher_foreground.png" to
                "06d6a6f7e186a80f1ef7148baf19dd2e3f0f1b07edfdd89f4f66758bec60a094",
            "mipmap-xxxhdpi/ic_launcher.png" to
                "fe93d0ee04985fe7c410393f8aa08ff2697548b2ca6820333ffbacb8b4302fe9",
            "mipmap-xxxhdpi/ic_launcher_foreground.png" to
                "bd84ebc95ad5168e03f34a88f93e432f26418c00548a1beaae3da925a4b33028",
        )
        for ((path, hash) in expected) {
            val file = File(resDir(), path)
            assertTrue("$path missing", file.isFile)
            assertEquals("$path changed", hash, sha256(file))
        }
    }

    @Test
    fun notificationSilhouetteMatchesApprovedDerivation() {
        val file = File(resDir(), "drawable-nodpi/ic_stat_academy.png")
        assertTrue("notification silhouette missing", file.isFile)
        assertEquals(
            "52a3032cbb3b30a991785dc95fd78322fb7544a5756c3175b03e1464f144a42e",
            sha256(file),
        )
    }

    @Test
    fun inAppLogoIsStillTheRealSentinelLogo() {
        val file = File(resDir(), "drawable-nodpi/sentinel_logo.jpg")
        assertTrue("in-app logo missing", file.isFile)
        assertEquals(
            "the company surfaces keep the owner's real logo",
            "8d373bf2755f1b9f80d6d9498d7b08924501d368e854635e7091639fa8821910",
            sha256(file),
        )
    }
}
