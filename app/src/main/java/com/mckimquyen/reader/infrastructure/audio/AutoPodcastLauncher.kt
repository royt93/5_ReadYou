package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem

/**
 * Starts a podcast episode the car asked for. The id is untrusted input: it must carry the podcast prefix and be one
 * of the episodes currently shown, and only then is the audio URL read for that single article.
 */
class AutoPodcastLauncher(
    private val tree: AutoMediaTree,
    private val loadShown: suspend () -> List<PodcastBrowseItem>,
    private val loadAudio: suspend (articleId: String) -> AudioInfo?,
    private val start: (articleId: String, title: String, feedName: String, audio: AudioInfo) -> Unit,
) {
    /** True when an episode was started. */
    suspend fun play(mediaId: String?): Boolean {
        if (AutoMediaIds.podcastArticleId(mediaId) == null) return false
        val item = tree.resolvePodcast(mediaId, loadShown()) ?: return false
        val audio = loadAudio(item.id) ?: return false
        start(item.id, item.title, item.feedName, audio)
        return true
    }
}
