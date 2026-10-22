# FIX-18/19 — Bản sửa sau rà soát

- Ngày: 2026-10-22
- Trạng thái: đã thực thi Inline Execution và kiểm chứng (2026-10-22); chưa commit/push.
- Không tự commit/push. Quy tắc này áp dụng cả spec và plan.

## Mục tiêu

Khép sai lệch đã xác minh của FIX-18/19: URL thô trong log, thiếu policy gate trực tiếp tại OfflinePrecache, forensic counter/assertion không đúng cam kết và tài liệu chưa đồng bộ. Không điều tra thêm writer lịch sử nếu chưa có bằng chứng mới.

## Bằng chứng đầu vào

- `./gradlew testDevDebugUnitTest --rerun` chạy thật: 1246 tests, 0 failures/errors/skipped; không dùng `JAVA_TOOL_OPTIONS`. Lượt trước có `FROM-CACHE` không được tính là chạy mới.
- `RssHelper.parseFullContent()` ghi nguyên link ở hai log từ chối oversized; `searchFeed()` ghi nguyên entry link và exception khi bỏ entry lỗi.
- `OfflinePrecacheService` chỉ dựa cap của `parseFullContent()`, chưa gate trực tiếp/test over-cap như plan. Chưa chứng minh bypass cap trong production hiện tại.
- Script forensic tính tổng C0 là 525,801 do chuỗi loại trừ bị escape sai. C0 trừ LF/CR/tab là 518,528, trong đó có 63,610 NUL; trừ thêm NUL còn 454,918.
- Script chỉ kiểm độ dài/hash body để quyết định exit, chưa kiểm ID/raw/feed như plan.
- Test network mang tên exact-limit nhưng fixture 1,043,573 bytes, thấp hơn cap 5,003 bytes. Policy/RSS tests riêng đã kiểm exact-limit.
- Mục FIX-18 bị thay mất trong `doc/feature.md`; trạng thái push và checklist FIX-18/19 còn cũ. Commit đến `cff1472f` đã nằm trong remote-tracking `origin/dev` khi rà soát.

## 1. Log URL trong RssHelper

Dùng helper redaction hiện có cho entry URL và URL của hai nhánh Readability oversized; không tạo logging framework hay dependency mới. Helper loại userinfo, query và fragment; path tiếp tục bị giới hạn bằng named constant hiện có. URL không hợp lệ trả placeholder, không quay về URL thô.

Nhánh malformed-entry chỉ log URL đã redact và tên loại exception; không log message hoặc gắn throwable vì chúng có thể chứa URL thô. Việc bỏ entry lỗi, parser, request URL thật và nội dung lưu không đổi. Đây không phải cam kết scrub mọi log/exception toàn ứng dụng; chỉ sửa các nhánh đã xác định.

Tests dùng URL giả chứa token/userinfo/fragment và body sentinel. Kiểm nhánh known-length, chunked oversized và malformed-entry không lộ các giá trị đó, vẫn giữ source/byte count/loại lỗi cần chẩn đoán. Test entry hiện có tiếp tục pass.

## 2. OfflinePrecache

Chỉ áp `FullContentPolicy.accept()` cho kết quả parse tải mới. Không gate lại cache lịch sử nonblank; tiếp tục reuse cache mà không ghi lại hay fetch lại.

Nội dung mới vượt 1,048,576 UTF-8 bytes bị từ chối, không gọi `updateFullContent`, không tăng số bài cache thành công, không retry. Có thể tiếp tục prefetch ảnh metadata theo hành vi cũ; không lấy ảnh từ nội dung đã từ chối. Log từ chối chỉ source, article ID và byte count, không body. Bài tiếp theo vẫn được xử lý.

Tests chứng minh dưới/đúng cap được ghi bằng narrow update, cap+1 không ghi, cached oversized không bị sửa/fetch lại, và bài hợp lệ sau bài bị từ chối vẫn được cache. Không ghi entity tái dựng từ metadata.

## 3. Forensic script

Giữ Python stdlib, kết nối SQLite `mode=ro`, chỉ SELECT và đóng connection trong `finally`. Không cần thêm CLI/config cho đường dẫn backup hiện có.

