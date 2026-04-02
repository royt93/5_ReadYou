package com.mckimquyen.reader.ui.page.setting.acc

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.KeepArchivedPreference
import com.mckimquyen.reader.infrastructure.pref.SyncBlockListPref
import com.mckimquyen.reader.infrastructure.pref.SyncIntervalPref
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseDlg
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import com.mckimquyen.reader.ui.component.base.RadioDlg
import com.mckimquyen.reader.ui.component.base.RadioDialogOption
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.component.base.TextFieldDlg
import com.mckimquyen.reader.ui.component.base.Tips
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.showToast
import com.mckimquyen.reader.ui.ext.showToastLong
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.page.setting.acc.connection.AccountConnection
import com.mckimquyen.reader.ui.theme.palette.onLight

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AccountDetailsPage(
    navController: NavHostController = rememberNavController(),
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState = viewModel.accountUiState.collectAsStateValue()
    val context = LocalContext.current

    val selectedAccount = uiState.selectedAccount.collectAsStateValue(initial = null)

    var nameValue by remember { mutableStateOf(selectedAccount?.name) }
    var nameDialogVisible by remember { mutableStateOf(false) }
    var blockListValue by remember {
        mutableStateOf(
            SyncBlockListPref.toString(
                selectedAccount?.syncBlockList
                    ?: SyncBlockListPref.default
            )
        )
    }
    var blockListDialogVisible by remember { mutableStateOf(false) }
    var syncIntervalDialogVisible by remember { mutableStateOf(false) }
    var keepArchivedDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect {
            it.arguments?.getString("accountId")?.let {
                viewModel.initData(it.toInt())
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml")
    ) { result ->
        viewModel.exportAsOPML(selectedAccount!!.id!!) { string ->
            result?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(string.toByteArray())
                }
            }
        }
    }

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
                    DisplayText(text = selectedAccount?.type?.toDesc(context) ?: "", desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.display),
                    )
                    SettingItem(
                        title = stringResource(R.string.name),
                        desc = selectedAccount?.name ?: "",
                        onClick = {
                            nameValue = selectedAccount?.name
                            nameDialogVisible = true
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                if (selectedAccount != null) {
                    item {
                        AccountConnection(account = selectedAccount)
                    }
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.synchronous),
                    )
                    SettingItem(
                        title = stringResource(R.string.sync_interval),
                        desc = selectedAccount?.syncInterval?.toDesc(context),
                        onClick = { syncIntervalDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.sync_once_on_start),
                        onClick = {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnStart).put(it, viewModel)
                            }
                        },
                    ) {
                        BaseSwitch(activated = selectedAccount?.syncOnStart?.value == true) {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnStart).put(it, viewModel)
                            }
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.only_on_wifi),
                        onClick = {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnlyOnWiFi).put(it, viewModel)
                            }
                        },
                    ) {
                        BaseSwitch(activated = selectedAccount?.syncOnlyOnWiFi?.value == true) {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnlyOnWiFi).put(it, viewModel)
                            }
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.only_when_charging),
                        onClick = {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnlyWhenCharging).put(it, viewModel)
                            }
                        },
                    ) {
                        BaseSwitch(activated = selectedAccount?.syncOnlyWhenCharging?.value == true) {
                            selectedAccount?.id?.let {
                                (!selectedAccount.syncOnlyWhenCharging).put(it, viewModel)
                            }
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.keep_archived_articles),
                        desc = selectedAccount?.keepArchived?.toDesc(context),
                        onClick = { keepArchivedDialogVisible = true },
                    ) {}
                    // SettingItem(
                    //     title = stringResource(R.string.block_list),
                    //     onClick = { blockListDialogVisible = true },
                    // ) {}
                    Tips(text = stringResource(R.string.synchronous_tips))
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.advanced),
                    )
                    SettingItem(
                        title = stringResource(R.string.export_as_opml),
                        onClick = {
                            launcher.launch("ReadYou.opml")
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.clear_all_articles),
                        onClick = { viewModel.showClearDialog() },
                    ) {}
//                    SettingItem(
//                        title = stringResource(R.string.delete_account),
//                        onClick = { viewModel.showDeleteDialog() },
//                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    TextFieldDlg(
        visible = nameDialogVisible,
        title = stringResource(R.string.name),
        value = nameValue ?: "",
        placeholder = stringResource(R.string.value),
        onValueChange = {
            nameValue = it
        },
        onDismissRequest = {
            nameDialogVisible = false
        },
        onConfirm = {
            if (nameValue?.isNotBlank() == true) {
                selectedAccount?.id?.let {
                    viewModel.update(it) {
                        name = nameValue ?: ""
                    }
                }
                nameDialogVisible = false
            }
        }
    )

    RadioDlg(
        visible = syncIntervalDialogVisible,
        title = stringResource(R.string.sync_interval),
        options = SyncIntervalPref.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == selectedAccount?.syncInterval,
            ) {
                selectedAccount?.id?.let { accountId ->
                    it.put(accountId, viewModel)
                }
            }
        }
    ) {
        syncIntervalDialogVisible = false
    }

    RadioDlg(
        visible = keepArchivedDialogVisible,
        title = stringResource(R.string.keep_archived_articles),
        options = KeepArchivedPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == selectedAccount?.keepArchived,
            ) {
                selectedAccount?.id?.let { accountId ->
                    it.put(accountId, viewModel)
                }
            }
        }
    ) {
        keepArchivedDialogVisible = false
    }

    TextFieldDlg(
        visible = blockListDialogVisible,
        title = stringResource(R.string.block_list),
        value = blockListValue,
        singleLine = false,
        placeholder = stringResource(R.string.value),
        onValueChange = {
            blockListValue = it
        },
        onDismissRequest = {
            blockListDialogVisible = false
        },
        onConfirm = {
            selectedAccount?.id?.let {
                SyncBlockListPref.put(it, viewModel, selectedAccount.syncBlockList)
                blockListDialogVisible = false
                context.showToast(selectedAccount.syncBlockList.toString())
            }
        }
    )

    BaseDlg(
        visible = uiState.clearDialogVisible,
        onDismissRequest = {
            viewModel.hideClearDialog()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.clear_all_articles),
            )
        },
        title = {
            Text(text = stringResource(R.string.clear_all_articles))
        },
        text = {
            Text(text = stringResource(R.string.clear_all_articles_tips))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedAccount?.let {
                        viewModel.clear(it) {
                            viewModel.hideClearDialog()
                            context.showToastLong(context.getString(R.string.clear_all_articles_toast))
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.clear),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.hideClearDialog()
                }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )

    BaseDlg(
        visible = uiState.deleteDialogVisible,
        onDismissRequest = {
            viewModel.hideDeleteDialog()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.PersonOff,
                contentDescription = stringResource(R.string.delete_account),
            )
        },
        title = {
            Text(text = stringResource(R.string.delete_account))
        },
        text = {
            Text(text = stringResource(R.string.delete_account_tips))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedAccount?.id?.let {
                        viewModel.delete(it) {
                            navController.popBackStack()
                            context.showToastLong(context.getString(R.string.delete_account_toast))
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.delete),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.hideDeleteDialog()
                }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )
}
