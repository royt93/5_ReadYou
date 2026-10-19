package com.mckimquyen.reader.ui.component.quote

import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteChipPlacementTest {

    private fun place(left: Int = 100, top: Int = 400, bottom: Int = 460, w: Int = 1000, h: Int = 2000, cw: Int = 300, ch: Int = 100) =
        QuoteChipPlacement.place(left, top, bottom, w, h, cw, ch, gap = 8)

    @Test
    fun placesBelowSelectionWhenThereIsRoom() {
        assertEquals(IntOffset(100, 468), place())
    }

    @Test
    fun placesAboveWhenNotEnoughRoomBelow() {
        // dưới: 1950+8+100 > 2000 nên đặt phía trên: 1900 - 8 - 100 = 1792
        assertEquals(IntOffset(100, 1792), place(top = 1900, bottom = 1950))
    }

    @Test
    fun clampsInsideWindowWhenNeitherSideFits() {
        // cửa sổ thấp 150: không đủ cả hai phía -> kẹp sát đáy
        assertEquals(IntOffset(100, 50), place(top = 60, bottom = 120, h = 150))
    }

    @Test
    fun clampsHorizontallyToWindow() {
        assertEquals(IntOffset(700, 468), place(left = 950))
        assertEquals(IntOffset(0, 468), place(left = -40))
    }
}
