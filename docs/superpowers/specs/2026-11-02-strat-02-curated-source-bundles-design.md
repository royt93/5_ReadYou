# STRAT-02 — Bộ sưu tập nguồn tin tuyển chọn (Curated Source Bundles): Design

**Ngày:** 2026-11-02 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Người mới dùng RSS thường không biết tìm link RSS ở đâu, và kho của owner chỉ có một feed (BBC News) nên ECHO-02/03 không hiện. Thêm các **bộ sưu tập theo chủ đề** vào trang Add Sources hiện có, với nút "Đăng ký cả bộ" tạo một nhóm mới và thêm các feed đọc được.

## Vì sao khác todo gốc
Todo `STRAT-02_curated-feed-marketplace.md` mô tả một **tab "Khám Phá"** riêng, xem trước bài gần nhất của từng nguồn và bộ sưu tập "cộng đồng". Thực tế:
- App đã có trang **Add Sources** (`AddSourcesPage`, `AddSourcesViewModel`, `assets/rss_sources.json`, 552 nguồn theo 12 ngôn ngữ) và luồng thêm một feed (`rssHelper.searchFeed` + `rssService.get().subscribe`). Thay vì thêm tab song song, mở rộng trang này.
- "Cộng đồng" cần server riêng của chúng ta; không làm. Bộ sưu tập là **dữ liệu tĩnh đóng gói trong app**.
- Không xem trước bài (owner chọn bản nhỏ): bài đầu tiên của mỗi feed về kho ngay lúc đăng ký vì `subscribe` đã nạp bài.

## Quyết định của owner (AskUserQuestion)
1. Mở rộng **Add Sources** bằng tầng "Bộ sưu tập", không làm tab mới.
2. Đăng ký cả bộ: **thêm từng feed hợp lệ, bỏ feed lỗi hoặc trùng, báo kết quả**.
3. Dữ liệu bộ: **chỉ dùng feed đã kiểm chứng còn sống**, cộng nguồn mới đã kiểm chứng.
4. **Không xem trước bài**, chỉ hiện tên nguồn và kết quả đăng ký.

## Sự thật đã kiểm (2026-11-02, fetch thật từ máy dev)
- `rss_sources.json` có 552 URL; chỉ **263 đọc được** như RSS/Atom, 44 trả HTML không phải feed, 245 lỗi (403, 404, 429, timeout, DNS). Có 4 URL trùng và 25 URL `http://`. Đây là một lần đo, không chứng minh link ổn định lâu dài.
- Nguồn mới đã kiểm là feed hợp lệ: OpenAI News `https://openai.com/news/rss.xml`, Muzli `https://medium.muz.li/feed`, Hugging Face Blog `https://huggingface.co/blog/feed.xml`.
- Không đưa vào: DeepMind (trả 200 nhưng không phải feed), Anthropic (404), VentureBeat (429), FT, Forbes, CafeF (404), Nhịp Sống Kinh Tế, và các báo Việt Nam Thanh Niên, Dân Trí, Vietnamplus (trả trang HTML), VietnamNet, VOV (404), Zing (403). Tôi không đoán URL thay thế.

## Dữ liệu 5 bộ (chỉ feed đã kiểm hôm nay)
| Bộ | Feed |
|---|---|
| Công nghệ & AI | TechCrunch, The Verge, Ars Technica, Wired, MIT Technology Review, IEEE Spectrum, Hacker News, OpenAI News |
| Kinh tế & Đầu tư | Bloomberg Markets, WSJ, The Economist, VnEconomy, Báo Đầu tư |
| Thời sự Việt Nam | VnExpress, Tuổi Trẻ |
| Thiết kế & Sáng tạo | Smashing Magazine, A List Apart, CSS-Tricks, Awwwards, Behance, Designboom, Fast Company, Muzli |
| Lập trình | Dev.to, GitHub Blog, Stack Overflow Blog, freeCodeCamp, Hacker Noon, Android Developers Blog, Viblo, TopDev, Hugging Face Blog |

**Điểm cần owner xem:** hai URL trong bộ Kinh tế không đúng chủ đề tài chính: WSJ trỏ tới `RSSWorldNews.xml` (tin thế giới) và The Economist trỏ tới `the-world-this-week/rss.xml` (tổng hợp thế giới). Chúng đọc được nên đi theo catalog, nhưng "Kinh tế & Đầu tư" chỉ có Bloomberg Markets, VnEconomy và Báo Đầu tư là sát chủ đề. Nếu không muốn, bỏ hai URL này khỏi bộ.

