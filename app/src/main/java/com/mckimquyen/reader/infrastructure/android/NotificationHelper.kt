package com.mckimquyen.reader.infrastructure.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.ui.page.common.ExtraName
import com.mckimquyen.reader.ui.page.common.NotificationGroupName
import java.util.*
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

    fun notify(feedWithArticle: FeedWithArticle) {
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
                        Random().nextInt() + article.id.hashCode(),
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

            notificationManager.notify(
                Random().nextInt() + article.id.hashCode(),
                builder.build().apply {
                    flags = Notification.FLAG_AUTO_CANCEL
                }
            )
        }

        if (feedWithArticle.articles.size > 1) {
            notificationManager.notify(
                Random().nextInt() + feedWithArticle.feed.id.hashCode(),
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

        notificationManager.notify(COMMUTE_NOTIFICATION_ID, notification)
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

        notificationManager.notify(ZEN_DAILY_EDITION_NOTIFICATION_ID, notification)
    }

    fun notifyWatchdogAlert(article: Article, keyword: String, feedName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ExtraName.ARTICLE_ID, article.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            Random().nextInt() + article.id.hashCode(),
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

        notificationManager.notify(article.id.hashCode(), notification)
    }

    companion object {
        const val WATCHDOG_CHANNEL_ID = "watchdog_keyword_alert_channel"
        const val COMMUTE_CHANNEL_ID = "commute_cast_channel"
        const val COMMUTE_NOTIFICATION_ID = 9988
        const val EXTRA_START_COMMUTE = "extra_start_commute"
        const val ZEN_DAILY_EDITION_CHANNEL_ID = "zen_daily_edition_channel"
        const val ZEN_DAILY_EDITION_NOTIFICATION_ID = 9977
    }
}
