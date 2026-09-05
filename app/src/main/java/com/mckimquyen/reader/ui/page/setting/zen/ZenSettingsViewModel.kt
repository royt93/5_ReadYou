package com.mckimquyen.reader.ui.page.setting.zen

import androidx.lifecycle.ViewModel
import com.mckimquyen.reader.domain.zen.ZenDailyEditionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ZenSettingsViewModel @Inject constructor(
    val zenDailyEditionManager: ZenDailyEditionManager
) : ViewModel()
