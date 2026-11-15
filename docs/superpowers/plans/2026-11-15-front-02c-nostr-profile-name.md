# FRONT-02c — Dùng tên hồ sơ Nostr cho nguồn tin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Khi người dùng dán `npub` vào ô Thêm nguồn, app cũng hỏi hồ sơ Nostr công khai (kind 0) và đặt tên nguồn `Nostr · <tên>` nếu tìm được; không có hồ sơ thì giữ tên `Nostr · npub1…` cũ.

**Architecture:** Hàm thuần `NostrProfileName` đọc tên từ `content` JSON không đáng tin. `NostrEvent` có lối kiểm hợp lệ rõ ràng cho kind 0, không nới lỏng kiểm kind 1 đang dùng cho ghi chú. `NostrRelayClient` gửi hai bộ lọc trong cùng một `REQ`, trả ghi chú và tên hồ sơ; preview dùng tên, đồng bộ nền không đổi tên nguồn đã lưu.

**Tech Stack:** Kotlin 2.1, OkHttp 5.0.0-alpha.11, `org.json`, JUnit4, MockK, Robolectric (đã có). **Không dependency mới.**

## Global Constraints

- Nhánh `dev` only; không worktree, push, PR; không commit `.idea`.
- Spec: `docs/superpowers/specs/2026-11-15-front-02c-nostr-profile-name-design.md` (bản đã commit `854ec392`; quyết định owner ghi sau đó ở commit docs kế tiếp).
- Giữ **hai bộ lọc trong một WebSocket `REQ`**: kind 1 `limit:50`; kind 0 `limit:1`; cùng author và subscription id `f2`; cùng EOSE, timeout 8 s, giới hạn 50 event/64 KiB/20 note; luôn đóng socket đúng thứ tự (`onClosing` hoàn tất chờ; sau đó gửi CLOSE rồi `close`, không `cancel` ngay).
- Kind 0 **không** được coi là note, không đi vào `NostrFeedMapper`; phải qua `id`, chữ ký BIP-340 và `pubkey` như event khác. **Không nới** `NostrEvent.isValid(expectedPubkeyHex)` dành cho kind 1; thêm hàm/kiểm riêng tường minh để unit test chứng minh boundary.
- Lấy `display_name`, rỗng thì `name`; chuẩn hóa khoảng trắng, bỏ ký tự điều khiển, cắt tối đa 40 Unicode code points, không cắt giữa emoji. Tên tự khai, **không** kiểm `nip05`; tiền tố luôn `Nostr · `.
- Hồ sơ không có/sai JSON/sai chữ ký/sai tác giả ⇒ fallback `Nostr · npub1…`; nhiều hồ sơ ⇒ `created_at` mới nhất.
- Tên chỉ áp dụng lúc preview/tìm. **Không đổi tên feed khi sync nền**, vì chủ nguồn có thể đã đổi tên.
- Không log `npub`, tên, nội dung event, relay URL hay JSON; không ảnh/profile picture, `about`, `nip05` verification, Room/DataStore/manifest/chuỗi mới.
- TDD thật: chạy đỏ trước; đọc XML chỉ sau `.exit` tồn tại; sửa mã tối thiểu; mutation bằng `/tmp/s01_mut.sh` có backup + `cmp`, một batch nền, không đụng file đột biến đến `ALLDONE`.
- Gradle nền, output file, chờ `.exit`/`ALLDONE`; thông báo launcher completed không phải kết quả.
- zsh: không `echo ===`, không `grep --include`; pattern đặt trong nháy; sửa tệp bằng Python; Python 3.9 với chữ Việt dùng `# -*- coding: utf-8 -*-` + `open(..., encoding="utf-8")`.
- `org.json` trên JVM cần Robolectric; `android.util.Log` cũng cần Robolectric; test nguồn chỉ xét mã, bỏ comment/import; không MockK trên Android (A50s Android 11 lỗi `IncompatibleClassChangeError`).
- Thiết bị **CHỈ Samsung A50s `R58MA6WYRPE`** (owner 2026-11-15: “dùng a50s thay tecno đi”); không Tecno KJ7, không Pixel. Gate trước mọi action, chỉ `adb -s R58MA6WYRPE`; sao lưu/khôi phục byte-exact bằng `/tmp/a50s-backup.py`, `/tmp/a50s-restore.py`; không `connected*`, không chạm tọa độ, helper probe tạm rồi xóa.

---

## Cấu trúc file

| Tệp | Việc | Task |
|---|---|---|
| `infrastructure/social/nostr/NostrProfileName.kt` | JSON `content` hồ sơ ⇒ tên an toàn hoặc null | 1 |
| `infrastructure/social/nostr/NostrEvent.kt` | kiểm kind 0 rõ ràng, giữ ranh giới kind 1 | 2 |
| `infrastructure/social/nostr/NostrFetchResult.kt` | data class trả notes + `profileName` | 3 |
| `infrastructure/social/nostr/NostrRelayClient.kt` | một REQ hai bộ lọc, lấy note + hồ sơ đã kiểm | 3 |
| `domain/sv/LocalRssSv.kt`, `SubscribeViewModel.kt` + tests | cập nhật mọi caller; tên chỉ lúc preview, không đổi tên khi sync | 4 |
| test helper, androidTest probe tạm, report, `feature.md`, task gốc, spec | kiểm cuối và ghi giới hạn | 5 |

**Thứ tự:** 1 → 2 → 3 → 4 → 5. Không thêm database/room migration.

---

### Task 1: Đọc tên hồ sơ thuần (`NostrProfileName`)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileName.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileNameTest.kt` (Robolectric vì `org.json`)

**Interface:** `object NostrProfileName { const val MAX_NAME_CODE_POINTS = 40; fun fromContent(json: String): String? }`.

**Quy tắc (viết bằng tập ký tự rõ ràng, không dựa vào định nghĩa "khoảng trắng" của từng thư viện, vì nguyên mẫu Python đã cho thấy `U+0085` là khoảng trắng ở Python nhưng không phải ở Java/Kotlin):**
1. `JSONObject(json)`; JSON hỏng hoặc không phải object ⇒ `null`.
2. Thử `display_name`, rồi `name`. Một trường chỉ được xét khi là **chuỗi** (`String`); số, `null`, mảng bị bỏ qua và rơi sang trường kế.
3. Làm sạch một chuỗi: mọi ký tự thuộc loại **điều khiển** (`Character.getType == CONTROL`, tức `U+0000..U+001F` và `U+007F..U+009F`, gồm cả tab và xuống dòng) hoặc là khoảng trắng/khoảng cách (`Char.isWhitespace()` hoặc `Character.isSpaceChar`, gồm `U+00A0`) được đổi thành **một dấu cách**; sau đó gộp các dấu cách liên tiếp thành một và `trim()`.
4. Chuỗi sau làm sạch rỗng ⇒ thử trường kế; hết trường ⇒ `null`.
5. Cắt tối đa `MAX_NAME_CODE_POINTS = 40` **điểm mã Unicode** bằng `offsetByCodePoints` (không cắt giữa emoji), rồi `trimEnd()`.
6. Không HTML, không `picture/about/nip05`, không log, không trả JSON thô. `U+200B` (ký tự định dạng, không điều khiển) **không** bị bỏ: ghi vào giới hạn.

