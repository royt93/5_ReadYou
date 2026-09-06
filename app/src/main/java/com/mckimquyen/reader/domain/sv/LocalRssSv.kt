package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import javax.inject.Inject

class LocalRssSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    workManager: WorkManager,
    watchdogManager: WatchdogManager,
) : AbstractRssRepository(
    context,
    accountDao,
    articleDao,
    groupDao,
    feedDao,
    workManager,
    rssHelper,
    notificationHelper,
    ioDispatcher,
    defaultDispatcher,
    watchdogManager,
)
