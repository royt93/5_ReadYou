package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrFeedMapperTest {
    private val now = 1_800_000_000_000L
    private val npub = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"

    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json))
    private fun map(vararg json: String, nowMillis: Long = now): List<com.mckimquyen.reader.domain.model.article.Article> {
        val counter = AtomicInteger()
        return NostrFeedMapper.toArticles(json.map { ev(it) }, "feed-1", 7, nowMillis) { "u${counter.incrementAndGet()}" }
    }

    @Test fun aPlainNote_becomesAnArticle() {
        val a = map(NostrTestEvents.PLAIN).single()
        assertEquals("Hello nostr, first note", a.title)
        assertEquals("Hello nostr, first note", a.rawDescription)
        assertEquals("Hello nostr, first note", a.shortDescription)
        assertEquals("feed-1", a.feedId)
        assertEquals(7, a.accountId)
        assertEquals(1_700_000_300_000L, a.date.time)
        assertTrue(a.isUnread)
        assertEquals(null, a.author)
        assertEquals(null, a.img)
        assertEquals("7\$u1", a.id)
    }

    @Test fun theLink_isTheNjumpAddressOfTheNote() {
        assertEquals(
            "https://njump.me/note1cqneqau89xzdllkft58klqecyrjdte7cgv5svy9u92yc9lxewdlqw0n65r",
            map(NostrTestEvents.PLAIN).single().link,
        )
    }

    @Test fun htmlInTheContent_isEscaped_soNothingIsInterpretedAsMarkup() {
        val a = map(NostrTestEvents.RICH).single()
        assertFalse(a.rawDescription.contains("<script>"))
        assertTrue(a.rawDescription.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(a.rawDescription.contains("&amp;"))
        assertTrue(a.rawDescription.contains("&quot;"))
        assertTrue(a.rawDescription.contains("<br>"))
        assertTrue("the visible title keeps the typed text", a.title.contains("<script>alert(1)</script>"))
        assertTrue(a.title.startsWith("Quote \" backslash"))
    }

    @Test fun aLongNote_getsAnEightyCharacterTitleWithAnEllipsis_andTheFullBodyIsKept() {
        val a = map(NostrTestEvents.LONG).single()
        // 80 code points are "word " x16; the trailing space is trimmed before the ellipsis.
        assertEquals("word ".repeat(15) + "word" + "\u2026", a.title)
        assertEquals("word ".repeat(40).trim(), a.rawDescription)
        assertTrue(a.shortDescription.length <= 110)
        assertTrue(a.shortDescription.startsWith("word word word"))
    }

    @Test fun anApostropheAndAWindowsLineBreak_areStoredAsEntityAndOneBreak() {
        val a = map(NostrTestEvents.QUOTES).single()
        assertEquals("it&#39;s<br>line2", a.rawDescription)
        assertEquals("it's line2", a.title)
    }

    @Test fun repliesAndBlankNotesAreSkipped() {
        assertEquals(listOf("Hello nostr, first note"), map(NostrTestEvents.PLAIN, NostrTestEvents.REPLY, NostrTestEvents.EMPTY).map { it.title })
    }

    @Test fun theSameNoteTwice_isKeptOnce() = assertEquals(1, map(NostrTestEvents.PLAIN, NostrTestEvents.PLAIN).size)

    @Test fun notes_areSortedNewestFirst() {
        val titles = map(NostrTestEvents.OLDER, NostrTestEvents.PLAIN, NostrTestEvents.RICH).map { it.title.take(5) }
        assertEquals(listOf("Hello", "Quote", "Older"), titles)
    }

    @Test fun aNoteDatedInTheFuture_isClampedToNow() {
        val a = map(NostrTestEvents.FUTURE, nowMillis = now).single()
        assertEquals(now, a.date.time)
    }

    @Test fun aPastNote_isNotClamped() = assertEquals(1_700_000_300_000L, map(NostrTestEvents.PLAIN, nowMillis = now).single().date.time)

    @Test fun atMostTwentyArticles_areKept_newestFirst() {
        val a = map(*NostrTestEvents.MANY.toTypedArray())
        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, a.size)
        assertEquals(1_700_001_029_000L, a.first().date.time)
        assertEquals(1_700_001_010_000L, a.last().date.time)
    }

    @Test fun feedNameAndUrl_useTheNpub() {
        assertEquals("Nostr \u00b7 npub1mlc\u20267a0a", NostrFeedMapper.feedName(npub))
        assertEquals("nostr:$npub", NostrFeedMapper.feedUrl(npub))
        assertEquals("nostr:$npub", NostrFeedMapper.feedUrl(npub.uppercase()))
    }
}
