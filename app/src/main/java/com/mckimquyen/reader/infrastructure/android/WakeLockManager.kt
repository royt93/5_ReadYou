package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WakeLock safely for background operations and active reading sessions.
 * Uses PARTIAL_WAKE_LOCK with timeout guards and proper state tracking to prevent battery drain.
 */
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "roy93~WakeLock"
        private const val WAKE_LOCK_TAG = "ReadYou:WakeLock"
        const val DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes default safety timeout
    }

    private val powerManager: PowerManager? by lazy {
        try {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PowerManager: ${e.message}", e)
            null
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val lock = Any()

    /**
     * Acquires the WakeLock with a safety timeout.
     *
     * @param timeoutMs Maximum duration in milliseconds before the system automatically releases the lock.
     */
    fun acquire(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        synchronized(lock) {
            try {
                if (wakeLock == null) {
                    wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                        setReferenceCounted(false)
                    }
                }

                val wl = wakeLock
                if (wl != null) {
                    if (!wl.isHeld) {
                        wl.acquire(timeoutMs)
                        Log.d(TAG, "WakeLock acquired with timeout=${timeoutMs}ms")
                    } else {
                        Log.d(TAG, "WakeLock already held, skipping acquire")
                    }
                } else {
                    Log.w(TAG, "WakeLock is null (PowerManager not available)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire WakeLock: ${e.message}", e)
            }
        }
    }

    /**
     * Releases the WakeLock safely if held.
     */
    fun release() {
        synchronized(lock) {
            try {
                val wl = wakeLock
                if (wl != null && wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "WakeLock released successfully")
                } else {
                    Log.d(TAG, "WakeLock not held or null, nothing to release")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release WakeLock: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if the WakeLock is currently held.
     */
    fun isHeld(): Boolean {
        synchronized(lock) {
            return wakeLock?.isHeld == true
        }
    }
}
