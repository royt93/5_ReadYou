# 🎮 EPIC 12 — Index: Brain RPG & Knowledge Wrapped (Game Hóa Việc Đọc & Viral Retention)

> **Mục tiêu epic:** Biến trải nghiệm đọc tin RSS từ thụ động thành một trò chơi nhập vai phát triển bản thân (Duolingo for Reading). Người dùng tích lũy XP theo danh mục kiến thức, làm bài kiểm tra hiểu biết AI cuối bài đọc, nhận thẻ "Brain Wrapped" hàng tuần để viral mạng xã hội. Cơ chế monetization dựa trên vòng lặp Rewarded Video (x2 XP, cứu streak, hồi sinh kỹ năng) và Interstitial khi thăng cấp (`AdmobApplovinWrapper`).
>
> File này từng là 1 epic gộp 4 task (`TASK-RPG-01..04`), đã được tách thành các file task riêng theo chuẩn `doc/task/_TEMPLATE_TASK.md`. File này giờ chỉ đóng vai trò **mục lục (Epic Index)** — nội dung chi tiết từng task nằm ở các file link bên dưới, KHÔNG xoá.
>
> **Quan trọng:** Completion Report gốc ở [`doc/task/done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md`](../done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md) tuyên bố epic này đã **HOÀN THÀNH điểm 9.9/10**. Audit lại code thực tế ngày 2026-09-06 (đọc `domain/model/rpg/`, `domain/repository/BrainRpgRepository.kt`, `domain/sv/QuizGeneratorService.kt`, `ui/page/rpg/`, `ui/component/rpg/`) phát hiện **5 gap nghiêm trọng** (3× P1, 2× P2) không được ghi nhận trong report gốc — bao gồm quiz "AI" thực chất là template tĩnh, XP có thể farm không giới hạn, và logic streak không kiểm thử được. Report DONE đã được cập nhật bổ sung mục "⚠️ Audit lại (phát hiện sau khi DONE)" với điểm đánh giá khách quan hạ xuống **4.5/10**. 5 task fix mới đã được tạo (`RPG-05..RPG-09`).

---

## 📋 Danh Sách Task (9 tasks — 4 gốc + 5 fix từ audit)

| # | Task | Priority | Story Points | Trạng thái | File |
|---|---|:---:|:---:|---|---|
| 1 | Cây Kỹ Năng Tri Thức (Constellation Skill Tree) | P1 | 5 SP | ✅ Done (đồ thị chòm sao 6 node, không thêm luật mở khoá) | [`../done/RPG-01_xp-skill-tree-system_DONE.md`](../done/RPG-01_xp-skill-tree-system_DONE.md) |
| 2 | AI Micro-Quiz Engine | P1 | 8 SP | 🟡 Triển khai; distractor đã theo ngữ cảnh ở RPG-05 (vẫn là heuristic, không dùng LLM) | [`RPG-02_ai-micro-quiz-engine.md`](RPG-02_ai-micro-quiz-engine.md) |
| 3 | Cognitive Decay & Streak Shield | P1 | 5 SP | 🟡 Triển khai một phần, thiếu decay thật — xem RPG-06 | [`RPG-03_cognitive-decay-streak-shield.md`](RPG-03_cognitive-decay-streak-shield.md) |
| 4 | Weekly "Brain Wrapped" 9:16 | P2 | 8 SP | ✅ MVP Done | [`../done/RPG-09_wrapped-full-spec-mvp_DONE.md`](../done/RPG-09_wrapped-full-spec-mvp_DONE.md) |
| 5 | Quiz distractor theo ngữ cảnh (đáp án là tiêu đề bài, distractor là tiêu đề bài khác trong Room) | **P1** | 5 SP | ✅ Done | [`RPG-05_quiz-distractors-context-aware_DONE.md`](../done/RPG-05_quiz-distractors-context-aware_DONE.md) |
| 6 | Streak/decay logic — test đồng hồ giả lập (chỉ test, giữ nguyên hành vi) | **P1** | 3 SP | ✅ Done (có 6 điểm hành vi đáng ngờ chờ owner quyết) | [`RPG-06_streak-decay-testable-clock_DONE.md`](../done/RPG-06_streak-decay-testable-clock_DONE.md) |
| 7 | **[MỚI]** Sai lệch tài liệu vs code: Room vs SharedPreferences, ngưỡng 80%+30s vs 75% | P2 | 5 SP | ✅ Done (Hướng A) | [`RPG-07_room-migration-or-doc-alignment_DONE.md`](../done/RPG-07_room-migration-or-doc-alignment_DONE.md) |
| 8 | **[MỚI]** XP farm exploit sau khi app bị kill/recreate — persist idempotency | **P1** | 3 SP | ✅ Done | [`RPG-08_persist-xp-awarded-articles.md`](../done/RPG-08_persist-xp-awarded-articles_DONE.md) |
| 9 | **[MỚI]** "Wrapped" chỉ là share text — bổ sung radar chart, ảnh 1080×1920, nhắc lịch Chủ Nhật | P2 | 8 SP | ✅ MVP Done | [`RPG-09_wrapped-full-spec-mvp.md`](../done/RPG-09_wrapped-full-spec-mvp_DONE.md) |

**Tổng Story Points:** 26 SP (gốc) + 24 SP (fix mới) = **50 SP**

---

## 📊 Ghi chú
- Report DONE gốc: [`doc/task/done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md`](../done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md) — đã bổ sung mục "⚠️ Audit lại (phát hiện sau khi DONE)" với điểm đánh giá khách quan mới (4.5/10) và danh sách 5 gap chi tiết.
- Ưu tiên xử lý trước: `RPG-05`, `RPG-06`, `RPG-08` (đều P1, ảnh hưởng trực tiếp tính toàn vẹn của hệ thống game hoá — quiz giả và XP farm).
- `RPG-07` nên chạy trước hoặc song song `RPG-08` vì quyết định kiến trúc lưu trữ (Room vs SharedPreferences) ảnh hưởng cách implement persist idempotency.
