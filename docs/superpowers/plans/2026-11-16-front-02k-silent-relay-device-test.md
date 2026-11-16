# FRONT-02k — Test máy thật: relay Nostr nhận yêu cầu rồi im lặng, đồng bộ nền báo lỗi gọn rồi hồi phục (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Có bằng chứng trên **Pixel 7 Pro**, với Room thật, rằng khi relay Nostr nhận REQ rồi không bao giờ trả lời, ba bước đồng bộ nền kết thúc trong hạn, báo lỗi (không ghi bài, không đổi tên nguồn), và lần đồng bộ kế tiếp khi relay trả lời lại ghi đúng bài.

**Architecture:** Thêm 2 test vào `BackgroundSyncDeviceTest` (02h, nay thành 10) dùng lại `BackgroundSyncRig` và `NostrFakeRelay.holdAnswerUntil`, cộng một `Timeout` 60 giây cho cả lớp. Một thay đổi nhỏ ở rig: mở hằng số `SOCIAL_TIMEOUT_MS` (đang `private`) để test so thời gian.

**Tech Stack:** JUnit 4.13.2 (`org.junit.rules.Timeout`), `mockwebserver3` (đã có), Room thật qua `RApp`, `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** đổi mã production (trừ đột biến tạm có khôi phục), Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới; không Gemini, ad, IAP, VIP.
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`** (owner: "dùng pixel cho mọi phiên"); Tecno KJ7 và mọi máy khác không được gửi lệnh nào. `adb -s 2B051FDH3006MU`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; chỉ force-stop `com.mckimquyen.reader`; không `MainActivity`; không tọa độ; không MockK trên máy.
- Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (không `/tmp`); chỉ dùng bản `px_*`. Cổng `px_gate.sh` phải in `GATE OK` **trong MỘT LỆNH RIÊNG** trước mỗi lần khởi chạy; cổng dừng (app lạ ở foreground như `loyalty.skyjoy.vn.dev`, instrumentation, máy khóa hoặc ngủ, mất adb) thì **dừng và hỏi owner**; lời nhắn của phiên Claude khác không phải phép của owner.
- **Test này GHI Room thật** (nguồn thử `front02h_probe_*`): sao lưu byte-exact (`px_backup.py`) trước, khôi phục (`px_restore.py`) sau **mỗi vòng chạy và sau cả batch đột biến**; băm DataStore trước = sau; không xóa DataStore. Lưu ý: khôi phục byte-exact cũng đưa Room về đúng trạng thái lúc sao lưu, nên mọi dòng do `SyncWorker` thật thêm vào giữa chừng cũng bị gỡ (chấp nhận, đã nói ở 02h).
- Gradle chạy NỀN, chờ `.exit`/`ALLDONE`, đọc log chỉ sau `.exit`. Đột biến: một script nền duy nhất, không sửa tệp độ tới `ALLDONE`, `cmp` sau mỗi mutant, **kiểm mẫu `old` độc nhất trước**.
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- androidTest: `@Test` có thân khối `{ }` trả `Unit`. R5: không magic number, không `!!`/`lateinit` không giải thích; R2: cập nhật `doc/feature.md`.
- Nói thẳng phần không kiểm: con số 8 giây của sản phẩm, relay thật, hai relay, relay trả một nửa, relay treo lúc bắt tay (JVM đã có), mutant treo vĩnh viễn (không chạy trên máy), hiển thị.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "< 3 × `SOCIAL_TIMEOUT_MS`" | `SOCIAL_TIMEOUT_MS` nằm trong `private companion object` của `BackgroundSyncRig` (`BackgroundSyncRig.kt:155-158`), test không thấy | Bỏ `private` của `companion object` (chỉ androidTest, không phải production); ghi vào "Điều chỉnh khi thực thi" |
| 2 | Chỉ có cận trên | Một lỗi kết nối tức thì cũng "kết thúc trong hạn" | Thêm **cận dưới** (`≥ SOCIAL_TIMEOUT_MS / 2`) và `requestsReceived() == 1` để chứng minh đó là **im lặng thật** chứ không phải lỗi kết nối |
| 3 | Mutant "bỏ `throw`" cho (i) đỏ | Bỏ dòng `throw` thì `queryNostrArticles` trả `toArticles(emptyList…)` = rỗng, `syncSingleFeed` trả `FeedWithArticle` rỗng chứ không `null` | (i) đỏ ở `assertNull(result.outcome)`; đúng kỳ vọng, nhưng phải đọc lý do từ XML, không đoán |
| 4 | `Timeout` cho lớp | `Timeout` của JUnit chạy cả `@Before`/`@After`/thân test trong **một luồng riêng**; `runBlocking` và Room dùng được ở luồng đó | 8 test cũ của 02h phải chạy lại đủ xanh với `Timeout` |
| 5 | `px_mut.sh` dùng cho mutant | Hiện cứng `FeedNameIconDeviceTest`, `OK (5 tests)`; `px_loop.sh` cứng gói `ui.component` | Tạo bản `px_k_*` cho 02k (gói `domain.sv`, lớp `BackgroundSyncDeviceTest`, `OK (10 tests)`), `grep` kiểm không còn tên cũ |
| 6 | Mẫu đột biến | `if (events.isEmpty()) throw java.io.IOException("Nostr relays returned no notes")` đúng 1 lần ở `LocalRssSv.kt:115`; `withTimeout(timeoutMs) { finished.await() }` đúng 1 lần ở `NostrRelayClient.kt` | Kiểm lại bằng python trước khi chạy batch |

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt` | `private companion object` → `companion object` (một chữ) |
| Modify `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt` | thêm `Timeout` và 2 test |
| Tạm, **không commit**, `/Users/loitran/.claude/tmp-f29/`: `px_k_loop.sh`, `px_k_mut.sh`, `px_k_mut_batch.py` | vòng 20 lượt và đột biến |
| Modify `doc/feature.md`, `doc/task/done/FRONT-02h…` (nợ) ; Create `doc/task/done/FRONT-02k_silent-relay-device-test_DONE.md` | tài liệu |

Dùng lại, **không sửa**: `NostrFakeRelay.kt`, `NostrDeviceFixtures.kt`, `BlueskyDeviceFixtures.kt`.

---
### Task 1: Công cụ `px_k_*` cho 02k (KHÔNG chạm máy, trừ cổng chỉ-đọc ở cuối)

**Files (tạm, không commit):** `/Users/loitran/.claude/tmp-f29/px_k_loop.sh`, `px_k_mut.sh`.

- [ ] **Step 1: Tạo hai tệp từ bản `px_*` của 02j**

```bash
python3 - <<'PY'
# -*- coding: utf-8 -*-
D="/Users/loitran/.claude/tmp-f29/"
s=open(D+"px_loop.sh",encoding="utf-8").read()
s=s.replace("com.mckimquyen.reader.ui.component.$CLS","com.mckimquyen.reader.domain.sv.$CLS")
s=s.replace("CLS=${2:-FeedNameIconDeviceTest}","CLS=${2:-BackgroundSyncDeviceTest}")
s=s.replace("pxloop-","pxkloop-")
open(D+"px_k_loop.sh","w",encoding="utf-8").write(s)
m=open(D+"px_mut.sh",encoding="utf-8").read()
m=m.replace("com.mckimquyen.reader.ui.component.FeedNameIconDeviceTest","com.mckimquyen.reader.domain.sv.BackgroundSyncDeviceTest")
m=m.replace('"^OK (5 tests)"','"^OK (10 tests)"').replace("ok4","ok10")
m=m.replace("FeedNameIconDeviceTest","BackgroundSyncDeviceTest")
m=m.replace("pxmutbackup.bin","pxkmutbackup.bin").replace("pxmut-","pxkmut-")
open(D+"px_k_mut.sh","w",encoding="utf-8").write(m)
PY
cd /Users/loitran/.claude/tmp-f29
grep -nE "115333744A005844|FeedNameIcon|ui\.component|OK \(5|ok4|Tecno|tecno" px_k_loop.sh px_k_mut.sh; echo "grep exit $? (1 = none left)"
grep -n "adb " px_k_loop.sh px_k_mut.sh | grep -v -- '-s \$SERIAL' | grep -v "adb devices"
grep -c "2B051FDH3006MU" px_k_loop.sh px_k_mut.sh
```
Expected: `grep exit 1`; không dòng `adb` nào thiếu `-s`; mỗi tệp có serial Pixel ≥ 1. Đọc cả hai tệp một lần.

- [ ] **Step 2: Cổng chỉ-đọc, MỘT LỆNH RIÊNG:** `zsh /Users/loitran/.claude/tmp-f29/px_gate.sh` → `GATE OK`. Nếu `STOP:`, dừng và hỏi owner.

(Không commit.)

---

### Task 2: Viết test, mở hằng số rig, biên dịch (KHÔNG chạy trên máy)

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt` (dòng `private companion object {`)
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt`

**Về TDD:** mã production đã đúng nên lần chạy đầu **dự kiến xanh**; "đỏ đúng lý do" được chứng minh bằng mutant ở Task 5. Lần chạy đầu đỏ thì **dừng, đọc lý do, không sửa mã production**.

- [ ] **Step 1: Mở hằng số ở rig** (đổi đúng một dòng, cuối tệp)

```kotlin
    companion object {
        /** A stored Nostr source is "nostr:<npub>" (bech32), never the hex key; the sync reads it back with NostrKeys.npubToHex. */
        private val AUTHOR_NPUB ...
```
Thực tế chỉ đổi `private companion object {` thành `companion object {`; `AUTHOR_NPUB` và `SOCIAL_MAX_BODY_BYTES` thêm `private` để không lộ thêm gì ngoài `SOCIAL_TIMEOUT_MS`. Làm bằng python và kiểm `count == 1` mỗi thay thế:

```python
# -*- coding: utf-8 -*-
p="app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt"
s=open(p,encoding="utf-8").read()
for old,new in [("    private companion object {\n","    companion object {\n"),
                ("        val AUTHOR_NPUB:","        private val AUTHOR_NPUB:"),
                ("        const val SOCIAL_MAX_BODY_BYTES","        private const val SOCIAL_MAX_BODY_BYTES")]:
    assert s.count(old)==1,(old,s.count(old))
    s=s.replace(old,new)
open(p,"w",encoding="utf-8").write(s)
```

- [ ] **Step 2: Thêm vào `BackgroundSyncDeviceTest.kt`** các import `kotlinx.coroutines.CompletableDeferred`, `org.junit.Rule`, `org.junit.rules.Timeout`, `java.util.concurrent.TimeUnit`; hằng số tệp-cấp `private const val CLASS_TIMEOUT_SECONDS = 60L`; trong lớp:

```kotlin
    /** One hung sync must not hang the whole instrumentation run on a phone other sessions share. */
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
```
và hai test (đặt sau `nostrSource_writesItsNote_withAnNjumpLink_andIsNeverRenamed`):

```kotlin
    @Test
    fun nostrRelayThatReceivesTheRequestButStaysSilent_isASyncFailure_inBoundedTime() {
        val gate = CompletableDeferred<Unit>()
        rig.nostrRelay.holdAnswerUntil(gate)
        try {
            val startedNs = System.nanoTime()
            val result = runBlocking { rig.syncOnce(rig.nostrFeed) }
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)
            assertEquals("the relay must have received the request (silence, not a connection error)", 1, rig.nostrRelay.requestsReceived())
            assertNull(result.outcome)
            assertTrue(result.inserted.isEmpty())
            assertEquals(0, rig.articleCountOf(rig.nostrFeed.id))
            assertEquals("Nostr · Probe", storedFeed(rig.nostrFeed.id).name)
            assertTrue("gave up after only $elapsedMs ms", elapsedMs >= BackgroundSyncRig.SOCIAL_TIMEOUT_MS / 2)
            assertTrue("took $elapsedMs ms", elapsedMs < BackgroundSyncRig.SOCIAL_TIMEOUT_MS * 3)
        } finally {
            gate.complete(Unit)
        }
    }

    @Test
    fun aSilentNostrRelay_doesNotBlockTheNextSync_whenItAnswersAgain() {
        val gate = CompletableDeferred<Unit>()
        rig.nostrRelay.holdAnswerUntil(gate)
        try {
            assertNull(runBlocking { rig.syncOnce(rig.nostrFeed) }.outcome)
            assertEquals(0, rig.articleCountOf(rig.nostrFeed.id))
            gate.complete(Unit)
            rig.nostrRelay.serveAnotherConnection()
            val second = runBlocking { rig.syncOnce(rig.nostrFeed) }
            assertNotNull(second.outcome)
            assertEquals(1, second.inserted.size)
            assertTrue(second.inserted.single().link.startsWith("https://njump.me/note1"))
            assertEquals(1, rig.articleCountOf(rig.nostrFeed.id))
            assertEquals("Nostr · Probe", storedFeed(rig.nostrFeed.id).name)
        } finally {
            gate.complete(Unit)
        }
    }
```
Cập nhật KDoc đầu lớp: thêm một dòng "NOT covered" còn lại (relay treo lúc bắt tay, hai relay, relay trả một nửa chỉ có test JVM); sửa "Deliberately NOT run" giữ nguyên.

- [ ] **Step 3: Biên dịch trên máy tính** (nền, chờ `.exit`)

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29; rm -f $D/f02k-build.exit
(./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f02k-build.log 2>&1; echo $? > $D/f02k-build.exit) > /dev/null 2>&1 &
```
Expected sau `.exit`: `0`. Lỗi biên dịch: đọc đúng lỗi, sửa **test**; ghi vào "Điều chỉnh khi thực thi". Chưa commit.

---

### Task 3: Lần chạy đầu trên Pixel (một lượt, có cổng, có sao lưu)

- [ ] **Step 1: Cổng, MỘT LỆNH RIÊNG:** `zsh /Users/loitran/.claude/tmp-f29/px_gate.sh` → `GATE OK`; `STOP:` thì dừng và hỏi owner.
- [ ] **Step 2 (lệnh riêng, sau `GATE OK`):**

```bash
D=/Users/loitran/.claude/tmp-f29; rm -f $D/pxk1.out
nohup zsh $D/px_k_loop.sh 1 > $D/pxk1.out 2>&1 &
```
Chờ `ALLDONE`. Expected: `run1: exit=0 OK (10 tests)`, `restore_exit=0`, số dòng `BYTE IDENTICAL` bằng số tệp có trong bản sao lưu (6), `datastore now` = `datastore backup`, dòng `rotation:` (chỉ ghi lại).
- Đỏ: đọc `pxkloop-run1.log`; **không sửa mã production**; hiểu lý do trước. Nếu một trong 8 test cũ đỏ vì `Timeout` (ví dụ chạy trên luồng khác), ghi lý do và sửa test, không bỏ `Timeout` mà không hỏi.
- Cổng dừng giữa chừng: dừng, hỏi owner.

---

### Task 4: Chạy lặp 20 lượt và commit test

- [ ] **Step 1:** cổng (lệnh riêng) rồi `nohup zsh $D/px_k_loop.sh 20 > $D/pxk20.out 2>&1 &`, chờ `ALLDONE`. Expected: 20 dòng `exit=0 OK (10 tests)`, restore byte-identical, băm DataStore trước = sau. Chập chờn thì điều tra gốc (so thời gian từng lượt; so `requestsReceived`), không thêm vòng gõ lại; ghi số lượt đỏ/xanh thật.
- [ ] **Step 2: Commit** (sau 20/20 xanh):

```bash
git log --oneline | grep -c "test(front-02k)"   # phải là 0
git add app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt
git diff --cached --check && git commit -m "test(front-02k): a Nostr relay that receives the request and stays silent is a quiet sync failure, then the next sync recovers

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Đột biến trên máy (mỗi mutant làm đúng nhóm test đỏ)

**Files (tạm, không commit):** `px_k_mut_batch.py`.

| Nhãn | Tệp | Thay | Bằng | Test phải đỏ |
|---|---|---|---|---|
| m1_no_throw | `domain/sv/LocalRssSv.kt` | `if (events.isEmpty()) throw java.io.IOException("Nostr relays returned no notes")` | `if (false) throw java.io.IOException("Nostr relays returned no notes")` | **cả hai** test mới (kết quả rỗng thành `FeedWithArticle` rỗng, `outcome` khác `null`) |
| m2_timeout_x4 | `infrastructure/social/nostr/NostrRelayClient.kt` | `withTimeout(timeoutMs) { finished.await() }` | `withTimeout(timeoutMs * 4) { finished.await() }` | test (i) (quá 15 giây); (ii) vẫn xanh (không có cận thời gian) |
| m3_timeout_1ms | cùng tệp | `withTimeout(timeoutMs) { finished.await() }` | `withTimeout(1L) { finished.await() }` | (i) đỏ ở **cận dưới**; (ii) đỏ; và hai test Nostr cũ cũng đỏ: ghi rõ đây là mutant không riêng |

- [ ] **Step 1: Kiểm mẫu `old` độc nhất** bằng python trước (mỗi mẫu đếm đúng 1; đã đếm 1 và 1 lúc viết plan). `px_k_mut.sh` giữ dòng `assert s.count(o) == 1`.
- [ ] **Step 2: Viết `px_k_mut_batch.py`**: sao lưu Room byte-exact (`px_backup.py`) **một lần ở đầu**; chạy `base` rồi ba mutant lần lượt qua `px_k_mut.sh` (cổng chạy trong từng mutant; **dừng cả batch** khi `GATE-STOP`); **khôi phục byte-exact (`px_restore.py`) ở cuối kể cả khi dừng giữa chừng** (khối `finally`); in `cmp` băm của ba tệp nguồn trước/sau; in `ALLDONE`.

```python
# -*- coding: utf-8 -*-
import subprocess, hashlib
R="/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/"
D="/Users/loitran/.claude/tmp-f29/"
J="app/src/main/java/com/mckimquyen/reader/"
L=J+"domain/sv/LocalRssSv.kt"; N=J+"infrastructure/social/nostr/NostrRelayClient.kt"
MUT=[("m1_no_throw",L,'if (events.isEmpty()) throw java.io.IOException("Nostr relays returned no notes")','if (false) throw java.io.IOException("Nostr relays returned no notes")'),
     ("m2_timeout_x4",N,"withTimeout(timeoutMs) { finished.await() }","withTimeout(timeoutMs * 4) { finished.await() }"),
     ("m3_timeout_1ms",N,"withTimeout(timeoutMs) { finished.await() }","withTimeout(1L) { finished.await() }")]
def h(p): return hashlib.sha256(open(R+p,"rb").read()).hexdigest()
before={L:h(L),N:h(N)}
def run(args):
    r=subprocess.run(["/bin/zsh",D+"px_k_mut.sh"]+args,capture_output=True,text=True)
    out=(r.stdout.strip()+" "+r.stderr.strip()[:200]).strip(); print(out,flush=True); return out
b=subprocess.run(["python3",D+"px_backup.py"],capture_output=True,text=True)
print("backup_exit",b.returncode,flush=True)
try:
    if b.returncode==0:
        o=run(["base","-","",""])
        if "GATE-STOP" not in o:
            for lab,f,old,new in MUT:
                if "GATE-STOP" in run([lab,f,old,new]): print("ABORT-GATE",flush=True); break
finally:
    if b.returncode==0:
        r=subprocess.run(["python3",D+"px_restore.py"],capture_output=True,text=True)
        print("restore_exit",r.returncode,r.stdout.strip().splitlines()[-1] if r.stdout.strip() else "",flush=True)
print("cmp","SAME" if before=={L:h(L),N:h(N)} else "DIFFERENT",flush=True)
print("ALLDONE",flush=True)
```
- [ ] **Step 3:** cổng (lệnh riêng) rồi chạy batch nền; không sửa tệp độ tới `ALLDONE`. Mutant sống sót hoặc đỏ sai test: điều tra, thêm hoặc siết test, chạy lại **đúng mutant đó**.
- [ ] **Step 4:** sau batch: `git status --short | grep -v '.idea'` phải sạch; build lại bản gốc (`assembleDevDebug assembleDevDebugAndroidTest`) rồi `px_k_loop.sh 1` (cổng riêng trước) để không để APK đột biến trên máy; `OK (10 tests)`, băm DataStore trùng.

---

### Task 6: Kiểm cuối, báo cáo, tài liệu, commit

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền) → **3.720**, 0 lỗi; đếm từ XML cùng cách như 02g–02j. `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.
- [ ] **Step 2:** `git diff --stat HEAD~1 -- app/schemas app/src/main app/build.gradle` rỗng (commit test chỉ đổi hai tệp androidTest).
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02k_silent-relay-device-test_DONE.md` (số lượt đỏ/xanh thật, kết quả mutant kèm test đỏ thật, băm DataStore, thời gian đo của hai test, điều chỉnh khi thực thi, **điều chưa kiểm**); cập nhật `doc/feature.md` (R2); sửa nợ "test relay không trả lời" trong báo cáo 02h: nay **đã kiểm** ca relay nhận REQ rồi im lặng, **chưa** kiểm relay treo lúc bắt tay trên máy, hai relay, trả một nửa, con số 8 giây, mạng thật.
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02k): report …`.

---

## Self-review

1. **Spec coverage:** (i) im lặng → lỗi gọn trong hạn, (ii) hồi phục → Task 2; `Timeout` lớp → Task 2; an toàn thiết bị (cổng, sao lưu Room byte-exact, khôi phục sau cả batch, băm DataStore) → Task 1, 3, 4, 5; đột biến → Task 5; JVM không đổi, tài liệu → Task 6. Điều spec nói "mutant treo vĩnh viễn không chạy trên máy" giữ nguyên, ghi trong báo cáo.
2. **Placeholder scan:** không TBD. Ba chỗ phụ thuộc điều chưa đọc (lỗi biên dịch `Timeout.seconds`; `Timeout` có đổi hành vi 8 test cũ không; `m3` đỏ thêm những test nào) đều có bước đọc và điều kiện dừng.
3. **Nhất quán tên:** `classTimeout`, `CLASS_TIMEOUT_SECONDS`, `BackgroundSyncRig.SOCIAL_TIMEOUT_MS`, `holdAnswerUntil`, `serveAnotherConnection`, `requestsReceived`, `px_k_loop.sh`, `px_k_mut.sh`, `px_k_mut_batch.py`; `OK (10 tests)` khớp 8 + 2.
4. **Rủi ro nhận thấy khi rà:** (a) `px_loop.sh` và `px_k_mut_batch.py` cùng ghi `px_root` khi sao lưu: **không chạy song song**, và lượt `px_k_loop` sau batch tự sao lưu lại; (b) khôi phục byte-exact cuối batch đưa Room về lúc sao lưu đầu batch (đúng ý, nhưng gỡ cả dòng do `SyncWorker` thật thêm trong lúc chạy; đã ghi); (c) `Timeout` chạy test ở luồng khác: nếu `Room`/`RApp` đòi luồng chính ở `@Before`, lượt đầu sẽ đỏ rõ ràng và Task 3 dừng để đọc.
