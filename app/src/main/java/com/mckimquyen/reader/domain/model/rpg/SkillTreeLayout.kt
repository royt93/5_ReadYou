package com.mckimquyen.reader.domain.model.rpg

import com.mckimquyen.reader.domain.sv.QuizGeneratorService

data class SkillTreeNodeSpec(
    val categoryId: String,
    val x: Float,
    val y: Float,
)

data class SkillTreeEdge(
    val fromCategoryId: String,
    val toCategoryId: String,
)

data class SkillTreeNodeVisual(
    val diameterDp: Float,
    val emphasis: Float,
    val retainedPercent: Int,
    val isDecayed: Boolean,
)

object SkillTreeLayout {
    const val MIN_NODE_DIAMETER_DP = 48f
    const val MAX_NODE_DIAMETER_DP = 72f
    private const val PERCENT_SCALE = 100L

    val nodes: List<SkillTreeNodeSpec> = listOf(
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_TECH, x = 0.22f, y = 0.22f),
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_SCIENCE, x = 0.78f, y = 0.20f),
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_BUSINESS, x = 0.50f, y = 0.46f),
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_HEALTH, x = 0.18f, y = 0.76f),
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_PHILOSOPHY, x = 0.82f, y = 0.74f),
        SkillTreeNodeSpec(QuizGeneratorService.CATEGORY_GENERAL, x = 0.50f, y = 0.86f),
    )

    val edges: List<SkillTreeEdge> = listOf(
        SkillTreeEdge(QuizGeneratorService.CATEGORY_TECH, QuizGeneratorService.CATEGORY_BUSINESS),
        SkillTreeEdge(QuizGeneratorService.CATEGORY_SCIENCE, QuizGeneratorService.CATEGORY_BUSINESS),
        SkillTreeEdge(QuizGeneratorService.CATEGORY_BUSINESS, QuizGeneratorService.CATEGORY_HEALTH),
        SkillTreeEdge(QuizGeneratorService.CATEGORY_BUSINESS, QuizGeneratorService.CATEGORY_PHILOSOPHY),
        SkillTreeEdge(QuizGeneratorService.CATEGORY_HEALTH, QuizGeneratorService.CATEGORY_GENERAL),
        SkillTreeEdge(QuizGeneratorService.CATEGORY_PHILOSOPHY, QuizGeneratorService.CATEGORY_GENERAL),
    )

    fun visual(
        effectiveXp: Long,
        baseXp: Long,
        maxEffectiveXp: Long,
    ): SkillTreeNodeVisual {
        val safeEffective = effectiveXp.coerceAtLeast(0L)
        val safeBase = baseXp.coerceAtLeast(0L)
        val safeMax = maxEffectiveXp.coerceAtLeast(1L)
        val fraction = (safeEffective.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
        val diameter = MIN_NODE_DIAMETER_DP + (MAX_NODE_DIAMETER_DP - MIN_NODE_DIAMETER_DP) * fraction
        val isDecayed = safeBase > safeEffective
        val retained = if (safeBase > 0L) {
            ((safeEffective * PERCENT_SCALE) / safeBase).toInt().coerceIn(0, 100)
        } else {
            100
        }
        return SkillTreeNodeVisual(
            diameterDp = diameter,
            emphasis = fraction,
            retainedPercent = retained,
            isDecayed = isDecayed,
        )
    }
}
