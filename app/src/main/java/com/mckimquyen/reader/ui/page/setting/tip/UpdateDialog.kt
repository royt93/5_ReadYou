package com.mckimquyen.reader.ui.page.setting.tip

import com.mckimquyen.reader.infrastructure.android.AppOpenGuard
import com.mckimquyen.reader.infrastructure.android.AppOpenGuard.Companion.launchGuarded
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.net.Download
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionLog
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionNumber
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionPublishDate
import com.mckimquyen.reader.infrastructure.pref.LocalNewVersionSize
import com.mckimquyen.reader.infrastructure.pref.OpenLinkPref
import com.mckimquyen.reader.infrastructure.pref.SkipVersionNumberPref
import com.mckimquyen.reader.ui.component.base.BaseDlg
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.installLatestApk
import com.mckimquyen.reader.ui.ext.openURL
import kotlinx.coroutines.Dispatchers

@Composable
fun UpdateDialog(
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val updateUiState = updateViewModel.updateUiState.collectAsStateValue()
    val downloadState = updateUiState.downloadFlow.collectAsState(initial = Download.NotYet).value
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val newVersionNumber = LocalNewVersionNumber.current
    val newVersionPublishDate = LocalNewVersionPublishDate.current
    val newVersionLog = LocalNewVersionLog.current
    val newVersionSize = LocalNewVersionSize.current

    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        AppOpenGuard.shared.onResult()
        context.installLatestApk()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        AppOpenGuard.shared.onResult()
        if (result) {
            context.installLatestApk()
        } else {
            settings.launchGuarded(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    BaseDlg(
        modifier = Modifier.heightIn(max = 400.dp),
        visible = updateUiState.updateDialogVisible,
        onDismissRequest = { updateViewModel.hideDialog() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Update,
                contentDescription = stringResource(R.string.change_log),
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(R.string.change_log))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$newVersionPublishDate $newVersionSize",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        text = {
            SelectionContainer {
                Text(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    text = newVersionLog,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.openURL(
                        "${context.getString(R.string.github_link)}/releases/latest",
                        OpenLinkPref.AutoPreferCustomTabs
                    )
                    // Disable automatic updates in F-Droid
//                    if (downloadState !is Download.Progress) {
//                        updateViewModel.dispatch(
//                            UpdateViewAction.DownloadUpdate(
//                                url = context.newVersionDownloadUrl,
//                            )
//                        )
//                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.update) + when (downloadState) {
                        is Download.NotYet -> ""
                        is Download.Progress -> " ${downloadState.percent}%"
                        is Download.Finished -> {
                            if (context.packageManager.canRequestPackageInstalls()) {
                                Log.i(
                                    "RLog",
                                    "Download.Finished: ${downloadState.file.absolutePath}"
                                )
                                context.installLatestApk()
                            } else {
                                launcher.launchGuarded(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                            }
                            ""
                        }
                    }
                )
            }
        },
        dismissButton = {
            if (downloadState !is Download.Progress) {
                TextButton(
                    onClick = {
                        SkipVersionNumberPref.put(context, scope, newVersionNumber.toString())
                        updateViewModel.hideDialog()
                    }
                ) {
                    Text(text = stringResource(R.string.skip_this_version))
                }
            }
        },
    )
}
