package com.mckimquyen.reader.ui.page.home.feed

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionDrawer
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import com.mckimquyen.reader.ui.page.home.feed.subs.BlueskyGuardRig
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.Date

private const val CLASS_TIMEOUT_SECONDS = 120L
private const val WAIT_MILLIS = 15_000L
private const val NOT_YET_REAL_ROWS = -1L
private const val ARTICLES_PER_FEED = 3
private const val ARTICLE_DATE_MILLIS = 1L

/** Owns the view models of the probe and hands out the real [FeedOptionViewModel], built from the running app, without Hilt. */
private class DeleteClearOwner(app: RApp) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
    }
}

/**
 * FRONT-02v left the two buttons that remove data untested on a phone. This drives them on the REAL screen pieces and the phone's REAL Room:
 * a long press on the real FeedItem opens the real FeedOptionDrawer, the "Clear Articles" and "Unsubscribe" chips open the real confirm
 * dialogs, and the confirm button removes the rows. Every test seeds TWO probe sources with three articles each and checks that the other
 * source is left exactly as it was, so "removed only what it should" is measured, not assumed. Only probe rows (ids with the probe prefix)
 * are written; [BlueskyGuardRig.cleanUp] removes them, and the owner's own feed and article counts are compared before and after.
 * Touches are Compose actions on nodes, never coordinates; the confirm button is found INSIDE the dialog because the same word is also
 * on the chip and in the dialog title.
 * NOT covered: FeedsPage, swiping by hand, the toast text, a group, other account types, the unread counters in the list.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class FeedDrawerDeleteClearDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var owner: DeleteClearOwner? = null
    private var shown: FeedOptionViewModel? = null
    private val app: RApp get() = LocalRssSvFactory.app()

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "Delete and Clear only run for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the view model first (it collects Flows in its own scope), then the probe rows. */
    @After
    fun tearDown() {
        try {
            owner?.viewModelStore?.clear()
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private val unsubscribeLabel get() = compose.activity.getString(R.string.unsubscribe)
    private val clearArticlesLabel get() = compose.activity.getString(R.string.clear_articles)
    private val clearLabel get() = compose.activity.getString(R.string.clear)
    private val cancelLabel get() = compose.activity.getString(R.string.cancel)

    private fun describe(): String = try {
        val state = shown?.feedOptionUiState?.value
        "feedLoaded=${state?.feed != null} drawerVisible=${state?.drawerState?.isVisible} deleteDialog=${state?.deleteDialogVisible} " +
            "clearDialog=${state?.clearDialogVisible} dialogsOnScreen=${compose.onAllNodes(isDialog()).fetchSemanticsNodes().size}"
    } catch (e: Throwable) {
        "describe failed: ${e.javaClass.simpleName}"
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    private fun state() = requireNotNull(shown) { "the screen was not shown yet" }.feedOptionUiState.value

    private fun articleCount(feedId: String): Int = rig.articleCountOf(feedId)

    private fun seedWithArticles(id: String) {
        runBlocking {
            rig.seed(id, url = BlueskyFeedMapper.feedUrl(HANDLE), sourceUrl = BlueskyFeedMapper.feedUrl(DID))
            val stored = requireNotNull(rig.stored(id))
            val articles = (1..ARTICLES_PER_FEED).map { n ->
                Article(
                    id = "${BlueskyGuardRig.PROBE_PREFIX}art_${id}_$n", date = Date(ARTICLE_DATE_MILLIS + n), title = "probe $id $n",
                    rawDescription = "RAW", shortDescription = "SHORT", link = "https://example.org/probe/$id/$n",
                    feedId = stored.id, accountId = stored.accountId,
                )
            }
            app.androidDatabase.articleDao().insert(*articles.toTypedArray())
        }
        assertEquals("the probe articles were written for $id", ARTICLES_PER_FEED, articleCount(id))
    }

    /** Seeds the two probe sources and puts the real drawer with the real row of [shownId] on the screen. */
    private fun showTheRowOf(shownId: String, otherId: String): Feed {
        seedWithArticles(shownId)
        seedWithArticles(otherId)
        val seeded = requireNotNull(runBlocking { rig.stored(shownId) })
        val testOwner = DeleteClearOwner(app)
        owner = testOwner
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner) {
                AppTheme(useDarkTheme = false) {
                    FeedOptionDrawer { FeedItem(feed = seeded, isEnded = { true }, isExpanded = { true }) }
                }
            }
        }
        shown = compose.runOnUiThread { ViewModelProvider(testOwner)[FeedOptionViewModel::class.java] }
        return seeded
    }

    private fun openTheDrawer(seeded: Feed) {
        compose.onNode(hasText(seeded.name), useUnmergedTree = true).performTouchInput { longClick() }
        waitFor("the drawer for the source") { state().drawerState.isVisible && state().feed?.id == seeded.id }
    }

    /** The chip is outside any dialog; the word "Unsubscribe" / "Clear Articles" must belong to a clickable node that is NOT in a dialog. */
    private fun pressChip(label: String) {
        compose.onNode(hasText(label) and hasClickAction() and !hasAnyAncestor(isDialog())).assertHasClickAction().performClick()
    }

    private fun confirmButton(label: String) = compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    private fun rowGone(id: String) = runBlocking { rig.stored(id) } == null

    // ---- Clear Articles

    @Test
    fun clearArticles_confirm_removesOnlyThisSourcesArticles_andKeepsTheSource() {
        val seeded = showTheRowOf("clear", "clear_other")
        openTheDrawer(seeded)
        pressChip(clearArticlesLabel)
        waitFor("the clear dialog") { state().clearDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("opening the dialog removes nothing", ARTICLES_PER_FEED, articleCount("clear"))
        confirmButton(clearLabel).assertHasClickAction().performClick()
        waitFor("the articles of this source to be gone and the drawer closed") {
            articleCount("clear") == 0 && !state().clearDialogVisible && !state().drawerState.isVisible
        }
        assertNotNull("Clear Articles keeps the source itself", runBlocking { rig.stored("clear") })
        assertEquals("the other source keeps all its articles", ARTICLES_PER_FEED, articleCount("clear_other"))
        assertNotNull("the other source is untouched", runBlocking { rig.stored("clear_other") })
    }

    @Test
    fun clearArticles_cancel_removesNothing() {
        val seeded = showTheRowOf("clearcancel", "clearcancel_other")
        openTheDrawer(seeded)
        pressChip(clearArticlesLabel)
        waitFor("the clear dialog") { state().clearDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(cancelLabel) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitFor("the dialog to close") { !state().clearDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        assertEquals("Cancel removes nothing", ARTICLES_PER_FEED, articleCount("clearcancel"))
        assertEquals(ARTICLES_PER_FEED, articleCount("clearcancel_other"))
        assertNotNull(runBlocking { rig.stored("clearcancel") })
        assertTrue("Cancel closes only the dialog: the drawer stays open", state().drawerState.isVisible)
    }

    // ---- Unsubscribe

    @Test
    fun unsubscribe_confirm_removesThisSourceAndItsArticles_andLeavesTheOtherOne() {
        val seeded = showTheRowOf("delete", "delete_other")
        openTheDrawer(seeded)
        pressChip(unsubscribeLabel)
        waitFor("the unsubscribe dialog") { state().deleteDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        assertNotNull("opening the dialog removes nothing", runBlocking { rig.stored("delete") })
        confirmButton(unsubscribeLabel).assertHasClickAction().performClick()
        waitFor("the source to be gone and the drawer closed") { rowGone("delete") && !state().deleteDialogVisible && !state().drawerState.isVisible }
        assertNull("the source row is gone", runBlocking { rig.stored("delete") })
        assertEquals("its articles are gone with it", 0, articleCount("delete"))
        assertNotNull("the other source is still there", runBlocking { rig.stored("delete_other") })
        assertEquals("and keeps all its articles", ARTICLES_PER_FEED, articleCount("delete_other"))
    }

    @Test
    fun unsubscribe_cancel_removesNothing() {
        val seeded = showTheRowOf("delcancel", "delcancel_other")
        openTheDrawer(seeded)
        pressChip(unsubscribeLabel)
        waitFor("the unsubscribe dialog") { state().deleteDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(cancelLabel) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitFor("the dialog to close") { !state().deleteDialogVisible && compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        assertNotNull("Cancel keeps the source", runBlocking { rig.stored("delcancel") })
        assertEquals("and its articles", ARTICLES_PER_FEED, articleCount("delcancel"))
        assertEquals(ARTICLES_PER_FEED, articleCount("delcancel_other"))
        assertTrue("Cancel closes only the dialog: the drawer stays open", state().drawerState.isVisible)
    }

    // ---- cleanliness

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeft_andTheRealDataIsUntouched() {
        seedWithArticles("tmp")
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
    }

    private companion object {
        const val HANDLE = "alice-front02ah.bsky.social"
        const val DID = "did:plc:front02ahdeleteclearprobe"
    }
}
