package com.mckimquyen.reader.ui.page.home.read.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import com.mckimquyen.reader.domain.model.podcast.SleepTimer
import com.mckimquyen.reader.domain.sv.PodcastPlaybackController

const val PODCAST_BAR_TAG = "podcast_player_bar"
const val PODCAST_TOGGLE_TAG = "podcast_toggle"
const val PODCAST_BACK_TAG = "podcast_skip_back"
const val PODCAST_FORWARD_TAG = "podcast_skip_forward"
const val PODCAST_SEEK_TAG = "podcast_seek"
const val PODCAST_SPEED_TAG = "podcast_speed"
const val PODCAST_SLEEP_TAG = "podcast_sleep"
const val PODCAST_ERROR_TAG = "podcast_error"
const val PODCAST_RETRY_TAG = "podcast_retry"
const val PODCAST_SPEED_ITEM_TAG_PREFIX = "podcast_speed_item_"
const val PODCAST_SLEEP_ITEM_TAG_PREFIX = "podcast_sleep_item_"
const val PODCAST_SLEEP_OFF_ITEM_TAG = "podcast_sleep_item_off"

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private val TOGGLE_ICON_SIZE = 40.dp
private val TOGGLE_BUTTON_SIZE = 52.dp
private val SKIP_BUTTON_SIZE = 44.dp
private val SKIP_ICON_SIZE = 32.dp
private val SKIP_LABEL_SIZE = 9.sp
private val ROW_HEIGHT = 48.dp
private val TIME_MIN_WIDTH = 40.dp
private val BAR_CORNER = 16.dp
private val SLEEP_ICON_SIZE = 22.dp
private val BAR_MARGIN_H = 12.dp
private val BAR_MARGIN_V = 6.dp
private const val INACTIVE_TRACK_ALPHA = 0.3f

internal fun formatPlaybackTime(totalSec: Int): String {
    val sec = totalSec.coerceAtLeast(0)
    val h = sec / SECONDS_PER_HOUR
    val m = sec % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val s = sec % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlayerBar(
    state: PodcastPlaybackState,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onSeek: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: (Int?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying = state.status == PodcastStatus.PLAYING || state.status == PodcastStatus.PREPARING
    val upper = state.durationSec.coerceAtLeast(1)
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val shownPosition = (dragging ?: state.positionSec.toFloat()).coerceIn(0f, upper.toFloat())

    Surface(
        modifier = modifier
            .padding(horizontal = BAR_MARGIN_H, vertical = BAR_MARGIN_V)
            .fillMaxWidth()
            .testTag(PODCAST_BAR_TAG),
        shape = RoundedCornerShape(BAR_CORNER),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            if (state.status == PodcastStatus.ERROR) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp).testTag(PODCAST_ERROR_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.podcast_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag(PODCAST_RETRY_TAG)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            val seekDescription = stringResource(R.string.podcast_seek)
            Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatPlaybackTime(shownPosition.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = TIME_MIN_WIDTH).padding(start = 8.dp),
                )
                Slider(
                    value = shownPosition,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toInt()) }
                        dragging = null
                    },
                    valueRange = 0f..upper.toFloat(),
                    enabled = state.durationSec > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = INACTIVE_TRACK_ALPHA),
                    ),
                    modifier = Modifier.weight(1f).testTag(PODCAST_SEEK_TAG).semantics { contentDescription = seekDescription },
                )
                Text(
                    formatPlaybackTime(state.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = TIME_MIN_WIDTH).padding(end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().height(ROW_HEIGHT + 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val speedDescription = stringResource(R.string.podcast_speed)
                    val speedLabel = "${state.speed}×"
                    TextButton(
                        onClick = { speedMenu = true },
                        modifier = Modifier.testTag(PODCAST_SPEED_TAG).semantics {
                            contentDescription = speedDescription
                            stateDescription = speedLabel
                        },
                    ) {
                        Text(speedLabel, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        PodcastPlaybackController.SPEEDS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("$speed×") },
                                onClick = { speedMenu = false; onSpeed(speed) },
                                modifier = Modifier.testTag(PODCAST_SPEED_ITEM_TAG_PREFIX + speed),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkipButton(
                        isForward = false,
                        onClick = { onSkip(-PodcastPlaybackController.SKIP_INTERVAL_SEC) },
                        tag = PODCAST_BACK_TAG,
                        description = stringResource(R.string.podcast_skip_back),
                    )
                    IconButton(onClick = onToggle, modifier = Modifier.size(TOGGLE_BUTTON_SIZE).testTag(PODCAST_TOGGLE_TAG)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.podcast_pause else R.string.podcast_play),
                            modifier = Modifier.size(TOGGLE_ICON_SIZE),
                        )
                    }
                    SkipButton(
                        isForward = true,
                        onClick = { onSkip(PodcastPlaybackController.SKIP_INTERVAL_SEC) },
                        tag = PODCAST_FORWARD_TAG,
                        description = stringResource(R.string.podcast_skip_forward),
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val sleepDescription = stringResource(R.string.podcast_sleep_timer)
                    val remaining = state.sleepRemainingSec
                    TextButton(
                        onClick = { sleepMenu = true },
                        modifier = Modifier.testTag(PODCAST_SLEEP_TAG).semantics {
                            contentDescription = sleepDescription
                            remaining?.let { stateDescription = formatPlaybackTime(it) }
                        },
                    ) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(SLEEP_ICON_SIZE))
                        if (remaining != null) {
                            Text(
                                formatPlaybackTime(remaining),
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.podcast_sleep_off)) },
                            onClick = { sleepMenu = false; onSleep(null) },
                            modifier = Modifier.testTag(PODCAST_SLEEP_OFF_ITEM_TAG),
                        )
                        SleepTimer.OPTIONS_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(pluralStringResource(R.plurals.podcast_sleep_minutes, minutes, minutes)) },
                                onClick = { sleepMenu = false; onSleep(minutes) },
                                modifier = Modifier.testTag(PODCAST_SLEEP_ITEM_TAG_PREFIX + minutes),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Replay arrow with "15" centred inside; the forward variant mirrors the arrow only, never the digits. */
@Composable
private fun SkipButton(isForward: Boolean, onClick: () -> Unit, tag: String, description: String) {
    IconButton(onClick = onClick, modifier = Modifier.size(SKIP_BUTTON_SIZE).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Replay,
                contentDescription = description,
                modifier = Modifier.size(SKIP_ICON_SIZE).graphicsLayer { scaleX = if (isForward) -1f else 1f },
            )
            Text(
                PodcastPlaybackController.SKIP_INTERVAL_SEC.toString(),
                fontSize = SKIP_LABEL_SIZE,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
