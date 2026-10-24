package com.mckimquyen.reader.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleWidgetPinHelperTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)
    private val shadow = shadowOf(manager)
    private val component = ComponentName(context, ArticleWidgetProvider::class.java)

    @Test
    fun requestPin_whenLauncherDoesNotSupportPinning_returnsFalseAndBindsNothing() {
        shadow.setRequestPinAppWidgetSupported(false)

        assertFalse(ArticleWidgetPinHelper.isPinSupported(context))
        assertFalse(ArticleWidgetPinHelper.requestPin(context))
        assertEquals(0, manager.getAppWidgetIds(component).size)
    }

    @Test
    fun requestPin_whenSupported_bindsExactlyOneArticleWidgetProvider() {
        shadow.setRequestPinAppWidgetSupported(true)

        assertTrue(ArticleWidgetPinHelper.isPinSupported(context))
        assertTrue(ArticleWidgetPinHelper.requestPin(context))
        assertEquals(1, manager.getAppWidgetIds(component).size)
    }

    @Test
    fun requestPinResult_distinguishesUnsupportedFromRequested() {
        shadow.setRequestPinAppWidgetSupported(false)
        assertEquals(WidgetPinRequestResult.UNSUPPORTED, ArticleWidgetPinHelper.requestPinResult(context))

        shadow.setRequestPinAppWidgetSupported(true)
        assertEquals(WidgetPinRequestResult.REQUESTED, ArticleWidgetPinHelper.requestPinResult(context))
    }

    @Test
    fun requestPin_calledTwice_requestsTwoIndependentWidgets() {
        shadow.setRequestPinAppWidgetSupported(true)

        assertTrue(ArticleWidgetPinHelper.requestPin(context))
        assertTrue(ArticleWidgetPinHelper.requestPin(context))
        assertEquals(2, manager.getAppWidgetIds(component).size)
    }
}
