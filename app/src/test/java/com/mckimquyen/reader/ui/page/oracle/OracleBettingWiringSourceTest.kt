package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleBettingWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }
        .joinToString("\n")

    private val section = code("ui/component/oracle/OracleBettingSection.kt")
    private val sheet = code("ui/component/oracle/OracleClaimSheet.kt")
    private val home = code("ui/page/common/HomeEntry.kt")

    @Test fun theSheetHasAFooterSlotThatReceivesTheCard() {
        assertTrue(sheet.contains("footer(card)"))
        assertEquals("both the dialog and the body declare the slot", 2, Regex("""footer: @Composable \(OracleClaimCard\) -> Unit""").findAll(sheet).count())
    }

    @Test fun homeEntryHostsTheBettingViewModelAndOpensItWithTheSheet() {
        assertTrue(home.contains("val oracleBettingViewModel: OracleBettingViewModel = hiltViewModel()"))
        assertTrue("the card is handed to the view model when it opens", home.contains("oracleBettingViewModel.open(") || home.contains("oracleBettingViewModel::open"))
        assertTrue(home.contains("oracleBettingViewModel.close()"))
        assertTrue(home.contains("OracleBettingSection("))
        assertTrue(home.contains("oracleBettingViewModel::placeBet"))
        assertTrue(home.contains("oracleBettingViewModel::settle"))
    }

    @Test fun theSectionNeverMentionsAdsLinksOrMoneyEntryPoints() {
        listOf("AdManager", "showRewarded", "Rewarded", "Admob", "Applovin", "link", "feedId", "accountId", "Billing", "purchase").forEach {
            assertFalse("the betting section must not mention '$it'", section.contains(it, ignoreCase = true))
        }
    }

    @Test fun theSectionReadsEveryTextFromStringResources() {
        assertTrue(section.contains("R.string.oracle_bet_balance"))
        assertTrue(section.contains("R.string.oracle_bet_out_of_points"))
        assertFalse("no hard-coded sentence", Regex("""Text\(\s*"[A-Za-z ]{4,}"""").containsMatchIn(section))
    }

    @Test fun theStakesComeFromThePolicy_notALocalCopy() {
        assertTrue(section.contains("OracleBettingPolicy.STAKES"))
        assertFalse(Regex("""listOf\(\s*50\s*,\s*100\s*,\s*500""").containsMatchIn(section))
    }
}
