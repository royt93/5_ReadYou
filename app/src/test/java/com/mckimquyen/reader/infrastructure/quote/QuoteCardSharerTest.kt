package com.mckimquyen.reader.infrastructure.quote

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuoteCardSharerTest {

    private lateinit var context: Context
    private lateinit var sharer: QuoteCardSharer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, QuoteCardSharer.DIR_NAME).deleteRecursively()
        sharer = QuoteCardSharer(context, Dispatchers.Unconfined)
    }

    private fun bitmap(width: Int = 40, height: Int = 50): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    @Test
    fun writePng_createsValidPngUnderQuoteCardsDir() = runBlocking {
        val file = sharer.writePng(bitmap(40, 50), QuoteCardTemplate.DARK_STUDIO, nowMillis = NOW)

        assertEquals(File(context.cacheDir, QuoteCardSharer.DIR_NAME), file.parentFile)
        val header = file.inputStream().use { stream -> ByteArray(PNG_SIGNATURE.size).also { stream.read(it) } }
        assertTrue("phải có chữ ký PNG", header.contentEquals(PNG_SIGNATURE))
        val decoded = BitmapFactory.decodeFile(file.path)
        assertNotNull(decoded)
        assertEquals(40, decoded.width)
        assertEquals(50, decoded.height)
    }

    @Test
    fun writePng_usesDifferentNamesAtDifferentTimes() = runBlocking {
        val a = sharer.writePng(bitmap(), QuoteCardTemplate.MINIMALIST, nowMillis = NOW)
        val b = sharer.writePng(bitmap(), QuoteCardTemplate.MINIMALIST, nowMillis = NOW + 1)

        assertNotEquals(a.name, b.name)
        assertTrue(a.exists() && b.exists())
    }

    @Test
    fun writePng_prunesFilesOlderThanRetention() = runBlocking {
        val dir = File(context.cacheDir, QuoteCardSharer.DIR_NAME).apply { mkdirs() }
        val old = File(dir, "old.png").apply {
            writeText("x")
            setLastModified(NOW - QuoteCardSharer.RETENTION_MS - 1)
        }
        val recent = File(dir, "recent.png").apply {
            writeText("y")
            setLastModified(NOW - QuoteCardSharer.RETENTION_MS + 60_000)
        }

        val fresh = sharer.writePng(bitmap(), QuoteCardTemplate.MINIMALIST, nowMillis = NOW)

        assertFalse("file quá hạn phải bị xoá", old.exists())
        assertTrue("file còn hạn phải được giữ", recent.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun buildShareIntent_hasActionTypeStreamAndReadPermission() = runBlocking {
        val file = sharer.writePng(bitmap(), QuoteCardTemplate.GRADIENT_MATERIAL, nowMillis = NOW)

        val chooser = sharer.buildShareIntent(file, "Share quote card")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals(QuoteCardSharer.MIME_PNG, send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("${context.packageName}.fileprovider", uri!!.authority)
        assertTrue(uri.path!!.endsWith(file.name))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.clipData)
    }

    private companion object {
        // Mốc thời gian thật: mtime file do hệ thống đặt theo giờ thật nên mốc giả ở tương lai sẽ làm prune xoá nhầm.
        val NOW = System.currentTimeMillis()
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
