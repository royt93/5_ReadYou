package com.mckimquyen.reader.ui.page.rpg

import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import javax.inject.Inject
import javax.inject.Singleton

/** What the end-of-article quiz card shows for the current article (RPG-02). */
sealed interface QuizUiState {
    /** The card has not been asked for yet (the reader has not reached the end). */
    object Hidden : QuizUiState
    object AwaitingConsent : QuizUiState
    object ConsentDeclined : QuizUiState
    object Loading : QuizUiState
    data class Ready(val quiz: QuizQuestion) : QuizUiState
    data class Failed(val reason: AiQuizFailure) : QuizUiState
}

/**
 * In-memory, least-recently-used cache of ready quizzes by article id. Only [QuizUiState.Ready] results are stored.
 * A singleton on purpose: the reader creates a new view model each time an article opens, so a cache held by the view
 * model would be empty on every reopening and the same article would call Gemini again.
 */
@Singleton
class QuizCache @Inject constructor() {
    private var capacity: Int = DEFAULT_CAPACITY

    /** For tests only: a cache with a smaller capacity. */
    constructor(capacity: Int) : this() {
        this.capacity = capacity
    }

    private val limit get() = capacity.coerceAtLeast(1)

    // accessOrder = true: get() and put() move an entry to the end, so the eldest entry is the least recently used.
    private val entries = object : LinkedHashMap<String, QuizQuestion>(INITIAL_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizQuestion>?): Boolean = size > limit
    }

    val size: Int get() = synchronized(this) { entries.size }

    @Synchronized
    fun get(articleId: String): QuizQuestion? = entries[articleId]

    @Synchronized
    fun put(articleId: String, quiz: QuizQuestion) {
        entries[articleId] = quiz
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
        private const val INITIAL_SIZE = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
