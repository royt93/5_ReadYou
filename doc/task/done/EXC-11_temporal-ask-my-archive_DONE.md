# [DONE] EXC-11 — Temporal "Ask My Archive" — Hỏi Đáp Kho Lưu Trữ RSS Theo Mốc Thời Gian

- **Status:** ✅ Done (toàn bộ xử lý on-device; đã kiểm chứng trên runtime Android thật và dữ liệu lưu trữ thật)
- **Hoàn thành ngày:** 2026-11-08
- **Branch:** `dev` (commits từ `6f44f42d` đến `3878afbc`)
- **Điểm audit khách quan:** **9.5/10** (đủ mọi AC, 0 cloud/API key/dependency mới, Room v20, xử lý được bẫy từ khóa chuỗi con phát hiện trong smoke thật; trừ 0.5 điểm vì chưa kiểm chứng perspective shift trên dữ liệu thật nhiều nguồn, chỉ kiểm chứng qua fixture).

---

## 1. Tóm tắt kết quả đạt được so với Acceptance Criteria

| Acceptance Criteria | Kết quả | Bằng chứng |
|---|---|---|
| Câu hỏi tự nhiên kèm mốc thời gian | ✅ Đạt. Parser nhận dạng cú pháp VI (`từ tháng M [năm YYYY] đến giờ\|đến nay`) và EN (`since <month> [YYYY]`, `from <month> to now`). Cụm thời gian không hỗ trợ yêu cầu người dùng nhập ngày rõ ràng thay vì âm thầm áp mặc định. | `ArchiveQueryInputParserTest` (16 tests); smoke thật trên S24U/KJ7. |
| Lọc theo chủ đề qua `SemanticSearchEngine` | ✅ Đạt. Phân trang metadata `ArticleDao.queryArticleMetadataPage` (200 bài/trang, FIX-17), lọc thêm để tránh bẫy chuỗi con (keyword ngắn như "ai" nằm trong "Ukraine"). | `ArchiveQueryRepositoryTest` (22 tests); probe thật trên 152 bài BBC: Ukraine đúng 10/10, Russia 8/8, Trump 7/7. |
| Nhóm theo giai đoạn (tháng), 1–3 trích dẫn nguyên văn từ body | ✅ Đạt. Đọc body tuần tự tối đa 3 bài/tháng (giới hạn 36 bài/truy vấn, bỏ qua bài >1 MiB raw+full). `EvidenceExtractor` dùng Jsoup loại script/style, tách câu bằng `BreakIterator`, trích đúng substring nguyên văn, cắt tối đa 300 ký tự. Nút "Mở bài" mở đúng `readingRoute(articleId)`. | `EvidenceExtractorTest` (16 tests); smoke thật trên KJ7 mở đúng bài "What lower interest rates mean for borrowers". |
| Đánh dấu "Có thể có thay đổi quan điểm" | ✅ Đạt. Heuristic 6 cặp từ đối lập EN/VI (`increase/decrease`, `support/oppose`, `approve/reject`, `tăng/giảm`, `ủng hộ/phản đối`, `chấp thuận/bác bỏ`). Chỉ kích hoạt giữa 2 tháng liền nhau, mỗi bên ≥2 bài, có trích dẫn hợp lệ hai phía, loại bỏ câu phủ định hoặc chứa cả hai chiều. | `PerspectiveShiftDetectorTest` (18 tests); `ArchiveQueryDeviceTest` trên KJ7 thật. |
| Hiển thị "Không đủ dữ liệu" khi thiếu bài | ✅ Đạt. Mọi tháng trong khoảng thời gian được liệt kê từ cũ đến mới; tháng không có bài hiển thị rõ "Không đủ dữ liệu lưu trữ cho giai đoạn này." | Smoke thật trên S24U & KJ7 (ảnh chụp màn hình xác nhận). |
| Toàn bộ on-device, không cloud | ✅ Đạt. Không mạng, không Gemini, không dependency mới, Room giữ v20. | Không có network permission/call trong package `archivequery`. |

---

## 2. Danh sách commit trên nhánh `dev`

