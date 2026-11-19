# FRONT-02ab — Test máy thật ảnh bìa Nostr theo công tắc ảnh: kế hoạch thực thi

> **Cho người thực thi:** dùng superpowers:executing-plans (inline). Các bước dùng ô `- [ ]`.

**Mục tiêu:** chứng minh trên Tecno KJ7 rằng bài Nostr có link ảnh https làm bộ tải ảnh Coil hỏi đúng địa chỉ đó khi công tắc ảnh danh sách BẬT, và không hỏi khi TẮT, không có link, hoặc link `http`.

**Kiến trúc:** một lớp test `androidTest` (Compose thuần, `createComposeRule`). Bộ tải ảnh Coil được thay bằng một bộ tải có `Interceptor` ghi lại `request.data` và trả ảnh giả, nên không có mạng. Bài đi qua `NostrFeedMapper.toArticles` thật và `ArticleItem` thật.

**Công nghệ:** Kotlin, AndroidX Test + `ui-test-junit4`, Coil 2.1.0 (đã đọc chữ ký bằng `javap`), `am instrument` trên Tecno.

**Spec:** `docs/superpowers/specs/2026-11-19-front-02ab-nostr-cover-image-device-test-design.md` (commit `db95a7f5`).

## Global Constraints
- Nhánh `dev`; không worktree, không push, không `.idea`. **Chỉ thiết bị Tecno KJ7 `115333744A005844`** (khóa cho cả phiên; Pixel không dùng). Không `connected*`, không tọa độ, không `setRotation`, không MockK, không mạng ngoài.
- **Không đổi mã production** (chỉ đổi tạm khi chạy đột biến, luôn khôi phục + `cmp`). Không đổi UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới. Không ghi Room/DataStore.
- Mỗi lần khởi chạy thiết bị: `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng**; sao lưu/khôi phục byte-exact (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng; gỡ `com.mckimquyen.reader.test` sau vòng. Không gõ lại, không `sleep`; không tăng timeout để cứu lượt đỏ. Không đọc/in khóa Gemini.
- Quảng cáo toàn màn hình: owner bảo bỏ qua; lượt đỏ do `AdActivity` chỉ ghi số thật, không điều tra, mutation đỏ do quảng cáo thì chạy lại nhãn đó (tối đa 3 lần, ghi thẳng).
- R5: không magic number (hằng có tên), test kèm. R2: cập nhật `doc/feature.md`. Gradle chạy NỀN, chờ `.exit`; lệnh chờ quá 595 giây thì tiếp tục bằng vòng chờ riêng. zsh: không `echo ====`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`).
- Nói thẳng: chỉ chứng minh "có hỏi đúng địa chỉ", **không** chứng minh ảnh hiện trên màn hình hay mạng thật.

## Điều chỉnh so với spec (đã đọc API thật, 2026-11-19)
1. **`Coil.reset()` không public** (chỉ `reset$coil_singleton_release`). Thay vào: `@Before` lưu `Coil.imageLoader(context)` (bộ tải thật của app), `@After` trả lại bằng `Coil.setImageLoader(saved)`.
2. **Không có `LocalImageLoader`** trong Coil 2.1.0; `rememberAsyncImagePainter` lấy `LocalContext.current.imageLoader`, tức bộ tải singleton: đặt bằng `Coil.setImageLoader` là đủ.
3. **Khẳng định theo địa chỉ khác nhau (`distinct()`), không theo số yêu cầu:** Coil Compose có thể gửi lại cùng một yêu cầu khi bố cục đo xong; điều cần chứng minh là **địa chỉ nào được hỏi**.
4. **Chờ có hằng riêng** `WAIT_MS = 15_000` cho `waitUntil` (mặc định của Compose chỉ 1 giây): đây là test mới nên không phải "tăng timeout để cứu lượt đỏ".
5. `Interceptor.intercept` chạy **trước** mapper nên `chain.request.data` là chuỗi gốc. `SuccessResult(drawable, request, dataSource)` có tham số mặc định (xác nhận bằng `javap`).
6. **Cứu chuyện "test xanh sẵn":** hành vi đã có từ FRONT-02aa nên test **không thể đỏ trước khi viết mã**; bằng chứng test có răng là bốn đột biến trên máy (Task 4). Nói thẳng trong báo cáo.
7. `FeedIcon` của `ArticleItem` có thể tải ảnh nếu `feed.icon` khác null: test để `icon = null` nên không có yêu cầu thừa.

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN, kiểm tự động ở Task 4)
Test (lớp `NostrCoverImageDeviceTest`): **D1** `switchOn_aNoteWithAnHttpsImageLink_asksForThatImage`, **D2** `switchOff_aNoteWithAnHttpsImageLink_asksForNothing`, **D3** `switchOn_aNoteWithoutALink_asksForNothing`, **D4** `switchOn_anHttpImageLink_asksForNothing`, **D5** `switchOn_onlyTheFirstOfTwoImageLinks_isAsked`.

