package com.mckimquyen.reader.infrastructure.social.nostr

/**
 * What one fetch found: every valid note it read from the relay, newest first (at most as many as frames read, [NostrRelayClient.MAX_EVENTS_READ]),
 * and, when a valid public profile has a usable name, that name. The list is NOT cut to the feed size: replies to other people are
 * still in it, and the mapper cuts to [NostrFeedMapper.MAX_ARTICLES_SHOWN] only after filtering them out.
 */
data class NostrFetchResult(val notes: List<NostrEvent>, val profileName: String?) {
    companion object {
        val EMPTY = NostrFetchResult(emptyList(), null)
    }
}
