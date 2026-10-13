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

@HiltWorker
class BrainRpgDecayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: BrainRpgDecayProcessor,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        processor.run()
        Result.success()
    } catch (error: Exception) {
        Log.e(TAG, "Brain RPG decay notification failed", error)
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "BrainRpgDailyDecayNotification"
        private const val REPEAT_INTERVAL_DAYS = 1L
        private const val TAG = "BrainRpgDecayWorker"

        fun enqueueDailyWork(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<BrainRpgDecayWorker>(
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
