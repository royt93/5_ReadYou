# [RPG-04] Thẻ Báo Cáo Tri Thức Động Hàng Tuần (Weekly "Brain Wrapped" 9:16)

- **Type:** Feature
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`ui/page/rpg/BrainRpgPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt#L451-L517) (`WeeklyWrappedCard`, `onShare`)

> **Trạng thái:** 🟡 Triển khai tối thiểu (MVP text-share only), CHƯA đúng đặc tả gốc. Xem task fix nối tiếp: [`RPG-09`](RPG-09_wrapped-full-spec-mvp.md).

## Vấn đề thực tế / Mô tả
Vào mỗi sáng Chủ Nhật, ứng dụng tổng hợp tuần đọc sách thành một slide show 9:16 phong cách Spotify Wrapped: Tổng số từ đã đọc, biểu đồ mạng nhện đa giác (Brain Radar Chart), chủ đề thống trị, danh hiệu đạt được (ví dụ: *"Top 2% AI Researcher"*). Hỗ trợ render ra ảnh bitmap sắc nét kèm logo app và mã QR để chia sẻ 1 chạm lên Instagram Stories, Facebook, X.

## User Story
> Là người dùng đã tích lũy XP và streak trong tuần,
> Tôi muốn nhận một thẻ tổng kết trực quan, đẹp mắt để chia sẻ lên mạng xã hội,
> Để tôi khoe thành tích đọc của mình và mời bạn bè cùng dùng app (viral loop).

## Acceptance Criteria (Gherkin)
```gherkin
Given đến 8:00 sáng Chủ Nhật hàng tuần
When người dùng mở app
Then xuất hiện Banner nổi bật "Bản Tóm Tắt Trí Tuệ Tuần Này Của Bạn (Brain Wrapped)"
When bấm vào xem và chọn "Chia sẻ lên Story"
Then ứng dụng xuất ảnh Compose canvas 1080x1920 với biểu đồ sắc nét và mở Android Share Sheet
```

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-04] "Thẻ Báo Cáo Tri Thức Động Hàng Tuần (Weekly Brain Wrapped 9:16)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-04_weekly-brain-wrapped-card.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — `BrainRpgPage.kt` `onShare` (dòng 115-127) hiện tại chỉ dùng `Intent.ACTION_SEND` với `type = "text/plain"`, KHÔNG có Compose canvas 1080x1920, KHÔNG có radar chart, KHÔNG có Sunday banner/reminder. Đây chính là task RPG-09 — cân nhắc làm chung hoặc tách MVP theo phạm vi mô tả ở RPG-09.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-04 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-04 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
> **Ghi chú audit (2026-09-06):** Card `WeeklyWrappedCard` hiển thị đúng trong trang Brain RPG và nút share hoạt động, nhưng chỉ tạo một chuỗi text đơn giản qua `Intent.ACTION_SEND` — KHÔNG có: banner tự động sáng Chủ Nhật, radar chart đa giác, render bitmap 1080x1920, logo/QR code. Đây là task có gap lớn nhất so với đặc tả gốc trong toàn bộ Epic 12 — điểm audit thực tế cho riêng phần "Wrapped" chỉ ở mức MVP tối thiểu, không đạt được trải nghiệm "story-ready" như mô tả. Xem RPG-09 để hoàn thiện hoặc chốt phạm vi MVP chính thức.
