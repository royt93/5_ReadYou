package com.mckimquyen.reader.infrastructure.filter

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.filter.FilterAction
import com.mckimquyen.reader.domain.model.filter.FilterTargetField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartFilterManagerTest {

    private lateinit var context: Context
    private lateinit var manager: SmartFilterManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("smart_filter_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        manager = SmartFilterManager(context, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
    }

    @Test
    fun addRule_persistsAndUpdatesFlow() {
        val success = manager.addRule(
            targetField = FilterTargetField.TITLE,
            keyword = "[Quảng cáo]",
            action = FilterAction.MARK_READ
        )
        assertTrue(success)
        assertEquals(1, manager.rules.value.size)
        assertEquals("[Quảng cáo]", manager.rules.value[0].keyword)
        assertEquals(FilterAction.MARK_READ, manager.rules.value[0].action)
    }

    @Test
    fun addRule_duplicateKeywordAndAction_rejected() {
        manager.addRule(FilterTargetField.TITLE, "AI", FilterAction.STAR)
        val duplicate = manager.addRule(FilterTargetField.TITLE, "AI", FilterAction.STAR)
        assertFalse(duplicate)
        assertEquals(1, manager.rules.value.size)
    }

    @Test
    fun toggleRule_updatesEnabledState() {
        manager.addRule(FilterTargetField.TITLE, "News", FilterAction.MARK_READ)
        val id = manager.rules.value[0].id

        manager.toggleRule(id, isEnabled = false)
        assertFalse(manager.rules.value[0].isEnabled)

        manager.toggleRule(id, isEnabled = true)
        assertTrue(manager.rules.value[0].isEnabled)
    }

    @Test
    fun removeRule_removesFromList() {
        manager.addRule(FilterTargetField.TITLE, "R1", FilterAction.MARK_READ)
        manager.addRule(FilterTargetField.TITLE, "R2", FilterAction.STAR)
        assertEquals(2, manager.rules.value.size)

        val id1 = manager.rules.value[0].id
        manager.removeRule(id1)

        assertEquals(1, manager.rules.value.size)
        assertEquals("R2", manager.rules.value[0].keyword)
    }
}
