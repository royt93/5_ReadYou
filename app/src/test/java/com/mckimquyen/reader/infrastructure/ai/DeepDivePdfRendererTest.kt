package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfPlan
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepDivePdfRendererTest {

    private val texts = DeepDivePdfTexts("AI wrote this", "Context", "Critical", "Impact", "Area", "Short", "Long", "More in the app", "2026-11-11")
    private val report = DeepDiveReport(
        "a",
        listOf("Rates were low", "Inflation rose", "The bank pivoted"),
        listOf(DeepDivePerspective("Monetarist", "Overdue"), DeepDivePerspective("Keynesian", "Risky")),
        listOf(DeepDiveImpact("Housing", "Costs rise", "Prices cool"), DeepDiveImpact("Exports", "Currency firms", "Margins shrink")),
    )

    private fun blankPage(): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(DeepDivePdfLayout.PAGE_WIDTH, DeepDivePdfLayout.PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
        return bmp to canvas
    }

    private fun anyInkInRows(bmp: Bitmap, from: Int, to: Int): Boolean {
        for (y in from until to) for (x in 0 until bmp.width) if (bmp.getPixel(x, y) != Color.WHITE) return true
        return false
    }

    @Test fun drawPage_paintsInsideTheMargins() {
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, report, false), "Rates rise", texts)
        assertTrue("something must be drawn", anyInkInRows(bmp, DeepDivePdfLayout.MARGIN, DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN))
    }

    @Test fun drawPage_leavesTheBottomMarginEmptyEvenForAHugeReport() {
        val long = "word ".repeat(60)
        val huge = DeepDiveReport("a", List(5) { long }, List(4) { DeepDivePerspective("s", long) }, List(5) { DeepDiveImpact("a", long, long) })
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, huge, true), "Title", texts)
        assertEquals(false, anyInkInRows(bmp, DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN + 1, DeepDivePdfLayout.PAGE_HEIGHT))
    }

    @Test fun drawPage_stopsAtTheBottomMarginWhenTheTextSectionsOverflow_notOnlyTheMatrix() {
        // Overflow happens in the context bullets (drawn by block), before the matrix (drawn by row) is reached.
        val huge = "word ".repeat(500)
        val report = DeepDiveReport("a", List(5) { huge }, List(2) { DeepDivePerspective("s", huge) }, List(2) { DeepDiveImpact("a", "b", "c") })
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, report, true), "Title", texts)
        assertEquals(false, anyInkInRows(bmp, DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN + 1, DeepDivePdfLayout.PAGE_HEIGHT))
        assertTrue("the top of the page is still drawn", anyInkInRows(bmp, DeepDivePdfLayout.MARGIN, DeepDivePdfLayout.MARGIN + 40))
    }

    @Test fun drawPage_leavesTheSideMarginsEmpty() {
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, report, false), "Rates rise", texts)
        var ink = false
        for (y in 0 until bmp.height) for (x in 0 until DeepDivePdfLayout.MARGIN - 1) if (bmp.getPixel(x, y) != Color.WHITE) ink = true
        assertEquals(false, ink)
    }

    // Not here on purpose: Robolectric's PdfDocument is closed as soon as it is created ("document is closed!"), so
    // `render` (PdfDocument + one page + PDF bytes) cannot run on the JVM. It is covered on a real device by
    // DeepDiveSheetWidgetTest.pdfRenderer_producesAOnePagePdfOnARealDevice (Task 10), which reopens the bytes with
    // PdfRenderer and checks one page of A4. This test only guards that the source asks for exactly one page.
    @Test fun renderStartsExactlyOnePage_andNeverASecond() {
        val source = java.io.File("src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDivePdfRenderer.kt").readText()
        assertEquals(1, Regex("""\.startPage\(""").findAll(source).count())
        assertEquals(1, Regex("""\.finishPage\(""").findAll(source).count())
        assertTrue(source.contains("PageInfo.Builder(DeepDivePdfLayout.PAGE_WIDTH, DeepDivePdfLayout.PAGE_HEIGHT, 1)"))
    }
}
