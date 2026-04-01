package com.mckimquyen.reader.ui.page.setting.interaction

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.InitialFilterPref
import com.mckimquyen.reader.infrastructure.pref.InitialPagePref
import com.mckimquyen.reader.infrastructure.pref.LocalInitialFilter
import com.mckimquyen.reader.infrastructure.pref.LocalInitialPage
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLink
import com.mckimquyen.reader.infrastructure.pref.LocalOpenLinkSpecificBrowser
import com.mckimquyen.reader.infrastructure.pref.OpenLinkPref
import com.mckimquyen.reader.infrastructure.pref.LocalAutoTts
import com.mckimquyen.reader.infrastructure.pref.AutoTtsPref
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.RadioDlg
import com.mckimquyen.reader.ui.component.base.RadioDialogOption
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.ext.getBrowserAppList
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.theme.palette.onLight

@Composable
fun InteractionPage(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val initialPage = LocalInitialPage.current
    val initialFilter = LocalInitialFilter.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val autoTts = LocalAutoTts.current
    val scope = rememberCoroutineScope()
    val isOpenLinkSpecificBrowserItemEnabled = remember(openLink) {
        openLink == OpenLinkPref.SpecificBrowser
    }
    var initialPageDialogVisible by remember { mutableStateOf(false) }
    var initialFilterDialogVisible by remember { mutableStateOf(false) }
    var openLinkDialogVisible by remember { mutableStateOf(false) }
    var openLinkSpecificBrowserDialogVisible by remember { mutableStateOf(false) }

    BaseScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                navController.popBackStack()
            }
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.interaction), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.on_start),
                    )
                    SettingItem(
                        title = stringResource(R.string.initial_page),
                        desc = initialPage.toDesc(context),
                        onClick = {
                            initialPageDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.initial_filter),
                        desc = initialFilter.toDesc(context),
                        onClick = {
                            initialFilterDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.initial_open_app),
                        desc = openLink.toDesc(context),
                        onClick = {
                            openLinkDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.open_link_specific_browser),
                        desc = openLinkSpecificBrowser.toDesc(context),
                        enable = isOpenLinkSpecificBrowserItemEnabled,
                        onClick = {

                            if (isOpenLinkSpecificBrowserItemEnabled) {
                                openLinkSpecificBrowserDialogVisible = true
                            }
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.auto_tts),
                        desc = stringResource(R.string.auto_tts_desc),
                        icon = Icons.Outlined.VolumeUp,
                        action = {
                            Switch(
                                checked = autoTts.value,
                                onCheckedChange = { autoTts.not().put(context, scope) }
                            )
                        },
                        onClick = {
                            autoTts.not().put(context, scope)
                        },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDlg(
        visible = initialPageDialogVisible,
        title = stringResource(R.string.initial_page),
        options = InitialPagePref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == initialPage,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        initialPageDialogVisible = false
    }

    RadioDlg(
        visible = initialFilterDialogVisible,
        title = stringResource(R.string.initial_filter),
        options = InitialFilterPref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == initialFilter,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        initialFilterDialogVisible = false
    }

    RadioDlg(
        visible = openLinkDialogVisible,
        title = stringResource(R.string.initial_open_app),
        options = OpenLinkPref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == openLink,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        openLinkDialogVisible = false
    }

    val browserList = remember(context) {
        context.getBrowserAppList()
    }

    RadioDlg(
        visible = openLinkSpecificBrowserDialogVisible,
        title = stringResource(R.string.open_link_specific_browser),
        options = browserList.map {
            RadioDialogOption(
                text = it.loadLabel(context.packageManager).toString(),
                selected = it.activityInfo.packageName == openLinkSpecificBrowser.packageName,
            ) {
                openLinkSpecificBrowser.copy(packageName = it.activityInfo.packageName).put(context, scope)
            }
        },
        onDismissRequest = {
            openLinkSpecificBrowserDialogVisible = false
        }
    )

}
