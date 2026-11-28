package com.mckimquyen.reader.ui.component.commute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CommuteCastSheet had Vietnamese text typed into the code ("Đoạn x / y", "Đang phát...", "Đã dừng", the Deep Dive blurb), so every language showed Vietnamese there.
 * The texts are string resources now, and every shipped locale must define each of them.
 */
class CommuteCastStringsTest {
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
    private val source: String = main.resolve("java/com/mckimquyen/reader/ui/component/commute/CommuteCastSheet.kt").readText()

    private val keys = listOf(
        "commute_progress_segment",
        "commute_status_playing",
        "commute_status_stopped",
        "commute_deepdive_desc",
        "commute_listen_deepdive_vip",
    )

    private val vietnamese = "àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ"

    @Test fun theSheetHasNoVietnameseTextTypedIntoCode() {
        val literal = Regex("\"[^\"\n]*[$vietnamese][^\"\n]*\"", RegexOption.IGNORE_CASE)
        val hits = literal.findAll(source).map { it.value }.toList()
        assertTrue("hard-coded Vietnamese: $hits", hits.isEmpty())
    }

    @Test fun everyLocaleDefinesEveryCommuteCastLabel() {
        val files = main.resolve("res").listFiles { f -> f.name.startsWith("values") && f.resolve("strings.xml").exists() }!!
            .map { it.resolve("strings.xml") }
        assertTrue("expected the default file plus the locale files", files.size > 30)
        files.forEach { f ->
            val text = f.readText()
            keys.forEach { k -> assertTrue("${f.parentFile.name} lacks $k", "name=\"$k\"" in text) }
        }
    }

    @Test fun theProgressLabelKeepsBothNumbersInEveryLocale() {
        val re = Regex("""name="commute_progress_segment">([^<]*)<""")
        main.resolve("res").listFiles { f -> f.name.startsWith("values") && f.resolve("strings.xml").exists() }!!.forEach { d ->
            val v = re.find(d.resolve("strings.xml").readText())?.groupValues?.get(1) ?: return@forEach
            assertEquals("${d.name} must use %1\$d and %2\$d", listOf("%1\$d", "%2\$d"), Regex("%\\d\\$" + "d").findAll(v).map { it.value }.toList())
        }
    }
}
