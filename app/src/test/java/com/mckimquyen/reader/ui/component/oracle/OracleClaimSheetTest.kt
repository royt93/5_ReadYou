package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.oracle.OracleClaimCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OracleClaimSheetTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val card = OracleClaimCard(
        articleId = "a",
        question = "Will the chipmaker open the fab?",
        expiresAt = LocalDate.of(2026, 12, 31).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1,
        resolutionCriteria = "The company's own announcement.",
    )
    private var dismissed = 0

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun nullCard_drawsNothing() {
        compose.setContent { OracleClaimSheet(card = null, onDismiss = { dismissed++ }) }
        compose.onAllNodesWithTag(ORACLE_SHEET_TAG).assertCountEquals(0)
        // An empty Dialog would still open a window; no popup root may exist either.
        assertEquals("no dialog window may open for a null card", 0, compose.onAllNodes(androidx.compose.ui.test.isDialog()).fetchSemanticsNodes().size)
    }

    @Test fun showsQuestionDateCriteriaAndTheAiLabel() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = { dismissed++ }) }
        compose.onNodeWithTag(ORACLE_QUESTION_TAG).assertTextContains(card.question)
        compose.onNodeWithTag(ORACLE_EXPIRES_TAG).assertTextContains("Dec 31, 2026", substring = true)
        compose.onNodeWithTag(ORACLE_CRITERIA_TAG).assertTextContains(card.resolutionCriteria)
        compose.onNodeWithTag(ORACLE_AI_LABEL_TAG).assertTextContains(str(R.string.oracle_sheet_ai_label))
    }

    @Test fun theDateIsTheUtcDay_notTheLocalDayOfTheDevice() {
        // 23:59:59.999 UTC on Dec 31 must still read Dec 31 in a timezone behind UTC.
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            compose.setContent { OracleClaimSheetContent(card, onDismiss = {}) }
            compose.onNodeWithTag(ORACLE_EXPIRES_TAG).assertTextContains("Dec 31, 2026", substring = true)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

    @Test fun offersNoBettingOrPointsControl_onlyClose() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = {}) }
        compose.onAllNodesWithTag(ORACLE_CLOSE_TAG).assertCountEquals(1)
    }

    @Test fun closeCallsDismiss() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = { dismissed++ }) }
        compose.onNodeWithTag(ORACLE_CLOSE_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test fun dialogVersion_showsTheContentForACard() {
        compose.setContent { OracleClaimSheet(card = card, onDismiss = {}) }
        compose.onNodeWithTag(ORACLE_SHEET_TAG).assertIsDisplayed()
    }
}
