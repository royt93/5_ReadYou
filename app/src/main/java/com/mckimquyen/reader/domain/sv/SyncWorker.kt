package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.infrastructure.android.AppLog
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mckimquyen.reader.infrastructure.ai.OracleClaimExtractor
import com.mckimquyen.reader.infrastructure.ai.OracleDueReminder
import com.mckimquyen.reader.infrastructure.ai.search.SemanticEmbeddingIndexer
import com.mckimquyen.reader.infrastructure.sync.WebDavReadPositionSyncer
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.infrastructure.pref.SyncIntervalPref
import com.mckimquyen.reader.infrastructure.pref.SyncOnlyOnWiFiPref
import com.mckimquyen.reader.infrastructure.pref.SyncOnlyWhenChargingPref
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountService: AccountSv,
    private val rssService: RssSv,
    private val offlinePrecacheService: com.mckimquyen.reader.infrastructure.rss.OfflinePrecacheService,
    private val semanticEmbeddingIndexer: SemanticEmbeddingIndexer,
    private val webDavReadPositionSyncer: WebDavReadPositionSyncer,
    private val oracleClaimExtractor: OracleClaimExtractor,
    private val oracleDueReminder: OracleDueReminder,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.Default) {
            Log.i("RLog", "doWork: ")
            AppLog.event("sync", "start", "attempt" to runAttemptCount)
            val syncResult = rssService.get().sync(this@SyncWorker).also {
                rssService.get().clearKeepArchivedArticles()
            }
            if (syncResult.isSuccessfulSync()) {
                val accountId = context.currentAccountId
                val readPositionSyncEnabled = context.dataStore.get(DataStoreKeys.ReadPositionSync) ?: true
                if (readPositionSyncEnabled) {
                    webDavReadPositionSyncer.syncPositions(accountId)
                        .onFailure { Log.w("RLog", "WebDAV read-position sync failed", it) }
                }
                runCatching {
                    offlinePrecacheService.precacheLatestUnread(accountId)
                }
                // Build the semantic index here so search never pays for embedding on keystroke.
                runCatching {
                    semanticEmbeddingIndexer.indexRecent(accountId)
                }.onFailure { Log.w("RLog", "Semantic embedding indexing failed", it) }
                // Background prediction finding (ORACLE-01); a no-op unless the user agreed to it separately.
                runCatching {
                    oracleClaimExtractor.extractRecent(accountId)
                }.onFailure { Log.w("RLog", "Prediction finding failed", it) }
                // Local only: reminds about bets that reached their date, whether or not the AI finder is on (ORACLE-03).
                runCatching {
                    oracleDueReminder.remindDue()
                }.onFailure { Log.w("RLog", "Due reminder failed", it) }
                runCatching {
                    com.mckimquyen.reader.ui.widget.ArticleWidgetUpdater.notifyDataChanged(context)
                }
            }
            syncResult
        }

    companion object {

        private const val IS_SYNCING = "isSyncing"
        const val WORK_NAME = "ReadYou"
        const val ONE_TIME_WORK_NAME = "ReadYouOneTime"
        // Was lateinit: never assigned anywhere — accessing before init throws UninitializedPropertyAccessException
        var uuid: UUID? = null

        fun enqueueOneTimeWork(
            workManager: WorkManager,
        ) {
            // Unique, so a second pull-to-refresh or the start-up sync cannot run a second SyncWorker next to the first. It has its own name: WORK_NAME belongs to the
            // periodic chain, and two unique works under one name replace each other. The WORK_NAME tag stays so the UI still sees this work as a sync.
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .addTag(WORK_NAME)
                    .build()
            )
        }

        fun enqueuePeriodicWork(
            workManager: WorkManager,
            syncInterval: SyncIntervalPref,
            syncOnlyWhenCharging: SyncOnlyWhenChargingPref,
            syncOnlyOnWiFi: SyncOnlyOnWiFiPref,
        ) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP: don't cancel an ongoing sync if settings change mid-flight.
                // REPLACE would kill the running worker immediately.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(syncInterval.value, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(syncOnlyWhenCharging.value)
                            .setRequiredNetworkType(if (syncOnlyOnWiFi.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
                            .build()
                    )
                    .addTag(WORK_NAME)
                    .setInitialDelay(syncInterval.value, TimeUnit.MINUTES)
                    .build()
            )
        }

        fun setIsSyncing(boolean: Boolean) = workDataOf(IS_SYNCING to boolean)
        fun Data.getIsSyncing(): Boolean = getBoolean(IS_SYNCING, false)
    }
}

/**
 * `Result.Success` is a restricted API (androidx.work library group only), so detect success through the public
 * surface instead: success is distinguishable by [androidx.work.ListenableWorker.Result.toString]:
 * the type name is `Success`/`Failure`/`Retry`, and attached output data does not change it.
 */
internal fun androidx.work.ListenableWorker.Result.isSuccessfulSync(): Boolean = toString().startsWith(SUCCESS_RESULT_PREFIX)

private const val SUCCESS_RESULT_PREFIX = "Success"
