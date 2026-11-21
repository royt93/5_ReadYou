package com.mckimquyen.reader.ui.page.setting.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app only ever calls Gemini: the key box writes the Gemini key whatever chip is selected and nothing else reads the chip. So a chip other
 * than Gemini must not be selectable, and a choice saved by an older build must not show another provider as selected (otherwise a key for
 * that provider would be saved as the Gemini key and sent to Google).
 */
class AiProviderChoiceTest {
    @Test fun theProviderListKeepsItsOrder_soStoredIndexesStayMeaningful() {
        assertEquals(listOf("Gemini", "OpenAI", "DeepSeek", "Groq"), AiProviderChoice.NAMES)
    }

    @Test fun onlyGeminiIsSelectable() {
        assertTrue(AiProviderChoice.isSelectable(AiProviderChoice.GEMINI_INDEX))
        listOf(1, 2, 3).forEach { assertFalse("index $it must not be selectable", AiProviderChoice.isSelectable(it)) }
    }

    @Test fun aSavedGemini_staysGemini() = assertEquals(0, AiProviderChoice.effective(0))

    @Test fun aSavedOtherProvider_isShownAsGemini() {
        listOf(1, 2, 3).forEach { assertEquals("saved $it", 0, AiProviderChoice.effective(it)) }
    }

    @Test fun anOutOfRangeSavedValue_isShownAsGemini() {
        listOf(-1, 4, 99, Int.MAX_VALUE, Int.MIN_VALUE).forEach { assertEquals("saved $it", 0, AiProviderChoice.effective(it)) }
    }

    @Test fun theSelectableIndexIsGemini() = assertEquals(0, AiProviderChoice.GEMINI_INDEX)
}
