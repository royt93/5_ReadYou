package com.mckimquyen.reader.domain.filter

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import com.mckimquyen.reader.domain.model.filter.SmartFilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SmartFilterEngineTest {

    private fun sampleArticle(title: String, author: String? = null) = Article(
        id = "1\$art_1",
        date = Date(),
        title = title,
        author = author,
        rawDescription = "Desc",
        shortDescription = "Short",
        link = "https://example.com/1",
        feedId = "1\$feed_1",
        accountId = 1,
        isUnread = true,
        isStarred = false,
    )

    @Test
    fun apply_markReadWhenTitleMatchesKeyword() {
        val rules = listOf(
            SmartFilterRule(
                targetField = FilterTargetField.TITLE,
                keyword = "[Quảng cáo]",
                action = FilterAction.MARK_READ,
            )
        )
        val article = sampleArticle(title = "Siêu giảm giá [Quảng cáo] cuối tuần")
        val filtered = SmartFilterEngine.apply(article, rules)

        assertFalse("Matched ad rule should be marked as read", filtered.isUnread)
        assertFalse("Starred state should not change", filtered.isStarred)
    }

    @Test
    fun apply_starWhenAuthorMatchesKeyword() {
        val rules = listOf(
            SmartFilterRule(
                targetField = FilterTargetField.AUTHOR,
                keyword = "Paul Graham",
                action = FilterAction.STAR,
            )
        )
        val article = sampleArticle(title = "How to Think for Yourself", author = "Paul Graham")
        val filtered = SmartFilterEngine.apply(article, rules)

        assertTrue("Matched author rule should be starred", filtered.isStarred)
        assertTrue("Unread status should remain unread", filtered.isUnread)
    }

    @Test
    fun apply_disabledRuleHasNoEffect() {
        val rules = listOf(
            SmartFilterRule(
                targetField = FilterTargetField.TITLE,
                keyword = "Sponsored",
                action = FilterAction.MARK_READ,
                isEnabled = false,
            )
        )
        val article = sampleArticle(title = "Sponsored post about tech")
        val filtered = SmartFilterEngine.apply(article, rules)

        assertTrue("Disabled rule should not mark as read", filtered.isUnread)
    }

    @Test
    fun applyAll_processesMultipleArticles() {
        val rules = listOf(
            SmartFilterRule(
                targetField = FilterTargetField.TITLE,
                keyword = "Tài trợ",
                action = FilterAction.MARK_READ,
            ),
            SmartFilterRule(
                targetField = FilterTargetField.TITLE,
                keyword = "AI",
                action = FilterAction.STAR,
            )
        )
        val articles = listOf(
            sampleArticle("Bài viết Tài trợ số 1"),
            sampleArticle("Đột phá mới trong lĩnh vực AI"),
            sampleArticle("Tin tức bình thường"),
        )
        val result = SmartFilterEngine.applyAll(articles, rules)

        assertFalse(result[0].isUnread)
        assertFalse(result[0].isStarred)

        assertTrue(result[1].isUnread)
        assertTrue(result[1].isStarred)

        assertTrue(result[2].isUnread)
        assertFalse(result[2].isStarred)
    }
}
