package com.mckimquyen.reader.ui.page.setting.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.infrastructure.backup.WebDavBackupManager
import com.mckimquyen.reader.infrastructure.backup.WebDavConfig
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupOpState {
    object Idle : BackupOpState()
    object InProgress : BackupOpState()
    data class Success(val message: String) : BackupOpState()
    data class Error(val error: String) : BackupOpState()
}

@HiltViewModel
class WebDavBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavBackupManager: WebDavBackupManager,
) : ViewModel() {

    private val _config = MutableStateFlow(webDavBackupManager.getConfig())
    val config: StateFlow<WebDavConfig> = _config.asStateFlow()

    private val _opState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val opState: StateFlow<BackupOpState> = _opState.asStateFlow()

    fun saveConfig(serverUrl: String, username: String, password: String) {
        webDavBackupManager.saveConfig(serverUrl, username, password)
        _config.value = webDavBackupManager.getConfig()
    }

    fun backup() {
        _opState.value = BackupOpState.InProgress
        viewModelScope.launch {
            val result = webDavBackupManager.backup(context.currentAccountId)
            _config.value = webDavBackupManager.getConfig()
            _opState.value = result.fold(
                onSuccess = { BackupOpState.Success("SUCCESS") },
                onFailure = { BackupOpState.Error(it.message ?: "Backup failed") }
            )
        }
    }

    fun restore() {
        _opState.value = BackupOpState.InProgress
        viewModelScope.launch {
            val result = webDavBackupManager.restore()
            _opState.value = result.fold(
                onSuccess = { BackupOpState.Success("SUCCESS") },
                onFailure = { BackupOpState.Error(it.message ?: "Restore failed") }
            )
        }
    }

    fun resetOpState() {
        _opState.value = BackupOpState.Idle
    }
}
