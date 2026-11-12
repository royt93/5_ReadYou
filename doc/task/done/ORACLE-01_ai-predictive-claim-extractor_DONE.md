# ORACLE-01 (Bộ trích kèo dự báo từ tin tức)

**Date:** 2026-11-12  
**Branch:** `dev` (không push)  
**Scope:** Sau mỗi lần sync thành công, AI chạy nền tìm khẳng định dự báo có thể kiểm chứng trong tối đa 5 bài mới (tiêu đề + mô tả ≤ 500 ký tự), lưu thành kèo Yes/No trong bảng Room `oracle_market` (Room v21). Bài có kèo hiện huy hiệu "Kèo Dự Đoán" ở danh sách và màn đọc; bấm mở thẻ **chỉ xem**.  
**Spec / plan:** `docs/superpowers/specs/2026-11-12-oracle-01-claim-extractor-design.md`, `docs/superpowers/plans/2026-11-12-oracle-01-claim-extractor.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì chưa thấy AI trả một kèo hợp lệ trên bài thật (xem "Chưa kiểm").

## Nói thẳng về phạm vi

- **Chỉ xem.** Không đặt cược, không ví Intel Points, không tỷ lệ cộng đồng, không tự chốt kèo, không quảng cáo thưởng (ORACLE-02/03/04). Thẻ ghi rõ "Do AI soạn từ tiêu đề và mô tả; có thể sai. Chưa tham gia cược được."
- **Kiểm tra chỉ là cấu trúc và ngày**, không kiểm sự thật. Câu hỏi phải kết thúc bằng dấu hỏi, ≤ 160 ký tự; tiêu chí ≤ 240; ngày đáo hạn không ở quá khứ và không quá 3 năm.
- **Chạy nền gọi Gemini khi người dùng không mở app**, chỉ khi họ đã bật công tắc riêng (mặc định TẮT).
- **Mô tả ngắn nên bỏ sót** dự báo nằm sâu trong thân bài. Không gửi thân bài.
- **Hai chỉnh so với spec** (đã ghi vào spec): chỉ xét bài trong 3 ngày gần nhất (`RECENT_WINDOW_DAYS`), để lần bật consent đầu không quét tồn đọng cũ; mọi lỗi gọi AI (không chỉ quota hay khóa) dừng cả lượt và không ghi gì cho bài đó.
- **Dữ liệu rời máy** (khi có consent): tiêu đề + mô tả, tổng ≤ 500 ký tự, tối đa 5 bài mỗi lần sync. Không gửi thân bài, đường dẫn, tên nguồn, tài khoản. Công tắc nói rõ điều này.

## Đã làm

- `OracleClaimPolicy` (thuần): hằng số, làm sạch đầu vào (bỏ HTML, không cắt giữa surrogate), prompt (cấm bịa, cho phép `{"hasClaim": false}`), body JSON mode, `parseAndValidate(raw, now)` → `Claim` / `NoClaim` / `Rejected(lý do)`; ngày tính theo ngày UTC, lưu là cuối ngày UTC.
- Bảng `oracle_market` (Room **v21**, `MIGRATION_20_21` chỉ `CREATE TABLE/INDEX IF NOT EXISTS`, `21.json`): một dòng mỗi bài (unique `articleId`, FK CASCADE), `status` `PENDING`/`HAS_CLAIM`/`NO_CLAIM`, `attempts`. Không cột tiền hay điểm. SQL migration khớp `createSql` trong `21.json` (đã đối chiếu bằng script).
- `OracleMarketDao`: ứng viên (mới nhất trước, trong cửa sổ, chưa quyết hoặc `PENDING`), upsert, quan sát kèo, dọn.
- `OracleClaimExtractor` (`@Singleton`): consent đúng bằng `true` mới chạy; ≤ 5 bài, mỗi bài 1 request (use case `oracle_claim`); lỗi gọi AI dừng cả lượt; JSON hỏng tăng `attempts` (2 lần thì `NO_CLAIM`); kèo hết hạn quá 30 ngày bị dọn (kể cả khi consent tắt); log chỉ in loại exception hoặc lý do từ chối.
- `SyncWorker` gọi extractor sau semantic indexer, trong `runCatching` (lỗi không làm hỏng sync).
- Consent riêng `oracle_claims_gemini_consent` (`Boolean?`), công tắc ở Cài đặt → Quyền riêng tư.
- UI: `OracleBadge` (lấp lánh nhẹ, tắt khi hệ thống tắt animation, `contentDescription` đọc kèm hành động), `OracleClaimSheet` (câu hỏi, "Dự kiến trước <ngày UTC>", tiêu chí, nhãn AI, chỉ một nút Đóng), nối vào `ArticleItem`, `Content`, `HomeEntry` theo khuôn Retraction Radar (`CompositionLocal`, không truy vấn từng dòng).
- 9 chuỗi `oracle_*` × 38 locale (EN và VI viết tay, 36 bản tiếng Anh), thêm bằng một script "tất cả hoặc không".
- Không dependency mới, không quảng cáo/IAP/tiền, manifest không đổi.

## Kiểm chứng (JVM)

- Full suite chạy lại sạch (`--rerun-tasks`, HEAD `8bf78fb6`): **2.987 test, 0 lỗi, 0 skip**, 415 tệp kết quả, không tệp cũ (nền trước 2.901, thêm 86). Lint: **0 lỗi, 2.465 cảnh báo** (bằng nền, **0 cảnh báo trong tệp ORACLE-01**). `assembleDevDebug` và `assembleDevDebugAndroidTest` thành công. `20.json` không đổi.
- Test mới: consent (4 + 2 công tắc), policy prompt (10), parse (13), migration 20→21 (5), DAO (7), extractor (20), nối `SyncWorker` (4), view model (4), badge (3), thẻ (6), nối giao diện (4), chuỗi (4).
- Mutation (sửa, test phải đỏ, khôi phục bằng `cmp`): từng giới hạn và biên của policy (500, 160, 240, 3 năm, hôm nay, cuối ngày, dấu `？`, fence, surrogate, JSON mode, "Do not invent"); unique và CASCADE của bảng; thứ tự, cửa sổ, tài khoản, bộ lọc `PENDING`, biên `<`/`<=` của dọn; mọi nhánh extractor (consent, 5 bài, dừng khi lỗi, `attempts`, hủy coroutine, log không lộ nội dung, thứ tự dọn trước consent); nối `SyncWorker` (nằm trong nhánh thành công, `runCatching`, sau indexer); khóa consent riêng; chuỗi (thiếu khóa ở một locale, `%1$s`, mất chữ "500"); UI (ngày UTC, hành vi nút).

## Kiểm chứng (Tecno KJ7 `115333744A005844`, Android 14, 2026-11-12)

Cổng thiết bị trước (không instrumentation, màn hình mở khóa, launcher ở foreground), sao lưu byte-exact Room, `-shm`, `-wal`, DataStore và `shared_prefs`.

- **androidTest 6/6** (`am instrument`, không `connected*`): nâng cấp DB v20→v21 từ `20.json` giữ nguyên dữ liệu cũ và tạo `oracle_market`; kèo bị xóa cùng bài (CASCADE); huy hiệu bấm được và đọc đúng; thẻ hiện câu hỏi/ngày/tiêu chí/nhãn AI; thẻ chỉ có một điều khiển. **Ba mutation trên máy đều bị bắt** (đổi múi giờ ngày, thêm nút "Bet", bỏ UNIQUE).
- **Smoke tay với app thật:**
  - Mở app trên DB đã nâng cấp: `user_version = 21`, `oracle_market` rỗng, `integrity ok`, 212 bài nguyên vẹn.
  - Công tắc "Find predictions in new articles" nằm cuối mục Quyền riêng tư, **mặc định tắt**; DataStore chưa có khóa.
  - **Consent tắt + sync thật** (212 → 234 bài): log **0** dòng `oracle_claim`, 0 dòng extractor.
  - **Bật công tắc**: DataStore ghi khóa (194 → 230 byte). Sync lần 1: đúng **5** request, cả 5 thành công, bảng có 5 dòng `NO_CLAIM`. Sync lần 2: 5 bài khác (10 dòng), không hỏi lại 5 bài đã quyết. Sync lần 3: gặp key `RateLimited`/`InvalidApiKey` (gateway thử lần lượt các key), bài thứ 3 mọi key đều lỗi → log `Prediction request failed: InvalidApiKey`, **lượt dừng** (đúng 3 request, không bài nào bị ghi cho bài lỗi), bảng 12 dòng.
  - **Kèo mẫu** (chèn bằng một instrumentation nhỏ vào DB đã sao lưu; mã trợ giúp đó không đưa vào repo): huy hiệu "Prediction" hiện ở danh sách đúng bài đó và ở màn đọc; bấm mở thẻ: "Will the ceasefire hold…?", "Expected by Dec 12, 2026", "How to check the answer", nhãn AI "…You cannot bet on it yet.", chỉ nút Close; bài khác không có huy hiệu.
- Quảng cáo toàn màn hình khi mở app (nhiều lần): chỉ dùng Back (cần vài lần Back mới đóng; có lần Back đưa sang trang Play Store của quảng cáo, vẫn chỉ Back để quay lại); không chạm "Continue to app", không cài gì (`pm list` không có gói quảng cáo).
- **Khôi phục:** Room, `-shm`, `-wal`, DataStore (về 194 byte, khóa consent biến mất), hai `shared_prefs` đều **BYTE IDENTICAL**; `Reader-journal` và `pairing_store.xml` vắng như trước; test package đã gỡ; app dừng; bản HEAD sạch được cài.

## Phát hiện khi làm

1. **Mutation sống sót làm lộ test yếu (đã vá):** thay nhánh `card == null` bằng một Dialog rỗng không làm test nào đỏ vì test chỉ đếm tag `oracle_sheet`. Đã thêm kiểm tra không có cửa sổ Dialog nào (`isDialog()`), mutation mới bị bắt.
2. **Hai đợt mutation không hợp lệ** (đường dẫn locale sai `values-de` thay vì `values-de-rDE`; pattern không có trong file; một lần owner hủy lệnh giữa chừng, `OracleBadge.kt` còn ở trạng thái đột biến, tôi khôi phục thủ công, kiểm lại bằng `grep` và chạy lại toàn bộ). Không tính chúng là "đã bắt".
3. **Test nguồn "không có chữ bet" ban đầu đỏ vì comment** nói betting sẽ đến ở ORACLE-02; thu hẹp test chỉ xét mã, bỏ dòng comment.
4. **`sed -i` trên macOS** làm lệnh `&&` dừng trước khi ghi `OracleClaimPolicy.kt` (phát hiện ngay vì test vẫn đỏ vì lý do không tồn tại, không phải lý do mới); ghi file lại bằng công cụ Write.
5. Sai tên Activity khi `am start` (`MainActivity` thay vì `SplashActivity`); app không mở, đã sửa.

## Chưa kiểm (nói thẳng)

- **Một kèo thật do AI sinh ra trên bài thật.** Smoke dùng key mặc định chung (FIX-09): 5 + 5 + 2 request thành công nhưng cả 12 bài BBC đều ra `NO_CLAIM` (câu trả lời `len=19`, đúng độ dài `{"hasClaim": false}`), sau đó key bị `RateLimited`/`InvalidApiKey`. Tin BBC thế giới/thể thao ít khẳng định tương lai có ngày, nên `NO_CLAIM` có thể đúng, nhưng **chưa chứng minh được** AI trả một kèo `HAS_CLAIM` hợp lệ. Kèo hiện trên máy là dữ liệu mẫu.
- Tỉ lệ JSON hợp lệ và chất lượng câu hỏi/ngày (RPG-02 từng bị 2/~25 câu "không phải JSON"; ở đây 12/12 hợp lệ nhưng đều "không có dự báo").
- Mất mạng giữa lượt, tắt công tắc rồi sync lại, TalkBack, ngôn ngữ ngoài EN, Pixel và máy thứ hai.
- Tác động pin/băng thông của việc chạy nền mỗi lần sync (tối đa 5 request).

## Giới hạn

- Mô tả ngắn nên bỏ sót dự báo nằm sâu trong thân bài.
- Ngày đáo hạn do AI suy ra có thể sai; chỉ kiểm định dạng và phạm vi.
- Chạy nền gọi Gemini khi người dùng không mở app (chỉ khi đã đồng ý).
- Key mặc định chung và có key hỏng (FIX-09): tính năng có thể không tạo kèo nào do quota hay key, không phải do mã.
- 36 locale dùng chuỗi tiếng Anh.
- Migration chạm DB người dùng thật: chỉ tạo bảng mới, có test và đã đối chiếu `21.json`.

## Commits trên `dev`

- `94da3d1a` docs: thiết kế · `6bd04d39` docs: kế hoạch
- `8ff2d546` consent riêng
- `8e93dcd0` policy: giới hạn, làm sạch, prompt
- `07c6964c` policy: kiểm hợp lệ
- `1c81cdca` bảng `oracle_market`, DAO, Room v21
- `9f8fa9df` extractor
- `8896b99d` nối `SyncWorker`
- `974d62f5` chuỗi 38 locale và công tắc
- `5736aa99` view model, huy hiệu
- `8bf78fb6` thẻ chỉ xem và nối giao diện
- `20afd761` androidTest trên máy
