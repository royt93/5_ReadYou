package com.mckimquyen.reader.ui.page.home.read

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkVolumeKeyHandlerTest {

    @After
    fun tearDown() = ReaderVolumeKeyBridge.clearForTest()

    private fun dispatch(keyCode: Int = KeyEvent.KEYCODE_VOLUME_UP) =
        ReaderVolumeKeyBridge.dispatch(keyCode, KeyEvent.ACTION_DOWN, repeatCount = 0)

    @Test
    fun noHandlerRegistered_keyIsNotConsumed() {
        assertFalse(dispatch())
    }

    @Test
    fun registeredHandler_receivesKeyAndDecidesConsumption() {
        val seen = mutableListOf<Int>()
        val handler: (Int, Int, Int) -> Boolean = { keyCode, _, _ -> seen += keyCode; keyCode == KeyEvent.KEYCODE_VOLUME_UP }
        ReaderVolumeKeyBridge.register(handler)

        assertTrue(dispatch(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(dispatch(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN), seen)
    }

    @Test
    fun unregister_restoresDefaultVolumeBehavior() {
        val handler: (Int, Int, Int) -> Boolean = { _, _, _ -> true }
        ReaderVolumeKeyBridge.register(handler)
        ReaderVolumeKeyBridge.unregister(handler)

        assertFalse(dispatch())
    }

    @Test
    fun staleUnregister_doesNotRemoveNewerHandler() {
        // Activity recreation: the new Reader registers before the old one disposes.
        val old: (Int, Int, Int) -> Boolean = { _, _, _ -> true }
        val new: (Int, Int, Int) -> Boolean = { _, _, _ -> true }
        ReaderVolumeKeyBridge.register(old)
        ReaderVolumeKeyBridge.register(new)
        ReaderVolumeKeyBridge.unregister(old)

        assertTrue(dispatch())
        ReaderVolumeKeyBridge.unregister(new)
        assertFalse(dispatch())
    }

    @Test
    fun ttsPlayingOrModeOff_handlerLeavesKeyToSystem() {
        var ttsPlaying = true
        val handler: (Int, Int, Int) -> Boolean = { keyCode, action, repeat ->
            resolveVolumeKeyTurn(keyCode, action, repeat, eInk = true, ttsPlaying = ttsPlaying, readerActive = true) != null
        }
        ReaderVolumeKeyBridge.register(handler)

        assertFalse(dispatch())
        ttsPlaying = false
        assertTrue(dispatch())
    }
}
