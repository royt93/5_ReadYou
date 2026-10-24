package com.mckimquyen.reader.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

internal enum class WidgetPinRequestResult { REQUESTED, UNSUPPORTED, REJECTED }

object ArticleWidgetPinHelper {

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)
    }

    internal fun requestPinResult(context: Context): WidgetPinRequestResult {
        if (!isPinSupported(context)) return WidgetPinRequestResult.UNSUPPORTED
        val manager = AppWidgetManager.getInstance(context) ?: return WidgetPinRequestResult.UNSUPPORTED
        val component = ComponentName(context, ArticleWidgetProvider::class.java)
        val success = runCatching {
            manager.requestPinAppWidget(component, null, null)
        }.getOrDefault(false)
        return if (success) WidgetPinRequestResult.REQUESTED else WidgetPinRequestResult.REJECTED
    }

    fun requestPin(context: Context): Boolean =
        requestPinResult(context) == WidgetPinRequestResult.REQUESTED
}
