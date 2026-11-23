package com.mckimquyen.reader.ui.page.setting.ai

/**
 * The app calls only Gemini: the key box writes the Gemini key whatever chip is selected and nothing else reads the choice, so only Gemini is
 * offered. REL-03: a key pasted under another provider was saved as the Gemini key and sent to Google; on a 411 dp phone the fourth chip was
 * also squeezed into a vertical column. Gemini keeps index 0 (`DataStoreKeys.AiProvider` stores the index), so a choice saved by an older
 * build still means the same thing; the other providers come back as new entries when AI-01 wires them up.
 */
object AiProviderChoice {
    val NAMES = listOf("Gemini")
    const val GEMINI_INDEX = 0

    fun isSelectable(index: Int): Boolean = index == GEMINI_INDEX

    /** What the screen shows as selected: a choice saved by an older build (or a bad value) never shows another provider. */
    fun effective(saved: Int): Int = if (isSelectable(saved)) saved else GEMINI_INDEX
}
