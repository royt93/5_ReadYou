package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleDueStringsTest {

    private val keys = listOf(
        "oracle_due_channel_name", "oracle_due_channel_desc", "oracle_due_notification_title",
        "oracle_due_notification_title_many", "oracle_due_notification_body_many", "oracle_due_evidence_title",
        "oracle_due_evidence_none", "oracle_due_evidence_hint",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun thereAreExactlyEightKeys() = assertEquals(8, keys.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun onlyTheManyBodyHasAPlaceholder_andItIsExactlyOneOrdinalString() {
        for (f in files) for (k in keys) {
            val v = value(f, k)!!
            val expected = if (k == "oracle_due_notification_body_many") 1 else 0
            assertEquals("${f.parentFile.name} $k placeholders", expected, Regex("%\\d+\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k must not use %d or %s", 0, Regex("%\\d*d|%s").findAll(v).count())
        }
    }

    @Test fun englishAndVietnameseSayItIsAHint_andThatTheUserDecides() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        assertTrue(value(en, "oracle_due_evidence_hint")!!.contains("may be wrong") && value(en, "oracle_due_evidence_hint")!!.contains("You decide"))
        assertTrue(value(vi, "oracle_due_evidence_hint")!!.contains("chưa chắc đúng") && value(vi, "oracle_due_evidence_hint")!!.contains("tự quyết định"))
    }

    @Test fun noStringPromisesAnAutomaticVerdict() {
        for (f in files) for (k in keys) {
            val v = value(f, k)!!
            assertFalse("${f.parentFile.name} $k must not claim an automatic result", v.contains("automatically", ignoreCase = true) && v.contains("won", ignoreCase = true))
        }
    }
}
