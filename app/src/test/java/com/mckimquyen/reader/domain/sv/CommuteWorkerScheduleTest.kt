package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

// Robolectric, because the scheduler logs through android.util.Log, which a bare JVM test cannot resolve.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteWorkerScheduleTest {

    private val utc = ZoneOffset.UTC
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 13, h, m).atZone(utc).toInstant().toEpochMilli()

    private fun enqueue(
        listen: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
        now: Long = at(5, 0),
    ): Pair<PeriodicWorkRequest, ExistingPeriodicWorkPolicy> {
        val manager = mockk<WorkManager>(relaxed = true)
        val requests = mutableListOf<PeriodicWorkRequest>()
        val policies = mutableListOf<ExistingPeriodicWorkPolicy>()
        CommuteWorker.enqueueDailyWork(manager, listen, policy, now, utc)
        verify(exactly = 1) {
            manager.enqueueUniquePeriodicWork(CommuteWorker.WORK_NAME, capture(policies), capture(requests))
        }
        return requests.single() to policies.single()
    }

    @Test fun theDefaultCall_stillSchedulesAtTheOldCompatibleShape() {
        val manager = mockk<WorkManager>(relaxed = true)
        CommuteWorker.enqueueDailyWork(manager)
        verify(exactly = 1) { manager.enqueueUniquePeriodicWork(CommuteWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, any()) }
    }

    @Test fun theFirstRun_isThirtyMinutesBeforeTheListenTime() {
        val (request, _) = enqueue(listen = 7 * 60)
        assertEquals(90 * 60_000L, request.workSpec.initialDelay)
    }

    @Test fun aLaterListenTime_movesTheFirstRun() {
        val (request, _) = enqueue(listen = 8 * 60 + 30)
        assertEquals((3 * 60 + 0) * 60_000L, request.workSpec.initialDelay)
    }

    @Test fun theIntervalIsStillOneDay_andTheWorkerClassIsRight() {
        val (request, _) = enqueue(listen = 7 * 60)
        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(CommuteWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test fun theNetworkConstraintIsKept_andTheBatteryNotLowConditionIsAdded_butNotACharger() {
        val c = enqueue(listen = 7 * 60).first.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
        assertTrue("STRAT-01b: do not compose the episode when the battery is low", c.requiresBatteryNotLow())
        assertTrue("a charger is NOT required (owner chose battery-not-low only)", !c.requiresCharging())
    }

    @Test fun changingTheTime_alsoCarriesTheBatteryCondition() {
        val c = enqueue(listen = 9 * 60, policy = ExistingPeriodicWorkPolicy.REPLACE).first.workSpec.constraints
        assertTrue(c.requiresBatteryNotLow())
    }

    @Test fun theUniqueName_isUnchanged_soOldInstallsDoNotGetTwoSchedules() =
        assertEquals("CommuteCastDailyWork", CommuteWorker.WORK_NAME)

    @Test fun startup_keepsTheExistingSchedule() {
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, enqueue(listen = 7 * 60).second)
    }

    @Test fun changingTheTime_replacesTheSchedule_becauseKeepWouldIgnoreIt() {
        assertEquals(ExistingPeriodicWorkPolicy.REPLACE, enqueue(listen = 9 * 60, policy = ExistingPeriodicWorkPolicy.REPLACE).second)
    }

    @Test fun aCorruptListenTime_fallsBackToTheDefault() {
        val (request, _) = enqueue(listen = 99_999)
        assertEquals(90 * 60_000L, request.workSpec.initialDelay)
    }
}
