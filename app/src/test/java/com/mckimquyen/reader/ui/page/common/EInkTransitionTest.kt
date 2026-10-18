package com.mckimquyen.reader.ui.page.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.mckimquyen.reader.ui.ext.navEnterTransition
import com.mckimquyen.reader.ui.ext.navExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EInkTransitionTest {

    @Test
    fun eInkOn_readerToolbarVisibilityTransitionsAreInstant() {
        assertEquals(EnterTransition.None, com.mckimquyen.reader.ui.component.base.extensibleEnterTransition(true))
        assertEquals(ExitTransition.None, com.mckimquyen.reader.ui.component.base.extensibleExitTransition(true))
        assertNotEquals(EnterTransition.None, com.mckimquyen.reader.ui.component.base.extensibleEnterTransition(false))
        assertNotEquals(ExitTransition.None, com.mckimquyen.reader.ui.component.base.extensibleExitTransition(false))
    }

    @Test
    fun eInkOn_navigationTransitionsAreInstant() {
        assertEquals(EnterTransition.None, navEnterTransition(eInk = true))
        assertEquals(ExitTransition.None, navExitTransition(eInk = true))
    }

    @Test
    fun eInkOff_navigationKeepsDefaultAnimations() {
        assertNotEquals(EnterTransition.None, navEnterTransition(eInk = false))
        assertNotEquals(ExitTransition.None, navExitTransition(eInk = false))
    }
}
