package com.mckimquyen.reader.domain.repository

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleMeta
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.article.ArticleBody
import com.mckimquyen.reader.domain.model.article.ArticleBodyLengths
import com.mckimquyen.reader.domain.model.article.ArticleBodyChunk
import java.io.ByteArrayOutputStream
import com.mckimquyen.reader.domain.model.statistics.FeedReadCount
import com.mckimquyen.reader.domain.model.statistics.ReadingDayCount
import com.mckimquyen.reader.domain.model.feed.ImportantNum
import java.util.*

@Dao
interface ArticleDao {

    @Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId AND isUnread = 1 ORDER BY date DESC LIMIT :limit")
    suspend fun queryUnreadArticleList(accountId: Int, limit: Int = 5): List<ArticleListItem>

    @Transaction
    @Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId ORDER BY date DESC LIMIT :limit")
    suspend fun queryRecentArticleListWithFeed(accountId: Int, limit: Int = 150): List<ArticleListItemWithFeed>

    @Transaction
    @Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId AND id = :articleId")
    suspend fun queryArticleListWithFeedById(accountId: Int, articleId: String): ArticleListItemWithFeed?

    @Query("UPDATE article SET fullContent = :content WHERE accountId = :accountId AND id = :articleId")
    suspend fun updateFullContent(accountId: Int, articleId: String, content: String)

    @Query("SELECT length(CAST(rawDescription AS BLOB)) AS rawBytes, length(CAST(fullContent AS BLOB)) AS fullBytes FROM article WHERE accountId = :accountId AND id = :articleId")
    suspend fun queryArticleBodyLengths(accountId: Int, articleId: String): ArticleBodyLengths?

    @Query("SELECT substr(CAST(rawDescription AS BLOB), :offset, :size) AS raw, substr(CAST(fullContent AS BLOB), :offset, :size) AS `full` FROM article WHERE accountId = :accountId AND id = :articleId")
    suspend fun queryArticleBodyChunk(accountId: Int, articleId: String, offset: Int, size: Int): ArticleBodyChunk?

    /** Đọc dưới cùng transaction, mỗi cursor chỉ chứa hai đoạn byte hữu hạn. */
    @Transaction
    suspend fun queryArticleBody(accountId: Int, articleId: String): ArticleBody? {
        val lengths = queryArticleBodyLengths(accountId, articleId) ?: return null
        val raw = ByteArrayOutputStream()
        val full = lengths.fullBytes?.let { ByteArrayOutputStream() }
        var offset = 0
        val totalBytes = maxOf(lengths.rawBytes, lengths.fullBytes ?: 0)
        while (offset < totalBytes) {
            val chunk = checkNotNull(queryArticleBodyChunk(accountId, articleId, offset + SQLITE_FIRST_BYTE, ARTICLE_BODY_CHUNK_BYTES)) {
                "Article disappeared while reading its body: $articleId"
            }
            raw.write(chunk.raw)
            chunk.full?.let { full?.write(it) }
            offset += ARTICLE_BODY_CHUNK_BYTES
        }
        check(raw.size() == lengths.rawBytes && full?.size() == lengths.fullBytes) { "Incomplete article body: $articleId" }
        return ArticleBody(articleId, raw.toString(Charsets.UTF_8.name()), full?.toString(Charsets.UTF_8.name()))
    }

