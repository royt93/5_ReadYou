# FRONT-02y — Nostr: cắt 20 bài sau khi lọc, không phải trước (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** `NostrRelayClient.fetch` trả **mọi** sự kiện hợp lệ đã đọc (tối đa 50) thay vì cắt còn 20 trước khi bộ lọc của FRONT-02x chạy; `NostrFeedMapper.toArticles` vẫn chỉ hiện tối đa 20 bài **sau** khi lọc.

**Architecture:** Bỏ `.take(MAX_NOTES_KEPT)` ở cuối `readRelay`. Hằng `NostrRelayClient.MAX_NOTES_KEPT` (20) đổi tên thành `NostrFeedMapper.MAX_ARTICLES_SHOWN` (vẫn 20) và chỉ còn dùng ở mapper. Không đổi REQ, không đổi mạng, không đổi `NostrFetchResult`, `LocalRssSv`, `SubscribeViewModel`. **Chỉ JVM, không chạm thiết bị nào.**

**Tech Stack:** Kotlin, JUnit4 + Robolectric + MockWebServer3 (đã dùng cho `NostrRelayClientTest`), MockK, Gradle `testDevDebugUnitTest --tests`, Python 3.9 (đột biến và kiểm chéo).

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không chạm thiết bị nào** (không Tecno, không Pixel, không `adb`).
- Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; **không đổi mạng**: REQ vẫn `limit=50` (test REQ chính xác ở `NostrRelayClientTest` **không đổi**), vẫn đọc tối đa `MAX_EVENTS_READ = 50` khung, 8 giây, 64 KiB mỗi khung.
- Mã production đổi **ba tệp**: `NostrRelayClient.kt` (bỏ cắt, bỏ hằng), `NostrFeedMapper.kt` (thêm hằng, dùng hằng), `NostrFetchResult.kt` (chỉ KDoc). **Không đổi** `LocalRssSv`, `SubscribeViewModel`, `NostrEvent`.
- `fetch` vẫn **dừng ở relay đầu có bài** (`result.notes.isNotEmpty()`), không thử relay hai (owner chọn giữ).
- Hằng mới `NostrFeedMapper.MAX_ARTICLES_SHOWN = 20` (công khai, có chú thích); mọi test tham chiếu `MAX_NOTES_KEPT` đổi theo.
- R5: không magic number, test kèm. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, output ra file, chờ `.exit`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Chạy lớp test riêng (`--tests`) cho từng bước; full suite `--rerun-tasks` ở cuối, **đo lại số test** (số trước là **3.848** theo FRONT-02x; không dùng làm bằng chứng). Đỏ chập chờn: chạy riêng lớp đó 3 lần + chạy lại cả bộ, ghi thẳng cả hai kết quả.
- Mutation: một script nền, không sửa tệp đột biến cho tới `ALLDONE`; khôi phục + `cmp`; mỗi đột biến chạy các lớp Nostr và sync; so sánh tự động tập test đỏ; **bảng mong đợi là dự đoán**, lệch thì đọc lý do (không bỏ qua); mẫu phải biên dịch được.
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- Nói thẳng phần không kiểm: relay thật, máy thật; chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt; relay đầu chỉ có trả lời người khác thì không thử relay hai.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-19)

