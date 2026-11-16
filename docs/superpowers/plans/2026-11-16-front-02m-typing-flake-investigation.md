# FRONT-02m — Điều tra gốc lỗi gõ chữ chập chờn của test 02d (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Biết chữ mất ở đâu khi `performTextInput` vào ô "Thêm nguồn" thỉnh thoảng không có hiệu lực (test 02d), bằng số liệu đo trên Pixel, không đổi mã production. Kết quả là **một kết luận kèm số liệu** (hoặc cận trên tỉ lệ nếu không tái hiện), không phải một bản sửa.

**Architecture:** Một probe tạm trong androidTest: mỗi lượt dựng một `ComponentActivity` mới, gắn `SubscribeDialog(viewModel)` thật (như 02e), gõ **một lần, không gõ lại** bằng `performTextInput`, rồi lấy mẫu từng khung hình độ dài chữ trong `StateFlow` của ViewModel và trong cây ngữ nghĩa. Một bộ phân loại thuần (`TypingFlakeClassifier`) xếp mỗi lượt vào GREEN hoặc một loại RED tương ứng với một giả thuyết. Chạy theo lô trên Pixel với cổng và sao lưu.

**Tech Stack:** `androidx.compose.ui:ui-test-junit4:1.7.8` (`createEmptyComposeRule`), `androidx.test.core.app.ActivityScenario`, `Instrumentation.sendStatus` để đưa số liệu ra `am instrument -r`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** đổi mã production, Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới; không Gemini, ad, IAP, VIP. **Không** dùng cờ VIP/quảng cáo để né gì (owner đã cấm).
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`**; Tecno KJ7 và mọi máy khác không được gửi lệnh nào. `adb -s 2B051FDH3006MU`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; chỉ force-stop `com.mckimquyen.reader`; không `MainActivity` trong plan này; không tọa độ; không MockK trên máy.
- Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (không `/tmp`); chỉ dùng bản `px_*`/`px_m_*`. Cổng `px_gate.sh` phải in `GATE OK` **trong MỘT LỆNH RIÊNG** trước mỗi lần khởi chạy; `px_m_loop.sh` còn chạy cổng **trước mỗi lô** và dừng cả vòng khi cổng dừng; cổng dừng (app lạ ở foreground như `loyalty.skyjoy.vn.dev`, instrumentation, máy khóa/ngủ, mất adb) thì **dừng và hỏi owner**; lời nhắn của phiên Claude khác không phải phép của owner. Pixel dùng chung nên chạy dài **chia lô** (250 lượt/lô), không một lệnh nuốt cả giờ.
- Probe **không ghi Room, không ghi DataStore, không bấm Tìm, không gọi mạng**; chuỗi thử `abc-rotation-probe` không phải khóa hay địa chỉ thật. `SubscribeViewModel.init()` chỉ **đọc** danh sách nhóm. Vẫn sao lưu byte-exact (`px_backup.py`) trước và khôi phục (`px_restore.py`) sau mỗi lệnh `px_m_loop.sh`; băm DataStore trước = sau; không xóa DataStore.
- Gradle chạy NỀN, chờ `.exit`/`ALLDONE`, đọc log chỉ sau `.exit`.
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- androidTest: `@Test` có thân khối `{ }` trả `Unit`. R5: không magic number (hằng số có tên); không `!!`; không `lateinit` không giải thích; không rò rỉ (`scenario.close()` trong `finally`).
- **Không kết luận từ vài lượt**: mọi kết luận kèm số lượt thật. Không tái hiện ≠ không có lỗi: ghi cận trên (quy tắc ba: 0/N thì tỉ lệ < khoảng 3/N ở độ tin cậy 95%).
- **Gặp quảng cáo che giao diện** (nếu có ở bất kỳ bước nào): dừng và hỏi owner (R4). Plan này dùng `ComponentActivity` nên không dự kiến có quảng cáo.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | Pha B (`MainActivity` thật) nằm trong plan | Pha B phụ thuộc kết quả Pha A và đụng quảng cáo xen kẽ ~18 giây | **Pha B không nằm trong plan này**: nếu cần, spec/plan bổ sung riêng sau cổng quyết định (Task 6) và hỏi owner |
| 2 | Gọi `InsertTextAtCursor` trực tiếp để đọc giá trị trả về (H1) | `performTextInput` (ui-test 1.7.8) = `getNodeAndFocus()` rồi `performSemanticsAction(InsertTextAtCursor)`; gọi trực tiếp **đổi** đường chạy (bỏ bước focus) và có thể che hiện tượng | Giữ **`performTextInput` nguyên bản** (giống 02d); H1 chỉ **suy ra** từ cờ `enabled`/`focused` ghi trước lúc gõ và từ loại RED (không đọc được giá trị trả về): nói rõ trong báo cáo |
| 3 | `TestOwner` + `hiltViewModel()` cho `SubscribeDialog()` | `SubscribeDialog(subscribeViewModel: SubscribeViewModel = hiltViewModel())` **nhận ViewModel làm tham số**; 02e đã dùng `ViewModelProvider(activity, subscribeViewModelFactory(url))` và `activity.setContent { SubscribeDialog(viewModel) }` | Dùng đúng cách 02e; không cần `TestOwner` |
| 4 | Mỗi lượt một Activity mới | `createAndroidComposeRule` chỉ có một Activity cho cả test | Dùng `createEmptyComposeRule()` + `ActivityScenario.launch(ComponentActivity::class.java)` mỗi lượt, `scenario.close()` trong `finally` |
| 5 | Đồng hồ Compose | 02d chạy `mainClock.autoAdvance = false` (vì `FeedsPage` có hoạt ảnh vô hạn); 02e (cùng hộp thoại, cùng `performTextInput`, **đồng hồ tự động**) **0 lượt đỏ trong 80** (40 Pixel, 40 Tecno) | Thêm tham số `clock=manual\|auto`: `manual` giống 02d (đẩy từng khung hình), `auto` giống 02e. Bằng chứng 02e **yếu**: nếu tỉ lệ thật là 2% thì xác suất 0/80 khoảng 20%, nếu 5% thì khoảng 1,6% |
| 6 | "Dòng thời gian `linkContent` và độ dài chữ trong cây ngữ nghĩa" | `SubscribeDialog` đọc state bằng `collectAsState(Dispatchers.Default)`: composition thấy giá trị **trễ** so với `StateFlow` | Lấy mẫu **cả hai** mỗi khung hình; loại RED phân biệt "app không nhận chữ" (`NEVER_DELIVERED`) với "app nhận nhưng không vẽ" (`NOT_RENDERED`) và "nhận rồi bị xóa" (`ERASED`) |
| 7 | Số lượt mặc định 1.000 | Chạy tình cờ trong một lượt `connected` sẽ ngốn cả giờ | Mặc định `iterations = 5`; số lượt thật truyền bằng `-e iterations` |
| 8 | Cờ `windowFocused` | `ActivityScenario.onActivity { it.hasWindowFocus() }` | Lượt mà cửa sổ test **không có focus** ngay trước khi gõ được đếm `INVALID`, không đếm đỏ |

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/TypingFlakeClassifier.kt` | dữ liệu mẫu + bộ phân loại thuần (không Android) |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/TypingFlakeClassifierTest.kt` | 8 test thuần (JUnit chạy trên máy, dưới 1 giây) |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/TypingFlakeProbeTest.kt` | probe + 2 mẫu đối chứng |
| Tạm, **không commit**, `/Users/loitran/.claude/tmp-f29/`: `px_m_loop.sh`, `px_m_sum.py` | chạy lô và cộng số liệu |
| Create `doc/task/done/FRONT-02m_typing-flake-investigation_DONE.md`; Modify `doc/feature.md`, `doc/task/done/FRONT-02d…` (nợ "gốc chưa biết") | tài liệu |

