package com.mckimquyen.reader.domain.model.digest

import com.mckimquyen.reader.domain.model.article.Article
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class DigestArticleSelectorTest {
    @Test
    fun selectNewestUnreadArticles_skipsBlankContentDeduplicatesAndCapsResults() {
        val articles = (1..25).map { index ->
            Article(
                id = "article-$index",
                date = Date(index.toLong()),
                title = "Title $index",
                rawDescription = "Body $index",
                shortDescription = "Short $index",
                fullContent = null,
                link = "https://example.test/$index",
                feedId = "feed",
                accountId = 1,
                isUnread = true,
            )
        } + Article(
            id = "blank",
            date = Date(30L),
            title = "Blank article",
            rawDescription = " ",
            shortDescription = "",
            fullContent = "",
            link = "https://example.test/blank",
            feedId = "feed",
            accountId = 1,
            isUnread = true,
        ) + Article(
            id = "article-25",
            date = Date(31L),
            title = "Duplicate",
            rawDescription = "duplicate body",
            shortDescription = "duplicate",
            fullContent = null,
            link = "https://example.test/duplicate",
            feedId = "feed",
            accountId = 1,
            isUnread = false,
        )

        val selected = DigestArticleSelector.select(articles)

        assertEquals(20, selected.size)
        assertEquals("article-25", selected.first().id)
        assertEquals("article-6", selected.last().id)
        assertEquals(selected.size, selected.map { it.id }.toSet().size)
        assertEquals("Body 25", selected.first().content)
    }

    @Test
    fun select_noUnreadWithContent_returnsEmpty() {
        val readArticle = Article(
            id = "read",
            date = Date(1L),
            title = "Read",
            rawDescription = "Body",
            shortDescription = "Body",
            link = "https://example.test/read",
            feedId = "feed",
            accountId = 1,
            isUnread = false,
        )

        assertEquals(emptyList<DigestArticleSource>(), DigestArticleSelector.select(listOf(readArticle)))
    }
}
