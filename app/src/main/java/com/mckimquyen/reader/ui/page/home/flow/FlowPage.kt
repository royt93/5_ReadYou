package com.mckimquyen.reader.ui.page.home.flow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.general.MarkAsReadConditions
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListDateStickyHeader
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListFeedIcon
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalFlowFilterBarFilled
import com.mckimquyen.reader.infrastructure.pref.LocalFlowFilterBarPadding
import com.mckimquyen.reader.infrastructure.pref.LocalFlowFilterBarStyle
import com.mckimquyen.reader.infrastructure.pref.LocalFlowFilterBarTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalFlowTopBarTonalElevation
import com.mckimquyen.reader.ui.component.FilterBar
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseExtensibleVisibility
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.SwipeRefresh
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.page.common.RouteName
import com.mckimquyen.reader.ui.page.home.HomeViewModel
import com.mckimquyen.reader.ui.page.home.addsources.CountriesList
import com.mckimquyen.reader.ui.component.cluster.StoryClusterSheet
import com.mckimquyen.reader.ui.component.watchdog.LocalWatchdogKeywords
import com.mckimquyen.reader.ui.component.watchdog.WatchdogSheet
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.component.search.SemanticSearchCard
import com.mckimquyen.reader.ui.page.collection.SaveSmartCollectionDialog
import com.mckimquyen.reader.ui.page.collection.SmartCollectionsViewModel
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun FlowPage(
    navController: NavHostController,
    flowViewModel: FlowViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarFilled = LocalFlowFilterBarFilled.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val homeUiState = homeViewModel.homeUiState.collectAsStateValue()
    val semanticResults = homeViewModel.semanticSearchResults.collectAsStateValue()
    val flowUiState = flowViewModel.flowUiState.collectAsStateValue()
    val filterUiState = homeViewModel.filterUiState.collectAsStateValue()
    val pagingItems = homeUiState.pagingData.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var onSearch by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var showSaveCollectionDialog by remember { mutableStateOf(false) }
    val smartCollectionsViewModel: SmartCollectionsViewModel = hiltViewModel()
    // observeAsState() is the correct Compose-idiomatic way to observe LiveData.
    // Raw .observe() in a composable body registers a new observer every recomposition.
    val workInfoList by homeViewModel.syncWorkLiveData.observeAsState()
    isSyncing = workInfoList?.any { it.state == WorkInfo.State.RUNNING } == true

    // Simplified: LaunchedEffect(onSearch) already re-runs when onSearch changes,
    // wrapping with snapshotFlow{onSearch}.collect is redundant.
    LaunchedEffect(onSearch) {
        if (onSearch) {
            delay(100)
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            if (homeUiState.searchContent.isNotBlank()) {
                homeViewModel.inputSearchContent("")
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            if (it > 0) {
                keyboardController?.hide()
            }
        }
    }

    BackHandler(onSearch) {
        onSearch = false
    }

    BaseScaffold(
        topBarTonalElevation = topBarTonalElevation.value.dp,
        containerTonalElevation = articleListTonalElevation.value.dp,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                onSearch = false
                if (navController.previousBackStackEntry == null) {
                    navController.navigate(RouteName.FEEDS) {
                        launchSingleTop = true
                    }
                } else {
                    navController.popBackStack()
                }
            }
        },
        actions = {
            // Chỉ hiển thị action buttons khi không ở tab AddSources
            if (!filterUiState.filter.isAddSources()) {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    onClick = { actionsExpanded = true },
                )
                FlowActionsMenu(
                    expanded = actionsExpanded,
                    showMarkAllAsRead = !filterUiState.filter.isStarred(),
                    onDismiss = { actionsExpanded = false },
                    onMarkAllAsRead = if (!filterUiState.filter.isStarred()) ({
                        scope.launch {
                            if (listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
                            markAsRead = !markAsRead
                            onSearch = false
                        }
                    }) else null,
                    onDailyDigest = {
                        navController.navigate(RouteName.DAILY_DIGEST) { launchSingleTop = true }
                    },
                    onReels = {
                        navController.navigate(RouteName.REELS) { launchSingleTop = true }
                    },
                    onCommuteCast = { homeViewModel.openCommuteCast() },
                    onBrainRpg = {
                        navController.navigate(RouteName.BRAIN_RPG) { launchSingleTop = true }
                    },
                    onWatchdog = { homeViewModel.openWatchdogSheet() },
                    onSmartCollections = {
                        navController.navigate(RouteName.SMART_COLLECTIONS) { launchSingleTop = true }
                    },
                    onOnThisDay = {
                        navController.navigate(RouteName.ON_THIS_DAY) { launchSingleTop = true }
                    },
                )
                FeedbackIconButton(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = if (onSearch) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    scope.launch {
                        if (listState.firstVisibleItemIndex != 0) {
                            listState.scrollToItem(0)
                        }
                        onSearch = !onSearch
                    }
                }
            }
        },
        content = {
            // Nếu filter hiện tại là AddSources, hiển thị CountriesList thay vì flow
            if (filterUiState.filter.isAddSources()) {
                CountriesList(navController = navController)
            } else {
                val watchdogKeywords = homeViewModel.watchdogKeywords.collectAsStateValue()
                CompositionLocalProvider(LocalWatchdogKeywords provides watchdogKeywords) {
                    SwipeRefresh(
                        onRefresh = {
                            if (!isSyncing) {
                                flowViewModel.sync()
                            }
                        }
                    ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    item {
                        DisplayText(
                            modifier = Modifier.padding(start = if (articleListFeedIcon.value) 30.dp else 0.dp),
                            text = when {
                                filterUiState.group != null -> filterUiState.group.name
                                filterUiState.feed != null -> filterUiState.feed.name
                                else -> filterUiState.filter.toName()
                            },
                            desc = if (isSyncing) stringResource(R.string.syncing) else "",
                        )
                        BaseExtensibleVisibility(visible = markAsRead) {
                            Spacer(modifier = Modifier.height((56 + 24 + 10).dp))
                        }
                        MarkAsReadBar(
                            visible = markAsRead,
                            absoluteY = if (isSyncing) (4 + 16 + 180).dp else 180.dp,
                            onDismissRequest = {
                                markAsRead = false
                            },
                        ) {
                            markAsRead = false
                            flowViewModel.markAsRead(
                                groupId = filterUiState.group?.id,
                                feedId = filterUiState.feed?.id,
                                articleId = null,
                                conditions = it,
                            )
                        }
                        BaseExtensibleVisibility(visible = onSearch) {
                            SearchBar(
                                value = homeUiState.searchContent,
                                placeholder = when {
                                    filterUiState.group != null -> stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name
                                    )

                                    filterUiState.feed != null -> stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name
                                    )

                                    else -> stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName()
                                    )
                                },
                                focusRequester = focusRequester,
                                onValueChange = {
                                    homeViewModel.inputSearchContent(it)
                                },
                                onClose = {
                                    onSearch = false
                                    homeViewModel.inputSearchContent("")
                                }
                            )
                            Spacer(modifier = Modifier.height((56 + 24 + 10).dp))
                        }
                    }

                    if (onSearch && semanticResults.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Subtitle(text = stringResource(R.string.semantic_search_results_title))
                                androidx.compose.material3.TextButton(onClick = { showSaveCollectionDialog = true }) {
                                    Text(stringResource(R.string.smart_collection_save_action))
                                }
                            }
                        }
                        items(semanticResults, key = { "semantic_${it.articleWithFeed.article.id}" }) { result ->
                            SemanticSearchCard(
                                result = result,
                                isShowFeedIcon = articleListFeedIcon.value,
                                onClick = {
                                    onSearch = false
                                    navController.navigate("${RouteName.READING}/${result.articleWithFeed.article.id}") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Subtitle(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                text = stringResource(R.string.keyword_search_results_title),
                            )
                        }
                    }

                    ArticleList(
                        pagingItems = pagingItems,
                        isShowFeedIcon = articleListFeedIcon.value,
                        isShowStickyHeader = articleListDateStickyHeader.value,
                        articleListTonalElevation = articleListTonalElevation.value,
                        onClick = {
                            onSearch = false
                            navController.navigate("${RouteName.READING}/${it.article.id}") {
                                launchSingleTop = true
                            }
                        },
                        onClusterClick = { cluster ->
                            homeViewModel.openCluster(cluster)
                        }
                    ) {
                        flowViewModel.markAsRead(
                            groupId = filterUiState.group?.id,
                            feedId = filterUiState.feed?.id,
                            articleId = it.article.id,
                            MarkAsReadConditions.All
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(128.dp))
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                    }
                    }
                }
            }

            // Sheet xem toàn cảnh đa chiều cụm tin tức
            val selectedCluster = homeViewModel.selectedCluster.collectAsStateValue()
            StoryClusterSheet(
                cluster = selectedCluster,
                onDismissRequest = { homeViewModel.closeCluster() },
                onArticleClick = {
                    navController.navigate("${RouteName.READING}/${it.article.id}") {
                        launchSingleTop = true
                    }
                },
                onMarkAllRead = { cluster ->
                    homeViewModel.markClusterAsRead(cluster)
                },
                calculateSimilarity = homeViewModel::calculateArticleSimilarity,
            )

            // Sheet quản lý từ khóa theo dõi khẩn cấp (Watchdog)
            val showWatchdogSheet = homeViewModel.showWatchdogSheet.collectAsStateValue()
            if (showWatchdogSheet) {
                val watchdogKeywordsForSheet = homeViewModel.watchdogKeywords.collectAsStateValue()
                WatchdogSheet(
                    keywords = watchdogKeywordsForSheet,
                    onDismissRequest = { homeViewModel.closeWatchdogSheet() },
                    onAddKeyword = { homeViewModel.addWatchdogKeyword(it) },
                    onRemoveKeyword = { homeViewModel.removeWatchdogKeyword(it) },
                    onToggleKeyword = { id, enabled -> homeViewModel.toggleWatchdogKeyword(id, enabled) },
                    onOpenInbox = { navController.navigate(RouteName.WATCHDOG_INBOX) { launchSingleTop = true } },
                    onSnoozeKeyword = { id, duration -> homeViewModel.snoozeWatchdogKeyword(id, duration) },
                )
            }
        },
        bottomBar = {
            FilterBar(
                filter = filterUiState.filter,
                filterBarStyle = filterBarStyle.value,
                filterBarFilled = filterBarFilled.value,
                filterBarPadding = filterBarPadding.dp,
                filterBarTonalElevation = filterBarTonalElevation.value.dp,
            ) {
                // Reels is a destination, not a filter: open it and leave the flow state untouched
                // so returning lands on the same list with the same scroll position.
                if (it.isReels()) {
                    navController.navigate(RouteName.REELS) {
                        launchSingleTop = true
                    }
                    return@FilterBar
                }
                // Khi tap AddSources, chỉ cần thay đổi filter state, UI sẽ tự động update
                if (!it.isAddSources()) {
                    scope.launch {
                        if (listState.firstVisibleItemIndex != 0) {
                            listState.scrollToItem(0)
                        }
                    }
                }
                homeViewModel.changeFilter(filterUiState.copy(filter = it))
                if (!it.isAddSources()) {
                    homeViewModel.fetchArticles()
                }
            }
        }
    )

    if (showSaveCollectionDialog) {
        val currentQuery = homeUiState.searchContent.trim()
        val context = androidx.compose.ui.platform.LocalContext.current
        val savedToastText = stringResource(R.string.smart_collection_saved)
        SaveSmartCollectionDialog(
            initialQuery = currentQuery,
            onDismiss = { showSaveCollectionDialog = false },
            onSave = { name, query ->
                smartCollectionsViewModel.createCollection(name, query) {
                    showSaveCollectionDialog = false
                    android.widget.Toast.makeText(context, savedToastText, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