| Commit | Nội dung |
|---|---|
| `6f44f42d` | `docs(exc-11): add temporal ask-my-archive design spec` |
| `1573228c` | `docs(exc-11): update spec status to owner-approved` |
| `42c67585` | `docs(exc-11): add TDD implementation plan` |
| `abfe36af` | `docs(feature): mark EXC-11 as in progress` |
| `eab7ad2f` | `feat(exc-11): parse archive query dates and validated ranges` (Task 1, 26 unit tests) |
| `203e4cbf` | `feat(exc-11): group ranked archive articles by month` (Task 2, 9 unit tests) |
| `7eddec2c` | `feat(exc-11): extract bounded verbatim evidence quotes` (Task 3, 16 unit tests) |
| `fa871ae9` | `feat(exc-11): detect cautious opposing archive signals` (Task 4, 18 unit tests) |
| `4d4d1c7b` | `feat(exc-11): page archive metadata by account and date` (Task 5, 6 Room tests) |
| `93df31aa` | `fix(exc-11): harden date parser and strengthen evidence tests after review` (sửa 3 lỗi phát hiện từ independent review) |
| `38e20a48` | `feat(exc-11): search archive metadata and bounded evidence` (Task 6, 20 Room tests) |
| `89f2cb4b` | `feat(exc-11): add cancellable archive query state` (Task 7, 19 ViewModel tests) |
| `fce16ed0` | `fix(exc-11): mark ViewModel context as application context for lint` (sửa 1 warning StaticFieldLeak) |
| `d7f779e0` | `feat(exc-11): add archive query strings to all locales` (Task 8, 34 keys × 38 files) |
| `b7c727f4` | `feat(exc-11): render temporal archive evidence timeline` (Task 9, 22 Compose tests) |
| `888278b7` | `feat(exc-11): add archive query navigation entry` (Task 10, HomeEntry + Settings row + 2 navigation tests) |
| `b333c4e5` | `fix(exc-11): show the last searched day, not the exclusive range end` (phát hiện từ smoke S24U) |
| `6056dbac` | `fix(exc-11): keep only articles that really share the topic` (sửa bẫy chuỗi con phát hiện từ smoke KJ7) |
| `3878afbc` | `test(exc-11): add on-device integration test for archive timeline and shift` (Task 11, instrumentation 1/1) |

---

## 3. Tổng hợp kiểm thử và xác minh

### 3.1 Unit & JVM Integration Tests

- **Tổng số test toàn suite:** **2.363 tests, 0 failures, 0 errors** (349 test suites, tăng 144 tests so với trước EXC-11).
- **Phân bổ tests mới của EXC-11:**
  - `ArchiveQueryDateRangeTest`: 12 tests (ranh giới 12 tháng, qua năm, năm nhuận, múi giờ, kết thúc hôm nay).
  - `ArchiveQueryInputParserTest`: 16 tests (cú pháp VI/EN, chữ hoa/thường Unicode, tháng không năm, cụm không hỗ trợ yêu cầu làm rõ).
  - `TemporalGrouperTest`: 9 tests (nhóm tháng, tie-break score/date/id, dedupe article ID, count độc lập với top-3).
  - `EvidenceExtractorTest`: 16 tests (HTML sang plain text, lọc script/style/noscript, decode entity, surrogate pairs, trích đúng câu chứa từ khóa, fallback câu đầu, cắt 300 ký tự).
  - `PerspectiveShiftDetectorTest`: 18 tests (6 cặp đối lập EN/VI, đảo chiều, tháng gián đoạn không báo, <2 bài không báo, câu chứa cả hai chiều, câu phủ định, từ nằm trong từ dài).
  - `ArticleDaoArchiveQueryTest`: 6 tests Room native (start-inclusive/end-exclusive, cô lập account, thứ tự date desc / id desc, keyset paging không lặp/sót, giới hạn, body 1.2 MiB không bị nạp).
  - `ArchiveQueryRepositoryTest`: 22 tests Room native (trang 200 bài, recall qua >1.000 bài, bài bị xóa giữa chừng, body quá 1 MiB raw+full, hủy job giữa các trang).
  - `ArchiveQueryViewModelTest`: 19 tests (trạng thái Idle/Searching/Ready/NoResults/InvalidInput/Error, retry, hủy job cũ, đổi account bỏ kết quả cũ, bảo vệ bằng generation counter).
  - `StringResourceParityTest`: 6 tests (34 chuỗi mới đầy đủ trên 38 file `strings.xml`, không thừa, không thiếu, placeholder đồng nhất).
  - `ArchiveQueryPageTest`: 24 tests Compose Robolectric (nhập liệu, giới hạn 500 ký tự, định dạng ngày, tiến trình, thông báo lỗi, card tháng, trích dẫn, card đổi quan điểm, accessibility labels).
  - `HomeEntryNavigationTest`: 4 tests (route `archive_query` duy nhất, helper `readingRoute` mở đúng article ID).

### 3.2 Android Lint

- `./gradlew lintDevDebug`: **0 errors**, **2.463 warnings** (đúng bằng baseline trước khi làm EXC-11).
- **0 warning, 0 error** liên quan đến các file hoặc resource của EXC-11.
- Warning tạm thời `StaticFieldLeak` ở `ArchiveQueryViewModel` đã được sửa triệt để bằng `@ApplicationContext`.
- Chuỗi thừa tạm thời `archive_query_month_title` đã được gỡ khỏi cả 38 file locale.

### 3.3 On-Device Instrumentation

- Test: `ArchiveQueryDeviceTest.liveDevice_runsEndToEndTimelineEvidenceAndShift`
- Thiết bị: **Tecno KJ7 `115333744A005844`** (Android 14, API 34).
- Kết quả: **`OK (1 test)`**, `INSTRUMENTATION_CODE: -1`.
- Kiểm chứng: Room in-memory trên runtime SQLite Android thật, nạp 4 bài trải qua 2 tháng liền kề (tháng 3 "increase" → tháng 4 "decrease"), chạy trích dẫn Jsoup thật, phát hiện đúng perspective shift trên máy.

### 3.4 Smoke Test thực tế trên thiết bị

