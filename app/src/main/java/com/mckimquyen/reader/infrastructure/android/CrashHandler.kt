package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mckimquyen.reader.ui.ext.showToastLong
import java.lang.Thread.UncaughtExceptionHandler
import java.lang.ref.WeakReference

/**
 * The uncaught exception handler for the application.
 */
class CrashHandler(context: Context) : UncaughtExceptionHandler {

    private val contextRef = WeakReference(context.applicationContext)

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /**
     * Catch all uncaught exception and log it.
     */
    override fun uncaughtException(p0: Thread, p1: Throwable) {
        val causeMessage = getCauseMessage(p1)
        Log.e("RLog", "uncaughtException: $causeMessage")

        // Only show toast if context is still available and we're on main thread
        contextRef.get()?.let { context ->
            try {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // We're already on the main thread, show toast directly
                    context.showToastLong(causeMessage)
                } else {
                    // We're on a background thread, post to main thread
                    Handler(Looper.getMainLooper()).post {
                        try {
                            context.showToastLong(causeMessage)
                        } catch (e: Exception) {
                            Log.e("RLog", "Error showing crash toast on main thread: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RLog", "Error showing crash toast: ${e.message}")
            }
        }

        p1.printStackTrace()
        // android.os.Process.killProcess(android.os.Process.myPid());
        // exitProcess(1)
    }

    private fun getCauseMessage(e: Throwable?): String? {
        val cause = getCauseRecursively(e)
        return cause?.message ?: e?.javaClass?.name
    }

    private fun getCauseRecursively(e: Throwable?): Throwable? {
        var cause: Throwable?
        cause = e
        while (cause?.cause != null && cause !is RuntimeException) {
            cause = cause.cause
        }
        return cause
    }
}
