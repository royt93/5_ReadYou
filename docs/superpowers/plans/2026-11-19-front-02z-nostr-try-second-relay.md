# FRONT-02z — Nostr: thử relay hai khi relay đầu không có bài nào hiện được (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** `NostrRelayClient.fetch` chỉ dừng ở relay đầu có **ít nhất một bài hiện được** (không phải "có sự kiện nào"), nên relay đầu chỉ có trả lời người khác hoặc bài trống không còn chặn relay hai.

**Architecture:** `NostrFeedMapper.visibleNotes(events)` (công khai, thuần) là **một chỗ duy nhất** cho luật "bài nào hiện": qua bộ lọc chuỗi tự trả lời, có `noteId` hợp lệ, tiêu đề không rỗng. `toArticles` dùng nó **trước** khi sắp xếp và cắt 20. `fetch` gọi nó để quyết định dừng, và vẫn trả `result` nguyên (không gộp hai relay). Cả hai relay đều không có bài hiện được thì trả rỗng kèm tên hồ sơ đầu tiên. **Chỉ JVM, không chạm thiết bị nào.**

**Tech Stack:** Kotlin, JUnit4 + Robolectric (cả bốn lớp test relay và hai lớp mapper đã chạy Robolectric, cần cho `android.text.Html` trong `titleFromBody`) + MockWebServer3, Gradle `testDevDebugUnitTest --tests`, Python 3.9 (đột biến, mô hình dự đoán).

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không chạm thiết bị nào** (không Tecno, không Pixel, không `adb`).
- Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; **không đổi REQ** gửi relay; **không đổi chữ ký** `fetch` và `NostrFetchResult`.
- Mã production đổi **hai tệp**: `NostrFeedMapper.kt` (thêm `visibleNotes`, `toArticles` dùng nó) và `NostrRelayClient.kt` (điều kiện dừng). **Không đổi** `LocalRssSv`, `SubscribeViewModel`, `NostrEvent`, `NostrFetchResult`.
- `fetch` vẫn tối đa 2 relay, mỗi relay tối đa 8 giây; **không gộp** sự kiện hai relay; trả `result` nguyên (chưa lọc) của relay đầu tiên có bài hiện được.
- Cả hai relay có sự kiện nhưng không bài hiện được thì `fetch` trả `NostrFetchResult(emptyList(), firstProfile)`; xem trước báo `web_feed_fetch_failed`, đồng bộ ném `IOException` như hiện nay.
- Thay đổi hành vi nhỏ **có chủ ý, owner đã duyệt**: bài trống (toàn khoảng trắng) không còn chiếm chỗ trong 20 bài của `toArticles`.
- R5: không magic number, test kèm. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, output ra file, chờ `.exit`; "completed" của lệnh nền chỉ là lệnh khởi chạy; nếu một lệnh chờ quá 595 giây thì tiếp tục bằng vòng chờ riêng, không bỏ qua. Chạy lớp test riêng (`--tests`) cho từng bước; full suite `--rerun-tasks` ở cuối, **đo lại số test** (số trước là **3.852** theo FRONT-02y; không dùng làm bằng chứng). Đỏ chập chờn: chạy riêng lớp đó 3 lần + chạy lại cả bộ, ghi thẳng cả hai kết quả.
- Mutation: một script nền, không sửa tệp đột biến cho tới `ALLDONE`; khôi phục + `cmp`; mỗi đột biến chạy các lớp Nostr, sync và `SubscribeViewModelNostr*`; so sánh tự động tập test đỏ; **bảng mong đợi là dự đoán**, lệch thì đọc lý do (không bỏ qua); mẫu phải biên dịch được.
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- Nói thẳng phần không kiểm: relay thật, máy thật; chuỗi nằm rải hai relay vẫn có thể cụt (không gộp); ca hiếm có thể tốn tới 16 giây.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-19)