Giá trị đã đo bằng nguyên mẫu độc lập: `"A\u0001B\u0085C"` ⇒ `A B C`; `"  Ada\n\t Lovelace  "` ⇒ `Ada Lovelace`; `"a\u00a0b"` ⇒ `a b`; chuỗi `39 chữ a + 😀 + Z` dài 41 điểm mã và 42 đơn vị UTF-16.

- [ ] **Step 1: Viết test đỏ** (`NostrProfileNameTest.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrProfileNameTest {
    private fun content(name: String?, display: String? = null): String = buildString {
        append('{')
        val parts = mutableListOf<String>()
        if (display != null) parts += "\"display_name\":\"" + display + "\""
        if (name != null) parts += "\"name\":\"" + name + "\""
        append(parts.joinToString(","))
        append('}')
    }

    @Test fun prefersDisplayName_overName() =
        assertEquals("Alice Liddell", NostrProfileName.fromContent(content("alice", display = " Alice Liddell ")))

    @Test fun fallsBackToName_whenDisplayNameIsBlank() =
        assertEquals("alice", NostrProfileName.fromContent(content("alice", display = "   ")))

    @Test fun fallsBackToName_whenDisplayNameIsNotAString() =
        assertEquals("alice", NostrProfileName.fromContent("""{"display_name":42,"name":"alice"}"""))

    @Test fun fallsBackToName_whenDisplayNameIsNull() =
        assertEquals("alice", NostrProfileName.fromContent("""{"display_name":null,"name":"alice"}"""))

    @Test fun returnsNull_forAnythingWithoutAUsableName() {
        listOf("", "no json", "{}", "null", "[]", """{"name":null}""", """{"name":17}""", """{"name":"   "}""", """{"name":["x"]}""")
            .forEach { assertNull(it, NostrProfileName.fromContent(it)) }
    }

    @Test fun collapsesWhitespaceAndTrims() =
        assertEquals("Ada Lovelace", NostrProfileName.fromContent("{\"name\":\"  Ada\\n\\t Lovelace  \"}"))

    @Test fun controlCharactersBecomeASpace_soWordsDoNotGlueTogether() =
        assertEquals("A B C", NostrProfileName.fromContent("{\"name\":\"A\\u0001B\\u0085C\"}"))

    @Test fun aNonBreakingSpaceIsASpace() =
        assertEquals("a b", NostrProfileName.fromContent("{\"name\":\"a\\u00a0b\"}"))

    @Test fun aNameThatIsOnlyControlCharacters_isNotAName() =
        assertNull(NostrProfileName.fromContent("{\"name\":\"\\u0001\\u0002\\n\"}"))

    @Test fun displayNameOfOnlyControlCharacters_fallsBackToName() =
        assertEquals("alice", NostrProfileName.fromContent("{\"display_name\":\"\\u0001\",\"name\":\"alice\"}"))

    @Test fun exactlyFortyCodePoints_isKeptWhole() {
        val forty = "a".repeat(39) + "\uD83D\uDE00"
        assertEquals(40, forty.codePointCount(0, forty.length))
        assertEquals(forty, NostrProfileName.fromContent(content(forty)))
    }

    @Test fun fortyOneCodePoints_areCutToForty_withoutBreakingTheEmoji() {
        val long = "a".repeat(39) + "\uD83D\uDE00" + "Z"
        val name = requireNotNull(NostrProfileName.fromContent(content(long)))
        assertEquals("a".repeat(39) + "\uD83D\uDE00", name)
        assertEquals(40, name.codePointCount(0, name.length))
    }

    @Test fun anEmojiThatWouldStraddleTheLimit_isDroppedWhole() {
        val long = "a".repeat(40) + "\uD83D\uDE00"
        assertEquals("a".repeat(40), NostrProfileName.fromContent(content(long)))
    }

    @Test fun htmlLikeTextStaysPlainText() =
        assertEquals("<img src=x>", NostrProfileName.fromContent(content("<img src=x>")))

    @Test fun theParserNeverLogs() {
        val source = File("src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileName.kt").readLines()
            .filterNot { it.trimStart().let { t -> t.startsWith("import ") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
            .joinToString("\n")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, source.contains(it)) }
    }
}
```

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f22-t1.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrProfileNameTest' > /tmp/f22-t1-red.log 2>&1; echo $? > /tmp/f22-t1.exit) &`. Đỏ đúng lý do: `Unresolved reference: NostrProfileName`.
- [ ] **Step 3: Code tối thiểu** (`NostrProfileName.kt`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import org.json.JSONException
import org.json.JSONObject

/** Reads a display name out of the `content` of a Nostr profile event. The text is self-declared and untrusted. */
object NostrProfileName {
    const val MAX_NAME_CODE_POINTS = 40
    private val FIELDS = listOf("display_name", "name")

    fun fromContent(json: String): String? {
        val profile = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        for (field in FIELDS) {
            val raw = profile.opt(field) as? String ?: continue
            val cleaned = clean(raw)
            if (cleaned.isNotEmpty()) return cut(cleaned)
        }
        return null
    }

    private fun clean(raw: String): String {
        val spaced = buildString(raw.length) {
            for (c in raw) {
                val blank = Character.getType(c) == Character.CONTROL.toInt() || c.isWhitespace() || Character.isSpaceChar(c)
                append(if (blank) ' ' else c)
            }
        }
        return spaced.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun cut(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_NAME_CODE_POINTS) return text
        return text.substring(0, text.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)).trimEnd()
    }
}
```

- [ ] **Step 4: Xanh** (cùng lệnh, nền, chờ `.exit`): 15 test, 0 lỗi, 0 bỏ qua. Đỏ thì đọc lý do từ XML và sửa **mã**, không nới test.
- [ ] **Step 5: Mutation** (`/tmp/f22_t1_batch.sh`, một batch nền, lớp `NostrProfileNameTest`, tệp `NostrProfileName.kt`, cuối `cmp` + `ALLDONE`): đảo thứ tự `FIELDS`; bỏ `display_name`; `as? String` thành `toString()`; bỏ `continue` khi rỗng; bỏ nhánh `CONTROL`; bỏ `isWhitespace`; bỏ `isSpaceChar`; đổi `' '` thành `""` ở `append(if (blank)…)`; bỏ gộp dấu cách (`joinToString` không lọc rỗng); `MAX_NAME_CODE_POINTS` 40→39 và 40→41; cắt bằng `take(40)` (vỡ emoji); bỏ `trimEnd()`; `catch` trả `""`. Đột biến sống: thêm test; guard thừa (tương đương): gỡ guard.
- [ ] **Step 6: Commit** sau `exit=0` và XML `failures=0`:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileName.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileNameTest.kt && git commit -m "feat(front-02c): read a safe display name from a public Nostr profile"`

---

### Task 2: `NostrEvent` kiểm hồ sơ (kind 0) riêng, không nới kiểm ghi chú

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt` (thêm 5 hồ sơ ký sẵn)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileEventTest.kt` (Robolectric vì `org.json`)

**Interfaces:**
- Consumes: `NostrEvent.parse`, `Bip340.verify` (đã có).
- Produces: `const val NostrEvent.PROFILE_KIND = 0` và `fun isValidProfile(expectedPubkeyHex: String): Boolean`.
- **Không đổi** `isValid(expectedPubkeyHex)`: vẫn chỉ nhận kind 1. `isValidProfile` chỉ nhận kind 0. Phần dùng chung (độ dài hex, `pubkey`, `id` băm chuẩn, chữ ký) tách ra hàm riêng tư `hasValidSignature(expectedPubkeyHex)` để hai hàm công khai chỉ khác nhau đúng ở `kind`.

**Fixture** (do bộ ký Python độc lập `/tmp/f23_profiles.py` sinh bằng khóa thử của vector BIP-340 số 1 và 2; không phải khóa của ai thật; thêm vào `NostrTestEvents.kt` ngay trước `val MANY`): `PROFILE` (`created_at` 1700000900, `{"name":"alice","display_name":"Alice Liddell"}`), `PROFILE_NEWER` (1700000950, `{"name":"alice2"}`), `PROFILE_OLDER` (1700000800, `{"name":"old alice"}`), `PROFILE_STRANGER` (khóa khác, `{"name":"mallory"}`), `PROFILE_BLANK_NEWER` (1700000990, `display_name` và `name` chỉ có khoảng trắng; ký thật nên hợp lệ nhưng không có tên dùng được). Nếu `/tmp` bị dọn: chạy `python3 /tmp/f3_gen.py`, rồi `python3 /tmp/f23_profiles.py` (cần `/tmp/bip340.csv`, `/tmp/f3_lib.py`).

```kotlin
    const val PROFILE = """{"id":"0d9a8e7564b00aa09efed3aa0b9ece8fb177d3c191bd403678124073419d31af","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000900,"kind":0,"tags":[],"content":"{\"name\":\"alice\",\"display_name\":\"Alice Liddell\"}","sig":"25c665edde362e4cefe1b659e07bad3624e791a7c94ea463d8d160bc4cb70eec5d03cce94db2ad731f38144b85bb47281980883ad4a9aacda0ca56f10df635c3"}"""
    const val PROFILE_NEWER = """{"id":"5f0e0c67a4ad0b2f7dd0de65f84aa21a6ddf6b31a429f9083dcbbc2ea492fe26","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000950,"kind":0,"tags":[],"content":"{\"name\":\"alice2\"}","sig":"7d3725ba3fcd4e35ea998460ddf3a4f385becbb7a6e91d9c9889d7150e1dd5578bed2c3d29a2dc14a2488b3e418a69e4edc4174194c791e647c0a29c834263de"}"""
    const val PROFILE_OLDER = """{"id":"770e854d9ed7d0b4f60a0cbfbfeb181ebcdc4036925b9d73a35fe803c5f8773f","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000800,"kind":0,"tags":[],"content":"{\"name\":\"old alice\"}","sig":"13ecc9b0d3b31e756161a9e086d7e8e268e3e8876112bb334fd5800249b87912c501801bc1705adcaf8835baa500083e320fc4230294b8f872b58ccab993d3d5"}"""
    const val PROFILE_STRANGER = """{"id":"a4815bd97ba8652d884975240c2dca9b600602e2dd8dd941c1a9b8cd39d3fc5d","pubkey":"dd308afec5777e13121fa72b9cc1b7cc0139715309b086c960e18fd969774eb8","created_at":1700000900,"kind":0,"tags":[],"content":"{\"name\":\"mallory\"}","sig":"134d85bf75089cc732baffeefc2016bbf0cd5397f955c3c51c6bef7f55bf286854b66bc360a5613033e09dc3a42505924087e5e982647eef1b3b27daf3a4ee2c"}"""
    const val PROFILE_BLANK_NEWER = """{"id":"8ff0497411bc8d4e1f992df7299d46197698ff2bb668fcfc09ff92e0cad84c81","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000990,"kind":0,"tags":[],"content":"{\"display_name\":\"   \",\"name\":\"  \"}","sig":"95d343a6e3eaa581dc46dd18dda4d2684f8ce4c0348b2f09b5347e314c605ee8a2a46b72834950eea21a8408300d6d627d3ce1370f2a9f213f4ee2f94adfca2d"}"""
```

- [ ] **Step 1: Viết test đỏ** (`NostrProfileEventTest.kt`)

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrProfileEventTest {
    private val author = NostrTestEvents.AUTHOR
    private fun ev(json: String) = requireNotNull(NostrEvent.parse(json)) { "fixture must parse" }

    @Test fun aSignedProfileByTheAuthor_isValid() = assertTrue(ev(NostrTestEvents.PROFILE).isValidProfile(author))

    @Test fun aProfile_isNotAValidNote() = assertFalse("a profile must never pass the note check", ev(NostrTestEvents.PROFILE).isValid(author))

    @Test fun aNote_isNotAValidProfile() = assertFalse(ev(NostrTestEvents.PLAIN).isValidProfile(author))

    @Test fun aNote_stillPassesTheNoteCheck() = assertTrue(ev(NostrTestEvents.PLAIN).isValid(author))

    @Test fun aProfileByAnotherAuthor_isRejected() {
        assertFalse(ev(NostrTestEvents.PROFILE_STRANGER).isValidProfile(author))
        assertTrue(ev(NostrTestEvents.PROFILE_STRANGER).isValidProfile(NostrTestEvents.STRANGER))
    }

    @Test fun aProfileWithTamperedContent_isRejected() {
        val forged = NostrTestEvents.PROFILE.replace("Alice Liddell", "Someone Else")
        assertFalse(ev(forged).isValidProfile(author))
    }

    @Test fun aProfileWithABorrowedSignature_isRejected() {
        val e = ev(NostrTestEvents.PROFILE)
        val borrowed = ev(NostrTestEvents.PROFILE_NEWER).sig
        assertFalse(ev(NostrTestEvents.PROFILE.replace(e.sig, borrowed)).isValidProfile(author))
    }

    @Test fun aProfileWithAChangedTimestamp_isRejected() =
        assertFalse(ev(NostrTestEvents.PROFILE.replace("1700000900", "1700000901")).isValidProfile(author))

    @Test fun anUppercaseHexProfile_isRejected() {
        val e = ev(NostrTestEvents.PROFILE)
        assertFalse(ev(NostrTestEvents.PROFILE.replace(e.id, e.id.uppercase())).isValidProfile(author))
        assertFalse(ev(NostrTestEvents.PROFILE).isValidProfile(author.uppercase()))
    }

    @Test fun theProfileKindConstant_isZero() = assertEquals(0, NostrEvent.PROFILE_KIND)

    @Test fun otherKinds_areNotProfiles() = assertFalse(ev(NostrTestEvents.KIND7).isValidProfile(author))
}
```

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f23-t2.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrProfileEventTest' > /tmp/f23-t2-red.log 2>&1; echo $? > /tmp/f23-t2.exit) &`. Đỏ đúng lý do: `Unresolved reference: isValidProfile` / `PROFILE_KIND` và `PROFILE*` trong `NostrTestEvents` (thêm fixture trước khi chạy để đỏ chỉ vì thiếu hàm).
- [ ] **Step 3: Code tối thiểu.** Trong `NostrEvent.kt`: giữ `isValid` y nguyên về hành vi; tách thân sau dòng kiểm `kind` thành hàm riêng tư; thêm hàm công khai mới.

```kotlin
    fun isValid(expectedPubkeyHex: String): Boolean {
        if (kind != TEXT_NOTE_KIND) return false
        return hasValidSignature(expectedPubkeyHex)
    }

    /** A profile (kind 0): same id, signature and author checks as a note, only the kind differs. Never accepted as a note. */
    fun isValidProfile(expectedPubkeyHex: String): Boolean {
        if (kind != PROFILE_KIND) return false
        return hasValidSignature(expectedPubkeyHex)
    }

    private fun hasValidSignature(expectedPubkeyHex: String): Boolean {
        if (!isLowerHex(id, ID_HEX_LENGTH) || !isLowerHex(pubkey, ID_HEX_LENGTH) || !isLowerHex(sig, SIG_HEX_LENGTH)) return false
        if (pubkey != expectedPubkeyHex) return false
        if (computeId() != id) return false
        return Bip340.verify(pubkey, id, sig)
    }
```

  và trong `companion object`: `const val PROFILE_KIND = 0` ngay dưới `TEXT_NOTE_KIND`.
- [ ] **Step 4: Xanh**, kèm hồi quy: `*NostrProfileEventTest`, `*NostrEventTest` (20 test cũ phải còn xanh). Kỳ vọng 11 + 20 = 31 test, 0 lỗi.
- [ ] **Step 5: Mutation** (`/tmp/f23_t2_batch.sh`, lớp `NostrProfileEventTest` + `NostrEventTest`, tệp `NostrEvent.kt`, cuối `cmp` + `ALLDONE`): `isValidProfile` bỏ kiểm `kind`; `isValid` bỏ kiểm `kind` (hồ sơ lọt làm ghi chú); `isValid` nhận cả kind 0; `PROFILE_KIND` 0→1; `hasValidSignature` bỏ `pubkey`; bỏ kiểm `computeId`; bỏ `Bip340.verify`; `isValidProfile` luôn `true`. Đột biến sống thì thêm test.
- [ ] **Step 6: Commit** sau `exit=0`, XML `failures=0`:
`git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileEventTest.kt && git commit -m "feat(front-02c): accept a signed Nostr profile as such, without letting it pass for a note"`


---

### Task 3: Một WebSocket `REQ` trả ghi chú + hồ sơ (`NostrFetchResult`)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFetchResult.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt` (năm hồ sơ đã thêm ở Task 2: `PROFILE`, `PROFILE_NEWER`, `PROFILE_OLDER`, `PROFILE_STRANGER`, `PROFILE_BLANK_NEWER`; `MANY` giữ nguyên)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayProfileTest.kt`
- Update tests/callers found by `grep -rn 'nostrRelayClient.fetch\|client(.*fetch\|\.fetch(NostrTestEvents' app/src/main app/src/test app/src/androidTest`; không đoán, liệt kê từng file trước khi sửa.

**Interfaces:**
- Produces: `data class NostrFetchResult(val notes: List<NostrEvent>, val profileName: String?)`; `suspend fun NostrRelayClient.fetch(authorHex: String): NostrFetchResult`.
- `REQ` wire format (một subscription id `f2`, một kết nối):

```json
["REQ","f2",{"kinds":[1],"authors":["<64-lowerhex>"],"limit":50},{"kinds":[0],"authors":["<same-hex>"],"limit":1}]
```

- **Không sửa** `MAX_EVENTS_READ`, `MAX_NOTES_KEPT`, `MAX_MESSAGE_BYTES`, EOSE, timeout, fallback, close. `frames` tăng cho **cả** kind 0 lẫn kind 1 (kể cả event bị loại/sai signature); khung quá to cũng tăng như đang làm. Bài hợp lệ đi `notes` (kind 1 + `isValid`); hồ sơ hợp lệ đi một biến `profileEvents` (kind 0 + `isValidProfile`). EVENT khác kind, sai chữ ký/id/pubkey, subscription id lạ ⇒ bỏ. Cùng một subscription id `f2`; không mở WebSocket thứ hai.
- Đến EOSE / 50 khung / timeout: `notes = sortedByDescending(createdAt).take(20)`; `profileName = profileEvents.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { NostrProfileName.fromContent(it.content) }` (đã xác thực trước). Nếu relay không có hồ sơ/JSON không có tên ⇒ `null`. Nếu relay không trả ghi chú hợp lệ nhưng trả hồ sơ: vẫn kết quả `notes=[]`, `profileName=...`; caller sẽ báo lỗi nguồn xem trước (Task 4) nhưng không ghi tên vào Feed.
- Fallback qua relay thứ hai: dựa vào kết quả ghi chú như hiện tại (`notes.isNotEmpty()`); nếu relay đầu không có ghi chú nhưng có hồ sơ, vẫn thử relay hai, vì relay hai có thể cho ghi chú đầy đủ. Khi relay hai có notes, ưu tiên **tên hồ sơ của relay hai nếu có**, nếu không thì dùng hồ sơ relay đầu (cùng author, nhưng có thể cũ); quy tắc exact: `profileName = second.profileName ?: first.profileName`. Nếu relay đầu có notes, dừng như hiện tại, tên từ relay đầu.
- Event kind 0 không được đưa vào feed notes/Article.

**Sự thật đã đo (2026-11-15, từ Mac, không key riêng):** `relay.primal.net` với hai bộ lọc trong **một REQ** trả 9 event kind 1 + 1 kind 0, đúng một EOSE, khoảng 2,0–2,2 giây (một khóa thử có hồ sơ). `nos.lol` hôm nay báo lỗi handshake (`Received network error or non-101 status code`) cho kind 1 đơn, kind 0 đơn, cả hai filter cùng REQ, và filter gộp kinds `[0,1]`; đây là sự cố relay/mạng có sẵn, không bằng chứng lỗi cú pháp REQ. Giữ fallback sang primal.

- [ ] **Step 1: Fixture.** Năm hồ sơ ký sẵn đã vào `NostrTestEvents.kt` ở Task 2; không thêm gì, **không sửa JSON bằng tay** (`id` và `sig` phụ thuộc cả `created_at` lẫn nội dung).
- [ ] **Step 2: Viết test đỏ** `NostrRelayProfileTest.kt` (cùng mock WebSocket đã có trong `NostrRelayClientTest`, fixture mới trong server source set `NostrTestEvents`):

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayProfileTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val sent = CopyOnWriteArrayList<String>()

    @Before fun setUp() { server = MockWebServer().apply { start() }; http = OkHttpClient() }
    @After fun tearDown() { http.dispatcher.executorService.shutdownNow(); server.shutdown() }
    private fun url() = server.url("/").toString()
    private fun client(vararg urls: String) = NostrRelayClient(http, Dispatchers.IO, urls.toList(), false, 1_000L)

    private fun relay(vararg events: String) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            sent += text
            if (!text.startsWith("[\"REQ\"")) return
            events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
            webSocket.send("[\"EOSE\",\"f2\"]")
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
    })

    @Test fun oneReq_hasBothExactFilters_underOneSubscription() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PROFILE))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(1, result.notes.size)
        assertEquals("Alice Liddell", result.profileName)
        val req = sent.single()
        assertTrue(req.startsWith("[\"REQ\",\"f2\","))
        assertTrue(req.contains("{\"kinds\":[1],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":50}"))
        assertTrue(req.contains("{\"kinds\":[0],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":1}"))
    }

    @Test fun kind0_neverAppearsInNotes() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE, NostrTestEvents.PLAIN))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), result.notes.map { it.id })
    }

    @Test fun profileName_prefersDisplayName_throughProfileParser() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        assertEquals("Alice Liddell", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun wrongAuthors_andBadSignatures_cannotSupplyTheProfileName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_STRANGER))
        server.enqueue(MockResponse().setResponseCode(503))
        val result = client(url(), url()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertNull(result.profileName)
    }

    @Test fun theNewestValidProfileWins_evenIfTheRelaySendsAnOlderProfileLater() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_NEWER, NostrTestEvents.PROFILE_OLDER, NostrTestEvents.PROFILE))
        assertEquals("alice2", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun aNewestProfileWithoutAUsableName_fallsBackToAnOlderProfileThatHasOne() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_BLANK_NEWER, NostrTestEvents.PROFILE))
        assertEquals("Alice Liddell", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun noProfileName_keepsTheFallbackNull() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertNull(client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun noNotesButAProfile_isReturnedForTheCallerToRejectAsAnEmptyFeed() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertEquals("Alice Liddell", result.profileName)
    }

    @Test fun subIdMismatch_cannotSupplyAProfile() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!text.startsWith("[\"REQ\"")) return
                webSocket.send("[\"EVENT\",\"wrong\",${NostrTestEvents.PROFILE}]")
                webSocket.send("[\"EOSE\",\"f2\"]")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
        }))
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(client(url(), url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun fallsBackToTheSecondRelay_whenFirstHasOnlyAProfile_andPrefersSecondName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_OLDER))
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PROFILE_NEWER))
        val result = client(url(), url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), result.notes.map { it.id })
        assertEquals("alice2", result.profileName)
    }

    @Test fun fallsBackToTheFirstProfile_whenSecondHasNoName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals("Alice Liddell", client(url(), url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }
}
```

**Quy tắc chọn tên trong một relay** (khớp với test `aNewestProfileWithoutAUsableName_…`): sắp mọi hồ sơ hợp lệ theo `created_at` giảm dần, lấy tên từ hồ sơ đầu tiên có tên dùng được (`NostrProfileName.fromContent != null`). Nghĩa là "mới nhất **có tên**", không chỉ "mới nhất".

- [ ] **Step 3: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f23-t3.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrRelayProfileTest' > /tmp/f23-t3-red.log 2>&1; echo $? > /tmp/f23-t3.exit) &`. Đỏ đúng lý do: `NostrFetchResult` / `result.notes` / `result.profileName` chưa tồn tại.
- [ ] **Step 4: Code tối thiểu.**

  `NostrFetchResult.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.social.nostr

/** What one fetch found: the author's notes and, when a valid public profile has a usable name, that name. */
data class NostrFetchResult(val notes: List<NostrEvent>, val profileName: String?) {
    companion object {
        val EMPTY = NostrFetchResult(emptyList(), null)
    }
}
```

  `NostrRelayClient.kt` (đọc lại tệp ngay trước khi sửa, chỉ đổi những chỗ sau):
  - `fetch` trả `NostrFetchResult`. Vòng `for (relay in relays)`: gọi `readRelay`; giữ `var firstProfile: String? = null`; nếu `result.notes.isNotEmpty()` thì `return@withContext result.copy(profileName = result.profileName ?: firstProfile)`; nếu không, `firstProfile = firstProfile ?: result.profileName`; hết vòng trả `NostrFetchResult(emptyList(), firstProfile)`; `authorHex` sai hoặc relay không `wss://` trả `NostrFetchResult.EMPTY` như cũ (không mở kết nối).
  - `readRelay` trả `NostrFetchResult`; thêm `val profiles = ArrayList<NostrEvent>()`.
  - `onOpen` gửi: `"[\"REQ\",\"$SUBSCRIPTION_ID\",{\"kinds\":[${NostrEvent.TEXT_NOTE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$MAX_EVENTS_READ},{\"kinds\":[${NostrEvent.PROFILE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$PROFILE_LIMIT}]"`; thêm `private const val PROFILE_LIMIT = 1`.
  - `onMessage`, nhánh `EVENT`: sau `parse`, `if (event != null && event.isValid(authorHex)) found[event.id] = event else if (event != null && event.isValidProfile(authorHex)) profiles += event`. `frames` vẫn tăng cho mọi EVENT cùng subscription id.
  - Cuối hàm: `return NostrFetchResult(notes = found.values.sortedByDescending { it.createdAt }.take(MAX_NOTES_KEPT), profileName = profiles.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { NostrProfileName.fromContent(it.content) })`.
  - Giữ nguyên `onFailure`, `onClosing`, thứ tự đóng socket, timeout, `MAX_*`. **Không log gì.**

