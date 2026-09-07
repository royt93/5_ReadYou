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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.mckimquyen.reader.ui.component.ambient.ZenSoundSheet
import com.mckimquyen.reader.ui.page.rsvp.RsvpReaderDialog
import com.mckimquyen.reader.ui.page.rsvp.RsvpViewModel
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
    val listState = rememberLazyListState()

    var showZenAudioSheet by remember { mutableStateOf(false) }
    var showRsvpDialog by remember { mutableStateOf(false) }
    val rsvpViewModel: RsvpViewModel = hiltViewModel()
    val isZenAudioPlaying by readingViewModel.zenAudioManager.isPlaying.collectAsState()

    val isShowToolBar = if (LocalReadingAutoHideToolbar.current.value) {
        readingUiState.articleWithFeed != null && !listState.isScrollDown()
    } else {
        true
    }

    LaunchedEffect(Unit) {
        readingViewModel.scrollToTopEvent.collect {
            if (listState.firstVisibleItemIndex != 0) {
                listState.scrollToItem(0)
            }
        }
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

    val isAnySheetOpen = readingUiState.showSummarySheet || readingUiState.showMindMapSheet || readingUiState.showDeepReadSheet

    // Đồng bộ trạng thái mở/đóng từ ViewModel -> drawer.
    LaunchedEffect(isAnySheetOpen) {
        if (isAnySheetOpen) summaryDrawerState.show()
        else if (summaryDrawerState.isVisible) summaryDrawerState.hide()
    }
    // Khi người dùng vuốt/chạm ra ngoài để đóng -> đồng bộ ngược lại ViewModel.
    LaunchedEffect(summaryDrawerState.currentValue) {
        if (summaryDrawerState.currentValue == ModalBottomSheetValue.Hidden && isAnySheetOpen) {
            readingViewModel.dismissSummary()
            readingViewModel.dismissMindMap()
            readingViewModel.dismissDeepRead()
        }
    }
    BackHandler(isAnySheetOpen) {
        if (readingUiState.showDeepReadSheet) {
            readingViewModel.dismissDeepRead()
        } else if (readingUiState.showMindMapSheet) {
            readingViewModel.dismissMindMap()
        } else {
            readingViewModel.dismissSummary()
        }
    }

    BottomDrawer(
        drawerState = summaryDrawerState,
        sheetContent = {
            if (readingUiState.showDeepReadSheet) {
                DeepReadChatSheetContent(
                    state = readingUiState.deepReadState,
                    onSendQuestion = { question -> readingViewModel.sendDeepReadQuestion(question) },
                    onClearChat = { readingViewModel.clearDeepReadChat() },
                    onClose = { readingViewModel.dismissDeepRead() },
                )
            } else if (readingUiState.showMindMapSheet) {
                MindMapSheetContent(
                    state = readingUiState.mindMapState,
                    onRetry = { readingViewModel.requestMindMap() },
                    onClose = { readingViewModel.dismissMindMap() },
                    onForceOffline = { readingViewModel.requestMindMap(forceOffline = true) },
                )
            } else {
                SummarySheetContent(
                    state = readingUiState.summaryState,
                    onRetry = { readingViewModel.requestSummary() },
                    onClose = { readingViewModel.dismissSummary() },
                    onForceOffline = { readingViewModel.requestSummary(forceOffline = true) },
                    onOpenMindMap = {
                        readingViewModel.dismissSummary()
                        readingViewModel.openMindMap()
                    },
                    onOpenDeepRead = {
                        readingViewModel.dismissSummary()
                        readingViewModel.openDeepRead()
                    },
                )
            }
        },
    ) {
    BaseScaffold(
        topBarTonalElevation = tonalElevation.value.dp,
        containerTonalElevation = tonalElevation.value.dp,
        content = {
            Log.i("RLog", "TopBar: recomposition")

            // Linear Column (not an absolutely-positioned Box overlay): status bar inset -> top
            // action bar -> expanding content -> app bottom bar -> banner ad -> device
            // navigation bar inset. Each row claims exactly its own real height and the
            // scrollable content Box(weight(1f)) fills whatever remains — Compose recalculates
            // this automatically whenever any row's height changes (TopBar/BottomBar auto-hide,
            // ad load), so content can never end up hidden behind, or leave a stale gap in front
            // of, any of these rows. This replaces the previous absolutely-positioned overlay +
            // manually-computed "reserved height" Spacer approach, which was fragile: it required
            // hand-keeping a dp calculation in sync with several independently-animating rows.
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopBar(
                    navController = navController,
                    isShow = isShowToolBar,
                    title = readingUiState.articleWithFeed?.article?.title,
                    link = readingUiState.articleWithFeed?.article?.link,
                    isPlayingAudio = readingUiState.ttsState == TtsState.PLAYING,
                    isZenAudioPlaying = isZenAudioPlaying,
                    onPlayAudio = {
                        readingViewModel.togglePlayAudio()
                    },
                    onZenAudio = {
                        showZenAudioSheet = true
                    },
                    onRsvpReading = {
                        showRsvpDialog = true
                    },
                    showSummary = true,
                    onSummary = {
                        readingViewModel.openSummary()
                    },
                    onMindMap = {
                        readingViewModel.openMindMap()
                    },
                    onDeepRead = {
                        readingViewModel.openDeepRead()
                    },
                    onClose = {
                        navController.popBackStack()
                    },
                )

                // Content — expands to fill all space left over between TopBar and the bottom
                // rows below, whatever their current combined height happens to be.
                Box(modifier = Modifier.weight(1f)) {
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
                                listState = listState,
                                articleId = readingUiState.articleWithFeed.article.id,
                                brainRpgViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                            )
                        }
                    }
                }

                // Bottom Bar + Banner Ad — plain Column rows now, each sized to its own real
                // content. BottomBar genuinely collapses to 0.dp when auto-hidden by scroll
                // (safe: it holds no persistent resource). The ad stays unconditionally
                // composed for as long as the article is open, so its AdView is loaded once and
                // never torn down/reloaded by the toolbar auto-hide toggle, and renders at its
                // own natural size (no forced height/clip needed now that nothing has to match
                // a separately-computed reservation).
                if (readingUiState.articleWithFeed != null) {
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
                        onDeepRead = {
                            readingViewModel.openDeepRead()
                        },
                    )
                    // Removed entirely (was 16.dp, then 4.dp): the ad's own inflated layout
                    // (layout_ad_banner.xml, AdmobApplovinWrapper SDK) already adds 8.dp top
                    // padding plus its own "Ad" label chip above the creative — that alone is
                    // enough breathing room above BottomBar without any extra app-side spacer.
                    // ComposeBannerAd() already applies navigationBarsPadding() internally
                    // (see sdkadbmob/ComposeBannerAd.kt) — an extra trailing Spacer for the same
                    // inset here was double-reserving the device navigation bar's height.
                    com.mckimquyen.reader.sdkadbmob.ComposeBannerAd()
                }
            }
        }
    )
    }

    if (showZenAudioSheet) {
        ZenSoundSheet(
            zenAudioManager = readingViewModel.zenAudioManager,
            onDismiss = { showZenAudioSheet = false }
        )
    }

    if (showRsvpDialog) {
        val articleContent = readingUiState.content
            ?.takeIf { it.isNotBlank() }
            ?: readingUiState.articleWithFeed?.article?.shortDescription
            ?: ""
        RsvpReaderDialog(
            content = articleContent,
            viewModel = rsvpViewModel,
            onDismiss = { showRsvpDialog = false }
        )
    }
}
