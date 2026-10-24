package com.mckimquyen.reader.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.android.MainActivity

class ArticleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_article_layout).apply {
                setRemoteAdapter(
                    R.id.widget_article_list,
                    Intent(context, ArticleWidgetService::class.java).apply {
                        // Mỗi instance có identity riêng để hệ thống không dùng chung adapter.
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    },
                )
                setEmptyView(R.id.widget_article_list, R.id.widget_empty_view)
                setPendingIntentTemplate(R.id.widget_article_list, createItemClickTemplate(context))
                setOnClickPendingIntent(R.id.widget_header, createOpenAppIntent(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_article_list)
        }
    }

    companion object {
        /**
         * Template phải MUTABLE: hệ thống gộp fillInIntent của từng hàng vào template; với
         * FLAG_IMMUTABLE các extra (action/articleId/accountId) sẽ bị mất. Chỉ trampoline nội bộ
         * (`exported=false`) nhận intent này nên chấp nhận được.
         */
        internal fun createItemClickTemplate(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_ITEM_CLICK,
                Intent(context, ArticleWidgetActionActivity::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun createOpenAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private const val REQUEST_ITEM_CLICK = 1
        private const val REQUEST_OPEN_APP = 2
    }
}
