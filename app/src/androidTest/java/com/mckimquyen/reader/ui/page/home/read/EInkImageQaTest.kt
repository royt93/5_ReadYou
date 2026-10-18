package com.mckimquyen.reader.ui.page.home.read

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.ui.component.base.BaseAsyncImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/** Kiểm tra pixel thật của BaseAsyncImage; server loopback chỉ ảnh hưởng fixture, không đổi mạng thiết bị. */
class EInkImageQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bitmapGolden_grayscaleMatchesLuminanceAndOffRestoresColor() {
        var mode by mutableStateOf<EInkModePref>(EInkModePref.ON)
        val bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalEInkMode provides mode) {
                    MaterialTheme {
                        BaseAsyncImage(Modifier.size(IMAGE_DP).testTag(IMAGE_TAG), data = bitmap,
                            placeholder = null, error = null, enableClipping = false)
                    }
                }
            }
            waitForPixel { isGray(it) && abs(it.red - RED_LUMINANCE) < PIXEL_TOLERANCE }
            val pixels = composeRule.onNodeWithTag(IMAGE_TAG).captureToImage().toPixelMap()
            // Golden toán học: ảnh đỏ đặc phải thành gray = hệ số đỏ của ColorMatrix saturation 0.
            for (y in pixels.height / SAMPLE_DIVISOR until pixels.height * 3 / SAMPLE_DIVISOR) {
                for (x in pixels.width / SAMPLE_DIVISOR until pixels.width * 3 / SAMPLE_DIVISOR) {
                    val pixel = pixels[x, y]
                    assertTrue("Pixel ($x,$y) không khớp golden gray: $pixel", isGray(pixel))
                    assertEquals(RED_LUMINANCE, pixel.red, PIXEL_TOLERANCE)
                }
            }
            mode = EInkModePref.OFF
            waitForPixel { it.red > STRONG_CHANNEL && it.green < WEAK_CHANNEL && it.blue < WEAK_CHANNEL }
        } finally {
            // Coil có thể giữ bitmap trong cache; không recycle bitmap đang thuộc painter/cache.
        }
    }

    @Test
    fun throttledImageAndHttpFailure_doNotBreakRenderingOrModeToggle() {
        FixtureServer().use { server ->
            var mode by mutableStateOf<EInkModePref>(EInkModePref.ON)
            var source by mutableStateOf(server.url("slow"))
            composeRule.setContent {
                CompositionLocalProvider(LocalEInkMode provides mode) {
                    MaterialTheme {
                        BaseAsyncImage(Modifier.size(IMAGE_DP).testTag(IMAGE_TAG), data = source, enableClipping = false)
                    }
                }
            }
            waitForPixel { isGray(it) && abs(it.red - RED_LUMINANCE) < PIXEL_TOLERANCE }
            source = server.url("failure")
            composeRule.waitUntil(TIMEOUT_MS) { server.failureServed.get() }
            // Lỗi HTTP vẫn giữ node và thao tác đổi mode; không crash hoặc kẹt request cũ.
            composeRule.onNodeWithTag(IMAGE_TAG).captureToImage()
            mode = EInkModePref.OFF
            source = server.url("retry")
            waitForPixel { it.red > STRONG_CHANNEL && it.green < WEAK_CHANNEL && it.blue < WEAK_CHANNEL }
            assertTrue(server.slowServed.get())
        }
    }

    private fun waitForPixel(predicate: (Color) -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            val map = composeRule.onNodeWithTag(IMAGE_TAG).captureToImage().toPixelMap()
            predicate(map[map.width / 2, map.height / 2])
        }
    }

    private fun isGray(color: Color) =
        abs(color.red - color.green) < PIXEL_TOLERANCE && abs(color.red - color.blue) < PIXEL_TOLERANCE

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0)
        private val running = AtomicBoolean(true)
        val failureServed = AtomicBoolean(false)
        val slowServed = AtomicBoolean(false)
        private val png = ByteArrayOutputStream().use { output ->
            val image = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(android.graphics.Color.RED)
                image.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            } finally { image.recycle() }
        }
        private val worker = thread(name = "eink-image-fixture", isDaemon = true) {
            while (running.get()) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                client.use { serve(it) }
            }
        }

        fun url(path: String) = "http://127.0.0.1:${socket.localPort}/$path"

        private fun serve(client: Socket) {
            client.soTimeout = SOCKET_TIMEOUT_MS
            try {
                val reader = client.getInputStream().bufferedReader()
                val request = reader.readLine().orEmpty()
                while (!reader.readLine().isNullOrEmpty()) { /* Đọc hết HTTP headers. */ }
                val failure = request.contains("/failure")
                val output = client.getOutputStream()
                if (failure) {
                    output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    output.flush()
                    failureServed.set(true)
                } else {
                    val slow = request.contains("/slow")
                    if (slow) Thread.sleep(RESPONSE_DELAY_MS)
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: no-store\r\nContent-Length: ${png.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    png.asList().chunked(CHUNK_BYTES).forEach { chunk ->
                        output.write(chunk.toByteArray())
                        output.flush()
                        if (slow) Thread.sleep(CHUNK_DELAY_MS)
                    }
                    if (slow) slowServed.set(true)
                }
            } catch (_: java.io.IOException) {
                // Painter bị dispose/cancel có thể chủ động đóng socket.
            }
        }

        override fun close() {
            running.set(false)
            socket.close()
            worker.interrupt()
            worker.join(SOCKET_TIMEOUT_MS.toLong())
            check(!worker.isAlive) { "Fixture worker chưa dừng" }
        }
    }

    private companion object {
        const val IMAGE_TAG = "eink-image"
        const val IMAGE_SIZE = 32
        val IMAGE_DP = 64.dp
        const val TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 2_000
        const val RESPONSE_DELAY_MS = 350L
        const val CHUNK_DELAY_MS = 20L
        const val CHUNK_BYTES = 16
        const val RED_LUMINANCE = 0.213f
        const val PIXEL_TOLERANCE = 0.02f
        const val STRONG_CHANNEL = 0.9f
        const val WEAK_CHANNEL = 0.1f
        const val SAMPLE_DIVISOR = 4
    }
}
