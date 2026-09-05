package com.mckimquyen.reader.ui.page.setting.language

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jakewharton.processphoenix.ProcessPhoenix
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LanguagesPref
import com.mckimquyen.reader.infrastructure.pref.LocalLanguages
import com.mckimquyen.reader.infrastructure.pref.OpenLinkPref
import com.mckimquyen.reader.ui.component.base.Banner
import com.mckimquyen.reader.ui.component.base.DisplayText
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.ext.openURL
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.theme.palette.onLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesPage(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val savedLanguage = remember {
        val sp = context.getSharedPreferences("locale_prefs", android.content.Context.MODE_PRIVATE)
        val langVal = sp.getInt("languages", -1)
        if (langVal != -1) LanguagesPref.fromValue(langVal) else null
    }
    val currentLanguage = savedLanguage ?: LocalLanguages.current
    val scope = rememberCoroutineScope()

    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf<LanguagesPref?>(null) }
    var isRestarting by remember { mutableStateOf(false) }

    // Loading Dialog
    if (isRestarting) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal */ },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.restarting_app),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = { },
            dismissButton = { }
        )
    }

    // Confirmation Dialog
    if (showDialog && pendingLanguage != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                selectedLanguage = currentLanguage // Revert selection
                pendingLanguage = null
            },
            title = {
                Text(text = stringResource(R.string.change_language))
            },
            text = {
                Text(text = stringResource(R.string.change_language_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pendingLanguage
                        showDialog = false
                        pendingLanguage = null
                        if (target != null) {
                            selectedLanguage = target
                            isRestarting = true
                            scope.launch {
                                target.put(context, scope)
                                delay(300)
                                ProcessPhoenix.triggerRebirth(context)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        selectedLanguage = currentLanguage // Revert selection
                        pendingLanguage = null
                    }
                ) {
                    Text(stringResource(R.string.no))
                }
            }
        )
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
                    LanguagesPref.values.map { languagePref ->
                        SettingItem(
                            title = languagePref.toDesc(context),
                            onClick = {
                                if (languagePref != currentLanguage) {
                                    selectedLanguage = languagePref
                                    pendingLanguage = languagePref
                                    showDialog = true
                                }
                            },
                        ) {
                            RadioButton(
                                selected = languagePref == selectedLanguage,
                                onClick = {
                                    if (languagePref != currentLanguage) {
                                        selectedLanguage = languagePref
                                        pendingLanguage = languagePref
                                        showDialog = true
                                    }
                                }
                            )
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
