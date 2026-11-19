# FRONT-02aa — Ảnh bìa cho bài Nostr: kế hoạch thực thi (TDD)

> **Cho người thực thi:** dùng superpowers:executing-plans (inline). Các bước dùng ô `- [ ]`.

**Mục tiêu:** bài Nostr có link ảnh https trong chữ thì `Article.img` được điền, để danh sách hiện ảnh bìa theo công tắc ảnh có sẵn.

**Kiến trúc:** một hàm thuần `NostrFeedMapper.coverImageUrl(content)` (regex có tên hằng) và `toArticles` gọi nó. Chỉ một tệp production đổi.

**Công nghệ:** Kotlin, JUnit4 + Robolectric (`toArticles` dùng `titleFromBody`), Gradle chạy nền.

**Spec:** `docs/superpowers/specs/2026-11-19-front-02aa-nostr-cover-image-design.md` (commit `ce239c2a`).

## Global Constraints
- Nhánh `dev`; không worktree, không push, không `.idea`. **Chỉ JVM, không chạm thiết bị nào (không adb).**
- Production đổi **đúng một tệp**: `NostrFeedMapper.kt`. Không đổi `OfflinePrecacheService` (đã bỏ qua nguồn Nostr), UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay; không đổi chữ ký `toArticles`.
- Chỉ `https`, đuôi `jpg jpeg png gif webp` (không phân biệt hoa thường), `?query` hoặc `#fragment` được phép sau đuôi, kết thúc bằng khoảng trắng hoặc hết chữ. Địa chỉ lấy từ chữ gốc `event.content`, **không** từ HTML đã thoát. Link dài hơn `MAX_COVER_URL_LENGTH = 2048` bị bỏ qua (lấy link hợp lệ tiếp theo).
- R5: không magic number (hằng có tên), test kèm. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, ghi file, chờ `.exit`; lệnh chờ quá 595 giây thì tiếp tục bằng vòng chờ riêng. zsh: không `echo ====`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `encoding="utf-8"`).
- Nói thẳng: link không có đuôi ảnh bị bỏ sót; link dính dấu câu ("a.jpg.") bị bỏ sót; ảnh chỉ có ở bài mới; Triage và Reels hiện ảnh khi đang xem (không theo công tắc danh sách, như RSS); mở danh sách khi công tắc ảnh bật làm máy chủ ảnh thấy IP; chưa kiểm máy thật.

## Điều chỉnh so với spec (đã đọc code thật)
1. Sự kiện mẫu `NostrTestEvents` **không có link ảnh**; test dựng sự kiện hình thức (`sig` toàn 0) như `NostrFeedMapperThreadTest`. `toArticles` không kiểm chữ ký.
2. Test mới nằm trong lớp mới `NostrCoverImageTest` (Robolectric, vì `toArticles` gọi `titleFromBody`). `NostrFeedMapperTest.aPlainNote_becomesAnArticle` khẳng định `img == null` với `PLAIN` (không có link) vẫn đúng.
3. "Quá dài" chọn là: **bỏ link đó, lấy link hợp lệ kế tiếp** (không phải bỏ cả bài). Ranh giới: đúng 2048 ký tự được giữ, 2049 bị bỏ.
4. Regex dựng từ hằng `COVER_IMAGE_EXTENSIONS`; ca xấu nhất của backtracking (nhiều `https://` nối nhau trong 64 KiB) là bậc hai nhưng nhỏ, chấp nhận và nói trong báo cáo.

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN, kiểm tự động ở Task 2)
Test mới (lớp `NostrCoverImageTest`): **C1** `aHttpsImageLink_isTheCover`, **C2** `everyImageExtension_isRecognised_inAnyCase`, **C3** `anHttpLink_isNotACover`, **C4** `aLinkThatIsNotAnImage_isNotACover`, **C5** `theFirstImageLink_wins`, **C6** `aQueryAfterTheExtension_isKept`, **C7** `aTooLongLink_isSkipped_andTheNextOneIsUsed`, **C8** `aLinkOfExactlyTheLimit_isKept_andOneMoreIsSkipped`, **C9** `aNoteWithoutALink_hasNoCover`, **C10** `toArticles_setsTheCover_onlyForANoteWithAnImageLink`, **C11** `theCover_comesFromTheTextAsTyped_notFromTheEscapedHtml`, **C12** `aLinkAtTheEndOfALine_isStillACover`.

