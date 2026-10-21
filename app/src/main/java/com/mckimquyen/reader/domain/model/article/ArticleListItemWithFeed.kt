package com.mckimquyen.reader.domain.model.article

import androidx.room.Embedded
import androidx.room.Relation
import com.mckimquyen.reader.domain.model.feed.Feed

data class ArticleListItemWithFeed(
    @Embedded val article: ArticleListItem,
    @Relation(parentColumn = "feedId", entityColumn = "id") val feed: Feed,
)
