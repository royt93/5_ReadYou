package com.mckimquyen.reader.infrastructure.social.nostr

/** What one fetch found: the author's notes and, when a valid public profile has a usable name, that name. */
data class NostrFetchResult(val notes: List<NostrEvent>, val profileName: String?) {
    companion object {
        val EMPTY = NostrFetchResult(emptyList(), null)
    }
}
