# [INGEST-03] Bản Tin Email & Newsletter Ingestion Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Giải mã tự động các liên kết blog/newsletter cá nhân (Substack, Medium, Ghost, Beehiiv) thành feed RSS hợp lệ và làm sạch triệt để form đăng ký email, footer unsubscribe, share buttons và tracking pixels trước khi lưu vào Room Database.

**Architecture:** Tạo gói `infrastructure/newsletter/` bao gồm `NewsletterPlatform`, `NewsletterUrlResolver` và `NewsletterSanitizer`. Tích hợp `NewsletterSanitizer` vào `RssHelper` để lọc nội dung ngay tại tầng Ingestion và tích hợp `NewsletterUrlResolver` vào `SubscribeViewModel` để tự động suy diễn RSS feed URL khi người dùng dán liên kết web.

**Tech Stack:** Kotlin 2.1.0, Jsoup 1.15.3, OkHttp 4.12.0, Hilt, Room Database, Jetpack Compose, JUnit4, MockK, Robolectric.

## Global Constraints

- Device Lock: Chỉ dùng `Pixel_10_Pro` (`emulator-5554`). Tuyệt đối không gửi lệnh tới thiết bị vật lý.
- Dừng ngay khi phát hiện quảng cáo toàn màn hình (R4) trong quá trình test màn hình UI.
- Tuân thủ TDD nghiêm ngặt: Viết test trước (RED) -> Implement tối thiểu (GREEN) -> Refactor & Commit.
- Không magic numbers, đầy đủ hằng số mô tả (`const val`), null-safe, coroutines trên `Dispatchers.IO`.
- Bảo toàn 100% các unit test hiện có (813 tests).

---

### Task 1: NewsletterPlatform & NewsletterSanitizer

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/newsletter/NewsletterPlatform.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/newsletter/NewsletterSanitizer.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/newsletter/NewsletterSanitizerTest.kt`

**Interfaces:**
- Produces: `NewsletterPlatform` enum, `NewsletterSanitizer.cleanArticleHtml(html: String, platform: NewsletterPlatform): String`

- [ ] **Step 1: Write the failing unit tests for NewsletterSanitizer**
  Tạo `NewsletterSanitizerTest.kt` với các trường hợp:
  - Loại bỏ tracking pixels (`1x1`, `open.substack.com`, `email.ghost.org`, `beehiiv.com/track`).
  - Loại bỏ các form và widget đăng ký email của Substack (`subscription-widget-wrap`, `subscribe-widget`).
  - Loại bỏ CTA đăng ký và upgrade của Ghost (`gh-post-upgrade-cta`, `gh-footer-signup`).
  - Loại bỏ form subscribe của Beehiiv (`subscribe_form`).
  - Loại bỏ footer và liên kết Unsubscribe, Manage Preferences.
  - Bảo toàn 100% bài viết chính: các đoạn văn, code block, trích dẫn, ảnh minh họa hợp lệ (> 2px).

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.newsletter.NewsletterSanitizerTest"`
  Expected: FAIL (lớp chưa tồn tại).

- [ ] **Step 3: Implement NewsletterPlatform & NewsletterSanitizer**
  Viết `NewsletterPlatform.kt` và `NewsletterSanitizer.kt` sử dụng Jsoup với bộ lọc CSS selectors và text matcher chính xác.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.newsletter.NewsletterSanitizerTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(newsletter): add NewsletterSanitizer for email junk and tracking removal`.

---

### Task 2: NewsletterUrlResolver

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/newsletter/NewsletterUrlResolver.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/newsletter/NewsletterUrlResolverTest.kt`

**Interfaces:**
- Produces: `NewsletterUrlResolver.resolveToRssUrl(inputUrl: String): String?`
- Produces: `NewsletterUrlResolver.detectPlatform(url: String): NewsletterPlatform`

