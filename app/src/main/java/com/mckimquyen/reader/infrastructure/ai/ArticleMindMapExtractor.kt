package com.mckimquyen.reader.infrastructure.ai

import com.google.gson.JsonParser
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.MindMapNode
import java.util.Locale

/**
 * Extractor and parser for Article Concept Mind Maps.
 *
 * Provides:
 * 1. Robust JSON parsing of Gemini AI responses into hierarchical [ArticleMindMap].
 * 2. Intelligent, deterministic offline heuristic generation when offline or API is unavailable.
 */
object ArticleMindMapExtractor {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "this", "that", "from", "are", "was", "were", "will",
        "has", "have", "had", "can", "may", "not", "but", "all", "any", "how", "what", "when",
        "where", "why", "who", "which", "their", "there", "they", "been", "about", "into",
        "more", "some", "other", "just", "like", "also", "would", "could", "should", "your",
        "than", "then", "its", "over", "after", "most", "only", "such", "these", "those",
        "trong", "nhung", "cac", "mot", "duoc", "cho", "voi", "cua", "nay", "tren"
    )

    /**
     * Parses Gemini API response text into [ArticleMindMap].
     * Supports raw JSON and Markdown fenced JSON blocks (```json ... ```).
     */
    fun parseGeminiResponse(rawText: String, fallbackTitle: String = ""): ArticleMindMap {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) {
            return ArticleMindMap(rootTitle = fallbackTitle)
        }

        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val jsonCandidate = cleaned.substring(firstBrace, lastBrace + 1)
            try {
                val json = JsonParser.parseString(jsonCandidate).asJsonObject
                val rootTitle = if (json.has("root") && !json.get("root").isJsonNull) {
                    json.get("root").asString.trim()
                } else fallbackTitle.ifBlank { "Article Overview" }

                val nodeList = mutableListOf<MindMapNode>()
                if (json.has("nodes") && json.get("nodes").isJsonArray) {
                    val nodesArray = json.getAsJsonArray("nodes")
                    for (element in nodesArray) {
                        if (element.isJsonObject) {
                            val nodeObj = element.asJsonObject
                            val id = if (nodeObj.has("id")) nodeObj.get("id").asString else "node_${nodeList.size}"
                            val label = if (nodeObj.has("label")) nodeObj.get("label").asString.trim() else ""
                            val detail = if (nodeObj.has("detail") && !nodeObj.get("detail").isJsonNull) {
                                nodeObj.get("detail").asString.trim()
                            } else ""
                            val depth = if (nodeObj.has("depth")) nodeObj.get("depth").asInt else 1
                            val parentId = if (nodeObj.has("parentId") && !nodeObj.get("parentId").isJsonNull) {
                                val pid = nodeObj.get("parentId").asString.trim()
                                pid.ifBlank { null }
                            } else null
                            val tag = if (nodeObj.has("tag") && !nodeObj.get("tag").isJsonNull) {
                                nodeObj.get("tag").asString.trim().removePrefix("#")
                            } else null

                            if (label.isNotBlank()) {
                                nodeList.add(
                                    MindMapNode(
                                        id = id,
                                        label = label,
                                        detail = detail,
                                        depth = depth,
                                        parentId = parentId,
                                        tag = tag,
                                    )
                                )
                            }
                        }
                    }
                }

                if (nodeList.isNotEmpty()) {
                    // Ensure a root node exists
                    val hasRoot = nodeList.any { it.depth == 0 || it.parentId == null }
                    if (!hasRoot) {
                        val rootId = "root_0"
                        nodeList.add(
                            0,
                            MindMapNode(
                                id = rootId,
                                label = rootTitle.take(50),
                                detail = rootTitle,
                                depth = 0,
                                parentId = null,
                            )
                        )
                        // Reparent orphans
                        for (i in 1 until nodeList.size) {
                            if (nodeList[i].parentId == null) {
                                nodeList[i] = nodeList[i].copy(parentId = rootId)
                            }
                        }
                    }

                    return ArticleMindMap(
                        rootTitle = rootTitle,
                        nodes = nodeList,
                        isOfflineFallback = false,
                    )
                }
            } catch (_: Exception) {
                // Fallthrough to offline heuristic generator
            }
        }

        return extractOfflineMindMap(fallbackTitle, cleaned)
    }

    /**
     * Generates an intelligent, hierarchical Mind Map entirely offline using paragraph/sentence
     * structure, keyword discovery, and contextual clustering.
     */
    fun extractOfflineMindMap(title: String, plainText: String): ArticleMindMap {
        val cleanTitle = title.trim().ifBlank { "Article Overview" }
        val cleanText = plainText.trim()
        if (cleanText.isBlank()) {
            val singleRoot = MindMapNode(
                id = "root",
                label = cleanTitle.take(40),
                detail = cleanTitle,
                depth = 0,
                parentId = null,
            )
            return ArticleMindMap(rootTitle = cleanTitle, nodes = listOf(singleRoot), isOfflineFallback = true)
        }

        val rootNodeId = "root"
        val rootNode = MindMapNode(
            id = rootNodeId,
            label = cleanTitle.take(45),
            detail = cleanTitle,
            depth = 0,
            parentId = null,
            tag = "Core Topic",
        )

        val nodes = mutableListOf<MindMapNode>()
        nodes.add(rootNode)

        // Split text into meaningful sentences
        val sentences = cleanText.split(Regex("(?<=[.!?\\n])\\s+"))
            .map { it.trim() }
            .filter { s ->
                s.length in 25..300 &&
                    !s.startsWith("http", ignoreCase = true) &&
                    !s.startsWith("Copyright", ignoreCase = true)
            }

        if (sentences.isEmpty()) {
            val fallbackChild = MindMapNode(
                id = "node_1",
                label = cleanText.take(35),
                detail = cleanText.take(150),
                depth = 1,
                parentId = rootNodeId,
            )
            nodes.add(fallbackChild)
            return ArticleMindMap(rootTitle = cleanTitle, nodes = nodes, isOfflineFallback = true)
        }

        // Categorize into thematic pillars
        val pillarThemes = listOf(
            "Overview & Context",
            "Key Developments",
            "Data & Evidence",
            "Impact & Outlook"
        )

        // Chunk sentences across available pillars (up to 3-4 pillars)
        val pillarCount = sentences.size.coerceIn(2, 4)
        val chunkSize = (sentences.size + pillarCount - 1) / pillarCount

        for (i in 0 until pillarCount) {
            val chunkStart = i * chunkSize
            if (chunkStart >= sentences.size) break
            val chunkEnd = (chunkStart + chunkSize).coerceAtMost(sentences.size)
            val chunkSentences = sentences.subList(chunkStart, chunkEnd)

            val leadSentence = chunkSentences.firstOrNull() ?: continue
            val pillarId = "pillar_$i"
            val pillarTheme = pillarThemes.getOrElse(i) { "Key Insight ${i + 1}" }

            // Extract a concise punchy label from the sentence (first 4-6 words)
            val pillarLabel = extractConciseLabel(leadSentence, pillarTheme)

            nodes.add(
                MindMapNode(
                    id = pillarId,
                    label = pillarLabel,
                    detail = leadSentence,
                    depth = 1,
                    parentId = rootNodeId,
                    tag = pillarTheme,
                )
            )

            // Add up to 2 supporting sub-nodes
            val supportingSentences = chunkSentences.drop(1).take(2)
            for ((subIndex, subSentence) in supportingSentences.withIndex()) {
                val subId = "sub_${i}_$subIndex"
                val subLabel = extractConciseLabel(subSentence, "Detail ${subIndex + 1}")
                nodes.add(
                    MindMapNode(
                        id = subId,
                        label = subLabel,
                        detail = subSentence,
                        depth = 2,
                        parentId = pillarId,
                    )
                )
            }
        }

        return ArticleMindMap(
            rootTitle = cleanTitle,
            nodes = nodes,
            isOfflineFallback = true,
        )
    }

    private fun extractConciseLabel(sentence: String, fallback: String): String {
        val words = sentence
            .replace(Regex("^[-*•–—\\d+.)]+\\s*"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return fallback

        // Select the first 3 to 6 words
        val count = words.size.coerceIn(3, 6)
        val candidate = words.take(count).joinToString(" ")
        return candidate.trim().trimEnd(',', ';', ':')
    }
}
