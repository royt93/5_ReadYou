package com.mckimquyen.reader.domain.model.commute

import androidx.annotation.Keep
import java.util.Date

@Keep
enum class CommuteSpeaker {
    ALEX, // Host chính: Giọng nam trầm, phong thái phân tích, điềm tĩnh
    SAM,  // Co-host: Giọng nữ trẻ trung, năng động, phản biện và hào hứng
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
