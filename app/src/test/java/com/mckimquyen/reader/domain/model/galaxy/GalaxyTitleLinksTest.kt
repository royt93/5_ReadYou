package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyTitleLinksTest {
    private fun star(id: String, title: String, date: Long = 1L, vararg concepts: String) =
        GalaxyStar(id, title, "BBC", date, concepts.toSet())

    private fun titleLines(graph: GalaxyGraph) = graph.lines.filter { it.kind == GalaxyLineKind.TITLE_WORDS }

    @Test fun twoSharedRareWordsLinkTheStarsAndNameTheWords() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Russell Brand arrives at court for rape trial"),
                star("b", "Russell Brand was a sexual predator as rape trial opens"),
            ),
        )
        val line = titleLines(graph).single()
        assertEquals(setOf("a", "b"), setOf(line.a, line.b))
        assertEquals(listOf("brand", "rape", "russell", "trial"), line.sharedTerms)
        assertEquals(0, line.sharedConcepts)
    }

    @Test fun oneSharedWordIsNotEnough() {
        val graph = GalaxyGraphBuilder.build(
            listOf(star("a", "Police raid opposition station"), star("b", "Robot seized by police after incident")),
        )
        assertTrue(titleLines(graph).isEmpty())
    }

    @Test fun aWordInMoreThanTheDocFreqLimitNeverLinks() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        // "sanctions" and "russia" occur in limit + 1 headlines, so both are too common to mean anything.
        val common = (0..limit).map { star("c$it", "Sanctions russia item$it${"x".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(common)
        assertTrue("common words must not link: ${titleLines(graph)}", titleLines(graph).isEmpty())
    }

    @Test fun aWordAtExactlyTheDocFreqLimitStillCounts() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        val rare = (1..limit).map { star("r$it", "Sanctions russia topic$it${"y".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(rare)
        assertTrue("at the limit the words are still rare", titleLines(graph).isNotEmpty())
    }

    @Test fun rarerSharedWordsRankAheadOfCommonerOnes() {
        // weak pair: THREE shared words that four headlines use (score 3 * 1/4 = 0.75).
        // strong pair: only TWO shared words, but only these two headlines use them (score 2 * 1/2 = 1.0).
        // Ranking by the plain number of shared words would put the weak pair first; ranking by rarity must not.
        val stars = listOf(
            star("a1", "Energy ceasefire claim alpha"),
            star("a2", "Energy ceasefire claim bravo"),
            star("a3", "Energy ceasefire claim charlie"),
            star("a4", "Energy ceasefire claim delta"),
            star("z1", "Orangutan released borneo"),
            star("z2", "Orangutan released forest"),
        )
        val lines = titleLines(GalaxyGraphBuilder.build(stars))
        assertEquals(listOf("orangutan", "released"), lines.first().sharedTerms)
        assertEquals(setOf("z1", "z2"), setOf(lines.first().a, lines.first().b))
        assertTrue("the commoner three-word pair still links, just later", lines.any { it.sharedTerms.size == 3 })
    }

    @Test fun eachStarKeepsAtMostTheNeighbourLimitOfTitleLines() {
        val stars = (1..8).map { star("n$it", "Burnham climate revolt crisis report$it${"z".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(stars)
        val degree = titleLines(graph).flatMap { listOf(it.a, it.b) }.groupingBy { it }.eachCount()
        assertTrue("degree: $degree", degree.values.all { it <= GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR })
    }

    @Test fun totalLinesNeverExceedTheDrawBudget() {
        val stars = (0 until GalaxyGraphBuilder.MAX_STARS).map {
            star("%04d".format(it), "Alpha${it % 40} beta${it % 41} gamma${it % 43} delta${it % 47}", it.toLong(), "common")
        }
        assertTrue(GalaxyGraphBuilder.build(stars).lines.size <= GalaxyGraphBuilder.MAX_LINES)
    }

    @Test fun aConceptLinkKeepsItsKindButStillNamesTheSharedWords() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Trump energy ceasefire claim", 1L, "politics"),
                star("b", "Trump energy ceasefire welcomed", 2L, "politics"),
            ),
        )
        val line = graph.lines.single()
        assertEquals(GalaxyLineKind.CONCEPT, line.kind)
        assertEquals(1, line.sharedConcepts)
        assertEquals(listOf("ceasefire", "energy", "trump"), line.sharedTerms)
    }

    @Test fun aPairIsNeverLinkedTwice() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Trump energy ceasefire claim", 1L, "politics"),
                star("b", "Trump energy ceasefire welcomed", 2L, "politics"),
            ),
        )
        assertEquals(1, graph.lines.size)
        assertEquals(graph.lines.size, graph.lines.map { setOf(it.a, it.b) }.toSet().size)
    }

    @Test fun theConceptLinkDoesNotUseUpTheTitleNeighbourBudget() {
        val concept = star("a", "Alpha bravo charlie delta", 1L, "x")
        val conceptPartner = star("b", "Alpha bravo charlie delta", 2L, "x")
        val titleOnly = (1..GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR).map {
            star("t$it", "Alpha bravo charlie delta echo$it${"q".repeat(it)}", 3L + it)
        }
        val graph = GalaxyGraphBuilder.build(listOf(concept, conceptPartner) + titleOnly)
        val aTitleLines = titleLines(graph).count { "a" == it.a || "a" == it.b }
        assertTrue("a keeps its title neighbours next to the concept line: $aTitleLines", aTitleLines >= 1)
    }

    @Test fun sharedTermsAreSortedAndDeterministicRegardlessOfInputOrder() {
        val input = listOf(
            star("a", "Zelensky putin trump summit"),
            star("b", "Putin zelensky trump talks"),
            star("c", "Unrelated gardening tips"),
        )
        assertEquals(GalaxyGraphBuilder.build(input), GalaxyGraphBuilder.build(input.reversed()))
        assertEquals(listOf("putin", "trump", "zelensky"), titleLines(GalaxyGraphBuilder.build(input)).single().sharedTerms)
    }

    @Test fun theDateWindowChangesWhichWordsAreRare() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        val pair = listOf(star("p1", "Pope cancer diagnosis surgery", 100L), star("p2", "Pope cancer diagnosis vatican", 101L))
        val noise = (1..limit).map { star("z$it", "Pope cancer diagnosis filler$it${"k".repeat(it)}", 10L + it) }
        val all = GalaxyGraphBuilder.build(pair + noise)
        val windowed = GalaxyGraphBuilder.build(pair + noise, from = 100L, to = 200L)
        assertTrue("with the noise the shared words are too common: ${titleLines(all)}", titleLines(all).none { setOf(it.a, it.b) == setOf("p1", "p2") })
        assertNotNull(titleLines(windowed).firstOrNull { setOf(it.a, it.b) == setOf("p1", "p2") })
    }

    @Test fun noTitleLinkWhenTitlesAreEmptyOrShort() {
        val graph = GalaxyGraphBuilder.build(listOf(star("a", ""), star("b", "   "), star("c", "UK EU US")))
        assertTrue(graph.lines.isEmpty())
        assertFalse(graph.stars.isEmpty())
    }
}
