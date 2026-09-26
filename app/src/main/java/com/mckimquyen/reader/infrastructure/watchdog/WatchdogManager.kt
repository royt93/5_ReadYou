package com.mckimquyen.reader.infrastructure.watchdog

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
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
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quản lý toàn bộ vòng đời của tính năng Chó Săn Cảnh Báo Từ Khóa Khẩn Cấp (Keyword Watchdog).
 * Lưu trữ danh sách từ khóa theo dõi, phát hiện bài viết mới khớp từ khóa trong chu kỳ sync và bắn High-Priority Notification.
 */
@Singleton
class WatchdogManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchdogEngine: WatchdogEngine,
    private val notificationHelper: NotificationHelper,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val _keywords = MutableStateFlow<List<WatchdogKeyword>>(emptyList())
    val keywords: StateFlow<List<WatchdogKeyword>> = _keywords.asStateFlow()

    // Serializes every read-modify-write mutation (ensureLoaded + snapshot + transform + save) so
    // a UI-thread edit (addKeyword/removeKeyword/toggleKeyword) and a background SyncWorker
    // increment (incrementMatchCount) can never interleave and lose one another's update.
    // No suspension ever happens inside this lock — every guarded function is a plain blocking
    // call — so `synchronized` here is safe with coroutines.
    private val mutationLock = Any()

    @Volatile
    private var isLoaded = false

    init {
        // Load off the constructing thread (usually Main, when Hilt builds the graph) so the
        // SharedPreferences disk read never blocks startup. `keywords` emits emptyList() until then.
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
    }

    /**
     * Loads persisted keywords exactly once. Every mutation calls this first: if the user mutates
     * before the async load finished, the persisted list is loaded (synchronously, rare path)
     * before being modified, so saving never overwrites stored keywords with a partial list.
     *
     * Must be called while holding [mutationLock] from every mutator; [checkArticle] also calls it
     * standalone (read-only path, no lock needed there).
     */
    private fun ensureLoaded() {
        if (isLoaded) return
        synchronized(mutationLock) {
            if (isLoaded) return
            _keywords.value = readKeywords()
            isLoaded = true
        }
    }

    /**
     * Reads the primary key, falling back to the last-known-good backup if the primary is corrupt
     * (e.g. process was killed mid-write). Never silently wipes existing data: a fully unreadable
     * primary+backup pair only happens when no data was ever saved, in which case an empty list is
     * the correct, honest result — not data loss.
     */
    private fun readKeywords(): List<WatchdogKeyword> {
        val primary = prefs.getString(KEY_WATCHDOG_LIST, null)
        parseKeywordsJson(primary)?.let { return it }

        if (!primary.isNullOrBlank()) {
            Log.e(TAG, "Watchdog keyword JSON corrupt, falling back to last-known-good backup")
        }

        val backup = prefs.getString(KEY_WATCHDOG_LIST_BACKUP, null)
        parseKeywordsJson(backup)?.let { return it }

        return emptyList()
    }

    private fun parseKeywordsJson(jsonStr: String?): List<WatchdogKeyword>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<WatchdogKeyword>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WatchdogKeyword(
                        id = obj.optString("id"),
                        keyword = obj.optString("keyword"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        matchCount = obj.optInt("matchCount", 0),
                    )
                )
            }
            list
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Persists [list] and mirrors it into a backup key so a corrupt/partial primary write on the
     * next load can still recover the previous good state instead of resetting to empty.
     * Must be called while holding [mutationLock].
     */
    private fun saveKeywords(list: List<WatchdogKeyword>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("keyword", item.keyword)
                    put("isEnabled", item.isEnabled)
                    put("createdAt", item.createdAt)
                    put("matchCount", item.matchCount)
                }
                jsonArray.put(obj)
            }
            val json = jsonArray.toString()
            // Back up the previous primary before overwriting it, then write the new primary.
            // SharedPreferences.Editor.commit() applies both puts as a single atomic file write.
            val previousPrimary = prefs.getString(KEY_WATCHDOG_LIST, null)
            val editor = prefs.edit().putString(KEY_WATCHDOG_LIST, json)
            if (!previousPrimary.isNullOrBlank()) {
                editor.putString(KEY_WATCHDOG_LIST_BACKUP, previousPrimary)
            }
            editor.commit()
            _keywords.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist watchdog keywords: ${e.message}", e)
        }
    }

    /**
     * Thêm từ khóa mới cần theo dõi.
     * Trả về true nếu thêm thành công, false nếu từ khóa rỗng hoặc đã tồn tại.
     */
    fun addKeyword(rawKeyword: String): Boolean {
        val trimmed = rawKeyword.trim()
        if (trimmed.isBlank()) return false

        synchronized(mutationLock) {
            ensureLoaded()
            val current = _keywords.value
            if (current.any { it.keyword.equals(trimmed, ignoreCase = true) }) {
                return false
            }

            val updated = current + WatchdogKeyword(keyword = trimmed)
            saveKeywords(updated)
            return true
        }
    }

    /**
     * Xóa từ khóa theo id.
     */
    fun removeKeyword(id: String) {
        synchronized(mutationLock) {
            ensureLoaded()
            val updated = _keywords.value.filter { it.id != id }
            saveKeywords(updated)
        }
    }

    /**
     * Bật/tắt trạng thái theo dõi của từ khóa.
     */
    fun toggleKeyword(id: String, isEnabled: Boolean) {
        synchronized(mutationLock) {
            ensureLoaded()
            val updated = _keywords.value.map {
                if (it.id == id) it.copy(isEnabled = isEnabled) else it
            }
            saveKeywords(updated)
        }
    }

    /**
     * Tăng số lượng bài viết phát hiện được bởi từ khóa này.
     */
    fun incrementMatchCount(id: String) {
        incrementMatchCounts(mapOf(id to 1))
    }

    /**
     * Cộng dồn nhiều lượt tăng `matchCount` (theo id) trong **một** lần đọc-sửa-ghi duy nhất, thay vì
     * một lần ghi I/O riêng cho mỗi lượt tăng — dùng khi xử lý cả một batch bài viết khớp từ khóa.
     */
    fun incrementMatchCounts(deltasById: Map<String, Int>) {
        if (deltasById.isEmpty()) return
        synchronized(mutationLock) {
            ensureLoaded()
            val updated = _keywords.value.map {
                val delta = deltasById[it.id]
                if (delta != null) it.copy(matchCount = it.matchCount + delta) else it
            }
            saveKeywords(updated)
        }
    }

    /**
     * Kiểm tra nhanh một bài viết có khớp từ khóa nào đang bật hay không.
     */
    fun checkArticle(article: Article): WatchdogKeyword? {
        ensureLoaded()
        return watchdogEngine.match(article, _keywords.value)
    }

    /**
     * Quét danh sách bài viết mới được tải về từ chu kỳ đồng bộ nền và phát cảnh báo ưu tiên cao.
     * Trả về tổng số cảnh báo đã kích hoạt. Toàn bộ lượt tăng `matchCount` trong batch được gộp lại
     * và ghi persistence đúng một lần ở cuối, thay vì ghi lặp lại cho từng bài viết khớp.
     */
    fun checkAndNotify(articles: List<Article>, feed: Feed): Int {
        if (articles.isEmpty()) return 0
        var alertCount = 0
        val matchDeltas = mutableMapOf<String, Int>()

        for (article in articles) {
            val matchedKeyword = checkArticle(article)
            if (matchedKeyword != null) {
                notificationHelper.notifyWatchdogAlert(
                    article = article,
                    keyword = matchedKeyword.keyword,
                    feedName = feed.name,
                )
                matchDeltas.merge(matchedKeyword.id, 1, Int::plus)
                alertCount++
            }
        }
        incrementMatchCounts(matchDeltas)
        return alertCount
    }

    companion object {
        private const val TAG = "WatchdogManager"
        private const val PREFS_NAME = "watchdog_prefs"
        private const val KEY_WATCHDOG_LIST = "watchdog_keywords_json"
        private const val KEY_WATCHDOG_LIST_BACKUP = "watchdog_keywords_json_backup"
    }
}