| Đột biến | Đổi gì | Test đỏ mong đợi |
|---|---|---|
| base | không | không |
| c1 | nhận cả `http` | C3 |
| c2 | đuôi nào cũng nhận (`[a-z0-9]+`) | C4 |
| c3 | lấy link hợp lệ **cuối** thay vì đầu | C5 |
| c4 | bỏ giới hạn độ dài | C7, C8 |
| c5 | `toArticles` không đặt `img` | C10, C11, C12 |
| c6 | lấy link từ HTML đã thoát | C11, C12 |
| c7 | bỏ điều kiện "sau đuôi là khoảng trắng hoặc hết chữ" | C4 |
| c8 | bỏ không-phân-biệt-hoa-thường | C2 |
| c9 | giới hạn `<=` thành `<` | C8 |

Tất cả kiểu so: **đúng** (tập đỏ phải trùng chính xác). Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng, ghi trong báo cáo.

## Cấu trúc file
- Sửa: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` (hằng, regex, `coverImageUrl`, dòng `img = …`).
- Tạo: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrCoverImageTest.kt`.
- Tài liệu: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02aa_nostr-cover-image_DONE.md`, spec (mục "Điều chỉnh khi thực thi").

---

### Task 1: Hàm `coverImageUrl` và `toArticles` điền `img` (test đỏ trước)

**Files:** Tạo `NostrCoverImageTest.kt`; sửa `NostrFeedMapper.kt`.

**Interfaces:** Produces: `NostrFeedMapper.coverImageUrl(content: String): String?`, `NostrFeedMapper.MAX_COVER_URL_LENGTH: Int` (= 2048).

- [ ] **Step 1: Viết test (đỏ vì lỗi biên dịch: chưa có `coverImageUrl`, `MAX_COVER_URL_LENGTH`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrCoverImageTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cover image of a Nostr note: the first https link in the text that ends in an image extension. The text is untrusted, so only
 * the address is taken; nothing is downloaded here. The notes are built by hand (the mapper never checks the signature).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrCoverImageTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val limit = NostrFeedMapper.MAX_COVER_URL_LENGTH

    private fun coverOf(content: String): String? {
        val event = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", "%064x".format(1)); put("pubkey", author); put("created_at", 1_700_000_000L); put("kind", 1)
                    put("tags", org.json.JSONArray()); put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        return NostrFeedMapper.toArticles(listOf(event), "feed-1", 7, now).single().img
    }

    /** A link of exactly [length] characters: the address, then filler, then ".jpg". */
    private fun linkOfLength(length: Int): String {
        val head = "https://x.example/"
        return head + "a".repeat(length - head.length - ".jpg".length) + ".jpg"
    }

    @Test fun aHttpsImageLink_isTheCover() {
        assertEquals("https://img.example/a.jpg", NostrFeedMapper.coverImageUrl("look https://img.example/a.jpg here"))
    }

    @Test fun everyImageExtension_isRecognised_inAnyCase() {
        for (ext in listOf("jpg", "jpeg", "png", "gif", "webp")) {
            assertEquals("https://x.example/a.$ext", NostrFeedMapper.coverImageUrl("https://x.example/a.$ext"))
        }
        assertEquals("https://x.example/A.PNG", NostrFeedMapper.coverImageUrl("https://x.example/A.PNG"))
    }

    @Test fun anHttpLink_isNotACover() {
        assertNull(NostrFeedMapper.coverImageUrl("http://x.example/a.jpg"))
        assertNull(NostrFeedMapper.coverImageUrl("ftp://x.example/a.png"))
    }

    @Test fun aLinkThatIsNotAnImage_isNotACover() {
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/page"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.pdf"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.png.exe"))
        assertNull(NostrFeedMapper.coverImageUrl("https://example.com/a.pngx"))
    }

    @Test fun theFirstImageLink_wins() {
        assertEquals(
            "https://x.example/one.jpg",
            NostrFeedMapper.coverImageUrl("https://x.example/one.jpg and https://x.example/two.png"),
        )
    }

    @Test fun aQueryAfterTheExtension_isKept() {
        assertEquals("https://x.example/a.jpg?w=100&h=2", NostrFeedMapper.coverImageUrl("see https://x.example/a.jpg?w=100&h=2 ok"))
    }

    @Test fun aTooLongLink_isSkipped_andTheNextOneIsUsed() {
        val tooLong = linkOfLength(limit + 1)
        assertEquals("https://y.example/short.png", NostrFeedMapper.coverImageUrl("$tooLong https://y.example/short.png"))
        assertNull(NostrFeedMapper.coverImageUrl(tooLong))
    }

    @Test fun aLinkOfExactlyTheLimit_isKept_andOneMoreIsSkipped() {
        val exact = linkOfLength(limit)
        assertEquals(limit, exact.length)
        assertEquals(exact, NostrFeedMapper.coverImageUrl(exact))
        assertNull(NostrFeedMapper.coverImageUrl(linkOfLength(limit + 1)))
    }

    @Test fun aNoteWithoutALink_hasNoCover() {
        assertNull(NostrFeedMapper.coverImageUrl(""))
        assertNull(NostrFeedMapper.coverImageUrl("just words, no link"))
    }

    @Test fun toArticles_setsTheCover_onlyForANoteWithAnImageLink() {
        assertEquals("https://img.example/a.jpg", coverOf("photo https://img.example/a.jpg"))
        assertNull(coverOf("no image here"))
    }

    @Test fun theCover_comesFromTheTextAsTyped_notFromTheEscapedHtml() {
        // The stored body escapes "&" to "&amp;"; the address must keep the "&" the author typed.
        assertEquals("https://x.example/a.jpg?a=1&b=2", coverOf("https://x.example/a.jpg?a=1&b=2"))
    }

    @Test fun aLinkAtTheEndOfALine_isStillACover() {
        // A new line becomes "<br>" in the stored body; the link must be found in the text as typed, where the line break is a space.
        assertEquals("https://x.example/a.jpg", coverOf("look\nhttps://x.example/a.jpg\nnext line"))
    }
}
EOF
rm -f /tmp/aa-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrCoverImageTest" > /tmp/aa-t1.log 2>&1; echo $? > /tmp/aa-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aa-t1.exit && break; sleep 5; done; cat /tmp/aa-t1.exit; grep -E "^e: " /tmp/aa-t1.log | cut -c1-200 | head -4
```
Expected: `1`, lỗi biên dịch `Unresolved reference` cho `coverImageUrl` và `MAX_COVER_URL_LENGTH`.

