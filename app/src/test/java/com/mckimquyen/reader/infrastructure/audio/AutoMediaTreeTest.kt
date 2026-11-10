package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaTreeTest {

    private val tree = AutoMediaTree(commuteBranchTitle = "Morning", podcastBranchTitle = "Podcasts")
    private val commute = CommuteEntry(title = "Edition", subtitle = "Hello")

    private fun episodes(count: Int) = (1..count).map { PodcastBrowseItem("a$it", "Title $it", "Feed", 60 * it) }

    private fun childrenOf(parent: String, entry: CommuteEntry?, podcasts: List<PodcastBrowseItem>): List<AutoNode> {
        val children = tree.children(parent, entry, podcasts)
        assertNotNull("children of $parent must not be null", children)
        return children.orEmpty()
    }

    @Test
    fun root_hasTwoBrowsableBranchesInOrder() {
        val children = childrenOf(AutoMediaIds.ROOT, commute, episodes(2))

        assertEquals(listOf(AutoMediaIds.COMMUTE_BRANCH, AutoMediaIds.PODCAST_BRANCH), children.map { it.mediaId })
        assertEquals(listOf("Morning", "Podcasts"), children.map { it.title })
        assertTrue(children.all { it.browsable })
    }

    @Test
    fun commuteBranch_holdsTheSingleLatestEpisodeKeepingTheOldId() {
        val children = childrenOf(AutoMediaIds.COMMUTE_BRANCH, commute, emptyList())

        assertEquals(1, children.size)
        assertEquals(AutoMediaIds.COMMUTE_EPISODE, children.single().mediaId)
        assertEquals("commutecast_latest_episode", children.single().mediaId)
        assertFalse(children.single().browsable)
        assertEquals("Edition", children.single().title)
        assertEquals("Hello", children.single().subtitle)
    }

    @Test
    fun commuteBranch_withoutEpisode_isEmptyNotNull() {
        assertEquals(emptyList<AutoNode>(), tree.children(AutoMediaIds.COMMUTE_BRANCH, null, emptyList()))
    }

    @Test
    fun podcastBranch_showsAtMostFiveEpisodesWithPrefixedIds() {
        val children = childrenOf(AutoMediaIds.PODCAST_BRANCH, null, episodes(8))

        assertEquals(5, children.size)
        assertEquals(listOf("podcast:a1", "podcast:a2", "podcast:a3", "podcast:a4", "podcast:a5"), children.map { it.mediaId })
        assertTrue(children.none { it.browsable })
        assertEquals("Feed", children.first().subtitle)
        assertEquals(60, children.first().durationSec)
    }

    @Test
    fun podcastBranch_empty_isEmptyNotNull() {
        assertEquals(emptyList<AutoNode>(), tree.children(AutoMediaIds.PODCAST_BRANCH, null, emptyList()))
    }

    @Test
    fun unknownParent_isNull() {
        assertNull(tree.children("nope", commute, episodes(1)))
        assertNull(tree.children("podcast:a1", commute, episodes(1)))
    }

    @Test
    fun podcastArticleId_requiresTheExactPrefixAndANonBlankRest() {
        assertEquals("abc", AutoMediaIds.podcastArticleId("podcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId("podcast:"))
        assertNull(AutoMediaIds.podcastArticleId("Podcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId("xpodcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId(AutoMediaIds.COMMUTE_EPISODE))
        assertNull(AutoMediaIds.podcastArticleId(null))
    }

    @Test
    fun resolvePodcast_returnsTheItemOnlyWhenItIsInTheCurrentResult() {
        val current = episodes(3)

        assertEquals("a2", tree.resolvePodcast("podcast:a2", current)?.id)
        assertNull("id valid by prefix but no longer in the result", tree.resolvePodcast("podcast:a9", current))
        assertNull(tree.resolvePodcast("a2", current))
        assertNull(tree.resolvePodcast("podcast:", current))
        assertNull(tree.resolvePodcast(null, current))
    }

    @Test
    fun resolvePodcast_ignoresEpisodesBeyondTheFiveShown() {
        assertNull("a6 was never shown to the car", tree.resolvePodcast("podcast:a6", episodes(8)))
    }

    @Test
    fun idIsNeverUsedAsAPathOrQuery() {
        val hostile = "podcast:../../x' OR 1=1 --"

        assertNull(tree.resolvePodcast(hostile, episodes(3)))
    }
}
