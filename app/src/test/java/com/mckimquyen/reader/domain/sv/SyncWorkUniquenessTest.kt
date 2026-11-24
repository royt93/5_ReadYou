package com.mckimquyen.reader.domain.sv

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A one-time sync used workManager.enqueue with only a tag, so two pull-to-refresh taps (or a refresh next to the start-up sync) ran two SyncWorkers at once, and
 * cancelUniqueWork could not stop either. It is now unique with KEEP, under its own name so it does not replace the periodic chain.
 */
class SyncWorkUniquenessTest {
    @Test
    fun theOneTimeSyncIsUniqueAndKeepsARunningOne() {
        val wm = mockk<WorkManager>(relaxed = true)
        val name = slot<String>()
        val policy = slot<ExistingWorkPolicy>()
        val request = slot<OneTimeWorkRequest>()

        SyncWorker.enqueueOneTimeWork(wm)

        verify(exactly = 1) { wm.enqueueUniqueWork(capture(name), capture(policy), capture(request)) }
        assertEquals(SyncWorker.ONE_TIME_WORK_NAME, name.captured)
        assertEquals(ExistingWorkPolicy.KEEP, policy.captured)
        assertTrue("the UI finds sync work by this tag", SyncWorker.WORK_NAME in request.captured.tags)
    }

    @Test
    fun theOneTimeNameDiffersFromThePeriodicOne() {
        assertTrue(SyncWorker.ONE_TIME_WORK_NAME != SyncWorker.WORK_NAME)
    }

    @Test
    fun cancelSyncStopsBothTheOneTimeAndThePeriodicWork() {
        val root = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
        val repo = File(root, "domain/sv/AbstractRssRepository.kt").readText()
        val cancel = repo.substringAfter("fun cancelSync()").substringBefore("suspend fun doSync")
        assertTrue("cancelSync must cancel the periodic work", "cancelUniqueWork(SyncWorker.WORK_NAME)" in cancel)
        assertTrue("cancelSync must cancel the one-time work", "cancelUniqueWork(SyncWorker.ONE_TIME_WORK_NAME)" in cancel)
    }
}
