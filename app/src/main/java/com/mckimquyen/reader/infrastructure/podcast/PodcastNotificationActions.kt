package com.mckimquyen.reader.infrastructure.podcast

object PodcastNotificationActions {
    const val REWIND = "rewind"
    const val TOGGLE = "toggle"
    const val FORWARD = "forward"
    const val STOP = "stop"

    /** Order of the notification buttons; only the toggle icon changes between playing and paused. */
    val actionNames: List<String> = listOf(REWIND, TOGGLE, FORWARD, STOP)

    /** Rewind, toggle and forward stay visible when the notification is collapsed. */
    val compactIndices: List<Int> = listOf(0, 1, 2)
}
