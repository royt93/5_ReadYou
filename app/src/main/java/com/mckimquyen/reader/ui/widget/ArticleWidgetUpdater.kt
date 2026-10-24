package com.mckimquyen.reader.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.mckimquyen.reader.R

object ArticleWidgetUpdater {

    fun notifyDataChanged(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val component = ComponentName(context, ArticleWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_article_list)
        }
    }
}
