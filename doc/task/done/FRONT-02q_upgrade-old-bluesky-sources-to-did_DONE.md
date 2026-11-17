# FRONT-02q (Nâng cấp nguồn Bluesky cũ lên lưu DID khi đồng bộ thành công)

**Date:** 2026-11-17  
**Branch:** `dev` (không push)  
**Commit:** `d977a213` (hàm DAO có điều kiện + test), `94018f4d` (khối nâng cấp trong đồng bộ + test)  
**Scope:** **chỉ JVM, không chạm thiết bị nào**. Mã production đổi **hai tệp, 17 dòng thêm, 0 dòng bỏ**: `FeedDao.updateSourceUrlIfUnchanged` (10 dòng) và một khối 7 dòng trong `LocalRssSv.queryBlueskyArticles`. **Không đổi schema Room** (băm `app/schemas/.../22.json` giữ nguyên `45d76f04…`, `git diff` rỗng ở `app/schemas`, không migration, không cột mới), không chuỗi, locale, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP.  
**Spec / plan:** `docs/superpowers/specs/2026-11-17-front-02q-upgrade-old-bluesky-sources-to-did-design.md`, `docs/superpowers/plans/2026-11-17-front-02q-upgrade-old-bluesky-sources-to-did.md`.

## ⚠ Điều phải nói trước: full suite JVM KHÔNG sạch lúc viết báo cáo này
Báo cáo các việc trước (02i đến 02p) đều ghi một con số "full suite, 0 lỗi". **Báo cáo này không có con số đó**, và lý do được ghi rõ ở mục "Full suite" bên dưới. Phần đã đo được là **11 lớp test liên quan, 165 test, 0 lỗi** khi chạy riêng.

## Việc đã làm
FRONT-02o chỉ nhớ DID cho nguồn **đăng ký mới**. Nguồn đăng ký trước đó có `sourceUrl = url = bsky:<handle>`, nên nếu chủ tài khoản đổi handle thì FRONT-02n/02o không nhận ra nó là trùng. Mỗi lần đồng bộ thành công, API đã trả DID; nay ghi DID đó vào cột `sourceUrl` của nguồn cũ, **không cần hỏi mạng thêm**.

- Ghi bằng **một câu `UPDATE` có điều kiện**: `UPDATE feed SET sourceUrl = :new WHERE id = :id AND sourceUrl IS :expected`. Chỉ đổi **một cột**; không đổi `url`, `name`, `icon`, `groupId`, `isNotification`, `isFullContent`. Nếu người dùng vừa sửa địa chỉ (02p) hoặc có ghi khác chen vào thì điều kiện sai và **không ghi gì**. `IS` (không phải `=`) để khớp cả khi cột là `NULL`.
- Chỉ ghi **sau** khi chắc đồng bộ thành công (đã có bài), và **chỉ khi địa chỉ đồng bộ chưa phải DID**: sau lần ghi đầu điều kiện sai nên không ghi lại mỗi lần đồng bộ.
- **Lỗi ghi không làm hỏng đồng bộ**: bọc `runCatching`, bài vẫn được trả về.
- Trước đây đồng bộ chỉ ghi bài; **đây là lần đầu nền đồng bộ ghi vào bảng `feed`**.

