package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteMorningStringsTest {

    private val keys = listOf(
        "commute_consent_title", "commute_consent_message", "commute_listen_time_title", "commute_listen_time_desc",
        "commute_autoplay_title", "commute_autoplay_desc", "commute_autoplay_permission_denied", "commute_play_action",
        "commute_ready_to_play_title", "commute_ready_to_play_body",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun thereAreExactlyTenKeys() = assertEquals(10, keys.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun noStringHasAPlaceholder() {
        for (f in files) for (k in keys) {
            assertEquals("${f.parentFile.name} $k", 0, Regex("%\\d*\\$?[sd]").findAll(value(f, k)!!).count())
        }
    }

    @Test fun theConsentSaysWhatIsSent_andWhatIsNot_inEnglishAndVietnamese() {
        val en = value(File("src/main/res/values/strings.xml"), "commute_consent_message")!!
        val vi = value(File("src/main/res/values-vi/strings.xml"), "commute_consent_message")!!
        assertTrue(en.contains("250 characters") && en.contains("never sent") && en.contains("on your device"))
        assertTrue(vi.contains("250 ký tự") && vi.contains("không bao giờ được gửi") && vi.contains("trên máy"))
    }

    @Test fun theAutoPlayDescriptionIsHonest_itSaysAndroidMayBlockIt() {
        val en = value(File("src/main/res/values/strings.xml"), "commute_autoplay_desc")!!
        val vi = value(File("src/main/res/values-vi/strings.xml"), "commute_autoplay_desc")!!
        assertTrue(en.contains("blocks") && en.contains("Play button"))
        assertTrue(vi.contains("chặn") && vi.contains("nút Phát"))
    }

    @Test fun theListenTimeDescriptionSaysThirtyMinutes() {
        assertTrue(value(File("src/main/res/values/strings.xml"), "commute_listen_time_desc")!!.contains("30 minutes"))
        assertTrue(value(File("src/main/res/values-vi/strings.xml"), "commute_listen_time_desc")!!.contains("30 phút"))
    }

    @Test fun vietnameseIsReallyTranslated_notTheEnglishCopy() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        for (k in keys) assertTrue("vi must differ from en for $k", value(en, k) != value(vi, k))
    }
}
