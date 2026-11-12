package com.mckimquyen.reader.domain.ai.agents

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDivePolicyParseTest {

    private fun arr(items: List<String>) = items.joinToString(",", "[", "]") { "\"$it\"" }

    private fun json(
        context: List<String> = listOf("Rates were low for a decade", "Inflation rose after 2021", "The bank signalled a pivot"),
        critical: List<Pair<String, String>> = listOf("Monetarist" to "Tightening is overdue", "Keynesian" to "It risks a recession"),
        impact: List<Triple<String, String, String>> = listOf(
            Triple("Housing", "Mortgage costs rise", "Prices cool"),
            Triple("Exports", "Currency firms", "Margins shrink"),
        ),
    ): String {
        val c = critical.joinToString(",", "[", "]") { """{"school":"${it.first}","view":"${it.second}"}""" }
        val i = impact.joinToString(",", "[", "]") { """{"area":"${it.first}","shortTerm":"${it.second}","longTerm":"${it.third}"}""" }
        return """{"context":${arr(context)},"critical":$c,"impact":$i}"""
    }

    private fun parse(raw: String) = DeepDivePolicy.parseAndValidate(raw, articleId = "art-1")
    private fun rejected(raw: String): DeepDiveRejection = (parse(raw) as DeepDiveParse.Rejected).reason
    private fun valid(raw: String): DeepDiveReport = (parse(raw) as DeepDiveParse.Valid).report

    @Test fun validAnswer_isAccepted() {
        val r = valid(json())
        assertEquals("art-1", r.articleId)
        assertEquals(3, r.context.size)
        assertEquals("Monetarist", r.perspectives[0].school)
        assertEquals("Mortgage costs rise", r.impacts[0].shortTerm)
        assertEquals("Prices cool", r.impacts[0].longTerm)
    }

    @Test fun markdownFenceIsStripped() {
        assertTrue(parse("```json\n" + json() + "\n```") is DeepDiveParse.Valid)
    }

    @Test fun surroundingWhitespaceAndInnerRunsAreTidied() {
        val r = valid(json(context = listOf("  a   b  ", "c", "d")))
        assertEquals("a b", r.context[0])
    }

    @Test fun notJson_isRejected() {
        assertEquals(DeepDiveRejection.NOT_JSON, rejected("Sure! here is your report"))
        assertEquals(DeepDiveRejection.NOT_JSON, rejected(""))
    }

    @Test fun missingSection_isRejected() {
        assertEquals(DeepDiveRejection.MISSING_FIELD, rejected("""{"context":["a","b","c"],"critical":[]}"""))
        assertEquals(DeepDiveRejection.MISSING_FIELD, rejected("""{"context":"text","critical":[],"impact":[]}"""))
    }

    @Test fun contextCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_CONTEXT_COUNT, rejected(json(context = listOf("a", "b"))))
        assertTrue(parse(json(context = listOf("a", "b", "c"))) is DeepDiveParse.Valid)
        assertTrue(parse(json(context = listOf("a", "b", "c", "d", "e"))) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_CONTEXT_COUNT, rejected(json(context = listOf("a", "b", "c", "d", "e", "f"))))
    }

    @Test fun perspectiveCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT, rejected(json(critical = listOf("s" to "v"))))
        val four = (1..4).map { "s$it" to "v$it" }
        assertTrue(parse(json(critical = four)) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT, rejected(json(critical = four + ("s5" to "v5"))))
    }

    @Test fun impactCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_IMPACT_COUNT, rejected(json(impact = listOf(Triple("a", "b", "c")))))
        val five = (1..5).map { Triple("a$it", "b$it", "c$it") }
        assertTrue(parse(json(impact = five)) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_IMPACT_COUNT, rejected(json(impact = five + Triple("a6", "b6", "c6"))))
    }

    @Test fun emptyOrBlankText_isRejectedInEveryField() {
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(context = listOf("a", " ", "c"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(critical = listOf("" to "v", "s" to "v"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(critical = listOf("s" to " ", "s" to "v"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("", "b", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("a", "", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("a", "b", ""), Triple("a", "b", "c")))))
    }

    @Test fun nonStringEntryInContext_isRejectedAsEmptyText() {
        assertEquals(
            DeepDiveRejection.EMPTY_TEXT,
            rejected("""{"context":["a",5,"c"],"critical":[{"school":"s","view":"v"},{"school":"s","view":"v"}],"impact":[{"area":"a","shortTerm":"b","longTerm":"c"},{"area":"a","shortTerm":"b","longTerm":"c"}]}"""),
        )
    }

    @Test fun textLengthBoundaries() {
        val ok = "x".repeat(DeepDivePolicy.MAX_POINT_CHARS)
        val long = "x".repeat(DeepDivePolicy.MAX_POINT_CHARS + 1)
        assertTrue(parse(json(context = listOf(ok, "b", "c"))) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(context = listOf(long, "b", "c"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(critical = listOf("s".repeat(DeepDivePolicy.MAX_SCHOOL_CHARS + 1) to "v", "s" to "v"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(critical = listOf("s" to "v".repeat(DeepDivePolicy.MAX_VIEW_CHARS + 1), "s" to "v"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a".repeat(DeepDivePolicy.MAX_AREA_CHARS + 1), "b", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a", "b".repeat(DeepDivePolicy.MAX_IMPACT_CHARS + 1), "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a", "b", "c".repeat(DeepDivePolicy.MAX_IMPACT_CHARS + 1)), Triple("a", "b", "c")))))
    }

    @Test fun limitsAreTheDocumentedNumbers_notJustWhateverTheConstantsSay() {
        assertEquals(220, DeepDivePolicy.MAX_POINT_CHARS)
        assertEquals(40, DeepDivePolicy.MAX_SCHOOL_CHARS)
        assertEquals(240, DeepDivePolicy.MAX_VIEW_CHARS)
        assertEquals(40, DeepDivePolicy.MAX_AREA_CHARS)
        assertEquals(160, DeepDivePolicy.MAX_IMPACT_CHARS)
        // Literal lengths: 240 is accepted and 241 is rejected for a view.
        assertTrue(parse(json(critical = listOf("s" to "v".repeat(240), "s" to "v"))) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(critical = listOf("s" to "v".repeat(241), "s" to "v"))))
    }

    @Test fun textAtExactlyTheLimitIsAccepted_inEveryField() {
        val r = valid(
            json(
                critical = listOf("s".repeat(DeepDivePolicy.MAX_SCHOOL_CHARS) to "v".repeat(DeepDivePolicy.MAX_VIEW_CHARS), "s" to "v"),
                impact = listOf(
                    Triple("a".repeat(DeepDivePolicy.MAX_AREA_CHARS), "b".repeat(DeepDivePolicy.MAX_IMPACT_CHARS), "c".repeat(DeepDivePolicy.MAX_IMPACT_CHARS)),
                    Triple("a", "b", "c"),
                ),
            )
        )
        assertEquals(DeepDivePolicy.MAX_VIEW_CHARS, r.perspectives[0].view.length)
    }
}
