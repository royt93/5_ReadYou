package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommutePlayLatestSourceTest {

    private val service = File("src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaSessionService.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val playLatestBranch = service.substringAfter("ACTION_PLAY_LATEST ->").substringBefore("ACTION_PAUSE ->")

    @Test fun theActionIsDeclaredWithAUniqueName() {
        assertTrue(service.contains("const val ACTION_PLAY_LATEST = \"com.mckimquyen.reader.commute.ACTION_PLAY_LATEST\""))
        assertEquals(1, Regex("""ACTION_PLAY_LATEST =""").findAll(service).count())
    }

    @Test fun theBranchGoesForegroundFirst_thenStartsThePlayback() {
        assertTrue(playLatestBranch.contains("startForeground("))
        assertTrue(playLatestBranch.contains("resumeOrPlayLatestEpisode()"))
        assertTrue(
            "startForeground must come first: Android gives a service a few seconds to do it",
            playLatestBranch.indexOf("startForeground(") < playLatestBranch.indexOf("resumeOrPlayLatestEpisode()"),
        )
    }

    @Test fun startingAnEpisodeMarksItAsPlayed_onBothPaths() {
        val body = service.substringAfter("private fun resumeOrPlayLatestEpisode()").substringBefore("// --- MediaBrowserServiceCompat")
        assertEquals(2, Regex("""markPlayed\(""").findAll(body).count())
    }

    @Test fun thePlainPlayBranchIsUnchanged_itStillOnlyShowsTheNotification() {
        val plain = service.substringAfter("ACTION_PLAY ->").substringBefore("ACTION_PLAY_LATEST ->")
        assertTrue(!plain.contains("resumeOrPlayLatestEpisode"))
    }

    @Test fun theHelperCatchesAStartThatAndroidRefuses() {
        val helper = service.substringAfter("fun playLatest(").substringBefore("fun pause(")
        assertTrue(helper.contains("catch (e: Exception)"))
    }
}