    companion object {
        private const val SQLITE_FIRST_BYTE = 1
        const val ARTICLE_BODY_CHUNK_BYTES = 64 * 1024
        const val ARTICLE_LIST_COLUMNS = "id, date, title, author, shortDescription, img, link, feedId, accountId, isUnread, isStarred, isReadLater, updateAt, aiSummary, readAt, audioUrl, audioMime, audioDurationSec"
    }

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        AND article.isUnread = :isUnread
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByGroupIdWhenIsUnread(
        accountId: Int,
        query: String,
        groupId: String,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        AND article.isStarred = :isStarred
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByGroupIdWhenIsStarred(
        accountId: Int,
        query: String,
        groupId: String,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByGroupIdWhenAll(
        accountId: Int,
        query: String,
        groupId: String,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId = :feedId
        AND article.isUnread = :isUnread
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByFeedIdWhenIsUnread(
        accountId: Int,
        query: String,
        feedId: String,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId = :feedId
        AND article.isStarred = :isStarred
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByFeedIdWhenIsStarred(
        accountId: Int,
        query: String,
        feedId: String,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.feedId = :feedId
        ORDER BY article.date DESC
        """
    )
    fun searchArticleByFeedIdWhenAll(
        accountId: Int,
        query: String,
        feedId: String,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.isUnread = :isUnread
        ORDER BY article.date DESC
        """
    )
    fun searchArticleWhenIsUnread(
        accountId: Int,
        query: String,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        AND article.isStarred = :isStarred
        ORDER BY article.date DESC
        """
    )
    fun searchArticleWhenIsStarred(
        accountId: Int,
        query: String,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        JOIN article_fts ON article.rowid = article_fts.docid
        WHERE article_fts MATCH :query
        AND article.accountId = :accountId
        ORDER BY article.date DESC
        """
    )
    fun searchArticleWhenAll(
        accountId: Int,
        query: String,
    ): PagingSource<Int, ArticleWithFeed>

    @Query(
        """
        DELETE FROM article
        WHERE accountId = :accountId
        AND updateAt < :before
        AND isUnread = 0
        AND isStarred = 0
        """
    )
    suspend fun deleteAllArchivedBeforeThan(
        accountId: Int,
        before: Date,
    )

    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE accountId = :accountId
        AND date < :before
        """
    )
    suspend fun markAllAsRead(
        accountId: Int,
        isUnread: Boolean,
        before: Date,
    )

    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE feedId IN (
            SELECT id FROM feed 
            WHERE groupId = :groupId
        )
        AND accountId = :accountId
        AND date < :before
        """
    )
    suspend fun markAllAsReadByGroupId(
        accountId: Int,
        groupId: String,
        isUnread: Boolean,
        before: Date,
    )

    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE feedId = :feedId
        AND accountId = :accountId
        AND date < :before
        """
    )
    suspend fun markAllAsReadByFeedId(
        accountId: Int,
        feedId: String,
        isUnread: Boolean,
        before: Date,
    )

    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE id = :articleId
        AND accountId = :accountId
        """
    )
    suspend fun markAsReadByArticleId(
        accountId: Int,
        articleId: String,
        isUnread: Boolean,
    )

    @Query(
        """
        UPDATE article SET isStarred = :isStarred 
        WHERE id = :articleId
        AND accountId = :accountId
        """
    )
    suspend fun markAsStarredByArticleId(
        accountId: Int,
        articleId: String,
        isStarred: Boolean,
    )

    @Query(
        """
        UPDATE article SET aiSummary = :aiSummary 
        WHERE id = :articleId
        """
    )
    suspend fun updateAiSummary(
        articleId: String,
        aiSummary: String?,
    )

    @Query(
        """
        SELECT aiSummary FROM article 
        WHERE id = :articleId 
        LIMIT 1
        """
    )
    suspend fun queryAiSummaryByArticleId(
        articleId: String,
    ): String?

    @Query(
        """
        DELETE FROM article
        WHERE accountId = :accountId
        AND feedId = :feedId
        """
    )
    suspend fun deleteByFeedId(accountId: Int, feedId: String)

    @Query(
        """
        DELETE FROM article
        WHERE id IN (
            SELECT a.id FROM article AS a, feed AS b, `group` AS c
            WHERE a.accountId = :accountId
            AND a.feedId = b.id
            AND b.groupId = c.id
            AND c.id = :groupId
        )
        """
    )
    suspend fun deleteByGroupId(accountId: Int, groupId: String)

    @Query(
        """
        DELETE FROM article
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteByAccountId(accountId: Int)

    @Transaction
    @Query(
        """
        SELECT COUNT(*) AS important, a.feedId, b.groupId
        FROM article AS a
        LEFT JOIN feed AS b
        ON a.feedId = b.id
        WHERE a.isUnread = :isUnread
        AND a.accountId = :accountId
        GROUP BY a.feedId
        """
    )
    fun queryImportantCountWhenIsUnread(
        accountId: Int,
        isUnread: Boolean,
    ): Flow<List<ImportantNum>>

    @Transaction
    @Query(
        """
        SELECT COUNT(*) AS important, a.feedId, b.groupId 
        FROM article AS a
        LEFT JOIN feed AS b
        ON a.feedId = b.id
        WHERE a.isStarred = :isStarred
        AND a.accountId = :accountId
        GROUP BY a.feedId
        """
    )
    fun queryImportantCountWhenIsStarred(
        accountId: Int,
        isStarred: Boolean,
    ): Flow<List<ImportantNum>>

    @Transaction
    @Query(
        """
        SELECT COUNT(*) AS important, a.feedId, b.groupId 
        FROM article AS a
        LEFT JOIN feed AS b
        ON a.feedId = b.id
        WHERE a.accountId = :accountId
        GROUP BY a.feedId
        """
    )
    fun queryImportantCountWhenIsAll(accountId: Int): Flow<List<ImportantNum>>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsAll(accountId: Int): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE isStarred = :isStarred 
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsStarred(
        accountId: Int,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE isUnread = :isUnread 
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsUnread(
        accountId: Int,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.aiSummary, a.readAt, 
        a.audioUrl, a.audioMime, a.audioDurationSec 
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsAll(
        accountId: Int,
        groupId: String,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.aiSummary, a.readAt, 
        a.audioUrl, a.audioMime, a.audioDurationSec 
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.isStarred = :isStarred
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsStarred(
        accountId: Int,
        groupId: String,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.aiSummary, a.readAt, 
        a.audioUrl, a.audioMime, a.audioDurationSec 
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.isUnread = :isUnread
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsUnread(
        accountId: Int,
        groupId: String,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE feedId = :feedId
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsAll(
        accountId: Int,
        feedId: String,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * from article 
        WHERE feedId = :feedId 
        AND isStarred = :isStarred
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsStarred(
        accountId: Int,
        feedId: String,
        isStarred: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE feedId = :feedId 
        AND isUnread = :isUnread
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsUnread(
        accountId: Int,
        feedId: String,
        isUnread: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.aiSummary, a.readAt, 
        a.audioUrl, a.audioMime, a.audioDurationSec 
        FROM article AS a LEFT JOIN feed AS b 
        ON a.feedId = b.id
        WHERE a.feedId = :feedId 
        AND a.accountId = :accountId
        ORDER BY date DESC LIMIT 1
        """
    )
    suspend fun queryLatestByFeedId(accountId: Int, feedId: String): Article?

    @Query(
        """
        SELECT * from article 
        WHERE link = :link
        AND feedId = :feedId
        AND accountId = :accountId
        """
    )
    suspend fun queryArticleByLink(
        link: String,
        feedId: String,
        accountId: Int,
    ): Article?

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE id = :id
        """
    )
    suspend fun queryById(id: String): ArticleWithFeed?

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleMetadataAll(
        accountId: Int,
    ): List<ArticleMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg article: Article)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnConflictIgnore(vararg article: Article)

    @Insert
    suspend fun insertList(articles: List<Article>)

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        ORDER BY date DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun queryArticlesWithFeedPage(accountId: Int, limit: Int, offset: Int): List<ArticleWithFeed>

    /** Bài đã đọc, xuất bản cùng ngày/tháng (giờ local) ở các năm trước. `monthDay` dạng `MM-dd`. */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
          AND isUnread = 0
          AND strftime('%m-%d', date / 1000, 'unixepoch', 'localtime') = :monthDay
          AND strftime('%Y', date / 1000, 'unixepoch', 'localtime') < :currentYear
        ORDER BY date DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun queryOnThisDay(
        accountId: Int,
        monthDay: String,
        currentYear: String,
        limit: Int = 5,
    ): List<ArticleWithFeed>

    /** Ghi nhận thời điểm đọc đầu tiên; nếu đã có thì giữ nguyên (COALESCE). */
    @Query(
        """
        UPDATE article
        SET readAt = COALESCE(readAt, :readAt)
        WHERE id = :articleId AND accountId = :accountId
        """
    )
    suspend fun markOpened(accountId: Int, articleId: String, readAt: Long)

    /** Số bài đọc theo từng ngày local trong cửa sổ từ :sinceMillis. */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', readAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
        FROM article
        WHERE accountId = :accountId AND readAt IS NOT NULL AND readAt >= :sinceMillis
        GROUP BY day
        ORDER BY day ASC
        """
    )
    suspend fun queryReadingDayCounts(accountId: Int, sinceMillis: Long): List<ReadingDayCount>

    /** Tổng số bài đã được ghi nhận đọc của tài khoản. */
    @Query("SELECT COUNT(*) FROM article WHERE accountId = :accountId AND readAt IS NOT NULL")
    suspend fun queryTotalRead(accountId: Int): Int

    /** Top nguồn tin có nhiều bài đọc nhất, tie-break theo tên nguồn. */
    @Query(
        """
        SELECT f.id AS feedId, f.name AS feedName, COUNT(*) AS count
        FROM article a JOIN feed f ON a.feedId = f.id
        WHERE a.accountId = :accountId AND a.readAt IS NOT NULL
        GROUP BY f.id
        ORDER BY count DESC, f.name ASC
        LIMIT :limit
        """
    )
    suspend fun queryTopFeeds(accountId: Int, limit: Int = 5): List<FeedReadCount>

    @Update
    suspend fun update(vararg article: Article)

    @Transaction
    suspend fun insertListIfNotExist(articles: List<Article>): List<Article> {
        return articles.mapNotNull {
            if (queryArticleByLink(
                    link = it.link,
                    feedId = it.feedId,
                    accountId = it.accountId
                ) == null
            ) it else null
        }.also {
            insertList(it)
        }
    }
}
