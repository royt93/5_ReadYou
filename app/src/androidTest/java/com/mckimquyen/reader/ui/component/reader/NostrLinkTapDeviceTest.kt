package com.mckimquyen.reader.ui.component.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Nostr note through the REAL mapper and the REAL Reader (Jsoup and Compose): a tap on a link in the text gives the reader that
 * address, and a tap on plain text gives nothing. The tap goes where the text layout says the link is, never to a guessed point.
 * It stops at the callback: the browser is not opened (openURL is not called) and nothing is downloaded.
 */
class NostrLinkTapDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = CopyOnWriteArrayList<String>()

    @Test
    fun tappingAnHttpsLink_givesTheReaderThatAddress() {
        show(note(TEXT_BEFORE + " " + FIRST_LINK))
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun tappingTheReplyLine_givesTheLinkOfTheNoteItAnswers() {
        val target = ID_ANSWERED
        show(note("An answer", listOf("e", target, "", "reply")))
        val expected = NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(target))
        tapOnTheTextAt("An answer", expected)
        assertEquals(listOf(expected), opened.toList())
    }

    @Test
    fun tappingAnHttpLink_givesNothing() {
        // The first link works, so a tap that reaches nothing proves the second one is only text, not a dead tap.
        show(note("$TEXT_BEFORE $FIRST_LINK and $HTTP_LINK"))
        tapOnTheTextAt("$TEXT_BEFORE $FIRST_LINK and ", HTTP_LINK)
        assertTrue("opened ${opened.toList()}", opened.isEmpty())
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun aTrailingDot_isNotPartOfTheTappedAddress() {
        show(note("$TEXT_BEFORE $FIRST_LINK."))
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun tappingTheSecondOfTwoLinks_givesTheSecondAddress() {
        show(note("$FIRST_LINK $TEXT_BEFORE $SECOND_LINK"))
        tapOnTheTextAt("$FIRST_LINK $TEXT_BEFORE ", SECOND_LINK)
        assertEquals(listOf(SECOND_LINK), opened.toList())
    }

    // ---- FRONT-02ai: a closing bracket that matches an opening one inside the link is part of the tapped address.

    @Test
    fun aLinkWithABalancedParenthesis_isTappedWithItsClosingParenthesis() {
        show(note("$TEXT_BEFORE $WIKI_LINK"))
        tapOnTheTextAt(TEXT_BEFORE, WIKI_LINK)
        assertEquals(listOf(WIKI_LINK), opened.toList())
    }

    @Test
    fun aBalancedParenthesisFollowedByTheEndOfTheSentence_isStillTappedWhole() {
        show(note("($TEXT_BEFORE $WIKI_LINK)."))
        tapOnTheTextAt(TEXT_BEFORE, WIKI_LINK)
        assertEquals(listOf(WIKI_LINK), opened.toList())
    }

    @Test
    fun aParenthesisThatMatchesNothing_isNotPartOfTheTappedAddress() {
        show(note("($TEXT_BEFORE $FIRST_LINK)"))
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun aLinkWithBalancedSquareBrackets_isTappedWithThem() {
        show(note("$TEXT_BEFORE $SQUARE_LINK, ok"))
        tapOnTheTextAt(TEXT_BEFORE, SQUARE_LINK)
        assertEquals(listOf(SQUARE_LINK), opened.toList())
    }

    @Test
    fun aLinkWithBalancedCurlyBrackets_isTappedWithThem() {
        show(note("$TEXT_BEFORE $CURLY_LINK, ok"))
        tapOnTheTextAt(TEXT_BEFORE, CURLY_LINK)
        assertEquals(listOf(CURLY_LINK), opened.toList())
    }

    private fun note(content: String, vararg tags: List<String>): NostrEvent = requireNotNull(
        NostrEvent.parse(
            JSONObject().apply {
                put("id", NOTE_ID); put("pubkey", AUTHOR); put("created_at", CREATED_AT_SECONDS); put("kind", 1)
                put("tags", JSONArray(tags.map { JSONArray(it) })); put("content", content); put("sig", "0".repeat(SIG_HEX_LENGTH))
            }.toString(),
        ),
    )

    private fun show(event: NostrEvent) {
        val article = NostrFeedMapper.toArticles(listOf(event), "feed-1", ACCOUNT_ID, NOW_MILLIS).single()
        compose.setContent {
            AppTheme(useDarkTheme = true) {
                LazyColumn(Modifier.fillMaxSize()) {
                    Reader(link = article.link, content = article.rawDescription, onLinkClick = { opened += it })
                }
            }
        }
        compose.waitForIdle()
    }

    /** Taps the middle of the first character of [target] in the one text block that starts with [before]. */
    private fun tapOnTheTextAt(before: String, target: String) {
        val block = compose.onNode(hasText(before, substring = true) and hasLayout())
        val action = block.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the text block must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        val layout = layouts.single()
        val start = layout.layoutInput.text.text.indexOf(target)
        assertTrue("'$target' is not in the shown text '${layout.layoutInput.text.text}'", start >= 0)
        val box = layout.getBoundingBox(start)
        block.performTouchInput { click(box.center) }
        compose.waitForIdle()
    }

    private fun hasLayout() = SemanticsMatcher("has a text layout") { it.config.contains(SemanticsActions.GetTextLayoutResult) }

    private companion object {
        const val TEXT_BEFORE = "Look at this photo"
        const val FIRST_LINK = "https://img.example.test/one.jpg"
        const val SECOND_LINK = "https://img.example.test/two.png"
        const val HTTP_LINK = "http://img.example.test/plain.jpg"
        const val WIKI_LINK = "https://vi.wikipedia.org/wiki/Foo_(bar)"
        const val SQUARE_LINK = "https://img.example.test/list[1]"
        const val CURLY_LINK = "https://img.example.test/set{x}"
        const val ID_ANSWERED = "00000000000000000000000000000000000000000000000000000000000000aa"
        const val NOTE_ID = "0000000000000000000000000000000000000000000000000000000000000001"
        const val AUTHOR = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        const val ACCOUNT_ID = 1
        const val CREATED_AT_SECONDS = 1_700_000_000L
        const val NOW_MILLIS = 1_800_000_000_000L
        const val SIG_HEX_LENGTH = 128
    }
}
