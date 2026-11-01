package com.mckimquyen.reader.ui.page.rsvp

import android.content.Context
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class SavedRsvpSettings(val wpm: Int, val punctuationDelayScale: Float)

/** Remembers the speed the user accepted from the calibration. `null` from [read] means "never calibrated". */
interface RsvpCalibrationStore {
    suspend fun read(): SavedRsvpSettings?

    suspend fun write(wpm: Int, punctuationDelayScale: Float)
}

class DataStoreRsvpCalibrationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RsvpCalibrationStore {

    override suspend fun read(): SavedRsvpSettings? {
        val prefs = context.dataStore.data.first()
        val wpm = prefs[DataStoreKeys.RsvpSuggestedWpm.key] ?: return null
        val scale = prefs[DataStoreKeys.RsvpPunctuationDelayScale.key] ?: RsvpCalibrationPlan.DEFAULT_DELAY_SCALE
        return SavedRsvpSettings(wpm = wpm, punctuationDelayScale = scale)
    }

    override suspend fun write(wpm: Int, punctuationDelayScale: Float) {
        context.dataStore.put(DataStoreKeys.RsvpSuggestedWpm, wpm)
        context.dataStore.put(DataStoreKeys.RsvpPunctuationDelayScale, punctuationDelayScale)
    }
}
