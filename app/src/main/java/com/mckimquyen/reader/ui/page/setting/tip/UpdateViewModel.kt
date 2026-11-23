package com.mckimquyen.reader.ui.page.setting.tip

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.sv.AppSv
import com.mckimquyen.reader.infrastructure.net.Download
import com.mckimquyen.reader.ui.ext.UPDATE_CHECK_ENABLED
import com.mckimquyen.reader.ui.ext.notFdroid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appService: AppSv,
) : ViewModel() {

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    fun checkUpdate(
        preProcessor: suspend () -> Unit = {},
        postProcessor: suspend (Boolean) -> Unit = {},
    ) {
        if (notFdroid && UPDATE_CHECK_ENABLED) {
            viewModelScope.launch {
                preProcessor()
                appService.checkUpdate().let {
                    it?.let {
                        if (it) {
                            showDialog()
                        } else {
                            hideDialog()
                        }
                        postProcessor(it)
                    }
                }
            }
        }
    }

    fun showDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = true
            )
        }
    }

    fun hideDialog() {
        _updateUiState.update {
            it.copy(
                updateDialogVisible = false
            )
        }
    }

    fun downloadUpdate(url: String) {
        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    downloadFlow = flow { emit(Download.Progress(0)) }
                )
            }
            _updateUiState.update {
                it.copy(
                    downloadFlow = appService.downloadFile(url)
                )
            }
        }
    }
}

@Keep
data class UpdateUiState(
    val updateDialogVisible: Boolean = false,
    val downloadFlow: Flow<Download> = emptyFlow(),
)
