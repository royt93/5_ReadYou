package com.mckimquyen.reader.domain.model.article

import java.util.Date

/** Metadata cho danh sách/chọn bài; không materialize body vào CursorWindow. */
data class ArticleListItem(
    val id: String,
    val date: Date,
    val title: String,
    val author: String?,
    val shortDescription: String,
    val img: String?,
    val link: String,
    val feedId: String,
    val accountId: Int,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val isReadLater: Boolean,
    val updateAt: Date?,
    val aiSummary: String?,
    val readAt: Long?,
    val audioUrl: String?,
    val audioMime: String?,
    val audioDurationSec: Int?,
) {
    /** Adapter cho thuật toán cũ; không dùng kết quả thiếu body để ghi đè entity trong DB. */
    fun toArticle(body: ArticleBody? = null): Article {
        require(body == null || body.articleId == id) { "Body belongs to another article" }
        return Article(
            id = id, date = date, title = title, author = author,
            rawDescription = body?.rawDescription.orEmpty(), fullContent = body?.fullContent,
            shortDescription = shortDescription, img = img, link = link, feedId = feedId,
            accountId = accountId, isUnread = isUnread, isStarred = isStarred, isReadLater = isReadLater,
            updateAt = updateAt, aiSummary = aiSummary, readAt = readAt,
            audioUrl = audioUrl, audioMime = audioMime, audioDurationSec = audioDurationSec,
        )
    }
}
