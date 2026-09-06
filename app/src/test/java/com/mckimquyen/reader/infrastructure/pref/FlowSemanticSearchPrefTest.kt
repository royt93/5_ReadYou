package com.mckimquyen.reader.infrastructure.pref

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowSemanticSearchPrefTest {

    @Test
    fun default_isON() {
        assertEquals(FlowSemanticSearchPref.ON, FlowSemanticSearchPref.default)
        assertTrue(FlowSemanticSearchPref.default.value)
    }

    @Test
    fun notOperator_togglesValues() {
        assertEquals(FlowSemanticSearchPref.OFF, !FlowSemanticSearchPref.ON)
        assertEquals(FlowSemanticSearchPref.ON, !FlowSemanticSearchPref.OFF)
    }

    @Test
    fun values_containsBothStates() {
        val values = FlowSemanticSearchPref.values
        assertEquals(2, values.size)
        assertTrue(values.contains(FlowSemanticSearchPref.ON))
        assertTrue(values.contains(FlowSemanticSearchPref.OFF))
    }
}
