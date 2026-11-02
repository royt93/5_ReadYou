package com.mckimquyen.reader.domain.curated

import android.content.Context
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wires the curated bundles to the app: reads the bundled asset, decides whether the current account may subscribe,
 * collects the URLs the account already has, and runs [BundleSubscriber] with the real repository. Only the Local
 * account is supported because synced accounts (Fever, Google Reader…) subscribe through their own server API.
 */
class CuratedBundleService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountService: AccountSv,
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val addedRssSourceDao: AddedRssSourceDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun load(): List<CuratedBundle> = withContext(ioDispatcher) {
        CuratedBundleLoader.load(context.assets.open(ASSET_NAME))
    }

    suspend fun canSubscribe(): Boolean =
        accountService.getCurrentAccount().type.id == AccountType.Local.id

    /** Tracked Add Sources URLs plus every feed already in the account, since feeds can be added by other screens. */
    suspend fun existingUrls(): Set<String> = withContext(ioDispatcher) {
        val account = accountService.getCurrentAccount()
        val tracked = addedRssSourceDao.getAddedSourcesByAccount(account.id ?: 0).first().map { it.url }
        val subscribed = rssService.get(AccountType.Local.id).pullFeeds().first()
            .flatMap { group -> group.feeds.map { it.url } }
        (tracked + subscribed).toSet()
    }

    suspend fun subscribe(
        bundle: CuratedBundle,
        groupName: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): BundleSubscribeResult {
        if (!canSubscribe()) return BundleSubscribeResult(added = 0, skippedExisting = 0, failed = 0)
        val account = accountService.getCurrentAccount()
        val gateway = RssBundleFeedGateway(
            repository = rssService.get(AccountType.Local.id),
            rssHelper = rssHelper,
            addedRssSourceDao = addedRssSourceDao,
            accountId = account.id ?: 0,
        )
        return BundleSubscriber(gateway).subscribe(bundle, existingUrls(), groupName, onProgress)
    }

    private companion object {
        const val ASSET_NAME = "curated_bundles.json"
    }
}
