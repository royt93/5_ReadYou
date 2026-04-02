package com.mckimquyen.reader.ui.page.setting.color

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mckimquyen.reader.infrastructure.pref.LocalAmoledUnlocked
import com.mckimquyen.reader.infrastructure.pref.AmoledUnlockedPref
import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.DarkThemePref
import com.mckimquyen.reader.infrastructure.pref.LocalAmoledDarkTheme
import com.mckimquyen.reader.infrastructure.pref.LocalDarkTheme
import com.mckimquyen.reader.infrastructure.pref.not
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import com.mckimquyen.reader.ui.component.base.Subtitle
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.theme.palette.onLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkThemePage(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkTheme.current
    val amoledDarkTheme = LocalAmoledDarkTheme.current
    val amoledUnlocked = LocalAmoledUnlocked.current
    val scope = rememberCoroutineScope()
    var showRewardDialog by remember { mutableStateOf(false) }

    val handleAmoledToggle: () -> Unit = {
        if (amoledUnlocked.value) {
            (!amoledDarkTheme).put(context, scope)
        } else {
            showRewardDialog = true
        }
    }

    if (showRewardDialog) {
        AlertDialog(
            onDismissRequest = { showRewardDialog = false },
            title = { Text(stringResource(id = R.string.unlock_amoled_theme)) },
            text = { Text(stringResource(id = R.string.unlock_amoled_theme_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showRewardDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        com.roy.sdkadbmob.AdManager.showInterstitial(activity) { success ->
                            android.util.Log.d("roy93~Ad", "showInterstitial callback: success=$success")
                            if (success) {
                                kotlinx.coroutines.GlobalScope.launch {
                                    AmoledUnlockedPref.ON.put(context, this)
                                    com.mckimquyen.reader.infrastructure.pref.AmoledDarkThemePref.ON.put(context, this)
                                    com.mckimquyen.reader.infrastructure.pref.DarkThemePref.ON.put(context, this)
                                }
                                android.util.Log.d("roy93~Ad", "Amoled theme unlocked and activated with GlobalScope!")
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.ad_not_ready), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text(stringResource(id = R.string.watch_ad))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRewardDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    BaseScaffold(
        // ... (keep the rest identical except handleAmoledToggle integration)
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
                    DisplayText(text = stringResource(R.string.dark_theme), desc = "")
                }
                item {
                    DarkThemePref.values.map {
                        SettingItem(
                            title = it.toDesc(context),
                            onClick = {
                                it.put(context, scope)
                            },
                        ) {
                            RadioButton(selected = it == darkTheme, onClick = {
                                it.put(context, scope)
                            })
                        }
                    }
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.other),
                    )
                    SettingItem(
                        title = stringResource(R.string.amoled_dark_theme),
                        onClick = handleAmoledToggle,
                    ) {
                        BaseSwitch(activated = amoledDarkTheme.value) {
                            handleAmoledToggle()
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}
