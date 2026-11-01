package com.mckimquyen.reader.ui.page.home.read

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.readposition.ReadingPositionMapper
import com.mckimquyen.reader.ui.page.rsvp.RsvpViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionHandoffDeviceTest {
    @Test
    fun rsvpStartsMidArticle_andReturnsAnIndexInsideTheBody() {
        val vm = RsvpViewModel()
        vm.loadContent((1..9).joinToString(" ") { "word$it" }, startFraction = 0.5f)
        assertEquals(4, vm.uiState.value.currentIndex)

        vm.seekTo(6)
        val fraction = ReadingPositionMapper.fractionFromTokenIndex(6, vm.uiState.value.tokens.size)
        val target = ReadingPositionMapper.scrollTargetFromBodyFraction(fraction, bodyStart = 3, bodyCount = 10)
        assertTrue(target.listIndex in 3 until 13)
        assertFalse(ReadingPositionMapper.shouldHandoffFromRsvp(startIndex = 4, currentIndex = 6))
    }
}
