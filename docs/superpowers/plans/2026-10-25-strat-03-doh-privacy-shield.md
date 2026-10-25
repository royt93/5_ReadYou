# [STRAT-03] DoH Privacy Shield Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trang bị lá chắn quyền riêng tư toàn diện cho RSS Cat Hub gồm: DNS-over-HTTPS (DoH Cloudflare 1.1.1.1 & AdGuard 94.140.14.14 qua OkHttp Dns có tự động fallback System), tự động gọt bỏ query parameter theo dõi (utm_*, fbclid, gclid...) trên link bài viết, bộ lọc HTML dọn pixel 1x1 và script/iframe rác, và màn hình Cài đặt Quyền riêng tư & Bảo mật hỗ trợ 38 ngôn ngữ.

**Architecture:** Mở rộng tầng mạng OkHttpClient bằng `AppDns` (triển khai `okhttp3.Dns`), sử dụng giao thức DoH `application/dns-json` với bootstrap IP cố định để tránh truy vấn đệ quy, timeout 3s tự động fallback về `Dns.SYSTEM`. `UrlTrackingCleaner` và `AntiTrackerHtmlSanitizer` hoạt động ở tầng domain thuần Kotlin/Jsoup, gọt sạch link và bài đọc khi nạp/lưu DB, mở web và chia sẻ. Quản lý trạng thái bằng DataStore Preferences và giao diện Compose Material 3 trên route `RouteName.PRIVACY_SETTINGS`.

**Tech Stack:** Kotlin 2.1.0, OkHttp 5.0.0-alpha.11 (`okhttp3.Dns`), MockWebServer3, Jsoup, DataStore Preferences, Jetpack Compose Material 3, Hilt, JUnit4, Robolectric 4.14.1.

## Global Constraints
- **Lưu ý:** Chỉ code trên 1 nhánh `dev` duy nhất, không tạo worktree hay nhánh con.
- Không thêm bất kỳ thư viện hay dependency Gradle mới nào (tuân thủ YAGNI).
- Mọi I/O mạng hoặc đọc ghi DataStore phải chạy trên `Dispatchers.IO` / Worker thread, không bao giờ chặn Main thread (DoD).
- DoH timeout tối đa 3 giây; nếu gặp lỗi mạng hoặc quá thời gian, bắt buộc tự động fallback về `Dns.SYSTEM` để không bao giờ làm đứt kết nối nạp RSS.
- Bộ lọc HTML chống theo dõi phải bảo toàn nguyên vẹn các iframe video hợp lệ (`youtube.com/embed`, `player.vimeo.com`).
- Toàn bộ chuỗi giao diện mới phải được định nghĩa đầy đủ trên 38 file `res/values-*/strings.xml`, vượt qua `StringResourceParityTest`.
- Mỗi task kết thúc bằng commit riêng kèm attribution `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

---

## File Structure Map

| File | Trách nhiệm |
|---|---|
| `app/src/main/java/com/mckimquyen/reader/domain/security/UrlTrackingCleaner.kt` | Domain utility gọt bỏ các query tracking params (`utm_*`, `fbclid`, `gclid`, `_ga`...) trên URL. |
| `app/src/main/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizer.kt` | Domain utility dọn pixel 1x1, scripts, iframe quảng cáo trong HTML, giữ lại video YouTube/Vimeo. |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/DohProviderPref.kt` | DataStore preference cho DoH Provider (`OFF`, `CLOUDFLARE`, `ADGUARD`). |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/UrlTrackingCleanPref.kt` | DataStore preference toggle gọt URL tracking (boolean, mặc định true). |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HtmlAntiTrackerPref.kt` | DataStore preference toggle lọc HTML chống theo dõi (boolean, mặc định true). |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/net/AppDns.kt` | Cài đặt `okhttp3.Dns` hỗ trợ DoH JSON (`1.1.1.1`, `94.140.14.14`) kèm bootstrap client & fallback 3s. |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt` | Inject `AppDns` vào Singleton `OkHttpClient` của ứng dụng. |
| `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt` | ViewModel quản lý state và cập nhật DataStore cho trang cài đặt Quyền riêng tư. |
| `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt` | Màn hình Compose "Quyền riêng tư & Bảo mật" với Radio group DoH và 2 Toggle switches. |
| `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt` | Khai báo `RouteName.PRIVACY_SETTINGS = "privacy_settings"`. |
| `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` | Đăng ký route `PRIVACY_SETTINGS`. |
| `app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt` | Thêm mục điều hướng "Quyền riêng tư & Bảo mật" vào danh sách Cài đặt chính. |
| `app/src/main/res/values*/strings.xml` | 11 chuỗi bản dịch đa ngôn ngữ cho toàn bộ 38 locales. |

