package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleBetStringsTest {

    private val keys = listOf(
        "oracle_bet_balance", "oracle_bet_pick_side", "oracle_bet_yes", "oracle_bet_no", "oracle_bet_pick_stake",
        "oracle_bet_stake_option", "oracle_bet_place", "oracle_bet_place_summary", "oracle_bet_out_of_points",
        "oracle_bet_open", "oracle_bet_open_hint", "oracle_bet_awaiting_title", "oracle_bet_outcome_unclear",
        "oracle_bet_awaiting_hint", "oracle_bet_settled_won", "oracle_bet_settled_lost", "oracle_bet_settled_refunded",
        "oracle_bet_closed_no_bet", "oracle_bet_virtual_note", "oracle_bet_error",
    )
    private val placeholders = mapOf(
        "oracle_bet_balance" to 1, "oracle_bet_stake_option" to 1, "oracle_bet_place_summary" to 2, "oracle_bet_open" to 2,
        "oracle_bet_awaiting_hint" to 2, "oracle_bet_settled_won" to 1, "oracle_bet_settled_lost" to 1, "oracle_bet_settled_refunded" to 1,
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

    @Test fun placeholdersAreOrdinalStringsWithTheRightCount_everywhere() {
        for (f in files) for ((k, n) in placeholders) {
            val v = value(f, k)!!
            for (i in 1..n) assertEquals("${f.parentFile.name} $k %$i\$s", 1, Regex("%$i\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k has extra placeholders", n, Regex("%\\d+\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k must not use %d", 0, Regex("%\\d*d").findAll(v).count())
        }
    }

    @Test fun englishAndVietnameseStateTheRulesInNumbers() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        listOf(en, vi).forEach {
            val v = value(it, "oracle_bet_out_of_points")!!
            assertTrue(it.parentFile.name, v.contains("100") && (v.contains("2,000") || v.contains("2.000")))
        }
        assertTrue(value(en, "oracle_bet_virtual_note")!!.contains("Virtual points"))
        assertTrue(value(vi, "oracle_bet_virtual_note")!!.contains("Điểm ảo"))
    }

    @Test fun theAiLabelNoLongerSaysBettingIsUnavailable_everywhere() {
        for (f in files) {
            val v = value(f, "oracle_sheet_ai_label")!!
            assertFalse("${f.parentFile.name} still says you cannot bet", v.contains("cannot bet") || v.contains("Chưa tham gia cược"))
        }
        assertTrue(value(File("src/main/res/values/strings.xml"), "oracle_sheet_ai_label")!!.contains("can be wrong"))
        assertTrue(value(File("src/main/res/values-vi/strings.xml"), "oracle_sheet_ai_label")!!.contains("có thể sai"))
    }
}
