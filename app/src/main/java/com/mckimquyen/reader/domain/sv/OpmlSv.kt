package com.mckimquyen.reader.domain.sv

import android.content.Context
import be.ceau.opml.OpmlWriter
import be.ceau.opml.entity.Body
import be.ceau.opml.entity.Head
import be.ceau.opml.entity.Opml
import be.ceau.opml.entity.Outline
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.*
import javax.inject.Inject

/**
 * Supports import and export from OPML files.
 */
class OpmlSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val accountDao: AccountDao,
    private val rssService: RssSv,
    private val OPMLDataSource: OPMLDataSource,
) {

    /**
     * Imports OPML file.
     *
     * @param [inputStream] input stream of OPML file
     */
    @Throws(Exception::class)
    suspend fun saveToDatabase(inputStream: InputStream) {
        val defaultGroup = requireDefaultGroup(context.currentAccountId)
        val groupWithFeedList =
            OPMLDataSource.parseFileInputStream(inputStream, defaultGroup)
        groupWithFeedList.forEach { groupWithFeed ->
            if (groupWithFeed.group != defaultGroup) {
                groupDao.insert(groupWithFeed.group)
            }
            val repeatList = mutableListOf<Feed>()
            groupWithFeed.feeds.forEach {
                it.groupId = groupWithFeed.group.id
                if (rssService.get().isFeedExist(it.url)) {
                    repeatList.add(it)
                }
            }
            feedDao.insertList((groupWithFeed.feeds subtract repeatList.toSet()).toList())
        }
    }

    /**
     * Exports OPML file.
     */
    @Throws(Exception::class)
    suspend fun saveToString(accountId: Int): String {
        val defaultGroup = requireDefaultGroup(accountId)
        return OpmlWriter().write(
            Opml(
                "2.0",
                Head(
                    accountDao.queryById(accountId)?.name,
                    Date().toString(), null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                ),
                Body(groupDao.queryAllGroupWithFeed(accountId).map {
                    Outline(
                        mapOf(
                            "text" to it.group.name,
                            "title" to it.group.name,
                            "isDefault" to (it.group.id == defaultGroup.id).toString()
                        ),
                        it.feeds.map { feed ->
                            Outline(
                                mapOf(
                                    "text" to feed.name,
                                    "title" to feed.name,
                                    "xmlUrl" to feed.url,
                                    "htmlUrl" to feed.url,
                                    "isNotification" to feed.isNotification.toString(),
                                    "isFullContent" to feed.isFullContent.toString(),
                                ),
                                listOf()
                            )
                        }
                    )
                })
            )
        )
    }

    private suspend fun requireDefaultGroup(accountId: Int) =
        groupDao.queryById(getDefaultGroupId(accountId))
            ?: throw IllegalStateException("Default group not found for account $accountId")

    private fun getDefaultGroupId(accountId: Int): String = accountId.getDefaultGroupId()
}
