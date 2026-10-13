package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.lazy.LazyListState
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.ui.component.reader.BodyItemRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Drives [ReadingCompletionTracker] in a real Activity (ActivityScenario, no ComposeTestRule - see
 * BrainQuizCardWidgetTest) with a fake clock so the 30s dwell needs no real waiting.
 * Layout: 1 header item (index 0) + [BODY] body items (index 1..10), 100dp each in a 300dp viewport (3 visible).
 */
@RunWith(AndroidJUnit4::class)
class ReadingCompletionTrackerWidgetTest {
    private val clock = AtomicLong(1_000L)
    private val completions = AtomicInteger(0)
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var listState: LazyListState? = null
    private var scope: CoroutineScope? = null
    private val articleIdState = mutableStateOf("article-1")
    private val bodyRange = BodyItemRange().apply { start = 1; count = BODY }

    @After fun tearDown() { scenario?.close() }

    private fun launch(bodyCount: Int = BODY, initialArticle: String = "article-1") {
        bodyRange.start = 1
        bodyRange.count = bodyCount
        articleIdState.value = initialArticle
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { sc ->
            sc.onActivity { activity ->
                activity.setContent {
                    val state = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    listState = state
                    scope = coroutineScope
                    val article by articleIdState
                    ReadingCompletionTracker(
                        listState = state,
                        articleId = article,
                        bodyRange = bodyRange,
                        onCompleted = { completions.incrementAndGet() },
                        clock = { clock.get() },
                        pollMillis = POLL,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp), state = state) {
                        items(1 + BODY) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                    }
                }
            }
        }
        waitUntil { listState != null }
    }

    private fun scrollTo(index: Int) {
        scenario!!.onActivity { scope!!.launch { listState!!.scrollToItem(index) } }
        waitUntil { listState!!.firstVisibleItemIndex == index }
    }

    private fun advance(millis: Long) { clock.addAndGet(millis) }

    private fun settle() = Thread.sleep(SETTLE_MILLIS)

    private fun waitUntil(timeoutMillis: Long = 4_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("condition not met in ${timeoutMillis}ms", condition())
    }

    @Test fun completesOnce_afterEightyPercentOfBodyAnd30sForeground() {
        launch()
        scrollTo(6) // visible 6,7,8 -> 8 of 10 body items reached
        advance(30_000)
        waitUntil { completions.get() == 1 }
        advance(60_000)
        settle()
        assertEquals("must fire exactly once per visit", 1, completions.get())
    }

    @Test fun doesNotComplete_whenBodyProgressBelowEightyPercent_evenAfterLongDwell() {
        launch()
        scrollTo(5) // visible 5,6,7 -> 7 of 10
        advance(120_000)
        settle()
        assertEquals(0, completions.get())
    }

    @Test fun doesNotComplete_whenDwellUnder30s_evenAtTheEndOfTheBody() {
        launch()
        scrollTo(8)
        advance(29_000)
        settle()
        assertEquals(0, completions.get())
        advance(1_000)
        waitUntil { completions.get() == 1 }
    }

    @Test fun headerOnlyScroll_neverCounts_becauseOnlyBodyItemsAreInTheRange() {
        launch()
        scrollTo(0) // header + first two body items
        advance(120_000)
        settle()
        assertEquals(0, completions.get())
    }

    @Test fun emptyBody_neverCompletes() {
        launch(bodyCount = 0)
        scrollTo(8)
        advance(120_000)
        settle()
        assertEquals(0, completions.get())
    }

    @Test fun blankArticleId_neverCompletes() {
        launch(initialArticle = "")
        scrollTo(8)
        advance(120_000)
        settle()
        assertEquals(0, completions.get())
    }

    @Test fun timeSpentInBackground_doesNotCountTowardsDwell() {
        launch()
        scrollTo(8)
        advance(20_000)
        settle()
        scenario!!.moveToState(Lifecycle.State.CREATED) // activity stopped
        advance(300_000)
        settle()
        assertEquals("background time must not count", 0, completions.get())
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        advance(9_000)
        settle()
        assertEquals("only 29s of foreground so far", 0, completions.get())
        advance(1_500)
        waitUntil { completions.get() == 1 }
    }

    @Test fun switchingArticle_startsAFreshVisit() {
        launch()
        scrollTo(8)
        advance(25_000)
        settle()
        scenario!!.onActivity { articleIdState.value = "article-2" }
        settle()
        advance(10_000)
        settle()
        assertEquals("dwell restarts for the new article", 0, completions.get())
        advance(20_000)
        waitUntil { completions.get() == 1 }
        assertFalse(articleIdState.value == "article-1")
    }

    private companion object {
        const val BODY = 10
        const val POLL = 20L
        const val SETTLE_MILLIS = 400L
    }
}
