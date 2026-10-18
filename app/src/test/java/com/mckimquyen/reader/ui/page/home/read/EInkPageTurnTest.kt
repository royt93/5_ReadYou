package com.mckimquyen.reader.ui.page.home.read

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EInkPageTurnTest {

    // ── Tap zones ────────────────────────────────────────────────────────────

    @Test
    fun resolveTapZone_splitsWidthIntoThirds() {
        assertEquals(TapZone.PREVIOUS, resolveTapZone(x = 0f, width = 300f))
        assertEquals(TapZone.PREVIOUS, resolveTapZone(x = 99.9f, width = 300f))
        assertEquals(TapZone.CENTER, resolveTapZone(x = 150f, width = 300f))
        assertEquals(TapZone.NEXT, resolveTapZone(x = 200.1f, width = 300f))
        assertEquals(TapZone.NEXT, resolveTapZone(x = 300f, width = 300f))
    }

    @Test
    fun resolveTapZone_exactBoundariesBelongToCenter() {
        assertEquals(TapZone.CENTER, resolveTapZone(x = 100f, width = 300f))
        assertEquals(TapZone.CENTER, resolveTapZone(x = 200f, width = 300f))
    }

    @Test
    fun resolveTapZone_invalidGeometryIsCenter() {
        assertEquals(TapZone.CENTER, resolveTapZone(x = 10f, width = 0f))
        assertEquals(TapZone.CENTER, resolveTapZone(x = 10f, width = -5f))
        assertEquals(TapZone.CENTER, resolveTapZone(x = Float.NaN, width = 300f))
    }

    @Test
    fun tapZone_mapsToDirectionAndCenterIsNoOp() {
        assertEquals(PageDirection.PREVIOUS, TapZone.PREVIOUS.toDirection())
        assertEquals(PageDirection.NEXT, TapZone.NEXT.toDirection())
        assertNull(TapZone.CENTER.toDirection())
    }

    // ── Page target: many short items (viewport 1000px, items 250px) ─────────

    private fun items(firstIndex: Int, firstOffset: Int, count: Int, size: Int = 250) =
        List(count) { i -> PageItem(index = firstIndex + i, offset = firstOffset + i * size, size = size) }

    @Test
    fun next_advancesToLastVisibleItemKeepingItAsOverlap() {
        // Visible: 0..3 (4 items fill the 1000px viewport exactly).
        val target = resolvePageTurn(
            direction = PageDirection.NEXT,
            visible = items(0, 0, 4),
            totalCount = 20,
            viewportHeight = 1000,
        )
        assertEquals(PageTarget(index = 3, offset = 0), target)
    }

    @Test
    fun previous_retreatsByVisibleCountMinusOverlapItem() {
        val target = resolvePageTurn(
            direction = PageDirection.PREVIOUS,
            visible = items(8, 0, 4),
            totalCount = 20,
            viewportHeight = 1000,
        )
        assertEquals(PageTarget(index = 5, offset = 0), target)
    }

    @Test
    fun previous_clampsAtFirstItemAndNoOpsAtTop() {
        assertEquals(
            PageTarget(index = 0, offset = 0),
            resolvePageTurn(PageDirection.PREVIOUS, items(1, 0, 4), 20, 1000),
        )
        assertNull(resolvePageTurn(PageDirection.PREVIOUS, items(0, 0, 4), 20, 1000))
    }

    @Test
    fun next_noOpsWhenLastItemFullyVisibleAtEnd() {
        // Items 16..19 visible, last one ends exactly at viewport bottom.
        assertNull(resolvePageTurn(PageDirection.NEXT, items(16, 0, 4), 20, 1000))
    }

    @Test
    fun next_nearEndNoOpsWhenLastItemAlreadyFullyVisible() {
        // Last item ends before the viewport boundary, so further scrolling would only expose blank space.
        assertNull(resolvePageTurn(PageDirection.NEXT, items(16, -100, 4), 20, 1000))
    }

    // ── Page target: one item taller than the viewport ───────────────────────

    @Test
    fun next_insideTallItemScrollsOneViewportMinusOverlap() {
        val tall = listOf(PageItem(index = 2, offset = 0, size = 5000))
        val target = resolvePageTurn(PageDirection.NEXT, tall, 10, 1000)
        assertEquals(PageTarget(index = 2, offset = 900), target)
    }

    @Test
    fun next_insideTallItemAlreadyScrolledAddsExistingOffset() {
        val tall = listOf(PageItem(index = 2, offset = -900, size = 5000))
        val target = resolvePageTurn(PageDirection.NEXT, tall, 10, 1000)
        assertEquals(PageTarget(index = 2, offset = 1800), target)
    }

    @Test
    fun next_tallItemEndingMovesOntoFollowingItem() {
        val tall = listOf(PageItem(index = 2, offset = -3900, size = 5000))
        val target = resolvePageTurn(PageDirection.NEXT, tall, 10, 1000)
        assertEquals(PageTarget(index = 3, offset = 0), target)
    }

    @Test
    fun previous_insideTallItemRetreatsOneViewportMinusOverlapWithinItem() {
        val tall = listOf(PageItem(index = 2, offset = -1800, size = 5000))
        val target = resolvePageTurn(PageDirection.PREVIOUS, tall, 10, 1000)
        assertEquals(PageTarget(index = 2, offset = 900), target)
    }

    @Test
    fun previous_nearTopOfTallItemClampsToItemStart() {
        val tall = listOf(PageItem(index = 2, offset = -300, size = 5000))
        val target = resolvePageTurn(PageDirection.PREVIOUS, tall, 10, 1000)
        assertEquals(PageTarget(index = 2, offset = 0), target)
    }

    // ── Degenerate input ─────────────────────────────────────────────────────

    @Test
    fun emptyOrUnmeasuredLayoutIsNoOp() {
        assertNull(resolvePageTurn(PageDirection.NEXT, emptyList(), 20, 1000))
        assertNull(resolvePageTurn(PageDirection.PREVIOUS, emptyList(), 20, 1000))
        assertNull(resolvePageTurn(PageDirection.NEXT, items(0, 0, 4), 20, viewportHeight = 0))
        assertNull(resolvePageTurn(PageDirection.NEXT, items(0, 0, 4), totalCount = 0, viewportHeight = 1000))
    }

    // ── Volume keys ──────────────────────────────────────────────────────────

    private fun volume(
        keyCode: Int = KeyEvent.KEYCODE_VOLUME_UP,
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
        eInk: Boolean = true,
        ttsPlaying: Boolean = false,
        readerActive: Boolean = true,
    ) = resolveVolumeKeyTurn(keyCode, action, repeatCount, eInk, ttsPlaying, readerActive)

    @Test
    fun volumeKeys_mapDirectionWhenAllConditionsMet() {
        assertEquals(PageDirection.NEXT, volume(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(PageDirection.PREVIOUS, volume(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun volumeKeys_passThroughWhenAnyConditionFails() {
        assertNull(volume(eInk = false))
        assertNull(volume(ttsPlaying = true))
        assertNull(volume(readerActive = false))
    }

    @Test
    fun volumeKeys_onlyFirstKeyDownTurns() {
        assertNull(volume(action = KeyEvent.ACTION_UP))
        assertNull(volume(repeatCount = 1))
    }

    @Test
    fun otherKeysAreNeverHandled() {
        assertNull(volume(keyCode = KeyEvent.KEYCODE_BACK))
        assertNull(volume(keyCode = KeyEvent.KEYCODE_VOLUME_MUTE))
    }
}
