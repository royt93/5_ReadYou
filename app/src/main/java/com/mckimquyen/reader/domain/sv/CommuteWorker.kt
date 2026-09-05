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
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
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
    private val scriptService: CommuteScriptService,
    private val notificationHelper: NotificationHelper,
    private val commuteAudioPlayer: CommuteAudioPlayer,
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
            val unreadArticles = articleDao.queryLatestUnread(accountId, limit = 5)

            if (unreadArticles.isEmpty()) {
                Log.d(TAG, "No unread articles found for CommuteCast.")
                return@withContext Result.success()
            }

            Log.d(TAG, "Synthesizing CommuteCast episode for ${unreadArticles.size} articles...")
            val episode = scriptService.generateScript(unreadArticles, isDeepDive = false)

            // Lưu episode vào audio player sẵn sàng
            commuteAudioPlayer.playEpisode(episode, startFromIndex = 0)
            commuteAudioPlayer.pause() // Đặt ở trạng thái sẵn sàng phát

            // Bắn thông báo sáng
            notificationHelper.notifyCommuteCast(episode)
            Log.d(TAG, "CommuteCast Episode generated and notification dispatched successfully.")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CommuteWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
