# EXC-10 — Retraction Radar — DONE

**Hoàn tất:** 2026-10-27 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Tính năng
Lưu snapshot văn bản thuần (tiêu đề + nội dung) của bài viết khi sync; khi tiêu đề/nội dung đổi thì lưu thêm một phiên bản và
gắn cờ "✏️ Đã chỉnh sửa"; khi bài biến mất khỏi nguồn thì gắn cờ "🗑️ Đã gỡ bỏ". Huy hiệu hiện ở `ArticleItem` và đầu bài trong
Reader; bấm vào mở màn "Nội dung đã thay đổi" (chữ cũ đỏ gạch ngang, chữ mới xanh). Bật/tắt ở Cài đặt → Quyền riêng tư (mặc định BẬT).

## Quyết định của owner (AskUserQuestion)
- Phạm vi: cả ba backend (xem bảng thực tế bên dưới — **không** đồng đều).
- Snapshot: text thuần, giới hạn dung lượng (`MAX_SNAPSHOT_CHARS = 40_000` ký tự/bản).
- Gỡ bỏ: 3 lần sync hợp lệ liên tiếp + trong cửa sổ ngày.

## Phạm vi thực tế theo backend (nói thẳng)
| Backend | Snapshot bài mới | Phát hiện sửa | Phát hiện gỡ |
|---|---|---|---|
| Local RSS | có | có | có |
| Google Reader | có | có, chỉ với item còn nằm trong stream 10 trang | **không** (stream không liệt kê đầy đủ) |
| Fever | có | có, chỉ với 50 bài gần nhất đã lưu (kiểm lại bằng `with_ids`) | có, cũng chỉ trong 50 bài đó |

**Fever (cập nhật 2026-10-27, commit `d4c11afe`):** server không gửi lại item cũ ở luồng `since_id`, nên mỗi sync thêm đúng một
request `with_ids` cho tối đa 50 bài mới nhất đã lưu. Item server trả về được so snapshot; item server thôi không trả về đi qua cùng
`RemovalTracker` (3 lần liên tiếp). Câu trả lời rỗng, lỗi mạng, hoặc không chứa id nào đã hỏi (server bỏ qua `with_ids`) thì không
tính là "vắng". **Chưa kiểm với server Fever thật** (không có); chỉ kiểm bằng mock và Room thật. Không rõ mọi triển khai Fever có
trả đúng kết quả cho `with_ids` hay không.

## Thiết kế
- **Room v19 → v20** (`MIGRATION_19_20`, chỉ `CREATE TABLE IF NOT EXISTS`, không đụng bảng cũ, không destructive fallback):
  `article_snapshot` (FK CASCADE, index `(articleId, capturedAt)`) và `article_radar_state` (FK CASCADE). Cờ nằm ngoài bảng
  `article` nên danh sách và FTS không đổi (giữ FIX-17: không có cột body trong truy vấn danh sách).
- **Domain thuần Kotlin** (`domain/radar/`): `SnapshotNormalizer` (HTML→text, cắt, SHA-256 trên chữ thường đã gộp khoảng trắng),
  `RetractionDetector` (title/body rỗng ở bản mới **không** tính là sửa), `RemovalTracker`, `TextDiff` (LCS theo từ, có chốt chặn
  4 triệu ô DP).
- **Điều kiện đếm một lần "vắng"** (nghiêng về không báo sai): fetch hợp lệ và không rỗng; bài không cũ hơn bài cũ nhất còn
  trong feed; bài có ngày; và **không** phải trường hợp feed bị thay đổi hàng loạt (từ 4 bài trong cửa sổ trở lên, hơn một nửa
  cùng biến mất → bỏ qua lần đó). Bài xuất hiện lại thì reset.
- **Local RSS**: khi radar bật, `AbstractRssRepository.fetchNewArticles` đọc cả feed một lần, so snapshot/đếm vắng, rồi chỉ trả
  các bài mới hơn bài mới nhất đã lưu để chèn (hành vi chèn không đổi; feed `WEB` giữ nguyên). Radar tắt = đúng luồng cũ.
- **Google Reader/Fever**: so sánh trước khi ghi (`IGNORE`/`REPLACE`), baseline sau khi ghi.
- Mọi lỗi radar bị nuốt trong `RetractionRadarService` (trừ huỷ coroutine) nên **không bao giờ làm hỏng sync**.
- **Dọn dẹp** (chạy trong `clearKeepArchivedArticles()` mỗi sync): giữ bản gốc + 4 bản mới nhất mỗi bài; bài đã gỡ quá 90 ngày
  thì xoá snapshot và cờ. Snapshot chết cùng bài nhờ FK CASCADE.
- Cờ cho danh sách được cấp một lần ở `HomeEntry` qua `LocalRadarFlags` (bảng cờ rất nhỏ), nên mỗi dòng không tốn truy vấn.