1. **Không phải sửa test hiện có nào ngoài việc thêm test mới.** Đã đọc: `NostrTestEvents.REPLY` và `EMPTY` chỉ dùng trong `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped` (`PLAIN`, `REPLY`, `EMPTY` cho ra `["Hello nostr, first note"]`, vẫn đúng) và trong danh sách `everyValidNote` của `NostrRelayClientTest` (hai test giới hạn khung). Với `everyValidNote` (38 sự kiện, 36 hiện được, `REPLY` và `EMPTY` không hiện) và 37 sự kiện ở test "khung thứ 51", **tập hiện được vẫn khác rỗng** nên `fetch` vẫn dừng ở relay đầu và trả **cùng** kết quả thô; hai test đó **vẫn xanh không đổi**.
2. **Hồ sơ:** `NostrTestEvents.PROFILE` cho tên `Alice Liddell`, `PROFILE_NEWER` cho `alice2`, `PROFILE_OLDER` cho `old alice`. Test relay dùng `relay(...)` của từng lớp: `NostrRelayProfileTest.relay(vararg events)` (không có `sendEose`) và `NostrRelayClientTest.relay(vararg events, sendEose)`; test mới đặt ở **`NostrRelayClientTest`** (có `server.requestCount` dùng được, đã có ở dòng ~229).
3. **Đếm kết nối bằng `server.requestCount`:** mỗi relay mở một kết nối, nên "không hỏi relay hai" nghĩa `requestCount == 1`, hỏi cả hai là `2`. Với MockWebServer, `requestCount` đếm yêu cầu HTTP (mỗi nâng cấp WebSocket là một yêu cầu): đã có test `fetch_doesNotOpenAConnection_forAnInvalidAuthor` dùng `assertEquals(0, server.requestCount)`.
4. **Test mapper chạy Robolectric** (`NostrFeedMapperTest` và `NostrFeedMapperThreadTest` đã có `@RunWith(RobolectricTestRunner)`), vì `titleFromBody` dùng `android.text.Html`.
5. **Dựng bài trống/trả lời cho test mapper bằng sự kiện hình thức** (`NostrEvent.parse` với `sig` toàn 0, như `NostrFeedMapperThreadTest`); test relay phải dùng **sự kiện ký thật** (`fetch` gọi `isValid`): `PLAIN` (hiện), `REPLY` (trả lời người khác), `EMPTY` (trống), `OLDER`.
6. **Thứ tự TDD:** Task 1 (mapper) đỏ bằng lỗi biên dịch (`visibleNotes` chưa có); Task 2 (fetch) đỏ bằng khẳng định thật (relay đầu chỉ có `REPLY` mà `fetch` vẫn dừng).
7. **Mô hình dự đoán Python** của `fetch` dưới đột biến (8 kịch bản T1 đến T8) đã chạy; bảng dưới lấy từ đó rồi suy luận lại từng test thật.
8. **Test `visibleNotes` khớp `toArticles`:** so **id** của `visibleNotes(...)` (sau khi sắp mới nhất trước và cắt `MAX_ARTICLES_SHOWN`) với **thứ tự link** của `toArticles(...)` qua `NostrKeys.noteId`; không cần `Article.id` (là UUID ngẫu nhiên).

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN; kiểm tự động ở Task 3)

Test relay mới (lớp `NostrRelayClientTest`): **A1** `fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers`, **A2** `fetch_stopsAtTheFirstRelay_whenItHasAShowableNote`, **A3** `fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers`, **A4** `fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote`, **A5** `fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay`, **A6** `fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote`, **A7** `fetch_doesNotMergeTheTwoRelays`.
Test mapper mới: **V1** (lớp `NostrFeedMapperThreadTest`) `visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles`, **V2** `aBlankNote_isNotShown_andDoesNotTakeAPlaceAmongTheTwenty`, **V3** `visibleNotes_dropsRepliesToOthersAndBlankNotes`.
Sẵn có liên quan: **K1** `fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes`, **K2** `fetch_readsFiftyFramesAndKeepsEveryValidOne`, **K3** `fetch_doesNotReadTheFiftyFirstFrame`, **M1** `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped`, **M2** `NostrFeedMapperTest.atMostTwentyArticles_areKept_newestFirst`, **M3** `NostrFeedMapperThreadTest.atMostTwentyArticles_stillHolds_withThreads`, **M4** `NostrFeedMapperThreadTest.aThreadBehindManyAnswersToOthers_isStillShown`.

| Đột biến | Đổi gì | Test đỏ mong đợi | Kiểu so |
|---|---|---|---|
| base | không | không | đúng |
| z1 | `fetch` quay lại `result.notes.isNotEmpty()` | A1, A3, A4, A5, A6, A7 | đúng |
| z2 | `fetch` luôn hỏi cả hai relay | A2 | đúng |
| z3 | `fetch` gộp sự kiện hai relay (relay đầu `REPLY` lẫn vào kết quả) | A7 | đúng |
| z4 | `fetch` bỏ `firstProfile` | A5, A6 | đúng |
| z5 | `visibleNotes` bỏ bước lọc chuỗi (chỉ lọc trống): bỏ cả bộ lọc chuỗi nên **nhiều test chuỗi sẵn có cũng đỏ** | V1, V3, A1, A3, A5, A6, A7, M1, M4 | ít nhất |
| z6 | `visibleNotes` bỏ lọc bài trống | V1, V2, V3, A4, M1 | ít nhất |
| z7 | `toArticles` không dùng `visibleNotes` (lọc riêng như cũ: cắt 20 rồi mới bỏ bài trống) | V2 | đúng |

**Ghi chú từ suy luận từng test:** `toArticles` và `visibleNotes` cùng đổi khi z5 hoặc z6 (dùng chung một hàm), nên phép so "visibleNotes khớp toArticles" trong V1 không tự bắt z5, z6; nhưng V1 **cũng** khẳng định danh sách tiêu đề cụ thể (`Own thread piece`, `Titled two`, `Titled one`) nên vẫn đỏ ở z5 (trả lời người lạ lọt vào) và z6 (bài trống lọt vào). V3 khóa nội dung `visibleNotes`. M1 (`repliesAndBlankNotesAreSkipped`) đỏ ở z5 vì trả lời lọt vào và ở z6 vì bài trống lọt vào (`toArticles` **không** còn tự kiểm tiêu đề trống: tin vào `visibleNotes`, xem Task 1). M4 đỏ ở z5 vì 25 trả lời người lạ chiếm 20 chỗ. **z5 bỏ cả bộ lọc chuỗi nên các test chuỗi sẵn có của `NostrFeedMapperThreadTest` (trả lời người lạ, vòng lặp, ...) cũng đỏ**: bảng chỉ liệt kê tập con bắt buộc. V2 xanh ở z5 (V2 không có trả lời) và đỏ ở z6 (bài trống mới nhất chiếm chỗ). A1 khẳng định id trả về **chứa** `PLAIN`, còn A7 khẳng định danh sách **đúng bằng** `[PLAIN]`, nên chỉ A7 bắt việc gộp (z3).

