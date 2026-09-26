package com.mckimquyen.reader.ui.page.setting.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import com.mckimquyen.reader.domain.model.filter.SmartFilterRule
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SmartFilterViewModel @Inject constructor(
    private val smartFilterManager: SmartFilterManager,
) : ViewModel() {

    val rules: StateFlow<List<SmartFilterRule>> = smartFilterManager.rules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    fun addRule(
        targetField: FilterTargetField,
        keyword: String,
        action: FilterAction,
    ): Boolean = smartFilterManager.addRule(targetField, keyword, action)

    fun removeRule(id: String) {
        smartFilterManager.removeRule(id)
    }

    fun toggleRule(id: String, isEnabled: Boolean) {
        smartFilterManager.toggleRule(id, isEnabled)
    }
}
