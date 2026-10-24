package com.mckimquyen.reader.ui.widget

import com.mckimquyen.reader.domain.repository.ArticleDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleWidgetActionHandlerTest {

    private val articleDao = mockk<ArticleDao>()
    private var notifyCount = 0
    private val handler = ArticleWidgetActionHandler(articleDao) { notifyCount++ }

    @Test
    fun markRead_writesReadStateThenNotifiesWidget() = runBlocking {
        coEvery { articleDao.markAsReadByArticleId(any(), any(), any()) } answers {
            assertEquals(0, notifyCount)
        }

        handler.markRead(accountId = 1, articleId = "art-1")

        coVerifyOrder {
            articleDao.markAsReadByArticleId(1, "art-1", false)
        }
        coVerify(exactly = 1) { articleDao.markAsReadByArticleId(1, "art-1", false) }
        assertEquals(1, notifyCount)
    }

    @Test
    fun markRead_doesNotNotifyWhenWriteFails() = runBlocking {
        coEvery { articleDao.markAsReadByArticleId(any(), any(), any()) } throws IllegalStateException("db closed")

        val failure = runCatching { handler.markRead(accountId = 1, articleId = "art-1") }

        assertEquals(true, failure.isFailure)
        assertEquals(0, notifyCount)
    }

    @Test
    fun parse_validOpenAndMarkRead_returnsMatchingAction() {
        assertEquals(
            WidgetAction.OpenArticle("art-1"),
            ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_OPEN_ARTICLE, "art-1", 1),
        )
        assertEquals(
            WidgetAction.MarkRead(accountId = 2, articleId = "art-2"),
            ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_MARK_READ, "art-2", 2),
        )
    }

    @Test
    fun parse_missingIdOrUnknownAction_returnsNull() {
        assertNull(ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_OPEN_ARTICLE, null, 1))
        assertNull(ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_MARK_READ, "", 1))
        assertNull(ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_MARK_READ, "   ", 1))
        assertNull(ArticleWidgetActionHandler.parse("unknown", "art-1", 1))
        assertNull(ArticleWidgetActionHandler.parse(null, "art-1", 1))
    }

    @Test
    fun parse_markReadWithMissingAccount_returnsNullButOpenStillWorks() {
        assertNull(ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_MARK_READ, "art-1", -1))
        assertEquals(
            WidgetAction.OpenArticle("art-1"),
            ArticleWidgetActionHandler.parse(ArticleWidgetActionHandler.ACTION_OPEN_ARTICLE, "art-1", -1),
        )
    }
}
