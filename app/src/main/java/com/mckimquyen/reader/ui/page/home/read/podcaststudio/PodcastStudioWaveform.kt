package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import kotlin.math.PI
import kotlin.math.sin

const val WAVEFORM_TAG = "podcast_studio_waveform"
const val WAVEFORM_BAR_COUNT = 24

/** Height of a bar while nothing is playing, as a fraction of the canvas height. */
const val WAVEFORM_REST_FRACTION = 0.2f

/** Lowest height a bar reaches while animating, so a bar never vanishes. */
const val WAVEFORM_MIN_FRACTION = 0.15f

private const val WAVEFORM_CYCLE_MILLIS = 1_400
private const val BAR_PHASE_SPREAD = 0.37f
private const val BAR_RIPPLES = 2f
private const val HALF = 0.5f
private const val BAR_GAP_FRACTION = 0.35f
private val WAVEFORM_HEIGHT = 56.dp

/**
 * Bar heights for one frame. Pure so it can be tested without a clock: paused bars never depend on [phase].
 * This is a playback-state visualizer, not a measurement of the sound: Android TTS exposes no audio stream.
 */
fun waveformBarFractions(phase: Float, isPlaying: Boolean): List<Float> =
    List(WAVEFORM_BAR_COUNT) { index ->
        if (!isPlaying) {
            WAVEFORM_REST_FRACTION
        } else {
            val angle = 2.0 * PI * (phase * BAR_RIPPLES + index * BAR_PHASE_SPREAD)
            val wave = (sin(angle).toFloat() + 1f) * HALF
            WAVEFORM_MIN_FRACTION + wave * (1f - WAVEFORM_MIN_FRACTION)
        }
    }

@Composable
fun PodcastStudioWaveform(
    isPlaying: Boolean,
    speaker: CommuteSpeaker,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val phase: State<Float> = if (isPlaying) {
        rememberInfiniteTransition(label = "waveform").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(WAVEFORM_CYCLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "waveformPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val color = when (speaker) {
        CommuteSpeaker.ALEX -> MaterialTheme.colorScheme.primary
        CommuteSpeaker.SAM -> MaterialTheme.colorScheme.tertiary
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(WAVEFORM_HEIGHT)
            .testTag(WAVEFORM_TAG)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val fractions = waveformBarFractions(phase.value, isPlaying)
        val slot = size.width / WAVEFORM_BAR_COUNT
        val barWidth = slot * (1f - BAR_GAP_FRACTION)
        fractions.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * slot + (slot - barWidth) * HALF, (size.height - barHeight) * HALF),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * HALF),
            )
        }
    }
}
