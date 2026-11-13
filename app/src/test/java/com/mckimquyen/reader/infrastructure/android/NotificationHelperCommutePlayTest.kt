package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.audio.CommuteMediaSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperCommutePlayTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    private fun posted() = shadowOf(manager).getNotification(NotificationHelper.COMMUTE_READY_NOTIFICATION_ID)

    @Test fun theIdIsHardCoded_andDistinctFromEveryOtherNotification() {
        assertEquals(9944, NotificationHelper.COMMUTE_READY_NOTIFICATION_ID)
        val others = listOf(
            NotificationHelper.COMMUTE_NOTIFICATION_ID, NotificationHelper.ZEN_DAILY_EDITION_NOTIFICATION_ID,
            NotificationHelper.RPG_DECAY_NOTIFICATION_ID, NotificationHelper.ORACLE_DUE_NOTIFICATION_ID,
        )
        others.forEach { assertNotEquals(NotificationHelper.COMMUTE_READY_NOTIFICATION_ID, it) }
    }

    @Test fun itPostsTheLocalisedTitleAndBody_onTheCommuteChannel() {
        helper.notifyCommuteReadyToPlay()
        val n = posted()
        assertEquals(context.getString(R.string.commute_ready_to_play_title), n.extras.getCharSequence("android.title").toString())
        assertEquals(context.getString(R.string.commute_ready_to_play_body), n.extras.getCharSequence("android.text").toString())
        assertEquals(NotificationHelper.COMMUTE_CHANNEL_ID, n.channelId)
    }

    @Test fun itCarriesExactlyOneAction_namedPlay() {
        helper.notifyCommuteReadyToPlay()
        val actions = posted().actions
        assertEquals(1, actions.size)
        assertEquals(context.getString(R.string.commute_play_action), actions[0].title.toString())
    }

    @Test fun thePlayAction_startsTheMediaServiceWithPlayLatest_notThePlainPlayThatOnlyShowsANotification() {
        helper.notifyCommuteReadyToPlay()
        val pending = posted().actions[0].actionIntent
        val shadow = shadowOf(pending)
        assertTrue(shadow.isService || shadow.isForegroundService)
        assertNotEquals(CommuteMediaSessionService.ACTION_PLAY, shadow.savedIntent.action)
        assertEquals(CommuteMediaSessionService::class.java.name, shadow.savedIntent.component?.className)
        assertEquals(CommuteMediaSessionService.ACTION_PLAY_LATEST, shadow.savedIntent.action)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun tappingTheBody_opensTheAppAndStartsTheCommuteSheet() {
        helper.notifyCommuteReadyToPlay()
        val intent = shadowOf(posted().contentIntent).savedIntent
        assertEquals(true, intent.getBooleanExtra(NotificationHelper.EXTRA_START_COMMUTE, false))
    }

    @Test fun aSecondPost_replacesTheFirst() {
        helper.notifyCommuteReadyToPlay()
        helper.notifyCommuteReadyToPlay()
        val n = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.COMMUTE_CHANNEL_ID }
        assertEquals(1, n.size)
    }

    @Test fun withoutPermission_nothingIsPosted_andNothingCrashes() {
        shadowOf(manager).setNotificationsEnabled(false)
        helper.notifyCommuteReadyToPlay()
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
