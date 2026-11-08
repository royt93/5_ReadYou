package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedAutopilotWorkerTest {

    @Test fun enqueueWeeklyWorkUsesKeepPolicySevenDayIntervalAndUniqueName() {
        val workManager: WorkManager = mockk(relaxed = true)
        val nameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        FeedAutopilotWorker.enqueueWeeklyWork(workManager)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                capture(nameSlot),
                capture(policySlot),
                capture(requestSlot),
            )
        }
        assertEquals("FeedAutopilotWeeklyWorker", nameSlot.captured)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
        assertEquals(TimeUnit.DAYS.toMillis(7), requestSlot.captured.workSpec.intervalDuration)
    }

    @Test fun companionConstantsStayPinned() {
        assertEquals("FeedAutopilotWeeklyWorker", FeedAutopilotWorker.UNIQUE_WORK_NAME)
        assertEquals(7L, FeedAutopilotWorker.REPEAT_INTERVAL_DAYS)
    }
}
