package com.mckimquyen.reader.infrastructure.quote

import org.junit.Assert.assertFalse
import org.junit.Test

class DualCardWordingTest {
    @Test fun imageText_neverSaysRebuttalOrDebate() {
        val drawn = listOf(DualCardRenderer.DISCLAIMER, DualCardRenderer.WATERMARK).joinToString(" ").lowercase()
        assertFalse(drawn.contains("rebuttal"))
        assertFalse(drawn.contains("debate"))
    }
}
