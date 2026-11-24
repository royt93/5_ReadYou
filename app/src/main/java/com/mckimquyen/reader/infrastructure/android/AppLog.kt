package com.mckimquyen.reader.infrastructure.android

import android.util.Log

/**
 * One place for the trace lines a manual tester reads with `adb logcat -s 'roy93~Flow'`.
 *
 * Rules: log screen names, route names, ids, counts and the way something was triggered; never article titles, URLs, keys or any text the user typed.
 * The ad SDK writes its own lines under `roy93~Ad*`, so nothing here repeats ad events.
 */
object AppLog {
    const val TAG = "roy93~Flow"

    /** Tests replace this to read the lines without Android's Log. */
    @Volatile
    internal var sink: (String) -> Unit = { line ->
        // A plain JVM unit test has no android.util.Log ("Method d ... not mocked"); a trace line must never be able to fail the caller.
        try {
            Log.d(TAG, line)
        } catch (_: RuntimeException) {
        }
    }

    /** [area] is a short noun such as `nav`, `reels`, `setting`, `sync`; [event] a short phrase; [kv] extra facts. */
    fun event(area: String, event: String, vararg kv: Pair<String, Any?>) {
        sink(format(area, event, *kv))
    }

    internal fun format(area: String, event: String, vararg kv: Pair<String, Any?>): String =
        buildString {
            append(area).append(": ").append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
        }
}
