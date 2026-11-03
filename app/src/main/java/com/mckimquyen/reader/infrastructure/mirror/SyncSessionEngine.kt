package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import java.net.InetAddress

interface SyncSessionEngine {
    val isRunning: Boolean
    fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult
    fun stop()
}
