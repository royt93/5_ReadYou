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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
}
