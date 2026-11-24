package com.mckimquyen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pre-release audit found 40 strings no code referenced, one of them a nonsense sentence ("1 Mark posts older than days as read") copied into 37 locales, and
 * 12 locales where over 700 of 1139 strings were still the English text. Dead keys cost translation work for text nobody sees, so they are rejected here, and every
 * translation must keep the placeholders of the source string.
 */
class StringResourcesHealthTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
    private val nameRegex = Regex("""<string\s[^>]*?\bname="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun strings(file: File): Map<String, String> =
        nameRegex.findAll(file.readText())
            .filter { "translatable=\"false\"" !in it.groupValues[2] }
            .associate { it.groupValues[1] to it.groupValues[3] }

    private val base = strings(File(root, "res/values/strings.xml"))
    private val locales = File(root, "res").listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").exists() }.orEmpty().sortedBy { it.name }

    @Test
    fun everyDefaultStringIsReferencedByCodeOrResources() {
        val referenced = HashSet<String>()
        val ref = Regex("""R\.string\.([A-Za-z0-9_]+)|@string/([A-Za-z0-9_]+)""")
        root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.forEach { f ->
            ref.findAll(f.readText()).forEach { referenced += it.groupValues[1].ifEmpty { it.groupValues[2] } }
        }
        val dead = base.keys.filter { it.replace('.', '_') !in referenced }
        assertEquals("strings nobody references (delete them from every locale): $dead", emptyList<String>(), dead)
    }

    @Test
    fun noLocaleDefinesAKeyThatTheDefaultFileLacks() {
        // Language names are translatable="false" in the default file yet Vietnamese localises them on purpose, so count every default key, flagged or not.
        val allDefault = nameRegex.findAll(File(root, "res/values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        val offenders = locales.flatMap { l -> (strings(File(l, "strings.xml")).keys - allDefault).map { "${l.name}:$it" } }
        assertEquals("keys only in a translation: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun everyTranslationKeepsThePlaceholdersOfTheDefaultString() {
        val ph = Regex("""%(?:\d+\$)?[sdfx]""")
        val bad = ArrayList<String>()
        locales.forEach { l ->
            strings(File(l, "strings.xml")).forEach { (k, v) ->
                val b = base[k] ?: return@forEach
                // A translation may repeat a placeholder (Romanian unsubscribe_tips uses %1$s twice, which Android allows); it may not add or drop one.
                if (ph.findAll(b).map { it.value }.toSet() != ph.findAll(v).map { it.value }.toSet()) bad += "${l.name}:$k"
            }
        }
        assertTrue("placeholders changed by a translation: $bad", bad.isEmpty())
    }

    @Test
    fun noTranslationIsEmpty() {
        val empty = locales.flatMap { l -> strings(File(l, "strings.xml")).filter { it.value.isBlank() }.keys.map { "${l.name}:$it" } }
        assertEquals("empty translations: $empty", emptyList<String>(), empty)
    }
}