1. **Chỉ có 38 sự kiện kind 1 ký thật của `AUTHOR`** trong `NostrTestEvents` (8 hằng lẻ + 30 trong `MANY`), nên **không thể** chứng minh "`fetch` trả tối đa 50" bằng sự kiện hợp lệ (38 < 50). Spec ghi test "55 sự kiện hợp lệ" nên **không làm được**. Thay bằng cách chứng minh giới hạn đọc 50 **bằng số khung**: `readRelay` đếm **mọi** khung `EVENT` đúng subscription (kể cả khung rác), nên 12 khung rác + 38 hợp lệ = 50 khung thì đọc hết 38; 13 khung rác + 38 hợp lệ = 51 khung thì **khung hợp lệ cuối không được đọc** (37 trả về). Hai test này khóa "không cắt ở 20, cắt ở 50 khung".
2. **Test `fetch_stopsReadingAfterFiftyFrames` và `fetch_countsAnOversizedFrameAmongTheFiftyItMayRead` đã có** và dùng `MAX_EVENTS_READ`; không đổi, chỉ chạy để chắc vẫn xanh.
3. **Hằng `MAX_NOTES_KEPT` có 4 chỗ tham chiếu** (đã grep): `NostrFeedMapper.kt:41`, `NostrFeedMapperTest.kt:92`, `NostrFeedMapperThreadTest.kt:109`, `NostrRelayClientTest.kt:116`, cộng khai báo ở `NostrRelayClient.kt:121` và dùng ở `NostrRelayClient.kt:111`. `androidTest` không dùng (đã grep).
4. **Hai test relay khác kiểm kích thước** (`NostrRelayClientLeakTest.kt:70` `assertEquals(1, notes.size)`, `NostrRelayProfileTest.kt:48` `assertEquals(1, result.notes.size)`) chỉ có 1 sự kiện, không liên quan giới hạn.
5. **Bước đỏ của Task 1:** đổi test `fetch_keepsTheTwentyNewest_newestFirst` thành "giữ hết 30" **trước khi sửa mã**: đỏ vì `fetch` còn cắt còn 20 (khẳng định thật). Hằng mới chưa tồn tại thì mapper và hai test mapper vẫn dùng hằng cũ ở Task 1; **Task 2** mới đổi tên (bước đỏ là lỗi biên dịch khi test dùng `NostrFeedMapper.MAX_ARTICLES_SHOWN` chưa có).
6. **Ca đồng bộ của spec** (`LocalRssSvNostrSyncTest`) dùng MockK trả `NostrFetchResult` nên **không đi qua `readRelay`**; nó chỉ khóa hành vi của mapper. **Bỏ ca đó** (trùng với test mapper mới) và để bằng chứng tầng relay ở `NostrRelayClientTest`. Sự kiện hình thức (`sig` 0) **không dùng** cho test relay vì `fetch` gọi `isValid` (cần chữ ký thật); chỉ dùng sự kiện hình thức ở tầng mapper.
7. **Test chứng minh "chuỗi hiện lại" ở tầng relay + mapper:** dựng danh sách từ các sự kiện **ký thật**: 25 trả lời người khác (`REPLY` chỉ có 1; thay bằng cách dùng `MANY`) không thể. Cách đơn giản đúng: ở tầng **relay**, chứng minh `fetch` giữ đủ 30 + 8 (không cắt); ở tầng **mapper**, dựng 40 sự kiện hình thức (25 trả lời người khác mới hơn + chuỗi 3 mảnh cũ hơn) và chứng minh chuỗi hiện. Hai tầng ghép lại là bằng chứng cho cả đường.
8. **Đột biến `y1`** (thêm lại `.take(20)` ở relay) làm đỏ test "giữ hết 30"; nhưng `take(20)` dùng hằng đã bị xóa ở Task 2, nên mẫu `y1` viết bằng số `20` (có tên hằng cục bộ trong mẫu đột biến, không sửa mã sản phẩm).

## Tập test đỏ mong đợi của đột biến (DỰ ĐOÁN; kiểm tự động ở Task 3)

Tên viết tắt: **R1** `fetch_keepsEveryValidEventItRead_newestFirst`, **R2** `fetch_readsFiftyFramesAndKeepsEveryValidOne`, **R3** `fetch_doesNotReadTheFiftyFirstFrame`, **R4** (sẵn có) `fetch_stopsReadingAfterFiftyFrames`, **R5** (sẵn có) `fetch_countsAnOversizedFrameAmongTheFiftyItMayRead`.
**F1** (sẵn có) `NostrFeedMapperTest.atMostTwentyArticles_areKept_newestFirst`, **F2** (sẵn có) `NostrFeedMapperThreadTest.atMostTwentyArticles_stillHolds_withThreads`, **F3** mới `NostrFeedMapperThreadTest.aThreadBehindManyAnswersToOthers_isStillShown` (xanh cả trước và sau, lưới an toàn cho y7), **F4** mới `NostrFeedMapperTest.theArticleLimit_isTwentyArticles`.

| Đột biến | Đổi gì | Test đỏ mong đợi | Kiểu so |
|---|---|---|---|
| base | không | không | đúng |
| y1 | `readRelay` cắt lại còn 20 | R1, R2, R3 | đúng |
| y2 | `readRelay` cắt còn 30 (không dùng 49: chỉ có 38 sự kiện hợp lệ nên cắt 49 không đổi gì, là đột biến tương đương) | R2, R3 | đúng |
| y3 | bỏ `sortedByDescending` ở `readRelay` (sự kiện còn theo thứ tự đến, cũ nhất trước) | R1 | đúng |
| y4 | mapper bỏ `take(MAX_ARTICLES_SHOWN)` | F1, F2 | đúng |
| y5 | `MAX_ARTICLES_SHOWN` thành 21 (F1 và F2 so với **chính hằng** nên vẫn xanh: 21 = 21; chỉ F4 khóa cứng 20 bắt được) | F4 | đúng |
| y6 | mapper `take(10)` | F1, F2 | đúng |
| y7 | mapper cắt 20 **trước** khi lọc (đúng lỗi gốc, đặt ở mapper): F2 đỏ vì 20 mới nhất của chuỗi 26 mảnh mất đầu chuỗi nên cả chuỗi rụng; F3 đỏ vì 20 bài trả lời người khác chiếm hết | F2, F3 | đúng |

