package com.mckimquyen.reader.ui.page.home.flow

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.general.MarkAsReadConditions
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlowViewModel @Inject constructor(
    private val rssService: RssSv,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _flowUiState = MutableStateFlow(FlowUiState())
    val flowUiState: StateFlow<FlowUiState> = _flowUiState.asStateFlow()

    fun sync() {
        viewModelScope.launch(ioDispatcher) {
            rssService.get().doSync()
        }
    }

    fun markAsRead(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        conditions: MarkAsReadConditions,
    ) {
        viewModelScope.launch {
            rssService.get().markAsRead(
                groupId = groupId,
                feedId = feedId,
                articleId = articleId,
                before = conditions.toDate(),
                isUnread = false,
            )
        }
    }
}

@Keep
data class FlowUiState(
    val filterImportant: Int = 0,
    val isBack: Boolean = false,
    val syncWorkInfo: String = "",
)
