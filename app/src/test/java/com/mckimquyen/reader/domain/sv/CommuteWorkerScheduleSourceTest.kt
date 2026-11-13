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

    @Test fun theWorkerStillTakesNoChargingOrBatteryConstraint() {
        assertFalse(worker.contains("setRequiresCharging"))
        assertFalse(worker.contains("setRequiresBatteryNotLow"))
    }

    @Test fun enqueueDailyWorkIsCalledFromExactlyOneStartupPlace() {
        assertEquals(1, Regex("""CommuteWorker\.enqueueDailyWork\(""").findAll(app).count())
    }
}