- [ ] **Step 5: Cập nhật mọi nơi gọi `fetch`** (đã đếm trước: `SubscribeViewModel.kt:157`, `LocalRssSv.kt:111` ở mã chính; 11 tệp test: `NostrRelayClientTest`, `NostrRelayNetworkTest`, `NostrRelayClientLeakTest`, `LocalRssSvNostrSyncTest`, `SubscribeViewModelNostrTest`, `SubscribeViewModelInterruptionTest`, `SubscribeDialogTypingTest`, và các test dựng `LocalRssSv`/`SubscribeViewModel` với `mockk(relaxed = true)` không đổi). Ở phần này **chỉ** làm cho biên dịch được và giữ hành vi cũ:
  - `LocalRssSv.queryNostrArticles`: `val events = nostrRelayClient.fetch(authorHex).notes` (tên hồ sơ **không** dùng khi sync nền; đây là quyết định của spec).
  - `SubscribeViewModel.search`: tạm `?.notes?.let { NostrFeedMapper.toArticles(events = it, …) }` (chưa dùng tên; Task 4 làm).
  - Test mock `coEvery { relay.fetch(any()) } returns notes(...)` đổi thành `returns NostrFetchResult(notes(...), null)`; `returns emptyList()` đổi thành `returns NostrFetchResult.EMPTY`; `ids(client(...).fetch(...))` đổi thành `ids(client(...).fetch(...).notes)`; `.fetch(...).isEmpty()` đổi thành `.fetch(...).notes.isEmpty()`; `throws` không đổi. Dùng `grep -rn` đã liệt kê ở đầu Task, sửa **từng tệp một**, chạy lớp test của tệp đó sau mỗi lần để thấy chỉ có lỗi biên dịch đúng chỗ.