**Về commit mã probe:** mặc định **không commit** probe và test của nó (spec: tạm). Cuối Task 6 hỏi owner bằng `AskUserQuestion` có muốn giữ không; chỉ khi owner đồng ý mới commit (`iterations` mặc định 5 nên chạy tình cờ không tốn thời gian).

Dùng lại, **không sửa**: `SubscribeViewModelFactory.kt`, `SubscribeDialogRecreateDeviceTest.kt`, mọi mã production.

---
### Task 1: Bộ phân loại thuần `TypingFlakeClassifier` (TDD, chạy trên máy tính, KHÔNG chạm Pixel)

**Mục đích:** biến một lượt gõ thành một nhãn có nghĩa bằng hàm thuần, để test được trước khi tốn thời gian trên máy. Bộ phân loại là nơi dễ sai nhất của cuộc điều tra (nhãn sai thì kết luận sai), nên test viết trước.

**Files:** Create `.../subs/TypingFlakeClassifier.kt`, `.../subs/TypingFlakeClassifierTest.kt` (đều trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/`).

**Interfaces (Produces):**
```kotlin
enum class TypingOutcome { GREEN, INVALID, NEVER_DELIVERED, NOT_RENDERED, ERASED, UNFOCUSED_AT_TYPING, DISABLED_AT_TYPING }

/** One sample of one frame: the length of the text the ViewModel holds and the length of the text on screen (semantics). */
data class TypingSample(val viewModelLength: Int, val screenLength: Int)

data class TypingTrace(
    val windowFocusedBeforeTyping: Boolean,
    val fieldFocusedBeforeTyping: Boolean,
    val fieldEnabledBeforeTyping: Boolean,
    val expectedLength: Int,
    val samples: List<TypingSample>, // taken after performTextInput returned, one per frame, oldest first
)

object TypingFlakeClassifier { fun classify(trace: TypingTrace): TypingOutcome }
```

- [ ] **Step 1: Viết test trước (đỏ vì chưa có lớp)**, 8 test thuần JUnit (không Android, không Compose):
  1. `aTraceWhereTheScreenReachesTheExpectedLength_isGreen`: `samples = [(0,0),(18,0),(18,18)]`, `expectedLength = 18` → `GREEN`.
  2. `anUnfocusedWindowBeforeTyping_isInvalid_whateverHappensAfter`: `windowFocusedBeforeTyping = false` với mẫu rỗng → `INVALID`.
  3. `aViewModelThatNeverHoldsTheText_isNeverDelivered`: mọi mẫu `viewModelLength = 0`, `screenLength = 0` → `NEVER_DELIVERED`.
  4. `aViewModelThatHoldsTheTextWhileTheScreenStaysEmpty_isNotRendered`: `viewModelLength` đạt 18 nhưng `screenLength` luôn 0 → `NOT_RENDERED`.
  5. `aViewModelThatHeldTheTextAndThenLostIt_isErased`: `viewModelLength` 0 → 18 → 0 → `ERASED` (kể cả khi màn hình từng thấy 18 hoặc chưa).
  6. `aFieldNotFocusedBeforeTyping_andNeverDelivered_isUnfocusedAtTyping`: `fieldFocusedBeforeTyping = false` và mọi mẫu 0 → `UNFOCUSED_AT_TYPING` (cờ giải thích ưu tiên hơn `NEVER_DELIVERED`).
  7. `aDisabledFieldBeforeTyping_andNeverDelivered_isDisabledAtTyping`: `fieldEnabledBeforeTyping = false` → `DISABLED_AT_TYPING`.
  8. `theOrderOfTheRulesIsFixed`: `windowFocused = false` thắng mọi cờ khác; `ERASED` thắng `NEVER_DELIVERED`; một lượt vừa ERASED vừa `fieldFocusedBeforeTyping = false` vẫn là `ERASED` (chữ đã vào thì cờ focus không còn là lời giải thích).
  Chạy đỏ (biên dịch hỏng vì thiếu lớp là đỏ đúng lý do): `./gradlew compileDevDebugAndroidTestKotlin` (nền, đọc lỗi sau `.exit`).
- [ ] **Step 2: Viết `TypingFlakeClassifier`** (hàm thuần, thứ tự quy tắc cố định):
  1. `!windowFocusedBeforeTyping` → `INVALID`.
  2. `GREEN` nếu **bất kỳ** mẫu nào có `screenLength >= expectedLength` **và** mẫu **cuối** có `screenLength >= expectedLength`.
  3. `viewModelPeak = samples.maxOf { it.viewModelLength }` (0 nếu rỗng); nếu `viewModelPeak >= expectedLength`: nếu mẫu cuối có `viewModelLength < viewModelPeak` → `ERASED`, nếu không (màn hình chưa đủ chữ) → `NOT_RENDERED`.
  4. còn lại (ViewModel không bao giờ giữ chữ): `!fieldEnabledBeforeTyping` → `DISABLED_AT_TYPING`; `!fieldFocusedBeforeTyping` → `UNFOCUSED_AT_TYPING`; ngược lại `NEVER_DELIVERED`.
  Hằng số không có số ma thuật: mọi số trong mã đến từ `expectedLength`; không dùng số cứng.
- [ ] **Step 3: Chạy 8 test trên máy tính.** Test nằm trong `androidTest` nên **không** chạy được bằng `testDevDebugUnitTest`. Chọn một trong hai và ghi vào "Điều chỉnh khi thực thi" cái đã dùng: (a) chuyển hai tệp `TypingFlakeClassifier*.kt` sang `app/src/test/java/.../subs/` (JVM thuần, JUnit4, đã có), **đây là cách ưu tiên** vì chạy trong full suite; (b) giữ ở androidTest rồi chạy trên Pixel. Với (a): `./gradlew :app:testDevDebugUnitTest --tests '*TypingFlakeClassifierTest'` → 8 xanh; **kiểm mutant tay** (đổi thứ tự quy tắc 3 và 4 → test 6 hoặc 8 đỏ; đổi `>=` thành `>` trong quy tắc 2 → test 1 đỏ). Khi chọn (a), full suite JVM thành 3.728 (3.720 + 8); ghi con số đo thật.
- [ ] **Step 4: Commit** (chỉ hai tệp bộ phân loại; trước đó `git log --oneline | grep -c "test(front-02m)"` phải là 0):

```bash
git add <hai tệp> && git diff --cached --check && git commit -m "test(front-02m): classify one typing attempt of the Add Source box by where the text was lost

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Công cụ `px_m_*` (chỉ tệp cục bộ, cổng chỉ-đọc ở cuối)

**Files (tạm, không commit):** `/Users/loitran/.claude/tmp-f29/px_m_loop.sh`, `px_m_sum.py`.

- [ ] **Step 1: `px_m_loop.sh`** = `px_loop.sh` đã có, sửa để: (a) tham số `<lô> <lượt mỗi lô> <clock>` (mặc định 1 lô, 5 lượt, `manual`); (b) cổng chạy **trước mỗi lô** (đã là hành vi của `px_loop.sh`); (c) `am instrument … -e class com.mckimquyen.reader.ui.page.home.feed.subs.TypingFlakeProbeTest -e iterations $N -e clock $CLOCK …`; (d) lưu log mỗi lô `pxmloop-run$i.log`; (e) in cuối mỗi lô dòng `batch$i: exit=… counts=…` lấy từ các dòng `INSTRUMENTATION_STATUS: typing_summary=...` (xem Task 3). Khác `px_loop.sh`: **không** ép `OK (n tests)`, vì probe có thể kết thúc bằng đỏ có chủ ý (xem Task 3, `@Test` đo thật không `fail` khi gặp RED; chỉ `fail` khi **bộ đếm bất khả**).
- [ ] **Step 2: `px_m_sum.py`**: đọc `pxmloop-run*.log`, cộng `counts` của các lô (GREEN, INVALID, NEVER_DELIVERED, NOT_RENDERED, ERASED, UNFOCUSED_AT_TYPING, DISABLED_AT_TYPING) và in: tổng lượt, lượt hợp lệ (trừ INVALID), số đỏ, tỉ lệ đỏ/hợp lệ, **cận trên 95% theo quy tắc ba** khi đỏ = 0, và danh sách dòng `RED{...}` (đã được probe in sạch, không chữ người dùng).
- [ ] **Step 3: Kiểm**

```bash
cd /Users/loitran/.claude/tmp-f29
grep -nE "115333744A005844|Tecno|tecno|FeedNameIcon|BackgroundSync" px_m_loop.sh px_m_sum.py; echo "grep exit $? (1 = none left)"
grep -n "adb " px_m_loop.sh | grep -v -- '-s \$SERIAL' | grep -v "adb devices"
python3 px_m_sum.py --self-check
```
Expected: `grep exit 1`; không dòng `adb` thiếu `-s`; `--self-check` tự dựng log giả (3 lô: 249 GREEN + 1 RED mỗi lô, một lô có 5 INVALID) rồi in đúng tổng và tỉ lệ, rồi dùng log giả với 0 đỏ để thấy cận trên `~3/N`.
- [ ] **Step 4: Cổng chỉ-đọc, MỘT LỆNH RIÊNG:** `zsh /Users/loitran/.claude/tmp-f29/px_gate.sh` → `GATE OK`; `STOP:` thì dừng và hỏi owner.

(Không commit.)

---

### Task 3: Viết probe và biên dịch (KHÔNG chạy trên máy)

**Files:** Create `.../subs/TypingFlakeProbeTest.kt`

- [ ] **Step 1: Đọc lại hai tệp 02e và 02d đang tham chiếu** (`SubscribeNostrPreviewRecreateDeviceTest.kt` dòng 96–112 cho `viewModelOf`, `showDialogOn`, `openDialog`; `SubscribeDialogRecreateDeviceTest.kt` dòng 72–120 cho `describeScreen`, `waitFor`, `openDialogAndType`) để chép **đúng** tên và chữ ký. Probe dùng **`createEmptyComposeRule()`** (mỗi lượt một `ActivityScenario`).
- [ ] **Step 2: Viết probe.** Đối số từ `InstrumentationRegistry.getArguments()`: `iterations` (mặc định `5`), `clock` (`manual` mặc định, hoặc `auto`). Mỗi lượt:
  1. `ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> … }`.
  2. Lấy `SubscribeViewModel` từ `ViewModelProvider(activity, subscribeViewModelFactory(<URL relay giả>))` (như 02e; probe **không** tìm kiếm nên relay không bao giờ bị hỏi; truyền một URL hợp lệ tĩnh như `ws://127.0.0.1:1`, đã kiểm trong Step 1 rằng 02e cũng chỉ truyền URL).
  3. `activity.setContent { SubscribeDialog(viewModel) }` trên luồng UI; `viewModel.showDrawer()`; chờ ô nhập xuất hiện (`hasText(placeholder)`), với `clock=manual` thì `mainClock.autoAdvance = false` và đẩy `advanceTimeByFrame` trong vòng chờ như 02d.
  4. **Trước khi gõ** ghi `windowFocusedBeforeTyping` (`activity.hasWindowFocus()`), `fieldFocusedBeforeTyping` và `fieldEnabledBeforeTyping` (từ nút `hasSetTextAction()` đầu tiên: `isFocused`, `!isNotEnabled`); kiểm ô **đang trống** (nếu không, lượt đó `fail` rõ ràng, không nối chữ).
  5. `performTextInput(PROBE_TEXT)` **một lần, không gõ lại**.
  6. Lấy mẫu **`SAMPLE_FRAMES` khung hình** (hằng số, mặc định 60, tương đương khoảng 1 giây ở 60 Hz): mỗi khung (`manual`: `advanceTimeByFrame`; `auto`: `waitForIdle` nhẹ rồi `Thread.sleep(FRAME_MILLIS)`) ghi `TypingSample(viewModel.subscribeUiState.value.linkContent.length, <độ dài chữ lớn nhất của các nút hasSetTextAction() trong cây ngữ nghĩa>)`.
  7. `TypingFlakeClassifier.classify(trace)`; cộng bộ đếm; nếu `RED` (không GREEN, không INVALID) in **một dòng** `RED{outcome=… iter=… winFocus=… fieldFocus=… enabled=… vmSeries=… screenSeries=…}` (chuỗi độ dài thu gọn kiểu `0x12,18x48`, **không** chữ người dùng).
  8. `scenario` đóng trong `finally`; `viewModel` không giữ gì sau đó (`ViewModelStore` của Activity bị hủy khi Activity hủy).
  Cuối: `Instrumentation.sendStatus(0, Bundle{ "typing_summary" → "GREEN=…,INVALID=…,NEVER_DELIVERED=…,…" })`.
- [ ] **Step 3: Hai `@Test`:** `measure()` (chạy `iterations` lượt, **không** `fail` khi gặp RED; chỉ `fail` nếu **không có lượt hợp lệ nào** hoặc số đếm không cộng đủ `iterations`); `theClassifierAgreesWithTheProbeWiring()` (một lượt giả không cần máy: dựng `TypingTrace` rồi `classify` để chắc probe dùng cùng kiểu dữ liệu; chặn lỗi biên dịch/nối dây).
- [ ] **Step 4: Biên dịch** (nền, chờ `.exit`):

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29; rm -f $D/f02m-build.exit
(./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f02m-build.log 2>&1; echo $? > $D/f02m-build.exit) > /dev/null 2>&1 &
```
Expected `0`. Lỗi biên dịch: đọc đúng lỗi, sửa **probe**; ghi vào "Điều chỉnh khi thực thi". Chưa commit.

---
### Task 4: Lượt thử nhỏ trên Pixel (5 lượt, kiểm probe đo đúng)

**Mục đích:** biết probe chạy được, số liệu hợp lý, trước khi đốt cả giờ. Không kết luận gì ở bước này.

- [ ] **Step 1: Cổng, MỘT LỆNH RIÊNG** (`px_gate.sh` → `GATE OK`; `STOP:` thì dừng và hỏi owner).
- [ ] **Step 2 (lệnh riêng sau `GATE OK`):** `nohup zsh $D/px_m_loop.sh 1 5 manual > $D/pxm_try.out 2>&1 &`, chờ `ALLDONE`. Expected: `counts` cộng đủ 5; phần lớn GREEN; restore byte-identical, DataStore trùng.
- **Kiểm tính hợp lệ của phép đo** (làm ngay, trước khi tin số liệu):
  - Mọi lượt `INVALID` (cửa sổ test không focus) nghĩa là cổng hoặc môi trường sai; **dừng, không chạy lô lớn**, đọc log.
  - `hasSetTextAction()` phải thấy **đúng một** ô; nếu 0 hoặc nhiều hơn, probe đang đo sai nút: dừng, sửa probe.
  - Một lượt GREEN phải có chuỗi `screenSeries` đi từ 0 lên `PROBE_TEXT.length` trong ít khung hình; ghi số khung hình điển hình.
- [ ] **Step 3: Phép thử âm (bắt buộc, chứng minh probe KHÔNG luôn xanh):** thêm một `@Test` tạm `controlField_wrongTextIsDetected()` không đổi mã production: dùng cùng quy trình nhưng gõ vào ô **khi đã đặt `lockLinkInput = true`** (`viewModel` công khai không có hàm đặt; thay bằng cách gõ **trước khi ô xuất hiện** → hành động không tìm thấy nút, hoặc ngắt `enabled` bằng `setContent` của một `TextField` riêng `enabled = false` cùng placeholder). Kỳ vọng: nhãn **không phải GREEN** (`DISABLED_AT_TYPING` hoặc `NEVER_DELIVERED`). Mục đích: chứng minh bộ phân loại + probe **nhìn thấy** một lượt hỏng có chủ ý. Làm cùng lượt `px_m_loop.sh 1 5 manual` của Step 2 hoặc một lượt riêng sau cổng riêng.

---

### Task 5: Chạy lô lớn (Pha A)

- [ ] **Step 1: Chạy `manual` trước** (giống 02d, nơi lỗi từng xảy ra): cổng (lệnh riêng) rồi `nohup zsh $D/px_m_loop.sh 4 250 manual > $D/pxm_manual.out 2>&1 &` (4 lô × 250 = 1.000 lượt). Cổng chạy trước **mỗi lô**; cổng dừng giữa chừng: dừng cả vòng và hỏi owner.
- [ ] **Step 2: Chạy `auto`** (giống 02e) sau đó: cổng riêng, `px_m_loop.sh 4 250 auto`.
- [ ] **Step 3:** `python3 $D/px_m_sum.py` cho từng chế độ; ghi: tổng, hợp lệ, đỏ theo từng nhãn, tỉ lệ, cận trên; **dán nguyên các dòng `RED{...}`** vào báo cáo.
- [ ] **Step 4: Thời lượng:** nếu 250 lượt mất quá lâu (đo ở Task 4), giảm lô còn 100 và tăng số lô; không bao giờ để một lệnh chạy quá nửa giờ không có cổng.

---

### Task 6: Cổng quyết định, báo cáo, tài liệu

- [ ] **Step 1: Đọc kết quả, quyết định (số liệu, không đoán):**
  - **Có tái hiện (≥ 3 lượt đỏ trong một chế độ):** nhãn đa số cho biết giả thuyết: `NEVER_DELIVERED` hoặc `UNFOCUSED_AT_TYPING` hoặc `DISABLED_AT_TYPING` → hạ tầng test/focus (H1, H3); `NOT_RENDERED` → composition thấy trễ (H2); `ERASED` → có thứ xóa chữ (H4, có thể là app). Nếu nhãn là `ERASED` hoặc gốc có vẻ ở app: **dừng và hỏi owner (`AskUserQuestion`) trước khi làm gì tiếp**, không sửa mã app. Nếu gốc ở test/thư viện: đề xuất **một** thay đổi cho 02d, chạy lại **80 lượt** có cổng với đúng thay đổi đó (bài học 02d: không kết luận từ ít lượt) rồi mới báo.
  - **Không tái hiện (0 đến 2 đỏ / ~1.000 hợp lệ):** ghi cận trên (`<~0,3%` với 0 đỏ) và nói thẳng ba lý do không loại trừ được: (i) hiệu ứng người quan sát (probe đọc mỗi khung), (ii) Pha A không có `FeedsPage` (H5), (iii) bằng chứng 02e yếu. **Hỏi owner** có muốn Pha B (`MainActivity` thật, 300 lượt, có thể gặp quảng cáo; chỉ làm khi owner đồng ý, spec/plan bổ sung riêng) hay dừng ở đây.
- [ ] **Step 2: Viết `doc/task/done/FRONT-02m_typing-flake-investigation_DONE.md`**: câu hỏi, 5 giả thuyết và **số liệu cho từng giả thuyết**, số lượt thật theo chế độ, nhãn, `RED{...}`, phép thử âm, cận trên, **kết luận hoặc "chưa kết luận"**, điều không kiểm (không `MainActivity`, không IME thật, một máy), điều chỉnh khi thực thi, băm DataStore trước/sau, `user_rotation` Pixel (**báo, không sửa**). Cập nhật `doc/feature.md` (R2) và dòng "Nguyên nhân gốc chưa biết" ở báo cáo 02d bằng đúng kết luận (hoặc "đã điều tra, chưa kết luận, xem 02m").
- [ ] **Step 3: Chạy lại full suite JVM** (nền): kỳ vọng **3.728** nếu Task 1 chọn cách (a) (3.720 + 8), nếu không thì **3.720**; đếm từ XML cùng cách như 02g–02l; ghi con số thật. `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`. `git diff --stat <commit đầu> HEAD -- app/schemas app/src/main app/build.gradle` rỗng.
- [ ] **Step 4: Hỏi owner (`AskUserQuestion`)** có giữ `TypingFlakeProbeTest.kt` trong kho (mặc định 5 lượt, không tốn thời gian khi chạy tình cờ) hay xóa. Chỉ commit probe khi owner đồng ý; ngược lại `git checkout`/xóa tệp và ghi "probe không được giữ" trong báo cáo.
- [ ] **Step 5:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02m): report …` (cùng, nếu owner chọn giữ, commit `test(front-02m): …` riêng cho probe **trước** báo cáo).

---

## Self-review

1. **Spec coverage:** Pha A (probe, bộ ghi chẩn đoán, 1.000 lượt, lô nhỏ, lượt `INVALID`) → Task 3, 5; bộ phân loại nhãn theo H1–H4 → Task 1; H5 (`MainActivity`/`FeedsPage`) → chế độ `manual` vs `auto` và Pha B **được hoãn có chủ ý** (điều chỉnh 1) với cổng hỏi owner; an toàn thiết bị (cổng trước mỗi lô, sao lưu, khôi phục, băm DataStore) → Task 2, 4, 5; "không tái hiện ≠ không có lỗi, ghi cận trên" → Task 2 (`px_m_sum.py`), Task 6; kiểm 80 lượt khi đề xuất chỉnh 02d → Task 6; báo cáo và nợ 02d → Task 6.
2. **Placeholder scan:** không TBD. Những điều chưa chắc đều có bước đọc/dừng hỏi: URL relay giả của probe (Step 1 Task 3 đọc lại 02e); cách tạo ô bị tắt cho phép thử âm (Task 4 Step 3, hai phương án, kèm tiêu chí kết quả); chạy bộ phân loại ở JVM hay androidTest (Task 1 Step 3, hai phương án, ưu tiên JVM).
3. **Nhất quán tên:** `TypingOutcome`, `TypingSample`, `TypingTrace`, `TypingFlakeClassifier`, `TypingFlakeClassifierTest`, `TypingFlakeProbeTest`, `px_m_loop.sh`, `px_m_sum.py`, `typing_summary`, `PROBE_TEXT`, `SAMPLE_FRAMES`; 7 nhãn khớp giữa Task 1, 2, 3, 6.
4. **Rủi ro nhận thấy khi rà:** (a) **giá trị trả về của `InsertTextAtCursor` không đo được** nếu giữ `performTextInput` nguyên bản (điều chỉnh 2): H1 chỉ suy ra gián tiếp, báo cáo phải nói rõ; (b) **hiệu ứng người quan sát**: đọc `StateFlow` và cây ngữ nghĩa mỗi khung có thể đổi nhịp; (c) `createEmptyComposeRule` + nhiều `ActivityScenario` liên tiếp có thể rò rỉ nếu quên `close()`: bắt buộc trong `finally`; (d) 1.000 lượt × ~1–2 giây mỗi lượt cộng thời gian khởi động Activity có thể mất 30–60 phút, chia lô và cổng trước mỗi lô; (e) nếu probe tự nó khiến lượt nào cũng xanh (đã gõ chậm hơn thật), phép thử âm ở Task 4 chỉ chứng minh probe *nhìn thấy* lỗi, không chứng minh nó *tái tạo* lỗi.
