# ORACLE-01 — Bộ trích kèo dự báo từ tin tức (thiết kế)

**Ngày:** 2026-11-12 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/ORACLE-01_ai-predictive-claim-extractor.md`

## Mục tiêu
Khi đồng bộ RSS xong, AI chạy nền tìm khẳng định về tương lai có thể kiểm chứng trong bài mới (ví dụ "OpenAI dự kiến ra mắt GPT-5 vào tháng 11"), lưu thành một **kèo Yes/No** có ngày đáo hạn và tiêu chí giải quyết. Bài có kèo hiện huy hiệu "Kèo Dự Đoán" ở danh sách và màn đọc; bấm mở thẻ **chỉ xem**.

## Quyết định của owner (2026-11-12)
- Kích hoạt: **tự động nền khi sync** (không phải theo yêu cầu).
- Lưu: **bảng Room mới `oracle_market`, Room v21 + `MIGRATION_20_21`** (owner cho phép migration cho task này).
- Dữ liệu gửi: consent riêng, **mặc định TẮT**; **tiêu đề + mô tả ≤ 500 ký tự**; **tối đa 5 bài mỗi lần sync**; không gửi thân bài, link, nguồn, tài khoản.
- UI: huy hiệu ở danh sách + màn đọc; bấm mở **thẻ chỉ xem** (không cược, không điểm, không quảng cáo).

## Phạm vi trung thực
- AI chỉ rút khẳng định *có trong tiêu đề/mô tả*; không tra cứu web; không kiểm sự thật. Thẻ ghi: "Do AI soạn từ tiêu đề và mô tả; có thể sai; chưa tham gia cược được".
- Chạy nền cần key Gemini dùng được. Key mặc định chung đang hỏng (FIX-09, owner hoãn), nên tính năng có thể không tạo kèo nào trên máy người dùng.
- Không làm: đặt cược, ví Intel Points, tỷ lệ cộng đồng (ORACLE-02), tự chốt kèo (ORACLE-03), quảng cáo thưởng (ORACLE-04), dependency mới.

## Dữ liệu
Bảng `oracle_market` (chỉ `CREATE TABLE IF NOT EXISTS`, không sửa bảng cũ, `fallbackToDestructiveMigration` không dùng):
- `id` PK tự tăng; `articleId` FK→`article(id)` ON DELETE CASCADE, **unique** (một dòng mỗi bài), có index.
- `status`: `PENDING` (đã thử, JSON hỏng, còn lượt), `HAS_CLAIM`, `NO_CLAIM`.
- `attempts` (số lần bị từ chối), `question`, `expiresAt` (epoch ms, UTC), `resolutionCriteria`, `createdAt`.
- `NO_CLAIM` được lưu để không gọi Gemini lại. `PENDING` đạt 2 lần từ chối thì thành `NO_CLAIM`.
- Không có cột tiền/điểm/cược.

## Thành phần
- `domain/ai/agents/OracleClaimPolicy` (thuần):
  - hằng số có tên: `MAX_INPUT_CHARS=500`, `MAX_ARTICLES_PER_RUN=5`, `MAX_ATTEMPTS=2`, `MAX_QUESTION_CHARS=160`, `MAX_CRITERIA_CHARS=240`, `MAX_HORIZON_YEARS=3`, `RETENTION_DAYS=30`.
  - `sanitizeInput(title, description)`: bỏ HTML, cắt ≤ 500 ký tự (không cắt giữa surrogate).
  - `buildPrompt`/`buildRequestBody` (JSON mode): chỉ rút khẳng định có trong văn bản, cấm bịa ngày/số liệu/tên, cho phép `{"hasClaim": false}`; ngày ở dạng ISO `yyyy-MM-dd`.
  - `parseAndValidate(raw, now): OracleParse` (`Claim` / `NoClaim` / `Rejected(reason)`): bóc fence; `question` không rỗng, ≤ 160, kết thúc `?`; `resolutionCriteria` không rỗng, ≤ 240; `expiresAt` phải **sau `now`** và ≤ 3 năm, đổi sang cuối ngày UTC; ngày sai/đã qua → `Rejected`.
- `domain/model` + `domain/repository/OracleMarketDao` + `OracleMarket` entity; đăng ký trong `AndroidDatabase` và `DbModule`.
- `infrastructure/ai/OracleClaimExtractor` (`@Singleton`): `extractRecent(accountId)`:
  1. Consent `oracle_claims_gemini_consent` ≠ `true` → không làm gì, 0 request.
  2. Lấy ≤ 5 bài mới nhất chưa có dòng `HAS_CLAIM`/`NO_CLAIM` (kể cả `PENDING` còn lượt).
  3. Mỗi bài 1 request qua `AiRequestGateway`, use case `oracle_claim`.
  4. Dừng cả lượt khi gặp `RateLimited`/`InvalidApiKey`/`MissingApiKey`; lỗi mạng: không ghi gì.
  5. `Claim` → `HAS_CLAIM`; `NoClaim` → `NO_CLAIM`; `Rejected` → tăng `attempts`.
  - Hủy coroutine thì dừng; log chỉ in loại exception hoặc lý do từ chối; extractor nhận chuỗi, không nhận `Article`.
- `SyncWorker`: sau sync thành công thêm `runCatching { oracleClaimExtractor.extractRecent(accountId) }.onFailure { Log.w }` cạnh semantic indexer (đổi constructor Hilt → clean rebuild).
- Dọn: khi sync, xóa kèo có `expiresAt` quá 30 ngày; xóa theo bài nhờ CASCADE.
- Cài đặt: `DataStoreKeys.OracleClaimsGeminiConsent` (`Boolean?`, null = chưa hỏi), công tắc trong `PrivacySettingsPage` (+ ViewModel), khác khóa quiz/podcast/deep dive. Bật công tắc là hành động đồng ý, văn bản nêu rõ dữ liệu gửi và tối đa 5 bài mỗi lần sync.
- UI:
  - `OracleBadge` ("Kèo Dự Đoán", lấp lánh bằng animation nhẹ, tắt khi bật giảm chuyển động, có `contentDescription`) trong `ArticleItem` cạnh `WatchdogBadge` và trên đầu `ReadingPage`.
  - `OracleClaimSheet` (Dialog): câu hỏi, ngày đáo hạn, tiêu chí, nhãn AI. Chỉ xem.
  - Tra theo tập `articleId` đang hiển thị qua một `Flow`; không đọc thân bài (FIX-17).
- Chuỗi mới ở 38 locale (EN và VI viết tay, 36 bản tiếng Anh; không đặt `%d` trước danh từ số nhiều); `StringResourceParityTest` phải qua.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML kết quả), mutation check có backup + `cmp`:
  - policy: giới hạn có test số cứng, ngày quá khứ/xa/sai định dạng, câu hỏi không `?`, fence, surrogate.
  - extractor: consent tắt = 0 request, đúng 5 bài, dừng ở quota/khóa, `attempts` 1→2→`NO_CLAIM`, lỗi mạng không ghi, hủy, log không lộ nội dung.
  - DAO + CASCADE + unique (Robolectric); `RoomMigration20To21Test` (mẫu `RoomMigration18To19Test`), schema `21.json`.
  - badge/sheet Compose; test nguồn cho nối `SyncWorker`, chuỗi, khóa consent riêng.
  - Cả suite `--rerun-tasks`, lint, `assembleDevDebug`.
- androidTest: migration chuỗi đầy đủ + badge/sheet, bằng `adb -s 115333744A005844 shell am instrument` (không dùng `connected*`).
- Máy thật chỉ **Tecno KJ7 `115333744A005844`**: cổng thiết bị chỉ đọc, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`. Ca: nâng cấp DB v20→v21 giữ nguyên dữ liệu; consent tắt = 0 request trong log; bật + key hỏng = dừng sớm; badge và thẻ với dòng mẫu chèn vào DB đã sao lưu; khôi phục byte-identical. Quảng cáo toàn màn hình: chỉ Back.
- **Không kiểm được:** kèo thật từ Gemini (không có key riêng), tỷ lệ JSON hợp lệ, chất lượng câu hỏi/ngày; báo cáo DONE sẽ ghi rõ.

## Giới hạn đã biết
- Mô tả ngắn nên bỏ sót dự báo nằm sâu trong thân bài.
- Ngày đáo hạn do AI suy ra có thể sai; chỉ kiểm định dạng và phạm vi, không kiểm sự thật.
- Nền gọi Gemini khi người dùng không mở app (chỉ khi đã bật consent).
- 36 locale dùng chuỗi tiếng Anh.
- Migration chạm DB người dùng thật: chỉ tạo bảng mới.
