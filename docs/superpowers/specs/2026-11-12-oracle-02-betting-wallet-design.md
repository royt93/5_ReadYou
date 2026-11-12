# ORACLE-02 — Khung đặt cược Intel Points và ví cục bộ (thiết kế)

**Ngày:** 2026-11-12 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/ORACLE-02_intel-points-betting-sheet.md` · **Phụ thuộc:** ORACLE-01 (`oracle_market`, `OracleClaimSheet`)

## Mục tiêu
Trên thẻ kèo của ORACLE-01, người dùng đặt cược điểm ảo **Intel Points** (50 / 100 / 500) vào Yes hoặc No. Điểm nằm trong một ví cục bộ. Sau ngày đáo hạn, người dùng tự báo kết quả để nhận hoặc mất điểm.

## Quyết định của owner (2026-11-12)
- **Phạm vi:** ví riêng cục bộ + người dùng tự chốt kết quả (không backend, không tự động).
- **Room v22 + migration** (owner cho phép riêng task này): hai bảng mới.
- **Điểm vào ví:** 1.000 điểm khởi đầu + 100 điểm mỗi ngày (UTC) khi mở thẻ hoặc ví, trần số dư 2.000. **Không quảng cáo, không mua.**
- **Chốt kết quả:** người dùng báo Yes / No / Không rõ; Không rõ hoàn cược; quá 30 ngày không báo thì tự hoàn.
- **Tỷ lệ:** bỏ "% cộng đồng" (không có backend nên sẽ là số giả); thắng +100% cược, thua -100%.

## Phạm vi trung thực
- **Không có cộng đồng thật.** Gherkin "68% Yes – 32% No" không thể đúng nếu không có server; thẻ chỉ hiện số dư và cược của chính người dùng.
- **Tự báo kết quả nghĩa là có thể gian lận điểm của chính mình.** Điểm ảo không đổi được thành tiền, quà hay lợi ích nào; không có bảng xếp hạng.
- Không âm thanh chip (cần asset mới; có thể thêm sau).
- Kèo do AI soạn từ tiêu đề và mô tả, đã có nhãn "có thể sai"; ngày đáo hạn có thể sai. Vì vậy "Không rõ" luôn có để hoàn cược.
- **Không làm:** quảng cáo nạp điểm và AI Oracle Edge (ORACLE-04, owner hoãn), tự chốt kèo (ORACLE-03), tỷ lệ cộng đồng, đổi điểm, bảng xếp hạng, đụng Brain RPG (XP), dependency mới.

## Dữ liệu (Room v22, chỉ `CREATE TABLE IF NOT EXISTS`, không sửa bảng cũ)
**Bẫy đã tránh:** `oracle_market` bị dọn 30 ngày sau ngày đáo hạn và `upsert` dùng `REPLACE`; nếu cược có khóa ngoại tới `oracle_market` hoặc `article`, xóa kèo hay bài sẽ làm mất cược và điểm. Vì vậy `oracle_bet` **không có khóa ngoại** và tự lưu bản chụp câu hỏi và ngày.

- `user_wallet` (một dòng, `id = 1`): `balance` (Long), `lastGrantEpochDay` (Long, ngày UTC của lần thưởng gần nhất).
- `oracle_bet`: `id` PK tự tăng; `articleId` (TEXT, **unique**, mỗi bài một cược); `question` (TEXT); `expiresAt` (Long); `side` (`YES`/`NO`); `stake` (Int); `status` (`OPEN`/`WON`/`LOST`/`REFUNDED`); `placedAt` (Long); `settledAt` (Long?).
- Ví được tạo lười ở lần dùng đầu (1.000 điểm, `lastGrantEpochDay` = hôm nay).

## Luật điểm (`OracleBettingPolicy`, thuần, hằng số có tên)
- `STARTING_BALANCE = 1000`, `DAILY_GRANT = 100`, `BALANCE_CAP = 2000`, `STAKES = [50, 100, 500]`, `REFUND_AFTER_DAYS = 30`, `WIN_MULTIPLIER = 2`.
- **Thưởng ngày:** khi ngày UTC hiện tại > `lastGrantEpochDay` và `balance < BALANCE_CAP`: `balance = min(balance + 100, 2000)`; luôn cập nhật `lastGrantEpochDay`. Không cộng dồn ngày bỏ lỡ; không lùi khi đồng hồ bị chỉnh ngược.
- **Đặt cược** hợp lệ khi: `stake` thuộc `STAKES`, `balance >= stake`, `now < expiresAt`, chưa có cược cho bài đó. Trừ điểm và ghi cược trong **một transaction**. Không đổi hay hủy cược sau khi đặt.
- **Chốt** hợp lệ khi: cược `OPEN` và `now >= expiresAt`.
  - Báo đúng cửa: trả `stake × 2`, `WON`.
  - Báo sai cửa: không trả, `LOST`.
  - Báo Không rõ: trả `stake`, `REFUNDED`.
  - Số dư sau chốt không bị cắt bởi trần (trần chỉ áp cho thưởng ngày).
- **Tự hoàn:** cược `OPEN` có `now >= expiresAt + 30 ngày` → trả `stake`, `REFUNDED`. Chạy trong `OracleClaimExtractor.cleanUp` **trước** `deleteStale`.

## Thành phần
- `domain/oracle/OracleBettingPolicy` (thuần) + enum `OracleBetSide`, `OracleBetStatus`, `OracleOutcome` (`YES`/`NO`/`UNCLEAR`).
- `domain/model/oracle/UserWallet.kt`, `OracleBet.kt` (+ converters enum), `domain/repository/OracleWalletDao.kt` với các hàm `@Transaction`: `ensureWallet`, `claimDailyGrant(todayEpochDay)`, `placeBet(...)`, `settleBet(articleId, outcome, now)`, `autoRefundStale(now)`; mỗi hàm tự kiểm trạng thái trong chính transaction (chặn bấm đúp và gọi lặp, giống `claimAndApplyReward`).
- `OracleBettingViewModel` (`@HiltViewModel`): trạng thái theo `articleId` (`NoBet`/`Open`/`Settleable`/`Settled`, số dư, lỗi không đủ điểm); đồng hồ và múi giờ UTC lấy từ một nguồn có thể thay trong test.
- UI: mở rộng `OracleClaimSheet` (thân `OracleClaimSheetContent` của ORACLE-01 giữ nguyên và được bọc, để test cũ không vỡ) với 5 trạng thái: chưa cược còn hạn (số dư, Yes/No, 50/100/500, nút đặt cược, mức vượt số dư bị vô hiệu); đã cược còn hạn; đến hạn chưa báo (Yes/No/Không rõ); đã chốt (Thắng +N / Thua -N / Hoàn, số dư mới); hết điểm (nút cược vô hiệu, "Điểm mới sau 00:00 UTC", **không có nút xem quảng cáo**). Đích chạm ≥ 48 dp, mọi điều khiển có `contentDescription`.
- `OracleClaimExtractor.cleanUp` gọi `autoRefundStale`; thêm tham số constructor (`OracleWalletDao`) → clean rebuild Hilt.
- Chuỗi `oracle_bet_*` (khoảng 20 khóa) × 38 locale (EN và VI viết tay, 36 bản tiếng Anh), một script all-or-none; không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` xanh.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML), mutation có backup và `cmp`:
  - policy: biên 50/100/500, trần 2.000, thưởng ngày (cùng ngày, ngày mới, đồng hồ lùi, sát trần), hệ số thắng, mốc hoàn 30 ngày.
  - DAO trong transaction (Room in-memory): đặt cược đủ/thiếu điểm, cược đôi cùng bài, đặt sau hạn, chốt trước hạn bị từ chối, chốt đôi, rollback khi lỗi giữa chừng, cược sống sót khi kèo và bài bị xóa, tự hoàn đúng mốc.
  - view model (dispatcher unconfined + chờ thời gian thực có hạn), thẻ Compose (5 trạng thái, không có nút quảng cáo), nối `cleanUp`, `RoomMigration21To22Test`, schema `22.json` (đối chiếu `createSql` với SQL migration), parity chuỗi.
  - Cả suite `--rerun-tasks`, lint, `assembleDevDebug`.
