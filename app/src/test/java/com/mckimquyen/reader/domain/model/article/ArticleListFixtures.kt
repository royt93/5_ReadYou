package com.mckimquyen.reader.domain.model.article

/** Fixture chuyển entity test sẵn có thành projection để mock DAO đúng kiểu trả về. */
fun Article.toListItem() = ArticleListItem(
    id, date, title, author, shortDescription, img, link, feedId, accountId,
    isUnread, isStarred, isReadLater, updateAt, aiSummary, readAt, audioUrl, audioMime, audioDurationSec,
)

fun ArticleWithFeed.toListItemWithFeed() = ArticleListItemWithFeed(article.toListItem(), feed)
