package com.mckimquyen.reader.ui.page.onthisday

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.onthisday.yearsAgo
import com.mckimquyen.reader.ui.page.common.RouteName

const val ON_THIS_DAY_PAGE_TITLE_TAG = "on_this_day_page_title"
const val ON_THIS_DAY_EMPTY_TAG = "on_this_day_empty"
const val ON_THIS_DAY_ERROR_TAG = "on_this_day_error"
const val ON_THIS_DAY_RETRY_TAG = "on_this_day_retry"
const val ON_THIS_DAY_LIST_TAG = "on_this_day_list"
const val ON_THIS_DAY_LOADING_TAG = "on_this_day_loading"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnThisDayPage(
    navController: NavHostController,
    viewModel: OnThisDayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                        .testTag(ON_THIS_DAY_PAGE_TITLE_TAG),
                    text = stringResource(R.string.on_this_day_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { innerPadding ->
        when (val s = state) {
            is OnThisDayUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = navBarBottom)
                        .testTag(ON_THIS_DAY_LOADING_TAG),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            is OnThisDayUiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = navBarBottom)
                        .padding(24.dp)
                        .testTag(ON_THIS_DAY_EMPTY_TAG),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.on_this_day_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is OnThisDayUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = navBarBottom)
                        .padding(24.dp)
                        .testTag(ON_THIS_DAY_ERROR_TAG),
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
                        modifier = Modifier.testTag(ON_THIS_DAY_RETRY_TAG),
                        onClick = { viewModel.refresh() },
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is OnThisDayUiState.Content -> {
                val context = LocalContext.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = navBarBottom)
                        .testTag(ON_THIS_DAY_LIST_TAG),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.articles, key = { it.article.id }) { item ->
                        val years = yearsAgo(item.article.date, s.currentYear)
                        val yearsText = context.resources.getQuantityString(
                            R.plurals.on_this_day_years_ago,
                            years,
                            years,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate("${RouteName.READING}/${item.article.id}") {
                                        launchSingleTop = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = item.article.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = "${item.feed.name} · $yearsText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
