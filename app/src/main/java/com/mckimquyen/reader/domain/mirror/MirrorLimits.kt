package com.mckimquyen.reader.domain.mirror

/** Every cap of the one-way LAN mirror in one place, so tests and the server cannot drift apart. */
object MirrorLimits {
    const val TOKEN_BYTES = 16
    const val TOKEN_TTL_MILLIS = 30L * 60_000

    const val MAX_CLIENTS = 3
    const val MAX_REQUESTS_PER_MINUTE = 20
    const val RATE_WINDOW_MILLIS = 60_000L
    const val MAX_TRACKED_IPS = 256

    const val MAX_REQUEST_BYTES = 8 * 1024
    const val MAX_HEADER_LINES = 32
    const val MAX_PATH_CHARS = 512

    const val SOCKET_TIMEOUT_MILLIS = 10_000
    const val KEEPALIVE_MILLIS = 15_000L
    const val WORKER_THREADS = 4

    const val MAX_PARAGRAPHS = 400
    const val MAX_PARAGRAPH_CHARS = 4_000
    const val MAX_SNAPSHOT_BYTES = 512 * 1024
}
