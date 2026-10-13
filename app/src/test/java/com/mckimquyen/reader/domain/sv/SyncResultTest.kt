package com.mckimquyen.reader.domain.sv

import androidx.work.Data
import androidx.work.ListenableWorker.Result
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncResultTest {

    @Test
    fun plainSuccess_isSuccessful() = assertTrue(Result.success().isSuccessfulSync())

    @Test
    fun successCarryingOutputData_isSuccessful() {
        // Fever/GoogleReader return success(Data) with the isSyncing flag.
        assertTrue(Result.success(Data.Builder().putBoolean("isSyncing", false).build()).isSuccessfulSync())
    }

    @Test
    fun failure_isNotSuccessful() = assertFalse(Result.failure().isSuccessfulSync())

    @Test
    fun failureCarryingOutputData_isNotSuccessful() {
        assertFalse(Result.failure(Data.Builder().putBoolean("isSyncing", false).build()).isSuccessfulSync())
    }

    @Test
    fun retry_isNotSuccessful() = assertFalse(Result.retry().isSuccessfulSync())
}
