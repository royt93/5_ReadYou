package com.mckimquyen.reader.domain.p2psync

object SyncLimits {
    const val MAX_ENVELOPE_BYTES = 8 * 1024 * 1024
    const val MAX_FEEDS = 5_000
    const val MAX_ARTICLE_STATES = 20_000
    const val MAX_FEED_URL_CHARS = 2_048
    const val MAX_ARTICLE_LINK_CHARS = 4_096
    const val MAX_NAME_CHARS = 300
    const val SECRET_BYTES = 16
    const val SALT_BYTES = 16
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val PBKDF2_ITERATIONS = 310_000
    const val KEY_BITS = 256
    const val SESSION_TTL_MILLIS = 5L * 60_000
}
