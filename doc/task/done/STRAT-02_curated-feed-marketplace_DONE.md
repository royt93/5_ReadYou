# STRAT-02 — Bộ sưu tập nguồn tin tuyển chọn (Curated Source Bundles) — DONE

**Hoàn tất:** 2026-11-02 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Todo gốc mô tả một tab "Khám Phá" riêng, xem trước bài gần nhất của từng nguồn và bộ sưu tập "cộng đồng". Bản làm theo các quyết định của owner:
- **Mở rộng trang Add Sources hiện có** (không thêm tab). Phần "Bộ sưu tập tuyển chọn" nằm đầu trang, mỗi bộ hiện tên (EN hoặc VI theo ngôn ngữ UI), số nguồn, ngày kiểm chứng, và nút **"Đăng ký cả bộ"**. Chạm vào tiêu đề bộ để xem tên các nguồn.
- Đăng ký cả bộ: tạo **một nhóm mới** cùng tên bộ, thêm từng feed đọc được, bỏ feed lỗi và feed đã có, rồi báo "Đã thêm N · đã có M · lỗi K". Nếu không thêm được nguồn nào thì xoá nhóm vừa tạo.
- Dữ liệu là **file tĩnh đóng gói trong app** (`assets/curated_bundles.json`, 5 bộ, 32 nguồn). Không server của chúng ta, không "cộng đồng".
- **Không** xem trước bài (owner chọn bản nhỏ). Không AI, không API key, không quảng cáo/IAP, không đổi Room (vẫn v20).
- Chỉ tài khoản **Local** được đăng ký cả bộ; tài khoản đồng bộ (Fever, Google Reader…) vẫn xem được bộ nhưng không có nút, kèm một dòng giải thích.

## Dữ liệu và độ tin cậy
Tôi fetch thật các URL ngày **2026-11-02** (một lần đo, không bảo đảm link còn sống sau này):
- `rss_sources.json` hiện có 552 URL nhưng chỉ **263 đọc được** như RSS/Atom, 44 trả HTML không phải feed, 245 lỗi (403, 404, 429, timeout, DNS); 4 URL trùng, 25 URL `http://`. **Không dọn catalog này trong task này.**
- 5 bộ chỉ chứa URL đã đọc được hôm nay: Công nghệ & AI (8), Kinh tế & Đầu tư (5), Thời sự Việt Nam (**2**), Thiết kế & Sáng tạo (8), Lập trình & Công cụ (9). Nguồn mới thêm đã kiểm: OpenAI News, Muzli, Hugging Face Blog.
- Bỏ vì không đọc được: DeepMind (200 nhưng không phải feed), Anthropic (404), VentureBeat (429), FT, Forbes, CafeF (404), Nhịp Sống Kinh Tế; Thanh Niên, Dân Trí, Vietnamplus (trả trang HTML), VietnamNet, VOV (404), Zing (403). Tôi không đoán URL thay thế.
- **Bộ Thời sự Việt Nam chỉ có 2 nguồn** (todo kỳ vọng 4).
- Trong bộ Kinh tế, WSJ trỏ tới feed *World News* và The Economist tới *The World This Week*: đọc được nên giữ theo spec nhưng **không sát chủ đề tài chính**.

