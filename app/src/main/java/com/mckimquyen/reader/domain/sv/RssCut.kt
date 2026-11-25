package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed

/**
 * An RSS read returns the whole feed; what is kept is decided by DATE against the newest stored article, not by position in the document. Position was wrong for a feed that
 * pins an old entry on top (the entries after it were never fetched), yet some cut is still needed: an article the user archived away (Keep archived) must not come back
 * because the feed still lists it. Entries as new as the newest stored one are kept (insertListIfNotExist drops the link it already has). A stored date in the future, from a
 * feed with a wrong pubDate, is capped at now, otherwise it would hide every real new entry.
 */
object RssCut {
    fun onlyNotOlderThanStored(feed: Feed, latest: Article?, fetched: List<Article>, nowMillis: Long = System.currentTimeMillis()): List<Article> {
        if (latest == null || feed.sourceType != Feed.RSS_SOURCE_TYPE) return fetched
        val reference = minOf(latest.date.time, nowMillis)
        return fetched.filter { it.date.time >= reference }
    }
}
