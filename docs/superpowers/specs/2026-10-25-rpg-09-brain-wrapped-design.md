# Thiết Kế: [RPG-09] Brain Wrapped 9:16 — Ảnh Tổng Kết Tuần

- **Mã task:** RPG-09
- **Loại:** Feature Gap
- **Ưu tiên:** P2 (Medium)
- **Ngày:** 2026-10-25
- **Nhánh:** `dev`
- **Thiết kế được duyệt:** MVP, phong cách Neon celebratory

## 1. Mục tiêu và phạm vi

Brain RPG hiện chia sẻ một đoạn text all-time qua Android Share Sheet. MVP thay luồng đó bằng ảnh PNG tỉ lệ 9:16 (1080×1920), tóm tắt XP đạt được trong tuần lịch, streak đọc hiện tại và category nhận nhiều XP nhất trong tuần. Người dùng chọn “Chia sẻ lên Story” tại `WeeklyWrappedCard`; app dựng ảnh và mở Android Sharesheet với PNG qua FileProvider.

Phạm vi MVP: event log cho các XP award mới, truy vấn thống kê tuần, renderer Canvas, lưu file tạm, UI share state và test. Không nằm trong MVP: radar chart, QR tải app, banner/nhắc Chủ Nhật, danh hiệu Top X%, backfill XP lịch sử trước khi cài phiên bản có event log.

Nếu tuần hiện tại chưa có XP event, nút share disabled và UI giải thích cần nhận XP trong tuần. Topic lấy từ các XP event tuần; nếu chưa có category event, ẩn topic hoặc hiện trạng thái “đang hình thành”, không suy đoán từ all-time XP. Streak hiển thị streak hiện tại tại thời điểm chia sẻ, có nhãn “Current streak”; không giả định đây là streak riêng trong tuần.

## 2. Quy tắc thời gian và số liệu

- Tuần chạy từ **Thứ Hai 00:00** đến **Thứ Hai kế tiếp 00:00**, theo timezone địa phương của thiết bị tại thời điểm truy vấn.
- Weekly XP = tổng `xpDelta` của event có `createdAtMillis` trong `[weekStartMillis, weekEndMillis)`.
- Weekly topic = category có tổng `xpDelta` lớn nhất trong cùng khoảng; tie-break ổn định theo `categoryId` tăng dần.
- Current streak = `BrainRpgProfileEntity.streakDays` hiện tại.
- Event log chỉ bắt đầu sau migration. Không backfill lịch sử vì DB cũ không lưu timestamp/category cho XP awards; không tạo dữ liệu giả.

## 3. Kiến trúc và data flow

### 3.1 Room v19

Tạo `BrainRpgXpEventEntity` trong bảng `brain_rpg_xp_event`:
- `id: Long` auto-generated primary key.
- `categoryId: String`.
- `xpDelta: Long`.
- `rewardType: BrainRpgRewardType` (converter Room hiện có).
- `createdAtMillis: Long`.

Thêm index trên `createdAtMillis` và `(categoryId, createdAtMillis)`. `MIGRATION_18_19` chỉ tạo bảng và index; không chèn event cho profile cũ. Room schema v19 phải được export và lưu snapshot.

### 3.2 Ghi XP event nguyên tử

Mọi nhánh award được ghi event trong cùng `@Transaction` DAO operation đã claim ledger và cập nhật profile/category:
- READ: category do caller cung cấp, amount award thật, timestamp của award.
- QUIZ: category của quiz, amount quiz award thật.
- DOUBLE: category của quiz gốc, bonus XP; ledger vẫn chỉ cấp khi QUIZ đã claim.
- TRIAGE: category `General Knowledge`, amount triage award.

Event chỉ insert nếu reward ledger claim mới thành công. Nếu transaction lỗi, event/profile/category/ledger cùng rollback. Các hành động không award XP (sai quiz, shield, recovery) không tạo event.

### 3.3 Weekly summary và UI

Thêm DAO projection/query nhận `weekStartMillis`, `weekEndMillis` và trả `weeklyXp` cùng aggregate theo category. Repository tạo summary kết hợp query event với streak hiện tại. `BrainRpgViewModel` cung cấp `StateFlow`/hàm tải summary và share state; Compose không gọi DAO trực tiếp.

Khi user bấm nút share:
1. UI chỉ cho kích hoạt nếu `weeklyXp > 0`.
2. Renderer thuần Android `Canvas` vẽ ảnh PNG 1080×1920 theo palette Neon celebratory.
3. Render chạy trên `Dispatchers.Default`; ghi file cache dưới thư mục riêng và cleanup file cũ chạy trên `Dispatchers.IO`.
4. Dùng FileProvider hiện có (`cache-path`), `Intent.ACTION_SEND`, MIME `image/png`, `EXTRA_STREAM`, `ClipData` và `FLAG_GRANT_READ_URI_PERMISSION`; mở chooser từ `Activity` để không cần `FLAG_ACTIVITY_NEW_TASK`.
5. Không chia sẻ text-only fallback; lỗi render/write/intent hiển thị localized error và không mất dữ liệu Room.

