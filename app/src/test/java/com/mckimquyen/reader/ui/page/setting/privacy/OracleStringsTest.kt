package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The new keys must exist in every shipped locale, and the consent text must state the limits of what is sent.
class OracleStringsTest {

    private val keys = listOf(
        "oracle_badge_label", "oracle_badge_open", "oracle_sheet_title", "oracle_sheet_expires",
        "oracle_sheet_criteria_heading", "oracle_sheet_ai_label", "oracle_sheet_close",
        "oracle_consent_title", "oracle_consent_message",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun expiresHasExactlyOneStringPlaceholder_everywhere() {
        for (f in files) {
            val v = value(f, "oracle_sheet_expires")!!
            assertEquals(f.parentFile.name, 1, Regex("%1\\\$s").findAll(v).count())
            assertEquals(f.parentFile.name, 0, Regex("%\\d*d").findAll(v).count())
        }
    }

    @Test fun consentMessageStatesTheLimitsAndTheAiLabelStatesTheRisk() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        val enMsg = value(en, "oracle_consent_message")!!
        val viMsg = value(vi, "oracle_consent_message")!!
        assertTrue(enMsg.contains("500") && enMsg.contains("5 new articles"))
        assertTrue(viMsg.contains("500") && viMsg.contains("5 bài"))
        assertTrue(value(en, "oracle_sheet_ai_label")!!.contains("can be wrong"))
        assertTrue(value(vi, "oracle_sheet_ai_label")!!.contains("có thể sai"))
    }
}
