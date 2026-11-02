package com.mckimquyen.reader.domain.mirror

/**
 * Per-IP sliding-window request limit plus a cap on concurrent event streams. Only accepted requests are recorded,
 * so a client that keeps hammering the server cannot extend its own block. Thread-safe.
 */
class MirrorRateLimiter(private val clock: () -> Long) {
    private val lock = Any()
    private val requestsByIp = HashMap<String, ArrayDeque<Long>>()
    private var openStreams = 0

    fun tryRequest(ip: String): Boolean = synchronized(lock) {
        val now = clock()
        val cutoff = now - MirrorLimits.RATE_WINDOW_MILLIS
        val times = requestsByIp[ip] ?: ArrayDeque<Long>().also { makeRoomFor(ip, cutoff); requestsByIp[ip] = it }
        while (times.isNotEmpty() && times.first() <= cutoff) times.removeFirst()
        if (times.size >= MirrorLimits.MAX_REQUESTS_PER_MINUTE) return false
        times.addLast(now)
        true
    }

    fun tryOpenStream(): Boolean = synchronized(lock) {
        if (openStreams >= MirrorLimits.MAX_CLIENTS) return false
        openStreams++
        true
    }

    fun closeStream() = synchronized(lock) {
        if (openStreams > 0) openStreams--
    }

    fun trackedIps(): Int = synchronized(lock) { requestsByIp.size }

    /** Keeps the table bounded: drop expired entries first, then the least recently active address. */
    private fun makeRoomFor(newIp: String, cutoff: Long) {
        if (requestsByIp.size < MirrorLimits.MAX_TRACKED_IPS || newIp in requestsByIp) return
        requestsByIp.entries.removeAll { (_, times) -> times.isEmpty() || times.last() <= cutoff }
        if (requestsByIp.size < MirrorLimits.MAX_TRACKED_IPS) return
        requestsByIp.entries.minByOrNull { it.value.last() }?.let { requestsByIp.remove(it.key) }
    }
}
