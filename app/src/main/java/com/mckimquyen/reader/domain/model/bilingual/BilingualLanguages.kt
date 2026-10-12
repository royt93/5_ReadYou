package com.mckimquyen.reader.domain.model.bilingual

import java.util.Locale

/**
 * Target languages for Bilingual Reader. ML Kit on-device translation covers 59 languages, which does
 * not include every app locale (az, eu, lzh, ml, my, sr), so the target is a user choice that defaults to
 * [AUTO] (= follow the app locale when ML Kit supports it).
 */
object BilingualLanguages {
    /** Stored value meaning "follow the app locale". */
    const val AUTO = "auto"

    /** ML Kit TranslateLanguage tags, sorted. */
    val supported: List<String> = listOf(
        "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en", "eo", "es", "et", "fa", "fi",
        "fr", "ga", "gl", "gu", "he", "hi", "hr", "ht", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt",
        "lv", "mk", "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sv", "sw", "ta",
        "te", "th", "tl", "tr", "uk", "ur", "vi", "zh",
    )

    fun isSupported(code: String): Boolean = code in supported

    /** ML Kit tag for [locale], or null when ML Kit cannot translate into that language. */
    fun fromLocale(locale: Locale): String? = normalise(locale.language).takeIf(::isSupported)

    /**
     * The language to translate into. [choice] is the stored preference ([AUTO] or a code); an unknown or
     * no-longer-supported stored code behaves like [AUTO]. Null means "no usable target": the UI should
     * ask the user to pick one.
     */
    fun resolveTarget(choice: String, appLocale: Locale): String? =
        if (choice != AUTO && isSupported(choice)) choice else fromLocale(appLocale)

    // Android and the JDK still report some legacy ISO 639 codes; ML Kit uses the current ones.
    private fun normalise(language: String): String = when (language) {
        "iw" -> "he"
        "in" -> "id"
        "nb", "nn" -> "no"
        else -> language
    }
}
