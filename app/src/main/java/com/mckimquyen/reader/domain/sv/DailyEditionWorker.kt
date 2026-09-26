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
import androidx.work.workDataOf
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.zen.ZenDailyEditionManager
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker gửi bản tin Zen Daily Edition vào đúng các mốc giờ người dùng cấu hình (mặc định 07:00
 * và 20:00).
 *
 * Lịch gồm **hai** unique periodic work riêng biệt (sáng và tối), mỗi cái chu kỳ 24 giờ nhưng được
 * neo bằng `setInitialDelay` tới đúng mốc giờ tương ứng — thay cho chu kỳ 12 giờ cũ vốn tính từ
 * thời điểm user bật tính năng và không liên quan gì tới giờ trong ngày. Khi user đổi giờ,
 * [ZenDailyEditionManager] gọi lại [scheduleNext] và lịch được re-anchor bằng
 * [ExistingPeriodicWorkPolicy.REPLACE].
 *
 * Buổi (sáng/tối) được truyền qua input data nên tiêu đề thông báo luôn đúng, không phải suy ra
 * từ giờ chạy thực tế (WorkManager có thể chạy trễ so với mốc đã hẹn).
 */
@HiltWorker
class DailyEditionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyEditionWorker"

        const val WORK_NAME_MORNING = "DailyEditionWorkMorning"
        const val WORK_NAME_EVENING = "DailyEditionWorkEvening"

        /** Input data key: true = ấn phẩm buổi sáng, false = buổi tối. */
        const val KEY_IS_MORNING = "key_is_morning"

        private const val MAX_UNREAD_ARTICLES = 50
        private const val REPEAT_INTERVAL_HOURS = 24L

        /**
         * Lên lịch (hoặc re-anchor) cả hai mốc giờ phát hành. Mỗi mốc là một unique periodic work
         * 24 giờ với initial delay tính tới lần xuất hiện kế tiếp của đúng mốc giờ đó.
         */
        fun scheduleNext(
            workManager: WorkManager,
            morningTime: String,
            eveningTime: String,
            nowMillis: Long = System.currentTimeMillis(),
        ) {
            scheduleSlot(workManager, WORK_NAME_MORNING, morningTime, isMorning = true, nowMillis)
            scheduleSlot(workManager, WORK_NAME_EVENING, eveningTime, isMorning = false, nowMillis)
        }

        private fun scheduleSlot(
            workManager: WorkManager,
            workName: String,
            time: String,
            isMorning: Boolean,
            nowMillis: Long,
        ) {
            val delay = ZenDailyEditionManager.millisUntilNextOccurrence(listOf(time), nowMillis)
            if (delay == null) {
                Log.w(TAG, "Invalid edition time '$time' for $workName, skipping scheduling")
                return
            }

            val request = PeriodicWorkRequestBuilder<DailyEditionWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .setInputData(workDataOf(KEY_IS_MORNING to isMorning))
                .addTag(workName)
                .build()

            // REPLACE: đổi giờ phải thực sự dịch mốc chạy, không giữ lại lịch đã neo giờ cũ
            // (KEEP sẽ bỏ qua request mới và giữ nguyên mốc sai).
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
            Log.d(TAG, "Scheduled $workName at $time, first run in ${delay / 1000}s")
        }

        fun cancelDailyWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME_MORNING)
            workManager.cancelUniqueWork(WORK_NAME_EVENING)
            Log.d(TAG, "Cancelled daily edition work")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val accountId = context.currentAccountId
            val unreadArticles = articleDao.queryLatestUnread(accountId, MAX_UNREAD_ARTICLES)
            if (unreadArticles.isNotEmpty()) {
                val isMorning = inputData.getBoolean(KEY_IS_MORNING, true)
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
