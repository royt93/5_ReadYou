package com.mckimquyen.reader.ui.page.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.radar.DiffPart
import com.mckimquyen.reader.domain.radar.DiffType
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

const val DIFF_PAGE_TITLE_TAG = "radar_diff_title"
const val DIFF_EMPTY_TAG = "radar_diff_empty"
const val DIFF_REMOVED_NOTICE_TAG = "radar_diff_removed_notice"
const val DIFF_BODY_TAG = "radar_diff_body"

// Fixed hues are intentional: red = old/struck, green = new, readable on light and dark surfaces.
private val RemovedColor = Color(0xFFD32F2F)
private val AddedColor = Color(0xFF2E7D32)

@Composable
fun ArticleDiffPage(
    navController: NavHostController,
    articleId: String,
    viewModel: ArticleDiffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(articleId) { viewModel.load(articleId) }
    ArticleDiffContent(state = state, onBack = { navController.popBackStack() })
}

@Composable
fun ArticleDiffContent(state: ArticleDiffUiState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
            Text(
                modifier = Modifier.weight(1f).testTag(DIFF_PAGE_TITLE_TAG),
                text = stringResource(R.string.radar_diff_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when (state) {
            ArticleDiffUiState.Loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            ArticleDiffUiState.Error -> Message(stringResource(R.string.radar_diff_no_history), DIFF_EMPTY_TAG)
            is ArticleDiffUiState.Ready -> {
                val diff = state.diff
                if (!diff.hasHistory) {
                    Message(stringResource(R.string.radar_diff_no_history), DIFF_EMPTY_TAG)
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    ) {
                        if (diff.removed) {
                            Text(
                                modifier = Modifier.testTag(DIFF_REMOVED_NOTICE_TAG),
                                text = stringResource(R.string.radar_diff_removed_notice),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = stringResource(R.string.radar_diff_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        Section(stringResource(R.string.radar_diff_headline), diff.titleDiff, "radar_diff_title_text")
                        Spacer(Modifier.height(16.dp))
                        Section(stringResource(R.string.radar_diff_text), diff.bodyDiff, DIFF_BODY_TAG)
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Section(label: String, parts: List<DiffPart>, tag: String) {
    Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        modifier = Modifier.testTag(tag),
        text = diffText(parts),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

fun diffText(parts: List<DiffPart>): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { index, part ->
        if (index > 0) append(' ')
        when (part.type) {
            DiffType.SAME -> append(part.text)
            DiffType.REMOVED -> withStyle(SpanStyle(color = RemovedColor, textDecoration = TextDecoration.LineThrough)) { append(part.text) }
            DiffType.ADDED -> withStyle(SpanStyle(color = AddedColor, fontWeight = FontWeight.SemiBold)) { append(part.text) }
        }
    }
}
