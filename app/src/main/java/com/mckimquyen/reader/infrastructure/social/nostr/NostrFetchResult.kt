package com.mckimquyen.reader.infrastructure.social.nostr

/**
 * What one fetch found: every valid note it read from the relay, newest first (at most as many as frames read, [NostrRelayClient.MAX_EVENTS_READ]),
 * and, when a valid public profile has a usable name, that name. The list is NOT cut to the feed size: the mapper cuts to
 * [NostrFeedMapper.MAX_ARTICLES_SHOWN] after it has dropped the notes that cannot be shown.
 */
data class NostrFetchResult(val notes: List<NostrEvent>, val profileName: String?) {
    companion object {
        val EMPTY = NostrFetchResult(emptyList(), null)
    }
}
