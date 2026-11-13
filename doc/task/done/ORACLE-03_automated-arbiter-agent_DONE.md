# ORACLE-03 (Nhắc kèo đến hạn và gợi ý bằng chứng cục bộ)

**Date:** 2026-11-13  
**Branch:** `dev` (không push)  
**Scope:** Khi một cược Intel Points đến hạn mà người dùng chưa báo kết quả, app nhắc **một lần** bằng một thông báo gộp ở kênh riêng; mở thẻ kèo thì hiện **tối đa 3 bài liên quan trong tin đã lưu** làm gợi ý. Người dùng vẫn tự bấm Có / Không / Không rõ. Room **giữ v22**.  
**Spec / plan:** `docs/superpowers/specs/2026-11-12-oracle-03-due-reminder-design.md`, `docs/superpowers/plans/2026-11-12-oracle-03-due-reminder.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì ba yêu cầu của Gherkin gốc không làm.

## Nói thẳng về phạm vi

- **Đây không phải "agent tự thẩm định và chốt kèo".** AI không tra web; app chỉ có tin trong máy, nên tự chốt sẽ thường ra "không rõ" hoặc chốt sai làm người dùng mất điểm oan, không hoàn tác được. Không tự chốt, không gọi Gemini, không gateway.
- **Không "chia thưởng cho tất cả người dùng chọn đúng"**: app có một người dùng, không backend.
- **Không push "+850 Intel Points"**: không có số điểm nào trong nội dung thông báo (có test); thông báo chỉ nói có kèo đến hạn.
- Bài liên quan là **gợi ý**, có thể không liên quan hoặc vắng. Thẻ ghi: gợi ý từ tin đã lưu, chưa chắc đúng, bạn tự quyết định.
- Nhắc chỉ khi có **sync thành công**, không đúng giờ cố định (theo tần suất sync của người dùng).
- Không quảng cáo, không mua, không tiền (ORACLE-04 vẫn hoãn). Không consent mới vì không có gì rời máy.

## Đã làm

- **Luật chọn bằng chứng thuần** `OracleEvidencePolicy`: chỉ bài có `date >= placedAt`, không trùng id, sắp theo điểm giảm dần rồi ngày mới hơn rồi id, tối đa 3 (`MAX_EVIDENCE`), quét 200 bài mới nhất (`RECENT_SCAN_LIMIT`).
- **`OracleEvidenceFinder`** (`@Singleton`): chỉ đọc cột danh sách (không đọc thân bài), dùng `SemanticSearchEngine.rank` cục bộ, lỗi thì trả rỗng (chỉ log tên lớp), không nuốt `CancellationException`.
- **`OracleWalletDao.dueOpenBets(nowMillis)`**: một truy vấn đọc, **không đổi lược đồ** (`app/schemas` không đổi, `22.json` vẫn mới nhất).
- **Trạng thái "đã nhắc" ở DataStore** (`oracle_reminded_bets`, `OracleRemindedCodec`: id nối bằng xuống dòng, trần 200 cắt phần cũ nhất).
- **`OracleDueReminder`** (`@Singleton`): một thông báo gộp; một cược thì nội dung là câu hỏi, nhiều cược thì "N kèo". **Chỉ đánh dấu sau khi đã đăng**; không quyền thì không đăng và không đánh dấu (lần sau thử lại); dọn marker của cược đã chốt; lỗi chỉ log loại exception.
- **Kênh `oracle_due_channel`** (id thông báo 9955, mở `MainActivity`, `FLAG_IMMUTABLE`, một id cố định nên lần sau thay lần trước).
- **`SyncWorker`**: gọi sau `OracleClaimExtractor`, trong `runCatching`, **chỉ ở nhánh sync thành công**, **không phụ thuộc consent AI**.
- **`OracleBettingViewModel`**: thêm `evidence` (`Idle`/`Loading`/`Ready`), tải đúng một lần mỗi cược khi pha là chờ kết quả, trên `@IODispatcher`; constructor đổi (`dao, finder, io, accountId, clock`).
- **UI**: `OracleBettingSection` có mục "Bài liên quan trong tin của bạn" **trên** ba nút kết quả (tối đa 3 hàng: tiêu đề, nguồn · ngày; bấm đóng thẻ rồi mở màn đọc), dòng "chưa thấy bài nào" khi rỗng, nhãn gợi ý luôn có. Mục này **không** xuất hiện ở các pha khác.
- 8 chuỗi `oracle_due_*` × 38 locale (EN và VI viết tay, 36 bản tiếng Anh), một script "tất cả hoặc không"; không `%d` trước danh từ số nhiều.
- Không dependency mới, manifest không đổi.

## Kiểm chứng (JVM)

- Full suite chạy lại sạch (`--rerun-tasks`, HEAD `becb1097` cộng androidTest chưa commit lúc đó, sau đó commit thành `b2d85052`): **3.208 test, 0 lỗi, 0 skip**, 435 tệp, không tệp cũ (nền ORACLE-02 là 3.099; thêm 109 từ ORACLE-03). Lint: **0 lỗi, 2.465 cảnh báo** (bằng nền) và **0 vấn đề trong tệp ORACLE-03**. `assembleDevDebug` và `assembleDevDebugAndroidTest` thành công. Lược đồ không đổi.
- Test mới: luật chọn bằng chứng (11), codec (9), khóa DataStore (4), truy vấn đến hạn (7), finder (12), chuỗi (6), thông báo (7), reminder (16), nối `SyncWorker` (6), view model bằng chứng (10), thẻ bằng chứng (13), nối giao diện (8). Hồi quy ORACLE-01/02 xanh; các lớp có luồng Room chạy **3 lần liên tiếp** đều xanh.
- Mutation (sửa, test phải đỏ, khôi phục bằng `cmp`): reminder (bỏ kiểm quyền, ghi marker trước khi đăng, ngưỡng gộp, bỏ lọc đã nhắc, bỏ dọn marker, lộ số điểm, nuốt hủy coroutine, log message thay vì loại, chặn bởi consent), kênh (id trùng Daily Edition, bỏ `FLAG_IMMUTABLE`, sai kênh), `SyncWorker` (bỏ `runCatching`, chạy trước finder, ngoài nhánh thành công), view model (mọi pha, bỏ `distinctUntilChanged`, sai tài khoản, lỗi không hấp thụ, bỏ `flowOn(io)`, bỏ `Loading`, trạng thái đầu), thẻ (bỏ mục, đặt sau nút, trần 3, bỏ `Idle`, bỏ nhãn, id rỗng), `HomeEntry` (bỏ truyền bằng chứng, bỏ `close()`). Tất cả bị bắt sau khi vá test.

## Kiểm chứng (Tecno KJ7 `115333744A005844`, Android 14, 2026-11-13)

Owner chốt "chỉ dùng tecno cho mọi phiên" (Pixel đang mở app loyalty của owner, tôi **không chạm Pixel**; memory đã cập nhật). Cổng thiết bị chỉ đọc trước (launcher ở foreground, mở khóa, không instrumentation), sao lưu byte-exact Room, `-shm`, `-wal`, DataStore và hai `shared_prefs`. Quyền thông báo của app trên KJ7 vốn tắt; owner cho phép cấp tạm bằng `pm grant` và thu hồi sau smoke.

- **androidTest `OracleEvidenceWidgetTest` 5/5** (`am instrument`, không `connected*`): có bài thì liệt kê và bấm một hàng báo đúng id; không có bài thì hiện "không thấy" cùng nhãn; pha khác không có mục; ba nút kết quả vẫn bấm được; bằng chứng nằm trên ba nút. **Mutation trên máy:** đặt mục sau nút lúc đầu **sống sót** (test máy chưa kiểm thứ tự) nên đã thêm test thứ tự, chạy lại thì bị bắt; bỏ nhãn gợi ý bị bắt. Bỏ `testTag` của hàng bài **sống sót trên máy** vì test máy tìm hàng theo chữ, không theo tag; tag này được test JVM giữ (ghi nhận, không vá).
- **Nâng DB trên máy thật:** KJ7 đang ở `user_version 20` (212 bài); sau khi cài và mở bản mới: **`user_version 22`, `integrity ok`**, 212 bài nguyên vẹn.
- **Smoke tay** (kèo và cược mẫu chèn bằng helper tạm, **không đưa vào repo**; giao diện tiếng Anh):
  - Không có cược đến hạn: không có thông báo ở kênh.
  - Cược 1 đến hạn rồi sync thật (Worker `SUCCESS`): **đúng một** thông báo, id 9955, kênh `oracle_due_channel`, tiêu đề "A prediction has reached its date", nội dung là câu hỏi, **không có số điểm**; DataStore có `oracle_reminded_bets` chứa id cược.
  - Sync lần hai: **không** thêm thông báo, marker không đổi.
  - Cược 2 đến hạn rồi sync: vẫn **một** thông báo ở kênh (thay, không chồng), nội dung là câu hỏi của kèo mới chưa nhắc (không lặp kèo cũ); marker có cả hai id.
  - Thẻ của kèo đến hạn: "Kết quả là gì?", **"Related articles in your news"** phía trên ba nút với 3 bài (tiêu đề, "BBC News · ngày"), nhãn "Hints from your saved news; they may be wrong. You decide."
  - Chèn một bài sau ngày đặt cược có từ trùng câu hỏi: mở lại thẻ, bài đó **xếp đầu** danh sách; bấm hàng đó: thẻ đóng và **mở đúng màn đọc bài** (không còn Dialog treo trên màn đọc).
  - Bấm **Không rõ**: "Your 50 points were given back.", DB `REFUNDED`, ví 1.050; mục bằng chứng biến mất. Sync tiếp: marker của cược đã chốt **bị dọn**, chỉ còn marker của cược còn mở.
  - **Tắt quyền thông báo**, xóa marker, sync: **không đăng và không đánh dấu** (marker rỗng). **Cấp lại quyền**, sync: **đăng** và đánh dấu. Quyền được thu hồi cuối phiên (`granted=false` như ban đầu).
  - Kênh: `oracle_due_channel` importance 3; các kênh khác của app không đổi (4, 3, 4, 3).
- Quảng cáo toàn màn hình khi mở app (nhiều lần): chỉ Back (có lần cần chờ rồi Back nhiều lần); không chạm "Continue to app", không cài gì.
- **Khôi phục:** Room, `-shm`, `-wal`, DataStore, `commute_cast_prefs.xml`, `podcast_positions.xml` đều **BYTE IDENTICAL**; `Reader-journal` và `pairing_store.xml` vắng như trước; quyền thông báo thu hồi; test package đã gỡ; helper tạm đã xóa khỏi cây mã (`git status` sạch, không commit); app dừng; bản HEAD sạch cài lại. Lưu ý: bản app trên máy giờ là bản mới (DB v22), không còn bản cũ.

## Phát hiện khi làm

1. **Mutation sống sót làm lộ test yếu (đã vá):** thứ tự "đăng rồi mới ghi marker" (test phải ghi **lần ghi đầu**, không phải lần cuối), `distinctUntilChanged` (cần test ví đổi trong khi thẻ đang mở, qua DAO vì SQL thô không báo Room), và thứ tự bằng chứng trên ba nút ở test máy.
2. **Test của plan có lỗi:** phép đặt cược 500 điểm vượt số dư ví khởi đầu nên các test "nhiều cược" không đặt được cược (đổi mức cược mặc định và chèn trực tiếp 205 dòng cho test trần 200); test lặp `setContent` nhiều lần bị Compose từ chối (tách mỗi pha một test); test nguồn ORACLE-02 cấm chữ `accountId` (nới đúng một từ kèm lý do).
3. **Đọc nhầm kết quả hai lần:** thông báo "completed" của lệnh nền chỉ là lệnh khởi chạy; chỉ đọc XML sau khi `.exit` hoặc `ALLDONE` tồn tại.
4. **Đổi thiết bị giữa chừng:** cổng thiết bị thấy Pixel đang mở app của owner; hỏi owner, owner chọn chỉ dùng Tecno. Trên KJ7, lần sync thứ hai ban đầu tôi tưởng không chạy vì bấm lúc app còn ở quảng cáo; sau đó chỉ bấm khi `topResumedActivity` là `MainActivity` và chờ log `SyncWorker returned`.
5. Thông báo hệ thống của app khác (Messenger) che nút một lần trong ảnh chụp; không chạm.

## Chưa kiểm (nói thẳng)

- **Chất lượng gợi ý trên tin thật lâu dài:** gợi ý trên máy là bài BBC bất kỳ cùng điểm ngữ nghĩa thấp (ví dụ "Booker Prize" cho câu hỏi về ngân hàng); chỉ bài mẫu cố ý trùng từ mới xếp đầu. Đây đúng là lý do thẻ ghi "có thể sai".
- Hành vi khi thiết bị tiết kiệm pin trì hoãn sync; TalkBack; ngôn ngữ ngoài EN và VI trên máy; Pixel và máy thứ hai (owner khóa chỉ Tecno).
- Test máy không giữ `testTag` của hàng bài (chỉ test JVM giữ).
- Kèo trong lúc kiểm vẫn là kèo mẫu, không do AI sinh (không có key Gemini riêng, FIX-09 hoãn; ORACLE-01 chưa thấy AI trả kèo thật).

## Giới hạn

- Gợi ý có thể sai hoặc vắng; người dùng tự quyết định.
- Nhắc phụ thuộc tần suất sync; chỉ giữ 200 cược gần nhất trong tập "đã nhắc".
- Một người dùng, không backend; điểm ảo, tự báo kết quả.
- 36 locale dùng chuỗi tiếng Anh.
- Không có lược đồ mới nên không rủi ro migration (migration v20→v22 trên KJ7 là của ORACLE-01/02, đã chạy thành công trên máy này lần đầu).

## Commits trên `dev`

- `a2fcc5cb` docs: thiết kế · `b543064f` docs: kế hoạch
- `11e0a81b` luật chọn bằng chứng thuần
- `a2096cd5` codec và khóa DataStore "đã nhắc"
- `0d1180c8` truy vấn cược đến hạn
- `bdf76663` finder cục bộ
- `870fa375` chuỗi 38 locale
- `6a003098` nhắc kèo sau mỗi sync
- `becb1097` gợi ý bài liên quan trên thẻ
- `b2d85052`, `bfe2e815` test trên máy
