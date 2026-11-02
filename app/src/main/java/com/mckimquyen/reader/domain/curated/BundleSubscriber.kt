package com.mckimquyen.reader.domain.curated

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** What happened when a bundle was subscribed: feeds added, feeds the account already had, feeds that failed. */
data class BundleSubscribeResult(
    val added: Int,
    val skippedExisting: Int,
    val failed: Int,
)

/** The three storage/network operations a bundle subscription needs; kept apart so the logic is testable without Android. */
interface BundleFeedGateway {
    /** Creates a feed group and returns its id. */
    suspend fun createGroup(name: String): String

    suspend fun deleteGroup(groupId: String)

    /** Fetches [source], then subscribes it into [groupId]. Throws when the feed cannot be read or saved. */
    suspend fun subscribeFeed(source: CuratedSource, groupId: String)
}

/**
 * Subscribes the usable sources of a bundle into one new group. A feed that fails is counted and skipped; it never
 * stops the others. If nothing could be added the empty group is removed again, also when the call is cancelled.
 */
class BundleSubscriber(private val gateway: BundleFeedGateway) {

    suspend fun subscribe(
        bundle: CuratedBundle,
        existingUrls: Set<String>,
        groupName: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BundleSubscribeResult {
        val plan = BundleSubscribePlanner.plan(bundle.sources, existingUrls)
        if (plan.toAdd.isEmpty()) {
            return BundleSubscribeResult(added = 0, skippedExisting = plan.alreadyHave.size, failed = 0)
        }

        val groupId = gateway.createGroup(groupName)
        val added = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val done = AtomicInteger(0)
        val total = plan.toAdd.size
        try {
            coroutineScope {
                val permits = Semaphore(MAX_PARALLEL_FEEDS)
                plan.toAdd.map { source ->
                    async {
                        permits.withPermit {
                            try {
                                gateway.subscribeFeed(source, groupId)
                                added.incrementAndGet()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failed.incrementAndGet()
                            }
                            onProgress(done.incrementAndGet(), total)
                        }
                    }
                }.awaitAll()
            }
        } finally {
            if (added.get() == 0) {
                // NonCancellable: a cancelled caller must still be able to remove the group it just created.
                withContext(NonCancellable) { gateway.deleteGroup(groupId) }
            }
        }
        return BundleSubscribeResult(
            added = added.get(),
            skippedExisting = plan.alreadyHave.size,
            failed = failed.get(),
        )
    }

    companion object {
        /** Feeds fetched at the same time; keeps a 9-feed bundle from opening nine connections at once. */
        const val MAX_PARALLEL_FEEDS = 3
    }
}
