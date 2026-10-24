package com.mckimquyen.reader.ui.widget

import com.mckimquyen.reader.domain.repository.ArticleDao

internal sealed class WidgetAction {
    data class OpenArticle(val articleId: String) : WidgetAction()
    data class MarkRead(val accountId: Int, val articleId: String) : WidgetAction()
}

internal class ArticleWidgetActionHandler(
    private val articleDao: ArticleDao,
    private val notifyWidget: () -> Unit,
) {
    suspend fun markRead(accountId: Int, articleId: String) {
        require(accountId >= 0 && articleId.isNotBlank())
        articleDao.markAsReadByArticleId(accountId, articleId, isUnread = false)
        notifyWidget()
    }

    companion object {
        const val ACTION_OPEN_ARTICLE = "open_article"
        const val ACTION_MARK_READ = "mark_read"
        const val EXTRA_ACTION = "widget.action"
        const val EXTRA_ARTICLE_ID = "widget.article_id"
        const val EXTRA_ACCOUNT_ID = "widget.account_id"

        fun parse(action: String?, articleId: String?, accountId: Int): WidgetAction? {
            if (articleId.isNullOrBlank()) return null
            return when (action) {
                ACTION_OPEN_ARTICLE -> WidgetAction.OpenArticle(articleId)
                ACTION_MARK_READ -> if (accountId >= 0) WidgetAction.MarkRead(accountId, articleId) else null
                else -> null
            }
        }
    }
}
