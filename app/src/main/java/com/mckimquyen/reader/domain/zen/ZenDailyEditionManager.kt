package com.mckimquyen.reader.domain.zen

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.sv.DailyEditionWorker
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenDailyEditionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val _isEnabled = MutableStateFlow(DEFAULT_ENABLED)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isBatchSilence = MutableStateFlow(DEFAULT_BATCH_SILENCE)
    val isBatchSilence: StateFlow<Boolean> = _isBatchSilence.asStateFlow()

    private val _morningTime = MutableStateFlow(DEFAULT_MORNING_TIME)
    val morningTime: StateFlow<String> = _morningTime.asStateFlow()

    private val _eveningTime = MutableStateFlow(DEFAULT_EVENING_TIME)
    val eveningTime: StateFlow<String> = _eveningTime.asStateFlow()

    @Volatile
    private var isLoaded = false

    init {
        // Load off the constructing thread (usually Main, when Hilt builds the graph) so the
        // SharedPreferences disk read never blocks startup. Flows emit the defaults until then.
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
    }

    /**
     * Loads persisted settings exactly once. Setters and synchronous readers call this first, so
     * a late async load can never overwrite a value the user just set, and a caller deciding on
     * notification silencing never acts on the defaults instead of the stored settings.
     */
    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        _isBatchSilence.value = prefs.getBoolean(KEY_BATCH_SILENCE, DEFAULT_BATCH_SILENCE)
        _morningTime.value = prefs.getString(KEY_MORNING_TIME, DEFAULT_MORNING_TIME) ?: DEFAULT_MORNING_TIME
        _eveningTime.value = prefs.getString(KEY_EVENING_TIME, DEFAULT_EVENING_TIME) ?: DEFAULT_EVENING_TIME
        isLoaded = true
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
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
        ensureLoaded()
        prefs.edit().putBoolean(KEY_BATCH_SILENCE, silence).apply()
        _isBatchSilence.value = silence
    }

    fun shouldSilenceImmediateNotification(): Boolean {
        ensureLoaded()
        return _isEnabled.value && _isBatchSilence.value
    }

    companion object {
        private const val PREF_NAME = "zen_daily_edition_prefs"
        private const val KEY_ENABLED = "key_daily_edition_enabled"
        private const val KEY_BATCH_SILENCE = "key_daily_edition_batch_silence"
        private const val KEY_MORNING_TIME = "key_morning_time"
        private const val KEY_EVENING_TIME = "key_evening_time"

        private const val DEFAULT_ENABLED = false
        private const val DEFAULT_BATCH_SILENCE = true
        private const val DEFAULT_MORNING_TIME = "07:00"
        private const val DEFAULT_EVENING_TIME = "20:00"
    }
}
