package com.mckimquyen.reader.ui.page.setting.zen

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget tests cho [EditionTimeRow] — component UI mới của ZEN-05 (chọn giờ phát hành ấn phẩm
 * sáng/tối). Xác nhận row compose được, và callback chỉ chạy khi người dùng thật sự confirm
 * trong TimePickerDialog (không tự bắn lúc render).
 */
@RunWith(AndroidJUnit4::class)
class ZenSettingsPageTest {

    @Test
    fun editionTimeRow_attachesToActivityWithoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    EditionTimeRow(
                        label = "Morning edition time",
                        time = "06:30",
                        testTag = "zen_morning_time_row",
                        activity = activity,
                        onPick = { _, _ -> },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun editionTimeRow_doesNotInvokeCallbackOnRender() {
        var picked: Pair<Int, Int>? = null

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    EditionTimeRow(
                        label = "Evening edition time",
                        time = "20:00",
                        testTag = "zen_evening_time_row",
                        activity = activity,
                        onPick = { hour, minute -> picked = hour to minute },
                    )
                }
            }
            activity.setContentView(composeView)
        }
        scenario.close()

        // Rendering alone must never write a new edition time.
        assertNull(picked)
    }

    @Test
    fun formatTime_padsHourAndMinuteToTwoDigits() {
        assertEquals("06:05", formatTime(6, 5))
        assertEquals("00:00", formatTime(0, 0))
        assertEquals("23:59", formatTime(23, 59))
    }

    @Test
    fun parseTime_roundTripsValidTime() {
        assertEquals(6 to 30, parseTime("06:30"))
        assertEquals(20 to 0, parseTime("20:00"))
        assertEquals(23 to 59, parseTime("23:59"))
    }

    @Test
    fun parseTime_returnsSafeFallbackForMalformedInput() {
        // Malformed input must not crash the picker; it falls back to a valid hour/minute pair.
        assertEquals(7 to 0, parseTime("not-a-time"))
        assertEquals(7 to 0, parseTime(""))
        assertEquals(7 to 0, parseTime("07"))
    }
}
