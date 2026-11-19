package com.mckimquyen.reader.domain.commute

import androidx.work.ExistingPeriodicWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteScheduleMigrationTest {
    @Test fun aScheduleThatNeverGotTheBatteryCondition_isReplacedOnce() =
        assertEquals(ExistingPeriodicWorkPolicy.REPLACE, CommuteScheduleMigration.policyFor(batteryConditionApplied = false))

    @Test fun aScheduleThatGotIt_isKeptSoStartupNeverMovesTheRunTime() =
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, CommuteScheduleMigration.policyFor(batteryConditionApplied = true))
}
