package com.mckimquyen.reader.ui.page.setting.privacy

import com.mckimquyen.reader.infrastructure.android.AppOpenGuard
import com.mckimquyen.reader.infrastructure.android.AppOpenGuard.Companion.launchGuarded
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mckimquyen.reader.ui.ext.findActivity
import com.roy.sdkadbmob.AdManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.page.setting.SettingItem

@Composable
fun PrivacySettingsPage(
    navController: NavHostController,
    onBack: () -> Unit = { navController.popBackStack() },
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
) {
    val dohProvider by viewModel.dohProvider.collectAsState()
    val urlTrackingClean by viewModel.urlTrackingClean.collectAsState()
    val htmlAntiTracker by viewModel.htmlAntiTracker.collectAsState()
    val retractionRadar by viewModel.retractionRadar.collectAsState()
    val headlineSignals by viewModel.headlineSignals.collectAsState()
    val podcastStudioGeminiConsent by viewModel.podcastStudioGeminiConsent.collectAsState()
    val quizGeminiConsent by viewModel.quizGeminiConsent.collectAsState()
    val deepDiveGeminiConsent by viewModel.deepDiveGeminiConsent.collectAsState()
    val oracleClaimsGeminiConsent by viewModel.oracleClaimsGeminiConsent.collectAsState()
    val commuteScriptGeminiConsent by viewModel.commuteScriptGeminiConsent.collectAsState()
    val commuteListenMinuteOfDay by viewModel.commuteListenMinuteOfDay.collectAsState()
    val commuteAutoPlayOnHeadset by viewModel.commuteAutoPlayOnHeadset.collectAsState()

    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppOpenGuard.shared.onResult()
        permissionDenied = !granted
        if (granted) viewModel.setCommuteAutoPlayOnHeadset(true)
    }
    val onToggleAutoPlay: (Boolean) -> Unit = { turnOn ->
        if (!turnOn) {
            permissionDenied = false
            viewModel.setCommuteAutoPlayOnHeadset(false)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            viewModel.setCommuteAutoPlayOnHeadset(true)
        } else {
            // Asked only now, when the user turns the feature on; denying keeps the switch off.
            permissionLauncher.launchGuarded(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.privacy_settings_title),
                        desc = stringResource(R.string.privacy_settings_desc),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Subtitle(text = stringResource(R.string.doh_title))
                        Text(
                            text = stringResource(R.string.doh_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    PrivacyRadioItem(stringResource(R.string.doh_mode_system), dohProvider == DohProviderPref.SYSTEM) {
                        viewModel.setDohProvider(DohProviderPref.SYSTEM)
                    }
                }
                item {
                    PrivacyRadioItem(stringResource(R.string.doh_mode_cloudflare), dohProvider == DohProviderPref.CLOUDFLARE) {
                        viewModel.setDohProvider(DohProviderPref.CLOUDFLARE)
                    }
                }
                item {
                    PrivacyRadioItem(stringResource(R.string.doh_mode_adguard), dohProvider == DohProviderPref.ADGUARD) {
                        viewModel.setDohProvider(DohProviderPref.ADGUARD)
                    }
                }
                item {
                    // Ad consent: lets the user change the answer given in the first-start form (Google UMP privacy options). The SDK opens
                    // the form only where one is required and does nothing elsewhere; it needs a real Activity, so no Activity means no action.
                    val adActivity = remember(context) { context.findActivity() }
                    SettingItem(
                        title = stringResource(R.string.ad_consent_manage_title),
                        desc = stringResource(R.string.ad_consent_manage_desc),
                        onClick = { adActivity?.let { AdManager.showConsentFormIfAvailable(it) } },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.url_tracking_title),
                        desc = stringResource(R.string.url_tracking_desc),
                        onClick = { viewModel.setUrlTrackingClean(!urlTrackingClean) },
                        action = {
                            BaseSwitch(activated = urlTrackingClean) {
                                viewModel.setUrlTrackingClean(!urlTrackingClean)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.html_antitracker_title),
                        desc = stringResource(R.string.html_antitracker_desc),
                        onClick = { viewModel.setHtmlAntiTracker(!htmlAntiTracker) },
                        action = {
                            BaseSwitch(activated = htmlAntiTracker) {
                                viewModel.setHtmlAntiTracker(!htmlAntiTracker)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.retraction_radar_title),
                        desc = stringResource(R.string.retraction_radar_desc),
                        onClick = { viewModel.setRetractionRadar(!retractionRadar) },
                        action = {
                            BaseSwitch(activated = retractionRadar) {
                                viewModel.setRetractionRadar(!retractionRadar)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.headline_signals_title),
                        desc = stringResource(R.string.headline_signals_desc),
                        onClick = { viewModel.setHeadlineSignals(!headlineSignals) },
                        action = {
                            BaseSwitch(activated = headlineSignals) {
                                viewModel.setHeadlineSignals(!headlineSignals)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.podcast_studio_title),
                        desc = stringResource(R.string.podcast_studio_consent_message),
                        onClick = { viewModel.setPodcastStudioGeminiConsent(!podcastStudioGeminiConsent) },
                        action = {
                            BaseSwitch(activated = podcastStudioGeminiConsent) {
                                viewModel.setPodcastStudioGeminiConsent(!podcastStudioGeminiConsent)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.quiz_ai_title),
                        desc = stringResource(R.string.quiz_ai_consent_message),
                        onClick = { viewModel.setQuizGeminiConsent(!quizGeminiConsent) },
                        action = {
                            BaseSwitch(activated = quizGeminiConsent) {
                                viewModel.setQuizGeminiConsent(!quizGeminiConsent)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.deep_dive_title),
                        desc = stringResource(R.string.deep_dive_consent_message),
                        onClick = { viewModel.setDeepDiveGeminiConsent(!deepDiveGeminiConsent) },
                        action = {
                            BaseSwitch(activated = deepDiveGeminiConsent) {
                                viewModel.setDeepDiveGeminiConsent(!deepDiveGeminiConsent)
                            }
                        },
                    )
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.oracle_consent_title),
                        desc = stringResource(R.string.oracle_consent_message),
                        onClick = { viewModel.setOracleClaimsGeminiConsent(!oracleClaimsGeminiConsent) },
                        action = {
                            BaseSwitch(activated = oracleClaimsGeminiConsent) {
                                viewModel.setOracleClaimsGeminiConsent(!oracleClaimsGeminiConsent)
                            }
                        },
                    )
                }
                item {
                    (context as? Activity)?.let { activity ->
                        CommuteMorningSettingsRows(
                            consent = commuteScriptGeminiConsent,
                            listenMinuteOfDay = commuteListenMinuteOfDay,
                            autoPlay = commuteAutoPlayOnHeadset,
                            permissionDenied = permissionDenied,
                            activity = activity,
                            onConsent = viewModel::setCommuteScriptGeminiConsent,
                            onPickTime = { hour, minute -> viewModel.setCommuteListenMinuteOfDay(hour * 60 + minute) },
                            onAutoPlay = onToggleAutoPlay,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}

@Composable
private fun PrivacyRadioItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SettingItem(
        title = title,
        onClick = onSelect,
        action = { RadioButton(selected = selected, onClick = onSelect) },
    )
}
