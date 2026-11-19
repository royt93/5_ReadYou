# FRONT-02af (Nostr: regex ảnh bìa không còn chậm bậc hai)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng một tệp**: `NostrFeedMapper.kt` (regex ảnh bìa có giới hạn `\S{1,2040}?` thay `\S+?`, hằng `COVER_SCHEME`, `COVER_MAX_MIDDLE`). Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới. **Chỉ JVM**, không chạm thiết bị nào.
**Spec:** `docs/superpowers/specs/2026-11-19-front-02af-nostr-cover-regex-linear-time-design.md`. **Không có plan riêng** (theo lựa chọn của bạn: quy trình ngắn cho một dòng): spec ngắn, rồi test đỏ, sửa, đột biến, JVM đầy đủ.

## Nguyên nhân (phát hiện khi rà code 02z đến 02ae)
Regex ảnh bìa của FRONT-02aa chạy **bậc hai** với chữ độc hại. Đo bằng JDK (không đụng repo): 65.000 ký tự `https://` nối nhau không khoảng trắng mất **174 đến 206 ms** một bài (một lượt khác 836 đến 875 ms), chữ thường 0 ms. Mỗi bài tối đa 64 KiB, nên vài bài xấu làm đồng bộ chậm vài giây. Regex link của FRONT-02ac (`https://\S+`) **tuyến tính** (1 ms), không bị.

## Việc đã làm
Giới hạn phần giữa: `\S+?` thành `\S{1,2040}?` (2040 = `MAX_COVER_URL_LENGTH` − độ dài `https://`). Mọi link hợp lệ (tối đa 2048) vẫn khớp, nên hành vi không đổi với dữ liệu thật; chỉ link quá dài bị loại sớm hơn. Đo bản sửa: cùng chuỗi độc hại **nhanh hơn khoảng 15 lần** (836 ms xuống 52 ms; 206 ms xuống 21 ms).

## Kiểm chứng (đo thật)
- **TDD:** ba test mới trong `NostrCoverImageTest`. Trên mã cũ **đỏ đúng hai**: `aHostileNote_isScannedMuchFasterThanTheUnboundedPattern` (đo thật: `bounded=770 ms, unbounded=742 ms`, tức chưa nhanh hơn) và `aValidLinkInsideAVeryLongToken_isStillFound` (mã cũ khớp nhầm cả token dài và trả `null`, bản sửa lấy đúng `https://y.example/inner.png`); `aHostileNote_stillGivesAnArticleWithoutACover` xanh sẵn. Sau khi sửa xanh cả ba. Test thời gian dùng **tỉ lệ** (bản sửa so với bản cũ dựng trong test, ngưỡng 3 lần, đo được khoảng 15 lần), không dùng mili giây tuyệt đối.
- **Đột biến JVM 2/2 khớp dự đoán:** m1 về `\S+?` (đỏ đúng hai test mới); m2 giới hạn nhỏ hơn 100 ký tự (đỏ đúng `aLinkOfExactlyTheLimit_isKept_andOneMoreIsSkipped`). Mã khôi phục giống hệt (`cmp`), `git diff` production rỗng.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.889 test, 0 lỗi, 0 bỏ qua** (3.886 + 3), BUILD SUCCESSFUL.
- Diff production đúng một tệp; test đổi đúng một tệp.

## Giới hạn (nói thẳng)
- **Test thời gian có thể chập chờn** trên máy rất chậm hoặc bị tải; dùng tỉ lệ và lấy lượt nhanh nhất trong 5 lượt sau 3 lượt khởi động để giảm nguy cơ; ngưỡng 3 lần so với ~15 lần đo được.
- Bản sửa **vẫn chậm hơn tuyến tính thuần** (chặn ở 2040 ký tự mỗi điểm bắt đầu, nên trần là khoảng 2040 lần số điểm `https://`), nhưng đủ nhỏ: khoảng 20 đến 50 ms cho bài xấu nhất 65.000 ký tự.
- Chưa kiểm máy thật.
