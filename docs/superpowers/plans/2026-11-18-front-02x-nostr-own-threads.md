# FRONT-02x — Nguồn Nostr: hiện chuỗi bài của chính tác giả (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Trong nguồn Nostr, giữ các bài trong chuỗi **tự trả lời** của chính tác giả (bài 2, 3… nối bài 1 của họ) thay vì bỏ mọi bài có thẻ `e`; trả lời bài của người khác vẫn bị bỏ.

**Architecture:** `NostrEvent` hiểu NIP-10: biết một bài là trả lời hay chỉ nhắc (`mention`), và đọc id bài được trả lời. `NostrFeedMapper.toArticles` giữ bài không phải trả lời, và bài trả lời một bài mà chính bài đó cũng được giữ (cùng tập sự kiện đã kiểm của cùng tác giả; đệ quy có nhớ, chặn vòng lặp, không phụ thuộc thứ tự). Mỗi mảnh vẫn là một bài riêng. **Chỉ JVM, không chạm thiết bị nào.**

**Tech Stack:** Kotlin, JUnit4 + Robolectric (đã dùng cho `NostrEventTest` và `NostrFeedMapperTest`), MockK (JVM được phép), Gradle `testDevDebugUnitTest --tests`, Python 3.9 (mô hình tham chiếu và đột biến).

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không chạm thiết bị nào** (không Tecno, không Pixel, không `adb`).
- Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không thêm lệnh gọi mạng. Mã production đổi **hai tệp**: `NostrEvent.kt`, `NostrFeedMapper.kt`. **Không đổi** chữ ký `toArticles`, `NostrRelayClient` (kể cả giới hạn 20), `LocalRssSv`, `SubscribeViewModel`.
- Quy tắc giữ bài: giữ nếu **không phải trả lời**; hoặc là trả lời mà `repliedToId` (hex 64 chữ thường) trỏ tới một bài trong cùng danh sách (đã bỏ trùng) và **chính bài đó cũng được giữ**. Không tin thẻ `p`. Không phụ thuộc thứ tự đầu vào hay thời gian.
- Owner đã chốt: **giữ nguyên `NostrRelayClient.fetch`**; giới hạn "20 bài mới nhất áp dụng trước bộ lọc" (một chuỗi có thể bị đẩy ra ngoài nếu tác giả đăng nhiều trả lời người khác) phải được **nói thẳng trong báo cáo**.
- R5: không magic number (hằng số có tên), test kèm mọi thay đổi. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, output ra file, chờ `.exit`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Chạy lớp test riêng (`--tests`) cho từng bước; full suite `--rerun-tasks` ở cuối, **đo lại số test**, không chép số cũ. Đỏ chập chờn: chạy riêng lớp đó 3 lần + chạy lại cả bộ, ghi thẳng cả hai kết quả.
- Mutation: một script nền, không sửa tệp đột biến cho tới `ALLDONE`; khôi phục + `cmp` sau mỗi đột biến; mỗi đột biến chạy các lớp Nostr; so sánh tự động tập test đỏ; **bảng tập đỏ mong đợi là dự đoán**, lệch thì đọc lý do (không bỏ qua).
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- Nói thẳng phần không kiểm: máy thật, mạng thật, relay thật, "bài 2/3" trong giao diện; lint không đổi.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **`toArticles` không kiểm chữ ký**, nên test mapper dựng sự kiện bằng `NostrEvent.parse(JSON)` với `id` hex 64 và `sig` toàn số 0 (đúng hình thức, không đúng chữ ký). `NostrEvent.parse` chỉ kiểm kiểu, không kiểm chữ ký (đã đọc). Test `LocalRssSvNostrSyncTest` đi qua `NostrFetchResult(notes(...))` do MockK trả về, **không** qua `isValid`, nên cũng dùng được sự kiện hình thức đó. **Không cần bộ ký Python.**
2. **Kiểm xanh giả của test chữ hoa:** id `…0001` toàn chữ số thì `uppercase()` không đổi; test "id chữ hoa" phải dùng id có chữ cái (`…0abc`), đã kiểm bằng Python (`upper differs: True`, `upper matches lower-hex: False`).
3. **`toArticles` hiện chạy bước `take(20)` SAU khi lọc**, nên phần lọc mới không làm đổi giới hạn 20 của mapper; chỉ `NostrRelayClient.fetch` cắt 20 **trước** (spec đã nêu, giữ nguyên).
4. **`isReply` đổi nghĩa:** từ "có thẻ `e`" thành "có thẻ `e` không phải `mention`". Hai test cũ khóa nghĩa cũ (`NostrEventTest.isReply_isTrueOnlyWithAnETag`, `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped`) vẫn đúng với nghĩa mới (fixture `REPLY` có marker `reply`, trỏ id `aaaa…` không có trong tập): test đầu giữ nguyên và **thêm** ca mới; test thứ hai giữ nguyên (khóa "trả lời bài không có trong tập vẫn bị bỏ").
5. **Đột biến vòng lặp (x4):** bản đệ quy không chặn vòng lặp sẽ **tràn ngăn xếp** (`StackOverflowError`), không treo; JUnit bắt nó như một test đỏ. Test vòng lặp thêm `@Test(timeout = ...)` làm lưới an toàn thứ hai. Nêu rõ trong báo cáo.
6. **Luật nhiều thẻ `reply`:** lấy thẻ **cuối** (spec); thẻ `e` không dấu (kiểu cũ): lấy thẻ không dấu **cuối cùng** sau khi bỏ `mention`; thẻ `e` thiếu phần tử id → `repliedToId = null` nhưng `isReply` vẫn đúng (bài bị bỏ).
7. **Mô hình tham chiếu Python** của toàn bộ luật (NIP-10 + giữ-nếu-đích-được-giữ) đã chạy trên 18 kịch bản và cho kết quả đúng như các test của plan mong đợi; Task 2 và Task 3 dựa trên các kịch bản đó.
8. **Phạm vi hai tệp test mới, một tệp sửa:** thêm lớp `NostrEventThreadTest` (đọc NIP-10) và `NostrFeedMapperThreadTest` (luật giữ bài) thay vì làm phình hai lớp cũ; sửa `LocalRssSvNostrSyncTest` thêm một ca.

## Dữ liệu mẫu dùng chung

Mọi test mới dựng sự kiện qua một hàm nhỏ trong test (cùng nội dung ở hai lớp, không tách chung để giữ mỗi lớp tự đủ):

