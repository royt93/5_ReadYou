package com.mckimquyen.reader.domain.model.galaxy

import java.util.Locale

/**
 * Pure, deterministic galaxy construction: one star per read article. Two relations draw a line:
 * a shared concept, or at least [MIN_SHARED_TERMS] rare words in both headlines. Output is bounded so the canvas never
 * draws more than [MAX_STARS] + [MAX_LINES] shapes.
 */
object GalaxyGraphBuilder {
    const val MAX_STARS = 600

    /** Every line is a canvas draw call, so keep only the strongest. */
    const val MAX_LINES = 400

    /** A concept shared by hundreds of articles would imply O(n²) pairs; link each star to its nearest by date only. */
    const val MAX_NEIGHBORS_PER_STAR = 3

    /** A word in more headlines than this ("says", "trump") links everything and therefore means nothing. */
    const val MAX_WORD_DOC_FREQ = 8

    /** One shared word is too often a coincidence ("police", "city"); two almost always mean the same story. */
    const val MIN_SHARED_TERMS = 2

    private val byDateThenId = compareBy<GalaxyStar>({ it.date }, { it.id })

    fun build(
        articles: List<GalaxyStar>,
        from: Long = Long.MIN_VALUE,
        to: Long = Long.MAX_VALUE,
    ): GalaxyGraph {
        val stars = articles
            .filter { it.date in from..to }
            .map { star -> star.copy(concepts = star.concepts.mapNotNullTo(linkedSetOf(), ::normalize)) }
            .sortedWith(byDateThenId.reversed())
            .take(MAX_STARS)
            .sortedWith(byDateThenId)
        if (stars.isEmpty()) return GalaxyGraph.EMPTY

        val conceptLines = conceptLines(stars)
        val titleLines = titleLines(stars)

        val byPair = HashMap<Pair<String, String>, GalaxyLine>()
        conceptLines.forEach { byPair[orderedPair(it.a, it.b)] = it }

        val extra = mutableListOf<GalaxyLine>()
        titleLines.forEach { candidate ->
            val key = orderedPair(candidate.a, candidate.b)
            val existing = byPair[key]
            if (existing != null) {
                // One line per pair: keep the concept line, but let the card say which words the headlines share.
                byPair[key] = existing.copy(sharedTerms = candidate.sharedTerms)
            } else {
                extra += candidate
            }
        }

        // Concept lines first (most shared concepts first); then title-word lines in the order titleLines ranked them,
        // strongest first. Re-sorting by id here would throw that ranking away, so MAX_LINES would cut the wrong lines.
        val conceptOrdered = byPair.values.sortedWith(
            compareByDescending<GalaxyLine> { it.sharedConcepts }.thenBy { it.a }.thenBy { it.b },
        )
        val lines = (conceptOrdered + extra).take(MAX_LINES)
        return GalaxyGraph(stars, lines)
    }

    private fun conceptLines(stars: List<GalaxyStar>): List<GalaxyLine> {
        val byConcept = linkedMapOf<String, MutableList<GalaxyStar>>()
        stars.forEach { star -> star.concepts.forEach { byConcept.getOrPut(it) { mutableListOf() }.add(star) } }

        val pairCounts = hashMapOf<Pair<String, String>, Int>()
        byConcept.values.forEach { members ->
            for (i in members.indices) {
                for (j in i + 1..minOf(i + MAX_NEIGHBORS_PER_STAR, members.lastIndex)) {
                    pairCounts.merge(members[i].id to members[j].id, 1, Int::plus)
                }
            }
        }
        return pairCounts.entries
            .map { (pair, count) -> GalaxyLine(pair.first, pair.second, count) }
            .sortedWith(compareByDescending<GalaxyLine> { it.sharedConcepts }.thenBy { it.a }.thenBy { it.b })
            .take(MAX_LINES)
    }

    /**
     * Stars whose headlines share at least [MIN_SHARED_TERMS] rare words, strongest first. A word's weight is 1/df,
     * so a word only two headlines use outranks one that eight do. Each star keeps at most
     * [MAX_NEIGHBORS_PER_STAR] of these lines.
     */
    private fun titleLines(stars: List<GalaxyStar>): List<GalaxyLine> {
        val words = stars.associate { it.id to TitleWords.extract(it.title) }
        val docFreq = HashMap<String, Int>()
        words.values.forEach { set -> set.forEach { docFreq.merge(it, 1, Int::plus) } }

        // Inverted index limited to rare words: each list has at most MAX_WORD_DOC_FREQ stars, so the pair loop is tiny.
        val starsByWord = HashMap<String, MutableList<String>>()
        words.forEach { (id, set) ->
            set.forEach { word ->
                if (docFreq.getValue(word) <= MAX_WORD_DOC_FREQ) starsByWord.getOrPut(word) { mutableListOf() }.add(id)
            }
        }

        val sharedByPair = HashMap<Pair<String, String>, MutableList<String>>()
        starsByWord.forEach { (word, ids) ->
            val sorted = ids.sorted()
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    sharedByPair.getOrPut(sorted[i] to sorted[j]) { mutableListOf() }.add(word)
                }
            }
        }

        class Candidate(val a: String, val b: String, val terms: List<String>, val score: Double)

        val ranked = sharedByPair.entries
            .filter { it.value.size >= MIN_SHARED_TERMS }
            .map { (pair, terms) ->
                Candidate(pair.first, pair.second, terms.sorted(), terms.sumOf { 1.0 / docFreq.getValue(it) })
            }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.a }.thenBy { it.b })

        val degree = HashMap<String, Int>()
        val kept = mutableListOf<GalaxyLine>()
        for (candidate in ranked) {
            if ((degree[candidate.a] ?: 0) >= MAX_NEIGHBORS_PER_STAR) continue
            if ((degree[candidate.b] ?: 0) >= MAX_NEIGHBORS_PER_STAR) continue
            degree.merge(candidate.a, 1, Int::plus)
            degree.merge(candidate.b, 1, Int::plus)
            kept += GalaxyLine(candidate.a, candidate.b, 0, GalaxyLineKind.TITLE_WORDS, candidate.terms)
        }
        return kept
    }

    private fun orderedPair(a: String, b: String) = if (a <= b) a to b else b to a

    private fun normalize(concept: String): String? =
        concept.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
}
