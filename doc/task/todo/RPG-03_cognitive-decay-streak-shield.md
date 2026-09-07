# [RPG-03] Cơ Chế Suy Thoái Tri Thức (Cognitive Decay) & Hồi Sinh Streak Bằng Rewarded Ads

- **Type:** Feature
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt#L72-L112), [`ui/page/rpg/BrainRpgPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt#L221-L310) (`StreakShieldCard`), [`ui/page/rpg/BrainRpgViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt#L62-L73)

> **Trạng thái:** ✅ Đã triển khai một phần (xem [`doc/task/done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md`](../../done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md)), nhưng logic streak/decay không test được và chưa có worker "cognitive decay" thực sự (XP không giảm dần khi không ôn tập). Xem task fix nối tiếp: [`RPG-06`](RPG-06_streak-decay-testable-clock.md), [`RPG-07`](RPG-07_room-migration-or-doc-alignment.md).

## Vấn đề thực tế / Mô tả
Nếu người dùng không đọc bài viết trong một chuyên mục quá 7 ngày, thanh kỹ năng của chuyên mục đó chuyển sang trạng thái "Bị oxy hóa / Giảm cấp" (Cognitive Decay). Bắn thông báo thông minh: *"Kỹ năng AI của bạn đang giảm 10%! Đọc 1 bài ngay để phục hồi"*. Cung cấp nút "Hồi sinh tức thì bằng 1 Rewarded Video".

## User Story
> Là người dùng đã xây dựng streak đọc bài,
> Tôi muốn được cảnh báo và có cách cứu vãn khi có nguy cơ mất streak hoặc kỹ năng bị suy thoái,
> Để tôi không cảm thấy công sức tích lũy bị mất trắng chỉ vì lỡ quên đọc một ngày.

## Acceptance Criteria (Gherkin)
```gherkin
Given người dùng bị đứt streak 5 ngày liên tiếp
When mở app vào ngày thứ 6
Then hiển thị dialog thông báo mất chuỗi cùng 2 lựa chọn: "Bắt đầu lại từ đầu" hoặc "Xem 1 video ngắn để bảo lưu chuỗi đọc (Streak Shield)"
And nếu chọn xem video, AdmobApplovinWrapper hiển thị Rewarded Ad thành công thì streak được giữ nguyên vẹn
```

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-03] "Cơ Chế Suy Thoái Tri Thức (Cognitive Decay) & Hồi Sinh Streak Bằng Rewarded Ads" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-03_cognitive-decay-streak-shield.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — hiện tại `BrainRpgRepository.addReadingXp` có xử lý streak/shield, nhưng KHÔNG có worker/background job nào tính "Cognitive Decay" (XP giảm dần theo thời gian không ôn tập); `activateStreakShield` chỉ tự động active shield thay vì gắn liền dialog "mất streak" mô tả trong Gherkin. `currentEpochDay()` (dòng 161-163) gọi trực tiếp System.currentTimeMillis(), không mock được — xem task RPG-06 để fix riêng phần này, không làm trùng ở đây.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới. Nếu thêm worker định kỳ, dùng WorkManager + Hilt worker factory theo pattern SyncWorker hiện có.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-03 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-03 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---
> **Ghi chú audit (2026-09-06):** Streak counting + Streak Shield (xem quảng cáo để bảo lưu chuỗi) đã hoạt động ở mức cơ bản trong `BrainRpgRepository`. Nhưng: (1) không có "Cognitive Decay" thực sự — XP theo category không giảm dần khi >7 ngày không đọc, chỉ có nhãn UI tĩnh; (2) không có dialog chủ động cảnh báo "mất streak" khi mở app — StreakShieldCard chỉ là nút bấm thủ công trong trang RPG; (3) logic ngày dùng `System.currentTimeMillis()` trực tiếp nên không unit-test được các case nghỉ/giữ/dùng-shield. Điểm audit thực tế cho phần "Cognitive Decay" theo đúng đặc tả: thấp, cần fix ở RPG-03 (dialog + decay worker) và RPG-06 (testability).
