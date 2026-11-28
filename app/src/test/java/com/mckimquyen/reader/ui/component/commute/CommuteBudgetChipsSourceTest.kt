package com.mckimquyen.reader.ui.component.commute

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On a Pixel with Russian text at font scale 1.3, the CommuteCast duration chips sat in one Row with the label: the "15 мин" chip was squeezed to a sliver and its label ran down it one letter per line.
 * The label and the chips are in a FlowRow now, so a chip that does not fit wraps to the next line instead of being squeezed.
 */
class CommuteBudgetChipsSourceTest {
    private val source: String = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader/ui/component/commute/CommuteCastSheet.kt").readText()

    private val budgetBlock = source.substringAfter("// Time Budget Selector Row").substringBefore("budgets.forEach")

    @Test fun theBudgetChipsWrapInsteadOfSharingOneRow() {
        assertTrue("label and chips must sit in a FlowRow", "FlowRow(" in budgetBlock)
        assertTrue("not in a plain Row that squeezes the last chip", "        Row(" !in budgetBlock)
        assertTrue("file must opt in to the experimental layout API", "ExperimentalLayoutApi" in source.substringBefore("package "))
    }
}
