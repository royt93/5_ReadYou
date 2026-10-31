package com.mckimquyen.reader.domain.model.rpg

import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTreeLayoutTest {

    @Test
    fun nodesContainExactlyTheSixExistingCategoriesInFixedPositions() {
        val expectedIds = setOf(
            QuizGeneratorService.CATEGORY_TECH,
            QuizGeneratorService.CATEGORY_BUSINESS,
            QuizGeneratorService.CATEGORY_SCIENCE,
            QuizGeneratorService.CATEGORY_HEALTH,
            QuizGeneratorService.CATEGORY_PHILOSOPHY,
            QuizGeneratorService.CATEGORY_GENERAL,
        )

        assertEquals(expectedIds, SkillTreeLayout.nodes.map { it.categoryId }.toSet())
        assertEquals(6, SkillTreeLayout.nodes.map { it.categoryId }.distinct().size)
        assertTrue(SkillTreeLayout.nodes.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(
            SkillTreeLayout.nodes.associate { it.categoryId to (it.x to it.y) },
            SkillTreeLayout.nodes.associate { it.categoryId to (it.x to it.y) },
        )
    }

    @Test
    fun fixedEdgesReferenceExistingNodesAndAvoidSelfEdges() {
        val nodeIds = SkillTreeLayout.nodes.map { it.categoryId }.toSet()

        assertTrue(SkillTreeLayout.edges.isNotEmpty())
        assertEquals(SkillTreeLayout.edges.size, SkillTreeLayout.edges.distinct().size)
        assertTrue(SkillTreeLayout.edges.all { edge ->
            edge.fromCategoryId in nodeIds && edge.toCategoryId in nodeIds && edge.fromCategoryId != edge.toCategoryId
        })
    }

    @Test
    fun nodeSizeIsMonotonicBoundedAndZeroXpRemainsVisible() {
        val low = SkillTreeLayout.visual(effectiveXp = 0L, baseXp = 0L, maxEffectiveXp = 100L)
        val mid = SkillTreeLayout.visual(effectiveXp = 50L, baseXp = 50L, maxEffectiveXp = 100L)
        val high = SkillTreeLayout.visual(effectiveXp = 100L, baseXp = 100L, maxEffectiveXp = 100L)

        assertTrue(low.diameterDp >= SkillTreeLayout.MIN_NODE_DIAMETER_DP)
        assertTrue(low.diameterDp <= mid.diameterDp)
        assertTrue(mid.diameterDp <= high.diameterDp)
        assertTrue(high.diameterDp <= SkillTreeLayout.MAX_NODE_DIAMETER_DP)
        assertTrue(low.emphasis in 0f..1f)
        assertTrue(mid.emphasis in 0f..1f)
        assertTrue(high.emphasis in 0f..1f)
    }

    @Test
    fun decayVisualUsesEffectiveXpAndRetainedPercentage() {
        val visual = SkillTreeLayout.visual(effectiveXp = 90L, baseXp = 100L, maxEffectiveXp = 100L)

        assertTrue(visual.isDecayed)
        assertEquals(90, visual.retainedPercent)
    }

    @Test
    fun retainedPercentFloorsAndClamps() {
        assertEquals(66, SkillTreeLayout.visual(2L, 3L, 3L).retainedPercent)
        assertEquals(100, SkillTreeLayout.visual(4L, 3L, 4L).retainedPercent)
        assertFalse(SkillTreeLayout.visual(4L, 3L, 4L).isDecayed)
    }

    @Test
    fun zeroBaseAndZeroMaxNeverDivideByZero() {
        val empty = SkillTreeLayout.visual(effectiveXp = 0L, baseXp = 0L, maxEffectiveXp = 0L)
        val orphanedEffectiveXp = SkillTreeLayout.visual(effectiveXp = 25L, baseXp = 0L, maxEffectiveXp = 0L)

        assertEquals(100, empty.retainedPercent)
        assertEquals(100, orphanedEffectiveXp.retainedPercent)
        assertTrue(empty.diameterDp >= SkillTreeLayout.MIN_NODE_DIAMETER_DP)
        assertTrue(orphanedEffectiveXp.diameterDp <= SkillTreeLayout.MAX_NODE_DIAMETER_DP)
    }
}
