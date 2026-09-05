package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.ai.GeminiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service biên kịch radio tự động giữa 2 MC (Alex & Sam) theo phong cách NotebookLM Audio Overview.
 */
@Singleton
class CommuteScriptService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    companion object {
        private const val TAG = "CommuteScriptService"
    }

    suspend fun generateScript(
        articles: List<Article>,
        isDeepDive: Boolean = false,
        languageTag: String = Locale.getDefault().toLanguageTag()
    ): CommuteEpisode = withContext(Dispatchers.Default) {
        if (articles.isEmpty()) {
            return@withContext createFallbackEmptyEpisode()
        }

        val topArticles = if (isDeepDive) articles.take(10) else articles.take(5)
        
        // Thử sinh kịch bản bằng Gemini AI trước
        val aiEpisode = runCatching {
            generateScriptWithGemini(topArticles, isDeepDive, languageTag)
        }.getOrNull()

        if (aiEpisode != null && aiEpisode.dialogues.isNotEmpty()) {
            return@withContext aiEpisode
        }

        // Nếu offline hoặc hết quota AI, dùng bộ sinh kịch bản đối đáp heuristic cục bộ
        return@withContext generateHeuristicScript(topArticles, isDeepDive, languageTag)
    }

    private suspend fun generateScriptWithGemini(
        articles: List<Article>,
        isDeepDive: Boolean,
        languageTag: String
    ): CommuteEpisode = withContext(Dispatchers.IO) {
        val keys = GeminiConfig.API_KEYS.filter { it.isNotBlank() }
        if (keys.isEmpty()) throw IllegalStateException("No API key available")

        val articleSummaries = articles.mapIndexed { index, art ->
            val content = art.shortDescription.ifBlank { art.title }
            "${index + 1}. Title: ${art.title}\nSummary: ${content.take(250)}"
        }.joinToString("\n\n")

        val prompt = buildString {
            append("You are the head producer of 'CommuteCast Radio', a lively 2-host morning podcast show.\n")
            append("Hosts:\n")
            append("- ALEX: Male host, wise, analytical, calm tone.\n")
            append("- SAM: Female co-host, energetic, curious, quick-witted.\n\n")
            append("Task: Transform the following ${articles.size} headlines into a conversational, punchy 2-person morning radio script.\n")
            append("Tone: Engaging, smart, witty, like Google NotebookLM Audio Overview.\n")
            append("Output format: STRICT JSON ARRAY OF OBJECTS ONLY, no markdown ticks, no preamble:\n")
            append("[{\"speaker\": \"ALEX\"|\"SAM\", \"text\": \"...\"}]\n")
            append("Target language tag: $languageTag.\n\n")
            append("Headlines:\n")
            append(articleSummaries)
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().put("temperature", 0.7))
        }.toString()

        for (key in keys) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${GeminiConfig.MODEL}:generateContent?key=$key"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val responseJson = JSONObject(it.body?.string().orEmpty())
                        val text = responseJson
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()

                        val dialogues = parseDialoguesFromJson(text)
                        if (dialogues.isNotEmpty()) {
                            return@withContext CommuteEpisode(
                                id = UUID.randomUUID().toString(),
                                title = if (isDeepDive) "CommuteCast Deep Dive Edition" else "CommuteCast Morning Edition",
                                date = Date(),
                                dialogues = dialogues,
                                articleIds = articles.map { it.id },
                                isDeepDive = isDeepDive
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini script generation failed with key: ${e.message}")
            }
        }
        throw IllegalStateException("All Gemini keys failed")
    }

    fun parseDialoguesFromJson(rawText: String): List<CommuteDialogue> {
        val cleaned = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val jsonArray = JSONArray(cleaned)
        val dialogues = mutableListOf<CommuteDialogue>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val speakerStr = obj.optString("speaker", "ALEX").uppercase()
            val speaker = if (speakerStr == "SAM") CommuteSpeaker.SAM else CommuteSpeaker.ALEX
            val text = obj.optString("text", "").trim()
            if (text.isNotBlank()) {
                dialogues.add(CommuteDialogue(speaker, text))
            }
        }
        return dialogues
    }

    /**
     * Bộ sinh kịch bản đối đáp cục bộ chuẩn xác, đảm bảo 100% không bao giờ lỗi kể cả khi mất mạng.
     */
    fun generateHeuristicScript(
        articles: List<Article>,
        isDeepDive: Boolean,
        languageTag: String
    ): CommuteEpisode {
        val dialogues = mutableListOf<CommuteDialogue>()
        val isVietnamese = languageTag.startsWith("vi", ignoreCase = true)

        if (isVietnamese) {
            dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Chào buổi sáng! Đây là CommuteCast Radio của RSS Cat Hub, bản tin nhanh dành riêng cho bạn trên đường đi làm."))
            dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Chào Alex và chào mọi người! Sáng nay bảng tin có rất nhiều chuyển động đáng chú ý. Cùng điểm qua nhé!"))

            articles.forEachIndexed { idx, art ->
                val shortDesc = art.shortDescription.take(180).trim()
                if (idx % 2 == 0) {
                    dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Đầu tiên là tiêu điểm: ${art.title}. ${if (shortDesc.isNotBlank()) shortDesc else "Bài viết mang đến góc nhìn phân tích rất đáng chú ý."}"))
                    dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Một thông tin rất đáng suy ngẫm trong chuyên mục này!"))
                } else {
                    dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Tiếp theo, hãy cùng xem qua: ${art.title}. ${if (shortDesc.isNotBlank()) shortDesc else "Đây cũng là đề tài đang được thảo luận rất sôi nổi."}"))
                    dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Đúng vậy Sam, diễn biến này chắc chắn sẽ tác động nhiều đến xu hướng sắp tới."))
                }
            }

            dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Đó là toàn bộ những điểm tin nóng nhất sáng nay. Bạn có thể mở chi tiết từng bài viết ngay trong ứng dụng."))
            dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Chúc bạn một ngày mới tràn đầy năng lượng và làm việc thật hiệu quả!"))
        } else {
            dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Good morning! Welcome to CommuteCast Radio on RSS Cat Hub, your bespoke morning drive briefing."))
            dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Morning Alex! We've got quite a few intriguing developments across your subscriptions today. Let's dive right in!"))

            articles.forEachIndexed { idx, art ->
                val shortDesc = art.shortDescription.take(180).trim()
                if (idx % 2 == 0) {
                    dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "First up: ${art.title}. ${if (shortDesc.isNotBlank()) shortDesc else "A compelling story with important takeaways."}"))
                    dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Definitely an essential read for anyone following this sector closely."))
                } else {
                    dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Next on our radar: ${art.title}. ${if (shortDesc.isNotBlank()) shortDesc else "This is generating quite a buzz across communities."}"))
                    dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "Indeed, Sam. One we will definitely want to keep an eye on as it unfolds."))
                }
            }

            dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, "That wraps up our top headlines for this morning. Full articles are ready for you in the app whenever you have a moment."))
            dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, "Have a wonderful, productive day ahead! See you on the next ride."))
        }

        return CommuteEpisode(
            id = UUID.randomUUID().toString(),
            title = if (isVietnamese) "Bản Tin Sáng CommuteCast" else "CommuteCast Morning Briefing",
            date = Date(),
            dialogues = dialogues,
            articleIds = articles.map { it.id },
            isDeepDive = isDeepDive
        )
    }

    private fun createFallbackEmptyEpisode(): CommuteEpisode {
        return CommuteEpisode(
            id = UUID.randomUUID().toString(),
            title = "CommuteCast Morning Edition",
            date = Date(),
            dialogues = listOf(
                CommuteDialogue(CommuteSpeaker.ALEX, "Chào buổi sáng! Hộp tin của bạn hiện đang ở trạng thái trống."),
                CommuteDialogue(CommuteSpeaker.SAM, "Hãy thêm thêm một vài nguồn RSS yêu thích để đón nghe bản tin sáng mai nhé!")
            ),
            articleIds = emptyList()
        )
    }
}
