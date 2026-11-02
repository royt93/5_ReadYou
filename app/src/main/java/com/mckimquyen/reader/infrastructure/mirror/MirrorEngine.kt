package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

/** What the view model needs from a mirror server, so tests can swap in a fake without opening sockets. */
interface MirrorEngine {
    val isRunning: Boolean

    /** Number of browsers currently holding an event stream open. */
    val clients: StateFlow<Int>

    fun start(bindAddress: InetAddress): MirrorStartResult
    fun stop()
    fun publish(snapshot: MirrorSnapshot)
}
