package com.mckimquyen.reader.domain.model.bilingual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BilingualLanguagesTest {

    @Test
    fun appLocalesMlKitSupports_mapToTheirCode() {
        assertEquals("vi", BilingualLanguages.fromLocale(Locale("vi", "VN")))
        assertEquals("ja", BilingualLanguages.fromLocale(Locale.JAPAN))
        assertEquals("fr", BilingualLanguages.fromLocale(Locale.FRANCE))
        assertEquals("zh", BilingualLanguages.fromLocale(Locale.SIMPLIFIED_CHINESE))
        assertEquals("zh", BilingualLanguages.fromLocale(Locale.TRADITIONAL_CHINESE))
    }

    @Test
    fun legacyAndroidLanguageCodes_areNormalised() {
        assertEquals("he", BilingualLanguages.fromLocale(Locale("iw")))
        assertEquals("id", BilingualLanguages.fromLocale(Locale("in", "ID")))
        assertEquals("no", BilingualLanguages.fromLocale(Locale("nb", "NO")))
        assertEquals("no", BilingualLanguages.fromLocale(Locale("nn")))
    }

    @Test
    fun appLocalesMlKitCannotTranslate_areUnsupported() {
        for (code in listOf("az", "eu", "lzh", "ml", "my", "sr")) {
            assertNull("$code must not resolve", BilingualLanguages.fromLocale(Locale(code)))
            assertFalse(BilingualLanguages.isSupported(code))
        }
    }

    @Test
    fun resolveTarget_explicitChoiceWinsOverTheAppLocale() {
        assertEquals("de", BilingualLanguages.resolveTarget(choice = "de", appLocale = Locale("vi")))
    }

    @Test
    fun resolveTarget_autoFollowsTheAppLocale() {
        assertEquals("vi", BilingualLanguages.resolveTarget(BilingualLanguages.AUTO, Locale("vi")))
    }

    @Test
    fun resolveTarget_autoWithUnsupportedLocale_isNull_soTheUiCanAskForAChoice() {
        assertNull(BilingualLanguages.resolveTarget(BilingualLanguages.AUTO, Locale("my")))
    }

    @Test
    fun resolveTarget_aStoredCodeThatIsNoLongerSupported_fallsBackToAuto() {
        assertEquals("vi", BilingualLanguages.resolveTarget("xx", Locale("vi")))
        assertNull(BilingualLanguages.resolveTarget("xx", Locale("my")))
    }

    @Test
    fun supportedList_isSortedUniqueAndContainsEnglish() {
        val codes = BilingualLanguages.supported
        assertEquals(codes.toSet().size, codes.size)
        assertTrue("en" in codes)
        assertEquals(codes.sorted(), codes)
    }
}
