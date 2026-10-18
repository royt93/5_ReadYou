package com.mckimquyen.reader.ui.page.collection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import com.mckimquyen.reader.domain.sv.SmartCollectionMatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartCollectionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val smartCollectionDao: SmartCollectionDao = mockk(relaxed = true)
    private val smartCollectionMatcher: SmartCollectionMatcher = mockk(relaxed = true)
    private val matchAccounts = mutableListOf<Int>()
    private lateinit var viewModel: SmartCollectionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { smartCollectionDao.observeCollectionsWithCount(any()) } returns flowOf(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(
                        id = "col-1",
                        accountId = 0,
                        name = "AI Tech",
                        query = "artificial intelligence",
                    ),
                    articleCount = 5,
                )
            )
        )
        matchAccounts.clear()
        coEvery { smartCollectionMatcher.reevaluateCollection(any(), any()) } coAnswers {
            matchAccounts += firstArg<Int>()
            0
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = SmartCollectionsViewModel(
            context = app,
            smartCollectionDao = smartCollectionDao,
            smartCollectionMatcher = smartCollectionMatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createCollection_validNameAndQuery_callsUpsert() = runTest(testDispatcher) {
        val captured = slot<SmartCollection>()
        coEvery { smartCollectionDao.upsertCollection(capture(captured)) } returns Unit

        viewModel.createCollection("Robotics", "robot humanoid")
        advanceUntilIdle()

        coVerify(exactly = 1) { smartCollectionDao.upsertCollection(any()) }
        assertEquals("Robotics", captured.captured.name)
        assertEquals("robot humanoid", captured.captured.query)
    }

    @Test
    fun createCollection_blankNameOrQuery_doesNotCallUpsert() = runTest(testDispatcher) {
        viewModel.createCollection("   ", "valid query")
        advanceUntilIdle()

        coVerify(exactly = 0) { smartCollectionDao.upsertCollection(any()) }

        viewModel.createCollection("Valid Name", "   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { smartCollectionDao.upsertCollection(any()) }
    }

    @Test
    fun updateCollection_changedQueryReevaluatesMembership() = runTest(testDispatcher) {
        coEvery { smartCollectionDao.getCollectionById("col-1") } returns SmartCollection(
            id = "col-1",
            accountId = 0,
            name = "AI Tech",
            query = "old query",
        )

        viewModel.updateCollection("col-1", "New Name", "new query")
        advanceUntilIdle()

        coVerify(exactly = 1) { smartCollectionDao.updateCollection("col-1", "New Name", "new query") }
        coVerify(exactly = 1) { smartCollectionMatcher.reevaluateCollection(any(), "col-1") }
        val expectedAccountId = ApplicationProvider.getApplicationContext<Application>().currentAccountId
        assertEquals(listOf(expectedAccountId), matchAccounts)
    }

    @Test
    fun updateCollection_nameOnlyDoesNotReevaluateMembership() = runTest(testDispatcher) {
        coEvery { smartCollectionDao.getCollectionById("col-1") } returns SmartCollection(
            id = "col-1",
            accountId = 0,
            name = "AI Tech",
            query = "same query",
        )

        viewModel.updateCollection("col-1", "New Name", "same query")
        advanceUntilIdle()

        coVerify(exactly = 0) { smartCollectionMatcher.reevaluateCollection(any(), any()) }
    }

    @Test
    fun deleteCollection_callsDaoDelete() = runTest(testDispatcher) {
        viewModel.deleteCollection("col-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { smartCollectionDao.deleteCollection("col-1") }
    }
}
