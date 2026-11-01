package com.mckimquyen.reader.ui.page.home.read.bounty

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.domain.model.bounty.Dossier
import com.mckimquyen.reader.domain.model.bounty.DossierArticleRef
import com.mckimquyen.reader.domain.model.bounty.QuotedSentence
import com.mckimquyen.reader.domain.model.bounty.RecurringPhrase
import com.mckimquyen.reader.domain.model.bounty.SourceFraming
import com.mckimquyen.reader.domain.model.bounty.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DossierSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val date = Date(1_700_000_000_000L)

    private fun ref(id: String, title: String, feed: String = "BBC") = DossierArticleRef(id, title, feed, date)

    private fun dossier(
        summary: List<QuotedSentence> = listOf(QuotedSentence("DeepSeek launches R1.", "BBC", date, "a1")),
        timeline: List<TimelineEntry> = listOf(TimelineEntry(LocalDate.parse("2023-11-14"), listOf(ref("a1", "Timeline story")))),
        phrases: List<RecurringPhrase> = listOf(RecurringPhrase("Sam Altman", 3)),
        framings: List<SourceFraming> = listOf(
            SourceFraming("BBC", listOf(ref("a1", "BBC take"))),
            SourceFraming("Wired", listOf(ref("a2", "Wired take", "Wired"))),
        ),
        sourceCount: Int = 2,
    ) = Dossier("DeepSeek", summary, timeline, phrases, framings, sourceCount, 1_700_000_000_000L)

    private fun show(
        dossier: Dossier,
        onOpenArticle: (String) -> Unit = {},
        onShare: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = compose.setContent {
        DossierSheetContent(dossier = dossier, onOpenArticle = onOpenArticle, onShare = onShare, onClose = onClose)
    }

    @Test
    fun showsFourTabs_summarySelectedFirst() {
        show(dossier())

        listOf("summary", "timeline", "names", "sources").forEach {
            compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + it).assertIsDisplayed()
        }
        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "summary").assertIsSelected()
    }

    @Test
    fun tabsExposeContentDescriptions() {
        show(dossier())

        listOf("Summary", "Timeline", "Names", "Sources").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test
    fun summary_showsQuoteWithFeedAndTheLimitationLine() {
        show(dossier())

        compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + "summary").assertIsDisplayed()
        compose.onNodeWithText("DeepSeek launches R1.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("BBC", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(DOSSIER_LIMITATION_TAG).assertIsDisplayed()
    }

    @Test
    fun summary_emptyShowsMessage() {
        show(dossier(summary = emptyList()))

        compose.onNodeWithText("No sentence in your saved articles contains this phrase.").assertIsDisplayed()
    }

    @Test
    fun timelineTab_showsDayAndArticle() {
        show(dossier())

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "timeline").performClick()

        compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + "timeline").assertIsDisplayed()
        compose.onAllNodesWithTag(DOSSIER_SECTION_TAG_PREFIX + "summary").assertCountEquals(0)
        compose.onNodeWithText("Timeline story", substring = true).assertIsDisplayed()
    }

    @Test
    fun namesTab_showsPhraseCountAndHonestNote() {
        show(dossier())

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "names").performClick()

        compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + "names").assertIsDisplayed()
        compose.onNodeWithText("Sam Altman", substring = true).assertIsDisplayed()
        compose.onNodeWithText("3 articles", substring = true).assertIsDisplayed()
        compose.onNodeWithText("not named-entity recognition", substring = true).assertIsDisplayed()
    }

    @Test
    fun namesTab_emptyShowsMessage() {
        show(dossier(phrases = emptyList()))

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "names").performClick()

        compose.onNodeWithText("No phrase recurs in at least 2 articles.").assertIsDisplayed()
    }

    @Test
    fun sourcesTab_showsHeadlinesPerFeed() {
        show(dossier())

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "sources").performClick()

        compose.onNodeWithTag(DOSSIER_SECTION_TAG_PREFIX + "sources").assertIsDisplayed()
        compose.onNodeWithText("BBC take", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Wired take", substring = true).assertIsDisplayed()
    }

    @Test
    fun sourcesTab_singleSourceShowsHonestMessage() {
        show(dossier(framings = emptyList(), sourceCount = 1))

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "sources").performClick()

        compose.onNodeWithText("Your archive has only one source on this topic.").assertIsDisplayed()
    }

    @Test
    fun tappingArticleRow_opensItOnce() {
        val opened = mutableListOf<String>()
        show(dossier(), onOpenArticle = { opened += it })

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "timeline").performClick()
        compose.onNodeWithTag(DOSSIER_ARTICLE_TAG_PREFIX + "a1").performClick()

        assertEquals(listOf("a1"), opened)
    }

    @Test
    fun tappingQuote_opensItsArticle() {
        val opened = mutableListOf<String>()
        show(dossier(), onOpenArticle = { opened += it })

        compose.onNodeWithTag(DOSSIER_ARTICLE_TAG_PREFIX + "a1").performClick()

        assertEquals(listOf("a1"), opened)
    }

    @Test
    fun shareAndClose_callTheirCallbacksOnce() {
        var shared = 0
        var closed = 0
        show(dossier(), onShare = { shared++ }, onClose = { closed++ })

        compose.onNodeWithTag(DOSSIER_SHARE_TAG).performClick()
        compose.onNodeWithTag(DOSSIER_CLOSE_TAG).performClick()

        assertEquals(1, shared)
        assertEquals(1, closed)
    }

    @Test
    fun touchTargets_areAtLeast48dp() {
        show(dossier())

        compose.onNodeWithTag(DOSSIER_TAB_TAG_PREFIX + "summary").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(DOSSIER_SHARE_TAG).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(DOSSIER_CLOSE_TAG).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(DOSSIER_ARTICLE_TAG_PREFIX + "a1").assertHeightIsAtLeast(48.dp)
    }
}
