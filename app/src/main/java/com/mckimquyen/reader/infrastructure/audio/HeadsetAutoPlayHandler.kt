package com.mckimquyen.reader.infrastructure.audio

import android.util.Log
import com.mckimquyen.reader.domain.commute.AutoPlayDecision
import com.mckimquyen.reader.domain.commute.AutoPlayInput
import com.mckimquyen.reader.domain.commute.CommuteAutoPlayPolicy
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import kotlinx.coroutines.CancellationException

/**
 * What happens when a Bluetooth audio device connects (STRAT-01). Kept apart from the receiver so every branch is
 * reachable from a plain unit test. The rules live in [CommuteAutoPlayPolicy]; this class only gathers the facts, acts
 * on the decision and remembers today's auto-play AFTER playback really started.
 */
class HeadsetAutoPlayHandler(private val deps: Deps) {

    interface Deps {
        suspend fun switchOn(): Boolean
        suspend fun episode(): CommuteEpisode?
        suspend fun episodePlayed(id: String): Boolean
        suspend fun lastAutoPlayEpochDay(): Long?
        suspend fun saveAutoPlayEpochDay(day: Long)
        fun otherAudioPlaying(): Boolean
        fun hasBluetoothPermission(): Boolean
        fun startPlayback(): Boolean
        fun offerPlayButton()
        fun nowMillis(): Long
    }

    enum class Result { SKIPPED, PLAYED, OFFERED }

    suspend fun onAudioDeviceConnected(isAudioDevice: Boolean): Result = try {
        if (!deps.hasBluetoothPermission()) {
            Result.SKIPPED
        } else {
            val now = deps.nowMillis()
            val episode = deps.episode()
            val input = AutoPlayInput(
                switchOn = deps.switchOn(),
                episodeDateMillis = episode?.date?.time,
                episodePlayed = episode?.let { deps.episodePlayed(it.id) } ?: false,
                nowMillis = now,
                lastAutoPlayEpochDay = deps.lastAutoPlayEpochDay(),
                isAudioDevice = isAudioDevice,
                otherAudioPlaying = deps.otherAudioPlaying(),
            )
            when (CommuteAutoPlayPolicy.decide(input)) {
                is AutoPlayDecision.Skip -> Result.SKIPPED
                AutoPlayDecision.OfferPlayButton -> {
                    deps.offerPlayButton()
                    Result.OFFERED
                }
                AutoPlayDecision.PlayNow -> if (deps.startPlayback()) {
                    // Only a start that Android allowed uses up today's single automatic play.
                    deps.saveAutoPlayEpochDay(CommuteAutoPlayPolicy.epochDayUtc(now))
                    Result.PLAYED
                } else {
                    deps.offerPlayButton()
                    Result.OFFERED
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A receiver must never crash the app; only the type is logged.
        Log.w(TAG, "Headset auto-play failed: ${e.javaClass.simpleName}")
        Result.SKIPPED
    }

    private companion object {
        const val TAG = "HeadsetAutoPlay"
    }
}