| Đột biến | Đổi gì | Test đỏ mong đợi |
|---|---|---|
| base | không | không |
| a1 | mapper không đặt `img` (`img = coverImageUrl(event.content),` bỏ) | D1, D5 |
| b1 | `ArticleItem` bỏ `&& articleListImage.value` | D2 |
| c1 | mapper nhận `http` (`Regex("""https?://…`) | D4 |
| d1 | mapper lấy link cuối (`lastOrNull`) | D5 |

Kiểu so: **đúng**. D3 xanh ở mọi đột biến (không có link nào để hỏi). D1 xanh ở b1, c1, d1 (một link https, công tắc bật).

## Cấu trúc file
- Tạo: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/NostrCoverImageDeviceTest.kt`.
- Tạm (không commit): `/tmp/aab_run.sh`, `/tmp/aab_mut.sh`, `/tmp/aab_batch.sh`, `/tmp/aab_check.py`.
- Tài liệu: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02ab_nostr-cover-image-device-test_DONE.md`, spec (mục "Điều chỉnh khi thực thi").

---

### Task 1: Viết test máy và biên dịch

**Files:** Tạo `NostrCoverImageDeviceTest.kt`.

- [ ] **Step 1: Ghi test**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/NostrCoverImageDeviceTest.kt <<'EOF'
package com.mckimquyen.reader.ui.page.home.flow

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.pref.FlowArticleListImagePref
import com.mckimquyen.reader.infrastructure.pref.LocalFlowArticleListImage
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Nostr note with an image link in its text, through the REAL mapper and the REAL list row, with the image loader replaced by one that
 * only records which address it was asked for (no network, no disk). It proves the list asks for the cover image exactly when the user's
 * image switch is ON; it does not prove the picture is drawn, nor that a real server answers.
 */