R4 và R5 (sẵn có) không bị đột biến nào phá nên là **lưới an toàn** (khóa giới hạn đọc 50 khung). F3 xanh cả trước lẫn sau khi sửa (mapper đã lọc rồi mới cắt từ FRONT-02x), bằng chứng nó bắt lỗi là đột biến y7. Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng hoặc test và ghi trong báo cáo.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt` | bỏ `.take(MAX_NOTES_KEPT)` và hằng |
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` | thêm `MAX_ARTICLES_SHOWN`, dùng hằng |
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFetchResult.kt` | KDoc hợp đồng mới |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt` | R1, R2, R3 |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt` | hằng mới, F4 |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt` | hằng mới, F3 |
| Tạm, **không commit**: `/tmp/y_mut.sh`, `/tmp/y_batch.sh`, `/tmp/y_check.py` | đột biến JVM |
| Modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02y_nostr-keep-events-until-filtered_DONE.md` | báo cáo |

---

### Task 1: `fetch` giữ mọi sự kiện hợp lệ đã đọc, không cắt còn 20 (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt`

**Interfaces:**
- Consumes: `NostrRelayClient(client, ioDispatcher, relays, requireSecure, timeoutMs)`, `fetch(authorHex): NostrFetchResult`, `NostrRelayClient.MAX_EVENTS_READ = 50`, helper của test `relay(vararg events)`, `client(vararg urls)`, `ids(notes)`.
- Produces: `fetch(...).notes` là mọi sự kiện hợp lệ đã đọc (tối đa số khung đọc), mới nhất trước, **không cắt còn 20**.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
N=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt
T=app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
grep -n "take(MAX_NOTES_KEPT)\|const val MAX_NOTES_KEPT\|const val MAX_EVENTS_READ" $N
grep -n "fetch_keepsTheTwentyNewest_newestFirst\|MAX_NOTES_KEPT" $T
git status --short app
```
Expected: relay: `take(MAX_NOTES_KEPT)` (dòng ~111), khai báo hai hằng; test: tên test cũ và `MAX_NOTES_KEPT` (dòng ~116); `git status` sạch.

- [ ] **Step 2: Viết test đỏ (thay test cũ bằng R1, thêm R2 và R3)**

Cách chứng minh giới hạn **đọc 50 khung** (không phải 50 sự kiện hợp lệ: chỉ có 38 sự kiện ký thật): `readRelay` đếm **mọi** khung `EVENT` của subscription (kể cả khung rác); 12 rác + 38 hợp lệ là đúng 50 khung nên đọc hết 38; 13 rác + 38 hợp lệ là 51 khung nên khung hợp lệ cuối **không** được đọc (37). Mô hình Python của việc đếm khung cho đúng các số này.

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt"
s = open(T, encoding="utf-8").read()
old = '''    @Test fun fetch_keepsTheTwentyNewest_newestFirst() = runBlocking {
        server.enqueue(relay(*NostrTestEvents.MANY.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, notes.size)
        assertEquals(notes.sortedByDescending { it.createdAt }.map { it.id }, ids(notes))
        assertEquals(1_700_001_029L, notes.first().createdAt)
        assertEquals(1_700_001_010L, notes.last().createdAt)
    }
'''
assert s.count(old) == 1
new = '''    // FRONT-02y: fetch hands over EVERY valid event it read; the feed is cut to twenty later, after the replies are filtered out.
    @Test fun fetch_keepsEveryValidEventItRead_newestFirst() = runBlocking {
        server.enqueue(relay(*NostrTestEvents.MANY.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(NostrTestEvents.MANY.size, notes.size)
        assertEquals(notes.sortedByDescending { it.createdAt }.map { it.id }, ids(notes))
        assertEquals(1_700_001_029L, notes.first().createdAt)
        assertEquals(1_700_001_000L, notes.last().createdAt)
    }

    /** Every valid note of the author that the fixtures hold: 8 single notes and the 30 of MANY (38, fewer than the 50 frames read). */
    private val everyValidNote: List<String> = listOf(
        NostrTestEvents.PLAIN, NostrTestEvents.RICH, NostrTestEvents.REPLY, NostrTestEvents.OLDER,
        NostrTestEvents.EMPTY, NostrTestEvents.FUTURE, NostrTestEvents.LONG, NostrTestEvents.QUOTES,
    ) + NostrTestEvents.MANY

    private fun junkFrames(count: Int): Array<String> = Array(count) { "{\\"n\\":$it}" }

    // The limit is on frames read (junk counts), not on notes kept: with exactly 50 frames every valid note is still read.
    @Test fun fetch_readsFiftyFramesAndKeepsEveryValidOne() = runBlocking {
        server.enqueue(relay(*junkFrames(NostrRelayClient.MAX_EVENTS_READ - everyValidNote.size), *everyValidNote.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(everyValidNote.size, notes.size)
    }

    // One frame over the limit: the 51st frame (here the last valid note) is never read.
    @Test fun fetch_doesNotReadTheFiftyFirstFrame() = runBlocking {
        server.enqueue(relay(*junkFrames(NostrRelayClient.MAX_EVENTS_READ - everyValidNote.size + 1), *everyValidNote.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        val lastSentId = requireNotNull(NostrEvent.parse(everyValidNote.last())).id
        assertEquals(everyValidNote.size - 1, notes.size)
        assertFalse("the last frame was the 51st and must not be read", ids(notes).contains(lastSentId))
    }
'''
open(T, "w", encoding="utf-8").write(s.replace(old, new, 1))
EOF
grep -n "fetch_keepsEveryValidEventItRead_newestFirst\|fetch_readsFiftyFramesAndKeepsEveryValidOne\|fetch_doesNotReadTheFiftyFirstFrame" app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
```
Expected: 3 dòng. (`assertFalse` đã được import ở đầu tệp.)

