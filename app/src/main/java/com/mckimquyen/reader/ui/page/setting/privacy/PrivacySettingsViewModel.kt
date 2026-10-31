package com.mckimquyen.reader.ui.page.setting.privacy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                _dohProvider.value = DohProviderPref.fromPreferences(preferences)
                _urlTrackingClean.value = UrlTrackingCleanPref.fromPreferences(preferences).value
                _htmlAntiTracker.value = HtmlAntiTrackerPref.fromPreferences(preferences).value
                _retractionRadar.value = RetractionRadarPref.fromPreferences(preferences).value
                _headlineSignals.value = HeadlineSignalsPref.fromPreferences(preferences).value
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
}
