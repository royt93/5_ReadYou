package com.mckimquyen.reader.domain.sv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
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

private const val CLASS_TIMEOUT_SECONDS = 60L
private const val PROBE_PREFIX = "front02h_probe_"

/**
 * subscribe() on the phone's REAL Room (real SQLite, real foreign keys): a failing article write must leave no source behind, a good one
 * saves both. Probe rows only (ids with the probe prefix, in the probe group); cleanUp removes them and the group cascade takes the rest.
 * NOT covered: the dialog (see SubscribeNostrRealTapDeviceTest), Fever and Google Reader accounts.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeAtomicDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)

    private lateinit var rig: BackgroundSyncRig
    private val feed get() = rig.nostrFeed.copy(id = "${PROBE_PREFIX}atomic")

    private fun article(id: String, link: String) = Article(
        id = "$PROBE_PREFIX$id", date = Date(1L), title = "t $id", rawDescription = "RAW", shortDescription = "SHORT", link = link,
        feedId = feed.id, accountId = feed.accountId,
    )

    @Before
    fun setUp() {
        rig = BackgroundSyncRig()
        runBlocking {
            rig.cleanUp()
            rig.addProbeSources()
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking {
                rig.feedDao().queryById(feed.id)?.let { rig.feedDao().delete(it) }
                rig.cleanUp()
            }
        } finally {
            rig.close()
        }
    }

    @Test
    fun aGoodSubscribe_savesTheSourceAndBothArticles() {
        runBlocking { rig.service.subscribe(feed, listOf(article("a1", "https://front02h.example/a1"), article("a2", "https://front02h.example/a2"))) }
        assertNotNull(runBlocking { rig.feedDao().queryById(feed.id) })
        assertEquals(2, rig.articleCountOf(feed.id))
    }

    @Test
    fun aFailingArticleWrite_leavesNoSourceAndNoArticle() {
        val failed = runCatching {
            runBlocking { rig.service.subscribe(feed, listOf(article("same", "https://front02h.example/x"), article("same", "https://front02h.example/y"))) }
        }
        assertTrue("the failure must reach the caller", failed.isFailure)
        assertNull("a source without its articles must not stay behind", runBlocking { rig.feedDao().queryById(feed.id) })
        assertEquals(0, rig.articleCountOf(feed.id))
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeft() {
        runBlocking { rig.service.subscribe(feed, listOf(article("a1", "https://front02h.example/a1"))) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking {
            rig.feedDao().queryById(feed.id)?.let { rig.feedDao().delete(it) }
            rig.cleanUp()
        }
        assertEquals(0L, rig.probeRowCount())
    }
}
