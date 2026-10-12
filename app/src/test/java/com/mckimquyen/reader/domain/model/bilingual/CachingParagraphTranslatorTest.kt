package com.mckimquyen.reader.domain.model.bilingual

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CachingParagraphTranslatorTest {

    private class Fake(private val answer: (String, String) -> TranslationResult) : ParagraphTranslator {
        val calls = AtomicInteger()
        override suspend fun translate(text: String, target: String): TranslationResult {
            calls.incrementAndGet()
            return answer(text, target)
        }
    }

    private fun upper() = Fake { text, _ -> TranslationResult.Translated(text.uppercase()) }

    @Test
    fun sameParagraphAndLanguage_isTranslatedOnce() = runTest {
        val fake = upper()
        val cache = CachingParagraphTranslator(fake)

        assertEquals(TranslationResult.Translated("HELLO"), cache.translate("hello", "vi"))
        assertEquals(TranslationResult.Translated("HELLO"), cache.translate("hello", "vi"))

        assertEquals(1, fake.calls.get())
    }

    @Test
    fun theSameParagraphInAnotherLanguage_isNotServedFromTheWrongCacheEntry() = runTest {
        val fake = Fake { text, target -> TranslationResult.Translated("$target:$text") }
        val cache = CachingParagraphTranslator(fake)

        assertEquals(TranslationResult.Translated("vi:hello"), cache.translate("hello", "vi"))
        assertEquals(TranslationResult.Translated("de:hello"), cache.translate("hello", "de"))
        assertEquals(2, fake.calls.get())
    }

    @Test
    fun failures_areNotCached_soALaterRetryCanSucceed() = runTest {
        var fail = true
        val fake = Fake { text, _ ->
            if (fail) TranslationResult.Failed else TranslationResult.Translated(text.uppercase())
        }
        val cache = CachingParagraphTranslator(fake)

        assertEquals(TranslationResult.Failed, cache.translate("hello", "vi"))
        fail = false
        assertEquals(TranslationResult.Translated("HELLO"), cache.translate("hello", "vi"))
        assertEquals(2, fake.calls.get())
    }

    @Test
    fun sameLanguageResult_isCachedToo_soDetectionRunsOnce() = runTest {
        val fake = Fake { _, _ -> TranslationResult.SameLanguage }
        val cache = CachingParagraphTranslator(fake)

        cache.translate("xin chao", "vi")
        cache.translate("xin chao", "vi")

        assertEquals(1, fake.calls.get())
    }

    @Test
    fun oldestEntryIsEvicted_whenCapacityIsExceeded() = runTest {
        val fake = upper()
        val cache = CachingParagraphTranslator(fake, capacity = 2)

        cache.translate("a1", "vi")
        cache.translate("b2", "vi")
        cache.translate("c3", "vi") // evicts a1
        assertEquals(3, fake.calls.get())

        cache.translate("b2", "vi") // still cached
        assertEquals(3, fake.calls.get())
        cache.translate("a1", "vi") // evicted -> translated again
        assertEquals(4, fake.calls.get())
    }

    @Test
    fun concurrentRequestsForTheSameParagraph_shareOneTranslation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val slow = object : ParagraphTranslator {
            override suspend fun translate(text: String, target: String): TranslationResult {
                calls.incrementAndGet()
                gate.await()
                return TranslationResult.Translated(text.uppercase())
            }
        }
        val cache = CachingParagraphTranslator(slow)

        val first = async { cache.translate("hello", "vi") }
        val second = async { cache.translate("hello", "vi") }
        advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(TranslationResult.Translated("HELLO"), first.await())
        assertEquals(TranslationResult.Translated("HELLO"), second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun cancellingTheCaller_doesNotPoisonTheCache() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val slow = object : ParagraphTranslator {
            override suspend fun translate(text: String, target: String): TranslationResult {
                if (first) {
                    first = false
                    gate.await() // never completes for the cancelled caller
                }
                return TranslationResult.Translated(text.uppercase())
            }
        }
        val cache = CachingParagraphTranslator(slow)

        val cancelled = async { cache.translate("hello", "vi") }
        advanceUntilIdle()
        cancelled.cancel()
        advanceUntilIdle()

        assertTrue(cancelled.isCancelled)
        assertEquals(TranslationResult.Translated("HELLO"), cache.translate("hello", "vi"))
    }

    @Test
    fun shouldTranslate_skipsEmptyShortAndNonLetterParagraphs() {
        assertFalse(ParagraphFilter.shouldTranslate(""))
        assertFalse(ParagraphFilter.shouldTranslate("   "))
        assertFalse(ParagraphFilter.shouldTranslate("42"))
        assertFalse(ParagraphFilter.shouldTranslate("— — —"))
        assertFalse(ParagraphFilter.shouldTranslate("OK"))
        assertTrue(ParagraphFilter.shouldTranslate("The actor portrays the host."))
        assertTrue(ParagraphFilter.shouldTranslate("今日はいい天気ですね"))
    }

    @Test
    fun shouldTranslate_skipsUrlOnlyParagraphs() {
        assertFalse(ParagraphFilter.shouldTranslate("https://example.com/a/b?x=1"))
        assertFalse(ParagraphFilter.shouldTranslate("someone@example.com"))
    }
}
