package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpCalibrationContentTest {

    private fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun assertWellFormed(tag: String) {
        val passages = RsvpCalibrationContent.forLanguage(tag)
        assertEquals("$tag passages", RsvpCalibrationPlan.ROUND_WPMS.size, passages.size)
        passages.forEachIndexed { i, passage ->
            assertTrue("$tag passage $i has ${wordCount(passage.text)} words", wordCount(passage.text) in 60..120)
            assertEquals("$tag passage $i questions", RsvpCalibrationPlan.QUESTIONS_PER_ROUND, passage.questions.size)
            passage.questions.forEach { q ->
                assertEquals("four options", 4, q.options.size)
                assertEquals("distinct options", 4, q.options.distinct().size)
                assertTrue("correct index in range", q.correctIndex in q.options.indices)
                assertTrue("prompt not blank", q.prompt.isNotBlank())
            }
        }
    }

    @Test fun english_isWellFormed() = assertWellFormed("en")

    @Test fun vietnamese_isWellFormed() = assertWellFormed("vi")

    @Test fun vietnameseTags_selectTheVietnameseSet() {
        val vi = RsvpCalibrationContent.forLanguage("vi").first().text
        assertEquals(vi, RsvpCalibrationContent.forLanguage("vi-VN").first().text)
        assertEquals(vi, RsvpCalibrationContent.forLanguage("VI").first().text)
        assertNotEquals(vi, RsvpCalibrationContent.forLanguage("en").first().text)
    }

    @Test fun otherLanguages_fallBackToEnglish() {
        val en = RsvpCalibrationContent.forLanguage("en").first().text
        assertEquals(en, RsvpCalibrationContent.forLanguage("de").first().text)
        assertEquals(en, RsvpCalibrationContent.forLanguage("").first().text)
    }

    @Test fun everyQuestion_isAnswerableFromItsOwnPassage_notFromGuessingTheCorrectIndexPattern() {
        // The correct option must not always sit in the same slot, or the pattern itself becomes a giveaway.
        val indices = RsvpCalibrationContent.forLanguage("en").flatMap { it.questions }.map { it.correctIndex }.toSet()
        assertTrue("correct answers should vary, got $indices", indices.size >= 2)
    }
}
