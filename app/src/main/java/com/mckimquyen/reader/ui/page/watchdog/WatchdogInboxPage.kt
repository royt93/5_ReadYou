package com.mckimquyen.reader.ui.page.watchdog

import android.app.Activity
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.watchdog.WatchdogAlert
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.theme.palette.onLight

/**
 * Màn hình Watchdog Alert Inbox (REEL-06): lịch sử cảnh báo từ khóa, mới nhất trước,
 * phân biệt đã đọc/chưa đọc, mở bài viết gốc, đánh dấu tất cả đã đọc / xóa lịch sử.
 */
@Composable
fun WatchdogInboxPage(
    navController: NavHostController,
    activity: Activity,
    viewModel: WatchdogInboxViewModel = hiltViewModel(),
) {
    val alerts by viewModel.alerts.collectAsState()

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            ) {
                navController.popBackStack()
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.watchdog_inbox_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.watchdog_inbox_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InboxActionChip(
                                testTag = "watchdog_mark_all_read_btn",
                                icon = Icons.Rounded.DoneAll,
                                label = stringResource(R.string.watchdog_mark_all_read),
                                onClick = { viewModel.markAllAsRead() },
                            )
                            InboxActionChip(
                                testTag = "watchdog_clear_all_btn",
                                icon = Icons.Rounded.DeleteSweep,
                                label = stringResource(R.string.watchdog_clear_all),
                                onClick = { viewModel.clearAll() },
                            )
                        }
                    }
                }

                if (alerts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.watchdog_inbox_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }

                items(alerts, key = { it.id }) { alert ->
                    WatchdogAlertCard(
                        alert = alert,
                        onOpen = {
                            viewModel.markAsRead(alert.id)
                            navController.navigate("${com.mckimquyen.reader.ui.page.common.RouteName.READING}/${alert.articleId}") {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    )
}

@Composable
private fun InboxActionChip(
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun WatchdogAlertCard(
    alert: WatchdogAlert,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBg = if (alert.isRead) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("watchdog_alert_${alert.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!alert.isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        text = "🚨 ${alert.keyword}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = DateUtils.getRelativeTimeSpanString(alert.detectedAt).toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.articleTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (alert.isRead) FontWeight.Normal else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.matchedExcerpt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = alert.feedName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
