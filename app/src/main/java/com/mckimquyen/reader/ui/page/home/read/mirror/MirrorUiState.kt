package com.mckimquyen.reader.ui.page.home.read.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure

/** What the Second Screen sheet shows. [Expired] is separate from [Off] so the user learns why the session stopped. */
sealed interface MirrorUiState {
    data object Off : MirrorUiState
    data object NoNetwork : MirrorUiState
    /** Android 17 blocks LAN traffic until the user allows local network access. */
    data object NoPermission : MirrorUiState
    data object Starting : MirrorUiState
    data class Running(val url: String, val clients: Int, val expiresAtMillis: Long) : MirrorUiState
    data object Expired : MirrorUiState
    data class Error(val kind: MirrorFailure) : MirrorUiState
}