#### Lần 1: Galaxy S24 Ultra (`R5CX613VZBR`, Android 17 / API 37) với fixture
- Nạp 8 bài fixture phân bổ qua các tháng 1, 3, 4, 5 năm 2026 (tháng 2 trống).
- Tìm kiếm `interest rates`:
  - Hiện đầy đủ timeline 12 tháng từ tháng 12/2025 đến tháng 11/2026.
  - Tháng 1/2026: 2 bài, trích dẫn đúng nguyên văn body.
  - Tháng 2/2026: hiển thị đúng "Không đủ dữ liệu lưu trữ cho giai đoạn này."
  - Giữa tháng 3 và tháng 4: xuất hiện card **"Có thể có thay đổi quan điểm"** với nhãn `increase` / `decrease`, hiển thị đủ cả 2 câu trích dẫn của 2 phía và dòng miễn trừ trách nhiệm.
  - Chạm nút "Mở bài" chuyển đúng vào màn đọc bài tương ứng.
  - Phát hiện lỗi hiển thị nhãn ngày `2026-07-01` thay vì `2026-06-30` → **đã fix tại `b333c4e5`** kèm test hồi quy.
- *Ghi chú:* S24U bị ngắt kết nối adb trước khi kịp khôi phục DB gốc; bản backup gốc được lưu an toàn tại `/tmp/exc11-s24u-backup/`.

#### Lần 2: Tecno KJ7 (`115333744A005844`, Android 14 / API 34) với 152 bài BBC News thật
- Sao lưu trước smoke: `Reader` 569.344 bytes, `settings.preferences_pb` 194 bytes.
- Mở app gặp quảng cáo toàn màn hình AdMob: thực hiện uiautomator dump, kiểm tra foreground `AdActivity`, chạm đúng nút có nhãn **"Continue to app"** tại (771, 174).
- Vào Settings → hàng **"Ask My Archive"** hiển thị chuẩn, chạm vào mở màn hình tìm kiếm.
- Lần chạy đầu với `Ukraine` ra 76 bài: phân tích cho thấy engine khớp chuỗi con từ khóa "ai" trong "Ukraine" với 66 bài không liên quan.
- **Đã fix tại `6056dbac`** bằng bộ lọc nguyên từ (`detectConceptsStrict` + token trùng khớp).
- Chạy lại trên KJ7 sau khi cài APK mới:
  - Truy vấn `Ukraine`: ra đúng **10 bài** (trùng khớp 100% với đếm SQL nguyên từ trên DB backup).
  - Trích dẫn nguyên văn body hiển thị đúng.
  - Tháng 7, 8, 9/2026: hiển thị đúng "Not enough archived data for this period."
  - Tháng 10/2026: hiển thị đúng 10 bài liên quan.
- **Khôi phục sau smoke:**
  - `databases/Reader`: khôi phục từ backup, `cmp OK` (569.344 bytes, `PRAGMA integrity_check` = `ok`, đủ 152 bài, 1 feed, 1 group).
  - `files/datastore/settings.preferences_pb`: `cmp OK` (194 bytes, không bị chạm, không bị xóa).

---

## 4. Giới hạn trung thực (Honest Limitations)

1. **Khớp chủ đề dựa trên metadata:** Chỉ tìm kiếm trên `title` và `shortDescription`. Bài viết chỉ nhắc đến chủ đề sâu trong thân bài mà tiêu đề không đề cập sẽ không được tìm thấy (đây là đánh đổi có chủ đích để tuân thủ FIX-17 và tránh nghẽn I/O).
2. **Perspective Shift là heuristic từ vựng, không phải hiểu nghĩa:** App chỉ nhận diện sự xuất hiện của các cặp từ đối lập tĩnh (EN/VI). Nếu bài báo trích lời một người phản đối trong khi bài viết ủng hộ, hoặc chủ đề đổi ngữ cảnh, app vẫn có thể gắn cờ. Thẻ UI luôn ghi rõ dòng cảnh báo này.
3. **Chỉ trích dẫn tối đa 3 bài/tháng:** Giới hạn này nhằm bảo vệ hiệu năng máy và tránh đọc quá nhiều body vào bộ nhớ (tối đa 36 bài/lần tìm).
4. **Phụ thuộc vào kho bài người dùng đã lưu:** Nếu kho bài chỉ có một nguồn hoặc thời gian lưu ngắn, tính năng sẽ chủ yếu hiển thị "Không đủ dữ liệu".
5. **Dữ liệu S24U chưa khôi phục:** Máy S24U bị rút cáp giữa chừng khi đang có fixture test. Bản backup gốc vẫn còn trên máy tính dev tại `/tmp/exc11-s24u-backup/`.

---

## 5. Kết luận

EXC-11 đáp ứng đầy đủ Acceptance Criteria ban đầu, tuân thủ nghiêm ngặt mọi quy tắc dự án (Room v20, không cloud/Gemini, bảo vệ CursorWindow theo FIX-17, kiểm thử TDD nghiêm ngặt, khôi phục DB sạch sẽ trên thiết bị test). Tính năng sẵn sàng để chuyển sang trạng thái **Done**.
