package com.mckimquyen.reader.ui.page.home.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R

import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseExtensibleVisibility
import com.mckimquyen.reader.ui.ext.share
import com.mckimquyen.reader.ui.page.common.RouteName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    navController: NavHostController,
    isShow: Boolean,
    title: String? = "",
    link: String? = "",
    isPlayingAudio: Boolean = false,
    isZenAudioPlaying: Boolean = false,
    showSummary: Boolean = false,
    onPlayAudio: () -> Unit = {},
    onZenAudio: () -> Unit = {},
    onRsvpReading: () -> Unit = {},
    onSummary: () -> Unit = {},
    onMindMap: () -> Unit = {},
    onDeepRead: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        contentAlignment = Alignment.TopCenter
    ) {
        BaseExtensibleVisibility(visible = isShow) {
            TopAppBar(
                title = {},
                modifier = Modifier,
                windowInsets = TopAppBarDefaults.windowInsets,
                navigationIcon = {
                    FeedbackIconButton(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface
                    ) {
                        onClose()
                    }
                },
                actions = {
                    if (showSummary) {
                        FeedbackIconButton(
                            modifier = Modifier.size(22.dp),
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.summary_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        ) {
                            android.util.Log.d("roy93~AI", "TopBar: Summary clicked")
                            onSummary()
                        }
                    }
                    // RSVP Speed Reading
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = Icons.Rounded.ElectricBolt,
                        contentDescription = stringResource(R.string.zen_speed_reading),
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        onRsvpReading()
                    }
                    // Zen Ambient Audio
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = if (isZenAudioPlaying) Icons.Rounded.GraphicEq else Icons.Outlined.Headphones,
                        contentDescription = stringResource(R.string.zen_ambient_audio),
                        tint = if (isZenAudioPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ) {
                        onZenAudio()
                    }
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = if (isPlayingAudio) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.read_aloud),
                        tint = MaterialTheme.colorScheme.onSurface
                    ) {
                        android.util.Log.d("roy93~", "TopBar: PlayAudio clicked")
                        onPlayAudio()
                    }
                    Box {
                        FeedbackIconButton(
                            modifier = Modifier.size(22.dp),
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurface
                        ) {
                            menuExpanded = true
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.style)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Palette,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(RouteName.READING_PAGE_STYLE) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    context.share(
                                        title
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { it + "\n" } + link
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mindmap_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountTree,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMindMap()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.deep_read_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Forum,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeepRead()
                                }
                            )
                        }
                    }
                }, colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    }
}
