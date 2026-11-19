# FRONT-02ae — Test máy thật bấm link trong bài Nostr (đến Reader): kế hoạch thực thi

> **Cho người thực thi:** dùng superpowers:executing-plans (inline). Các bước dùng ô `- [ ]`.

**Mục tiêu:** chứng minh trên Tecno KJ7 rằng link https trong bài Nostr (và dòng `↩ link` của bài trả lời) bấm được ở `Reader` thật: chạm vào link thì `onLinkClick` nhận đúng địa chỉ; chạm vào link `http` (chữ thường) thì không gọi.

**Kiến trúc:** một lớp test `androidTest` (`createAndroidComposeRule<ComponentActivity>`, không Hilt). Bài dựng bằng `NostrEvent` hình thức, qua `NostrFeedMapper.toArticles`; `LazyColumn { Reader(link = article.link, content = article.rawDescription, onLinkClick = …) }`. Vị trí chạm lấy từ `TextLayoutResult` thật của nút chữ (`SemanticsActions.GetTextLayoutResult`, cách repo đã dùng ở `FeedItemDeviceTest`), không đoán tọa độ.

**Công nghệ:** Kotlin, AndroidX Test + `ui-test-junit4` 1.7.8, `am instrument` trên Tecno.

**Spec:** `docs/superpowers/specs/2026-11-19-front-02ae-nostr-link-tap-device-test-design.md` (commit `4e46f385`).

## Global Constraints
- Nhánh `dev`; không worktree, không push, không `.idea`. **Chỉ Tecno KJ7 `115333744A005844`** (Pixel không dùng). Không `connected*`, không tọa độ đoán, không `setRotation`, không MockK, không mạng, không mở trình duyệt (`openURL` không được gọi).
- **Không đổi mã production** (chỉ đổi tạm khi chạy đột biến, luôn khôi phục + `cmp`). Không đổi UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới. Không ghi Room/DataStore.
- Mỗi lần khởi chạy thiết bị: `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng**; sao lưu/khôi phục byte-exact; băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng; gỡ `com.mckimquyen.reader.test` sau vòng. Không gõ lại, không `sleep`, không tăng timeout để cứu lượt đỏ. Không in khóa Gemini.
- Quảng cáo toàn màn hình: owner bảo bỏ qua; lượt đỏ do `AdActivity` chỉ ghi số thật; mutation đỏ vì quảng cáo thì chạy lại nhãn đó (tối đa 3 lần, ghi thẳng).
- R5: không magic number (hằng có tên), test kèm. R2: cập nhật `doc/feature.md`. Gradle chạy NỀN, chờ `.exit`; quá 595 giây thì vòng chờ riêng. zsh: không `echo ====`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`); Kotlin có `"` ghi bằng heredoc `'EOF'`.
- Nói thẳng: chỉ chứng minh đến `onLinkClick`; không chứng minh `openURL`/trình duyệt/màn đọc thật; test xanh sẵn (hành vi đã có), răng là các đột biến.

## Điều chỉnh so với spec (đã đọc API và test thật)
1. **Lấy vị trí chạm:** `FeedItemDeviceTest` đã dùng `config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action` rồi `invoke(list)`; test này làm giống, rồi `getBoundingBox(offset)` của ký tự đầu của link trong **chữ hiển thị** (không phải HTML). Hộp trả về theo pixel của nút chữ; chạm bằng `performTouchInput { click(box.center) }`. Nếu nút không lộ `GetTextLayoutResult` thì **dừng và hỏi owner** (không đổi thiết kế một mình).
2. **Nút chữ:** `ClickableText` nằm trong một `item` của `LazyColumn`; mỗi bài là một đoạn nên có đúng một nút `hasText(…, substring = true)` có hành động bố cục chữ. Test tìm bằng chữ đứng trước link.
3. **Khẳng định "không gọi":** cặp với ca có gọi (cùng lớp) để biết cơ chế chạm hoạt động; chạm vào vị trí của `http://…` rơi vào chữ thường (không chú thích `URL`) nên `onLinkClick` không được gọi.
4. **Reader có `Log.i("RLog", ...)`** (production, không đổi). `AppTheme(useDarkTheme = true)` như mẫu hiện có.
5. Các hằng link của test là chuỗi cố định, có tên.

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN, kiểm tự động ở Task 3)
Test (lớp `NostrLinkTapDeviceTest`): **T1** `tappingAnHttpsLink_givesTheReaderThatAddress`, **T2** `tappingTheReplyLine_givesTheLinkOfTheNoteItAnswers`, **T3** `tappingAnHttpLink_givesNothing`, **T4** `aTrailingDot_isNotPartOfTheTappedAddress`, **T5** `tappingTheSecondOfTwoLinks_givesTheSecondAddress`.

