package com.mckimquyen.reader.domain.sv

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class BrainRpgDecayWorkerTest {
    @Test fun enqueueDailyWork_isOneUniqueDailyKeepRequest_soStartupCannotStackDuplicates() {
        val manager = mockk<WorkManager>(relaxed = true)
        val requests = mutableListOf<PeriodicWorkRequest>()

        BrainRpgDecayWorker.enqueueDailyWork(manager)
        BrainRpgDecayWorker.enqueueDailyWork(manager)

        verify(exactly = 2) {
            manager.enqueueUniquePeriodicWork(
                BrainRpgDecayWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                capture(requests),
            )
        }
        requests.forEach { request ->
            assertEquals(TimeUnit.DAYS.toMillis(1), request.workSpec.intervalDuration)
            assertEquals(BrainRpgDecayWorker::class.java.name, request.workSpec.workerClassName)
        }
    }
}