```kotlin
private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
private fun id(n: Int) = "%064x".format(n)
private fun note(n: Int, createdAt: Long, content: String, vararg tags: List<String>): NostrEvent =
    requireNotNull(NostrEvent.parse(
        org.json.JSONObject().apply {
            put("id", id(n)); put("pubkey", author); put("created_at", createdAt); put("kind", 1)
            put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
            put("content", content); put("sig", "0".repeat(128))
        }.toString(),
    ))
private fun reply(n: Int, createdAt: Long, content: String, to: String, marker: String = "reply") =
    note(n, createdAt, content, listOf("e", to, "", marker))
```

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN; kiểm tự động ở Task 4)

Lớp **E** = `NostrEventThreadTest` (Task 2), lớp **M** = `NostrFeedMapperThreadTest` (Task 3), lớp **L** = `LocalRssSvNostrSyncTest` (Task 3). Tên test viết tắt:

**E1** `aNoteWithoutETags_isNotAReply`, **E2** `aMentionOnlyNote_isNotAReply`, **E3** `aReplyMarker_isAReply_andNamesItsTarget`, **E4** `aRootOnlyNote_repliesDirectlyToTheRoot`, **E5** `replyBeatsRoot_whenBothArePresent`, **E6** `oldStyleOneTag_isAReplyToThatNote`, **E7** `oldStyleManyTags_theLastUnmarkedOneIsTheTarget`, **E8** `aMentionAmongOldStyleTags_isNeverTheTarget`, **E9** `aBrokenTargetId_isStillAReply_butNamesNothing`, **E10** `severalReplyMarkers_theLastOneWins`, **E11** `anETagWithNoId_isAReplyThatNamesNothing`.
**M1** `anOwnThread_isKeptWholeNewestFirst`, **M2** `aReplyToSomeoneElse_isDropped`, **M3** `aReplyToADroppedNote_isDropped`, **M4** `aMentionOnlyNote_isKeptLikeAnyNote`, **M5** `aLoopOfReplies_endsAndDropsBoth`, **M6** `aNoteReplyingToItself_isDropped`, **M7** `theSameNoteTwice_isKeptOnce`, **M8** `theOrderOfTheInputChangesNothing`, **M9** `eachPieceOfAThreadHasItsOwnTitleAndLink`, **M10** `aRootOnlyReplyToAKeptNote_isKept`, **M11** `atMostTwentyArticles_stillHolds_withThreads`, **M12** `aReplyWhoseTargetIdIsBroken_isDropped`.
**L1** `anOwnThread_isSyncedWhole` (trong `LocalRssSvNostrSyncTest`).
Sẵn có: **O1** `NostrEventTest.isReply_isTrueOnlyWithAnETag`, **O2** `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped`.

| Đột biến | Đổi gì | Test đỏ mong đợi | Kiểu so |
|---|---|---|---|
| base | không | không | đúng |
| x1 | `toArticles` lại `filterNot { it.isReply }` (bỏ mọi trả lời) | M1, M9, M10, L1 | đúng |
| x2 | `toArticles` giữ mọi trả lời (bỏ kiểm đích) | M2, M3, M5, M6, M12, O2 | đúng |
| x3 | bỏ điều kiện "đích cũng được giữ" (chỉ kiểm đích có trong tập) | M3 | đúng |
| x4 | bỏ chặn vòng lặp | M5, M6 | đúng |
| x5 | `mention` tính là trả lời | E2, M4 | đúng |
| x6 | ưu tiên `root` hơn `reply` | E5 | đúng |
| x7 | kiểu cũ lấy thẻ không dấu **đầu** thay vì cuối | E7 | đúng |
| x8 | bỏ kiểm id hex 64 chữ thường | E9, M12 | đúng (E11: thẻ `["e"]` không có id vẫn `null`, **không** đỏ; id rỗng `""` của E9 thì đỏ) |
| x9 | giữ/bỏ phụ thuộc thứ tự đầu vào (chỉ xét các bài đứng **trước** trong danh sách) | M8 | đúng |
| x10 | nhiều thẻ `reply` lấy thẻ **đầu** thay vì cuối | E10 | đúng |
| x11 | `mention` có thể là đích kiểu cũ | E8 | đúng |

"Đúng": phải trùng chính xác. Đây là **dự đoán**; lệch thì đọc lý do và sửa bảng hoặc test (ghi trong báo cáo). Phần của lớp E (x5 đến x8, x10) đã được **dự đoán lại bằng một bản chuyển Python của các kịch bản test** (kết quả: x5 chỉ E2; x6 chỉ E5; x7 chỉ E7; x8 làm đỏ E9 và E11 trong bản chuyển, nhưng mã Kotlin trả `null` sớm (`?: return null`) khi thẻ không có phần tử id nên E11 **vẫn xanh** ở Kotlin: bảng giữ E9 và M12; x10 chỉ E10); phần của lớp M (x1 đến x4, x9) chưa có bản chuyển tương đương nên là dự đoán thuần.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt` | `isReply` đổi nghĩa, thêm `repliedToId` |
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` | luật giữ bài |
| Create `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventThreadTest.kt` | E1 đến E11 |
| Create `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt` | M1 đến M12 |
| Modify `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvNostrSyncTest.kt` | thêm L1 |
| Tạm, **không commit**: `/tmp/x_mut.sh`, `/tmp/x_batch.sh`, `/tmp/x_check.py` | đột biến JVM |
| Modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02x_nostr-own-threads_DONE.md` | báo cáo |

---

### Task 1: `NostrEvent` hiểu NIP-10 (`isReply` đổi nghĩa, thêm `repliedToId`) (TDD)

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventThreadTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt`

