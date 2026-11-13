package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleEvidenceWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val viewModel = code("ui/page/oracle/OracleBetting.kt")
    private val section = code("ui/component/oracle/OracleBettingSection.kt")
    private val home = code("ui/page/common/HomeEntry.kt")

    @Test fun viewModelSearchesOnlyThroughTheLocalFinder_onTheIoDispatcher() {
        assertTrue(viewModel.contains("OracleEvidenceFinder"))
        assertTrue(viewModel.contains("val evidence: StateFlow<OracleEvidenceUiState>"))
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded").forEach {
            assertFalse("OracleBetting.kt must not mention '$it'", viewModel.contains(it, ignoreCase = true))
        }
    }

    @Test fun viewModelSearchesOnlyWhileTheBetAwaitsItsResult() {
        assertTrue(viewModel.contains("OracleBetPhase.AWAITING_RESULT"))
    }

    @Test fun everyLaunchStillGoesThroughViewModelScope() {
        assertEquals(
            "every launch goes through viewModelScope",
            Regex("""\blaunch\b""").findAll(viewModel).count(),
            Regex("""viewModelScope\.launch""").findAll(viewModel).count(),
        )
    }

    @Test fun sectionReadsItsTextFromStringResources_andShowsNoLinkOrBody() {
        assertTrue(section.contains("R.string.oracle_due_evidence_title"))
        assertTrue(section.contains("R.string.oracle_due_evidence_none"))
        assertTrue(section.contains("R.string.oracle_due_evidence_hint"))
        listOf("rawDescription", "fullContent", "link", "Rewarded", "AdManager").forEach {
            assertFalse("the evidence section must not mention '$it'", section.contains(it, ignoreCase = true))
        }
    }

    @Test fun sectionShowsAtMostTheThreeBestItems_fromThePolicyConstant() {
        assertTrue(section.contains("OracleEvidencePolicy.MAX_EVIDENCE"))
        assertFalse(Regex("""\.take\(\s*3\s*\)""").containsMatchIn(section))
    }

    @Test fun viewModelLoadsOffTheMainThread() {
        assertTrue(viewModel.contains(".flowOn(io)"))
    }

    @Test fun openingAnArticleClosesTheCardFirst_soNoDialogStaysOverTheReader() {
        val handler = home.substringAfter("onOpenArticle = { id ->").substringBefore("navController.navigate")
        assertTrue(handler.contains("openOracleArticleId = null"))
        assertTrue(handler.contains("oracleBettingViewModel.close()"))
    }

    @Test fun homeEntryPassesTheEvidenceAndOpensTheArticleThroughTheReadingRoute() {
        assertTrue(home.contains("oracleBettingViewModel.evidence.collectAsState()"))
        assertTrue(home.contains("evidence = oracleEvidence"))
        assertTrue(home.contains("onOpenArticle"))
        assertTrue(home.contains("\"\${RouteName.READING}/"))
    }
}
