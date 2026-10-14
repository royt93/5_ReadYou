package com.mckimquyen.reader.infrastructure.audio

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat

/** Voice search currently selects the single playable item exposed by each media session. */
internal open class PlayFromSearchCallback(
    private val playSingleItem: () -> Unit,
) : MediaSessionCompat.Callback() {
    override fun onPlay() = playSingleItem()

    override fun onPlayFromSearch(query: String?, extras: Bundle?) = playSingleItem()
}