## 4. Bố cục ảnh — Neon celebratory

- Canvas: 1080×1920 px; kiểm soát vùng an toàn để nội dung không bị cắt bởi Story UI.
- Nền navy/tím gradient tối; chữ tương phản cao.
- Header: “YOUR WEEK IN KNOWLEDGE” và khoảng ngày tuần địa phương.
- Hero: headline ngắn và số Weekly XP lớn, lavender glow bằng shadow/layer native.
- Hai ô số liệu phụ: current streak và weekly top topic.
- Cuối ảnh: tagline ngắn và wordmark `RSS Cat Hub`.
- Tất cả text hiển thị được truyền từ resource/localized model; renderer không tự tạo câu tiếng Anh/Vietnamese.
- Nếu không có topic, bỏ ô topic hoặc dùng trạng thái localized “đang hình thành”; không hiện category all-time.

## 5. Lỗi và edge cases

- Không có XP event tuần: disable share button, cho biết cần đọc/hoàn thành hoạt động để kiếm XP tuần.
- Có XP nhưng không có category aggregate hợp lệ: vẫn share XP/streak; topic hiển thị trạng thái trống đã localize.
- Category tie: chọn `categoryId` theo thứ tự tăng dần, để kết quả xác định.
- Tuần đổi lúc truy vấn: tính khoảng tuần một lần, truyền cùng boundary xuyên suốt query/summary/render.
- Đổi timezone: tuần được tính theo timezone thiết bị lúc share; event timestamps vẫn giữ epoch millis.
- Render/PNG compression thất bại: không mở chooser, hiện lỗi localized; dọn file tạm một cách an toàn.
- Cache cleanup lỗi: không làm hỏng file ảnh mới; chỉ xóa file cũ trong thư mục riêng của Wrapped.
- FileProvider cấp read permission cho chooser recipient; không cấp URI ngoài cache share directory.
- Legacy profile: không backfill timestamp giả; tuần đầu chỉ gồm award tạo sau migration.

## 6. Localization

Thêm key UI tối thiểu cho nút “Chia sẻ lên Story”, progress/loading/error, zero-week-XP guidance, title, header, weekly XP, current streak, top topic/empty topic, tagline và chooser title. Duy trì parity trên toàn bộ 38 maintained locales bằng `StringResourceParityTest`. Renderer chỉ nhận text đã localized, không phụ thuộc `Context`/resource lookup.

## 7. Kiểm thử

- **Room migration:** v18→v19 tạo bảng/index, giữ nguyên profile/ledger/category totals và không sinh event backfill.
- **DAO transaction:** READ/QUIZ/DOUBLE/TRIAGE award tạo đúng một event với category, amount, type, time; duplicate reward không tạo event; transaction fault rollback cả ledger/profile/category/event.
- **Weekly stats:** Monday boundary, Sunday final millisecond, Monday next-week exclusion, timezone boundary, empty week, category tie và aggregate đúng.
- **Unit renderer:** bitmap đúng 1080×1920, PNG encode được, palette/layout render không crash với topic rỗng và chuỗi dài.
- **Share helper:** FileProvider URI, MIME `image/png`, `EXTRA_STREAM`, `ClipData`, grant-read flag, chooser title; cleanup chỉ xóa file quá hạn trong Wrapped cache dir.
- **Compose UI:** XP tuần bằng 0 thì disabled guidance; XP dương thì share enabled; loading/error/success state.
- **Integration/device smoke:** nâng DB thật qua migration, tạo award, xác nhận summary tuần, bấm share và xác nhận Sharesheet nhận PNG kích thước đúng; kiểm ảnh hiển thị trên Pixel 7 Pro. Không restore snapshot nếu thao tác smoke tạo dữ liệu mới mà owner muốn giữ; trước test device phải backup DB và dừng ngay khi interstitial che UI theo R4.

## 8. Các phương án đã cân nhắc

1. **Android Canvas renderer + FileProvider (chọn):** tái dùng pattern `QuoteCardRenderer`/`QuoteCardSharer`; renderer thuần và dễ unit test, không cần dependency.
2. **ComposeView off-screen capture:** UI component dùng chung preview/export nhưng lifecycle/measure/draw phức tạp và khó kiểm thử.
3. **Thêm chart/dependency ngay:** không cần cho MVP; radar chart nằm ngoài scope, thêm dependency tăng APK và test burden.
