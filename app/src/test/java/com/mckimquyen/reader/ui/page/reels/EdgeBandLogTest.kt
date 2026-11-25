package com.mckimquyen.reader.ui.page.reels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeBandLogTest {
    @Test fun theFirstPositionIsLogged() = assertTrue(edgeBandMovedEnoughToLog(Int.MIN_VALUE, 1210))

    @Test fun aOnePixelRoundingFlipIsNotLogged() {
        assertFalse(edgeBandMovedEnoughToLog(1209, 1210))
        assertFalse(edgeBandMovedEnoughToLog(1210, 1209))
    }

    @Test fun aRealMoveIsLogged() = assertTrue(edgeBandMovedEnoughToLog(1210, 1210 + EDGE_LOG_MIN_MOVE_PX))
}
