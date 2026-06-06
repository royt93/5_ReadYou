package com.mckimquyen.reader.infrastructure.ai

import android.util.Base64

/**
 * Cấu hình AI Summary (Gemini) bằng class constant.
 *
 * BẢO MẬT KEY:
 *  - Key KHÔNG lưu plain-text. Mỗi key được XOR với [PAD] rồi Base64 ([ENCODED_KEYS]),
 *    và chỉ giải mã trong bộ nhớ lúc chạy ([API_KEYS]). Nhờ vậy `strings`/grep trên APK
 *    KHÔNG đọc được key trực tiếp.
 *  - ⚠️ Đây chỉ là OBFUSCATION (làm khó), KHÔNG an toàn tuyệt đối: kẻ tấn công quyết tâm vẫn có
 *    thể decompile lấy PAD + thuật toán để khôi phục key. Muốn an toàn THẬT, key phải nằm ở
 *    backend (proxy) hoặc dùng Firebase AI Logic + App Check (key không ship trong app).
 *  - NÊN vào Google Cloud Console giới hạn từng key: Application restriction = Android app
 *    (package com.mckimquyen.reader + SHA-1) và API restriction = Generative Language API.
 *
 * FAILOVER: nhiều key thử lần lượt; key lỗi (sai key 400/403, hết quota 429) -> dùng key kế tiếp.
 *
 * CÁCH THÊM/ĐỔI KEY: encode key bằng cùng thuật toán (XOR với PAD rồi Base64) rồi dán chuỗi
 * Base64 vào [ENCODED_KEYS]. KHÔNG dán key thô vào đây.
 */
object GeminiConfig {

    /** Model Gemini dùng để tóm tắt. */
    const val MODEL = "gemini-2.5-flash"

    private const val PAD = "rHub!2026#ReadYou\$Gemini^Pad.v1"

    /** Các key đã được XOR(PAD)+Base64. Thử theo thứ tự, lỗi thì sang key kế tiếp. */
    private val ENCODED_KEYS: List<String> = listOf(
        "MwEPA3JLdFFVdGU6LxEYOk1wLywcBBQta30QIGc1QB07PBEUfHpn",
        "MwEPA3JLcn8OYREwEwwTFxtWJC0mXz1EKCAQU34dAD8BMDF7AgBj",
        "MwEPA3JLcmVSWj4OJFUxLT0QCFNcCBQlByQMLWk4aBQMQQkQYEVR",
        "MwEPA3JLcgcCajAuUyYAWipjEVIGRDcCKz4gCXE5Xj4FHwoWY19d",
        "MwEPA3JLcm13cRQDVAIBJB1vKBNZPAYfFzcyC2sEayAtDxQMbVhZ",
        "MwEPA3JLdEJTfDU2JyEIPjBuIiomATYqDB0WFlY+WQsDTAN0XQVV",
    )

    /** Danh sách key đã giải mã (chỉ tồn tại trong RAM lúc chạy). */
    val API_KEYS: List<String> by lazy {
        ENCODED_KEYS.map { deobfuscate(it) }.filter { it.isNotBlank() }
    }

    /** Có ít nhất 1 key hay không (dùng để quyết định ẩn/hiện nút ✨). */
    fun hasAnyKey(): Boolean = ENCODED_KEYS.any { it.isNotBlank() }

    private fun deobfuscate(encoded: String): String {
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val pad = PAD.toByteArray(Charsets.UTF_8)
            val out = ByteArray(data.size) { i ->
                (data[i].toInt() xor pad[i % pad.size].toInt()).toByte()
            }
            String(out, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
