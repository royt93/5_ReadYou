package com.mckimquyen.reader.ui.page.home.read

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mckimquyen.reader.ui.page.home.read.mirror.SecondScreenViewModel
import com.mckimquyen.reader.ui.page.home.read.mirror.SecondScreenSheetContent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.readposition.ReadingPositionMapper
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import com.mckimquyen.reader.ui.page.home.read.podcast.PodcastPlayerBar
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveSheet
import com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveViewModel
import com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioSheet
import com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioViewModel
import com.mckimquyen.reader.ui.page.home.read.podcast.PodcastPlayerViewModel
import com.mckimquyen.reader.ui.page.home.read.quote.QuoteCardViewModel
import com.mckimquyen.reader.ui.page.headline.EchoSignalDetailsSheet
import com.mckimquyen.reader.ui.page.headline.EchoSignalState
import com.mckimquyen.reader.ui.page.headline.readyResult
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyInvestigationSheetContent
import com.mckimquyen.reader.ui.page.home.read.bounty.DossierSheetContent
import com.mckimquyen.reader.ui.page.home.read.quote.QuoteCardSheetContent
import com.mckimquyen.reader.ui.page.home.read.steelman.DualCardSheetContent
import com.mckimquyen.reader.ui.page.home.read.steelman.DualCardViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.pref.LocalReadingAutoHideToolbar
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.LocalReadingPageTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalReadPositionSync
import com.mckimquyen.reader.infrastructure.pref.LocalAutoTts
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BottomDrawer
import com.mckimquyen.reader.ui.component.ambient.ZenSoundSheet
import com.mckimquyen.reader.ui.page.rsvp.RsvpReaderDialog
import com.mckimquyen.reader.ui.page.rsvp.RsvpViewModel
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.common.RouteName
import com.mckimquyen.reader.ui.page.notebook.HighlightNoteDialog
import com.mckimquyen.reader.ui.page.notebook.NotebookViewModel
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
    val eInkEnabled = LocalEInkMode.current.value
    val tonalElevation = LocalReadingPageTonalElevation.current
    val readPositionSyncEnabled = LocalReadPositionSync.current.value
    val autoTts = LocalAutoTts.current.value
    val readingUiState = readingViewModel.readingUiState.collectAsStateValue()
    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var showHighlightDialog by remember { mutableStateOf(false) }
    val notebookViewModel: NotebookViewModel = hiltViewModel()

    var showZenAudioSheet by remember { mutableStateOf(false) }
    var showRsvpDialog by remember { mutableStateOf(false) }
    val rsvpViewModel: RsvpViewModel = hiltViewModel()
    val bodyRange = remember { BodyItemRange() }
    var rsvpStartFraction by remember { mutableStateOf(0f) }

    // Body fraction already read; a position near either end counts as "not started" so RSVP/TTS begin at the top.
    fun currentBodyFraction(): Float = ReadingPositionMapper.bodyFractionFromList(
        firstVisibleIndex = listState.firstVisibleItemIndex,
        firstVisibleOffsetPx = listState.firstVisibleItemScrollOffset,
        firstVisibleSizePx = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == listState.firstVisibleItemIndex }?.size ?: 0,
        bodyStart = bodyRange.start,
        bodyCount = bodyRange.count,
    ).let { fraction ->
        val atEdge = fraction < ReadingPositionMapper.RESTART_BELOW_FRACTION ||
            fraction >= ReadingPositionMapper.TREAT_AS_FINISHED_FRACTION
        if (atEdge) 0f else fraction
    }
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

    val currentArticleId by rememberUpdatedState(readingUiState.articleWithFeed?.article?.id)

    // Volume keys turn pages only while this Reader shows an article, E-Ink is on and TTS is stopped.
    val pageTurnScope = rememberCoroutineScope()
    ReaderVolumeKeys(
        enabled = eInkEnabled && readingUiState.articleWithFeed != null,
        ttsPlaying = readingUiState.ttsState == TtsState.PLAYING,
        onTurn = { direction -> pageTurnScope.launch { listState.turnPage(direction) } },
    )

    LaunchedEffect(Unit) {
        readingViewModel.restoreScrollPositionEvent.collect { event ->
            if (event.articleId != currentArticleId) return@collect
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > 1 }
            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            val targetIndex = (event.progress * lastIndex).toInt().coerceIn(0, lastIndex)
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(readingUiState.articleWithFeed?.article?.id, readingUiState.isLoading, listState, readPositionSyncEnabled) {
        val currentArticleId = readingUiState.articleWithFeed?.article?.id ?: return@LaunchedEffect
        if (readingUiState.isLoading) return@LaunchedEffect
        snapshotFlow {
            val itemCount = listState.layoutInfo.totalItemsCount
            val firstIndex = listState.firstVisibleItemIndex
            if (itemCount > 1) firstIndex.toFloat() / (itemCount - 1) else 0f
        }
            .distinctUntilChanged()
            .collect { progress ->
                if (!readPositionSyncEnabled) return@collect
                if (readingUiState.articleWithFeed?.article?.id == currentArticleId && !readingUiState.isLoading) {
                    readingViewModel.saveReadPosition(currentArticleId, progress.coerceIn(0f, 1f))
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
                    readingViewModel.initData(articleId, autoTts, readPositionSyncEnabled)
                }
            }
        }
    }

    LaunchedEffect(readingUiState.articleWithFeed?.article?.id) {
        Log.i("RLog", "ReadPage: ${readingUiState.articleWithFeed}")
        readingUiState.articleWithFeed?.let {
            readingViewModel.recordOpened()
            if (it.article.isUnread) {
                readingViewModel.markUnread(false)
            }
        }
    }

    // BottomSheet "Tóm tắt bằng AI" — dùng BottomDrawer chuẩn của app (ModalBottomSheetLayout)
    // để có drag handle, shape và xử lý system insets đúng (không lỗi edge-to-edge).
    // skipHalfExpanded: sheet trích dẫn cao hơn nửa màn hình, nếu mở ở HalfExpanded thì nút Chia sẻ nằm dưới mép
    // và người dùng phải tự kéo lên (đã thấy trên Tecno). Drawer này dùng chung với các sheet AI (tóm tắt, mind map,
    // Deep Read): khi nội dung của chúng cao hơn nửa màn hình, chúng cũng sẽ mở thẳng Expanded thay vì HalfExpanded.
    val summaryDrawerState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
    )

    val quoteViewModel: QuoteCardViewModel = hiltViewModel()
    val quoteState by quoteViewModel.state.collectAsState()
    val quoteTemplate by quoteViewModel.selectedTemplate.collectAsState()
    val quoteScope = rememberCoroutineScope()
    val quoteChooserTitle = stringResource(R.string.quote_card_chooser_title)
    val dualCardViewModel: DualCardViewModel = hiltViewModel()
    val dualCardState by dualCardViewModel.state.collectAsState()
    val dualChooserTitle = stringResource(R.string.echo_dual_chooser_title)
    val dualShareText = stringResource(R.string.echo_dual_share_text)
    val podcastStudioViewModel: PodcastStudioViewModel = hiltViewModel()
    val deepDiveViewModel: DeepDiveViewModel = hiltViewModel()
    val deepDiveState by deepDiveViewModel.state.collectAsState()
    val deepDiveChooserTitle = stringResource(R.string.deep_dive_chooser_title)
    val deepDiveExportError = stringResource(R.string.deep_dive_export_error)
    val deepDiveTexts = DeepDivePdfTexts(
        aiLabel = stringResource(R.string.deep_dive_label),
        headingContext = stringResource(R.string.deep_dive_section_context),
        headingCritical = stringResource(R.string.deep_dive_section_critical),
        headingImpact = stringResource(R.string.deep_dive_section_impact),
        colArea = stringResource(R.string.deep_dive_col_area),
        colShort = stringResource(R.string.deep_dive_col_short),
        colLong = stringResource(R.string.deep_dive_col_long),
        truncatedNote = stringResource(R.string.deep_dive_pdf_truncated),
        dateText = remember { java.text.DateFormat.getDateInstance().format(java.util.Date()) },
    )
    // A report belongs to the article it was written for: leaving or switching articles closes the sheet.
    LaunchedEffect(readingUiState.articleWithFeed?.article?.id) { deepDiveViewModel.dismiss() }
    val podcastStudioState by podcastStudioViewModel.state.collectAsState()
    val secondScreenViewModel: SecondScreenViewModel = hiltViewModel()
    val lanPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { secondScreenViewModel.start() }
    val secondScreenState by secondScreenViewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val secondScreenCopiedText = stringResource(R.string.second_screen_copied)
    val secondScreenChooserTitle = stringResource(R.string.second_screen_share_chooser)

    // EXC-13: the browser follows whichever article (and body, once full content loads) is on screen.
    LaunchedEffect(readingUiState.articleWithFeed?.article?.id, readingUiState.content) {
        val current = readingUiState.articleWithFeed ?: return@LaunchedEffect
        secondScreenViewModel.updateArticle(
            title = current.article.title,
            source = current.feed.name,
            dateMillis = current.article.date.time,
            html = readingUiState.content,
        )
    }
    // Leaving this page ends the session; so does the whole app going to the background.
    DisposableEffect(Unit) { onDispose { secondScreenViewModel.stop() } }
    DisposableEffect(Unit) {
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) secondScreenViewModel.onAppBackgrounded()
        }
        processLifecycle.addObserver(observer)
        onDispose { processLifecycle.removeObserver(observer) }
    }
    val bountyState by readingViewModel.bountyInvestigationState.collectAsState()
    val dossier by readingViewModel.dossier.collectAsState()
    val echoState by readingViewModel.echoSignalState.collectAsState()
    val contrastingCoverage by readingViewModel.contrastingCoverage.collectAsState()
    val dossierChooserTitle = stringResource(R.string.dossier_chooser_title)
    val isAnySheetOpen = readingUiState.showSummarySheet || readingUiState.showMindMapSheet ||
        readingUiState.showDeepReadSheet || readingUiState.showQuoteSheet || readingUiState.showBountySheet ||
        readingUiState.showEchoSheet || readingUiState.showDossierSheet || readingUiState.showDualCardSheet ||
        readingUiState.showSecondScreenSheet

    // Render lại thẻ mỗi khi mở sheet với đoạn trích mới; giải phóng bitmap khi đóng.
    LaunchedEffect(readingUiState.quoteSpec) {
        val spec = readingUiState.quoteSpec
        if (spec != null) quoteViewModel.start(spec) else quoteViewModel.dismiss()
    }

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
            readingViewModel.dismissQuoteSheet()
            readingViewModel.dismissDossier()
            readingViewModel.dismissBountyInvestigation()
            readingViewModel.dismissEchoSheet()
            readingViewModel.dismissDualCard()
            readingViewModel.dismissSecondScreen()
        }
    }
    LaunchedEffect(readingUiState.dualCardSpec) {
        val spec = readingUiState.dualCardSpec
        if (spec != null) dualCardViewModel.start(spec) else dualCardViewModel.dismiss()
    }

    BackHandler(isAnySheetOpen) {
        if (readingUiState.showSecondScreenSheet) {
            readingViewModel.dismissSecondScreen()
        } else if (readingUiState.showDualCardSheet) {
            readingViewModel.dismissDualCard()
        } else if (readingUiState.showEchoSheet) {
            readingViewModel.dismissEchoSheet()
        } else if (readingUiState.showDossierSheet) {
            readingViewModel.dismissDossier()
        } else if (readingUiState.showBountySheet) {
            readingViewModel.dismissBountyInvestigation()
        } else if (readingUiState.showQuoteSheet) {
            readingViewModel.dismissQuoteSheet()
        } else if (readingUiState.showDeepReadSheet) {
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
            val echoResult = echoState.readyResult()
            val currentDossier = dossier
            if (readingUiState.showSecondScreenSheet) {
                SecondScreenSheetContent(
                    state = secondScreenState,
                    onToggle = { on ->
                        if (!on) {
                            secondScreenViewModel.stop()
                        } else if (com.mckimquyen.reader.domain.p2psync.LanAccess.required(android.os.Build.VERSION.SDK_INT) &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context, com.mckimquyen.reader.domain.p2psync.LanAccess.PERMISSION,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            lanPermissionLauncher.launch(com.mckimquyen.reader.domain.p2psync.LanAccess.PERMISSION)
                        } else {
                            secondScreenViewModel.start()
                        }
                    },
                    onCopy = { url ->
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, secondScreenCopiedText, Toast.LENGTH_SHORT).show()
                    },
                    onShare = { url ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(send, secondScreenChooserTitle))
                    },
                )
            } else if (readingUiState.showDualCardSheet) {
                DualCardSheetContent(
                    state = dualCardState,
                    onShare = {
                        quoteScope.launch {
                            dualCardViewModel.prepareShareIntent(dualChooserTitle, dualShareText)
                                ?.let { context.startActivity(it) }
                        }
                    },
                    onRetry = { readingUiState.dualCardSpec?.let { dualCardViewModel.start(it) } },
                )
            } else if (readingUiState.showEchoSheet && echoResult != null) {
                EchoSignalDetailsSheet(
                    result = echoResult,
                    onClose = { readingViewModel.dismissEchoSheet() },
                )
            } else if (readingUiState.showDossierSheet && currentDossier != null) {
                DossierSheetContent(
                    dossier = currentDossier,
                    onOpenArticle = { targetId ->
                        readingViewModel.dismissDossier()
                        readingViewModel.dismissBountyInvestigation()
                        navController.navigate("${RouteName.READING}/$targetId")
                    },
                    onShare = {
                        quoteScope.launch {
                            val intent = readingViewModel.prepareDossierShare(dossierChooserTitle)
                            if (intent != null) context.startActivity(intent)
                            else Toast.makeText(context, R.string.dossier_share_error, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClose = { readingViewModel.dismissDossier() },
                )
            } else if (readingUiState.showBountySheet) {
                BountyInvestigationSheetContent(
                    state = bountyState,
                    onOpenArticle = { targetId ->
                        readingViewModel.dismissBountyInvestigation()
                        navController.navigate("${RouteName.READING}/$targetId")
                    },
                    onRequestAiContext = { readingViewModel.requestBountyAiContext() },
                    onClose = { readingViewModel.dismissBountyInvestigation() },
                    onCreateDossier = { readingViewModel.openDossier() },
                )
            } else if (readingUiState.showQuoteSheet) {
                QuoteCardSheetContent(
                    state = quoteState,
                    selected = quoteTemplate,
                    onSelectTemplate = { quoteViewModel.selectTemplate(it) },
                    onShare = {
                        quoteScope.launch {
                            quoteViewModel.prepareShareIntent(quoteChooserTitle)?.let { context.startActivity(it) }
                        }
                    },
                    onRetry = { readingUiState.quoteSpec?.let { quoteViewModel.start(it) } },
                )
            } else if (readingUiState.showDeepReadSheet) {
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
                        readingViewModel.togglePlayAudio(currentBodyFraction())
                    },
                    onZenAudio = {
                        showZenAudioSheet = true
                    },
                    onRsvpReading = {
                        rsvpStartFraction = currentBodyFraction()
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
                    onSecondScreen = {
                        readingViewModel.openSecondScreen()
                    },
                    onPodcastStudio = {
                        readingUiState.articleWithFeed?.article?.let(podcastStudioViewModel::initiate)
                    },
                    onDeepDive = {
                        readingUiState.articleWithFeed?.article?.let { article ->
                            deepDiveViewModel.open(article.id, article.title, readingUiState.content ?: article.fullContent ?: article.rawDescription)
                        }
                    },
                    onHighlight = {
                        showHighlightDialog = true
                    },
                    onNotebook = {
                        navController.navigate(RouteName.NOTEBOOK) {
                            launchSingleTop = true
                        }
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
                                if (eInkEnabled) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    slideInVertically(
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        )
                                    ) { height -> height / 2 } togetherWith
                                        (slideOutVertically { height -> -(height / 2) } + fadeOut(
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessLow,
                                            )
                                        ))
                                }
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
                                shortDescription = readingUiState.articleWithFeed.article.shortDescription,
                                brainRpgViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                                onShareQuote = { readingViewModel.openQuoteSheet(it) },
                                onInvestigate = { readingViewModel.openBountyInvestigation(it) },
                                echoSignalState = echoState,
                                onOpenEchoSheet = { readingViewModel.openEchoSheet() },
                                contrastingCoverageState = contrastingCoverage,
                                onSelectContrastPhrase = { readingViewModel.selectContrastPhrase(it) },
                                onOpenContrastArticle = { targetId -> navController.navigate("${RouteName.READING}/$targetId") },
                                bodyRange = bodyRange,
                                onExportContrastArticle = { readingViewModel.openDualCard(it) },
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
                val podcastArticle = readingUiState.articleWithFeed?.article
                val podcastUrl = podcastArticle?.audioUrl
                val podcastViewModel: PodcastPlayerViewModel = hiltViewModel()
                val podcastState = podcastViewModel.state.collectAsStateValue()
                val podcastFeedName = readingUiState.articleWithFeed?.feed?.name.orEmpty()
                LaunchedEffect(podcastArticle?.id) {
                    if (podcastArticle != null && podcastUrl != null) {
                        podcastViewModel.bind(
                            podcastArticle.id, podcastArticle.title, podcastFeedName,
                            AudioInfo(podcastUrl, podcastArticle.audioMime, podcastArticle.audioDurationSec),
                        )
                    }
                }
                if (podcastArticle != null && podcastUrl != null && podcastState.articleId != podcastArticle.id) {
                    com.mckimquyen.reader.ui.page.home.read.podcast.PodcastEpisodeAction(
                        title = podcastArticle.title,
                        onPlay = {
                            podcastViewModel.playEpisode(
                                podcastArticle.id, podcastArticle.title, podcastFeedName,
                                AudioInfo(podcastUrl, podcastArticle.audioMime, podcastArticle.audioDurationSec),
                            )
                        },
                    )
                }
                if (podcastState.articleId != null) {
                    PodcastPlayerBar(
                        state = podcastState,
                        onToggle = podcastViewModel::onToggle,
                        onSkip = podcastViewModel::onSkip,
                        onSeek = podcastViewModel::onSeek,
                        onSpeed = podcastViewModel::onSpeed,
                        onSleep = podcastViewModel::onSleep,
                        onRetry = podcastViewModel::onRetry,
                    )
                }

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

    DeepDiveSheet(
        state = deepDiveState,
        title = readingUiState.articleWithFeed?.article?.title.orEmpty(),
        onDismiss = deepDiveViewModel::dismiss,
        onConsent = deepDiveViewModel::onConsent,
        onConsentLater = deepDiveViewModel::onConsentLater,
        onRetry = deepDiveViewModel::retry,
        onExportPdf = {
            quoteScope.launch {
                val intent = deepDiveViewModel.prepareShare(deepDiveTexts, deepDiveChooserTitle)
                if (intent != null) context.startActivity(intent)
                else Toast.makeText(context, deepDiveExportError, Toast.LENGTH_SHORT).show()
            }
        },
    )

    PodcastStudioSheet(
        state = podcastStudioState,
        onDismiss = podcastStudioViewModel::dismissSheet,
        onConsent = podcastStudioViewModel::onConsent,
        onConfirmConflict = podcastStudioViewModel::onConfirmConflict,
        onDismissConflict = podcastStudioViewModel::onDismissConflict,
        onToggle = podcastStudioViewModel::togglePlayPause,
        onSkipNext = podcastStudioViewModel::skipNext,
        onSkipPrevious = podcastStudioViewModel::skipPrevious,
        onSeek = podcastStudioViewModel::seekTo,
        onRetry = { readingUiState.articleWithFeed?.article?.let(podcastStudioViewModel::initiate) },
    )

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
            startFraction = rsvpStartFraction,
            onDismiss = {
                val state = rsvpViewModel.uiState.value
                val startIndex = ReadingPositionMapper.tokenIndexFromFraction(rsvpStartFraction, state.tokens.size)
                val articleId = readingUiState.articleWithFeed?.article?.id
                if (articleId != null && bodyRange.count > 0 &&
                    ReadingPositionMapper.shouldHandoffFromRsvp(startIndex, state.currentIndex)
                ) {
                    val fraction = ReadingPositionMapper.fractionFromTokenIndex(state.currentIndex, state.tokens.size)
                    val target = ReadingPositionMapper.scrollTargetFromBodyFraction(fraction, bodyRange.start, bodyRange.count)
                    val total = listState.layoutInfo.totalItemsCount
                    pageTurnScope.launch { listState.scrollToBodyTarget(target) }
                    if (readPositionSyncEnabled) {
                        readingViewModel.saveReadPosition(
                            articleId,
                            ReadingPositionMapper.listFractionFromBodyFraction(fraction, bodyRange.start, bodyRange.count, total),
                        )
                    }
                }
                showRsvpDialog = false
            },
        )
    }

    if (showHighlightDialog && readingUiState.articleWithFeed != null) {
        val article = readingUiState.articleWithFeed.article
        val feed = readingUiState.articleWithFeed.feed
        HighlightNoteDialog(
            initialText = "",
            initialNote = "",
            isEditing = false,
            onDismissRequest = { showHighlightDialog = false },
            onConfirm = { text, note, colorHex ->
                notebookViewModel.addHighlight(
                    articleId = article.id,
                    articleTitle = article.title,
                    feedName = feed.name,
                    articleLink = article.link ?: "",
                    selectedText = text,
                    noteComment = note,
                    colorHex = colorHex,
                ) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.notebook_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                    showHighlightDialog = false
                }
            }
        )
    }
}

/** Scrolls to the target item, then [BodyScrollTarget.withinItem] of the way into it once the item has been measured. */
private suspend fun LazyListState.scrollToBodyTarget(target: ReadingPositionMapper.BodyScrollTarget) {
    scrollToItem(target.listIndex)
    val size = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.listIndex }?.size ?: return
    if (target.withinItem > 0f) scrollBy(size * target.withinItem)
}