"Đúng": phải trùng chính xác. "Ít nhất": tập đỏ thực tế phải **chứa** các test ghi, và mọi test đỏ phải nằm trong các lớp Nostr, sync, `SubscribeViewModelNostr*`. Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng hoặc test và ghi trong báo cáo.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` | `visibleNotes`, `toArticles` dùng nó |
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt` | điều kiện dừng của `fetch` |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt` | V1, V2, V3 |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt` | A1 đến A7 |
| Tạm, **không commit**: `/tmp/z_mut.sh`, `/tmp/z_batch.sh`, `/tmp/z_check.py` | đột biến JVM |
| Modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02z_nostr-try-second-relay_DONE.md` | báo cáo |

---

### Task 1: `NostrFeedMapper.visibleNotes`, một chỗ cho luật "bài nào hiện" (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt`

**Interfaces:**
- Consumes: `NostrFeedMapper.toArticles(events, feedId, accountId, nowMillis, newId)`, `keepOriginalsAndOwnThreads(events)` (private), `escapeToHtml(text)` (private), `NostrKeys.noteId(hex)`, `RssDescriptionParser.titleFromBody(html)`; helper của test `note(n, createdAt, content, vararg tags)`, `reply(n, createdAt, content, to, marker)`, `id(n)`, `titles(vararg events)`, hằng `now`.
- Produces: `fun NostrFeedMapper.visibleNotes(events: List<NostrEvent>): List<NostrEvent>` (công khai, thuần, giữ thứ tự đầu vào); `toArticles` dùng nó **trước** khi sắp xếp và cắt `MAX_ARTICLES_SHOWN`.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
M=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt
grep -n "keepOriginalsAndOwnThreads(events).asSequence()\|if (title.isEmpty()) return@mapNotNull null\|val noteId = NostrKeys.noteId\|private fun escapeToHtml\|private fun keepOriginalsAndOwnThreads" $M
git status --short app
```
Expected: mỗi mẫu 1 dòng; `git status` sạch trong `app`.

- [ ] **Step 2: Viết test đỏ V1, V2, V3 (chèn trước `aReplyWhoseTargetIdIsBroken_isDropped`)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt"
s = open(T, encoding="utf-8").read()
anchor = "    @Test fun aReplyWhoseTargetIdIsBroken_isDropped() {"
assert s.count(anchor) == 1
new = '''    // ---- FRONT-02z: the one rule that says which notes are shown, shared by the feed and by the relay client.

    /** A mix: two titled notes, a blank one, an answer to a stranger, an own thread piece and another blank one (ids 1..6, oldest first). */
    private val mix: List<NostrEvent> = listOf(
        note(1, 1_700_000_100L, "Titled one"),
        note(2, 1_700_000_200L, "   "),
        reply(3, 1_700_000_300L, "Answer to a stranger", id(999)),
        note(4, 1_700_000_400L, "Titled two"),
        reply(5, 1_700_000_500L, "Own thread piece", id(4)),
        note(6, 1_700_000_600L, "\\n  \\t"),
    )

    @Test fun visibleNotes_dropsRepliesToOthersAndBlankNotes() {
        val shown = NostrFeedMapper.visibleNotes(mix)
        assertEquals(listOf(id(1), id(4), id(5)), shown.map { it.id })
    }

    @Test fun visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles() {
        val counter = AtomicInteger()
        val articles = NostrFeedMapper.toArticles(mix, "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
        val shownLinks = NostrFeedMapper.visibleNotes(mix).sortedByDescending { it.createdAt }
            .map { NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(it.id)) }
        assertEquals(shownLinks, articles.map { it.link })
        assertEquals(listOf("Own thread piece", "Titled two", "Titled one"), articles.map { it.title })
    }

    // Before FRONT-02z the cut to twenty came first and the blank notes were dropped after it, so a few blank notes among the newest
    // twenty left fewer than twenty articles: here 22 real notes and 3 NEWER blank ones must still give twenty real articles.
    @Test fun aBlankNote_isNotShown_andDoesNotTakeAPlaceAmongTheTwenty() {
        val real = (0 until 22).map { n -> note(300 + n, 1_700_000_000L + n, "real $n") }
        val blanks = (0 until 3).map { n -> note(400 + n, 1_700_009_000L + n, "  ") }
        val articles = NostrFeedMapper.toArticles(real + blanks, "feed-1", 7, now)
        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)
        assertEquals("real 21", articles.first().title)
        assertTrue(articles.none { it.title.isBlank() })
    }

'''
open(T, "w", encoding="utf-8").write(s.replace(anchor, new + anchor, 1))
EOF
grep -n "visibleNotes" app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt | head -5
grep -n "^import" app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt
```
Expected: `visibleNotes` dùng trong 2 test; import có `AtomicInteger`, `assertEquals`, `assertTrue` (đã có ở tệp).

