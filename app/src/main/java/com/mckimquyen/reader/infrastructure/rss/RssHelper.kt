package com.mckimquyen.reader.infrastructure.rss

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Log
import com.google.gson.Gson
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.spacerDollar
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import java.io.InputStream
import java.util.*
import javax.inject.Inject

/**
 * Some operations on RSS.
 */
class RssHelper @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
) {

    @Throws(Exception::class)
    suspend fun searchFeed(feedLink: String): FeedWithArticle {
        return withContext(ioDispatcher) {
            val accountId = context.currentAccountId
            val syndFeed = SyndFeedInput().build(XmlReader(inputStream(okHttpClient, feedLink)))
            val feed = Feed(
                id = accountId.spacerDollar(UUID.randomUUID().toString()),
                name = syndFeed.title!!,
                url = feedLink,
                groupId = "",
                accountId = accountId,
            )
            val list = syndFeed.entries.map { article(feed, context.currentAccountId, it) }
            FeedWithArticle(feed, list)
        }
    }

    @Throws(Exception::class)
    suspend fun parseFullContent(link: String, title: String): String {
        return withContext(ioDispatcher) {
            val response = response(okHttpClient, link)
            val content = response.body.string()
            val readability4J = Readability4JExtended(link, content)
            val articleContent = readability4J.parse().articleContent
            if (articleContent == null) {
                ""
            } else {
                val h1Element = articleContent.selectFirst("h1")
                if (h1Element != null && h1Element.hasText() && h1Element.text() == title) {
                    h1Element.remove()
                }
                articleContent.toString()
            }
        }
    }

    suspend fun queryRssXml(
        feed: Feed,
        latestLink: String?,
    ): List<Article> =
        try {
            val accountId = context.currentAccountId
            inputStream(okHttpClient, feed.url).use {
                SyndFeedInput().apply { isPreserveWireFeed = true }
                    .build(XmlReader(it))
                    .entries
                    .asSequence()
                    .takeWhile { latestLink == null || latestLink != it.link }
                    .map { article(feed, accountId, it) }
                    .toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RLog", "queryRssXml[${feed.name}]: ${e.message}")
            listOf()
        }

    private fun article(
        feed: Feed,
        accountId: Int,
        syndEntry: SyndEntry,
    ): Article {
        val desc = syndEntry.description?.value
        val content = syndEntry.contents
            .takeIf { it.isNotEmpty() }
            ?.let { it.joinToString("\n") { it.value } }
        Log.i(
            "RLog",
            "request rss:\n" +
                    "name: ${feed.name}\n" +
                    "feedUrl: ${feed.url}\n" +
                    "url: ${syndEntry.link}\n" +
                    "title: ${syndEntry.title}\n" +
                    "desc: ${desc}\n" +
                    "content: ${content}\n"
        )
        return Article(
            id = accountId.spacerDollar(UUID.randomUUID().toString()),
            accountId = accountId,
            feedId = feed.id,
            date = syndEntry.publishedDate ?: syndEntry.updatedDate ?: Date(),
            title = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(syndEntry.title.toString(), Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(syndEntry.title.toString()).toString()
            },
            author = syndEntry.author,
            rawDescription = (content ?: desc) ?: "",
            shortDescription = (Readability4JExtended("", desc ?: content ?: "")
                .parse().textContent ?: "")
                .take(110)
                .trim(),
            fullContent = content,
            img = extractThumbnail(syndEntry, (content ?: desc) ?: ""),
            link = syndEntry.link ?: "",
            updateAt = Date(),
        )
    }

    /**
     * Extracts article thumbnail prioritizing high-res `<enclosure>` and `<media:content>` / `<media:thumbnail>`
     * before falling back to `<img>` in HTML content/description, filtering out 1x1 tracking pixels.
     */
    fun extractThumbnail(syndEntry: SyndEntry?, rawDescription: String): String? {
        if (syndEntry != null) {
            // 1. Enclosure (<enclosure type="image/..." url="..."/>)
            val enclosureImg = syndEntry.enclosures.orEmpty().firstNotNullOfOrNull { enc ->
                val isImage = enc.type?.startsWith("image", ignoreCase = true) == true ||
                        isImageExtension(enc.url)
                if (isImage && isValidThumbnailUrl(enc.url)) enc.url else null
            }
            if (enclosureImg != null) return enclosureImg

            // 2. Media module (<media:content url="..."/> or <media:thumbnail url="..."/>)
            val mediaImg = extractMediaThumbnail(syndEntry.foreignMarkup.orEmpty())
            if (mediaImg != null) return mediaImg
        }

        // 3. Fallback to HTML description / content <img>
        return findImg(rawDescription)
    }

    fun findImg(rawDescription: String): String? {
        // From: https://gitlab.com/spacecowboy/Feeder
        // Using negative lookahead to skip data: urls, being inline base64
        // And capturing original quote to use as ending quote
        val regex = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)
        // Base64 encoded images can be quite large - and crash database cursors
        return regex.find(rawDescription)?.groupValues?.get(2)?.takeIf { isValidThumbnailUrl(it) }
    }

    fun isValidThumbnailUrl(url: String?, width: Int? = null, height: Int? = null): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return false
        if (url.startsWith("data:", ignoreCase = true)) return false
        if (width != null && width <= 2) return false
        if (height != null && height <= 2) return false
        val lower = url.lowercase()
        return TRACKING_PATTERNS.none { lower.contains(it) }
    }

    private fun isImageExtension(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore("?").substringBefore("#").lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                clean.endsWith(".png") || clean.endsWith(".webp") ||
                clean.endsWith(".avif") || clean.endsWith(".gif")
    }

    private fun extractMediaThumbnail(elements: List<org.jdom2.Element>): String? {
        for (element in elements) {
            val name = element.name.lowercase()
            val prefix = element.namespacePrefix.lowercase()
            val uri = element.namespaceURI.lowercase()
            val isMedia = prefix == "media" || uri.contains("search.yahoo.com/mrss")

            if (isMedia) {
                if (name == "group") {
                    val childImg = extractMediaThumbnail(element.children)
                    if (childImg != null) return childImg
                } else if (name == "content" || name == "thumbnail") {
                    val url = element.getAttributeValue("url")
                    val width = element.getAttributeValue("width")?.toIntOrNull()
                    val height = element.getAttributeValue("height")?.toIntOrNull()
                    val medium = element.getAttributeValue("medium")
                    val type = element.getAttributeValue("type")

                    val isImage = (medium?.equals("image", ignoreCase = true) == true) ||
                            (type?.startsWith("image", ignoreCase = true) == true) ||
                            isImageExtension(url) ||
                            name == "thumbnail"

                    if (isImage && isValidThumbnailUrl(url, width, height)) {
                        return url
                    }
                }
            }
        }
        return null
    }

    private companion object {
        val TRACKING_PATTERNS = listOf(
            "1x1",
            "pixel",
            "beacon",
            "/open.gif",
            "/feed-burner",
            "statcounter",
            "doubleclick",
            "tracking",
        )
    }

    @Throws(Exception::class)
    suspend fun queryRssIcon(
        feedDao: FeedDao,
        feed: Feed,
    ) {
        withContext(ioDispatcher) {
            val host = runCatching { Uri.parse(feed.url).host }.getOrNull()
            if (!host.isNullOrBlank()) {
                val iconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=128"
                saveRssIcon(feedDao, feed, iconUrl)
            }
        }
    }

    private suspend fun saveRssIcon(feedDao: FeedDao, feed: Feed, iconLink: String) {
        feedDao.update(
            feed.apply {
                icon = iconLink
            }
        )
    }

    private suspend fun inputStream(
        client: OkHttpClient,
        url: String,
    ): InputStream = response(client, url).body.byteStream()

    private suspend fun response(
        client: OkHttpClient,
        url: String,
    ) = client.newCall(Request.Builder().url(url).build()).executeAsync()
}
