package com.mckimquyen.reader.domain.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BrainRpgTriageTest {

    private lateinit var db: AndroidDatabase
    private lateinit var repository: BrainRpgRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.brainRpgDao()
        val legacyImporter = BrainRpgLegacyImporter(
            db,
            context.getSharedPreferences("test_rpg_prefs", Context.MODE_PRIVATE),
            dao,
        )
        repository = BrainRpgRepository(
            context = context,
            database = db,
            dao = dao,
            legacyImporter = legacyImporter,
            applicationScope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun awardTriageXpOnce_awards5XpFirstTime_andZeroOnRepeat() = testScope.runTest {
        val xp1 = repository.awardTriageXpOnce(articleId = "article_1", amount = 5L, nowMillis = 100_000L)
        assertEquals(5L, xp1)

        val xpRepeat = repository.awardTriageXpOnce(articleId = "article_1", amount = 5L, nowMillis = 100_000L)
        assertEquals(0L, xpRepeat)

        val progress = repository.getProgress()
        assertEquals(5L, progress.totalXp)
    }
}
