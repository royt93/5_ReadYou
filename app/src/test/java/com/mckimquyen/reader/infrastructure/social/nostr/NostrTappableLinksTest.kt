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
}