| Đột biến | Đổi gì | Test đỏ mong đợi |
|---|---|---|
| base | không | không |
| a1 | `escapeToHtml` không bọc link thường thành thẻ `a` (chỉ chữ thường) | T1, T3, T4, T5 |
| b1 | bỏ dòng `↩` (`val body = html`) | T2 |
| c1 | link nhận cả `http` (`Regex("""https?://\S+""")`) | T3 |
| d1 | dòng `↩` dùng id của chính bài (`event.id`) | T2 |

Kiểu so: **đúng**. T3 đỏ ở a1 vì nửa sau của T3 cần link đầu bấm được (a1 biến nó thành chữ thường); T3 xanh ở b1, d1; T1, T4, T5 xanh ở b1, c1, d1. T2 xanh ở a1 vì dòng `↩` dựng bằng `replyLine` (không qua nhánh bọc link thường) và vẫn là thẻ `a`. Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng, ghi trong báo cáo.

## Cấu trúc file
- Tạo: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader/NostrLinkTapDeviceTest.kt`.
- Tạm (không commit): `/tmp/aae_run.sh`, `/tmp/aae_mut.sh`, `/tmp/aae_batch.sh`, `/tmp/aae_check.py`.
- Tài liệu: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02ae_nostr-link-tap-device-test_DONE.md`, spec (mục "Điều chỉnh khi thực thi").

---

### Task 1: Viết test máy và biên dịch

**Files:** Tạo `NostrLinkTapDeviceTest.kt`.

- [ ] **Step 1: Ghi test (đã biên dịch được khi lập plan; import `getOrNull` và `click` là bắt buộc)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
mkdir -p app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader
cat > app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader/NostrLinkTapDeviceTest.kt <<'EOF'
package com.mckimquyen.reader.ui.component.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Nostr note through the REAL mapper and the REAL Reader (Jsoup and Compose): a tap on a link in the text gives the reader that
 * address, and a tap on plain text gives nothing. The tap goes where the text layout says the link is, never to a guessed point.
 * It stops at the callback: the browser is not opened (openURL is not called) and nothing is downloaded.
 */
class NostrLinkTapDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = CopyOnWriteArrayList<String>()

    @Test
    fun tappingAnHttpsLink_givesTheReaderThatAddress() {
        show(note(TEXT_BEFORE + " " + FIRST_LINK))
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun tappingTheReplyLine_givesTheLinkOfTheNoteItAnswers() {
        val target = ID_ANSWERED
        show(note("An answer", listOf("e", target, "", "reply")))
        val expected = NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(target))
        tapOnTheTextAt("An answer", expected)
        assertEquals(listOf(expected), opened.toList())
    }

    @Test
    fun tappingAnHttpLink_givesNothing() {
        // The first link works, so a tap that reaches nothing proves the second one is only text, not a dead tap.
        show(note("$TEXT_BEFORE $FIRST_LINK and $HTTP_LINK"))
        tapOnTheTextAt("$TEXT_BEFORE $FIRST_LINK and ", HTTP_LINK)
        assertTrue("opened ${opened.toList()}", opened.isEmpty())
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun aTrailingDot_isNotPartOfTheTappedAddress() {
        show(note("$TEXT_BEFORE $FIRST_LINK."))
        tapOnTheTextAt(TEXT_BEFORE, FIRST_LINK)
        assertEquals(listOf(FIRST_LINK), opened.toList())
    }

    @Test
    fun tappingTheSecondOfTwoLinks_givesTheSecondAddress() {
        show(note("$FIRST_LINK $TEXT_BEFORE $SECOND_LINK"))
        tapOnTheTextAt("$FIRST_LINK $TEXT_BEFORE ", SECOND_LINK)
        assertEquals(listOf(SECOND_LINK), opened.toList())
    }

    private fun note(content: String, vararg tags: List<String>): NostrEvent = requireNotNull(
        NostrEvent.parse(
            JSONObject().apply {
                put("id", NOTE_ID); put("pubkey", AUTHOR); put("created_at", CREATED_AT_SECONDS); put("kind", 1)
                put("tags", JSONArray(tags.map { JSONArray(it) })); put("content", content); put("sig", "0".repeat(SIG_HEX_LENGTH))
            }.toString(),
        ),
    )

    private fun show(event: NostrEvent) {
        val article = NostrFeedMapper.toArticles(listOf(event), "feed-1", ACCOUNT_ID, NOW_MILLIS).single()
        compose.setContent {
            AppTheme(useDarkTheme = true) {
                LazyColumn(Modifier.fillMaxSize()) {
                    Reader(link = article.link, content = article.rawDescription, onLinkClick = { opened += it })
                }
            }
        }
        compose.waitForIdle()
    }

    /** Taps the middle of the first character of [target] in the one text block that starts with [before]. */
    private fun tapOnTheTextAt(before: String, target: String) {
        val block = compose.onNode(hasText(before, substring = true) and hasLayout())
        val action = block.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the text block must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        val layout = layouts.single()
        val start = layout.layoutInput.text.text.indexOf(target)
        assertTrue("'$target' is not in the shown text '${layout.layoutInput.text.text}'", start >= 0)
        val box = layout.getBoundingBox(start)
        block.performTouchInput { click(box.center) }
        compose.waitForIdle()
    }

    private fun hasLayout() = SemanticsMatcher("has a text layout") { it.config.contains(SemanticsActions.GetTextLayoutResult) }

    private companion object {
        const val TEXT_BEFORE = "Look at this photo"
        const val FIRST_LINK = "https://img.example.test/one.jpg"
        const val SECOND_LINK = "https://img.example.test/two.png"
        const val HTTP_LINK = "http://img.example.test/plain.jpg"
        const val ID_ANSWERED = "00000000000000000000000000000000000000000000000000000000000000aa"
        const val NOTE_ID = "0000000000000000000000000000000000000000000000000000000000000001"
        const val AUTHOR = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        const val ACCOUNT_ID = 1
        const val CREATED_AT_SECONDS = 1_700_000_000L
        const val NOW_MILLIS = 1_800_000_000_000L
        const val SIG_HEX_LENGTH = 128
    }
}
EOF
rm -f /tmp/aae-build.exit; ( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/aae-build.log 2>&1; echo $? > /tmp/aae-build.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aae-build.exit && break; sleep 5; done; cat /tmp/aae-build.exit; grep -E "^e: " /tmp/aae-build.log | sed -E 's#file:///.*/##' | cut -c1-200 | head -5
```
Expected: `0`. Lỗi biên dịch: đọc, sửa test (không đổi production).

- [ ] **Step 2: Commit test (chưa chạy máy)**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader/NostrLinkTapDeviceTest.kt docs/superpowers/plans/2026-11-19-front-02ae-nostr-link-tap-device-test.md
git commit -m "test(front-02ae): a tap on a link in a Nostr note reaches the Reader callback (device test, not yet run)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline -1
```

---

### Task 2: Một lượt thử rồi 20 lượt trên Tecno

- [ ] **Step 1: Dựng `/tmp/aae_run.sh` (lớp ở gói `ui.component.reader`)**

```bash
sed -e 's#CLS=com.mckimquyen.reader.ui.page.home.feed.subs.$1#CLS=com.mckimquyen.reader.ui.component.reader.$1#' /tmp/kj7_run.sh > /tmp/aae_run.sh
grep -c "ui.component.reader" /tmp/aae_run.sh
adb devices
```
Expected: `1`; thấy cả Tecno và Pixel, **chỉ dùng Tecno** (script ghim serial).

- [ ] **Step 2: Cổng riêng (lệnh riêng)**

```bash
zsh /tmp/kj7_gate.sh | head -2
```
Expected: `GATE OK`. Khác: dừng, báo owner (khóa màn hình thì nhờ owner mở khóa).

- [ ] **Step 3: Chạy 1 lượt (nền, chờ ALLDONE)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/aae-run.log; ( zsh /tmp/aae_run.sh NostrLinkTapDeviceTest 1 > /tmp/aae-run.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 100); do grep -q ALLDONE /tmp/aae-run.log 2>/dev/null && break; sleep 5; done; cut -c1-200 /tmp/aae-run.log
```
Expected: `run1: OK (5 tests)`, `datastore: now=… before=…` trùng, `rotation: 1 0`, `GATE OK`. Đỏ: đọc `/tmp/kj7-run-NostrLinkTapDeviceTest-1.log`; lỗi trong mã test (vd nút chữ không lộ bố cục, link rơi ngoài hộp) thì sửa test (quay Task 1); `AdActivity` thì ghi số, chạy lại.

- [ ] **Step 4: Chạy 20 lượt (nền)**

```bash
rm -f /tmp/aae-run20.log; ( zsh /tmp/aae_run.sh NostrLinkTapDeviceTest 20 > /tmp/aae-run20.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aae-run20.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aae-run20.log; tail -3 /tmp/aae-run20.log | cut -c1-160
```
Chưa `ALLDONE`: chạy lại **chỉ** vòng chờ.

- [ ] **Step 5: Đếm**

```bash
echo "xanh: $(grep -c 'OK (5 tests)' /tmp/aae-run20.log)"; grep -E "^run[0-9]+:" /tmp/aae-run20.log | grep -v "OK (5 tests)" | cut -c1-200
grep -E "^datastore|^restore|RESTORE|LEAK|STOP" /tmp/aae-run20.log | cut -c1-160
for f in /tmp/kj7-run-NostrLinkTapDeviceTest-*.log; do grep -l AdActivity "$f"; done 2>/dev/null | wc -l
```
Ghi **số thật**: lượt xanh, lượt đỏ và nguyên nhân từng lượt (đọc log). Từ 2 lượt đỏ **không do quảng cáo**: dừng, điều tra theo systematic-debugging, hỏi owner.

---

### Task 3: Đột biến trên máy (4), so sánh tự động

- [ ] **Step 1: Kiểm mẫu duy nhất**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
mp = open("app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt", encoding="utf-8").read()
pats = {
 "a1": '            append("<a href=\\"").append(url).append("\\">").append(url).append("</a>")\n',
 "b1": "val body = html + replyLine(event)",
 "c1": 'Regex("""https://\\S+"""',
 "d1": "event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }",
}
print({k: mp.count(v) for k, v in pats.items()})
EOF
```
Expected: mọi nhãn `1`.

- [ ] **Step 2: Dựng script từ mẫu 02ab**

```bash
sed -e 's#NostrCoverImageDeviceTest#NostrLinkTapDeviceTest#g' -e 's#ui.page.home.flow.NostrLinkTapDeviceTest#ui.component.reader.NostrLinkTapDeviceTest#' -e 's#kj7-aab-#kj7-aae-#g' /tmp/aab_mut.sh > /tmp/aae_mut.sh; chmod +x /tmp/aae_mut.sh
grep -n "TEST=\|OK (5" /tmp/aae_mut.sh | cut -c1-140
sed -e 's#aab_mut#aae_mut#g' -e 's#kj7-aab-#kj7-aae-#g' /tmp/aab_batch.sh > /tmp/aae_batch.sh
python3 - <<'EOF'
# -*- coding: utf-8 -*-
p = "/tmp/aae_batch.sh"; s = open(p, encoding="utf-8").read()
a = s.index("zsh $M a1 "); b = s.index("zsh $M base\ncmp")
muts = (
 "zsh $M a1 $MP '            append(\"<a href=\\\"\").append(url).append(\"\\\">\").append(url).append(\"</a>\")' '            append(url)'\n"
 "zsh $M b1 $MP 'val body = html + replyLine(event)' 'val body = html'\n"
 "zsh $M c1 $MP 'Regex(\"\"\"https://\\S+\"\"\"' 'Regex(\"\"\"https?://\\S+\"\"\"'\n"
 "zsh $M d1 $MP 'event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }' 'NostrKeys.noteId(event.id)?.let { NJUMP_BASE + it }'\n")
s = s[:a] + muts + s[b:]
s = s.replace("MP=$J/infrastructure/social/nostr/NostrFeedMapper.kt; AI=$J/ui/page/home/flow/ArticleItem.kt", "MP=$J/infrastructure/social/nostr/NostrFeedMapper.kt")
s = s.replace("cp -p $MP /tmp/kj7-aae-orig-mp.kt; cp -p $AI /tmp/kj7-aae-orig-ai.kt", "cp -p $MP /tmp/kj7-aae-orig-mp.kt")
s = s.replace('cmp -s /tmp/kj7-aae-orig-ai.kt $AI && echo "final ai: identical" || echo "final ai: DIFFERENT"\n', "")
open(p, "w", encoding="utf-8").write(s)
EOF
grep -n 'zsh $M \(a1\|b1\|c1\|d1\)' /tmp/aae_batch.sh | cut -c1-120; grep -c "AI" /tmp/aae_batch.sh
cat > /tmp/aae_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
N = {"T1": "tappingAnHttpsLink_givesTheReaderThatAddress", "T2": "tappingTheReplyLine_givesTheLinkOfTheNoteItAnswers",
     "T3": "tappingAnHttpLink_givesNothing", "T4": "aTrailingDot_isNotPartOfTheTappedAddress",
     "T5": "tappingTheSecondOfTwoLinks_givesTheSecondAddress"}
def S(*ks): return {N[k] for k in ks}
EXPECTED = {"a1": S("T1", "T3", "T4", "T5"), "b1": S("T2"), "c1": S("T3"), "d1": S("T2")}
bad = 0
for label, want in EXPECTED.items():
    try: t = open(f"/tmp/kj7-aae-mut-{label}-run.log", encoding="utf-8").read()
    except FileNotFoundError: print("BAD  ", label, "no log"); bad += 1; continue
    got = set(re.findall(r"^\d+\) ([A-Za-z0-9_]+)", t, re.M))
    ok = got == want; bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got), "AdActivity" if "AdActivity" in t else "")
t = open("/tmp/kj7-aae-mut-base-run.log", encoding="utf-8").read()
print("base:", "GREEN" if "OK (5 tests)" in t else "NOT GREEN"); bad += 0 if "OK (5 tests)" in t else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```
Expected: `TEST=` trỏ `ui.component.reader.NostrLinkTapDeviceTest`, có `OK (5 tests)`, bốn dòng `zsh $M a1/b1/c1/d1`, `grep -c "AI"` bằng `0`.

- [ ] **Step 3: Cổng riêng, rồi chạy batch nền**

```bash
zsh /tmp/kj7_gate.sh | head -1
```
Expected `GATE OK`. Rồi (lệnh riêng):

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/aae-batch.log; ( zsh /tmp/aae_batch.sh > /tmp/aae-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aae-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aae-batch.log; cut -c1-200 /tmp/aae-batch.log | tail -4
```
Không sửa `NostrFeedMapper.kt` cho tới `ALLDONE`. Chưa `ALLDONE`: chạy lại **chỉ** vòng chờ.

- [ ] **Step 4: Đối chiếu tự động**

```bash
python3 /tmp/aae_check.py
```
Expected: `ALL-MATCH`; cuối batch: `final mp: identical`, `uncommitted production diff: 0 lines`, `restore_exit=0`, băm `now=` bằng `before=`, `rotation: 1 0`, `GATE OK`. Lệch: nhãn có `AdActivity` thì chạy lại nhãn đó (tối đa 3 lần, ghi thẳng); lệch thật thì đọc lý do rồi (a) sửa dự đoán, (b) test yếu thì thêm khẳng định, (c) đột biến tương đương thì đổi thành đột biến thật.

---

### Task 4: JVM đầy đủ, phạm vi, tài liệu, commit

**Files:** Sửa `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi"); tạo `doc/task/done/FRONT-02ae_nostr-link-tap-device-test_DONE.md`.

- [ ] **Step 1: JVM full (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/aae-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/aae-jvm.log 2>&1; echo $? > /tmp/aae-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/aae-jvm.exit && break; sleep 5; done; cat /tmp/aae-jvm.exit 2>/dev/null || echo "still running"
```
Rồi đếm test (đoạn python đọc `app/build/test-results/testDevDebugUnitTest/*.xml`, **một lệnh riêng**). Số trước **3.886** (không đổi vì chỉ thêm test máy).

- [ ] **Step 2: Phạm vi (hai lệnh riêng)**

```bash
git diff --name-only 4e46f385 HEAD -- app
```
```bash
git status --short
```
Expected: `app` chỉ có `NostrLinkTapDeviceTest.kt`; không tệp production.

- [ ] **Step 3: Báo cáo, `feature.md`, task gốc, spec**

Báo cáo `doc/task/done/FRONT-02ae_nostr-link-tap-device-test_DONE.md` (tiếng Việt đơn giản): việc đã làm; số lượt và lượt đỏ + nguyên nhân (số **thật**, kể cả `AdActivity`); bảng đột biến **đo được**; băm DataStore trước/sau; xoay sau mỗi lượt; **nói thẳng:** chỉ chứng minh đến `onLinkClick` (không `openURL`, không trình duyệt, không `ReadingPage`); test xanh sẵn, răng là bốn đột biến; vị trí chạm lấy từ bố cục chữ thật; một máy; lint không quét `androidTest`. `doc/feature.md`: thêm dòng FRONT-02ae sau dòng FRONT-02ad. Task gốc: mục "Cập nhật" ghi FRONT-02ae. Spec: nối "Điều chỉnh khi thực thi".

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02ae): report the device test of tapping a link in a Nostr note

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review
- **Spec coverage:** năm test (link trong chữ, dòng `↩`, `http`, dấu chấm cuối, hai link) và vị trí chạm từ bố cục thật → Task 1; cổng, một lượt, 20 lượt → Task 2; bốn đột biến với dự đoán suy luận → Task 3; JVM, phạm vi không đổi production, báo cáo nói thẳng → Task 4; không `openURL`, không trình duyệt → Global Constraints.
- **Placeholder scan:** không có.
- **Type consistency:** `NostrLinkTapDeviceTest`, `tapOnTheTextAt`, `show`, `note`, hằng `FIRST_LINK`/`SECOND_LINK`/`HTTP_LINK` dùng đồng nhất; tên T1 đến T5 khớp `aae_check.py`.
