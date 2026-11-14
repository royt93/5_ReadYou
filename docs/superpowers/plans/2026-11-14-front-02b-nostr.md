# FRONT-02b — Theo dõi Nostr bằng `npub` công khai Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Người dùng dán `npub1…` vào ô Thêm nguồn; app kiểm chữ ký Nostr, xem trước tối đa 20 ghi chú mới nhất từ hai relay cố định và đồng bộ như nguồn thường.

**Architecture:** Mã Nostr thuần Kotlin trong `infrastructure/social/nostr/`: Bech32/NIP-19, xác minh BIP-340 bằng `BigInteger`, xác minh event, WebSocket relay, ánh xạ bài. Nối qua `Feed.sourceType = NOSTR`, nhánh đồng bộ của `LocalRssSv` và `SubscribeViewModel.search()` trước `formatUrl()`; chặn mọi tải web ngoài ý muốn.

**Tech Stack:** Kotlin 2.1, OkHttp 5.0.0-alpha.11 và MockWebServer3 5.0.0-alpha.11 (đã có), `org.json`, JUnit4, MockK, Robolectric. **Không dependency mới.**

## Global Constraints

- Chỉ nhánh `dev`; không worktree, push, PR; không commit `.idea`.
- Spec: `docs/superpowers/specs/2026-11-14-front-02b-nostr-design.md` (commit `a76ba090`). Task gốc: `doc/task/todo/FRONT-02_decentralized-web3-social.md`.
- Chỉ đọc `npub` công khai; `nsec` không nhận/không gửi/không log; không đăng nhập, không giữ khóa.
- Hai relay cố định: `wss://nos.lol`, `wss://relay.primal.net`; lần lượt thử, timeout 8 giây mỗi relay; đọc tối đa 50 event, mỗi tin tối đa 64 KiB, giữ tối đa 20 bài.
- Xác minh event kind 1: id SHA-256 chuẩn Nostr, Schnorr BIP-340 đúng, pubkey đúng npub; bỏ trả lời (tag `e`), bỏ trùng; link bài `https://njump.me/note1…`, chỉ mở khi người dùng bấm.
- Không dependency, Room, DataStore, manifest, chuỗi mới, Gemini, ad/IAP/tiền. Room giữ v22; `app/src/main/res` không đổi.
- Hằng có tên, không magic values; không `!!`/`late` nếu không có lý do; luôn đóng WebSocket khi EOSE/lỗi/timeout/cancel.
- TDD: test đỏ vì đúng lý do; chỉ đọc XML sau khi `.exit` tồn tại; xanh; mutation có backup + `cmp`; commit nhỏ sau khi exit=0.
- Gradle chạy nền, log ra file, chờ `.exit`/`ALLDONE`; thông báo lệnh nền `completed` chỉ nghĩa là launcher xong.
- Mutation dùng `/tmp/s01_mut.sh`; chạy cả batch bằng script nền, không sửa file bị đột biến đến `ALLDONE`; cuối batch phải `cmp` khôi phục.
- Shell zsh: không `echo ===`, không `grep --include`, quote pattern; sửa file bằng Python thay `sed -i`; Python 3.9 với tiếng Việt: `# -*- coding: utf-8 -*-`, file `open(..., encoding="utf-8")`; `org.json`/`android.util.Log` JVM test cần Robolectric.
- Thiết bị chỉ Tecno KJ7 `115333744A005844`; không Pixel. Trước mọi device action: cổng adb chỉ đọc, foreground/khóa/instrumentation; dừng nếu app lạ, màn hình khóa hoặc biometric. Sao lưu/restore byte-exact bằng `/tmp/o03p-backup.py`, `/tmp/o03p-restore.py`; chỉ `am instrument`, không `connected*`, không chạm màn hình/tọa độ.
- Log không chứa `npub`, nội dung, relay URL hay event JSON. Mạng thật chỉ qua helper tạm, xóa sau, không commit.

---

## Cấu trúc file

| Tệp | Việc | Task |
|---|---|---|
| `infrastructure/social/nostr/Bech32.kt`, `NostrKeys.kt` | checksum Bech32, NIP-19 `npub`/`note1` | 1 |
| `infrastructure/social/nostr/Bip340.kt` | xác minh Schnorr secp256k1 bằng `BigInteger` | 2 |
| `infrastructure/social/nostr/NostrEvent.kt` | parse và xác minh event/id/signature/pubkey/kind | 3 |
| `infrastructure/social/nostr/NostrRelayClient.kt` | OkHttp WebSocket, relay fallback, giới hạn và đóng socket | 4 |
| `infrastructure/social/nostr/NostrFeedMapper.kt` | event hợp lệ thành Article | 5 |
| `domain/model/feed/Feed.kt`, `domain/sv/LocalRssSv.kt` và mọi đường network phù hợp | source type NOSTR; đồng bộ, chặn tải web | 6 |
| `ui/page/home/feed/subs/SubscribeViewModel.kt` | nhận npub trước `formatUrl`, preview/confirm | 7 |
| `doc/task/done/FRONT-02b_nostr_DONE.md`, `doc/feature.md`, task gốc | báo cáo và cập nhật trạng thái | 8 |

**Thứ tự:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Mỗi task kết thúc bằng test xanh và commit riêng.

---

### Task 1: `Bech32` và `NostrKeys` (npub, note1; nsec bị từ chối)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt`

**Interfaces:**
- Produces: `internal object Bech32 { fun decode(text: String): Decoded?; fun encode(hrp: String, payload: ByteArray): String; class Decoded(val hrp: String, val payload: ByteArray) }`.
- Produces: `object NostrKeys { fun npubToHex(input: String): String?; fun hexToNpub(hex: String): String?; fun noteId(eventIdHex: String): String?; fun isSecretKey(input: String): Boolean }`.
- `npubToHex` chỉ nhận hrp `npub` với đúng 32 byte; trả hex thường 64 ký tự hoặc `null`. `isSecretKey` đúng khi chuỗi (cắt khoảng trắng) bắt đầu `nsec1` không phân biệt hoa thường: dùng ở Task 7 để **không** đẩy khóa bí mật vào bất kỳ đường nào.

Ví dụ đã kiểm bằng nguyên mẫu Python độc lập (cùng thuật toán), chạy đủ bộ vector BIP-173: `npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg` giải ra `7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e`.

- [ ] **Step 1: Viết test đỏ** (`NostrKeysTest.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrKeysTest {
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val hex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

    // ---- Bech32 (BIP-173 test vectors)
    @Test fun bech32_validVectors_decode() {
        listOf("A12UEL5L", "a12uel5l", "abcdef1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw", "?1ezyfcl")
            .forEach { assertNotNull(it, Bech32.decode(it)) }
    }

    @Test fun bech32_invalidVectors_areRejected() {
        listOf("A12uEL5L", "pzry9x0s0muk", "1pzry9x0s0muk", "x1b4n0q5v", "li1dgmt3", "de1lg7wtÿ", "a12uel5m", "", "1")
            .forEach { assertNull(it, Bech32.decode(it)) }
    }

    @Test fun bech32_isCaseInsensitiveButNotMixed() {
        assertNotNull(Bech32.decode(npub.uppercase()))
        assertNull(Bech32.decode(npub.take(10).uppercase() + npub.drop(10)))
    }

    @Test fun bech32_oneChangedCharacter_failsTheChecksum() {
        val broken = npub.dropLast(1) + (if (npub.last() == 'g') 'h' else 'g')
        assertNull(Bech32.decode(broken))
    }

    @Test fun bech32_encodeThenDecode_roundTrips() {
        val payload = ByteArray(32) { it.toByte() }
        val text = Bech32.encode("npub", payload)
        val back = Bech32.decode(text)
        assertEquals("npub", back?.hrp)
        assertEquals(payload.toList(), back?.payload?.toList())
    }

    // ---- NIP-19 npub
    @Test fun npubToHex_decodesTheKnownExample() = assertEquals(hex, NostrKeys.npubToHex(npub))

    @Test fun npubToHex_trimsSurroundingSpaces() = assertEquals(hex, NostrKeys.npubToHex("  $npub \n"))

    @Test fun npubToHex_acceptsAllUppercase() = assertEquals(hex, NostrKeys.npubToHex(npub.uppercase()))

    @Test fun npubToHex_rejectsOtherPrefixes() {
        assertNull(NostrKeys.npubToHex(nsec))
        assertNull(NostrKeys.npubToHex(Bech32.encode("note", ByteArray(32))))
        assertNull(NostrKeys.npubToHex(Bech32.encode("nprofile", ByteArray(32))))
    }

    @Test fun npubToHex_rejectsWrongLength() {
        assertNull(NostrKeys.npubToHex(Bech32.encode("npub", ByteArray(31))))
        assertNull(NostrKeys.npubToHex(Bech32.encode("npub", ByteArray(33))))
    }

    @Test fun npubToHex_rejectsJunk() {
        listOf("", "   ", "npub1", "https://example.com", "@bsky.app", "npub1 abc", "hello").forEach { assertNull(it, NostrKeys.npubToHex(it)) }
    }

    @Test fun hexToNpub_isTheInverse() {
        assertEquals(npub, NostrKeys.hexToNpub(hex))
        assertNull(NostrKeys.hexToNpub("zz"))
        assertNull(NostrKeys.hexToNpub(hex.dropLast(2)))
    }

    // ---- NIP-19 note id
    @Test fun noteId_encodesAKnownEventId() {
        assertEquals(
            "note1cqneqau89xzdllkft58klqecyrjdte7cgv5svy9u92yc9lxewdlqw0n65r",
            NostrKeys.noteId("c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e"),
        )
    }

    @Test fun noteId_rejectsAnInvalidId() {
        assertNull(NostrKeys.noteId(""))
        assertNull(NostrKeys.noteId("abc"))
        assertNull(NostrKeys.noteId("g".repeat(64)))
    }

    // ---- secret keys
    @Test fun isSecretKey_flagsNsecInAnyCase() {
        assertTrue(NostrKeys.isSecretKey(nsec))
        assertTrue(NostrKeys.isSecretKey("  " + nsec.uppercase()))
        assertTrue(NostrKeys.isSecretKey("nsec1abc"))
    }

    @Test fun isSecretKey_isFalseForPublicKeysAndText() {
        assertFalse(NostrKeys.isSecretKey(npub))
        assertFalse(NostrKeys.isSecretKey(""))
        assertFalse(NostrKeys.isSecretKey("nsecurity"))
    }
}
```

- [ ] **Step 2: Chạy đỏ** (nền, ra tệp, chờ `.exit`):
`rm -f /tmp/f3-t1.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrKeysTest' > /tmp/f3-t1-red.log 2>&1; echo $? > /tmp/f3-t1.exit) &`
Đỏ đúng lý do: lỗi biên dịch `Unresolved reference: Bech32` / `NostrKeys` (đọc từ log sau khi `.exit` tồn tại; chưa có XML kết quả vì không biên dịch được).

- [ ] **Step 3: `Bech32.kt`**

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import java.util.Locale

/** Bech32 (BIP-173) decode/encode, enough for NIP-19. Pure Kotlin, no Android. */
internal object Bech32 {
    class Decoded(val hrp: String, val payload: ByteArray)

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val SEPARATOR = '1'
    private const val CHECKSUM_LENGTH = 6
    private const val MAX_LENGTH = 90
    private const val MIN_PRINTABLE = 33
    private const val MAX_PRINTABLE = 126
    private const val BITS_PER_CHAR = 5
    private const val BITS_PER_BYTE = 8
    private const val CHECKSUM_SHIFT = 25
    private const val CHECKSUM_MASK = 0x1ffffff
    private const val CHAR_MASK = 31
    private const val CHECKSUM_CONSTANT = 1
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun decode(text: String): Decoded? {
        if (text.length > MAX_LENGTH) return null
        if (text.any { it.code < MIN_PRINTABLE || it.code > MAX_PRINTABLE }) return null
        if (text != text.lowercase(Locale.ROOT) && text != text.uppercase(Locale.ROOT)) return null
        val lower = text.lowercase(Locale.ROOT)
        val sep = lower.lastIndexOf(SEPARATOR)
        if (sep < 1 || sep + 1 + CHECKSUM_LENGTH > lower.length) return null
        val hrp = lower.substring(0, sep)
        val data = IntArray(lower.length - sep - 1)
        for (i in data.indices) {
            val v = CHARSET.indexOf(lower[sep + 1 + i])
            if (v < 0) return null
            data[i] = v
        }
        if (polymod(expandHrp(hrp) + data) != CHECKSUM_CONSTANT) return null
        val bytes = convertBits(data.copyOf(data.size - CHECKSUM_LENGTH), BITS_PER_CHAR, BITS_PER_BYTE, pad = false) ?: return null
        return Decoded(hrp, ByteArray(bytes.size) { bytes[it].toByte() })
    }

    fun encode(hrp: String, payload: ByteArray): String {
        val values = convertBits(IntArray(payload.size) { payload[it].toInt() and 0xff }, BITS_PER_BYTE, BITS_PER_CHAR, pad = true)
            ?: error("padding conversion cannot fail")
        val mod = polymod(expandHrp(hrp) + values + IntArray(CHECKSUM_LENGTH)) xor CHECKSUM_CONSTANT
        val checksum = IntArray(CHECKSUM_LENGTH) { (mod shr (BITS_PER_CHAR * (CHECKSUM_LENGTH - 1 - it))) and CHAR_MASK }
        return buildString {
            append(hrp).append(SEPARATOR)
            (values + checksum).forEach { append(CHARSET[it]) }
        }
    }

    private fun expandHrp(hrp: String): IntArray =
        IntArray(hrp.length * 2 + 1).also { out ->
            hrp.forEachIndexed { i, c ->
                out[i] = c.code shr BITS_PER_CHAR
                out[hrp.length + 1 + i] = c.code and CHAR_MASK
            }
        }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr CHECKSUM_SHIFT
            chk = ((chk and CHECKSUM_MASK) shl BITS_PER_CHAR) xor v
            for (i in GENERATOR.indices) if ((top shr i) and 1 == 1) chk = chk xor GENERATOR[i]
        }
        return chk
    }

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val maxValue = (1 shl to) - 1
        val out = ArrayList<Int>()
        for (v in data) {
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc shr bits) and maxValue)
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxValue)
        } else if (bits >= from || ((acc shl (to - bits)) and maxValue) != 0) {
            return null
        }
        return out.toIntArray()
    }
}
```

- [ ] **Step 4: `NostrKeys.kt`**

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import java.util.Locale

/** NIP-19 helpers: public keys (`npub`) and note ids (`note1`). Never handles a secret key beyond flagging it. */
object NostrKeys {
    private const val NPUB_HRP = "npub"
    private const val NOTE_HRP = "note"
    private const val SECRET_PREFIX = "nsec1"
    private const val KEY_BYTES = 32
    private const val HEX_LENGTH = KEY_BYTES * 2
    private const val HEX_RADIX = 16

    fun npubToHex(input: String): String? {
        val decoded = Bech32.decode(input.trim()) ?: return null
        if (decoded.hrp != NPUB_HRP || decoded.payload.size != KEY_BYTES) return null
        return decoded.payload.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun hexToNpub(hex: String): String? = fromHex(hex)?.let { Bech32.encode(NPUB_HRP, it) }

    fun noteId(eventIdHex: String): String? = fromHex(eventIdHex)?.let { Bech32.encode(NOTE_HRP, it) }

    /** True when the text looks like a Nostr SECRET key, which must never be used, sent or logged. */
    fun isSecretKey(input: String): Boolean = input.trim().lowercase(Locale.ROOT).startsWith(SECRET_PREFIX)

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length != HEX_LENGTH) return null
        return ByteArray(KEY_BYTES) { i ->
            hex.substring(i * 2, i * 2 + 2).toIntOrNull(HEX_RADIX)?.toByte() ?: return null
        }
    }
}
```

