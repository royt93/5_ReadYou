# ORACLE-03 — Nhắc kèo đến hạn và gợi ý bằng chứng cục bộ (thiết kế)

**Ngày:** 2026-11-12 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/ORACLE-03_automated-arbiter-agent.md` · **Phụ thuộc:** ORACLE-01 (`oracle_market`), ORACLE-02 (`oracle_bet`, `OracleWalletDao.settleBet`)

## Mục tiêu
Khi một cược đã đến hạn mà người dùng chưa báo kết quả, app **nhắc đúng lúc** bằng một thông báo gộp, và khi họ mở thẻ kèo thì hiện **tối đa 3 bài liên quan trong tin đã lưu** làm gợi ý bằng chứng. Người dùng vẫn tự bấm Có / Không / Không rõ như ORACLE-02.

## Quyết định của owner (2026-11-12)
- **Không tự chốt bằng AI.** Agent đề xuất từ tin đã lưu, người dùng xác nhận một chạm.
- **Chạy sau mỗi lần sync thành công** (cạnh `OracleClaimExtractor`), không thêm Worker hay lịch mới.
- **Giữ Room v22.** Trạng thái "đã nhắc" lưu bằng **DataStore**; bằng chứng không lưu, tìm lại khi mở thẻ.
- **Thông báo bật sẵn, kênh riêng, tối đa 1 thông báo gộp mỗi lần sync.** Không cần consent vì không có gì rời máy.

## Phạm vi trung thực
- **Đây không phải "agent tự thẩm định và chốt kèo" như Gherkin gốc.** AI không tra web; app chỉ có tin trong máy, nên tự chốt sẽ thường ra "không rõ" hoặc chốt sai làm người dùng mất điểm oan mà không hoàn tác được. Gherkin còn nói "chia thưởng cho tất cả người dùng chọn đúng": app có một người dùng, không backend, nên không áp dụng.
- Bài liên quan là **gợi ý**, có thể không liên quan hoặc vắng. Thẻ ghi rõ: gợi ý từ tin đã lưu, chưa chắc đúng, bạn tự quyết định.
- Nhắc chỉ khi có sync thành công, không đúng giờ cố định (tần suất sync của người dùng, mặc định 30 phút).
- **Không làm:** tự chốt bằng AI/Gemini, gateway, chia thưởng nhiều người, ORACLE-04 (quảng cáo, tiền), đổi luật điểm hay `settleBet`, Room v23, dependency mới.

## Thành phần
- `domain/oracle/OracleEvidencePolicy` (thuần): `MAX_EVIDENCE = 3`, `RECENT_SCAN_LIMIT = 200`, `MIN_SCORE` (tái dùng `SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD`); `select(candidates, placedAt, limit)` lọc **chỉ bài có `date >= placedAt`**, sắp theo điểm giảm dần rồi theo ngày mới hơn, cắt 3, không trùng `articleId`.
- `infrastructure/ai/OracleEvidenceFinder` (`@Singleton`): `suspend fun find(accountId: Int, bet: OracleBet): List<OracleEvidence>`; đọc `queryRecentArticleListWithFeed(accountId, RECENT_SCAN_LIMIT)` (chỉ cột danh sách, **không đọc thân bài**), gọi `SemanticSearchEngine.rank(bet.question, bài, ngưỡng, limit)` rồi `OracleEvidencePolicy.select`. `OracleEvidence(articleId, title, feedName, dateMillis)`. Không dùng mạng, không dùng Gemini.
- `infrastructure/ai/OracleDueReminder` (`@Singleton`): `suspend fun remindDue(nowMillis: Long = now): Int` (số thông báo đã đăng, 0 hoặc 1):
  1. Lấy cược `OPEN` có `expiresAt <= now` (thêm truy vấn nhỏ vào `OracleWalletDao`: `dueOpenBets(nowMillis)`; **không đổi lược đồ**).
  2. Bỏ cược đã nhắc (so với tập `articleId` đã nhắc trong DataStore).
  3. Nếu còn ≥ 1: nếu `NotificationHelper.canPostNotifications()` sai thì **không đăng và không đánh dấu** (lần sau thử lại); ngược lại đăng **một** thông báo: một cược thì nội dung là câu hỏi của cược; nhiều cược thì "N kèo đã đến hạn". **Không ghi số điểm** vào nội dung.
  4. Chỉ **sau khi đăng** mới thêm các `articleId` vào tập "đã nhắc". Tập có trần **200** (`REMINDED_CAP`), cắt phần cũ nhất.
  5. Dọn: bỏ khỏi tập các `articleId` mà cược đã `WON/LOST/REFUNDED` (tránh tập phình).
  - Lỗi bất kỳ chỉ log loại exception, không nuốt `CancellationException`.
- `NotificationHelper`: kênh mới `ORACLE_DUE_CHANNEL_ID = "oracle_due"` (tên và mô tả đọc từ chuỗi), hàm `notifyOracleDue(title: String, body: String)`; bấm thông báo mở `MainActivity` (theo khuôn `notifyDailyEdition`).
- DataStore: `DataStoreKeys.OracleRemindedBets` (chuỗi `articleId` nối bằng ký tự phân tách không xuất hiện trong id) với `Context.oracleRemindedBets` / `setOracleRemindedBets`; mã hóa/giải mã thuần và có test.
- `SyncWorker`: sau `OracleClaimExtractor`, thêm `runCatching { oracleDueReminder.remindDue() }.onFailure { Log.w }` (đổi constructor Hilt → clean rebuild). Chạy **kể cả khi consent AI của Oracle tắt** (chỉ dữ liệu cục bộ).
- `OracleBettingViewModel`: thêm `evidence: StateFlow<EvidenceState>` (`Idle` / `Loading` / `Ready(list)`), tải khi pha là `AWAITING_RESULT` (qua `OracleEvidenceFinder` trên `@IODispatcher`), xóa khi `close()`; lỗi → `Ready(emptyList())`.
- UI `OracleBettingSection`: ở pha `AWAITING_RESULT`, **trên** ba nút kết quả, mục "Bài liên quan trong tin của bạn" (tối đa 3 hàng: tiêu đề, nguồn, ngày; bấm mở bài qua `LocalOpenLink`/điều hướng sẵn có), hoặc dòng "Chưa thấy bài nào liên quan"; nhãn gợi ý. Đích chạm ≥ 48 dp, `contentDescription`.
- Chuỗi: khoảng 8 khóa `oracle_due_*` (tên kênh, mô tả kênh, tiêu đề + nội dung thông báo một kèo, tiêu đề + nội dung nhiều kèo, tiêu đề mục bằng chứng, dòng không có bài, nhãn gợi ý) × 38 locale (EN và VI viết tay, còn lại tiếng Anh), một script "tất cả hoặc không"; không `%d` trước danh từ số nhiều (dùng `%1$s`); `StringResourceParityTest` xanh.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML), mutation có backup và `cmp`:
  - `OracleEvidencePolicy`: lọc theo `placedAt` (biên), thứ tự, cắt 3, trùng id, danh sách rỗng, số cứng.
  - `OracleEvidenceFinder` (Robolectric + Room in-memory + `SemanticSearchEngine` thật): chỉ bài sau ngày đặt cược, đúng tài khoản, tối đa 3, không đọc thân bài (test nguồn), không có bài thì rỗng.
  - `OracleDueReminder`: chỉ cược `OPEN` đã đến hạn; cược chưa đến hạn hoặc đã chốt thì bỏ; không nhắc lặp ở lần sync sau; nhiều cược đúng **một** thông báo; không đăng và **không đánh dấu** khi không có quyền; trần 200; dọn tập; lỗi không làm hỏng sync; hủy coroutine; nội dung không chứa số điểm.
  - DataStore (mã hóa/giải mã, khóa riêng), view model (tải bằng chứng đúng pha, xóa khi đóng, lỗi → rỗng), thẻ Compose (danh sách, dòng rỗng, nhãn, bấm), nối `SyncWorker` (test nguồn), chuỗi 38 locale.
  - Cả suite `--rerun-tasks`, lint, `assembleDevDebug`.
- androidTest (thẻ bằng chứng) bằng `adb -s 2B051FDH3006MU shell am instrument` (không dùng `connected*`).
- Máy thật chỉ **Pixel `2B051FDH3006MU`**: cổng thiết bị chỉ đọc, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`. Ca: chèn kèo mẫu + cược quá hạn bằng helper tạm (không đưa vào repo); sync thật → **một** thông báo gộp ở kênh riêng; sync lần hai không nhắc lại; mở thẻ thấy bài liên quan hoặc dòng "chưa thấy"; tắt quyền thông báo thì không đăng và lần sau (bật lại) mới nhắc; khôi phục byte-identical. Quảng cáo toàn màn hình: chỉ Back, không bấm "Continue to app".
- **Không kiểm được:** chất lượng gợi ý trên tin thật dài hạn; AI thật (không dùng).

## Giới hạn đã biết
- Gợi ý có thể sai hoặc vắng; người dùng tự quyết định.
- Nhắc phụ thuộc tần suất sync; thiết bị tiết kiệm pin có thể trì hoãn.
- Tập "đã nhắc" chỉ giữ 200 cược gần nhất.
- 36 locale dùng chuỗi tiếng Anh.
- Không có lược đồ mới nên không có rủi ro migration.
