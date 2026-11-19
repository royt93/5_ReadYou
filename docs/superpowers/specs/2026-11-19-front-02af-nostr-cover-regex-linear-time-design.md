# FRONT-02af — Regex ảnh bìa Nostr không còn chậm bậc hai (thiết kế ngắn, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02aa (`NostrFeedMapper.coverImageUrl`), phát hiện khi rà code 02z đến 02ae

> Trạng thái: owner đã chọn (2026-11-19) sửa bằng giới hạn độ dài trong regex, theo quy trình ngắn (spec, plan, TDD, đột biến). Chưa có plan, chưa có mã.

## Vấn đề (đo thật bằng JDK, không đụng repo)
Regex ảnh bìa `https://\S+?\.(?:jpe?g|png|gif|webp)(?:[?#]\S*)?(?=\s|$)` chạy **bậc hai** với chữ độc hại: 65.000 ký tự gồm `https://` nối nhau không khoảng trắng mất **khoảng 200 ms** (174 đến 206 ms; chữ thường 0 ms). Mỗi bài tối đa 64 KiB (giới hạn khung relay) nên một bài xấu tốn tối đa vài trăm mili giây, 20 bài xấu vài giây mỗi lần đồng bộ. Regex link của 02ac (`https://\S+`) **tuyến tính** (1 ms), không bị.

## Thiết kế
Giới hạn phần giữa: `\S+?` thành `\S{1,N}?` với `N = MAX_COVER_URL_LENGTH − độ dài của "https://"` (= 2040). Đo bản sửa: cùng chuỗi độc hại giảm từ ~206 ms xuống ~21 ms; chữ thường, link đúng 2048 ký tự và link 2049 ký tự cho kết quả giống hệt bản cũ. Mọi link hợp lệ (dài tối đa `MAX_COVER_URL_LENGTH`) vẫn nằm trong N, nên **hành vi không đổi** với dữ liệu thật; chỉ link quá dài bị loại sớm hơn (trước đây khớp rồi bị bộ lọc độ dài bỏ).
Hằng có tên (R5): `COVER_SCHEME_LENGTH = "https://".length`; `N` tính từ `MAX_COVER_URL_LENGTH`. Chỉ đổi `NostrFeedMapper.kt` (một regex và một hằng). Không đổi UI, chuỗi, Room, DataStore, manifest, `app/build.gradle`; không dependency; chỉ JVM.

## Kiểm chứng
- **Test mới** trong `NostrCoverImageTest`: (1) chữ độc hại 65.000 ký tự (`https://` lặp) chạy `coverImageUrl` dưới ngưỡng rộng (đặt 1.000 ms, bản cũ ~200 ms đo trên máy dev: **nói thẳng ngưỡng rộng nên có thể không đỏ với bản cũ trên máy nhanh**; vì vậy thêm (2)); (2) khẳng định **cấu trúc**: bản sửa không khớp một link có phần giữa dài hơn 2040 ký tự nhưng vẫn khớp link đúng giới hạn (`aLinkOfExactlyTheLimit…` có sẵn); (3) bài độc hại qua `toArticles` vẫn cho ra bài (không nghẽn, không ném lỗi).
- **Độ nhạy:** vì thời gian khó làm test đỏ ổn định, **bằng chứng có răng là đột biến**: bỏ `{1,N}` (về `+`) phải làm test (1) chậm hơn bản sửa rõ rệt; kiểm bằng một phép đo trong cùng test so hai regex (bản cũ dựng trong test với cùng cờ) và khẳng định **bản sửa nhanh hơn bản cũ ít nhất 3 lần** trên chuỗi độc hại (đo trên máy dev: ~10 lần). Nếu lượt đo thật cho tỉ lệ dưới 3, **dừng và hỏi owner** thay vì hạ ngưỡng.
- Đột biến JVM: (m1) về `\S+?` → test tỉ lệ đỏ; (m2) N nhỏ hơn (`MAX_COVER_URL_LENGTH − 100`) → `aLinkOfExactlyTheLimit…` đỏ; (m3) N lớn hơn không đổi hành vi (**đột biến tương đương** về đúng/sai, chỉ chậm hơn: không dùng). Dự đoán suy luận từng test, kiểm tự động.
- JVM full `--rerun-tasks` đo lại số test (số trước 3.886). Diff production đúng một tệp.

## Giới hạn đã biết
- Test thời gian có thể chập chờn trên máy rất chậm: dùng tỉ lệ (bản sửa so bản cũ trong cùng lượt chạy) thay vì mili giây tuyệt đối, và ngưỡng 3 lần so với ~10 lần đo được.
- Chỉ sửa regex ảnh bìa; regex link 02ac tuyến tính, không đổi.
- Chưa kiểm máy thật; chỉ JVM.

## Điều chỉnh khi thực thi
- **Không lập plan riêng** (chọn quy trình ngắn); bước: test đỏ, sửa, đột biến, JVM đầy đủ.
- Số đo thật: bản cũ `unbounded=742 ms` trong test (một lượt đo JDK khác ~850 ms, lượt khác 206 ms; thay đổi theo máy và lượt); tỉ lệ bản sửa so bản cũ ~15 lần, ngưỡng test 3 lần.
- Test thứ hai (`aValidLinkInsideAVeryLongToken_isStillFound`) là một **khác biệt hành vi có lợi** ngoài thời gian: bản cũ khớp nhầm cả token dài rồi bỏ nó và mất link con hợp lệ; bản sửa tìm đúng link con.
- **JVM đo lại:** 3.889 test, 0 lỗi (3.886 + 3). Đột biến 2/2 khớp.