## Kiểm chứng (đo thật)
- **DAO:** +7 test (`FeedDaoSourceUrlTest` 6 → 13): ghi khi khớp; không ghi khi cột đã đổi; khớp `NULL` bằng `IS`; `NULL` kỳ vọng không khớp giá trị khác; chỉ đổi đúng một feed; **không cột nào khác đổi** (so cả `Feed`); id lạ không ghi gì.
- **Đồng bộ (mock):** +9 test (`LocalRssSvBlueskySyncTest` 10 → 19): nguồn cũ được nâng cấp đúng một lần với đúng giá trị chờ; nguồn `sourceUrl = null` nâng cấp với kỳ vọng `null`; nguồn đã lưu DID không ghi lại; đồng bộ lỗi (`Failed`, `NotFound`, không bài) không ghi gì; ghi ném lỗi vẫn trả bài; nguồn không phải Bluesky không đụng DAO; tên, icon, bài và địa chỉ hiển thị không đổi.
- **Room thật:** +5 test (`LocalRssSvBlueskyUpgradeRoomTest`, `LocalRssSv` thật trên Room trong bộ nhớ): đồng bộ thật ghi DID và không đổi gì khác; sau nâng cấp `isFeedSourceExist(DID)` đúng còn `isFeedSourceExist(handle cũ)` sai; đồng bộ lần hai không ghi thêm; đồng bộ lỗi để nguyên; địa chỉ bị sửa chen vào không bị ghi đè.
- **Bước đỏ:** 3 test đỏ đúng lý do (hai test nâng cấp, và test hủy vì khối chưa tồn tại), 6 xanh ngay; đã đọc lý do trước khi sửa. Hàm DAO: 7 test đỏ vì thiếu hàm.
- **Mutation JVM: 12/12 bị bắt**, tệp khôi phục giống hệt (`cmp SAME`) sau mỗi batch. DAO 4/4 (`IS` → `=`, bỏ điều kiện, bỏ `WHERE id`, ghi sai cột); khối đồng bộ 8/8 (bỏ điều kiện "chưa phải DID", so sai cột, giá trị chờ lấy từ `url`, bỏ `runCatching`, nuốt `CancellationException`, ghi `handle` thay DID, ghi trước khi kiểm có bài, ghi cột `url`). Không mutant nào sống sót. Mẫu `r2` được viết bằng biểu thức luôn đúng thay vì xóa dòng để mutant vẫn biên dịch (bài học 02j).
- **11 lớp liên quan chạy riêng: 165 test, 0 lỗi, 0 bỏ qua** (`FeedDaoSourceUrlTest` 13, `AbstractRssRepositorySourceExistTest` 3, `LocalRssSvBlueskySyncTest` 19, `LocalRssSvBlueskyUpgradeRoomTest` 5, `LocalRssSvNostrSyncTest` 8, `BlueskyFeedMapperTest` 44, `FeedOptionViewModelTest` 8, `SubscribeDialogTypingTest` 5, `SubscribeViewModelBlueskyTest` 39, `SubscribeViewModelNostrTest` 13, `SubscribeViewModelSocialTest` 8). Thêm 21 test mới ở 02q (7 + 9 + 5).

## Full suite JVM: KHÔNG có số sạch (nói thẳng)
- Lần chạy full suite đầu của 02q bị **nghẽn hơn 40 phút** (bình thường 3 đến 4 phút) và đỏ 37 test; tôi dừng daemon và chạy lại **ba lần** với daemon sạch. Cả ba lần **đều đỏ cùng một mô hình**: bắt đầu đỏ ở khoảng **240 giây**, đúng test `pressingSubscribeAfterTheNostrPreview_savesANostrFeed` của `SubscribeDialogTypingTest`, sau đó nhiều lớp giao diện Compose khác lần lượt đỏ (`WebFeedPreviewWidgetTest`, `FlowActionsMenuWidgetTest`, `MindMapNodeLevelLabelTest`, `PodcastPlayerBarTest`, `PodcastEpisodeActionTest`, `ArticleDiffPageTest`, ...), **tất cả cùng loại lỗi `androidx.test.espresso.AppNotIdleException`**. Lần chạy cuối ngừng ở 24 test đỏ khi tôi dừng chủ động; **chưa có lần nào chạy hết bộ**.
- **Chạy riêng các lớp đã đỏ thì xanh**: 5 lớp (`SubscribeDialogTypingTest`, `WebFeedPreviewWidgetTest`, `FlowActionsMenuWidgetTest`, `MindMapNodeLevelLabelTest`, `PodcastPlayerBarTest`), **34 test, 0 lỗi, trong 20 giây**.
- **Bisect: lỗi đã có sẵn trước 02q.** Tôi chạy full suite trên commit **02p (`3c9b7d47`)** (không có mã 02q): `pressingSubscribeAfterTheNostrPreview_savesANostrFeed` đỏ ở **238 giây**, cùng `AppNotIdleException`, cùng mốc. Tôi dừng bisect sau khi thấy lỗi đầu tiên nên **chỉ chứng minh "đã đỏ ở 02p"**, chưa đếm tổng số đỏ ở 02p. Các lớp đỏ không lớp nào chạm Bluesky, đồng bộ hay DAO.
- **Mâu thuẫn chưa giải thích:** các báo cáo 02p (và 02o) ghi full suite **xanh** (3.776 / 3.767 test, 0 lỗi) ở **cùng commit**. Con số đó đúng cho lần chạy lúc đó, nhưng **hôm nay cùng commit không tái lập được**. Nguyên nhân tôi **chưa biết**.
- **Giả thuyết (chưa chứng minh):** (a) máy cạn tài nguyên: lúc đó 15 GB bộ nhớ đã dùng, trống 80 MB, 6 đến 7 GB bị nén, Android Studio và Chrome chạy cùng; (b) trạng thái chung giữa các test Compose khi chạy cả bộ. Chỗ yếu của (a): lần chạy sạch chỉ có một daemon vẫn đỏ đúng chỗ đó. **Không loại trừ cả hai.**
- **Liên quan 02m:** `SubscribeDialogTypingTest` gõ chữ vào đúng hộp thoại "Thêm nguồn" mà FRONT-02m đang điều tra, và lỗi cũng là Compose không bao giờ "rảnh". Có thể **cùng gốc**, chỉ là phỏng đoán.

