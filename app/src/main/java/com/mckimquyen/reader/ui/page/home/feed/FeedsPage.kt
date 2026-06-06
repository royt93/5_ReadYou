package com.mckimquyen.reader.ui.page.home.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.work.WorkInfo
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsFilterBarFilled
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsFilterBarPadding
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsFilterBarStyle
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsFilterBarTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsGroupListExpand
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsGroupListTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalFeedsTopBarTonalElevation
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionNumber
import com.mckimquyen.reader.infrastructure.pref.LocalSkipVersionNumber
import com.mckimquyen.reader.ui.component.FilterBar
import com.mckimquyen.reader.ui.component.base.Banner
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.ext.alphaLN
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.findActivity
import com.mckimquyen.reader.ui.ext.getCurrentVersion
import com.mckimquyen.reader.ui.page.common.RouteName
import com.mckimquyen.reader.ui.page.setting.vip.VipBadge
import com.mckimquyen.reader.ui.page.home.FilterState
import com.mckimquyen.reader.ui.page.home.HomeViewModel
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionDrawer
import com.mckimquyen.reader.ui.page.home.feed.drawer.group.GroupOptionDrawer
import com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeDialog
import com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModel
import com.mckimquyen.reader.ui.page.home.addsources.CountriesList
import kotlin.collections.set
import kotlin.math.ln

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun FeedsPage(
    navController: NavHostController,
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val groupListTonalElevation = LocalFeedsGroupListTonalElevation.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val filterBarStyle = LocalFeedsFilterBarStyle.current
    val filterBarFilled = LocalFeedsFilterBarFilled.current
    val filterBarPadding = LocalFeedsFilterBarPadding.current
    val filterBarTonalElevation = LocalFeedsFilterBarTonalElevation.current
    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val filterUiState = homeViewModel.filterUiState.collectAsStateValue()
    val importantSum = feedsUiState.importantSum.collectAsStateValue(initial = stringResource(R.string.loading))
    val groupWithFeedList = feedsUiState.groupWithFeedList.collectAsStateValue(initial = emptyList())
    val groupsVisible: SnapshotStateMap<String, Boolean> = feedsUiState.groupsVisible
    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember { context.getCurrentVersion() }
    var isSyncing by remember { mutableStateOf(false) }
    // observeAsState() is the correct Compose-idiomatic way to observe LiveData.
    // Raw .observe() in a composable body registers a new observer every recomposition.
    val workInfoList by homeViewModel.syncWorkLiveData.observeAsState()
    isSyncing = workInfoList?.any { it.state == WorkInfo.State.RUNNING } == true

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        )
    )

    val feedBadgeAlpha by remember { derivedStateOf { (ln(groupListTonalElevation.value + 1.4f) + 2f) / 100f } }
    val groupAlpha by remember { derivedStateOf { groupListTonalElevation.value.dp.alphaLN(weight = 1.2f) } }
    val groupIndicatorAlpha by remember {
        derivedStateOf {
            groupListTonalElevation.value.dp.alphaLN(
                weight = 1.4f
            )
        }
    }

    LaunchedEffect(Unit) {
        feedsViewModel.fetchAccount()
    }

    // LaunchedEffect already re-runs when filterUiState changes —
    // wrapping it in snapshotFlow is redundant and adds unnecessary indirection.
    LaunchedEffect(filterUiState) {
        feedsViewModel.pullFeeds(filterUiState)
    }

    BackHandler(true) {
        context.findActivity()?.moveTaskToBack(false)
    }

    BaseScaffold(
        topBarTonalElevation = topBarTonalElevation.value.dp,
        containerTonalElevation = groupListTonalElevation.value.dp,
        navigationIcon = {
            FeedbackIconButton(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.onSurface,
                showBadge = newVersion.whetherNeedUpdate(currentVersion, skipVersion),
            ) {
                navController.navigate(RouteName.SETTINGS) {
                    launchSingleTop = true
                }
            }
        },
        actions = {
            // Badge VIP vàng — chỉ hiện khi VIP active, tap mở VIP screen.
            VipBadge {
                navController.navigate(RouteName.VIP) {
                    launchSingleTop = true
                }
            }
            // Chỉ hiển thị refresh icon khi không ở tab AddSources
            if (!filterUiState.filter.isAddSources()) {
                FeedbackIconButton(
                    modifier = Modifier.rotate(if (isSyncing) angle else 0f),
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    tint = MaterialTheme.colorScheme.onSurface,
                ) {
                    if (!isSyncing) homeViewModel.sync()
                }
                if (subscribeViewModel.rssService.get().subscribe) {
                    FeedbackIconButton(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.subscribe),
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        subscribeViewModel.showDrawer()
                    }
                }
            }
        },
        content = {
            // Nếu filter hiện tại là AddSources, hiển thị CountriesList thay vì feeds
            if (filterUiState.filter.isAddSources()) {
                CountriesList(navController = navController)
            } else {
                LazyColumn {
                item {
                    DisplayText(
//                        modifier = Modifier
//                            .clickable {
//                                accountTabVisible = true
//                            },
                        // .pointerInput(Unit) {
                        //     detectTapGestures(
                        //         onPress = {
                        //             accountTabRemember = true
                        //         },
                        //         onLongPress = {
                        //             accountTabRemember = true
                        //         }
                        //     )
                        // },
                        text = feedsUiState.account?.name ?: "",
                        desc = if (isSyncing) stringResource(R.string.syncing) else "",
                    )
                }
                item {
                    Banner(
                        title = filterUiState.filter.toName(),
                        desc = importantSum,
                        icon = filterUiState.filter.iconOutline,
                        action = {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.go_to),
                            )
                        },
                    ) {
                        filterChange(
                            navController = navController,
                            homeViewModel = homeViewModel,
                            filterState = filterUiState.copy(
                                group = null,
                                feed = null,
                            )
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Subtitle(
                        modifier = Modifier.padding(start = 26.dp),
                        text = stringResource(R.string.feeds)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                itemsIndexed(groupWithFeedList) { index, groupWithFeed ->
                    when (groupWithFeed) {
                        is GroupFeedsView.Group -> {
                            if (index != 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            GroupItem(
                                isExpanded = {
                                    groupsVisible.getOrPut(
                                        groupWithFeed.group.id,
                                        groupListExpand::value
                                    )
                                },
                                group = groupWithFeed.group,
                                alpha = groupAlpha,
                                indicatorAlpha = groupIndicatorAlpha,
                                isEnded = { index == groupWithFeedList.lastIndex },
                                onExpanded = {
                                    groupsVisible[groupWithFeed.group.id] = groupsVisible.getOrPut(
                                        groupWithFeed.group.id,
                                        groupListExpand::value
                                    ).not()
                                }
                            ) {
                                filterChange(
                                    navController = navController,
                                    homeViewModel = homeViewModel,
                                    filterState = filterUiState.copy(
                                        group = groupWithFeed.group,
                                        feed = null,
                                    )
                                )
                            }
                        }

                        is GroupFeedsView.Feed -> {
                            FeedItem(
                                feed = groupWithFeed.feed,
                                alpha = groupAlpha,
                                badgeAlpha = feedBadgeAlpha,
                                isEnded = { index == groupWithFeedList.lastIndex || groupWithFeedList[index + 1] is GroupFeedsView.Group },
                                isExpanded = {
                                    groupsVisible.getOrPut(
                                        groupWithFeed.feed.groupId,
                                        groupListExpand::value
                                    )
                                },
                            ) {
                                filterChange(
                                    navController = navController,
                                    homeViewModel = homeViewModel,
                                    filterState = filterUiState.copy(
                                        group = null,
                                        feed = groupWithFeed.feed,
                                    )
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(128.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
                }
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
                // Thay đổi filter state để UI tự động update
                filterChange(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    filterState = filterUiState.copy(filter = it),
                    isNavigate = false,
                )
            }
        }
    )

    SubscribeDialog()
    GroupOptionDrawer()
    FeedOptionDrawer()

//    AccountsTab(
//        visible = accountTabVisible,
//        accounts = accounts,
//        onAccountSwitch = {
//            accountViewModel.switchAccount(it) {
//                accountTabVisible = false
//                navController.navigate(RouteName.SETTINGS)
//                navController.navigate(RouteName.FEEDS) {
//                    launchSingleTop = true
//                    restoreState = true
//                }
//            }
//        },
//        onDismissRequest = {
//            accountTabVisible = false
//        },
//    )
}

private fun filterChange(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    filterState: FilterState,
    isNavigate: Boolean = true,
) {
    homeViewModel.changeFilter(filterState)
    if (isNavigate) {
        if (filterState.filter.isAddSources()) {
            navController.navigate(RouteName.ADD_SOURCES) {
                launchSingleTop = true
            }
        } else {
            navController.navigate(RouteName.FLOW) {
                launchSingleTop = true
            }
        }
    }
}
