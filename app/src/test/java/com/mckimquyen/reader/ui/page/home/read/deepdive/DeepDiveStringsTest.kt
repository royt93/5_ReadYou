package com.mckimquyen.reader.ui.page.home.read.deepdive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The new keys must exist in every shipped locale strings.xml, and the consent text must say the body is sent.
class DeepDiveStringsTest {

    private val res = File("src/main/res")
    private val keys = listOf(
        "deep_dive_menu", "deep_dive_title", "deep_dive_consent_message", "deep_dive_consent_agree", "deep_dive_consent_later",
        "deep_dive_declined_message", "deep_dive_open_privacy", "deep_dive_loading", "deep_dive_failed", "deep_dive_no_key",
        "deep_dive_nothing_to_analyze", "deep_dive_retry", "deep_dive_label", "deep_dive_section_context",
        "deep_dive_section_critical", "deep_dive_section_impact", "deep_dive_col_area", "deep_dive_col_short",
        "deep_dive_col_long", "deep_dive_export_pdf", "deep_dive_chooser_title", "deep_dive_export_error",
        "deep_dive_pdf_truncated", "deep_dive_close",
    )

    private val localeFiles = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }

    @Test fun thereAre38LocaleFiles() {
        assertEquals(38, localeFiles.size)
    }

    @Test fun everyLocaleDefinesEveryKeyExactlyOnce() {
        for (file in localeFiles) {
            val text = file.readText()
            for (key in keys) {
                val count = Regex("""<string name="$key"[ >]""").findAll(text).count()
                assertEquals("${file.parentFile.name} must define $key once", 1, count)
            }
        }
    }

    @Test fun englishConsentSaysTheTextIsSent() {
        val en = File(res, "values/strings.xml").readText()
        val consent = Regex("""<string name="deep_dive_consent_message">(.*?)</string>""").find(en)!!.groupValues[1]
        assertTrue(consent.contains("6,000"))
        assertTrue(consent.contains("text"))
        assertFalse(consent.contains("%"))
    }

    @Test fun vietnameseConsentSaysTheTextIsSent() {
        val vi = File(res, "values-vi/strings.xml").readText()
        val consent = Regex("""<string name="deep_dive_consent_message">(.*?)</string>""").find(vi)!!.groupValues[1]
        assertTrue(consent.contains("6.000"))
        assertTrue(consent.contains("nội dung"))
    }

    @Test fun noPercentFormatAppearsInTheNewKeys() {
        for (file in localeFiles) {
            val text = file.readText()
            for (key in keys) {
                val body = Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
                assertFalse("${file.parentFile.name}/$key must not contain a format placeholder", body.contains("%"))
            }
        }
    }
}
