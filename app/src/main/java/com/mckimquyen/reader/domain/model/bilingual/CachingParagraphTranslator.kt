package com.mckimquyen.reader.domain.model.bilingual

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Caches results per (paragraph, target language) and shares one in-flight translation between callers
 * asking for the same paragraph. Failures are not cached so a retry can succeed. A cancelled caller
 * (article changed, item scrolled away) never leaves a stuck entry behind: the next caller takes over.
 */
class CachingParagraphTranslator(
    private val delegate: ParagraphTranslator,
    private val capacity: Int = DEFAULT_CAPACITY,
) : ParagraphTranslator {

    private val lock = Any()

    // Access-ordered, so the eldest entry is the least recently used.
    private val cache = object : LinkedHashMap<String, TranslationResult>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult>?) =
            size > capacity
    }
    private val inFlight = HashMap<String, CompletableDeferred<TranslationResult>>()

    override suspend fun translate(text: String, target: String): TranslationResult {
        val key = "$target$KEY_SEPARATOR$text"
        while (true) {
            var owned: CompletableDeferred<TranslationResult>? = null
            var running: CompletableDeferred<TranslationResult>? = null
            synchronized(lock) {
                cache[key]?.let { return it }
                running = inFlight[key]
                if (running == null) {
                    owned = CompletableDeferred<TranslationResult>().also { inFlight[key] = it }
                }
            }
            owned?.let { return runOwned(key, it, text, target) }

            val shared = try {
                running!!.await()
            } catch (e: CancellationException) {
                // Either this caller was cancelled (propagate) or the owner was (take over on the next loop).
                currentCoroutineContext().ensureActive()
                null
            }
            if (shared != null) return shared
        }
    }

    private suspend fun runOwned(
        key: String,
        owned: CompletableDeferred<TranslationResult>,
        text: String,
        target: String,
    ): TranslationResult {
        val result = try {
            delegate.translate(text, target)
        } catch (e: CancellationException) {
            synchronized(lock) { inFlight.remove(key) }
            owned.cancel(e)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            TranslationResult.Failed
        }
        synchronized(lock) {
            inFlight.remove(key)
            if (result !is TranslationResult.Failed) cache[key] = result
        }
        owned.complete(result)
        return result
    }

    private companion object {
        const val DEFAULT_CAPACITY = 500
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val KEY_SEPARATOR = '\u0000'
    }
}
