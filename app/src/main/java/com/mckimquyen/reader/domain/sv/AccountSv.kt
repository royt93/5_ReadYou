package com.mckimquyen.reader.domain.sv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import com.mckimquyen.reader.ui.ext.put
import com.mckimquyen.reader.ui.ext.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssService: RssSv,
) {

    fun getAccounts(): Flow<List<Account>> = accountDao.queryAllAsFlow()

    fun getAccountById(accountId: Int): Flow<Account?> = accountDao.queryAccount(accountId)

    suspend fun getCurrentAccount(): Account = accountDao.queryById(context.currentAccountId)!!

    suspend fun isNoAccount(): Boolean = accountDao.queryAll().isEmpty()

    suspend fun addAccount(account: Account): Account =
        account.apply {
            id = accountDao.insert(this).toInt()
        }.also {
            // handle default group
            when (it.type) {
                AccountType.Local -> {
                    groupDao.insert(
                        Group(
                            id = it.id!!.getDefaultGroupId(),
                            name = context.getString(R.string.defaults),
                            accountId = it.id!!,
                        )
                    )
                }
            }
            context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountId, value = it.id!!)
            context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountType, value = it.type.id)
        }

    suspend fun addDefaultAccount(): Account =
        addAccount(
            Account(
                type = AccountType.Local,
                name = context.getString(R.string.read_you),
            )
        )

    suspend fun update(accountId: Int, block: Account.() -> Unit) {
        accountDao.queryById(accountId)?.let {
            accountDao.update(it.apply(block))
        }
    }

    suspend fun delete(accountId: Int) {
        if (accountDao.queryAll().size == 1) {
            withContext(Dispatchers.Main) {
                context.showToast(context.getString(R.string.must_have_an_account))
            }
            return
        }
        accountDao.queryById(accountId)?.let {
            articleDao.deleteByAccountId(accountId)
            feedDao.deleteByAccountId(accountId)
            groupDao.deleteByAccountId(accountId)
            accountDao.delete(it)
            accountDao.queryAll().getOrNull(0)?.let {
                context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountId, value = it.id!!)
                context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountType, value = it.type.id)
            }
        }
    }

    suspend fun switch(account: Account) {
        rssService.get().cancelSync()
        context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountId, value = account.id!!)
        context.dataStore.put(dataStoreKeys = DataStoreKeys.CurrentAccountType, value = account.type.id)

        // Restart
        // context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        //     it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        //     context.startActivity(it)
        //     android.os.Process.killProcess(android.os.Process.myPid())
        // }
    }
}