**Interfaces:**
- Consumes: `NostrEvent(id, pubkey, createdAt, kind, tags, content, sig)`, `NostrEvent.parse(json): NostrEvent?`.
- Produces: `NostrEvent.isReply: Boolean` (có thẻ `e` không phải `mention`) và `NostrEvent.repliedToId: String?` (id bài được trả lời theo NIP-10, chỉ khi hex 64 chữ thường, ngược lại `null`).

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
N=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt
grep -n "val isReply\|REPLY_TAG\|A reply carries\|ID_HEX_LENGTH\|private fun isLowerHex" $N
git status --short app
```
Expected: `isReply` (1 dòng), `REPLY_TAG = "e"` (khai báo), `ID_HEX_LENGTH`, `isLowerHex`; `git status` sạch trong `app`.

- [ ] **Step 2: Viết test đỏ `NostrEventThreadTest.kt` (E1 đến E11)**

```bash
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventThreadTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How a note says "I answer that note" (NIP-10). The signature is not looked at here (never needed to read tags), so the notes are
 * built by hand with a well-formed but meaningless signature.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrEventThreadTest {
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private fun id(n: Int) = "%064x".format(n)
    private fun note(n: Int, vararg tags: List<String>): NostrEvent =
        requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", author); put("created_at", 1_700_000_000L + n); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", "x"); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )

    @Test fun aNoteWithoutETags_isNotAReply() {
        val plain = note(1, listOf("t", "news"))
        assertFalse(plain.isReply)
        assertNull(plain.repliedToId)
    }

    @Test fun aMentionOnlyNote_isNotAReply() {
        val quoting = note(2, listOf("e", id(1), "", "mention"))
        assertFalse("a note that only points at another is not an answer", quoting.isReply)
        assertNull(quoting.repliedToId)
    }

    @Test fun aReplyMarker_isAReply_andNamesItsTarget() {
        val reply = note(3, listOf("e", id(1), "", "reply"))
        assertTrue(reply.isReply)
        assertEquals(id(1), reply.repliedToId)
    }

    @Test fun aRootOnlyNote_repliesDirectlyToTheRoot() {
        val direct = note(4, listOf("e", id(1), "", "root"))
        assertTrue(direct.isReply)
        assertEquals(id(1), direct.repliedToId)
    }

    @Test fun replyBeatsRoot_whenBothArePresent() {
        val deep = note(5, listOf("e", id(1), "", "root"), listOf("e", id(2), "", "reply"))
        assertEquals("the note being answered, not the root of the thread", id(2), deep.repliedToId)
        val deepReversed = note(6, listOf("e", id(2), "", "reply"), listOf("e", id(1), "", "root"))
        assertEquals(id(2), deepReversed.repliedToId)
    }

    @Test fun oldStyleOneTag_isAReplyToThatNote() {
        val old = note(7, listOf("e", id(1)))
        assertTrue(old.isReply)
        assertEquals(id(1), old.repliedToId)
    }

    @Test fun oldStyleManyTags_theLastUnmarkedOneIsTheTarget() {
        val old = note(8, listOf("e", id(1)), listOf("e", id(2)), listOf("e", id(3)))
        assertEquals("first is the root, last is the note being answered", id(3), old.repliedToId)
    }

    @Test fun aMentionAmongOldStyleTags_isNeverTheTarget() {
        val old = note(9, listOf("e", id(1)), listOf("e", id(9), "", "mention"))
        assertTrue(old.isReply)
        assertEquals("the mention comes last but is only a mention", id(1), old.repliedToId)
    }

    @Test fun aBrokenTargetId_isStillAReply_butNamesNothing() {
        val upperId = "%064X".format(0xabc)
        listOf("XYZ", id(1).dropLast(2), upperId, "").forEach { bad ->
            val reply = note(10, listOf("e", bad, "", "reply"))
            assertTrue(bad, reply.isReply)
            assertNull(bad, reply.repliedToId)
        }
    }

    @Test fun severalReplyMarkers_theLastOneWins() {
        val odd = note(11, listOf("e", id(1), "", "reply"), listOf("e", id(2), "", "reply"))
        assertEquals(id(2), odd.repliedToId)
    }

    @Test fun anETagWithNoId_isAReplyThatNamesNothing() {
        val bare = note(12, listOf("e"))
        assertTrue(bare.isReply)
        assertNull(bare.repliedToId)
    }
}
EOF
```

- [ ] **Step 3: Chạy test, phải ĐỎ (lỗi biên dịch: chưa có `repliedToId`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/x-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrEventThreadTest" > /tmp/x-t1.log 2>&1; echo $? > /tmp/x-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/x-t1.exit && break; sleep 5; done; cat /tmp/x-t1.exit; grep -E "^e: " /tmp/x-t1.log | sed -E 's/.*(NostrEventThreadTest.kt:[0-9]+:[0-9]+ )/\1/' | cut -c1-110 | sort -u | head -4
```
Expected: `1` và `Unresolved reference: repliedToId`.

