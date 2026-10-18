package com.mckimquyen.reader.ui.page.home.read

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.infrastructure.android.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Volume keys must reach the Reader through the real Activity dispatch path (not just Compose focus)
 * and must be handed back to the system whenever the Reader stops claiming them.
 */
@RunWith(AndroidJUnit4::class)
class EInkVolumeDispatchInstrumentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() = ReaderVolumeKeyBridge.clearForTest()

    private fun volumeUp(action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0) =
        KeyEvent(0L, 0L, action, KeyEvent.KEYCODE_VOLUME_UP, repeat)

    // ── Compose lifecycle: registration follows composition ─────────────────

    @Test
    fun composable_claimsKeysOnlyWhileEnabledAndTtsStopped() {
        var enabled by mutableStateOf(true)
        var ttsPlaying by mutableStateOf(false)
        val turns = mutableListOf<PageDirection>()
        composeRule.setContent {
            ReaderVolumeKeys(enabled = enabled, ttsPlaying = ttsPlaying) { turns += it }
            Box {}
        }
        composeRule.waitForIdle()

        assertTrue(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))
        assertTrue(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0))
        assertEquals(listOf(PageDirection.NEXT, PageDirection.PREVIOUS), turns)

        ttsPlaying = true
        composeRule.waitForIdle()
        assertFalse(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))
        assertEquals(2, turns.size)

        ttsPlaying = false
        enabled = false
        composeRule.waitForIdle()
        assertFalse(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))
        assertEquals(2, turns.size)
    }

    @Test
    fun composable_leavingCompositionUnregistersHandler() {
        var reader by mutableStateOf(true)
        composeRule.setContent {
            if (reader) ReaderVolumeKeys(enabled = true, ttsPlaying = false) {}
            Box {}
        }
        composeRule.waitForIdle()
        assertTrue(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))

        reader = false
        composeRule.waitForIdle()
        assertFalse(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))
    }

    @Test
    fun composable_activityRecreationDoesNotLeaveStaleHandler() {
        composeRule.setContent { ReaderVolumeKeys(enabled = true, ttsPlaying = false) {}; Box {} }
        composeRule.waitForIdle()
        assertTrue(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))

        composeRule.activityRule.scenario.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse(ReaderVolumeKeyBridge.dispatch(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0))
    }

    // ── Real window dispatch through MainActivity ────────────────────────────

    @Test
    fun mainActivity_routesHardwareVolumeKeyDownOnceThroughBridgeAndPassesOthers() {
        val seen = mutableListOf<Triple<Int, Int, Int>>()
        ReaderVolumeKeyBridge.register { keyCode, action, repeat ->
            seen += Triple(keyCode, action, repeat)
            keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_DOWN && repeat == 0
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            instrumentation.sendKeySync(volumeUp(KeyEvent.ACTION_DOWN))
            instrumentation.sendKeySync(volumeUp(KeyEvent.ACTION_UP))
            instrumentation.waitForIdleSync()
        }

        assertTrue(
            "MainActivity did not forward volume key-down to the bridge: $seen",
            seen.contains(Triple(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0)),
        )
        assertTrue(
            "key-up must also be seen so the bridge can decide to pass it through: $seen",
            seen.contains(Triple(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0)),
        )
    }

    @Test
    fun mainActivity_afterUnregisterNoKeyReachesBridge() {
        val seen = mutableListOf<Int>()
        val handler: (Int, Int, Int) -> Boolean = { keyCode, _, _ -> seen += keyCode; true }
        ReaderVolumeKeyBridge.register(handler)
        ReaderVolumeKeyBridge.unregister(handler)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            instrumentation.sendKeySync(volumeUp(KeyEvent.ACTION_DOWN))
            instrumentation.sendKeySync(volumeUp(KeyEvent.ACTION_UP))
            instrumentation.waitForIdleSync()
        }

        assertTrue("Stale handler consumed keys after unregister: $seen", seen.isEmpty())
    }
}
