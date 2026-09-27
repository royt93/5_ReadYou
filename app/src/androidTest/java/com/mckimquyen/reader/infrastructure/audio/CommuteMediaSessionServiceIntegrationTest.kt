package com.mckimquyen.reader.infrastructure.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device verification: CommuteMediaSessionService starts, creates lock-screen Media Notification,
 * handles play/pause state transitions, and stops cleanly.
 */
@RunWith(AndroidJUnit4::class)
class CommuteMediaSessionServiceIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            ).close()
        }
    }

    @After
    fun tearDown() {
        CommuteMediaSessionService.stop(context)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun service_startsForegroundWithCommuteMediaNotification() {
        CommuteMediaSessionService.start(
            context = context,
            title = "Morning CommuteCast",
            subtitle = "Alex: Good morning listeners",
        )

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(1_000)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val activeNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == CommuteMediaSessionService.NOTIFICATION_ID }

        assertNotNull("CommuteCast media notification must be active", activeNotification)
        val ongoing = (activeNotification?.notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0
        assertTrue("Notification must be ongoing while playing", ongoing)

        CommuteMediaSessionService.stop(context)
        Thread.sleep(500)
    }

    @Test
    fun service_pauseAndStop_transitionsCleanly() {
        CommuteMediaSessionService.start(
            context = context,
            title = "Morning CommuteCast",
            subtitle = "Alex: Good morning listeners",
        )
        Thread.sleep(800)

        CommuteMediaSessionService.pause(context)
        Thread.sleep(500)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        var activeNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == CommuteMediaSessionService.NOTIFICATION_ID }
        assertNotNull("Notification still present in paused state", activeNotification)

        CommuteMediaSessionService.stop(context)
        Thread.sleep(500)

        activeNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == CommuteMediaSessionService.NOTIFICATION_ID }
        assertNull("Notification must be cleared on stop", activeNotification)
    }
}
