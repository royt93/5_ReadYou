package com.mckimquyen.reader.integration

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import com.mckimquyen.reader.ui.component.reader.ItemCountingScope
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.component.reader.recordRange
import com.mckimquyen.reader.ui.page.home.read.ReadingCompletionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** End-to-end check that Reader's actual Jsoup-produced items, not metadata/footer items, feed the RPG gate. */
@RunWith(AndroidJUnit4::class)
class ReadingCompletionGateIntegrationTest {
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var stateScope: CoroutineScope? = null
    private val lazyState = AtomicReferenceLazyState()
    private val now = AtomicLong(0L)
    private val completed = AtomicInteger(0)
    private val range = BodyItemRange()

    @After fun tearDown() { scenario?.close() }

    @Test fun realReaderBodyRange_excludesHeaderAndFooter_andAwardsOnceAfterDwell() {
        val html = buildString {
            append("<article>")
            repeat(BODY_PARAGRAPHS) { append("<p>Paragraph $it contains enough article text to become a Reader list item.</p>") }
            append("</article>")
        }
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.setContent {
                val state = rememberLazyListState()
                val scope = rememberCoroutineScope()
                SideEffect { lazyState.value = state; stateScope = scope }
                val bounds = remember { range }
                ReadingCompletionTracker(
                    listState = state,
                    articleId = ARTICLE_ID,
                    bodyRange = bounds,
                    onCompleted = { completed.incrementAndGet() },
                    clock = { now.get() },
                    pollMillis = POLL_MILLIS,
                )
                LazyColumn(Modifier.fillMaxWidth().height(VIEWPORT_DP.dp), state) {
                    with(ItemCountingScope(this)) {
                        item { Box(Modifier.height(HEADER_DP.dp)) }
                        recordRange(bounds) {
                            Reader(link = "https://example.test", content = html, onLinkClick = {})
                        }
                        item { Box(Modifier.height(HEADER_DP.dp)) } // quiz/footer after body
                    }
                }
            }
        }
        waitUntil { range.count > 0 }
        assertEquals("body starts after the metadata header", 1, range.start)
        assertTrue("Reader must append body items", range.count > 0)

        val state = requireNotNull(lazyState.value)
        scenario!!.onActivity { stateScope!!.launch { state.scrollToItem(range.start + range.count - 1) } }
        waitUntil { state.firstVisibleItemIndex >= range.start + range.count - 1 }
        Thread.sleep(SETTLE_MILLIS)
        assertEquals("scroll alone must not satisfy the foreground dwell", 0, completed.get())
        now.addAndGet(30_000L)
        waitUntil { completed.get() == 1 }
        now.addAndGet(60_000L)
        Thread.sleep(SETTLE_MILLIS)
        assertEquals("one award callback per visit", 1, completed.get())
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("condition did not become true", condition())
    }

    private class AtomicReferenceLazyState {
        @Volatile var value: androidx.compose.foundation.lazy.LazyListState? = null
    }

    private companion object {
        const val ARTICLE_ID = "article-real-reader"
        const val BODY_PARAGRAPHS = 12
        const val HEADER_DP = 80
        const val VIEWPORT_DP = 240
        const val POLL_MILLIS = 20L
        const val SETTLE_MILLIS = 300L
    }
}
