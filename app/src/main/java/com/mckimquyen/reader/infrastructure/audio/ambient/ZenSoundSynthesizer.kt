package com.mckimquyen.reader.infrastructure.audio.ambient

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ZenSoundSynthesizer {

    companion object {
        private const val TAG = "ZenSynthesizer"
        const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE = 4096
    }

    @Volatile
    private var isPlaying = false

    @Volatile
    private var volume = 0.5f

    @Volatile
    private var currentType = ZenSoundType.GENTLE_RAIN

    val isCurrentlyPlaying: Boolean get() = isPlaying
    val currentVolume: Float get() = volume
    val activeSoundType: ZenSoundType get() = currentType

    private var synthThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun setVolume(vol: Float) {
        this.volume = vol.coerceIn(0f, 1f)
    }

    fun setSoundType(type: ZenSoundType) {
        this.currentType = type
    }

    @Synchronized
    fun start(type: ZenSoundType, initialVolume: Float = 0.5f) {
        if (isPlaying) {
            stop()
        }

        this.currentType = type
        this.volume = initialVolume.coerceIn(0f, 1f)
        this.isPlaying = true

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(BUFFER_SIZE * 2)

        try {
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            isPlaying = false
            return
        }

        synthThread = Thread({ runSynthesisLoop() }, "ZenSynthThread").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        val thread = synthThread
        synthThread = null
        thread?.interrupt()
        try {
            thread?.join(300)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    private fun runSynthesisLoop() {
        val buffer = ShortArray(BUFFER_SIZE)
        val random = Random()

        // Persistent filter states
        var brownLast = 0.0
        var pinkB0 = 0.0
        var pinkB1 = 0.0
        var pinkB2 = 0.0
        var pinkB3 = 0.0
        var pinkB4 = 0.0
        var pinkB5 = 0.0
        var pinkB6 = 0.0

        var sampleIndex = 0L

        while (isPlaying) {
            val vol = this.volume
            val type = this.currentType

            var i = 0
            while (i < BUFFER_SIZE) {
                val t = sampleIndex.toDouble() / SAMPLE_RATE
                val whiteLeft = (random.nextDouble() * 2.0 - 1.0)
                val whiteRight = (random.nextDouble() * 2.0 - 1.0)

                var sampleLeft = 0.0
                var sampleRight = 0.0

                when (type) {
                    ZenSoundType.GENTLE_RAIN -> {
                        // Filtered Brown noise base + occasional subtle raindrop clicks
                        brownLast = (brownLast * 0.94) + (whiteLeft * 0.06)
                        sampleLeft = brownLast * 1.5
                        sampleRight = ((brownLast * 0.94) + (whiteRight * 0.06)) * 1.5

                        // Droplet impulse
                        if (random.nextDouble() < 0.0008) {
                            val drop = (random.nextDouble() * 0.8)
                            sampleLeft += drop
                            sampleRight += drop * 0.7
                        }
                    }

                    ZenSoundType.OCEAN_WAVES -> {
                        // Low-frequency surf swell envelope (period ~ 10 seconds = 0.1 Hz)
                        val swell = (sin(2.0 * PI * 0.1 * t) + 1.0) * 0.5
                        brownLast = (brownLast * 0.96) + (whiteLeft * 0.04)
                        sampleLeft = brownLast * swell * 3.0
                        sampleRight = brownLast * (1.0 - swell * 0.2) * swell * 3.0
                    }

                    ZenSoundType.PINK_NOISE -> {
                        // 1/f Paul Kellet filter
                        pinkB0 = 0.99886 * pinkB0 + whiteLeft * 0.0555179
                        pinkB1 = 0.99332 * pinkB1 + whiteLeft * 0.0750759
                        pinkB2 = 0.96900 * pinkB2 + whiteLeft * 0.1538520
                        pinkB3 = 0.86650 * pinkB3 + whiteLeft * 0.3104856
                        pinkB4 = 0.55000 * pinkB4 + whiteLeft * 0.5329522
                        pinkB5 = -0.7616 * pinkB5 - whiteLeft * 0.0168980
                        val pink = pinkB0 + pinkB1 + pinkB2 + pinkB3 + pinkB4 + pinkB5 + pinkB6 + whiteLeft * 0.5362
                        pinkB6 = whiteLeft * 0.115926
                        sampleLeft = pink * 0.15
                        sampleRight = sampleLeft
                    }

                    ZenSoundType.BINAURAL_40HZ -> {
                        // Left: 200 Hz carrier, Right: 240 Hz carrier (40Hz difference gamma beat)
                        sampleLeft = sin(2.0 * PI * 200.0 * t) * 0.35
                        sampleRight = sin(2.0 * PI * 240.0 * t) * 0.35
                    }

                    ZenSoundType.TIBETAN_BOWL -> {
                        // Chimes every 7 seconds, exponential decay
                        val periodSec = 7.0
                        val cycleTime = t % periodSec
                        val decay = exp(-1.2 * cycleTime)
                        // Harmonic series: 216Hz + 432Hz + 648Hz
                        val bowl = (sin(2.0 * PI * 216.0 * t) * 0.5 +
                                sin(2.0 * PI * 432.0 * t) * 0.3 +
                                sin(2.0 * PI * 648.0 * t) * 0.2) * decay
                        sampleLeft = bowl * 0.6
                        sampleRight = bowl * 0.6
                    }
                }

                // Volume and 16-bit PCM conversion with soft clipping
                val clampedL = (sampleLeft * vol).coerceIn(-1.0, 1.0)
                val clampedR = (sampleRight * vol).coerceIn(-1.0, 1.0)

                buffer[i] = (clampedL * 32767.0).toInt().toShort()
                buffer[i + 1] = (clampedR * 32767.0).toInt().toShort()

                sampleIndex++
                i += 2
            }

            if (!isPlaying) break

            val written = try {
                audioTrack?.write(buffer, 0, BUFFER_SIZE) ?: -1
            } catch (e: Exception) {
                -1
            }
            if (written < 0) break
        }
    }
}
