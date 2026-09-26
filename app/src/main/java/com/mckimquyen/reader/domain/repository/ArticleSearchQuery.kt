package com.mckimquyen.reader.domain.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** Builds safe FTS5 MATCH queries and optional feed/group/read/star filters. */
object ArticleSearchQuery {
    private const val TITLE_WEIGHT = 10.0
    private const val DESCRIPTION_WEIGHT = 2.0
    private const val CONTENT_WEIGHT = 1.0
    private val termRegex = Regex("\"([^\"]+)\"|(\\S+)")

    fun build(
        text: String,
        accountId: Int,
        groupId: String?,
        feedId: String?,
        isStarred: Boolean,
        isUnread: Boolean,
    ): SupportSQLiteQuery {
        val matchQuery = toMatchQuery(text)
        val sql = StringBuilder(
            """
            SELECT article.*
            FROM article
            JOIN article_fts ON article.rowid = article_fts.rowid
            WHERE article_fts MATCH ?
            AND article.accountId = ?
            """.trimIndent()
        )
        val args = mutableListOf<Any>(matchQuery, accountId)

        when {
            groupId != null -> {
                sql.append(" AND article.feedId IN (SELECT id FROM feed WHERE groupId = ?)")
                args += groupId
            }
            feedId != null -> {
                sql.append(" AND article.feedId = ?")
                args += feedId
            }
        }
        when {
            isStarred -> {
                sql.append(" AND article.isStarred = 1")
            }
            isUnread -> {
                sql.append(" AND article.isUnread = 1")
            }
        }
        sql.append(
            " ORDER BY bm25(article_fts, $TITLE_WEIGHT, $DESCRIPTION_WEIGHT, $CONTENT_WEIGHT), article.date DESC"
        )
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    /**
     * Quotes every user term so FTS operators cannot alter query semantics. Unquoted terms use
     * prefix matching; explicitly quoted input remains a phrase. unicode61 removes diacritics,
     * allowing e.g. "cong nghe" to match "công nghệ".
     */
    fun toMatchQuery(input: String): String {
        val terms = termRegex.findAll(input.trim()).mapNotNull { match ->
            val phrase = match.groups[1]?.value
            val word = match.groups[2]?.value
            when {
                !phrase.isNullOrBlank() -> quote(phrase.trim())
                !word.isNullOrBlank() -> "${quote(word.trim())}*"
                else -> null
            }
        }.toList()
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" AND ") ?: quote("")
    }

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
