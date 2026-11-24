package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import com.roy.sdkadbmob.AdManager

/**
 * What the "Ad choices" entry does when pressed.
 *
 * The SDK (ConsentBridge.showPrivacyOptionsForm) opens Google's form only when UMP reports the privacy options as REQUIRED. For every other status,
 * UNKNOWN included, it logs "privacy options form not required" and calls `onDone` before it returns; a real form calls `onDone` later, when the form is
 * dismissed. So "onDone already ran when the call returned" means no form was shown, and the user was left looking at a row that did nothing.
 * Reading UMP's status ourselves is not enough: in Vietnam it stays UNKNOWN, and treating UNKNOWN as "not needed" would hide the form from an EEA user who
 * has not answered yet; the SDK is the one that decides, so the app asks the SDK and looks at what it did.
 */
class AdChoicesAction(
    private val show: (Activity, () -> Unit) -> Unit = { activity, done -> AdManager.showConsentFormIfAvailable(activity, done) },
) {
    /** Calls [onNoForm] when the SDK showed no form (it finished before the call returned); never when a form was opened. */
    fun open(activity: Activity, onNoForm: () -> Unit) {
        var noForm = false
        show(activity) { noForm = true }
        // A form that is on screen calls `done` only when it is dismissed, after this line has run, so only a call made before show() returned counts.
        if (noForm) onNoForm()
    }
}
