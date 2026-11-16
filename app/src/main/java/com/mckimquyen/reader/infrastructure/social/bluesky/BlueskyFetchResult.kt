package com.mckimquyen.reader.infrastructure.social.bluesky

import org.json.JSONArray

/** What one successful read of a Bluesky account found. The posts are still untrusted JSON; the mapper picks the usable ones. */
data class BlueskyFetchResult(
    val items: JSONArray,
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/** The three ways a read can end; "not found" is not a network failure and must not fall back to RSS. */
sealed interface BlueskyOutcome {
    data class Found(val result: BlueskyFetchResult) : BlueskyOutcome
    object NotFound : BlueskyOutcome
    object Failed : BlueskyOutcome
}
