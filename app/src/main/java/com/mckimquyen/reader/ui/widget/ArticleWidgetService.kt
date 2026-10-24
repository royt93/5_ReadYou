package com.mckimquyen.reader.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import android.widget.RemoteViewsService
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.EntryPointAccessors

class ArticleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entry = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        return ArticleWidgetFactory(applicationContext, entry.articleDao(), entry.feedDao()) {
            applicationContext.currentAccountId
        }
    }
}

internal class ArticleWidgetFactory(
    private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val currentAccountId: () -> Int,
) : RemoteViewsService.RemoteViewsFactory {
    private data class WidgetRow(val article: ArticleListItem, val feedName: String)
    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val accountId = currentAccountId()
        val loadedRows = runBlocking(Dispatchers.IO) {
            articleDao.queryUnreadArticleList(accountId, MAX_ARTICLES).map { article ->
                WidgetRow(article, feedDao.queryById(article.feedId)?.name ?: context.getString(R.string.unknown))
            }
        }
        rows = loadedRows
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount() = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val row = rows.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_article_item).apply {
            setTextViewText(R.id.widget_item_title, row.article.title)
            setTextViewText(R.id.widget_item_feed, row.feedName)
            fun fill(action: String) = Intent().apply {
                putExtra(ArticleWidgetActionHandler.EXTRA_ACTION, action)
                putExtra(ArticleWidgetActionHandler.EXTRA_ARTICLE_ID, row.article.id)
                putExtra(ArticleWidgetActionHandler.EXTRA_ACCOUNT_ID, row.article.accountId)
            }
            setOnClickFillInIntent(
                R.id.widget_item_container,
                fill(ArticleWidgetActionHandler.ACTION_OPEN_ARTICLE),
            )
            setOnClickFillInIntent(
                R.id.widget_item_mark_read,
                fill(ArticleWidgetActionHandler.ACTION_MARK_READ),
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false

    companion object {
        const val MAX_ARTICLES = 5
    }
}