- [ ] **Step 3: Chạy test, phải ĐỎ (lỗi biên dịch: chưa có `visibleNotes`)**

```bash
rm -f /tmp/z-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapperThreadTest" > /tmp/z-t1.log 2>&1; echo $? > /tmp/z-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/z-t1.exit && break; sleep 5; done; cat /tmp/z-t1.exit; grep -E "^e: " /tmp/z-t1.log | sed -E 's/.*(NostrFeedMapperThreadTest.kt:[0-9]+:[0-9]+ )/\1/' | cut -c1-110 | sort -u | head -4
```
Expected: `1` và `Unresolved reference 'visibleNotes'`.

- [ ] **Step 4: Sửa `NostrFeedMapper.kt` (hai mẫu, mỗi mẫu đúng 1 lần)**

`toArticles` không còn tự bỏ bài có `noteId` hỏng hay tiêu đề rỗng: `visibleNotes` đã loại; để tránh chép hai lần, một hàm riêng tính tiêu đề dùng chung.

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
M = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(M, encoding="utf-8").read()
def sub(old, new):
    global s
    assert s.count(old) == 1, old[:70]
    s = s.replace(old, new, 1)

sub('''        keepOriginalsAndOwnThreads(events).asSequence()
            .sortedByDescending { it.createdAt }
            .take(MAX_ARTICLES_SHOWN)
            .mapNotNull { event ->
                val noteId = NostrKeys.noteId(event.id) ?: return@mapNotNull null
                val html = escapeToHtml(event.content)
                val title = RssDescriptionParser.titleFromBody(html)
                if (title.isEmpty()) return@mapNotNull null
                Article(
''', '''        visibleNotes(events).asSequence()
            .sortedByDescending { it.createdAt }
            .take(MAX_ARTICLES_SHOWN)
            .map { event ->
                // visibleNotes already refused every note without a valid id or a title, so both are there.
                val noteId = requireNotNull(NostrKeys.noteId(event.id))
                val html = escapeToHtml(event.content)
                val title = titleOf(html)
                Article(
''')
sub('''    /**
     * The notes the feed shows: every note that is not a reply,''', '''    /**
     * The notes that become articles, in the order they came: not a reply to someone else (see [keepOriginalsAndOwnThreads]), a valid
     * id to link to, and a title that is not empty (a note of nothing but spaces has none). This is the ONE rule for "which notes are
     * shown": [toArticles] cuts to [MAX_ARTICLES_SHOWN] after it, and the relay client asks it before deciding that a relay had
     * something to show, so an answer to a stranger or a blank note never stops the search at the first relay.
     */
    fun visibleNotes(events: List<NostrEvent>): List<NostrEvent> =
        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }

    private fun titleOf(html: String): String = RssDescriptionParser.titleFromBody(html)

    /**
     * The notes the feed shows: every note that is not a reply,''')
