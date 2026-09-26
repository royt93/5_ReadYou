package com.mckimquyen.reader.infrastructure.audio.ambient

/**
 * Các lỗi phát âm thanh nền có thể xảy ra trong [ZenAudioManager.play].
 * Được đưa ra UI để thay thế trạng thái "đang phát" khi thực tế âm thanh không được bật.
 */
enum class ZenPlaybackError {
    AUDIO_FOCUS_DENIED,
    AUDIO_TRACK_FAILED,
    PLAYBACK_INTERRUPTED,
}
