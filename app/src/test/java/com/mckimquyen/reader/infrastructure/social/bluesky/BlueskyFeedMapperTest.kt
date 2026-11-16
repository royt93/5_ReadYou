package com.mckimquyen.reader.infrastructure.social.bluesky

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.HANDLE
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.NOW
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.OTHER_DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.array
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.embedExternal
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.post
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.repost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyFeedMapperTest {
    private fun map(vararg items: String, did: String = DID, now: Long = NOW): List<Article> {
        val counter = AtomicInteger()
        return BlueskyFeedMapper.toArticles(did, array(*items), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
    }

    // ---- address and name
    @Test fun feedUrl_isLowercaseForAHandle_andKeepsTheCaseOfADid() {
        assertEquals("bsky:alice.bsky.social", BlueskyFeedMapper.feedUrl("Alice.Bsky.Social"))
        assertEquals("bsky:did:plc:AbC123", BlueskyFeedMapper.feedUrl("did:plc:AbC123"))
    }

    @Test fun feedName_usesTheDisplayNameBehindThePrefix() =
        assertEquals("Bluesky · Alice A", BlueskyFeedMapper.feedName("alice.bsky.social", "Alice A"))

    @Test fun feedName_withoutADisplayName_usesTheHandle() {
        assertEquals("Bluesky · @alice.bsky.social", BlueskyFeedMapper.feedName("alice.bsky.social", null))
        assertEquals("Bluesky · @alice.bsky.social", BlueskyFeedMapper.feedName("alice.bsky.social", ""))
    }

    @Test fun cleanDisplayName_dropsControlCharsAndCollapsesSpaces() {
        assertEquals("a b c", BlueskyFeedMapper.cleanDisplayName("  a\n\tb\u0007  c  "))
        assertEquals("x y", BlueskyFeedMapper.cleanDisplayName("x y"))
    }

    @Test fun cleanDisplayName_isNullWhenNothingUsableIsLeft() {
        assertNull(BlueskyFeedMapper.cleanDisplayName(null))
        assertNull(BlueskyFeedMapper.cleanDisplayName("  \n\u0007 "))
    }

    @Test fun cleanDisplayName_cutsAtFortyCodePoints_notInTheMiddleOfAnEmoji() {
        val emoji = "😀"
        assertEquals("a".repeat(39) + emoji, BlueskyFeedMapper.cleanDisplayName("a".repeat(39) + emoji + "zzz"))
        assertEquals("a".repeat(40), BlueskyFeedMapper.cleanDisplayName("a".repeat(45)))
    }

    // ---- icon
    @Test fun iconUrl_acceptsHttpsOnTheBlueskyHosts() {
        assertEquals("https://cdn.bsky.app/img/avatar/x@jpeg", BlueskyFeedMapper.iconUrl("https://cdn.bsky.app/img/avatar/x@jpeg"))
        assertEquals("https://a.bsky.social/p.png", BlueskyFeedMapper.iconUrl("https://a.bsky.social/p.png"))
    }

    @Test fun iconUrl_refusesOtherSchemesHostsAndLookalikes() {
        listOf(
            null, "", "http://cdn.bsky.app/x", "https://evil.example/x", "https://cdn.bsky.app.evil.example/x",
            "https://evilbsky.app/x", "https://user@cdn.bsky.app/x", "javascript:alert(1)", "https://cdn.bsky.app:8443/x",
        ).forEach { assertNull(it, BlueskyFeedMapper.iconUrl(it)) }
    }

    // ---- which posts
    @Test fun anOwnPost_becomesAnArticle_linkedByDidAndRkey() {
        val a = map(post("3k1", "Hello world")).single()
        assertEquals("Hello world", a.title)
        assertEquals("https://bsky.app/profile/$DID/post/3k1", a.link)
        assertEquals("feed-1", a.feedId)
        assertEquals(7, a.accountId)
    }

    @Test fun aRepost_isLeftOut() = assertTrue(map(repost("r1"), post("3k1", "mine")).map { it.title } == listOf("mine"))

    // A repost of the account's OWN earlier post carries the account's own DID as author, so only `reason` can tell it apart.
    @Test fun aRepostOfTheAccountsOwnPost_isLeftOut_evenThoughTheAuthorIsTheSame() =
        assertEquals(listOf("mine"), map(repost("r9", did = DID), post("3k1", "mine")).map { it.title })

    @Test fun theSamePostSeenTwice_isListedOnce() {
        val out = map(post("3k1", "same"), post("3k1", "same"))
        assertEquals(1, out.size)
        assertEquals(listOf("https://bsky.app/profile/$DID/post/3k1"), out.map { it.link })
    }

    @Test fun aPostByAnotherDid_isLeftOut_evenWithoutAReason() =
        assertEquals(listOf("mine"), map(post("x1", "stranger", did = OTHER_DID), post("3k1", "mine")).map { it.title })

    @Test fun aPostThatIsAReply_isLeftOut() =
        assertEquals(listOf("mine"), map(post("r2", "reply", recordExtra = ""","reply":{"root":{"uri":"u"}}"""), post("3k1", "mine")).map { it.title })

    @Test fun anEmptyOrBlankPost_isLeftOut() =
        assertEquals(emptyList<String>(), map(post("e1", ""), post("e2", "   ")).map { it.title })

    @Test fun duplicates_arePostedOnce_newestFirst_andAtMostTwentyAreKept() {
        val many = (1..30).map { post("p$it", "post $it", createdAt = "2026-11-%02dT08:00:00.000Z".format(1 + it % 28)) }
        val out = map(*(many + post("p1", "post 1 again")).toTypedArray())
        assertEquals(20, out.size)
        assertEquals(out.map { it.date }.sortedDescending(), out.map { it.date })
        assertEquals(out.size, out.map { it.link }.distinct().size)
    }

    @Test fun aMalformedItem_isSkipped_notFatal() {
        val items = org.json.JSONArray("""[42, null, {"post":"x"}, {"post":{"uri":"u"}}, ${post("3k1", "ok")}]""")
        val out = BlueskyFeedMapper.toArticles(DID, items, "f", 1, NOW)
        assertEquals(listOf("ok"), out.map { it.title })
    }

    // ---- text and dates
    @Test fun theText_isEscapedBeforeItIsStoredAsHtml() {
        val a = map(post("h1", "<b>x</b> & \"y\" 'z'\nnext")).single()
        assertEquals("&lt;b&gt;x&lt;/b&gt; &amp; &quot;y&quot; &#39;z&#39;<br>next", a.rawDescription)
    }

    @Test fun theTitle_isTheFirstEightyCodePointsOfTheBody() {
        val a = map(post("t1", "a".repeat(100))).single()
        assertEquals("a".repeat(80) + "…", a.title)
    }

    @Test fun aPostDatedInTheFuture_isClampedToNow() {
        val a = map(post("f1", "future", createdAt = "2999-01-01T00:00:00.000Z")).single()
        assertEquals(NOW, a.date.time)
    }

    @Test fun anUnreadableDate_fallsBackToNow() {
        val a = map(post("d1", "no date", createdAt = "not a date")).single()
        assertEquals(NOW, a.date.time)
    }

    // ---- embeds: one line of text, nothing downloaded
    @Test fun anImageEmbed_addsOneLine_andNeverAnImageAddress() {
        val a = map(post("i1", "pic", embed = BlueskyTestPayloads.EMBED_IMAGES)).single()
        assertTrue(a.rawDescription.endsWith("<br>[image]"))
        assertFalse(a.rawDescription.contains("cdn.bsky.app"))
    }

    @Test fun aVideoAndAQuoteEmbed_addOneWordEach() {
        assertTrue(map(post("v1", "v", embed = BlueskyTestPayloads.EMBED_VIDEO)).single().rawDescription.endsWith("<br>[video]"))
        assertTrue(map(post("q1", "q", embed = BlueskyTestPayloads.EMBED_QUOTE)).single().rawDescription.endsWith("<br>[quoted post]"))
    }

    @Test fun anExternalLinkEmbed_showsTheAddress_escaped_andOnlyForHttps() {
        val ok = map(post("x1", "l", embed = embedExternal("https://example.test/a?b=1&c=2"))).single()
        assertTrue(ok.rawDescription, ok.rawDescription.endsWith("<br>[link] https://example.test/a?b=1&amp;c=2"))
        val bad = map(post("x2", "l", embed = embedExternal("javascript:alert(1)"))).single()
        assertTrue(bad.rawDescription, bad.rawDescription.endsWith("<br>[link]"))
        assertFalse(bad.rawDescription.contains("javascript"))
    }

    @Test fun aPostWithOnlyAnEmbed_andNoText_isLeftOut() =
        assertEquals(emptyList<String>(), map(post("o1", "", embed = BlueskyTestPayloads.EMBED_IMAGES)).map { it.title })
}
