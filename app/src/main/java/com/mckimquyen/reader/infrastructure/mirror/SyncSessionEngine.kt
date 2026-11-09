package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import java.net.InetAddress

interface SyncSessionEngine {
    val isRunning: Boolean
    fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult

    /**
     * Same session, but the path token is supplied by the caller (derived from a paired secret) instead of drawn at
     * random. [tokenValue] must be exactly 32 lowercase hex characters, otherwise [IllegalArgumentException] is thrown
     * before any socket is opened.
     */
    fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult
    fun stop()
}
