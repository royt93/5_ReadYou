package com.mckimquyen.reader.ui.page.home.read

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.pref.LocalReadingAutoHideToolbar
import com.mckimquyen.reader.infrastructure.pref.LocalReadingPageTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalAutoTts
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BottomDrawer
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.isScrollDown
import com.mckimquyen.reader.ui.page.home.HomeViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ReadingPage(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    readingViewModel: ReadingViewModel = hiltViewModel(),
) {
    val tonalElevation = LocalReadingPageTonalElevation.current
    val autoTts = LocalAutoTts.current.value
    val readingUiState = readingViewModel.readingUiState.collectAsStateValue()
    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()
    val isShowToolBar = if (LocalReadingAutoHideToolbar.current.value) {
        readingUiState.articleWithFeed != null && !readingUiState.listState.isScrollDown()
    } else {
        true
    }

    val pagingItems = homeUiState.pagingData.collectAsLazyPagingItems().itemSnapshotList

    // Use LaunchedEffect so recorderNextArticle only runs when the list size or
    // current article actually changes, not on every recomposition.
    LaunchedEffect(pagingItems.size, readingUiState.articleWithFeed?.article?.id) {
        readingViewModel.recorderNextArticle(pagingItems)
    }

    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect {
            it.arguments?.getString("articleId")?.let { articleId ->
                if (readingUiState.articleWithFeed?.article?.id != articleId) {
                    readingViewModel.initData(articleId, autoTts)
                }
            }
        }
    }

    LaunchedEffect(readingUiState.articleWithFeed?.article?.id) {
        Log.i("RLog", "ReadPage: ${readingUiState.articleWithFeed}")
        readingUiState.articleWithFeed?.let {
            if (it.article.isUnread) {
                readingViewModel.markUnread(false)
            }
        }
    }

    // BottomSheet "Tóm tắt bằng AI" — dùng BottomDrawer chuẩn của app (ModalBottomSheetLayout)
    // để có drag handle, shape và xử lý system insets đúng (không lỗi edge-to-edge).
    val summaryDrawerState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    // Đồng bộ trạng thái mở/đóng từ ViewModel -> drawer.
    LaunchedEffect(readingUiState.showSummarySheet) {
        if (readingUiState.showSummarySheet) summaryDrawerState.show()
        else if (summaryDrawerState.isVisible) summaryDrawerState.hide()
    }
    // Khi người dùng vuốt/chạm ra ngoài để đóng -> đồng bộ ngược lại ViewModel.
    LaunchedEffect(summaryDrawerState.currentValue) {
        if (summaryDrawerState.currentValue == ModalBottomSheetValue.Hidden &&
            readingUiState.showSummarySheet
        ) {
            readingViewModel.dismissSummary()
        }
    }
    BackHandler(readingUiState.showSummarySheet) {
        readingViewModel.dismissSummary()
    }

    BottomDrawer(
        drawerState = summaryDrawerState,
        sheetContent = {
            SummarySheetContent(
                state = readingUiState.summaryState,
                onRetry = { readingViewModel.requestSummary() },
                onSaveKey = { key -> readingViewModel.saveApiKeyAndSummarize(key) },
            )
        },
    ) {
    BaseScaffold(
        topBarTonalElevation = tonalElevation.value.dp,
        containerTonalElevation = tonalElevation.value.dp,
        content = {
            Log.i("RLog", "TopBar: recomposition")

            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopBar(
                    navController = navController,
                    isShow = isShowToolBar,
                    title = readingUiState.articleWithFeed?.article?.title,
                    link = readingUiState.articleWithFeed?.article?.link,
                    isPlayingAudio = readingUiState.ttsState == TtsState.PLAYING,
                    onPlayAudio = {
                        readingViewModel.togglePlayAudio()
                    },
                    onSummary = {
                        readingViewModel.openSummary()
                    },
                    onClose = {
                        navController.popBackStack()
                    },
                )

                // Content
                if (readingUiState.articleWithFeed != null) {
                    AnimatedContent(
                        targetState = readingUiState.content ?: "",
                        transitionSpec = {
                            slideInVertically(
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow,
                                )
                            ) { height -> height / 2 } with slideOutVertically { height -> -(height / 2) } + fadeOut(
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow,
                                )
                            )
                        }
                    ) { target ->
                        Content(
                            content = target,
                            feedName = readingUiState.articleWithFeed.feed.name,
                            title = readingUiState.articleWithFeed.article.title,
                            author = readingUiState.articleWithFeed.article.author,
                            link = readingUiState.articleWithFeed.article.link,
                            publishedDate = readingUiState.articleWithFeed.article.date,
                            isLoading = readingUiState.isLoading,
                            listState = readingUiState.listState,
                        )
                    }
                }
                // Bottom Bar Wrapper (BottomBar + Banner Ad)
                if (readingUiState.articleWithFeed != null) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(1f)
                    ) {
                        BottomBar(
                            isShow = isShowToolBar,
                            isUnread = readingUiState.articleWithFeed.article.isUnread,
                            isStarred = readingUiState.articleWithFeed.article.isStarred,
                            isFullContent = readingUiState.isFullContent,
                            onUnread = {
                                readingViewModel.markUnread(it)
                            },
                            onStarred = {
                                readingViewModel.markStarred(it)
                            },
                            onNextArticle = {
                                if (readingUiState.nextArticleId.isNotEmpty()) {
                                    readingViewModel.initData(readingUiState.nextArticleId, autoTts)
                                }
                            },
                            onFullContent = {
                                if (it) readingViewModel.renderFullContent()
                                else readingViewModel.renderDescriptionContent()
                            },
                        )
                        if (isShowToolBar) {
                            Spacer(modifier = Modifier.height(16.dp))
                            com.mckimquyen.reader.sdkadbmob.ComposeBannerAd()
                        }
                    }
                }
            }
        }
    )
    }
}
