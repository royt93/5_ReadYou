package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpmlSvTest {

    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val rssSv = mockk<RssSv>(relaxed = true)
    private val opmlDataSource = mockk<OPMLDataSource>(relaxed = true)
    private lateinit var opmlSv: OpmlSv

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        coEvery { groupDao.queryById(any()) } returns null
        opmlSv = OpmlSv(context, groupDao, feedDao, accountDao, rssSv, opmlDataSource)
    }

    @Test
    fun saveToDatabase_whenDefaultGroupMissing_throwsIllegalStateInsteadOfNpe() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                opmlSv.saveToDatabase(ByteArrayInputStream(ByteArray(0)))
            }
        }

        assertTrue(error.message.orEmpty().contains("Default group not found"))
        // Import must stop before parsing or writing anything.
        coVerify(exactly = 0) { opmlDataSource.parseFileInputStream(any(), any()) }
        coVerify(exactly = 0) { feedDao.insertList(any()) }
    }

    @Test
    fun saveToString_whenDefaultGroupMissing_throwsIllegalStateWithAccountId() {
        val accountId = 7

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { opmlSv.saveToString(accountId) }
        }

        assertTrue(error.message.orEmpty().contains("account $accountId"))
        coVerify(exactly = 0) { groupDao.queryAllGroupWithFeed(any()) }
    }
}