open(M, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/main | tail -1
```
Expected: 1 tệp, khoảng 14 dòng thêm, 5 dòng bỏ.

- [ ] **Step 5: Chạy test, phải XANH (mapper mới, mapper cũ, đồng bộ, ViewModel Nostr)**

```bash
rm -f /tmp/z-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/z-t1.log 2>&1; echo $? > /tmp/z-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/z-t1.exit && break; sleep 5; done; cat /tmp/z-t1.exit; grep -E "FAILED|tests completed|^e: " /tmp/z-t1.log | head -6 | cut -c1-200; tail -2 /tmp/z-t1.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Cũng xác nhận `repliesAndBlankNotesAreSkipped` và `atMostTwentyArticles_*` vẫn xanh. Test đỏ lạ: đọc lý do (có thể là một test đang mong "bài trống chiếm chỗ"; nếu có, đó là một hành vi cũ cần báo, không tự sửa).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapperThreadTest.kt
git commit -m "feat(front-02z): one rule for which Nostr notes are shown, and a blank note no longer takes a place among the twenty

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `fetch` chỉ dừng ở relay đầu có bài hiện được (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt`

**Interfaces:**
- Consumes: `NostrFeedMapper.visibleNotes(events): List<NostrEvent>` (Task 1); `NostrRelayClient.fetch(authorHex)`; helper của test `relay(vararg events, sendEose)`, `client(vararg urls, secure, timeoutMs)`, `ids(notes)`, `wsUrl()`, `server` (có `requestCount`); sự kiện ký thật `PLAIN`, `OLDER`, `REPLY`, `EMPTY`, `PROFILE`, các hằng `PLAIN_ID`.
- Produces: `fetch` trả `result` của relay đầu tiên có `visibleNotes(result.notes).isNotEmpty()`; nếu không relay nào, `NostrFetchResult(emptyList(), firstProfile)`.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
N=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt
T=app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
grep -n "if (result.notes.isNotEmpty())" $N
grep -n "fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes\|import org.junit.Assert" $T
git status --short app
```
Expected: 1 dòng `if (result.notes.isNotEmpty())`; test cũ và các import `assertEquals`, `assertFalse`, `assertTrue` (không có `assertNull`); `git status` sạch.

- [ ] **Step 2: Viết test đỏ A1 đến A7 (chèn ngay sau `fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes`)**

Mỗi kịch bản dùng sự kiện **ký thật** (vì `fetch` gọi `isValid`): `REPLY` là trả lời người khác (id đích `aaaa…` không có), `EMPTY` là bài trống, `PLAIN` hiện được. Số kết nối đếm bằng `server.requestCount`.

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt"
s = open(T, encoding="utf-8").read()
anchor = "    @Test fun fetch_returnsEmpty_whenBothRelaysFail() = runBlocking {"
assert s.count(anchor) == 1
new = '''    // ---- FRONT-02z: a relay "had something" only when at least one of its notes can be SHOWN (not an answer to a stranger, not blank).

    @Test fun fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val notes = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertTrue(ids(notes).contains(NostrTestEvents.PLAIN_ID))
        assertEquals("both relays were asked", 2, server.requestCount)
    }

    @Test fun fetch_stopsAtTheFirstRelay_whenItHasAShowableNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        server.enqueue(relay(NostrTestEvents.OLDER))
        val notes = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertEquals("the second relay must not be asked", 1, server.requestCount)
    }

    @Test fun fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY))
        server.enqueue(relay(NostrTestEvents.REPLY))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
        assertEquals(2, server.requestCount)
    }

    @Test fun fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY, NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val result = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(result.notes))
        assertEquals("Alice Liddell", result.profileName)
    }

    @Test fun fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY, NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.REPLY))
        val result = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertEquals("Alice Liddell", result.profileName)
    }

    // The relay that is used is used ALONE: what the first relay had (an answer to a stranger) does not travel along.
    @Test fun fetch_doesNotMergeTheTwoRelays() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

'''
open(T, "w", encoding="utf-8").write(s.replace(anchor, new + anchor, 1))
EOF
grep -c "FRONT-02z" app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
```
Expected: `1` (cả khối chèn một lần).

- [ ] **Step 3: Chạy test, phải ĐỎ đúng các test dự kiến (A1, A3, A4, A5, A6, A7)**

```bash
rm -f /tmp/z-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClientTest" > /tmp/z-t2.log 2>&1; echo $? > /tmp/z-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/z-t2.exit && break; sleep 5; done; cat /tmp/z-t2.exit; grep -E "FAILED|^e: " /tmp/z-t2.log | sed -E 's/^com\.mckimquyen\.[a-z.]*\.([A-Za-z]+) > /\1 > /' | cut -c1-150 | head -10
```
Expected: `1`; đỏ **đúng** `fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers`, `fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers`, `fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote`, `fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay`, `fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote`, `fetch_doesNotMergeTheTwoRelays`. **A2 (`fetch_stopsAtTheFirstRelay_whenItHasAShowableNote`) xanh sẵn** (hành vi cũ đã dừng ở relay đầu có bài): nó là lưới an toàn cho đột biến z2. Có test khác đỏ hoặc lỗi biên dịch thì đọc lý do trước khi làm tiếp.

- [ ] **Step 4: Sửa `NostrRelayClient.fetch` (một mẫu, đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
N = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt"
s = open(N, encoding="utf-8").read()
old = "            if (result.notes.isNotEmpty()) return@withContext result.copy(profileName = result.profileName ?: firstProfile)\n"
assert s.count(old) == 1
new = '''            // A relay "had something" only if one of its notes can be SHOWN: a relay that holds nothing but answers to strangers (or blank
            // notes) must not stop the search here. The relay that is used is used alone (its notes are returned as they came, not merged).
            if (NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) {
                return@withContext result.copy(profileName = result.profileName ?: firstProfile)
            }
'''
open(N, "w", encoding="utf-8").write(s.replace(old, new, 1))
EOF
git diff --stat -- app/src/main | tail -1
```
Expected: 1 tệp, 5 dòng thêm, 1 dòng bỏ.

- [ ] **Step 5: Chạy test, phải XANH (mọi lớp relay, mapper, đồng bộ, ViewModel Nostr)**

```bash
rm -f /tmp/z-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/z-t2.log 2>&1; echo $? > /tmp/z-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/z-t2.exit && break; sleep 5; done; cat /tmp/z-t2.exit; grep -E "FAILED|tests completed|^e: " /tmp/z-t2.log | head -6 | cut -c1-200; tail -2 /tmp/z-t2.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Hai test giới hạn khung (`fetch_readsFiftyFramesAndKeepsEveryValidOne`, `fetch_doesNotReadTheFiftyFirstFrame`) vẫn xanh vì tập hiện được vẫn khác rỗng.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClientTest.kt
git commit -m "fix(front-02z): the Nostr relay search stops at the first relay with a note that can be shown, not at the first with any event

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Mutation JVM (7 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/z_mut.sh`, `/tmp/z_batch.sh`, `/tmp/z_check.py`. Đột biến tạm trong `NostrRelayClient.kt` và `NostrFeedMapper.kt` (luôn khôi phục).

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
 ("z1", rc, "            if (NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) {\n"),
 ("z1-2-3", rc, "            val result = readRelay(relay, authorHex)\n"),
 ("z2/z3 tail", rc, "        NostrFetchResult(emptyList(), firstProfile)\n"),
 ("z4", rc, "            firstProfile = firstProfile ?: result.profileName\n"),
 ("z5/z6", mp, "        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }\n"),
 ("z7", mp, "        visibleNotes(events).asSequence()\n"),
 ("z7 map", mp, "                val title = titleOf(html)\n"),
]
print({l: s.count(p) for l, s, p in pats})
EOF
```
Expected: mọi nhãn `1`. Khác 1: đọc lại tệp và sửa mẫu ở `z_batch.sh` (không chạy batch).

- [ ] **Step 2: Dựng `/tmp/z_mut.sh`, `/tmp/z_batch.sh`, `/tmp/z_check.py`**

```bash
cat > /tmp/z_mut.sh <<'EOF'
#!/bin/zsh
# usage: z_mut.sh <label> <file> <old> <new>   ("base" = no change). JVM only, never touches a device.
label="$1"; FILE="${2-}"; old="${3-}"; new="${4-}"
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
BACKUP=/tmp/z-mutbackup.bin
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
  > "/tmp/z-mut-$label.log" 2>&1
rc=$?
[ "$label" != base ] && { restore; trap - EXIT INT TERM; }
failing=$(grep -E "^com\.mckimquyen.* > .* FAILED" "/tmp/z-mut-$label.log" | sed -E 's/^.* > ([A-Za-z0-9_`. ,()-]+) FAILED.*/\1/' | sort -u | tr '\n' ',')
compile=$(grep -c "^e: " "/tmp/z-mut-$label.log")
echo "$label: gradle_exit=$rc compile_errors=$compile failing=[$failing] source=$rest"
EOF
chmod +x /tmp/z_mut.sh
cat > /tmp/z_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr
RC=$J/NostrRelayClient.kt; MP=$J/NostrFeedMapper.kt
cp -p $RC /tmp/z-orig-rc.kt; cp -p $MP /tmp/z-orig-mp.kt
M=/tmp/z_mut.sh
zsh $M base
zsh $M z1 $RC '            if (NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) {
' '            if (result.notes.isNotEmpty()) {
'
zsh $M z2 $RC '        NostrFetchResult(emptyList(), firstProfile)
' '        NostrFetchResult(emptyList(), firstProfile)
' 
zsh $M z4 $RC '            firstProfile = firstProfile ?: result.profileName
' ''
zsh $M z5 $MP '        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }
' '        events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }
'
zsh $M z6 $MP '        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }
' '        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null }
'
zsh $M z7 $MP '        visibleNotes(events).asSequence()
' '        keepOriginalsAndOwnThreads(events).asSequence()
'
zsh $M base
cmp -s /tmp/z-orig-rc.kt $RC && echo "final rc: identical" || echo "final rc: DIFFERENT"
cmp -s /tmp/z-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "git diff vs HEAD (main): $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
```
**Hai đột biến z2 và z3 thay cả khối vòng lặp của `fetch`.** Khối dưới đây là khối thật của mã sau Task 2, chép nguyên văn; hai biến thể được dựng từ nó (đã kiểm về kiểu). Dòng `zsh $M z2 ...` tạm ở trên được thay ở Step 3.

- [ ] **Step 3: Ghi khối thật và hai biến thể, sửa `z_batch.sh`**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > /tmp/z-block.txt <<'BLOCKEOF'
        var firstProfile: String? = null
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val result = readRelay(relay, authorHex)
            // A relay "had something" only if one of its notes can be SHOWN: a relay that holds nothing but answers to strangers (or blank
            // notes) must not stop the search here. The relay that is used is used alone (its notes are returned as they came, not merged).
            if (NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) {
                return@withContext result.copy(profileName = result.profileName ?: firstProfile)
            }
            firstProfile = firstProfile ?: result.profileName
        }
        NostrFetchResult(emptyList(), firstProfile)
BLOCKEOF
cat > /tmp/z-block-z2.txt <<'Z2EOF'
        var firstProfile: String? = null
        var chosen: NostrFetchResult? = null
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val result = readRelay(relay, authorHex)
            if (chosen == null && NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) chosen = result.copy(profileName = result.profileName ?: firstProfile)
            firstProfile = firstProfile ?: result.profileName
        }
        chosen ?: NostrFetchResult(emptyList(), firstProfile)
Z2EOF
cat > /tmp/z-block-z3.txt <<'Z3EOF'
        var firstProfile: String? = null
        val allNotes = ArrayList<NostrEvent>()
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val result = readRelay(relay, authorHex)
            allNotes += result.notes
            firstProfile = firstProfile ?: result.profileName
        }
        if (NostrFeedMapper.visibleNotes(allNotes).isNotEmpty()) NostrFetchResult(allNotes.sortedByDescending { it.createdAt }, firstProfile) else NostrFetchResult(emptyList(), firstProfile)
Z3EOF
python3 - <<'EOF'
# -*- coding: utf-8 -*-
import re
block = open('/tmp/z-block.txt', encoding='utf-8').read()
rc = open('app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt', encoding='utf-8').read()
assert rc.count(block) == 1, 'the fetch loop block is not exactly as the plan expects: read it again and fix the three files'
p = '/tmp/z_batch.sh'; b = open(p, encoding='utf-8').read()
new = 'zsh $M z2 $RC "$(cat /tmp/z-block.txt)" "$(cat /tmp/z-block-z2.txt)"\nzsh $M z3 $RC "$(cat /tmp/z-block.txt)" "$(cat /tmp/z-block-z3.txt)"\n'
b, n = re.subn(r"zsh \$M z2 \$RC '.*?'\s*\n(?:\s*\n)?", lambda m: new, b, count=1, flags=re.S)
assert n == 1 and b.count('z-block-z2') == 1 and b.count('z-block-z3') == 1, 'z_batch.sh was not rewritten as expected'
open(p, 'w', encoding='utf-8').write(b)
EOF
grep -n 'zsh $M z' /tmp/z_batch.sh | cut -c1-110
```
Expected: dòng `z2`, `z3` dùng `$(cat ...)`; z1, z4, z5, z6, z7 giữ nguyên. Nếu `assert` báo khối khác (thụt lề hay dòng chú thích đổi), đọc `NostrRelayClient.kt` rồi sửa ba tệp cho khớp, không tắt `assert`. Chú ý: `$(cat ...)` bỏ các dòng trống cuối nên mẫu `old` kết thúc ở dòng `NostrFetchResult(emptyList(), firstProfile)` không có xuống dòng cuối; `z_mut.sh` so khớp chuỗi con nên vẫn đúng 1 lần.

