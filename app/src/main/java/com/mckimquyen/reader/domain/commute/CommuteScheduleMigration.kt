package com.mckimquyen.reader.domain.commute

import androidx.work.ExistingPeriodicWorkPolicy

/**
 * STRAT-01b: the morning episode is composed only when the battery is not low. A phone that already has the daily schedule keeps its old
 * constraints under [ExistingPeriodicWorkPolicy.KEEP] (this WorkManager has no UPDATE), so the schedule is replaced ONCE; after that startup keeps it,
 * because replacing it on every launch would move the next run to the time the app was opened.
 */
object CommuteScheduleMigration {
    fun policyFor(batteryConditionApplied: Boolean): ExistingPeriodicWorkPolicy =
        if (batteryConditionApplied) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.REPLACE
}
