package com.mckimquyen.reader.domain.model.rpg

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiQuizPolicyParseTest {

    private fun json(
        question: String = "Which body launched the rocket?",
        options: List<String> = listOf("The space agency", "A shipping firm", "A local council", "A film studio"),
        correct: Any = 0,
        explanation: String = "The preview names the agency.",
    ): String {
        val opts = options.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
        val c = if (correct is String) "\"$correct\"" else correct.toString()
        return """{"question":"${question.replace("\"", "\\\"")}","options":[$opts],"correctIndex":$c,"explanation":"${explanation.replace("\"", "\\\"")}"}"""
    }

    private fun parse(raw: String) = AiQuizPolicy.parseAndValidate(raw, articleId = "art-1", category = "General Knowledge")

    private fun rejected(raw: String): AiQuizRejection = (parse(raw) as AiQuizParse.Rejected).reason

    private fun valid(raw: String): QuizQuestion = (parse(raw) as AiQuizParse.Valid).quiz

    @Test
    fun aWellFormedAnswerIsAccepted_andKeepsTheCorrectTextAtTheRightIndex() {
        val quiz = valid(json())

        assertEquals("Which body launched the rocket?", quiz.question)
        assertEquals(4, quiz.options.size)
        assertEquals("The space agency", quiz.options[quiz.correctAnswerIndex])
        assertEquals("art-1", quiz.articleId)
        assertEquals("General Knowledge", quiz.category)
        assertEquals("The preview names the agency.", quiz.explanation)
    }

    @Test
    fun notJson_isRejected() {
        assertEquals(AiQuizRejection.NOT_JSON, rejected("Sure! Here is your question"))
        assertEquals(AiQuizRejection.NOT_JSON, rejected(""))
        assertEquals(AiQuizRejection.NOT_JSON, rejected("[1,2,3]"))
    }

    @Test
    fun missingFields_areRejected() {
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"options":["a","b","c","d"],"correctIndex":0,"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","correctIndex":0,"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","options":["a","b","c","d"],"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","options":["a","b","c","d"],"correctIndex":0}"""))
    }

    @Test
    fun anyOptionCountOtherThanFour_isRejected() {
        assertEquals(AiQuizRejection.WRONG_OPTION_COUNT, rejected(json(options = listOf("alpha one", "beta two", "gamma three"))))
        assertEquals(AiQuizRejection.WRONG_OPTION_COUNT, rejected(json(options = listOf("alpha one", "beta two", "gamma three", "delta four", "epsilon five"))))
    }

    @Test
    fun emptyOrBlankOption_isRejected() {
        assertEquals(AiQuizRejection.EMPTY_OPTION, rejected(json(options = listOf("alpha one", "beta two", "gamma three", "   "))))
        assertEquals(AiQuizRejection.EMPTY_OPTION, rejected(json(options = listOf("alpha one", "", "gamma three", "delta four"))))
    }

    @Test
    fun duplicateOptions_areRejected_ignoringCaseAndPunctuation() {
        assertEquals(AiQuizRejection.DUPLICATE_OPTIONS, rejected(json(options = listOf("The agency", "the agency.", "A firm", "A studio"))))
        assertEquals(AiQuizRejection.DUPLICATE_OPTIONS, rejected(json(options = listOf("The agency", "THE AGENCY!", "A firm", "A studio"))))
    }

    @Test
    fun correctIndexMustBeAnIntegerFromZeroToThree() {
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = -1)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = 4)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = 1.5)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = "two")))
        for (i in 0..3) assertTrue(parse(json(correct = i)) is AiQuizParse.Valid)
    }

    @Test
    fun blankQuestion_isRejected_andOverlongQuestionIsRejectedAtTheBoundary() {
        assertEquals(AiQuizRejection.EMPTY_QUESTION, rejected(json(question = "   ")))
        assertTrue(parse(json(question = "q".repeat(200))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.QUESTION_TOO_LONG, rejected(json(question = "q".repeat(201))))
    }

    @Test
    fun overlongOption_isRejectedAtTheBoundary() {
        val ok = "w".repeat(120)
        val long = "w".repeat(121)
        assertTrue(parse(json(options = listOf(ok, "x".repeat(110), "y".repeat(100), "z".repeat(90)))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.OPTION_TOO_LONG, rejected(json(options = listOf(long, "x".repeat(110), "y".repeat(100), "z".repeat(90)))))
    }

    @Test
    fun wildlyDifferentOptionLengths_areRejected_soTheLongestIsNotTheAnswer() {
        val skewed = json(options = listOf("This is a very long and detailed correct answer sentence", "No", "Yes", "Maybe"))

        assertEquals(AiQuizRejection.OPTION_LENGTH_SKEW, rejected(skewed))
    }

    @Test
    fun similarLengthsAreAccepted_atTheRatioBoundary() {
        // 25 vs 10 is exactly 2.5: accepted; 26 vs 10 is above: rejected.
        assertTrue(parse(json(options = listOf("a".repeat(25), "b".repeat(10), "c".repeat(15), "d".repeat(20)))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.OPTION_LENGTH_SKEW, rejected(json(options = listOf("a".repeat(26), "b".repeat(10), "c".repeat(15), "d".repeat(20)))))
    }

    @Test
    fun questionThatContainsTheCorrectAnswerVerbatim_isRejected() {
        val leaky = json(
            question = "Was it the space agency that launched the rocket?",
            options = listOf("the space agency", "A shipping firm", "A local council", "A film studio"),
        )

        assertEquals(AiQuizRejection.ANSWER_LEAKED_IN_QUESTION, rejected(leaky))
    }

    @Test
    fun aShortCorrectAnswerIsNotTreatedAsLeakedJustBecauseItIsASubstring() {
        val shortAnswer = json(
            // "six" is a substring of "sixth" here, but a 3-letter answer is too short to count as a leak.
            question = "What was the sixth item on the list?",
            options = listOf("Six", "Four", "Nine", "Two"),
            correct = 0,
        )

        assertTrue(parse(shortAnswer) is AiQuizParse.Valid)
    }

    @Test
    fun blankExplanation_isRejected_andOverlongExplanationIsCutNotRejected() {
        assertEquals(AiQuizRejection.EMPTY_EXPLANATION, rejected(json(explanation = "   ")))
        val quiz = valid(json(explanation = "e".repeat(500)))
        assertEquals(220, quiz.explanation.length)
    }

    @Test
    fun optionOrderIsShuffledDeterministicallyFromTheArticleId_andTheCorrectIndexFollowsTheText() {
        val a1 = parse(json(correct = 0)) as AiQuizParse.Valid
        val a2 = parse(json(correct = 0)) as AiQuizParse.Valid
        assertEquals("same article, same order", a1.quiz.options, a2.quiz.options)

        val seen = mutableSetOf<Int>()
        for (id in 1..60) {
            val q = (AiQuizPolicy.parseAndValidate(json(correct = 0), "article-$id", "c") as AiQuizParse.Valid).quiz
            assertEquals("The space agency", q.options[q.correctAnswerIndex])
            seen += q.correctAnswerIndex
        }
        assertEquals("the AI always put the answer first; the shuffle must spread it over all four slots", setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun fencedJsonIsAccepted_becauseModelsOftenWrapIt() {
        val fenced = "```json\n" + json() + "\n```"

        assertTrue(parse(fenced) is AiQuizParse.Valid)
    }

    @Test
    fun stringsAreTrimmedAndWhitespaceCollapsed() {
        val quiz = valid(json(question = "  Which   body\nlaunched it?  ", options = listOf(" The space  agency ", "A shipping firm", "A local council", "A film studio")))

        assertEquals("Which body launched it?", quiz.question)
        assertTrue(quiz.options.contains("The space agency"))
    }
}
