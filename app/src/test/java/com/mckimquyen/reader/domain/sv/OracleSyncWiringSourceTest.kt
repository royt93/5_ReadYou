package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleSyncWiringSourceTest {

    private val worker = File("src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt").readText()

    @Test fun workerTakesTheExtractorThroughItsConstructor() {
        assertTrue(worker.contains("private val oracleClaimExtractor: OracleClaimExtractor"))
        assertTrue(worker.contains("import com.mckimquyen.reader.infrastructure.ai.OracleClaimExtractor"))
    }

    @Test fun extractorRunsOnlyInsideTheSuccessfulSyncBranch() {
        val success = worker.substringAfter("if (syncResult.isSuccessfulSync()) {").substringBefore("            syncResult\n")
        assertTrue("extractor call must sit in the success branch", success.contains("oracleClaimExtractor.extractRecent("))
        assertEquals("called exactly once", 1, Regex("extractRecent\\(").findAll(worker).count())
    }

    @Test fun extractorFailureNeverFailsTheSync() {
        assertTrue(worker.substringBefore("oracleClaimExtractor.extractRecent(").trimEnd().endsWith("runCatching {"))
        assertTrue(worker.contains("Prediction finding failed"))
    }

    @Test fun extractorRunsAfterTheSemanticIndexer() {
        assertTrue(worker.indexOf("semanticEmbeddingIndexer.indexRecent") < worker.indexOf("oracleClaimExtractor.extractRecent"))
    }
}
