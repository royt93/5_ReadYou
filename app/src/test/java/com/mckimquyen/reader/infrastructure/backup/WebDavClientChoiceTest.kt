package com.mckimquyen.reader.infrastructure.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.sv.OpmlSv
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** WebDAV sends a password, so it uses the certificate-checking client unless the user allowed that one server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebDavClientChoiceTest {
    private val trustAll = OkHttpClient()
    private val strict = OkHttpClient()
    private lateinit var manager: WebDavBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("self_signed_trust", Context.MODE_PRIVATE).edit().clear().commit()
        manager = WebDavBackupManager(context, mockk<OpmlSv>(relaxed = true), trustAll, strict, Dispatchers.Unconfined)
    }

    @Test
    fun aServerNobodyAllowedGetsTheStrictClient() {
        assertSame(strict, manager.clientFor("https://dav.example.com/files/"))
    }

    @Test
    fun onlyTheAllowedServerGetsTheTrustAllClient() {
        manager.setSelfSignedTrusted("https://nas.home:5006/dav", true)
        assertSame(trustAll, manager.clientFor("https://nas.home:5006/other/path"))
        assertSame(strict, manager.clientFor("https://nas.home:5007/dav"))
        assertSame(strict, manager.clientFor("https://dav.example.com/"))
    }

    @Test
    fun switchingTheExceptionOffGoesBackToStrict() {
        manager.setSelfSignedTrusted("https://nas.home/dav", true)
        manager.setSelfSignedTrusted("https://nas.home/dav", false)
        assertSame(strict, manager.clientFor("https://nas.home/dav"))
    }
}
