package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteConsentWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val service = code("domain/sv/CommuteScriptService.kt")
    private val preparer = code("domain/sv/CommuteEpisodePreparer.kt")
    private val viewModel = code("ui/component/commute/CommuteCastViewModel.kt")

    @Test fun theServiceDefaultsToNoGemini() {
        assertTrue(service.contains("allowGemini: Boolean = false"))
    }

    @Test fun theServiceReadsTheKeysOnlyBehindTheGate() {
        val body = service.substringAfter("suspend fun generateScript(").substringBefore("private suspend fun generateScriptWithGemini")
        assertTrue(body.contains("if (allowGemini)"))
        assertEquals("the keys are touched in exactly one place", 1, Regex("API_KEYS").findAll(service).count())
    }

    @Test fun theWorkerPathPassesTheConsent() {
        assertTrue(preparer.contains("allowGemini = context.commuteScriptGeminiConsent == true"))
    }

    @Test fun theSheetPathPassesTheConsent_offTheMainThread() {
        assertTrue(viewModel.contains("commuteScriptGeminiConsent == true"))
        assertTrue(viewModel.substringBefore("commuteScriptGeminiConsent").takeLast(200).contains("Dispatchers.IO"))
    }

    @Test fun noOtherCallerOfGenerateScriptExists() {
        val callers = File(root).walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("""generateScript\(""").containsMatchIn(it.readText()) && !it.name.startsWith("CommuteScriptService") }
            .map { it.name }.sorted().toList()
        assertEquals(listOf("CommuteCastViewModel.kt", "CommuteEpisodePreparer.kt"), callers)
    }

    @Test fun neitherCallerHardCodesTrue() {
        assertFalse(preparer.contains("allowGemini = true"))
        assertFalse(viewModel.contains("allowGemini = true"))
    }
}
