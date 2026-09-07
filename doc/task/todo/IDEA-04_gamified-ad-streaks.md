# [IDEA-04] Chuỗi Thưởng Xem Quảng Cáo & Đọc Tin Nhận VIP (Gamified Ad Streaks)

- **Type:** Monetization / User Retention
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng (Ideas & Engagement)
- **Location:** [`ui/page/setting/vip/VipManagementPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/vip/VipManagementPage.kt)

## Vấn đề thực tế
Hiện tại chỉ có 1 nút bấm "Xem ad nhận 3 ngày VIP". Người dùng xem 1 lần rồi thôi. Nếu biến thành chuỗi nhiệm vụ hàng ngày: "Check-in đọc báo + Xem 1 ad mỗi ngày để cộng dồn chuỗi VIP", tỷ lệ người dùng quay lại app hàng ngày (DAU) và số lượt hiển thị Rewarded Ad (AppLovin/AdMob eCPM cao nhất) sẽ tăng vọt 300%.

## User Story
> Là người dùng miễn phí muốn dùng VIP lâu dài,
> Tôi muốn check-in xem 1 quảng cáo ngắn mỗi sáng để cộng dồn ngày VIP và duy trì chuỗi đọc,
> Để tôi vừa có động lực đọc báo mỗi ngày vừa được hưởng trọn vẹn quyền lợi VIP.

## Acceptance Criteria (Gherkin)
- **Given** người dùng mở màn hình Quản Lý VIP
- **When** bấm nút "Check-in nhận VIP" và xem hết 1 video quảng cáo
- **Then** app cộng thêm 24h hoặc 3 ngày VIP vào tài khoản thông qua `AdManager`
- **And** hiển thị thanh tiến độ chuỗi ngày (Streak 1/7 ngày, 7/7 ngày thưởng thêm 7 ngày VIP)
- **And** gửi thông báo nhắc nhẹ vào giờ người dùng thường đọc báo nếu chưa check-in.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [IDEA-04] "Chuỗi Thưởng Xem Quảng Cáo & Đọc Tin Nhận VIP (Gamified Ad Streaks)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/IDEA-04_gamified-ad-streaks.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [IDEA-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [IDEA-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
