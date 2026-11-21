package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An https link in the text of a Nostr note becomes an anchor in the stored HTML, so the reading page makes it tappable. The text is
 * untrusted: the address is escaped like any other text, so it cannot leave the attribute or add a tag. Nothing is downloaded here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrTappableLinksTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val limit = NostrFeedMapper.MAX_LINK_URL_LENGTH

    private fun articleOf(content: String): Article {
        val event = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", "%064x".format(1)); put("pubkey", author); put("created_at", 1_700_000_000L); put("kind", 1)
                    put("tags", org.json.JSONArray()); put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        return NostrFeedMapper.toArticles(listOf(event), "feed-1", 7, now).single()
    }

    private fun linksIn(content: String): List<String> = NostrFeedMapper.linkSpans(content).map { content.substring(it.first, it.last + 1) }

    /** A link of exactly [length] characters. */
    private fun linkOfLength(length: Int): String = "https://x.example/" + "a".repeat(length - "https://x.example/".length)

    @Test fun anHttpsLink_becomesAnAnchor() {
        assertEquals(
            "look <a href=\"https://x.example/a\">https://x.example/a</a> ok",
            articleOf("look https://x.example/a ok").rawDescription,
        )
    }

    @Test fun trailingPunctuation_isLeftOutOfTheLink() {
        assertEquals(
            listOf("https://x.example/a", "https://y.example/b"),
            linksIn("see https://x.example/a, and (https://y.example/b)."),
        )
        assertEquals(listOf("https://x.example/a"), linksIn("\"https://x.example/a\"!?"))
    }

    // ---- FRONT-02ai: a closing bracket that matches an opening one INSIDE the link belongs to the link; one that matches nothing ends a sentence.

    private val wiki = "https://vi.wikipedia.org/wiki/Foo_(bar)"

    @Test fun aBalancedParenthesisAtTheEnd_staysInTheLink() {
        assertEquals(listOf(wiki), linksIn("see $wiki now"))
        assertEquals(listOf(wiki), linksIn(wiki))
    }

    @Test fun aBalancedParenthesisInsideAnOuterOne_keepsOnlyTheLinksOwn() {
        assertEquals(listOf(wiki), linksIn("(xem $wiki)."))
        assertEquals(listOf("https://x.example/a_(b)"), linksIn("https://x.example/a_(b))"))
    }

    @Test fun aClosingParenthesisThatMatchesNothing_isStillCut() {
        assertEquals(listOf("https://y.example/b"), linksIn("(https://y.example/b)."))
        assertEquals(listOf("https://x.example/a"), linksIn("https://x.example/a)))"))
    }

    @Test fun squareAndCurlyBracketsAreBalancedTheSameWay() {
        assertEquals(listOf("https://x.example/a[1]"), linksIn("see https://x.example/a[1]."))
        assertEquals(listOf("https://x.example/a{x}"), linksIn("see https://x.example/a{x}, ok"))
        assertEquals(listOf("https://x.example/a"), linksIn("[https://x.example/a]"))
        assertEquals(listOf("https://x.example/a"), linksIn("{https://x.example/a}"))
    }

    @Test fun eachKindOfBracketIsCountedOnItsOwn() {
        // The "]" has no "[" in the link, so it is cut even though a "(" is there; the ")" matches its "(" and stays.
        assertEquals(listOf("https://x.example/a(1"), linksIn("https://x.example/a(1]"))
        assertEquals(listOf("https://x.example/a_(b)"), linksIn("https://x.example/a_(b)]"))
        assertEquals(listOf("https://x.example/a_(b)"), linksIn("https://x.example/a_(b)}"))
    }

    @Test fun moreOpeningThanClosingBrackets_keepsTheClosingOnes() {
        assertEquals(listOf("https://x.example/a_((b)"), linksIn("https://x.example/a_((b)"))
    }

    @Test fun sentencePunctuationAfterABalancedBracket_isCutFirst() {
        assertEquals(listOf(wiki), linksIn("$wiki."))
        assertEquals(listOf(wiki), linksIn("$wiki,"))
        assertEquals(listOf(wiki), linksIn("$wiki!?"))
        assertEquals(listOf(wiki), linksIn("\"$wiki\""))
        assertEquals(listOf(wiki), linksIn("\"$wiki\"."))
    }

    @Test fun theAnchorKeepsTheBalancedParenthesis() {
        assertEquals(
            "see <a href=\"$wiki\">$wiki</a> ok",
            articleOf("see $wiki ok").rawDescription,
        )
    }

    @Test fun aBalancedLinkOfExactlyTheLimit_isKept_andOneMoreIsStillPlainText() {
        val exact = linkOfLength(limit - 3) + "(b)"
        assertEquals(limit, exact.length)
        assertEquals(listOf(exact), linksIn(exact))
        assertEquals(listOf(exact), linksIn("$exact."))
        assertEquals(emptyList<String>(), linksIn(linkOfLength(limit - 2) + "(b)"))
    }

    @Test fun aHostileNoteOfClosingBrackets_isScannedInLinearTime() {
        // A quadratic scan (counting the whole link again for every cut character) needs about 4e10 steps here; the linear one a few ms.
        val hostile = "https://x.example/" + ")".repeat(HOSTILE_BRACKETS)
        val started = System.nanoTime()
        val links = NostrFeedMapper.linkSpans(hostile)
        val millis = (System.nanoTime() - started) / NANOS_PER_MS
        assertEquals(1, links.size)
        assertEquals("https://x.example/", hostile.substring(links.single().first, links.single().last + 1))
        assertTrue("took $millis ms", millis < HOSTILE_BUDGET_MS)
    }

    @Test fun anHttpLink_orAnotherScheme_staysPlainText() {
        val text = "http://x.example/a javascript:alert(1) data:text/html,x mailto:a@b.c nostr:npub1abc ftp://x.example/f"
        assertTrue(linksIn(text).isEmpty())
        assertTrue(!articleOf(text).rawDescription.contains("<a "))
    }

    @Test fun aLinkLongerThanTheLimit_staysPlainText_andOneOfExactlyTheLimitIsKept() {
        assertTrue(linksIn(linkOfLength(limit + 1)).isEmpty())
        val exact = linkOfLength(limit)
        assertEquals(limit, exact.length)
        assertEquals(listOf(exact), linksIn(exact))
    }

    @Test fun theSchemeAlone_isNotALink() {
        for (text in listOf("https://", "https://.", "https://,,", "say https:// now")) {
            assertTrue("'$text' must not be a link", linksIn(text).isEmpty())
        }
    }

    @Test fun theScheme_isCaseInsensitive() {
        assertEquals(listOf("HTTPS://X.EXAMPLE/A"), linksIn("HTTPS://X.EXAMPLE/A"))
    }

    @Test fun markupInsideTheLink_cannotEscapeTheAttribute() {
        val hostile = "https://x.example/a\"onmouseover=\"alert(1)<script>"
        val raw = articleOf(hostile).rawDescription
        val escaped = "https://x.example/a&quot;onmouseover=&quot;alert(1)&lt;script&gt;"
        assertEquals("<a href=\"$escaped\">$escaped</a>", raw)
        val anchors = Jsoup.parse(raw).select("a")
        assertEquals(1, anchors.size)
        assertEquals(1, anchors.single().attributes().size())
        assertTrue(Jsoup.parse(raw).select("script").isEmpty())
    }

    @Test fun anAmpersandInTheQuery_isEscapedInTheHref_andReadBackByTheParser() {
        val raw = articleOf("https://x.example/a?a=1&b=2").rawDescription
        assertTrue(raw, raw.contains("href=\"https://x.example/a?a=1&amp;b=2\""))
        // The reading page takes the address from this attribute (HtmlToComposable, "abs:href"), with the note's own address as base.
        assertEquals("https://x.example/a?a=1&b=2", Jsoup.parse(raw, "https://njump.me/").selectFirst("a")!!.attr("abs:href"))
    }

    @Test fun aLink_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink() {
        val a = articleOf("look https://x.example/a")
        assertEquals("look https://x.example/a", a.title)
        assertEquals("look https://x.example/a", a.shortDescription)
        assertTrue(a.link.startsWith(NostrFeedMapper.NJUMP_BASE + "note1"))
    }

    @Test fun aNoteWithoutALink_isEscapedAsBefore() {
        assertEquals("a &amp; b &lt;i&gt;<br>c", articleOf("a & b <i>\nc").rawDescription)
    }

    @Test fun twoLinksAndNewLines_keepTheTextBetween() {
        assertEquals(
            "one <a href=\"https://x.example/1\">https://x.example/1</a><br>two <a href=\"https://y.example/2\">https://y.example/2</a>",
            articleOf("one https://x.example/1\ntwo https://y.example/2").rawDescription,
        )
    }

    private companion object {
        const val HOSTILE_BRACKETS = 65_000
        const val HOSTILE_BUDGET_MS = 500L
        const val NANOS_PER_MS = 1_000_000L
    }
}
