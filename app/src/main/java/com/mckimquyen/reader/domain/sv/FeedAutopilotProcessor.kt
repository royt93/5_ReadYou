package com.mckimquyen.reader.domain.sv

import android.content.Context
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodec
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotEvaluation
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotScorer
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.feedAutopilotEnabled
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.ui.ext.put
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs one Feed Autopilot analysis for the current account. It only reads articles and writes two small DataStore
 * values (the pruned decisions map and the number of suggestions shown as text in Settings); it never deletes a feed.
 */
class FeedAutopilotProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun computeSuggestions(nowMillis: Long = System.currentTimeMillis()): FeedAutopilotEvaluation =
        withContext(ioDispatcher) {
            val evaluation = evaluate(nowMillis)
            val pending = (evaluation as? FeedAutopilotEvaluation.Ready)?.suggestions?.size ?: 0
            context.dataStore.put(DataStoreKeys.FeedAutopilotPendingCount, pending)
            evaluation
        }

    private suspend fun evaluate(nowMillis: Long): FeedAutopilotEvaluation {
        if (!context.feedAutopilotEnabled) return FeedAutopilotEvaluation.Disabled
        if (context.currentAccountType != AccountType.Local.id) return FeedAutopilotEvaluation.NotLocalAccount

        val accountId = context.currentAccountId
        val account = accountDao.queryById(accountId) ?: return FeedAutopilotEvaluation.NotLocalAccount
        val feeds = feedDao.queryAll(accountId)

        // Dropping decisions of feeds that no longer exist covers a feed deleted anywhere in the app.
        val stored = context.dataStore.get(DataStoreKeys.FeedAutopilotDecisions)
        val decisions = FeedAutopilotDecisionsCodec.decode(stored, nowMillis, feeds.map { it.id }.toSet())
        val pruned = FeedAutopilotDecisionsCodec.encode(decisions)
        if (pruned != stored) context.dataStore.put(DataStoreKeys.FeedAutopilotDecisions, pruned)

        val sinceMillis = FeedAutopilotScorer.windowStartMillis(account.keepArchived, nowMillis)
        val stats = articleDao.queryFeedEngagementStats(accountId, sinceMillis)
        return FeedAutopilotScorer.evaluate(stats, feeds, decisions, account.keepArchived, nowMillis)
    }
}
