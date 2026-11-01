package com.mckimquyen.reader.ui.page.headline

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.headline.ArticleSignalResult
import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.domain.headline.HeadlineTone
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
class EchoSignalSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val noteworthy = ArticleSignalResult(HeadlineLanguage.ENGLISH, 80, HeadlineTone.UNCLEAR, emptyList())
    private val ordinary = ArticleSignalResult(HeadlineLanguage.ENGLISH, 5, HeadlineTone.UNCLEAR, emptyList())

    @Test
    fun enabledAndNoteworthy_showsGauges() {
        rule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(state = EchoSignalState.Ready(noteworthy), onOpen = {})
            }
        }

        rule.onNodeWithTag(ECHO_GAUGES_TAG).assertIsDisplayed()
    }

    @Test
    fun sharedToggleOff_hidesGaugesEvenWhenNoteworthy() {
        rule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides false) {
                EchoSignalSection(state = EchoSignalState.Ready(noteworthy), onOpen = {})
            }
        }

        rule.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    private fun assertNothingFor(state: EchoSignalState) {
        rule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(state = state, onOpen = {})
            }
        }
        rule.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    // One setContent per test: Compose's rule cannot be re-composed with new content in the same test.
    @Test fun idleState_showsNothing() = assertNothingFor(EchoSignalState.Idle)

    @Test fun analyzingState_showsNothing() = assertNothingFor(EchoSignalState.Analyzing)

    @Test fun noneState_showsNothing() = assertNothingFor(EchoSignalState.None)

    @Test
    fun readyButNotNoteworthy_showsNothing() {
        rule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(state = EchoSignalState.Ready(ordinary), onOpen = {})
            }
        }

        rule.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    @Test
    fun tappingTheGauges_callsOnOpenOnce() {
        var opened = 0
        rule.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(state = EchoSignalState.Ready(noteworthy), onOpen = { opened++ })
            }
        }

        rule.onNodeWithTag(ECHO_GAUGES_TAG).performClick()

        assertEquals(1, opened)
    }
}
