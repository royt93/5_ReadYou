package com.mckimquyen.reader.domain.sv

import kotlinx.coroutines.flow.Flow

/** Cổng nói: controller không phụ thuộc trực tiếp TtsManager nên test được bằng bản giả. */
interface ReadNewsSpeaker {
    /** Phát một lần khi bài đang đọc được đọc xong hoàn toàn. */
    val completions: Flow<Unit>

    fun play(text: String, title: String, subtitle: String)

    /** Đọc một câu thông báo ngắn (không có tin, hết tin). */
    fun speakNotice(text: String)

    fun stop()
}
