package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which replies the Nostr feed keeps: only those that continue the author's OWN kept notes (a thread). The signature is never checked
 * by the mapper, so the notes are built by hand. A note is kept when it is not a reply, or when the note it answers is itself kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrFeedMapperThreadTest {
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

    private fun titles(vararg events: NostrEvent): List<String> {
        val counter = AtomicInteger()
        return NostrFeedMapper.toArticles(events.toList(), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }.map { it.title }
    }

    // the thread A <- B <- C, all by the author
    private val a = note(1, 1_700_000_100L, "Part one")
    private val b = reply(2, 1_700_000_200L, "Part two", id(1))
    private val c = reply(3, 1_700_000_300L, "Part three", id(2))

    @Test fun anOwnThread_isKeptWholeNewestFirst() {
        assertEquals(listOf("Part three", "Part two", "Part one"), titles(a, b, c))
    }

    @Test fun aReplyToSomeoneElse_isDropped() {
        val toStranger = reply(9, 1_700_000_150L, "Answer to a stranger", id(99))
        assertEquals(listOf("Part one"), titles(a, toStranger))
    }

    @Test fun aReplyToADroppedNote_isDropped() {
        val toStranger = reply(9, 1_700_000_150L, "Answer to a stranger", id(99))
        val followUp = reply(4, 1_700_000_400L, "Follow-up to my own answer", id(9))
        assertEquals("the follow-up would read with no context", emptyList<String>(), titles(toStranger, followUp))
    }

    @Test fun aMentionOnlyNote_isKeptLikeAnyNote() {
        val quoting = note(5, 1_700_000_500L, "Look at this", listOf("e", id(1), "", "mention"))
        assertEquals(listOf("Look at this", "Part one"), titles(a, quoting))
    }

    @Test(timeout = 5_000)
    fun aLoopOfReplies_endsAndDropsBoth() {
        val first = reply(6, 1_700_000_600L, "Loop one", id(7))
        val second = reply(7, 1_700_000_700L, "Loop two", id(6))
        assertEquals(listOf("Part one"), titles(first, second, a))
    }

    @Test(timeout = 5_000)
    fun aNoteReplyingToItself_isDropped() {
        val selfish = reply(8, 1_700_000_800L, "Answering myself in a loop", id(8))
        assertEquals(listOf("Part one"), titles(selfish, a))
    }

    @Test fun theSameNoteTwice_isKeptOnce() {
        assertEquals(listOf("Part two", "Part one"), titles(a, a, b))
    }

    @Test fun theOrderOfTheInputChangesNothing() {
        val expected = listOf("Part three", "Part two", "Part one")
        assertEquals(expected, titles(a, b, c))
        assertEquals(expected, titles(c, b, a))
        assertEquals(expected, titles(b, c, a))
        assertEquals(expected, titles(c, a, b))
    }

    @Test fun eachPieceOfAThreadHasItsOwnTitleAndLink() {
        val counter = AtomicInteger()
        val articles = NostrFeedMapper.toArticles(listOf(a, b, c), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
        assertEquals(3, articles.size)
        assertEquals(3, articles.map { it.link }.toSet().size)
        assertTrue(articles.all { it.link.startsWith("https://njump.me/note1") })
        assertEquals(listOf("Part three", "Part two", "Part one"), articles.map { it.title })
        assertEquals(listOf(1_700_000_300_000L, 1_700_000_200_000L, 1_700_000_100_000L), articles.map { it.date.time })
    }

    @Test fun aRootOnlyReplyToAKeptNote_isKept() {
        val direct = reply(10, 1_700_000_900L, "Straight to the root", id(1), marker = "root")
        assertEquals(listOf("Straight to the root", "Part one"), titles(a, direct))
    }

    @Test fun atMostTwentyArticles_stillHolds_withThreads() {
        val root = note(100, 1_700_001_000L, "root")
        val chain = (1..25).map { n -> reply(100 + n, 1_700_001_000L + n, "piece $n", id(100 + n - 1)) }
        val articles = NostrFeedMapper.toArticles(listOf(root) + chain, "feed-1", 7, now)
        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)
        assertEquals("piece 25", articles.first().title)
    }

    // FRONT-02y: twenty-five NEWER answers to people we do not follow must not push an older own thread out: the cut comes after the filter.
    @Test fun aThreadBehindManyAnswersToOthers_isStillShown() {
        val strangers = (0 until 25).map { n -> reply(200 + n, 1_700_005_000L + n, "answer $n", id(900 + n)) }
        assertEquals(listOf("Part three", "Part two", "Part one"), titles(*(strangers + listOf(a, b, c)).toTypedArray()))
    }

    // ---- FRONT-02z: the one rule that says which notes are shown, shared by the feed and by the relay client.

    /** A mix: two titled notes, a blank one, an answer to a stranger, an own thread piece and another blank one (ids 1..6, oldest first). */
    private val mix: List<NostrEvent> = listOf(
        note(1, 1_700_000_100L, "Titled one"),
        note(2, 1_700_000_200L, "   "),
        reply(3, 1_700_000_300L, "Answer to a stranger", id(999)),
        note(4, 1_700_000_400L, "Titled two"),
        reply(5, 1_700_000_500L, "Own thread piece", id(4)),
        note(6, 1_700_000_600L, "\n  \t"),
    )

    @Test fun visibleNotes_dropsRepliesToOthersAndBlankNotes() {
        val shown = NostrFeedMapper.visibleNotes(mix)
        assertEquals(listOf(id(1), id(4), id(5)), shown.map { it.id })
    }

    @Test fun visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles() {
        val counter = AtomicInteger()
        val articles = NostrFeedMapper.toArticles(mix, "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
        val shownLinks = NostrFeedMapper.visibleNotes(mix).sortedByDescending { it.createdAt }
            .map { NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(it.id)) }
        assertEquals(shownLinks, articles.map { it.link })
        assertEquals(listOf("Own thread piece", "Titled two", "Titled one"), articles.map { it.title })
    }

    // Before FRONT-02z the cut to twenty came first and the blank notes were dropped after it, so a few blank notes among the newest
    // twenty left fewer than twenty articles: here 22 real notes and 3 NEWER blank ones must still give twenty real articles.
    @Test fun aBlankNote_isNotShown_andDoesNotTakeAPlaceAmongTheTwenty() {
        val real = (0 until 22).map { n -> note(300 + n, 1_700_000_000L + n, "real $n") }
        val blanks = (0 until 3).map { n -> note(400 + n, 1_700_009_000L + n, "  ") }
        val articles = NostrFeedMapper.toArticles(real + blanks, "feed-1", 7, now)
        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)
        assertEquals("real 21", articles.first().title)
        assertTrue(articles.none { it.title.isBlank() })
    }

    @Test fun aReplyWhoseTargetIdIsBroken_isDropped() {
        val broken = note(11, 1_700_001_100L, "Answers nothing findable", listOf("e", "not-an-id", "", "reply"))
        assertEquals(listOf("Part one"), titles(a, broken))
    }

    // An id written in capitals is not the lower-case form NIP-01 requires, so a reply naming it names nothing, even though a note
    // with that very id is in the list and could be linked.
    @Test fun aReplyNamingATargetInCapitals_isDropped() {
        val upperId = "%064X".format(0xabc)
        val target = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", upperId); put("pubkey", author); put("created_at", 1_700_001_200L); put("kind", 1)
                    put("tags", org.json.JSONArray()); put("content", "Id in capitals"); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        val answer = reply(12, 1_700_001_300L, "Answers the capitals note", upperId)
        assertEquals(listOf("Id in capitals"), titles(target, answer))
    }

    // Nothing in the rule looks at dates: an answer stamped BEFORE the note it answers (clocks differ) is still part of the thread.
    @Test fun aReplyDatedBeforeItsTarget_isStillKept() {
        val late = note(13, 1_700_002_000L, "Posted late")
        val early = reply(14, 1_700_001_500L, "Answer with an earlier date", id(13))
        assertEquals(listOf("Posted late", "Answer with an earlier date"), titles(late, early))
        assertEquals(listOf("Posted late", "Answer with an earlier date"), titles(early, late))
    }
}
