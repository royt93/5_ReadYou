package com.mckimquyen.reader.ui.page.setting.ai

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.aiProvider
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 60L
private const val STORED_BEFORE_NOT_SET = -1

/**
 * The AI screen on the phone: only the Gemini chip is shown (the other providers are not wired up, so they are not offered), and a
 * provider saved by an older build is shown as Gemini. The stored value is read before the test and put back afterwards (only this one
 * DataStore key is touched; the rest of the owner's settings, including any key, are not read or written).
 * NOT covered: the key box and Save button (they would write the owner's real key), the Back button (it shows an interstitial ad).
 */
@RunWith(AndroidJUnit4::class)
class AiProviderChipsDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var storedBefore = STORED_BEFORE_NOT_SET

    @Before
    fun rememberTheStoredChoice() {
        storedBefore = compose.activity.aiProvider
    }

    @After
    fun putTheStoredChoiceBack() {
        runBlocking { compose.activity.dataStore.put(DataStoreKeys.AiProvider, storedBefore) }
    }

    private fun showThePage() {
        compose.runOnUiThread {
            compose.activity.setContent {
                AiSettingsPage(navController = rememberNavController(), activity = compose.activity)
            }
        }
        compose.waitUntil(10_000L) { compose.onAllNodesWithTextCount("Gemini") > 0 }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun onlyTheGeminiChipIsShown_andItIsEnabled() {
        showThePage()
        compose.onNodeWithText("Gemini").assertIsEnabled()
        listOf("OpenAI", "DeepSeek", "Groq").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun aProviderSavedByAnOlderBuild_isShownAsGemini() {
        runBlocking { compose.activity.dataStore.put(DataStoreKeys.AiProvider, 1) }
        showThePage()
        compose.onNodeWithText("Gemini").assertIsSelected()
        compose.onNodeWithText("OpenAI").assertDoesNotExist()
    }

    @Test
    fun theOnlyChipLeft_isGemini_andTappingItKeepsTheChoice() {
        runBlocking { compose.activity.dataStore.put(DataStoreKeys.AiProvider, 0) }
        showThePage()
        compose.onNodeWithText("Gemini").performClick()
        compose.onNodeWithText("Gemini").assertIsSelected()
        assertEquals(0, compose.activity.aiProvider)
    }
}
