# RPG-06 — Streak/Decay test với đồng hồ giả lập — DONE (chỉ test, giữ nguyên hành vi)

**Hoàn tất:** 2026-10-27 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Hiện trạng so với spec
Spec ghi `currentEpochDay()` gọi `System.currentTimeMillis()` cứng nên không test được. Điều đó **đã lỗi thời**: RPG-07 chuyển sang Room và
các hàm `awardReadingXpOnce`/`awardTriageXpOnce` đã nhận `nowMillis`, nên đồng hồ vốn đã inject được. Phần còn thiếu thật sự là **test**
cho logic streak/shield. Owner chọn (AskUserQuestion): **chỉ thêm test, giữ nguyên hành vi** — không đổi `nextStreak`, không đổi múi giờ.

## Test thêm (`BrainRpgRepositoryTest`, 14 test mới, tổng 31)
Đọc liên tục tăng 1/ngày · nhiều lần đọc cùng ngày chỉ tính 1 · nghỉ 1 ngày không shield → về 1 · nghỉ nhiều ngày → về 1 ·
nghỉ có shield → streak tiếp tục và shield bị tiêu · sau khi tiêu shield, lần nghỉ kế tiếp → về 1 · shield giữ nguyên khi đọc mỗi ngày ·
đồng hồ lùi → về 1 · ranh giới ngày là 00:00 UTC · quiz không đổi streak · đọc lại bài đã thưởng vào ngày sau vẫn tính ngày ·
triage tăng streak như đọc.

## Kiểm chứng
- `./gradlew testDevDebugUnitTest --rerun`: **1.445 test, 0 lỗi**.
- Mutation trên `nextStreak` (test phải đỏ): bỏ `+1` khi đọc hôm sau → 8 test đỏ; shield không bị tiêu → 5 đỏ; bỏ reset → 6 đỏ. Đã khôi phục.
- Không đổi code production nên không chạy lại lint/smoke thiết bị.

## Hành vi hiện tại mà test ghi nhận — CÓ THỂ LÀ LỖI, owner nên quyết định (chưa sửa)
1. **Ngày tính theo UTC** (`nowMillis / 86_400_000`), trong khi Wrapped (RPG-09) dùng giờ địa phương. Người dùng ở UTC+7 sẽ thấy ngày
   "đổi" lúc 07:00 sáng. Có test `streak_dayBoundaryIsUtcMidnight` ghi nhận điều này.
2. **Shield bắc cầu mọi khoảng nghỉ**, không chỉ 1 ngày (`streak_shieldBridgesAGapOfAnyLength`: nghỉ 40 ngày vẫn giữ streak).
3. **Đọc lại bài đã thưởng vào ngày sau vẫn tính là một ngày** (XP chỉ trao một lần nhưng streak thì không), nên có thể giữ streak bằng cách
   mở lại một bài cũ. Có thể là chủ ý (khuyến khích quay lại) hoặc kẽ hở.
4. **Quiz đúng không tăng streak**; chỉ đọc và triage mới tăng.
5. `activateStreakShield()` luôn trả `true` và không kiểm tra điều kiện (VIP/đã dùng/…): bất kỳ ai gọi cũng bật shield. Không có test cho
   điều kiện vì code không có.
6. Đồng hồ lùi (đổi giờ máy) làm streak về 1.
Nếu sửa bất kỳ điểm nào, cần quyết định dữ liệu `lastReadEpochDay` đã lưu (đang là UTC) cho người dùng hiện có.

## Giới hạn
Không có test với múi giờ thiết bị khác nhau (vì code hiện không dùng múi giờ). Không kiểm giao diện hay thông báo streak.