## Thiết kế
### Dữ liệu
- `app/src/main/assets/curated_bundles.json`: mảng bộ, mỗi bộ có `id`, `titleEn`, `titleVi`, `verifiedOn` (`"2026-11-02"`) và `sources: [{name, url}]`.
- Một test đọc JSON và khẳng định: id không trùng, URL không trùng giữa các bộ trong cùng bộ, chỉ `https`, bộ không rỗng, `verifiedOn` đúng định dạng ngày.

### Thành phần
- `domain/curated/CuratedBundle` (+ `CuratedSource`) và `CuratedBundleLoader` (đọc asset bằng Gson, cùng cách `RssSourceRepository`).
- `domain/curated/BundleSubscribePlanner` (thuần): nhận danh sách nguồn của bộ và tập URL đã đăng ký, trả `toAdd` và `alreadyHave`; chuẩn hoá URL khi so (bỏ `/` cuối, không phân biệt hoa thường).
- `domain/curated/BundleSubscribeResult(added, skippedExisting, failed)` và `BundleSubscriber`: tạo nhóm bằng `rssService.get().addGroup(title)`, rồi với từng nguồn `toAdd` gọi `rssHelper.searchFeed` + `subscribe` (đã có sẵn), tối đa `MAX_PARALLEL_FEEDS` feed một lúc, bắt lỗi từng feed. Nếu `added == 0` thì xoá nhóm vừa tạo để không để nhóm rỗng.
- `AddSourcesViewModel`: trạng thái `bundles` và tiến trình đăng ký; `AddSourcesPage`: phần "Bộ sưu tập" phía trên danh sách ngôn ngữ, mỗi bộ có tên, số nguồn, ngày kiểm chứng, danh sách tên nguồn và nút "Đăng ký cả bộ".
- Chỉ cho tài khoản Local (các tài khoản đồng bộ ngoài sẽ ẩn nút vì `subscribe` của họ gọi API riêng).

### Luồng và lỗi
- Bấm "Đăng ký cả bộ" → vô hiệu nút, hiện tiến trình "x/y", cuối cùng hiện một dòng kết quả: "Đã thêm N · M đã có sẵn · K lỗi". Mỗi feed lỗi không làm hỏng cả bộ.
- Chạy trên IO; tiến trình không giữ `Context` hay `Activity`; huỷ khi ViewModel bị dọn.
- Không mạng: mọi feed lỗi, `added == 0`, báo "K lỗi", không tạo nhóm.

### Chuỗi giao diện
Khoảng 10 chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh), kiểm bằng `StringResourceParityTest`. Tên các bộ lấy từ JSON (EN/VI), không đi qua `strings.xml`.

## Giới hạn nói thẳng
1. Danh sách đúng ở ngày **2026-11-02**; link có thể chết sau này. Lúc đăng ký feed chết bị bỏ qua và được đếm vào "lỗi".
2. Bộ Thời sự Việt Nam chỉ có **2 nguồn** (todo kỳ vọng 4); Thanh Niên và Dân Trí không có RSS đọc được ở URL catalog.
3. Đăng ký cả bộ cần mạng và có thể chậm; không xem trước nội dung nên người dùng chỉ biết nguồn qua tên.
4. Tiêu đề feed lấy từ chính feed, không dùng tên trong JSON, nên tên trong nhóm có thể khác tên đã hiện.
5. Catalog 552 link vẫn còn nhiều link chết; **không** dọn trong task này.

## Kiểm thử
- Unit: `CuratedBundleLoaderTest` (đọc JSON thật trong assets, kiểm bất biến ở trên), `BundleSubscribePlannerTest` (trùng URL khác hoa thường và dấu `/`, bộ rỗng, toàn trùng), `BundleSubscriberTest` (MockK: thêm một phần, tất cả lỗi → xoá nhóm, tất cả trùng, huỷ giữa chừng).
- `AddSourcesViewModelTest` bộ sưu tập với `defaultDispatcher`/`ioDispatcher` tiêm; test Compose cho phần Bộ sưu tập (hiện bộ, bấm nút gọi callback, kết quả hiển thị, nút ẩn ở tài khoản không Local).
- Instrumentation trên Pixel `2B051FDH3006MU` bằng `am instrument`; smoke Pixel: backup Room (3 file) và DataStore, đăng ký một bộ thật (cần mạng) rồi kiểm tra nhóm và feed trong DB, xác nhận ECHO-02/03 hiện sau khi có nguồn thứ hai, đóng quảng cáo toàn màn hình theo quy tắc, rồi khôi phục và `cmp`.

## Ngoài phạm vi
Xem trước bài, bộ sưu tập cộng đồng hoặc cần server, chia sẻ bộ, tab "Khám Phá" riêng, dọn catalog 552 link, đổi Room, quảng cáo, IAP, AI.
