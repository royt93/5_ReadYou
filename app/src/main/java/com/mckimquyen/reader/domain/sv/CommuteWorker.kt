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
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.ui.ext.commuteTimeBudgetMinutes
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
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

        fun enqueueDailyWork(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Tính toán độ trễ (delay) để công việc kích hoạt vào 6:00 AM sáng hôm sau
            val currentTime = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            if (targetTime.before(currentTime)) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis

            val periodicRequest = PeriodicWorkRequestBuilder<CommuteWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "CommuteCast Daily 6 AM work scheduled with initial delay: ${initialDelay / 1000}s")
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
            val candidatePool = articleDao.queryLatestUnread(
                accountId,
                limit = CommuteContentSelector.MAX_CANDIDATES_QUERY_LIMIT
            )

            if (candidatePool.isEmpty()) {
                Log.d(TAG, "No unread articles found for CommuteCast.")
                return@withContext Result.success()
            }

            val selectedArticles = contentSelector.selectArticles(
                candidates = candidatePool,
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
