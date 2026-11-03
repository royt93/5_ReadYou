package com.mckimquyen.reader.domain.p2psync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object SyncPackageCodec {
    private val rootKeys = setOf("version", "sourceId", "createdAt", "feeds", "articles")
    private val feedKeys = setOf("url", "name", "groupName")
    private val articleKeys = setOf("feedUrl", "link", "isUnread", "isStarred", "readAt")
    private val sourcePattern = Regex("^[0-9a-f]{32}$")

    fun encode(data: SyncPackage): ByteArray {
        val normalized = normalizeAndValidate(data)
        val root = JSONObject()
            .put("version", normalized.version)
            .put("sourceId", normalized.sourceId)
            .put("createdAt", normalized.createdAt)
            .put("feeds", JSONArray().apply {
                normalized.feeds.sortedBy { it.url }.forEach { feed ->
                    put(JSONObject().put("url", feed.url).put("name", feed.name).put("groupName", feed.groupName))
                }
            })
            .put("articles", JSONArray().apply {
                normalized.articles.sortedWith(compareBy(SyncArticleState::feedUrl, SyncArticleState::link)).forEach { article ->
                    put(JSONObject().put("feedUrl", article.feedUrl).put("link", article.link)
                        .put("isUnread", article.isUnread).put("isStarred", article.isStarred)
                        .put("readAt", article.readAt ?: JSONObject.NULL))
                }
            })
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= SyncLimits.MAX_ENVELOPE_BYTES)
        return bytes
    }

    fun decodeAndValidate(bytes: ByteArray): SyncPackageResult {
        if (bytes.size > SyncLimits.MAX_ENVELOPE_BYTES) return SyncPackageResult.Invalid(SyncPackageError.TOO_LARGE)
        return try {
            SyncPackageResult.Valid(parse(JSONObject(bytes.toString(Charsets.UTF_8))))
        } catch (e: JSONException) {
            SyncPackageResult.Invalid(SyncPackageError.BAD_JSON)
        } catch (e: PackageRejected) {
            SyncPackageResult.Invalid(e.reason)
        } catch (e: IllegalArgumentException) {
            SyncPackageResult.Invalid(SyncPackageError.INVALID_VALUE)
        }
    }

    private fun parse(root: JSONObject): SyncPackage {
        requireKeys(root, rootKeys)
        val version = root.requiredInt("version")
        if (version != PACKAGE_VERSION) reject(SyncPackageError.UNSUPPORTED_VERSION)
        val sourceId = root.requiredString("sourceId")
        if (!sourcePattern.matches(sourceId)) reject(SyncPackageError.INVALID_VALUE)
        val createdAt = root.requiredLong("createdAt")
        if (createdAt < 0) reject(SyncPackageError.INVALID_VALUE)
        val feedsArray = root.requiredArray("feeds")
        val articlesArray = root.requiredArray("articles")
        checkCount(feedsArray.length(), SyncLimits.MAX_FEEDS)
        checkCount(articlesArray.length(), SyncLimits.MAX_ARTICLE_STATES)
        val feeds = List(feedsArray.length()) { index ->
            val obj = feedsArray.getJSONObject(index)
            requireKeys(obj, feedKeys)
            val rawUrl = obj.requiredString("url")
            val url = FeedUrlNormalizer.normalize(rawUrl) ?: reject(SyncPackageError.INVALID_VALUE)
            val name = obj.requiredString("name").validatedText(SyncLimits.MAX_NAME_CHARS)
            val group = obj.requiredString("groupName").validatedText(SyncLimits.MAX_NAME_CHARS)
            SyncFeed(url, name, group)
        }
        val articles = List(articlesArray.length()) { index ->
            val obj = articlesArray.getJSONObject(index)
            requireKeys(obj, articleKeys)
            val rawFeedUrl = obj.requiredString("feedUrl")
            val feedUrl = FeedUrlNormalizer.normalize(rawFeedUrl) ?: reject(SyncPackageError.INVALID_VALUE)
            val link = obj.requiredString("link").validatedUrl(SyncLimits.MAX_ARTICLE_LINK_CHARS)
            val unread = obj.requiredBoolean("isUnread")
            val starred = obj.requiredBoolean("isStarred")
            val readAt = obj.optionalLong("readAt")
            if (readAt != null && readAt < 0) reject(SyncPackageError.INVALID_VALUE)
            SyncArticleState(feedUrl, link, unread, starred, readAt)
        }
        val feedUrls = feeds.map { it.url }
        if (feedUrls.toSet().size != feedUrls.size) reject(SyncPackageError.DUPLICATE_KEY)
        val feedUrlSet = feedUrls.toSet()
        if (articles.any { it.feedUrl !in feedUrlSet }) reject(SyncPackageError.INVALID_VALUE)
        val articleKeys = articles.map { it.feedUrl to it.link }
        if (articleKeys.toSet().size != articleKeys.size) reject(SyncPackageError.DUPLICATE_KEY)
        return SyncPackage(version, sourceId, createdAt, feeds, articles)
    }

    private fun normalizeAndValidate(data: SyncPackage): SyncPackage {
        require(data.version == PACKAGE_VERSION && sourcePattern.matches(data.sourceId) && data.createdAt >= 0)
        require(data.feeds.size <= SyncLimits.MAX_FEEDS && data.articles.size <= SyncLimits.MAX_ARTICLE_STATES)
        val feeds = data.feeds.map { feed ->
            val url = requireNotNull(FeedUrlNormalizer.normalize(feed.url))
            SyncFeed(url, feed.name.validatedText(SyncLimits.MAX_NAME_CHARS), feed.groupName.validatedText(SyncLimits.MAX_NAME_CHARS))
        }
        val articles = data.articles.map { article ->
            val feedUrl = requireNotNull(FeedUrlNormalizer.normalize(article.feedUrl))
            require(article.readAt == null || article.readAt >= 0)
            SyncArticleState(feedUrl, article.link.validatedUrl(SyncLimits.MAX_ARTICLE_LINK_CHARS), article.isUnread, article.isStarred, article.readAt)
        }
        require(feeds.map { it.url }.toSet().size == feeds.size)
        val knownFeeds = feeds.map { it.url }.toSet()
        require(articles.all { it.feedUrl in knownFeeds })
        require(articles.map { it.feedUrl to it.link }.toSet().size == articles.size)
        return SyncPackage(data.version, data.sourceId, data.createdAt, feeds, articles)
    }

    private fun String.validatedText(maxLength: Int): String {
        if (isEmpty() || length > maxLength || any { it.isISOControl() }) reject(SyncPackageError.INVALID_VALUE)
        return this
    }

    private fun String.validatedUrl(maxLength: Int): String {
        if (length > maxLength || any { it.isWhitespace() || it.isISOControl() }) reject(SyncPackageError.INVALID_VALUE)
        val normalized = FeedUrlNormalizer.normalize(this) ?: reject(SyncPackageError.INVALID_VALUE)
        return this.also { if (normalized.isEmpty()) reject(SyncPackageError.INVALID_VALUE) }
    }

    private fun requireKeys(obj: JSONObject, keys: Set<String>) {
        val actual = obj.keys().asSequence().toSet()
        if (actual != keys) reject(SyncPackageError.WRONG_SCHEMA)
    }

    private fun JSONObject.requiredString(key: String): String =
        if (!has(key) || isNull(key) || opt(key) !is String) reject(SyncPackageError.WRONG_SCHEMA) else getString(key)

    private fun JSONObject.requiredInt(key: String): Int =
        if (!has(key) || isNull(key) || opt(key) !is Int) reject(SyncPackageError.WRONG_SCHEMA) else getInt(key)

    private fun JSONObject.requiredLong(key: String): Long =
        if (!has(key) || isNull(key) || (opt(key) !is Int && opt(key) !is Long)) reject(SyncPackageError.WRONG_SCHEMA) else getLong(key)

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        if (!has(key) || isNull(key) || opt(key) !is Boolean) reject(SyncPackageError.WRONG_SCHEMA) else getBoolean(key)

    private fun JSONObject.optionalLong(key: String): Long? {
        if (!has(key)) reject(SyncPackageError.WRONG_SCHEMA)
        if (isNull(key)) return null
        if (opt(key) !is Int && opt(key) !is Long) reject(SyncPackageError.WRONG_SCHEMA)
        return getLong(key)
    }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        if (!has(key) || isNull(key) || opt(key) !is JSONArray) reject(SyncPackageError.WRONG_SCHEMA) else getJSONArray(key)

    private fun checkCount(count: Int, cap: Int) {
        if (count > cap) reject(SyncPackageError.LIMIT_EXCEEDED)
    }

    private fun reject(reason: SyncPackageError): Nothing = throw PackageRejected(reason)
    private class PackageRejected(val reason: SyncPackageError) : RuntimeException()
    private const val PACKAGE_VERSION = 1
}
