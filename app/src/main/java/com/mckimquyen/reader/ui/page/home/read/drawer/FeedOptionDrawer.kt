package com.mckimquyen.reader.ui.page.home.read.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.mckimquyen.reader.ui.component.base.BottomDrawer
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun StyleOptionDrawer(
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()

    BackHandler(feedOptionUiState.drawerState.isVisible) {
        scope.launch {
            feedOptionUiState.drawerState.hide()
        }
    }

    BottomDrawer(
        drawerState = feedOptionUiState.drawerState,
        sheetContent = {
            Info()
        }
    ) {
        content()
    }
}

@Composable
fun Info() {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Tab(selected = true, onClick = { /*TODO*/ })
        }
    }
}

@Preview
@Composable
fun Prev() {
    Tab(selected = true, onClick = { /*TODO*/ })
}
