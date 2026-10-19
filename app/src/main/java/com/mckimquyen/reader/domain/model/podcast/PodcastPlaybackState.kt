package com.mckimquyen.reader.domain.model.podcast

enum class PodcastStatus { IDLE, PREPARING, PLAYING, PAUSED, ERROR }

data class PodcastPlaybackState(
    val articleId: String? = null,
    val title: String = "",
    val feedName: String = "",
    val status: PodcastStatus = PodcastStatus.IDLE,
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val speed: Float = 1.0f,
    val sleepRemainingSec: Int? = null,
)
