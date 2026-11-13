package com.mckimquyen.reader.domain.commute

data class AutoPlayInput(
    val switchOn: Boolean,
    val episodeDateMillis: Long?,
    val episodePlayed: Boolean,
    val nowMillis: Long,
    val lastAutoPlayEpochDay: Long?,
    val isAudioDevice: Boolean,
    val otherAudioPlaying: Boolean,
)

enum class SkipReason { SWITCH_OFF, NO_EPISODE, EPISODE_PLAYED, EPISODE_TOO_OLD, ALREADY_AUTO_PLAYED_TODAY, NOT_AN_AUDIO_DEVICE }

sealed interface AutoPlayDecision {
    data object PlayNow : AutoPlayDecision
    data object OfferPlayButton : AutoPlayDecision
    data class Skip(val reason: SkipReason) : AutoPlayDecision
}

/**
 * Decides what to do when a Bluetooth audio device connects (STRAT-01). Pure and ordered: the first rule that matches
 * wins. Playing sound unasked is intrusive, so every guard errs towards doing nothing or only offering a button.
 */
object CommuteAutoPlayPolicy {
    private const val HOURS = 2L
    private const val MILLIS_PER_HOUR = 3_600_000L
    const val AUTO_PLAY_WINDOW_MS = HOURS * MILLIS_PER_HOUR
    const val MILLIS_PER_DAY = 86_400_000L

    fun epochDayUtc(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)

    fun decide(input: AutoPlayInput): AutoPlayDecision {
        if (!input.switchOn) return AutoPlayDecision.Skip(SkipReason.SWITCH_OFF)
        val date = input.episodeDateMillis ?: return AutoPlayDecision.Skip(SkipReason.NO_EPISODE)
        if (input.episodePlayed) return AutoPlayDecision.Skip(SkipReason.EPISODE_PLAYED)
        if (input.nowMillis - date > AUTO_PLAY_WINDOW_MS) return AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD)
        if (input.lastAutoPlayEpochDay == epochDayUtc(input.nowMillis)) {
            return AutoPlayDecision.Skip(SkipReason.ALREADY_AUTO_PLAYED_TODAY)
        }
        if (!input.isAudioDevice) return AutoPlayDecision.Skip(SkipReason.NOT_AN_AUDIO_DEVICE)
        if (input.otherAudioPlaying) return AutoPlayDecision.OfferPlayButton
        return AutoPlayDecision.PlayNow
    }
}
