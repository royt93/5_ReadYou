# [FRONT-02] Tích Hợp Mạng Xã Hội Phi Tập Trung Web3 (Nostr, Bluesky AT Protocol & Mastodon)

- **Type:** Decentralized Web / Web3
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [10. FRONTIER — Ý Tưởng Đỉnh Cao & Mở Rộng Hệ Sinh Thái](10_FRONTIER_AND_ECOSYSTEM.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/decentralized/` (Gói mới)

## Vấn đề thực tế
Làn sóng chuyển dịch khỏi các mạng xã hội truyền thống (vốn bị kiểm duyệt gắt gao và thuật toán quảng cáo độc hại) sang các mạng phi tập trung như Nostr (dùng mã hóa công khai npub), Bluesky (AT Protocol) và Mastodon (ActivityPub).

## User Story
> Là người yêu thích tự do ngôn luận và mạng phi tập trung,
> Tôi muốn theo dõi trực tiếp các tài khoản Nostr, Bluesky hoặc Mastodon yêu thích ngay trong app,
> Để tôi đọc được tin tức gốc từ các tác giả uy tín mà không cần cài thêm các app mạng xã hội gây nghiện.

## Acceptance Criteria (Gherkin)
- **Given** người dùng nhập khóa công khai Nostr (`npub...`) hoặc handle Bluesky (`@user.bsky.social`)
- **When** app kết nối tới Nostr Relays qua WebSocket hoặc AT Protocol API
- **Then** các bài đăng mới nhất (Notes/Posts) được hiển thị sạch sẽ như một kênh tin RSS thông thường
- **And** không có thuật toán thao túng, hiển thị 100% theo trình tự thời gian thuần khiết.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FRONT-02] "Tích Hợp Mạng Xã Hội Phi Tập Trung Web3 (Nostr, Bluesky AT Protocol & Mastodon)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FRONT-02_decentralized-web3-social.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [FRONT-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [FRONT-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
