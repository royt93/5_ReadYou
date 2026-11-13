package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleWalletDaoDueTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao
    private val day = 86_400_000L
    private val placedAt = 100 * day

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
    }

    @After
    fun tearDown() = db.close()

    private fun bet(article: String, expiresAt: Long, stake: Int = 50) = runBlocking {
        dao.placeBet(article, "Q-$article?", expiresAt, OracleBetSide.YES, stake, placedAt)
    }

    private fun due(now: Long) = runBlocking { dao.dueOpenBets(now) }.map { it.articleId }

    @Test
    fun noBets_isEmpty() = assertTrue(due(placedAt + 10 * day).isEmpty())

    @Test
    fun aBetIsDueAtTheDeadlineExactly_notBefore() {
        val deadline = placedAt + 5 * day
        bet("a", deadline)
        assertTrue("one millisecond early", due(deadline - 1).isEmpty())
        assertEquals(listOf("a"), due(deadline))
        assertEquals(listOf("a"), due(deadline + 1))
    }

    @Test
    fun settledAndRefundedBets_areNeverDue() {
        bet("won", placedAt + day); bet("refunded", placedAt + day); bet("open", placedAt + day)
        runBlocking {
            dao.settleBet("won", OracleOutcome.YES, placedAt + day)
            dao.settleBet("refunded", OracleOutcome.UNCLEAR, placedAt + day)
        }
        assertEquals(listOf("open"), due(placedAt + 2 * day))
    }

    @Test
    fun aBetThatIsNotYetDue_isLeftOut() {
        bet("soon", placedAt + 2 * day); bet("later", placedAt + 9 * day)
        assertEquals(listOf("soon"), due(placedAt + 3 * day))
    }

    @Test
    fun orderedByDeadline_thenById() {
        bet("late", placedAt + 4 * day); bet("early", placedAt + 2 * day); bet("tie1", placedAt + 3 * day); bet("tie2", placedAt + 3 * day)
        assertEquals(listOf("early", "tie1", "tie2", "late"), due(placedAt + 10 * day))
    }

    @Test
    fun equalDeadlines_followTheOrderTheBetsWerePlaced_notTheArticleName() {
        // Names are in reverse alphabetical order, so only the id (placing order) can explain the result.
        val deadline = placedAt + 3 * day
        bet("zzz", deadline); bet("mmm", deadline); bet("aaa", deadline)
        assertEquals(listOf("zzz", "mmm", "aaa"), due(placedAt + 10 * day))
    }

    @Test
    fun theQueryOnlyReads_itChangesNoBetAndNoBalance() {
        bet("a", placedAt + day, stake = 100)
        val before = runBlocking { dao.wallet()?.balance }
        due(placedAt + 5 * day)
        assertEquals(before, runBlocking { dao.wallet()?.balance })
        assertEquals(OracleBetStatus.OPEN, runBlocking { dao.betFor("a") }!!.status)
    }
}
