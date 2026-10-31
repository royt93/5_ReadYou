package com.mckimquyen.reader.ui.page.headline

import com.mckimquyen.reader.domain.headline.HeadlineLanguage
import com.mckimquyen.reader.domain.headline.HeadlineTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlineSignalsStateTest {

    @Test
    fun disabledGateReturnsNullWithoutAnalysis() {
        assertNull(headlineSignalFor("SHOCKING headline!!!", enabled = false))
    }

    @Test
    fun enabledGateUsesAnalyzerForSupportedHeadline() {
        val signal = headlineSignalFor("SHOCKING: You won't believe what happened next!!!", enabled = true)
        assertEquals(HeadlineLanguage.ENGLISH, signal?.language)
        assertEquals(HeadlineTone.UNCLEAR, signal?.tone)
    }

    @Test
    fun enabledGateLeavesUnsupportedLanguageUnclassified() {
        assertNull(headlineSignalFor("東京の鉄道ニュース", enabled = true))
    }
}
