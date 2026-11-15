package com.mckimquyen.reader.ui.page.home.feed.subs

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.ui.theme.AppTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * On the phone: the Add Source dialog is typed into, the screen is recreated (what a rotation does) and the phone is
 * turned sideways, and the same view model, typed text and preview must still be there. The view model is built from
 * MockK fakes (no Hilt in androidTest) and kept by the Activity's own ViewModelStore, as in the app. Everything is
 * driven by code; nothing taps by coordinates.
 */
class SubscribeDialogRotationQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val npub = requireNotNull(NostrKeys.hexToNpub(AUTHOR))
    private var originalAccountType = 1

    @Before fun setUp() {
        originalAccountType = context.currentAccountType
        runBlocking { context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id) }
    }

    @After fun tearDown() {
        runBlocking { context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType) }
    }

    private fun newViewModel(): SubscribeViewModel {
        val local = mockk<LocalRssSv>(relaxed = true)
        coEvery { local.pullGroups() } returns flowOf(mutableListOf(Group(id = "g", name = "Default", accountId = 1)))
        coEvery { local.isFeedExist(any()) } returns false
        val rss = mockk<RssSv>()
        every { rss.get() } returns local
        every { rss.get(AccountType.Local.id) } returns local
        val relay = mockk<NostrRelayClient>()
        coEvery { relay.fetch(any()) } returns listOf(requireNotNull(NostrEvent.parse(PLAIN)))
        return SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rss, rssHelper = mockk<RssHelper>(),
            androidStringsHelper = AndroidStringsHelper(context), webFeedScraper = mockk<WebFeedScraper>(),
            newsletterUrlResolver = mockk<NewsletterUrlResolver>(), nostrRelayClient = relay,
        )
    }

    /** The factory is the same on every recreate; the ViewModelStore decides whether the old instance is handed back. */
    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = newViewModel() as T
    }

    private fun viewModel(): SubscribeViewModel =
        ViewModelProvider(composeRule.activity, factory)[SubscribeViewModel::class.java]

    private fun showDialog() {
        val vm = viewModel()
        composeRule.runOnUiThread {
            composeRule.activity.setContent { AppTheme(useDarkTheme = false) { SubscribeDialog(vm) } }
            vm.showDrawer()
        }
        waitForText("Feed or Site URL")
    }

    private fun waitForText(text: String) = composeRule.waitUntil(WAIT_MILLIS) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun theViewModel_theTypedText_andThePreview_surviveRecreatingTheScreen() {
        showDialog()
        val first = viewModel()
        composeRule.onNodeWithText("Feed or Site URL").performTextInput(npub)
        composeRule.onNodeWithText("Search").performClick()
        composeRule.waitUntil(WAIT_MILLIS) { first.subscribeUiState.value.feed != null }
        val before = first.subscribeUiState.value

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val after = viewModel()
        assertSame("a recreated Activity must be handed the very same view model", first, after)
        assertNotNull(after.subscribeUiState.value.feed)
        assertEquals(before.feed?.url, after.subscribeUiState.value.feed?.url)
        assertEquals(before.articles.map { it.title }, after.subscribeUiState.value.articles.map { it.title })
    }

    @Test fun turningThePhoneSidewaysAndBack_keepsTheTypedText() {
        showDialog()
        val vm = viewModel()
        composeRule.onNodeWithText("Feed or Site URL").performTextInput(npub)
        for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
            composeRule.runOnUiThread { composeRule.activity.requestedOrientation = orientation }
            composeRule.waitForIdle()
        }
        composeRule.runOnUiThread { composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        composeRule.waitForIdle()
        assertSame(vm, viewModel())
        assertTrue("the typed key must still be in the box after two rotations", viewModel().subscribeUiState.value.linkContent.startsWith("npub1"))
    }

    private companion object {
        const val WAIT_MILLIS = 8_000L
        const val AUTHOR = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        const val PLAIN = """{"id":"c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000300,"kind":1,"tags":[],"content":"Hello nostr, first note","sig":"4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623"}"""
    }
}
