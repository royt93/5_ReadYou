package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.ai.GeminiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import com.mckimquyen.reader.infrastructure.di.SecureHttp
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
    @SecureHttp private val okHttpClient: OkHttpClient,
) {

    companion object {
        private const val TAG = "CommuteScriptService"
    }

    suspend fun generateScript(
        articles: List<Article>,
        isDeepDive: Boolean = false,
        languageTag: String = Locale.getDefault().toLanguageTag(),
        // STRAT-01: sending article titles and descriptions to Gemini needs the user's own agreement; the safe default is no.
        allowGemini: Boolean = false,
    ): CommuteEpisode = withContext(Dispatchers.Default) {
        if (articles.isEmpty()) {
            return@withContext createFallbackEmptyEpisode(languageTag)
        }

        val topArticles = if (isDeepDive) articles.take(10) else articles.take(5)
        
        if (allowGemini) {
            val aiEpisode = runCatching {
                generateScriptWithGemini(topArticles, isDeepDive, languageTag)
            }.getOrNull()
            if (aiEpisode != null && aiEpisode.dialogues.isNotEmpty()) {
                return@withContext aiEpisode
            }
        }

        // Chưa đồng ý, offline hoặc hết quota AI: bộ sinh kịch bản đối đáp cục bộ luôn chạy được
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
                                title = localized(languageTag).getString(if (isDeepDive) R.string.commute_edition_deepdive else R.string.commute_edition_morning),
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
        val res = localized(languageTag)
        val dialogues = mutableListOf<CommuteDialogue>()
        dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, res.getString(R.string.commute_fb_intro_alex)))
        dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, res.getString(R.string.commute_fb_intro_sam)))

        articles.forEachIndexed { idx, art ->
            val shortDesc = art.shortDescription.take(180).trim()
            if (idx % 2 == 0) {
                val detail = shortDesc.ifBlank { res.getString(R.string.commute_fb_first_up_default) }
                dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, res.getString(R.string.commute_fb_first_up, art.title, detail)))
                dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, res.getString(R.string.commute_fb_sam_react)))
            } else {
                val detail = shortDesc.ifBlank { res.getString(R.string.commute_fb_next_up_default) }
                dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, res.getString(R.string.commute_fb_next_up, art.title, detail)))
                dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, res.getString(R.string.commute_fb_alex_react)))
            }
        }

        dialogues.add(CommuteDialogue(CommuteSpeaker.ALEX, res.getString(R.string.commute_fb_outro_alex)))
        dialogues.add(CommuteDialogue(CommuteSpeaker.SAM, res.getString(R.string.commute_fb_outro_sam)))

        return CommuteEpisode(
            id = UUID.randomUUID().toString(),
            title = res.getString(R.string.commute_fb_title),
            date = Date(),
            dialogues = dialogues,
            articleIds = articles.map { it.id },
            isDeepDive = isDeepDive
        )
    }

    private fun createFallbackEmptyEpisode(languageTag: String): CommuteEpisode {
        val res = localized(languageTag)
        return CommuteEpisode(
            id = UUID.randomUUID().toString(),
            title = res.getString(R.string.commute_edition_morning),
            date = Date(),
            dialogues = listOf(
                CommuteDialogue(CommuteSpeaker.ALEX, res.getString(R.string.commute_empty_alex)),
                CommuteDialogue(CommuteSpeaker.SAM, res.getString(R.string.commute_empty_sam)),
            ),
            articleIds = emptyList()
        )
    }

    /** Resources in the language the episode is written in (the tag the caller asked for), not necessarily the one the process runs in. */
    private fun localized(languageTag: String): Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(languageTag))
        return context.createConfigurationContext(config).resources
    }
}