- [ ] **Step 5: Chạy xanh** (cùng lệnh, kỳ vọng `exit=0`, 16 test, 0 lỗi/0 bỏ qua; đọc `tests/failures/errors` từ XML mới).

- [ ] **Step 6: Mutation** (script nền `/tmp/f3_t1_batch.sh`, mẫu như `/tmp/f2_t1_batch.sh`; tệp `Bech32.kt` rồi `NostrKeys.kt`, lớp `com.mckimquyen.reader.infrastructure.social.nostr.NostrKeysTest`; in `ALLDONE`, `cmp` cuối). Đột biến phải bị bắt: bỏ nhánh `text != lowercase && != uppercase` (lẫn hoa/thường); `CHECKSUM_CONSTANT` 1→0; `sep + 1 + CHECKSUM_LENGTH` thành `sep + 1`; `!= NPUB_HRP` thành `== NPUB_HRP`; `!= KEY_BYTES` bỏ; `bits >= from ||` bỏ (padding nhận bậy); `CHARSET` đổi một ký tự; `lowercase()` bỏ ở `isSecretKey`; `startsWith` thành `contains`; `MAX_LENGTH` 90→200 (chuỗi 91 ký tự phải bị từ chối: **thêm test** `bech32_overLongText_isRejected` nếu sống sót). Đột biến sống sót nghĩa là test yếu: thêm test, không bỏ đột biến; guard thừa (tương đương) thì xóa guard.

- [ ] **Step 7: Commit** sau khi kiểm `exit=0`, `git status` chỉ có hai tệp mã và một tệp test:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt && git commit -m "feat(front-02b): add the pure Bech32 and NIP-19 rules that read an npub and name a note"`

---

### Task 2: `Bip340` kiểm chữ ký Schnorr bằng `BigInteger`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bip340.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/Bip340Test.kt`

**Interfaces:**
- Produces: `object Bip340 { fun verify(pubkeyHex: String, messageHex: String, signatureHex: String): Boolean }`. Không bao giờ ném lỗi: đầu vào sai (không phải hex, sai độ dài, điểm không nằm trên đường cong) trả `false`. **Chỉ kiểm tra; không có hàm ký, không giữ khóa bí mật.** `messageHex` có thể rỗng (BIP-340 cho phép thông điệp mọi độ dài).
- Nguồn vector: bộ chính thức `bitcoin/bips/bip-0340/test-vectors.csv` (19 dòng, tải 2026-11-14). Nguyên mẫu Python độc lập (cùng thuật toán, tọa độ Jacobian) đã chạy đủ 19 vector: **0 sai khác**; và đã kiểm chữ ký + id của **120 ghi chú thật** lấy từ `nos.lol` và `relay.primal.net` (120/120 đúng).

