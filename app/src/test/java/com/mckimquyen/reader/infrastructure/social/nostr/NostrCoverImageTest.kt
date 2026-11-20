package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cover image of a Nostr note: the first https link in the text that ends in an image extension. The text is untrusted, so only
 * the address is taken; nothing is downloaded here. The notes are built by hand (the mapper never checks the signature).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrCoverImageTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val limit = NostrFeedMapper.MAX_COVER_URL_LENGTH

    private fun coverOf(content: String): String? {
        val event = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", "%064x".format(1)); put("pubkey", author); put("created_at", 1_700_000_000L); put("kind", 1)
                    put("tags", org.json.JSONArray()); put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        return NostrFeedMapper.toArticles(listOf(event), "feed-1", 7, now).single().img
    }

    /** A link of exactly [length] characters: the address, then filler, then ".jpg". */
    private fun linkOfLength(length: Int): String {
        val head = "https://x.example/"
        return head + "a".repeat(length - head.length - ".jpg".length) + ".jpg"
    }

    @Test fun aHttpsImageLink_isTheCover() {
        assertEquals("https://img.example/a.jpg", NostrFeedMapper.coverImageUrl("look https://img.example/a.jpg here"))
    }

    @Test fun everyImageExtension_isRecognised_inAnyCase() {
        for (ext in listOf("jpg", "jpeg", "png", "gif", "webp")) {
            assertEquals("https://x.example/a.$ext", NostrFeedMapper.coverImageUrl("https://x.example/a.$ext"))
        }
        assertEquals("https://x.example/A.PNG", NostrFeedMapper.coverImageUrl("https://x.example/A.PNG"))
    }

    @Test fun anHttpLink_isNotACover() {
        assertNull(NostrFeedMapper.coverImageUrl("http://x.example/a.jpg"))
        assertNull(NostrFeedMapper.coverImageUrl("ftp://x.example/a.png"))
    }

    @Test fun aLinkThatIsNotAnImage_isNotACover() {
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/page"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.pdf"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.png.exe"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.pngx"))
    }

    @Test fun theFirstImageLink_wins() {
        assertEquals(
            "https://x.example/one.jpg",
            NostrFeedMapper.coverImageUrl("https://x.example/one.jpg and https://x.example/two.png"),
        )
    }

    @Test fun aQueryAfterTheExtension_isKept() {
        assertEquals("https://x.example/a.jpg?w=100&h=2", NostrFeedMapper.coverImageUrl("see https://x.example/a.jpg?w=100&h=2 ok"))
    }

    @Test fun aTooLongLink_isSkipped_andTheNextOneIsUsed() {
        val tooLong = linkOfLength(limit + 1)
        assertEquals("https://y.example/short.png", NostrFeedMapper.coverImageUrl("$tooLong https://y.example/short.png"))
        assertNull(NostrFeedMapper.coverImageUrl(tooLong))
    }

    @Test fun aLinkOfExactlyTheLimit_isKept_andOneMoreIsSkipped() {
        val exact = linkOfLength(limit)
        assertEquals(limit, exact.length)
        assertEquals(exact, NostrFeedMapper.coverImageUrl(exact))
        assertNull(NostrFeedMapper.coverImageUrl(linkOfLength(limit + 1)))
    }

    @Test fun aNoteWithoutALink_hasNoCover() {
        assertNull(NostrFeedMapper.coverImageUrl(""))
        assertNull(NostrFeedMapper.coverImageUrl("just words, no link"))
    }

    @Test fun toArticles_setsTheCover_onlyForANoteWithAnImageLink() {
        assertEquals("https://img.example/a.jpg", coverOf("photo https://img.example/a.jpg"))
        assertNull(coverOf("no image here"))
    }

    @Test fun theCover_comesFromTheTextAsTyped_notFromTheEscapedHtml() {
        // The stored body escapes "&" to "&amp;"; the address must keep the "&" the author typed.
        assertEquals("https://x.example/a.jpg?a=1&b=2", coverOf("https://x.example/a.jpg?a=1&b=2"))
    }

    @Test fun aLinkAtTheEndOfALine_isStillACover() {
        // A new line becomes "<br>" in the stored body; the link must be found in the text as typed, where the line break is a space.
        assertEquals("https://x.example/a.jpg", coverOf("look\nhttps://x.example/a.jpg\nnext line"))
    }

    // ---- FRONT-02ag: punctuation right after the image address must not lose the cover image, and must not be part of the address.

    @Test fun aTrailingDotAfterTheExtension_keepsTheCover_withoutTheDot() {
        assertEquals("https://x.example/a.jpg", NostrFeedMapper.coverImageUrl("see https://x.example/a.jpg."))
    }

    @Test fun everySentencePunctuationAfterTheExtension_keepsTheCover_withoutIt() {
        for (mark in listOf(".", ",", ";", ":", "!", "?", ")", "]", "}", "'", "\"", "!?", ").", "\".")) {
            assertEquals("after '$mark'", "https://x.example/a.png", NostrFeedMapper.coverImageUrl("(look https://x.example/a.png$mark next"))
        }
    }

    @Test fun punctuationAfterTheQuery_isNotPartOfTheAddress() {
        assertEquals("https://x.example/a.jpg?w=1", NostrFeedMapper.coverImageUrl("see https://x.example/a.jpg?w=1."))
        assertEquals("https://x.example/a.jpg?w=1&h=2", NostrFeedMapper.coverImageUrl("(https://x.example/a.jpg?w=1&h=2)"))
    }

    @Test fun aLinkInBracketsOrQuotes_isACover() {
        assertEquals("https://x.example/a.png", NostrFeedMapper.coverImageUrl("(https://x.example/a.png)"))
        assertEquals("https://x.example/a.png", NostrFeedMapper.coverImageUrl("\"https://x.example/a.png\""))
    }

    @Test fun punctuationFollowedByMoreLetters_isStillNotACover() {
        // The extension must END the address (up to punctuation and a space): these are other file names.
        for (text in listOf("https://x.example/a.png.exe", "https://x.example/a.pngx", "https://x.example/a.jpg,and", "https://x.example/a.jpg.bak")) {
            assertNull(text, NostrFeedMapper.coverImageUrl(text))
        }
    }

    @Test fun aLinkOfExactlyTheLimitFollowedByADot_isKept_andOneMoreIsStillSkipped() {
        val exact = linkOfLength(limit)
        assertEquals(exact, NostrFeedMapper.coverImageUrl("$exact."))
        assertNull(NostrFeedMapper.coverImageUrl(linkOfLength(limit + 1) + "."))
    }

    @Test fun aSentenceWithAnImageLinkAtTheEnd_getsACoverThroughToArticles() {
        assertEquals("https://img.example/photo.jpg", coverOf("Look at this photo https://img.example/photo.jpg."))
    }

    // ---- FRONT-02af: a hostile note must not slow the sync. The old pattern is rebuilt here, unbounded, only to measure against.

    private val unboundedPattern = Regex("""https://\S+?\.(?:jpe?g|png|gif|webp)(?:[?#]\S*)?(?=\s|$)""", RegexOption.IGNORE_CASE)

    /** The fastest of [runs] timings of [block], after a few warm-up runs, in nanoseconds. */
    private fun fastestNanos(block: () -> Unit): Long {
        repeat(WARMUP_RUNS) { block() }
        return (1..TIMED_RUNS).minOf { val t = System.nanoTime(); block(); System.nanoTime() - t }
    }

    @Test fun aHostileNote_isScannedMuchFasterThanTheUnboundedPattern() {
        val hostile = "https://".repeat(HOSTILE_LENGTH / "https://".length)
        val bounded = fastestNanos { NostrFeedMapper.coverImageUrl(hostile) }
        val unbounded = fastestNanos { unboundedPattern.findAll(hostile).toList() }
        assertTrue("bounded=${bounded / NANOS_PER_MS} ms, unbounded=${unbounded / NANOS_PER_MS} ms", unbounded >= bounded * MIN_SPEEDUP)
    }

    @Test fun aHostileNote_stillGivesAnArticleWithoutACover() {
        val hostile = "https://".repeat(HOSTILE_LENGTH / "https://".length)
        assertNull(coverOf(hostile))
    }

    @Test fun aValidLinkInsideAVeryLongToken_isStillFound() {
        val token = "https://" + "a".repeat(limit + 100) + "https://y.example/inner.png"
        assertEquals("https://y.example/inner.png", NostrFeedMapper.coverImageUrl(token))
    }

    private companion object {
        const val HOSTILE_LENGTH = 65_000
        const val WARMUP_RUNS = 3
        const val TIMED_RUNS = 5
        const val MIN_SPEEDUP = 3
        const val NANOS_PER_MS = 1_000_000L
    }
}
