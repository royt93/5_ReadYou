package com.mckimquyen.reader.ui.component.base

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Native Compose Material pull-to-refresh replacement for deprecated Accompanist SwipeRefresh. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeRefresh(
    isRefresh: Boolean = false,
    onRefresh: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val state = rememberPullRefreshState(isRefresh, onRefresh)
    Box(modifier = Modifier.fillMaxSize().pullRefresh(state)) {
        content()
        PullRefreshIndicator(
            refreshing = isRefresh,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
