package com.mckimquyen.reader.infrastructure.android

import android.app.Activity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SDK calls `onDone` before it returns when it shows no form (status not REQUIRED: "privacy options form not required"), and later, on dismissal,
 * when a form was shown. The Toast belongs only to the first case.
 */
class AdChoicesActionTest {
    private val activity = mockk<Activity>(relaxed = true)

    @Test fun noFormShown_sdkFinishesAtOnce_userIsToldSo() {
        var told = 0
        AdChoicesAction(show = { _, done -> done() }).open(activity) { told++ }
        assertEquals(1, told)
    }

    @Test fun aFormIsShown_doneComesLater_noMessage() {
        var told = 0
        var dismiss: (() -> Unit)? = null
        AdChoicesAction(show = { _, done -> dismiss = done }).open(activity) { told++ }
        assertEquals("the form is on screen, nothing to say", 0, told)
        dismiss!!.invoke() // the user closes the form
        assertEquals("closing a form that was shown is not a 'no form' case", 0, told)
    }

    @Test fun theMessageIsShownOnce_evenIfDoneFiresTwiceBeforeReturn() {
        var told = 0
        AdChoicesAction(show = { _, done -> done(); done() }).open(activity) { told++ }
        assertEquals(1, told)
    }

    @Test fun theSdkIsAskedExactlyOnce() {
        var calls = 0
        AdChoicesAction(show = { _, done -> calls++; done() }).open(activity) { }
        assertEquals(1, calls)
    }
}
