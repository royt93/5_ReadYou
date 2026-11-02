package com.mckimquyen.reader.domain.mirror

enum class MirrorFailure { BIND, ALREADY_RUNNING }

sealed interface MirrorStartResult {
    data class Started(val port: Int, val token: MirrorToken) : MirrorStartResult
    data class Failed(val reason: MirrorFailure) : MirrorStartResult
}
