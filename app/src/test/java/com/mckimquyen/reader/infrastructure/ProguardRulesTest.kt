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

    /**
     * Found by the release run on the A50s (2026-11-23): once the update check was switched off nothing called a method of this interface, so the
     * Retrofit rule `-if interface * { @retrofit2.http.* <methods>; }` never fired, R8 saw an interface nobody implements (Retrofit builds a
     * java.lang.reflect.Proxy at run time) and compiled `Retrofit.create(...)` into `throw ClassCastException`. The app then crashed in
     * `RApp.onCreate` on every start of the release build. The interface is injected into RApp, so it has to stay whether or not a method is used.
     */
    @Test fun networkDataSource_isKeptSoRetrofitsProxyIsNotCompiledIntoAClassCastException() {
        val source = source("infrastructure/net/NetworkDataSource.kt")
        assertTrue(source.contains("interface NetworkDataSource"))
        val rule = Regex("""-keep\s+interface\s+com\.mckimquyen\.reader\.infrastructure\.net\.NetworkDataSource\s*\{\s*\*;\s*}""")
        assertTrue("no keep rule for NetworkDataSource", rule.containsMatchIn(rules))
    }

    @Test fun webDavPositionRecord_hasItsFieldsKept() {
        assertTrue(source("infrastructure/sync/WebDavReadPositionSyncer.kt").contains("data class WebDavPositionRecord("))
        assertFieldsKept("com.mckimquyen.reader.infrastructure.sync.WebDavPositionRecord")
    }

    /**
     * `-keep class androidx.compose.** { *; }` kept every class of material-icons-extended (about 11 000 icon files) and made the R8 mapping
     * 264 MiB. Compose ships its own consumer rules, so the blanket keep is not needed; without it the mapping is about 127 MiB.
     */
    @Test fun composeIsNotKeptWholesale_soTheMappingFileStaysSmall() {
        assertTrue(!Regex("""-keep\s+class\s+androidx\.compose\.\*\*""").containsMatchIn(rules))
    }

    /**
     * Gson reads these classes by reflection, so R8 must not remove their fields. Each `data class` of the provider DTO files has to carry
     * `@Keep`; the DTOs outside them are pinned by [assertFieldsKept] above or sit under `-keep class ...domain.model.**`. A new provider DTO
     * without `@Keep` would work in a debug build and lose its fields in the release build.
     */
    @Test fun everyProviderDtoDataClass_isMarkedKeep() {
        listOf("infrastructure/rss/provider/fever/FeverDTO.kt", "infrastructure/rss/provider/googleReader/GoogleReaderApiDto.kt").forEach { path ->
            val lines = source(path).lines()
            lines.forEachIndexed { index, line ->
                if (line.trimStart().startsWith("data class ")) {
                    // a comment line may sit between the annotation and the class (ReadingList does)
                    val previous = lines.take(index).lastOrNull { it.isNotBlank() && !it.trimStart().startsWith("//") }?.trim().orEmpty()
                    assertTrue("$path: `${line.trim()}` has no @Keep right above it", previous == "@Keep")
                }
            }
        }
    }

    @Test fun gsonTargetsOutsideTheKeptPackages_haveKeepOrFieldRules() {
        val articleHighlights = source("domain/model/article/ArticleHighlights.kt")
        assertTrue(articleHighlights.substringBefore("data class ArticleHighlights(").trimEnd().endsWith("@Keep"))
        assertTrue(rules.contains("-keep class com.mckimquyen.reader.domain.model.** { *; }"))
        assertFieldsKept("com.mckimquyen.reader.infrastructure.ai.DailyDigestContent")
    }
}
