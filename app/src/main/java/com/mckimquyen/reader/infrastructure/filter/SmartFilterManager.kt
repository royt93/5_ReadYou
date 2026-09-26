package com.mckimquyen.reader.infrastructure.filter

import android.content.Context
import com.mckimquyen.reader.domain.filter.SmartFilterEngine
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import com.mckimquyen.reader.domain.model.filter.SmartFilterRule
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartFilterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val _rules = MutableStateFlow<List<SmartFilterRule>>(emptyList())
    val rules: StateFlow<List<SmartFilterRule>> = _rules.asStateFlow()

    private val loadLock = Any()

    @Volatile
    private var isLoaded = false

    init {
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (isLoaded) return
        synchronized(loadLock) {
            if (isLoaded) return
            _rules.value = readRules()
            isLoaded = true
        }
    }

    private fun readRules(): List<SmartFilterRule> {
        val jsonStr = prefs.getString(KEY_RULES, null)
        if (jsonStr.isNullOrBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<SmartFilterRule>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val targetField = runCatching {
                    FilterTargetField.valueOf(obj.optString("targetField", FilterTargetField.TITLE.name))
                }.getOrDefault(FilterTargetField.TITLE)
                val action = runCatching {
                    FilterAction.valueOf(obj.optString("action", FilterAction.MARK_READ.name))
                }.getOrDefault(FilterAction.MARK_READ)

                list.add(
                    SmartFilterRule(
                        id = obj.optString("id"),
                        targetField = targetField,
                        keyword = obj.optString("keyword"),
                        action = action,
                        isEnabled = obj.optBoolean("isEnabled", true),
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRules(list: List<SmartFilterRule>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("targetField", item.targetField.name)
                    put("keyword", item.keyword)
                    put("action", item.action.name)
                    put("isEnabled", item.isEnabled)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_RULES, jsonArray.toString()).apply()
            _rules.value = list
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun addRule(
        targetField: FilterTargetField,
        keyword: String,
        action: FilterAction,
    ): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return false

        ensureLoaded()
        val current = _rules.value
        if (current.any { it.keyword.equals(trimmed, ignoreCase = true) && it.targetField == targetField && it.action == action }) {
            return false
        }

        val updated = current + SmartFilterRule(
            targetField = targetField,
            keyword = trimmed,
            action = action,
        )
        saveRules(updated)
        return true
    }

    fun removeRule(id: String) {
        ensureLoaded()
        val updated = _rules.value.filter { it.id != id }
        saveRules(updated)
    }

    fun toggleRule(id: String, isEnabled: Boolean) {
        ensureLoaded()
        val updated = _rules.value.map {
            if (it.id == id) it.copy(isEnabled = isEnabled) else it
        }
        saveRules(updated)
    }

    fun applyRules(articles: List<Article>): List<Article> {
        ensureLoaded()
        return SmartFilterEngine.applyAll(articles, _rules.value)
    }

    companion object {
        private const val PREFS_NAME = "smart_filter_prefs"
        private const val KEY_RULES = "smart_filter_rules_json"
    }
}
