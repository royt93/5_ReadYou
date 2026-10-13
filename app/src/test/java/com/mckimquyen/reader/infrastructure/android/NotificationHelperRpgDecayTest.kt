package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.ui.page.common.RouteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperRpgDecayTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    @Test fun notifyRpgDecay_usesOneFixedId_soADailyRepostReplacesTheOldSummary() {
        helper.notifyRpgDecay("Title", "Body one")
        helper.notifyRpgDecay("Title", "Body two")
        val active = shadowOf(manager).allNotifications
        assertEquals(1, active.size)
        assertEquals("Body two", active.single().extras.getCharSequence("android.text").toString())
        assertNotNull(shadowOf(manager).getNotification(NotificationHelper.RPG_DECAY_NOTIFICATION_ID))
    }

    @Test fun tappingTheSummary_opensTheBrainRpgRoute() {
        helper.notifyRpgDecay("Title", "Body")
        val pending = shadowOf(manager).getNotification(NotificationHelper.RPG_DECAY_NOTIFICATION_ID).contentIntent
        val intent = shadowOf(pending).savedIntent
        assertEquals(RouteName.BRAIN_RPG, intent.getStringExtra(NotificationHelper.EXTRA_INITIAL_ROUTE))
        assertEquals(true, shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun cancelRpgDecay_removesOnlyTheSummary() {
        helper.notifyRpgDecay("Title", "Body")
        helper.cancelRpgDecay()
        assertNull(shadowOf(manager).getNotification(NotificationHelper.RPG_DECAY_NOTIFICATION_ID))
    }

    @Test fun notificationsDisabled_doesNotPostOrCrash() {
        shadowOf(manager).setNotificationsEnabled(false)
        helper.notifyRpgDecay("Title", "Body")
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
