package com.mckimquyen.reader.domain.p2psync

import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncPlannerTest {
    private var nextId = 0
    private val source = SyncFeed("https://EXAMPLE.org:443/rss/#fragment", "Sender name", "Sender group")

    private fun feed(id: String, url: String, name: String = id, group: String = "default") =
        Feed(id, name, url = url, groupId = group, accountId = 1)

    private fun article(id: String, f: Feed, link: String, unread: Boolean, starred: Boolean, readAt: Long? = null) =
        SyncArticleSnapshot(id, 1, f.url, link, unread, starred, readAt)

    private fun pkg(feeds: List<SyncFeed>, articles: List<SyncArticleState>) =
        SyncPackage(1, "a".repeat(32), 1, feeds, articles)

    private fun plan(
        incoming: SyncPackage,
        feeds: List<Feed> = emptyList(),
        articles: List<SyncArticleSnapshot> = emptyList(),
        account: Int = 1,
        defaultGroup: String = "default",
    ) = LocalSyncPlanner.plan(incoming, feeds, articles, account, defaultGroup) { "id-${nextId++}" }

    @Test fun matchingFeedKeepsReceiverNameGroupAndIcon() {
        val receiver = feed("local-feed", "https://example.org/rss", name = "My local name", group = "my-group")
            .copy(icon = "local-icon")
        val result = plan(pkg(listOf(source), emptyList()), feeds = listOf(receiver))
        assertTrue(result.feedsToAdd.isEmpty())
        assertEquals(1, result.duplicateFeeds)
        assertEquals("My local name", receiver.name)
        assertEquals("my-group", receiver.groupId)
        assertEquals("local-icon", receiver.icon)
    }

    @Test fun missingFeedGoesToDefaultGroupWithoutFetchingAnything() {
        val added = plan(pkg(listOf(source), emptyList())).feedsToAdd.single()
        assertEquals("id-0", added.id)
        assertEquals("default", added.groupId)
        assertEquals("Sender name", added.name)
        assertEquals("https://example.org/rss", added.url)
    }

    @Test fun collisionsAmongNewNamesGetStableSuffixes() {
        val feeds = listOf(source, source.copy(url = "https://example.org/other"), source.copy(url = "https://example.org/third"))
        val names = plan(pkg(feeds, emptyList())).feedsToAdd.map { it.name }
        assertEquals(listOf("Sender name", "Sender name (2)", "Sender name (3)"), names)
    }

    @Test fun newNameCollidingWithReceiverFeedInDefaultGroupIsSuffixed() {
        val existing = feed("e", "https://example.org/existing", name = "sender NAME", group = "default")
        val added = plan(pkg(listOf(source), emptyList()), feeds = listOf(existing)).feedsToAdd.single()
        assertEquals("Sender name (2)", added.name)
    }

    @Test fun pairsByNormalizedFeedUrlAndExactLinkNotSenderUuid() {
        val f = feed("receiver-random-id", "https://example.org/rss")
        val a = article("receiver-article-uuid", f, "https://example.org/story?id=1", true, false)
        val incoming = SyncArticleState("https://example.org/rss", a.link, false, true, 50)
        val result = plan(pkg(listOf(source), listOf(incoming)), feeds = listOf(f), articles = listOf(a))
        assertEquals("receiver-article-uuid", result.updates.single().receiverArticleId)
        assertEquals(1, result.matchedArticles)
        assertEquals(0, result.unmatchedArticles)
    }

    @Test fun exactArticleLinkMeansQueryDifferenceDoesNotMatch() {
        val f = feed("f", "https://example.org/rss")
        val a = article("a", f, "https://example.org/story?x=1", true, false)
        val incoming = SyncArticleState("https://example.org/rss", "https://example.org/story?x=2", false, true, null)
        val result = plan(pkg(listOf(source), listOf(incoming)), feeds = listOf(f), articles = listOf(a))
        assertEquals(0, result.matchedArticles)
        assertEquals(1, result.unmatchedArticles)
        assertTrue(result.updates.isEmpty())
    }

    @Test fun unreadUsesAndAndStarredUsesOr() {
        val f = feed("f", "https://example.org/rss")
        val a = article("a", f, "https://e.test/a", unread = false, starred = true, readAt = 100)
        val incoming = SyncArticleState("https://example.org/rss", a.link, isUnread = true, isStarred = false, readAt = 200)
        val update = plan(pkg(listOf(source), listOf(incoming)), feeds = listOf(f), articles = listOf(a)).updates
        assertTrue("already read and starred locally: nothing may regress", update.isEmpty())
    }

    @Test fun incomingReadAndStarWinWhenReceiverHasNeither() {
        val f = feed("f", "https://example.org/rss")
        val a = article("a", f, "https://e.test/a", unread = true, starred = false)
        val incoming = SyncArticleState("https://example.org/rss", a.link, isUnread = false, isStarred = true, readAt = 5)
        val u = plan(pkg(listOf(source), listOf(incoming)), feeds = listOf(f), articles = listOf(a)).updates.single()
        assertFalse(u.isUnread)
        assertTrue(u.isStarred)
        assertEquals(5L, u.readAt)
    }

    @Test fun readAtUsesTheEarliestNonNullValue() {
        val f = feed("f", "https://example.org/rss")
        fun merged(old: Long?, incoming: Long?): Long? {
            val a = article("a", f, "L", unread = true, starred = false, readAt = old)
            val state = SyncArticleState("https://example.org/rss", "L", isUnread = false, isStarred = false, readAt = incoming)
            return plan(pkg(listOf(source), listOf(state)), feeds = listOf(f), articles = listOf(a)).updates.single().readAt
        }
        assertEquals(20L, merged(null, 20))
        assertEquals(10L, merged(10, null))
        assertEquals(10L, merged(10, 20))
        assertEquals(10L, merged(20, 10))
        assertNull(merged(null, null))
    }

    @Test fun unmatchedRowsAreCountedNotInsertedAndUnmatchedFeedStillCreatesFeed() {
        val incoming = SyncArticleState(source.url, "missing", false, true, null)
        val result = plan(pkg(listOf(source), listOf(incoming)))
        assertEquals(1, result.feedsToAdd.size)
        assertEquals(0, result.matchedArticles)
        assertEquals(1, result.unmatchedArticles)
        assertTrue(result.updates.isEmpty())
    }

    @Test fun articlesOfAnotherAccountAreNeverMatched() {
        val f = feed("f", "https://example.org/rss")
        val foreign = SyncArticleSnapshot("foreign", 2, f.url, "L", true, false, null)
        val incoming = SyncArticleState("https://example.org/rss", "L", false, true, 1)
        val result = plan(pkg(listOf(source), listOf(incoming)), feeds = listOf(f), articles = listOf(foreign))
        assertEquals(0, result.matchedArticles)
        assertTrue(result.updates.isEmpty())
    }

    @Test fun articlesAreMatchedOnlyInsideTheirOwnFeed() {
        val f1 = feed("f1", "https://example.org/rss")
        val f2 = feed("f2", "https://other.org/rss")
        val a = article("a", f2, "https://shared.test/story", true, false)
        val other = SyncFeed("https://other.org/rss", "Other", "G")
        val incoming = SyncArticleState("https://example.org/rss", "https://shared.test/story", false, true, 1)
        val result = plan(pkg(listOf(source, other), listOf(incoming)), feeds = listOf(f1, f2), articles = listOf(a))
        assertEquals(0, result.matchedArticles)
        assertEquals(1, result.unmatchedArticles)
    }

    @Test fun duplicateInputFeedOrArticleKeysAreRejected() {
        val duplicateFeeds = listOf(source, source.copy(url = "https://example.org/rss"))
        assertTrue(runCatching { plan(pkg(duplicateFeeds, emptyList())) }.isFailure)
        val duplicateArticles = listOf(
            SyncArticleState(source.url, "L", true, false, null),
            SyncArticleState("https://example.org/rss", "L", false, true, null),
        )
        assertTrue(runCatching { plan(pkg(listOf(source), duplicateArticles)) }.isFailure)
    }

    @Test fun invalidAccountIdOrMissingDefaultGroupIsRejected() {
        assertTrue(runCatching { plan(pkg(emptyList(), emptyList()), account = 0) }.isFailure)
        assertTrue(runCatching { plan(pkg(emptyList(), emptyList()), defaultGroup = "") }.isFailure)
    }

    @Test fun planIsDeterministicRegardlessOfInputOrder() {
        val f = feed("f", "https://example.org/rss")
        val a1 = article("a1", f, "L1", true, false)
        val a2 = article("a2", f, "L2", true, false)
        val states = listOf(
            SyncArticleState("https://example.org/rss", "L2", false, false, 2),
            SyncArticleState("https://example.org/rss", "L1", false, false, 1),
        )
        val first = plan(pkg(listOf(source), states), feeds = listOf(f), articles = listOf(a1, a2))
        val second = plan(pkg(listOf(source), states.reversed()), feeds = listOf(f), articles = listOf(a2, a1))
        assertEquals(first.updates, second.updates)
        assertEquals(listOf("a1", "a2"), first.updates.map { it.receiverArticleId })
    }
}
