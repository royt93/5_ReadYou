package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import com.mckimquyen.reader.infrastructure.audio.CommuteVoiceMode
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge

const val PODCAST_STUDIO_SHEET_TAG = "podcast_studio_sheet"
const val PODCAST_CONSENT_DIALOG_TAG = "podcast_consent_dialog"
const val PODCAST_CONFLICT_DIALOG_TAG = "podcast_conflict_dialog"
const val PODCAST_CONFLICT_CONFIRM_TAG = "podcast_conflict_confirm"
const val PODCAST_PROGRESS_TAG = "podcast_progress"
const val PODCAST_PLAY_PAUSE_TAG = "podcast_play_pause"

private val MIN_TOUCH_TARGET = 48.dp

@Composable
fun PodcastStudioSheet(
    state: PodcastStudioUiState,
    onDismiss: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onConfirmConflict: () -> Unit,
    onDismissConflict: () -> Unit,
    onToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        PodcastStudioUiState.Idle -> Unit
        is PodcastStudioUiState.AwaitingConsent -> ConsentDialog(state.article, onConsent)
        is PodcastStudioUiState.ConfirmConflict -> ConflictDialog(state.article, onConfirmConflict, onDismissConflict)
        PodcastStudioUiState.Generating -> ProgressSheet(onDismiss)
        PodcastStudioUiState.Error -> ErrorSheet(onDismiss, onRetry)
        is PodcastStudioUiState.Ready -> ReadySheet(
            ready = state,
            onDismiss = onDismiss,
            onToggle = onToggle,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeek = onSeek,
        )
    }
}

@Composable
private fun ConsentDialog(article: Article, onConsent: (Boolean) -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(PODCAST_CONSENT_DIALOG_TAG),
        onDismissRequest = { onConsent(false) },
        title = { Text(stringResource(R.string.podcast_studio_consent_title)) },
        text = { Text(stringResource(R.string.podcast_studio_consent_message)) },
        confirmButton = {
            Button(onClick = { onConsent(true) }) {
                Text(stringResource(R.string.podcast_studio_consent_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConsent(false) }) {
                Text(stringResource(R.string.podcast_studio_consent_decline))
            }
        },
    )
}

@Composable
private fun ConflictDialog(article: Article, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(PODCAST_CONFLICT_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.podcast_studio_conflict_title)) },
        text = { Text(stringResource(R.string.podcast_studio_conflict_message)) },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag(PODCAST_CONFLICT_CONFIRM_TAG)) {
                Text(stringResource(R.string.podcast_studio_play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun BaseSheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        DialogEdgeToEdge()
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    // Swallows taps so they do not reach the scrim, WITHOUT clickable: clickable merges every
                    // descendant into one semantics node, which hid the sheet's own children from tests and
                    // from accessibility services.
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ProgressSheet(onDismiss: () -> Unit) {
    BaseSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).testTag(PODCAST_PROGRESS_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.commute_tts_preparing), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorSheet(onDismiss: () -> Unit, onRetry: () -> Unit) {
    BaseSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.feed_autopilot_error), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ReadySheet(
    ready: PodcastStudioUiState.Ready,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val playerState = ready.playerState
    val currentSpeaker = playerState.currentDialogue?.speaker ?: CommuteSpeaker.ALEX
    val isPlaying = playerState.isPlaying

    LaunchedEffect(playerState.currentDialogueIndex) {
        if (ready.episode.dialogues.isNotEmpty()) {
            listState.animateScrollToItem(playerState.currentDialogueIndex.coerceIn(0, ready.episode.dialogues.lastIndex))
        }
    }

    BaseSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(PODCAST_STUDIO_SHEET_TAG),
        ) {
            HeaderRow(title = ready.episode.title, onDismiss = onDismiss)
            Spacer(modifier = Modifier.height(8.dp))
            BadgesRow(voiceMode = playerState.voiceMode, source = ready.source)
            Spacer(modifier = Modifier.height(12.dp))

            val waveformDesc = if (isPlaying) {
                val speakerLabel = stringResource(
                    if (currentSpeaker == CommuteSpeaker.ALEX) R.string.podcast_studio_host_alex else R.string.podcast_studio_host_sam,
                )
                stringResource(R.string.podcast_studio_waveform_playing, speakerLabel)
            } else {
                stringResource(R.string.podcast_studio_waveform_paused)
            }
            PodcastStudioWaveform(isPlaying = isPlaying, speaker = currentSpeaker, contentDescription = waveformDesc)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(ready.episode.dialogues) { index, dialogue ->
                    DialogueRow(
                        dialogue = dialogue,
                        isCurrent = index == playerState.currentDialogueIndex,
                        onClick = { onSeek(index) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ControlsRow(
                isPlaying = isPlaying,
                onToggle = onToggle,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
            )
        }
    }
}

@Composable
private fun HeaderRow(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.podcast_studio_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
        }
    }
}

@Composable
private fun BadgesRow(voiceMode: CommuteVoiceMode, source: PodcastScriptSource) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val voiceText = stringResource(
            if (voiceMode == CommuteVoiceMode.REAL_DUAL) R.string.commute_voice_mode_dual else R.string.commute_voice_mode_simulated,
        )
        val sourceText = stringResource(
            if (source == PodcastScriptSource.GEMINI) R.string.podcast_studio_badge_source_gemini else R.string.podcast_studio_badge_source_heuristic,
        )
        Text(voiceText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(sourceText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DialogueRow(dialogue: CommuteDialogue, isCurrent: Boolean, onClick: () -> Unit) {
    val isAlex = dialogue.speaker == CommuteSpeaker.ALEX
    val speakerName = stringResource(if (isAlex) R.string.podcast_studio_host_alex else R.string.podcast_studio_host_sam)
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                speakerName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isAlex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(dialogue.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ControlsRow(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.podcast_studio_previous))
        }
        Spacer(modifier = Modifier.width(16.dp))
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(56.dp).clip(CircleShape).testTag(PODCAST_PLAY_PAUSE_TAG),
        ) {
            val icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
            val desc = stringResource(if (isPlaying) R.string.podcast_studio_pause else R.string.podcast_studio_play)
            Icon(icon, contentDescription = desc, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onSkipNext, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.podcast_studio_next))
        }
    }
}
