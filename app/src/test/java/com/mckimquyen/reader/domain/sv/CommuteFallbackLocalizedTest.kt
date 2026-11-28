package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

/**
 * The on-device CommuteCast script and the empty-inbox episode had English and Vietnamese typed into the code, picked by "is it Vietnamese", so a Russian or Polish
 * user's phone read Vietnamese aloud for an empty inbox. The lines are string resources now and follow the requested language tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteFallbackLocalizedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val service = CommuteScriptService(context, mockk<OkHttpClient>())
    // Only letters that Vietnamese uses and no other Latin language here does (hook, tilde and dot-below tones, ă, ơ, ư, đ): Polish also writes ó and French é.
    private val vietnameseLetters = Regex("[ảãạăắằẳẵặấầẩẫậẻẽẹếềểễệỉĩịỏõọốồổỗộơớờởỡợủũụưứừửữựỷỹỵđ]", RegexOption.IGNORE_CASE)

    private fun article(i: Int, desc: String) = Article(
        id = "a$i", date = Date(), title = "Title $i", rawDescription = "Raw $i",
        shortDescription = desc, link = "https://example.invalid/$i", feedId = "f", accountId = 1,
    )

    private val articles = listOf(article(1, "Short 1"), article(2, ""))

    private fun text(tag: String) = service.generateHeuristicScript(articles, false, tag).dialogues.joinToString(" ") { it.text }

    @Test fun anEmptyInboxIsNotReadInVietnameseToARussianUser() {
        val ru = runBlocking { service.generateScript(emptyList(), languageTag = "ru-RU") }
        val spoken = ru.dialogues.joinToString(" ") { it.text }
        assertTrue("no Vietnamese letters in: $spoken", !vietnameseLetters.containsMatchIn(spoken))
        assertTrue("Cyrillic expected in: $spoken", spoken.any { it in 'а'..'я' || it in 'А'..'Я' })
    }

    @Test fun theScriptFollowsTheLanguageTag() {
        assertTrue(vietnameseLetters.containsMatchIn(text("vi-VN")))
        assertTrue(!vietnameseLetters.containsMatchIn(text("pl-PL")))
        assertNotEquals(text("en-US"), text("pl-PL"))
        assertNotEquals(text("en-US"), text("ja-JP"))
    }

    @Test fun englishAndVietnameseKeepTheirExistingWording() {
        val en = service.generateHeuristicScript(articles, false, "en-US")
        assertEquals("CommuteCast Morning Briefing", en.title)
        assertTrue(en.dialogues.first().text.startsWith("Good morning! Welcome to CommuteCast Radio"))
        assertTrue(en.dialogues.any { it.text == "First up: Title 1. Short 1" })
        assertTrue(en.dialogues.any { it.text.startsWith("Next on our radar: Title 2. ") })
        val vi = service.generateHeuristicScript(articles, false, "vi-VN")
        assertEquals("Bản Tin Sáng CommuteCast", vi.title)
        assertTrue(vi.dialogues.any { it.text == "Đầu tiên là tiêu điểm: Title 1. Short 1" })
    }

    @Test fun everyLocaleDefinesEveryFallbackKeyAndKeepsItsPlaceholders() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        val keys = listOf(
            "commute_fb_title", "commute_edition_morning", "commute_edition_deepdive", "commute_fb_intro_alex", "commute_fb_intro_sam",
            "commute_fb_first_up", "commute_fb_first_up_default", "commute_fb_sam_react", "commute_fb_next_up", "commute_fb_next_up_default",
            "commute_fb_alex_react", "commute_fb_outro_alex", "commute_fb_outro_sam", "commute_empty_alex", "commute_empty_sam",
        )
        val files = main.resolve("res").listFiles { f -> f.name.startsWith("values") && f.resolve("strings.xml").exists() }!!
        assertTrue(files.size > 30)
        files.forEach { d ->
            val text = d.resolve("strings.xml").readText()
            keys.forEach { k ->
                val m = Regex("""name="$k">([^<]*)<""").find(text)
                assertTrue("${d.name} lacks $k", m != null)
                if (k == "commute_fb_first_up" || k == "commute_fb_next_up") {
                    val v = m!!.groupValues[1]
                    assertTrue("${d.name} $k needs %1\$s and %2\$s", "%1\$s" in v && "%2\$s" in v)
                }
            }
        }
    }
}
