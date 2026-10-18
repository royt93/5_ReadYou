package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EInkPageTurnInstrumentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sideTapTurnsByViewportAndClampsAtTop() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().eInkTapZones(enabled = true, key = ARTICLE_ID) { direction ->
                    scope.launch { listState.turnPage(direction) }
                },
            ) {
                items((0 until ITEM_COUNT).toList()) { index ->
                    Box(Modifier.fillMaxSize().height(ITEM_HEIGHT)) { Text("row-$index") }
                }
            }
        }
        composeRule.waitForIdle()

        val startIndex = listState.firstVisibleItemIndex
        composeRule.onRoot().performTouchInput { click(centerRight) }
        composeRule.waitForIdle()
        assertTrue("Right edge should advance one viewport, from $startIndex", listState.firstVisibleItemIndex > startIndex)

        val afterNext = listState.firstVisibleItemIndex
        composeRule.onRoot().performTouchInput { click(centerLeft) }
        composeRule.waitForIdle()
        assertTrue("Left edge should retreat", listState.firstVisibleItemIndex < afterNext)

        composeRule.runOnUiThread { kotlinx.coroutines.runBlocking { listState.scrollToItem(0) } }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(centerLeft) }
        composeRule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)
    }

    @Test
    fun centerTapKeepsChildClickAndDoesNotTurnPage() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var clickCount: androidx.compose.runtime.MutableIntState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        composeRule.setContent {
            listState = rememberLazyListState()
            clickCount = remember { mutableIntStateOf(0) }
            scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().eInkTapZones(enabled = true, key = ARTICLE_ID) { direction ->
                    scope.launch { listState.turnPage(direction) }
                },
            ) {
                items((0 until ITEM_COUNT).toList()) { index ->
                    Box(
                        Modifier.fillMaxSize()
                            .height(ITEM_HEIGHT)
                            .clickable { clickCount.intValue++ }
                            .testTag("row-$index")
                    ) { Text("row-$index") }
                }
            }
        }
        composeRule.waitForIdle()
        val initial = listState.firstVisibleItemIndex
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(1, clickCount.intValue)
        assertEquals(initial, listState.firstVisibleItemIndex)
    }

    @Test
    fun disabledModeDoesNotPageTurn() {
        lateinit var listState: androidx.compose.foundation.lazy.LazyListState
        lateinit var scope: kotlinx.coroutines.CoroutineScope
        composeRule.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().eInkTapZones(enabled = false, key = ARTICLE_ID) { direction ->
                    scope.launch { listState.turnPage(direction) }
                },
            ) {
                items((0 until ITEM_COUNT).toList()) { index ->
                    Box(Modifier.fillMaxSize().height(ITEM_HEIGHT)) { Text("row-$index") }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(centerRight) }
        composeRule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)
    }

    private companion object {
        const val ARTICLE_ID = "test-article"
        const val ITEM_COUNT = 20
        val ITEM_HEIGHT = 100.dp
    }
}
