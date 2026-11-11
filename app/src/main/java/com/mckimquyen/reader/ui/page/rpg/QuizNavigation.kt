package com.mckimquyen.reader.ui.page.rpg

import androidx.compose.runtime.compositionLocalOf

/** Opens Privacy settings from the quiz card; a no-op where navigation is unavailable (previews, tests). */
val LocalOpenPrivacySettings = compositionLocalOf<() -> Unit> { {} }
