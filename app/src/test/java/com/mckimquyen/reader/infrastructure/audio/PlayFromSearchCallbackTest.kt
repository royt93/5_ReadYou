package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayFromSearchCallbackTest {
    @Test fun voiceSearchWithAnyQuery_startsTheSinglePlayableContent() {
        var plays = 0
        val callback = PlayFromSearchCallback(playSingleItem = { plays++ })
        callback.onPlayFromSearch("commutecast", null)
        callback.onPlayFromSearch("", null)
        callback.onPlayFromSearch(null, null)
        assertEquals(3, plays)
    }

    @Test fun plainPlayStillStartsPlayback() {
        var plays = 0
        PlayFromSearchCallback(playSingleItem = { plays++ }).onPlay()
        assertEquals(1, plays)
    }
}