- [ ] **Step 3: Chạy test, phải ĐỎ đúng ba test mới (hiện `fetch` còn cắt còn 20)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/y-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClientTest" > /tmp/y-t1.log 2>&1; echo $? > /tmp/y-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/y-t1.exit && break; sleep 5; done; cat /tmp/y-t1.exit; grep -E "FAILED|^e: " /tmp/y-t1.log | cut -c1-170 | head -8
```
Expected: `1`; đỏ **đúng** `fetch_keepsEveryValidEventItRead_newestFirst`, `fetch_readsFiftyFramesAndKeepsEveryValidOne`, `fetch_doesNotReadTheFiftyFirstFrame`. Có test khác đỏ hoặc lỗi biên dịch thì đọc lý do trước khi làm tiếp.

- [ ] **Step 4: Sửa `NostrRelayClient.kt` (một mẫu, đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
N = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt"
s = open(N, encoding="utf-8").read()
old = "            notes = found.values.sortedByDescending { it.createdAt }.take(MAX_NOTES_KEPT),\n"
assert s.count(old) == 1
new = "            notes = found.values.sortedByDescending { it.createdAt },\n"
open(N, "w", encoding="utf-8").write(s.replace(old, new, 1))
EOF
git diff --stat -- app/src/main | tail -1
```
Expected: 1 tệp, 1 dòng thêm, 1 dòng bỏ. (Hằng `MAX_NOTES_KEPT` còn nguyên ở bước này: mapper vẫn dùng nó; Task 2 sẽ đổi tên.)

- [ ] **Step 5: Chạy test, phải XANH (relay, hai lớp relay khác, mapper cũ, đồng bộ)**

