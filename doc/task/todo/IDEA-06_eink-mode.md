# [IDEA-06] Chế Độ Tối Ưu Cho Màn Hình Giấy Điện Tử E-Ink (E-Paper Mode)

- **Type:** Accessibility / Specialized UX
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng (Ideas & Engagement)
- **Location:** [`ui/page/setting/color/ColorAndStylePage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/color/ColorAndStylePage.kt), [`ui/page/home/read/Content.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt)

## Vấn đề thực tế
Rất nhiều độc giả trung thành của RSS sử dụng máy đọc sách Android chạy màn hình E-Ink (Onyx Boox, Meebook, Xiaomi InkPalm). Màn hình E-Ink có tần số quét thấp, màu xám nhạt và bóng mờ khi cuộn mượt.

## User Story
> Là người đọc tin tức trên máy đọc sách E-Ink,
> Tôi muốn có chế độ hiển thị đơn sắc thuần túy (đen trắng tuyệt đối) và chuyển trang theo từng trang (Tap to turn page),
> Để màn hình không bị bóng mờ và pin máy đọc sách dùng được cả tuần.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bật chế độ "Tối ưu hóa E-Ink" trong Cài đặt giao diện
- **When** vào màn hình đọc bài
- **Then** toàn bộ hình ảnh và giao diện chuyển về thang độ tương phản cao (Pure Black & White)
- **And** tắt toàn bộ hiệu ứng chuyển động (animations = 0ms)
- **And** cho phép chạm vào 1/3 mép trái/phải màn hình hoặc bấm phím âm lượng để sang trang tiếp theo.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [IDEA-06] "Chế Độ Tối Ưu Cho Màn Hình Giấy Điện Tử E-Ink (E-Paper Mode)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/IDEA-06_eink-mode.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [IDEA-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [IDEA-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
```
