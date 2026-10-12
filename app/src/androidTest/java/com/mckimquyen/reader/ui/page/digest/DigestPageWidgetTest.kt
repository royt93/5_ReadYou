package com.mckimquyen.reader.ui.page.digest

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent
import com.mckimquyen.reader.infrastructure.ai.DailyDigestItem
import com.mckimquyen.reader.infrastructure.ai.DailyDigestSection
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun successState(
        sections: List<DailyDigestSection>,
        sources: List<DigestSourceUi> = emptyList(),
    ) = DigestUiState.Success(
        digest = com.mckimquyen.reader.domain.model.digest.DailyDigest(
            id = "d1", createdAt = 1, headline = "Morning headlines", contentJson = "{}",
            sourceArticleIdsJson = "[]", accountId = 1,
        ),
        content = DailyDigestContent("Morning headlines", sections),
        sources = sources,
    )

    private fun manyItems(n: Int) = List(n) { DailyDigestItem("Story number $it", emptyList()) }

    @Test
    fun twoSectionsWithTheSameTitle_doNotCrashThePage() {
        composeRule.setContent {
            DigestPage(
                state = successState(
                    listOf(
                        DailyDigestSection("Top stories", manyItems(1)),
                        DailyDigestSection("Top stories", manyItems(1)),
                    ),
                ),
                onGenerate = {}, onRetry = {}, onOpenSource = {},
            )
        }
        // Both sections render; before the fix the second "Top stories" key made LazyColumn throw.
        composeRule.onAllNodesWithText("Top stories").assertCountEquals(2)
    }

    @Test
    fun generateButton_isReachableEvenWhenTheDigestIsLong() {
        var generateCalls = 0
        composeRule.setContent {
            DigestPage(
                state = successState(listOf(DailyDigestSection("Long", manyItems(60)))),
                onGenerate = { generateCalls++ }, onRetry = {}, onOpenSource = {},
            )
        }
        // A long digest must not push the button out of the window: it has to be displayed and tappable.
        val label = composeRule.activity.getString(com.mckimquyen.reader.R.string.daily_digest_generate)
        composeRule.onNodeWithText(label).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, generateCalls) }
    }

    @Test
    fun text_usesTheMaterial3ContentColor_notTheMaterial2LightFallback() {
        // The app only provides a Material 3 theme. A Material 2 Text would read LocalContentColor from the
        // M2 default (black) and be unreadable on a dark M3 surface.
        var content = androidx.compose.ui.graphics.Color.Unspecified
        var surface = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme(),
            ) {
                androidx.compose.material3.Surface {
                    surface = androidx.compose.material3.MaterialTheme.colorScheme.surface
                    DigestPage(
                        state = DigestUiState.Empty,
                        onGenerate = {}, onRetry = {}, onOpenSource = {},
                    )
                    content = androidx.compose.material3.LocalContentColor.current
                }
            }
        }
        composeRule.waitForIdle()
        val emptyText = composeRule.activity.getString(com.mckimquyen.reader.R.string.daily_digest_empty)
        val color = textColorOf(emptyText)
        assertTrue("text colour $color must differ from the dark surface $surface", color != surface)
        assertTrue("text must not be M2's default black on a dark surface", color != androidx.compose.ui.graphics.Color.Black)
    }

    private fun textColorOf(text: String): androidx.compose.ui.graphics.Color {
        val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithText(text).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(result)
        return result.first().layoutInput.style.color
    }
}