```bash
rm -f /tmp/y-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClientTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClientLeakTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayProfileTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayNetworkTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperThreadTest" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" > /tmp/y-t1.log 2>&1; echo $? > /tmp/y-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/y-t1.exit && break; sleep 5; done; cat /tmp/y-t1.exit; grep -E "FAILED|tests completed|^e: " /tmp/y-t1.log | head -6 | cut -c1-200; tail -2 /tmp/y-t1.log
```
Expected: `0`, `BUILD SUCCESSFUL`. (`NostrRelayNetworkTest` dùng MockWebServer cục bộ, đã đọc: không gọi mạng thật.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
git commit -m "fix(front-02y): fetch keeps every valid event it read, the feed is cut to twenty after the filter

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Đổi tên hằng thành `NostrFeedMapper.MAX_ARTICLES_SHOWN`, KDoc hợp đồng mới, hai test mapper (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt` (bỏ hằng)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFetchResult.kt` (chỉ KDoc)

**Interfaces:**
- Consumes: `NostrFeedMapper.toArticles(events, feedId, accountId, nowMillis, newId)`; `NostrRelayClient.MAX_NOTES_KEPT` (sẽ bị xóa).
- Produces: `const val NostrFeedMapper.MAX_ARTICLES_SHOWN = 20`; `toArticles` cắt bằng hằng này sau khi lọc.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
T=app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr
M=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr
grep -n "MAX_NOTES_KEPT" $M/*.kt $T/*.kt
grep -n "const val NJUMP_BASE" $M/NostrFeedMapper.kt
git status --short app
```
Expected: `MAX_NOTES_KEPT` ở `NostrFeedMapper.kt` (1), `NostrRelayClient.kt` (khai báo; dòng `take` đã bỏ ở Task 1), `NostrFeedMapperTest.kt` (1), `NostrFeedMapperThreadTest.kt` (1); `const val NJUMP_BASE` (1 dòng); `git status` sạch.

- [ ] **Step 2: Viết test đỏ: dùng hằng mới trong hai test cũ, thêm F4 và F3**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
D = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/"
# --- NostrFeedMapperTest: new constant in the old test, plus F4
f = D + "NostrFeedMapperTest.kt"; s = open(f, encoding="utf-8").read()
old = "        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, a.size)\n"
assert s.count(old) == 1
s = s.replace(old, "        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, a.size)\n", 1)
anchor = "    @Test fun feedNameAndUrl_useTheNpub() {"
assert s.count(anchor) == 1
s = s.replace(anchor, '''    // FRONT-02y: the number of articles shown is a property of the mapper (it cuts after the filter), and it is twenty.
    @Test fun theArticleLimit_isTwentyArticles() = assertEquals(20, NostrFeedMapper.MAX_ARTICLES_SHOWN)

''' + anchor, 1)
open(f, "w", encoding="utf-8").write(s)

# --- NostrFeedMapperThreadTest: new constant in the old test, plus F3
f = D + "NostrFeedMapperThreadTest.kt"; s = open(f, encoding="utf-8").read()
old = "        assertEquals(NostrRelayClient.MAX_NOTES_KEPT, articles.size)\n"
assert s.count(old) == 1
s = s.replace(old, "        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)\n", 1)
anchor = "    @Test fun aReplyWhoseTargetIdIsBroken_isDropped() {"
assert s.count(anchor) == 1
s = s.replace(anchor, '''    // FRONT-02y: twenty-five NEWER answers to people we do not follow must not push an older own thread out: the cut comes after the filter.
    @Test fun aThreadBehindManyAnswersToOthers_isStillShown() {
        val strangers = (0 until 25).map { n -> reply(200 + n, 1_700_005_000L + n, "answer $n", id(900 + n)) }
        assertEquals(listOf("Part three", "Part two", "Part one"), titles(*(strangers + listOf(a, b, c)).toTypedArray()))
    }

''' + anchor, 1)
open(f, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/test | tail -1
```
Expected: 2 tệp. (Lưu ý: ở bước này `NostrFeedMapper.MAX_ARTICLES_SHOWN` **chưa tồn tại**.)

- [ ] **Step 3: Chạy test, phải ĐỎ (lỗi biên dịch: chưa có `MAX_ARTICLES_SHOWN`)**

```bash
rm -f /tmp/y-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperThreadTest" > /tmp/y-t2.log 2>&1; echo $? > /tmp/y-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/y-t2.exit && break; sleep 5; done; cat /tmp/y-t2.exit; grep -E "^e: " /tmp/y-t2.log | sed -E 's/.*(NostrFeedMapper[A-Za-z]*Test.kt:[0-9]+:[0-9]+ )/\1/' | cut -c1-120 | sort -u | head -4
```
Expected: `1` và `Unresolved reference 'MAX_ARTICLES_SHOWN'`.

- [ ] **Step 4: Sửa mã production (ba tệp; mỗi mẫu đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
M = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/"
def edit(name, pairs):
    f = M + name; s = open(f, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, (name, old[:60])
        s = s.replace(old, new, 1)
    open(f, "w", encoding="utf-8").write(s)

edit("NostrFeedMapper.kt", [
    ('    const val NJUMP_BASE = "https://njump.me/"\n',
     '    const val NJUMP_BASE = "https://njump.me/"\n\n    /** The most articles one Nostr source shows. The cut comes AFTER replies to other people are filtered out, never before. */\n    const val MAX_ARTICLES_SHOWN = 20\n\n'),
    ("            .take(NostrRelayClient.MAX_NOTES_KEPT)\n", "            .take(MAX_ARTICLES_SHOWN)\n"),
])
edit("NostrRelayClient.kt", [("        const val MAX_NOTES_KEPT = 20\n", "")])
edit("NostrFetchResult.kt", [(
    "/** What one fetch found: the author's notes and, when a valid public profile has a usable name, that name. */",
    "/**\n * What one fetch found: every valid note it read from the relay, newest first (at most as many as frames read, [NostrRelayClient.MAX_EVENTS_READ]),\n * and, when a valid public profile has a usable name, that name. The list is NOT cut to the feed size: replies to other people are\n * still in it, and the mapper cuts to [NostrFeedMapper.MAX_ARTICLES_SHOWN] only after filtering them out.\n */")])
