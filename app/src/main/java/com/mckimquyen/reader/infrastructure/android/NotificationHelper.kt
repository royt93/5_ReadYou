package com.mckimquyen.reader.infrastructure.android

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.ui.page.common.ExtraName
import com.mckimquyen.reader.ui.page.common.NotificationGroupName
import javax.inject.Inject

class NotificationHelper @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(
                NotificationChannel(
                    NotificationGroupName.ARTICLE_UPDATE,
                    NotificationGroupName.ARTICLE_UPDATE,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            createNotificationChannel(
                NotificationChannel(
                    COMMUTE_CHANNEL_ID,
                    "CommuteCast Morning Radio",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Bản tin phát thanh buổi sáng hàng ngày"
                }
            )
            createNotificationChannel(
                NotificationChannel(
                    ZEN_DAILY_EDITION_CHANNEL_ID,
                    "Daily Focus Edition",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Bản tin tạp chí định giờ sáng và tối"
                }
            )
            createNotificationChannel(
                NotificationChannel(
                    WATCHDOG_CHANNEL_ID,
                    context.getString(R.string.watchdog_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.watchdog_channel_desc)
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }

    /**
     * False when the user denied POST_NOTIFICATIONS (Android 13+) or turned notifications off.
     */
    fun canPostNotifications(): Boolean = notificationManager.areNotificationsEnabled()

    // Every notification goes through here so none is posted without permission (the OS would drop it silently).
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Skip notification $id: notifications are not allowed")
            return
        }
        notificationManager.notify(id, notification)
    }

    fun notify(feedWithArticle: FeedWithArticle) {
        if (!canPostNotifications()) return
        notificationManager.createNotificationChannelGroup(
            NotificationChannelGroup(
                feedWithArticle.feed.id,
                feedWithArticle.feed.name
            )
        )
        feedWithArticle.articles.forEach { article ->
            val builder = NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(
                    (BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.ic_notification
                    ))
                )
                .setContentTitle(article.title)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        articleNotificationId(article.id),
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(
                                ExtraName.ARTICLE_ID,
                                article.id
                            )
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .setGroup(feedWithArticle.feed.id)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(article.shortDescription)
                        .setSummaryText(feedWithArticle.feed.name)
                )

            post(
                articleNotificationId(article.id),
                builder.build().apply {
                    flags = Notification.FLAG_AUTO_CANCEL
                }
            )
        }

        if (feedWithArticle.articles.size > 1) {
            post(
                feedSummaryNotificationId(feedWithArticle.feed.id),
                NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(
                        (BitmapFactory.decodeResource(
                            context.resources,
                            R.drawable.ic_notification
                        ))
                    )
                    .setStyle(
                        NotificationCompat.InboxStyle()
                            .setSummaryText(feedWithArticle.feed.name)
                    )
                    .setGroup(feedWithArticle.feed.id)
                    .setGroupSummary(true)
                    .build()
            )
        }
    }

    fun notifyCommuteCast(episode: com.mckimquyen.reader.domain.model.commute.CommuteEpisode) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_START_COMMUTE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            10099,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("☕ ${episode.title}")
            .setContentText("Alex & Sam đã chuẩn bị 5 điểm tin nổi bật sáng nay cho bạn!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        post(COMMUTE_NOTIFICATION_ID, notification)
    }

    fun notifyDailyEdition(title: String, body: String, unreadCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ZEN_DAILY_EDITION_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ZEN_DAILY_EDITION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        post(ZEN_DAILY_EDITION_NOTIFICATION_ID, notification)
    }

    fun notifyWatchdogAlert(article: Article, keyword: String, feedName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ExtraName.ARTICLE_ID, article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            watchdogNotificationId(article.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WATCHDOG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚨 [$keyword] ${article.title}")
            .setContentText(article.shortDescription.ifBlank { article.title })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(article.shortDescription.ifBlank { article.title })
                    .setSummaryText(feedName)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        post(watchdogNotificationId(article.id), notification)
    }

    companion object {
        const val WATCHDOG_CHANNEL_ID = "watchdog_keyword_alert_channel"
        const val COMMUTE_CHANNEL_ID = "commute_cast_channel"
        const val COMMUTE_NOTIFICATION_ID = 9988
        const val EXTRA_START_COMMUTE = "extra_start_commute"
        const val ZEN_DAILY_EDITION_CHANNEL_ID = "zen_daily_edition_channel"
        const val ZEN_DAILY_EDITION_NOTIFICATION_ID = 9977
        private const val TAG = "NotificationHelper"
        private const val FEED_SUMMARY_ID_PREFIX = "feed_summary:"
        private const val WATCHDOG_ID_PREFIX = "watchdog:"

        // Stable IDs: re-notifying the same article replaces its notification instead of stacking
        // duplicates, and the ID can be recomputed later to update or cancel it.
        fun articleNotificationId(articleId: String): Int = articleId.hashCode()

        fun feedSummaryNotificationId(feedId: String): Int = (FEED_SUMMARY_ID_PREFIX + feedId).hashCode()

        // Prefixed so a watchdog alert never replaces the regular notification of the same article.
        fun watchdogNotificationId(articleId: String): Int = (WATCHDOG_ID_PREFIX + articleId).hashCode()
    }
}
