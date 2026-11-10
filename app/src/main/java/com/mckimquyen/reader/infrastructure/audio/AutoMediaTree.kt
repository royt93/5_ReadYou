package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem

/** Fixed ids of the Android Auto content tree. The root id and the CommuteCast leaf id predate this tree. */
object AutoMediaIds {
    const val ROOT = "commutecast_root"
    const val COMMUTE_BRANCH = "commute_root"
    const val PODCAST_BRANCH = "podcast_root"
    const val COMMUTE_EPISODE = "commutecast_latest_episode"
    const val PODCAST_PREFIX = "podcast:"
    const val MAX_PODCAST_ITEMS = 5

    fun podcastId(articleId: String): String = PODCAST_PREFIX + articleId

    /** The article id inside a podcast media id, or null for any other (or malformed) id. */
    fun podcastArticleId(mediaId: String?): String? =
        mediaId?.takeIf { it.startsWith(PODCAST_PREFIX) }?.removePrefix(PODCAST_PREFIX)?.takeIf { it.isNotBlank() }
}

data class AutoNode(
    val mediaId: String,
    val title: String,
    val subtitle: String?,
    val durationSec: Int?,
    val browsable: Boolean,
)

data class CommuteEntry(val title: String, val subtitle: String)

/** Pure description of what Android Auto may list and play. Holds no Android type and no URL. */
class AutoMediaTree(
    private val commuteBranchTitle: String,
    private val podcastBranchTitle: String,
) {
    fun children(parentId: String, commute: CommuteEntry?, podcasts: List<PodcastBrowseItem>): List<AutoNode>? =
        when (parentId) {
            AutoMediaIds.ROOT -> listOf(
                AutoNode(AutoMediaIds.COMMUTE_BRANCH, commuteBranchTitle, null, null, browsable = true),
                AutoNode(AutoMediaIds.PODCAST_BRANCH, podcastBranchTitle, null, null, browsable = true),
            )
            AutoMediaIds.COMMUTE_BRANCH ->
                listOfNotNull(commute?.let { AutoNode(AutoMediaIds.COMMUTE_EPISODE, it.title, it.subtitle, null, false) })
            AutoMediaIds.PODCAST_BRANCH -> shown(podcasts).map {
                AutoNode(AutoMediaIds.podcastId(it.id), it.title, it.feedName, it.durationSec, false)
            }
            else -> null
        }

    /** The item for [mediaId] only if it is exactly one of the episodes currently shown; never trusts the id alone. */
    fun resolvePodcast(mediaId: String?, current: List<PodcastBrowseItem>): PodcastBrowseItem? {
        val articleId = AutoMediaIds.podcastArticleId(mediaId) ?: return null
        return shown(current).firstOrNull { it.id == articleId }
    }

    private fun shown(podcasts: List<PodcastBrowseItem>) = podcasts.take(AutoMediaIds.MAX_PODCAST_ITEMS)
}
