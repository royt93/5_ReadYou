package com.mckimquyen.reader.ui.page.rpg

import android.content.Context
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Remembers the UTC epoch day on which the broken-streak dialog was last shown. */
interface StreakRescuePromptDayStore {
    /** Returns [NEVER_PROMPTED] when the dialog has never been shown. */
    suspend fun readLastPromptDay(): Long

    suspend fun writeLastPromptDay(epochDay: Long)

    companion object {
        const val NEVER_PROMPTED = -1L
    }
}

class DataStoreStreakRescuePromptDayStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : StreakRescuePromptDayStore {

    override suspend fun readLastPromptDay(): Long =
        context.dataStore.data
            .map { it[DataStoreKeys.StreakRescueLastPromptDay.key] ?: StreakRescuePromptDayStore.NEVER_PROMPTED }
            .first()

    override suspend fun writeLastPromptDay(epochDay: Long) {
        context.dataStore.put(DataStoreKeys.StreakRescueLastPromptDay, epochDay)
    }
}
