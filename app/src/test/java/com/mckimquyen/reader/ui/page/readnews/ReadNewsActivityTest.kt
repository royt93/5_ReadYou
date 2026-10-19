package com.mckimquyen.reader.ui.page.readnews

import android.content.Intent
import android.net.Uri
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.sv.ReadNewsOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ReadNewsActivityTest {

    private fun view(uri: String?) = Intent(Intent.ACTION_VIEW).apply { uri?.let { data = Uri.parse(it) } }

    @Test
    fun isReadNewsIntent_acceptsOnlyTheDeepLink() {
        assertTrue(ReadNewsActivity.isReadNewsIntent(view("rsscathub://read-news")))
    }

    @Test
    fun isReadNewsIntent_rejectsOtherIntents() {
        assertFalse(ReadNewsActivity.isReadNewsIntent(null))
        assertFalse(ReadNewsActivity.isReadNewsIntent(view(null)))
        assertFalse(ReadNewsActivity.isReadNewsIntent(view("https://read-news")))
        assertFalse(ReadNewsActivity.isReadNewsIntent(view("rsscathub://other")))
        assertFalse(ReadNewsActivity.isReadNewsIntent(Intent(Intent.ACTION_MAIN).apply { data = Uri.parse("rsscathub://read-news") }))
    }

    @Test
    fun messageFor_mapsOutcomesToUserMessages() {
        assertEquals(R.string.read_news_no_unread, ReadNewsActivity.messageFor(ReadNewsOutcome.NO_UNREAD))
        assertEquals(R.string.read_news_error, ReadNewsActivity.messageFor(ReadNewsOutcome.NO_READABLE_TEXT))
        assertEquals(R.string.read_news_error, ReadNewsActivity.messageFor(ReadNewsOutcome.ERROR))
        assertNull(ReadNewsActivity.messageFor(ReadNewsOutcome.STARTED))
        assertNull(ReadNewsActivity.messageFor(ReadNewsOutcome.QUEUE_FINISHED))
    }
}
