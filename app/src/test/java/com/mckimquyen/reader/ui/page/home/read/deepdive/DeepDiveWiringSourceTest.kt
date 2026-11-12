package com.mckimquyen.reader.ui.page.home.read.deepdive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeepDiveWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private val readingPage = File("$root/ui/page/home/read/ReadingPage.kt").readText()
    private val topBar = File("$root/ui/page/home/read/TopBar.kt").readText()
    private val sheet = File("$root/ui/page/home/read/deepdive/DeepDiveSheet.kt").readText()

    @Test fun readingPageOpensDeepDiveWithOnlyIdTitleAndBody() {
        val call = Regex("""deepDiveViewModel\.open\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
            .find(readingPage)?.groupValues?.get(1).orEmpty()
        assertTrue("ReadingPage must call deepDiveViewModel.open(...)", call.isNotBlank())
        assertFalse("no link", call.contains("link"))
        assertFalse("no feed", call.contains("feed", ignoreCase = true))
        assertFalse("no account", call.contains("account", ignoreCase = true))
    }

    @Test fun readingPageHooksTheSheetToTheViewModel() {
        assertTrue(readingPage.contains("DeepDiveSheet("))
        assertTrue(readingPage.contains("val deepDiveViewModel: DeepDiveViewModel = hiltViewModel()"))
        assertTrue(readingPage.contains("onDeepDive ="))
        assertTrue(readingPage.contains("prepareShare("))
    }

    @Test fun readingPageClosesTheSheetWhenTheArticleChanges() {
        assertTrue(readingPage.contains("deepDiveViewModel.dismiss()"))
    }

    @Test fun topBarHasTheMenuEntryAndTheParameter() {
        assertTrue(topBar.contains("onDeepDive: () -> Unit = {}"))
        assertTrue(topBar.contains("R.string.deep_dive_menu"))
    }

    @Test fun theSheetNeverShowsALinkOrASourceName() {
        assertFalse(sheet.contains("link", ignoreCase = true))
        assertFalse(sheet.contains("feed", ignoreCase = true))
    }
}