Định nghĩa output rõ:
- `nul_chars`: số NUL.
- `control_chars`: C0 trừ LF/CR/tab, **bao gồm NUL**; giá trị backup mong đợi 518,528.
- `replacement_chars`: U+FFFD, không thuộc C0; giá trị mong đợi 1,713,674.

Thành công phải đồng thời thỏa: đúng một row; ID dạng account ID 1 + `$` + UUID hợp lệ; title khớp tiêu chí truy vấn; feed URL chính xác `https://www.npr.org/sections/news/`; rawDescription thực sự là chuỗi rỗng; fullContent là chuỗi có độ dài 12,324,490 code points, 15,890,764 UTF-8 bytes và SHA-256 đã ghi nhận; counter khớp số đã xác minh. Không đổi tiêu chí để suy ra exact historical writer.

Sai metadata hoặc hash/counter trả exit khác 0; lỗi đọc/query/giải mã dữ liệu phải báo lỗi metadata-only, không traceback chứa dữ liệu row. Không in body, ID/title thô hay URL đầy đủ. Giữ output hash/host/count cần thiết.

Thêm một file self-test stdlib `unittest`, chạy không cần backup lịch sử: dữ liệu tổng hợp, kiểm LF/CR/tab/NUL, UUID không hợp lệ, raw/feed sai, lệch body metadata, lỗi đọc và exit/output không lộ sentinel. Chỉ tách helper thuần nhỏ cần cho test; không tạo abstraction repository/factory.

## 4. Tài liệu và kiểm chứng

- Khôi phục mục FIX-18 từ lịch sử, chỉnh theo hành vi và giới hạn thực tế; không thay mất FIX-19.
- Sửa báo cáo FIX-18/19: trạng thái các commit cũ đã push, counter đúng định nghĩa, phân biệt cap parser với gate writer được bổ sung trong đợt này.
- Bổ sung ghi chú đối chiếu cho plan cũ: device/instrumentation đã ghi nhận trong báo cáo lịch sử khác với UI smoke kế hoạch ban đầu. Không đánh dấu bước chưa thực hiện thành đã thực hiện; ghi rõ thay thế/không chạy.
- Số test cũ không sửa bằng suy đoán; nếu matrix không có bằng chứng giữ số cũ, ghi rõ cần đối chiếu và dùng số chạy mới cho đợt sửa.
- Đổi tên test network thành near-limit, không đổi fixture để giả nhận exact-limit coverage.
- Ghi kết quả follow-up trong báo cáo liên quan; không mở epic/feature mới độc lập.

Kiểm chứng theo TDD: regression phải fail trên code cũ vì đúng hành vi cần sửa, rồi pass sau sửa. Chạy self-test Python, forensic backup read-only (chỉ metadata), focused tests, full unit suite buộc chạy mới, `assembleDevDebug`, `compileDevDebugAndroidTestKotlin` và `git diff --check`. Đếm XML mới, không cộng kết quả stale. Không claim lint/device/instrumentation execution nếu chưa chạy.

## Ràng buộc

Không sửa/xoá body lịch sử, không migration/schema change, không cap rawDescription, không đổi parser/request semantics, không thêm dependency, không chạm ad/IAP/money hoặc FIX-09. Không tương tác thiết bị. Không tự commit/push. `.idea/deviceManager.xml` ngoài phạm vi.

## Tiêu chí chấp nhận

1. Các log URL đã chỉ định không lộ query/userinfo/fragment hoặc body sentinel qua message/throwable.
2. OfflinePrecache từ chối fetched content over-cap, giữ cached historical và tiếp tục xử lý bài khác.
3. Forensic count đúng định nghĩa; mọi điều kiện metadata đã cam kết tham gia quyết định thành công; self-test độc lập backup.
4. Mục FIX-18/19 cùng tồn tại, trạng thái push/checklist/report phản ánh bằng chứng và giới hạn thật.
5. Kiểm chứng mới được báo chính xác; chưa commit/push nếu chưa được yêu cầu.
