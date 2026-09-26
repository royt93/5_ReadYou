package com.mckimquyen.reader.domain.sv

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.ui.ext.currentAccountType
import javax.inject.Inject

class RssSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val localRssService: LocalRssSv,
    private val feverRssService: FeverRssSv,
    private val googleReaderRssService: GoogleReaderRssSv,
) {

    fun get() = get(context.currentAccountType)

    fun get(accountTypeId: Int) = when (accountTypeId) {
        AccountType.Local.id -> localRssService
        AccountType.Fever.id -> feverRssService
        // FreshRSS, Miniflux, Nextcloud News and other self-hosted servers all speak the same
        // "Google Reader API" v1 protocol, so they share a single backend implementation.
        AccountType.GoogleReader.id -> googleReaderRssService
        AccountType.FreshRSS.id -> googleReaderRssService
        AccountType.Inoreader.id -> localRssService
        AccountType.Feedly.id -> localRssService
        else -> localRssService
    }
}