EOF
git diff --stat -- app/src/main | tail -4
grep -rn "MAX_NOTES_KEPT" app/src | head -3
```
Expected: 3 tệp production; `grep` không còn kết quả.

- [ ] **Step 5: Chạy test, phải XANH (mọi lớp Nostr liên quan và đồng bộ)**

```bash
rm -f /tmp/y-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/y-t2.log 2>&1; echo $? > /tmp/y-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/y-t2.exit && break; sleep 5; done; cat /tmp/y-t2.exit; grep -E "FAILED|tests completed|^e: " /tmp/y-t2.log | head -6 | cut -c1-200; tail -2 /tmp/y-t2.log
```
Expected: `0`, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFetchResult.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt
git commit -m "refactor(front-02y): the article limit belongs to the mapper, MAX_ARTICLES_SHOWN, and a thread behind many answers to others is shown

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Mutation JVM (7 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/y_mut.sh`, `/tmp/y_batch.sh`, `/tmp/y_check.py`. Đột biến tạm trong `NostrRelayClient.kt` và `NostrFeedMapper.kt` (luôn khôi phục).

**Interfaces:** Consumes: mã sau Task 1 và 2. Produces: bảng đột biến với tập test đỏ **đo được**, so với bảng dự đoán ở đầu plan.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất (mỗi mẫu đúng 1 lần), trên mã sau Task 2**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/"
rc = open(J + "NostrRelayClient.kt", encoding="utf-8").read()
mp = open(J + "NostrFeedMapper.kt", encoding="utf-8").read()
pats = [
 ("y1/y2/y3", rc, "            notes = found.values.sortedByDescending { it.createdAt },\n"),
 ("y4/y6", mp, "            .take(MAX_ARTICLES_SHOWN)\n"),
 ("y5", mp, "    const val MAX_ARTICLES_SHOWN = 20\n"),
 ("y7", mp, "        keepOriginalsAndOwnThreads(events).asSequence()\n"),
]
print({l: s.count(p) for l, s, p in pats})
EOF
```
Expected: mọi nhãn `1`. Khác 1: đọc lại tệp và sửa mẫu ở `y_batch.sh` (không chạy batch).

- [ ] **Step 2: Dựng `/tmp/y_mut.sh`, `/tmp/y_batch.sh`, `/tmp/y_check.py`**

```bash
cat > /tmp/y_mut.sh <<'EOF'
#!/bin/zsh
# usage: y_mut.sh <label> <file> <old> <new>   ("base" = no change). JVM only, never touches a device.
label="$1"; FILE="${2-}"; old="${3-}"; new="${4-}"
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
BACKUP=/tmp/y-mutbackup.bin
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
  > "/tmp/y-mut-$label.log" 2>&1
