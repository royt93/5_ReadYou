package com.mckimquyen.reader.ui.page.home.read

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mckimquyen.reader.domain.model.rpg.ReadingCompletionGate
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import kotlinx.coroutines.delay

internal const val COMPLETION_POLL_MILLIS = 1_000L

/**
 * Fires [onCompleted] once per article visit after >= 80% of the Reader-appended body items were reached AND the
 * article stayed in the foreground for 30s ([ReadingCompletionGate]). Time spent while the screen is stopped does not
 * count. A new [articleId] starts a fresh visit; a blank id or an empty body never completes.
 */
@Composable
fun ReadingCompletionTracker(
    listState: LazyListState,
    articleId: String,
    bodyRange: BodyItemRange,
    onCompleted: () -> Unit,
    clock: () -> Long = SystemClock::elapsedRealtime,
    pollMillis: Long = COMPLETION_POLL_MILLIS,
) {
    val gate = remember(articleId) { ReadingCompletionGate() }
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentClock by rememberUpdatedState(clock)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(gate, lifecycle) {
        gate.onForegroundChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), currentClock())
        val observer = LifecycleEventObserver { source, _ ->
            gate.onForegroundChanged(source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), currentClock())
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(gate, articleId) {
        if (articleId.isBlank()) return@LaunchedEffect
        gate.onArticleChanged(articleId, currentClock())
        while (true) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (gate.isComplete(articleId, lastVisible, bodyRange.start, bodyRange.count, currentClock())) {
                currentOnCompleted()
                return@LaunchedEffect
            }
            delay(pollMillis)
        }
    }
}
