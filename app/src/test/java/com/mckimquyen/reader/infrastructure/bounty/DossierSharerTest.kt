package com.mckimquyen.reader.infrastructure.bounty

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DossierSharerTest {

    private lateinit var context: Context
    private lateinit var sharer: DossierSharer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, DossierSharer.DIR_NAME).deleteRecursively()
        sharer = DossierSharer(context, Dispatchers.Unconfined)
    }

    @Test
    fun write_createsFileUnderDossiersDir_withUtf8Content() = runBlocking {
        val markdown = "# Dossier: DeepSeek\n\nNội dung tiếng Việt có dấu và emoji 😀\n"
        val file = sharer.write(markdown, "DeepSeek", NOW)

        assertEquals(File(context.cacheDir, DossierSharer.DIR_NAME), file.parentFile)
        assertTrue(file.name.startsWith("dossier_deepseek_${NOW}"))
        assertTrue(file.name.endsWith(".md"))
        assertEquals(markdown, file.readText(Charsets.UTF_8))
    }

    @Test
    fun slug_isSafeAgainstHostileQueries() = runBlocking {
        val hostile = "../../etc/passwd with spaces & punctuation?!"
        val file = sharer.write("# hi", hostile, NOW)

        assertFalse(file.name.contains(".."))
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains("\\"))
        assertTrue(file.name.startsWith("dossier_etc_passwd_with_spaces_punctuation_"))
    }

    @Test
    fun slug_fallsBackWhenQueryHasNoAlphanumeric() = runBlocking {
        val file = sharer.write("# hi", "??? !!!", NOW)

        assertTrue(file.name.startsWith("dossier_query_${NOW}"))
    }

    @Test
    fun write_prunesFilesOlderThan24Hours() = runBlocking {
        val dir = File(context.cacheDir, DossierSharer.DIR_NAME).apply { mkdirs() }
        val old = File(dir, "old.md").apply {
            writeText("old")
            setLastModified(NOW - DossierSharer.RETENTION_MS - 1)
        }
        val recent = File(dir, "recent.md").apply {
            writeText("recent")
            setLastModified(NOW - DossierSharer.RETENTION_MS + 60_000)
        }

        val fresh = sharer.write("# test", "test", NOW)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun buildShareIntent_hasChooserActionSendMarkdownTypeAndReadPermission() = runBlocking {
        val file = sharer.write("# test", "DeepSeek", NOW)

        val chooser = sharer.buildShareIntent(file, "Share dossier")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals(DossierSharer.MIME_MARKDOWN, send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("${context.packageName}.fileprovider", uri!!.authority)
        assertTrue(uri.path!!.endsWith(file.name))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.clipData)
    }

    private companion object {
        val NOW = System.currentTimeMillis()
    }
}
