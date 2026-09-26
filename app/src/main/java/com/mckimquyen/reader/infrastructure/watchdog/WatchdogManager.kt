package com.mckimquyen.reader.infrastructure.watchdog

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.watchdog.WatchdogAlert
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
import java.time.LocalTime
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

    private val _alerts = MutableStateFlow<List<WatchdogAlert>>(emptyList())
    val alerts: StateFlow<List<WatchdogAlert>> = _alerts.asStateFlow()

    // Serializes every read-modify-write mutation (ensureLoaded + snapshot + transform + save) so
    // a UI-thread edit (addKeyword/removeKeyword/toggleKeyword) and a background SyncWorker
    // increment (incrementMatchCount) can never interleave and lose one another's update.
    // No suspension ever happens inside this lock — every guarded function is a plain blocking
    // call — so `synchronized` here is safe with coroutines.
    private val mutationLock = Any()

    @Volatile
    private var isLoaded = false

    @Volatile
    private var areAlertsLoaded = false

    init {
        // Load off the constructing thread (usually Main, when Hilt builds the graph) so the
        // SharedPreferences disk read never blocks startup. `keywords` emits emptyList() until then.
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
        applicationScope.launch(ioDispatcher) { ensureAlertsLoaded() }
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
                        snoozeUntil = if (obj.has("snoozeUntil")) obj.getLong("snoozeUntil") else null,
                        quietHoursStart = if (obj.has("quietHoursStart")) obj.getInt("quietHoursStart") else null,
                        quietHoursEnd = if (obj.has("quietHoursEnd")) obj.getInt("quietHoursEnd") else null,
                    )
                )
            }
            list
        } catch (e: JSONException) {
            null
        }
    }

    private fun keywordsToJson(list: List<WatchdogKeyword>): String {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("keyword", item.keyword)
                put("isEnabled", item.isEnabled)
                put("createdAt", item.createdAt)
                put("matchCount", item.matchCount)
                item.snoozeUntil?.let { put("snoozeUntil", it) }
                item.quietHoursStart?.let { put("quietHoursStart", it) }
                item.quietHoursEnd?.let { put("quietHoursEnd", it) }
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun alertsToJson(list: List<WatchdogAlert>): String {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("articleId", item.articleId)
                put("articleTitle", item.articleTitle)
                put("feedName", item.feedName)
                put("keyword", item.keyword)
                put("matchedExcerpt", item.matchedExcerpt)
                put("detectedAt", item.detectedAt)
                put("isRead", item.isRead)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    /**
     * Một lần ghi SharedPreferences duy nhất cho cả keywords lẫn alerts (mỗi nhóm vẫn có backup key
     * riêng để phục hồi khi primary corrupt). Truyền null cho nhóm không thay đổi.
     * `Editor.commit()` áp tất cả `putString` như một lần ghi file nguyên tử.
     * Must be called while holding [mutationLock].
     */
    private fun persist(keywords: List<WatchdogKeyword>?, alerts: List<WatchdogAlert>?) {
        if (keywords == null && alerts == null) return
        try {
            val editor = prefs.edit()
            if (keywords != null) {
                val previous = prefs.getString(KEY_WATCHDOG_LIST, null)
                editor.putString(KEY_WATCHDOG_LIST, keywordsToJson(keywords))
                if (!previous.isNullOrBlank()) editor.putString(KEY_WATCHDOG_LIST_BACKUP, previous)
            }
            if (alerts != null) {
                val previous = prefs.getString(KEY_WATCHDOG_ALERTS, null)
                editor.putString(KEY_WATCHDOG_ALERTS, alertsToJson(alerts))
                if (!previous.isNullOrBlank()) editor.putString(KEY_WATCHDOG_ALERTS_BACKUP, previous)
            }
            editor.commit()
            if (keywords != null) _keywords.value = keywords
            if (alerts != null) _alerts.value = alerts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist watchdog state: ${e.message}", e)
        }
    }

    /**
     * Persists [list] and mirrors it into a backup key so a corrupt/partial primary write on the
     * next load can still recover the previous good state instead of resetting to empty.
     * Must be called while holding [mutationLock].
     */
    private fun saveKeywords(list: List<WatchdogKeyword>) = persist(list, null)

    // ---- Alert Inbox (REEL-06) ----

    private fun ensureAlertsLoaded() {
        if (areAlertsLoaded) return
        synchronized(mutationLock) {
            if (areAlertsLoaded) return
            _alerts.value = readAlerts()
            areAlertsLoaded = true
        }
    }

    private fun readAlerts(): List<WatchdogAlert> {
        val primary = prefs.getString(KEY_WATCHDOG_ALERTS, null)
        parseAlertsJson(primary)?.let { return it }
        if (!primary.isNullOrBlank()) {
            Log.e(TAG, "Watchdog alert JSON corrupt, falling back to last-known-good backup")
        }
        return parseAlertsJson(prefs.getString(KEY_WATCHDOG_ALERTS_BACKUP, null)) ?: emptyList()
    }

    private fun parseAlertsJson(jsonStr: String?): List<WatchdogAlert>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<WatchdogAlert>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WatchdogAlert(
                        id = obj.optString("id"),
                        articleId = obj.optString("articleId"),
                        articleTitle = obj.optString("articleTitle"),
                        feedName = obj.optString("feedName"),
                        keyword = obj.optString("keyword"),
                        matchedExcerpt = obj.optString("matchedExcerpt"),
                        detectedAt = obj.optLong("detectedAt", System.currentTimeMillis()),
                        isRead = obj.optBoolean("isRead", false),
                    )
                )
            }
            list
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Persists [list] with the same primary+backup atomic scheme as [saveKeywords].
     * Must be called while holding [mutationLock].
     */
    private fun saveAlerts(list: List<WatchdogAlert>) = persist(null, list)

    /** Đánh dấu một cảnh báo đã đọc. */
    fun markAlertAsRead(alertId: String) {
        synchronized(mutationLock) {
            ensureAlertsLoaded()
            val updated = _alerts.value.map { if (it.id == alertId) it.copy(isRead = true) else it }
            if (updated != _alerts.value) saveAlerts(updated)
        }
    }

    /** Đánh dấu toàn bộ cảnh báo đã đọc. */
    fun markAllAlertsAsRead() {
        synchronized(mutationLock) {
            ensureAlertsLoaded()
            val current = _alerts.value
            if (current.none { !it.isRead }) return
            saveAlerts(current.map { it.copy(isRead = true) })
        }
    }

    /** Xóa toàn bộ lịch sử cảnh báo. */
    fun clearAlerts() {
        synchronized(mutationLock) {
            ensureAlertsLoaded()
            if (_alerts.value.isEmpty()) return
            saveAlerts(emptyList())
        }
    }

    
    /**
     * Snooze một từ khóa trong [durationMillis] kể từ bây giờ (0 hoặc âm = bỏ snooze).
     */
    fun snoozeKeyword(id: String, durationMillis: Long) {
        synchronized(mutationLock) {
            ensureLoaded()
            val until = if (durationMillis > 0) System.currentTimeMillis() + durationMillis else null
            val updated = _keywords.value.map { if (it.id == id) it.copy(snoozeUntil = until) else it }
            saveKeywords(updated)
        }
    }

    /**
     * Đặt quiet hours riêng cho từ khóa ([startMinute]/[endMinute] = phút trong ngày 0..1439).
     * Truyền null cho cả hai để tắt.
     */
    fun setQuietHours(id: String, startMinute: Int?, endMinute: Int?) {
        synchronized(mutationLock) {
            ensureLoaded()
            val validStart = startMinute?.takeIf { it in 0 until MINUTES_PER_DAY }
            val validEnd = endMinute?.takeIf { it in 0 until MINUTES_PER_DAY }
            val updated = _keywords.value.map {
                if (it.id == id) it.copy(quietHoursStart = validStart, quietHoursEnd = validEnd) else it
            }
            saveKeywords(updated)
        }
    }

    /** Từ khóa này hiện có đang bị tắt tiếng thông báo (snooze / quiet hours) không? */
    fun isKeywordMuted(keyword: WatchdogKeyword, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val now = LocalTime.now()
        val minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute
        return WatchdogEngine.isMuted(keyword, nowMillis, minuteOfDay)
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
     * Mỗi match đều được ghi vào Alert Inbox — kể cả khi từ khóa đang snooze/quiet-hours (lúc đó chỉ
     * không bắn notification). Toàn bộ `matchCount` trong batch được gộp và ghi persistence một lần.
     * Trả về tổng số notification đã bắn (loại trừ các match bị muted).
     */
    fun checkAndNotify(articles: List<Article>, feed: Feed): Int {
        if (articles.isEmpty()) return 0
        var alertCount = 0
        val matchDeltas = mutableMapOf<String, Int>()
        val newAlerts = mutableListOf<WatchdogAlert>()

        for (article in articles) {
            val matchedKeyword = checkArticle(article)
            if (matchedKeyword != null) {
                val muted = isKeywordMuted(matchedKeyword)
                if (!muted) {
                    notificationHelper.notifyWatchdogAlert(
                        article = article,
                        keyword = matchedKeyword.keyword,
                        feedName = feed.name,
                    )
                    alertCount++
                }
                newAlerts.add(
                    WatchdogAlert(
                        articleId = article.id,
                        articleTitle = article.title,
                        feedName = feed.name,
                        keyword = matchedKeyword.keyword,
                        matchedExcerpt = WatchdogEngine.extractExcerpt(
                            matchedKeyword.keyword,
                            article.title,
                            article.shortDescription,
                            article.fullContent,
                        ),
                    )
                )
                matchDeltas.merge(matchedKeyword.id, 1, Int::plus)
            }
        }
        if (matchDeltas.isEmpty()) return 0
        synchronized(mutationLock) {
            ensureLoaded()
            ensureAlertsLoaded()
            val updatedKeywords = _keywords.value.map {
                val delta = matchDeltas[it.id]
                if (delta != null) it.copy(matchCount = it.matchCount + delta) else it
            }
            val mergedAlerts = (newAlerts.asReversed() + _alerts.value).take(MAX_ALERTS)
            persist(updatedKeywords, mergedAlerts)
        }
        return alertCount
    }

    companion object {
        private const val TAG = "WatchdogManager"
        private const val PREFS_NAME = "watchdog_prefs"
        private const val KEY_WATCHDOG_LIST = "watchdog_keywords_json"
        private const val KEY_WATCHDOG_LIST_BACKUP = "watchdog_keywords_json_backup"

        private const val MAX_ALERTS = 200
        private const val KEY_WATCHDOG_ALERTS = "watchdog_alerts_json"
        private const val KEY_WATCHDOG_ALERTS_BACKUP = "watchdog_alerts_json_backup"
        internal const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
