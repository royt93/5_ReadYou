# [RPG-07] Sai Lệch Tài Liệu vs Code: Room vs SharedPreferences, Ngưỡng 80%+30s vs 75%, Thiếu Cognitive Decay Worker

- **Type:** Architecture / Documentation Debt
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt) (dùng `SharedPreferences`), [`ui/page/home/read/Content.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt#L61-L86) (ngưỡng 75%, không có điều kiện thời gian), [`doc/task/todo/RPG-01_xp-skill-tree-system.md`](RPG-01_xp-skill-tree-system.md), [`infrastructure/db/AndroidDb.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt) (Room DB hiện tại, version 7, để tham khảo nếu chọn hướng migrate)

## Vấn đề thực tế
Tài liệu gốc (Epic 12 / `RPG-01`) mô tả:
- Lưu trữ bằng **Room DB** (bảng `user_progress`, `skill_node`).
- Ngưỡng cộng XP: **80% cuộn bài + tối thiểu 30 giây** đọc.
- Cơ chế **"cognitive decay"**: XP/kỹ năng giảm dần nếu không ôn tập >7 ngày.

Code thực tế (`BrainRpgRepository.kt`, `Content.kt`):
- Lưu bằng **`SharedPreferences`** (`context.getSharedPreferences("brain_rpg_prefs", ...)`), không phải Room.
- Ngưỡng chỉ kiểm tra **75% scroll** (`Content.kt` dòng 77: `lastVisible >= (totalItems * 0.75f).toInt()`) — **thiếu hoàn toàn điều kiện thời gian đọc tối thiểu 30 giây**, nghĩa là người dùng có thể fling nhanh xuống cuối bài trong <1 giây và vẫn được cộng XP.
- **Không có worker/job nào** tính "cognitive decay" — XP theo category không giảm dần theo thời gian.

Sai lệch này khiến tài liệu đặc tả (dùng làm căn cứ để tính điểm 9.9/10 trong Completion Report) không phản ánh đúng implementation thực tế.

## User Story
> Là chủ sản phẩm/QA đối chiếu tài liệu với code thực tế,
> Tôi muốn tài liệu và implementation khớp nhau (hoặc code được nâng cấp đúng đặc tả),
> Để các quyết định roadmap sau này (ví dụ đồng bộ nhiều thiết bị, query nâng cao theo category) không bị chặn bởi kiến trúc lưu trữ sai lệch so với tài liệu.

## Acceptance Criteria (Gherkin)
- **Given** đội ngũ chọn 1 trong 2 hướng xử lý task này (ghi rõ hướng đã chọn trong Completion Report)
- **Hướng A — Migrate đúng đặc tả gốc:**
  - **When** implement xong
  - **Then** `user_progress` và `skill_node` được lưu trong Room DB (có migration từ dữ liệu `SharedPreferences` cũ sang Room, không mất dữ liệu người dùng hiện có)
  - **And** điều kiện cộng XP là `scrollProgress >= 0.80f AND thời gian đọc >= 30_000ms`
  - **And** có `Worker` (WorkManager + Hilt) chạy định kỳ tính "cognitive decay" giảm dần XP hiển thị/hiệu ứng theo category không được ôn tập >7 ngày
- **Hướng B — Sửa tài liệu khớp thực tế + vá tối thiểu chống XP-farm:**
  - **When** implement xong
  - **Then** tài liệu Epic 12 và RPG-01/03 được cập nhật để phản ánh đúng: lưu bằng SharedPreferences, ngưỡng scroll thực tế
  - **And** BẮT BUỘC bổ sung tối thiểu điều kiện thời gian đọc tối thiểu (ví dụ 15-30 giây, do team quyết định) vào `Content.kt` trước khi gọi `onArticleReadFinished`, để chống XP-farm bằng cách fling nhanh xuống cuối bài (liên quan `RPG-08`)
- **And** dù chọn hướng nào, `BrainRpgRepositoryTest`/test tương ứng phải cập nhật để phản ánh đúng ngưỡng/kiến trúc mới

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-07] "Sai Lệch Tài Liệu vs Code: Room vs SharedPreferences, Ngưỡng 80%+30s vs 75%" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-07_room-migration-or-doc-alignment.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra BrainRpgRepository.kt còn dùng SharedPreferences không, Content.kt còn ngưỡng 75% không kèm thời gian không.
2. QUYẾT ĐỊNH hướng xử lý (A hoặc B) dựa trên đánh giá chi phí/lợi ích thực tế — ưu tiên hỏi người dùng/chủ dự án nếu môi trường cho phép tương tác; nếu chạy tự động không tương tác được, mặc định chọn Hướng B (rẻ hơn, không rủi ro migration mất dữ liệu người dùng hiện có) trừ khi có yêu cầu rõ ràng khác. Ghi rõ hướng đã chọn và lý do vào đầu Completion Report.
3. Implement theo Acceptance Criteria của hướng đã chọn. Nếu chọn Hướng A: viết Room entity + DAO mới trong domain/repository (theo pattern AccountDao/FeedDao hiện có), viết migration đọc dữ liệu cũ từ SharedPreferences "brain_rpg_prefs" sang bảng Room mới (chạy 1 lần, không mất dữ liệu), export schema vào app/schemas, thêm điều kiện thời gian đọc 30s vào Content.kt (dùng LaunchedEffect + delay hoặc đo timestamp bắt đầu đọc). Nếu chọn Hướng B: cập nhật lại "Vấn đề thực tế" trong RPG-01.md và RPG-03.md để khớp thực tế (ghi chú rõ đã sửa), thêm điều kiện thời gian đọc tối thiểu vào Content.kt trước khi gọi onArticleReadFinished (đây cũng chính là 1 phần của RPG-08 — phối hợp, không làm trùng lặp không cần thiết).
4. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), Room schema export nếu có thay đổi, localize đủ 6 ngôn ngữ nếu có text UI mới.
5. Với thay đổi kiến trúc/thiết kế quan trọng (bắt buộc cho task này vì ảnh hưởng kiến trúc lưu trữ), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt hướng đi:
   - `codex exec -s workspace-write "Review approach cho task RPG-07 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm, hướng A hay B, vì sao>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-07 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm, hướng A hay B, vì sao>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
6. Build kiểm tra: `./gradlew assembleDevDebug`.
7. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm; nếu chọn Hướng B mà chỉ sửa tài liệu không thêm điều kiện chống farm thì KHÔNG đạt AC, điểm phải phản ánh đúng.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`) — nếu Hướng A: test Room DAO + migration; nếu Hướng B: test điều kiện thời gian đọc tối thiểu.
3. Bổ sung **widget/Compose UI test** cho thay đổi hành vi UI trong `Content.kt` (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository nếu Hướng A).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, đọc 1 bài và xác nhận XP chỉ cộng sau khi thỏa ngưỡng mới (không fling nhanh farm được), ghi lại bằng chứng cụ thể (logcat hoặc mô tả quan sát).
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm, hướng đã chọn A/B).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
