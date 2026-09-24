package com.mckimquyen.reader.infrastructure.android

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakeLockManagerTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var wakeLockManager: WakeLockManager

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        powerManager = mockk(relaxed = true)
        wakeLock = mockk(relaxed = true)

        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, any()) } returns wakeLock

        wakeLockManager = WakeLockManager(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `acquire should request wakeLock with timeout when not held`() {
        every { wakeLock.isHeld } returns false

        wakeLockManager.acquire(10_000L)

        verify(exactly = 1) { wakeLock.acquire(10_000L) }
        verify(exactly = 1) { wakeLock.setReferenceCounted(false) }
    }

    @Test
    fun `acquire should not re-acquire when already held`() {
        every { wakeLock.isHeld } returns true

        wakeLockManager.acquire(10_000L)

        verify(exactly = 0) { wakeLock.acquire(any()) }
    }

    @Test
    fun `release should release wakeLock when held`() {
        // First acquire to initialize wakeLock field
        every { wakeLock.isHeld } returns false
        wakeLockManager.acquire()

        every { wakeLock.isHeld } returns true
        wakeLockManager.release()

        verify(exactly = 1) { wakeLock.release() }
    }

    @Test
    fun `release should do nothing when not held`() {
        every { wakeLock.isHeld } returns false

        wakeLockManager.release()

        verify(exactly = 0) { wakeLock.release() }
    }

    @Test
    fun `isHeld reflects underlying wakeLock status`() {
        every { wakeLock.isHeld } returns false
        wakeLockManager.acquire()
        assertFalse(wakeLockManager.isHeld())

        every { wakeLock.isHeld } returns true
        assertTrue(wakeLockManager.isHeld())
    }

    @Test
    fun `acquire handles null powerManager gracefully without crash`() {
        val noPowerContext = mockk<Context>(relaxed = true)
        every { noPowerContext.getSystemService(Context.POWER_SERVICE) } returns null

        val manager = WakeLockManager(noPowerContext)
        manager.acquire()
        assertFalse(manager.isHeld())
        manager.release()
    }
}
