package com.mckimquyen.reader.ui.page.home.read.deepdive

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepDiveSheetTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val report = DeepDiveReport(
        "a", listOf("Rates were low", "Inflation rose", "The bank pivoted"),
        listOf(DeepDivePerspective("Monetarist", "Overdue"), DeepDivePerspective("Keynesian", "Risky")),
        listOf(DeepDiveImpact("Housing", "Costs rise", "Prices cool"), DeepDiveImpact("Exports", "Currency firms", "Margins shrink")),
    )

    private var consent: Boolean? = null
    private var later = 0
    private var retry = 0
    private var export = 0
    private var dismissed = 0

    private fun show(state: DeepDiveUiState) = compose.setContent {
        DeepDiveSheet(
            state = state, title = "Rates rise",
            onDismiss = { dismissed++ }, onConsent = { consent = it }, onConsentLater = { later++ },
            onRetry = { retry++ }, onExportPdf = { export++ },
        )
    }

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun idle_drawsNothing() {
        show(DeepDiveUiState.Idle)
        compose.onAllNodesWithTag(DEEP_DIVE_SHEET_TAG).assertCountEquals(0)
    }

    @Test fun consent_showsTheQuestionWithTheBodyNoticeAndTwoButtons() {
        show(DeepDiveUiState.AwaitingConsent)
        compose.onNodeWithTag(DEEP_DIVE_CONSENT_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.deep_dive_consent_message)).assertIsDisplayed()
        compose.onNodeWithTag(DEEP_DIVE_AGREE_TAG).performClick()
        assertEquals(true, consent)
        compose.onNodeWithTag(DEEP_DIVE_LATER_TAG).performClick()
        assertEquals(1, later)
        assertEquals("'Not now' must not answer the consent", true, consent)
    }

    @Test fun declined_showsGuidanceAndTheButtonOpensPrivacy() {
        var opened = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.mckimquyen.reader.ui.page.rpg.LocalOpenPrivacySettings provides { opened++ },
            ) {
                DeepDiveSheet(DeepDiveUiState.ConsentDeclined, "T", {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag(DEEP_DIVE_DECLINED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DEEP_DIVE_OPEN_PRIVACY_TAG).performClick()
        assertEquals(1, opened)
    }

    @Test fun loading_showsTheProgressText() {
        show(DeepDiveUiState.Loading)
        compose.onNodeWithTag(DEEP_DIVE_LOADING_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.deep_dive_loading)).assertIsDisplayed()
    }

    @Test fun failed_showsTheReasonAndRetry() {
        show(DeepDiveUiState.Failed(AiDeepDiveFailure.NO_KEY))
        compose.onNodeWithTag(DEEP_DIVE_FAILED_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.deep_dive_no_key)).assertIsDisplayed()
        compose.onNodeWithTag(DEEP_DIVE_RETRY_TAG).performClick()
        assertEquals(1, retry)
    }

    @Test fun failed_rejectedAndNetworkShowTheGenericMessage() {
        show(DeepDiveUiState.Failed(AiDeepDiveFailure.REJECTED))
        compose.onNodeWithText(str(R.string.deep_dive_failed)).assertIsDisplayed()
    }

    @Test fun failed_nothingToAnalyzeHasItsOwnMessageAndNoRetry() {
        show(DeepDiveUiState.Failed(AiDeepDiveFailure.NOTHING_TO_ANALYZE))
        compose.onNodeWithText(str(R.string.deep_dive_nothing_to_analyze)).assertIsDisplayed()
        compose.onAllNodesWithTag(DEEP_DIVE_RETRY_TAG).assertCountEquals(0)
    }

    // The report scrolls (a long report does not fit a phone), so each part is scrolled into view before it is checked.
    @Test fun ready_showsTheLabelThreeSectionsAndTheMatrix() {
        show(DeepDiveUiState.Ready(report))
        compose.onNodeWithTag(DEEP_DIVE_REPORT_TAG).assertIsDisplayed()
        listOf(
            str(R.string.deep_dive_label),
            str(R.string.deep_dive_section_context),
            "• Rates were low",
            str(R.string.deep_dive_section_critical),
            "• Monetarist: Overdue",
            str(R.string.deep_dive_section_impact),
            str(R.string.deep_dive_col_short),
            "Prices cool",
            "Margins shrink",
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag(DEEP_DIVE_MATRIX_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test fun ready_exportButtonReportsOneTap() {
        show(DeepDiveUiState.Ready(report))
        compose.onNodeWithTag(DEEP_DIVE_EXPORT_TAG).performScrollTo().performClick()
        assertEquals(1, export)
    }

    @Test fun ready_doesNotShowConsentOrFailureParts() {
        show(DeepDiveUiState.Ready(report))
        compose.onAllNodesWithTag(DEEP_DIVE_CONSENT_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(DEEP_DIVE_FAILED_TAG).assertCountEquals(0)
    }
}
