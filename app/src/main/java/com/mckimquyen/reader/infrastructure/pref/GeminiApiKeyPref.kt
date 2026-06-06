package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gemini API key do người dùng tự nhập (lưu trong DataStore, KHÔNG nằm trong APK).
 * Khi gọi AI Summary, key của user sẽ được ưu tiên; nếu để trống thì fallback về
 * [com.mckimquyen.reader.BuildConfig.GEMINI_API_KEY] (key dev nhúng sẵn).
 */
object GeminiApiKeyPref {

    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.GeminiApiKey, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKeys.GeminiApiKey.key] ?: default
}
