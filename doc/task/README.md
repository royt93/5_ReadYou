# 📋 Product Backlog & Scrum Master Roadmap — RSS Cat Hub

> **Dự án:** RSS Cat Hub (`com.mckimquyen.reader`)  
> **Phiên bản hiện tại:** `2026.09.06` | **Target SDK:** 37 (Android 16) | **Kotlin:** 2.1.0  
> **Kiến trúc & Monetization:** Single-Activity, Jetpack Compose, Hilt, Room, WorkManager, **giữ nguyên `AdmobApplovinWrapper:1.1.5`** (AppLovin MAX / AdMob + VIP system by key/rewarded ad).  
> **Scrum Framework:** 2-Week Sprints | Fibonacci Story Points (1, 2, 3, 5, 8) | Definition of Done (DoD)

---

## 🎯 Tổng Quan Phân Bổ Backlog (109 Tasks, mỗi task = 1 file riêng)

> **Cập nhật 2026-09-06:** Đã audit lại toàn bộ 70 task gốc đối chiếu code thực tế (qua 3 nguồn phân tích độc lập: tự đọc source + `codex exec` + `claude` nested, hội tụ nhiều phát hiện trùng khớp), tách MỖI task thành 1 file riêng trong `doc/task/todo/` kèm `🔁 Loop Prompt` + `🏁 Tín hiệu kết thúc loop` (xem [`_TEMPLATE_TASK.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/_TEMPLATE_TASK.md)), bổ sung 39 task mới phát hiện qua audit (bug bảo mật, race condition, tính năng "DONE" thực ra chưa hoàn thiện...), và di dời 7 task đã thực sự hoàn thành sang `doc/task/done/`. Mỗi file epic bên dưới giờ là 1 "Epic Index" — vào file để xem bảng liệt kê + link tới từng task con. Story Points nay ghi trong từng file task riêng (không tổng hợp tập trung nữa vì độ chi tiết đã tăng nhiều).

| Epic / Danh Mục | Task (todo + done) | Prefix | Độ Ưu Tiên Cao Nhất | Epic Index |
|---|:---:|:---:|:---:|---|
| **1. FIX — Sửa Lỗi, Ổn Định & Ad Unit** | 15 | `FIX-` | **P0 (Blocker)** | [`01_FIX_BUGS_AND_STABILITY.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/01_FIX_BUGS_AND_STABILITY.md) |
| **2. ENHANCE — Tối Ưu & Nâng Cấp UX** | 6 | `ENH-` | **P1 (High)** | [`02_ENHANCE_PERFORMANCE_AND_UX.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/02_ENHANCE_PERFORMANCE_AND_UX.md) |
| **3. NEW — Tính Năng Mới Chuẩn RSS** | 5 | `NEW-` | **P1 (High)** | [`03_NEW_CORE_FEATURES.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/03_NEW_CORE_FEATURES.md) |
| **4. IDEAS — Ý Tưởng Tăng Trưởng & Media** | 8 | `IDEA-` | **P2 (Medium)** | [`04_IDEAS_AND_GROWTH.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/04_IDEAS_AND_GROWTH.md) |
| **5. EXCLUSIVE — Tính Năng Độc Quyền "Killer"** | 14 (1 done: EXC-06) | `EXC-` | **P1 (High)** | [`05_EXCLUSIVE_KILLER_FEATURES.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md) |
| **6. KNOWLEDGE — AI Gom Cụm & Second Brain** | 7 (2 done: KNOW-01/02) | `KNOW-` | **P1 (High)** | [`06_NEXT_GEN_AI_KNOWLEDGE.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/06_NEXT_GEN_AI_KNOWLEDGE.md) |
| **7. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau** | 4 | `INGEST-` | **P1 (High)** | [`07_UNIVERSAL_INGESTION_AND_SYNC.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/07_UNIVERSAL_INGESTION_AND_SYNC.md) |
| **8. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối** | 8 | `ZEN-` | **P1 (High)** | [`08_ZEN_FOCUS_AND_SPEED_READING.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md) |
| **9. REELS — Thẻ Lướt Dọc TikTok, Video PiP & Watchdog** | 6 (1 done: REEL-03) | `REEL-` | **P1 (High)** | [`09_VISUAL_REELS_AND_MEDIA.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/09_VISUAL_REELS_AND_MEDIA.md) |
| **10. FRONTIER — Báo Cáo McKinsey, Web3 & Xe Hơi** | 4 | `FRONT-` | **P1 (High)** | [`10_FRONTIER_AND_ECOSYSTEM.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/10_FRONTIER_AND_ECOSYSTEM.md) |
| **11. AUTONOMOUS — Zero-Click Agent & Privacy Vault** | 3 | `STRAT-` | **P1 (High)** | [`11_AUTONOMOUS_COMMUNITY_PRIVACY.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/11_AUTONOMOUS_COMMUNITY_PRIVACY.md) |
| **12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped** | 9 | `RPG-` | **P1 (High)** | [`12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md) |
| **13. ORACLE — Thị Trường Dự Đoán Polymarket Trên RSS** | 4 | `ORACLE-` | **P0 (Critical Ad)** | [`13_THE_ORACLE_PREDICTION_MARKET.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/13_THE_ORACLE_PREDICTION_MARKET.md) |
| **14. COMMUTECAST — Radio AI 6:00 Sáng, Android Auto & Lockscreen** | 8 (1 done: DJ-04) | `DJ-` | **P0 (Critical Ad)** | [`14_COMMUTECAST_AUTONOMOUS_AI_DJ.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/14_COMMUTECAST_AUTONOMOUS_AI_DJ.md) |
| **15. BOUNTY HUNTER — Agent Điều Tra Sâu & Mạng Nhện Obsidian** | 4 | `BOUNTY-` | **P0 (Critical Ad)** | [`15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/15_BOUNTY_HUNTER_AGENT_AND_GRAPH.md) |
| **16. ECHOCHAMBER — La Bàn Thiên Kiến & Đấu Trường Phản Biện** | 4 | `ECHO-` | **P1 (High)** | [`16_ECHOCHAMBER_AND_BIAS_COMPASS.md`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md) |
| **TỔNG CỘNG** | **109 tasks** (7 done, 102 todo) | — | — | — |

### ⚠️ Audit lại — 4 epic từng tuyên bố DONE nhưng có gap thực tế
Điểm đã được chấm lại khách quan sau khi đối chiếu code thật (xem mục "⚠️ Audit lại" trong từng file):
- `done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md` — RSVP paragraph-pause chết logic, lịch Daily Edition sai giờ → task fix mới: ZEN-04, ZEN-05, ZEN-06.
- `done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md` — điểm ~~9.9~~ → **4.5/10**: quiz "AI" là template giả, streak logic không test được, XP farm exploit → task fix mới: RPG-05..09.
- `done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` — điểm ~~9.9~~ → **5.5/10**: episode không persist, "Dual-Voice TTS" thực chất 1 giọng pitch-shift → task fix mới: DJ-05..08.
- `done/01_FOUNDATION_STABILITY_DONE.md` — audit lại khớp code thực tế, không phát hiện sai lệch.

---

## 🚦 Quy Định Mức Độ Ưu Tiên (Priority Matrix)

- **P0 (Blocker / Critical):** Ảnh hưởng trực tiếp đến độ ổn định, crash ngầm, vi phạm Play Store Policy, hoặc memory leak nghiêm trọng. Bắt buộc fix ngay trong Sprint hiện tại.
- **P1 (High):** Tính năng cốt lõi hoặc tối ưu hiệu năng có tác động lớn đến trải nghiệm người dùng và tỷ lệ giữ chân (Retention / Ratings).
- **P2 (Medium):** Tính năng mở rộng nâng cao, tự động hóa, tăng tiện ích cho power-user.
- **P3 (Low / Nice-to-have):** Ý tưởng sáng tạo, viral sharing, giao diện trang trí.

---

## 📦 Định Nghĩa Hoàn Thành (Definition of Done - DoD)

Một task chỉ được kéo từ `doc/task/todo/` sang `doc/task/inprogress/` và cuối cùng vào `doc/task/done/` khi thỏa mãn 100% các tiêu chí sau:
1. **Code Quality:** Không phát sinh compiler warnings (`./gradlew clean :app:compileProdDebugKotlin` ra 0 warning).
2. **Architecture:** Tuân thủ phân tầng `domain/`, `infrastructure/`, `ui/`. Không gọi DAO trực tiếp từ Composable.
3. **Threading:** Mọi tác vụ I/O, Database, Network, Heavy Parsing đều chạy trên `Dispatchers.IO` hoặc `Dispatchers.Default`. Tuyệt đối không block Main Thread.
4. **Lifecycle & Memory:** Không lưu `Context`, `Activity`, hoặc Compose `LazyListState` vào ViewModel hay static Singleton.
5. **UI & Theme:** Hỗ trợ đầy đủ Material You Dynamic Color, Dark Mode, AMOLED, không vỡ layout trên tablet/màn hình gập.
6. **Localization:** Toàn bộ text UI phải có trong `strings.xml` của cả 6 ngôn ngữ (`en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`).
