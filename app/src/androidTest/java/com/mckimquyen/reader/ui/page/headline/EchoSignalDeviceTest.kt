package com.mckimquyen.reader.ui.page.headline

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.headline.ArticleSignalAnalyzer
import com.mckimquyen.reader.domain.headline.ArticleSignalResult
import com.mckimquyen.reader.domain.headline.FlaggedPassage
import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.domain.headline.HeadlineTone
import com.mckimquyen.reader.domain.headline.HypeCueCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EchoSignalDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val promo = ArticleSignalResult(
        HeadlineLanguage.ENGLISH, 82, HeadlineTone.UNCLEAR,
        listOf(FlaggedPassage("Revolutionary game-changing platform!!!", 91, listOf(HypeCueCategory.PROMOTIONAL_PHRASE))),
    )
    private val ordinary = ArticleSignalResult(HeadlineLanguage.ENGLISH, 4, HeadlineTone.UNCLEAR, emptyList())

    @Test
    fun noteworthyResult_rendersGaugesAndOpensOnTap() {
        var opened = 0
        compose.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(EchoSignalState.Ready(promo), onOpen = { opened++ })
            }
        }

        compose.onNodeWithTag(ECHO_GAUGES_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ECHO_HYPE_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ECHO_GAUGES_TAG).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun ordinaryResult_rendersNothing() {
        compose.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides true) {
                EchoSignalSection(EchoSignalState.Ready(ordinary), onOpen = {})
            }
        }

        compose.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    @Test
    fun sharedToggleOff_rendersNothing() {
        compose.setContent {
            CompositionLocalProvider(LocalHeadlineSignalsEnabled provides false) {
                EchoSignalSection(EchoSignalState.Ready(promo), onOpen = {})
            }
        }

        compose.onAllNodesWithTag(ECHO_GAUGES_TAG).assertCountEquals(0)
    }

    @Test
    fun detailsSheet_showsDisclaimerAndFlaggedPassageOnRealRenderer() {
        compose.setContent { EchoSignalDetailsSheet(promo, onClose = {}) }

        compose.onNodeWithTag(ECHO_DISCLAIMER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ECHO_PASSAGE_TAG_PREFIX + "0").assertIsDisplayed()
    }

    @Test
    fun analyzerRunsOnDeviceRuntime_andSeparatesPromoFromPlainNews() {
        val promoBody = "<p>This revolutionary and game-changing platform is guaranteed to be the best ever solution!!!</p>" +
            "<p>Our world-class, groundbreaking and unparalleled product is a must-have for every company.</p>"
        val plainBody = "<p>The council approved a new tram route on Tuesday after a vote that ended seven to four.</p>" +
            "<p>Construction is expected to begin next spring according to the transport department.</p>"

        val promoResult = ArticleSignalAnalyzer.analyze(promoBody)
        val plainResult = ArticleSignalAnalyzer.analyze(plainBody)

        assertNotNull(promoResult)
        assertTrue("promo hype=${promoResult?.hypeScore}", promoResult!!.hypeScore >= ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD)
        assertTrue("plain must not be noteworthy", plainResult == null || !plainResult.isNoteworthy)
    }
}
