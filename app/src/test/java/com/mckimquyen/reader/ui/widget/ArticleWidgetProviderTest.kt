package com.mckimquyen.reader.ui.widget

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleWidgetProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, ArticleWidgetProvider::class.java)

    @Test
    fun onUpdate_rendersHeaderListEmptyViewAndNeverCrashesForSeveralIds() {
        val shadow = shadowOf(manager)
        shadow.bindAppWidgetId(11, component)
        shadow.bindAppWidgetId(12, component)

        ArticleWidgetProvider().onUpdate(context, manager, intArrayOf(11, 12))

        for (id in intArrayOf(11, 12)) {
            val root = shadow.getViewFor(id)
            assertNotNull("Widget $id must have rendered RemoteViews", root)
            assertEquals(
                context.getString(R.string.widget_article_name),
                root.findViewById<TextView>(R.id.widget_header).text.toString(),
            )
            assertNotNull(root.findViewById<ListView>(R.id.widget_article_list))
            assertEquals(
                context.getString(R.string.widget_article_empty),
                root.findViewById<TextView>(R.id.widget_empty_view).text.toString(),
            )
        }
    }

    @Test
    fun clickTemplate_isMutableActivityIntentForTrampolineOnly() {
        val template = ArticleWidgetProvider.createItemClickTemplate(context)
        val shadowIntent = shadowOf(template)
        assertTrue(shadowIntent.isActivity)
        assertEquals(
            ArticleWidgetActionActivity::class.java.name,
            shadowIntent.savedIntent.component?.className,
        )
        // fillInIntent chỉ được gộp khi template mutable
        assertTrue((shadowIntent.flags and PendingIntent.FLAG_MUTABLE) != 0)
    }

    @Test
    fun updater_isNoOpWhenNoWidgetIsPlacedAndDoesNotThrow() {
        shadowOf(manager)
        ArticleWidgetUpdater.notifyDataChanged(context)
        assertEquals(0, manager.getAppWidgetIds(component).size)
    }

    @Test
    fun layoutKeepsEmptyViewVisibleBeforeAdapterLoads() {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.widget_article_layout, FrameLayout(context), false)
        assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.widget_empty_view).visibility)
    }
}
