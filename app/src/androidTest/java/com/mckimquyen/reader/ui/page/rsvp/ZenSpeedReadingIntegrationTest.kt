package com.mckimquyen.reader.ui.page.rsvp

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.domain.zen.ZenDailyEditionManager
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenSoundType
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import com.mckimquyen.reader.ui.component.ambient.ZenSoundSheetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZenSpeedReadingIntegrationTest {

    @Test
    fun rsvpTokenizer_executesCorrectlyOnAndroidRuntime() {
        val article = "<h1>Breaking News</h1><p>Android 17 brings revolutionary speed reading to ReadYou.</p>"
        val tokens = RsvpTokenizer.tokenize(article)

        assertTrue(tokens.isNotEmpty())
        assertEquals("Breaking", tokens[0].fullWord)
        assertEquals(2, tokens[0].orpIndex)
        assertEquals('e', tokens[0].orpChar)
    }

    @Test
    fun notificationHelper_hasDailyEditionChannelRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = NotificationHelper(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(NotificationHelper.ZEN_DAILY_EDITION_CHANNEL_ID)

        assertNotNull(channel)
        assertEquals("Daily Focus Edition", channel?.name)
    }

    @Test
    fun zenAudioManager_initializesAndHandlesPresets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ZenAudioManager(context)

        manager.setSoundType(ZenSoundType.GENTLE_RAIN)
        assertEquals(ZenSoundType.GENTLE_RAIN, manager.currentType.value)

        manager.setVolume(0.7f)
        assertEquals(0.7f, manager.volume.value, 0.01f)

        manager.setSleepTimer(15)
        assertEquals(15, manager.sleepTimerMinutes.value)
    }

    @Test
    fun zenDailyEditionManager_persistsStateOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ZenDailyEditionManager(context)

        manager.setEnabled(true)
        assertTrue(manager.isEnabled.value)

        manager.setBatchSilence(true)
        assertTrue(manager.isBatchSilence.value)
        assertTrue(manager.shouldSilenceImmediateNotification())

        manager.setEnabled(false)
    }

    @Test
    fun rsvpReaderContent_attachesToActivityWithoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val viewModel = RsvpViewModel()
            viewModel.loadContent("Fast reading in ReadYou on Android 17")
            val composeView = ComposeView(activity).apply {
                setContent {
                    RsvpReaderContent(
                        uiState = viewModel.uiState.value,
                        viewModel = viewModel,
                        onDismiss = {}
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
            assertEquals(7, viewModel.uiState.value.tokens.size)
        }
        scenario.close()
    }

    @Test
    fun zenSoundSheetContent_attachesToActivityWithoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val manager = ZenAudioManager(activity)
            val composeView = ComposeView(activity).apply {
                setContent {
                    ZenSoundSheetContent(
                        zenAudioManager = manager,
                        onDismiss = {}
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
            assertEquals(ZenSoundType.GENTLE_RAIN, manager.currentType.value)
        }
        scenario.close()
    }

    @Test
    fun topBar_attachesToActivityWithoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    com.mckimquyen.reader.ui.page.home.read.TopBar(
                        navController = androidx.navigation.compose.rememberNavController(),
                        isShow = true,
                        title = "Test Article",
                        link = "https://example.com",
                        isPlayingAudio = false,
                        isZenAudioPlaying = false,
                        showSummary = false
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
