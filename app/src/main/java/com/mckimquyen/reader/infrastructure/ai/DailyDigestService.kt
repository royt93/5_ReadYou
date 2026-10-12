package com.mckimquyen.reader.infrastructure.ai

import android.util.Log
import com.google.gson.JsonParser
import com.mckimquyen.reader.domain.model.digest.DigestArticleSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyDigestService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {
    suspend fun generate(sources: List<DigestArticleSource>): DailyDigestContent {
        require(sources.isNotEmpty()) { "Digest needs at least one source article" }
        val request = buildString {
            appendLine("Create a concise daily news digest with headline, sections and items.")
            appendLine("Return only JSON: {\"headline\":string,\"sections\":[{\"title\":string,\"items\":[{\"text\":string,\"sourceIds\":[string]}]}]}.")
            appendLine("Use only the supplied article IDs as sourceIds. Do not invent facts or URLs.")
            appendLine("Write all text in the app language.")
            appendLine("Articles:")
            sources.forEach { source ->
                appendLine("ID: ${source.id}\nTitle: ${source.title}\nContent: ${source.content.take(MAX_SOURCE_CHARS)}")
            }
        }
        val body = """{"contents":[{"parts":[{"text":${com.google.gson.Gson().toJson(request)}}]}],"generationConfig":{"temperature":0.2}}"""
        val raw = gateway.execute("daily_digest", body, summaryService.resolveApiKeys())
        return try {
            parse(raw, sources.mapTo(mutableSetOf()) { it.id })
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Digest response rejected: ${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}", error)
            throw error
        }
    }

    companion object {
        private const val TAG = "DailyDigest"
        private const val MAX_SOURCE_CHARS = 2_000

        /** Gemini often wraps JSON in a ```json fence or adds prose; keep only the outermost object. */
        private fun extractJsonObject(raw: String): String {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            return if (start in 0 until end) raw.substring(start, end + 1) else raw
        }

        fun parse(raw: String, validSourceIds: Set<String>): DailyDigestContent {
            try {
                val root = JsonParser.parseString(extractJsonObject(raw)).asJsonObject
                val headline = root.get("headline")?.asString?.trim().orEmpty()
                val sections = root.getAsJsonArray("sections")?.map { sectionElement ->
                    val section = sectionElement.asJsonObject
                    val title = section.get("title")?.asString?.trim().orEmpty()
                    val items = section.getAsJsonArray("items")?.map { itemElement ->
                        val item = itemElement.asJsonObject
                        val text = item.get("text")?.asString?.trim().orEmpty()
                        val sourceIds = item.getAsJsonArray("sourceIds")?.map { it.asString }
                            .orEmpty().filter { it in validSourceIds }.distinct()
                        require(text.isNotBlank() && sourceIds.isNotEmpty())
                        DailyDigestItem(text, sourceIds)
                    }.orEmpty()
                    require(title.isNotBlank() && items.isNotEmpty())
                    DailyDigestSection(title, items)
                }.orEmpty()
                require(headline.isNotBlank() && sections.isNotEmpty())
                return DailyDigestContent(headline, sections)
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid daily digest response", error)
            }
        }
    }
}

data class DailyDigestContent(
    val headline: String,
    val sections: List<DailyDigestSection>,
)

data class DailyDigestSection(
    val title: String,
    val items: List<DailyDigestItem>,
)

data class DailyDigestItem(
    val text: String,
    val sourceIds: List<String>,
)
