package com.mckimquyen.reader.infrastructure.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleMindMapExtractorTest {

    @Test
    fun parseGeminiResponse_validJson_parsesCorrectly() {
        val sampleJson = """
            {
              "root": "Future of Artificial Intelligence",
              "nodes": [
                {
                  "id": "root",
                  "label": "AI Future",
                  "detail": "Transforming modern productivity and science",
                  "depth": 0,
                  "parentId": null,
                  "tag": "Central Theme"
                },
                {
                  "id": "branch_1",
                  "label": "Autonomous Agents",
                  "detail": "Agents executing complex tasks independently",
                  "depth": 1,
                  "parentId": "root",
                  "tag": "Technology"
                },
                {
                  "id": "sub_1_1",
                  "label": "Tool Calling API",
                  "detail": "Enabling agents to browse web and run commands",
                  "depth": 2,
                  "parentId": "branch_1"
                },
                {
                  "id": "branch_2",
                  "label": "Safety and Alignment",
                  "detail": "Ensuring models behave ethically and reliably",
                  "depth": 1,
                  "parentId": "root",
                  "tag": "Ethics"
                }
              ]
            }
        """.trimIndent()

        val mindMap = ArticleMindMapExtractor.parseGeminiResponse(sampleJson, "Default Title")

        assertEquals("Future of Artificial Intelligence", mindMap.rootTitle)
        assertFalse(mindMap.isOfflineFallback)
        assertEquals(4, mindMap.nodes.size)

        val rootNode = mindMap.getRootNode()
        assertNotNull(rootNode)
        assertEquals("root", rootNode?.id)
        assertEquals(0, rootNode?.depth)

        val branches = mindMap.getBranches()
        assertEquals(2, branches.size)

        val childrenOfBranch1 = mindMap.findChildren("branch_1")
        assertEquals(1, childrenOfBranch1.size)
        assertEquals("sub_1_1", childrenOfBranch1[0].id)
    }

    @Test
    fun parseGeminiResponse_withMarkdownFences_parsesCorrectly() {
        val markdownJson = """
            Here is the concept mind map:
            ```json
            {
              "root": "Quantum Computing 2026",
              "nodes": [
                {
                  "id": "root",
                  "label": "Quantum 2026",
                  "detail": "Major breakthroughs in error correction",
                  "depth": 0,
                  "parentId": null
                },
                {
                  "id": "branch_1",
                  "label": "Qubit Scaling",
                  "detail": "Scaling up logical qubits above 1,000",
                  "depth": 1,
                  "parentId": "root"
                }
              ]
            }
            ```
        """.trimIndent()

        val mindMap = ArticleMindMapExtractor.parseGeminiResponse(markdownJson, "Quantum Title")
        assertEquals("Quantum Computing 2026", mindMap.rootTitle)
        assertEquals(2, mindMap.nodes.size)
        assertFalse(mindMap.isOfflineFallback)
    }

    @Test
    fun parseGeminiResponse_invalidJson_fallsBackToOfflineHeuristic() {
        val garbage = "Not a json response at all. Just some random text about technology and science."
        val mindMap = ArticleMindMapExtractor.parseGeminiResponse(garbage, "Technology Article")

        assertTrue(mindMap.isOfflineFallback)
        assertTrue(mindMap.nodes.isNotEmpty())
        assertEquals("Technology Article", mindMap.rootTitle)
    }

    @Test
    fun extractOfflineMindMap_blankText_returnsSingleRootNode() {
        val mindMap = ArticleMindMapExtractor.extractOfflineMindMap("Empty Article", "")
        assertTrue(mindMap.isOfflineFallback)
        assertEquals(1, mindMap.nodes.size)
        assertEquals("Empty Article", mindMap.nodes[0].label)
    }

    @Test
    fun extractOfflineMindMap_multiSentenceText_generatesPillarsAndSubNodes() {
        val text = """
            SpaceX announced today a new mission to the outer solar system.
            The spacecraft will utilize innovative ion propulsion systems for deep space transit.
            Scientists from NASA and ESA will collaborate on payload instruments.
            The budget is estimated at 1.5 billion dollars with a launch target in late 2028.
            Key milestones include orbital testing and thermal shield stress tests.
        """.trimIndent()

        val mindMap = ArticleMindMapExtractor.extractOfflineMindMap("Deep Space Exploration", text)

        assertTrue(mindMap.isOfflineFallback)
        assertEquals("Deep Space Exploration", mindMap.rootTitle)
        assertTrue(mindMap.nodes.size >= 3)

        val rootNode = mindMap.getRootNode()
        assertNotNull(rootNode)
        assertEquals(0, rootNode?.depth)

        val branches = mindMap.getBranches()
        assertTrue(branches.isNotEmpty())
    }

    @Test
    fun formatAsOutline_producesCleanFormattedText() {
        val sampleJson = """
            {
              "root": "Renewable Energy",
              "nodes": [
                {
                  "id": "root",
                  "label": "Clean Energy",
                  "detail": "Transition to net-zero power grids",
                  "depth": 0,
                  "parentId": null
                },
                {
                  "id": "solar",
                  "label": "Solar Power",
                  "detail": "Perovskite solar cell efficiency reaches 33%",
                  "depth": 1,
                  "parentId": "root"
                }
              ]
            }
        """.trimIndent()

        val mindMap = ArticleMindMapExtractor.parseGeminiResponse(sampleJson)
        val outline = mindMap.formatAsOutline()

        assertTrue(outline.contains("Renewable Energy"))
        assertTrue(outline.contains("🎯 Clean Energy"))
        assertTrue(outline.contains("🔹 Solar Power"))
        assertTrue(outline.contains("Perovskite solar cell efficiency reaches 33%"))
    }
}