rc=$?
[ "$label" != base ] && { restore; trap - EXIT INT TERM; }
failing=$(grep -E "^com\.mckimquyen.* > .* FAILED" "/tmp/y-mut-$label.log" | sed -E 's/^.* > ([A-Za-z0-9_`. ,()-]+) FAILED.*/\1/' | sort -u | tr '\n' ',')
compile=$(grep -c "^e: " "/tmp/y-mut-$label.log")
echo "$label: gradle_exit=$rc compile_errors=$compile failing=[$failing] source=$rest"
EOF
chmod +x /tmp/y_mut.sh
cat > /tmp/y_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr
RC=$J/NostrRelayClient.kt; MP=$J/NostrFeedMapper.kt
cp -p $RC /tmp/y-orig-rc.kt; cp -p $MP /tmp/y-orig-mp.kt
M=/tmp/y_mut.sh
zsh $M base
zsh $M y1 $RC '            notes = found.values.sortedByDescending { it.createdAt },
' '            notes = found.values.sortedByDescending { it.createdAt }.take(20),
'
zsh $M y2 $RC '            notes = found.values.sortedByDescending { it.createdAt },
' '            notes = found.values.sortedByDescending { it.createdAt }.take(30),
'
zsh $M y3 $RC '            notes = found.values.sortedByDescending { it.createdAt },
' '            notes = found.values.toList(),
'
zsh $M y4 $MP '            .take(MAX_ARTICLES_SHOWN)
' ''
zsh $M y5 $MP '    const val MAX_ARTICLES_SHOWN = 20
' '    const val MAX_ARTICLES_SHOWN = 21
'
zsh $M y6 $MP '            .take(MAX_ARTICLES_SHOWN)
' '            .take(10)
'
zsh $M y7 $MP '        keepOriginalsAndOwnThreads(events).asSequence()
' '        keepOriginalsAndOwnThreads(events.sortedByDescending { it.createdAt }.take(MAX_ARTICLES_SHOWN)).asSequence()
'
zsh $M base
cmp -s /tmp/y-orig-rc.kt $RC && echo "final rc: identical" || echo "final rc: DIFFERENT"
cmp -s /tmp/y-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "git diff vs HEAD (main): $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
cat > /tmp/y_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
R1 = "fetch_keepsEveryValidEventItRead_newestFirst"; R2 = "fetch_readsFiftyFramesAndKeepsEveryValidOne"; R3 = "fetch_doesNotReadTheFiftyFirstFrame"
F1 = "atMostTwentyArticles_areKept_newestFirst"; F2 = "atMostTwentyArticles_stillHolds_withThreads"
F3 = "aThreadBehindManyAnswersToOthers_isStillShown"; F4 = "theArticleLimit_isTwentyArticles"
EXPECTED = {
 "y1": {R1, R2, R3}, "y2": {R2, R3}, "y3": {R1}, "y4": {F1, F2}, "y5": {F4}, "y6": {F1, F2}, "y7": {F2, F3},
}
bad = 0
for label, want in EXPECTED.items():
    try:
        text = open(f"/tmp/y-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError:
        print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M):
        print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = got == want
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/y-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN")
bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```
**Ghi chú:** `y1` dùng số `20` trực tiếp vì hằng cũ đã bị xóa (đột biến chỉ trên bản build thử). Đột biến lỗi biên dịch thì đổi mẫu cho biên dịch được trước khi chạy batch.

- [ ] **Step 3: Chạy batch nền, chờ ALLDONE**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/y-batch.log; ( zsh /tmp/y_batch.sh > /tmp/y-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/y-batch.log 2>/dev/null && break; sleep 5; done; cut -c1-200 /tmp/y-batch.log
```
Không sửa hai tệp production cho tới `ALLDONE`. Mỗi đột biến mất 1 đến 2 phút; chưa xong sau 115×5 giây thì chạy lại **chỉ** vòng chờ (nhiều lần nếu cần).

- [ ] **Step 4: Đối chiếu tự động và xử lý lệch**

```bash
python3 /tmp/y_check.py
```
Expected: `ALL-MATCH`. Đột biến lệch: **đọc lý do** (tập đỏ thực tế và thông báo trong `/tmp/y-mut-<nhãn>.log`) rồi chọn: (a) dự đoán sai, sửa bảng ở đầu plan và `y_check.py`, ghi trong báo cáo; (b) test yếu (mọi test vẫn xanh): thêm khẳng định (quay lại Task 1 hoặc 2) rồi chạy lại batch. Kiểm cuối batch: hai dòng `final … identical` và `git diff vs HEAD (main): 0 lines`.

---

### Task 4: JVM đầy đủ, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng FRONT-02x và dòng 02y mới), `doc/task/todo/FRONT-02_decentralized-web3-social.md` (mục "Cập nhật"), `docs/superpowers/specs/2026-11-19-front-02y-nostr-keep-events-until-filtered-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02y_nostr-keep-events-until-filtered_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/y-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/y-jvm.log 2>&1; echo $? > /tmp/y-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/y-jvm.exit && break; sleep 5; done; cat /tmp/y-jvm.exit; grep -E "FAILED|tests completed" /tmp/y-jvm.log | head -5; tail -3 /tmp/y-jvm.log
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
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi. Số test = số trước (**3.848**, chỉ để đối chiếu) cộng test thêm: Task 1 thêm 2 (R2, R3; R1 thay test cũ), Task 2 thêm 2 (F3, F4): **kỳ vọng 3.852**; báo cáo ghi số **đo được**. Có test đỏ **không liên quan**: chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả.

