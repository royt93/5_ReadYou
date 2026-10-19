package com.mckimquyen.reader.ui.page.home.read.quote

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.infrastructure.quote.QuoteCardRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import com.mckimquyen.reader.ui.component.quote.QuoteTextToolbar
import com.mckimquyen.reader.ui.component.reader.Reader
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QuoteCardInstrumentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sharerUriIsReadableAndDecodesAs1080x1350() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharer = QuoteCardSharer(context, Dispatchers.IO)
        val spec = QuoteCardSpec("A real quote for the device.", "Feed", "Author", "Title")
        val bitmap = QuoteCardRenderer.render(spec, QuoteCardTemplate.DARK_STUDIO)

        val file = sharer.writePng(bitmap, QuoteCardTemplate.DARK_STUDIO)
        val intent = sharer.buildShareIntent(file, "title")
        @Suppress("DEPRECATION")
        val send = intent.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!

        val decoded = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertNotNull(decoded)
        assertEquals(QuoteCardRenderer.CARD_WIDTH, decoded.width)
        assertEquals(QuoteCardRenderer.CARD_HEIGHT, decoded.height)
        file.delete()
        }
    }

    @Test
    fun sheetContentClearsSystemBarInsets() {
        val bitmap = Bitmap.createBitmap(108, 135, Bitmap.Config.ARGB_8888)
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        AppTheme(useDarkTheme = false) {
                            QuoteCardSheetContent(
                                state = QuoteCardUiState.Ready(bitmap, QuoteCardTemplate.MINIMALIST, false),
                                selected = QuoteCardTemplate.MINIMALIST,
                                onSelectTemplate = {},
                                onShare = {},
                                onRetry = {},
                                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
                            )
                        }
                    }
                }
            )
        }

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var measured: Triple<Int, Int, Int>? = null // previewTop, shareBottom, decorHeight
        var insets: Pair<Int, Int> = 0 to 0
        while (measured == null && System.currentTimeMillis() < deadline) {
            composeRule.runOnUiThread {
                val decor = activity.window.decorView
                val rootInsets = ViewCompat.getRootWindowInsets(decor)
                val top = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0
                val bottom = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                val root = findComposeRoot(decor)
                val preview = root?.findTagBounds(QUOTE_SHEET_PREVIEW_TAG)
                val share = root?.findTagBounds(QUOTE_SHEET_SHARE_TAG)
                if (top > 0 && bottom > 0 && preview != null && share != null) {
                    insets = top to bottom
                    measured = Triple(preview.top, share.bottom, decor.height)
                }
            }
            if (measured == null) Thread.sleep(POLL_DELAY_MS)
        }
        val (previewTop, shareBottom, decorHeight) = measured ?: throw AssertionError("Sheet content or system insets not observed")
        assertTrue("Preview under status bar: top=$previewTop inset=${insets.first}", previewTop >= insets.first)
        assertTrue("Share overlaps nav bar: bottom=$shareBottom limit=${decorHeight - insets.second}", shareBottom <= decorHeight - insets.second)
    }

    @Test
    fun customToolbarKeepsLongPressSelectionAndCopy() {
        val shared = AtomicBoolean(false)
        val copy = AtomicReference<(() -> Unit)?>()
        val fakePlatformToolbar = object : TextToolbar {
            override val status: TextToolbarStatus get() = if (copy.get() == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown
            override fun hide() { copy.set(null) }
            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) { copy.set(onCopyRequested) }
        }
        val quoteToolbar = QuoteTextToolbar(fakePlatformToolbar) { shared.set(true) }
        composeRule.setContent {
            CompositionLocalProvider(LocalTextToolbar provides quoteToolbar) {
                AppTheme(useDarkTheme = false) {
                    SelectionContainer {
                        LazyColumn { Reader(link = LINK, content = "<p>$PROSE</p>", onLinkClick = {}) }
                    }
                }
            }
        }

        composeRule.onNodeWithText(PROSE).performTouchInput { longClick(center) }
        composeRule.waitUntil(TIMEOUT_MS) { quoteToolbar.shareAvailable }
        composeRule.runOnUiThread { quoteToolbar.triggerShare() }

        val clipboard = composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.runOnUiThread {
            val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(composeRule.activity)?.toString().orEmpty()
            assertTrue("Clipboard phải chứa đoạn chọn, nhận: '$copied'", copied.isNotBlank() && PROSE.contains(copied))
            assertTrue("Callback chia sẻ phải chạy", shared.get())
        }
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findComposeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun ViewRootForTest.findTagBounds(tag: String): android.graphics.Rect? {
        val stack = ArrayDeque<SemanticsNode>().apply { add(semanticsOwner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) {
                val b = node.boundsInWindow
                return android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            stack.addAll(node.children)
        }
        return null
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_DELAY_MS = 50L
        const val LINK = "https://example.com/quote-qa"
        const val PROSE = "A quotable sentence that the reader selects with a long press."
    }
}
