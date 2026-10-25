# Thiết Kế: [STRAT-03] Lá Chắn Quyền Riêng Tư: DNS-over-HTTPS (DoH) & Chặn Tracker Ngầm

- **Mã tính năng:** STRAT-03
- **Loại:** Security & Privacy / Anti-Tracking
- **Độ ưu tiên:** P2 (Medium)
- **Ngày lập:** 2026-10-25
- **Tác giả:** Claude Code & roy
- **Nhánh:** `dev`

---

## 1. Mục tiêu & Bối cảnh

### 1.1 Vấn đề thực tế
Nhiều nhà mạng viễn thông hoặc mạng Wi-Fi công cộng theo dõi và ghi nhật ký thói quen đọc báo của người dùng thông qua các truy vấn DNS dạng văn bản thuần (plain-text). Ngoài ra, các bài viết RSS thường chứa các tham số theo dõi URL cồng kềnh (`utm_source`, `fbclid`, `gclid`, `_ga`...) và các pixel theo dõi ẩn (1x1 GIF / script rác) nhằm thu thập dấu vân tay thiết bị (fingerprinting) của người đọc.

### 1.2 Giải pháp
Trang bị **Lá Chắn Quyền Riêng Tư (Privacy Shield)** toàn diện trong RSS Cat Hub:
1. **DNS-over-HTTPS (DoH) qua OkHttp Dns:** Mã hóa truy vấn DNS bằng giao thức DNS-over-HTTPS với nhà cung cấp Cloudflare (`1.1.1.1`) hoặc AdGuard (`94.140.14.14`), tự động fallback về DNS hệ thống khi gặp lỗi mạng/timeout (3 giây) để không làm gián đoạn việc tải RSS.
2. **Gọt bỏ tham số theo dõi URL (URL Tracking Stripping):** Tự động loại bỏ các query parameters nhận diện (`utm_*`, `fbclid`, `gclid`, `_ga`, `mc_cid`...) khi lưu bài viết vào database, khi mở trình duyệt ngoài và khi chia sẻ bài viết.
3. **Bộ lọc HTML chống theo dõi (Anti-Tracker HTML Filter):** Loại bỏ triệt để các thẻ ảnh pixel theo dõi 1x1/0x0/ẩn, các thẻ `<script>` ngầm và các `<iframe>` quảng cáo rác trong nội dung bài viết, đồng thời bảo toàn các iframe video hợp lệ (YouTube, Vimeo).
4. **Trang Cài Đặt Riêng Biệt:** Thêm trang "Quyền riêng tư & Bảo mật" (`RouteName.PRIVACY_SETTINGS`) trong Cài đặt với toggle trực quan và hỗ trợ đầy đủ 38 ngôn ngữ.

---

## 2. Kiến trúc & Luồng dữ liệu

```
[Mạng Internet / RSS Feeds]
          │
          ▼
   [OkHttpClient] ────► [AppDns (DohDns)]
          │                     │
          │                     ├── OFF: Dns.SYSTEM
          │                     ├── CLOUDFLARE: https://1.1.1.1/dns-query (dns-json)
          │                     ├── ADGUARD: https://94.140.14.14/dns-query (dns-json)
          │                     └── Fallback: Dns.SYSTEM nếu timeout > 3s hoặc lỗi
          ▼
   [RssHelper / Ingestion]
          │
          ├── [UrlTrackingCleaner] ──► Gọt sạch utm_*, fbclid, gclid... trên Article.link
          │
          └── [AntiTrackerHtmlSanitizer] ──► Lọc sạch pixel 1x1, scripts, iframes rác trên HTML
          │
          ▼
   [Room Database v19]
          │
          ▼
   [ReadingPage / Sharesheet] ──► Luôn mở & chia sẻ URL sạch không tracking
```

### 2.1 Cài đặt DNS-over-HTTPS (`AppDns`)
- Triển khai interface `okhttp3.Dns`.
- Sử dụng Bootstrap OkHttpClient độc lập (dùng `Dns.SYSTEM` và kết nối IP tĩnh `1.1.1.1` hoặc `94.140.14.14`, tránh vòng lặp phân giải DNS đệ quy).
- Sử dụng chuẩn `application/dns-json` (được cả Cloudflare và AdGuard hỗ trợ chính thức):
  - Request: `GET https://<bootstrap-ip>/dns-query?name=<hostname>&type=A` với Header `Accept: application/dns-json`.
  - Parse mảng `Answer` lấy danh sách địa chỉ `InetAddress`.
- Giới hạn timeout 3 giây. Nếu quá 3 giây hoặc gặp lỗi HTTP/parse JSON, tự động fallback về `Dns.SYSTEM.lookup(hostname)` để đảm bảo sync feed không bao giờ bị đứt kết nối.