## Rủi ro bảo mật cần biết (đã được owner chấp nhận 2026-11-17)
Sau khi ghi DID vào `sourceUrl`, đồng bộ đọc theo DID (trước đây theo handle). `BlueskyApiClient` kiểm dạng DID nhưng **không kiểm DID có thuộc về handle đã gõ**, nên nếu máy chủ trả lời bị giả mạo và trả DID của người khác, nguồn bị gắn DID đó và đồng bộ bài của người khác từ lần sau. **Đính chính của tôi:** khi hỏi owner, tôi từng nói rủi ro này "02o chưa có"; **sai**. FRONT-02o đã ghi `sourceUrl = <DID lấy từ câu trả lời API>` cho mọi nguồn **đăng ký mới**, nên rủi ro đã tồn tại từ 02o; 02q chỉ **mở rộng sang nguồn cũ**. Giảm: bài chỉ được giữ khi đúng DID đó (`toArticles(profileDid)` lọc), HTTPS kiểm chứng chứng chỉ, máy chủ là của Bluesky.

## Những gì CHƯA làm hoặc CHƯA chặn (nói thẳng)
- **Nguồn đã đổi handle trước khi được nâng cấp** thì không nâng cấp được (đồng bộ đã lỗi `NotFound`, không có DID); vẫn trùng khi đăng ký lại. Chỉ **phòng ngừa** cho nguồn còn đồng bộ được.
- Nguồn **không bao giờ đồng bộ thành công** (tắt, lỗi mạng kéo dài) không được nâng cấp.
- OPML, địa chỉ dạng khác, cặp trùng có sẵn: không xử lý. Menu sửa địa chỉ nguồn (02p) ghi cả hai cột nên **nguồn đã nâng cấp mà người dùng sửa địa chỉ sẽ mất DID** (như đã nêu ở 02p).
- **Chưa kiểm trên máy** (chỉ JVM, đúng phạm vi); **chưa kiểm hai đồng bộ song song** của cùng nguồn trên máy thật (đã lập luận là vô hại và điều kiện `IS` chặn ghi lần hai).
- **Test hủy chỉ ghim một phần:** `queryFeedArticles` là `protected` và `LocalRssSv` là lớp `final`, nên test không gọi trực tiếp để kiểm "ném lại `CancellationException`". `syncSingleFeed` vốn biến mọi `Throwable` thành đồng bộ thất bại (có từ trước 02q). Mutant "nuốt `CancellationException`" vẫn bị bắt, nhưng bằng cơ chế: bản gốc ném ra rồi `syncSingleFeed` trả `null`, còn mutant nuốt nên đồng bộ vẫn thành công và `assertNull` đỏ; **không** phải test chứng minh hủy lan ra ngoài.
- **Full suite JVM chưa sạch** (xem trên): đây là nợ cần xử lý, không phải kết quả "0 lỗi".

## Điều tra full suite đỏ (2026-11-17, chỉ đo, không đổi mã)
Chỉ chạy JVM, không chạm thiết bị, **không sửa tệp nào trong kho** (công cụ đo nằm ngoài kho; hai test chẩn đoán tạm đã xóa). **Kết luận: chưa tìm ra gốc của `AppNotIdleException`; tìm ra và thu hẹp một lỗi thứ hai, riêng biệt.**

