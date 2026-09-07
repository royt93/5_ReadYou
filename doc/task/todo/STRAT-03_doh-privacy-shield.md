# [STRAT-03] Lá Chắn Quyền Riêng Tư: DNS-over-HTTPS (DoH) & Chặn Tracker Ngầm

- **Type:** Security & Privacy / Anti-Tracking
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** 11. AUTONOMOUS, COMMUNITY & PRIVACY — Tự Động Hóa Không Chạm, Chợ Nguồn Tin & Bảo Mật Tuyệt Đối
- **Location:** [`infrastructure/di/OkHttpClientModule.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt), [`infrastructure/rss/RssHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt)

## Vấn đề thực tế
**Bối cảnh:** Nhiều nhà mạng hoặc wifi công cộng có thể theo dõi thói quen đọc báo của người dùng thông qua DNS query dạng plain-text. Ngoài ra, nhiều trang báo mạng nhúng các pixel theo dõi (tracking pixels 1x1 GIF) và các tham số theo dõi URL (`utm_source`, `fbclid`, `gclid`) để theo dõi hành vi người đọc.

## User Story
> Là người coi trọng bảo mật và quyền riêng tư trực tuyến,
> Tôi muốn toàn bộ kết nối tải tin được mã hóa qua DNS-over-HTTPS và tự động thanh lọc các mã theo dõi ngầm,
> Để nhà mạng và bên thứ ba không thể thu thập hồ sơ thói quen đọc tin của tôi.

## Acceptance Criteria (Gherkin)
- **Given** tùy chọn "Lá Chắn Riêng Tư" trong Cài Đặt Bảo Mật
- **When** người dùng kích hoạt
- **Then** OkHttpClient sử dụng DNS-over-HTTPS (hỗ trợ chọn Cloudflare `1.1.1.1` hoặc AdGuard DoH)
- **And** tự động gọt bỏ toàn bộ tracking parameters (`utm_*`, `fbclid`, `_ga`, `gclid`) trên tất cả URL bài viết
- **And** bộ lọc HTML tự động loại bỏ các thẻ ảnh tracking pixel 1x1 và script quảng cáo bẩn trước khi hiển thị.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [STRAT-03] "Lá Chắn Quyền Riêng Tư: DNS-over-HTTPS (DoH) & Chặn Tracker Ngầm" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/STRAT-03_doh-privacy-shield.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [STRAT-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [STRAT-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
