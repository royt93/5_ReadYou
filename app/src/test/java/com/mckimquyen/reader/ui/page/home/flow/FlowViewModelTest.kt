package com.mckimquyen.reader.ui.page.home.flow

import com.mckimquyen.reader.domain.sv.RssSv
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val rssService = mockk<RssSv>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun flowUiState_initializesWithCleanState() = runTest(testDispatcher) {
        val viewModel = FlowViewModel(
            rssService = rssService,
            ioDispatcher = testDispatcher
        )

        val state = viewModel.flowUiState.value
        assertEquals(0, state.filterImportant)
        assertFalse(state.isBack)
        assertEquals("", state.syncWorkInfo)
    }
}