**Đã loại (có số đo):**
- **Hết bộ nhớ heap:** mẫu heap worker mỗi 15 giây trong một lần full suite đỏ: ổn định ~300 MB trên trần 512 MB, chỉ 1 lần Full GC. Worker chạy 169% CPU lúc treo (bận, không đứng).
- **`setMain` không `resetMain`:** cả 61 lớp dùng `setMain` đều có `resetMain`.
- **Cấu hình `@Config(application = Application::class)`:** 43 lớp Compose cùng cấu hình vẫn xanh; 4 lớp đỏ không có gì riêng ở cấu hình.
- **JIT/C1:** chạy với `-XX:TieredStopAtLevel=1` lỗi vẫn xảy ra; còn sinh thêm cảnh báo "CodeCache is full" do chính thiết lập đó, nên không dùng để suy luận.

**Đã đo được:**
- 60 lớp Compose chạy **riêng**: 407 test, 0 lỗi, 24 giây.
- 210 lớp không-Compose (nửa A) cộng 60 lớp Compose: 2.104 test, **xanh**, 37 giây. Nửa B (211 lớp) cộng Compose: **đỏ** (lần mặc định thì JVM tự sập vì lỗi trình biên dịch C2 của JBR 17.0.14, exit 134, SIGSEGV; lần C1-only thì `AppNotIdleException`). Nửa B chia tiếp: B1a xanh, B2a đỏ, rồi C1 đỏ kiểu treo và C2 đỏ kiểu `ReelsViewModelTest`. **Chưa bisect AppNotIdle tới một lớp cụ thể** (mỗi lượt đỏ treo 5 đến 7 phút).
- Một sai sót đo của chính tôi, đã sửa: một đợt 6 lượt chẩn đoán bị Gradle lấy kết quả **từ cache** (`FROM-CACHE`, 5 trong 6 lượt không chạy thật). Mọi đợt đo trước đó (bisect, ddmin, pair) đều có `cache=0`; đợt chẩn đoán cuối đã chạy lại với `--no-build-cache`, 8 lượt thật.

**Lỗi thứ hai (tách biệt): `ReelsViewModelTest` đỏ khi chạy sau `SelectionActionChipsTest`.**
- `ReelsViewModelTest` chạy riêng: xanh. Chạy chung với 60 lớp Compose: đỏ 6/8 test, lặp lại (2 lần liên tiếp). Chia đôi tự động ra **cặp nhỏ nhất: `SelectionActionChipsTest` + `ReelsViewModelTest`**.
- **Chập chờn:** chạy cặp thật: đỏ, đỏ, xanh (3 lần); test chẩn đoán đầu (có thể có lượt lấy cache, nên số này chỉ để tham khảo): đỏ 7/8; test chẩn đoán cuối, chắc chắn không cache: **đỏ 1/8**.
- **Dấu vết:** test Reels khóa cứng `ACCOUNT_ID = 1`, nhưng ở mọi lượt đỏ `context.currentAccountId` đọc ra **2, 3 hoặc 4**, bài rỗng, `isLoading` vẫn true. Lượt xanh luôn đọc ra 1. Ở lượt đỏ duy nhất của đợt cuối, tệp DataStore của lớp chip có **24 byte** (các lượt xanh: **50 byte**), tức bị ghi dở hoặc ghi khác.
- `Application` thật là `android.app.Application` (không phải `RApp`), nên giả thuyết "`RApp` khởi động ở nền" **sai**. Mã của lớp chip (`QuoteShareChip.kt`, `QuoteTextToolbar.kt`) **không chạm DataStore** (đã đọc).

**Chưa biết:** **ai ghi** `currentAccountId` ra 2, 3, 4; vì sao tệp có 24 byte; và liệu lỗi này có chung gốc với `AppNotIdleException` hay không (không có bằng chứng cho hay chống). Lỗi Reels là **lỗi phụ thuộc thứ tự và chập chờn của chính bộ test**, không phải lỗi sản phẩm; lỗi sản phẩm chưa được chứng minh ở đâu.

**Việc còn lại (nợ mở):** (1) bisect `AppNotIdleException` tới lớp cụ thể (khoảng 30 đến 40 phút, có thể cần một tổ hợp lớp); (2) tìm ai ghi `currentAccountId` (đọc mã chỉ cho biết ai **có thể** ghi, không biết ai **đã** ghi; cần đo); (3) sau khi hiểu gốc, mới quyết cách sửa. Cho tới lúc đó, **không có số full suite sạch sau 02p**.
