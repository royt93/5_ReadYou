package com.mckimquyen.reader.ui.page.home.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLink
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLinkSpecificBrowser
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadUpperCase
import com.mckimquyen.reader.ui.component.base.BaseExtensibleVisibility
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.component.rpg.BrainQuizCard
import com.mckimquyen.reader.ui.ext.drawVerticalScrollbar
import com.mckimquyen.reader.ui.ext.openURL
import com.mckimquyen.reader.ui.page.rpg.BrainRpgViewModel
import java.util.Date

@Composable
fun Content(
    content: String,
    feedName: String,
    title: String,
    author: String? = null,
    link: String? = null,
    publishedDate: Date,
    listState: LazyListState,
    isLoading: Boolean,
    articleId: String = "",
    brainRpgViewModel: BrainRpgViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current

    val quiz = remember(articleId, content) {
        if (articleId.isNotBlank()) {
            brainRpgViewModel.quizGeneratorService.generateQuiz(articleId, title, content, author)
        } else null
    }

    LaunchedEffect(listState, articleId) {
        if (articleId.isNotBlank() && quiz != null) {
            snapshotFlow {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 2 && lastVisible >= totalItems - 2
            }.collect { isNearBottom ->
                if (isNearBottom) {
                    brainRpgViewModel.onArticleReadFinished(articleId, quiz.category)
                }
            }
        }
    }

    SelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drawVerticalScrollbar(listState),
            state = listState,
        ) {
            item {
                // Top bar status bar inset and height
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(64.dp))
                // padding
                Spacer(modifier = Modifier.height(22.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                ) {
                    DisableSelection {
                        Metadata(
                            feedName = feedName,
                            title = title,
                            author = author,
                            link = link,
                            publishedDate = publishedDate,
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(22.dp))
                BaseExtensibleVisibility(visible = isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(22.dp))
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(30.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                        }
                    }
                }
            }
            if (!isLoading) {
                Reader(
                    subheadUpperCase = subheadUpperCase.value,
                    link = link ?: "",
                    content = content,
                    onLinkClick = {
                        context.openURL(it, openLink, openLinkSpecificBrowser)
                    }
                )
                if (quiz != null) {
                    item {
                        BrainQuizCard(
                            quiz = quiz,
                            onAnswerSubmitted = { isCorrect ->
                                brainRpgViewModel.submitQuizAnswer(quiz.category, isCorrect) { _, _ -> }
                            },
                            onDoubleXpRequested = { act, onDone ->
                                brainRpgViewModel.doubleQuizReward(act, quiz.category, 150L, onDone)
                            }
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(128.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
