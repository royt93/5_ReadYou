package com.mckimquyen.reader.ui.page.setting.privacy

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.commute.CommuteSchedulePolicy
import com.mckimquyen.reader.domain.sv.CommuteWorker
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import com.mckimquyen.reader.infrastructure.pref.HeadlineSignalsPref
import com.mckimquyen.reader.infrastructure.pref.HtmlAntiTrackerPref
import com.mckimquyen.reader.infrastructure.pref.RetractionRadarPref
import com.mckimquyen.reader.infrastructure.pref.UrlTrackingCleanPref
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _dohProvider = MutableStateFlow(DohProviderPref.default)
    val dohProvider = _dohProvider.asStateFlow()

    private val _urlTrackingClean = MutableStateFlow(UrlTrackingCleanPref.default.value)
    val urlTrackingClean = _urlTrackingClean.asStateFlow()

    private val _htmlAntiTracker = MutableStateFlow(HtmlAntiTrackerPref.default.value)
    val htmlAntiTracker = _htmlAntiTracker.asStateFlow()

    private val _retractionRadar = MutableStateFlow(RetractionRadarPref.default.value)
    val retractionRadar = _retractionRadar.asStateFlow()

    private val _headlineSignals = MutableStateFlow(HeadlineSignalsPref.default.value)
    val headlineSignals = _headlineSignals.asStateFlow()

    // EXC-02: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _podcastStudioGeminiConsent = MutableStateFlow(false)
    val podcastStudioGeminiConsent = _podcastStudioGeminiConsent.asStateFlow()

    // RPG-02: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _quizGeminiConsent = MutableStateFlow(false)
    val quizGeminiConsent = _quizGeminiConsent.asStateFlow()

    // FRONT-01: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _deepDiveGeminiConsent = MutableStateFlow(false)
    val deepDiveGeminiConsent = _deepDiveGeminiConsent.asStateFlow()

    // ORACLE-01: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _oracleClaimsGeminiConsent = MutableStateFlow(false)
    val oracleClaimsGeminiConsent = _oracleClaimsGeminiConsent.asStateFlow()

    // STRAT-01: true only when the user agreed to Gemini writing the morning episode; "never asked" reads as off.
    private val _commuteScriptGeminiConsent = MutableStateFlow(false)
    val commuteScriptGeminiConsent = _commuteScriptGeminiConsent.asStateFlow()

    private val _commuteListenMinuteOfDay = MutableStateFlow(CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY)
    val commuteListenMinuteOfDay = _commuteListenMinuteOfDay.asStateFlow()

    private val _commuteAutoPlayOnHeadset = MutableStateFlow(false)
    val commuteAutoPlayOnHeadset = _commuteAutoPlayOnHeadset.asStateFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                _dohProvider.value = DohProviderPref.fromPreferences(preferences)
                _urlTrackingClean.value = UrlTrackingCleanPref.fromPreferences(preferences).value
                _htmlAntiTracker.value = HtmlAntiTrackerPref.fromPreferences(preferences).value
                _retractionRadar.value = RetractionRadarPref.fromPreferences(preferences).value
                _headlineSignals.value = HeadlineSignalsPref.fromPreferences(preferences).value
                _podcastStudioGeminiConsent.value = preferences[DataStoreKeys.PodcastStudioGeminiConsent.key] == true
                _quizGeminiConsent.value = preferences[DataStoreKeys.QuizGeminiConsent.key] == true
                _deepDiveGeminiConsent.value = preferences[DataStoreKeys.DeepDiveGeminiConsent.key] == true
                _oracleClaimsGeminiConsent.value = preferences[DataStoreKeys.OracleClaimsGeminiConsent.key] == true
                _commuteScriptGeminiConsent.value = preferences[DataStoreKeys.CommuteScriptGeminiConsent.key] == true
                _commuteListenMinuteOfDay.value =
                    CommuteSchedulePolicy.clampListenMinute(preferences[DataStoreKeys.CommuteListenMinuteOfDay.key])
                _commuteAutoPlayOnHeadset.value = preferences[DataStoreKeys.CommuteAutoPlayOnHeadset.key] == true
            }
        }
    }

    fun setDohProvider(provider: DohProviderPref) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.DohProvider.key] = provider.id }
            }
        }
    }

    fun setUrlTrackingClean(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.UrlTrackingClean.key] = enabled }
            }
        }
    }

    fun setHtmlAntiTracker(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.HtmlAntiTracker.key] = enabled }
            }
        }
    }

    fun setHeadlineSignals(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.HeadlineSignals.key] = enabled }
            }
        }
    }

    fun setRetractionRadar(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.RetractionRadar.key] = enabled }
            }
        }
    }

    fun setPodcastStudioGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.PodcastStudioGeminiConsent.key] = agreed }
            }
        }
    }

    fun setQuizGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.QuizGeminiConsent.key] = agreed }
            }
        }
    }

    fun setDeepDiveGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.DeepDiveGeminiConsent.key] = agreed }
            }
        }
    }

    fun setOracleClaimsGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.OracleClaimsGeminiConsent.key] = agreed }
            }
        }
    }

    fun setCommuteScriptGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.CommuteScriptGeminiConsent.key] = agreed }
            }
        }
    }

    fun setCommuteListenMinuteOfDay(minute: Int) {
        if (minute !in 0 until CommuteSchedulePolicy.MINUTES_PER_DAY) return
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.CommuteListenMinuteOfDay.key] = minute }
            }
            // The saved time must survive a scheduling failure, so the reschedule comes second and cannot undo it.
            // runCatching is safe here: enqueueDailyWork is not a suspend function, so no cancellation is swallowed.
            runCatching {
                CommuteWorker.enqueueDailyWork(WorkManager.getInstance(context), minute, ExistingPeriodicWorkPolicy.REPLACE)
            }.onFailure { Log.w("PrivacySettingsVM", "Could not reschedule: ${it.javaClass.simpleName}") }
        }
    }

    fun setCommuteAutoPlayOnHeadset(on: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.CommuteAutoPlayOnHeadset.key] = on }
            }
        }
    }
}
