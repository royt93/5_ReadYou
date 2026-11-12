package com.mckimquyen.reader.ui.page.home.read.deepdive

import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import javax.inject.Inject
import javax.inject.Singleton

/** What the Deep Dive sheet shows (FRONT-01). */
sealed interface DeepDiveUiState {
    /** Nothing is open. */
    data object Idle : DeepDiveUiState
    data object AwaitingConsent : DeepDiveUiState
    data object ConsentDeclined : DeepDiveUiState
    data object Loading : DeepDiveUiState
    data class Ready(val report: DeepDiveReport) : DeepDiveUiState
    data class Failed(val reason: AiDeepDiveFailure) : DeepDiveUiState
}

/**
 * In-memory, least-recently-used cache of ready reports by article id. Only ready reports are stored. A singleton on
 * purpose: `hiltViewModel()` creates a new view model each time the sheet's screen is composed, so a cache held by the
 * view model would be empty again and the same article would call Gemini again (found on a phone in RPG-02).
 */
@Singleton
class DeepDiveCache @Inject constructor() {
    private var capacity: Int = DEFAULT_CAPACITY

    /** For tests only: a cache with a smaller capacity. */
    constructor(capacity: Int) : this() {
        this.capacity = capacity
    }

    private val limit get() = capacity.coerceAtLeast(1)

    // accessOrder = true: get() and put() move an entry to the end, so the eldest entry is the least recently used.
    private val entries = object : LinkedHashMap<String, DeepDiveReport>(INITIAL_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DeepDiveReport>?): Boolean = size > limit
    }

    val size: Int get() = synchronized(this) { entries.size }

    @Synchronized
    fun get(articleId: String): DeepDiveReport? = entries[articleId]

    @Synchronized
    fun put(articleId: String, report: DeepDiveReport) {
        entries[articleId] = report
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
        private const val INITIAL_SIZE = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
