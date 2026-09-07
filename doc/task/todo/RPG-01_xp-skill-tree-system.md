# [RPG-01] Hệ Thống Điểm Kinh Nghiệm (XP) & Cây Kỹ Năng Tri Thức (Knowledge Skill Tree)

- **Type:** Feature
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/model/rpg/BrainRpg.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpg.kt), [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt), [`ui/page/home/read/Content.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt#L61-L86), [`ui/page/rpg/BrainRpgViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt)

> **Trạng thái:** ✅ Đã triển khai (xem [`doc/task/done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md`](../../done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md)), nhưng audit lại phát hiện gap thực thi khác với đặc tả gốc (ngưỡng 75% thay vì 80%+30s, dùng SharedPreferences thay vì Room, XP có thể farm khi kill app). Xem các task fix nối tiếp: [`RPG-06`](RPG-06_streak-decay-testable-clock.md), [`RPG-07`](RPG-07_room-migration-or-doc-alignment.md), [`RPG-08`](RPG-08_persist-xp-awarded-articles.md).

## Vấn đề thực tế / Mô tả
Thiết kế kiến trúc Room DB lưu trữ bảng `user_progress` (XP, cấp độ, streak ngày đọc) và `skill_node` (các nhánh kỹ năng: Tech & AI, Macroeconomics, Health & Biohacking, Philosophy, Design...). Khi người đọc cuộn hết 80% độ dài bài viết và ở lại tối thiểu 30 giây, hệ thống tự động cộng XP theo chủ đề được AI phân loại.

## User Story
> Là người dùng đọc tin tức trong RSS Cat Hub,
> Tôi muốn tích lũy điểm kinh nghiệm (XP) và thấy cây kỹ năng tri thức của mình phát triển theo từng chủ đề tôi đọc,
> Để tôi có động lực nội tại quay lại đọc tin đều đặn mỗi ngày (game hóa việc đọc).

## Acceptance Criteria (Gherkin)
```gherkin
Given người dùng đang đọc một bài viết thuộc chuyên mục "AI & Công nghệ"
When người dùng cuộn đọc qua 80% bài viết với thời lượng trên 30 giây
Then hệ thống bắn sự kiện hiệu ứng vi mô (+50 XP Tech) bay nhẹ ở góc màn hình
And cập nhật cấp độ và thanh tiến trình trong Room DB không gây giật lag khung hình
```

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-01] "Hệ Thống Điểm Kinh Nghiệm (XP) & Cây Kỹ Năng Tri Thức" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-01_xp-skill-tree-system.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — code hiện tại dùng SharedPreferences (không phải Room) và ngưỡng 75% scroll không kèm điều kiện 30 giây (xem Content.kt dòng 61-86, BrainRpgRepository.kt). Đối chiếu với RPG-06/RPG-07/RPG-08 để tránh làm trùng công việc đã tách task riêng.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới. Nếu migrate sang Room, export schema mới vào app/schemas và viết Migration tương ứng.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-01 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-01 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
> **Ghi chú audit (2026-09-06):** Code hiện tại đã triển khai cơ chế cộng XP theo chủ đề (`BrainRpgRepository.addReadingXp`), nhưng dùng `SharedPreferences` thay vì Room, và ngưỡng kích hoạt là 75% cuộn (`Content.kt` dòng 77: `totalItems * 0.75f`) — KHÔNG có điều kiện thời gian đọc tối thiểu 30 giây như đặc tả. Task này coi như "implemented nhưng lệch spec" — xem RPG-06/07/08 để fix từng gap cụ thể thay vì làm lại toàn bộ ở đây.
