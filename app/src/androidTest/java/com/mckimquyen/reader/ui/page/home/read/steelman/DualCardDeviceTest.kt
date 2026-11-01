package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.infrastructure.quote.DualCardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualCardDeviceTest {
    @Test
    fun renderer_producesNineBySixteenCardWithTwoDistinctHalves_onARealDevice() {
        val spec = DualPerspectiveSpec("Top headline", "BBC News", "1 Nov", "Bottom headline", "Science Wire", "1 Nov", "warn")
        val bitmap = DualCardRenderer.render(spec)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        assertNotEquals(bitmap.getPixel(8, 8), bitmap.getPixel(8, bitmap.height - 8))
    }
}
