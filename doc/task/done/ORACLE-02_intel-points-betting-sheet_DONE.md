# ORACLE-02 (Khung đặt cược Intel Points và ví cục bộ)

**Date:** 2026-11-12  
**Branch:** `dev` (không push)  
**Scope:** Trên thẻ kèo của ORACLE-01, người dùng đặt cược điểm ảo **Intel Points** (50 / 100 / 500) vào Có hoặc Không bằng một ví cục bộ. Sau ngày đáo hạn, người dùng tự báo kết quả (Có / Không / Không rõ) để nhận hoặc mất điểm. Room lên v22.  
**Spec / plan:** `docs/superpowers/specs/2026-11-12-oracle-02-betting-wallet-design.md`, `docs/superpowers/plans/2026-11-12-oracle-02-betting-wallet.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì hai yêu cầu của Gherkin gốc không làm: "% đồng thuận của cộng đồng" và "âm thanh chip".

## Nói thẳng về phạm vi

- **Không có cộng đồng thật.** Gherkin gốc ghi "68% Yes - 32% No"; không có backend thì con số đó sẽ là số giả, nên **bỏ**. Thẻ chỉ hiện số dư và cược của chính người dùng.
- **Tự báo kết quả nghĩa là có thể gian lận điểm của chính mình.** Điểm ảo không đổi được thành tiền, quà hay lợi ích nào; không có bảng xếp hạng. "Không rõ" luôn có để hoàn cược, vì kèo do AI soạn và ngày đáo hạn có thể sai.
- **Không âm thanh chip** (cần asset mới).
- **Không quảng cáo, không mua, không tiền.** Hết điểm thì thẻ chỉ có chữ, không có nút nào (có test cấm từ khóa quảng cáo trong mã, test thiết bị khẳng định "không có điều khiển nào để bấm"). ORACLE-04 vẫn hoãn.
- **Kèo trong lúc kiểm vẫn là kèo mẫu**, không phải do AI sinh (xem "Chưa kiểm"): ORACLE-01 chưa chứng minh được AI trả kèo hợp lệ trên bài thật.
- Không đụng Brain RPG (XP).

## Đã làm

- **Luật điểm thuần** `OracleBettingPolicy`: ví 1.000 điểm khởi đầu; +100 mỗi ngày UTC, trần 2.000, không cộng dồn ngày bỏ lỡ, không lùi khi đồng hồ chỉnh ngược; cược chỉ 50/100/500, cần đủ số dư, chỉ trước hạn, mỗi bài một cược, không đổi/hủy; chốt chỉ sau hạn: đúng cửa trả gấp đôi, sai cửa mất, Không rõ hoàn; quá 30 ngày chưa báo thì tự hoàn. Số dư sau chốt không bị cắt bởi trần.
- **Hai bảng Room mới, v22** (`MIGRATION_21_22` chỉ `CREATE TABLE/INDEX IF NOT EXISTS`, `22.json`): `user_wallet` (một dòng) và `oracle_bet` (unique `articleId`). **Cược không có khóa ngoại**, vì kèo bị dọn và `upsert` dùng `REPLACE`, và bài có thể bị xóa; điểm đã đặt không được mất theo. SQL migration khớp `createSql` trong `22.json` (đã đối chiếu bằng script cho cả hai bảng).
- **`OracleWalletDao`**: `ensureWalletAndGrant`, `placeBet`, `settleBet`, `autoRefundStale` đều là `@Transaction`, tự kiểm lại điều kiện trong transaction; câu lệnh đóng cược có `AND status = 'OPEN'`, nên bấm đúp hay gọi lặp không thể trừ hoặc trả hai lần.
- **Tự hoàn trước khi dọn kèo** trong `OracleClaimExtractor.cleanUp` (chạy cả khi consent AI tắt; lỗi hoàn không chặn việc dọn kèo).
- **`OracleBettingViewModel`** (`@HiltViewModel`) và trạng thái giao diện thuần `buildOracleBetUiState`; cược đã có giữ hạn lúc đặt, không theo hạn mới của kèo.
- **`OracleBettingSection`**: 6 pha (chưa cược, hết điểm, đã cược, chờ kết quả, đã chốt, đã đóng), mức cược vượt số dư bị vô hiệu, nút đặt cược chỉ bật khi đã chọn đủ cửa và mức, số định dạng theo locale; nối vào khe `footer` mới của `OracleClaimSheet` qua `HomeEntry`.
- 20 chuỗi `oracle_bet_*` × 38 locale (EN và VI viết tay, 36 bản tiếng Anh) và **đổi nhãn AI** `oracle_sheet_ai_label` ở cả 38 file (bỏ câu "chưa cược được"), thêm bằng một script "tất cả hoặc không".
- Không dependency mới, manifest không đổi, `21.json` không đổi.

## Kiểm chứng (JVM)

- Full suite chạy lại sạch (`--rerun-tasks`, HEAD `f3c1dd87`): **3.099 test, 0 lỗi, 0 skip**, 423 tệp, không tệp cũ (nền trước 2.987, thêm 112). Lint: **0 lỗi, 2.465 cảnh báo** (bằng nền) và **0 cảnh báo trong tệp ORACLE**. `assembleDevDebug` và `assembleDevDebugAndroidTest` thành công.
- Test mới: luật điểm (22), migration 21→22 (5), DAO (25, gồm 3 test tính toàn vẹn khi cập nhật ví lỗi giữa chừng bằng trigger), tự hoàn qua extractor (+4, tổng 24), chuỗi (5), trạng thái UI (8) và view model (14), thẻ Compose (24), nối giao diện (5).
- Mutation (sửa, test phải đỏ, khôi phục bằng `cmp`): từng hằng số và biên của luật (trần, hôm nay/ngày mới, đồng hồ lùi, biên hạn, biên số dư, mốc 30 ngày, hệ số thắng, `floorDiv`, nhánh Không rõ); unique và "không khóa ngoại" của bảng; trừ điểm, bảo vệ `status = 'OPEN'`, `@Transaction` của cả ba hàm, trần không cắt số dư sau chốt; thứ tự và sự độc lập với consent của tự hoàn; mọi nhánh view model (số dư mặc định, hạn của cược, xóa lỗi khi đóng/mở, cờ lỗi khi đặt/chốt, `CancellationException`, lỗi ví lúc mở); UI (mức vượt số dư, nút đặt cược, tên cửa, nhãn thắng/thua, hết điểm không có nút, cờ lỗi); nối giao diện (khe `footer`, `close()`, hằng `STAKES`, từ khóa quảng cáo); chuỗi (thiếu khóa ở một locale, `%1$s`, mất `%2$s`, nhãn AI cũ, mất "2,000").

## Kiểm chứng (Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-11-12)

Owner đổi sang **Pixel** vì Tecno KJ7 rơi khỏi `adb devices` giữa phiên ("dùng pixel cho mọi phiên", đã ghi vào memory). Cổng thiết bị trước (không instrumentation, màn hình mở khóa; chỉ `bemytester` và `loyalty.skyjoy.vn.stg` ở nền), sao lưu byte-exact Room, `-shm`, `-wal`, DataStore và hai `shared_prefs`.

- **androidTest 7/7** (`am instrument`, không `connected*`): nâng cấp DB v21→v22 từ `21.json` giữ dữ liệu cũ và tạo hai bảng; cược còn nguyên sau khi bài bị xóa và vẫn chốt được; hoàn đúng mốc 30 ngày trên DB thật; thẻ chọn cửa + mức rồi đặt cược gọi đúng `(cửa, mức)`; mức vượt số dư không chọn được; hết điểm không có điều khiển nào; đến hạn có ba kết quả. **Ba mutation trên máy đều bị bắt** (thêm khóa ngoại, bỏ vô hiệu mức cược, thêm nút "Watch an ad" khi hết điểm).
- **Smoke tay với app thật** (kèo mẫu chèn bằng một instrumentation tạm, **không đưa vào repo**; giao diện tiếng Việt):
  - DB nâng cấp: `user_version = 22`, `integrity ok`, 1.785 bài nguyên vẹn.
  - Huy hiệu "Kèo Dự Đoán" hiện đúng bài có kèo; bấm mở thẻ: câu hỏi, "Dự kiến trước 12 thg 12, 2026", tiêu chí, nhãn AI (đã bỏ câu "chưa cược được"), **Số dư: 1.000 điểm**, Có/Không, 50/100/500, nút "Đặt cược" **tắt** tới khi chọn đủ; chọn Không + 100 thì nút hiện "Cược 100 điểm vào Không".
  - Đặt cược: DB ví `1000 → 900`, một dòng `NO 100 OPEN`; thẻ chuyển sang "Bạn đã cược 100 điểm vào Không." và không còn nút cược (không cược đôi được).
  - Đến hạn (hạn ép về quá khứ): "Kết quả là gì?" với ba nút Có / Không / Không rõ. Bấm **Không** (đúng cửa): "Bạn thắng 100 điểm.", ví `1100`, `WON`, có `settledAt`.
  - Cược thứ hai Có 50, bấm **Không rõ**: "Bạn được hoàn 50 điểm.", ví `1100`, `REFUNDED`.
  - **Tự hoàn khi sync:** cược Có 500 (ví 600), hạn ép về hơn 30 ngày; bấm đồng bộ thật (Worker `SUCCESS`, 1.961 bài): cược thành `REFUNDED`, ví `1100`; **0** request `oracle_claim` (consent AI tắt vẫn dọn/hoàn).
  - **Hết điểm:** ví `0`: thẻ hiện "Số dư: 0 điểm" và "Không đủ điểm. Mỗi ngày (UTC) bạn nhận thêm 100 điểm, tối đa 2.000." và **không có nút nào**.
  - **Thưởng ngày:** lùi `lastGrantEpochDay` một ngày, mở thẻ: ví `0 → 100`, ngày được cập nhật; mức 500 vô hiệu (số dư 100), 50 và 100 bấm được.
- Quảng cáo toàn màn hình khi mở app (nhiều lần): chỉ Back (cần vài lần, có lần Back đưa sang trang Play Store của quảng cáo; vẫn chỉ Back để quay lại); không chạm "Continue to app", không cài gì.
- **Khôi phục:** Room, `-shm`, `-wal`, DataStore (20 byte), `commute_cast_prefs.xml`, `pairing_store.xml` đều **BYTE IDENTICAL**; `Reader-journal` vắng như trước; test package đã gỡ; helper tạm đã xóa khỏi cây mã; app dừng; bản HEAD sạch (`8c330b87`) được cài.

## Phát hiện khi làm

1. **Test yếu lộ ra qua mutation (đã vá):** bỏ khối `@Transaction` của `placeBet` và chuyển câu lệnh đóng cược sang không có `AND status = 'OPEN'` đều **sống sót** vì test đồng thời trên Robolectric không bắt được. Đã thêm ba test tính toàn vẹn dùng một trigger SQL làm hỏng bản cập nhật ví giữa chừng (đặt cược, chốt, hoàn đều phải hoàn tác toàn bộ), rồi chạy lại: cả ba `@Transaction` bị bắt. Riêng bảo vệ `AND status = 'OPEN'` ở câu lệnh đóng cược thì **vẫn sống sót**: trong DAO hiện tại mọi đường gọi đều kiểm trạng thái `OPEN` ngay trước đó trong cùng transaction (và `settleBet` kiểm `canSettle`, `autoRefundStale` lấy từ `openBets()`), nên bảo vệ này là lớp phòng thủ thứ hai chưa có đường nào kích hoạt riêng nó. Giữ lại, ghi nhận không có test riêng.
2. **Hai test không ổn định (đã sửa gốc):** `settle_afterTheDeadline...` và `placeBet_takesThePoints...` thỉnh thoảng đỏ vì số dư và cược là hai luồng Room cập nhật riêng, một khung hình có thể thấy cược mới với số dư cũ. Sửa test chờ đúng trạng thái cuối, chạy 3 lần liên tiếp đều xanh. Test lỗi ví khi mở thẻ ban đầu "qua" cả khi bỏ `guarded` (ngoại lệ chỉ rơi vào handler luồng); đã chặn handler để bắt và chỉ tính ngoại lệ của riêng test đó.
3. **Kết quả cũ đọc nhầm hai lần:** một lệnh nền báo "xong" khi tôi vẫn đọc XML cũ (Gradle chưa chạy xong, rồi biên dịch lỗi nên XML cũ còn nguyên). Sau đó chỉ đọc kết quả sau khi tệp `.exit` tồn tại, và kiểm số tệp XML mới.
4. **Mutation không hợp lệ không tính:** một lệnh bị hủy giữa chừng; script mutation ghi lại và khôi phục, kiểm lại bằng `cmp`/`grep`. Một lần mẫu thay thế không khớp (file tự khôi phục, `PATTERN-MISSING`).
5. **Đổi thiết bị giữa phiên:** KJ7 rơi khỏi adb khi tới bước máy thật; hỏi owner, owner chọn Pixel. Giữa smoke, Pixel hiện hộp thoại sinh trắc và màn hình đen (do một chạm của tôi trên Settings > USB mà Pixel đang mở sẵn); tôi dừng, không tự xử lý, owner xử lý trên máy, rồi tiếp tục.
6. **Một lần bấm nhầm bài** (danh sách đã đổi thứ tự sau sync) mở màn đọc; không hại gì, quay lại và tìm huy hiệu qua truy vấn DB thay vì đoán tọa độ.

## Chưa kiểm (nói thẳng)

- **Kèo thật do AI sinh ra** (xem ORACLE-01): toàn bộ smoke dùng kèo mẫu.
- **Thước đo "không thể gian lận"**: người dùng tự báo kết quả nên không có gì kiểm chứng sự thật; đó là thiết kế, không phải lỗi.
- Đồng hồ máy bị chỉnh tay (luật chỉ chặn lùi thưởng ngày, không chặn chỉnh tiến), múi giờ lệch ngày UTC gần nửa đêm, TalkBack, ngôn ngữ ngoài EN và VI, Tecno KJ7 (rơi khỏi adb), máy thứ hai.
- **Hạn trôi qua khi thẻ đang mở**: pha chỉ tính lại khi dữ liệu đổi hoặc khi mở thẻ, không có bộ đếm giờ; thẻ chỉ đổi sang "Kết quả là gì?" ở lần mở kế tiếp.

## Giới hạn

- Điểm ảo, một người chơi, tự báo kết quả; không cạnh tranh thật.
- Thưởng ngày theo ngày UTC, có thể lệch ngày địa phương vài giờ.
- Đồng hồ máy bị chỉnh tiến làm hạn và thưởng ngày đến sớm.
- Bảo vệ `AND status = 'OPEN'` ở câu lệnh đóng cược chưa có test cô lập (xem mục 1).
- 36 locale dùng chuỗi tiếng Anh.
- Migration chạm DB người dùng thật: chỉ tạo bảng mới, có test và đã đối chiếu `22.json`.

## Commits trên `dev`

- `e72776c4` docs: thiết kế · `9a68dbcc` docs: kế hoạch
- `627551ec` luật điểm thuần
- `6efaf05d` bảng ví và cược, DAO, Room v22
- `84b6ff4a` tự hoàn cược quá hạn
- `f22af92f` chuỗi 38 locale và đổi nhãn AI
- `0b72f9ec` view model và trạng thái giao diện
- `f3c1dd87` thẻ cược 6 pha, nối `HomeEntry`
- `8c330b87` androidTest trên máy