- [ ] **Step 6: Xanh.** Chạy `*NostrRelayProfileTest`, `*NostrRelayClientTest`, `*NostrRelayNetworkTest`, `*NostrRelayClientLeakTest`, `*LocalRssSv*`, `*SubscribeViewModel*`, `*SubscribeDialogTypingTest`. Kỳ vọng 0 lỗi; mọi số test cũ giữ nguyên, thêm đúng 11 test mới của `NostrRelayProfileTest`.
- [ ] **Step 7: Mutation** (`/tmp/f23_t3_batch.sh`, một batch nền, các lớp ở Step 6, tệp `NostrRelayClient.kt`, cuối `cmp` + `ALLDONE`): REQ chỉ còn một bộ lọc; bộ lọc kind 0 `limit` 1→2; `PROFILE_KIND` bỏ khỏi REQ; bỏ `isValidProfile`; nhận hồ sơ không kiểm `isValid` (`if (event != null) profiles += event`); đưa kind 0 vào `found`; bỏ `sortedByDescending` hồ sơ (lấy hồ sơ đầu); `firstNotNullOfOrNull` thành `firstOrNull()?.let` (không rơi về hồ sơ cũ); `frames` không tăng cho hồ sơ (`50 khung` không còn tính cả hồ sơ); bỏ `result.profileName ?: firstProfile`; bỏ nhánh giữ `firstProfile`; đổi `returns EMPTY` thành thử relay không `wss://`. Sống sót ⇒ thêm test; guard thừa ⇒ gỡ.
- [ ] **Step 8: Commit** sau `exit=0`, XML sạch: `git add` các tệp ở mục Files (mã chính, `NostrFetchResult.kt`, mọi test đã sửa, `NostrRelayProfileTest.kt`) rồi `git commit -m "feat(front-02c): ask the relay for the profile in the same request and return it with the notes"`.

