package com.mckimquyen.reader.domain.sv

import android.content.Context
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteScriptServiceTest {

    private val context = mockk<Context>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var service: CommuteScriptService

    @Before
    fun setUp() {
        service = CommuteScriptService(context, okHttpClient)
    }

    @Test
    fun parseDialoguesFromJson_validJson_parsesCorrectly() {
        val json = """
            [
              {"speaker": "ALEX", "text": "Good morning everyone!"},
              {"speaker": "SAM", "text": "Excited to review today's tech news."}
            ]
        """.trimIndent()

        val dialogues = service.parseDialoguesFromJson(json)
        assertEquals(2, dialogues.size)
        assertEquals(CommuteSpeaker.ALEX, dialogues[0].speaker)
        assertEquals("Good morning everyone!", dialogues[0].text)
        assertEquals(CommuteSpeaker.SAM, dialogues[1].speaker)
        assertEquals("Excited to review today's tech news.", dialogues[1].text)
    }

    @Test
    fun parseDialoguesFromJson_withMarkdownFences_stripsFencesAndParses() {
        val json = """
            ```json
            [
              {"speaker": "sam", "text": "Check this out!"},
              {"speaker": "alex", "text": "That is fascinating."}
            ]
            ```
        """.trimIndent()

        val dialogues = service.parseDialoguesFromJson(json)
        assertEquals(2, dialogues.size)
        assertEquals(CommuteSpeaker.SAM, dialogues[0].speaker)
        assertEquals("Check this out!", dialogues[0].text)
        assertEquals(CommuteSpeaker.ALEX, dialogues[1].speaker)
        assertEquals("That is fascinating.", dialogues[1].text)
    }

    @Test
    fun generateHeuristicScript_vietnamese_generatesAlternatingDialogue() {
        val articles = listOf(
            Article(
                id = "art_1",
                date = Date(),
                title = "AI Đang Thay Đổi Lập Trình",
                author = "Author 1",
                rawDescription = "Chi tiết về AI",
                shortDescription = "Mô tả ngắn gọn về AI",
                link = "https://example.com/1",
                feedId = "feed_1",
                accountId = 1
            ),
            Article(
                id = "art_2",
                date = Date(),
                title = "Bản Cập Nhật Android Mới",
                author = "Author 2",
                rawDescription = "Chi tiết về Android",
                shortDescription = "Mô tả ngắn gọn về Android",
                link = "https://example.com/2",
                feedId = "feed_1",
                accountId = 1
            )
        )

        val episode = service.generateHeuristicScript(articles, isDeepDive = false, languageTag = "vi-VN")
        assertNotNull(episode)
        assertTrue(episode.dialogues.size >= 4)
        assertEquals(CommuteSpeaker.ALEX, episode.dialogues[0].speaker)
        assertEquals(CommuteSpeaker.SAM, episode.dialogues[1].speaker)
        assertTrue(episode.title.contains("CommuteCast"))
        assertFalse(episode.isDeepDive)
    }

    @Test
    fun generateHeuristicScript_english_generatesEnglishDialogue() {
        val articles = listOf(
            Article(
                id = "art_1",
                date = Date(),
                title = "Breakthrough in Quantum Computing",
                author = "TechCrunch",
                rawDescription = "Quantum computing details",
                shortDescription = "Short quantum summary",
                link = "https://example.com/3",
                feedId = "feed_1",
                accountId = 1
            )
        )

        val episode = service.generateHeuristicScript(articles, isDeepDive = true, languageTag = "en-US")
        assertTrue(episode.isDeepDive)
        assertTrue(episode.dialogues.any { it.text.contains("Good morning") || it.text.contains("Welcome") })
    }

    private fun assertNotNull(obj: Any?) {
        assertTrue(obj != null)
    }
}
