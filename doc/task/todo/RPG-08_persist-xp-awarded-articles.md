# [RPG-08] XP Farm Exploit Sau Khi App Bị Kill/Recreate — `readArticleIds` Chỉ Lưu Trong RAM

- **Type:** Bug / Exploit
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`ui/page/rpg/BrainRpgViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt#L24-L34) (`readArticleIds`, `onArticleReadFinished`), [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt) (nơi nên persist idempotency)

## Vấn đề thực tế
`BrainRpgViewModel.kt` dòng 24:
```kotlin
private val readArticleIds = mutableSetOf<String>()

fun onArticleReadFinished(articleId: String, category: String, onRewarded: ((Long) -> Unit)? = null) {
    if (articleId.isBlank()) return
    if (readArticleIds.add(articleId)) {
        viewModelScope.launch {
            repository.addReadingXp(category, 50L)
            onRewarded?.invoke(50L)
        }
    }
}
```
`readArticleIds` chỉ lưu trong ViewModel (RAM), KHÔNG persist ra đâu cả. Khi:
- Người dùng rời màn hình đọc rồi mở lại cùng bài (ViewModel bị tạo lại nếu navigation back-stack pop hết, hoặc do `hiltViewModel()` scope thay đổi), hoặc
- Process bị hệ thống Android kill (low memory) rồi phục hồi (`onSaveInstanceState`/process death) — `readArticleIds` trở về rỗng,

thì cùng một bài viết có thể được cộng +50 XP (và tương tự nguy cơ quiz +150 XP nếu không có chặn tương tự ở `submitQuizAnswer`) **nhiều lần không giới hạn**, chỉ bằng cách mở lại bài đã đọc. Đây là lỗ hổng cho phép farm XP/level một cách không giới hạn, phá vỡ toàn bộ ý nghĩa "thành tích" của hệ thống game hoá và ảnh hưởng trực tiếp tới tính năng chia sẻ "Brain Wrapped" (khoe XP giả).

## User Story
> Là chủ sản phẩm muốn hệ thống XP phản ánh đúng nỗ lực đọc thật của người dùng,
> Tôi muốn mỗi bài viết chỉ được cộng XP đọc đúng 1 lần duy nhất vĩnh viễn, kể cả khi app bị kill/mở lại,
> Để bảng xếp hạng/streak/Wrapped không bị lạm dụng, giữ giá trị game hoá thật sự.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đã đọc xong bài viết `articleId = "abc"` và được cộng +50 XP
- **When** người dùng kill hoàn toàn app (hoặc process bị hệ thống thu hồi) rồi mở lại và đọc lại bài `"abc"` đến ngưỡng cộng XP
- **Then** hệ thống KHÔNG cộng thêm XP lần 2 cho bài `"abc"` (kiểm tra qua persisted store, không chỉ RAM)
- **And** danh sách article đã cộng XP (đọc + quiz) được lưu bền vững (Room hoặc DataStore, tuỳ theo quyết định ở `RPG-07`) chứ không chỉ tồn tại trong `ViewModel`
- **And** logic idempotent áp dụng cho CẢ `onArticleReadFinished` (XP đọc) và `submitQuizAnswer` (XP quiz) — hiện tại `submitQuizResult` trong repository không có bất kỳ chặn trùng lặp nào, cần bổ sung
- **And** có unit test xác nhận: gọi `addReadingXp`-tương-đương 2 lần liên tiếp cho cùng `articleId` (mô phỏng ViewModel bị tái tạo) chỉ cộng XP 1 lần

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-08] "XP Farm Exploit Sau Khi App Bị Kill/Recreate" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-08_persist-xp-awarded-articles.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — xác nhận readArticleIds trong BrainRpgViewModel.kt vẫn là mutableSetOf in-memory, và submitQuizResult trong BrainRpgRepository.kt vẫn không có chặn trùng theo articleId.
2. Implement fix: di chuyển việc kiểm tra "đã cộng XP cho articleId chưa" từ ViewModel (RAM) xuống BrainRpgRepository (persisted). Lưu trữ tập hợp articleId đã cộng XP đọc và articleId đã cộng XP quiz — dùng Room (bảng nhỏ `brain_rpg_xp_ledger(articleId, type, timestamp)`) nếu RPG-07 đã chọn Hướng A/Room, hoặc dùng DataStore/SharedPreferences Set<String> nếu vẫn giữ kiến trúc hiện tại (tối thiểu: `prefs.getStringSet(...)`) — ưu tiên nhất quán với quyết định đã chốt ở RPG-07 nếu task đó đã chạy trước; nếu chưa, chọn giải pháp tối thiểu bằng SharedPreferences Set<String> để không block task này. Đảm bảo thao tác kiểm tra+ghi là atomic/synchronized để tránh race condition khi 2 coroutine gọi đồng thời.
3. Sửa `addReadingXp` và `submitQuizResult` trong BrainRpgRepository để tự kiểm tra idempotency dựa trên articleId trước khi cộng XP, trả về kết quả rõ ràng cho biết đã cộng hay bị chặn trùng (ví dụ trả về nullable hoặc flag `alreadyAwarded: Boolean`) để ViewModel/UI xử lý đúng (không hiện toast "+50 XP" giả nếu bị chặn).
4. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ nếu có text UI mới.
5. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-08 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-08 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
6. Build kiểm tra: `./gradlew assembleDevDebug`.
7. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm; nếu chỉ fix `onArticleReadFinished` mà bỏ sót `submitQuizResult` thì KHÔNG đạt AC đầy đủ, điểm phải phản ánh đúng.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`) — bắt buộc: test cộng XP đọc 2 lần cùng articleId chỉ tính 1 lần (mô phỏng ViewModel tái tạo bằng cách tạo 2 instance ViewModel/Repository trỏ cùng persisted store), test tương tự cho quiz XP, test race condition cơ bản nếu khả thi.
3. Bổ sung **widget/Compose UI test** nếu UI phản hồi khác đi khi bị chặn trùng (ví dụ không hiện lại Toast +XP) (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end: đọc bài → kill/tái tạo ViewModel (hoặc Repository) → đọc lại → xác nhận DB/prefs không tăng XP lần 2.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, đọc 1 bài để cộng XP, force-stop app, mở lại và đọc lại đúng bài đó, xác nhận XP KHÔNG tăng thêm — ghi lại bằng chứng cụ thể (giá trị XP trước/sau qua UI hoặc logcat).
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