---

### Task 1: UrlTrackingCleaner — Gọt Bỏ Query Parameters Theo Dõi

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/security/UrlTrackingCleaner.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/security/UrlTrackingCleanerTest.kt`

**Interfaces:**
- Produces: `UrlTrackingCleaner.clean(url: String): String`

- [ ] **Step 1: Write failing unit test for UrlTrackingCleaner**

Tạo `app/src/test/java/com/mckimquyen/reader/domain/security/UrlTrackingCleanerTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlTrackingCleanerTest {

    @Test
    fun clean_removesUtmParametersWhilePreservingValidQueryParams() {
        val input = "https://example.com/article?id=123&utm_source=twitter&utm_medium=social&utm_campaign=spring2026&page=2"
        val expected = "https://example.com/article?id=123&page=2"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_removesSocialAndAdClickIdentifiers() {
        val input = "https://example.com/news?fbclid=IwAR123&gclid=Cj0K456&gbraid=0AAAA789&wbraid=0BBBB&msclkid=abc&twclid=xyz&igshid=def"
        val expected = "https://example.com/news"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_removesGoogleAnalyticsAndEmailTracking() {
        val input = "https://example.com/post?_ga=GA1.2.3.4&_gl=1*abc&mc_cid=c123&mc_eid=e456&_hsenc=h789&_hsmi=m012"
        val expected = "https://example.com/post"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_preservesFragmentAndAnchor() {
        val input = "https://example.com/doc?utm_source=rss&view=full#section-2"
        val expected = "https://example.com/doc?view=full#section-2"
        val actual = UrlTrackingCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun clean_handlesCleanUrlOrMalformedUrlGracefully() {
        assertEquals("https://example.com/clean/path", UrlTrackingCleaner.clean("https://example.com/clean/path"))
        assertEquals("", UrlTrackingCleaner.clean(""))
        assertEquals("not-a-valid-url", UrlTrackingCleaner.clean("not-a-valid-url"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.security.UrlTrackingCleanerTest"`
Expected: FAIL with compilation error (unresolved reference `UrlTrackingCleaner`).

- [ ] **Step 3: Implement UrlTrackingCleaner**

Tạo `app/src/main/java/com/mckimquyen/reader/domain/security/UrlTrackingCleaner.kt`:

```kotlin
package com.mckimquyen.reader.domain.security

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlTrackingCleaner {

    private val TRACKING_PARAMS = setOf(
        // Google Analytics & Marketing
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "utm_cid",
        "utm_reader",
        "utm_referrer",
        "_ga",
        "_gl",
        // Google Ads
        "gclid",
        "gbraid",
        "wbraid",
        // Meta / Facebook / Instagram
        "fbclid",
        "igshid",
        // Microsoft / Bing
        "msclkid",
        // Twitter / X
        "twclid",
        // MailChimp & Newsletters
        "mc_cid",
        "mc_eid",
        // Hubspot
        "_hsenc",
        "_hsmi",
    )

    fun clean(url: String): String {
        if (url.isBlank()) return url
        val httpUrl = url.toHttpUrlOrNull() ?: return url

        val names = httpUrl.queryParameterNames
        val hasTracking = names.any { isTrackingParam(it) }
        if (!hasTracking) return url

        val builder = httpUrl.newBuilder()
        for (name in names) {
            if (isTrackingParam(name)) {
                builder.removeAllQueryParameters(name)
            }
        }
        return builder.build().toString()
    }

    private fun isTrackingParam(name: String): Boolean {
        val lower = name.lowercase()
        return lower in TRACKING_PARAMS || lower.startsWith("utm_")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.security.UrlTrackingCleanerTest"`
Expected: PASS (5 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/security/UrlTrackingCleaner.kt app/src/test/java/com/mckimquyen/reader/domain/security/UrlTrackingCleanerTest.kt
git commit -m "feat(security): implement UrlTrackingCleaner for anti-tracking query stripping [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: AntiTrackerHtmlSanitizer — Dọn Dẹp Pixel 1x1, Scripts & Iframes Rác

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizer.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizerTest.kt`

**Interfaces:**
- Produces: `AntiTrackerHtmlSanitizer.sanitize(html: String?): String`

- [ ] **Step 1: Write failing unit test for AntiTrackerHtmlSanitizer**

Tạo `app/src/test/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizerTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiTrackerHtmlSanitizerTest {

    @Test
    fun sanitize_removesTinyTrackingPixelsAndHiddenImages() {
        val html = """
            <p>Welcome to our newsletter.</p>
            <img src="https://tracking.com/pixel.gif" width="1" height="1" alt="" />
            <img src="https://example.com/logo.png" width="800" height="400" />
            <img src="https://tracker.net/dot.png" style="display:none;" />
            <img src="https://stats.com/pixel?user=123" width="10" height="10" />
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("logo.png"))
        assertFalse(sanitized.contains("tracking.com/pixel.gif"))
        assertFalse(sanitized.contains("display:none"))
        assertFalse(sanitized.contains("stats.com/pixel"))
    }

    @Test
    fun sanitize_removesAllScriptTags() {
        val html = """
            <article>
                <h1>Breaking Story</h1>
                <script type="text/javascript">fetch('https://evil-analytics.com');</script>
                <p>Real article paragraph content.</p>
                <script src="https://cdn.tracking.js"></script>
            </article>
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("Real article paragraph content."))
        assertFalse(sanitized.contains("<script"))
        assertFalse(sanitized.contains("evil-analytics.com"))
        assertFalse(sanitized.contains("tracking.js"))
    }

    @Test
    fun sanitize_preservesAllowedVideoIframesWhileRemovingAdIframes() {
        val html = """
            <div>
                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ" width="560" height="315"></iframe>
                <iframe src="https://player.vimeo.com/video/123456"></iframe>
                <iframe src="https://adnetwork.com/banner?id=999"></iframe>
                <iframe src="https://tracking-widget.org/embed"></iframe>
            </div>
        """.trimIndent()

        val sanitized = AntiTrackerHtmlSanitizer.sanitize(html)
        assertTrue(sanitized.contains("youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(sanitized.contains("player.vimeo.com/video/123456"))
        assertFalse(sanitized.contains("adnetwork.com"))
        assertFalse(sanitized.contains("tracking-widget.org"))
    }

    @Test
    fun sanitize_handlesBlankAndNormalTextCorrectly() {
        assertEquals("", AntiTrackerHtmlSanitizer.sanitize(null))
        assertEquals("", AntiTrackerHtmlSanitizer.sanitize("   "))
        assertEquals("<p>Simple clean paragraph.</p>", AntiTrackerHtmlSanitizer.sanitize("<p>Simple clean paragraph.</p>"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.security.AntiTrackerHtmlSanitizerTest"`
Expected: FAIL with compilation error (unresolved reference `AntiTrackerHtmlSanitizer`).

- [ ] **Step 3: Implement AntiTrackerHtmlSanitizer**

Tạo `app/src/main/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizer.kt`:

```kotlin
package com.mckimquyen.reader.domain.security

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AntiTrackerHtmlSanitizer {

    private val TRACKING_URL_PATTERNS = listOf(
        "/pixel",
        "/track/",
        "/tr?",
        "1x1",
        "doubleclick.net",
        "google-analytics.com",
        "facebook.com/tr",
        "scorecardresearch.com",
        "pixel.wp.com",
        "mailtrack",
        "open.substack.com",
        "email.ghost.org",
        "beehiiv.com/track",
    )

    private val ALLOWED_VIDEO_HOSTS = listOf(
        "youtube.com",
        "youtube-nocookie.com",
        "youtu.be",
        "vimeo.com",
        "player.vimeo.com",
    )

    private const val MAX_TINY_PIXEL_DIMENSION = 2

    fun sanitize(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val doc: Document = Jsoup.parseBodyFragment(html)
        val body = doc.body()

        // 1. Remove tracking pixels & hidden images
        val images = body.select("img")
        for (img in images) {
            val src = img.attr("src").lowercase()
            val width = img.attr("width").toIntOrNull()
            val height = img.attr("height").toIntOrNull()
            val style = img.attr("style").lowercase()

            val isTiny = (width != null && width <= MAX_TINY_PIXEL_DIMENSION) ||
                    (height != null && height <= MAX_TINY_PIXEL_DIMENSION)
            val isHidden = style.contains("display:none") || style.contains("display: none") ||
                    style.contains("visibility:hidden") || style.contains("visibility: hidden")
            val isTrackerUrl = TRACKING_URL_PATTERNS.any { src.contains(it) }

            if (isTiny || isHidden || isTrackerUrl) {
                img.remove()
            }
        }

        // 2. Remove all script tags
        body.select("script").remove()

        // 3. Filter iframes (keep only legitimate video hosts)
        val iframes = body.select("iframe")
        for (iframe in iframes) {
            val src = iframe.attr("src").lowercase()
            val isAllowedVideo = ALLOWED_VIDEO_HOSTS.any { src.contains(it) }
            if (!isAllowedVideo) {
                iframe.remove()
            }
        }

        return body.html().trim()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.security.AntiTrackerHtmlSanitizerTest"`
Expected: PASS (4 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizer.kt app/src/test/java/com/mckimquyen/reader/domain/security/AntiTrackerHtmlSanitizerTest.kt
git commit -m "feat(security): implement AntiTrackerHtmlSanitizer for cleaning tracking pixels and scripts [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Preferences DataStore — DohProviderPref, UrlTrackingCleanPref & HtmlAntiTrackerPref

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/DohProviderPref.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/UrlTrackingCleanPref.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HtmlAntiTrackerPref.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/PrivacyPreferencesTest.kt`

**Interfaces:**
- `DohProviderPref`: sealed class with `SYSTEM` (0, default), `CLOUDFLARE` (1), `ADGUARD` (2)
- `UrlTrackingCleanPref`: boolean pref (default true)
- `HtmlAntiTrackerPref`: boolean pref (default true)
- `DataStoreKeys.DohProvider`, `DataStoreKeys.UrlTrackingClean`, `DataStoreKeys.HtmlAntiTracker`

- [ ] **Step 1: Write unit test for PrivacyPreferences**

Tạo `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/PrivacyPreferencesTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.pref

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPreferencesTest {

    @Test
    fun dohProviderPref_defaultsToSystemAndParsesCorrectly() {
        val emptyPrefs = mutablePreferencesOf()
        assertEquals(DohProviderPref.SYSTEM, DohProviderPref.fromPreferences(emptyPrefs))

        val cfPrefs = mutablePreferencesOf(intPreferencesKey("doh_provider") to 1)
        assertEquals(DohProviderPref.CLOUDFLARE, DohProviderPref.fromPreferences(cfPrefs))

        val agPrefs = mutablePreferencesOf(intPreferencesKey("doh_provider") to 2)
        assertEquals(DohProviderPref.ADGUARD, DohProviderPref.fromPreferences(agPrefs))
    }

    @Test
    fun urlTrackingCleanPref_defaultsToTrue() {
        val emptyPrefs = mutablePreferencesOf()
        assertTrue(UrlTrackingCleanPref.fromPreferences(emptyPrefs).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("url_tracking_clean") to false)
        assertFalse(UrlTrackingCleanPref.fromPreferences(offPrefs).value)
    }

    @Test
    fun htmlAntiTrackerPref_defaultsToTrue() {
        val emptyPrefs = mutablePreferencesOf()
        assertTrue(HtmlAntiTrackerPref.fromPreferences(emptyPrefs).value)

        val offPrefs = mutablePreferencesOf(booleanPreferencesKey("html_anti_tracker") to false)
        assertFalse(HtmlAntiTrackerPref.fromPreferences(offPrefs).value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.PrivacyPreferencesTest"`
Expected: FAIL with compilation error (unresolved references).

- [ ] **Step 3: Implement Preferences and DataStoreKeys**

1. Thêm keys vào `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`:
```kotlin
    object DohProvider : DataStoreKeys<Int>() {
        override val key = intPreferencesKey("doh_provider")
    }

    object UrlTrackingClean : DataStoreKeys<Boolean>() {
        override val key = booleanPreferencesKey("url_tracking_clean")
    }

    object HtmlAntiTracker : DataStoreKeys<Boolean>() {
        override val key = booleanPreferencesKey("html_anti_tracker")
    }
```

2. Tạo `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/DohProviderPref.kt`:
```kotlin
package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class DohProviderPref(val id: Int, val host: String, val endpoint: String, val bootstrapIp: String) : Pref() {
    object SYSTEM : DohProviderPref(0, "System", "", "")
    object CLOUDFLARE : DohProviderPref(1, "Cloudflare", "https://1.1.1.1/dns-query", "1.1.1.1")
    object ADGUARD : DohProviderPref(2, "AdGuard", "https://94.140.14.14/dns-query", "94.140.14.14")

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.DohProvider, id)
        }
    }

    companion object {
        val default: DohProviderPref get() = SYSTEM

        fun fromId(id: Int): DohProviderPref = when (id) {
            1 -> CLOUDFLARE
            2 -> ADGUARD
            else -> SYSTEM
        }

        fun fromPreferences(preferences: Preferences): DohProviderPref =
            fromId(preferences[DataStoreKeys.DohProvider.key] ?: default.id)
    }
}
```

3. Tạo `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/UrlTrackingCleanPref.kt`:
```kotlin
package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class UrlTrackingCleanPref(val value: Boolean) : Pref() {
    object ON : UrlTrackingCleanPref(true)
    object OFF : UrlTrackingCleanPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.UrlTrackingClean, value)
        }
    }

    companion object {
        val default: UrlTrackingCleanPref get() = ON

        fun fromPreferences(preferences: Preferences): UrlTrackingCleanPref =
            when (preferences[DataStoreKeys.UrlTrackingClean.key]) {
                false -> OFF
                else -> ON
            }
    }
}
```

4. Tạo `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HtmlAntiTrackerPref.kt`:
```kotlin
package com.mckimquyen.reader.infrastructure.pref

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class HtmlAntiTrackerPref(val value: Boolean) : Pref() {
    object ON : HtmlAntiTrackerPref(true)
    object OFF : HtmlAntiTrackerPref(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKeys.HtmlAntiTracker, value)
        }
    }

    companion object {
        val default: HtmlAntiTrackerPref get() = ON

        fun fromPreferences(preferences: Preferences): HtmlAntiTrackerPref =
            when (preferences[DataStoreKeys.HtmlAntiTracker.key]) {
                false -> OFF
                else -> ON
            }
    }
}
```

5. Thêm thuộc tính vào `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`:
Thêm `dohProvider: DohProviderPref`, `urlTrackingClean: UrlTrackingCleanPref`, `htmlAntiTracker: HtmlAntiTrackerPref` vào `Settings` và ánh xạ trong `fromPreferences`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.PrivacyPreferencesTest"`
Expected: PASS (3 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/pref/DohProviderPref.kt app/src/main/java/com/mckimquyen/reader/infrastructure/pref/UrlTrackingCleanPref.kt app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HtmlAntiTrackerPref.kt app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt app/src/test/java/com/mckimquyen/reader/infrastructure/pref/PrivacyPreferencesTest.kt
git commit -m "feat(pref): add DohProviderPref, UrlTrackingCleanPref and HtmlAntiTrackerPref [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: AppDns — Triển Khai OkHttp Dns Hỗ Trợ DoH JSON & Fallback 3s

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/net/AppDns.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/net/AppDnsTest.kt`

**Interfaces:**
- Produces: `class AppDns : okhttp3.Dns`
- Takes: `dohProviderSupplier: () -> DohProviderPref`, `bootstrapClient: OkHttpClient`, `systemDns: Dns = Dns.SYSTEM`

- [ ] **Step 1: Write unit test for AppDns using MockWebServer**

Tạo `app/src/test/java/com/mckimquyen/reader/infrastructure/net/AppDnsTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.net

import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class AppDnsTest {

    private lateinit var server: MockWebServer
    private val fakeSystemDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname == "example.com") {
                listOf(InetAddress.getByName("93.184.216.34"))
            } else {
                throw UnknownHostException("Not found: $hostname")
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookup_whenSystemDns_delegatesDirectlyWithoutNetworkCall() {
        val appDns = AppDns(
            dohProviderSupplier = { DohProviderPref.SYSTEM },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("93.184.216.34", addresses[0].hostAddress)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun lookup_whenIpLiteral_returnsAddressWithoutDnsQuery() {
        val appDns = AppDns(
            dohProviderSupplier = { DohProviderPref.CLOUDFLARE },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("1.1.1.1")
        assertEquals(1, addresses.size)
        assertEquals("1.1.1.1", addresses[0].hostAddress)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun lookup_whenDohSucceeds_parsesDnsJsonResponse() {
        val dnsJsonResponse = """
            {
              "Status": 0,
              "Answer": [
                {"name": "example.com", "type": 1, "TTL": 300, "data": "104.16.1.1"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(dnsJsonResponse))

        val customDoh = object : DohProviderPref(1, "MockDoH", server.url("/dns-query").toString(), "127.0.0.1") {}
        val appDns = AppDns(
            dohProviderSupplier = { customDoh },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("104.16.1.1", addresses[0].hostAddress)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("application/dns-json", recorded.headers["Accept"])
        assertTrue(recorded.path?.contains("name=example.com") == true)
    }

    @Test
    fun lookup_whenDohFailsWithHttpError_fallsBackToSystemDns() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val customDoh = object : DohProviderPref(1, "MockDoH", server.url("/dns-query").toString(), "127.0.0.1") {}
        val appDns = AppDns(
            dohProviderSupplier = { customDoh },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("93.184.216.34", addresses[0].hostAddress) // fallback system result
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.net.AppDnsTest"`
Expected: FAIL with compilation error (unresolved reference `AppDns`).

- [ ] **Step 3: Implement AppDns**

Tạo `app/src/main/java/com/mckimquyen/reader/infrastructure/net/AppDns.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.net

import android.util.Log
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class AppDns(
    private val dohProviderSupplier: () -> DohProviderPref,
    private val bootstrapClient: OkHttpClient = createDefaultBootstrapClient(),
    private val systemDns: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. IP literal check
        val ipLiteral = parseNumericAddress(hostname)
        if (ipLiteral != null) {
            return listOf(ipLiteral)
        }

        // 2. Check current preference
        val provider = dohProviderSupplier()
        if (provider == DohProviderPref.SYSTEM || provider.endpoint.isBlank()) {
            return systemDns.lookup(hostname)
        }

        // 3. Query DoH endpoint
        try {
            val resolved = queryDoh(hostname, provider)
            if (resolved.isNotEmpty()) {
                return resolved
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH lookup failed for $hostname via ${provider.host} ($e). Falling back to System DNS.")
        }

        // 4. Graceful fallback
        return systemDns.lookup(hostname)
    }

    private fun queryDoh(hostname: String, provider: DohProviderPref): List<InetAddress> {
        val url = provider.endpoint.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("name", hostname)
            ?.addQueryParameter("type", "A")
            ?.build() ?: return emptyList()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val bodyString = response.body?.string().orEmpty()
            return parseDnsJsonResponse(bodyString)
        }
    }

    private fun parseDnsJsonResponse(json: String): List<InetAddress> {
        if (json.isBlank()) return emptyList()
        val jsonObject = JSONObject(json)
        val answerArray = jsonObject.optJSONArray("Answer") ?: return emptyList()

        val result = mutableListOf<InetAddress>()
        for (i in 0 until answerArray.length()) {
            val answer = answerArray.getJSONObject(i)
            // Type 1 is DNS Record Type A (IPv4)
            val type = answer.optInt("type", 0)
            val data = answer.optString("data", "").trim()
            if (type == 1 && data.isNotEmpty()) {
                try {
                    result.add(InetAddress.getByName(data))
                } catch (_: UnknownHostException) {
                }
            }
        }
        return result
    }

    private fun parseNumericAddress(hostname: String): InetAddress? {
        val parts = hostname.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            return try {
                InetAddress.getByAddress(hostname, parts.map { it.toInt().toByte() }.toByteArray())
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    companion object {
        private const val TAG = "AppDns"

        fun createDefaultBootstrapClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.net.AppDnsTest"`
Expected: PASS (4 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/net/AppDns.kt app/src/test/java/com/mckimquyen/reader/infrastructure/net/AppDnsTest.kt
git commit -m "feat(net): implement AppDns with DoH JSON protocol and 3s fallback [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Integration Tầng Mạng & RSS — Nối AppDns, UrlTrackingCleaner & AntiTrackerHtmlSanitizer

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/GoogleReaderRssSv.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperSanitizerTest.kt`

**Interfaces:**
- `OkHttpClientModule` cung cấp `OkHttpClient` với `.dns(appDns)`.
- `RssHelper`, `LocalRssSv`, `FeverRssSv`, `GoogleReaderRssSv` gọi `UrlTrackingCleaner.clean(...)` và `AntiTrackerHtmlSanitizer.sanitize(...)`.

- [ ] **Step 1: Write integration test verifying RssHelper cleans tracking params and HTML**

Mở `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperSanitizerTest.kt`: Thêm test kiểm tra URL bài viết được gọt bỏ `utm_*` và pixel 1x1 được dọn khỏi `fullContent`:

```kotlin
    @Test
    fun parseFullContent_cleansTrackingUrlAndPixels() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            <html>
            <body>
                <h1>Title</h1>
                <p>Real article text.</p>
                <img src="https://stats.com/pixel.gif" width="1" height="1" />
                <script>badScript();</script>
            </body>
            </html>
        """.trimIndent()))
        server.start()

        val linkWithTracking = server.url("/article?id=1&utm_source=twitter&fbclid=abc").toString()
        val content = rssHelper.parseFullContent(linkWithTracking, "Title")
        
        assertTrue(content.contains("Real article text."))
        assertFalse(content.contains("badScript"))
        assertFalse(content.contains("pixel.gif"))
        server.shutdown()
    }
```

- [ ] **Step 2: Connect AppDns into OkHttpClientModule**

Chỉnh sửa `app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt`:
Cung cấp `AppDns` singleton và gắn vào `builder.dns(appDns)`:
```kotlin
    @Provides
    @Singleton
    fun provideAppDns(
        @ApplicationContext context: Context,
    ): AppDns = AppDns(
        dohProviderSupplier = {
            DohProviderPref.fromPreferences(context.dataStoreSnapshot)
        }
    )
```

- [ ] **Step 3: Connect UrlTrackingCleaner & AntiTrackerHtmlSanitizer into RssHelper & Providers**

1. Trong `RssHelper.kt`:
   - Ở `article(feed, accountId, syndEntry)`:
     ```kotlin
     val rawLink = syndEntry.link ?: ""
     val cleanLink = UrlTrackingCleaner.clean(rawLink)
     ```
   - Áp dụng `AntiTrackerHtmlSanitizer.sanitize(rawSource)` kết hợp `NewsletterSanitizer`.
2. Trong `LocalRssSv.kt`, `FeverRssSv.kt`, `GoogleReaderRssSv.kt`:
   - Đảm bảo các link bài được bọc qua `UrlTrackingCleaner.clean(link)`.
3. Trong `ReadingPage.kt`:
   - Khi chia sẻ hoặc mở web browser:
     ```kotlin
     val cleanUrl = UrlTrackingCleaner.clean(url)
     ```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.rss.RssHelperSanitizerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt app/src/main/java/com/mckimquyen/reader/domain/sv/ app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperSanitizerTest.kt
git commit -m "feat(network): wire AppDns, UrlTrackingCleaner and AntiTrackerHtmlSanitizer into app [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Localization — 11 Chuỗi Mới Cho 38 Locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: 37 `app/src/main/res/values-*/strings.xml`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java`

**11 chuỗi cần thêm:**
1. `privacy_settings_title`: "Privacy & Security" / "Quyền riêng tư & Bảo mật"
2. `privacy_settings_desc`: "DNS-over-HTTPS, URL cleaner, and anti-tracker shield"
3. `doh_title`: "DNS-over-HTTPS (DoH)"
4. `doh_desc`: "Encrypts domain name resolution queries to prevent surveillance by network providers and public Wi-Fi."
5. `doh_mode_system`: "System DNS (Default)"
6. `doh_mode_cloudflare`: "Cloudflare DoH (1.1.1.1 — Fast & Private)"
7. `doh_mode_adguard`: "AdGuard DoH (Blocks ads & trackers at DNS level)"
8. `url_tracking_title`: "Strip URL Tracking Parameters"
9. `url_tracking_desc`: "Automatically removes tracking tags (utm_*, fbclid, gclid...) when fetching, opening, and sharing links."
10. `html_antitracker_title`: "Anti-Tracker Content Shield"
11. `html_antitracker_desc`: "Filters out 1x1 tracking pixels, hidden beacons, and malicious scripts inside articles."

- [ ] **Step 1: Write English base strings in values/strings.xml**

Thêm 11 keys vào `app/src/main/res/values/strings.xml`.

- [ ] **Step 2: Run StringResourceParityTest to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
Expected: FAIL reporting missing keys in `values-*`.

- [ ] **Step 3: Run translation script for all 37 locale resource files**

Chạy script Python bổ sung bản dịch tự nhiên cho 11 keys trên toàn bộ 37 thư mục `values-*`.

- [ ] **Step 4: Run StringResourceParityTest to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
Expected: PASS (all 6 parity tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values*/strings.xml
git commit -m "feat(i18n): add Privacy & Security strings across 38 locales [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: UI — PrivacySettingsPage & Navigation Wiring

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt`

**Interfaces:**
- `PrivacySettingsPage(navController, onBack)`
- `PrivacySettingsViewModel`: exposes `dohProvider`, `urlTrackingClean`, `htmlAntiTracker`, and setter methods.

- [ ] **Step 1: Write unit test for PrivacySettingsViewModel**

Tạo `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import com.mckimquyen.reader.infrastructure.pref.HtmlAntiTrackerPref
import com.mckimquyen.reader.infrastructure.pref.UrlTrackingCleanPref
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PrivacySettingsViewModelTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun updateDohProvider_updatesPreferenceState() = runTest(testDispatcher) {
        val viewModel = PrivacySettingsViewModel(context)
        viewModel.setDohProvider(DohProviderPref.CLOUDFLARE)
        advanceUntilIdle()

        assertEquals(DohProviderPref.CLOUDFLARE, viewModel.dohProvider.value)
    }

    @Test
    fun toggleUrlTracking_updatesPreferenceState() = runTest(testDispatcher) {
        val viewModel = PrivacySettingsViewModel(context)
        viewModel.setUrlTrackingClean(false)
        advanceUntilIdle()

        assertFalse(viewModel.urlTrackingClean.value)
    }

    @Test
    fun toggleHtmlAntiTracker_updatesPreferenceState() = runTest(testDispatcher) {
        val viewModel = PrivacySettingsViewModel(context)
        viewModel.setHtmlAntiTracker(false)
        advanceUntilIdle()

        assertFalse(viewModel.htmlAntiTracker.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.setting.privacy.PrivacySettingsViewModelTest"`
Expected: FAIL (PrivacySettingsViewModel does not exist).

- [ ] **Step 3: Implement PrivacySettingsViewModel and PrivacySettingsPage**

1. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`:
Hỗ trợ quản lý state và lưu xuống DataStore.
2. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt`:
Compose Material 3: TopAppBar với nút Back, Radio buttons cho DoH (System, Cloudflare, AdGuard), Switches cho URL Tracking Cleaner và HTML Anti-Tracker Shield.
3. Trong `RouteName.kt`: Thêm `const val PRIVACY_SETTINGS = "privacy_settings"`.
4. Trong `HomeEntry.kt`: Đăng ký `animatedComposable(route = RouteName.PRIVACY_SETTINGS)`.
5. Trong `SettingsPage.kt`: Thêm mục "Quyền riêng tư & Bảo mật" (icon `Icons.Outlined.Security`) dẫn tới `RouteName.PRIVACY_SETTINGS`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.setting.privacy.PrivacySettingsViewModelTest"`
Expected: PASS (3 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/ app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt
git commit -m "feat(ui): implement PrivacySettingsPage and wire into Settings navigation [STRAT-03]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: Full Suite, Lint Check, Pixel Smoke & Completion Documentation

**Files:**
- Create: `doc/task/done/STRAT-03_doh-privacy-shield_DONE.md`
- Delete: `doc/task/todo/STRAT-03_doh-privacy-shield.md`
- Modify: `doc/task/todo/11_AUTONOMOUS_COMMUNITY_PRIVACY.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew testDevDebugUnitTest --rerun`
Expected: BUILD SUCCESSFUL, all unit tests pass, 0 failures.

- [ ] **Step 2: Run lint check**

Run: `./gradlew lintDevDebug`
Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 3: Device Smoke Test on Pixel 7 Pro (Serial `2B051FDH3006MU`)**

- Khóa 1 thiết bị duy nhất: `adb -s 2B051FDH3006MU`.
- Backup database Room trước khi cài đặt.
- Cài đặt APK: `adb -s 2B051FDH3006MU install -r app/build/outputs/apk/dev/debug/*.apk`.
- Mở Cài đặt -> Quyền riêng tư & Bảo mật:
  - Chọn Cloudflare DoH (1.1.1.1).
  - Ra ngoài FlowPage vuốt refresh sync feed -> xác nhận phân giải DNS thành công, không gián đoạn.
  - Kiểm tra mở bài có tracking -> link sạch sẽ.
- Tuân thủ R4: gặp quảng cáo toàn màn hình che UI thì dừng ngay, chờ owner xác nhận "done".
- Giữ nguyên dữ liệu người dùng trên thiết bị.

- [ ] **Step 4: Update documentation and move task to done**

1. Tạo `doc/task/done/STRAT-03_doh-privacy-shield_DONE.md` ghi nhận kết quả và bằng chứng smoke.
2. Xóa `doc/task/todo/STRAT-03_doh-privacy-shield.md`.
3. Cập nhật `doc/task/todo/11_AUTONOMOUS_COMMUNITY_PRIVACY.md` đánh dấu STRAT-03 `✅ Done`.
4. Cập nhật `doc/feature.md` chuyển STRAT-03 sang `✅ Recently implemented`.

- [ ] **Step 5: Final commit on dev branch**

```bash
git add doc/
git commit -m "docs: complete [STRAT-03] DoH Privacy Shield and move to done

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
