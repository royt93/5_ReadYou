# ECHO-02 — Góc nhìn khác trong kho của bạn (Contrasting Coverage) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Todo gốc là "Khung Luận Điểm Phản Biện Thép" (AI sinh lập luận phản biện). Owner duyệt bản khác, an toàn và offline hơn:
- **Có:** ở cuối bài đọc, thẻ "Góc nhìn khác trong kho của bạn" liệt kê tối đa 5 bài **đã lưu trong chính kho của người dùng**, từ **nguồn khác**, nhắc **cùng chủ đề** và có **từ ngữ trái chiều ngay trong tiêu đề**. Chủ đề là 1–3 cụm tên riêng tự trích từ tiêu đề bài đang đọc; người dùng chạm chip để đổi cụm. Chạm một hàng để mở bài đó.
- **Không có:** AI, sinh lập luận phản biện, mạng, API key, quảng cáo, mua hàng, thay đổi Room (DB vẫn v20). Thẻ ghi rõ "Không phải kiểm chứng sự thật".
- Khớp **từ vựng**, không hiểu ngữ nghĩa: một từ khóa như "warn" hay "but" chỉ nói rằng từ đó có mặt, không nói bài kia thật sự phản bác. Cụm chủ đề khớp nguyên cụm (không khớp chuỗi con), ở tiêu đề hoặc mô tả ngắn; từ khóa trái chiều chỉ tính ở **tiêu đề**.
- Thẻ dùng chung công tắc `Headline signals` (Cài đặt → Quyền riêng tư).

## Cấu trúc mã
- `domain/steelman/`: `WholePhraseMatcher`, `ContrarianCueLexicon` (EN + VI), `TopicPhraseExtractor` (tối đa 3 cụm, dài tối thiểu 4 ký tự), `ContrastingCoverageFinder` (khác feed, khác bài, khớp cụm nguyên, có từ khóa, mới nhất trước, tối đa 5), `ContrastingCoverage` (`ContrastingArticle`, `ContrastingCoverageState`).
- `ReadingViewModel`: quan sát bài đang đọc, truy vấn `articleDao.queryRelatedArticleListWithFeed` (giới hạn 40 ứng viên, LIKE đã escape, không đọc cột body theo FIX-17) trên IO, lọc trên Default; lỗi truy vấn chỉ làm thẻ không hiện. `selectContrastPhrase` đổi cụm.
- `ui/page/home/read/steelman/ContrastingCoverageCard.kt`, nối vào `Content.kt` (trên thẻ `BrainQuizCard`) và `ReadingPage.kt` (mở bài bằng `"${RouteName.READING}/$targetId"`).
- 8 chuỗi `echo_contrast_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh dự phòng).

## Phát hiện khi làm
1. **Lỗi thật chỉ lộ ra khi smoke trên máy:** bài BBC "Musk says SpaceX restricted Ukraine's Starlink connection…" không hiện thẻ vì `TopicPhraseExtractor` nối "Ukraine's Starlink" thành một cụm "Ukraine Starlink" (cụm dài nhất, xếp đầu), cụm đó không khớp bài nào. Sửa: sở hữu cách (`'s`/`’s`) kết thúc một chuỗi tên; thêm test hồi quy `possessiveEndsARun_soTwoNamesAreNeverMergedIntoOnePhrase` (commit `f09fec9b`). Sau sửa chip hiện đúng `Starlink`, `Ukraine`, `SpaceX`.
2. **Probe dữ liệu thật** (bản sao kho Pixel, 214 bài, 1 feed; probe đã xóa): 158 bài (73,8%) trích được ít nhất một cụm; 17 tiêu đề (7,9%) có từ khóa trái chiều (`but` 8, `warning` 2, `risk` 2, còn lại 1 mỗi loại). Các cụm hay gặp đều là tên riêng thật (Man City, Russia, Ukraine, Zelensky, Brexit…), không có từ rác. Vì kho chỉ có một feed nên finder không thể trả kết quả trên dữ liệu gốc.

