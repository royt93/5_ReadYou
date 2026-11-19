package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteWorkerScheduleSourceTest {

    private fun code(path: String) = File("src/main/java/com/mckimquyen/reader/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val worker = code("domain/sv/CommuteWorker.kt")
    private val app = code("RApp.kt")

    @Test fun theHardCodedSixAmIsGone() {
        assertFalse(worker.contains("Calendar.HOUR_OF_DAY, 6"))
        assertFalse(worker.contains("Calendar.getInstance"))
    }

    @Test fun theDelayComesFromThePolicy() {
        assertTrue(worker.contains("CommuteSchedulePolicy.initialDelayMillis("))
    }

    @Test fun startupPassesTheSavedListenTime_andKeepsTheSchedule() {
        val call = app.substringAfter("CommuteWorker.enqueueDailyWork(").substringBefore(")")
        assertTrue(call.contains("commuteListenMinuteOfDay"))
        assertFalse("startup must not replace the schedule on every launch", call.contains("REPLACE"))
    }

    @Test fun theWorkerTakesTheBatteryNotLowConstraint_butNoChargerConstraint() {
        assertTrue(worker.contains("setRequiresBatteryNotLow(true)"))
        assertFalse(worker.contains("setRequiresCharging"))
    }

    @Test fun startupChoosesThePolicyFromTheMigrationFlag_andWritesTheFlagAfterScheduling() {
        val body = app.substringAfter("private suspend fun workerInit()").substringBefore("FeedAutopilotWorker")
        assertTrue(body.contains("CommuteScheduleMigration.policyFor("))
        val schedule = body.indexOf("CommuteWorker.enqueueDailyWork(")
        val flag = body.indexOf("setCommuteBatteryConditionApplied(")
        assertTrue("the flag must be written", flag >= 0)
        assertTrue("the flag is written AFTER the schedule is queued, so a failure never marks it applied", schedule in 0 until flag)
    }

    @Test fun enqueueDailyWorkIsCalledFromExactlyOneStartupPlace() {
        assertEquals(1, Regex("""CommuteWorker\.enqueueDailyWork\(""").findAll(app).count())
    }
}
