package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDiveSharerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val sharer = DeepDiveSharer(app, Dispatchers.Unconfined)
    private val dir get() = File(app.cacheDir, DeepDiveSharer.DIR_NAME)

    @Before fun clean() { dir.deleteRecursively() }
    @After fun cleanAfter() { dir.deleteRecursively() }

    private val pdf = "%PDF-1.4 fake".toByteArray()

    @Test fun writesBytesUnderTheDeepDiveCacheDirectory() = runBlocking {
        val f = sharer.write(pdf, "Rates rise", nowMillis = 1_000L)
        assertEquals(dir.canonicalPath, f.parentFile!!.canonicalPath)
        assertEquals("deepdive_rates_rise_1000.pdf", f.name)
        assertTrue(f.readBytes().contentEquals(pdf))
    }

    @Test fun slugIsAsciiAndCannotEscapeTheDirectory() = runBlocking {
        val f = sharer.write(pdf, "../../etc/passwd Ünï \u0000 ${"x".repeat(100)}", nowMillis = 5L)
        assertEquals(dir.canonicalPath, f.parentFile!!.canonicalPath)
        assertTrue(f.name, Regex("deepdive_[a-z0-9_]{1,40}_5\\.pdf").matches(f.name))
    }

    @Test fun directoryNameIsTheDocumentedOne_notDossiers() {
        assertEquals("deepdive", DeepDiveSharer.DIR_NAME)
        assertEquals(24L * 60 * 60 * 1000, DeepDiveSharer.RETENTION_MS)
        assertEquals("application/pdf", DeepDiveSharer.MIME_PDF)
    }

    @Test fun emptyTitleFallsBackToAFixedSlug() = runBlocking {
        assertEquals("deepdive_report_7.pdf", sharer.write(pdf, "  ", nowMillis = 7L).name)
    }

    @Test fun filesOlderThanTheRetentionAreDeletedOnWrite() = runBlocking {
        val old = sharer.write(pdf, "old", nowMillis = 1_000L)
        old.setLastModified(1_000L)
        val fresh = sharer.write(pdf, "new", nowMillis = DeepDiveSharer.RETENTION_MS + 2_000L)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun shareIntentIsASendOfAPdfWithAReadGrant() = runBlocking {
        val f = sharer.write(pdf, "t", nowMillis = 1L)
        val chooser = sharer.buildShareIntent(f, "Share")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