- [ ] **Step 4: Sửa `NostrEvent.kt` (ba chỗ, mỗi mẫu đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
N = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt"
s = open(N, encoding="utf-8").read()
def sub(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)

sub('''    /** A reply carries an `e` tag (it answers another note); the feed shows originals only. */
    val isReply: Boolean get() = tags.any { it.firstOrNull() == REPLY_TAG }
''', '''    /**
     * A reply carries an `e` tag that is not just a mention (NIP-10): it answers another note. A note whose only `e` tags say
     * `mention` points at other notes without answering them, so it counts as an ordinary note.
     */
    val isReply: Boolean get() = answeredTags().isNotEmpty()

    /**
     * The id of the note this one answers, read the NIP-10 way: the tag marked `reply` (the last one if several), else the one marked
     * `root`, else (old style, no markers) the last unmarked tag. Only a lower-case 64-character hex id is accepted, anything else
     * is null (the note is still a reply, it just names nothing the app can find).
     */
    val repliedToId: String?
        get() {
            val answered = answeredTags()
            val pick = answered.lastOrNull { marker(it) == MARKER_REPLY }
                ?: answered.lastOrNull { marker(it) == MARKER_ROOT }
                ?: answered.lastOrNull { marker(it).isEmpty() }
            val target = pick?.getOrNull(ETAG_ID_INDEX) ?: return null
            return target.takeIf { isLowerHex(it, ID_HEX_LENGTH) }
        }

    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }

    private fun marker(tag: List<String>): String = tag.getOrNull(ETAG_MARKER_INDEX).orEmpty()
''')
sub('''        private const val REPLY_TAG = "e"\n''', '''        private const val REPLY_TAG = "e"
        private const val ETAG_ID_INDEX = 1
        private const val ETAG_MARKER_INDEX = 3
        private const val MARKER_REPLY = "reply"
        private const val MARKER_ROOT = "root"
        private const val MARKER_MENTION = "mention"
''')
open(N, "w", encoding="utf-8").write(s)
EOF
grep -n "answeredTags\|repliedToId\|MARKER_\|ETAG_" app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt | head -16
```
Expected: `answeredTags`, `repliedToId`, bốn hằng `MARKER_*` và `ETAG_*` mới. **Đã kiểm:** `isLowerHex` là hàm `private` trong `companion object` và dòng 33 hiện tại của `NostrEvent.kt` (`hasValidSignature`) đã gọi nó từ lớp ngoài, nên cách dùng này biên dịch được.

- [ ] **Step 5: Chạy test, phải XANH (lớp mới, `NostrEventTest` cũ để chắc `isReply` cũ vẫn đúng, và `NostrFeedMapperTest` cũ)**

```bash
rm -f /tmp/x-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrEventThreadTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrEventTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperTest" > /tmp/x-t1.log 2>&1; echo $? > /tmp/x-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/x-t1.exit && break; sleep 5; done; cat /tmp/x-t1.exit; grep -E "FAILED|tests completed|^e: " /tmp/x-t1.log | head -8 | cut -c1-200; tail -2 /tmp/x-t1.log
```
Expected: `0`, `BUILD SUCCESSFUL`. (`NostrFeedMapperTest` vẫn xanh vì `toArticles` chưa đổi và `REPLY` mẫu vẫn là trả lời.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEventThreadTest.kt
git commit -m "feat(front-02x): read NIP-10 reply markers, a note that only mentions another is not a reply

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `NostrFeedMapper.toArticles` giữ chuỗi tự trả lời (TDD)

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt`

**Interfaces:**
- Consumes: `NostrEvent.isReply`, `NostrEvent.repliedToId` (Task 1); `NostrFeedMapper.toArticles(events: List<NostrEvent>, feedId: String, accountId: Int, nowMillis: Long, newId: () -> String): List<Article>` (chữ ký **không đổi**).
- Produces: `toArticles` giữ bài không phải trả lời, và bài trả lời một bài (trong cùng danh sách) mà chính bài đó cũng được giữ.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
M=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt
grep -n "filterNot { it.isReply }\|distinctBy { it.id }\|fun toArticles\|sortedByDescending\|take(NostrRelayClient.MAX_NOTES_KEPT)\|private fun escapeToHtml" $M
git status --short app
```
Expected: mỗi mẫu 1 dòng; sạch trong `app`.

- [ ] **Step 2: Viết test đỏ `NostrFeedMapperThreadTest.kt` (M1 đến M12)**

```bash
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which replies the Nostr feed keeps: only those that continue the author's OWN kept notes (a thread). The signature is never checked
 * by the mapper, so the notes are built by hand. A note is kept when it is not a reply, or when the note it answers is itself kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrFeedMapperThreadTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private fun id(n: Int) = "%064x".format(n)
    private fun note(n: Int, createdAt: Long, content: String, vararg tags: List<String>): NostrEvent =
        requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", author); put("created_at", createdAt); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
    private fun reply(n: Int, createdAt: Long, content: String, to: String, marker: String = "reply") =
        note(n, createdAt, content, listOf("e", to, "", marker))

    private fun titles(vararg events: NostrEvent): List<String> {
        val counter = AtomicInteger()
        return NostrFeedMapper.toArticles(events.toList(), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }.map { it.title }
    }

    // the thread A <- B <- C, all by the author
    private val a = note(1, 1_700_000_100L, "Part one")
    private val b = reply(2, 1_700_000_200L, "Part two", id(1))
    private val c = reply(3, 1_700_000_300L, "Part three", id(2))

    @Test fun anOwnThread_isKeptWholeNewestFirst() {
        assertEquals(listOf("Part three", "Part two", "Part one"), titles(a, b, c))
    }

    @Test fun aReplyToSomeoneElse_isDropped() {
        val toStranger = reply(9, 1_700_000_150L, "Answer to a stranger", id(99))
        assertEquals(listOf("Part one"), titles(a, toStranger))
    }

    @Test fun aReplyToADroppedNote_isDropped() {
        val toStranger = reply(9, 1_700_000_150L, "Answer to a stranger", id(99))
        val followUp = reply(4, 1_700_000_400L, "Follow-up to my own answer", id(9))
        assertEquals("the follow-up would read with no context", emptyList<String>(), titles(toStranger, followUp))
    }

    @Test fun aMentionOnlyNote_isKeptLikeAnyNote() {
        val quoting = note(5, 1_700_000_500L, "Look at this", listOf("e", id(1), "", "mention"))
        assertEquals(listOf("Look at this", "Part one"), titles(a, quoting))
    }

    @Test(timeout = 5_000)
    fun aLoopOfReplies_endsAndDropsBoth() {
        val first = reply(6, 1_700_000_600L, "Loop one", id(7))
        val second = reply(7, 1_700_000_700L, "Loop two", id(6))
        assertEquals(listOf("Part one"), titles(first, second, a))
    }

    @Test(timeout = 5_000)
    fun aNoteReplyingToItself_isDropped() {
        val selfish = reply(8, 1_700_000_800L, "Answering myself in a loop", id(8))
        assertEquals(listOf("Part one"), titles(selfish, a))
    }

    @Test fun theSameNoteTwice_isKeptOnce() {
        assertEquals(listOf("Part two", "Part one"), titles(a, a, b))
    }

    @Test fun theOrderOfTheInputChangesNothing() {
        val expected = listOf("Part three", "Part two", "Part one")
        assertEquals(expected, titles(a, b, c))
        assertEquals(expected, titles(c, b, a))
        assertEquals(expected, titles(b, c, a))
        assertEquals(expected, titles(c, a, b))
    }

    @Test fun eachPieceOfAThreadHasItsOwnTitleAndLink() {
        val counter = AtomicInteger()
        val articles = NostrFeedMapper.toArticles(listOf(a, b, c), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
        assertEquals(3, articles.size)
        assertEquals(3, articles.map { it.link }.toSet().size)
        assertTrue(articles.all { it.link.startsWith("https://njump.me/note1") })
        assertEquals(listOf("Part three", "Part two", "Part one"), articles.map { it.title })
        assertEquals(listOf(1_700_000_300_000L, 1_700_000_200_000L, 1_700_000_100_000L), articles.map { it.date.time })
    }

    @Test fun aRootOnlyReplyToAKeptNote_isKept() {
        val direct = reply(10, 1_700_000_900L, "Straight to the root", id(1), marker = "root")
        assertEquals(listOf("Straight to the root", "Part one"), titles(a, direct))
    }

    @Test fun atMostTwentyArticles_stillHolds_withThreads() {
        val root = note(100, 1_700_001_000L, "root")
        val chain = (1..25).map { n -> reply(100 + n, 1_700_001_000L + n, "piece $n", id(100 + n - 1)) }
        val articles = NostrFeedMapper.toArticles(listOf(root) + chain, "feed-1", 7, now)
        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, articles.size)
        assertEquals("piece 25", articles.first().title)
    }

    @Test fun aReplyWhoseTargetIdIsBroken_isDropped() {
        val broken = note(11, 1_700_001_100L, "Answers nothing findable", listOf("e", "not-an-id", "", "reply"))
        assertEquals(listOf("Part one"), titles(a, broken))
    }
}
EOF
```

- [ ] **Step 3: Chạy test, phải ĐỎ đúng các test về chuỗi (không lỗi biên dịch: chữ ký không đổi)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/x-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperThreadTest" > /tmp/x-t2.log 2>&1; echo $? > /tmp/x-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/x-t2.exit && break; sleep 5; done; cat /tmp/x-t2.exit; grep -E "FAILED|^e: " /tmp/x-t2.log | cut -c1-170 | head -12
```
Expected: `1`, đỏ **đúng**: `anOwnThread_isKeptWholeNewestFirst`, `eachPieceOfAThreadHasItsOwnTitleAndLink`, `aRootOnlyReplyToAKeptNote_isKept`, `atMostTwentyArticles_stillHolds_withThreads` (mapper cũ bỏ mọi trả lời). **Xanh sẵn** (vì bỏ trả lời cũng cho kết quả đúng): `aReplyToSomeoneElse_isDropped`, `aReplyToADroppedNote_isDropped`, `aLoopOfReplies_endsAndDropsBoth`, `aNoteReplyingToItself_isDropped`, `aReplyWhoseTargetIdIsBroken_isDropped`, `theSameNoteTwice_isKeptOnce`, `aMentionOnlyNote_isKeptLikeAnyNote` (mention giờ đã không phải trả lời từ Task 1), `theOrderOfTheInputChangesNothing` có thể đỏ vì chuỗi cần giữ. Test xanh sẵn **không vô nghĩa**: chúng là lưới an toàn cho đột biến x2 đến x4, x9 ở Task 4. Có test đỏ **ngoài** danh sách trên thì đọc lý do trước khi làm tiếp.

