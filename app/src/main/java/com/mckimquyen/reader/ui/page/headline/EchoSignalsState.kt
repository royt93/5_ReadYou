package com.mckimquyen.reader.ui.page.headline

import com.mckimquyen.reader.domain.headline.ArticleSignalResult

sealed interface EchoSignalState {
    data object Idle : EchoSignalState
    data object Analyzing : EchoSignalState
    data class Ready(val result: ArticleSignalResult) : EchoSignalState
    data object None : EchoSignalState
}

fun EchoSignalState.readyResult(): ArticleSignalResult? = (this as? EchoSignalState.Ready)?.result