---

### Task 4: Nối tên hồ sơ vào bản xem trước (`SubscribeViewModel.search`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` (thêm `fun feedName(npub: String, profileName: String?): String`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt` (thêm), `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrProfileTest.kt` (mới), `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt` (thêm một test nguồn)

**Interfaces:**
- Consumes: `NostrFetchResult` (Task 3), `NostrProfileName` (Task 1).
- Produces: `NostrFeedMapper.feedName(npub, profileName)` = `FEED_NAME_PREFIX + profileName` khi `profileName` không null; ngược lại bằng `feedName(npub)` cũ. Hàm `feedName(npub)` một tham số **giữ nguyên** (còn nơi khác dùng, và test cũ).
- Tên chỉ vào `Feed.name` của **bản xem trước** trong `search()`. **Không** đổi `LocalRssSv.queryNostrArticles`: đồng bộ nền không đặt lại tên nguồn đã lưu.

**Đã đọc, không đoán:** `search()` tạo `Feed(name = NostrFeedMapper.feedName(npub), …)` ở khoảng dòng 181 và dùng `title = feed.name`. `runCatching { fetch }` giữ nguyên, kể cả `onFailure { if (it is CancellationException) throw it }`. Ba test cũ kiểm tên cũ (`SubscribeDialogTypingTest:111`, `SubscribeViewModelNostrTest:111` và `:132`) dùng `NostrFetchResult(notes, null)` nên **vẫn đúng** và là hồi quy cho đường lui.

