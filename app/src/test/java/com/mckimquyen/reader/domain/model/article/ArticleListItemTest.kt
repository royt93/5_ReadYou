package com.mckimquyen.reader.domain.model.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class ArticleListItemTest {
    private val metadata = ArticleListItem(
        id = "a", date = Date(123), title = "Title", author = "Author", shortDescription = "Summary",
        img = null, link = "https://example.com/a", feedId = "f", accountId = 7,
        isUnread = true, isStarred = true, isReadLater = false, updateAt = null,
        aiSummary = "Tags", readAt = 456, audioUrl = "https://example.com/a.mp3", audioMime = "audio/mpeg", audioDurationSec = 90,
    )

    @Test fun metadataAdapter_preservesFieldsWithoutInventingBody() {
        val article = metadata.toArticle()
        assertEquals(metadata.id, article.id)
        assertEquals(metadata.title, article.title)
        assertEquals(metadata.shortDescription, article.shortDescription)
        assertEquals(metadata.date, article.date)
        assertEquals(metadata.accountId, article.accountId)
        assertEquals(metadata.aiSummary, article.aiSummary)
        assertEquals(metadata.audioUrl, article.audioUrl)
        assertEquals(metadata.readAt, article.readAt)
        assertEquals("", article.rawDescription)
        assertNull(article.fullContent)
    }

    @Test fun bodyAdapter_preservesFullTextAndNullVsEmpty() {
        val article = metadata.toArticle(ArticleBody("a", "Raw", "Full"))
        assertEquals("Raw", article.rawDescription)
        assertEquals("Full", article.fullContent)
        assertEquals("", metadata.toArticle(ArticleBody("a", "Raw", "")).fullContent)
        assertNull(metadata.toArticle(ArticleBody("a", "Raw", null)).fullContent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bodyAdapter_rejectsWrongArticleId() {
        metadata.toArticle(ArticleBody("other", "Raw", "Full"))
    }
}
