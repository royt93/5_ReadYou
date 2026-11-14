package com.mckimquyen.reader.ui.golden

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import java.io.File
import kotlin.math.abs

/**
 * A tiny golden-image check with no extra library. [capture] draws the real view tree into a bitmap with `View.draw`
 * (Robolectric native graphics); [assertMatches] compares it with a PNG stored in `src/test/resources/golden`.
 * Record or refresh the PNGs by running the tests with the environment variable `GOLDEN_RECORD=true`, and look at them
 * before committing: a golden image only proves the screen did not change, not that it is right. Images are drawn by
 * Robolectric on this machine's JVM; a different OS or JDK can shift anti-aliasing, hence the small tolerance.
 */
object Golden {
    private const val DIR = "src/test/resources/golden"
    private const val CHANNEL_TOLERANCE = 2
    private const val MAX_DIFFERENT_PIXEL_RATIO = 0.001
    private val record: Boolean get() = System.getProperty("golden.record") == "true" || System.getenv("GOLDEN_RECORD") == "true"

    fun capture(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    sealed interface Result {
        data object Recorded : Result
        data object Same : Result
        data class Different(val ratio: Double, val actualFile: File) : Result
        data object Missing : Result
        data class SizeChanged(val expected: String, val actual: String) : Result
    }

    /** True when both images have the same size and differ in at most [MAX_DIFFERENT_PIXEL_RATIO] of their pixels. */
    fun sameImage(expected: Bitmap, actual: Bitmap): Boolean {
        if (expected.width != actual.width || expected.height != actual.height) return false
        val a = IntArray(actual.width * actual.height).also { actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height) }
        val e = IntArray(a.size).also { expected.getPixels(it, 0, expected.width, 0, 0, expected.width, expected.height) }
        var different = 0
        for (i in a.indices) if (!close(e[i], a[i])) different++
        return different.toDouble() / a.size <= MAX_DIFFERENT_PIXEL_RATIO
    }

    fun compare(name: String, actual: Bitmap): Result {
        val goldenFile = File(DIR, "$name.png")
        if (record) {
            writePng(actual, goldenFile)
            return Result.Recorded
        }
        if (!goldenFile.exists()) return Result.Missing
        val expected = requireNotNull(BitmapFactory.decodeFile(goldenFile.path)) { "golden '$name' is not a readable PNG" }
        if (expected.width != actual.width || expected.height != actual.height) {
            return Result.SizeChanged("${expected.width}x${expected.height}", "${actual.width}x${actual.height}")
        }
        val pixels = IntArray(actual.width * actual.height).also { actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height) }
        val wanted = IntArray(pixels.size).also { expected.getPixels(it, 0, expected.width, 0, 0, expected.width, expected.height) }
        var different = 0
        for (i in pixels.indices) if (!close(wanted[i], pixels[i])) different++
        val ratio = different.toDouble() / pixels.size
        if (ratio <= MAX_DIFFERENT_PIXEL_RATIO) return Result.Same
        val out = File("build/golden-actual/$name.png")
        writePng(actual, out)
        return Result.Different(ratio, out)
    }

    fun message(name: String, result: Result): String = when (result) {
        is Result.Different -> "golden '$name' differs in ${"%.2f".format(result.ratio * 100)}% of pixels; actual image: ${result.actualFile}"
        is Result.SizeChanged -> "golden '$name' size changed from ${result.expected} to ${result.actual}"
        Result.Missing -> "golden '$name' has no stored image yet; record it with GOLDEN_RECORD=true and look at it first"
        else -> "golden '$name': $result"
    }

    private fun close(a: Int, b: Int): Boolean =
        abs((a ushr 24) - (b ushr 24)) <= CHANNEL_TOLERANCE && abs((a shr 16 and 0xff) - (b shr 16 and 0xff)) <= CHANNEL_TOLERANCE &&
            abs((a shr 8 and 0xff) - (b shr 8 and 0xff)) <= CHANNEL_TOLERANCE && abs((a and 0xff) - (b and 0xff)) <= CHANNEL_TOLERANCE

    private fun writePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) { "PNG encoding failed" } }
    }

    private const val PNG_QUALITY = 100
}
