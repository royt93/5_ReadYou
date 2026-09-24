package com.mckimquyen.reader.domain.model.notebook

import androidx.annotation.Keep

/**
 * Highlight color palette with hex color codes and user-friendly labels.
 */
@Keep
enum class HighlightColor(val hex: String, val displayName: String) {
    YELLOW("#FFF176", "Yellow"),
    BLUE("#81D4FA", "Blue"),
    PINK("#F48FB1", "Pink"),
    GREEN("#A5D6A7", "Green"),
    ORANGE("#FFCC80", "Orange");

    companion object {
        fun fromHex(hex: String): HighlightColor {
            return entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) } ?: YELLOW
        }
    }
}
