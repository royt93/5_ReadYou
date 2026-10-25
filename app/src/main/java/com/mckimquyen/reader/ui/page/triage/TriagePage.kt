package com.mckimquyen.reader.ui.page.triage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.common.RouteName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriagePage(
    navController: NavController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TriageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.feed_zero_triage_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (!uiState.isInboxZero && uiState.totalCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.triage_progress_format,
                                    uiState.processedCount,
                                    uiState.totalCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    },
                )
                if (!uiState.isInboxZero && uiState.totalCount > 0) {
                    val progress = (uiState.processedCount.toFloat() / uiState.totalCount.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isInboxZero -> {
                    TriageInboxZeroView(
                        processedCount = uiState.processedCount,
                        savedCount = uiState.savedCount,
                        skippedCount = uiState.skippedCount,
                        totalXpEarned = uiState.totalXpEarned,
                        onFinish = onBack,
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TriageCardStack(
                            currentArticle = uiState.currentArticle,
                            nextArticle = uiState.nextArticle,
                            onSwipeLeft = { viewModel.skipCurrent() },
                            onSwipeRight = { viewModel.saveCurrent() },
                            onSwipeUp = {
                                val id = viewModel.markCurrentReadForNavigation()
                                if (id != null) {
                                    navController.navigate("${RouteName.READING}/$id")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TriageBottomBar(
                            onSkip = { viewModel.skipCurrent() },
                            onReadNow = {
                                val id = viewModel.markCurrentReadForNavigation()
                                if (id != null) {
                                    navController.navigate("${RouteName.READING}/$id")
                                }
                            },
                            onSave = { viewModel.saveCurrent() },
                        )
                    }
                }
            }
        }
    }
}
