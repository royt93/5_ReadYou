package com.mckimquyen.reader.ui.page.p2psync

import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.SyncPackage

/** The already paired device a verified receive came from, so the user can send this phone's state back. Never holds a secret. */
data class ReplyPeer(val accountId: Int, val peerId: String)

sealed interface LocalSyncUiState {
    data object Idle : LocalSyncUiState
    data object Starting : LocalSyncUiState
    data class Sending(
        val url: String,
        /** null when serving an already paired device: its secret stays in the store and is never shown. */
        val secretHex: String?,
        val expiresAtMillis: Long,
        val pairedPeerId: String? = null,
    ) : LocalSyncUiState {
        override fun toString() = "Sending(url=<redacted>, expiresAtMillis=$expiresAtMillis)"
    }
    data object Receiving : LocalSyncUiState
    data class Preview(val incoming: SyncPackage, val plan: LocalSyncPlan) : LocalSyncUiState {
        override fun toString() = "Preview(plan=$plan)"
    }
    data object Applying : LocalSyncUiState
    data class Done(val result: LocalSyncApplyResult) : LocalSyncUiState
    data class Error(val kind: LocalSyncError) : LocalSyncUiState
}

enum class LocalSyncError {
    NO_NETWORK, SERVER, URL, SECRET, NETWORK, PACKAGE, NOT_LOCAL, DATABASE, EXPIRED,
    LAN_PERMISSION, PAIRING_BROKEN, PAIRING_LABEL, PAIRING_LIMIT,
}
