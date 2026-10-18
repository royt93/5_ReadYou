package com.mckimquyen.reader.ui.page.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.statistics.ReadingStats
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.statistics.ReadingHeatmap
import java.time.LocalDate
import java.util.Locale

const val STATS_PAGE_TITLE_TAG = "reading_stats_title"
const val STATS_EMPTY_TAG = "reading_stats_empty"
const val STATS_ERROR_TAG = "reading_stats_error"
const val STATS_RETRY_TAG = "reading_stats_retry"
const val STATS_CONTENT_TAG = "reading_stats_content"
const val STATS_TOP_FEED_TAG_PREFIX = "reading_stats_feed_"

private const val STREAK_BADGE_WEEK_DAYS = 7
private const val STREAK_BADGE_MONTH_DAYS = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatisticsPage(
    navController: NavHostController,
    viewModel: ReadingStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Làm mới khi quay lại màn hình (vd sau khi đọc bài). Observer được gỡ khi rời composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedbackIconButton(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = { navController.popBackStack() },
                )
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(STATS_PAGE_TITLE_TAG),
                    text = stringResource(R.string.reading_stats_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { innerPadding ->
        when (val s = state) {
            is ReadingStatisticsUiState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = navBarBottom),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            is ReadingStatisticsUiState.Empty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = navBarBottom)
                    .padding(24.dp)
                    .testTag(STATS_EMPTY_TAG),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reading_stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ReadingStatisticsUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = navBarBottom)
                    .padding(24.dp)
                    .testTag(STATS_ERROR_TAG),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(s.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.size(16.dp))
                Button(
                    modifier = Modifier.testTag(STATS_RETRY_TAG),
                    onClick = { viewModel.refresh() },
                ) { Text(stringResource(R.string.retry)) }
            }

            is ReadingStatisticsUiState.Content -> StatisticsContent(
                stats = s.stats,
                today = LocalDate.now(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                bottomInset = navBarBottom,
            )
        }
    }
}

@Composable
private fun StatisticsContent(
    stats: ReadingStats,
    today: LocalDate,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(STATS_CONTENT_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), stringResource(R.string.reading_stats_total), stats.totalRead.toString())
                StatCard(Modifier.weight(1f), stringResource(R.string.reading_stats_today), stats.readToday.toString())
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    Modifier.weight(1f),
                    stringResource(R.string.reading_stats_current_streak),
                    pluralStringResource(R.plurals.reading_stats_days, stats.currentStreak, stats.currentStreak),
                )
                StatCard(
                    Modifier.weight(1f),
                    stringResource(R.string.reading_stats_longest_streak),
                    pluralStringResource(R.plurals.reading_stats_days, stats.longestStreak, stats.longestStreak),
                )
            }
        }
        item {
            StatCard(
                Modifier.fillMaxWidth(),
                stringResource(R.string.reading_stats_avg_per_day),
                String.format(Locale.getDefault(), "%.1f", stats.avgPerActiveDay),
            )
        }
        item { ReadingHeatmap(heatmap = stats.heatmap, today = today) }
        if (stats.longestStreak >= STREAK_BADGE_WEEK_DAYS) {
            item { BadgeRow(stringResource(R.string.reading_stats_badge_week)) }
        }
        if (stats.longestStreak >= STREAK_BADGE_MONTH_DAYS) {
            item { BadgeRow(stringResource(R.string.reading_stats_badge_month)) }
        }
        if (stats.topFeeds.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.reading_stats_top_feeds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(stats.topFeeds, key = { it.feedId }) { feed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(STATS_TOP_FEED_TAG_PREFIX + feed.feedId)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = feed.feedName,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.reading_stats_articles, feed.count, feed.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgeRow(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
