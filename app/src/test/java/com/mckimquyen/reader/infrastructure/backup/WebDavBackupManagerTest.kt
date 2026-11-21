package com.mckimquyen.reader.infrastructure.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.sv.OpmlSv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okio.Buffer
import okio.BufferedSource
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebDavBackupManagerTest {

    private lateinit var context: Context
    private val opmlSv = mockk<OpmlSv>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var manager: WebDavBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("webdav_backup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        manager = WebDavBackupManager(
            context = context,
            opmlSv = opmlSv,
            okHttpClient = okHttpClient,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun buildFileUrl_normalizesSlash() {
        assertEquals(
            "https://dav.test/files/rss_hub_backup.opml",
            manager.buildFileUrl("https://dav.test/files/")
        )
        assertEquals(
            "https://dav.test/files/rss_hub_backup.opml",
            manager.buildFileUrl("https://dav.test/files")
        )
    }

    @Test
    fun backup_whenNotConfigured_fails() = runBlocking {
        val result = manager.backup(accountId = 1)
        assertTrue(result.isFailure)
        assertEquals("WebDAV is not configured", result.exceptionOrNull()?.message)
    }

    @Test
    fun restore_whenNotConfigured_fails() = runBlocking {
        val result = manager.restore()
        assertTrue(result.isFailure)
        assertEquals("WebDAV is not configured", result.exceptionOrNull()?.message)
    }

    @Test
    fun saveConfig_persistsValues() {
        manager.saveConfig("https://nextcloud.test/dav", "user", "secret")
        val config = manager.getConfig()

        assertTrue(config.isConfigured)
        assertEquals("https://nextcloud.test/dav", config.serverUrl)
        assertEquals("user", config.username)
        assertEquals("secret", config.password)
    }

    @Test
    fun backup_success_updatesLastBackupTime() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        coEvery { opmlSv.saveToString(1) } returns "<opml version=\"2.0\"><head/></opml>"

        val requestSlot = io.mockk.slot<Request>()
        val mockCall = mockk<Call>(relaxed = true)
        every { okHttpClient.newCall(capture(requestSlot)) } returns mockCall
        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onResponse(
                mockCall,
                Response.Builder()
                    .request(requestSlot.captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody())
                    .build()
            )
        }

        val result = manager.backup(accountId = 1)

        assertTrue(result.isSuccess)
        assertNotNull(manager.getConfig().lastBackupTime)
        // Verify request targeted the correct WebDAV URL with Basic Auth header set
        assertEquals("https://nextcloud.test/dav/rss_hub_backup.opml", requestSlot.captured.url.toString())
        assertTrue(requestSlot.captured.header("Authorization")?.startsWith("Basic ") == true)
    }

    // --- restore: the server is not trusted, so the size of what it sends is bounded before anything is parsed ---

    private val restoreLimitBytes = 10L * 1024L * 1024L

    private fun answerRestoreWith(body: ResponseBody) {
        val requestSlot = io.mockk.slot<Request>()
        val mockCall = mockk<Call>(relaxed = true)
        every { okHttpClient.newCall(capture(requestSlot)) } returns mockCall
        every { mockCall.enqueue(any()) } answers {
            firstArg<okhttp3.Callback>().onResponse(
                mockCall,
                Response.Builder().request(requestSlot.captured).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
            )
        }
    }

    /** A body whose length is NOT announced (chunked), so only counting the bytes can stop it. */
    private fun unannouncedBody(size: Long): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1L
        override fun source(): BufferedSource = Buffer().write(ByteArray(size.toInt()))
    }

    @Test
    fun restore_smallFile_isHandedToTheImporterByteForByte() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        val xml = "<opml version=\"2.0\"><head/><body/></opml>".toByteArray()
        answerRestoreWith(xml.toResponseBody())
        val seen = io.mockk.slot<InputStream>()
        var received = ByteArray(0)
        coEvery { opmlSv.saveToDatabase(capture(seen)) } answers { received = seen.captured.readBytes() }

        val result = manager.restore()

        assertTrue(result.isSuccess)
        assertArrayEquals(xml, received)
    }

    @Test
    fun restore_whenTheServerAnnouncesAHugeFile_isRefused_andNothingIsImported() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        answerRestoreWith(ByteArray((restoreLimitBytes + 1).toInt()).toResponseBody())

        val result = manager.restore()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { opmlSv.saveToDatabase(any()) }
    }

    @Test
    fun restore_whenTheServerHidesTheLength_isStillRefused_afterTheLimit() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        answerRestoreWith(unannouncedBody(restoreLimitBytes + 1))

        val result = manager.restore()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { opmlSv.saveToDatabase(any()) }
    }

    @Test
    fun restore_whenTheServerAnnouncesAHugeFile_isRefusedBeforeReadingAnyByte() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        var sourceOpened = false
        answerRestoreWith(object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = restoreLimitBytes + 1
            override fun source(): BufferedSource {
                sourceOpened = true
                return Buffer().write(ByteArray(8))
            }
        })

        val result = manager.restore()

        assertTrue(result.isFailure)
        assertFalse("an announced oversize file must be refused without reading its body", sourceOpened)
        coVerify(exactly = 0) { opmlSv.saveToDatabase(any()) }
    }

    @Test
    fun restore_aFileExactlyAtTheLimit_isAccepted() = runBlocking {
        manager.saveConfig("https://nextcloud.test/dav/", "user", "secret")
        answerRestoreWith(unannouncedBody(restoreLimitBytes))
        coEvery { opmlSv.saveToDatabase(any()) } returns Unit

        val result = manager.restore()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { opmlSv.saveToDatabase(any()) }
    }

    @Test
    fun theLimitIsTenMebibytes() = assertEquals(restoreLimitBytes, WebDavBackupManager.MAX_RESTORE_BYTES)
}