@RunWith(AndroidJUnit4::class)
class NostrCoverImageDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val asked = CopyOnWriteArrayList<Any?>()
    private var appLoader: ImageLoader? = null

    @Before
    fun useARecordingImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appLoader = Coil.imageLoader(context)
        Coil.setImageLoader(ImageLoader.Builder(context).components { add(RecordingInterceptor(asked)) }.build())
    }

    @After
    fun giveTheAppImageLoaderBack() {
        appLoader?.let { Coil.setImageLoader(it) }
    }

    @Test
    fun switchOn_aNoteWithAnHttpsImageLink_asksForThatImage() {
        showRow("look $FIRST_IMAGE", FlowArticleListImagePref.ON)
        waitForAnImageRequest()
        compose.waitForIdle()
        assertEquals(listOf(FIRST_IMAGE), asked.distinct())
    }

    @Test
    fun switchOff_aNoteWithAnHttpsImageLink_asksForNothing() {
        showRow("look $FIRST_IMAGE", FlowArticleListImagePref.OFF)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_aNoteWithoutALink_asksForNothing() {
        showRow("only words, no link", FlowArticleListImagePref.ON)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_anHttpImageLink_asksForNothing() {
        showRow("look $HTTP_IMAGE", FlowArticleListImagePref.ON)
        compose.waitForIdle()
        assertTrue("asked for ${asked.distinct()}", asked.isEmpty())
    }

    @Test
    fun switchOn_onlyTheFirstOfTwoImageLinks_isAsked() {
        showRow("one $FIRST_IMAGE two $SECOND_IMAGE", FlowArticleListImagePref.ON)
        waitForAnImageRequest()
        compose.waitForIdle()
        assertEquals(listOf(FIRST_IMAGE), asked.distinct())
    }

    private fun waitForAnImageRequest() = compose.waitUntil(WAIT_MS) { asked.isNotEmpty() }

    private fun showRow(noteText: String, switch: FlowArticleListImagePref) {
        val event = requireNotNull(
            NostrEvent.parse(
                JSONObject().apply {
                    put("id", NOTE_ID); put("pubkey", AUTHOR); put("created_at", CREATED_AT_SECONDS); put("kind", 1)
                    put("tags", JSONArray()); put("content", noteText); put("sig", "0".repeat(SIG_HEX_LENGTH))
                }.toString(),
            ),
        )
        val article = NostrFeedMapper.toArticles(listOf(event), FEED_ID, ACCOUNT_ID, NOW_MILLIS).single()
        val feed = Feed(id = FEED_ID, name = "Nostr test", url = "nostr:test", groupId = "group", accountId = ACCOUNT_ID)
        compose.setContent {
            CompositionLocalProvider(LocalFlowArticleListImage provides switch) {
                ArticleItem(ArticleWithFeed(article = article, feed = feed))
            }
        }
    }

    private class RecordingInterceptor(private val asked: MutableList<Any?>) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            asked += chain.request.data
            return SuccessResult(drawable = ColorDrawable(Color.RED), request = chain.request, dataSource = DataSource.MEMORY_CACHE)
        }
    }

    private companion object {
        const val WAIT_MS = 15_000L
        const val FIRST_IMAGE = "https://img.example.test/one.jpg"
        const val SECOND_IMAGE = "https://img.example.test/two.png"
        const val HTTP_IMAGE = "http://img.example.test/plain.jpg"
        const val FEED_ID = "nostr-feed"
        const val ACCOUNT_ID = 1
        const val AUTHOR = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
        const val NOTE_ID = "0000000000000000000000000000000000000000000000000000000000000001"
        const val CREATED_AT_SECONDS = 1_700_000_000L
        const val NOW_MILLIS = 1_800_000_000_000L
        const val SIG_HEX_LENGTH = 128
    }
}
EOF
rm -f /tmp/aab-build.exit; ( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/aab-build.log 2>&1; echo $? > /tmp/aab-build.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aab-build.exit && break; sleep 5; done; cat /tmp/aab-build.exit; grep -E "^e: " /tmp/aab-build.log | cut -c1-220 | head -5
```
Expected: `0` (biên dịch được). Có lỗi biên dịch: đọc, sửa test (không đổi production).

- [ ] **Step 2: Commit test (chưa chạy máy)**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/NostrCoverImageDeviceTest.kt
git commit -m "test(front-02ab): the Nostr cover image is asked for only when the image switch is on (device test, not yet run)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline -1
```

---

### Task 2: Một lượt thử trên Tecno

- [ ] **Step 1: Dựng `/tmp/aab_run.sh` từ `/tmp/kj7_run.sh` (lớp ở gói `flow`)**

```bash
sed -e 's#CLS=com.mckimquyen.reader.ui.page.home.feed.subs.$1#CLS=com.mckimquyen.reader.ui.page.home.flow.$1#' /tmp/kj7_run.sh > /tmp/aab_run.sh
grep -c "home.flow" /tmp/aab_run.sh
adb devices
```
Expected: `1`; thấy cả Tecno và Pixel, **chỉ dùng Tecno** (script đã ghim serial).

- [ ] **Step 2: Cổng riêng (lệnh riêng)**

```bash
zsh /tmp/kj7_gate.sh | head -2
```
Expected: `GATE OK`. Khác: dừng, báo owner (màn hình khóa thì nhờ owner mở khóa).

- [ ] **Step 3: Chạy 1 lượt (nền, chờ ALLDONE)**

```bash
rm -f /tmp/aab-run.log; ( zsh /tmp/aab_run.sh NostrCoverImageDeviceTest 1 > /tmp/aab-run.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 100); do grep -q ALLDONE /tmp/aab-run.log 2>/dev/null && break; sleep 5; done; cat /tmp/aab-run.log | cut -c1-200
```
Expected: `run1: OK (5 tests)`, `datastore: now=… before=…` trùng, `rotation: 1 0`, `GATE OK`. Đỏ: đọc `/tmp/kj7-run-NostrCoverImageDeviceTest-1.log`; lỗi trong mã test thì sửa test (quay Task 1); `AdActivity` thì ghi số, chạy lại.

---

### Task 3: 20 lượt trên Tecno

- [ ] **Step 1: Chạy 20 lượt (nền)**

```bash
rm -f /tmp/aab-run20.log; ( zsh /tmp/aab_run.sh NostrCoverImageDeviceTest 20 > /tmp/aab-run20.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aab-run20.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aab-run20.log; tail -5 /tmp/aab-run20.log | cut -c1-200
```
Chưa `ALLDONE`: chạy lại **chỉ** vòng chờ (nhiều lần nếu cần).

- [ ] **Step 2: Đếm**

```bash
grep -c "OK (5 tests)" /tmp/aab-run20.log; grep -E "^run[0-9]+:" /tmp/aab-run20.log | grep -v "OK (5 tests)" | cut -c1-200
for f in /tmp/kj7-run-NostrCoverImageDeviceTest-*.log; do grep -l "AdActivity" "$f"; done 2>/dev/null | head
```
Ghi **số thật**: lượt xanh, lượt đỏ và nguyên nhân từng lượt (đọc log). Từ 2 lượt đỏ **không do quảng cáo**: dừng, điều tra theo systematic-debugging, hỏi owner.

---

### Task 4: Đột biến trên máy (4), so sánh tự động

- [ ] **Step 1: Kiểm mẫu duy nhất**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/"
mp = open(J + "infrastructure/social/nostr/NostrFeedMapper.kt", encoding="utf-8").read()
ai = open(J + "ui/page/home/flow/ArticleItem.kt", encoding="utf-8").read()
print({
 "a1": mp.count("img = coverImageUrl(event.content),"),
 "b1": ai.count("if (articleWithFeed.article.img != null && articleListImage.value) {"),
 "c1": mp.count('Regex("""https://\\S+?'),
 "d1": mp.count(".firstOrNull { it.length <= MAX_COVER_URL_LENGTH }"),
})
EOF
```
Expected: mọi nhãn `1`.

- [ ] **Step 2: Dựng script từ mẫu 02r**

```bash
sed -e 's#BlueskyGuardsDeviceTest#NostrCoverImageDeviceTest#g' -e 's#ui.page.home.feed.subs.NostrCoverImageDeviceTest#ui.page.home.flow.NostrCoverImageDeviceTest#' -e 's#OK (12 tests)#OK (5 tests)#' -e 's#kj7-r02-#kj7-aab-#g' -e 's#ok12#ok5#g' /tmp/kj7_mut_r02.sh > /tmp/aab_mut.sh; chmod +x /tmp/aab_mut.sh
grep -n "TEST=\|OK (5\|home.flow" /tmp/aab_mut.sh | cut -c1-140
cat > /tmp/aab_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
S=115333744A005844; APP=com.mckimquyen.reader
J=app/src/main/java/com/mckimquyen/reader
MP=$J/infrastructure/social/nostr/NostrFeedMapper.kt; AI=$J/ui/page/home/flow/ArticleItem.kt
cp -p $MP /tmp/kj7-aab-orig-mp.kt; cp -p $AI /tmp/kj7-aab-orig-ai.kt
zsh /tmp/kj7_gate.sh > /tmp/kj7-aab-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/kj7-aab-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/kj7_backup.py > /tmp/kj7-aab-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
R=$(cat /tmp/kj7-root); BEFORE=$(shasum $R/kj7/settings.preferences_pb | cut -c1-40)
M=/tmp/aab_mut.sh
zsh $M base
zsh $M a1 $MP 'img = coverImageUrl(event.content),' ''
zsh $M b1 $AI 'if (articleWithFeed.article.img != null && articleListImage.value) {' 'if (articleWithFeed.article.img != null) {'
zsh $M c1 $MP 'Regex("""https://\S+?' 'Regex("""https?://\S+?'
zsh $M d1 $MP '.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }' '.lastOrNull { it.length <= MAX_COVER_URL_LENGTH }'
zsh $M base
cmp -s /tmp/kj7-aab-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
cmp -s /tmp/kj7-aab-orig-ai.kt $AI && echo "final ai: identical" || echo "final ai: DIFFERENT"
echo "uncommitted production diff: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/kj7_restore.py > /tmp/kj7-aab-batch-restore.log 2>&1; echo "restore_exit=$?"; grep -E "DIFFERENT|RESTORE" /tmp/kj7-aab-batch-restore.log
echo "datastore: now=$(adb -s $S exec-out run-as $APP cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) before=$BEFORE"
adb -s $S uninstall $APP.test
echo "rotation: $(adb -s $S shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s $S shell settings get system user_rotation | tr -d '\r')"
zsh /tmp/kj7_gate.sh | head -1
echo ALLDONE
EOF
cat > /tmp/aab_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
N = {"D1": "switchOn_aNoteWithAnHttpsImageLink_asksForThatImage", "D2": "switchOff_aNoteWithAnHttpsImageLink_asksForNothing",
     "D3": "switchOn_aNoteWithoutALink_asksForNothing", "D4": "switchOn_anHttpImageLink_asksForNothing",
     "D5": "switchOn_onlyTheFirstOfTwoImageLinks_isAsked"}
def S(*ks): return {N[k] for k in ks}
EXPECTED = {"a1": S("D1", "D5"), "b1": S("D2"), "c1": S("D4"), "d1": S("D5")}
bad = 0
for label, want in EXPECTED.items():
    try: t = open(f"/tmp/kj7-aab-mut-{label}-run.log", encoding="utf-8").read()
    except FileNotFoundError: print("BAD  ", label, "no log"); bad += 1; continue
    got = set(re.findall(r"^\d+\) ([A-Za-z0-9_]+)", t, re.M))
    ok = got == want; bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got), "AdActivity" if "AdActivity" in t else "")
