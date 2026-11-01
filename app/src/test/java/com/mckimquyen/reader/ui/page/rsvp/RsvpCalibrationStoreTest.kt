package com.mckimquyen.reader.ui.page.rsvp

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import com.mckimquyen.reader.ui.ext.dataStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RsvpCalibrationStoreTest {
    private lateinit var context: Context
    private lateinit var store: RsvpCalibrationStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        store = DataStoreRsvpCalibrationStore(context)
    }

    @Test
    fun uncalibrated_returnsNull() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun write_and_read_roundtripsCorrectly() = runBlocking {
        store.write(wpm = 450, punctuationDelayScale = 0.85f)
        val read = store.read()
        assertEquals(450, read?.wpm)
        assertEquals(0.85f, read?.punctuationDelayScale ?: 0f, 0.0001f)
    }

    @Test
    fun overwritingWithNewValues_persistsTheNewValues() = runBlocking {
        store.write(wpm = 300, punctuationDelayScale = 1.25f)
        store.write(wpm = 600, punctuationDelayScale = 0.85f)
        val read = store.read()
        assertEquals(600, read?.wpm)
        assertEquals(0.85f, read?.punctuationDelayScale ?: 0f, 0.0001f)
    }
}
