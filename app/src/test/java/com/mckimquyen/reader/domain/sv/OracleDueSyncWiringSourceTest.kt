package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleDueSyncWiringSourceTest {

    private val worker = File("src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt").readLines()
        .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")
    private val reminderSource = File("src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleDueReminder.kt").takeIf { it.exists() }
        ?.readLines().orEmpty()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ")
        }.joinToString("\n")

    @Test fun workerTakesTheReminderThroughItsConstructor() {
        assertTrue(worker.contains("private val oracleDueReminder: OracleDueReminder"))
        assertTrue(worker.contains("import com.mckimquyen.reader.infrastructure.ai.OracleDueReminder"))
    }

    @Test fun reminderRunsOnlyInsideTheSuccessfulSyncBranch_andExactlyOnce() {
        val success = worker.substringAfter("if (syncResult.isSuccessfulSync()) {").substringBefore("            syncResult\n")
        assertTrue(success.contains("oracleDueReminder.remindDue("))
        assertEquals(1, Regex("remindDue\\(").findAll(worker).count())
    }

    @Test fun reminderFailureNeverFailsTheSync() {
        assertTrue(worker.substringBefore("oracleDueReminder.remindDue(").trimEnd().endsWith("runCatching {"))
        assertTrue(worker.contains("Due reminder failed"))
    }

    @Test fun reminderRunsAfterThePredictionFinder() {
        assertTrue(worker.indexOf("oracleClaimExtractor.extractRecent") < worker.indexOf("oracleDueReminder.remindDue"))
    }

    @Test fun reminderIsNotGatedOnTheGeminiConsent() {
        // Local data only: the consent that guards the AI finder must not switch the reminder off.
        assertTrue(!worker.substringAfter("oracleDueReminder.remindDue(").take(200).contains("Consent"))
        assertTrue(!reminderSource.contains("oracleClaimsGeminiConsent"))
    }

    @Test fun reminderNeverTouchesTheNetworkGeminiOrAds() {
        assertTrue("the reminder source must exist", reminderSource.isNotBlank())
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded", "http").forEach {
            assertTrue("OracleDueReminder must not mention '$it'", !reminderSource.contains(it, ignoreCase = true))
        }
    }
}
