package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPodcastLauncherTest {

    private val tree = AutoMediaTree("Morning", "Podcasts")
    private val shown = (1..8).map { PodcastBrowseItem("a$it", "Title $it", "Feed $it", 60) }
    private val audio = AudioInfo("https://cdn.example.com/a2.mp3", "audio/mpeg", 120)

    private var shownLoads = 0
    private val audioLoads = mutableListOf<String>()
    private val started = mutableListOf<List<Any?>>()
    private var audioResult: AudioInfo? = audio

    private val launcher = AutoPodcastLauncher(
        tree = tree,
        loadShown = { shownLoads++; shown },
        loadAudio = { audioLoads += it; audioResult },
        start = { id, title, feed, info -> started += listOf(id, title, feed, info) },
    )

    @Test
    fun validShownId_startsThatEpisodeWithItsMetadataAndAudio() = runTest {
        assertTrue(launcher.play("podcast:a2"))

        assertEquals(listOf("a2"), audioLoads)
        assertEquals(listOf(listOf("a2", "Title 2", "Feed 2", audio)), started)
    }

    @Test
    fun idNotInTheShownList_startsNothingAndNeverLoadsAudio() = runTest {
        assertFalse(launcher.play("podcast:zzz"))
        assertFalse("a6 exists but was never one of the five shown", launcher.play("podcast:a6"))

        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun idsWithoutThePodcastPrefix_areRejectedBeforeAnyQuery() = runTest {
        listOf(null, "", "podcast:", "a2", "commutecast_latest_episode").forEach {
            assertFalse(launcher.play(it))
        }

        assertEquals("a bad id must be rejected before the database is touched", 0, shownLoads)
        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun hostileIdWithTheRightPrefix_isLookedUpOnlyAsAnOpaqueKeyAndNeverMatches() = runTest {
        assertFalse(launcher.play("podcast:../../x' OR 1=1 --"))

        assertEquals(1, shownLoads)
        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun episodeWithoutPlayableAudio_startsNothing() = runTest {
        audioResult = null

        assertFalse(launcher.play("podcast:a2"))
        assertEquals(listOf("a2"), audioLoads)
        assertTrue(started.isEmpty())
    }
}
