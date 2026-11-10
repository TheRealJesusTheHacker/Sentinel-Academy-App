package net.sentinelsafeguard.academyapp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EN/ES string parity, programmatic (phase-2 scope item 7): the
 * two locale files must carry the SAME key set, and every shared
 * key must carry the SAME format placeholders (%1$s, %2$d, …) in
 * both — a translated string that drops a placeholder crashes at
 * runtime in exactly the locale nobody tested. This test is the
 * check, run in the suite like every other.
 */
class StringsParityTest {

    private fun resDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("res dir not found from ${File("").absolutePath}")
    }

    private fun parseStrings(file: File): Map<String, String> {
        val text = file.readText()
        val out = linkedMapOf<String, String>()
        val re = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        for (m in re.findAll(text)) out[m.groupValues[1]] = m.groupValues[2]
        return out
    }

    private fun placeholders(value: String): List<String> =
        Regex("""%\d+\$[sd]""").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun englishAndSpanishCarryTheSameKeys() {
        val en = parseStrings(File(resDir(), "values/strings.xml"))
        val es = parseStrings(File(resDir(), "values-es/strings.xml"))
        assertTrue("EN strings parsed (${en.size})", en.size > 50)
        assertEquals(
            "key sets must match exactly; EN-only=${en.keys - es.keys} ES-only=${es.keys - en.keys}",
            en.keys, es.keys,
        )
    }

    @Test
    fun placeholdersMatchAcrossLocales() {
        val en = parseStrings(File(resDir(), "values/strings.xml"))
        val es = parseStrings(File(resDir(), "values-es/strings.xml"))
        for ((key, enValue) in en) {
            val esValue = es[key] ?: continue
            assertEquals(
                "placeholder mismatch on $key",
                placeholders(enValue), placeholders(esValue),
            )
        }
    }
}
