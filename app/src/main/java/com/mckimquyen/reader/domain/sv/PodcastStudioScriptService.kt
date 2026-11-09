package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.domain.model.podcast.PodcastStudioEpisode
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns ONE article into a two-host dialogue (EXC-02).
 *
 * Privacy boundary: only the title and a preview of at most [MAX_SNIPPET_CHARS] characters ever leave the
 * device, and only when the caller says the user agreed. Anything else (body, link, feed, account) is not
 * even reachable from [buildPrompt]. The result says whether Gemini or the offline script produced it.
 */
@Singleton
class PodcastStudioScriptService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
    private val commuteScriptService: CommuteScriptService,
) {

    suspend fun generate(article: Article, useGemini: Boolean, languageTag: String): PodcastStudioEpisode {
        val title = article.title.trim()
        val snippet = sanitizeSnippet(article.shortDescription)
        val hasNothingToTalkAbout = title.isEmpty() && snippet.isEmpty()
        val keys = if (useGemini && !hasNothingToTalkAbout) summaryService.resolveApiKeys() else emptyList()
        if (keys.isEmpty()) return heuristic(article, title, snippet, languageTag)

        return try {
            val raw = gateway.execute(USE_CASE, buildRequestBody(buildPrompt(title, snippet, languageTag)), keys)
            val dialogues = commuteScriptService.parseDialoguesFromJson(raw)
            if (dialogues.isEmpty()) {
                heuristic(article, title, snippet, languageTag)
            } else {
                PodcastStudioEpisode(episode(article, title, dialogues), PodcastScriptSource.GEMINI)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini script failed, using the offline script: ${e.message}")
            heuristic(article, title, snippet, languageTag)
        }
    }

    fun sanitizeSnippet(raw: String): String = raw.trim().take(MAX_SNIPPET_CHARS)

    fun buildPrompt(title: String, snippet: String, languageTag: String): String = buildString {
        append("You are the producer of a short two-host spoken show about ONE news article.\n")
        append("Hosts: ALEX (calm, analytical) and SAM (curious, quick-witted).\n")
        append("Write a natural back-and-forth of 6 to 10 short lines that explains this article and why it matters.\n")
        append("Only use the facts given below; do not invent details.\n")
        append("Output STRICT JSON ARRAY ONLY, no markdown: [{\"speaker\": \"ALEX\"|\"SAM\", \"text\": \"...\"}]\n")
        append("Target language tag: $languageTag.\n\n")
        append("Title: $title\n")
        append("Preview: $snippet")
    }

    private fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
        )
        // JSON mode makes Gemini escape quotes inside a line; without it a quoted headline broke the array.
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()

    private fun heuristic(article: Article, title: String, snippet: String, languageTag: String): PodcastStudioEpisode {
        val vi = languageTag.startsWith("vi", ignoreCase = true)
        val lines = mutableListOf<CommuteDialogue>()
        fun say(speaker: CommuteSpeaker, text: String) = lines.add(CommuteDialogue(speaker, text))

        if (title.isEmpty() && snippet.isEmpty()) {
            if (vi) {
                say(CommuteSpeaker.ALEX, "Bài viết này chưa có tiêu đề hay phần mô tả để chúng ta trao đổi.")
                say(CommuteSpeaker.SAM, "Bạn hãy mở bài để đọc đầy đủ nhé.")
            } else {
                say(CommuteSpeaker.ALEX, "This article has no title or preview for us to talk about.")
                say(CommuteSpeaker.SAM, "Open it to read the full story.")
            }
        } else if (vi) {
            say(CommuteSpeaker.ALEX, "Hôm nay chúng ta nói về một bài viết: $title.")
            if (snippet.isNotEmpty()) say(CommuteSpeaker.SAM, "Phần mô tả cho biết: $snippet")
            say(CommuteSpeaker.ALEX, "Đó là những gì có trong phần xem trước của bài.")
            say(CommuteSpeaker.SAM, "Muốn biết thêm chi tiết thì bạn hãy mở bài để đọc đầy đủ nhé.")
        } else {
            say(CommuteSpeaker.ALEX, "Let's talk about one story today: $title.")
            if (snippet.isNotEmpty()) say(CommuteSpeaker.SAM, "Here is what the preview says: $snippet")
            say(CommuteSpeaker.ALEX, "That is all the preview gives us.")
            say(CommuteSpeaker.SAM, "For the details, open the article and read the full story.")
        }
        return PodcastStudioEpisode(episode(article, title, lines), PodcastScriptSource.HEURISTIC)
    }

    private fun episode(article: Article, title: String, dialogues: List<CommuteDialogue>) = CommuteEpisode(
        id = UUID.randomUUID().toString(),
        title = title.ifEmpty { DEFAULT_EPISODE_TITLE },
        date = Date(),
        dialogues = dialogues,
        articleIds = listOf(article.id),
        isDeepDive = false,
    )

    private companion object {
        const val TAG = "PodcastStudioScript"
        const val USE_CASE = "podcast_studio"
        const val MAX_SNIPPET_CHARS = 250
        const val TEMPERATURE = 0.7
        const val JSON_MIME_TYPE = "application/json"
        const val DEFAULT_EPISODE_TITLE = "Podcast Studio"
    }
}
