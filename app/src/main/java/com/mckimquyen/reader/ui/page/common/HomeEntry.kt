package com.mckimquyen.reader.ui.page.common

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mckimquyen.reader.domain.model.general.Filter
import com.mckimquyen.reader.infrastructure.pref.LocalDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.LocalReadingDarkTheme
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.component.commute.CommuteCastDialog
import androidx.compose.runtime.collectAsState

import com.mckimquyen.reader.ui.ext.animatedComposable
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.findActivity
import com.mckimquyen.reader.ui.ext.initialFilter
import com.mckimquyen.reader.ui.ext.initialPage
import com.mckimquyen.reader.ui.ext.isFirstLaunch
import com.mckimquyen.reader.ui.page.home.HomeViewModel
import com.mckimquyen.reader.ui.page.home.addsources.AddSourceDetailPage
import com.mckimquyen.reader.ui.page.home.addsources.AddSourcesMainPage
import com.mckimquyen.reader.ui.page.home.feed.FeedsPage
import com.mckimquyen.reader.ui.page.home.flow.FlowPage
import com.mckimquyen.reader.ui.page.collection.SmartCollectionDetailPage
import com.mckimquyen.reader.ui.page.collection.SmartCollectionsPage
import com.mckimquyen.reader.ui.page.home.read.ReadingPage
import com.mckimquyen.reader.ui.page.digest.DigestPage
import com.mckimquyen.reader.ui.page.digest.DigestViewModel
import com.mckimquyen.reader.ui.page.notebook.NotebookPage
import com.mckimquyen.reader.ui.page.reels.ReelsPage
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainPage
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainViewModel
import com.mckimquyen.reader.ui.page.rpg.BrainRpgPage
import com.mckimquyen.reader.ui.page.setting.SettingsPage
import com.mckimquyen.reader.ui.page.setting.ai.AiSettingsPage
import com.mckimquyen.reader.ui.page.setting.zen.ZenSettingsPage
import com.mckimquyen.reader.ui.page.setting.acc.AccountDetailsPage
import com.mckimquyen.reader.ui.page.setting.acc.AccountsPage
import com.mckimquyen.reader.ui.page.setting.acc.AddAccountsPage
import com.mckimquyen.reader.ui.page.setting.color.ColorAndStylePage
import com.mckimquyen.reader.ui.page.setting.color.DarkThemePage
import com.mckimquyen.reader.ui.page.setting.color.feed.FeedsPageStylePage
import com.mckimquyen.reader.ui.page.setting.color.flow.FlowPageStylePage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingDarkThemePage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingImagePage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingStylePage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingTextPage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingTitlePage
import com.mckimquyen.reader.ui.page.setting.color.reading.ReadingVideoPage
import com.mckimquyen.reader.ui.page.setting.interaction.InteractionPage
import com.mckimquyen.reader.ui.page.setting.language.LanguagesPage
import com.mckimquyen.reader.ui.page.setting.tip.TipsAndSupportPage
import com.mckimquyen.reader.ui.page.setting.vip.VipManagementPage
import com.mckimquyen.reader.ui.page.startup.StartupPage
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.DataStoreKeys

