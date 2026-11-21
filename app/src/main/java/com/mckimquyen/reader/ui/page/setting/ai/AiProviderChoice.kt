package com.mckimquyen.reader.ui.page.setting.ai

/**
 * The app calls only Gemini: the key box writes the Gemini key whatever chip is selected and nothing else reads the choice, so the other
 * chips are shown disabled. SEC/REL-03: a key pasted under another provider was saved as the Gemini key and sent to Google.
 * The order is the stored index order (`DataStoreKeys.AiProvider`), so it must not change.
 */
object AiProviderChoice {
    val NAMES = listOf("Gemini", "OpenAI", "DeepSeek", "Groq")
    const val GEMINI_INDEX = 0

    fun isSelectable(index: Int): Boolean = index == GEMINI_INDEX

    /** What the screen shows as selected: a choice saved by an older build (or a bad value) never shows another provider. */
    fun effective(saved: Int): Int = if (isSelectable(saved)) saved else GEMINI_INDEX
}
