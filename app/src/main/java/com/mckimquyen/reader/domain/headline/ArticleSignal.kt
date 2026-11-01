package com.mckimquyen.reader.domain.headline

/** Observable wording cues behind a flagged paragraph. These describe wording only, never truth or intent. */
enum class HypeCueCategory { PROMOTIONAL_PHRASE, INTENSIFIER, ABSOLUTE_CLAIM, EXCLAMATION }

data class FlaggedPassage(
    val text: String,
    val score: Int,
    val cues: List<HypeCueCategory>,
)

/**
 * Local lexical signals for an article body. Not a fact-check, credibility score, political-bias judgement or
 * claim about author intent. [HeadlineTone.UNCLEAR] means insufficient lexical evidence, not neutral.
 */
data class ArticleSignalResult(
    val language: HeadlineLanguage,
    val hypeScore: Int,
    val tone: HeadlineTone,
    val flaggedPassages: List<FlaggedPassage>,
) {
    val isNoteworthy: Boolean
        get() = hypeScore >= ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD || tone != HeadlineTone.UNCLEAR

    val hasHypeWarning: Boolean
        get() = hypeScore >= ArticleSignalAnalyzer.HYPE_WARNING_THRESHOLD
}
