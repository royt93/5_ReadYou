package com.mckimquyen.reader.infrastructure.pref

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStoryClusteringPrefTest {

    @Test
    fun default_isON() {
        assertEquals(FlowStoryClusteringPref.ON, FlowStoryClusteringPref.default)
        assertTrue(FlowStoryClusteringPref.default.value)
    }

    @Test
    fun notOperator_togglesValues() {
        assertEquals(FlowStoryClusteringPref.OFF, !FlowStoryClusteringPref.ON)
        assertEquals(FlowStoryClusteringPref.ON, !FlowStoryClusteringPref.OFF)
    }

    @Test
    fun values_containsBothStates() {
        val values = FlowStoryClusteringPref.values
        assertEquals(2, values.size)
        assertTrue(values.contains(FlowStoryClusteringPref.ON))
        assertTrue(values.contains(FlowStoryClusteringPref.OFF))
    }
}