- [ ] **Step 2: Kiểm phạm vi diff (đúng ba tệp production)**

```bash
git status --short; git diff --stat HEAD~2 HEAD -- app/src/main app/build.gradle app/schemas
git diff --name-only HEAD~2 HEAD
```
Expected: `app/src/main` chỉ có `NostrRelayClient.kt`, `NostrFeedMapper.kt`, `NostrFetchResult.kt`; không `app/build.gradle`, `app/schemas`, manifest, `res`, `LocalRssSv.kt`, `SubscribeViewModel.kt`, `NostrEvent.kt`; test: `NostrRelayClientTest.kt`, `NostrFeedMapperTest.kt`, `NostrFeedMapperThreadTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md`, task gốc và spec**

Báo cáo `doc/task/done/FRONT-02y_nostr-keep-events-until-filtered_DONE.md` (tiếng Việt đơn giản) gồm: điều đã làm (ba tệp production); nguyên nhân gốc (cắt 20 trước lọc); cách chứng minh giới hạn đọc 50 khung (12 và 13 khung rác, vì chỉ có 38 sự kiện ký thật; spec ghi "55 sự kiện" không làm được); bảng 7 đột biến với tập test đỏ **đo được** (kèm kết quả `y_check.py`, ghi mọi dự đoán sai đã sửa, đặc biệt y5); JVM số **đo lại**; **giới hạn nói thẳng**: chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt; relay đầu chỉ có trả lời người khác thì không thử relay hai; nguồn đã đăng ký có thể thấy thêm bài chuỗi cũ hơn ở lần đồng bộ sau; chưa kiểm relay thật hay máy thật.

`doc/feature.md`: dòng FRONT-02x ghi "giới hạn 20 của `fetch` đã khắc phục ở FRONT-02y"; thêm dòng FRONT-02y sau dòng FRONT-02x. `doc/task/todo/FRONT-02_decentralized-web3-social.md`: mục "Cập nhật" ghi FRONT-02y (giữ task gốc trong `todo/`).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02y): report that a Nostr feed is now cut to twenty after the filter

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -4; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** bỏ cắt ở relay, giữ ≤ 50 → Task 1 (R1, R2, R3, và R4/R5 sẵn có); đổi tên hằng thành `MAX_ARTICLES_SHOWN` ở mapper, KDoc hợp đồng mới → Task 2; chuỗi sau 25 trả lời người khác vẫn hiện → F3 (Task 2); REQ không đổi → Global Constraints (test REQ chính xác không đổi); `fetch` vẫn dừng ở relay đầu → Global Constraints; đột biến y1 đến y6 của spec cộng `y7` (lỗi gốc đặt ở mapper) → Task 3; JVM đo lại, báo cáo, `feature.md` → Task 4. **Hai chỗ lệch spec đã nêu ở mục "Điều chỉnh":** không có test "55 sự kiện hợp lệ" (chỉ có 38) và bỏ ca đồng bộ MockK (trùng test mapper).
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ. Ngoại lệ có ý thức: `y2` dùng cắt 30 thay cho 49 (49 là đột biến tương đương vì chỉ có 38 sự kiện hợp lệ), đã giải thích.
3. **Nhất quán tên:** `MAX_ARTICLES_SHOWN`, `MAX_EVENTS_READ`, `everyValidNote`, `junkFrames`, R1 đến R3, F1 đến F4 khớp giữa Task 1, 2, bảng và `y_check.py`. Mô hình Python của đếm khung (12 và 13 khung rác) và của kịch bản F3 (cắt trước mất chuỗi, cắt sau giữ 3 mảnh) khớp các số trong test.
4. **Rủi ro còn lại (nói thẳng):** (a) bảng tập test đỏ là **dự đoán** (đã sửa y5 sau khi nhận ra F1 và F2 so với chính hằng); (b) F3 xanh cả trước và sau sửa (mapper đã đúng từ FRONT-02x), chỉ đột biến y7 chứng minh nó bắt lỗi; (c) test R2/R3 phụ thuộc cách `readRelay` đếm khung (khung rác cũng tính), được khóa bởi test R4/R5 sẵn có; (d) số test mong đợi 3.852 chỉ để đối chiếu; (e) không kiểm relay thật hay máy thật, theo thiết kế.
