package com.mckimquyen.reader.infrastructure.rpg

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class BrainWrappedSharerTest {

    private lateinit var context: Context
    private lateinit var sharer: BrainWrappedSharer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, BrainWrappedSharer.DIR_NAME).deleteRecursively()
        sharer = BrainWrappedSharer(context, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        File(context.cacheDir, BrainWrappedSharer.DIR_NAME).deleteRecursively()
    }

    @Test
    fun writePng_createsReadableImageInDedicatedCacheDirectory() = runBlocking {
        val bitmap = Bitmap.createBitmap(BrainWrappedRenderer.CARD_WIDTH, BrainWrappedRenderer.CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val file = sharer.writePng(bitmap, nowMillis = System.currentTimeMillis())

        assertEquals(File(context.cacheDir, BrainWrappedSharer.DIR_NAME), file.parentFile)
        assertTrue(file.readBytes().take(PNG_SIGNATURE.size).toByteArray().contentEquals(PNG_SIGNATURE))
        val decoded = BitmapFactory.decodeFile(file.path)
        assertNotNull(decoded)
        assertEquals(BrainWrappedRenderer.CARD_WIDTH, decoded.width)
        assertEquals(BrainWrappedRenderer.CARD_HEIGHT, decoded.height)
        assertTrue(File(context.cacheDir, "quote_cards").exists().not())
    }

    @Test
    fun buildShareIntent_hasPngStreamProviderUriAndReadGrant() = runBlocking {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val file = sharer.writePng(bitmap)

        val chooser = sharer.buildShareIntent(file, "Share weekly Brain Wrapped")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals(BrainWrappedSharer.MIME_PNG, send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("${context.packageName}.fileprovider", uri!!.authority)
        assertTrue(uri.path.orEmpty().endsWith(file.name))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.clipData)
    }

    @Test
    fun writePng_cleanupDoesNotTouchOtherFeatureCache() = runBlocking {
        val otherDir = File(context.cacheDir, "quote_cards").apply { mkdirs() }
        val quoteCard = File(otherDir, "keep.png").apply { writeText("keep") }
        val wrappedDir = File(context.cacheDir, BrainWrappedSharer.DIR_NAME).apply { mkdirs() }
        val expired = File(wrappedDir, "expired.png").apply {
            writeText("expired")
            setLastModified(System.currentTimeMillis() - BrainWrappedSharer.RETENTION_MS - 1)
        }

        sharer.writePng(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))

        assertTrue(quoteCard.exists())
        assertTrue(!expired.exists())
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
