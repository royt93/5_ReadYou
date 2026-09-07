# [STRAT-02] Chợ Nguồn Tin Khám Phá & Bộ Sưu Tập Cộng Đồng (Curated Feed Marketplace)

- **Type:** Growth / Content Discovery
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** 11. AUTONOMOUS, COMMUNITY & PRIVACY — Tự Động Hóa Không Chạm, Chợ Nguồn Tin & Bảo Mật Tuyệt Đối
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/marketplace/` (Gói mới)

## Vấn đề thực tế
**Bối cảnh:** Trở ngại lớn nhất của 90% người dùng mới khi dùng RSS là: *"Tôi thích đọc tin nhưng không biết kiếm link RSS ở đâu"*. Các app khác bắt người dùng phải tự copy/paste URL thủ công rất phiền phức.

## User Story
> Là người mới dùng RSS hoặc muốn tìm thêm nguồn tin mới chất lượng,
> Tôi muốn mở "Chợ Nguồn Tin" để duyệt các bộ sưu tập được tuyển chọn sẵn theo chủ đề (AI, Tài chính, Lập trình, Báo chí Việt Nam),
> Để tôi có thể đăng ký hàng loạt nguồn tin hay chỉ bằng 1 chạm.

## Acceptance Criteria (Gherkin)
- **Given** tab "Khám Phá" trên thanh điều hướng
- **When** người dùng mở xem các danh mục:
  - 🤖 *Công Nghệ & AI Tuyển Chọn* (OpenAI, DeepMind, TechCrunch, The Verge)
  - 📈 *Kinh Tế & Đầu Tư* (VnEconomy, CafeF, Bloomberg, Financial Times)
  - 🇻🇳 *Thời Sự Việt Nam* (VnExpress, Tuổi Trẻ, Thanh Niên, Dân Trí)
  - 🎨 *Thiết Kế & Sáng Tạo* (Smashing Mag, Behance, Muzli)
- **Then** người dùng có thể xem trước các bài viết gần nhất của từng nguồn
- **And** có nút "Đăng ký toàn bộ danh mục" (1-tap subscribe to bundle) tự động tạo nhóm và thêm feeds vào app.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [STRAT-02] "Chợ Nguồn Tin Khám Phá & Bộ Sưu Tập Cộng Đồng (Curated Feed Marketplace)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/STRAT-02_curated-feed-marketplace.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [STRAT-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [STRAT-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
