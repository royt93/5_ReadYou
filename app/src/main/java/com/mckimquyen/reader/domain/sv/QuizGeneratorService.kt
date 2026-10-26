package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.rpg.QuizPeer
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizGeneratorService @Inject constructor() {

    fun detectCategory(title: String, content: String): String {
        val combined = "$title $content".lowercase()
        return when {
            combined.matchesKeywords("ai", "trí tuệ nhân tạo", "google", "apple", "microsoft", "code", "software", "chip", "tech", "nvidia", "robot", "phần mềm", "lập trình", "công nghệ") ->
                CATEGORY_TECH
            combined.matchesKeywords("kinh tế", "cổ phiếu", "lạm phát", "fed", "thị trường", "tài chính", "startup", "doanh thu", "business", "market", "finance", "stock", "crypto", "bitcoin", "ngân hàng") ->
                CATEGORY_BUSINESS
            combined.matchesKeywords("khoa học", "vũ trụ", "nasa", "sinh học", "vật lý", "khí hậu", "nghiên cứu", "science", "space", "biology", "physics", "earth", "climate", "năng lượng") ->
                CATEGORY_SCIENCE
            combined.matchesKeywords("sức khỏe", "thể thao", "dinh dưỡng", "y tế", "bác sĩ", "tập luyện", "giấc ngủ", "health", "diet", "fitness", "sleep", "medicine", "wellness") ->
                CATEGORY_HEALTH
            combined.matchesKeywords("triết học", "tư duy", "lịch sử", "văn hóa", "đạo đức", "sách", "philosophy", "thought", "history", "culture", "ethics", "book") ->
                CATEGORY_PHILOSOPHY
            else -> CATEGORY_GENERAL
        }
    }

    /**
     * Pure and deterministic for the same [articleId] + text, so it is safe to call during composition.
     *
     * The correct option is the article's own headline. Distractors are, in order of preference: headlines of
     * other articles ([peers]), then the article's own secondary statements. The answer is never cut; distractors are clipped
     * to its length plus a seeded random slack so length alone does not reveal the answer.
     */
    fun generateQuiz(
        articleId: String,
        title: String,
        content: String,
        author: String? = null,
        peers: List<QuizPeer> = emptyList(),
    ): QuizQuestion {
        val cleanTitle = cleanText(title)
        val cleanContent = cleanText(content)
        val category = detectCategory(cleanTitle, cleanContent)
        val rng = Random(articleId.hashCode().toLong())
        val isVietnamese = cleanTitle.containsAny("và", "các", "của", "trong", "được", "người", "những", "cho", "với", "không", "này")

        val bodyStatements = extractStatements(cleanContent)
        val allStatements = (listOf(normalize(cleanTitle)) + bodyStatements).filter(String::isNotBlank).distinct()

        // The answer is the article's own headline, so it has the same form as the peer headlines used as distractors.
        // A body sentence only stands in when the article has no usable title.
        val headline = cleanTitle.takeIf { it.length >= MIN_HEADLINE_LENGTH }
        val correct = headline ?: bodyStatements.firstOrNull() ?: allStatements.firstOrNull()
            ?: normalize(articleId).ifBlank { category }
        // Same-category headlines first; other categories only top up when the library has too few of them. Within each
        // group, headlines whose length is closest to the answer come first (seeded jitter breaks ties), so length is
        // not a tell. Distractors are chosen for length rather than cut to it, because cutting can only shorten.
        val (sameCategory, otherCategory) = peers.partition { detectCategory(it.title, it.shortDescription) == category }
        fun byLengthProximity(group: List<QuizPeer>) = group
            .map { cleanText(it.title) }
            .filter { it.length >= MIN_STATEMENT_LENGTH }
            // Key computed once per title: a random value inside the comparator breaks sort's consistency contract.
            .map { it to kotlin.math.abs(it.length - correct.length) + rng.nextInt(LENGTH_JITTER) }
            .sortedBy { it.second }
            .map { it.first }
        val peerTitles = byLengthProximity(sameCategory) + byLengthProximity(otherCategory)
        val secondary = if (headline != null) bodyStatements else bodyStatements.drop(1)
        val distractors = buildDistractors(correct, secondary, allStatements, peerTitles, rng)

        val raw = (listOf(correct to true) + distractors.map { it to false }).shuffled(rng)
        // The answer is never cut. A distractor longer than the answer by more than LENGTH_SLACK is clipped back to that
        // ceiling, so "pick the longest" is not a strategy; shorter distractors are left alone, because padding would be
        // invented text. If the library has nothing as long as the answer, the answer stays the longest (nothing to fix).
        val ceiling = correct.length + LENGTH_SLACK
        val options = uniquify(raw.map { (text, isCorrect) -> Option(if (isCorrect) text else clip(text, ceiling), isCorrect) })

        return QuizQuestion(
            articleId = articleId,
            question = if (isVietnamese) "Tiêu đề nào thuộc về bài viết này?" else "Which headline belongs to this article?",
            options = options.map { it.text },
            correctAnswerIndex = options.indexOfFirst { it.isCorrect },
            explanation = if (isVietnamese) {
                "Tiêu đề của bài viết là: \"${clip(correct, EXPLANATION_LIMIT)}\"."
            } else {
                "The article's headline is: \"${clip(correct, EXPLANATION_LIMIT)}\"."
            },
            category = category,
        )
    }

    private fun buildDistractors(
        correct: String,
        secondaryStatements: List<String>,
        allStatements: List<String>,
        peerTitles: List<String>,
        rng: Random,
    ): List<String> {
        // Other articles' headlines come first: they are real, on-topic, and cannot be told apart by wording.
        val candidates = (peerTitles.distinct() + (secondaryStatements + allStatements).distinct().shuffled(rng))
            .filter { it != correct }
            .distinct()
        val result = linkedSetOf<String>()
        candidates.forEach { candidate ->
            if (result.size < DISTRACTOR_COUNT && candidate != correct) result.add(candidate)
        }
        // Degenerate one-statement articles need three distinct non-answer choices. Keep these clearly marked
        // as unsupported paraphrases of the article rather than inventing unrelated factual distractors.
        var variant = 0
        while (result.size < DISTRACTOR_COUNT) {
            val candidate = unsupportedVariant(correct, variant++)
            if (candidate != correct) result.add(candidate)
        }
        return result.toList()
    }

    private fun unsupportedVariant(statement: String, variant: Int): String {
        val words = statement.split(WHITESPACE).filter(String::isNotBlank)
        val rotated = if (words.size > 1) {
            val offset = (variant + 1) % words.size
            (words.drop(offset) + words.take(offset)).joinToString(" ")
        } else {
            "$statement ${variant + 1}"
        }
        return "Not supported: $rotated"
    }

    private fun extractStatements(content: String): List<String> =
        content.split(SENTENCE_BOUNDARY)
            .map(::normalize)
            .filter { it.length >= MIN_STATEMENT_LENGTH }
            .distinct()
            .take(MAX_STATEMENTS)

    /** Clips at a word boundary to [maxLength]; never pads, so a short statement stays short. */
    private fun clip(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        val cut = value.take(maxLength)
        return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-').ifBlank { cut }
    }

    private fun uniquify(options: List<Option>): List<Option> {
        val seen = mutableSetOf<String>()
        return options.map { option ->
            var text = option.text
            var n = 1
            while (!seen.add(text)) text = "${option.text} (${++n})"
            option.copy(text = text)
        }
    }

    private fun cleanText(value: String): String = value.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim()

    private fun normalize(value: String): String = value.replace(WHITESPACE, " ").trim().trimEnd('.', '!', '?', ';', ':')

    private fun String.matchesKeywords(vararg keywords: String): Boolean {
        val lower = this.lowercase()
        val tokens = lower.split(Regex("[^\\p{L}\\p{Nd}]+")).toSet()
        return keywords.any { kw ->
            if (kw.contains(" ")) {
                lower.contains(kw.lowercase())
            } else if (kw.length <= 4) {
                kw.lowercase() in tokens
            } else {
                lower.contains(kw.lowercase())
            }
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { contains(it, ignoreCase = true) }

    private data class Option(val text: String, val isCorrect: Boolean)

    companion object {
        private const val DISTRACTOR_COUNT = 3
        private const val MIN_STATEMENT_LENGTH = 20
        private const val MIN_HEADLINE_LENGTH = 8
        private const val MAX_STATEMENTS = 24
        private const val EXPLANATION_LIMIT = 90
        private const val LENGTH_SLACK = 12
        private const val LENGTH_JITTER = 14
        private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+|[;\\n]+")
        private val WHITESPACE = Regex("\\s+")
        private val HTML_TAG = Regex("<[^>]*>")

        const val CATEGORY_TECH = "Tech & AI"
        const val CATEGORY_BUSINESS = "Business & Finance"
        const val CATEGORY_SCIENCE = "Science & Nature"
        const val CATEGORY_HEALTH = "Health & Lifestyle"
        const val CATEGORY_PHILOSOPHY = "Philosophy & Ideas"
        const val CATEGORY_GENERAL = "General Knowledge"
    }
}
