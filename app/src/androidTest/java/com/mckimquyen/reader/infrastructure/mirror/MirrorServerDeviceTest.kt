package com.mckimquyen.reader.infrastructure.mirror

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

@RunWith(AndroidJUnit4::class)
class MirrorServerDeviceTest {
    private val server = MirrorServer()
    @After fun tearDown() = server.stop()

    private fun http(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 5_000
        return try {
            val code = c.responseCode
            code to (if (code < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    @Test fun serverRunsOnARealDeviceServesTheTokenedPageAndHidesEverythingElse() {
        val started = server.start(InetAddress.getByName("127.0.0.1")) as MirrorStartResult.Started
        server.publish(MirrorSnapshot("Hello", "BBC", 5L, listOf("p"), false, 1L))
        val (pageCode, page) = http(started.port, "/m/${started.token.value}")
        assertEquals(200, pageCode)
        assertTrue(page.contains("EventSource"))
        val (stateCode, state) = http(started.port, "/m/${started.token.value}/state")
        assertEquals(200, stateCode)
        assertTrue(state.contains("\"title\":\"Hello\""))
        assertEquals(404, http(started.port, "/").first)
        assertEquals(404, http(started.port, "/m/" + "0".repeat(32) + "/state").first)
        server.stop()
        assertTrue(runCatching { http(started.port, "/m/${started.token.value}") }.isFailure)
    }
}
