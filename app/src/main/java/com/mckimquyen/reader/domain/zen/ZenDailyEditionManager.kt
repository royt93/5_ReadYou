package com.mckimquyen.reader.domain.zen

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.sv.DailyEditionWorker
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenDailyEditionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    @IODispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val _isEnabled = MutableStateFlow(DEFAULT_ENABLED)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isBatchSilence = MutableStateFlow(DEFAULT_BATCH_SILENCE)
    val isBatchSilence: StateFlow<Boolean> = _isBatchSilence.asStateFlow()

    private val _morningTime = MutableStateFlow(DEFAULT_MORNING_TIME)
    val morningTime: StateFlow<String> = _morningTime.asStateFlow()

    private val _eveningTime = MutableStateFlow(DEFAULT_EVENING_TIME)
    val eveningTime: StateFlow<String> = _eveningTime.asStateFlow()

    @Volatile
    private var isLoaded = false

    init {
        // Load off the constructing thread (usually Main, when Hilt builds the graph) so the
        // SharedPreferences disk read never blocks startup. Flows emit the defaults until then.
        applicationScope.launch(ioDispatcher) { ensureLoaded() }
    }

    /**
     * Loads persisted settings exactly once. Setters and synchronous readers call this first, so
     * a late async load can never overwrite a value the user just set, and a caller deciding on
     * notification silencing never acts on the defaults instead of the stored settings.
     */
    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        _isBatchSilence.value = prefs.getBoolean(KEY_BATCH_SILENCE, DEFAULT_BATCH_SILENCE)
        _morningTime.value = prefs.getString(KEY_MORNING_TIME, DEFAULT_MORNING_TIME) ?: DEFAULT_MORNING_TIME
        _eveningTime.value = prefs.getString(KEY_EVENING_TIME, DEFAULT_EVENING_TIME) ?: DEFAULT_EVENING_TIME
        isLoaded = true
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        if (enabled) {
            reschedule()
        } else {
            DailyEditionWorker.cancelDailyWork(WorkManager.getInstance(context))
        }
    }

    fun setBatchSilence(silence: Boolean) {
        ensureLoaded()
        prefs.edit().putBoolean(KEY_BATCH_SILENCE, silence).apply()
        _isBatchSilence.value = silence
    }

    /**
     * Đặt giờ bản tin buổi sáng. Lưu bền vững, cập nhật [morningTime], và lên lại lịch chạy
     * (nếu tính năng đang bật) để lần chạy kế tiếp rơi đúng mốc giờ mới.
     * Trả về false nếu [time] không đúng định dạng "HH:mm".
     */
    fun setMorningTime(time: String): Boolean {
        if (!isValidTime(time)) return false
        ensureLoaded()
        prefs.edit().putString(KEY_MORNING_TIME, time).apply()
        _morningTime.value = time
        if (_isEnabled.value) reschedule()
        return true
    }

    /**
     * Đặt giờ bản tin buổi tối. Xem [setMorningTime].
     */
    fun setEveningTime(time: String): Boolean {
        if (!isValidTime(time)) return false
        ensureLoaded()
        prefs.edit().putString(KEY_EVENING_TIME, time).apply()
        _eveningTime.value = time
        if (_isEnabled.value) reschedule()
        return true
    }

    /** Lên lại lịch chạy theo giờ hiện tại (morning/evening). */
    fun reschedule() {
        ensureLoaded()
        DailyEditionWorker.scheduleNext(
            workManager = WorkManager.getInstance(context),
            morningTime = _morningTime.value,
            eveningTime = _eveningTime.value,
        )
    }

    fun shouldSilenceImmediateNotification(): Boolean {
        ensureLoaded()
        return _isEnabled.value && _isBatchSilence.value
    }

    companion object {
        const val PREF_NAME = "zen_daily_edition_prefs"
        const val KEY_ENABLED = "key_daily_edition_enabled"
        const val KEY_BATCH_SILENCE = "key_daily_edition_batch_silence"
        const val KEY_MORNING_TIME = "key_morning_time"
        const val KEY_EVENING_TIME = "key_evening_time"

        const val DEFAULT_MORNING_TIME = "07:00"
        const val DEFAULT_EVENING_TIME = "20:00"

        private const val DEFAULT_ENABLED = false
        private const val DEFAULT_BATCH_SILENCE = true

        private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
        private const val TIME_PARTS = 2

        /** "HH:mm" hợp lệ trong khoảng 00:00–23:59. */
        fun isValidTime(time: String): Boolean = TIME_PATTERN.matches(time.trim())

        /**
         * Số mili-giây từ [nowMillis] tới mốc giờ gần nhất tiếp theo trong [times] ("HH:mm").
         * Bỏ qua mốc không hợp lệ. Nếu mọi mốc đã qua trong ngày hôm nay → nhảy sang ngày kế tiếp.
         * Trả về null khi [times] không có mốc hợp lệ nào.
         */
        fun millisUntilNextOccurrence(times: List<String>, nowMillis: Long): Long? {
            val valid = times.filter { isValidTime(it) }
            if (valid.isEmpty()) return null

            var best: Long? = null
            for (time in valid) {
                val parts = time.trim().split(":")
                if (parts.size != TIME_PARTS) continue
                val hour = parts[0].toIntOrNull() ?: continue
                val minute = parts[1].toIntOrNull() ?: continue

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (calendar.timeInMillis <= nowMillis) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                val delay = calendar.timeInMillis - nowMillis
                if (best == null || delay < best!!) best = delay
            }
            return best
        }
    }
}
