package com.mckimquyen.reader.infrastructure.pref

import com.mckimquyen.reader.ui.page.setting.acc.AccountViewModel

typealias SyncBlockList = List<String>

object SyncBlockListPref {

    val default: SyncBlockList = emptyList()

    fun put(accountId: Int, viewModel: AccountViewModel, syncBlockList: SyncBlockList) {
        viewModel.update(accountId) { this.syncBlockList = syncBlockList }
    }

    /**
     * Older builds stored `"a\n, b\n"` (a ", " crept in between lines). Only lines after the first carry that prefix, so it is stripped there and a rule the user
     * really typed with a leading comma on the first line is left alone.
     */
    fun of(syncBlockList: String): SyncBlockList =
        syncBlockList.split("\n")
            .mapIndexed { index, line -> if (index == 0) line.trim() else line.trim().trimStart(',', ' ') }
            .filter { it.isNotBlank() }

    fun toString(syncBlockList: SyncBlockList): String = syncBlockList
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}
