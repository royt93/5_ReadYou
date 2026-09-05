package com.mckimquyen.reader.ui.page.home.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    showSummary: Boolean = false,
    onPlayAudio: () -> Unit = {},
    onSummary: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current

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
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = if (isPlayingAudio) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.read_aloud),
                        tint = MaterialTheme.colorScheme.onSurface
                    ) {
                        android.util.Log.d("roy93~", "TopBar: PlayAudio clicked")
                        onPlayAudio()
                    }
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = stringResource(R.string.style),
                        tint = MaterialTheme.colorScheme.onSurface
                    ) {
                        navController.navigate(RouteName.READING_PAGE_STYLE) {
                            launchSingleTop = true
                        }
                    }
                    FeedbackIconButton(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        context.share(title
                            ?.takeIf { it.isNotBlank() }
                            ?.let { it + "\n" } + link
                        )
                    }
                }, colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    }
}
