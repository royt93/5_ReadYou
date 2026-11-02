package com.mckimquyen.reader.ui.page.home.addsources

import com.mckimquyen.reader.domain.curated.BundleSubscribeResult
import com.mckimquyen.reader.domain.curated.CuratedBundle
import com.mckimquyen.reader.domain.curated.CuratedBundleService
import com.mckimquyen.reader.domain.curated.CuratedSource
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.repository.RssSourceRepository
import com.mckimquyen.reader.domain.sv.AccountSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AddSourcesViewModelBundleTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<CuratedBundleService>()

    private val bundle = CuratedBundle(
        id = "tech_ai", titleEn = "Technology & AI", titleVi = "Công nghệ & AI", verifiedOn = "2026-11-02",
        sources = listOf(CuratedSource("A", "https://a.example/rss"), CuratedSource("B", "https://b.example/rss")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(language: String = "en") = AddSourcesViewModel(
        rssSourceRepository = mockk<RssSourceRepository>(relaxed = true),
        addedRssSourceDao = mockk<AddedRssSourceDao>(relaxed = true),
        rssService = mockk<RssSv>(relaxed = true),
        rssHelper = mockk<RssHelper>(relaxed = true),
        accountService = mockk<AccountSv>(relaxed = true),
        curatedBundleService = service,
    ).apply { languageProvider = { language } }

    @Test
    fun initialState_hasNoBundlesProgressOrResult() {
        val state = vm().uiState.value

        assertTrue(state.bundles.isEmpty())
        assertNull(state.subscribingBundleId)
        assertNull(state.bundleResult)
        assertFalse(state.canSubscribeBundles)
    }

    @Test
    fun loadBundles_exposesBundlesAndTheAccountPermission() = runTest(dispatcher) {
        coEvery { service.load() } returns listOf(bundle)
        coEvery { service.canSubscribe() } returns true
        val vm = vm()

        vm.loadBundles()
        advanceUntilIdle()

        assertEquals(listOf("tech_ai"), vm.uiState.value.bundles.map { it.id })
        assertTrue(vm.uiState.value.canSubscribeBundles)
        assertFalse(vm.uiState.value.loadingBundles)
    }

    @Test
    fun loadBundles_forANonLocalAccount_keepsBundlesButHidesSubscribe() = runTest(dispatcher) {
        coEvery { service.load() } returns listOf(bundle)
        coEvery { service.canSubscribe() } returns false
        val vm = vm()

        vm.loadBundles()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.bundles.size)
        assertFalse(vm.uiState.value.canSubscribeBundles)
    }

    @Test
    fun loadBundles_whenTheAssetIsBroken_surfacesAnErrorInsteadOfCrashing() = runTest(dispatcher) {
        coEvery { service.load() } throws IllegalArgumentException("bad asset")
        coEvery { service.canSubscribe() } returns true
        val vm = vm()

        vm.loadBundles()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.bundles.isEmpty())
        assertFalse(vm.uiState.value.loadingBundles)
        assertEquals("bad asset", vm.uiState.value.bundleError)
    }

    @Test
    fun subscribeBundle_usesTheLocalizedTitleAsTheGroupName_andPublishesTheResult() = runTest(dispatcher) {
        coEvery { service.subscribe(bundle, "Công nghệ & AI", any()) } returns BundleSubscribeResult(1, 1, 0)
        val vm = vm(language = "vi")

        vm.subscribeBundle(bundle)
        advanceUntilIdle()

        assertEquals(BundleSubscribeResult(1, 1, 0), vm.uiState.value.bundleResult)
        assertEquals("tech_ai", vm.uiState.value.bundleResultId)
        assertNull(vm.uiState.value.subscribingBundleId)
    }

    @Test
    fun subscribeBundle_whileRunning_marksTheBundleBusyAndIgnoresASecondTap() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { service.subscribe(bundle, "Technology & AI", any()) } coAnswers {
            gate.await()
            BundleSubscribeResult(2, 0, 0)
        }
        val vm = vm()

        vm.subscribeBundle(bundle)
        runCurrent()
        assertEquals("tech_ai", vm.uiState.value.subscribingBundleId)

        vm.subscribeBundle(bundle)
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { service.subscribe(bundle, "Technology & AI", any()) }
        assertNull(vm.uiState.value.subscribingBundleId)
    }

    @Test
    fun subscribeBundle_reportsProgressFromTheService() = runTest(dispatcher) {
        coEvery { service.subscribe(bundle, "Technology & AI", any()) } coAnswers {
            val onProgress = thirdArg<(Int, Int) -> Unit>()
            onProgress(1, 2)
            CompletableDeferred<Unit>().await()
            BundleSubscribeResult(0, 0, 0)
        }
        val vm = vm()

        vm.subscribeBundle(bundle)
        runCurrent()

        assertEquals(1 to 2, vm.uiState.value.bundleProgress)
    }

    @Test
    fun subscribeBundle_whenTheServiceFails_clearsBusyAndShowsAnError() = runTest(dispatcher) {
        coEvery { service.subscribe(bundle, "Technology & AI", any()) } throws IOException("offline")
        val vm = vm()

        vm.subscribeBundle(bundle)
        advanceUntilIdle()

        assertNull(vm.uiState.value.subscribingBundleId)
        assertEquals("offline", vm.uiState.value.bundleError)
    }

    @Test
    fun dismissBundleResult_clearsTheResultAndError() = runTest(dispatcher) {
        coEvery { service.subscribe(bundle, "Technology & AI", any()) } returns BundleSubscribeResult(1, 0, 0)
        val vm = vm()
        vm.subscribeBundle(bundle)
        advanceUntilIdle()

        vm.dismissBundleResult()

        assertNull(vm.uiState.value.bundleResult)
        assertNull(vm.uiState.value.bundleError)
    }
}