## Cấu trúc mã
- `domain/curated/`: `CuratedBundle`/`CuratedSource`, `CuratedBundleLoader` (kiểm id, tên, ngày `yyyy-MM-dd`, URL https có host, không trùng), `BundleSubscribePlanner` (chuẩn hoá URL: host/scheme không phân biệt hoa thường, bỏ một dấu `/` cuối), `BundleSubscriber` (`MAX_PARALLEL_FEEDS = 3`, lỗi từng feed được cô lập, xoá nhóm rỗng kể cả khi bị huỷ), `RssBundleFeedGateway`, `CuratedBundleService`.
- `AddSourcesViewModel`: giữ **một** `@Inject` constructor, thêm `CuratedBundleService`; ngôn ngữ UI là thuộc tính nội bộ (một tham số hàm trong constructor làm Hilt báo thiếu binding).
- `ui/page/home/addsources/CuratedBundlesSection` và `AddSourcesList` (tách khỏi Hilt để test được).
- 10 chuỗi `curated_bundle*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh).

## Phát hiện khi làm
1. **Lỗi chỉ thấy trên máy thật:** bộ sưu tập tải xong *sau* khi trang đã mở, lúc đó phần tử đầu danh sách có chiều cao 0 nên `LazyColumn` giữ phần tử nhìn thấy đầu tiên là thẻ quốc gia, khiến bộ sưu tập nằm **phía trên khung nhìn**; người dùng mở Add Sources chỉ thấy danh sách quốc gia. Log chẩn đoán (đã gỡ) cho thấy dữ liệu đã tới UI (`bundles=5`); kéo xuống thì thấy. Sửa: đặt `key` cho các phần tử và `scrollToItem(0)` khi bộ sưu tập xuất hiện (`533d35b2`). **Đã xác nhận trên Tecno**: mở Add Sources thấy ngay "Curated bundles".
2. **Test hồi quy cho lỗi này không bắt được lỗi.** Tôi viết `AddSourcesListTest` mô phỏng "bộ sưu tập đến muộn" rồi chạy hai lần mutation (tắt `scrollToItem`; và trả danh sách về dạng ban đầu không key), cả hai lần test vẫn xanh nên nó không chứng minh gì. Theo lựa chọn của owner tôi **xoá test đó và không tuyên bố có test hồi quy** cho lỗi này. Lỗi chỉ được bắt bằng máy thật; có thể quay lại mà không test nào báo.
3. Chuỗi "N sources" bị lint `PluralsCandidate`; đổi sang dạng không có số nhiều ("Sources: N" / "Nguồn: N") ở 38 locale.
4. Hilt: thêm tham số `() -> String` vào constructor làm `[Dagger/MissingBinding]`; chuyển thành thuộc tính nội bộ.
5. Plan có một chỗ mâu thuẫn (loader bắt buộc đúng 5 bộ nhưng test lại đưa JSON một bộ); owner chọn loader nhận số bộ hợp lệ bất kỳ, test asset thật khẳng định đúng 5.

## Kiểm chứng
- **Unit:** 1.891 test, 0 lỗi, 0 skip (nền trước task 1.836). Thêm: `CuratedBundleLoaderTest` 11, `BundleSubscribePlannerTest` 7, `BundleSubscriberTest` 9, `RssBundleFeedGatewayTest` 5, `CuratedBundleServiceTest` 4, `AddSourcesViewModelBundleTest` 9, `CuratedBundlesSectionTest` 10. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 0 fatal, 2.463 cảnh báo (không đổi). **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Tecno `115333744A005844`:** `CuratedBundlesDeviceTest` 1/1 (đọc asset đóng trong APK thật, 5 bộ, bấm nút trả đúng id) bằng `am instrument`, không dùng `connectedDevDebugAndroidTest`.
- **Smoke thật trên Tecno** (TECNO KJ7, Android 14, dùng mạng thật): mở Add Sources thấy 5 bộ. Bấm "Đăng ký cả bộ" ở **Vietnam News**: kết quả "Added 2 · already had 0 · failed 0". Đọc DB từ máy bằng cả 3 file Room: có nhóm mới "Vietnam News" chứa 2 feed thật (VnExpress 54 bài, Tuổi Trẻ 50 bài), 2 dòng `added_rss_source`, tổng bài 152 → 291, `integrity_check` ok. Bấm lại lần hai: "Added 0 · already had 2 · failed 0" kèm dòng "No source could be added, so no group was created"; DB vẫn chỉ có đúng một nhóm Vietnam News, 3 feed, không nhóm trùng.

## Giới hạn đã biết
1. Danh sách đúng ở ngày **2026-11-02**; link có thể chết. Lúc đăng ký, feed chết bị bỏ qua và đếm vào "lỗi".
2. Chưa quan sát trên máy: nhánh **lỗi một phần** (một số feed lỗi, một số được) — chỉ có unit test; nhánh **tất cả lỗi** (không mạng) — chỉ có unit test; tài khoản Fever/Google Reader (không có tài khoản đó trên máy) — chỉ có unit/Compose test; các bộ **Công nghệ & AI, Kinh tế, Thiết kế, Lập trình** chưa được đăng ký thật (tôi chỉ đăng ký bộ nhỏ nhất để hạn chế ghi dữ liệu và request); tiến trình "x/y" lúc đang đăng ký (xong quá nhanh để chụp); cách hiển thị tiếng Việt (máy dùng tiếng Anh); xem tên nguồn khi mở rộng bộ.
3. Đăng ký cả bộ cần mạng và có thể chậm với bộ 8–9 nguồn; chưa đo.
4. Tiêu đề feed lấy từ chính feed nên tên trong nhóm có thể khác tên trong bộ.
5. **ECHO-02/03 chưa được kiểm lại trên Tecno** sau khi có hai nguồn (tôi dừng ở xác nhận DB).
6. **Quảng cáo:** `AdActivity` toàn màn hình xuất hiện **nhiều lần** trên Tecno (mở app, sau mỗi lần cài lại APK). Nút có nhãn "Tiếp tục đến ứng dụng" **không hiện** trong dump nào, nên tôi không chạm creative mà hỏi owner; owner đã đóng giúp mỗi lần và dặn tôi chủ động đóng sau này. Một lần Tecno tắt màn hình và khoá (`Dozing`, keyguard); tôi không mở khoá được nên dừng và chờ owner mở.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu cả Room (`Reader`, `Reader-shm`, `Reader-wal` — wal rỗng) và DataStore (194 byte, không rỗng; `integrity_check` ok trên bản sao; 152 bài, 1 feed BBC News, 2 nhóm, 0 nguồn theo dõi). Sau smoke khôi phục cả bốn file bằng `run-as sh -c 'cat > ...'`; `cmp` xác nhận giống hệt, `integrity_check` ok, lại 152 bài, 1 feed, 2 nhóm, 0 nguồn theo dõi, gói test đã gỡ. Không xoá DataStore. Pixel `2B051FDH3006MU` không bị chạm trong task này.

## Commits trên nhánh `dev`
- `f2cb6211` docs: thiết kế STRAT-02 · `9276dfe1` docs: kế hoạch STRAT-02
- `fa3c6011` feat: dữ liệu 5 bộ và loader
- `87e64526` feat: planner chuẩn hoá URL
- `0405152b` feat: `BundleSubscriber`
- `4e2094b5` feat: cổng repository thật
- `9142b75e` feat: `CuratedBundleService`
- `f32510f6` feat: state và đăng ký bộ trong `AddSourcesViewModel`
- `556bdc1a` feat: phần Bộ sưu tập trong Add Sources
- `93a90d1e` feat: chuỗi không dùng số nhiều và test thiết bị
- `533d35b2` fix: giữ bộ sưu tập trong khung nhìn khi tải muộn
- `docs(strat-02)`: tài liệu hoàn tất (commit ngay sau các commit trên)
