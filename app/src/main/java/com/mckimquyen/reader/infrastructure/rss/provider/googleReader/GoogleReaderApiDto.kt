package com.mckimquyen.reader.infrastructure.rss.provider.googleReader

import androidx.annotation.Keep

object GoogleReaderApiDto {
    // subscription/list?output=json
    @Keep
    data class SubscriptionList(
        val subscriptions: List<SubscriptionItem>? = null,
    )

    @Keep
    data class SubscriptionItem(
        val id: String? = null,
        val title: String? = null,
        val categories: List<CategoryItem>? = null,
        val url: String? = null,
        val htmlUrl: String? = null,
        val iconUrl: String? = null,
    )

    @Keep
    data class CategoryItem(
        val id: String? = null,
        val label: String? = null,
    )

    // unread-count?output=json
    @Keep
    data class UnreadCount(
        val max: Int? = null,
        val unreadcounts: List<UnreadCountItem>? = null,
    )

    @Keep
    data class UnreadCountItem(
        val id: String? = null,
        val count: Int? = null,
        val newestItemTimestampUsec: String? = null,
    )

    // tag/list?output=json
    @Keep
    data class TagList(
        val tags: List<TagItem>? = null,
    )

    @Keep
    data class TagItem(
        val id: String? = null,
        val type: String? = null,
    )

    @Keep
    // stream/contents/reading-list?output=json
    data class ReadingList(
        val id: String? = null,
        val updated: Long? = null,
        val items: List<Item>? = null,
        val continuation: String? = null,
    )

    @Keep
    data class Item(
        val id: String? = null,
        val crawlTimeMsec: String? = null,
        val timestampUsec: String? = null,
        val published: Long? = null,
        val title: String? = null,
        val summary: Summary? = null,
        val categories: List<String>? = null,
        val origin: List<OriginItem>? = null,
        val author: String? = null,
    )

    @Keep
    data class Summary(
        val content: String? = null,
        val canonical: List<CanonicalItem>? = null,
        val alternate: List<CanonicalItem>? = null,
    )

    @Keep
    data class CanonicalItem(
        val href: String? = null,
    )

    @Keep
    data class OriginItem(
        val streamId: String? = null,
        val title: String? = null,
    )

    // stream/items/ids?output=json
    @Keep
    data class ItemRefs(
        val itemRefs: List<ItemRef>? = null,
        val continuation: String? = null,
    )

    @Keep
    data class ItemRef(
        val id: String? = null,
    )
}
