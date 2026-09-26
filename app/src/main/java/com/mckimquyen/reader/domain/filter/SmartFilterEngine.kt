package com.mckimquyen.reader.domain.filter

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import com.mckimquyen.reader.domain.model.filter.SmartFilterRule

object SmartFilterEngine {

    fun apply(article: Article, rules: List<SmartFilterRule>): Article {
        var unread = article.isUnread
        var starred = article.isStarred

        for (rule in rules) {
            if (!rule.isEnabled || rule.keyword.isBlank()) continue
            val text = when (rule.targetField) {
                FilterTargetField.TITLE -> article.title
                FilterTargetField.AUTHOR -> article.author.orEmpty()
            }
            if (text.contains(rule.keyword.trim(), ignoreCase = true)) {
                when (rule.action) {
                    FilterAction.MARK_READ -> unread = false
                    FilterAction.STAR -> starred = true
                }
            }
        }

        return if (unread != article.isUnread || starred != article.isStarred) {
            article.copy(isUnread = unread, isStarred = starred)
        } else {
            article
        }
    }

    fun applyAll(articles: List<Article>, rules: List<SmartFilterRule>): List<Article> {
        if (rules.none { it.isEnabled }) return articles
        return articles.map { apply(it, rules) }
    }
}
