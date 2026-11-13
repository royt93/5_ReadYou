package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleDueReminderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao
    private lateinit var helper: NotificationHelper
    private val day = 86_400_000L
    private val placedAt = 100 * day
    private var now = placedAt + 10 * day
    private var stored: List<String> = emptyList()
    private var writes = 0
    private var postedWhenWritten = -1

    private fun reminder(walletDao: OracleWalletDao = dao) = object : OracleDueReminder(context, walletDao, helper) {
        override fun nowMillis() = now
        override suspend fun readReminded() = stored
        override suspend fun writeReminded(ids: List<String>) { if (postedWhenWritten < 0) postedWhenWritten = posted().size; stored = ids; writes++ }
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
        helper = NotificationHelper(context)
    }

    @After
    fun tearDown() = db.close()

    private fun bet(article: String, expiresAt: Long, question: String = "Q-$article?", stake: Int = 50) = runBlocking {
        dao.placeBet(article, question, expiresAt, OracleBetSide.YES, stake, placedAt)
    }

    private fun run(r: OracleDueReminder = reminder()) = runBlocking { r.remindDue() }

    private fun posted() = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.ORACLE_DUE_CHANNEL_ID }

    private fun text(n: android.app.Notification, key: String) = n.extras.getCharSequence(key).toString()

    // --- what is reminded

    @Test fun nothingDue_postsNothing_andWritesNothing() {
        bet("later", now + 5 * day)
        assertEquals(0, run())
        assertTrue(posted().isEmpty())
        assertEquals(0, writes)
    }

    @Test fun aDueBet_postsOneNotification_withItsQuestion() {
        bet("a", now - day, question = "Will the ceasefire hold?")
        assertEquals(1, run())
        val n = posted().single()
        assertEquals(context.getString(R.string.oracle_due_notification_title), text(n, "android.title"))
        assertEquals("Will the ceasefire hold?", text(n, "android.text"))
    }

    @Test fun severalDueBets_postExactlyOneGroupedNotification() {
        bet("a", now - day); bet("b", now - 2 * day); bet("c", now - 3 * day)
        assertEquals(1, run())
        val n = posted().single()
        assertEquals(context.getString(R.string.oracle_due_notification_title_many), text(n, "android.title"))
        assertEquals(context.getString(R.string.oracle_due_notification_body_many, "3"), text(n, "android.text"))
    }

    @Test fun theNotificationNeverCarriesTheStake() {
        bet("a", now - day, stake = 500); bet("b", now - day, stake = 50)
        run()
        val n = posted().single()
        val all = text(n, "android.title") + text(n, "android.text")
        assertFalse("the points staked must not show on a lock screen", all.contains("500") || all.contains("50 "))
    }

    @Test fun settledAndRefundedBets_areNotReminded() {
        bet("won", now - day); bet("refunded", now - day)
        runBlocking {
            dao.settleBet("won", OracleOutcome.YES, now)
            dao.settleBet("refunded", OracleOutcome.UNCLEAR, now)
        }
        assertEquals(0, run())
    }

    // --- remembering what was already announced

    @Test fun afterPosting_theBetsAreMarkedReminded() {
        bet("a", now - day); bet("b", now - day)
        run()
        assertEquals(setOf("a", "b"), stored.toSet())
    }

    @Test fun theMarkersAreWritten_onlyAfterTheNotificationIsPosted() {
        bet("a", now - day)
        run()
        assertEquals("the marker must not be written before the notification is out", 1, postedWhenWritten)
    }

    @Test fun theSecondRun_doesNotRemindAgain() {
        bet("a", now - day)
        assertEquals(1, run())
        assertEquals(0, run())
        assertEquals(1, posted().size)
    }

    @Test fun aNewlyDueBet_isRemindedAlone_whileTheOldOneIsNotRepeated() {
        bet("a", now - 2 * day)
        run()
        bet("b", now - day, question = "Second question?")
        assertEquals(1, run())
        assertEquals("Second question?", text(posted().single(), "android.text"))
        assertEquals(setOf("a", "b"), stored.toSet())
    }

    @Test fun markersOfBetsThatAreSettled_areDropped() {
        bet("a", now - day); bet("b", now - day)
        run()
        runBlocking { dao.settleBet("a", OracleOutcome.NO, now) }
        run()
        assertEquals(listOf("b"), stored)
    }

    @Test fun theMarkerSetIsCappedAtTwoHundred() {
        // The wallet only affords a few real bets, so the rows go in directly.
        val sql = db.openHelper.writableDatabase
        (1..205).forEach {
            sql.execSQL(
                "INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) " +
                    "VALUES ('a$it', 'Q$it?', ${now - day}, 'YES', 50, 'OPEN', $placedAt)",
            )
        }
        run()
        assertEquals(200, stored.size)
        assertTrue("the oldest markers are the ones cut", "a205" in stored)
    }

    // --- permission

    @Test fun withoutPermission_nothingIsPosted_andNothingIsMarked() {
        shadowOf(manager).setNotificationsEnabled(false)
        bet("a", now - day)
        assertEquals(0, run())
        assertTrue(stored.isEmpty())
        assertEquals(0, writes)
    }

    @Test fun onceThePermissionIsBack_theSameBetIsRemindedThen() {
        shadowOf(manager).setNotificationsEnabled(false)
        bet("a", now - day)
        run()
        shadowOf(manager).setNotificationsEnabled(true)
        assertEquals(1, run())
        assertEquals(listOf("a"), stored)
    }

    // --- failures

    @Test fun aFailingQuery_postsNothing_marksNothing_andLogsOnlyTheType() {
        val failing = mockk<OracleWalletDao>()
        coEvery { failing.dueOpenBets(any()) } throws IllegalStateException("SecretQuestionText")
        assertEquals(0, run(reminder(failing)))
        assertEquals(0, writes)
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertTrue(log.contains("IllegalStateException"))
        assertFalse(log.contains("SecretQuestionText"))
    }

    @Test fun cancellation_isRethrown_andMarksNothing() {
        val cancelling = mockk<OracleWalletDao>()
        coEvery { cancelling.dueOpenBets(any()) } throws CancellationException("cancelled")
        val thrown = runCatching { run(reminder(cancelling)) }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(0, writes)
    }

    @Test fun aFailureWhileWritingTheMarkers_isAbsorbed() {
        bet("a", now - day)
        val r = object : OracleDueReminder(context, dao, helper) {
            override fun nowMillis() = now
            override suspend fun readReminded() = emptyList<String>()
            override suspend fun writeReminded(ids: List<String>) { error("disk full") }
        }
        assertEquals(0, run(r))
    }
}
