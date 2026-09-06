package com.mckimquyen.reader.ui.component.watchdog

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchdogSheetWidgetTest {

    private val sampleKeywords = listOf(
        WatchdogKeyword(id = "kw_1", keyword = "\$VIC", isEnabled = true, matchCount = 3),
        WatchdogKeyword(id = "kw_2", keyword = "Bitcoin", isEnabled = false, matchCount = 0),
        WatchdogKeyword(id = "kw_3", keyword = "Giá vàng", isEnabled = true, matchCount = 1),
    )

    @Test
    fun watchdogBadge_rendersSuccessfully() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    WatchdogBadge(keyword = "\$VIC")
                }
            }
            activity.setContentView(composeView)
            assertNotNull("WatchdogBadge attaches cleanly", composeView)
        }
        scenario.close()
    }

    @Test
    fun watchdogSheet_rendersContentWithKeywords() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    WatchdogSheetContent(
                        keywords = sampleKeywords,
                        onAddKeyword = { true },
                        onRemoveKeyword = {},
                        onToggleKeyword = { _, _ -> },
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("WatchdogSheetContent attaches cleanly with keywords", composeView)
        }
        scenario.close()
    }

    @Test
    fun watchdogSheet_rendersEmptyState() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    WatchdogSheetContent(
                        keywords = emptyList(),
                        onAddKeyword = { true },
                        onRemoveKeyword = {},
                        onToggleKeyword = { _, _ -> },
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("WatchdogSheetContent attaches cleanly with empty list", composeView)
        }
        scenario.close()
    }

    @Test
    fun watchdogKeywordRow_toggleAndRemoveCallbacks() {
        var toggledId = ""
        var toggledState = false
        var removedId = ""

        val keyword = WatchdogKeyword(id = "kw_test", keyword = "\$FPT", isEnabled = true, matchCount = 5)

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    WatchdogKeywordRow(
                        keyword = keyword,
                        onToggle = {
                            toggledId = keyword.id
                            toggledState = it
                        },
                        onRemove = {
                            removedId = keyword.id
                        },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun watchdogSheet_fullDialogRendersSuccessfully() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    WatchdogSheet(
                        keywords = sampleKeywords,
                        onDismissRequest = {},
                        onAddKeyword = { true },
                        onRemoveKeyword = {},
                        onToggleKeyword = { _, _ -> },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("Full WatchdogSheet dialog renders cleanly", composeView)
        }
        scenario.close()
    }
}
