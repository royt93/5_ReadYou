package com.mckimquyen.reader.ui.page.setting.vip

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric: VipPrefs đụng SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VipPrefsTest {

    private lateinit var ctx: Application
    private lateinit var prefs: VipPrefs

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // reset sạch SharedPreferences để test deterministic, không phụ thuộc thứ tự chạy
        ctx.getSharedPreferences("vip_screen_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = VipPrefs(ctx)
    }

    @Test
    fun `mac dinh grantedAt bang 0`() {
        assertEquals(0L, prefs.getGrantedAtMs())
    }

    @Test
    fun `save roi get tra dung gia tri`() {
        prefs.saveGrantedAtMs(123_456_789L)
        assertEquals(123_456_789L, prefs.getGrantedAtMs())
    }

    @Test
    fun `clear dua grantedAt ve 0`() {
        prefs.saveGrantedAtMs(999L)
        prefs.clearGrantedAtMs()
        assertEquals(0L, prefs.getGrantedAtMs())
    }

    @Test
    fun `mac dinh chua redeem la false`() {
        assertFalse(prefs.userRedeemedAtLeastOnce())
    }

    @Test
    fun `mark redeem set true va ben vung qua instance moi`() {
        prefs.markUserRedeemed()
        assertTrue(prefs.userRedeemedAtLeastOnce())
        // instance mới đọc cùng SharedPreferences vẫn thấy true
        assertTrue(VipPrefs(ctx).userRedeemedAtLeastOnce())
    }

    @Test
    fun `clear grantedAt khong xoa co redeem`() {
        prefs.markUserRedeemed()
        prefs.saveGrantedAtMs(555L)
        prefs.clearGrantedAtMs()
        assertEquals(0L, prefs.getGrantedAtMs())
        assertTrue(prefs.userRedeemedAtLeastOnce())
    }
}