- androidTest (migration v21→v22 từ `21.json`, thẻ cược) bằng `adb -s 115333744A005844 shell am instrument` (không dùng `connected*`).
- Máy thật chỉ **Tecno KJ7 `115333744A005844`**: cổng thiết bị chỉ đọc, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`. Ca: nâng cấp DB v21→v22 giữ dữ liệu; chèn kèo mẫu bằng một instrumentation nhỏ (không đưa vào repo); đặt cược và kiểm số dư/dòng cược trong DB; ép `expiresAt` về quá khứ để thử chốt và hoàn; khôi phục byte-identical. Quảng cáo toàn màn hình: chỉ Back, không bấm "Continue to app".
- **Không kiểm được:** kèo thật do AI sinh (không có key riêng, FIX-09); báo cáo DONE ghi rõ.

## Giới hạn đã biết
- Điểm ảo, một người chơi, tự báo kết quả; không cạnh tranh thật.
- Đồng hồ máy bị chỉnh có thể đổi mốc hết hạn và thưởng ngày; luật chỉ chặn lùi thưởng ngày, không chặn chỉnh đồng hồ tiến.
- Thưởng ngày tính theo ngày UTC, có thể lệch ngày địa phương vài giờ.
- 36 locale dùng chuỗi tiếng Anh.
- Migration chạm DB người dùng thật: chỉ tạo bảng mới.
