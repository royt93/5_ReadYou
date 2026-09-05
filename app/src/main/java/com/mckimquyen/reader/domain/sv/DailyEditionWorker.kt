package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyEditionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyEditionWorker"
        const val WORK_NAME = "DailyEditionPeriodicWork"

        fun enqueueDailyWork(workManager: WorkManager) {
            val constraints = Constraints.Builder().build()

            // 12-hour periodic work to cover morning and evening editions
            val periodicRequest = PeriodicWorkRequestBuilder<DailyEditionWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "Enqueued periodic daily edition work every 12 hours")
        }

        fun cancelDailyWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled daily edition work")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val accountId = context.currentAccountId
            val unreadArticles = articleDao.queryLatestUnread(accountId, 50)
            if (unreadArticles.isNotEmpty()) {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isMorning = hour in 4..13
                val title = if (isMorning) {
                    context.getString(R.string.zen_morning_edition)
                } else {
                    context.getString(R.string.zen_evening_edition)
                }
                val body = context.getString(R.string.zen_daily_edition_notification, unreadArticles.size)
                notificationHelper.notifyDailyEdition(title, body, unreadArticles.size)
                Log.d(TAG, "Daily edition notification posted: ${unreadArticles.size} unread articles")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in DailyEditionWorker", e)
            Result.retry()
        }
    }
}
