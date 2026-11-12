package com.mckimquyen.reader.ui.page.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OracleClaimsViewModelTest {

    private val source = MutableStateFlow<List<OracleMarket>>(emptyList())
    private val dao: OracleMarketDao = mockk { every { observeClaims() } returns source }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun claim(id: String, q: String? = "Will it ship?", exp: Long? = 9L, crit: String? = "Release notes.") =
        OracleMarket(id = 1, articleId = id, status = OracleMarketStatus.HAS_CLAIM, question = q, expiresAt = exp, resolutionCriteria = crit, createdAt = 1)

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    @Test fun startsEmpty() {
        assertEquals(emptyMap<String, OracleClaimCard>(), OracleClaimsViewModel(dao).claims.value)
    }

    @Test fun mapsClaimsByArticleId() {
        val vm = OracleClaimsViewModel(dao)
        source.value = listOf(claim("a"), claim("b"))
        val map = awaitReal { vm.claims.first { it.size == 2 } }
        assertEquals("Will it ship?", map.getValue("a").question)
        assertEquals(9L, map.getValue("b").expiresAt)
        assertEquals("Release notes.", map.getValue("a").resolutionCriteria)
    }

    @Test fun rowsMissingAnyFieldAreDropped() {
        val vm = OracleClaimsViewModel(dao)
        source.value = listOf(claim("a", q = null), claim("b", exp = null), claim("c", crit = null), claim("ok"))
        val map = awaitReal { vm.claims.first { it.isNotEmpty() } }
        assertEquals(setOf("ok"), map.keys)
        assertNull(map["a"])
    }

    @Test fun toCardOrNull_isNullForABlankField() {
        assertNull(claim("a", q = "  ").toCardOrNull())
        assertNull(claim("a", crit = "").toCardOrNull())
    }
}
