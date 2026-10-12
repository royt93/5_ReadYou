package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSvDeleteTest {

    private val dispatcher = StandardTestDispatcher()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val digestDao = mockk<DailyDigestDao>(relaxed = true)
    private val rssSv = mockk<RssSv>(relaxed = true)
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val accountA = Account(id = 1, name = "A", type = AccountType.Local)
    private val accountB = Account(id = 2, name = "B", type = AccountType.Local)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { accountDao.queryById(any()) } returns accountA
        coEvery { accountDao.queryAll() } returns listOf(accountA, accountB)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun sv() = AccountSv(context, accountDao, groupDao, feedDao, articleDao, rssSv, digestDao)

    @Test
    fun delete_alsoDeletesDigestRowsForThatAccount() = runTest(dispatcher) {
        sv().delete(1)
        advanceUntilIdle()
        coVerify { digestDao.deleteByAccountId(1) }
    }

    @Test
    fun delete_onlyAffectsTheChosenAccount_notOthers() = runTest(dispatcher) {
        sv().delete(1)
        advanceUntilIdle()
        coVerify(exactly = 0) { digestDao.deleteByAccountId(2) }
    }
}