- [ ] **Step 4: Sửa `NostrFeedMapper.toArticles` (một mẫu, đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
M = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(M, encoding="utf-8").read()
def sub(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)

sub('''        events.asSequence()
            .filterNot { it.isReply }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }''',
    '''        keepOriginalsAndOwnThreads(events).asSequence()
            .sortedByDescending { it.createdAt }''')
sub('''    private fun escapeToHtml(text: String): String = buildString {''',
    '''    /**
     * The notes the feed shows: every note that is not a reply, plus every reply to a note that is itself kept (the author continuing
     * their own thread). Whether a note is kept never depends on the order of the list or on the dates. A reply to a stranger's note
     * is dropped, and so is anything that only answers such a reply, because it would be read with no context. A loop of replies
     * (a note that answers itself, directly or through others) is dropped as a whole, never followed forever.
     */
    private fun keepOriginalsAndOwnThreads(events: List<NostrEvent>): List<NostrEvent> {
        val byId = LinkedHashMap<String, NostrEvent>()
        for (event in events) byId.putIfAbsent(event.id, event)
        val kept = HashMap<String, Boolean>()
        fun isKept(id: String, visiting: Set<String>): Boolean {
            kept[id]?.let { return it }
            if (id in visiting) return false
            val event = byId.getValue(id)
            val target = event.repliedToId
            val result = !event.isReply || (target != null && target in byId && isKept(target, visiting + id))
            // A verdict reached while a loop was open can depend on where the walk began, so only a verdict that did not touch a loop is stored.
            if (visiting.isEmpty() || result) kept[id] = result
            return result
        }
        return byId.values.filter { isKept(it.id, emptySet()) }
    }

    private fun escapeToHtml(text: String): String = buildString {''')
open(M, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/main | tail -2
```
Expected: 1 tệp, khoảng 25 dòng thêm.

> Ghi chú thiết kế (đọc kỹ): bộ nhớ đệm `kept` chỉ ghi kết quả khi (a) bắt đầu từ gốc (`visiting.isEmpty()`) hoặc (b) kết quả là **giữ** (đã đi hết chuỗi đến một bài không phải trả lời, nên không thể phụ thuộc vòng lặp). Kết quả **bỏ** ở giữa một lần đi có thể do vòng lặp mở nên không lưu, tránh kết luận sai tùy thứ tự. Mô hình tham chiếu Python (không dùng bộ nhớ đệm sai) đã cho cùng kết quả trên 18 kịch bản; test M8 khóa việc không phụ thuộc thứ tự.

- [ ] **Step 5: Chạy test, phải XANH (lớp mới, mapper cũ, event cũ)**

```bash
rm -f /tmp/x-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperThreadTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrEventThreadTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrEventTest" > /tmp/x-t2.log 2>&1; echo $? > /tmp/x-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/x-t2.exit && break; sleep 5; done; cat /tmp/x-t2.exit; grep -E "FAILED|tests completed|^e: " /tmp/x-t2.log | head -8 | cut -c1-200; tail -2 /tmp/x-t2.log
```
Expected: `0`, `BUILD SUCCESSFUL`. `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped` vẫn xanh: `REPLY` mẫu trả lời id `aaaa…` không có trong tập nên bị bỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt
git commit -m "feat(front-02x): keep an author's own reply threads in a Nostr source, drop replies to strangers

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Ca đồng bộ: chuỗi tự trả lời đi hết đường ghi bài (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvNostrSyncTest.kt`

**Interfaces:**
- Consumes: mã của Task 1 và 2; `LocalRssSv.syncSingleFeed(feed)`, `NostrFetchResult(notes, profileName)`, `NostrEvent.parse`.
- Produces: test `anOwnThread_isSyncedWhole` (L1). Không đổi mã production.

> Ghi chú TDD (nói thẳng): mã production đã đổi ở Task 1 và 2 nên test này **không có bước đỏ trước** (nó kiểm đường đồng bộ đi qua mã mới). Bằng chứng nó bắt được lỗi là đột biến `x1` ở Task 4 (L1 đỏ khi `toArticles` lại bỏ mọi trả lời).

- [ ] **Step 1: Thêm test L1 (chèn ngay trước dấu `}` cuối của lớp; mẫu duy nhất)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvNostrSyncTest.kt"
s = open(T, encoding="utf-8").read()
tail = '''        val result = service.syncSingleFeed(feed)
        assertEquals("Nostr", result?.feed?.name)
    }
}
'''
assert s.count(tail) == 1 and s.endswith(tail)
new = '''        val result = service.syncSingleFeed(feed)
        assertEquals("Nostr", result?.feed?.name)
    }

    // FRONT-02x: the author continuing their own thread is synced, answering a stranger is not.
    @Test fun anOwnThread_isSyncedWhole() = runBlocking {
        fun id(n: Int) = "%064x".format(n)
        fun note(n: Int, createdAt: Long, content: String, vararg tags: List<String>): NostrEvent = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", NostrTestEvents.AUTHOR); put("created_at", createdAt); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        val first = note(1, 1_700_000_100L, "Thread part one")
        val second = note(2, 1_700_000_200L, "Thread part two", listOf("e", id(1), "", "reply"))
        val toStranger = note(3, 1_700_000_300L, "Answer to somebody else", listOf("e", id(99), "", "reply"))
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(listOf(toStranger, second, first), null)
        val articles = service.syncSingleFeed(feed)?.articles.orEmpty()
        assertEquals(listOf("Thread part two", "Thread part one"), articles.map { it.title })
        assertTrue(articles.all { it.feedId == "nostr-feed" && it.link.startsWith("https://njump.me/note1") })
    }
}
'''
open(T, "w", encoding="utf-8").write(s[: -len(tail)] + new)
EOF
git diff --stat -- app/src/test | tail -1
```
Expected: 1 tệp, khoảng 25 dòng thêm.

- [ ] **Step 2: Chạy test, phải XANH (cả lớp sync cũ lẫn mới)**

```bash
rm -f /tmp/x-t3.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" > /tmp/x-t3.log 2>&1; echo $? > /tmp/x-t3.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/x-t3.exit && break; sleep 5; done; cat /tmp/x-t3.exit; grep -E "FAILED|tests completed|^e: " /tmp/x-t3.log | head -6 | cut -c1-200; tail -2 /tmp/x-t3.log
```
Expected: `0`, `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvNostrSyncTest.kt
git commit -m "test(front-02x): a Nostr sync keeps the author's own thread and drops the answer to a stranger

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Mutation JVM (11 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/x_mut.sh`, `/tmp/x_batch.sh`, `/tmp/x_check.py`. Đột biến tạm trong `NostrEvent.kt` và `NostrFeedMapper.kt` (luôn khôi phục).

**Interfaces:** Consumes: mã sau Task 1 đến 3. Produces: bảng đột biến với tập test đỏ **đo được**, so với bảng dự đoán ở đầu plan.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất (mỗi mẫu đúng 1 lần), trên mã đã làm xong Task 1 đến 3**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/"
ev = open(J + "NostrEvent.kt", encoding="utf-8").read()
mp = open(J + "NostrFeedMapper.kt", encoding="utf-8").read()
pats = [
 ("x1/x2", mp, "        keepOriginalsAndOwnThreads(events).asSequence()\n"),
 ("x3", mp, "val result = !event.isReply || (target != null && target in byId && isKept(target, visiting + id))"),
 ("x4", mp, "            if (id in visiting) return false\n"),
 ("x5", ev, "    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }"),
 ("x6", ev, "            val pick = answered.lastOrNull { marker(it) == MARKER_REPLY }\n                ?: answered.lastOrNull { marker(it) == MARKER_ROOT }\n"),
 ("x7", ev, "                ?: answered.lastOrNull { marker(it).isEmpty() }\n"),
 ("x8", ev, "            return target.takeIf { isLowerHex(it, ID_HEX_LENGTH) }\n"),
 ("x9", mp, "            if (visiting.isEmpty() || result) kept[id] = result\n"),
 ("x10", ev, "            val pick = answered.lastOrNull { marker(it) == MARKER_REPLY }\n"),
 ("x11", ev, "    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }"),
]
print({l: s.count(p) for l, s, p in pats})
EOF
```
Expected: mọi nhãn `1`. Nhãn nào khác 1: đọc lại tệp và sửa mẫu ở `x_batch.sh` (không chạy batch).

- [ ] **Step 2: Dựng `/tmp/x_mut.sh` (một đột biến một lần, chỉ JVM, các lớp Nostr và sync)**

```bash
cat > /tmp/x_mut.sh <<'EOF'
#!/bin/zsh
# usage: x_mut.sh <label> <file> <old> <new>   ("base" = no change). JVM only, never touches a device.
label="$1"; FILE="${2-}"; old="${3-}"; new="${4-}"
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
BACKUP=/tmp/x-mutbackup.bin
rest=none
if [ "$label" != base ]; then
  cp -p "$FILE" "$BACKUP"
  restore() { cp -p "$BACKUP" "$FILE"; cmp -s "$BACKUP" "$FILE" && rest=restored || rest=NOT-RESTORED; }
  trap restore EXIT INT TERM
  python3 - "$FILE" "$old" "$new" <<'PY'
import sys
f, o, n = sys.argv[1:4]
s = open(f, encoding="utf-8").read()
assert s.count(o) == 1, "pattern missing or not unique"
open(f, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
  [ $? -eq 0 ] || { echo "$label: PATTERN-PROBLEM"; exit 91; }
fi
./gradlew testDevDebugUnitTest \
  --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" \
  --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" \
  --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" \
  > "/tmp/x-mut-$label.log" 2>&1
rc=$?
[ "$label" != base ] && { restore; trap - EXIT INT TERM; }
failing=$(grep -E "^com\.mckimquyen.* > .* FAILED" "/tmp/x-mut-$label.log" | sed -E 's/^.* > ([A-Za-z0-9_`. ,()-]+) FAILED.*/\1/' | sort -u | tr '\n' ',')
compile=$(grep -c "^e: " "/tmp/x-mut-$label.log")
echo "$label: gradle_exit=$rc compile_errors=$compile failing=[$failing] source=$rest"
EOF
chmod +x /tmp/x_mut.sh
```
Đột biến lỗi biên dịch (`compile_errors>0`) thì không đo được tập đỏ; đổi mẫu cho biên dịch được trước khi chạy batch.

- [ ] **Step 3: Dựng `/tmp/x_batch.sh` (một batch nền, khôi phục + `cmp`, `ALLDONE`)**

```bash
cat > /tmp/x_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr
EV=$J/NostrEvent.kt; MP=$J/NostrFeedMapper.kt
cp -p $EV /tmp/x-orig-ev.kt; cp -p $MP /tmp/x-orig-mp.kt
M=/tmp/x_mut.sh
zsh $M base
zsh $M x1 $MP '        keepOriginalsAndOwnThreads(events).asSequence()
' '        events.filterNot { it.isReply }.distinctBy { it.id }.asSequence()
'
zsh $M x2 $MP '        keepOriginalsAndOwnThreads(events).asSequence()
' '        events.distinctBy { it.id }.asSequence()
'
zsh $M x3 $MP 'val result = !event.isReply || (target != null && target in byId && isKept(target, visiting + id))' 'val result = !event.isReply || (target != null && target in byId)'
zsh $M x4 $MP '            if (id in visiting) return false
' ''
zsh $M x5 $EV '    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }' '    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG }'
zsh $M x6 $EV '            val pick = answered.lastOrNull { marker(it) == MARKER_REPLY }
                ?: answered.lastOrNull { marker(it) == MARKER_ROOT }
' '            val pick = answered.lastOrNull { marker(it) == MARKER_ROOT }
                ?: answered.lastOrNull { marker(it) == MARKER_REPLY }
'
zsh $M x7 $EV '                ?: answered.lastOrNull { marker(it).isEmpty() }
' '                ?: answered.firstOrNull { marker(it).isEmpty() }
'
zsh $M x8 $EV '            return target.takeIf { isLowerHex(it, ID_HEX_LENGTH) }
' '            return target
'
zsh $M x9 $MP '            if (visiting.isEmpty() || result) kept[id] = result
' '            kept[id] = result
'
zsh $M x10 $EV '            val pick = answered.lastOrNull { marker(it) == MARKER_REPLY }
' '            val pick = answered.firstOrNull { marker(it) == MARKER_REPLY }
'
zsh $M x11 $EV '    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }' '    private fun answeredTags(): List<List<String>> = tags.filter { it.firstOrNull() == REPLY_TAG && marker(it) != MARKER_MENTION }
    private fun unusedMentionHook(): Boolean = tags.any { marker(it) == MARKER_MENTION }'
zsh $M base
cmp -s /tmp/x-orig-ev.kt $EV && echo "final ev: identical" || echo "final ev: DIFFERENT"
cmp -s /tmp/x-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "git diff vs HEAD (main): $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
```
**Hai đột biến cần giải thích:** `x9` đổi cách ghi đệm (ghi mọi kết quả kể cả trong lúc vòng lặp mở): đây là đột biến **mục tiêu** để chứng minh test M8 (đảo thứ tự) bắt lỗi phụ thuộc thứ tự; `x11` thêm một hàm không dùng nên **không đổi hành vi** (đột biến tương đương: dự đoán **không test nào đỏ**) và chỉ để xác nhận rằng script phân biệt được "không đổi" với "đổi". Nếu `x11` đỏ bất kỳ test nào thì có vấn đề với công cụ.

- [ ] **Step 4: Dựng `/tmp/x_check.py` (so sánh tự động; đây là DỰ ĐOÁN, lệch thì đọc lý do)**

```bash
cat > /tmp/x_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
E = {
 "E1": "aNoteWithoutETags_isNotAReply", "E2": "aMentionOnlyNote_isNotAReply", "E3": "aReplyMarker_isAReply_andNamesItsTarget",
 "E4": "aRootOnlyNote_repliesDirectlyToTheRoot", "E5": "replyBeatsRoot_whenBothArePresent", "E6": "oldStyleOneTag_isAReplyToThatNote",
 "E7": "oldStyleManyTags_theLastUnmarkedOneIsTheTarget", "E8": "aMentionAmongOldStyleTags_isNeverTheTarget",
 "E9": "aBrokenTargetId_isStillAReply_butNamesNothing", "E10": "severalReplyMarkers_theLastOneWins", "E11": "anETagWithNoId_isAReplyThatNamesNothing",
}
M = {
 "M1": "anOwnThread_isKeptWholeNewestFirst", "M2": "aReplyToSomeoneElse_isDropped", "M3": "aReplyToADroppedNote_isDropped",
 "M4": "aMentionOnlyNote_isKeptLikeAnyNote", "M5": "aLoopOfReplies_endsAndDropsBoth", "M6": "aNoteReplyingToItself_isDropped",
 "M7": "theSameNoteTwice_isKeptOnce", "M8": "theOrderOfTheInputChangesNothing", "M9": "eachPieceOfAThreadHasItsOwnTitleAndLink",
 "M10": "aRootOnlyReplyToAKeptNote_isKept", "M11": "atMostTwentyArticles_stillHolds_withThreads", "M12": "aReplyWhoseTargetIdIsBroken_isDropped",
}
L1 = "anOwnThread_isSyncedWhole"; O2 = "repliesAndBlankNotesAreSkipped"
S = lambda *ks: {({**E, **M}[k] if k in {**E, **M} else k) for k in ks}
EXPECTED = {
 "x1": S("M1", "M9", "M10", "M11", L1),
 "x2": S("M2", "M3", "M5", "M6", "M12", O2),
 "x3": S("M3"),
 "x4": S("M5", "M6"),
 "x5": S("E2", "M4"),
 "x6": S("E5"),
 "x7": S("E7"),
 "x8": S("E9", "M12"),
 "x9": S("M8"),
 "x10": S("E10"),
 "x11": set(),
}
bad = 0
for label, want in EXPECTED.items():
    try:
        text = open(f"/tmp/x-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError:
        print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M):
        print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = got == want
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/x-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN")
bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```
`x11` thành công cũng phải làm **gradle_exit=0** (không test đỏ); `x_check.py` coi đó là đúng (tập rỗng).

- [ ] **Step 5: Chạy batch nền, chờ ALLDONE**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/x-batch.log; ( zsh /tmp/x_batch.sh > /tmp/x-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/x-batch.log 2>/dev/null && break; sleep 5; done; cut -c1-230 /tmp/x-batch.log
```
Không sửa hai tệp production cho tới `ALLDONE`. Mỗi đột biến mất 1 đến 2 phút; chưa xong sau 115×5 giây thì chạy lại **chỉ** vòng chờ (nhiều lần nếu cần).

- [ ] **Step 6: Đối chiếu tự động và xử lý lệch**

```bash
python3 /tmp/x_check.py
```
Expected: `ALL-MATCH`. Đột biến lệch: **đọc lý do** (tập đỏ thực tế và thông báo trong `/tmp/x-mut-<nhãn>.log`) rồi chọn: (a) dự đoán sai, sửa bảng ở đầu plan và `x_check.py`, ghi trong báo cáo; (b) test yếu (đột biến sống sót, mọi test xanh): thêm khẳng định (quay lại Task 1 đến 3) rồi chạy lại batch. Với `x4` (mất chặn vòng lặp): mong đợi `StackOverflowError` ở M5 và M6 (lỗi ghi là FAILED, không treo); nếu thay vào đó bộ chạy treo tới hết `timeout = 5_000` thì cũng là FAILED và vẫn đúng tập. Kiểm cuối batch: hai dòng `final … identical` và `git diff vs HEAD (main): 0 lines`.

---

### Task 5: JVM đầy đủ, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng FRONT-02b và dòng 02x mới), `doc/task/todo/FRONT-02_decentralized-web3-social.md` (mục "Cập nhật"), `docs/superpowers/specs/2026-11-18-front-02x-nostr-own-threads-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02x_nostr-own-threads_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/x-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/x-jvm.log 2>&1; echo $? > /tmp/x-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/x-jvm.exit && break; sleep 5; done; cat /tmp/x-jvm.exit; grep -E "FAILED|tests completed" /tmp/x-jvm.log | head -5; tail -3 /tmp/x-jvm.log
python3 - <<'EOF'
import glob, re
t = f = e = s = 0
for p in glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml'):
    h = open(p, encoding='utf-8').read(2000)
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', h)
    a, b, c, d = map(int, m.groups()); t += a; s += b; f += c; e += d
print('tests', t, 'skipped', s, 'failures', f, 'errors', e)
EOF
```
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi. Số test phải lớn hơn **số trước đó đúng bằng số test thêm** (11 + 12 + 1 = 24 test mới; số trước là **3.822** theo báo cáo FRONT-02w, kỳ vọng **3.846**; chỉ để đối chiếu, báo cáo ghi số **đo được**). Có test đỏ **không liên quan**: chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả (không bỏ qua, không giấu).

- [ ] **Step 2: Kiểm phạm vi diff (đúng hai tệp production)**

```bash
git status --short; git diff --stat HEAD~3 HEAD -- app/src/main app/build.gradle app/schemas
git diff --name-only HEAD~3 HEAD
```
Expected: `app/src/main` chỉ có `NostrEvent.kt` và `NostrFeedMapper.kt`; không `app/build.gradle`, `app/schemas`, manifest, `res`, `NostrRelayClient.kt`, `LocalRssSv.kt`, `SubscribeViewModel.kt`; test: hai tệp mới và `LocalRssSvNostrSyncTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md`, task gốc và spec**

Báo cáo `doc/task/done/FRONT-02x_nostr-own-threads_DONE.md` (tiếng Việt đơn giản) gồm: điều đã làm (hai tệp production); luật giữ bài và cách đọc NIP-10; bảng 11 đột biến với tập test đỏ **đo được** (kèm kết quả `x_check.py`, ghi mọi dự đoán sai đã sửa và `x11` là đột biến tương đương); mô hình tham chiếu Python đã kiểm hơn 40.000 hoán vị cho việc ghi đệm; JVM số **đo lại**; **giới hạn nói thẳng**: `NostrRelayClient.fetch` cắt 20 bài mới nhất (tính cả trả lời người khác) **trước** khi bộ lọc chạy nên chuỗi có thể bị đẩy ra ngoài nếu tác giả đăng nhiều trả lời người khác; mảnh đầu chuỗi nằm ngoài 50 khung thì cả phần sau bị bỏ; không có dấu "bài 2/3"; nguồn đã đăng ký sẽ thấy thêm bài chuỗi ở lần đồng bộ sau (bài cũ không đổi); trả lời người khác vẫn không hiện; chưa kiểm relay hay máy thật.

`doc/feature.md`: dòng FRONT-02b thêm "(chuỗi bài tự trả lời của tác giả: FRONT-02x)"; thêm dòng FRONT-02x sau dòng FRONT-02w (✅ ngày, đường dẫn báo cáo, số liệu thật, nói thẳng phần chưa làm). `doc/task/todo/FRONT-02_decentralized-web3-social.md`: mục "Cập nhật" ghi chuỗi tự trả lời đã làm; còn ảnh và trả lời người khác, đăng nhập, đăng bài (giữ task gốc trong `todo/`).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02x): report that a Nostr source keeps the author's own reply threads

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -5; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** đọc NIP-10 (`mention` không phải trả lời; `reply` > `root` > thẻ không dấu cuối; nhiều `reply` lấy cuối; id hỏng thì vẫn là trả lời nhưng không có đích) → Task 1 (E1 đến E11); luật giữ bài (không phải trả lời, hoặc đích được giữ; chặn vòng lặp; không phụ thuộc thứ tự; mỗi mảnh một bài; tiêu đề, link, ngày) → Task 2 (M1 đến M12); không đổi `toArticles` chữ ký, `fetch`, `LocalRssSv`, `SubscribeViewModel` → Global Constraints và Task 5 Step 2; đường đồng bộ → Task 3 (L1); đột biến x1 đến x9 của spec cộng `x10`, `x11` (đột biến tương đương) → Task 4; JVM đo lại, báo cáo, `feature.md`, task gốc → Task 5; giới hạn 20 của `fetch` nói thẳng → Global Constraints và Task 5 Step 3.
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ (test, mã production, script, `x_check.py`). Hai chỗ có ý thức: Task 3 không có bước đỏ (đã ghi lý do) và `x11` là đột biến tương đương (đã giải thích).
3. **Nhất quán tên:** `isReply`, `repliedToId`, `answeredTags`, `marker`, `MARKER_REPLY/ROOT/MENTION`, `ETAG_ID_INDEX`, `ETAG_MARKER_INDEX`, `keepOriginalsAndOwnThreads`, `isKept` khớp giữa Task 1, 2 và mẫu đột biến; tên test E1 đến E11, M1 đến M12, L1 khớp bảng và `x_check.py`. Mô hình Python của luật (cả bản có bộ nhớ đệm giống mã Kotlin) khớp bộ kiểm chứng điểm bất động trên hơn 40.000 hoán vị.
4. **Rủi ro còn lại (nói thẳng):** (a) bảng tập test đỏ là **dự đoán** (đã nêu cách xử lý khi lệch); (b) số test mong đợi 3.846 chỉ để đối chiếu; (c) bước đỏ của Task 2 có vài test xanh sẵn (đã liệt kê, chúng là lưới an toàn cho đột biến); (d) bộ nhớ đệm của `isKept` là chỗ tinh vi nhất (đã khóa bằng M8 và mô hình Python); (e) giới hạn 20 của `fetch` làm tính năng kém hữu ích với tác giả hay trả lời người khác; (f) không kiểm trên relay hay máy thật theo thiết kế.
