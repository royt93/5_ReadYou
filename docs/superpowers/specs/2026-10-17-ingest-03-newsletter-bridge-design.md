# [INGEST-03] Bản Tin Email & Newsletter Ingestion Bridge — Architecture & Design Spec

- **Feature:** Ingestion / Content Source
- **Epic:** 07. Universal Ingestion & Sync
- **Date:** 2026-10-17
- **Status:** Draft / Review

---

## 1. Mục tiêu & Bối cảnh

### 1.1. Bối cảnh
Người dùng ngày càng theo dõi nhiều chuyên gia trên các nền tảng bản tin cá nhân (Substack, Ghost, Beehiiv, Medium). Tuy nhiên:
1. Khi sao chép liên kết từ trình duyệt (ví dụ: `https://danluu.substack.com` hoặc `https://medium.com/@swyx`), liên kết đó là trang web profile/blog HTML, không phải URL trực tiếp của RSS XML. Nếu dán trực tiếp vào app, trình phân tích cú pháp RSS hiện tại (`SyndFeedInput`) sẽ thất bại với lỗi XML parsing.
2. Nội dung các bản tin email này khi được phân phối qua RSS hoặc fetch web chứa rất nhiều rác đặc thù của email marketing:
   - Form nhập email đăng ký ("Type your email...", "Subscribe now")
   - Nút chia sẻ, app CTA ("Get the Substack app", "Open in app", "Restack")
   - Chữ ký email & footer ("You're reading this because...", "Unsubscribe", "Manage preferences", "Powered by Substack/Ghost")
   - Tracking pixels 1x1 ẩn giấu gây theo dõi hành vi đọc.

### 1.2. Mục tiêu
- **Tự động chuyển đổi / dò tìm (Resolver):** Nhận diện liên kết profile/trang chủ từ Substack, Medium, Ghost, Beehiiv và chuyển thành RSS feed hợp lệ mà người dùng không cần phải biết cú pháp `/feed` hay `/rss/`. Hỗ trợ fallback Auto-Discovery qua thẻ `<link rel="alternate">`.
- **Làm sạch tại tầng Ingestion (Sanitizer):** Tự động lược bỏ triệt để các form đăng ký, nút chia sẻ, footer unsubscribe, chữ ký email và tracking pixel trước khi lưu bài viết vào Room Database.
- Đảm bảo Reader View, TTS, AI Summary và Mind Map nhận nội dung hoàn toàn sạch, nâng cao trải nghiệm đọc tập trung và tiết kiệm dung lượng SQLite CursorWindow.

---

## 2. Kiến trúc & Thiết kế Chi Tiết

### 2.1. Phân tầng & Vị trí Mã nguồn
Package mới: `com.mckimquyen.reader.infrastructure.newsletter`

```
infrastructure/newsletter/
├── NewsletterPlatform.kt         # Enum nhận diện nền tảng (SUBSTACK, MEDIUM, GHOST, BEEHIIV, GENERIC)
├── NewsletterUrlResolver.kt      # Dò tìm và chuẩn hóa URL blog/profile thành URL RSS XML
└── NewsletterSanitizer.kt        # Bộ lọc DOM HTML (Jsoup) loại bỏ form, rác email và tracking
```

### 2.2. Nhận diện nền tảng & Dò tìm URL (`NewsletterUrlResolver`)
Các quy tắc nhận diện và suy diễn URL feed:
- **Substack:**
  - Pattern: `https?://([a-zA-Z0-9_-]+)\.substack\.com(/.*)?` -> Feed: `https://$1.substack.com/feed`
  - Pattern: `https?://substack\.com/@([a-zA-Z0-9_-]+)(/.*)?` -> Gọi HTTP HEAD/GET để lấy redirect URL sang subdomain hoặc trang chủ để trích xuất feed.
- **Medium:**
  - Pattern: `https?://medium\.com/@([a-zA-Z0-9_.-]+)(/.*)?` -> Feed: `https://medium.com/feed/@$1`
  - Pattern: `https?://([a-zA-Z0-9_-]+)\.medium\.com(/.*)?` -> Feed: `https://$1.medium.com/feed`
  - Pattern: `https?://medium\.com/([a-zA-Z0-9_-]+)(/.*)?` (publication) -> Feed: `https://medium.com/feed/$1`
- **Ghost:**
  - Pattern: `https?://([a-zA-Z0-9_-]+)\.ghost\.io(/.*)?` -> Feed: `https://$1.ghost.io/rss/`
  - Custom domain: kiểm tra endpoint `<base_url>/rss/`
- **Beehiiv:**
  - Pattern: `https?://([a-zA-Z0-9_-]+)\.beehiiv\.com(/.*)?` -> Feed: `https://$1.beehiiv.com/feed`
