# FRONT-02ag — Giữ ảnh bìa Nostr khi dấu câu theo sau URL

Ngày: 2026-11-20 · Nhánh: `dev` · Fix: `84dbbe64`.

## Hành vi

`see https://x.example/a.jpg.` nhận ảnh bìa `https://x.example/a.jpg`; URL trong ngoặc hoặc dấu nháy cũng nhận được. Dấu câu sau query bị cắt trước khi kiểm giới hạn 2.048 ký tự. `a.png.exe`, `a.pngx`, `a.jpg,and`, `a.jpg.bak` vẫn bị loại.

Chỉ đổi một tệp production: `NostrFeedMapper.kt`. Hai luật ảnh bìa và link bấm được dùng chung `TRAILING_PUNCTUATION`. Giữ giới hạn regex của FRONT-02af; không đổi UI, dữ liệu lưu, dependency hay cấu hình build.

## Kiểm chứng JVM

- Thêm 7 test trong `NostrCoverImageTest`: 6 đỏ trên mã cũ; ca từ chối dấu câu nối thêm chữ vốn đã xanh. Sau sửa, test liên quan xanh.
- Batch đột biến chỉ chạy nhóm `com.mckimquyen.reader.infrastructure.social.nostr.*`. Baseline trước và sau đều exit 0.

| Đột biến | Kết quả thực tế |
|---|---|
| g1: lookahead trở về `(?=\s|$)` | 5 test đỏ: ngoặc/nháy, đúng giới hạn kèm dấu chấm, mapper tới Article, dấu chấm sau đuôi, danh sách dấu câu |
| g2: bỏ `trimEnd` | 2 test đỏ: danh sách dấu câu và query |
| g3: bỏ `)` khỏi hằng dùng chung | 4 test đỏ: ngoặc/nháy, danh sách dấu câu, query, `trailingPunctuation_isLeftOutOfTheLink` |
| g4: không buộc khoảng trắng/hết chữ sau dấu câu | 2 test đỏ: `aLinkThatIsNotAnImage_isNotACover`, `punctuationFollowedByMoreLetters_isStillNotACover` |

Cả bốn exit 1 vì test, 0 lỗi biên dịch. Mỗi lượt khôi phục nguồn; `cmp` cuối xác nhận byte-identical. Log cục bộ: `/tmp/ag-batch.log`, `/tmp/ag-mut-g1.log` đến `/tmp/ag-mut-g4.log`.

Full suite sau batch: `./gradlew testDevDebugUnitTest --rerun-tasks` — **BUILD SUCCESSFUL**, exit 0, **3.928 test, 0 failure, 0 error, 0 skipped**, 3 phút 37 giây; 37 task đều thực thi. Số test đếm từ XML `app/build/test-results/testDevDebugUnitTest/`. Log cục bộ: `/tmp/ag-full.log`. Test thời gian FRONT-02af nằm trong lượt full này và vẫn xanh.

## Giới hạn

- Chỉ JVM; chưa kiểm ảnh trên thiết bị hoặc relay thật cho sửa đổi này. Không chạy lệnh thiết bị trong đợt kiểm chứng trên.
- Chỉ bài mới được ánh xạ; bài đã lưu không đổi.
- Theo đúng luật link hiện có: ký tự dấu câu hợp lệ thuộc URL nhưng đứng cuối cũng bị cắt. Không cố phân biệt dấu câu của câu văn với ký tự có chủ đích của URL.
- Không chạy lại lint hoặc release build cho riêng 02ag; không dùng kết quả cũ làm bằng chứng mới.
