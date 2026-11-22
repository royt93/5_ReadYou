package com.mckimquyen.reader.ui.page.setting.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The restore confirmation is shown in every language the app ships. The translations are machine made, so this only pins what a test can
 * prove: every locale has all three strings, none is blank, none is a copy of the English text, and none can break Android's string rules
 * (an unescaped apostrophe, a stray format placeholder, a leading `@` or `?` that Android reads as a resource reference).
 * It cannot judge the quality of the wording.
 */
class WebDavRestoreConfirmStringsTest {
    private val res = File("src/main/res")
    private val keys = listOf("webdav_restore_confirm_title", "webdav_restore_confirm_message", "webdav_restore_confirm_action")

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private val english by lazy { strings(File(res, "values/strings.xml")) }

    private val locales: Map<String, Map<String, String>> by lazy {
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.exists() }
            .associate { it.parentFile.name to strings(it) }
    }

    @Test
    fun theEnglishOriginalsExist() {
        keys.forEach { assertTrue("missing English $it", !english[it].isNullOrBlank()) }
    }

    @Test
    fun thereAreLocalesToCheck() {
        assertTrue("found only ${locales.size} locale folders", locales.size >= 30)
    }

    @Test
    fun everyLocaleHasAllThreeStrings() {
        val missing = locales.flatMap { (dir, map) -> keys.filter { map[it].isNullOrBlank() }.map { "$dir:$it" } }
        assertEquals("locales without the string", emptyList<String>(), missing)
    }

    @Test
    fun noLocaleJustCopiesTheEnglishText() {
        val copies = locales.flatMap { (dir, map) -> keys.filter { map[it] == english[it] }.map { "$dir:$it" } }
        assertEquals("untranslated copies", emptyList<String>(), copies)
    }

    @Test
    fun noTranslationBreaksAndroidStringRules() {
        val bad = locales.flatMap { (dir, map) ->
            keys.mapNotNull { key ->
                val text = map[key].orEmpty()
                val problem = when {
                    Regex("""(?<!\\)'""").containsMatchIn(text) -> "unescaped apostrophe"
                    text.contains('%') -> "format placeholder (the English text has none)"
                    text.startsWith("@") || text.startsWith("?") -> "starts with a resource-reference character"
                    text != text.trim() -> "leading or trailing space"
                    else -> null
                }
                problem?.let { "$dir:$key: $it" }
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun theEnglishTextHasNoPlaceholdersSoNoTranslationMayAddOne() {
        keys.forEach { assertTrue("$it has a %", !english[it].orEmpty().contains('%')) }
    }
}