## Kiểm chứng
- **Unit:** 1.782 test, 0 lỗi, 0 skip (nền trước task 1.746, thêm 36). Nhóm `steelman` 31/31.
- **Test chập chờn, đã tìm ra nguyên nhân và sửa:** `ReadingViewModelPositionTest.initData_loadsSavedPositionForArticle` thỉnh thoảng fail (`Dispatchers.Main was accessed when the platform dispatcher was absent`). Nguyên nhân: test dựng `ReadingViewModel` không truyền `defaultDispatcher`, nên observer `init` mới của ECHO-02 (`withContext(defaultDispatcher)`) chạy trên `Dispatchers.Default` thật rồi tiếp tục trên Main sau khi `tearDown()` đã `resetMain()`. Đây là hệ quả trực tiếp của thay đổi ECHO-02 nhưng lỗi nằm ở test thiếu tiêm dispatcher (3 test khác cùng kiểu đã truyền dispatcher theo vị trí nên không dính). Sửa: truyền `defaultDispatcher = dispatcher` trong test. Tái hiện trước khi sửa ở lần chạy cả bộ thứ 2; sau khi sửa 6 lần chạy cả bộ liên tiếp đều pass 1.782/1.782.
- Test thêm: `WholePhraseMatcherTest` 4, `ContrarianCueLexiconTest` 4, `TopicPhraseExtractorTest` 8, `ContrastingCoverageFinderTest` 8, `ReadingViewModelContrastTest` 5, `ContrastingCoverageRoomIntegrationTest` 2 (Room in-memory, hai feed, an toàn với ký tự `%` `_`), `ContrastingCoverageCardTest` 5. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 0 fatal, 2.463 cảnh báo (nền trước 2.459). **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** `ContrastingCoverageDeviceTest` 1/1 (`am instrument`, không dùng `connectedDevDebugAndroidTest`).
- **Smoke thật trên Pixel (kho đã gieo thêm feed "Science Wire" trên BẢN SAO):** mở bài BBC về Starlink, cuộn xuống: thẻ hiện đúng trên thẻ AI Quiz, có ba chip, một hàng "Scientists warn Starlink constellation disrupts astronomical research", nguồn Science Wire, huy hiệu `warn`, dòng giới hạn "Không phải kiểm chứng sự thật". Chạm hàng: mở đúng bài của Science Wire. Công tắc `Headline signals` tắt được trong Cài đặt → Quyền riêng tư.

## Giới hạn đã biết
1. **Từ vựng, không ngữ nghĩa.** Có thể trả bài chỉ nhắc cùng cụm và có chữ "but"/"risk" mà không hề phản biện; tiêu đề tiếng Anh/Việt là hai ngôn ngữ duy nhất có danh sách từ khóa.
2. **Kho một nguồn thì không bao giờ hiện thẻ** (đúng thiết kế). Smoke phải gieo feed giả vào bản sao DB; trên dữ liệu thật của owner, thẻ chỉ xuất hiện khi có từ hai feed trở lên.
3. Cụm chủ đề tự trích từ chữ hoa đầu từ nên tiêu đề toàn chữ thường hoặc tiêu đề không chứa tên riêng không có thẻ.
4. **Chưa quan sát trên máy:** việc thẻ biến mất khi tắt `Headline signals` (tôi đã tắt công tắc nhưng khôi phục DataStore ngay, không mở lại bài để nhìn); chạm chip đổi cụm (chỉ có test Compose, test Robolectric và instrumentation trên renderer thật). Hành vi "không vẽ khi tắt" được test bằng `card_notDrawn_whenDisabled`.
5. **Ads:** `AdActivity` toàn màn hình xuất hiện hai lần (lần mở app đầu và lần mở lại sau khi cài lại APK); đã tự đóng bằng nút "Tiếp tục đến ứng dụng" lấy từ dump giao diện mới, không chạm nội dung quảng cáo. Banner nhỏ ở đáy không che nội dung.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal`) và DataStore (20 byte, không rỗng; `integrity_check` ok trên bản sao; 214 bài, 1 feed). DB gieo (2 feed, 215 bài) chỉ tồn tại trên bản sao rồi mới đẩy lên máy; sau smoke khôi phục cả Room và DataStore bằng `run-as sh -c 'cat > ...'`. `cmp` xác nhận cả bốn file giống hệt bản sao lưu, `integrity_check` ok, 214 bài, 1 feed, gói test đã gỡ. Không xóa DataStore.

## Commits trên nhánh `dev`
- `8e5654e5` docs: thiết kế ECHO-02
- `ed14f935` docs: kế hoạch ECHO-02
- `9a244e64` feat(echo-02): matcher, từ khóa trái chiều, trích cụm chủ đề
- `599d13d1` feat(echo-02): finder và state
- `62c589fd` feat(echo-02): chuỗi ở 38 locale
- `37f8aef4` feat(echo-02): `ReadingViewModel` và test tích hợp Room
- `5ab74585` feat(echo-02): `ContrastingCoverageCard` và nối vào Reader
- `f09fec9b` fix(echo-02): sở hữu cách kết thúc chuỗi cụm chủ đề
- commit hoàn tất (test instrumentation và tài liệu) nằm ngay sau các commit trên
