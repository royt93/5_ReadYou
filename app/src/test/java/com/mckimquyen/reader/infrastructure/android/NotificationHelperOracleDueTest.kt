package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperOracleDueTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    @Test fun constants_areHardCoded() {
        assertEquals("oracle_due_channel", NotificationHelper.ORACLE_DUE_CHANNEL_ID)
        assertEquals(9955, NotificationHelper.ORACLE_DUE_NOTIFICATION_ID)
    }

    @Test fun theChannelExists_withTheLocalisedNameAndDescription() {
        val channel = manager.getNotificationChannel(NotificationHelper.ORACLE_DUE_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(context.getString(com.mckimquyen.reader.R.string.oracle_due_channel_name), channel.name.toString())
        assertEquals(context.getString(com.mckimquyen.reader.R.string.oracle_due_channel_desc), channel.description)
    }

    @Test fun notifyOracleDue_postsTitleAndBody_onItsOwnChannel() {
        helper.notifyOracleDue("Title", "Will it ship?")
        val n = shadowOf(manager).getNotification(NotificationHelper.ORACLE_DUE_NOTIFICATION_ID)
        assertEquals("Title", n.extras.getCharSequence("android.title").toString())
        assertEquals("Will it ship?", n.extras.getCharSequence("android.text").toString())
        assertEquals(NotificationHelper.ORACLE_DUE_CHANNEL_ID, n.channelId)
    }

    @Test fun aSecondPost_replacesTheFirst_insteadOfStacking() {
        helper.notifyOracleDue("T1", "B1")
        helper.notifyOracleDue("T2", "B2")
        val active = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.ORACLE_DUE_CHANNEL_ID }
        assertEquals(1, active.size)
        assertEquals("B2", active.single().extras.getCharSequence("android.text").toString())
    }

    @Test fun tappingIt_opensTheApp_withAnImmutableIntent() {
        helper.notifyOracleDue("T", "B")
        val pending = shadowOf(manager).getNotification(NotificationHelper.ORACLE_DUE_NOTIFICATION_ID).contentIntent
        assertTrue(shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals("com.mckimquyen.reader.infrastructure.android.MainActivity", shadowOf(pending).savedIntent.component?.className)
    }

    @Test fun notificationsDisabled_doesNotPostOrCrash() {
        shadowOf(manager).setNotificationsEnabled(false)
        helper.notifyOracleDue("T", "B")
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test fun postingDoesNotTouchTheOtherChannelsNotifications() {
        helper.notifyRpgDecay("RPG", "decay")
        helper.notifyOracleDue("T", "B")
        assertNotNull(shadowOf(manager).getNotification(NotificationHelper.RPG_DECAY_NOTIFICATION_ID))
        assertNull(shadowOf(manager).getNotification(NotificationHelper.ZEN_DAILY_EDITION_NOTIFICATION_ID))
    }
}
