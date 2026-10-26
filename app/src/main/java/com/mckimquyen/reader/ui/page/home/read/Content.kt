package com.mckimquyen.reader.ui.page.home.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.mckimquyen.reader.ui.component.quote.QuoteTextToolbar
import com.mckimquyen.reader.ui.component.quote.QuoteShareChip
import com.mckimquyen.reader.domain.model.quote.QuoteSelection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.os.SystemClock
import android.widget.Toast
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLink
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLinkSpecificBrowser
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadUpperCase
import com.mckimquyen.reader.ui.component.base.BaseExtensibleVisibility
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import com.mckimquyen.reader.ui.component.reader.ItemCountingScope
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.component.reader.recordRange
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
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
    brainRpgViewModel: BrainRpgViewModel? = null,
    completionClock: () -> Long = SystemClock::elapsedRealtime,
    onShareQuote: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val eInkEnabled = LocalEInkMode.current.value

    // Built off the main thread (Room peer lookup on IO, generation on Default); null until ready, so the card appears
    // a moment after the article instead of blocking composition. produceState is avoided on purpose: lint's
    // ProduceStateDoesNotAssignValue rejects it here (same rule FIX-21 hit), and LaunchedEffect says the same thing.
    var quiz by remember(articleId, title, content) { mutableStateOf<QuizQuestion?>(null) }
    LaunchedEffect(articleId, title, content, brainRpgViewModel) {
        if (brainRpgViewModel != null && articleId.isNotBlank() && (title.isNotBlank() || content.isNotBlank())) {
            quiz = brainRpgViewModel.buildQuiz(articleId, title, content, author)
        }
    }

    val doubleRewardedIds by (brainRpgViewModel?.doubleRewardedArticleIds ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) })
        .collectAsState()

    val articleCategory = remember(title, content) {
        brainRpgViewModel?.quizGeneratorService?.detectCategory(title, content) ?: ""
    }

    val pageTurnScope = rememberCoroutineScope()

    val bodyRange = remember { BodyItemRange() }
    if (brainRpgViewModel != null) {
        ReadingCompletionTracker(listState, articleId, bodyRange, clock = completionClock, onCompleted = {
            brainRpgViewModel.onArticleReadFinished(articleId, articleCategory) {
                Toast.makeText(context, context.getString(R.string.brain_rpg_reading_reward), Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Toolbar bọc: giữ menu chọn chữ gốc, thêm chip "Chia sẻ trích dẫn". Khi chia sẻ, đoạn chọn được copy
    // vào clipboard để đọc lại, rồi clipboard của người dùng được khôi phục ngay trước khi mở sheet.
    val platformToolbar = LocalTextToolbar.current
    val clipboard = LocalClipboardManager.current
    val latestOnShareQuote by rememberUpdatedState(onShareQuote)
    val quoteToolbar = remember(platformToolbar, clipboard) {
        QuoteTextToolbar(platformToolbar) {
            val previous = clipboard.getClip()
            val selected = QuoteSelection.sanitize(clipboard.getText()?.text)
            previous?.let { clipboard.setClip(it) }
            if (selected != null) latestOnShareQuote(selected)
        }
    }

    Box {
    CompositionLocalProvider(LocalTextToolbar provides quoteToolbar) {
    SelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .eInkTapZones(enabled = eInkEnabled, key = articleId) { direction ->
                    pageTurnScope.launch { listState.turnPage(direction) }
                }
                .drawVerticalScrollbar(listState),
            state = listState,
        ) {
          // Counting scope lets the tracker know exactly which items Reader appended (metadata/loader/quiz excluded).
          with(ItemCountingScope(this)) {
            bodyRange.clear()
            item {
                // TopBar is now a normal Column sibling above this Content (see ReadingPage.kt),
                // so it already claims its own space — no manual status-bar/TopBar-height
                // clearance spacers needed here anymore. Keep only the intentional visual gap.
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
                recordRange(bodyRange) {
                    Reader(
                        subheadUpperCase = subheadUpperCase.value,
                        link = link ?: "",
                        content = content,
                        onLinkClick = {
                            context.openURL(it, openLink, openLinkSpecificBrowser)
                        }
                    )
                }
                quiz?.let { quiz ->
                    item {
                        BrainQuizCard(
                            quiz = quiz,
                            doubleAlreadyClaimed = quiz.articleId in doubleRewardedIds,
                            onAnswerSubmitted = { isCorrect ->
                                brainRpgViewModel?.submitQuizAnswer(quiz.articleId, quiz.category, isCorrect) { _, awarded ->
                                    if (isCorrect && awarded > 0) {
                                        Toast.makeText(context, "+$awarded XP! 🎉", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDoubleXpRequested = { act, onDone ->
                                brainRpgViewModel?.doubleQuizReward(act, quiz.articleId, quiz.category, 150L, onDone)
                            },
                            onRetryQuizRequested = { act, onDone ->
                                brainRpgViewModel?.retryQuiz(act, onDone)
                            }
                        )
                    }
                }
            }
            item {
                // Small fixed breathing room at the true end of the article, above BottomBar/Ad
                // (now plain Column siblings below this list, not an overlay — no reservation
                // calculation needed here anymore).
                Spacer(modifier = Modifier.height(24.dp))
            }
          }
        }
    }
    }
    QuoteShareChip(quoteToolbar)
    }
}
