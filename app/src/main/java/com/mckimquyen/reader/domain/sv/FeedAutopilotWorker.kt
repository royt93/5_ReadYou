package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Weekly, silent: refreshes the suggestion count shown in Settings. No notification, no network. */
@HiltWorker
class FeedAutopilotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: FeedAutopilotProcessor,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        processor.computeSuggestions()
        Result.success()
    } catch (error: Exception) {
        Log.e(TAG, "Feed autopilot analysis failed", error)
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "FeedAutopilotWeeklyWorker"
        const val REPEAT_INTERVAL_DAYS = 7L
        private const val TAG = "FeedAutopilotWorker"

        fun enqueueWeeklyWork(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<FeedAutopilotWorker>(
                REPEAT_INTERVAL_DAYS,
                TimeUnit.DAYS,
            ).build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