- [ ] **Step 1: Test đỏ cho mapper** (thêm vào `NostrFeedMapperTest.kt`, cùng lớp, trước dấu `}` cuối):

```kotlin
    @Test fun feedName_withAProfileName_usesThePrefixAndThatName() =
        assertEquals("Nostr · Alice Liddell", NostrFeedMapper.feedName(npub, "Alice Liddell"))

    @Test fun feedName_withoutAProfileName_isTheOldNpubName() {
        assertEquals(NostrFeedMapper.feedName(npub), NostrFeedMapper.feedName(npub, null))
        assertEquals("Nostr · npub1mlc…7a0a", NostrFeedMapper.feedName(npub, null))
    }
```

- [ ] **Step 2: Test đỏ cho view model** (`SubscribeViewModelNostrProfileTest.kt`; chép phần dựng (`@Before`, `@After`, `search()` helper) y nguyên từ `SubscribeViewModelNostrTest.kt` rồi chỉ đổi các giả lập `relay.fetch` thành `NostrFetchResult`):

```kotlin
    @Test fun aProfileName_becomesTheNameOfThePreviewFeed_andTheDialogTitle() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), "Alice Liddell")
        search(npub)
        assertEquals("Nostr · Alice Liddell", state.feed?.name)
        assertEquals("Nostr · Alice Liddell", state.title)
    }

    @Test fun noProfileName_keepsTheNpubName() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), null)
        search(npub)
        assertEquals("Nostr · " + npub.take(8) + "…" + npub.takeLast(4), state.feed?.name)
    }

    @Test fun aProfileWithoutAnyNote_isStillAnEmptyFeedError_andNothingIsPreviewed() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(emptyList(), "Alice Liddell")
        search(npub)
        assertNull(state.feed)
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.web_feed_fetch_failed), state.errorMessage)
        assertFalse(state.errorMessage.contains("Alice"))
    }

    @Test fun subscribing_savesTheProfileNameAsTheFeedName() = runTest(dispatcher) {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), "Alice Liddell")
        search(npub)
        viewModel.selectedGroup("group_1")
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) { localService.subscribe(match { it.name == "Nostr · Alice Liddell" && it.url == "nostr:$npub" }, any()) }
    }
```

  **Hồi quy đồng bộ nền:** thêm vào `LocalRssSvNostrSyncTest.kt`:

