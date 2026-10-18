package com.mckimquyen.reader.ui.theme

import androidx.compose.ui.graphics.Color
import com.mckimquyen.reader.ui.theme.palette.eInkTonalColor
import org.junit.Assert.assertEquals
import org.junit.Test

class EInkTonalPaletteTest {
    @Test
    fun everyToneIsNeutralAndEndpointsStayBlackAndWhite() {
        for (tone in 0..100) {
            val color = eInkTonalColor(tone)
            assertEquals(color.red, color.green, 0f)
            assertEquals(color.red, color.blue, 0f)
        }
        assertEquals(Color.Black, eInkTonalColor(0))
        assertEquals(Color.White, eInkTonalColor(100))
    }
}
