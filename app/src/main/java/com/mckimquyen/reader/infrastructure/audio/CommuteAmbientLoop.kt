package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.mckimquyen.reader.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the background lofi ambient track looping beneath the CommuteCast dialogue.
 *
 * Implements audio ducking by reducing its volume when a host is speaking and restoring it during
 * pauses or transitions.
 */
@Singleton
class CommuteAmbientLoop @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "CommuteAmbientLoop"

        /** Regular volume when hosts pause or between dialogues. */
        const val NORMAL_VOLUME = 0.25f

        /** Ducked volume (~20% of regular) while a host is actively speaking. */
        const val DUCKED_VOLUME = 0.05f
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentVolume = NORMAL_VOLUME
    private var isPlayingInternal = false

    val isPlaying: Boolean
        get() = isPlayingInternal && mediaPlayer?.isPlaying == true

    /**
     * Starts looping the background lofi pad at [initialVolume].
     * If already playing, adjusts volume to [initialVolume].
     */
    fun start(initialVolume: Float = NORMAL_VOLUME) {
        val clamped = initialVolume.coerceIn(0.0f, 1.0f)
        currentVolume = clamped

        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(context, R.raw.commute_lofi_pad)?.apply {
                    isLooping = true
                    setVolume(clamped, clamped)
                    start()
                }
                isPlayingInternal = mediaPlayer != null
                if (mediaPlayer == null) {
                    Log.w(TAG, "Failed to create MediaPlayer for commute_lofi_pad resource.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting commute ambient loop", e)
                stop()
            }
        } else {
            try {
                setVolume(clamped)
                if (mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                }
                isPlayingInternal = true
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming commute ambient loop", e)
            }
        }
    }

    /**
     * Changes the playback volume of the loop.
     */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        currentVolume = clamped
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ambient loop volume", e)
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            isPlayingInternal = false
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing ambient loop", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                isPlayingInternal = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming ambient loop", e)
        }
    }

    /**
     * Stops playback and releases the native [MediaPlayer] instance.
     * Idempotent: safe to call repeatedly.
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            isPlayingInternal = false
        }
    }
}
