package com.mckimquyen.reader.domain.model.article

import androidx.annotation.Keep

/**
 * Node within an article's hierarchical concept mind map.
 *
 * @param id Unique identifier of the node.
 * @param label Short, punchy concept title (2-6 words) rendered prominently on the card.
 * @param detail Comprehensive explanation, key quote, or supporting facts revealed on interaction.
 * @param depth Hierarchy level: 0 = Central Theme / Root, 1 = Core Pillars / Main Branches, 2 = Sub-details / Evidence.
 * @param parentId ID of the parent node (null for the root).
 * @param tag Optional topical category tag (e.g. "Key Metric", "Context", "Impact").
 */
@Keep
data class MindMapNode(
    val id: String,
    val label: String,
    val detail: String = "",
    val depth: Int = 1,
    val parentId: String? = null,
    val tag: String? = null,
)

/**
 * Domain model representing an AI-generated or offline-extracted Concept Mind Map.
 *
 * @param rootTitle Central title of the mind map (typically the article subject).
 * @param nodes List of all hierarchical nodes.
 * @param isOfflineFallback True if generated via smart offline heuristics rather than Gemini API.
 */
@Keep
data class ArticleMindMap(
    val rootTitle: String = "",
    val nodes: List<MindMapNode> = emptyList(),
    val isOfflineFallback: Boolean = false,
) {
    /**
     * Returns all direct child nodes for the given parent node ID.
     */
    fun findChildren(parentId: String): List<MindMapNode> {
        return nodes.filter { it.parentId == parentId }
    }

    /**
     * Returns the root node (depth 0 or null parentId).
     */
    fun getRootNode(): MindMapNode? {
        return nodes.firstOrNull { it.depth == 0 || it.parentId == null }
            ?: nodes.firstOrNull()
    }

    /**
     * Returns level 1 branch nodes.
     */
    fun getBranches(): List<MindMapNode> {
        val root = getRootNode()
        return if (root != null) {
            val children = findChildren(root.id)
            if (children.isNotEmpty()) children else nodes.filter { it.depth == 1 && it.id != root.id }
        } else {
            nodes.filter { it.depth == 1 }
        }
    }

    /**
     * Formats the entire mind map into an indented, human-readable outline with emojis,
     * perfect for copying to notes or sharing.
     */
    fun formatAsOutline(): String = buildString {
        appendLine("🧠 Concept Mind Map: ${rootTitle.ifBlank { "Article Overview" }}")
        appendLine()
        val root = getRootNode()
        if (root != null) {
            appendNodeOutline(this, root, 0)
        } else {
            nodes.forEach { appendLine("• ${it.label}: ${it.detail}") }
        }
    }.trim()

    private fun appendNodeOutline(builder: StringBuilder, node: MindMapNode, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)
        val bullet = when (indentLevel) {
            0 -> "🎯"
            1 -> "🔹"
            else -> "▪️"
        }
        builder.appendLine("$indent$bullet ${node.label}")
        if (node.detail.isNotBlank() && indentLevel > 0) {
            builder.appendLine("$indent   ${node.detail}")
        }
        findChildren(node.id).forEach { child ->
            appendNodeOutline(builder, child, indentLevel + 1)
        }
    }
}
