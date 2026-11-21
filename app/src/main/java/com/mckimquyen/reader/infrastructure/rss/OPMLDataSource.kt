package com.mckimquyen.reader.infrastructure.rss

import android.content.Context
import be.ceau.opml.OpmlParser
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.spacerDollar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.*
import javax.inject.Inject

class OPMLDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Throws(Exception::class)
    suspend fun parseFileInputStream(
        inputStream: InputStream,
        defaultGroup: Group,
    ): List<GroupWithFeed> {
        return withContext(ioDispatcher) {
            val accountId = context.currentAccountId
            val opml = OpmlParser().parse(inputStream)
            val groupWithFeedList = mutableListOf<GroupWithFeed>().also {
                it.addGroup(defaultGroup)
            }

            opml.body.outlines.forEach {
                // Only feeds
                if (it.subElements.isEmpty()) {
                    // It's a empty group
                    if (it.attributes["xmlUrl"] == null) {
                        if (!it.attributes["isDefault"].toBoolean()) {
                            groupWithFeedList.addGroup(
                                Group(
                                    id = context.currentAccountId.spacerDollar(
                                        UUID.randomUUID().toString()
                                    ),
                                    name = it.attributes["title"] ?: it.text!!,
                                    accountId = accountId,
                                )
                            )
                        }
                    } else {
                        groupWithFeedList.addFeedToDefault(
                            Feed(
                                id = context.currentAccountId.spacerDollar(
                                    UUID.randomUUID().toString()
                                ),
                                name = it.attributes["title"] ?: it.text!!,
                                url = it.attributes["xmlUrl"]!!,
                                groupId = defaultGroup.id,
                                accountId = accountId,
                                isNotification = it.attributes["isNotification"].toBoolean(),
                                isFullContent = it.attributes["isFullContent"].toBoolean(),
                            ).withSocialSource(it.attributes)
                        )
                    }
                } else {
                    var groupId = defaultGroup.id
                    if (!it.attributes["isDefault"].toBoolean()) {
                        groupId =
                            context.currentAccountId.spacerDollar(UUID.randomUUID().toString())
                        groupWithFeedList.addGroup(
                            Group(
                                id = groupId,
                                name = it.attributes["title"] ?: it.text!!,
                                accountId = accountId,
                            )
                        )
                    }
                    it.subElements.forEach { outline ->
                        groupWithFeedList.addFeed(
                            Feed(
                                id = context.currentAccountId.spacerDollar(
                                    UUID.randomUUID().toString()
                                ),
                                name = outline.attributes["title"] ?: outline.text!!,
                                url = outline.attributes["xmlUrl"]!!,
                                groupId = groupId,
                                accountId = accountId,
                                isNotification = outline.attributes["isNotification"].toBoolean(),
                                isFullContent = outline.attributes["isFullContent"].toBoolean(),
                            ).withSocialSource(outline.attributes)
                        )
                    }
                }
            }
            groupWithFeedList
        }
    }

    private fun MutableList<GroupWithFeed>.addGroup(group: Group) {
        add(GroupWithFeed(group = group, feeds = mutableListOf()))
    }

    private fun MutableList<GroupWithFeed>.addFeed(feed: Feed) {
        last().feeds.add(feed)
    }

    private fun MutableList<GroupWithFeed>.addFeedToDefault(feed: Feed) {
        first().feeds.add(feed)
    }

    /**
     * What the file says about a social source (Bluesky, Nostr), kept ONLY when it matches the address. The file is untrusted: a kind that does
     * not fit its address, an unknown kind or a wrong stored address is dropped and the source stays a plain RSS one (the behaviour before this).
     */
    private fun Feed.withSocialSource(attributes: Map<String, String>): Feed {
        val kind = attributes[ATTR_SOURCE_TYPE]
        val stored = attributes[ATTR_SOURCE_URL]
        val fits: (String) -> Boolean = when (kind) {
            Feed.BLUESKY_SOURCE_TYPE -> ::isBlueskyAddress
            Feed.NOSTR_SOURCE_TYPE -> ::isNostrAddress
            else -> return this
        }
        if (!fits(url)) return this
        sourceType = kind
        sourceUrl = stored?.takeIf(fits)
        return this
    }

    companion object {
        private const val ATTR_SOURCE_TYPE = "sourceType"
        private const val ATTR_SOURCE_URL = "sourceUrl"

        private fun isBlueskyAddress(address: String): Boolean =
            address.startsWith(BlueskyFeedMapper.FEED_URL_PREFIX) &&
                BlueskyFeedMapper.isActor(address.removePrefix(BlueskyFeedMapper.FEED_URL_PREFIX))

        private fun isNostrAddress(address: String): Boolean =
            address.startsWith(NostrFeedMapper.FEED_URL_PREFIX) &&
                NostrKeys.npubToHex(address.removePrefix(NostrFeedMapper.FEED_URL_PREFIX)) != null

        /** The two extra attributes of an exported outline: only for a Bluesky or Nostr source, so every other outline is written as before. */
        fun socialAttributes(feed: Feed): Map<String, String> {
            if (feed.sourceType != Feed.BLUESKY_SOURCE_TYPE && feed.sourceType != Feed.NOSTR_SOURCE_TYPE) return emptyMap()
            val stored = feed.sourceUrl
            return if (stored == null) mapOf(ATTR_SOURCE_TYPE to feed.sourceType)
            else mapOf(ATTR_SOURCE_TYPE to feed.sourceType, ATTR_SOURCE_URL to stored)
        }
    }
}
