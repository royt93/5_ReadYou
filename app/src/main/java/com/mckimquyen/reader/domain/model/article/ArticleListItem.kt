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
)