@Composable
fun HomeEntry(
    homeViewModel: HomeViewModel = hiltViewModel(),
    activity: Activity,
) {
    val context = LocalContext.current
    val eInk = LocalEInkMode.current.value
    var isReadingPage by rememberSaveable { mutableStateOf(false) }
    val filterUiState = homeViewModel.filterUiState.collectAsStateValue()
    val navController = rememberNavController()

    val intent by rememberSaveable { mutableStateOf(context.findActivity()?.intent) }
    var openArticleId by rememberSaveable {
        mutableStateOf(intent?.extras?.getString(ExtraName.ARTICLE_ID) ?: "")
    }
    var initialRoute by rememberSaveable {
        mutableStateOf(intent?.getStringExtra(NotificationHelper.EXTRA_INITIAL_ROUTE).orEmpty())
    }
    intent?.replaceExtras(null)
    val notificationRoute = initialRoute.takeIf(String::isNotBlank)

    val showCommuteCast by homeViewModel.showCommuteCast.collectAsState()

    LaunchedEffect(Unit) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_START_COMMUTE, false) == true) {
            homeViewModel.openCommuteCast()
        }
    }

    LaunchedEffect(Unit) {
        // Load initial page asynchronously to avoid blocking
        val initialPage = withContext(Dispatchers.IO) {
            context.dataStore.data.map { prefs ->
                prefs[DataStoreKeys.InitialPage.key] ?: 0
            }.first()
        }

        resolveInitialRoute(notificationRoute, initialPage)?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            if (route == RouteName.BRAIN_RPG) initialRoute = ""
        }

        // Load initial filter asynchronously to avoid blocking
        val initialFilter = withContext(Dispatchers.IO) {
            context.dataStore.data.map { prefs ->
                prefs[DataStoreKeys.InitialFilter.key] ?: 2
            }.first()
        }

        homeViewModel.changeFilter(
            filterUiState.copy(
                filter = when (initialFilter) {
                    0 -> Filter.Starred
                    1 -> Filter.Unread
                    2 -> Filter.All
                    3 -> Filter.AddSources
                    else -> Filter.All
                }
            )
        )

        // This is finally
        navController.currentBackStackEntryFlow.collectLatest {
            Log.i("RLog", "currentBackStackEntry: ${navController.currentDestination?.route}")
            // Animation duration takes 310 ms
            delay(310L)
            isReadingPage = navController.currentDestination?.route == "${RouteName.READING}/{articleId}"
        }
    }

    LaunchedEffect(openArticleId) {
        if (openArticleId.isNotEmpty()) {
            navController.navigate(RouteName.FLOW) {
                launchSingleTop = true
            }
            navController.navigate("${RouteName.READING}/${openArticleId}") {
                launchSingleTop = true
            }
            openArticleId = ""
        }
    }

    val useDarkTheme = !eInk && if (isReadingPage) {
        LocalReadingDarkTheme.current.isDarkTheme()
    } else {
        LocalDarkTheme.current.isDarkTheme()
    }

    AppTheme(useDarkTheme = useDarkTheme) {

        // MainActivity.enableEdgeToEdge() owns transparent system bars; only icon contrast changes here.
        LaunchedEffect(useDarkTheme) {
            activity.window.decorView.let { decorView ->
                androidx.core.view.WindowCompat.getInsetsController(activity.window, decorView).run {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }

        CommuteCastDialog(
            visible = showCommuteCast,
            onDismiss = { homeViewModel.closeCommuteCast() },
        )

        NavHost(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            navController = navController,
            startDestination = if (context.isFirstLaunch) RouteName.STARTUP else RouteName.FEEDS,
        ) {
            // Startup
            animatedComposable(route = RouteName.STARTUP, eInk = eInk) {
                StartupPage(navController)
            }

            // Home
            animatedComposable(route = RouteName.FEEDS, eInk = eInk) {
                FeedsPage(navController = navController, homeViewModel = homeViewModel)
            }
            animatedComposable(route = RouteName.FLOW, eInk = eInk) {
                FlowPage(
                    navController = navController,
                    homeViewModel = homeViewModel,
                )
            }
            animatedComposable(route = RouteName.SMART_COLLECTIONS, eInk = eInk) {
                SmartCollectionsPage(navController = navController)
            }
            animatedComposable(route = RouteName.SMART_COLLECTION_DETAIL, eInk = eInk) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
                SmartCollectionDetailPage(navController = navController, collectionId = collectionId)
            }
            animatedComposable(route = RouteName.DAILY_DIGEST, eInk = eInk) {
                val digestViewModel: DigestViewModel = hiltViewModel()
                val state by digestViewModel.state.collectAsState()
                DigestPage(
                    state = state,
                    onGenerate = { digestViewModel.generate() },
                    onRetry = { digestViewModel.generate() },
                    onOpenSource = { articleId ->
                        navController.navigate("${RouteName.READING}/$articleId") { launchSingleTop = true }
                    },
                    onSelectDigest = { id -> digestViewModel.selectDigest(id) },
                )
            }
            animatedComposable(route = "${RouteName.READING}/{articleId}", eInk = eInk) {
                ReadingPage(navController = navController, homeViewModel = homeViewModel)
            }
            animatedComposable(route = RouteName.ADD_SOURCES, eInk = eInk) {
                AddSourcesMainPage(navController = navController)
            }

            // News Reels
            animatedComposable(route = RouteName.REELS, eInk = eInk) {
                ReelsPage(navController = navController)
            }
            animatedComposable(route = "${RouteName.ADD_SOURCES_DETAIL}/{countryCode}", eInk = eInk) { backStackEntry ->
                val countryCode = backStackEntry.arguments?.getString("countryCode") ?: "en"
                AddSourceDetailPage(navController = navController, countryCode = countryCode)
            }

            // Settings
            animatedComposable(route = RouteName.SETTINGS, eInk = eInk) {
                SettingsPage(navController = navController, activity = activity)
            }

            // AI Settings & BYOK
            animatedComposable(route = RouteName.AI_SETTINGS, eInk = eInk) {
                AiSettingsPage(navController = navController, activity = activity)
            }

            // Zen Focus & Speed Reading
            animatedComposable(route = RouteName.ZEN_FOCUS, eInk = eInk) {
                ZenSettingsPage(navController = navController, activity = activity)
            }

            // Feed Health Center
            animatedComposable(route = RouteName.FEED_HEALTH, eInk = eInk) {
                com.mckimquyen.reader.ui.page.setting.feedhealth.FeedHealthPage(
                    navController = navController,
                    activity = activity
                )
            }

            // Smart Filter Rules
            animatedComposable(route = RouteName.SMART_FILTER, eInk = eInk) {
                com.mckimquyen.reader.ui.page.setting.filter.SmartFilterPage(
                    navController = navController,
                    activity = activity
                )
            }

            // WebDAV Cloud Backup
            animatedComposable(route = RouteName.OPML_BACKUP, eInk = eInk) {
                com.mckimquyen.reader.ui.page.setting.backup.WebDavBackupPage(
                    navController = navController,
                    activity = activity
                )
            }

            // Watchdog Alert Inbox (REEL-06)
            animatedComposable(route = RouteName.WATCHDOG_INBOX, eInk = eInk) {
                com.mckimquyen.reader.ui.page.watchdog.WatchdogInboxPage(
                    navController = navController,
                    activity = activity
                )
            }

            // Brain RPG
            animatedComposable(route = RouteName.BRAIN_RPG, eInk = eInk) {
                BrainRpgPage(navController = navController, activity = activity)
            }

            // Notebook (Second Brain)
            animatedComposable(route = RouteName.NOTEBOOK, eInk = eInk) {
                NotebookPage(navController = navController)
            }

            // Second Brain graph (EXC-08)
            animatedComposable(route = RouteName.SECOND_BRAIN, eInk = eInk) {
                val secondBrainViewModel: SecondBrainViewModel = hiltViewModel()
                val state by secondBrainViewModel.state.collectAsState()
                SecondBrainPage(
                    state = state,
                    onSelectNode = secondBrainViewModel::selectNode,
                    onRangeChange = secondBrainViewModel::setRange,
                    onOpenArticle = { id -> navController.navigate("${RouteName.READING}/$id") { launchSingleTop = true } },
                    onRetry = secondBrainViewModel::refresh,
                )
            }

            // VIP
            animatedComposable(route = RouteName.VIP, eInk = eInk) {
                VipManagementPage(navController = navController)
            }

            // Accounts
            animatedComposable(route = RouteName.ACCOUNTS, eInk = eInk) {
                AccountsPage(navController)
            }

            animatedComposable(route = "${RouteName.ACCOUNT_DETAILS}/{accountId}", eInk = eInk) {
                AccountDetailsPage(navController)
            }

            animatedComposable(route = RouteName.ADD_ACCOUNTS, eInk = eInk) {
                AddAccountsPage(navController)
            }

            // Color & Style
            animatedComposable(route = RouteName.COLOR_AND_STYLE, eInk = eInk) {
                ColorAndStylePage(navController)
            }
            animatedComposable(route = RouteName.DARK_THEME, eInk = eInk) {
                DarkThemePage(navController)
            }
            animatedComposable(route = RouteName.FEEDS_PAGE_STYLE, eInk = eInk) {
                FeedsPageStylePage(navController)
            }
            animatedComposable(route = RouteName.FLOW_PAGE_STYLE, eInk = eInk) {
                FlowPageStylePage(navController)
            }
            animatedComposable(route = RouteName.READING_PAGE_STYLE, eInk = eInk) {
                ReadingStylePage(navController)
            }
            animatedComposable(route = RouteName.READING_DARK_THEME, eInk = eInk) {
                ReadingDarkThemePage(navController)
            }
            animatedComposable(route = RouteName.READING_PAGE_TITLE, eInk = eInk) {
                ReadingTitlePage(navController)
            }
            animatedComposable(route = RouteName.READING_PAGE_TEXT, eInk = eInk) {
                ReadingTextPage(navController)
            }
            animatedComposable(route = RouteName.READING_PAGE_IMAGE, eInk = eInk) {
                ReadingImagePage(navController)
            }
            animatedComposable(route = RouteName.READING_PAGE_VIDEO, eInk = eInk) {
                ReadingVideoPage(navController)
            }

            // Interaction
            animatedComposable(route = RouteName.INTERACTION, eInk = eInk) {
                InteractionPage(navController)
            }

            // Languages
            animatedComposable(route = RouteName.LANGUAGES, eInk = eInk) {
                LanguagesPage(navController = navController)
            }

            // Tips & Support
            animatedComposable(route = RouteName.TIPS_AND_SUPPORT, eInk = eInk) {
                TipsAndSupportPage(navController)
            }
        }
    }
}