## Kiểm chứng (chạy lại ngày 2026-10-27)
- `./gradlew testDevDebugUnitTest --rerun`: **1.431 test, 0 skip, 0 lỗi** (trước EXC-10: 1.342; thêm 89; hai lần chạy lại liên tiếp xanh).
- `./gradlew lintDevDebug --rerun-tasks`: **0 lỗi, 2.445 warning** (không đổi so với trước); không có cảnh báo nào ở file radar.
- `assembleDevDebug` + compile androidTest: pass.
- Test mới: `SnapshotNormalizerTest`, `TextDiffTest`, `RetractionDetectorTest`, `RemovalTrackerTest` (kể cả feed reset),
  `RoomMigration19To20Test` (3), `ArticleHistoryRepositoryTest` (9), `RetractionRadarSyncTest` (9, Local RSS với Room thật),
  `RetractionRadarServiceTest` (4), thứ tự so-sánh-trước-ghi trong `GoogleReaderRssSvTest`/`FeverRssSvTest`,
  `ArticleDiffViewModelTest`, `RadarFlagsTest`, `RadarBadgeTest`, `ArticleDiffPageTest`, `PrivacySettingsViewModelTest`/
  `PrivacyPreferencesTest` (toggle), 11 chuỗi × 38 locale (`StringResourceParityTest`).
- Mutation (test phải đỏ khi bỏ): nuốt exception (`RetractionRadarSyncTest`), đảo thứ tự so sánh/ghi ở Google Reader. Cả hai đỏ rồi khôi phục.
- androidTest chuỗi migration (v19 xuất từ `19.json` → mọi migration → v20) **2/2 pass trên Pixel 7 Pro** (`2B051FDH3006MU`),
  gồm `RetractionRadarMigration20Test` (mới) và `BrainRpgMigration19Test` (đổi sang kiểm phiên bản mới nhất).
- **Smoke Pixel 7 Pro `2B051FDH3006MU`** (feed giả qua `adb reverse`, DB v19 thật nâng lên v20): baseline 5 bài/5 snapshot không cờ;
  sửa bài 3 → `editCount=1`, 2 snapshot, Reader hiện "✏️ Đã chỉnh sửa", bấm mở màn diff đúng màu (Original đỏ gạch, Corrected xanh);
  bỏ bài 4 khỏi feed → bộ đếm lên 3 và `removedAt` được đặt, Reader hiện "🗑️ Đã gỡ bỏ", màn diff hiện thông báo bài đã gỡ.
  Sau smoke đã restore Room (176 bài, `integrity_check = ok`, không còn feed/bài test), tắt server và gỡ `adb reverse`.
- Sync thật trên feed BBC (smoke ban đầu) bắt được 4 bài BBC bị sửa thật, ví dụ "…leaves thousands without power" → "…90,000" → "…22,000".

## Giới hạn đã biết (nói thẳng)
- **Google Reader không phát hiện gỡ; Fever chỉ phủ 50 bài gần nhất và chưa kiểm với server thật** (bảng trên).
- Với Local RSS, ba lần "vắng" phải là ba lần sync **có tải lại feed**. Máy chủ trả `304 Not Modified` thì lần đó không đếm, nên
  thực tế có thể phải sync nhiều hơn 3 lần trước khi có cờ gỡ.
- Phát hiện gỡ dựa trên "bài biến mất khỏi feed", không phân biệt được "bị tòa soạn gỡ" với "feed tự cắt bài hoặc đổi cấu trúc".
  Điều kiện cửa sổ ngày và lọc thay đổi hàng loạt giảm báo sai, không loại bỏ hoàn toàn.
- Báo sai "đã sửa" vẫn có thể xảy ra khi nhà xuất bản đổi nội dung động (số liệu trực tiếp, bình luận) trong phần mô tả.
- Màn diff so **bản đầu tiên** với **bản mới nhất**, không cho duyệt từng phiên bản trung gian.
- Radar bật làm mỗi lần sync Local RSS tải và parse đủ feed thay vì dừng ở bài mới nhất.
- Chưa chạy các androidTest UI/Compose khác trên máy; chưa kiểm TalkBack/accessibility trên máy thật.
- Chuỗi mới đã dịch sang 36 locale (commit `54cb78ef`) bằng dịch máy, **chưa có người bản ngữ duyệt**; `en`/`vi` viết tay.
- `BrainRpgMigration19Test` đã đổi để kiểm "phiên bản mới nhất" thay vì cố định 19, nên không còn khẳng định riêng v19.

## Commits (local, chưa push)
`1d677bbb` domain thuần · `2530ebb6` Room v20/DAO/repository · `521567c5` sync + toggle · `5a15ab61` badge/diff/settings/strings ·
`b272fc3f` androidTest migration (commit này do owner tạo; nội dung đã được xác minh khớp file tôi viết) · `54cb78ef` dịch 36 locale · `d4c11afe` Fever re-check.

## Lưu ý về độ ổn định test
Một lần chạy toàn bộ có `HomeViewModelSearchRaceTest` lỗi (`Dispatchers.Main is used concurrently with setting it`, tại `setUp`). Chạy riêng và hai lần chạy toàn bộ kế tiếp đều xanh; nguyên nhân chưa xác định (nghi tranh chấp `Dispatchers.setMain` giữa các test song song), không liên quan radar.
