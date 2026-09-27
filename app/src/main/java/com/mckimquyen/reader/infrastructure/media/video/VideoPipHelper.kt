package com.mckimquyen.reader.infrastructure.media.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Picture-in-Picture plumbing for the in-app video player.
 *
 * Android rejects `PictureInPictureParams` whose aspect ratio falls outside roughly 1:2.39 … 2.39:1
 * with an `IllegalArgumentException` that crashes the activity, so every ratio is clamped here
 * before it reaches the system.
 */
object VideoPipHelper {

    /** Widest/narrowest ratio the platform accepts, per `Activity.enterPictureInPictureMode` docs. */
    private const val MAX_ASPECT_RATIO = 2.39f
    private const val MIN_ASPECT_RATIO = 1f / MAX_ASPECT_RATIO

    /** Used when the video has not reported its size yet. */
    private const val DEFAULT_WIDTH = 16
    private const val DEFAULT_HEIGHT = 9

    /**
     * Ratio used for videos outside the legal band: 2.38:1, a hair inside the 2.39 limit.
     *
     * Expressed as exact integers on purpose. Deriving the clamped ratio from a float and rounding
     * it can land just *outside* the limit (1/2.39 truncates to 0.418, below the 0.41841 minimum),
     * which makes the system throw and takes the activity down.
     */
    private const val LIMIT_LONG_SIDE = 238
    private const val LIMIT_SHORT_SIDE = 100

    /** True when this device supports PiP at all (Android Go and some TVs do not). */
    fun isPipSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Builds a system-safe aspect ratio for [width] x [height].
     *
     * Non-positive dimensions fall back to 16:9 rather than throwing, because ExoPlayer reports
     * 0x0 until the first frame is decoded.
     */
    fun aspectRatioFor(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) {
            return Rational(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }

        val ratio = width.toFloat() / height.toFloat()
        if (ratio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) {
            return Rational(width, height)
        }

        return if (ratio > MAX_ASPECT_RATIO) {
            Rational(LIMIT_LONG_SIDE, LIMIT_SHORT_SIDE)
        } else {
            Rational(LIMIT_SHORT_SIDE, LIMIT_LONG_SIDE)
        }
    }

    /**
     * Asks the system to shrink [activity] into PiP.
     *
     * @return true when PiP actually started, false when unsupported or refused. Callers must not
     *   assume success: the user can disable PiP per app in system settings.
     */
    fun enterPip(activity: Activity, videoWidth: Int, videoHeight: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!isPipSupported(activity)) return false
        if (activity.isFinishing || activity.isDestroyed) return false

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatioFor(videoWidth, videoHeight))
            .build()

        // The system throws if the activity is in a state that forbids PiP (e.g. already finishing
        // on some OEM builds); a failed shrink must never take the app down with it.
        return runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)
    }
}
