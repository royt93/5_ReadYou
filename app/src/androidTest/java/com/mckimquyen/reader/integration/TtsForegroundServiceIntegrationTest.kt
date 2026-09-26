package com.mckimquyen.reader.integration

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.infrastructure.audio.TtsForegroundService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real-device verification: TTS Foreground Service starts, posts Media Notification, then stops cleanly. */
@RunWith(AndroidJUnit4::class)
class TtsForegroundServiceIntegrationTest {

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
        context.stopService(Intent(context, TtsForegroundService::class.java))
    }

    @Test
    fun service_startsForegroundWithMediaNotification() {
        TtsForegroundService.start(
            context = context,
            title = "ENH-04 Pixel Integration",
            subtitle = "RSS Cat Hub",
        )

        // Give Android a short main-loop turn to create the service & notification.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(1_000)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val activeNotification = notificationManager.activeNotifications
            .firstOrNull { it.id == TtsForegroundService.NOTIFICATION_ID }

        assertNotNull("TTS media notification must be active", activeNotification)
        val ongoing = (activeNotification?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
        assertTrue("Service notification must be ongoing while playing", ongoing)

        TtsForegroundService.stop(context)
    }
}
