# ECHO-03 — Thẻ hai góc nhìn 9:16 (Two-Headline Card) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Todo gốc yêu cầu thẻ "đối kháng" với nửa dưới là **luận điểm phản biện của AI** lấy từ ECHO-02. ECHO-02 thực tế (owner đã duyệt) là tìm bài trong kho cục bộ, offline, **không AI, không sinh lập luận**: nó chỉ nói tiêu đề có từ ngữ trái chiều, không nói bài kia phản bác bài này. Vì vậy task này làm bản trung thực:
- Mỗi hàng bài trên thẻ "Góc nhìn khác trong kho của bạn" (ECHO-02) có nút **"Xuất thẻ"**. Chạm nút thì mở sheet xem trước ảnh 1080×1920: nửa trên là bài đang đọc (tiêu đề, nguồn, ngày), nửa dưới là bài từ nguồn khác bạn chọn (tiêu đề, nguồn, ngày, từ khoá như `warn`).
- Dòng trên ảnh: "Two headlines from my reading archive. Not a fact-check." Chia sẻ qua share sheet của Android; nội dung chia sẻ là `#RSSCatHub #Debate`.
- Giao diện và ảnh dùng chữ "hai tiêu đề / hai góc nhìn", **không** dùng "phản biện" hay "debate"; `#Debate` chỉ nằm trong `EXTRA_TEXT`. Có test canh điều này.
- Không có: AI, lập luận phản biện, ô nhập ghi chú, nhiều bài trong một thẻ, đổi Room (vẫn v20), mạng, API key, quảng cáo, mua hàng.
- Nút chỉ có khi thẻ ECHO-02 hiện (kho có từ hai nguồn trở lên) và công tắc "Tín hiệu giật tít & cảm xúc tiêu đề" bật.

## Cấu trúc mã
- `domain/steelman/DualPerspectiveSpec`, `DualPerspectiveTruncator` (giới hạn 140 ký tự, riêng nửa dưới 90 ký tự vừa 4 dòng).
- `infrastructure/quote/DualCardRenderer` (thuần `android.graphics`, hằng số có tên), `DualBitmapRenderer` + binding Hilt trong `QuoteModule`.
- `QuoteCardSharer`: thêm `writePng(bitmap, filePrefix, ...)` và `buildShareIntent(..., shareText)`; hành vi mặc định không đổi.
- `ui/page/home/read/steelman/DualCardViewModel` (một `@Inject` constructor, tiêm `defaultDispatcher`, recycle bitmap), `DualCardUiState`, `DualCardSheetContent`.
- `ContrastingCoverageCard`: nút xuất mỗi hàng (tham số `onExportArticle` mặc định `null`); `Content.kt` và `ReadingPage.kt` nối callback, drawer, `BackHandler` và đồng bộ khi vuốt đóng; `ReadingViewModel.openDualCard/dismissDualCard`.
- 8 chuỗi `echo_dual_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh).

## Phát hiện khi làm
1. **Smoke trên máy lộ lỗi bố cục mà test của plan không bắt được:** tiêu đề nửa dưới dài chồng lên dòng nguồn/ngày và dòng chú thích, vì plan cho nửa dưới 7 dòng nhưng chỉ còn chỗ cho 4. Sửa: tách `MAX_LOWER_HEADLINE_LINES = 4`, đặt vùng footer riêng, và thêm test hình học (`renderWithBounds`) khẳng định không khối chữ nào chồng nhau hay tràn ra ngoài ảnh. Kiểm tra ban đầu bằng đếm pixel không phát hiện được lỗi này; ảnh chụp trên máy mới thấy.
2. Sau khi giới hạn 4 dòng, phần rút gọn của renderer và ghi chú "đã rút gọn" lệch nhau (renderer cắt ở 4 dòng, ghi chú chỉ bật khi quá 140 ký tự). Đo thực tế: 100 ký tự đã thành 4 dòng, 120 ký tự thành 5 dòng. Sửa: giới hạn ký tự nửa dưới 90, renderer và ViewModel dùng cùng một giá trị.
3. `QuoteCardSharerTest`: thêm hai test `buildShareIntent` riêng thì cả hai fail ("Failed to find configured root"), vì `FileProvider` của AndroidX giữ cache path theo cả JVM và `cacheDir` của Robolectric đổi giữa các test. Gộp phần kiểm tra `EXTRA_TEXT` vào test intent hiện có.
4. Overload mới `writePng(bitmap, String, ...)` làm các stub MockK `any()` trong `QuoteCardViewModelTest` bị mơ hồ; chỉ rõ `any<QuoteCardTemplate>()`.

## Kiểm chứng
- **Unit:** 1.836 test, 0 lỗi, 0 skip (nền trước task 1.799), đo sau cả hai bản sửa bố cục. Chạy toàn bộ ba lần liên tiếp đều sạch, không flaky. Thêm: `DualPerspectiveTruncatorTest` 6, `DualCardRendererTest` 10 (gồm 3 test hình học), `DualCardViewModelTest` 9, `DualCardSheetContentTest` 5, `ReadingViewModelDualCardTest` 3, `ContrastingCoverageCardTest` +2, `QuoteCardSharerTest` +1, `DualCardWordingTest` 1. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 0 fatal, 2.463 cảnh báo (không đổi). **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** `DualCardDeviceTest` 1/1 (`am instrument`, không dùng `connectedDevDebugAndroidTest`).
- **Smoke thật trên Pixel** (kho gieo feed "Science Wire" trên BẢN SAO DB): mở bài BBC "Musk says SpaceX restricted Ukraine's Starlink connection…", cuộn tới thẻ "Góc nhìn khác" thấy nút "Xuất thẻ" ở hàng Science Wire. Chạm: sheet "Chia sẻ hai tiêu đề" hiện ảnh hai nửa (đen–neon trên, trắng–đỏ dưới), không chồng chữ sau khi sửa. Chạm "Chia sẻ": chooser "Chia sẻ hình ảnh" mở với ảnh thu nhỏ và nội dung `#RSSCatHub #Debate`; thoát bằng Back, không gửi đi đâu. Tắt "Tín hiệu giật tít & cảm xúc tiêu đề" rồi mở lại bài: không còn thẻ, không còn nút xuất (0 nút, 0 tiêu đề thẻ); thẻ quiz vẫn có.

