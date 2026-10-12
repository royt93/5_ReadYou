package com.mckimquyen.reader.ui.page.digest

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent
import com.mckimquyen.reader.infrastructure.ai.DailyDigestItem
import com.mckimquyen.reader.infrastructure.ai.DailyDigestSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DigestPageWidgetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyState_displaysMessageAndGenerateAction() {
        var generateCalls = 0
        composeRule.setContent {
            DigestPage(
                state = DigestUiState.Empty,
                onGenerate = { generateCalls++ },
                onRetry = {},
                onOpenSource = {},
            )
        }

        composeRule.onNodeWithText(com.mckimquyen.reader.R.string.daily_digest_empty.let { composeRule.activity.getString(it) }).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(com.mckimquyen.reader.R.string.daily_digest_generate)).performClick()
        composeRule.runOnIdle { assertEquals(1, generateCalls) }
    }

    @Test
    fun successState_displaysDigestAndOpensSourceLink() {
        var opened = ""
        composeRule.setContent {
            DigestPage(
                state = DigestUiState.Success(
                    digest = com.mckimquyen.reader.domain.model.digest.DailyDigest(
                        id = "d1", createdAt = 1, headline = "Morning headlines", contentJson = "{}",
                        sourceArticleIdsJson = "[\"a1\"]", accountId = 1,
                    ),
                    content = DailyDigestContent(
                        "Morning headlines",
                        listOf(DailyDigestSection("Top", listOf(DailyDigestItem("Story", listOf("a1"))))),
                    ),
                    sources = listOf(DigestSourceUi("a1", "Source article", "https://example.test/a1")),
                    history = listOf(
                        com.mckimquyen.reader.domain.model.digest.DailyDigest(
                            id = "d1", createdAt = 1, headline = "Saved morning digest", contentJson = "{}",
                            sourceArticleIdsJson = "[\"a1\"]", accountId = 1,
                        ),
                    ),
                ),
                onGenerate = {},
                onRetry = {},
                onOpenSource = { opened = it },
            )
        }

        composeRule.onNodeWithText("Morning headlines", substring = false, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Story").assertIsDisplayed()
        composeRule.onNodeWithText("Source article").performClick()
        composeRule.runOnIdle { assertEquals("a1", opened) }
    }
}