- [ ] **Step 1: Write the failing unit tests for NewsletterUrlResolver**
  Tạo `NewsletterUrlResolverTest.kt` với các test case:
  - Substack subdomain: `https://danluu.substack.com` -> `https://danluu.substack.com/feed`
  - Substack profile: `https://substack.com/@user` -> chuyển đổi chính xác
  - Medium profile: `https://medium.com/@swyx` -> `https://medium.com/feed/@swyx`
  - Medium custom subdomain: `https://user.medium.com` -> `https://user.medium.com/feed`
  - Medium publication: `https://medium.com/towards-data-science` -> `https://medium.com/feed/towards-data-science`
  - Ghost: `https://blog.ghost.io` -> `https://blog.ghost.io/rss/`
  - Beehiiv: `https://newsletter.beehiiv.com` -> `https://newsletter.beehiiv.com/feed`
  - Generic HTML Auto-Discovery: Trích xuất thẻ `<link rel="alternate" type="application/rss+xml" href="...">`

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolverTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement NewsletterUrlResolver**
  Viết `NewsletterUrlResolver.kt` với OkHttpClient và regex pattern matching.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolverTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(newsletter): add NewsletterUrlResolver for feed auto-discovery`.

---

### Task 3: Tích hợp NewsletterSanitizer vào RssHelper

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperSanitizerTest.kt`

- [ ] **Step 1: Write failing test verifying RssHelper sanitizes article content and full content**
  Tạo test mô phỏng article có chứa email unsubscribe footer và tracking pixel, đảm bảo sau khi qua `RssHelper` nội dung đã được làm sạch.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.rss.RssHelperSanitizerTest"`
  Expected: FAIL.

- [ ] **Step 3: Modify RssHelper to use NewsletterSanitizer**
  Cập nhật `RssHelper.article()` và `RssHelper.parseFullContent()`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.rss.RssHelperSanitizerTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(rss): integrate NewsletterSanitizer into RssHelper ingestion pipeline`.

---

### Task 4: Tích hợp NewsletterUrlResolver vào SubscribeViewModel

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNewsletterTest.kt`

- [ ] **Step 1: Write failing test verifying SubscribeViewModel resolves newsletter link when direct RSS search fails**
  Tạo test trong đó user nhập link `https://danluu.substack.com`, ban đầu RSS search direct ném lỗi, ViewModel tự động resolve thành `https://danluu.substack.com/feed` và search thành công.

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNewsletterTest"`
  Expected: FAIL.

- [ ] **Step 3: Modify SubscribeViewModel to inject and use NewsletterUrlResolver**
  Bổ sung dependency injection cho `NewsletterUrlResolver` và logic fallback trong `SubscribeViewModel.search()`.

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNewsletterTest"`
  Expected: PASS.

- [ ] **Step 5: Commit**
  Run: `git commit` với thông điệp `feat(subscribe): auto resolve newsletter URLs in SubscribeViewModel`.

---

### Task 5: End-to-End Smoke Test trên Pixel 10 Pro & Hoàn Tất Task

- [ ] **Step 1: Chạy toàn bộ Unit Tests & Lint**
  Run: `./gradlew testDevDebugUnitTest lintDevDebug`
  Expected: Tất cả tests pass (>= 813 tests + test mới), Lint 0 errors.

- [ ] **Step 2: Build & Cài đặt lên Pixel 10 Pro (emulator-5554)**
  Run: `./gradlew assembleDevDebug && adb -s emulator-5554 install -r app/build/outputs/apk/dev/debug/*.apk`

- [ ] **Step 3: Thực hiện Smoke Test trên thiết bị**
  Thêm một newsletter Substack qua UI dialog Subscribe, verify feed hiển thị, mở bài đọc kiểm tra không còn rác email hay pixel.

- [ ] **Step 4: Tạo completion report & Push**
  Tạo `doc/task/done/INGEST-03_email-newsletter-ingestion-bridge_DONE.md`, cập nhật `doc/feature.md`, commit và push lên `origin/dev`.
