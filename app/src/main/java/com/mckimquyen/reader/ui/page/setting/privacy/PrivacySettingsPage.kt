package com.mckimquyen.reader.ui.page.setting.privacy

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
