package com.mckimquyen.reader.domain.model.commute

import androidx.annotation.Keep
import java.util.Date

/**
 * The two hosts of an episode.
 *
 * Their difference is one of role, not of gender: Android's TTS API cannot report a voice's gender,
 * so the app picks two *different* voices when the device has them and never claims which is which.
 */
@Keep
enum class CommuteSpeaker {
    ALEX, // Anchor: leads the bulletin, analytical
    SAM,  // Co-host: pushes back, keeps the pace up
}

@Keep
data class CommuteDialogue(
    val speaker: CommuteSpeaker,
    val text: String,
)

@Keep
data class CommuteEpisode(
    val id: String,
    val title: String,
    val date: Date = Date(),
    val dialogues: List<CommuteDialogue>,
    val articleIds: List<String> = emptyList(),
    val isDeepDive: Boolean = false,
)
