package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.BrainRpgProfileEntity
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.domain.model.rpg.BrainRpgXpEventEntity
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderModel
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedSharer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainRpgViewModelWrappedTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var dao: BrainRpgDao
    private lateinit var repository: BrainRpgRepository
    private lateinit var scope: CoroutineScope
    private val sharer: BrainWrappedSharer = mockk()
    private lateinit var viewModel: BrainRpgViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.brainRpgDao()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        val importer = BrainRpgLegacyImporter(
            database,
            context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
            dao,
        )
        repository = BrainRpgRepository(context, database, dao, importer, scope, dispatcher)
        viewModel = BrainRpgViewModel(repository, QuizGeneratorService(), sharer, dispatcher, dispatcher)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun refreshWeeklyWrapped_withNoXpDisablesShareState() = runTest(dispatcher) {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 4, legacyImportComplete = true))
        viewModel.refreshWeeklyWrapped()
        advanceUntilIdle()

        val state = viewModel.weeklyWrappedState.value
        assertFalse(state.isLoading)
        assertEquals(0L, state.summary?.weeklyXp)
        assertEquals(4, state.summary?.currentStreakDays)
        assertFalse(state.canShare)
    }

    @Test
    fun shareWeeklyWrapped_withZeroXpDoesNotCreateOrOpenFile() = runTest(dispatcher) {
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        viewModel.refreshWeeklyWrapped()
        advanceUntilIdle()
        val activity = mockk<Activity>(relaxed = true)
        viewModel.shareWeeklyWrapped(renderModel(), "Share Wrapped", activity::startActivity)
        advanceUntilIdle()

        coVerify(exactly = 0) { sharer.writePng(any(), any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
        assertFalse(viewModel.weeklyWrappedState.value.isSharing)
    }

    @Test
    fun shareWeeklyWrapped_withWeeklyXpWritesPngAndStartsChooser() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        dao.upsertProfile(BrainRpgProfileEntity(id = 1, streakDays = 6))
        dao.insertXpEvent(
            BrainRpgXpEventEntity(
                categoryId = QuizGeneratorService.CATEGORY_SCIENCE,
                xpDelta = 150L,
                rewardType = BrainRpgRewardType.QUIZ,
                createdAtMillis = now,
            ),
        )
        viewModel.refreshWeeklyWrapped()
        advanceUntilIdle()
        val file = File(context.cacheDir, "wrapped-test.png")
        val chooser = Intent(Intent.ACTION_CHOOSER)
        coEvery { sharer.writePng(any(), any()) } returns file
        every { sharer.buildShareIntent(file, any()) } returns chooser
        val activity = mockk<Activity>(relaxed = true)

        viewModel.shareWeeklyWrapped(renderModel(), "Share Wrapped", activity::startActivity)
        advanceUntilIdle()

        coVerify(exactly = 1) { sharer.writePng(any(), any()) }
        verify(exactly = 1) { sharer.buildShareIntent(file, any()) }
        verify { activity.startActivity(chooser) }
        assertFalse(viewModel.weeklyWrappedState.value.isSharing)
        assertEquals(null, viewModel.weeklyWrappedState.value.error)
    }

    @Test
    fun shareWeeklyWrapped_fileFailureSetsErrorAndNeverStartsChooser() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        dao.upsertProfile(BrainRpgProfileEntity(id = 1))
        dao.insertXpEvent(
            BrainRpgXpEventEntity(
                categoryId = QuizGeneratorService.CATEGORY_GENERAL,
                xpDelta = 5L,
                rewardType = BrainRpgRewardType.TRIAGE,
                createdAtMillis = now,
            ),
        )
        viewModel.refreshWeeklyWrapped()
        advanceUntilIdle()
        coEvery { sharer.writePng(any(), any()) } throws IllegalStateException("disk full")
        val activity = mockk<Activity>(relaxed = true)

        viewModel.shareWeeklyWrapped(renderModel(), "Share Wrapped", activity::startActivity)
        advanceUntilIdle()

        assertEquals(WrappedShareError.WRITE_FAILED, viewModel.weeklyWrappedState.value.error)
        assertFalse(viewModel.weeklyWrappedState.value.isSharing)
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    private fun renderModel() = BrainWrappedRenderModel(
        weekLabel = "WEEK",
        header = "YOUR WEEK IN KNOWLEDGE",
        weeklyXpLabel = "WEEKLY XP",
        weeklyXp = 150L,
        streakLabel = "CURRENT STREAK",
        streakValue = "6 days",
        topicLabel = "TOPIC THIS WEEK",
        topicValue = QuizGeneratorService.CATEGORY_SCIENCE,
        topicEmptyLabel = "Topic forming",
        tagline = "Stay curious.",
        watermark = "RSS Cat Hub",
    )
}
