# [ORACLE-04] Kinh Tế Điểm Thưởng & Cỗ Máy In eCPM Rewarded Ads

- **Type:** New Feature / Monetization
- **Priority:** `P0 (Critical Monetization)`
- **Estimation:** `5 Story Points`
- **Epic:** [13. ORACLE — The Oracle Feed (Thị Trường Dự Đoán Tin Tức)](13_THE_ORACLE_PREDICTION_MARKET.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/RApp.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/RApp.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/setting/vip/VipManagementPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/vip/VipManagementPage.kt), [`app/src/main/java/com/mckimquyen/reader/sdkadbmob/ComposeBannerAd.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/sdkadbmob/ComposeBannerAd.kt)

## Vấn đề thực tế
Xây dựng hệ thống nạp điểm Intel Points thông qua Rewarded Video của `AdmobApplovinWrapper:1.1.5`. Cung cấp 2 tính năng độc quyền:
1. *Nạp Năng Lượng:* Xem 1 video = +500 Intel Points (tối đa 5 lần/ngày).
2. *AI Oracle Edge:* Xem 1 video để mở khóa phân tích chuyên sâu của AI về xác suất xảy ra sự kiện dựa trên dữ liệu lịch sử.

Repo đã tích hợp sẵn `AdManager`/`AdSdkConfig` từ AdmobWrapper (`RApp.setupAdmob()`, dùng ở `VipManagementPage.kt` cho luồng "xem ad nhận VIP" tương tự) — task này tái sử dụng cùng cơ chế `showRewardedVideo()` cho luồng nạp Intel Points, phụ thuộc bảng `user_wallet` (ORACLE-02).

## User Story
> Là người dùng miễn phí đã cạn điểm Intel Points,
> Tôi muốn xem quảng cáo thưởng để nạp thêm điểm hoặc mở khóa phân tích AI chuyên sâu,
> Để tôi tiếp tục tham gia đặt cược dự đoán mà không cần trả phí.

## Acceptance Criteria (Gherkin)
- **Given** người dùng còn dưới 50 Intel Points
- **When** bấm nút "Nạp Thêm Điểm Miễn Phí"
- **Then** gọi AdmobApplovinWrapper.showRewardedVideo()
- **And** khi callback onUserEarnedReward kích hoạt, cộng ngay 500 Points vào ví
- **And** hiển thị Banner cảm ơn nhà tài trợ

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ORACLE-04] "Kinh Tế Điểm Thưởng & Cỗ Máy In eCPM Rewarded Ads" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ORACLE-04_rewarded-ads-points-economy.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ORACLE-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ORACLE-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
