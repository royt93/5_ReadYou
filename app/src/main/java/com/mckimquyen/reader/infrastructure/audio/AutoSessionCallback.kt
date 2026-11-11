package com.mckimquyen.reader.infrastructure.audio

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat

/** The car's MediaSession callback: transport goes to the active side, voice search keeps its CommuteCast behaviour. */
internal class AutoSessionCallback(
    private val router: AutoControlRouter,
    private val playLatestCommute: () -> Unit,
    private val playMediaId: (String?) -> Unit,
    private val onServiceStop: () -> Unit,
) : MediaSessionCompat.Callback() {
    override fun onPlay() = router.play()
    override fun onPause() = router.pause()
    override fun onStop() {
        router.stop()
        onServiceStop()
    }
    override fun onSkipToNext() = router.next()
    override fun onSkipToPrevious() = router.previous()
    override fun onFastForward() = router.fastForward()
    override fun onRewind() = router.rewind()
    override fun onSeekTo(pos: Long) = router.seekToMs(pos)

    // An ambiguous spoken request must not pick a podcast for the user; it keeps today's CommuteCast behaviour.
    override fun onPlayFromSearch(query: String?, extras: Bundle?) = playLatestCommute()

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = playMediaId(mediaId)
}