- [ ] **Step 4: Dựng `/tmp/z_check.py`**

```bash
cat > /tmp/z_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
A = {
 "A1": "fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers", "A2": "fetch_stopsAtTheFirstRelay_whenItHasAShowableNote",
 "A3": "fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers", "A4": "fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote",
 "A5": "fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay", "A6": "fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote",
 "A7": "fetch_doesNotMergeTheTwoRelays",
}
V = {
 "V1": "visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles", "V2": "aBlankNote_isNotShown_andDoesNotTakeAPlaceAmongTheTwenty",
 "V3": "visibleNotes_dropsRepliesToOthersAndBlankNotes",
}
M1 = "repliesAndBlankNotesAreSkipped"; M4 = "aThreadBehindManyAnswersToOthers_isStillShown"
N = {**A, **V}
def S(*ks): return {N.get(k, k) for k in ks}
# kind "exact": equals; "atleast": contains
EXPECTED = {
 "z1": ("exact", S("A1", "A3", "A4", "A5", "A6", "A7")),
 "z2": ("exact", S("A2")),
 "z3": ("exact", S("A7")),
 "z4": ("exact", S("A5", "A6")),
 "z5": ("atleast", S("V1", "V3", "A1", "A3", "A5", "A6", "A7", M1, M4)),
 "z6": ("atleast", S("V1", "V2", "V3", "A4", M1)),
 "z7": ("exact", S("V2")),
}
bad = 0
for label, (kind, want) in EXPECTED.items():
    try:
        text = open(f"/tmp/z-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError:
        print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M):
        print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = (got == want) if kind == "exact" else want <= got
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, kind, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/z-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN")
bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```

