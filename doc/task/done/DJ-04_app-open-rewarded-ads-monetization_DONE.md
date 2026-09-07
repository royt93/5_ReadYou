# [DJ-04] Tối Ưu Doanh Thu Buổi Sáng Với App Open & Rewarded Ads

- **Type:** New Feature
- **Priority:** `P0 (Critical Monetization)`
- **Estimation:** `5 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](../todo/14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`ui/component/commute/CommuteCastSheet.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastSheet.kt#L595-L664), [`infrastructure/android/NotificationHelper.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt#L132-L151), `RApp.kt` (App Open Ad lifecycle, wrapper-managed)

## Vấn đề thực tế
Khi người dùng click vào thông báo đẩy buổi sáng lúc 6:00–8:00 AM, `AdmobApplovinWrapper` hiển thị ngay một App Open Ad (khung giờ vàng có eCPM cao nhất trong ngày từ $15–$35). Thêm tùy chọn "Nghe phiên bản Chuyên Sâu 15 phút (Deep Dive)" thông qua 1 lượt xem Rewarded Ad.

## User Story
> Là chủ sở hữu ứng dụng,
> Tôi muốn khai thác App Open Ad khi user mở app từ notification buổi sáng và Rewarded Ad khi user muốn nghe bản Deep Dive,
> Để tối ưu doanh thu quảng cáo vào khung giờ vàng và tăng thời lượng sử dụng app.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bấm vào notification "Bản tin sáng CommuteCast"
- **When** ứng dụng chuyển từ nền lên foreground
- **Then** AdmobApplovinWrapper hiển thị App Open Ad toàn màn hình
- **When** quảng cáo đóng lại, bản tin âm thanh tự động tiếp tục phát mà không bị ngắt quãng

## ✅ Completion Report (audit 2026-09-06)

- **Điểm audit khách quan:** `7.5 / 10` (không giữ nguyên 9.9/10 đã tuyên bố ở epic gốc — điểm đó áp cho toàn epic 4 task, trong khi 3/4 task khác của epic có gap nghiêm trọng, xem `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md`. Riêng phần việc của DJ-04 tự nó làm khá tốt.)
- **Đã implement:**
  - `NotificationHelper.notifyCommuteCast()` (dòng 132-151) tạo `PendingIntent` mở `MainActivity` với extra `EXTRA_START_COMMUTE = true`; `HomeEntry.kt` đọc extra này để tự mở CommuteCast sheet khi app foreground trở lại.
  - Rewarded Ad flow cho Deep Dive: `DeepDiveBanner` trong `CommuteCastSheet.kt` (dòng 595-664) gọi `AdManager.showRewarded(activity) { earned -> ... }`, chỉ `onUnlocked()` (mở khóa episode 15 phút / 10 bài) khi `earned == true`; nếu không earned thì fallback `AdManager.showInterstitial`. Có kiểm tra VIP (`AdManager.isVipByKeyActive()`) để bỏ qua ads cho user đã mua VIP — đúng pattern monetization chuẩn của app.
- **Gap còn sót (không chặn "done" nhưng cần lưu ý):**
  1. App Open Ad khi mở app từ notification **không phải logic riêng cho CommuteCast** — nó dùng chung cơ chế `registerAppOpenAdLifecycle` toàn app (mọi lần app foreground đều có thể trigger App Open Ad, không riêng khung giờ 6-8AM). Về hành vi cuối cùng, AC vẫn được thỏa mãn (app open ad hiển thị khi mở từ notification), nhưng không có logic đặc thù "khung giờ vàng 6-8AM" hay throttle riêng cho luồng CommuteCast — nếu cần tối ưu eCPM theo khung giờ thật sự phải làm task riêng (không tạo task mới ở đây vì ngoài phạm vi 4 gap được yêu cầu audit).
  2. Không có test tự động nào xác nhận "audio tự động tiếp tục phát sau khi đóng App Open Ad không bị ngắt quãng" — hành vi này phụ thuộc SDK ads bên thứ 3 (`AdmobWrapper`), khó unit-test; chỉ có thể xác minh qua smoke test thủ công.
  3. Vì `CommuteAudioPlayer` là in-memory singleton (xem `doc/task/todo/DJ-05_persist-episode-notification-ready-state.md`), nếu process bị kill trước khi App Open Ad đóng, "tiếp tục phát không ngắt quãng" sẽ thất bại do không còn episode nào để phát — đây là hệ quả của gap DJ-05, không phải lỗi riêng của DJ-04.
- **Test hiện có:** Không có unit/androidTest test riêng cho luồng ads (`AdManager.showRewarded`/`showInterstitial` là SDK ngoài, khó mock trong repo hiện tại — không có file test nào match `*Rewarded*` hoặc `*AppOpen*` liên quan Commute).
- **Kết luận:** Task được coi là hoàn thành cho phần thuộc trách nhiệm của app (rewarded ad gating + notification deep-link), đặt vào `done/`. Không cần loop lại trừ khi có yêu cầu thêm test cho luồng ads hoặc xử lý riêng khung giờ vàng.
