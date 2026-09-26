package com.mckimquyen.reader.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSearchQueryTest {

    @Test
    fun toMatchQuery_handlesSingleWordWithPrefix() {
        val query = ArticleSearchQuery.toMatchQuery("android")
        assertEquals("\"android\"*", query)
    }

    @Test
    fun toMatchQuery_handlesMultipleWordsWithAnd() {
        val query = ArticleSearchQuery.toMatchQuery("cong nghe")
        assertEquals("\"cong\"* AND \"nghe\"*", query)
    }

    @Test
    fun toMatchQuery_preservesQuotedPhrases() {
        val query = ArticleSearchQuery.toMatchQuery("\"tri tue nhan tao\" android")
        assertEquals("\"tri tue nhan tao\" AND \"android\"*", query)
    }

    @Test
    fun toMatchQuery_escapesInternalQuotes() {
        val query = ArticleSearchQuery.toMatchQuery("kotlin\"advanced")
        assertTrue(query.contains("\"\""))
    }

    @Test
    fun toMatchQuery_blankInput_returnsEmptyQuotedString() {
        val query = ArticleSearchQuery.toMatchQuery("   ")
        assertEquals("\"\"", query)
    }

    @Test
    fun build_generatesFtsMatchWithBm25Order() {
        val query = ArticleSearchQuery.build(
            text = "kotlin",
            accountId = 1,
            groupId = null,
            feedId = "feed_10",
            isStarred = false,
            isUnread = true,
        )
        val sql = query.sql
        assertTrue(sql.contains("FROM article"))
        assertTrue(sql.contains("JOIN article_fts ON article.rowid = article_fts.rowid"))
        assertTrue(sql.contains("WHERE article_fts MATCH ?"))
        assertTrue(sql.contains("AND article.accountId = ?"))
        assertTrue(sql.contains("AND article.feedId = ?"))
        assertTrue(sql.contains("AND article.isUnread = 1"))
        assertTrue(sql.contains("bm25(article_fts"))
    }

    @Test
    fun build_withGroupId_generatesSubqueryFilter() {
        val query = ArticleSearchQuery.build(
            text = "jetpack",
            accountId = 2,
            groupId = "group_tech",
            feedId = null,
            isStarred = true,
            isUnread = false,
        )
        val sql = query.sql
        assertTrue(sql.contains("article.feedId IN (SELECT id FROM feed WHERE groupId = ?)"))
        assertTrue(sql.contains("AND article.isStarred = 1"))
    }
}