t = open("/tmp/kj7-aab-mut-base-run.log", encoding="utf-8").read()
print("base:", "GREEN" if "OK (5 tests)" in t else "NOT GREEN"); bad += 0 if "OK (5 tests)" in t else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```
Expected: dòng `TEST=` trỏ `home.flow.NostrCoverImageDeviceTest`, có `OK (5 tests)`.

- [ ] **Step 3: Cổng riêng, rồi chạy batch nền**

```bash
zsh /tmp/kj7_gate.sh | head -1
```
Expected `GATE OK`. Rồi (lệnh riêng):

```bash
rm -f /tmp/aab-batch.log; ( zsh /tmp/aab_batch.sh > /tmp/aab-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aab-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aab-batch.log; cut -c1-200 /tmp/aab-batch.log | tail -4
```
Không sửa hai tệp production cho tới `ALLDONE`. Chưa `ALLDONE`: chạy lại **chỉ** vòng chờ.

- [ ] **Step 4: Đối chiếu tự động**

```bash
python3 /tmp/aab_check.py
```
Expected: `ALL-MATCH`; cuối batch: hai dòng `final … identical`, `uncommitted production diff: 0 lines`, `restore_exit=0`, băm `now=` bằng `before=`, `rotation: 1 0`, `GATE OK`. Lệch: nhãn có `AdActivity` thì chạy lại nhãn đó (script `/tmp/aab_mut.sh <nhãn> …`, tối đa 3 lần, ghi thẳng); lệch thật thì đọc lý do rồi (a) sửa dự đoán, (b) test yếu: thêm khẳng định, (c) đột biến tương đương: đổi thành đột biến thật.

---

### Task 5: JVM đầy đủ, tài liệu, commit

- [ ] **Step 1: JVM full (nền, chờ `.exit`)**

```bash
rm -f /tmp/aab-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/aab-jvm.log 2>&1; echo $? > /tmp/aab-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/aab-jvm.exit && break; sleep 5; done; cat /tmp/aab-jvm.exit 2>/dev/null || echo "still running"
```
Rồi đếm test bằng đoạn python đọc `app/build/test-results/testDevDebugUnitTest/*.xml`. Số trước **3.874** (không đổi vì chỉ thêm test máy; chỉ để đối chiếu).

- [ ] **Step 2: Phạm vi**

```bash
git status --short; git diff --name-only db95a7f5 HEAD -- app
```
Expected: `app` chỉ có `NostrCoverImageDeviceTest.kt`; không tệp production.

- [ ] **Step 3: Báo cáo, `feature.md`, task gốc, spec**

Báo cáo `doc/task/done/FRONT-02ab_nostr-cover-image-device-test_DONE.md` (tiếng Việt đơn giản): việc đã làm; số lượt và lượt đỏ + nguyên nhân từng lượt (số **thật**, kể cả `AdActivity`); bảng đột biến **đo được**; băm DataStore trước/sau; xoay sau mỗi lượt; **nói thẳng:** test không thể đỏ trước khi viết mã (hành vi đã có từ FRONT-02aa), bằng chứng có răng là bốn đột biến; chỉ chứng minh "có hỏi đúng địa chỉ", không chứng minh ảnh hiện hay mạng thật; bộ tải ảnh bị thay trong test; Tecno một máy; lint không quét `androidTest`. `doc/feature.md`: thêm dòng FRONT-02ab sau dòng FRONT-02aa. Task gốc FRONT-02 (giữ trong `todo/`): mục "Cập nhật" ghi FRONT-02ab. Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan cộng điều chỉnh phát sinh).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02ab): report the device test of the Nostr cover image

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review
- **Spec coverage:** bộ tải ghi lại + 5 test (Task 1); thiết bị, cổng, sao lưu, 20 lượt (Task 2, 3); đột biến a/b/c (+d) với dự đoán (Task 4); JVM đo lại, phạm vi không đổi production, báo cáo nói thẳng giới hạn (Task 5); điều chỉnh `Coil.reset` và `distinct()` ghi ở "Điều chỉnh so với spec".
- **Placeholder scan:** không có.
- **Type consistency:** `RecordingInterceptor(asked)`, `FIRST_IMAGE`, `WAIT_MS`, `showRow`, `waitForAnImageRequest` dùng đồng nhất; tên D1 đến D5 khớp `aab_check.py`.
