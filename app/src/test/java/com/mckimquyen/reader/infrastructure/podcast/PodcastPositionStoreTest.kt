package com.mckimquyen.reader.infrastructure.podcast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PodcastPositionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: PodcastPositionStore

    @Before fun setUp() {
        context.getSharedPreferences(PodcastPositionStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = PodcastPositionStore(context)
    }

    @Test fun saveAndLoad() {
        store.save("1\$abc", 120, 600)
        assertEquals(120, store.load("1\$abc"))
    }

    @Test fun unknownIsZero() = assertEquals(0, store.load("none"))

    @Test fun finishedTailResetsToZero() {
        store.save("a", 600 - PodcastPositionStore.COMPLETED_TAIL_SEC, 600)
        assertEquals(0, store.load("a"))
        store.save("b", 700, 600)
        assertEquals(0, store.load("b"))
    }

    @Test fun justBeforeTailIsKept() {
        store.save("a", 600 - PodcastPositionStore.COMPLETED_TAIL_SEC - 1, 600)
        assertEquals(584, store.load("a"))
    }

    @Test fun unknownDurationKeepsPosition() {
        store.save("a", 5000, null)
        assertEquals(5000, store.load("a"))
    }

    @Test fun negativeStoredAsZero() {
        store.save("a", -9, 600)
        assertEquals(0, store.load("a"))
    }

    @Test fun overwriteKeepsSingleEntry() {
        store.save("a", 10, 600)
        store.save("a", 20, 600)
        assertEquals(20, store.load("a"))
    }

    @Test fun capDropsOldest() {
        repeat(PodcastPositionStore.MAX_REMEMBERED_POSITIONS + 1) { store.save("id$it", it + 1, 10_000) }
        assertEquals(0, store.load("id0"))
        assertEquals(2, store.load("id1"))
        assertEquals(51, store.load("id50"))
    }

    @Test fun resavingRefreshesRecency() {
        repeat(PodcastPositionStore.MAX_REMEMBERED_POSITIONS) { store.save("id$it", it + 1, 10_000) }
        store.save("id0", 99, 10_000)
        store.save("new", 7, 10_000)
        assertEquals(99, store.load("id0"))
        assertEquals(0, store.load("id1"))
    }

    @Test fun persistsAcrossInstances() {
        store.save("a", 42, 600)
        assertEquals(42, PodcastPositionStore(context).load("a"))
    }

    @Test fun idsContainingDelimitersRoundTrip() {
        val id = "1\$tag:google.com,2005:reader/item/000123"
        store.save(id, 321, 5_000)
        store.save("plain", 5, 5_000)
        assertEquals(321, store.load(id))
        assertEquals(5, store.load("plain"))
        assertEquals(321, PodcastPositionStore(context).load(id))
    }

    @Test fun malformedJsonIsIgnoredWithoutCrashing() {
        context.getSharedPreferences(PodcastPositionStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("entries_json", "{not json").commit()
        assertEquals(0, store.load("any"))
        store.save("a", 9, 600)
        assertEquals(9, store.load("a"))
    }

    @Test fun legacyFormatIsStillReadAndMigratedOnNextSave() {
        val prefs = context.getSharedPreferences(PodcastPositionStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("entries", "garbage,,:5,ok:7,bad:x").commit()
        assertEquals(7, store.load("ok"))
        assertEquals(0, store.load("bad"))
        store.save("new", 3, 600)
        assertEquals(7, store.load("ok"))
        assertEquals(3, store.load("new"))
        assertEquals(false, prefs.contains("entries"))
    }
}
