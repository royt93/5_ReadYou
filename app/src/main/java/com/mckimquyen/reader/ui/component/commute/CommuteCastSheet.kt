package com.mckimquyen.reader.ui.component.commute

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.ui.ext.findActivity
import com.roy.sdkadbmob.AdManager

@Composable
fun CommuteCastDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: CommuteCastViewModel = hiltViewModel(),
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Scrim to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() }
            )

            // Sheet Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* absorb taps inside sheet */ },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                CommuteCastContent(
                    viewModel = viewModel,
                    onClose = onDismiss,
                )
            }
        }
    }
}

@Composable
fun CommuteCastContent(
    viewModel: CommuteCastViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val uiState by viewModel.uiState.collectAsState()

    // Tự động chuẩn bị nếu chưa có episode
    LaunchedEffect(Unit) {
        if (uiState.playerState.episode == null && !uiState.isLoading) {
            viewModel.prepareOrPlay()
        }
    }

    CommuteCastUi(
        uiState = uiState,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipNext = { viewModel.skipNext() },
        onSkipPrevious = { viewModel.skipPrevious() },
        onSeekTo = { viewModel.seekTo(it) },
        onUnlockDeepDive = { viewModel.unlockDeepDiveSuccess() },
        onRetry = { viewModel.prepareOrPlay(forceRegenerate = true) },
        onClose = onClose,
        modifier = modifier,
        activity = activity,
    )
}

@Composable
fun CommuteCastUi(
    uiState: CommuteUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onUnlockDeepDive: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    activity: Activity? = null,
) {
    val playerState = uiState.playerState
    val episode = playerState.episode
    val listState = rememberLazyListState()

    // Tự động cuộn theo câu đối đáp đang phát
    LaunchedEffect(playerState.currentDialogueIndex) {
        if (episode != null && playerState.currentDialogueIndex in episode.dialogues.indices) {
            listState.animateScrollToItem(playerState.currentDialogueIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.commute_cast_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (episode?.isDeepDive == true) "🎙️ Deep Dive Edition" else stringResource(R.string.commute_cast_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Host Cards Row (Alex & Sam)
        val currentSpeaker = playerState.currentDialogue?.speaker
        val isPlaying = playerState.isPlaying

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HostCard(
                modifier = Modifier.weight(1f),
                speaker = CommuteSpeaker.ALEX,
                name = stringResource(R.string.commute_host_alex),
                roleDesc = "Giọng nam phân tích",
                isSpeaking = isPlaying && currentSpeaker == CommuteSpeaker.ALEX,
                primaryColor = MaterialTheme.colorScheme.primary,
            )
            HostCard(
                modifier = Modifier.weight(1f),
                speaker = CommuteSpeaker.SAM,
                name = stringResource(R.string.commute_host_sam),
                roleDesc = "Giọng nữ phản biện",
                isSpeaking = isPlaying && currentSpeaker == CommuteSpeaker.SAM,
                primaryColor = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Loading or Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.commute_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (episode == null || episode.dialogues.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.commute_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else {
                // Live Transcript LazyColumn
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(episode.dialogues) { index, dialogue ->
                        val isCurrent = index == playerState.currentDialogueIndex
                        DialogueBubble(
                            dialogue = dialogue,
                            isCurrent = isCurrent,
                            onClick = { onSeekTo(index) },
                        )
                    }

                    // Deep Dive Unlock Banner at bottom of list if not yet Deep Dive
                    if (episode.isDeepDive != true) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            DeepDiveBanner(
                                activity = activity,
                                onUnlocked = onUnlockDeepDive,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Player Controls & Progress
        if (episode != null && episode.dialogues.isNotEmpty()) {
            val totalDialogues = episode.dialogues.size
            val progress = (playerState.currentDialogueIndex + 1).toFloat() / totalDialogues.toFloat()

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress indicator
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Đoạn ${playerState.currentDialogueIndex + 1} / $totalDialogues",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (playerState.isPlaying) "Đang phát..." else "Đã dừng",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (playerState.isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Control Buttons: Previous - Play/Pause - Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        enabled = playerState.currentDialogueIndex > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // Play / Pause prominent Button
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    IconButton(
                        onClick = onSkipNext,
                        enabled = playerState.currentDialogueIndex < totalDialogues - 1,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    speaker: CommuteSpeaker,
    name: String,
    roleDesc: String,
    isSpeaking: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSpeaking) primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isSpeaking) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSpeaking) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = if (isSpeaking) 1.0f else 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (speaker == CommuteSpeaker.ALEX) "A" else "S",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isSpeaking) "🎙️ Đang nói..." else roleDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSpeaking) primaryColor.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DialogueBubble(
    dialogue: CommuteDialogue,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val isAlex = dialogue.speaker == CommuteSpeaker.ALEX
    val backgroundColor = if (isCurrent) {
        if (isAlex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val contentColor = if (isCurrent) {
        if (isAlex) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val speakerBadgeColor = if (isAlex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
        border = if (isCurrent) BorderStroke(1.5.dp, speakerBadgeColor) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(speakerBadgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAlex) "Alex" else "Sam",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = speakerBadgeColor,
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Đang phát",
                        style = MaterialTheme.typography.labelSmall,
                        color = speakerBadgeColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dialogue.text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DeepDiveBanner(
    activity: Activity?,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val isVip = remember { AdManager.isVipByKeyActive() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CommuteCast Deep Dive (15 phút)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Phân tích chuyên sâu 10 tin tức nóng nhất từ nguồn RSS của bạn với Alex & Sam.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (isVip) {
                        Toast.makeText(context, context.getString(R.string.commute_deepdive_unlocked), Toast.LENGTH_SHORT).show()
                        onUnlocked()
                    } else {
                        val act = activity ?: return@Button
                        AdManager.showRewarded(act) { earned ->
                            if (earned) {
                                Toast.makeText(context, context.getString(R.string.commute_deepdive_unlocked), Toast.LENGTH_SHORT).show()
                                onUnlocked()
                            } else {
                                AdManager.showInterstitial(act) {
                                    Toast.makeText(context, context.getString(R.string.vip_rewarded_not_earned), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = if (isVip) "🎙️ Nghe Deep Dive (Đặc quyền VIP)" else stringResource(R.string.commute_unlock_deepdive),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
