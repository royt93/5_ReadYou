package com.mckimquyen.reader.ui.component.quote

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteTextToolbarTest {

    private class FakeToolbar : TextToolbar {
        var rect: Rect? = null
        var copy: (() -> Unit)? = null
        var paste: (() -> Unit)? = null
        var cut: (() -> Unit)? = null
        var selectAll: (() -> Unit)? = null
        var hideCalls = 0
        override var status: TextToolbarStatus = TextToolbarStatus.Hidden

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {
            this.rect = rect
            copy = onCopyRequested
            paste = onPasteRequested
            cut = onCutRequested
            selectAll = onSelectAllRequested
            status = TextToolbarStatus.Shown
        }

        override fun hide() {
            hideCalls++
            status = TextToolbarStatus.Hidden
        }
    }

    private val area = Rect(1f, 2f, 3f, 4f)

    @Test
    fun showMenu_forwardsStandardCallbacksToDelegate() {
        val delegate = FakeToolbar()
        val toolbar = QuoteTextToolbar(delegate) {}
        val copy = {}
        val paste = {}
        val cut = {}
        val selectAll = {}

        toolbar.showMenu(area, copy, paste, cut, selectAll)

        assertEquals(area, delegate.rect)
        assertSame(copy, delegate.copy)
        assertSame(paste, delegate.paste)
        assertSame(cut, delegate.cut)
        assertSame(selectAll, delegate.selectAll)
    }

    @Test
    fun showMenu_setsShareAvailableOnlyWhenCopyExists() {
        val toolbar = QuoteTextToolbar(FakeToolbar()) {}

        toolbar.showMenu(area, null, {}, null, {})
        assertFalse(toolbar.shareAvailable)

        toolbar.showMenu(area, {}, {}, null, {})
        assertTrue(toolbar.shareAvailable)
        assertEquals(area, toolbar.anchorRect)
    }

    @Test
    fun triggerShare_invokesCopyThenShare_inOrder() {
        val calls = mutableListOf<String>()
        val toolbar = QuoteTextToolbar(FakeToolbar()) { calls += "share" }
        toolbar.showMenu(area, { calls += "copy" }, null, null, null)

        toolbar.triggerShare()

        assertEquals(listOf("copy", "share"), calls)
    }

    @Test
    fun triggerShare_isNoOpWithoutCopy() {
        val calls = mutableListOf<String>()
        val toolbar = QuoteTextToolbar(FakeToolbar()) { calls += "share" }
        toolbar.showMenu(area, null, {}, null, {})

        toolbar.triggerShare()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun hide_forwardsToDelegateAndClearsState() {
        val delegate = FakeToolbar()
        val calls = mutableListOf<String>()
        val toolbar = QuoteTextToolbar(delegate) { calls += "share" }
        toolbar.showMenu(area, { calls += "copy" }, null, null, null)

        toolbar.hide()
        toolbar.triggerShare()

        assertEquals(1, delegate.hideCalls)
        assertNull(toolbar.anchorRect)
        assertFalse(toolbar.shareAvailable)
        assertTrue("sau hide không được chia sẻ nữa", calls.isEmpty())
    }

    @Test
    fun status_reflectsDelegate() {
        val delegate = FakeToolbar()
        val toolbar = QuoteTextToolbar(delegate) {}
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)

        toolbar.showMenu(area, {}, null, null, null)

        assertEquals(TextToolbarStatus.Shown, toolbar.status)
    }
}
