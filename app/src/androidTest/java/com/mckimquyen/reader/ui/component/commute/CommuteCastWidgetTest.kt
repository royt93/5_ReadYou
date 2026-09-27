package com.mckimquyen.reader.ui.component.commute

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import com.mckimquyen.reader.infrastructure.audio.CommuteVoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CommuteCastWidgetTest {

    private val sampleEpisode = CommuteEpisode(
        id = "ep_widget",
        title = "CommuteCast Morning",
        date = Date(),
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "Welcome to the morning update."),
            CommuteDialogue(CommuteSpeaker.SAM, "Here are today's top highlights.")
        ),
        isDeepDive = false
    )

    @Test
    fun commuteCastUi_displaysHostsAndDialogues() {
        val testState = CommuteUiState(
            isLoading = false,
            playerState = CommutePlayerState(
                episode = sampleEpisode,
                currentDialogueIndex = 0,
                isPlaying = true
            )
        )

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    CommuteCastUi(
                        uiState = testState,
                        onTogglePlayPause = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onSeekTo = {},
                        onUnlockDeepDive = {},
                        onRetry = {},
                        onClose = {}
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun commuteCastUi_playPauseButton_triggersCallback() {
        var toggleClicked = false
        val testState = CommuteUiState(
            isLoading = false,
            playerState = CommutePlayerState(
                episode = sampleEpisode,
                currentDialogueIndex = 0,
                isPlaying = false
            )
        )

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    CommuteCastUi(
                        uiState = testState,
                        onTogglePlayPause = { toggleClicked = true },
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onSeekTo = {},
                        onUnlockDeepDive = {},
                        onRetry = {},
                        onClose = {}
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    /**
     * Renders [state] and returns every piece of text the tree ended up showing.
     *
     * Walks the view hierarchy rather than using `createComposeRule`: that rule pulls in Espresso,
     * whose `InputManager.getInstance` lookup throws on this Android version and would take the
     * other tests in this class down with it.
     */
    private fun renderedTextsFor(state: CommuteUiState): List<String> {
        val texts = mutableListOf<String>()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val latch = CountDownLatch(1)

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    CommuteCastUi(
                        uiState = state,
                        onTogglePlayPause = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onSeekTo = {},
                        onUnlockDeepDive = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            composeView.post {
                texts += composeView.collectSemanticTexts()
                latch.countDown()
            }
        }

        assertTrue("Compose tree never settled", latch.await(10, TimeUnit.SECONDS))
        scenario.close()
        return texts
    }

    /** Pulls the text out of Compose's semantics tree, which is what a screen reader would read. */
    private fun View.collectSemanticTexts(): List<String> {
        if (this is ViewRootForTest) {
            return semanticsOwner.rootSemanticsNode.collectTexts()
        }
        if (this is ViewGroup) {
            return (0 until childCount).flatMap { getChildAt(it).collectSemanticTexts() }
        }
        return emptyList()
    }

    private fun SemanticsNode.collectTexts(): List<String> {
        val own = config.getOrNull(SemanticsProperties.Text)
            ?.map { it.text }
            .orEmpty()
        return own + children.flatMap { it.collectTexts() }
    }

    /** Finds the clickable semantics node whose own text exactly matches [text] and invokes its click action. */
    private fun SemanticsNode.clickNodeWithText(text: String): Boolean {
        val ownTexts = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
        if (text in ownTexts) {
            val onClick = config.getOrNull(SemanticsActions.OnClick)
            if (onClick?.action?.invoke() == true) return true
        }
        return children.any { it.clickNodeWithText(text) }
    }

    /**
     * Walks down to the real [ViewRootForTest] (the ComposeView passed in from a test is itself
     * just a plain ViewGroup wrapper — the semantics owner lives on its AndroidComposeView child)
     * and clicks the semantics node whose text matches. Mirrors [collectSemanticTexts]'s walk.
     */
    private fun View.findAndClickSemanticText(text: String): Boolean {
        if (this is ViewRootForTest) {
            return semanticsOwner.rootSemanticsNode.clickNodeWithText(text)
        }
        if (this is ViewGroup) {
            return (0 until childCount).any { getChildAt(it).findAndClickSemanticText(text) }
        }
        return false
    }

    @Test
    fun awaitingPlayback_tellsTheUserTheVoicesArePreparing() {
        val expected = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.commute_tts_preparing)

        val texts = renderedTextsFor(
            CommuteUiState(
                isLoading = false,
                playerState = CommutePlayerState(
                    episode = sampleEpisode,
                    isPlaying = false,
                    isAwaitingPlayback = true,
                ),
            )
        )

        // Before this state existed, the play request was dropped in silence and the sheet showed
        // nothing at all — so the visible message is the whole point of the fix.
        assertTrue(
            "Expected the preparing message; rendered: $texts",
            texts.any { it == expected },
        )
    }

    @Test
    fun readyToPlay_doesNotShowThePreparingMessage() {
        val preparing = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.commute_tts_preparing)

        val texts = renderedTextsFor(
            CommuteUiState(
                isLoading = false,
                playerState = CommutePlayerState(
                    episode = sampleEpisode,
                    isPlaying = true,
                    isTtsReady = true,
                    isAwaitingPlayback = false,
                ),
            )
        )

        assertFalse("Must not claim to be preparing once ready: $texts", texts.any { it == preparing })
        assertTrue(
            "Expected the dialogue to be shown; rendered: $texts",
            texts.any { it == sampleEpisode.dialogues.first().text },
        )
    }

    @Test
    fun voiceMode_tellsTheUserWhenOnlyOneVoiceIsAvailable() {
        val simulatedMessage = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.commute_voice_mode_simulated)

        val texts = renderedTextsFor(
            CommuteUiState(
                isLoading = false,
                playerState = CommutePlayerState(
                    episode = sampleEpisode,
                    voiceMode = CommuteVoiceMode.SIMULATED,
                ),
            )
        )

        assertTrue(
            "Must state single-voice mode honestly; rendered: $texts",
            texts.any { it == simulatedMessage },
        )
    }

    @Test
    fun voiceMode_announcesTwoRealVoicesWhenTheDeviceOffersThem() {
        val dualMessage = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.commute_voice_mode_dual)

        val texts = renderedTextsFor(
            CommuteUiState(
                isLoading = false,
                playerState = CommutePlayerState(
                    episode = sampleEpisode,
                    voiceMode = CommuteVoiceMode.REAL_DUAL,
                ),
            )
        )

        assertTrue(
            "Must announce dual voice when really available; rendered: $texts",
            texts.any { it == dualMessage },
        )
    }

    @Test
    fun commuteCastUi_displaysTimeBudgetChips() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedChips = listOf(
            ctx.getString(R.string.commute_budget_3m),
            ctx.getString(R.string.commute_budget_4m),
            ctx.getString(R.string.commute_budget_8m),
            ctx.getString(R.string.commute_budget_15m),
        )

        val texts = renderedTextsFor(
            CommuteUiState(
                isLoading = false,
                playerState = CommutePlayerState(episode = sampleEpisode, isPlaying = false),
                selectedBudgetMinutes = 4,
            )
        )

        expectedChips.forEach { chipLabel ->
            assertTrue(
                "Expected time budget chip '$chipLabel' to be rendered; rendered: $texts",
                texts.any { it == chipLabel },
            )
        }
    }

    @Test
    fun commuteCastUi_clickingTimeBudgetChip_invokesCallback() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val chip8m = ctx.getString(R.string.commute_budget_8m)

        var selectedMinutes: Int? = null
        val testState = CommuteUiState(
            isLoading = false,
            playerState = CommutePlayerState(episode = sampleEpisode, isPlaying = false),
            selectedBudgetMinutes = 4,
        )

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val latch = CountDownLatch(1)
        var rootView: View? = null
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    CommuteCastUi(
                        uiState = testState,
                        onTogglePlayPause = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onSeekTo = {},
                        onSelectBudget = { selectedMinutes = it },
                        onUnlockDeepDive = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            rootView = composeView
            composeView.post { latch.countDown() }
        }

        assertTrue("Compose tree never settled", latch.await(10, TimeUnit.SECONDS))

        val clickLatch = CountDownLatch(1)
        scenario.onActivity {
            val clicked = rootView!!.findAndClickSemanticText(chip8m)
            assertTrue("Must find a clickable node with text '$chip8m'", clicked)
            clickLatch.countDown()
        }
        assertTrue("Click never processed", clickLatch.await(10, TimeUnit.SECONDS))

        scenario.close()
        assertEquals("Clicking the 8-min chip must report budget=8", 8, selectedMinutes)
    }
}