- [ ] **Step 2: Viết mã tối thiểu trong `NostrFeedMapper.kt`**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
P = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(P, encoding="utf-8").read()
a = '''    const val MAX_ARTICLES_SHOWN = 20
'''
assert s.count(a) == 1
s = s.replace(a, a + '''
    /** The longest cover image address kept. A longer link is skipped, so one line of a note cannot fill the database. */
    const val MAX_COVER_URL_LENGTH = 2048
    private const val COVER_IMAGE_EXTENSIONS = "jpe?g|png|gif|webp"

    // https only, up to an image extension, then an optional ?query or #fragment, then a space or the end of the text.
    private val COVER_IMAGE_URL = Regex("""https://\\S+?\\.(?:$COVER_IMAGE_EXTENSIONS)(?:[?#]\\S*)?(?=\\s|$)""", RegexOption.IGNORE_CASE)
''', 1)
b = '''    fun feedUrl(npub: String): String'''
assert s.count(b) == 1
s = s.replace(b, '''    /**
     * The address of the first image link in the note text as the author typed it (not the escaped HTML): https, an image extension,
     * not longer than [MAX_COVER_URL_LENGTH]. Nothing is downloaded here; the list shows it only when the user's image switch is on.
     */
    fun coverImageUrl(content: String): String? =
        COVER_IMAGE_URL.findAll(content).map { it.value }.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }

''' + b, 1)
c = '''                    link = NJUMP_BASE + noteId,
'''
assert s.count(c) == 1
s = s.replace(c, c + '''                    img = coverImageUrl(event.content),
''', 1)
open(P, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/main | tail -1
```

- [ ] **Step 3: Chạy các lớp Nostr + sync + SubscribeViewModelNostr* (xanh)**

```bash
rm -f /tmp/aa-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/aa-t1.log 2>&1; echo $? > /tmp/aa-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aa-t1.exit && break; sleep 5; done; cat /tmp/aa-t1.exit; grep -E " FAILED$|^e: " /tmp/aa-t1.log | head -5
```
Expected: `0`. Đỏ ở đâu thì đọc lý do, sửa mã hoặc test (không bỏ qua).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrCoverImageTest.kt
git commit -m "feat(front-02aa): a Nostr note with an https image link gets a cover image in the list

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline -1; git status --short
```

---

### Task 2: Mutation JVM (9 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/aa_mut.sh`, `/tmp/aa_batch.sh`, `/tmp/aa_check.py`. Đột biến tạm trong `NostrFeedMapper.kt` (luôn khôi phục).

- [ ] **Step 1: Kiểm mẫu duy nhất (mỗi mẫu đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
mp = open("app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt", encoding="utf-8").read()
pats = {
 "c1": 'Regex("""https://\\S+?', "c2": 'private const val COVER_IMAGE_EXTENSIONS = "jpe?g|png|gif|webp"',
 "c3/c4": ".firstOrNull { it.length <= MAX_COVER_URL_LENGTH }", "c5/c6": "img = coverImageUrl(event.content),",
 "c7": '(?:[?#]\\S*)?(?=\\s|$)"""', "c8": ", RegexOption.IGNORE_CASE)", "c9": "it.length <= MAX_COVER_URL_LENGTH",
}
print({k: mp.count(v) for k, v in pats.items()})
EOF
```
Expected: mọi nhãn `1`. Khác 1: đọc tệp, sửa mẫu ở `aa_batch.sh`.

- [ ] **Step 2: Dựng script**

```bash
sed -e 's#/tmp/z-#/tmp/aa-#g' /tmp/z_mut.sh > /tmp/aa_mut.sh; chmod +x /tmp/aa_mut.sh
cat > /tmp/aa_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
MP=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt
cp -p $MP /tmp/aa-orig-mp.kt
M=/tmp/aa_mut.sh
zsh $M base
zsh $M c1 $MP 'Regex("""https://\S+?' 'Regex("""https?://\S+?'
zsh $M c2 $MP 'private const val COVER_IMAGE_EXTENSIONS = "jpe?g|png|gif|webp"' 'private const val COVER_IMAGE_EXTENSIONS = "[a-z0-9]+"'
zsh $M c3 $MP '.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }' '.lastOrNull { it.length <= MAX_COVER_URL_LENGTH }'
zsh $M c4 $MP '.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }' '.firstOrNull()'
zsh $M c5 $MP 'img = coverImageUrl(event.content),' ''
zsh $M c6 $MP 'img = coverImageUrl(event.content),' 'img = coverImageUrl(html),'
zsh $M c7 $MP '(?:[?#]\S*)?(?=\s|$)"""' '(?:[?#]\S*)?"""'
zsh $M c8 $MP ', RegexOption.IGNORE_CASE)' ')'
zsh $M c9 $MP 'it.length <= MAX_COVER_URL_LENGTH' 'it.length < MAX_COVER_URL_LENGTH'
zsh $M base
cmp -s /tmp/aa-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "uncommitted production diff: $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
cat > /tmp/aa_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
N = {"C1": "aHttpsImageLink_isTheCover", "C2": "everyImageExtension_isRecognised_inAnyCase", "C3": "anHttpLink_isNotACover",
     "C4": "aLinkThatIsNotAnImage_isNotACover", "C5": "theFirstImageLink_wins", "C6": "aQueryAfterTheExtension_isKept",
     "C7": "aTooLongLink_isSkipped_andTheNextOneIsUsed", "C8": "aLinkOfExactlyTheLimit_isKept_andOneMoreIsSkipped",
     "C9": "aNoteWithoutALink_hasNoCover", "C10": "toArticles_setsTheCover_onlyForANoteWithAnImageLink",
     "C11": "theCover_comesFromTheTextAsTyped_notFromTheEscapedHtml", "C12": "aLinkAtTheEndOfALine_isStillACover"}
def S(*ks): return {N[k] for k in ks}
EXPECTED = {"c1": S("C3"), "c2": S("C4"), "c3": S("C5"), "c4": S("C7", "C8"), "c5": S("C10", "C11", "C12"),
            "c6": S("C11", "C12"), "c7": S("C4"), "c8": S("C2"), "c9": S("C8")}
bad = 0
for label, want in EXPECTED.items():
    try: text = open(f"/tmp/aa-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError: print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M): print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = got == want; bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/aa-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN"); bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
grep -c "aa-" /tmp/aa_mut.sh
```

- [ ] **Step 3: Chạy batch nền, chờ ALLDONE**

```bash
rm -f /tmp/aa-batch.log; ( zsh /tmp/aa_batch.sh > /tmp/aa-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aa-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aa-batch.log; cut -c1-160 /tmp/aa-batch.log | tail -3
```
Chưa có `ALLDONE`: chạy lại **chỉ** vòng chờ. Không sửa `NostrFeedMapper.kt` cho tới `ALLDONE`.

- [ ] **Step 4: Đối chiếu tự động**

```bash
python3 /tmp/aa_check.py
```
Expected: `ALL-MATCH`; cuối batch: `final mp: identical`, `uncommitted production diff: 0 lines`. Lệch: đọc lý do (tập đỏ + thông báo trong `/tmp/aa-mut-<nhãn>.log`): (a) dự đoán sai thì sửa bảng và `aa_check.py`, ghi báo cáo; (b) đột biến sống sót thì thêm khẳng định (Task 1) rồi chạy lại; (c) đột biến tương đương thì đổi thành đột biến thật.

---

### Task 3: JVM đầy đủ, phạm vi, tài liệu, commit

**Files:** Sửa `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi"); tạo `doc/task/done/FRONT-02aa_nostr-cover-image_DONE.md`.

- [ ] **Step 1: JVM full (nền, chờ `.exit`)**

```bash
rm -f /tmp/aa-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/aa-jvm.log 2>&1; echo $? > /tmp/aa-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/aa-jvm.exit && break; sleep 5; done; cat /tmp/aa-jvm.exit 2>/dev/null || echo "still running"
```
Rồi: `grep -E "FAILED|tests completed" /tmp/aa-jvm.log | head -5; tail -2 /tmp/aa-jvm.log` và đếm test bằng đoạn python đọc `app/build/test-results/testDevDebugUnitTest/*.xml` (như FRONT-02z). Số trước **3.862** (chỉ để đối chiếu), kỳ vọng **3.874** (+12). Đỏ không liên quan: chạy riêng lớp đó 3 lần và chạy lại cả bộ, ghi thẳng.

- [ ] **Step 2: Kiểm phạm vi**

```bash
git diff --name-only ce239c2a HEAD -- app
```
Expected: đúng `NostrFeedMapper.kt` và `NostrCoverImageTest.kt`.

- [ ] **Step 3: Báo cáo, feature.md, task gốc, spec**

Báo cáo `FRONT-02aa_nostr-cover-image_DONE.md` (tiếng Việt đơn giản): việc đã làm; luật ảnh bìa; bảng 9 đột biến với tập đỏ **đo được**; JVM đo lại; **nói thẳng:** link không đuôi ảnh và link dính dấu câu bị bỏ sót; ảnh chỉ có ở bài mới; mở danh sách khi công tắc ảnh bật thì máy chủ ảnh thấy IP; Triage và Reels hiện ảnh khi đang xem (không theo công tắc danh sách, như RSS); không tải sẵn offline (đã sẵn có `continue` cho Nostr); chưa kiểm máy thật hay ảnh thật. `doc/feature.md`: thêm dòng FRONT-02aa sau dòng FRONT-02z. Task gốc FRONT-02 (giữ trong `todo/`): mục "Cập nhật" ghi FRONT-02aa, bỏ "ảnh" khỏi phần còn lại nếu đúng. Spec: nối "Điều chỉnh khi thực thi".

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02aa): report the cover image for Nostr notes

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review
- **Spec coverage:** hàm thuần, https, đuôi, query, giới hạn độ dài, lấy từ chữ gốc, `toArticles` điền `img` → Task 1; mutation → Task 2; JVM, phạm vi một tệp production, báo cáo nói thẳng riêng tư → Task 3; không tải sẵn offline: đã có `continue` cho Nostr (Điều chỉnh, báo cáo).
- **Placeholder scan:** không có.
- **Type consistency:** `coverImageUrl(String): String?`, `MAX_COVER_URL_LENGTH: Int`, `COVER_IMAGE_URL`, `COVER_IMAGE_EXTENSIONS` dùng đồng nhất ở test, mã và mẫu đột biến.
