package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFetchResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Types into the real Add Source dialog (the real view model, mocked services) and presses Search. It proves the dialog
 * is wired to the account resolvers; it does not prove how the dialog looks or feels on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeDialogTypingTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val relay = mockk<NostrRelayClient>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val scraper = mockk<WebFeedScraper>()
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val npub = requireNotNull(NostrKeys.hexToNpub(NostrTestEvents.AUTHOR))
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        viewModel = SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rssService, rssHelper = rssHelper,
            androidStringsHelper = AndroidStringsHelper(context), webFeedScraper = scraper, newsletterUrlResolver = resolver,
            nostrRelayClient = relay,
        )
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
    }

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }

    private fun openDialog() {
        composeRule.setContent { SubscribeDialog(viewModel) }
        composeRule.runOnIdle { viewModel.showDrawer() }
        // The state is collected on another dispatcher, so the field appears a moment later; wait for it instead of assuming.
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(hasText("Feed or Site URL")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typeAndSearch(text: String) {
        composeRule.onNodeWithText("Feed or Site URL").performTextInput(text)
        composeRule.onNodeWithText("Search").performClick()
        composeRule.waitUntil(WAIT_MILLIS) { viewModel.subscribeUiState.value.let { it.feed != null || it.errorMessage.isNotEmpty() } }
    }

    @Test fun typingAnNpubAndPressingSearch_showsTheNostrPreviewAndASubscribeButton() {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER), null)
        openDialog()
        typeAndSearch(npub)
        composeRule.onNodeWithText("Nostr · " + npub.take(8) + "…" + npub.takeLast(4)).assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertIsDisplayed()
        assertEquals(listOf("Hello nostr, first note", "Older note"), viewModel.subscribeUiState.value.articles.map { it.title })
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun pressingSubscribeAfterTheNostrPreview_savesANostrFeed() {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), null)
        openDialog()
        typeAndSearch(npub)
        composeRule.onNodeWithText("Subscribe").performClick()
        composeRule.waitUntil(WAIT_MILLIS) { runCatching { coVerify(exactly = 1) { localService.subscribe(any(), any()) } }.isSuccess }
        coVerify(exactly = 1) { localService.subscribe(match { it.sourceType == Feed.NOSTR_SOURCE_TYPE && it.url == "nostr:$npub" }, any()) }
    }

    @Test fun typingAMastodonHandle_isSearchedAsItsRssAddress() {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { rssHelper.searchFeed(rss) } returns FeedWithArticle(
            Feed(id = "f", name = "Mastodon", url = rss, groupId = "", accountId = 1),
            listOf(Article(id = "a", date = Date(), title = "Toot", rawDescription = "", shortDescription = "", link = "$rss/1", feedId = "f", accountId = 1)),
        )
        openDialog()
        typeAndSearch("@Mastodon@mastodon.social")
        composeRule.onNodeWithText("Subscribe").assertIsDisplayed()
        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { relay.fetch(any()) }
    }

    @Test fun typingABlueskyHandle_isNotMangledIntoAnHttpsAddress() {
        val rss = "https://bsky.app/profile/bsky.app/rss"
        coEvery { rssHelper.searchFeed(rss) } returns FeedWithArticle(
            Feed(id = "f", name = "Bluesky", url = rss, groupId = "", accountId = 1), emptyList(),
        )
        openDialog()
        typeAndSearch("@bsky.app")
        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { rssHelper.searchFeed("https://@bsky.app") }
    }

    @Test fun typingASecretKey_isRefused_andTheBoxIsEmptiedOnScreen() {
        openDialog()
        val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
        composeRule.onNodeWithText("Feed or Site URL").performTextInput(nsec)
        composeRule.onNodeWithText("Search").performClick()
        composeRule.waitUntil(WAIT_MILLIS) { viewModel.subscribeUiState.value.errorMessage.isNotEmpty() }
        assertEquals("", viewModel.subscribeUiState.value.linkContent)
        composeRule.onNodeWithText("Use a public HTTPS URL. Local or private network addresses are not supported.").assertIsDisplayed()
        coVerify(exactly = 0) { relay.fetch(any()) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
        assertTrue(composeRule.onAllNodesWithTextCount(nsec) == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().size

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
