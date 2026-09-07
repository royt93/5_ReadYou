# [RPG-06] Streak/Decay Logic Không Test Được — Inject Clock & Bổ Sung Test Nghỉ Ngày/Giữ Streak/Streak Shield

- **Type:** Bug / Architecture / Test Debt
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt#L161-L163) (`currentEpochDay()`), [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt#L72-L112) (`addReadingXp`), [`app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryTest.kt`](../../../app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgRepositoryTest.kt)

## Vấn đề thực tế
`BrainRpgRepository.currentEpochDay()` (dòng 161-163):
```kotlin
private fun currentEpochDay(): Long {
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
}
```
gọi trực tiếp `System.currentTimeMillis()` hardcode, không inject `Clock`/interface thời gian nào → không thể viết test giả lập "nghỉ 1 ngày", "giữ streak liên tục", "dùng shield bảo vệ streak khi nghỉ" một cách xác định (deterministic). `BrainRpgRepositoryTest` hiện tại (7 test case) hoàn toàn thiếu các case này — đây là phần logic dễ sai nhất của tính năng game hoá (streak counting, shield consumption, decay) nhưng chưa được verify thật bằng test tự động.

## User Story
> Là lập trình viên bảo trì tính năng Brain RPG,
> Tôi muốn logic tính streak/ngày đọc được inject qua một nguồn thời gian có thể giả lập (Clock/TimeProvider),
> Để tôi có thể viết unit test xác định (deterministic) cho các case nghỉ ngày, giữ streak, và dùng streak shield mà không phụ thuộc đồng hồ hệ thống thật.

## Acceptance Criteria (Gherkin)
- **Given** `BrainRpgRepository` được khởi tạo với một `Clock`/`TimeProvider` có thể inject qua Hilt (constructor param, có default = `Clock.systemDefaultZone()` hoặc tương đương cho production)
- **When** unit test cung cấp một `Clock` giả lập ngày N, gọi `addReadingXp`, rồi advance `Clock` sang ngày N+1 và gọi lại `addReadingXp`
- **Then** `streakDays` tăng thêm 1 (test case "giữ streak liên tục")
- **When** unit test advance `Clock` sang ngày N+2 (bỏ qua 1 ngày, không đọc gì) mà KHÔNG có streak shield active, rồi gọi `addReadingXp`
- **Then** `streakDays` reset về 1 (test case "nghỉ 1 ngày mất streak")
- **When** unit test advance `Clock` sang ngày N+2 (bỏ qua 1 ngày) nhưng CÓ streak shield active (`activateStreakShield()` đã gọi trước đó), rồi gọi `addReadingXp`
- **Then** `streakDays` tăng thêm 1 như bình thường (không reset) VÀ `streakShieldActive` chuyển về `false` sau khi tiêu thụ (test case "dùng shield giữ streak khi nghỉ")
- **And** cả 3 test case trên phải nằm trong `BrainRpgRepositoryTest` (hoặc file test mới cùng package) và pass khi chạy `./gradlew testDevDebugUnitTest`

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-06] "Streak/Decay Logic Không Test Được — Inject Clock & Bổ Sung Test" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-06_streak-decay-testable-clock.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — mở BrainRpgRepository.kt, xác nhận currentEpochDay() vẫn gọi System.currentTimeMillis() trực tiếp và BrainRpgRepositoryTest.kt chưa có test đa-ngày (multi-day).
2. Implement fix: thêm tham số `clock: java.time.Clock = java.time.Clock.systemDefaultZone()` (hoặc constructor Hilt-injectable qua @Provides trong DbModule/một Qualifier riêng nếu cần singleton) vào BrainRpgRepository, thay `currentEpochDay()` bằng `clock.instant().epochSecond / 86400` hoặc dùng `LocalDate.now(clock).toEpochDay()`. Đảm bảo test có thể tạo `Clock.fixed(instant, zone)` rồi dùng `Clock` mutable wrapper hoặc tạo repository mới mỗi lần advance ngày (tuỳ thiết kế nào đơn giản hơn để test). Không phá vỡ hành vi production hiện tại.
3. Viết đủ 3 test case theo Acceptance Criteria vào BrainRpgRepositoryTest.kt (hoặc file mới nếu class quá dài).
4. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, không lưu Context/Activity vào singleton nếu không cần thiết, không cần localize (task này không có UI/text mới).
5. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-06 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-06 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
6. Build kiểm tra: `./gradlew assembleDevDebug` và `./gradlew testDevDebugUnitTest`.
7. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu Clock chỉ inject giả (test vẫn phụ thuộc thời gian thật) thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`) — đủ 3 case bắt buộc: nghỉ ngày mất streak, giữ streak liên tục, dùng shield giữ streak khi nghỉ, cộng thêm edge case "đọc 2 lần cùng ngày không tăng streak 2 lần".
3. Bổ sung **widget/Compose UI test** nếu có thay đổi hành vi UI liên quan (thường không cần cho task thuần logic này; ghi rõ lý do bỏ qua nếu không áp dụng).
4. Bổ sung **integration test** cho luồng end-to-end nếu repository được wiring qua Hilt module mới.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, đọc bài để cộng XP, xác nhận streak vẫn hoạt động đúng với đồng hồ hệ thống thật (không có regression) — ghi lại bằng chứng cụ thể.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
