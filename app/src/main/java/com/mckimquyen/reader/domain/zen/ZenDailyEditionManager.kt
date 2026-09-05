package com.mckimquyen.reader.domain.zen

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.sv.DailyEditionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenDailyEditionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isBatchSilence = MutableStateFlow(true)
    val isBatchSilence: StateFlow<Boolean> = _isBatchSilence.asStateFlow()

    private val _morningTime = MutableStateFlow("07:00")
    val morningTime: StateFlow<String> = _morningTime.asStateFlow()

    private val _eveningTime = MutableStateFlow("20:00")
    val eveningTime: StateFlow<String> = _eveningTime.asStateFlow()

    init {
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _isBatchSilence.value = prefs.getBoolean(KEY_BATCH_SILENCE, true)
        _morningTime.value = prefs.getString(KEY_MORNING_TIME, "07:00") ?: "07:00"
        _eveningTime.value = prefs.getString(KEY_EVENING_TIME, "20:00") ?: "20:00"
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (enabled) {
            val wm = WorkManager.getInstance(context)
            DailyEditionWorker.enqueueDailyWork(wm)
        } else {
            val wm = WorkManager.getInstance(context)
            DailyEditionWorker.cancelDailyWork(wm)
        }
    }

    fun setBatchSilence(silence: Boolean) {
        prefs.edit().putBoolean(KEY_BATCH_SILENCE, silence).apply()
        _isBatchSilence.value = silence
    }

    fun shouldSilenceImmediateNotification(): Boolean {
        return _isEnabled.value && _isBatchSilence.value
    }

    companion object {
        private const val PREF_NAME = "zen_daily_edition_prefs"
        private const val KEY_ENABLED = "key_daily_edition_enabled"
        private const val KEY_BATCH_SILENCE = "key_daily_edition_batch_silence"
        private const val KEY_MORNING_TIME = "key_morning_time"
        private const val KEY_EVENING_TIME = "key_evening_time"
    }
}
