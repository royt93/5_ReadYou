package com.mckimquyen.reader.domain.model.podcast

import com.mckimquyen.reader.domain.model.commute.CommuteEpisode

/** Where a Podcast Studio script came from, so the screen can say so instead of implying everything is AI. */
enum class PodcastScriptSource {
    GEMINI,
    HEURISTIC,
}

/** A single-article episode plus the honest origin of its script. It is never persisted. */
data class PodcastStudioEpisode(
    val episode: CommuteEpisode,
    val source: PodcastScriptSource,
)
