package com.mckimquyen.reader.ui.page.home.flow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mckimquyen.reader.R

@Composable
internal fun FlowActionsMenu(
    expanded: Boolean,
    showMarkAllAsRead: Boolean,
    onDismiss: () -> Unit = {},
    onMarkAllAsRead: (() -> Unit)? = null,
    onDailyDigest: () -> Unit = {},
    onReels: () -> Unit = {},
    onCommuteCast: () -> Unit = {},
    onBrainRpg: () -> Unit = {},
    onWatchdog: () -> Unit = {},
    onSmartCollections: () -> Unit = {},
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (showMarkAllAsRead) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_all_as_read)) },
                leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                onClick = { onDismiss(); onMarkAllAsRead?.invoke() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.smart_collections_title)) },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            onClick = { onDismiss(); onSmartCollections() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.daily_digest_title)) },
            leadingIcon = { Icon(Icons.Rounded.Article, contentDescription = null) },
            onClick = { onDismiss(); onDailyDigest() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reels_title)) },
            leadingIcon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
            onClick = { onDismiss(); onReels() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.commute_cast_title)) },
            leadingIcon = { Icon(Icons.Outlined.Podcasts, contentDescription = null) },
            onClick = { onDismiss(); onCommuteCast() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.brain_rpg_title)) },
            leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
            onClick = { onDismiss(); onBrainRpg() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.watchdog_title)) },
            leadingIcon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
            onClick = { onDismiss(); onWatchdog() },
        )
    }
}