### 2.2 Công cụ làm sạch URL (`UrlTrackingCleaner`)
Loại bỏ các tham số query thuộc danh sách đen theo dõi:
- Chiến dịch marketing: `utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `utm_id`, `utm_cid`, `utm_reader`, `utm_referrer`
- Mạng xã hội: `fbclid`, `igshid`, `twclid`
- Quảng cáo số: `gclid`, `gbraid`, `wbraid`, `msclkid`
- Phân tích web: `_ga`, `_gl`
- Email tracking: `mc_cid`, `mc_eid`
- Bảo toàn tuyệt đối các query param chức năng nghiệp vụ hợp lệ (ví dụ: `id`, `articleId`, `page`, `view`, `p`).

### 2.3 Bộ lọc HTML chống theo dõi (`AntiTrackerHtmlSanitizer`)
- Kế thừa và mở rộng cơ chế của `NewsletterSanitizer`.
- Thẻ `<img>`: Loại bỏ nếu `width <= 2` hoặc `height <= 2`, hoặc thuộc tính style chứa `display:none` / `visibility:hidden`, hoặc src chứa các endpoint theo dõi (`/pixel`, `/track`, `doubleclick`, `google-analytics`, `facebook.com/tr`, `scorecardresearch`, `pixel.wp.com`).
- Thẻ `<script>`: Loại bỏ 100% trong nội dung và mô tả bài viết.
- Thẻ `<iframe>`: Loại bỏ nếu là iframe quảng cáo/tracking; giữ lại nguyên vẹn các iframe video hợp lệ (`youtube.com/embed`, `player.vimeo.com`).

---

## 3. Quản lý Cấu hình (Preferences) & Giao diện (Compose)

### 3.1 Preferences DataStore
1. `DohProviderPref`: Giá trị `OFF` (0, mặc định), `CLOUDFLARE` (1), `ADGUARD` (2).
2. `UrlTrackingCleanPref`: Giá trị boolean (mặc định `true`).
3. `HtmlAntiTrackerPref`: Giá trị boolean (mặc định `true`).

### 3.2 Giao diện Settings
- Trong `SettingsPage.kt`: Thêm mục "Quyền riêng tư & Bảo mật" (icon `Icons.Outlined.Security`).
- Màn hình `PrivacySettingsPage.kt`:
  - **Mục 1: DNS-over-HTTPS (DoH):**
    - Nhóm Radio Buttons:
      - *DNS hệ thống (Mặc định)*
      - *Cloudflare DoH (1.1.1.1 — Tốc độ cao & Riêng tư)*
      - *AdGuard DoH (Chặn quảng cáo & tracker ở tầng DNS)*
  - **Mục 2: Xóa tham số theo dõi URL:**
    - Switch bật/tắt (Mặc định: Bật).
    - Mô tả: Tự động xóa mã theo dõi (utm_*, fbclid...) khi nạp bài, mở web và chia sẻ.
  - **Mục 3: Chặn tracker trong nội dung:**
    - Switch bật/tắt (Mặc định: Bật).
    - Mô tả: Loại bỏ pixel 1x1, script ngầm và iframe quảng cáo trong bài viết.

---

## 4. Điều hướng & Đa ngôn ngữ (Localization)

- Route mới trong `RouteName.kt`:
  ```kotlin
  const val PRIVACY_SETTINGS = "privacy_settings"
  ```
- Đăng ký `animatedComposable(route = RouteName.PRIVACY_SETTINGS)` trong `HomeEntry.kt`.
- Thêm chuỗi tài nguyên mới đầy đủ trên toàn bộ 38 file `res/values-*/strings.xml`, vượt qua `StringResourceParityTest`:
  - `privacy_settings_title`
  - `privacy_settings_desc`
  - `doh_title`
  - `doh_desc`
  - `doh_mode_system`
  - `doh_mode_cloudflare`
  - `doh_mode_adguard`
  - `url_tracking_title`
  - `url_tracking_desc`
  - `html_antitracker_title`
  - `html_antitracker_desc`

---

## 5. Kế hoạch Kiểm thử (TDD & Verification)

1. **Unit Tests:**
   - `DohDnsTest`: Kiểm tra truy vấn DoH qua MockWebServer với format `dns-json` của Cloudflare và AdGuard; kiểm tra timeout/lỗi HTTP tự động fallback về `Dns.SYSTEM`; kiểm tra bỏ qua khi host là địa chỉ IP.
   - `UrlTrackingCleanerTest`: Kiểm tra gọt bỏ chính xác các query tracking, bảo toàn query param hợp lệ, kiểm tra các dạng URL phức tạp, URL không có query, URL có fragment (`#anchor`).
   - `AntiTrackerHtmlSanitizerTest`: Kiểm tra lọc ảnh pixel 1x1, ảnh ẩn, script, iframe rác; kiểm tra bảo toàn iframe video YouTube và Vimeo; kiểm tra bảo toàn văn bản bài viết.
   - `PrivacySettingsViewModelTest` & `PrivacySettingsPageTest`: Kiểm tra đọc/ghi Preferences DataStore và toggle giao diện.
   - `StringResourceParityTest`: Đảm bảo đồng bộ 38 locale.
2. **Device Smoke Test (Pixel 7 Pro serial `2B051FDH3006MU`):**
   - Mở màn hình Quyền riêng tư & Bảo mật từ Settings.
   - Chuyển DoH sang Cloudflare -> thực hiện đồng bộ feed -> sync thành công.
   - Kiểm tra bài viết có tracking link -> link đã được làm sạch.
   - Tuân thủ nghiêm ngặt R4: gặp quảng cáo toàn màn hình che UI thì dừng ngay, chờ owner xác nhận "done".
