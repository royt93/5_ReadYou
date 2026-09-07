# [FIX-12] I/O Đồng Bộ Trong `init{}` Block Vi Phạm DoD "Không I/O Trên Main Thread"

- **Type:** Bug / Performance Defect
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`infrastructure/watchdog/WatchdogManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt#L35-L42), [`domain/repository/BrainRpgRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt#L33-L42), [`domain/zen/ZenDailyEditionManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/zen/ZenDailyEditionManager.kt#L34-L39)

## Vấn đề thực tế
Repo `doc/task/README.md` quy định Definition of Done bao gồm "không I/O trên Main thread". Có ít nhất 3 class Hilt-injected `@Singleton`/repository đọc `SharedPreferences` **đồng bộ ngay trong constructor** (`init{}`), chạy trên bất kỳ thread nào gọi constructor lần đầu (thường là Main thread khi Hilt khởi tạo graph lúc `Activity`/`ViewModel` cần dependency lần đầu):
1. `WatchdogManager.kt`: `init { loadKeywords() }` (dòng 35-37) gọi `loadKeywords()` (dòng 39+) đọc `prefs.getString(KEY_WATCHDOG_LIST, null)` và parse JSON ngay khi class được khởi tạo.
2. `BrainRpgRepository.kt`: property khởi tạo `private val _userProgress = MutableStateFlow(loadProgress())` (dòng 33) — `loadProgress()` (dòng 39+) đọc nhiều key `SharedPreferences` (`KEY_TOTAL_XP`, `KEY_STREAK_DAYS`, `KEY_LAST_READ_EPOCH_DAY`, ...) đồng bộ trong lúc gán giá trị khởi tạo property, tức là chạy trước khi constructor hoàn tất.
3. `ZenDailyEditionManager.kt`: `init { ... }` (dòng 34-39) đọc 4 giá trị `SharedPreferences` (`KEY_ENABLED`, `KEY_BATCH_SILENCE`, `KEY_MORNING_TIME`, `KEY_EVENING_TIME`) đồng bộ ngay trong block khởi tạo.

Dù từng lần đọc riêng lẻ có thể nhanh, `SharedPreferences.getString/getBoolean/getLong` vẫn có thể block do I/O đĩa (đặc biệt lần đầu load file XML vào bộ nhớ, hoặc trên thiết bị storage chậm/đang bận), và việc dồn cả 3 constructor này vào cùng thời điểm khởi động app (Hilt tạo graph) làm tăng nguy cơ trễ khung hình/ANR khi app cold-start.

## User Story
> Là người dùng mở ứng dụng,
> Tôi muốn quá trình khởi động app luôn mượt mà không bị treo do đọc dữ liệu cấu hình,
> Để trải nghiệm mở app nhanh và ổn định ngay cả trên thiết bị storage chậm.

## Acceptance Criteria (Gherkin)
- **Given** `WatchdogManager`, `BrainRpgRepository`, `ZenDailyEditionManager` được Hilt khởi tạo lần đầu
- **When** constructor của các class này chạy
- **Then** constructor **không** thực hiện bất kỳ lệnh đọc `SharedPreferences`/file đồng bộ nào chặn thread gọi nó — việc load dữ liệu ban đầu (`loadKeywords()`, `loadProgress()`, đọc 4 key Zen) được chuyển sang chạy bất đồng bộ qua `Dispatchers.IO` (ví dụ trong 1 coroutine trên `CoroutineScope` được inject hoặc `applicationScope`)
- **And** các `StateFlow`/`MutableStateFlow` liên quan (`_keywords`, `_userProgress`, `_isEnabled`, v.v.) khởi tạo với giá trị mặc định an toàn (rỗng/false/default) rồi được cập nhật đúng giá trị thật ngay sau khi load xong bất đồng bộ, không làm UI hiển thị sai lâu dài (chấp nhận 1 khung hình đầu hiển thị giá trị mặc định trước khi load xong)
- **And** mọi call-site đang gọi trực tiếp các state này (UI, ViewModel khác) vẫn hoạt động đúng, không NPE/crash do thay đổi thời điểm có dữ liệu
- **And** không có regression: các tính năng Watchdog keyword alert, Brain RPG progress, Zen Daily Edition vẫn hoạt động đúng sau thay đổi.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-12] "I/O Đồng Bộ Trong init{} Block Vi Phạm DoD Không I/O Trên Main Thread" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-12_main-thread-io-init-blocks.md trước khi bắt đầu.

Task này gồm 3 file độc lập (WatchdogManager, BrainRpgRepository, ZenDailyEditionManager) — có thể xử lý tuần tự từng file trong cùng 1 task, áp dụng pattern nhất quán cho cả 3.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (cả 3 file), xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra từng class còn đọc SharedPreferences đồng bộ trong init{}/property initializer hay đã chuyển sang async.
2. Implement fix đúng theo Acceptance Criteria cho cả 3 file. Pattern gợi ý: mỗi class nhận thêm 1 `CoroutineScope`/`@ApplicationScope` qua Hilt inject (xem infrastructure/di/ đã có qualifier cho dispatcher/scope chưa — dùng lại nếu có), trong init{} chỉ launch `scope.launch(Dispatchers.IO) { val loaded = loadXxx(); _state.value = loaded }` thay vì gọi loadXxx() đồng bộ. Với BrainRpgRepository, đổi `MutableStateFlow(loadProgress())` thành `MutableStateFlow(UserProgress.DEFAULT_OR_EMPTY)` rồi cập nhật bất đồng bộ. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-12 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm để chuyển 3 init{} block đọc SharedPreferences sang async>. Chỉ ra rủi ro/cách tốt hơn nếu có, đặc biệt về race condition giữa lúc UI đọc StateFlow default và lúc load xong thật."`
   - `claude -p "Review approach cho task FIX-12 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — cho cả 3 class: xác nhận constructor trả về ngay lập tức (không block), và `StateFlow` cập nhật đúng giá trị sau khi coroutine load hoàn tất (dùng `TestDispatcher`/`runTest`/`advanceUntilIdle`).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — nếu UI đọc `WatchdogManager.keywords`/`BrainRpgRepository.userProgress`/`ZenDailyEditionManager` state ngay khi mở màn hình, xác nhận không crash/hiển thị sai khi state còn ở giá trị default.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công mở app từ cold-start, kiểm tra màn hình Watchdog/Brain RPG/Zen Settings hiển thị đúng dữ liệu đã lưu trước đó sau vài trăm ms, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được, ví dụ Profiler xác nhận không còn StrictMode violation) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
