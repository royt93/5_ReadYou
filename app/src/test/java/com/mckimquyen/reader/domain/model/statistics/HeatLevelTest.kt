package com.mckimquyen.reader.domain.model.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatLevelTest {

    @Test
    fun of_mapsExactThresholds() {
        assertEquals(0, HeatLevel.of(-5))
        assertEquals(0, HeatLevel.of(0))
        assertEquals(1, HeatLevel.of(1))
        assertEquals(2, HeatLevel.of(2))
        assertEquals(2, HeatLevel.of(3))
        assertEquals(3, HeatLevel.of(4))
        assertEquals(3, HeatLevel.of(6))
        assertEquals(4, HeatLevel.of(7))
        assertEquals(4, HeatLevel.of(200))
    }
}