```kotlin
    @Test fun aSync_neverRenamesTheStoredFeed_evenWhenTheRelayReturnsAProfileName() = runBlocking {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), "Someone New")
        val result = service.syncSingleFeed(feed)
        assertEquals("Nostr", result?.feed?.name)
    }
```

  **Test nguồn** (thêm vào `SubscribeViewModelNostrSourceTest.kt`): trong `search()` phần Nostr, `profileName` phải đi vào `feedName(` và chuỗi không chứa `Log.` hay `println` (kiểm bằng cùng cách lọc comment/import đang có).

- [ ] **Step 3: Chạy đỏ** (nền, chờ `.exit`): `rm -f /tmp/f24-t4.exit; (./gradlew :app:testDevDebugUnitTest --tests '*NostrFeedMapperTest' --tests '*SubscribeViewModelNostrProfileTest' --tests '*LocalRssSvNostrSyncTest' --tests '*SubscribeViewModelNostrSourceTest' > /tmp/f24-t4-red.log 2>&1; echo $? > /tmp/f24-t4.exit) &`. Đỏ đúng lý do: `feedName(npub, …)` chưa có (lỗi biên dịch), rồi sau khi thêm hàm rỗng, test hành vi đỏ vì `search()` chưa dùng tên.
- [ ] **Step 4: Code tối thiểu.**
  - `NostrFeedMapper.kt`: `fun feedName(npub: String, profileName: String?): String = if (profileName == null) feedName(npub) else FEED_NAME_PREFIX + profileName`.
  - `SubscribeViewModel.kt`: giữ nguyên chuỗi `runCatching { … }.onFailure { … }`; thay `.getOrNull()?.let { toArticles(events = it, …) }.orEmpty()` bằng cách giữ **cả** kết quả: `val fetched = runCatching { nostrRelayClient.fetch(authorHex) }.onFailure { if (it is CancellationException) throw it }.getOrNull()`; `val articles = fetched?.let { toArticles(events = it.notes, …) }.orEmpty()`; `name = NostrFeedMapper.feedName(npub, fetched?.profileName)`.
  - Không đụng `LocalRssSv` (Task 3 đã đủ cho caller).
- [ ] **Step 5: Xanh** (cùng lệnh) rồi hồi quy rộng: `*Nostr*`, `*SubscribeViewModel*`, `*SubscribeDialogTypingTest`, `*LocalRssSv*`, `*golden*`. 0 lỗi, mọi test cũ giữ nguyên.
- [ ] **Step 6: Mutation** (`/tmp/f24_t4_batch.sh`, một batch nền, các lớp ở Step 5, tệp `SubscribeViewModel.kt` và `NostrFeedMapper.kt`, cuối `cmp` + `ALLDONE`): `feedName(npub, fetched?.profileName)` thành `feedName(npub)` (bỏ dùng tên); bỏ tiền tố; `profileName == null` thành `profileName.isNullOrEmpty()` (nếu tên rỗng, tên vẫn phải rơi về npub: thêm test nếu sống); `toArticles(events = it.notes` thành `it.notes + …`; `onFailure { throw }` bỏ; **thêm** đổi tên trong `LocalRssSv.queryNostrArticles` (đột biến phải làm test hồi quy đồng bộ đỏ); `feedName(npub)` một tham số đổi hành vi. Sống sót ⇒ thêm test.
- [ ] **Step 7: Commit** sau `exit=0`, XML sạch: `git add` `SubscribeViewModel.kt`, `NostrFeedMapper.kt`, `NostrFeedMapperTest.kt`, `SubscribeViewModelNostrProfileTest.kt`, `SubscribeViewModelNostrSourceTest.kt`, `LocalRssSvNostrSyncTest.kt` rồi `git commit -m "feat(front-02c): name a new Nostr source after its public profile, and keep the old name when there is none"`.

---

### Task 5: Kiểm tổng thể, relay thật trên A50s, báo cáo

