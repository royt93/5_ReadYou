package com.mckimquyen.reader.infrastructure.ai

import com.google.gson.JsonParser
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import java.util.Locale
import kotlin.math.ceil

/**
 * Utility extractor for structured article highlights.
 *
 * Provides:
 * 1. Robust JSON / text parsing of Gemini responses into [ArticleHighlights].
 * 2. Intelligent, deterministic offline heuristic summarization when network/Gemini is unavailable.
 * 3. Reading time saved estimation and topic tag discovery.
 */
object ArticleHighlightsExtractor {

    private const val WORDS_PER_MINUTE = 220.0

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "this", "that", "from", "are", "was", "were", "will",
        "has", "have", "had", "can", "may", "not", "but", "all", "any", "how", "what", "when",
        "where", "why", "who", "which", "their", "there", "they", "been", "about", "into",
        "more", "some", "other", "just", "like", "also", "would", "could", "should", "your",
        "than", "then", "its", "over", "after", "most", "only", "such", "these", "those",
        "trong", "nhung", "nhung", "cac", "mot", "duoc", "cho", "voi", "cua", "nay", "tren"
    )

    /**
     * Calculates the estimated reading time saved in minutes.
     */
    fun calculateReadingTimeSaved(articleWordCount: Int, summaryWordCount: Int): Int {
        val articleMinutes = ceil(articleWordCount.coerceAtLeast(0) / WORDS_PER_MINUTE).toInt()
        val summaryMinutes = ceil(summaryWordCount.coerceAtLeast(0) / WORDS_PER_MINUTE).toInt()
        return (articleMinutes - summaryMinutes).coerceAtLeast(1)
    }

    /**
     * Parses Gemini API response text into [ArticleHighlights].
     * Handles raw JSON, Markdown code blocks (` ```json ... ``` `), or bulleted plain text fallback.
     */
    fun parseGeminiResponse(rawText: String, totalWordCount: Int): ArticleHighlights {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) {
            return ArticleHighlights()
        }

        // 1. Try to extract and parse JSON using Gson (cross-platform, works on JVM & Android)
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val jsonCandidate = cleaned.substring(firstBrace, lastBrace + 1)
            try {
                val json = JsonParser.parseString(jsonCandidate).asJsonObject
                val tldr = if (json.has("tldr") && !json.get("tldr").isJsonNull) {
                    json.get("tldr").asString.trim()
                } else ""

                val takeawaysList = mutableListOf<String>()
                if (json.has("takeaways") && json.get("takeaways").isJsonArray) {
                    val takeawaysArray = json.getAsJsonArray("takeaways")
                    for (element in takeawaysArray) {
                        if (!element.isJsonNull) {
                            val sanitized = sanitizeBulletPoint(element.asString)
                            if (sanitized.isNotBlank()) {
                                takeawaysList.add(sanitized)
                            }
                        }
                    }
                }

                val tagsList = mutableListOf<String>()
                if (json.has("tags") && json.get("tags").isJsonArray) {
                    val tagsArray = json.getAsJsonArray("tags")
                    for (element in tagsArray) {
                        if (!element.isJsonNull) {
                            val tag = element.asString.trim().removePrefix("#")
                            if (tag.isNotBlank()) {
                                tagsList.add(tag)
                            }
                        }
                    }
                }

                if (takeawaysList.isNotEmpty() || tldr.isNotBlank()) {
                    val summaryWordCount = countWords("$tldr ${takeawaysList.joinToString(" ")}")
                    val timeSaved = calculateReadingTimeSaved(totalWordCount, summaryWordCount)
                    return ArticleHighlights(
                        tldr = tldr,
                        keyTakeaways = takeawaysList,
                        readingTimeSavedMin = timeSaved,
                        tags = tagsList,
                        isOfflineFallback = false,
                    )
                }
            } catch (_: Exception) {
                // Fallthrough to text parser
            }
        }

        // 2. Fallback: Parse line-by-line bullet points or structured text
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
        val bulletPoints = mutableListOf<String>()
        var foundTldr = ""

        for (line in lines) {
            val stripped = sanitizeBulletPoint(line)
            if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•") ||
                line.matches(Regex("^\\d+[.)]\\s+.*"))
            ) {
                if (stripped.isNotBlank()) bulletPoints.add(stripped)
            } else if (foundTldr.isBlank() && stripped.length > 20) {
                foundTldr = stripped
            } else if (stripped.isNotBlank()) {
                bulletPoints.add(stripped)
            }
        }

        val summaryWordCount = countWords("$foundTldr ${bulletPoints.joinToString(" ")}")
        val timeSaved = calculateReadingTimeSaved(totalWordCount, summaryWordCount)

        return ArticleHighlights(
            tldr = foundTldr,
            keyTakeaways = if (bulletPoints.isNotEmpty()) bulletPoints else listOf(cleaned.take(150)),
            readingTimeSavedMin = timeSaved,
            tags = emptyList(),
            isOfflineFallback = false,
        )
    }

    /**
     * Generates high-quality offline heuristic highlights using sentence scoring,
     * title keyword resonance, and paragraph lead heuristics.
     */
    fun extractOfflineHighlights(title: String, plainText: String): ArticleHighlights {
        val text = plainText.trim()
        if (text.isBlank()) {
            return ArticleHighlights(isOfflineFallback = true)
        }

        val totalWordCount = countWords(text)
        val candidateSentences = extractCandidateSentences(text)
        if (candidateSentences.isEmpty()) {
            val fallbackBullet = text.take(120).trim()
            return ArticleHighlights(
                tldr = fallbackBullet,
                keyTakeaways = listOf(fallbackBullet),
                readingTimeSavedMin = calculateReadingTimeSaved(totalWordCount, countWords(fallbackBullet)),
                isOfflineFallback = true,
            )
        }

        // Extract title keywords
        val titleKeywords = title.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()

        // Score sentences
        data class ScoredSentence(val sentence: String, val score: Int, val originalIndex: Int)
        val scored = candidateSentences.mapIndexed { index, sentence ->
            var score = 0
            val lower = sentence.lowercase(Locale.ROOT)

            // Title relevance bonus
            for (keyword in titleKeywords) {
                if (lower.contains(keyword)) score += 4
            }

            // Informational stats / numbers bonus
            if (sentence.contains(Regex("\\d+%?|[$€£¥₫]"))) score += 2

            // Lead sentence bonus (first few sentences usually convey key thesis)
            if (index == 0) score += 5
            else if (index <= 2) score += 2

            // Sentence length sweet spot
            if (sentence.length in 50..180) score += 2

            ScoredSentence(sentence, score, index)
        }

        // Sort by score descending
        val sorted = scored.sortedByDescending { it.score }
        val tldr = sorted.firstOrNull()?.sentence.orEmpty()

        // Take 3-4 distinct takeaways, ordered by original occurrence for narrative flow
        val takeaways = sorted.drop(1)
            .take(4)
            .sortedBy { it.originalIndex }
            .map { sanitizeBulletPoint(it.sentence) }
            .filter { it.isNotBlank() }

        val tags = extractTags(text, title)
        val summaryWords = countWords("$tldr ${takeaways.joinToString(" ")}")
        val timeSaved = calculateReadingTimeSaved(totalWordCount, summaryWords)

        return ArticleHighlights(
            tldr = tldr,
            keyTakeaways = if (takeaways.isNotEmpty()) takeaways else listOf(tldr),
            readingTimeSavedMin = timeSaved,
            tags = tags,
            isOfflineFallback = true,
        )
    }

    /**
     * Extracts salient topic tags by analyzing prominent words from title and text.
     */
    fun extractTags(text: String, title: String = "", maxTags: Int = 3): List<String> {
        val wordFreq = mutableMapOf<String, Int>()
        val tokens = (title + " " + text.take(2000))
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length in 4..20 }

        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            if (lower !in STOP_WORDS && !lower.matches(Regex("\\d+"))) {
                // Capitalize first letter for display
                val displayTag = token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                wordFreq[displayTag] = (wordFreq[displayTag] ?: 0) + 1
            }
        }

        return wordFreq.entries
            .sortedByDescending { it.value }
            .take(maxTags)
            .map { it.key }
    }

    private fun extractCandidateSentences(text: String): List<String> {
        // Split by standard sentence delimiters (.!?\n)
        return text.split(Regex("(?<=[.!?\\n])\\s+"))
            .map { it.trim() }
            .filter { s ->
                s.length in 25..350 &&
                    !s.startsWith("http", ignoreCase = true) &&
                    !s.startsWith("Copyright", ignoreCase = true) &&
                    !s.startsWith("Photo by", ignoreCase = true)
            }
    }

    private fun sanitizeBulletPoint(text: String): String {
        return text
            .replace(Regex("^[-*•–—]\\s*"), "")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .trim()
    }

    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