- [ ] **Step 5: Chạy batch nền, chờ ALLDONE (vòng chờ riêng nếu quá 595 giây)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/z-batch.log; ( zsh /tmp/z_batch.sh > /tmp/z-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/z-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/z-batch.log; cut -c1-160 /tmp/z-batch.log | tail -4
```
Không sửa hai tệp production cho tới `ALLDONE`. Chưa có `ALLDONE`: chạy lại **chỉ** vòng chờ (nhiều lần nếu cần), không bỏ qua.

- [ ] **Step 6: Đối chiếu tự động và xử lý lệch**

```bash
python3 /tmp/z_check.py
```
Expected: `ALL-MATCH`. Đột biến lệch: **đọc lý do** (tập đỏ thực tế và thông báo trong `/tmp/z-mut-<nhãn>.log`) rồi chọn: (a) dự đoán sai, sửa bảng ở đầu plan và `z_check.py`, ghi trong báo cáo; (b) test yếu (mọi test vẫn xanh, đột biến sống sót): thêm khẳng định (quay lại Task 1 hoặc 2) rồi chạy lại batch; (c) đột biến hóa ra tương đương: đổi thành đột biến thật và ghi lại. Kiểm cuối batch: hai dòng `final … identical` và `git diff vs HEAD (main): 0 lines`.

---

### Task 4: JVM đầy đủ, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng FRONT-02y và dòng 02z mới), `doc/task/todo/FRONT-02_decentralized-web3-social.md` (mục "Cập nhật"), `docs/superpowers/specs/2026-11-19-front-02z-nostr-try-second-relay-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02z_nostr-try-second-relay_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`; quá 595 giây thì vòng chờ riêng)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/z-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/z-jvm.log 2>&1; echo $? > /tmp/z-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/z-jvm.exit && break; sleep 5; done; cat /tmp/z-jvm.exit 2>/dev/null || echo "still running"
```
Rồi (khi có `.exit`):
```bash
grep -E "FAILED|tests completed" /tmp/z-jvm.log | head -5; tail -3 /tmp/z-jvm.log
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
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi. Số test = số trước (**3.852**, chỉ để đối chiếu) cộng test thêm: Task 1 thêm 3 (V1, V2, V3), Task 2 thêm 7 (A1 đến A7): **kỳ vọng 3.862**; báo cáo ghi số **đo được**. Có test đỏ **không liên quan**: chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả.

- [ ] **Step 2: Kiểm phạm vi diff (đúng hai tệp production)**

```bash
git status --short
git diff --stat HEAD~2 HEAD -- app/src/main app/build.gradle app/schemas
git diff --name-only HEAD~2 HEAD
```
Expected: `app/src/main` chỉ có `NostrFeedMapper.kt` và `NostrRelayClient.kt`; không `NostrFetchResult.kt`, `NostrEvent.kt`, `LocalRssSv.kt`, `SubscribeViewModel.kt`, `app/build.gradle`, `app/schemas`, manifest, `res`; test: `NostrFeedMapperThreadTest.kt` và `NostrRelayClientTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md`, task gốc và spec**

Báo cáo `doc/task/done/FRONT-02z_nostr-try-second-relay_DONE.md` (tiếng Việt đơn giản) gồm: điều đã làm (hai tệp production); nguyên nhân (dừng ở relay đầu "có sự kiện" thay vì "có bài hiện được"); một chỗ duy nhất cho luật `visibleNotes`; thay đổi hành vi nhỏ có chủ ý (bài trống không chiếm chỗ trong 20); bảng 7 đột biến với tập test đỏ **đo được** (kèm `z_check.py`, ghi mọi dự đoán sai đã sửa); JVM số **đo lại**; **giới hạn nói thẳng**: không gộp hai relay nên chuỗi rải hai relay vẫn có thể cụt; ca hiếm có thể tốn tới 16 giây (hai relay, mỗi relay tối đa 8 giây); cả hai relay chỉ có trả lời người khác thì vẫn báo lỗi như cũ; chuỗi dài hơn 50 sự kiện vẫn cụt; chưa kiểm relay thật hay máy thật.

`doc/feature.md`: dòng FRONT-02y ghi "relay đầu chỉ có trả lời người khác đã khắc phục ở FRONT-02z"; thêm dòng FRONT-02z sau dòng FRONT-02y. `doc/task/todo/FRONT-02_decentralized-web3-social.md`: mục "Cập nhật" ghi FRONT-02z (giữ task gốc trong `todo/`).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02z): report that the Nostr relay search now looks for a note that can be shown

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -4; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** hàm thuần dùng chung `visibleNotes` (bài hiện được = qua bộ lọc chuỗi, có `noteId`, tiêu đề không rỗng); `toArticles` dùng nó trước khi sắp xếp và cắt 20 → Task 1 (V1, V2, V3); `fetch` dừng ở relay đầu có bài hiện được, trả `result` nguyên, không gộp, cả hai rỗng thì rỗng kèm hồ sơ → Task 2 (A1 đến A7); không đổi REQ, chữ ký `fetch`, `NostrFetchResult`, `LocalRssSv`, `SubscribeViewModel` → Global Constraints và Task 4 Step 2; đột biến z1 đến z7 của spec → Task 3; JVM đo lại, báo cáo, `feature.md` → Task 4; thay đổi hành vi bài trống (đã duyệt) → Global Constraints và V2; nói thẳng giới hạn (không gộp, 16 giây) → Global Constraints và Task 4 Step 3.
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ, kể cả khối thay thế của z2 và z3 (đã dựng từ mã thật và chép nguyên văn). Ngoại lệ có ý thức duy nhất: dòng `z2` tạm trong `z_batch.sh` ở Step 2 cố ý vô hại cho tới khi Step 3 thay bằng khối thật.
3. **Nhất quán tên:** `visibleNotes`, `titleOf`, `MAX_ARTICLES_SHOWN`, `requestCount`, A1 đến A7, V1 đến V3, M1, M4 khớp giữa Task 1, 2, 3 và `z_check.py`. Các sự kiện mẫu `PLAIN`, `REPLY`, `EMPTY`, `OLDER`, `PROFILE` đã đọc nội dung thật (`REPLY` trả lời id `aaaa…` không có trong tập, `EMPTY` là `"   "`). Mô hình Python của `fetch` dưới z1 đến z6 và của `visibleNotes` dưới z5, z6, z7 đã chạy; bảng dự đoán đã sửa theo suy luận từng test (z1 thêm A7; z2 chỉ A2; z5 và z6 liệt kê ít nhất vì bỏ lọc chuỗi làm đỏ nhiều test chuỗi sẵn có).
4. **Rủi ro còn lại (nói thẳng):** (a) bảng tập test đỏ là **dự đoán** (đã sửa nhiều lần sau suy luận từng test; z5 và z6 dùng kiểu "ít nhất" vì chạm nhiều test chuỗi sẵn có); (b) `assert` ở Step 3 sẽ báo nếu khối `fetch` thật lệch plan (thụt lề, dòng chú thích), khi đó phải chỉnh ba tệp khối; (c) số test mong đợi 3.862 chỉ để đối chiếu; (d) bước đỏ của Task 1 là lỗi biên dịch, của Task 2 là khẳng định thật (6 trên 7 test mới đỏ, A2 xanh sẵn); (e) không kiểm relay thật hay máy thật, theo thiết kế.