**Files:**
- Tạm, không commit: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrProfileProbe.kt`
- Create: `doc/task/done/FRONT-02c_nostr-profile-name_DONE.md`
- Modify: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `docs/superpowers/specs/2026-11-15-front-02c-nostr-profile-name-design.md` (mục "Điều chỉnh khi thực thi")

- [ ] **Step 1: Full suite** nền: `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f25-full.log 2>&1`, chờ `.exit`; đếm `tests/skip/failures/errors` từ XML mới. Kỳ vọng 0 lỗi (nền 3.580 + test mới của Task 1–4). Test đỏ chập chờn: chạy riêng, ghi nguyên văn, không giấu (đã gặp hai test chập chờn trước đây; chạy dưới tải CPU để kiểm).
- [ ] **Step 2: Lint** nền `./gradlew lintDevDebug` (nền 2.465 cảnh báo, 10 thông tin, 0 lỗi; 0 vấn đề trong tệp Nostr mới).
- [ ] **Step 3: Build** nền `./gradlew assembleDevDebug assembleDevDebugAndroidTest`, `exit=0`. `git status --short app/schemas` rỗng; `git diff --stat -- app/src/main/res` rỗng.
- [ ] **Step 4: Cổng thiết bị rồi sao lưu (CHỈ A50s `R58MA6WYRPE`).** `adb devices` (chỉ gọi lệnh có `-s R58MA6WYRPE`); `dumpsys activity activities` tìm instrumentation và foreground (phải là launcher hoặc app đã biết); `ps -A` tìm `instrument|monkey|uiautomator`; kiểm `isKeyguardShowing=false`. Dừng và hỏi nếu app lạ ở foreground, màn khóa hay có hộp sinh trắc. Sao lưu `python3 /tmp/a50s-backup.py`; ghi băm DataStore. Nếu `/tmp/a50s-*.py` bị dọn: sao `/tmp/o03p-backup.py`, `/tmp/o03p-restore.py` rồi đổi `SERIALS` thành `{'a50s': 'R58MA6WYRPE'}` và đường dẫn `/tmp/a50s-root`.
- [ ] **Step 5: Probe relay thật** (helper tạm `NostrProfileProbe`, **không MockK**, không Hilt): dựng `OkHttpClient` giống `OkHttpClientModule` (`cachingHttpClient(cacheDirectory = null).newBuilder().dns(AppDns(dohProviderSupplier = { DohProviderPref.fromId(context.dohProviderId) })).addInterceptor(ConditionalCacheInterceptor).addNetworkInterceptor(UserAgentInterceptor).build()`), gọi `NostrRelayClient(http, Dispatchers.IO).fetch(hex)` với hai khóa công khai của ví dụ NIP-19 (một khóa có hồ sơ; một khóa không có hồ sơ). **Chỉ** `Log.i("F4", …)` số ghi chú, `profileName != null`, độ dài tên theo điểm mã (không tên, không `npub`, không relay). Cài app + test APK bằng `adb -s R58MA6WYRPE install -r -t`, chạy `adb -s R58MA6WYRPE shell am instrument -w -e class …NostrProfileProbe com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`, **không chạm màn hình**. Nếu mạng/relay lỗi: ghi nguyên trạng, không sửa test cho khớp. Kỳ vọng: khóa có hồ sơ ⇒ `profileName != null` và độ dài ≤ 40; khóa không hồ sơ ⇒ `null`.
- [ ] **Step 6: Khôi phục** `adb -s R58MA6WYRPE shell am force-stop com.mckimquyen.reader`; `python3 /tmp/a50s-restore.py` (Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`); băm DataStore khớp bản sao lưu; `adb -s R58MA6WYRPE uninstall com.mckimquyen.reader.test`; xóa helper; `git status` sạch; `git diff --stat -- app/src/main` rỗng so với HEAD.
- [ ] **Step 7: Báo cáo** `doc/task/done/FRONT-02c_nostr-profile-name_DONE.md` (tiếng Việt, số liệu thật): phạm vi và quyết định owner; **tên tự khai, không xác minh `nip05`, có thể giả người nổi tiếng**; chỉ đặt tên lúc xem trước, sync nền không đổi tên, hồ sơ đổi sau đó không cập nhật; mutation và test mới; relay thật trên A50s (số liệu, `nos.lol` có lỗi handshake ngày 2026-11-15 nên fallback sang `relay.primal.net`); chưa kiểm: tên thật hiện trong hộp thoại trên màn hình (không chạm), relay khác, hồ sơ thay đổi theo thời gian, TalkBack, ngôn ngữ ngoài EN/VI; thiết bị là A50s (yếu hơn KJ7), số khung hình không so sánh được với Tecno.
- [ ] **Step 8: Tài liệu**: cập nhật `doc/feature.md` (R2), ghi chú đầu task gốc FRONT-02, và mục "Điều chỉnh khi thực thi" của spec; chuỗi/Room/dependency/manifest: không đổi.
- [ ] **Step 9: Commit** `git add doc/task/done/FRONT-02c_nostr-profile-name_DONE.md doc/feature.md doc/task/todo/FRONT-02_decentralized-web3-social.md docs/superpowers/specs/2026-11-15-front-02c-nostr-profile-name-design.md && git commit -m "docs(front-02c): report the Nostr profile name with its verified and unverified parts"`. Không push, không PR, không `.idea`.

---

## Self-review

**Phủ spec:** hai bộ lọc một REQ (Task 3), `isValid` kind 0 không nới kind 1 (Task 2), `display_name` rồi `name`, 40 điểm mã, bỏ điều khiển, gộp khoảng trắng (Task 1), mới nhất có tên và fallback (Task 3), tiền tố `Nostr · ` và dùng tên ở xem trước (Task 4), sync nền không đổi tên (Task 4, có test hồi quy), không xác minh `nip05` và tên có thể giả (Task 5 báo cáo), không log tên (Task 1 và 4 có test nguồn), kiểm thật trên A50s không chạm màn hình (Task 5).

**Điểm đã sửa sau khi đọc mã và đo:** (1) hằng hồ sơ dùng `PROFILE_KIND = 0`, không dùng số phỏng đoán; (2) quy tắc "mới nhất **có tên**" đã đo bằng một fixture ký thật (`PROFILE_BLANK_NEWER`) thay vì sửa JSON tay làm hỏng chữ ký; (3) `nos.lol` ngày 2026-11-15 lỗi handshake với cả REQ một bộ lọc nên không dùng làm bằng chứng về REQ hai bộ lọc; `relay.primal.net` đã xác nhận hai bộ lọc trong một REQ cho một EOSE; (4) chuỗi làm sạch tên viết bằng tập ký tự rõ ràng vì `U+0085` là khoảng trắng ở Python nhưng không ở Kotlin.

**Kiểu và tên dùng nhất quán:** `NostrProfileName.fromContent(String): String?`, `NostrEvent.PROFILE_KIND`, `NostrEvent.isValidProfile(String): Boolean`, `NostrFetchResult(notes, profileName)`, `NostrFetchResult.EMPTY`, `NostrFeedMapper.feedName(npub, profileName)`.

**Quét chỗ trống:** `grep -nE 'TBD|TODO|Sửa trước khi|Trước khi chạy|Điều chỉnh ngay' docs/superpowers/plans/2026-11-15-front-02c-nostr-profile-name.md` phải rỗng trước khi commit plan. Các câu "đọc lại tệp ngay trước khi sửa" là chủ ý (mã có thể đổi giữa các task), không phải chỗ trống.

**Rủi ro nói thẳng:** relay thấy thêm một bộ lọc (đã biết `npub`); tên là tự khai và có thể giả; `U+200B` (ký tự định dạng) không bị bỏ; hồ sơ chỉ được đặt một lần lúc tìm; kiểm thật phụ thuộc relay và mạng ngày chạy.
