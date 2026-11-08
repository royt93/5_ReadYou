package com.mckimquyen.reader.domain.archivequery

import com.mckimquyen.reader.domain.steelman.WholePhraseMatcher
import java.time.YearMonth

/** A month bucket plus the verbatim quotes already extracted from its top articles. */
data class ArchiveMonthWithEvidence(
    val month: ArchiveMonth,
    val evidence: List<ArchiveEvidence>,
) {
    val yearMonth: YearMonth get() = YearMonth.of(month.year, month.month)
}

/** Two opposing directions; each side lists the surface forms that express it. */
data class OpposingCuePair(val first: Set<String>, val second: Set<String>)

data class ArchivePerspectiveShift(
    val earlier: YearMonth,
    val later: YearMonth,
    val firstCue: String,
    val secondCue: String,
    val earlierEvidence: ArchiveEvidence,
    val laterEvidence: ArchiveEvidence,
)

/**
 * Lexical heuristic only: flags a possible change of direction between two consecutive calendar months when the
 * selected evidence of the earlier month leans one way and the later month leans the opposite way. It does not
 * understand meaning and says nothing about who changed their mind.
 */
object PerspectiveShiftDetector {

    const val MIN_ARTICLES_PER_SIDE = 2

    val CUE_PAIRS: List<OpposingCuePair> = listOf(
        OpposingCuePair(setOf("increase", "rising"), setOf("decrease", "falling")),
        OpposingCuePair(setOf("support", "supports"), setOf("oppose", "opposes")),
        OpposingCuePair(setOf("approve", "approves"), setOf("reject", "rejects")),
        OpposingCuePair(setOf("tăng", "tăng lên"), setOf("giảm", "giảm xuống")),
        OpposingCuePair(setOf("ủng hộ"), setOf("phản đối")),
        OpposingCuePair(setOf("chấp thuận"), setOf("bác bỏ")),
    )

    private val NEGATIONS = setOf("not", "never", "no", "không", "chưa", "chẳng")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    private class Hit(val side: Int, val cue: String, val evidence: ArchiveEvidence)

    fun detect(earlier: ArchiveMonthWithEvidence, later: ArchiveMonthWithEvidence): ArchivePerspectiveShift? {
        if (earlier.yearMonth.plusMonths(1) != later.yearMonth) return null
        if (earlier.month.articleCount < MIN_ARTICLES_PER_SIDE || later.month.articleCount < MIN_ARTICLES_PER_SIDE) return null
        if (earlier.evidence.isEmpty() || later.evidence.isEmpty()) return null

        for (pair in CUE_PAIRS) {
            val earlierHits = earlier.evidence.mapNotNull { hit(pair, it) }
            val laterHits = later.evidence.mapNotNull { hit(pair, it) }
            for (side in 0..1) {
                val opposite = 1 - side
                // A side seen anywhere in the earlier evidence is not a new direction.
                if (earlierHits.any { it.side == opposite }) continue
                val from = earlierHits.firstOrNull { it.side == side } ?: continue
                val to = laterHits.firstOrNull { it.side == opposite } ?: continue
                return ArchivePerspectiveShift(
                    earlier = earlier.yearMonth,
                    later = later.yearMonth,
                    firstCue = from.cue,
                    secondCue = to.cue,
                    earlierEvidence = from.evidence,
                    laterEvidence = to.evidence,
                )
            }
        }
        return null
    }

    /** One direction per sentence; a sentence mentioning both sides, or negating the cue, gives no signal. */
    private fun hit(pair: OpposingCuePair, evidence: ArchiveEvidence): Hit? {
        val firstCue = findCue(pair.first, evidence.quote)
        val secondCue = findCue(pair.second, evidence.quote)
        // Mixed sentences count as unusable even when a negated cue is the one that is mixed in.
        if (anyMention(pair.first, evidence.quote) && anyMention(pair.second, evidence.quote)) return null
        return when {
            firstCue != null -> Hit(0, firstCue, evidence)
            secondCue != null -> Hit(1, secondCue, evidence)
            else -> null
        }
    }

    private fun anyMention(cues: Set<String>, text: String): Boolean =
        cues.any { WholePhraseMatcher.containsPhrase(text, it) }

    /** Longest matching cue that is not directly preceded by a negation word. */
    private fun findCue(cues: Set<String>, text: String): String? =
        cues.sortedByDescending { it.length }.firstOrNull { cue ->
            WholePhraseMatcher.containsPhrase(text, cue) && !isNegated(text, cue)
        }

    private fun isNegated(text: String, cue: String): Boolean {
        val at = text.indexOf(cue, ignoreCase = true)
        if (at < 0) return false
        val previous = text.substring(0, at).split(WORD_SPLIT).lastOrNull { it.isNotEmpty() } ?: return false
        return previous.lowercase() in NEGATIONS
    }
}