## Cập nhật 2026-11-02: kiểm lại trên Tecno với hai nguồn thật
Smoke trước dùng feed gieo trên bản sao DB. Sau STRAT-02, trên Tecno `115333744A005844` có 9 feed thật: ở bài BBC "Why does Elon Musk want to merge Tesla and SpaceX?" thẻ ECHO-02 hiện bài OpenAI News và nút "Export card". Chạm nút: sheet "Share two headlines" hiện ảnh hai nửa (trên: bài BBC, 21 thg 10 2026; dưới: "The court rejects Elon's latest attempt to slow OpenAI down", OpenAI News, 14 thg 3 2025, `wording: rejects`) với dòng "Two headlines from my reading archive. Not a fact-check." Không mở chooser chia sẻ trên lần kiểm này (đã kiểm ở lần trước trên Pixel). Dữ liệu máy đã khôi phục.

## Giới hạn đã biết
1. **Hai tiêu đề cạnh nhau vẫn có thể bị đọc như "nguồn này phản bác nguồn kia".** Dòng "Not a fact-check" trên ảnh giảm nhưng không loại bỏ điều đó. Dòng chú thích hiện chỉ có tiếng Anh vì được vẽ bằng `android.graphics` (không đi qua tài nguyên chuỗi).
2. Chỉ xuất được khi kho có từ hai nguồn trở lên và có bài chứa từ khoá trái chiều; kho Pixel thật chỉ có một feed nên **người dùng này sẽ không thấy nút** nếu chưa thêm nguồn thứ hai. Smoke dựa vào feed gieo trên bản sao.
3. Nhiều ứng dụng bỏ `EXTRA_TEXT` khi nhận kèm ảnh nên hashtag có thể không đến nơi. Tôi chỉ xác nhận chooser của hệ thống hiển thị đúng nội dung, không gửi sang ứng dụng nào.
4. Nửa dưới bị cắt ở 90 ký tự, nên tiêu đề dài mất phần đuôi (có dấu "…" và ghi chú trong sheet).
5. **Chưa quan sát trên máy:** ghi chú "đã rút gọn" khi tiêu đề dài (chỉ có unit test; bài smoke dùng tiêu đề ngắn), nút "Thử lại" khi lỗi render, và việc ảnh PNG được ghi đúng vào `cache/quote_cards` sau khi nhấn Chia sẻ (tôi chỉ thấy ảnh thu nhỏ trong chooser).
6. **Quảng cáo:** `AdActivity` toàn màn hình xuất hiện hai lần trong lần smoke đầu và một lần sau khi đổi công tắc. Hai lần đầu tự đóng bằng nút "Tiếp tục đến ứng dụng" lấy từ dump mới; lần cuối nút có nhãn không hiện (chỉ có dấu X của creative), tôi không chạm creative mà dừng hỏi, và **bạn đã đóng quảng cáo giúp**. Banner nhỏ ở đáy không che nội dung.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal`) và DataStore (20 byte, không rỗng; `integrity_check` ok trên bản sao; 214 bài, 1 feed). DB gieo (2 feed, 215 bài) chỉ tồn tại trên bản sao rồi mới đẩy lên máy. Sau smoke khôi phục cả bốn file bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận giống hệt bản sao lưu, `integrity_check` ok, 214 bài, 1 feed, gói test đã gỡ. Không xóa DataStore. Công tắc "Tín hiệu giật tít" được khôi phục về trạng thái ban đầu nhờ việc khôi phục DataStore.

## Commits trên nhánh `dev`
- `2655c008` docs: thiết kế ECHO-03
- `b23009b8`, `d0a0dfd8` docs: kế hoạch ECHO-03
- `f17dc265` feat(echo-03): spec và truncator
- `0cefe704` feat(echo-03): renderer 9:16
- `90e73e06` feat(echo-03): sharer nhận share text và tiền tố tên tệp
- `94514afa` feat(echo-03): chuỗi 38 locale
- `33eb1853` feat(echo-03): `DualCardViewModel` và sheet
- `9d051618` feat(echo-03): nút xuất trên thẻ ECHO-02 và nối vào Reader
- `73c1e02d` fix(echo-03): chống chồng chữ ở nửa dưới
- `0d8e5e37` fix(echo-03): cắt tiêu đề nửa dưới ở 90 ký tự
- `29a3f985` feat(echo-03): hoàn tất thẻ hai tiêu đề từ kho cục bộ
