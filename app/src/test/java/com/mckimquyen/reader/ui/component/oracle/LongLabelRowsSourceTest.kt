package com.mckimquyen.reader.ui.component.oracle

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rows of chips whose labels are translated must wrap instead of squeezing: in Polish, Russian and Malayalam four "N points" chips or three result buttons do not fit one
 * line, and a plain Row squeezed the last one until its label ran down it letter by letter (the same defect the sleep-timer chips had). Static check on the three rows.
 */
class LongLabelRowsSourceTest {
    private val ui: File = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader/ui")

    private fun source(path: String) = ui.resolve(path).readText()

    @Test fun theStakeChipsAndTheResultButtonsOfTheOracleWrap() {
        val s = source("component/oracle/OracleBettingSection.kt")
        val stakes = s.substringAfter("OracleBettingPolicy.STAKES.forEach").let { s.substring(s.indexOf("OracleBettingPolicy.STAKES.forEach") - 80, s.indexOf("OracleBettingPolicy.STAKES.forEach")) }
        assertTrue("the stake chips must sit in a FlowRow: $stakes", "FlowRow(" in stakes)
        val results = s.substringAfter("private fun ResultButtons").substringBefore("}\n}")
        assertTrue("the result buttons must sit in a FlowRow", "FlowRow(" in results.take(200))
    }

    @Test fun theQuoteCardTemplateChipsWrap() {
        val s = source("page/home/read/quote/QuoteCardSheetContent.kt")
        val before = s.substring(s.indexOf("QuoteCardTemplate.entries.forEach") - 160, s.indexOf("QuoteCardTemplate.entries.forEach"))
        assertTrue("the template chips must sit in a FlowRow: $before", "FlowRow(" in before)
    }
}
