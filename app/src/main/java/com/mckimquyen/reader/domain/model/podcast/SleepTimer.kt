package com.mckimquyen.reader.domain.model.podcast

class SleepTimer(private val nowMillis: () -> Long = System::currentTimeMillis) {

    private var endMillis: Long? = null

    val isActive: Boolean get() = endMillis != null

    fun start(minutes: Int) {
        require(minutes in OPTIONS_MINUTES) { "Unsupported sleep timer: $minutes" }
        endMillis = nowMillis() + minutes * MILLIS_PER_MINUTE
    }

    fun cancel() {
        endMillis = null
    }

    /** Remaining whole seconds (rounded up), never negative; null when no timer is running. */
    fun remainingSec(): Int? {
        val end = endMillis ?: return null
        val left = (end - nowMillis()).coerceAtLeast(0L)
        return ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    fun isExpired(): Boolean = remainingSec() == 0

    companion object {
        val OPTIONS_MINUTES = listOf(15, 30, 45)
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