- **Generic Fallback (HTML Auto-Discovery):**
  - Tải HTML phần đầu trang (tối đa 256 KB) qua OkHttpClient an toàn.
  - Sử dụng Jsoup trích xuất `link[rel="alternate"][type="application/rss+xml"]` hoặc `link[rel="alternate"][type="application/atom+xml"]`.
  - Resolve href tương đối thành URL tuyệt đối.

### 2.3. Bộ làm sạch HTML (`NewsletterSanitizer`)
Áp dụng Jsoup làm sạch nội dung bài viết (`description` và `fullContent`):
1. **Tracking Pixels & Beacon:**
   - Xóa `img[width="1"]`, `img[height="1"]`, `img[width="0"]`, `img[height="0"]`.
   - Xóa `img[src*="open.substack.com"]`, `img[src*="email.ghost.org"]`, `img[src*="beehiiv.com/track"]`, `img[src*="pixel"]`, `img[src*="track"]`.
2. **Form & Subscribe CTA Widgets:**
   - Substack: `.subscription-widget-wrap`, `.subscribe-widget`, `div[class*="subscribe-form"]`, `.side-comment-widget`, `.button-wrapper:has(a[href*="subscribe"])`.
   - Ghost: `.gh-portal-close-button`, `form[action*="members/api"]`, `.gh-footer-signup`, `.gh-post-upgrade-cta`.
   - Beehiiv: `div[class*="subscribe_form"]`, `form[action*="beehiiv.com"]`.
   - Button/Link CTA: Các khối chỉ chứa liên kết có text hoặc href chứa "Get the app", "Open in app", "Restack", "Share this post", "Subscribe".
3. **Email Footers & Unsubscribe:**
   - Xóa các thẻ liên kết `a[href*="unsubscribe"]`, `a[href*="manage-subscription"]`, `a[href*="email-preferences"]`, `a[href*="subscribed"]`.
   - Quét các đoạn văn `<p>`, `<div>`, `<section>` ở cuối bài viết chứa các chuỗi nhận diện:
     - "You're reading this because"
     - "Unsubscribe"
     - "Update your preferences"
     - "Manage your subscription"
     - "Powered by Substack"
     - "View this post in your browser"
4. **An toàn bảo toàn nội dung:**
   - Không can thiệp vào các đoạn văn bản chính, code block, blockquote, bullet points, tiêu đề và hình ảnh bài viết thực tế (> 2px).

### 2.4. Điểm tích hợp vào hệ thống
1. **`SubscribeViewModel.search()`**:
   - Nếu `rssHelper.searchFeed(link)` trực tiếp ném lỗi ngoại lệ:
   - Gọi `newsletterUrlResolver.resolveToRssUrl(link)`.
   - Nếu tìm thấy URL RSS mới: thử tìm lại bằng `rssHelper.searchFeed(resolvedUrl)`.
   - Nếu không: tiếp tục fallback sang `webFeedScraper.discover(link)` (Web-to-RSS) như bình thường.
2. **`RssHelper.article()`**:
   - Khi parse các entry RSS, chạy `cleanArticleHtml` cho trường `description` và `content`.
3. **`RssHelper.parseFullContent()`**:
   - Sau khi parse bằng Readability4J, chạy `cleanArticleHtml` trước khi kiểm tra dung lượng và trả về.

---

## 3. Tiêu chí Kiểm Thử & Đảm Bảo Chất Lượng

- **Unit Tests:**
  - `NewsletterUrlResolverTest`: Kiểm tra chuyển đổi chính xác các dạng link Substack (subdomain, @profile), Medium (@user, custom subdomains, publication), Ghost (/rss/), Beehiiv (/feed), và Generic Auto-discovery HTML.
  - `NewsletterSanitizerTest`: Kiểm tra bóc tách và xóa sạch 100% các thành phần rác (form email, unsubscribe, tracking pixel, share button) trên các mẫu HTML thực tế của Substack, Ghost, Beehiiv và Medium. Đảm bảo nội dung bài viết chính không bị mất mát hay suy hao.
- **Integration & UI Smoke Test:**
  - Kiểm thử trên thiết bị `Pixel_10_Pro` (`emulator-5554`).
  - Thêm một feed Substack từ link web (`https://danluu.substack.com` hoặc tương đương) trong dialog Subscribe.
  - Verify feed được nhận diện thành công, tải bài viết vào Room DB, mở bài đọc kiểm tra hiển thị sạch đẹp không còn nút Subscribe/Unsubscribe/Pixel.
- **Tiêu chuẩn chất lượng:**
  - Không magic numbers, đầy đủ constant mô tả rõ mục đích.
  - Null-safe 100%, an toàn thread qua `Dispatchers.IO`.
  - Không phá vỡ bất kỳ feed RSS thông thường nào (813 unit test hiện tại giữ nguyên 100% pass).
