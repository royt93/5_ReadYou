package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mckimquyen.reader.domain.commute.CommuteSchedulePolicy
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.ui.ext.commuteTimeBudgetMinutes
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Worker chạy ngầm lúc 6:00 sáng hàng ngày để tạo bản tin phát thanh CommuteCast Radio.
 */
@HiltWorker
class CommuteWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val episodePreparer: CommuteEpisodePreparer,
    private val contentSelector: CommuteContentSelector,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CommuteWorker"
        const val WORK_NAME = "CommuteCastDailyWork"

        private const val REPEAT_INTERVAL_HOURS = 24L

        /**
         * The user picks the time they want to LISTEN; the episode is prepared a fixed lead earlier (STRAT-01).
         * Startup uses KEEP so launching the app never resets the schedule; changing the time passes REPLACE.
         */
        fun enqueueDailyWork(
            workManager: WorkManager,
            listenMinuteOfDay: Int = CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            nowMillis: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val initialDelay = CommuteSchedulePolicy.initialDelayMillis(nowMillis, listenMinuteOfDay, zone)

            val periodicRequest = PeriodicWorkRequestBuilder<CommuteWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, periodicRequest)
            Log.d(TAG, "CommuteCast daily work scheduled, listen minute $listenMinuteOfDay, initial delay: ${initialDelay / 1000}s")
        }

        fun enqueueOneTimeWork(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<CommuteWorker>()
                .addTag(WORK_NAME)
                .build()
            workManager.enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val accountId = context.currentAccountId
            // Honors whatever budget the user last picked in the sheet (DJ-08); falls back to the
            // 4-minute default if they never touched it.
            val budgetMinutes = context.commuteTimeBudgetMinutes
            val candidatePool = articleDao.queryUnreadArticleList(
                accountId,
                limit = CommuteContentSelector.MAX_CANDIDATES_QUERY_LIMIT
            )

            if (candidatePool.isEmpty()) {
                Log.d(TAG, "No unread articles found for CommuteCast.")
                return@withContext Result.success()
            }

            val selectedArticles = contentSelector.selectArticles(
                candidates = candidatePool.map { it.toArticle() },
                targetMinutes = budgetMinutes
            )

            Log.d(TAG, "Synthesizing CommuteCast episode for ${selectedArticles.size} selected articles from pool of ${candidatePool.size}...")

            // Generating, persisting and announcing all live in the preparer so they can be tested;
            // WorkerParameters cannot be built in a JVM test, so nothing testable belongs here.
            when (episodePreparer.prepareAndNotify(
                selectedArticles,
                isDeepDive = budgetMinutes >= CommuteContentSelector.DEFAULT_DEEP_DIVE_BUDGET_MINUTES,
                durationMinutes = budgetMinutes
            )) {
                CommuteEpisodePreparer.Outcome.SUCCESS -> Result.success()
                CommuteEpisodePreparer.Outcome.RETRY -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CommuteWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
