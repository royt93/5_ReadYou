package com.mckimquyen.reader.infrastructure

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R8 cannot run in a JVM test, so this only pins the rules a release run on a phone showed to be needed (REL-01): without them the release
 * build lost the generic types Retrofit and Gson read by reflection (`Class cannot be cast to ParameterizedType`, `LinkedTreeMap cannot be
 * cast to ...Dialogue`). The proof that the rules work is the release run on the Pixel; this test only stops them from being deleted or
 * from pointing at a class that was renamed.
 */
class ProguardRulesTest {
    private val rules = File("proguard-rules.pro").readText()
    private fun source(path: String) = File("src/main/java/com/mckimquyen/reader/$path").readText()

    @Test fun retrofitSuspendFunctions_keepContinuationAndResponse() {
        assertTrue(rules.contains("-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation"))
        assertTrue(rules.contains("-keep,allowobfuscation,allowshrinking class retrofit2.Response"))
    }

    private fun assertFieldsKept(binaryName: String) {
        val rule = Regex("""-keepclassmembers\s+class\s+${Regex.escape(binaryName)}\s*\{\s*<fields>;\s*}""")
        assertTrue("no keep rule for the fields of $binaryName", rule.containsMatchIn(rules))
    }

    @Test fun commuteEpisodeDtos_haveTheirFieldsKept() {
        val store = source("infrastructure/audio/CommuteEpisodeStore.kt")
        assertTrue(store.contains("data class EpisodeDto(") && store.contains("data class DialogueDto("))
        assertFieldsKept("com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore\$EpisodeDto")
        assertFieldsKept("com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore\$DialogueDto")
    }

    @Test fun dailyDigestDtos_haveTheirFieldsKept() {
        val digest = source("infrastructure/ai/DailyDigestService.kt")
        listOf("DailyDigestContent", "DailyDigestSection", "DailyDigestItem").forEach { name ->
            assertTrue(digest.contains("data class $name("))
            assertFieldsKept("com.mckimquyen.reader.infrastructure.ai.$name")
        }
    }

    @Test fun webDavPositionRecord_hasItsFieldsKept() {
        assertTrue(source("infrastructure/sync/WebDavReadPositionSyncer.kt").contains("data class WebDavPositionRecord("))
        assertFieldsKept("com.mckimquyen.reader.infrastructure.sync.WebDavPositionRecord")
    }
}
