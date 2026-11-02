package com.mckimquyen.reader.domain.mirror

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MirrorSnapshotJsonTest {
    private fun parse(s: MirrorSnapshot) = JSONObject(s.toJson())

    @Test fun roundTripsEveryField() {
        val json = parse(MirrorSnapshot("Title", "BBC", 123L, listOf("a", "b"), true, 9L))
        assertEquals("Title", json.getString("title"))
        assertEquals("BBC", json.getString("source"))
        assertEquals(123L, json.getLong("date"))
        assertEquals(listOf("a", "b"), (0 until json.getJSONArray("paragraphs").length()).map { json.getJSONArray("paragraphs").getString(it) })
        assertTrue(json.getBoolean("truncated"))
        assertEquals(9L, json.getLong("version"))
    }

    @Test fun escapesQuotesBackslashesNewlinesAndAngleBrackets() {
        val nasty = "\"quoted\" \\ back \n line </script><script>alert(1)</script>    "
        val raw = MirrorSnapshot(nasty, nasty, 0, listOf(nasty), false, 1).toJson()
        assertEquals(nasty, JSONObject(raw).getString("title"))
        assertFalse("raw JSON must not close a script element", raw.contains("</script>"))
        assertFalse(raw.contains(' '))
        assertFalse(raw.contains(' '))
    }

    @Test fun emptyJsonConstantIsValid() {
        val json = JSONObject(MirrorSnapshot.EMPTY_JSON)
        assertEquals(0, json.getJSONArray("paragraphs").length())
    }
}
