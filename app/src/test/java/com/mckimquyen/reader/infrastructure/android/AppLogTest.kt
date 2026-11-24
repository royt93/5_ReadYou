package com.mckimquyen.reader.infrastructure.android

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

class AppLogTest {
    private val lines = mutableListOf<String>()
    private lateinit var original: (String) -> Unit

    @Before
    fun capture() {
        original = AppLog.sink
        AppLog.sink = { lines += it }
    }

    @After
    fun restore() {
        AppLog.sink = original
    }

    @Test
    fun theDefaultSinkNeverThrowsEvenWithoutAndroidsLog() {
        AppLog.sink = original
        AppLog.event("nav", "screen", "route" to "x")
    }

    @Test
    fun formatsAreaEventAndKeyValues() {
        AppLog.event("reels", "open", "via" to "button", "article" to "a1")
        assertEquals(listOf("reels: open via=button article=a1"), lines)
    }

    @Test
    fun noPairsGivesJustTheEvent() {
        AppLog.event("nav", "screen")
        assertEquals(listOf("nav: screen"), lines)
    }

    @Test
    fun nullValuesAreWrittenNotDropped() {
        AppLog.event("nav", "screen", "route" to null)
        assertEquals(listOf("nav: screen route=null"), lines)
    }

    @Test
    fun keyPointsAreWiredToTheLogger() {
        val root = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
        val expect = mapOf(
            "ui/page/common/HomeEntry.kt" to "AppLog.event(\"nav\", \"screen\"",
            "domain/sv/SyncWorker.kt" to "AppLog.event(\"sync\", \"start\"",
            "infrastructure/pref/EInkModePref.kt" to "AppLog.event(\"setting\", \"eink\"",
            "ui/page/reels/ReelsPage.kt" to "AppLog.event(\"reels\", \"open\"",
            "ui/page/home/HomeViewModel.kt" to "AppLog.event(\"sync\", \"manual-request\"",
            "ui/ext/ExtContext.kt" to "AppLog.event(\"share\", \"chooser\"",
            "infrastructure/pref/DarkThemePref.kt" to "AppLog.event(\"setting\", \"dark-theme\"",
            "ui/page/setting/SettingsPage.kt" to "AppLog.event(\"setting\", \"eink-banner-off\"",
        )
        val missing = expect.filter { (f, s) -> s !in File(root, f).readText() }.keys
        assertEquals("trace points that lost their logger call: $missing", emptySet<String>(), missing)
    }

    @Test
    fun everyFeatureViewModelLogsItsMainActions() {
        val root = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
        val expect = mapOf(
            "ui/page/triage/TriageViewModel.kt" to listOf("\"triage\", \"skip\"", "\"triage\", \"save\"", "\"triage\", \"read-now\""),
            "ui/page/home/read/podcaststudio/PodcastStudioViewModel.kt" to listOf("\"podcast-studio\", \"consent\"", "\"podcast-studio\", \"toggle-play\""),
            "ui/page/setting/backup/WebDavBackupViewModel.kt" to listOf("\"webdav\", \"backup\"", "\"webdav\", \"restore\""),
            "ui/page/rpg/BrainRpgViewModel.kt" to listOf("\"rpg\", \"quiz-consent\"", "\"rpg\", \"article-read-finished\""),
            "ui/page/rpg/StreakRescueViewModel.kt" to listOf("\"streak\", \"check-on-startup\""),
            "ui/page/digest/DigestViewModel.kt" to listOf("\"digest\", \"generate\""),
            "ui/page/home/read/ReadingViewModel.kt" to listOf("\"reading\", \"open-summary\"", "\"reading\", \"open-second-screen\""),
            "ui/component/commute/CommuteCastViewModel.kt" to listOf("\"commute\", \"time-budget\"", "\"commute\", \"toggle-play\""),
            "ui/page/home/read/deepdive/DeepDiveViewModel.kt" to listOf("\"deep-dive\", \"consent\""),
            "ui/page/notebook/NotebookViewModel.kt" to listOf("\"notebook\", \"delete-highlight\"", "\"notebook\", \"export-all\""),
        )
        val missing = expect.flatMap { (f, needles) -> val s = File(root, f).readText(); needles.filter { "AppLog.event($it" !in s }.map { "$f: $it" } }
        assertEquals("actions that lost their trace line: $missing", emptyList<String>(), missing)
    }

    @Test
    fun loggerNeverTakesTextTheUserTyped() {
        val src = File(listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }, "com/mckimquyen/reader")
        val offenders = src.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().withIndex().filter { (_, l) -> "AppLog.event(" in l && Regex("""\b(title|url|link|password|apiKey|key)\b\s+to\s""").containsMatchIn(l) }.map { "${f.name}:${it.index + 1}" } }
            .toList()
        assertFalse("AppLog lines must not carry titles, URLs, keys or passwords: $offenders", offenders.isNotEmpty())
    }
}
