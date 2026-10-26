package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.rpg.QuizPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuizGeneratorServiceTest {

    private lateinit var service: QuizGeneratorService

    @Before
    fun setUp() {
        service = QuizGeneratorService()
    }

    @Test
    fun detectCategory_techKeywords_returnsTech() {
        val cat = service.detectCategory("Google announces new AI chip", "The chip outperforms previous neural models.")
        assertEquals(QuizGeneratorService.CATEGORY_TECH, cat)
    }

    @Test
    fun detectCategory_financeKeywords_returnsBusiness() {
        val cat = service.detectCategory("Fed raises interest rates as inflation cools", "Wall Street stocks jumped on the news.")
        assertEquals(QuizGeneratorService.CATEGORY_BUSINESS, cat)
    }

    @Test
    fun detectCategory_scienceKeywords_returnsScience() {
        val cat = service.detectCategory("NASA discovers water molecules on Europa", "Space exploration probe sends data back to Earth.")
        assertEquals(QuizGeneratorService.CATEGORY_SCIENCE, cat)
    }

    @Test
    fun detectCategory_healthKeywords_returnsHealth() {
        val cat = service.detectCategory("5 Habits to improve sleep and circadian rhythm", "Proper fitness and diet enhance cognitive health.")
        assertEquals(QuizGeneratorService.CATEGORY_HEALTH, cat)
    }

    @Test
    fun detectCategory_philosophyKeywords_returnsPhilosophy() {
        val cat = service.detectCategory("Marcus Aurelius and the Stoic art of emotional resilience", "A study on ancient philosophy and mind ethics.")
        assertEquals(QuizGeneratorService.CATEGORY_PHILOSOPHY, cat)
    }

    @Test
    fun generateQuiz_producesValidQuestionAndOptions() {
        val quiz = service.generateQuiz(
            articleId = "art_12345",
            title = "Breakthrough in Quantum Computing Architecture",
            content = "Researchers stabilized 1,000 qubits at room temperature. This enables faster simulations for pharmaceutical development.",
            author = "Dr. Alice"
        )

        assertEquals("art_12345", quiz.articleId)
        assertTrue(quiz.question.isNotBlank())
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.correctAnswerIndex in quiz.options.indices)
        assertEquals(4, quiz.options.toSet().size)
        assertTrue(quiz.explanation.isNotBlank())
        assertTrue(quiz.options[quiz.correctAnswerIndex].isNotBlank())
    }

    @Test
    fun generateQuiz_deterministicForSameArticleId() {
        val first = service.generateQuiz("article_xyz", "Title A", "Content A. More useful content here.", "Author")
        val second = service.generateQuiz("article_xyz", "Title A", "Content A. More useful content here.", "Author")

        assertEquals(first.question, second.question)
        assertEquals(first.options, second.options)
        assertEquals(first.correctAnswerIndex, second.correctAnswerIndex)
        assertEquals(first.explanation, second.explanation)
    }

    @Test
    fun generateQuiz_vietnameseArticle_producesVietnameseQuiz() {
        val quiz = service.generateQuiz(
            articleId = "art_vi_1",
            title = "Việt Nam phát triển thành công mô hình ngôn ngữ lớn tiếng Việt",
            content = "Các chuyên gia công nghệ đã công bố mô hình AI mã nguồn mở cho người dùng Việt Nam và doanh nghiệp trong nước. Nền tảng hỗ trợ nhiều trường đại học.",
            author = "Nguyễn Văn A"
        )

        assertTrue(quiz.question.isNotBlank())
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.correctAnswerIndex in quiz.options.indices)
    }

    private val articleA = Pair(
        "AI chip researchers announce a new neural processor design for hospital imaging systems",
        "Researchers designed neural processors for medical imaging in several large hospitals. " +
            "The prototype reduces energy use in hospital scanners by a measurable margin. " +
            "Clinicians expect shorter waiting times for patients who need urgent scans."
    )
    private val articleB = Pair(
        "AI chip team builds a language model accelerator to speed up inference in data centers",
        "Engineers built processors that run large language models inside modern data centers. " +
            "The device improves inference speed while using less cooling capacity overall. " +
            "Operators plan to deploy the accelerator across regional server clusters next year."
    )

    @Test
    fun generateQuiz_distractorsComeFromTheArticleAndDifferBetweenArticlesOfTheSameCategory() {
        // Same article id => same seed => same question template, so any difference below is caused by article text only.
        val first = service.generateQuiz("shared-seed", articleA.first, articleA.second)
        val second = service.generateQuiz("shared-seed", articleB.first, articleB.second)

        assertEquals(QuizGeneratorService.CATEGORY_TECH, first.category)
        assertEquals(first.category, second.category)
        assertFalse(distractors(first) == distractors(second))
        assertTrue("distractors must be intact article A details: ${distractors(first)}", distractors(first).all { comesFromArticle(it, articleA) })
        assertTrue("distractors must be intact article B details: ${distractors(second)}", distractors(second).all { comesFromArticle(it, articleB) })
    }

    @Test
    fun generateQuiz_neverUsesTheOldStaticDistractors() {
        val old = listOf(
            "Global financial restructuring plans for 2030",
            "Long-term impact of extreme weather in Antarctica",
            "The initiative was canceled indefinitely due to budget cuts",
            "Transition back to manual, analog methodologies",
        )
        (0 until 30).forEach { i ->
            val quiz = service.generateQuiz("static-check-$i", articleA.first, articleA.second)
            assertTrue(quiz.options.none { it in old })
        }
    }

    @Test
    fun generateQuiz_correctAnswerIsLongestInLessThanFortyPercentOfSampleCorpus() {
        val samples = (0 until 40).map { index ->
            val title = "AI research group number $index announces a new processor design for hospital imaging systems"
            val content = "Researchers at laboratory $index test a processor design for medical images from busy clinics. " +
                "Engineers report lower energy use and faster inference across several large hospitals nationwide. " +
                "The team plans additional clinical tests with partner hospitals during the next year. " +
                "Independent reviewers will publish their findings after the trial ends."
            service.generateQuiz("length-sample-$index", title, content)
        }

        val longestCorrect = samples.count { quiz ->
            val correctLength = quiz.options[quiz.correctAnswerIndex].length
            quiz.options.indices.filter { it != quiz.correctAnswerIndex }
                .all { correctLength > quiz.options[it].length }
        }

        // Ties are NOT counted as "longest": a tie gives no length signal to the player.
        assertTrue("Expected fewer than 40% longest correct, got $longestCorrect/${samples.size}", longestCorrect * 100 < samples.size * 40)
    }

    @Test
    fun generateQuiz_sparseAndRepeatedTextStillProducesFourDistinctOptions() {
        val inputs = listOf(
            Triple("empty", "", ""),
            Triple("short", "AI", "AI"),
            Triple("repeat", "Science report", "Science report explains the result. Science report explains the result.")
        )

        inputs.forEach { (id, title, content) ->
            val quiz = service.generateQuiz(id, title, content)
            assertEquals(4, quiz.options.size)
            assertEquals(4, quiz.options.toSet().size)
            assertTrue(quiz.options.all(String::isNotBlank))
            assertTrue(quiz.correctAnswerIndex in quiz.options.indices)
        }
        val emptyQuiz = service.generateQuiz("empty", "", "")
        assertEquals(4, emptyQuiz.options.size)
        assertEquals(4, emptyQuiz.options.toSet().size)
        assertTrue(emptyQuiz.correctAnswerIndex in emptyQuiz.options.indices)
    }

    private val bbcPeers = listOf(
        QuizPeer("Venezuelan opposition leader returns after months in exile", "Machado had left the country a month before the seizure."),
        QuizPeer("Two men killed by falling trees as Storm Boelo hits UK", "An amber wind warning has now expired."),
        QuizPeer("Antonelli delivers the race of a champion", "The young driver won in dramatic style."),
        QuizPeer("Sharon Osbourne says she visits Ozzy's grave every day", "She spoke about grief in an interview."),
    )

    @Test
    fun generateQuiz_oneSentenceArticle_usesOtherArticleTitlesAsDistractors() {
        val quiz = service.generateQuiz(
            "bbc-1", "Lula wins but faces hostile Congress and divided country",
            "President Lula has pulled of a surprise win, but he will face a hostile Congress and a polarised country.",
            peers = bbcPeers,
        )

        val peerTitles = bbcPeers.map { it.title }
        assertEquals(4, quiz.options.size)
        assertTrue("no synthetic filler expected: ${quiz.options}", quiz.options.none { it.startsWith("Not supported") })
        assertEquals(3, distractors(quiz).count { d -> peerTitles.any { it.startsWith(d.trimEnd('.', ' ')) || d.startsWith(it.take(20)) } })
    }

    @Test
    fun generateQuiz_peerDistractors_neverRepeatTheCorrectAnswerOrEachOther() {
        val quiz = service.generateQuiz(
            "bbc-2", "Lula wins", "President Lula has pulled of a surprise win in the election.",
            peers = bbcPeers + QuizPeer("Lula wins", "duplicate of the article title"),
        )

        assertEquals(4, quiz.options.toSet().size)
    }

    @Test
    fun generateQuiz_peerSelectionIsDeterministicAndDiffersBetweenArticles() {
        // Production draws from up to 60 stored headlines; 4 would make a collision of 3-of-4 likely and prove nothing.
        val library = (1..30).map { QuizPeer("Library headline number $it about something different", "Description $it is long enough") }
        val a1 = service.generateQuiz("id-a", "Alpha story headline here", "Alpha body statement is long enough.", peers = library)
        val a2 = service.generateQuiz("id-a", "Alpha story headline here", "Alpha body statement is long enough.", peers = library)
        val b = service.generateQuiz("id-b", "Beta story headline here", "Beta body statement is long enough.", peers = library)

        assertEquals(a1.options, a2.options)
        assertFalse(distractors(a1) == distractors(b))
    }

    @Test
    fun generateQuiz_noPeersAndSparseArticle_stillReturnsFourDistinctOptions() {
        val quiz = service.generateQuiz("solo", "Only title here", "One short body statement here.", peers = emptyList())

        assertEquals(4, quiz.options.toSet().size)
    }

    @Test
    fun generateQuiz_peerPathStillMeetsLengthBias() {
        val longest = (0 until 40).count { i ->
            val q = service.generateQuiz("peer-len-$i", "Headline number $i about a hospital imaging system", "Researchers at laboratory $i test a processor design for medical images.", peers = bbcPeers)
            val correct = q.options[q.correctAnswerIndex].length
            q.options.indices.filter { it != q.correctAnswerIndex }.all { correct > q.options[it].length }
        }
        assertTrue("longest-correct $longest/40", longest * 100 < 40 * 40)
    }

    @Test
    fun generateQuiz_correctAnswerIsTheArticlesOwnHeadline_inTheSameFormAsTheDistractors() {
        val title = "Storm Boelo leaves 90,000 properties without power"
        val quiz = service.generateQuiz(
            "headline-1", title, "An amber wind warning has now expired after severe weather sweeps across the UK.",
            peers = bbcPeers,
        )

        assertEquals(title, quiz.options[quiz.correctAnswerIndex])
        assertTrue("question must ask for the headline: ${quiz.question}", quiz.question.contains("headline", ignoreCase = true))
        val bodySentence = "An amber wind warning has now expired after severe weather sweeps across the UK"
        assertTrue("no body sentence among the options: ${quiz.options}", quiz.options.none { bodySentence.startsWith(it) })
        assertEquals(bbcPeers.map { it.title }.toSet().size, 4)
        assertEquals(3, distractors(quiz).count { d -> bbcPeers.any { it.title.startsWith(d) } })
    }

    @Test
    fun generateQuiz_vietnameseQuestionAsksForTheHeadline() {
        val quiz = service.generateQuiz(
            "headline-vi", "Việt Nam phát triển thành công mô hình ngôn ngữ lớn của người dùng",
            "Các chuyên gia công nghệ đã công bố mô hình AI mã nguồn mở cho người dùng Việt Nam.",
            peers = bbcPeers,
        )

        assertTrue(quiz.question.contains("tiêu đề", ignoreCase = true))
    }

    @Test
    fun generateQuiz_blankTitleFallsBackToABodyStatementInsteadOfAnEmptyOption() {
        val quiz = service.generateQuiz("no-title", "", "The only real sentence in this article is this one.", peers = bbcPeers)

        assertTrue(quiz.options.all(String::isNotBlank))
        assertEquals(4, quiz.options.toSet().size)
    }

    @Test
    fun generateQuiz_neverTruncatesTheCorrectHeadline() {
        val titles = listOf(
            "Venezuelan opposition leader María Machado returns after months in exile",
            "Two men killed by falling trees as Storm Boelo leaves 90,000 properties without power",
            "Is 'ferritin face' a real thing - and can you really spot low iron from looks?",
        )
        (0 until 60).forEach { i ->
            val title = titles[i % titles.size]
            val quiz = service.generateQuiz("trunc-$i", title, "A body statement that is long enough to count.", peers = bbcPeers)
            assertEquals("headline must stay whole", title, quiz.options[quiz.correctAnswerIndex])
        }
    }

    @Test
    fun generateQuiz_picksDistractorsOfSimilarLength_soNeitherLongestNorShortestIsAStrategy() {
        // A realistic library: headlines of widely varying length. The answer's length sits in the middle of it.
        val library = (0 until 60).map { QuizPeer("Library headline ${"x".repeat(it % 50)} number $it of the day", "Description $it long enough") }
        val title = "Our own headline of a middling length for this article here"
        val longest = (0 until 60).count { i ->
            val q = service.generateQuiz("sim-$i", title, "A body statement that is long enough to count.", peers = library)
            val correct = q.options[q.correctAnswerIndex].length
            q.options.indices.filter { it != q.correctAnswerIndex }.all { correct > q.options[it].length }
        }
        val shortest = (0 until 60).count { i ->
            val q = service.generateQuiz("sim-$i", title, "A body statement that is long enough to count.", peers = library)
            val correct = q.options[q.correctAnswerIndex].length
            q.options.indices.filter { it != q.correctAnswerIndex }.all { correct < q.options[it].length }
        }
        assertTrue("answer strictly longest in $longest/60", longest * 100 < 60 * 40)
        assertTrue("answer strictly shortest in $shortest/60", shortest * 100 < 60 * 40)
    }

    @Test
    fun generateQuiz_neverPadsOrCutsTheAnswer_evenWhenEveryPeerIsMuchLonger() {
        val longPeers = (1..8).map { QuizPeer("A very long distractor headline number $it that rambles on and on for ages", "Description $it long enough") }
        val title = "Short headline here"
        (0 until 20).forEach { i ->
            val q = service.generateQuiz("cap-$i", title, "A body statement that is long enough to count.", peers = longPeers)
            assertEquals("the answer is returned exactly as the article's own headline", title, q.options[q.correctAnswerIndex])
            // Distractors are clipped to the ceiling (answer + slack) rather than left arbitrarily long.
            assertTrue(q.options.indices.filter { it != q.correctAnswerIndex }.all { q.options[it].length <= title.length + 12 })
        }
    }

    private fun distractors(quiz: com.mckimquyen.reader.domain.model.rpg.QuizQuestion): Set<String> =
        quiz.options.filterIndexed { index, _ -> index != quiz.correctAnswerIndex }.toSet()

    private fun comesFromArticle(option: String, article: Pair<String, String>): Boolean {
        val sourceStatements = (article.first + ". " + article.second)
            .split(Regex("(?<=[.!?])\\s+|[;\\n]+"))
            .map { it.trim().trimEnd('.', '!', '?', ';', ':') }
        return sourceStatements.any { source ->
            option.length <= source.length && source.contains(option, ignoreCase = true)
        }
    }
}
