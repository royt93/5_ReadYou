package com.mckimquyen.reader.ui.page.home.read

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Tương tác với renderer và toolbar thật, không cần Hilt hoặc SDK quảng cáo. */
class EInkReaderInteractionQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun centerHtmlLinkOpensDestinationWithoutPageTurn() {
        val opened = AtomicReference<String>()
        val turns = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides EInkModePref.ON) {
                AppTheme(useDarkTheme = true) {
                    LazyColumn(Modifier.fillMaxSize().testTag(ROOT).eInkTapZones(true, ARTICLE) { turns.incrementAndGet() }) {
                        Reader(link = LINK, content = "<p><a href=\"$LINK\">${"Lien ket ".repeat(30)}</a></p>", onLinkClick = opened::set)
                    }
                }
            }
        }
        composeRule.onNode(hasText("Lien ket", substring = true)).performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertEquals(LINK, opened.get())
            assertEquals(0, turns.get())
        }
    }

    @Test
    fun centerLongPressKeepsRealTextSelectionAndCopy() {
        val turns = AtomicInteger()
        val copy = AtomicReference<(() -> Unit)?>()
        val toolbar = object : TextToolbar {
            override val status: TextToolbarStatus get() = if (copy.get() == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown
            override fun hide() { copy.set(null) }
            override fun showMenu(rect: androidx.compose.ui.geometry.Rect, onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                copy.set(onCopyRequested)
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides EInkModePref.ON, LocalTextToolbar provides toolbar) {
                AppTheme(useDarkTheme = false) {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().testTag(ROOT).eInkTapZones(true, ARTICLE) { turns.incrementAndGet() }) {
                            Reader(link = LINK, content = "<p>$PROSE</p>", onLinkClick = {})
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText(PROSE).performTouchInput { longClick(center) }
        composeRule.waitUntil(TIMEOUT_MS) { copy.get() != null }
        composeRule.runOnUiThread { requireNotNull(copy.get()).invoke() }
        val clipboard = composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.runOnUiThread {
            val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(composeRule.activity)?.toString().orEmpty()
            assertTrue("Copy phải có từ đã chọn, nhận được: $copied", copied.isNotBlank() && PROSE.contains(copied))
            assertEquals(0, turns.get())
        }
    }

    @Test
    fun realReaderChromeClearsSystemBarsAndRsvpActionRemainsClickable() {
        val rsvp = AtomicInteger()
        composeRule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false) }
        composeRule.setContent {
            CompositionLocalProvider(LocalEInkMode provides EInkModePref.ON) {
                AppTheme(useDarkTheme = true) {
                    val list = rememberLazyListState()
                    BaseScaffold {
                        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                            Box(Modifier.testTag(TOP)) {
                                TopBar(navController = NavHostController(composeRule.activity), isShow = true,
                                    title = "QA Reader", link = LINK, isPlayingAudio = false,
                                    onRsvpReading = { rsvp.incrementAndGet() })
                            }
                            Box(Modifier.weight(1f).testTag(BODY)) {
                                Content(content = "<p>$PROSE</p>".repeat(PARAGRAPHS), feedName = "QA Feed", title = "QA Reader",
                                    publishedDate = java.util.Date(0), listState = list, isLoading = false)
                            }
                            Box(Modifier.testTag(BOTTOM)) {
                                BottomBar(isShow = true, isUnread = false, isStarred = false, isFullContent = true)
                            }
                        }
                    }
                }
            }
        }
        val root = composeRule.activity.window.decorView
        var status = 0
        var nav = 0
        composeRule.waitUntil(TIMEOUT_MS) {
            val insets = ViewCompat.getRootWindowInsets(root)
            status = insets?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0
            nav = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            status > 0 && nav > 0
        }
        val top = composeRule.onNodeWithTag(TOP).fetchSemanticsNode().boundsInWindow
        val bottom = composeRule.onNodeWithTag(BOTTOM).fetchSemanticsNode().boundsInWindow
        val body = composeRule.onNodeWithTag(BODY).fetchSemanticsNode().boundsInWindow
        assertTrue("TopBar dưới status inset", top.top >= status)
        assertTrue("BottomBar trên navigation inset", bottom.bottom <= root.height - nav)
        assertTrue("Body không chồng toolbar", body.top >= top.bottom && body.bottom <= bottom.top)
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.zen_speed_reading)).performClick()
        assertEquals(1, rsvp.get())
    }

    private companion object {
        const val ROOT = "reader-qa-root"
        const val TOP = "reader-qa-top"
        const val BOTTOM = "reader-qa-bottom"
        const val BODY = "reader-qa-body"
        const val ARTICLE = "qa-article"
        const val LINK = "https://example.com/eink-qa"
        const val PROSE = "Selection survives the E-Ink center gesture without turning any page."
        const val TIMEOUT_MS = 10_000L
        const val PARAGRAPHS = 30
    }
}
