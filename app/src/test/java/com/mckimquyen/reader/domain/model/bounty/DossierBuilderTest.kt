package com.mckimquyen.reader.domain.model.bounty

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class DossierBuilderTest {

    private val zone = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L

    private fun item(
        id: String,
        title: String,
        short: String = "",
        feedName: String = "BBC News",
        date: Long = 1_700_000_000_000L,
    ) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id,
            date = Date(date),
            title = title,
            author = null,
            shortDescription = short,
            img = null,
            link = "https://example.com/$id",
            feedId = "feed-$feedName",
            accountId = 1,
            isUnread = true,
            isStarred = false,
            isReadLater = false,
            updateAt = null,
            aiSummary = null,
            readAt = null,
            audioUrl = null,
            audioMime = null,
            audioDurationSec = null,
        ),
        feed = Feed(
            id = "feed-$feedName",
            name = feedName,
            url = "https://example.com/rss",
            groupId = "group-1",
            accountId = 1,
        ),
    )

    private fun result(query: String, vararg articles: ArticleListItemWithFeed) =
        BountyInvestigationResult(
            query = query,
            articles = articles.toList(),
            sourceCount = articles.map { it.article.feedId }.distinct().size,
            earliestDate = articles.minOfOrNull { it.article.date },
            latestDate = articles.maxOfOrNull { it.article.date },
        )

    @Test
    fun returnsNull_whenFewerThanTwoArticles() {
        assertNull(DossierBuilder.build(result("DeepSeek"), zone, now))
        assertNull(DossierBuilder.build(result("DeepSeek", item("1", "DeepSeek launches")), zone, now))
    }

    @Test
    fun returnsDossier_whenTwoOrMoreArticles() {
        val dossier = DossierBuilder.build(
            result("DeepSeek", item("1", "DeepSeek launches"), item("2", "DeepSeek v2")),
            zone,
            now,
        )
        assertNotNull(dossier)
        assertEquals("DeepSeek", dossier!!.query)
        assertEquals(now, dossier.generatedAtMillis)
    }

    @Test
    fun summary_extractsVerbatimSentencesContainingQuery_wholePhraseOnly() {
        val r = result(
            "disrupt",
            item("1", "Disruption hits city transport.", "Services delayed."),
            item("2", "Startups disrupt the industry.", "Big changes expected."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        // "Disruption" is NOT a whole-phrase match for "disrupt"
        assertEquals(1, dossier.summary.size)
        assertEquals("Startups disrupt the industry.", dossier.summary.single().text)
        assertEquals("2", dossier.summary.single().articleId)
        assertEquals("BBC News", dossier.summary.single().feedName)
    }

    @Test
    fun summary_capsAtThreeSentences_newestFirst() {
        val r = result(
            "AI",
            item("1", "Older AI story.", date = 1_000L),
            item("2", "Second AI story.", date = 2_000L),
            item("3", "Third AI story.", date = 3_000L),
            item("4", "Newest AI story.", date = 4_000L),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertEquals(3, dossier.summary.size)
        assertEquals("Newest AI story.", dossier.summary[0].text)
        assertEquals("Third AI story.", dossier.summary[1].text)
        assertEquals("Second AI story.", dossier.summary[2].text)
    }

    @Test
    fun summary_dropsNearDuplicateSentences_viaJaccard() {
        val r = result(
            "Apple",
            item("1", "Apple unveils new iPhone today in California.", date = 2_000L),
            // Near-duplicate: same word set with tiny change -> Jaccard >= 0.8
            item("2", "Apple unveils new iPhone today in California soon.", date = 1_500L),
            // Distinct sentence -> should be kept
            item("3", "Apple stock reaches all-time high following quarterly earnings.", date = 1_000L),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertEquals(2, dossier.summary.size)
        assertEquals("Apple unveils new iPhone today in California.", dossier.summary[0].text)
        assertEquals("Apple stock reaches all-time high following quarterly earnings.", dossier.summary[1].text)
    }

    @Test
    fun summary_truncatesSentencesAt240Chars_safelyWithoutSurrogateSplit() {
        val emoji = "😀"
        val padding = "x".repeat(239)
        val longSentence = "OpenAI updates platform $padding$emoji trailing words."
        val r = result(
            "OpenAI",
            item("1", longSentence),
            item("2", "Another OpenAI note."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        val quoted = dossier.summary.first { it.articleId == "1" }
        assertTrue(quoted.text.length <= DossierBuilder.MAX_SENTENCE_CHARS)
        assertFalse(Character.isHighSurrogate(quoted.text.last()))
    }

    @Test
    fun summary_isEmpty_whenNoSentenceContainsQuery() {
        val r = result(
            "unrelated",
            item("1", "First story here."),
            item("2", "Second story here."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))
        assertTrue(dossier.summary.isEmpty())
    }

    @Test
    fun timeline_groupsByLocalDate_newestDayFirst() {
        val day1 = 1_700_000_000_000L // 2023-11-14T22:13:20Z
        val day1Later = 1_700_005_000_000L // same UTC day
        val day2 = 1_700_100_000_000L // 2023-11-16

        val r = result(
            "Topic",
            item("1", "Story 1", date = day1),
            item("2", "Story 2", date = day1Later),
            item("3", "Story 3", date = day2),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertEquals(2, dossier.timeline.size)
        assertEquals(LocalDate.parse("2023-11-16"), dossier.timeline[0].day)
        assertEquals(1, dossier.timeline[0].articles.size)
        assertEquals("3", dossier.timeline[0].articles.single().articleId)

        assertEquals(LocalDate.parse("2023-11-14"), dossier.timeline[1].day)
        assertEquals(2, dossier.timeline[1].articles.size)
    }

    @Test
    fun timeline_respectsInjectedZone() {
        // An instant near midnight UTC will land on different calendar days in Auckland vs UTC
        val instant = 1_700_004_000_000L
        val r = result("T", item("1", "A", date = instant), item("2", "B", date = instant + 1000))

        val utc = requireNotNull(DossierBuilder.build(r, ZoneId.of("UTC"), now))
        val auckland = requireNotNull(DossierBuilder.build(r, ZoneId.of("Pacific/Auckland"), now))

        assertNotNull(utc.timeline.single().day)
        assertNotNull(auckland.timeline.single().day)
        assertTrue(utc.timeline.single().day != auckland.timeline.single().day)
    }

    @Test
    fun recurringPhrases_findsCapitalizedPhrasesAppearingInAtLeastTwoArticles() {
        val r = result(
            "chip",
            item("1", "Sam Altman discusses chip strategy with TSMC in Taiwan."),
            item("2", "CEO Sam Altman announces investment alongside TSMC leadership."),
            item("3", "TSMC expands semiconductor manufacturing capacity."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        val phrases = dossier.phrases.associate { it.phrase to it.articleCount }
        assertEquals(3, phrases["TSMC"])
        assertEquals(2, phrases["Sam Altman"])
        // Phrases appearing in only 1 article ("Taiwan", "CEO") are excluded
        assertNull(phrases["Taiwan"])
        assertNull(phrases["CEO"])
    }

    @Test
    fun recurringPhrases_excludesQueryAndStopWords() {
        val r = result(
            "OpenAI",
            item("1", "OpenAI and The White House hold meeting on Monday."),
            item("2", "OpenAI leaders visit The White House on Monday."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        val phraseNames = dossier.phrases.map { it.phrase }
        assertFalse(phraseNames.contains("OpenAI")) // query itself excluded
        assertFalse(phraseNames.contains("Monday")) // stopword (weekday)
        assertFalse(phraseNames.contains("The")) // single stopword
        assertTrue(phraseNames.contains("White House")) // valid phrase kept
    }

    @Test
    fun recurringPhrases_cappedAtEight() {
        // Create 15 capitalized phrases, each in 2 articles
        val articles = (1..15).flatMap { i ->
            listOf(
                item("a-$i", "EntityNumber$i participates in project."),
                item("b-$i", "EntityNumber$i joins the effort."),
            )
        }
        val r = result("project", *articles.toTypedArray())
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertTrue(dossier.phrases.size <= DossierBuilder.MAX_PHRASES)
        assertEquals(8, dossier.phrases.size)
    }

    @Test
    fun recurringPhrases_excludesTheSourceFeedName() {
        val r = result(
            "police",
            item("1", "Watch: BBC joins police on Merseyside patrol."),
            item("2", "BBC follows police on Merseyside raid."),
        )
        val names = requireNotNull(DossierBuilder.build(r, zone, now)).phrases.map { it.phrase }

        assertFalse(names.contains("BBC")) // feed name "BBC News" is attribution noise
        assertTrue(names.contains("Merseyside"))
    }

    @Test
    fun recurringPhrases_emptyWhenTextHasNoCapitals() {
        val r = result(
            "test",
            item("1", "all lowercase words in first story here."),
            item("2", "all lowercase words in second story here."),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))
        assertTrue(dossier.phrases.isEmpty())
    }

    @Test
    fun sourceFramings_groupsByFeed_whenAtLeastTwoFeeds() {
        val r = result(
            "tax",
            item("1", "BBC on tax cut", feedName = "BBC News", date = 2_000L),
            item("2", "BBC follow-up", feedName = "BBC News", date = 1_000L),
            item("3", "Wired on tech tax", feedName = "Wired", date = 3_000L),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertEquals(2, dossier.framings.size)
        assertEquals(2, dossier.sourceCount)
        val bbc = dossier.framings.first { it.feedName == "BBC News" }
        assertEquals(listOf("BBC on tax cut", "BBC follow-up"), bbc.headlines.map { it.title })
        val wired = dossier.framings.first { it.feedName == "Wired" }
        assertEquals(listOf("Wired on tech tax"), wired.headlines.map { it.title })
    }

    @Test
    fun sourceFramings_empty_whenOnlyOneFeedExists() {
        val r = result(
            "tax",
            item("1", "Story 1", feedName = "OnlyFeed"),
            item("2", "Story 2", feedName = "OnlyFeed"),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        assertTrue(dossier.framings.isEmpty())
        assertEquals(1, dossier.sourceCount)
    }

    @Test
    fun sourceFramings_capsHeadlinesAtThreePerSource() {
        val manyBbc = (1..5).map { item("b$it", "BBC Story $it", feedName = "BBC", date = it * 1_000L) }
        val r = result(
            "Story",
            *manyBbc.toTypedArray(),
            item("w1", "Wired Story 1", feedName = "Wired"),
        )
        val dossier = requireNotNull(DossierBuilder.build(r, zone, now))

        val bbc = dossier.framings.first { it.feedName == "BBC" }
        assertEquals(3, bbc.headlines.size)
        assertEquals("BBC Story 5", bbc.headlines[0].title) // newest first
    }
}
