package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodec
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotEvaluation
import com.mckimquyen.reader.domain.autopilot.FeedDecisionRecord
import com.mckimquyen.reader.domain.autopilot.FeedDecisionType
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.feedAutopilotPendingCount
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.ui.ext.putBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FeedAutopilotProcessorTest {
    // Room được tạo trong @Before, chỉ dùng sau khi setUp hoàn tất.
    private lateinit var db: AndroidDatabase
    private lateinit var context: Context
    private lateinit var processor: FeedAutopilotProcessor

    private val now = READ_AT_INTRODUCED + 60L * DAY_MILLIS

    @Before fun setUp() = runBlocking {
        // KeepArchivedPreference has a class-initialization cycle: touching a subclass object first (For1Month)
        // builds the companion's `values` while the last object is still null, and Room's converter then throws.
        // The app always touches the companion first (Account.keepArchived defaults to `.default`), so tests do too.
        KeepArchivedPreference.values
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, ACCOUNT)
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotEnabled, true)
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotDecisions, "")
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotPendingCount, 0)

        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = ACCOUNT, name = "Local", type = AccountType.Local, keepArchived = KeepArchivedPreference.For1Month))
        db.groupDao().insert(Group(id = "g1", name = "G", accountId = ACCOUNT))
        db.feedDao().insert(Feed(id = "f_noisy", name = "Noisy Feed", url = "https://e.com/1", groupId = "g1", accountId = ACCOUNT))

        processor = FeedAutopilotProcessor(context, db.accountDao(), db.feedDao(), db.articleDao(), Dispatchers.Unconfined)
    }

    @After fun tearDown() { db.close() }

    private suspend fun seedArticles(feedId: String, total: Int, engaged: Int) {
        val oldest = now - 20L * DAY_MILLIS
        repeat(total) { i ->
            val at = oldest + i * 1_000L
            db.articleDao().insert(
                Article(
                    id = "art_$feedId$i", title = "T", date = Date(at), rawDescription = "", shortDescription = "S",
                    link = "https://e.com/$feedId$i", feedId = feedId, accountId = ACCOUNT,
                    updateAt = Date(at),
                    readAt = if (i < engaged) at else null,
                )
            )
        }
    }

    @Test fun disabledToggleReturnsDisabledOutcomeAndClearsPendingCount() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotEnabled, false)
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotPendingCount, 5)
        seedArticles("f_noisy", 25, 0)

        val result = processor.computeSuggestions(now)
        assertEquals(FeedAutopilotEvaluation.Disabled, result)
        assertEquals(0, context.feedAutopilotPendingCount)
    }

    @Test fun nonLocalAccountReturnsNotLocalAccountAndLeavesPendingZero() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        val result = processor.computeSuggestions(now)
        assertEquals(FeedAutopilotEvaluation.NotLocalAccount, result)
        assertEquals(0, context.feedAutopilotPendingCount)
    }

    @Test fun shortRetentionAccountReturnsRetentionTooShort() = runBlocking {
        db.accountDao().update(Account(id = ACCOUNT, name = "Local", type = AccountType.Local, keepArchived = KeepArchivedPreference.For1Week))
        val result = processor.computeSuggestions(now)
        assertEquals(FeedAutopilotEvaluation.RetentionTooShort, result)
    }

    @Test fun noisyFeedIsSurfacedAndUpdatesPendingCount() = runBlocking {
        seedArticles("f_noisy", 25, 0)
        val result = processor.computeSuggestions(now)
        val ready = result as FeedAutopilotEvaluation.Ready
        assertEquals(1, ready.suggestions.size)
        assertEquals("f_noisy", ready.suggestions.single().feedId)
        assertEquals(1, context.feedAutopilotPendingCount)
    }

    @Test fun cleanFeedsGiveZeroSuggestionsAndZeroPendingCount() = runBlocking {
        seedArticles("f_noisy", 25, 10) // 10/25 = 40% engaged
        val result = processor.computeSuggestions(now)
        val ready = result as FeedAutopilotEvaluation.Ready
        assertTrue(ready.suggestions.isEmpty())
        assertEquals(0, context.feedAutopilotPendingCount)
    }

    @Test fun orphanDecisionsArePrunedFromDataStoreWhenFeedIsDeleted() = runBlocking {
        val decisions = mapOf(
            "f_noisy" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now),
            "f_deleted" to FeedDecisionRecord(FeedDecisionType.KEEP_FOREVER, now),
        )
        context.dataStore.putBlocking(DataStoreKeys.FeedAutopilotDecisions, FeedAutopilotDecisionsCodec.encode(decisions))
        seedArticles("f_noisy", 25, 0)

        processor.computeSuggestions(now)
        val storedJson = context.dataStore.get(DataStoreKeys.FeedAutopilotDecisions)
        val decoded = FeedAutopilotDecisionsCodec.decode(storedJson, now, null)
        assertEquals(setOf("f_noisy"), decoded.keys)
    }

    private companion object {
        const val ACCOUNT = 1
        const val DAY_MILLIS = 86_400_000L
        const val READ_AT_INTRODUCED = 1_792_281_600_000L
    }
}
