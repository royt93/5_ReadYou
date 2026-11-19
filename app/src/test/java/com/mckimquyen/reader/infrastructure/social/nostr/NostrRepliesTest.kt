package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every reply is shown, with one last line "<mark> <link>" that points at the note it answers on njump. The link is built by the app from
 * a checked id (never taken from the note text) and nothing is downloaded. The notes are built by hand (the mapper never checks the signature).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRepliesTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private fun id(n: Int) = "%064x".format(n)
    private fun note(n: Int, createdAt: Long, content: String, vararg tags: List<String>): NostrEvent =
        requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", author); put("created_at", createdAt); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
    private fun reply(n: Int, createdAt: Long, content: String, to: String, marker: String = "reply") =
        note(n, createdAt, content, listOf("e", to, "", marker))

    private fun articlesOf(vararg events: NostrEvent) = NostrFeedMapper.toArticles(events.toList(), "feed-1", 7, now)
    private fun njump(targetId: String) = NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(targetId))

    @Test fun aReplyToAStranger_isShown_withALinkToTheNoteItAnswers() {
        val a = articlesOf(reply(1, 1_700_000_100L, "Answer to a stranger", id(99))).single()
        assertEquals("Answer to a stranger", a.title)
        assertTrue(a.rawDescription, a.rawDescription.endsWith("<br>${NostrFeedMapper.REPLY_MARK} <a href=\"${njump(id(99))}\">${njump(id(99))}</a>"))
    }

    @Test fun anOwnReply_isShown_withALinkToItsTarget() {
        val articles = articlesOf(note(1, 1_700_000_100L, "Part one"), reply(2, 1_700_000_200L, "Part two", id(1)))
        assertEquals(listOf("Part two", "Part one"), articles.map { it.title })
        assertTrue(articles[0].rawDescription.contains("href=\"${njump(id(1))}\""))
        assertFalse(articles[1].rawDescription.contains("<a "))
    }

    @Test fun theTarget_isTheReplyMarker_thenRoot_thenTheLastOldStyleTag() {
        val both = note(1, 1_700_000_100L, "both", listOf("e", id(10), "", "root"), listOf("e", id(11), "", "reply"))
        val rootOnly = note(2, 1_700_000_200L, "root only", listOf("e", id(12), "", "root"))
        val old = note(3, 1_700_000_300L, "old style", listOf("e", id(13)), listOf("e", id(14)))
        assertEquals(njump(id(11)), NostrFeedMapper.replyTargetUrl(both))
        assertEquals(njump(id(12)), NostrFeedMapper.replyTargetUrl(rootOnly))
        assertEquals(njump(id(14)), NostrFeedMapper.replyTargetUrl(old))
    }

    @Test fun aReplyWhoseTargetIsBroken_isShown_withoutALink() {
        val broken = note(1, 1_700_000_100L, "Answers nothing findable", listOf("e", "not-an-id", "", "reply"))
        assertNull(NostrFeedMapper.replyTargetUrl(broken))
        val a = articlesOf(broken).single()
        assertEquals("Answers nothing findable", a.rawDescription)
    }

    @Test fun aMentionOnlyNote_hasNoReplyLine() {
        val quoting = note(1, 1_700_000_100L, "Look at this", listOf("e", id(9), "", "mention"))
        assertNull(NostrFeedMapper.replyTargetUrl(quoting))
        assertEquals("Look at this", articlesOf(quoting).single().rawDescription)
    }

    @Test fun anOrdinaryNote_hasNoReplyLine() {
        val plain = note(1, 1_700_000_100L, "Just a note", listOf("t", "news"))
        assertNull(NostrFeedMapper.replyTargetUrl(plain))
        assertFalse(articlesOf(plain).single().rawDescription.contains(NostrFeedMapper.REPLY_MARK))
    }

    @Test fun theReplyLine_isAnEscapedAnchorAfterABreak() {
        val raw = articlesOf(reply(1, 1_700_000_100L, "Hi & <b>bye</b>", id(5))).single().rawDescription
        assertTrue(raw, raw.startsWith("Hi &amp; &lt;b&gt;bye&lt;/b&gt;<br>${NostrFeedMapper.REPLY_MARK} <a href=\"https://njump.me/note1"))
        // What the reading page reads (HtmlToComposable, "abs:href"): exactly the one address the app built.
        assertEquals(njump(id(5)), Jsoup.parse(raw, "https://njump.me/").selectFirst("a")!!.attr("abs:href"))
        assertEquals(1, Jsoup.parse(raw).select("a").size)
    }

    @Test fun aReply_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink() {
        val a = articlesOf(reply(1, 1_700_000_100L, "Short answer", id(5))).single()
        assertEquals("Short answer", a.title)
        assertEquals("Short answer", a.shortDescription)
        assertEquals(njump(id(1)), a.link)
    }

    @Test fun aBlankReply_isStillNotShown() {
        assertTrue(articlesOf(reply(1, 1_700_000_100L, "   ", id(5))).isEmpty())
    }

    @Test fun repliesAndOrdinaryNotes_shareTheTwentyNewestFirst() {
        val ordinary = (0 until 10).map { n -> note(100 + n, 1_700_000_000L + n, "ordinary $n") }
        val replies = (0 until 15).map { n -> reply(200 + n, 1_700_001_000L + n, "reply $n", id(900 + n)) }
        val articles = articlesOf(*(ordinary + replies).toTypedArray())
        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)
        assertEquals("reply 14", articles.first().title)
        assertEquals("ordinary 5", articles.last().title)
    }
}
