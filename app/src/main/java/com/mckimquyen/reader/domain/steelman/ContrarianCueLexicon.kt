package com.mckimquyen.reader.domain.steelman

/**
 * Wording that often signals a headline pushing back on a claim. A match says only that the word is present, never that
 * the article actually rebuts anything. Matching is whole-word, so "but" never fires inside "butter".
 */
object ContrarianCueLexicon {

    private val EN_CUES = listOf(
        "however", "despite", "although", "critics", "criticised", "criticized",
        "warns", "warn", "warning", "risks", "risk", "backlash", "controversy", "disputes",
        "denies", "fails", "slams", "rejects", "doubts", "but",
    )

    private val VI_CUES = listOf(
        "tuy nhiên", "bất chấp", "chỉ trích", "cảnh báo", "rủi ro", "tranh cãi",
        "phản đối", "bác bỏ", "nghi ngờ", "thất bại", "nhưng",
    )

    private val ALL_CUES = EN_CUES + VI_CUES

    /** Returns the first cue found in [title] as a whole word or phrase, or null. */
    fun find(title: String): String? {
        if (title.isBlank()) return null
        return ALL_CUES.firstOrNull { cue -> WholePhraseMatcher.containsPhrase(title, cue) }
    }
}