- [ ] **Step 1: Viết test đỏ** (`Bip340Test.kt`; các dòng `Vec(...)` chép nguyên từ CSV chính thức, đừng sửa tay)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip340Test {
    private class Vec(val index: Int, val pubkey: String, val message: String, val sig: String, val valid: Boolean, val note: String)

    private val vectors = listOf(
        Vec(0, "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", "0000000000000000000000000000000000000000000000000000000000000000", "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0", true, ""),
        Vec(1, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a", true, ""),
        Vec(2, "dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8", "7e2d58d8b3bcdf1abadec7829054f90dda9805aab56c77333024b9d0a508b75c", "5831aaeed7b44bb74e5eab94ba9d4294c49bcf2a60728d8b4c200f50dd313c1bab745879a5ad954a72c45a91c3a51d3c7adea98d82f8481e0e1e03674a6f3fb7", true, ""),
        Vec(3, "25d1dff95105f5253c4022f628a996ad3a0d95fbf21d468a1b33f8c160d8f517", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "7eb0509757e246f19449885651611cb965ecc1a187dd51b64fda1edc9637d5ec97582b9cb13db3933705b32ba982af5af25fd78881ebb32771fc5922efc66ea3", true, "test fails if msg is reduced modulo p or n"),
        Vec(4, "d69c3509bb99e412e68b0fe8544e72837dfa30746d8be2aa65975f29d22dc7b9", "4df3c3f68fcc83b27e9d42c90431a72499f17875c81a599b566c9889b9696703", "00000000000000000000003b78ce563f89a0ed9414f5aa28ad0d96d6795f9c6376afb1548af603b3eb45c9f8207dee1060cb71c04e80f593060b07d28308d7f4", true, ""),
        Vec(5, "eefdea4cdb677750a420fee807eacf21eb9898ae79b9768766e4faa04a2d4a34", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e17776969e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "public key not on the curve"),
        Vec(6, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975563cc27944640ac607cd107ae10923d9ef7a73c643e166be5ebeafa34b1ac553e2", false, "has_even_y(R) is false"),
        Vec(7, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "1fa62e331edbc21c394792d2ab1100a7b432b013df3f6ff4f99fcb33e0e1515f28890b3edb6e7189b630448b515ce4f8622a954cfe545735aaea5134fccdb2bd", false, "negated message"),
        Vec(8, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e177769961764b3aa9b2ffcb6ef947b6887a226e8d7c93e00c5ed0c1834ff0d0c2e6da6", false, "negated s value"),
        Vec(9, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "0000000000000000000000000000000000000000000000000000000000000000123dda8328af9c23a94c1feecfd123ba4fb73476f0d594dcb65c6425bd186051", false, "sG - eP is infinite. Test fails in single verification if has_even_y(i"),
        Vec(10, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "00000000000000000000000000000000000000000000000000000000000000017615fbaf5ae28864013c099742deadb4dba87f11ac6754f93780d5a1837cf197", false, "sG - eP is infinite. Test fails in single verification if has_even_y(i"),
        Vec(11, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "4a298dacae57395a15d0795ddbfd1dcb564da82b0f269bc70a74f8220429ba1d69e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "sig[0:32] is not an X coordinate on the curve"),
        Vec(12, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f69e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "sig[0:32] is equal to field size"),
        Vec(13, "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e177769fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", false, "sig[32:64] is equal to curve order"),
        Vec(14, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30", "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89", "6cff5c3ba86c69ea4b7376f31a9bcb4f74c1976089b2d9963da2e5543e17776969e89b4c5564d00349106b8497785dd7d1d713a8ae82b32fa79d5f7fc407d39b", false, "public key is not a valid X coordinate because it exceeds the field si"),
        Vec(15, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "", "71535db165ecd9fbbc046e5ffaea61186bb6ad436732fccc25291a55895464cf6069ce26bf03466228f19a3a62db8a649f2d560fac652827d1af0574e427ab63", true, "message of size 0 (added 2022-12)"),
        Vec(16, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "11", "08a20a0afef64124649232e0693c583ab1b9934ae63b4c3511f3ae1134c6a303ea3173bfea6683bd101fa5aa5dbc1996fe7cacfc5a577d33ec14564cec2bacbf", true, "message of size 1 (added 2022-12)"),
        Vec(17, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "0102030405060708090a0b0c0d0e0f1011", "5130f39a4059b43bc7cac09a19ece52b5d8699d1a71e3c52da9afdb6b50ac370c4a482b77bf960f8681540e25b6771ece1e5a37fd80e5a51897c5566a97ea5a5", true, "message of size 17 (added 2022-12)"),
        Vec(18, "778caa53b4393ac467774d09497a87224bf9fab6f6e68b23086497324d6fd117", "99999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999", "403b12b0d8555a344175ea7ec746566303321e5dbfa8be6f091635163eca79a8585ed3e3170807e7c03b720fc54c7b23897fcba0e9d0b4a06894cfd249f22367", true, "message of size 100 (added 2022-12)"),
    )

    @Test fun officialVectors_giveTheExpectedAnswer() {
        vectors.forEach {
            assertEquals("vector ${it.index} ${it.note}", it.valid, Bip340.verify(it.pubkey, it.message, it.sig))
        }
    }

    private val good = vectors[1]

    @Test fun aSingleFlippedBit_inTheSignature_isRejected() {
        for (i in listOf(0, 31, 32, 63)) {
            val bytes = good.sig.chunked(2).toMutableList()
            bytes[i] = "%02x".format(bytes[i].toInt(16) xor 1)
            assertFalse("byte $i", Bip340.verify(good.pubkey, good.message, bytes.joinToString("")))
        }
    }

    @Test fun aSingleFlippedBit_inTheMessage_isRejected() {
        val flipped = "%02x".format(good.message.take(2).toInt(16) xor 1) + good.message.drop(2)
        assertFalse(Bip340.verify(good.pubkey, flipped, good.sig))
    }

    @Test fun aDifferentPublicKey_isRejected() = assertFalse(Bip340.verify(vectors[2].pubkey, good.message, good.sig))

    @Test fun uppercaseHex_isAccepted() =
        assertTrue(Bip340.verify(good.pubkey.uppercase(), good.message.uppercase(), good.sig.uppercase()))

    @Test fun malformedInput_returnsFalseInsteadOfThrowing() {
        assertFalse(Bip340.verify("", good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey.dropLast(2), good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey, good.message, good.sig.dropLast(2)))
        assertFalse(Bip340.verify(good.pubkey, good.message, good.sig + "00"))
        assertFalse(Bip340.verify("zz".repeat(32), good.message, good.sig))
        assertFalse(Bip340.verify(good.pubkey, "abc", good.sig))
        assertFalse(Bip340.verify(good.pubkey, good.message, "g".repeat(128)))
    }

    // A note signed by the independent Python signer (see Task 3 fixtures): its id is the signed message.
    @Test fun aSignedNostrEventId_verifies() = assertTrue(
        Bip340.verify(
            "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659",
            "c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e",
            "4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623",
        ),
    )
}
```

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f3-t2.exit; (./gradlew :app:testDevDebugUnitTest --tests '*Bip340Test' > /tmp/f3-t2-red.log 2>&1; echo $? > /tmp/f3-t2.exit) &`. Đỏ đúng lý do: `Unresolved reference: Bip340`.

- [ ] **Step 3: Minimal implementation** (`Bip340.kt`). Use BIP-340 x-only public key lift, tagged hashes and Jacobian points (avoid modular inversion at every point addition):

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import java.math.BigInteger
import java.security.MessageDigest

/** BIP-340 Schnorr verification only; no secret key is accepted or retained. */
object Bip340 {
    private val FIELD = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val ORDER = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val GX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val GY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)
    private val SEVEN = BigInteger.valueOf(7)
    private val EIGHT = BigInteger.valueOf(8)
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private const val FIELD_BYTES = 32
    private const val SIGNATURE_BYTES = 64
    private const val HEX_RADIX = 16
    private val INFINITY = Point(ZERO, ONE, ZERO)
    private val GENERATOR = Point(GX, GY, ONE)

    fun verify(pubkeyHex: String, messageHex: String, signatureHex: String): Boolean = try {
        val pubkey = decodeHex(pubkeyHex) ?: return false
        val message = decodeHex(messageHex) ?: return false
        val signature = decodeHex(signatureHex) ?: return false
        if (pubkey.size != FIELD_BYTES || signature.size != SIGNATURE_BYTES) return false

        val px = BigInteger(1, pubkey)
        val publicPoint = liftX(px) ?: return false
        val r = BigInteger(1, signature.copyOfRange(0, FIELD_BYTES))
        val s = BigInteger(1, signature.copyOfRange(FIELD_BYTES, SIGNATURE_BYTES))
        if (r >= FIELD || s >= ORDER) return false

        val challengeInput = signature.copyOfRange(0, FIELD_BYTES) + pubkey + message
        val challenge = BigInteger(1, taggedHash("BIP0340/challenge", challengeInput)).mod(ORDER)
        val result = toAffine(add(multiply(s, GENERATOR), multiply(ORDER.subtract(challenge).mod(ORDER), publicPoint)))
            ?: return false
        result.y.testBit(0).not() && result.x == r
    } catch (_: ArithmeticException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private data class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger)
    private data class Affine(val x: BigInteger, val y: BigInteger)

    private fun decodeHex(value: String): ByteArray? {
        if (value.length % 2 != 0 || value.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
        return runCatching {
            ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte() }
        }.getOrNull()
    }

    private fun liftX(x: BigInteger): Point? {
        if (x >= FIELD) return null
        val ySquared = x.modPow(THREE, FIELD).add(SEVEN).mod(FIELD)
        var y = ySquared.modPow(FIELD.add(ONE).divide(BigInteger.valueOf(4)), FIELD)
        if (y.multiply(y).mod(FIELD) != ySquared) return null
        if (y.testBit(0)) y = FIELD.subtract(y)
        return Point(x, y, ONE)
    }

    private fun taggedHash(tag: String, message: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val tagHash = digest.digest(tag.toByteArray(Charsets.UTF_8))
        return MessageDigest.getInstance("SHA-256").digest(tagHash + tagHash + message)
    }

    private fun multiply(scalar: BigInteger, point: Point): Point {
        var n = scalar
        var addend = point
        var result = INFINITY
        while (n.signum() > 0) {
            if (n.testBit(0)) result = add(result, addend)
            addend = double(addend)
            n = n.shiftRight(1)
        }
        return result
    }

    private fun double(point: Point): Point {
        if (point.z == ZERO || point.y == ZERO) return INFINITY
        val a = point.x.multiply(point.x).mod(FIELD)
        val b = point.y.multiply(point.y).mod(FIELD)
        val c = b.multiply(b).mod(FIELD)
        val d = TWO.multiply(point.x.add(b).pow(2).subtract(a).subtract(c)).mod(FIELD)
        val e = THREE.multiply(a).mod(FIELD)
        val f = e.multiply(e).mod(FIELD)
        val x = f.subtract(TWO.multiply(d)).mod(FIELD)
        val y = e.multiply(d.subtract(x)).subtract(EIGHT.multiply(c)).mod(FIELD)
        val z = TWO.multiply(point.y).multiply(point.z).mod(FIELD)
        return Point(x, y, z)
    }

    private fun add(p: Point, q: Point): Point {
        if (p.z == ZERO) return q
        if (q.z == ZERO) return p
        val z1z1 = p.z.multiply(p.z).mod(FIELD)
        val z2z2 = q.z.multiply(q.z).mod(FIELD)
        val u1 = p.x.multiply(z2z2).mod(FIELD)
        val u2 = q.x.multiply(z1z1).mod(FIELD)
        val s1 = p.y.multiply(q.z).multiply(z2z2).mod(FIELD)
        val s2 = q.y.multiply(p.z).multiply(z1z1).mod(FIELD)
        val h = u2.subtract(u1).mod(FIELD)
        val r = TWO.multiply(s2.subtract(s1)).mod(FIELD)
        if (h == ZERO) return if (r == ZERO) double(p) else INFINITY
        val i = TWO.multiply(h).pow(2).mod(FIELD)
        val j = h.multiply(i).mod(FIELD)
        val v = u1.multiply(i).mod(FIELD)
        val x = r.multiply(r).subtract(j).subtract(TWO.multiply(v)).mod(FIELD)
        val y = r.multiply(v.subtract(x)).subtract(TWO.multiply(s1).multiply(j)).mod(FIELD)
        val z = p.z.add(q.z).pow(2).subtract(z1z1).subtract(z2z2).multiply(h).mod(FIELD)
        return Point(x, y, z)
    }

    private fun toAffine(point: Point): Affine? {
        if (point.z == ZERO) return null
        val inverse = point.z.modPow(FIELD.subtract(TWO), FIELD)
        val inverseSquared = inverse.multiply(inverse).mod(FIELD)
        val x = point.x.multiply(inverseSquared).mod(FIELD)
        val y = point.y.multiply(inverseSquared).multiply(inverse).mod(FIELD)
        return Affine(x, y)
    }
}
```

- [ ] **Step 4: Chạy xanh** (cùng lệnh, nền, chờ `.exit`): 8 test, tất cả 19 vector phải khớp; nếu có sai, đối chiếu nguyên văn CSV và sửa thuật toán, không bỏ vector.
- [ ] **Step 5: Mutation** (`/tmp/f3_t2_batch.sh`, một batch nền, cuối `cmp` và `ALLDONE`; lớp `Bip340Test`): đổi `FIELD`/`ORDER` một bit; bỏ `x >= FIELD`; đổi `FIELD.add(ONE).divide(4)` thành chia 2; bỏ nhánh sửa y lẻ; `taggedHash` bỏ tag-prefix kép; hash challenge dùng `message + pubkey`; bỏ `r >= FIELD`/`s >= ORDER`; thay `ORDER.subtract(challenge)` thành `ORDER.add(challenge)`; bỏ kiểm parity R; so sánh `result.x` với `s` thay `r`; đổi phép `mod(FIELD)` thành `mod(ORDER)` trong `double`; ép điểm infinity thành điểm gốc. Mọi đột biến phải bị bắt bởi test chính thức hoặc test lật bit; bổ sung test nếu sống sót.
- [ ] **Step 6: Commit** sau khi exit=0 và XML xác nhận `failures=0, errors=0`:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bip340.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/Bip340Test.kt && git commit -m "feat(front-02b): verify public Nostr signatures with the BIP-340 vectors"`

---

### Task 3: `NostrEvent` (parse và xác minh id, chữ ký, pubkey, kind)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventTest.kt` (Robolectric, vì `org.json`)

**Interfaces:**
- Consumes: `Bip340.verify(pubkeyHex, messageHex, signatureHex)` (Task 2).
- Produces: `class NostrEvent(val id: String, val pubkey: String, val createdAt: Long, val kind: Int, val tags: List<List<String>>, val content: String, val sig: String)` với `val isReply: Boolean`, `fun isValid(expectedPubkeyHex: String): Boolean`, và `companion { fun parse(json: String): NostrEvent? }`.
- `parse` chặt chẽ: đủ bảy trường, đúng kiểu (`created_at`/`kind` là số nguyên, `tags` là mảng các mảng **chuỗi**, còn lại là chuỗi), JSON hỏng hoặc thiếu trường ⇒ `null`. Trường lạ được bỏ qua.
- `isValid`: `kind == 1`; `id`, `pubkey` đúng 64 ký tự hex **thường**, `sig` 128 hex thường; `pubkey == expectedPubkeyHex`; `id` bằng SHA-256 của chuỗi chuẩn hóa NIP-01 `[0,pubkey,created_at,kind,tags,content]` (JSON nén, chỉ thoát `"` `\` `\n` `\r` `\t` `\b` `\f`, chữ Unicode giữ nguyên UTF-8); chữ ký đúng với `id`. Không ném lỗi.
- Fixture dưới đây do một bộ ký Python **độc lập** sinh ra (khóa thử lấy từ vector BIP-340 số 1 và 2; không phải khóa của ai thật); bộ ký đã tự kiểm lại bằng bộ kiểm cùng thuật toán với Task 2. Riêng `rich` chứa dấu nháy kép, `\`, tab, xuống dòng, `<script>`, `&`, chữ có dấu, chữ Hán và emoji để chứng minh chuỗi chuẩn hóa.

- [ ] **Step 1: Viết test đỏ** (`NostrEventTest.kt`, phần đầu: fixture)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrEventTest {
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val stranger = "dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8"

    private val plain = """{"id":"c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000300,"kind":1,"tags":[],"content":"Hello nostr, first note","sig":"4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623"}"""
    private val rich = """{"id":"a0c97278dbf284ce180d75923c1c5c50c82b9111ea1c61d7a02b97c84b811b49","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000200,"kind":1,"tags":[["t","news"]],"content":"Quote \" backslash \\ tab\t newline\nline2 <script>alert(1)</script> & cafe é 中文 😀","sig":"b32be964eb698aa9b4d7ea2b187c293905267b036886416e67773ab009e7dff007df9edae1adcfb959d5695ff53acaf02f44ffbc9372d95772ee283e76a3871e"}"""
    private val reply = """{"id":"36432db1e953c2945198a3326740ce3c57c06f0fffd075d3762bb743704b0bed","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000250,"kind":1,"tags":[["e","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","","reply"]],"content":"This one is a reply","sig":"637c1169b9905f2bac6fb28489852e649571dd16dad71ffc8ca19ca2e2aa321ba44ed8d3404af3b9a3cf38ad68bd52cd6ca00369b5afd84a6ce0188d5eb92582"}"""
    private val kind7 = """{"id":"409f5262d409c9d1d2af688942d2bf7be21a261a9a564da94fcfdd7dda577f29","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000400,"kind":7,"tags":[],"content":"+","sig":"a31d82905a8910278e8aa117f7e8768fcce5b908ea5a889a12cc6a9f71c900d246b8c0c46a400aa671c156dcbe75cf0d081ab9b8c7283bf492f1431c710b2616"}"""
    private val foreign = """{"id":"3f6c370c58122e1e43fe8ad81de0da6f642cbf359ecfee4c1fae8bb0a6f977a9","pubkey":"dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8","created_at":1700000300,"kind":1,"tags":[],"content":"Written by somebody else","sig":"a8a86899daf244626261ab4193936db94ed53f026b8dad7e9c0cda80a28bd436cc57bf213c158093619b25daa52d1f6064adec8e19dcb4f30cf153fb791ea290"}"""
    private val future = """{"id":"e13f00d75f8562723d5cf49f79b2c76c4070f51b548f3d715719ed5da2f1f956","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":4102444800,"kind":1,"tags":[],"content":"Dated far in the future","sig":"03671993de847550a7b9d81b6b0cf7ca1c764dee0f29edce59842bb96dd1723ad2afcb4deececd61bad5ba525e12b82e7a1b8ab823781a9b1a9f0b8ce30faf4e"}"""
    private val escapes = """{"id":"22131b2a5383313d46530e6cadf52eb6966b532d0740810dc318dd8c8ea7bbde","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000500,"kind":1,"tags":[],"content":"cr\rbs\bff\fend","sig":"c073d868e6ee74bfc63192dbfa42cdf0a4f570c38feb10cb0445fdc4860cf3148ec4e8a42913f22166d12c70fb433ff83b8cbe216dcc362b390ec8f78e8db844"}"""
```

- [ ] **Step 1b: Viết test đỏ** (cùng tệp, phần thân; nối ngay sau các dòng `private val` ở trên, trước dấu `}` cuối)

```kotlin
    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json)) { "fixture must parse" }
    private fun replaceFirst(json: String, old: String, new: String): String {
        require(json.contains(old)) { "pattern not in fixture: $old" }
        return json.replaceFirst(old, new)
    }

    // ---- parse
    @Test fun parse_readsEveryField() {
        val e = ev(plain)
        assertEquals("c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e", e.id)
        assertEquals(author, e.pubkey)
        assertEquals(1_700_000_300L, e.createdAt)
        assertEquals(1, e.kind)
        assertEquals("Hello nostr, first note", e.content)
        assertEquals(emptyList<List<String>>(), e.tags)
    }

    @Test fun parse_keepsTagsAndUnicodeContent() {
        val e = ev(rich)
        assertEquals(listOf(listOf("t", "news")), e.tags)
        assertTrue(e.content.contains("\"") && e.content.contains("\\") && e.content.contains("\t") && e.content.contains("\n"))
        assertTrue(e.content.contains("中文") && e.content.contains("😀"))
    }

    @Test fun parse_ignoresUnknownFields() = assertNotNull(NostrEvent.parse(plain.dropLast(1) + ""","extra":{"a":1}}"""))

    @Test fun parse_rejectsBrokenOrIncompleteJson() {
        listOf("", "not json", "[]", "{}", "null", plain.dropLast(1), plain.replace("\"sig\"", "\"sog\""), plain.replace("\"content\"", "\"cont\""))
            .forEach { assertNull(it, NostrEvent.parse(it)) }
    }

    @Test fun parse_rejectsWrongTypes() {
        listOf(
            replaceFirst(plain, "\"created_at\":1700000300", "\"created_at\":\"1700000300\""),
            replaceFirst(plain, "\"created_at\":1700000300", "\"created_at\":1.5"),
            replaceFirst(plain, "\"kind\":1", "\"kind\":\"1\""),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":{}"),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":[1]"),
            replaceFirst(plain, "\"tags\":[]", "\"tags\":[[1]]"),
            replaceFirst(plain, "\"content\":\"Hello nostr, first note\"", "\"content\":5"),
            replaceFirst(plain, "\"content\":\"Hello nostr, first note\"", "\"content\":null"),
        ).forEach { assertNull(it, NostrEvent.parse(it)) }
    }

    // ---- isValid
    @Test fun isValid_acceptsASignedNote() = assertTrue(ev(plain).isValid(author))

    @Test fun isValid_acceptsContentThatNeedsEscaping() = assertTrue(ev(rich).isValid(author))

    @Test fun isValid_acceptsEveryJsonEscape_CR_BS_FF() = assertTrue(ev(escapes).isValid(author))

    @Test fun isValid_acceptsAFutureDatedNote_theMapperClampsIt() = assertTrue(ev(future).isValid(author))

    @Test fun isValid_rejectsANoteFromSomeoneElse() {
        assertFalse(ev(foreign).isValid(author))
        assertTrue(ev(foreign).isValid(stranger))
    }

    @Test fun isValid_rejectsAnotherKind_evenWhenCorrectlySigned() = assertFalse(ev(kind7).isValid(author))

    @Test fun isValid_rejectsTamperedContent_becauseTheIdNoLongerMatches() =
        assertFalse(ev(replaceFirst(plain, "first note", "forged note")).isValid(author))

    @Test fun isValid_rejectsATamperedTimestampOrTags() {
        assertFalse(ev(replaceFirst(plain, "1700000300", "1700000301")).isValid(author))
        assertFalse(ev(replaceFirst(rich, "[[\"t\",\"news\"]]", "[[\"t\",\"fake\"]]")).isValid(author))
    }

    @Test fun isValid_rejectsAWrongSignature_evenWithAMatchingId() {
        val sig = ev(plain).sig
        val flipped = (if (sig.first() == '0') "1" else "0") + sig.drop(1)
        assertFalse(ev(replaceFirst(plain, sig, flipped)).isValid(author))
    }

    @Test fun isValid_rejectsASignatureBorrowedFromAnotherNote() =
        assertFalse(ev(replaceFirst(plain, ev(plain).sig, ev(rich).sig)).isValid(author))

    @Test fun isValid_rejectsUppercaseOrShortHex() {
        val id = ev(plain).id
        assertFalse(ev(replaceFirst(plain, id, id.uppercase())).isValid(author))
        assertFalse(ev(replaceFirst(plain, id, id.dropLast(2))).isValid(author))
        assertFalse(ev(plain).isValid(author.uppercase()))
    }

    // ---- isReply
    @Test fun isReply_isTrueOnlyWithAnETag() {
        assertTrue(ev(reply).isReply)
        assertFalse(ev(plain).isReply)
        assertFalse(ev(rich).isReply)
    }
}
```

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f3-t3.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrEventTest' > /tmp/f3-t3-red.log 2>&1; echo $? > /tmp/f3-t3.exit) &`. Đỏ đúng lý do: `Unresolved reference: NostrEvent`.

- [ ] **Step 3: Minimal implementation** (`NostrEvent.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/** One Nostr event (NIP-01) read from a relay. Nothing here is trusted until [isValid] says so. */
class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** A reply carries an `e` tag (it answers another note); the feed shows originals only. */
    val isReply: Boolean get() = tags.any { it.firstOrNull() == REPLY_TAG }

    fun isValid(expectedPubkeyHex: String): Boolean {
        if (kind != TEXT_NOTE_KIND) return false
        if (!isLowerHex(id, ID_HEX_LENGTH) || !isLowerHex(pubkey, ID_HEX_LENGTH) || !isLowerHex(sig, SIG_HEX_LENGTH)) return false
        if (pubkey != expectedPubkeyHex) return false
        if (computeId() != id) return false
        return Bip340.verify(pubkey, id, sig)
    }

    private fun computeId(): String {
        val canonical = buildString {
            append("[0,").append(quote(pubkey)).append(',').append(createdAt).append(',').append(kind).append(',')
            append('[')
            tags.forEachIndexed { i, tag ->
                if (i > 0) append(',')
                append('[').append(tag.joinToString(",") { quote(it) }).append(']')
            }
            append("],").append(quote(content)).append(']')
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        const val TEXT_NOTE_KIND = 1
        private const val REPLY_TAG = "e"
        private const val ID_HEX_LENGTH = 64
        private const val SIG_HEX_LENGTH = 128
        private const val CONTROL_LIMIT = 0x20

        fun parse(json: String): NostrEvent? = try {
            val o = JSONObject(json)
            NostrEvent(
                id = o.strictString("id"),
                pubkey = o.strictString("pubkey"),
                createdAt = o.strictLong("created_at"),
                kind = o.strictInt("kind"),
                tags = o.strictTags("tags"),
                content = o.strictString("content"),
                sig = o.strictString("sig"),
            )
        } catch (_: JSONException) {
            null
        }

        private fun JSONObject.strictString(name: String): String = get(name) as? String ?: throw JSONException("$name")
        private fun JSONObject.strictLong(name: String): Long = (get(name) as? Number)?.takeIf { it is Int || it is Long }?.toLong() ?: throw JSONException("$name")
        private fun JSONObject.strictInt(name: String): Int = (get(name) as? Number)?.takeIf { it is Int }?.toInt() ?: throw JSONException("$name")

        private fun JSONObject.strictTags(name: String): List<List<String>> {
            val outer = get(name) as? JSONArray ?: throw JSONException(name)
            return List(outer.length()) { i ->
                val inner = outer.get(i) as? JSONArray ?: throw JSONException(name)
                List(inner.length()) { j -> inner.get(j) as? String ?: throw JSONException(name) }
            }
        }

        private fun isLowerHex(value: String, length: Int): Boolean =
            value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

        /** NIP-01 escaping: only the seven JSON escapes below; any other character stays as it is. */
        private fun quote(text: String): String = buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c.code < CONTROL_LIMIT) append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }
    }
}
```

Chú ý: `org.json` có thể trả `Int`, `Long`, `Double`. Số nguyên quá lớn (ví dụ `created_at` ngoài `Int`) ra `Long`; `1.5` ra `Double` và bị từ chối. Nếu `JSONObject.get` trả `JSONObject.NULL` cho `content: null`, ép kiểu `as? String` đã trả `null` ⇒ ném `JSONException` đúng mong muốn.

- [ ] **Step 4: Chạy xanh** (nền, chờ `.exit`): 16 test. Nếu `parse_rejectsWrongTypes` đỏ ở một mẫu, in mẫu đó và sửa **mã** (không nới test).
- [ ] **Step 5: Mutation** (`/tmp/f3_t3_batch.sh`, một batch nền, `cmp` cuối, `ALLDONE`; lớp `NostrEventTest`, tệp `NostrEvent.kt`). Đột biến phải bị bắt: bỏ `kind != TEXT_NOTE_KIND`; bỏ `pubkey != expectedPubkeyHex`; bỏ `computeId() != id`; bỏ `Bip340.verify`; kiểm `Bip340.verify(pubkey, sig, id)` (đảo tham số); `isLowerHex` nhận hoa (`'A'..'F'`); độ dài `ID_HEX_LENGTH` 64→63; bỏ nhánh `'\\' ->`; bỏ `'\n' ->`; thêm khoảng trắng vào chuỗi chuẩn hóa (`append(", ")`); `isReply` bắt `"p"` thay `"e"`; `strictLong` nhận `Double`; bỏ `strictTags` kiểm chuỗi con. Sống sót ⇒ thêm test (fixture `escapes` đã phủ `\r`, `\b`, `\f`; riêng ký tự điều khiển `\u0001` chưa có fixture: nếu đột biến ở nhánh `\\u%04x` sống sót thì sinh thêm một event bằng `/tmp/f3_fixgen2.py` với `content` chứa `\x01`; guard thừa thì gỡ).
- [ ] **Step 6: Commit** sau exit=0:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventTest.kt && git commit -m "feat(front-02b): accept a Nostr note only when its id, signature and author all check out"`

---

### Task 4: `NostrRelayClient` — WebSocket có giới hạn, luôn đóng

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt` (event mẫu đã ký, dùng chung Task 3 đến 5)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt` (Robolectric vì `org.json`)

**Interfaces:**
- Consumes: `NostrEvent.parse`, `NostrEvent.isValid(authorHex)` (Task 3).
- Produces: `class NostrRelayClient` với constructor công khai `(client: OkHttpClient, ioDispatcher: CoroutineDispatcher)` (hai relay cố định, bắt buộc `wss://`) và constructor `internal` `(client, ioDispatcher, relays: List<String>, requireSecure: Boolean, timeoutMs: Long)` chỉ để test. `suspend fun fetch(authorHex: String): List<NostrEvent>`: tối đa `MAX_NOTES_KEPT` bài hợp lệ, mới nhất trước; rỗng khi cả hai relay không cho bài hợp lệ; `authorHex` không phải 64 hex thường ⇒ rỗng và **không mở kết nối**.
- Hằng: `RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net")`, `RELAY_TIMEOUT_MS = 8_000L`, `MAX_MESSAGE_BYTES = 64 * 1024`, `MAX_EVENTS_READ = 50`, `MAX_NOTES_KEPT = 20`, `SUBSCRIPTION_ID = "f2"` (không chứa npub).
- Server giả đọc `REQ` ở `onMessage` của WebSocket (không phải `takeRequest`); OkHttp tự đổi `ws://` thành `http://` khi tạo `Request`.

- [ ] **Step 1: Tạo `NostrTestEvents.kt`.** Dùng tệp sinh sẵn `/tmp/NostrTestEvents.kt` (51 dòng, do `python3 /tmp/f3_shared_fix.py` tạo từ các event ký bằng bộ ký Python độc lập): chứa `AUTHOR`, `STRANGER`, `PLAIN_ID`, `PLAIN`, `RICH`, `REPLY`, `OLDER`, `EMPTY`, `KIND7`, `FUTURE`, `FOREIGN`, `LONG` và `MANY` (30 bài hợp lệ, `created_at` 1700001000 đến 1700001029). Chép nguyên tệp: `cp /tmp/NostrTestEvents.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt`. Nếu `/tmp` bị dọn: chạy lại `python3 /tmp/f3_gen.py` rồi `python3 /tmp/f3_shared_fix.py` (cần `/tmp/bip340.csv` tải từ `bitcoin/bips/bip-0340/test-vectors.csv`).

- [ ] **Step 2: Viết test đỏ**, phần 1 (khung và các ca bài):

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val received = CopyOnWriteArrayList<String>()
    private val closed = CountDownLatch(1)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient()
    }

    @After fun tearDown() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun wsUrl() = server.url("/").toString()

    /** A relay that answers a REQ with [events] and then EOSE (unless [sendEose] is false). */
    private fun relay(vararg events: String, sendEose: Boolean = true): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                received.add(text)
                if (text.startsWith("[\"REQ\"")) {
                    events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
                    if (sendEose) webSocket.send("[\"EOSE\",\"f2\"]")
                }
                if (text.startsWith("[\"CLOSE\"")) closed.countDown()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closed.countDown()
                webSocket.close(code, reason)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed.countDown()
        })

    private fun client(vararg urls: String, secure: Boolean = false, timeoutMs: Long = 5_000L) =
        NostrRelayClient(http, Dispatchers.IO, urls.toList(), secure, timeoutMs)

    private fun ids(notes: List<NostrEvent>) = notes.map { it.id }

    @Test fun fetch_sendsOneReqForKind1AndTheAuthor_thenClosesTheSubscription() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertTrue("socket closed", closed.await(3, TimeUnit.SECONDS))
        val req = received.first()
        assertEquals("[\"REQ\",\"f2\",{\"kinds\":[1],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":50}]", req)
        assertTrue(received.any { it == "[\"CLOSE\",\"f2\"]" })
    }

    @Test fun fetch_dropsNotesByOthersWrongKindsAndBadSignatures() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.FOREIGN, NostrTestEvents.KIND7))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR)))
    }

    @Test fun fetch_dropsANoteWhoseSignatureWasForged() = runBlocking {
        val forged = NostrTestEvents.PLAIN.replace("first note", "forged note")
        server.enqueue(relay(forged))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).isEmpty())
    }

    @Test fun fetch_ignoresEventsOfAnotherSubscription() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.send("[\"EVENT\",\"other\",${NostrTestEvents.PLAIN}]")
                    webSocket.send("[\"EOSE\",\"f2\"]")
                }
            }),
        )
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).isEmpty())
    }

    @Test fun fetch_keepsTheTwentyNewest_newestFirst() = runBlocking {
        server.enqueue(relay(*NostrTestEvents.MANY.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, notes.size)
        assertEquals(notes.sortedByDescending { it.createdAt }.map { it.id }, ids(notes))
        assertEquals(1_700_001_029L, notes.first().createdAt)
        assertEquals(1_700_001_010L, notes.last().createdAt)
    }

    @Test fun fetch_mergesTheSameNoteSentTwice() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PLAIN))
        assertEquals(1, client(wsUrl()).fetch(NostrTestEvents.AUTHOR).size)
    }

    // ---- failures and limits
    @Test fun fetch_triesTheSecondRelay_whenTheFirstRefusesTheUpgrade() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)))
    }

    @Test fun fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes() = runBlocking {
        server.enqueue(relay(NostrTestEvents.FOREIGN))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)))
    }

    @Test fun fetch_returnsEmpty_whenBothRelaysFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).isEmpty())
    }

    @Test fun fetch_timesOut_whenARelayNeverSendsEose_andThenTriesTheNextOne() = runBlocking {
        server.enqueue(relay(sendEose = false))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val started = System.currentTimeMillis()
        val notes = client(wsUrl(), wsUrl(), timeoutMs = 400L).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 4_000L)
    }

    @Test fun fetch_skipsAnOversizedFrame_butStillReadsTheNextNote() = runBlocking {
        val huge = "{\"pad\":\"" + "x".repeat(NostrRelayClient.MAX_MESSAGE_BYTES) + "\"}"
        server.enqueue(relay(huge, NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR)))
    }

    @Test fun fetch_stopsReadingAfterFiftyFrames() = runBlocking {
        val junk = List(NostrRelayClient.MAX_EVENTS_READ) { "{\"n\":$it}" }
        server.enqueue(relay(*junk.toTypedArray(), NostrTestEvents.PLAIN))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue("the 51st frame must not be read", client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).isEmpty())
    }

    @Test fun fetch_doesNotOpenAConnection_forAnInvalidAuthor() = runBlocking {
        for (bad in listOf("", "abc", NostrTestEvents.AUTHOR.uppercase(), NostrTestEvents.AUTHOR.dropLast(1), "\"],\"x\":[\"")) {
            assertTrue(bad, client(wsUrl()).fetch(bad).isEmpty())
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun fetch_refusesARelayThatIsNotWss_whenSecurityIsRequired() = runBlocking {
        assertTrue(client(wsUrl(), secure = true).fetch(NostrTestEvents.AUTHOR).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun production_relays_areTheTwoFixedSecureOnes() {
        assertEquals(listOf("wss://nos.lol", "wss://relay.primal.net"), NostrRelayClient.RELAYS)
        assertTrue(NostrRelayClient.RELAYS.all { it.startsWith("wss://") })
    }

    @Test fun fetch_closesTheSocketEvenWhenTheCallerIsCancelled() = runBlocking {
        server.enqueue(relay(sendEose = false))
        val job = GlobalScope.launch(Dispatchers.IO) { client(wsUrl(), timeoutMs = 30_000L).fetch(NostrTestEvents.AUTHOR) }
        delay(500L)
        job.cancel()
        assertTrue("socket closed after cancel", closed.await(3, TimeUnit.SECONDS))
    }

    @Test fun relayClient_neverLogsOrPrintsAnything() {
        val src = java.io.File("src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt").readLines()
            .filterNot { it.trimStart().let { t -> t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ") } }
            .joinToString("\n")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, src.contains(it)) }
    }
}
```

- [ ] **Step 3: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f3-t4.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrRelayClientTest' > /tmp/f3-t4-red.log 2>&1; echo $? > /tmp/f3-t4.exit) &`. Đỏ đúng lý do: `Unresolved reference: NostrRelayClient`.

- [ ] **Step 4: Minimal implementation** (`NostrRelayClient.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray

/**
 * Reads one author's latest text notes from public Nostr relays. Read-only: it sends a single REQ and a CLOSE.
 * Nothing here logs: a relay sees the npub being read, so the app must not also write it down.
 */
class NostrRelayClient internal constructor(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val relays: List<String>,
    private val requireSecure: Boolean,
    private val timeoutMs: Long,
) {
    constructor(client: OkHttpClient, ioDispatcher: CoroutineDispatcher) :
        this(client, ioDispatcher, RELAYS, requireSecure = true, timeoutMs = RELAY_TIMEOUT_MS)

    suspend fun fetch(authorHex: String): List<NostrEvent> = withContext(ioDispatcher) {
        if (!AUTHOR_REGEX.matches(authorHex)) return@withContext emptyList()
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val notes = readRelay(relay, authorHex)
            if (notes.isNotEmpty()) return@withContext notes
        }
        emptyList()
    }

    private suspend fun readRelay(url: String, authorHex: String): List<NostrEvent> {
        val found = LinkedHashMap<String, NostrEvent>()
        val finished = CompletableDeferred<Unit>()
        var frames = 0
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return emptyList()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "[\"REQ\",\"$SUBSCRIPTION_ID\",{\"kinds\":[${NostrEvent.TEXT_NOTE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$MAX_EVENTS_READ}]",
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (finished.isCompleted) return
                    if (text.length > MAX_MESSAGE_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                        frames += 1
                        return
                    }
                    val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                    when (message.optString(0)) {
                        "EOSE" -> if (message.optString(1) == SUBSCRIPTION_ID) finished.complete(Unit)
                        "EVENT" -> {
                            if (message.optString(1) != SUBSCRIPTION_ID) return
                            frames += 1
                            val raw = runCatching { message.getJSONObject(2).toString() }.getOrNull()
                            val event = raw?.let { NostrEvent.parse(it) }
                            if (event != null && event.isValid(authorHex)) found[event.id] = event
                            if (frames >= MAX_EVENTS_READ) finished.complete(Unit)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finished.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finished.complete(Unit)
                }
            },
        )
        try {
            withTimeout(timeoutMs) { finished.await() }
        } catch (_: TimeoutCancellationException) {
            // A relay that never answers is skipped; whatever it already sent is still checked below.
        } catch (e: CancellationException) {
            socket.cancel()
            throw e
        } finally {
            runCatching { socket.send("[\"CLOSE\",\"$SUBSCRIPTION_ID\"]") }
            socket.close(NORMAL_CLOSE, null)
            socket.cancel()
        }
        return found.values.sortedByDescending { it.createdAt }.take(MAX_NOTES_KEPT)
    }

    companion object {
        val RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net")
        const val RELAY_TIMEOUT_MS = 8_000L
        const val MAX_MESSAGE_BYTES = 64 * 1024
        const val MAX_EVENTS_READ = 50
        const val MAX_NOTES_KEPT = 20
        private const val SUBSCRIPTION_ID = "f2"
        private const val SECURE_SCHEME = "wss://"
        private const val NORMAL_CLOSE = 1000
        private val AUTHOR_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
```

- [ ] **Step 5: Chạy xanh** (nền, chờ `.exit`): 17 test. `frames` chỉ bị ghi từ luồng đọc của một socket (OkHttp dùng một luồng cho mỗi socket) nên không cần khóa. Đỏ thì đọc lý do từ XML và **sửa mã, không nới test**.
- [ ] **Step 6: Mutation** (`/tmp/f3_t4_batch.sh`, một batch nền, `cmp` cuối, `ALLDONE`; lớp `NostrRelayClientTest`, tệp `NostrRelayClient.kt`): bỏ `AUTHOR_REGEX.matches`; bỏ `requireSecure && !relay.startsWith`; `notes.isNotEmpty()` thành `true` (dừng ở relay đầu dù rỗng); bỏ kiểm `message.optString(1) != SUBSCRIPTION_ID` ở EVENT; bỏ `event.isValid(authorHex)`; `frames >= MAX_EVENTS_READ` thành `>`; `take(MAX_NOTES_KEPT)` thành `take(MAX_NOTES_KEPT + 1)`; bỏ `sortedByDescending`; bỏ kiểm cỡ khung; bỏ `socket.cancel()`/`socket.close` trong `finally`; bỏ `catch CancellationException`; `withTimeout(timeoutMs)` thành `withTimeout(timeoutMs * 100)`; thêm `Log.i` (test không-log phải bắt). Sống sót ⇒ thêm test.
- [ ] **Step 7: Commit** sau exit=0, XML `failures=0, errors=0`:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt && git commit -m "feat(front-02b): read an author's latest notes from two fixed relays, with limits, and always close the socket"`

---

### Task 5: `NostrFeedMapper` — event hợp lệ thành `Article`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt` (Robolectric: `titleFromBody` dùng `android.text.Html`)

**Interfaces:**
- Consumes: `NostrEvent` (`isReply`, `createdAt`, `content`, `id`), `NostrKeys.noteId`, `RssDescriptionParser.titleFromBody(html, max)` và `MAX_SHORT_DESCRIPTION_LENGTH` (đã có, `internal`), `NostrRelayClient.MAX_NOTES_KEPT`, `String.spacerDollar` (`ui.ext`).
- Produces: `object NostrFeedMapper { fun toArticles(events: List<NostrEvent>, feedId: String, accountId: Int, nowMillis: Long, newId: () -> String = { UUID.randomUUID().toString() }): List<Article>; fun feedName(npub: String): String; fun feedUrl(npub: String): String }`, hằng `NJUMP_BASE = "https://njump.me/"`, `FEED_URL_PREFIX = "nostr:"`, `FEED_NAME_PREFIX = "Nostr · "`.
- Quy tắc: bỏ trả lời (`isReply`) và ghi chú chỉ có khoảng trắng; bỏ trùng `id`; `date = min(createdAt * 1000, nowMillis)` (relay không được đặt bài ở tương lai); sắp mới nhất trước; tối đa `MAX_NOTES_KEPT`; `link = NJUMP_BASE + note1…`; `rawDescription` là **nội dung đã thoát HTML** (`&` `<` `>` `"` `'`) với xuống dòng thành `<br>`; `title = titleFromBody(rawDescription)` (80 ký tự đầu, `…` nếu cắt); `shortDescription` = chữ thuần cắt `MAX_SHORT_DESCRIPTION_LENGTH`; `id = accountId.spacerDollar(newId())`; `author = null`, `img = null`, `isUnread = true`.
- `feedName(npub)` = `"Nostr · "` + 8 ký tự đầu + `…` + 4 ký tự cuối của `npub`; `feedUrl(npub)` = `"nostr:"` + `npub` chữ thường.

- [ ] **Step 1: Viết test đỏ** (`NostrFeedMapperTest.kt`; event lấy từ `NostrTestEvents`, `NostrEvent.parse` đã được Task 3 kiểm)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrFeedMapperTest {
    private val now = 1_800_000_000_000L
    private val npub = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"

    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json))
    private fun map(vararg json: String, nowMillis: Long = now): List<com.mckimquyen.reader.domain.model.article.Article> {
        val counter = AtomicInteger()
        return NostrFeedMapper.toArticles(json.map { ev(it) }, "feed-1", 7, nowMillis) { "u${counter.incrementAndGet()}" }
    }

    @Test fun aPlainNote_becomesAnArticle() {
        val a = map(NostrTestEvents.PLAIN).single()
        assertEquals("Hello nostr, first note", a.title)
        assertEquals("Hello nostr, first note", a.rawDescription)
        assertEquals("Hello nostr, first note", a.shortDescription)
        assertEquals("feed-1", a.feedId)
        assertEquals(7, a.accountId)
        assertEquals(1_700_000_300_000L, a.date.time)
        assertTrue(a.isUnread)
        assertEquals(null, a.author)
        assertEquals(null, a.img)
        assertEquals("7\$u1", a.id)
    }

    @Test fun theLink_isTheNjumpAddressOfTheNote() {
        assertEquals(
            "https://njump.me/note1cqneqau89xzdllkft58klqecyrjdte7cgv5svy9u92yc9lxewdlqw0n65r",
            map(NostrTestEvents.PLAIN).single().link,
        )
    }

    @Test fun htmlInTheContent_isEscaped_soNothingIsInterpretedAsMarkup() {
        val a = map(NostrTestEvents.RICH).single()
        assertFalse(a.rawDescription.contains("<script>"))
        assertTrue(a.rawDescription.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(a.rawDescription.contains("&amp;"))
        assertTrue(a.rawDescription.contains("&quot;"))
        assertTrue(a.rawDescription.contains("<br>"))
        assertTrue("the visible title keeps the typed text", a.title.contains("<script>alert(1)</script>"))
        assertTrue(a.title.startsWith("Quote \" backslash"))
    }

    @Test fun aLongNote_getsAnEightyCharacterTitleWithAnEllipsis_andTheFullBodyIsKept() {
        val a = map(NostrTestEvents.LONG).single()
        // 80 code points are "word " x16; the trailing space is trimmed before the ellipsis.
        assertEquals("word ".repeat(15) + "word" + "\u2026", a.title)
        assertEquals("word ".repeat(40).trim(), a.rawDescription)
        assertTrue(a.shortDescription.length <= 110)
        assertTrue(a.shortDescription.startsWith("word word word"))
    }

    @Test fun repliesAndBlankNotesAreSkipped() {
        assertEquals(listOf("Hello nostr, first note"), map(NostrTestEvents.PLAIN, NostrTestEvents.REPLY, NostrTestEvents.EMPTY).map { it.title })
    }

    @Test fun theSameNoteTwice_isKeptOnce() = assertEquals(1, map(NostrTestEvents.PLAIN, NostrTestEvents.PLAIN).size)

    @Test fun notes_areSortedNewestFirst() {
        val titles = map(NostrTestEvents.OLDER, NostrTestEvents.PLAIN, NostrTestEvents.RICH).map { it.title.take(5) }
        assertEquals(listOf("Hello", "Quote", "Older"), titles)
    }

    @Test fun aNoteDatedInTheFuture_isClampedToNow() {
        val a = map(NostrTestEvents.FUTURE, nowMillis = now).single()
        assertEquals(now, a.date.time)
    }

    @Test fun aPastNote_isNotClamped() = assertEquals(1_700_000_300_000L, map(NostrTestEvents.PLAIN, nowMillis = now).single().date.time)

    @Test fun atMostTwentyArticles_areKept_newestFirst() {
        val a = map(*NostrTestEvents.MANY.toTypedArray())
        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, a.size)
        assertEquals(1_700_001_029_000L, a.first().date.time)
        assertEquals(1_700_001_010_000L, a.last().date.time)
    }

    @Test fun feedNameAndUrl_useTheNpub() {
        assertEquals("Nostr \u00b7 npub1mlc\u20267a0a", NostrFeedMapper.feedName(npub))
        assertEquals("nostr:$npub", NostrFeedMapper.feedUrl(npub))
        assertEquals("nostr:$npub", NostrFeedMapper.feedUrl(npub.uppercase()))
    }
}
```

Giá trị cố định đã đo: `spacerDollar` ra `7$u1`; tiêu đề ghi chú `rich` dài 77 điểm mã nên không có `…`; tên nguồn là `Nostr · npub1mlc…7a0a` (8 ký tự đầu, `…`, 4 ký tự cuối).

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f3-t5.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrFeedMapperTest' > /tmp/f3-t5-red.log 2>&1; echo $? > /tmp/f3-t5.exit) &`. Đỏ đúng lý do: `Unresolved reference: NostrFeedMapper`.

- [ ] **Step 3: Minimal implementation** (`NostrFeedMapper.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.ui.ext.spacerDollar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Turns verified Nostr notes into articles. The note text is untrusted: it is escaped before it is stored as HTML. */
object NostrFeedMapper {
    const val NJUMP_BASE = "https://njump.me/"
    const val FEED_URL_PREFIX = "nostr:"
    const val FEED_NAME_PREFIX = "Nostr · "
    private const val MILLIS_PER_SECOND = 1000L
    private const val NAME_HEAD_CHARS = 8
    private const val NAME_TAIL_CHARS = 4
    private const val ELLIPSIS = "…"

    fun feedName(npub: String): String =
        FEED_NAME_PREFIX + npub.take(NAME_HEAD_CHARS) + ELLIPSIS + npub.takeLast(NAME_TAIL_CHARS)

    fun feedUrl(npub: String): String = FEED_URL_PREFIX + npub.trim().lowercase(Locale.ROOT)

    fun toArticles(
        events: List<NostrEvent>,
        feedId: String,
        accountId: Int,
        nowMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Article> =
        events.asSequence()
            .filterNot { it.isReply || it.content.isBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(NostrRelayClient.MAX_NOTES_KEPT)
            .mapNotNull { event ->
                val noteId = NostrKeys.noteId(event.id) ?: return@mapNotNull null
                val html = escapeToHtml(event.content)
                val title = RssDescriptionParser.titleFromBody(html)
                if (title.isEmpty()) return@mapNotNull null
                Article(
                    id = accountId.spacerDollar(newId()),
                    date = Date(minOf(event.createdAt * MILLIS_PER_SECOND, nowMillis)),
                    title = title,
                    rawDescription = html,
                    shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                    link = NJUMP_BASE + noteId,
                    feedId = feedId,
                    accountId = accountId,
                )
            }
            .toList()

    private fun escapeToHtml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '\n' -> append("<br>")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }
}
```

`shortDescription` dùng `title.take(110)` (tiêu đề đã ≤ 81 ký tự nên luôn ≤ 110): đơn giản hơn chạy Readability lên chữ đã thoát; nếu test `shortDescription` đỏ vì độ dài, sửa test cho khớp thực tế đo (tiêu đề), không đổi hành vi.

- [ ] **Step 4: Chạy xanh** (nền, chờ `.exit`): 11 test. Đọc lý do từ XML nếu đỏ; **sửa mã, không nới test**.
- [ ] **Step 5: Mutation** (`/tmp/f3_t5_batch.sh`, một batch nền, `cmp` cuối, `ALLDONE`; lớp `NostrFeedMapperTest`, tệp `NostrFeedMapper.kt`): bỏ `isReply`; bỏ `isBlank`; bỏ `distinctBy`; bỏ `sortedByDescending`; `take(MAX_NOTES_KEPT)` thành `take(MAX_NOTES_KEPT + 1)`; bỏ `minOf(..., nowMillis)` (dùng `createdAt * 1000` thẳng); `* MILLIS_PER_SECOND` thành `* 100`; bỏ nhánh `'<' ->`; bỏ nhánh `'&' ->`; đổi `NJUMP_BASE` thành `http://`; `NAME_HEAD_CHARS` 8→7; `NAME_TAIL_CHARS` 4→3; `'\n' -> "<br>"` thành `"\n"`; `feedUrl` bỏ `lowercase`; bỏ `FEED_URL_PREFIX`. Sống sót ⇒ thêm test (ví dụ thoát `'` và `>`: **thêm** test `quotesAndAngleBracketsAreEscaped` trên một event có `'` và `>` ký bằng bộ ký Python nếu nhánh `'\''`/`'>'` sống sót; guard thừa thì gỡ).
- [ ] **Step 6: Commit** sau exit=0, XML `failures=0, errors=0`:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt && git commit -m "feat(front-02b): turn verified Nostr notes into articles that cannot carry markup"`

---

### Task 6: `Feed.NOSTR_SOURCE_TYPE`, đồng bộ qua relay, và chặn mọi tải web ngoài ý muốn

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt` (hằng `NOSTR_SOURCE_TYPE = "NOSTR"`, **không** đổi cột)
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt` (thêm tham số `nostrRelayClient: NostrRelayClient`; nhánh NOSTR cạnh nhánh WEB)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt` (`parseFullContent` từ chối `NOSTR`; `queryRssIcon` bỏ qua `NOSTR`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt` (không tải toàn văn, không tải ảnh cho `NOSTR`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/` (cung cấp `NostrRelayClient` cho Hilt, xem Step 5)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvNostrSyncTest.kt` (mới, theo mẫu `LocalRssSvWebFeedSyncTest`), `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/NostrNoWebFetchTest.kt` (mới)
- Sửa cả 4 nơi gọi `LocalRssSv(` trong test cũ: `LocalRssSvWebFeedSyncTest`, `WebFeedRoomEndToEndTest`, `RetractionRadarSyncTest` (hai chỗ) thêm `nostrRelayClient = mockk(relaxed = true)`.

**Đã đọc, không đoán (2026-11-14):**
- `LocalRssSv.queryFeedArticles` rẽ nhánh theo `sourceType`: `READ_LATER` trả rỗng; không phải `WEB` thì gọi `super` (RSS). Nhánh `WEB` ném `IOException` khi lỗi và `AbstractRssRepository.syncSingleFeed` bọc `runCatching` nên lỗi thành "nguồn lỗi" (đã có sẵn).
- `AbstractRssRepository.fetchNewArticles` giữ nguyên danh sách đầy đủ cho nguồn không phải RSS ("WEB feeds never used latestLink") và Room bỏ trùng theo `link`: nguồn NOSTR đi đúng đường đó, không cần sửa.
- `syncSingleFeed` gọi `rssHelper.queryRssIcon` khi `feed.icon == null`: hàm này lấy `Uri.parse(feed.url).host` và nhờ `google.com/s2/favicons` tải biểu tượng. Với `nostr:npub1…` host rỗng nên hiện nay **không** gọi mạng, nhưng chỉ nhờ may; phải chặn tường minh và có test.
- `RssHelper.parseFullContent(link, title, sourceType)` mở `njump.me` bằng `okHttpClient` thường nếu được gọi: đường gọi là `OfflinePrecacheService`, `ReadingViewModel.internalRenderFullContent` (người dùng bấm "toàn văn") và `feed.isFullContent`. Phải trả `""` ngay cho `NOSTR` **trước khi** mở kết nối.
- `OfflinePrecacheService` đã có `if (sourceType == WEB) emptyList() else ảnh`; phải thêm `NOSTR`, và không gọi `parseFullContent` cho `NOSTR`.
- Đường ngoài (không đổi, kiểm bằng test): xuất OPML ghi `xmlUrl = feed.url` (`nostr:npub…`), đồng bộ LAN (P2P) chạy `FeedUrlNormalizer.normalize` chỉ nhận `http/https` nên **bỏ qua** nguồn NOSTR (hành vi chấp nhận được, ghi vào báo cáo); `FeedHealthPage` hiển thị `feed.url`.
- `OkHttpClient` thật của app (đã đọc): thêm `AppDns` (DoH), `ConditionalCacheInterceptor` (chỉ cho `GET`, WebSocket upgrade là `GET`: thêm `Cache-Control: max-age=0` vô hại), `UserAgentInterceptor`, `trustAllCerts` (có sẵn từ trước, **không đổi**), `followRedirects(true)`. Kết nối WebSocket dùng chung các cấu hình này; **không được** kiểm bằng JVM, chỉ kiểm bằng probe thật trên Tecno (Task 8).

- [ ] **Step 1: Viết test đỏ** `LocalRssSvNostrSyncTest.kt` (chép khối dựng `LocalRssSv(...)` từ `LocalRssSvWebFeedSyncTest.kt` dòng 37–47 rồi thêm `nostrRelayClient = relay`):

```kotlin
package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvNostrSyncTest {
    private val npub = requireNotNull(NostrKeys.hexToNpub(NostrTestEvents.AUTHOR))
    private val relay = mockk<NostrRelayClient>()
    private val rss = mockk<RssHelper>(relaxed = true)
    private val scraper = mockk<WebFeedScraper>()
    private val filters = mockk<com.mckimquyen.reader.infrastructure.filter.SmartFilterManager> {
        io.mockk.every { applyRules(any()) } answers { firstArg() }
    }
    private val service = LocalRssSv(
        context = ApplicationProvider.getApplicationContext(), articleDao = mockk<ArticleDao>(relaxed = true),
        feedDao = mockk(relaxed = true), rssHelper = rss, notificationHelper = mockk(relaxed = true),
        accountDao = mockk(relaxed = true), groupDao = mockk(relaxed = true), feedHealthDao = mockk<FeedHealthDao>(relaxed = true),
        ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
        workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
        smartFilterManager = filters, webFeedScraper = scraper,
        smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
        retractionRadar = mockk(relaxed = true), nostrRelayClient = relay,
    )
    private val feed = Feed(
        id = "nostr-feed", name = "Nostr", url = "nostr:$npub", groupId = "group", accountId = 1,
        icon = "icon", sourceType = Feed.NOSTR_SOURCE_TYPE, sourceUrl = "nostr:$npub",
    )

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }

    @Test fun theSourceTypeConstant_isNostr() = assertEquals("NOSTR", Feed.NOSTR_SOURCE_TYPE)

    @Test fun queryFeedArticles_readsTheNotesOfTheNpub_throughTheRelayClient() = runBlocking {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER)
        val articles = service.syncSingleFeed(feed)?.articles.orEmpty()
        assertEquals(listOf("Hello nostr, first note", "Older note"), articles.map { it.title })
        assertTrue(articles.all { it.feedId == "nostr-feed" && it.link.startsWith("https://njump.me/note1") })
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
    }

    @Test fun queryFeedArticles_neverCallsTheRssHelperOrTheWebScraper() = runBlocking {
        coEvery { relay.fetch(any()) } returns notes(NostrTestEvents.PLAIN)
        service.syncSingleFeed(feed)
        coVerify(exactly = 0) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { rss.queryRssIcon(any(), any()) }
    }

    @Test fun anEmptyRelayAnswer_isASyncFailure_notAnEmptyFeed() = runBlocking {
        coEvery { relay.fetch(any()) } returns emptyList()
        assertEquals(null, service.syncSingleFeed(feed))
    }

    @Test fun aStoredUrlThatIsNotAnNpub_isAFailure_andNeverReachesTheRelay() = runBlocking {
        val broken = feed.copy(url = "nostr:not-an-npub", sourceUrl = "nostr:not-an-npub")
        assertEquals(null, service.syncSingleFeed(broken))
        coVerify(exactly = 0) { relay.fetch(any()) }
    }

    @Test fun anRssFeed_stillUsesTheRssHelper() = runBlocking {
        coEvery { rss.queryRssXml(any(), any()) } returns emptyList()
        service.syncSingleFeed(feed.copy(sourceType = Feed.RSS_SOURCE_TYPE, url = "https://example.test/feed"))
        coVerify(exactly = 1) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { relay.fetch(any()) }
    }
}
```

- [ ] **Step 2: Viết test đỏ** `NostrNoWebFetchTest.kt` (MockWebServer ghi số yêu cầu; một test cho mỗi đường tải):

```kotlin
package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.toListItem
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrNoWebFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><p>should never be read</p></body></html>"))
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parseFullContent_forANostrArticle_returnsEmptyWithoutOpeningAConnection() = runBlocking {
        val result = helper.parseFullContent(server.url("/note").toString(), "t", Feed.NOSTR_SOURCE_TYPE)
        assertEquals("", result)
        assertEquals(0, server.requestCount)
    }

    @Test fun parseFullContent_forAnRssArticle_stillFetches() = runBlocking {
        helper.parseFullContent(server.url("/page").toString(), "t", Feed.RSS_SOURCE_TYPE)
        assertEquals(1, server.requestCount)
    }

    @Test fun queryRssIcon_forANostrFeed_doesNotWriteAFaviconAddress() = runBlocking {
        val feedDao = mockk<FeedDao>(relaxed = true)
        val feed = Feed(id = "n", name = "Nostr", url = "https://relay-host.test/x", groupId = "g", accountId = 1, sourceType = Feed.NOSTR_SOURCE_TYPE)
        helper.queryRssIcon(feedDao, feed)
        coVerify(exactly = 0) { feedDao.update(any()) }
    }

    @Test fun precache_forANostrArticle_neverFetchesFullContentOrImages() = runBlocking {
        val article = Article(
            id = "a1", date = Date(), title = "t", rawDescription = "<p>x</p>", shortDescription = "x",
            img = "https://example.com/banner.jpg", link = server.url("/note").toString(), feedId = "nostr-feed", accountId = 1,
        )
        val articleDao = mockk<ArticleDao>(relaxed = true)
        val feedDao = mockk<FeedDao>(relaxed = true)
        val rss = mockk<RssHelper>(relaxed = true)
        val images = mockk<ImageLoader>(relaxed = true)
        coEvery { feedDao.queryById("nostr-feed") } returns Feed(id = "nostr-feed", name = "N", url = "nostr:x", groupId = "g", accountId = 1, sourceType = Feed.NOSTR_SOURCE_TYPE)
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(article.toListItem())
        val service = spyk(OfflinePrecacheService(ApplicationProvider.getApplicationContext(), articleDao, rss, images, Dispatchers.Unconfined, feedDao))
        every { service.isOnWifi() } returns true

        service.precacheLatestUnread(1)

        coVerify(exactly = 0) { rss.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { rss.extractImageUrls(any()) }
        io.mockk.verify(exactly = 0) { images.enqueue(any()) }
        assertTrue(server.requestCount == 0)
    }
}
```

- [ ] **Step 3: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f3-t6.exit; (./gradlew :app:testDevDebugUnitTest --tests '*LocalRssSvNostrSyncTest' --tests '*NostrNoWebFetchTest' > /tmp/f3-t6-red.log 2>&1; echo $? > /tmp/f3-t6.exit) &`. Đỏ đúng lý do: lỗi biên dịch `Unresolved reference: NOSTR_SOURCE_TYPE`/`nostrRelayClient` (đây là lỗi biên dịch chứ không phải đỏ hành vi: sau khi thêm hằng và tham số trống, phải chạy lại và thấy các test hành vi đỏ vì **đúng lý do** — `parseFullContent` mở kết nối, precache gọi `parseFullContent`, `syncSingleFeed` gọi `queryRssXml` — trước khi viết nhánh thật).

- [ ] **Step 4: `Feed.kt` và `NostrRelayClient` (Hilt)**
  - `Feed.kt` companion: thêm `const val NOSTR_SOURCE_TYPE = "NOSTR"` ngay dưới `WEB_SOURCE_TYPE` (không đổi cột, không Room).
  - `NostrRelayClient.kt` (Task 4): đánh dấu lớp `@Singleton` và constructor công khai `@Inject constructor(client: OkHttpClient, @IODispatcher ioDispatcher: CoroutineDispatcher)`; constructor `internal` 5 tham số giữ nguyên cho test. Import `javax.inject.Inject`, `javax.inject.Singleton`, `com.mckimquyen.reader.infrastructure.di.IODispatcher`. Không thêm module Hilt mới (cùng kiểu `NewsletterUrlResolver`).

- [ ] **Step 5: Nhánh NOSTR trong `LocalRssSv.queryFeedArticles`** (đọc lại hàm ngay trước khi sửa; chỉ thêm, không đổi nhánh WEB):

```kotlin
// constructor: thêm cuối danh sách tham số
    private val nostrRelayClient: com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient,

// queryFeedArticles: ngay sau dòng kiểm READ_LATER, TRƯỚC dòng `if (feed.sourceType != Feed.WEB_SOURCE_TYPE)`
        if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return queryNostrArticles(feed)

// thêm hàm riêng tư (cuối lớp)
    private suspend fun queryNostrArticles(feed: Feed): List<Article> {
        val npub = (feed.sourceUrl ?: feed.url).removePrefix(
            com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.FEED_URL_PREFIX,
        )
        val authorHex = com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.npubToHex(npub)
            ?: throw java.io.IOException("Nostr feed address is not a public key")
        val events = nostrRelayClient.fetch(authorHex)
        if (events.isEmpty()) throw java.io.IOException("Nostr relays returned no notes")
        return com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.toArticles(
            events = events, feedId = feed.id, accountId = feed.accountId, nowMillis = System.currentTimeMillis(),
        )
    }
```
  Thông điệp lỗi **không** chứa npub hay relay. Thêm `nostrRelayClient = mockk(relaxed = true)` vào bốn lần dựng `LocalRssSv(` trong test cũ (`LocalRssSvWebFeedSyncTest`, `WebFeedRoomEndToEndTest`, `RetractionRadarSyncTest` hai chỗ); nếu `WebFeedRoomEndToEndTest` dựng Hilt/Room thật thì đọc nó trước.

- [ ] **Step 6: Chặn tải web cho NOSTR**
  - `RssHelper.parseFullContent`: dòng đầu thân hàm (trước `withContext`): `if (sourceType == Feed.NOSTR_SOURCE_TYPE) return ""`.
  - `RssHelper.queryRssIcon`: `if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return` ở đầu (trước `withContext`).
  - `OfflinePrecacheService`: trong vòng lặp, ngay sau khi có `sourceType`, thêm `if (sourceType == Feed.NOSTR_SOURCE_TYPE) continue` (bỏ qua cả toàn văn lẫn ảnh; **không** đếm vào `cachedCount`). Không đổi nhánh WEB.
  - `ReadingViewModel.internalRenderFullContent` đã truyền `sourceType` cho `parseFullContent` nên được chặn ở lớp trên; thêm một test hồi quy nếu `ReadingViewModelTest` đã có mẫu dựng sẵn (đọc trước), nếu không thì ghi vào báo cáo là chỉ kiểm bằng test `parseFullContent`.

- [ ] **Step 7: Chạy xanh** (nền, chờ `.exit`) cùng các lớp hồi quy: `*LocalRssSvNostrSyncTest`, `*NostrNoWebFetchTest`, `*LocalRssSvWebFeedSyncTest`, `*WebFeedRoomEndToEndTest`, `*RetractionRadarSyncTest`, `*OfflinePrecacheServiceTest`, `*RssHelper*`. Kỳ vọng 0 lỗi. **Phải đổi mã Hilt**: sau khi sửa constructor, chạy **clean build** (`./gradlew clean :app:testDevDebugUnitTest ...` hoặc ít nhất `kaptDevDebugKotlin --rerun-tasks`) để Dagger sinh lại.

- [ ] **Step 8: Mutation** (`/tmp/f3_t6_batch.sh`, một batch nền, `cmp` cuối, `ALLDONE`; lớp `LocalRssSvNostrSyncTest` + `NostrNoWebFetchTest` + `OfflinePrecacheServiceTest`):
  - `LocalRssSv.kt`: bỏ dòng nhánh `NOSTR_SOURCE_TYPE` (đỏ: gọi `queryRssXml`); `isEmpty()` bỏ ném lỗi (đỏ: feed rỗng không còn là lỗi); `?: throw` thành `?: return emptyList()` (đỏ ở "không phải npub"); `removePrefix` bỏ (đỏ: npub sai).
  - `RssHelper.kt`: bỏ `NOSTR` trong `parseFullContent` (đỏ: mở kết nối); bỏ chặn trong `queryRssIcon`.
  - `OfflinePrecacheService.kt`: bỏ `continue` cho NOSTR (đỏ: gọi `parseFullContent`/ảnh).
  - `Feed.kt`: `NOSTR_SOURCE_TYPE = "NOSTR"` thành `"WEB"` (đỏ: test hằng và test rẽ nhánh).
  Sống sót ⇒ thêm test, không bỏ đột biến.

- [ ] **Step 9: Commit** sau exit=0, XML `failures=0`:
`git add app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt app/src/test/java/com/mckimquyen/reader/domain/sv app/src/test/java/com/mckimquyen/reader/infrastructure/rss/NostrNoWebFetchTest.kt && git commit -m "feat(front-02b): sync Nostr sources through the relays and never fetch the web for them"` (kiểm `git status --short` chỉ chứa các tệp trên trước khi commit).

---

### Task 7: Nối `npub` vào ô Thêm nguồn (`SubscribeViewModel.search`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` (`search()`, `subscribe()`, constructor)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrTest.kt` (hành vi), `.../SubscribeViewModelNostrSourceTest.kt` (thứ tự và không log)
- Sửa 3 test cũ dựng `SubscribeViewModel(...)`: `SubscribeViewModelNewsletterTest`, `SubscribeViewModelWebFeedTest`, `SubscribeViewModelSocialTest` thêm `nostrRelayClient = mockk(relaxed = true)`.

**Interfaces:**
- Consumes: `NostrKeys.isSecretKey/npubToHex` (Task 1), `NostrRelayClient.fetch(authorHex)` (Task 4, đã `@Inject`, Task 6), `NostrFeedMapper.feedName/feedUrl/toArticles` (Task 5), `Feed.NOSTR_SOURCE_TYPE` (Task 6).
- Produces: constructor `SubscribeViewModel` có thêm tham số **cuối** `private val nostrRelayClient: NostrRelayClient`. Không đổi chữ ký hàm công khai.

**Quyết định thiết kế (đã đọc `search()`, `confirmWebFeed()`, `subscribe()`, `WebFeedPreviewContent`; không đoán):**
1. Nostr đi qua **luồng xem trước RSS sẵn có**, không qua nhánh `webPreview`: `feed` + `articles` vào state, `switchPage(false)`; người dùng chọn nhóm rồi bấm Đăng ký như mọi nguồn; `subscribe()` lưu `Feed(sourceType = NOSTR)`. Lý do: `WebFeedPreviewContent` dùng chuỗi nói về "trang web" (`web_feed_preview_notice`), sai với Nostr, mà **không được thêm chuỗi mới**.
2. **Chỉ tài khoản Local** (như nguồn WEB và `confirmWebFeed`): tài khoản Fever/Google Reader sẽ gửi `nostr:…` lên máy chủ từ xa. Không phải Local ⇒ `npub` **không** được nhận, rơi về luồng cũ y nguyên.
3. **`nsec` chặn trước mọi đường:** nếu rơi về luồng cũ, `https://nsec1…` sẽ thành tên máy gửi ra DNS. Gặp `nsec` ⇒ báo lỗi, **xóa ô nhập** (không giữ khóa bí mật trong state), không gọi `NostrRelayClient`, `rssHelper`, `newsletterUrlResolver`, `webFeedScraper`. Chuỗi lỗi **tái dùng** `R.string.web_feed_invalid_url` ("Use a public HTTPS URL…"): không chính xác tuyệt đối nhưng không thêm chuỗi (ghi vào báo cáo).
4. **Relay lỗi hoặc rỗng ⇒ báo lỗi, KHÔNG rơi về web** (nếu rơi về, `npub` sẽ bị gửi ra như một địa chỉ web). Tái dùng `R.string.web_feed_fetch_failed`. Thông điệp lỗi không chứa `npub`.
5. `subscribe()` với nguồn NOSTR ép `isFullContent = false` (`parseFullContent` trả rỗng cho NOSTR nên trang đọc sẽ trắng). Giới hạn đã biết: người dùng vẫn có thể bật "toàn văn" sau này trong tùy chọn nguồn; trang đọc sẽ rỗng; ghi vào báo cáo.
6. `isFeedExist` kiểm bằng `nostr:<npub chữ thường>`.

- [ ] **Step 1: Viết test đỏ** `SubscribeViewModelNostrTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelNostrTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val relay = mockk<NostrRelayClient>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val npub = requireNotNull(NostrKeys.hexToNpub(NostrTestEvents.AUTHOR))
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER)
        viewModel = SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rssService, rssHelper = rssHelper,
            androidStringsHelper = strings, webFeedScraper = scraper, newsletterUrlResolver = resolver, nostrRelayClient = relay,
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }
    private val state get() = viewModel.subscribeUiState.value

    private fun TestScope.search(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        advanceUntilIdle()
    }

    private fun verifyNothingWentToTheWeb() {
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
        coVerify(exactly = 0) { resolver.resolveToRssUrl(any()) }
        coVerify(exactly = 0) { scraper.discover(any()) }
    }

    @Test fun anNpub_showsAPreviewOfItsNotes_throughTheRelayOnly() = runTest(dispatcher) {
        search(npub)
        val feed = requireNotNull(state.feed)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, feed.sourceType)
        assertEquals("nostr:$npub", feed.url)
        assertEquals("nostr:$npub", feed.sourceUrl)
        assertEquals("Nostr · " + npub.take(8) + "…" + npub.takeLast(4), feed.name)
        assertEquals(listOf("Hello nostr, first note", "Older note"), state.articles.map { it.title })
        assertTrue(state.articles.all { it.feedId == feed.id })
        assertFalse(state.isSearchPage)
        assertFalse(state.webPreview)
        assertTrue(state.lockLinkInput)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNpub_withSpacesAndUppercase_isRecognisedToo() = runTest(dispatcher) {
        search("  " + npub.uppercase() + " \n")
        assertEquals("nostr:$npub", state.feed?.url)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun theBoxShowsTheNostrAddress_notAnHttpsGuess() = runTest(dispatcher) {
        search(npub)
        assertEquals("nostr:$npub", state.linkContent)
    }

    @Test fun theAlreadySubscribedCheck_usesTheNostrAddress_andSkipsTheRelay() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("nostr:$npub") } returns true
        search(npub)
        coVerify(exactly = 1) { localService.isFeedExist("nostr:$npub") }
        coVerify(exactly = 0) { relay.fetch(any()) }
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.already_subscribed), state.errorMessage)
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun whenTheRelaysAnswerNothing_itIsAnError_andNothingFallsBackToTheWeb() = runTest(dispatcher) {
        coEvery { relay.fetch(any()) } returns emptyList()
        search(npub)
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.web_feed_fetch_failed), state.errorMessage)
        assertEquals(null, state.feed)
        assertFalse(state.lockLinkInput)
        assertFalse(state.errorMessage.contains(npub))
        verifyNothingWentToTheWeb()
    }

    @Test fun whenTheRelayClientThrows_itIsAnError_andNothingFallsBackToTheWeb() = runTest(dispatcher) {
        coEvery { relay.fetch(any()) } throws java.io.IOException("socket closed")
        search(npub)
        assertEquals(null, state.feed)
        assertTrue(state.errorMessage.isNotEmpty())
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun aSecretKey_isRefused_clearedFromTheBox_andGoesNowhere() = runTest(dispatcher) {
        search(nsec)
        assertEquals("", state.linkContent)
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.web_feed_invalid_url), state.errorMessage)
        assertEquals(null, state.feed)
        assertFalse(state.lockLinkInput)
        coVerify(exactly = 0) { relay.fetch(any()) }
        verifyNothingWentToTheWeb()
    }

    @Test fun aSecretKey_inUppercaseWithSpaces_isRefusedToo() = runTest(dispatcher) {
        search("  " + nsec.uppercase())
        assertEquals("", state.linkContent)
        coVerify(exactly = 0) { relay.fetch(any()) }
        verifyNothingWentToTheWeb()
    }

    @Test fun onANonLocalAccount_anNpubIsNotNostr_andTheOldFlowRunsUntouched() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        coEvery { rssHelper.searchFeed(any()) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(any()) } returns null
        search(npub)
        coVerify(exactly = 0) { relay.fetch(any()) }
        coVerify(exactly = 1) { rssHelper.searchFeed("https://$npub") }
    }

    @Test fun anOrdinaryAddress_neverTouchesTheRelay() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed(any()) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(any()) } returns null
        coEvery { scraper.discover(any()) } returns com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult.Unsupported
        search("example.com/feed.xml")
        coVerify(exactly = 0) { relay.fetch(any()) }
    }

    // ---- subscribe
    @Test fun subscribe_savesTheNostrFeedInTheChosenGroup_withFullContentOff() = runTest(dispatcher) {
        search(npub)
        viewModel.selectedGroup("group_1")
        viewModel.changeParseFullContentPreset()
        assertTrue(state.parseFullContentPreset)
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            localService.subscribe(
                match { it.sourceType == Feed.NOSTR_SOURCE_TYPE && it.groupId == "group_1" && !it.isFullContent && it.url == "nostr:$npub" },
                match { it.size == 2 },
            )
        }
        assertFalse(state.visible)
    }

    @Test fun subscribe_stillHonoursFullContentForAnOrdinaryFeed() = runTest(dispatcher) {
        coEvery { rssHelper.searchFeed("https://example.com/feed.xml") } returns com.mckimquyen.reader.domain.model.feed.FeedWithArticle(
            Feed(id = "f", name = "N", url = "https://example.com/feed.xml", groupId = "", accountId = 1), emptyList(),
        )
        search("https://example.com/feed.xml")
        viewModel.selectedGroup("group_1")
        viewModel.changeParseFullContentPreset()
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) { localService.subscribe(match { it.isFullContent && it.sourceType == Feed.RSS_SOURCE_TYPE }, any()) }
    }
}
```

Đã đọc: `AccountType.Fever` có thật (`AccountType(2)`); sau xem trước RSS thành công `lockLinkInput` vẫn `true` (`search()` chỉ hạ nó khi lỗi hoặc đã đăng ký), nên nhánh Nostr đặt `true` giống hệt.

- [ ] **Step 2: Không có bước sửa test:** test ở Step 1 đã gồm import `TestScope`, `AccountType.Fever`, và khẳng định `lockLinkInput` đúng hành vi đã đọc. Test nguồn ở Step 3 chỉ xét mã (bỏ comment và `import`).

- [ ] **Step 3: Test nguồn** `SubscribeViewModelNostrSourceTest.kt` (không comment/import):

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubscribeViewModelNostrSourceTest {
    private val source = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt").readLines()
        .filterNot { it.trimStart().let { t -> t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ") } }
        .joinToString("\n")
    private val search = source.substringAfter("fun search()").substringBefore("fun confirmWebFeed()")

    @Test fun npubIsResolvedBeforeFormatUrl() {
        val nostr = search.substringAfter("npubToHex(")
        assertTrue(nostr.contains("NostrRelayClient"))
        assertTrue(search.indexOf("npubToHex(") < search.indexOf("formatUrl()"))
    }

    @Test fun nsecGuardIsBeforeEveryNetworkResolver() {
        val guard = search.indexOf("isSecretKey(")
        assertTrue(guard >= 0)
        listOf("nostrRelayClient.fetch(", "rssHelper.searchFeed(", "resolveToRssUrl(", "webFeedScraper.discover(")
            .forEach { call -> assertTrue("guard must be before $call", guard < search.indexOf(call)) }
    }

    @Test fun nostrFailureDoesNotFallThroughToWeb() {
        val branch = search.substringAfter("npubToHex(").substringBefore("formatUrl()")
        assertFalse(branch.contains("resolveToRssUrl("))
        assertFalse(branch.contains("webFeedScraper.discover("))
    }

    @Test fun newNostrCodeDoesNotLogKeysOrNotes() {
        val file = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt")
            .readLines().filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("//") }.joinToString("\n")
        val nostr = file.substringAfter("npubToHex(").substringBefore("formatUrl()")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, nostr.contains(it)) }
    }
}
```

- [ ] **Step 4: Chạy đỏ** (nền, output file, đợi `.exit`):
`rm -f /tmp/f3-t7.exit; (./gradlew :app:testDevDebugUnitTest --tests '*SubscribeViewModelNostrTest' --tests '*SubscribeViewModelNostrSourceTest' > /tmp/f3-t7-red.log 2>&1; echo $? > /tmp/f3-t7.exit) &`
Đỏ hợp lệ có thể là compile error vì ctor/enum/Hilt; sửa test trước cho compile; sau đó XML mới phải chứng minh hành vi đúng: chưa có resolver; nsec bị gửi vào search; npub chưa gọi relay. Đọc XML chỉ sau `.exit`.

- [ ] **Step 5: Minimal implementation** (đọc lại toàn bộ `search()` + `subscribe()` trước sửa):
  1. Thêm `nostrRelayClient` tham số ctor; update ba test Social/Newsletter/WebFeed.
  2. Đầu `search()`, **trước** `formatUrl()`: `val typed = linkContent`; nếu `NostrKeys.isSecretKey(typed)` thì set `linkContent = ""`, `errorMessage = web_feed_invalid_url`, trả về ngay. Nếu Local và `NostrKeys.npubToHex(typed) != null`, đặt `nostrInput = typed.trim().lowercase(Locale.ROOT)`; kiểm `isFeedExist("nostr:$nostrInput")`; nếu đã có thì báo already subscribed và return; gọi `nostrRelayClient.fetch(authorHex)`, map bằng `NostrFeedMapper.toArticles` với `feedId` tạo theo account UUID, `accountId=currentAccountId`, `nowMillis=System.currentTimeMillis()`; nếu rỗng/error đặt `web_feed_fetch_failed`, hạ `lockLinkInput=false`, `linkContent=nostr:…` (không npub trong lỗi), return; nếu thành công đặt Feed/Nostr + articles vào state, `linkContent=feed.url`, title=feed.name, `lockLinkInput=true`, `webPreview=false`, `isSearchPage=false`, return. Không gọi `formatUrl`, RSS, newsletter, scraper.
  3. Nếu không `npub`/tài khoản không Local, thực thi phần cũ nguyên vẹn (giữ `SocialFeedUrlResolver` trước `formatUrl` và fallback chữ gốc).
  4. Trong `subscribe()`, khi `feed.sourceType == NOSTR_SOURCE_TYPE`, copy thêm `isFullContent = false`; các nguồn khác giữ preset như cũ. Không sửa `WebFeedPreviewContent` hay chuỗi.
  5. Nút Hủy: không cần sửa UI. Ở trang xem trước không-WEB, `SubscribeDialog.dismissButton` đã hiện "Hủy" (`R.string.cancel`) và gọi `hideDrawer()`; hộp thoại đóng, và lần mở sau `reset()`/`init()` xóa `feed` và `articles`. Thêm một test: sau xem trước Nostr, `hideDrawer()` rồi `reset()` thì `feed == null` và `articles` rỗng (không rò rỉ sang lần tìm sau).

- [ ] **Step 6: Chạy xanh** (nền; chờ `.exit`, đọc XML): `SubscribeViewModelNostrTest`, `SubscribeViewModelNostrSourceTest`, `SubscribeViewModelSocialTest`, `SubscribeViewModelSocialSourceTest`, `SubscribeViewModelWebFeedTest`, `SubscribeViewModelNewsletterTest`, `LocalRssSvNostrSyncTest`. 0 lỗi. Thêm test cho Cancel/Back và account non-Local như phần trên nếu còn nhánh chưa kiểm.
- [ ] **Step 7: Mutation** (`/tmp/f3_t7_batch.sh`, một script nền, không sửa file đến `ALLDONE`; cuối `cmp`). Lớp hành vi + source: bỏ secret guard; cho `nsec` đi qua `formatUrl`; dùng npub trước/sau format đổi chỗ; bỏ `isFeedExist`; thay feed URL `nostr:` bằng npub thô; bỏ `relay.fetch`; lỗi relay tiếp tục rơi xuống RSS resolver; quên map bài; `isFullContent` không ép false; cho non-local account dùng Nostr; source test bỏ log guard. Từng đột biến phải làm test đỏ; nếu sống sót thì thêm test. Test hồi quy xã hội/newsletter/web phải luôn xanh trên mã gốc.
- [ ] **Step 8: Commit** sau exit=0/XML sạch:
`git add app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelSocialTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNewsletterTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelWebFeedTest.kt && git commit -m "feat(front-02b): preview and subscribe to a public Nostr npub from Add Source"`

---

### Task 8: Kiểm tổng thể, probe relay thật Tecno, báo cáo

**Files:**
- Tạm, không commit: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayProbe.kt`
- Create: `doc/task/done/FRONT-02b_nostr_DONE.md`
- Modify: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md` (đầu file; trước khi dọn hỏi owner task gốc giữ/chuyển)

- [ ] **Step 1: Full suite** nền, `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f3-full.log 2>&1`; ghi `.exit`, đếm tests/skipped/failures/errors từ XML mới. Nếu đỏ, đọc lỗi; test ngoài task timeout giống Oracle lần trước thì chạy riêng rồi full lần hai; báo nguyên văn và không giấu.
- [ ] **Step 2: Lint** nền `./gradlew lintDevDebug`; đếm severity (baseline 2.465 Warning, 10 Information); 0 error, 0 issue trong tệp FRONT-02b.
- [ ] **Step 3: Build** nền `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; exit 0; `git status --short app/schemas` rỗng; `git diff --stat -- app/src/main/res` rỗng.
- [ ] **Step 4: Kiểm thiết bị Tecno**: cổng chỉ đọc (`adb devices`, dumpsys instrumentation/topResumed, `ps`, màn hình mở khóa); dừng nếu app lạ/khóa/biometric. Sao lưu `/tmp/o03p-backup.py`, ghi integrity/schema/article count, băm `settings.preferences_pb`. Cài dev + androidTest APK bằng `adb -s 115333744A005844 install -r -t`.
- [ ] **Step 5: Probe relay thật**: tạm `NostrRelayProbe` với `@RunWith(AndroidJUnit4::class)`, dựng `OkHttpClient` đúng như `OkHttpClientModule.provideOkHttpClient` (androidTest không có Hilt test app nên không lấy qua EntryPoint): `cachingHttpClient(cacheDirectory = null).newBuilder().dns(AppDns(dohProviderSupplier = { DohProviderPref.fromId(context.dohProviderId) })).addInterceptor(ConditionalCacheInterceptor).addNetworkInterceptor(UserAgentInterceptor).build()`, để đi qua DoH, `trustAllCerts` và các interceptor của app (báo cáo ghi rõ client này **sao chép** cấu hình module, không phải đối tượng Hilt thật), `NostrRelayClient.fetch` bằng npub test công khai `npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m` (khóa công khai của ví dụ NIP-19, **không phải khóa bí mật**). Gọi `am instrument` (không `connected*`, không chạm màn hình); xem logcat chỉ các số: relay thành công/thất bại, số note hợp lệ, số tiêu đề không rỗng; không ghi npub, nội dung hay relay URL. Nếu không có bài hợp lệ, ghi nguyên trạng; không sửa test để khớp.
- [ ] **Step 6: Khôi phục** force-stop chỉ `com.mckimquyen.reader`; `python3 /tmp/o03p-restore.py`; kiểm Room/`-shm`/`-wal`/DataStore/`shared_prefs` BYTE IDENTICAL và hash DataStore; gỡ test package; xóa helper; kiểm launcher/không instrumentation; `git diff --stat -- app/src/main` rỗng. **Không chạm UI**, do quy tắc không bấm tọa độ.
- [ ] **Step 7: Báo cáo**, tiếng Việt, đúng sự thật: phạm vi; chỉ đọc, 2 relay và quyền riêng tư (relay biết npub + IP); BIP-173/BIP-340 19 vectors, 120 ghi chú thật đã kiểm bằng nguyên mẫu độc lập trước implementation (chỉ nêu kiểm tại thời điểm khảo sát, không dùng làm bằng chứng code sau nếu chưa chạy test tương đương); test thực sự chạy; mạng thật trên Tecno; việc đã restore; **Nostr chưa UI-smoke**; giới hạn hai relay, không tên profile/ảnh/replies, Fever/Google Reader không hỗ trợ vì chỉ Local, nút toàn văn bị tắt lúc đăng ký và nếu bật lại sẽ rỗng, P2P sync bỏ qua `nostr:`; ký tự secret lỗi tái dùng chuỗi URL không hoàn toàn chính xác; không kiểm TalkBack/locale ngoài EN-VI.
- [ ] **Step 8: Hỏi owner bằng `AskUserQuestion`** task gốc FRONT-02 nay đã đủ Mastodon/Bluesky/Nostr qua Nostr hẹp, nhưng các mục nguyên gốc (AT Protocol API, Nostr profiles/replies/images) không làm: giữ trong `todo/` với ghi chú hoàn tất ba MVP hay chuyển sang `done/` với phần chưa làm ghi rõ. Khuyến nghị giữ trong `todo/` (phạm vi gốc còn thiếu).
- [ ] **Step 9: Cập nhật tài liệu**: `doc/feature.md` ghi FRONT-02 đã hoàn thành bản thu hẹp, ba nguồn; gỡ ghi chú “Nostr chưa làm” khỏi task gốc nếu owner chọn giữ, thay bằng phần còn thiếu; thêm link báo cáo. Update spec “Điều chỉnh khi thực thi” (ví dụ khóa Nostr chỉ Local, fallback không đi web, link `njump.me`, DoH thực tế). Không dịch chuỗi mới (không có).
- [ ] **Step 10: Commit docs** (sau khi owner trả lời): `git add doc/feature.md doc/task/todo/FRONT-02_decentralized-web3-social.md doc/task/done/FRONT-02b_nostr_DONE.md docs/superpowers/specs/2026-11-14-front-02b-nostr-design.md && git commit -m "docs(front-02b): report Nostr support and its verified limits"`. Không push, PR, `.idea`.

---

## Self-review

- **Phủ spec:** NIP-19 npub/note1 + từ chối nsec (Task 1); BIP-340 chính thức (2); id/tag/kind/pubkey/event (3); relay/WebSocket/giới hạn/đóng (4); map Article/HTML/date/link (5); Room v22, sync, không fetch toàn văn/icon/precache (6); ô Thêm nguồn, fallback an toàn, subscribe (7); test/build/lint/relay thật/restore/report (8).
- **Ngoài phạm vi, ghi rõ:** không `nprofile`, hồ sơ, ảnh, replies, đăng nhập, khóa bí mật, relay cài đặt, Fever/Google Reader, LAN P2P với `nostr:`, Android UI smoke, Gemini, tiền/quảng cáo.
- **Điểm mở đã giải (không còn quyết định treo):** fixture `escapes` có trong Task 3; test hủy kết nối có import và `@OptIn` ngay trong Task 4; giá trị tên nguồn và tiêu đề đã đo cố định trong Task 5; hai nơi dựng `LocalRssSv`/`SubscribeViewModel` trong test cũ được liệt kê đủ ở Task 6 và 7; `AccountType.Fever` và đường Hủy đã đọc ở Task 7; cách dựng client probe đã chốt ở Task 8. Các câu "đọc lại hàm ngay trước khi sửa" là chủ ý (mã có thể đổi giữa các task), không phải chỗ trống.
- **Kiểu/tên:** `npubToHex: String?`, `Bip340.verify(pubkeyHex,messageHex,signatureHex)`, `NostrEvent.parse/isValid`, `NostrRelayClient.fetch(authorHex)`, `NostrFeedMapper.toArticles`, `Feed.NOSTR_SOURCE_TYPE`; dùng thống nhất giữa các task.
- **Lệnh tự-review cuối:** `grep -nE 'TBD|TODO|Sửa trước khi|Điều chỉnh ngay|Lưu ý trước khi chạy' docs/superpowers/plans/2026-11-14-front-02b-nostr.md` phải rỗng. Còn đúng một quyết định chủ ý dành cho owner: giữ hay chuyển task gốc ở Task 8 Step 8.
