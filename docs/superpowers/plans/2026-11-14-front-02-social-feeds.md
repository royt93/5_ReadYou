# FRONT-02 Theo dõi Mastodon và Bluesky bằng RSS công khai Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Người dùng dán một tài khoản Mastodon hoặc Bluesky vào ô Thêm nguồn sẵn có; app đổi nó thành địa chỉ RSS công khai, xem trước và đăng ký như mọi nguồn tin; bài thiếu tiêu đề không còn bị bỏ.

**Architecture:** Một bộ nhận dạng thuần (`SocialFeedUrlResolver`, không Android, không mạng) đổi chữ người dùng gõ thành địa chỉ RSS `https`; `SubscribeViewModel.search()` gọi nó **trước** `formatUrl()` và rơi về luồng cũ nếu địa chỉ đoán không đọc được. `RssHelper.article` lấy dòng đầu của nội dung làm tiêu đề khi bài không có tiêu đề, và `queryRssXml` thêm lưới an toàn từng bài.

**Tech Stack:** Kotlin 2.1, Rome 2.1.0 (đã có), OkHttp (đã có), Robolectric/JUnit4/MockK/MockWebServer (đã có). **Không dependency mới.**

## Global Constraints

- Nhánh `dev` only; không worktree, không push, không PR; không commit `.idea`.
- Spec: `docs/superpowers/specs/2026-11-14-front-02-social-feeds-design.md` (commit `b3ed0549`). Task gốc `doc/task/todo/FRONT-02_decentralized-web3-social.md`.
- **Phạm vi:** chỉ Mastodon và Bluesky, chỉ tài khoản công khai, chỉ đọc. **Nostr KHÔNG làm.** Không hashtag, không danh sách, không `with_replies`/`media`.
- **Không** đăng nhập, không lưu mật khẩu hay khóa, không dependency mới, không Room, không DataStore, không manifest, **không chuỗi mới** (nên không phải sửa 38 locale), không Gemini, không quảng cáo/mua bán/tiền.
- **Bắt buộc `@` hoặc link hồ sơ:** gõ trần `bsky.app` (không `@`) **không** được nhận là Bluesky. Email `a@b.com` (không `@` đầu) ⇒ không nhận.
- Hằng có tên (R5): `FALLBACK_TITLE_MAX_LENGTH = 80`, `ELLIPSIS = "…"`; không số cứng trong mã.
- Bộ nhận dạng chạy **trước** `formatUrl()` trong `SubscribeViewModel.search()` (nếu sau, `@bsky.app` thành `https://@bsky.app` và hỏng). Địa chỉ đoán không đọc được ⇒ **rơi về luồng cũ trên chữ gốc người dùng gõ**, không làm tệ hơn hiện nay.
- Log chỉ ghi **tên lớp** lỗi, không ghi URL, nội dung hay tên tài khoản.
- TDD thật: test đỏ vì **đúng lý do** (đọc từ XML **sau khi** `.exit` tồn tại), code tối thiểu, xanh, mutation có backup + `cmp`, commit nhỏ sau khi kiểm exit code.
- Gradle chạy **nền**, đầu ra ra tệp, chờ `.exit`/`ALLDONE`; **thông báo "completed" của lệnh nền chỉ là lệnh khởi chạy, không phải kết quả**; không đọc XML cũ; sau mỗi đợt mutation kiểm tệp đã khôi phục bằng `cmp`.
- Môi trường shell: zsh nội suy `$` trong nháy kép (dùng nháy đơn); không `echo ====`; `grep -r` đặt mẫu trong nháy và **không** dùng `--include` (ugrep): dùng `grep -rn 'mẫu' thư_mục`; `sed -i` macOS cần `''` nên **sửa tệp bằng python**; `org.json` và `android.util.Log` cần Robolectric trên JVM; test nguồn chỉ xét mã (bỏ comment và `import`); mỗi test Compose **một** `setContent`; công cụ gộp nhiều dòng đầu ra nên in kết quả gọn một dòng khi cần.
- Mutation: dùng `/tmp/s01_mut.sh <nhãn> <tệp> '<cũ>' '<mới>' <FQCN>...` (đã có, chạy `testDevDebugUnitTest`, luôn khôi phục, in `restored`/`NOT-RESTORED`/`PATTERN-MISSING`). Chạy cả đợt bằng **một script nền** và **không** sửa các tệp đó cho tới khi thấy `ALLDONE`. Nếu `/tmp` bị dọn: dựng lại script (xem `docs` STRAT-01 Task 10 hoặc mẫu trong lịch sử) trước khi bắt đầu.
- **Thiết bị: CHỈ Tecno KJ7 `115333744A005844`** (owner: "chỉ dùng tecno cho mọi phiên"); **không chạm Pixel** `2B051FDH3006MU` hay máy khác. Cổng thiết bị chỉ đọc trước (`adb devices`, `dumpsys activity` tìm instrumentation và `topResumedActivity`, `ps`); dừng và hỏi nếu màn hình khóa, có hộp sinh trắc, hoặc app lạ ở foreground. Sao lưu byte-exact bằng `python3 /tmp/o03p-backup.py` và khôi phục bằng `python3 /tmp/o03p-restore.py` (đã trỏ KJ7, ghi bằng đường dẫn tuyệt đối và đưa tệp qua stdin); kiểm băm DataStore sau cùng. Không `connected*` (dùng `adb -s … shell am instrument` với `timeout`); chỉ runner `com.mckimquyen.reader.test`; chỉ force-stop `com.mckimquyen.reader`; `run-as` không `--user`; không xóa DataStore.
- **KHÔNG bấm hay vuốt theo tọa độ** (memory `never-tap-by-stale-coordinates`): dùng `am instrument`, intent, đọc DB. Quảng cáo toàn màn hình khi mở app: chỉ **Back**.
- Test mạng thật (Mastodon/Bluesky) là **helper tạm không commit** (mạng đổi theo ngày); ghi rõ trong báo cáo.
- R2: cập nhật `doc/feature.md`. Task gốc: hỏi owner (AskUserQuestion) giữ trong `todo/` hay chuyển `done/`.

---

## Cấu trúc file

| Tệp | Việc | Task |
|---|---|---|
| `app/src/main/java/com/mckimquyen/reader/infrastructure/social/SocialFeedUrlResolver.kt` (mới) | hàm thuần: chữ người dùng gõ → địa chỉ RSS `https` hoặc `null` | 1 |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssDescriptionParser.kt` | `titleFromBody(html, max)` thuần | 2 |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt` | tiêu đề thay thế ở `article`; lưới an toàn từng bài ở `queryRssXml` | 3 |
| `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` | gọi bộ nhận dạng trước `formatUrl`, rơi về luồng cũ | 4 |
| `app/src/androidTest/.../SocialFeedsNetworkProbe.kt` (tạm, không commit), báo cáo, docs | máy thật Tecno | 5 |

**Thứ tự thực thi:** 1 → 2 → 3 → 4 → 5. Task 1 và 2 độc lập (thuần); Task 3 cần 2; Task 4 cần 1.

---

### Task 1: Bộ nhận dạng thuần `SocialFeedUrlResolver`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/SocialFeedUrlResolver.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/SocialFeedUrlResolverTest.kt`

**Interfaces:**
- Consumes: không (chỉ `java.net.URI` và Kotlin chuẩn; **không** dùng `okhttp3` để giữ nó chạy được trên JVM thuần).
- Produces: `object SocialFeedUrlResolver { fun resolve(input: String): String? }` (trả địa chỉ RSS `https` hoặc `null`).

Quy tắc (kiểm theo thứ tự, mỗi nhánh trả ngay):
1. `input.trim()`; rỗng ⇒ `null`. Có khoảng trắng bên trong ⇒ `null`.
2. **Mastodon `@tên@máy_chủ`**: bắt đầu `@`, đúng hai dấu `@`, tên khớp `[A-Za-z0-9_.-]+`, máy chủ là tên miền hợp lệ (xem dưới) ⇒ `https://<máy_chủ chữ thường>/@<tên>.rss`.
3. **Link `https://...`** (chỉ `https`, không `http`): parse bằng `java.net.URI`; từ chối nếu có `userInfo` hoặc cổng khác mặc định; `host` chữ thường:
   - `host == "bsky.app"`: đường dẫn là `/profile/<id>` hoặc `/profile/<id>/rss` (bỏ `/` cuối) với `<id>` là handle hợp lệ hoặc DID (`did:plc:` hoặc `did:web:` kèm `[A-Za-z0-9._:-]+`) ⇒ `https://bsky.app/profile/<id>/rss`.
   - Ngược lại, nếu `host` **không** thuộc danh sách loại trừ và đường dẫn đúng **một** đoạn có dạng `@<tên>` (tên khớp `[A-Za-z0-9_.-]+`; bỏ `/` cuối; bỏ qua `?` và `#`) ⇒ `https://<host>/@<tên>.rss`.
   - Mọi dạng khác ⇒ `null`.
4. **Bluesky `@tên.miền`**: bắt đầu `@`, đúng một dấu `@`, phần sau là handle hợp lệ (xem dưới) ⇒ `https://bsky.app/profile/<handle chữ thường>/rss`.
5. Còn lại ⇒ `null`.

- *Tên miền/handle hợp lệ:* các nhãn cách nhau bằng `.`, mỗi nhãn `[A-Za-z0-9-]+` không bắt đầu/kết thúc bằng `-`, có **ít nhất hai nhãn** (phải có dấu chấm), tổng ≤ 253 ký tự.
- *Danh sách loại trừ (`NOT_MASTODON_HOSTS`):* `youtube.com`, `youtu.be`, `medium.com`, `substack.com`, `tiktok.com`, `instagram.com`, `x.com`, `twitter.com`, `threads.net`, `bsky.app` (xử lý riêng ở nhánh Bluesky), kể cả **tên miền con** (`xxx.medium.com`). So sánh `host == d || host.endsWith("." + d)`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFeedUrlResolverTest {

    private fun ok(input: String, expected: String) = assertEquals(input, expected, SocialFeedUrlResolver.resolve(input))
    private fun no(input: String) = assertNull("'$input' must not be recognised", SocialFeedUrlResolver.resolve(input))

    // --- Mastodon, @name@server
    @Test fun mastodonHandle() = ok("@Mastodon@mastodon.social", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonHandle_serverIsLowerCased() = ok("@user@Mastodon.Social", "https://mastodon.social/@user.rss")
    @Test fun mastodonHandle_nameKeepsItsCase() = ok("@MixedCase@example.org", "https://example.org/@MixedCase.rss")
    @Test fun mastodonHandle_dotsAndDashesInName() = ok("@a.b-c_d@fosstodon.org", "https://fosstodon.org/@a.b-c_d.rss")
    @Test fun mastodonHandle_surroundingSpacesAreTrimmed() = ok("  @Mastodon@mastodon.social \n", "https://mastodon.social/@Mastodon.rss")

    // --- Mastodon, profile link
    @Test fun mastodonLink() = ok("https://mastodon.social/@Mastodon", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_trailingSlash() = ok("https://mastodon.social/@Mastodon/", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_queryAndFragmentAreIgnored() = ok("https://mastodon.social/@Mastodon?foo=1#top", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_uppercaseHost() = ok("https://Mastodon.Social/@Mastodon", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_anyOtherServer() = ok("https://fosstodon.org/@someone", "https://fosstodon.org/@someone.rss")

    // --- Bluesky, @handle
    @Test fun blueskyHandle() = ok("@bsky.app", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyHandle_withBskySocial() = ok("@alice.bsky.social", "https://bsky.app/profile/alice.bsky.social/rss")
    @Test fun blueskyHandle_isLowerCased() = ok("@Alice.BSKY.Social", "https://bsky.app/profile/alice.bsky.social/rss")
    @Test fun blueskyHandle_customDomain() = ok("@benharri.org", "https://bsky.app/profile/benharri.org/rss")

    // --- Bluesky, profile link
    @Test fun blueskyLink() = ok("https://bsky.app/profile/bsky.app", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_trailingSlash() = ok("https://bsky.app/profile/bsky.app/", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_alreadyRss() = ok("https://bsky.app/profile/bsky.app/rss", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_didPlc() = ok("https://bsky.app/profile/did:plc:z72i7hdynmk6r22z27h6tvur", "https://bsky.app/profile/did:plc:z72i7hdynmk6r22z27h6tvur/rss")
    @Test fun blueskyLink_queryIgnored() = ok("https://bsky.app/profile/bsky.app?x=1", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_uppercaseHost() = ok("https://BSKY.APP/profile/bsky.app", "https://bsky.app/profile/bsky.app/rss")

    // --- things that must NOT be taken over (the old flow keeps them)
    @Test fun empty_and_blank() { no(""); no("   ") }
    @Test fun plainWordsAndSites() { no("hello"); no("example.com"); no("https://example.com"); no("https://example.com/feed.xml") }
    @Test fun bareBlueskyDomain_withoutAt_isNotBluesky() { no("bsky.app"); no("alice.bsky.social") }
    @Test fun email_isNotAHandle() { no("a@b.com"); no("someone@mastodon.social") }
    @Test fun atWithoutADot_isNotBluesky() { no("@abc"); no("@alice") }
    @Test fun atWithEmptyParts() { no("@"); no("@@"); no("@a@"); no("@@mastodon.social"); no("@user@") }
    @Test fun tooManyAts() { no("@a@b.com@c.com"); no("@a@@b.com") }
    @Test fun serverWithoutADot_isRejected() { no("@user@localhost"); no("https://localhost/@user") }
    @Test fun plainHttp_isRejected_neverDowngradeToHttp() { no("http://mastodon.social/@Mastodon"); no("http://bsky.app/profile/bsky.app") }
    @Test fun otherSchemes_areRejected() { no("ftp://mastodon.social/@Mastodon"); no("file:///@Mastodon"); no("javascript:alert(1)") }
    @Test fun credentialsInTheLink_areRejected() { no("https://user:pass@mastodon.social/@Mastodon"); no("https://user@bsky.app/profile/bsky.app") }
    @Test fun customPort_isRejected() { no("https://mastodon.social:8443/@Mastodon"); no("https://bsky.app:444/profile/bsky.app") }
    @Test fun extraPathSegments_areRejected() {
        no("https://mastodon.social/@Mastodon/117466985005297414")
        no("https://mastodon.social/@Mastodon/with_replies")
        no("https://bsky.app/profile/bsky.app/post/3n27yge3hys24")
        no("https://bsky.app/profile")
        no("https://bsky.app/")
    }
    @Test fun hashtags_andLists_areNotAccounts() { no("https://mastodon.social/tags/rss"); no("https://mastodon.social/@Mastodon/media") }
    @Test fun illegalCharactersInNames() {
        no("@bad name@mastodon.social"); no("@bad/name@mastodon.social"); no("@bad%20@mastodon.social")
        no("https://mastodon.social/@bad name"); no("https://mastodon.social/@bad%2Fname")
        no("@evil.com/path"); no("@evil.com?x=1"); no("@evil.com#x")
    }
    @Test fun invalidDomainLabels() { no("@user@-bad.example.com"); no("@user@bad-.example.com"); no("@user@a..b.com"); no("@.bsky.app"); no("@bsky.app.") }

    // --- other platforms that also use /@name and must keep their own handling
    @Test fun otherPlatformsThatUseAtPaths_areLeftAlone() {
        no("https://medium.com/@someone")
        no("https://someone.medium.com/@x")
        no("https://substack.com/@someone")
        no("https://www.youtube.com/@channel")
        no("https://youtu.be/@x")
        no("https://www.tiktok.com/@user")
        no("https://www.instagram.com/@user")
        no("https://x.com/@user")
        no("https://twitter.com/@user")
        no("https://www.threads.net/@user")
    }
    @Test fun excludedList_matchesWholeDomainsOnly_notLookalikes() {
        // "notmedium.com" is a different site and a possible Mastodon server; only the real suffix is excluded.
        ok("https://notmedium.com/@someone", "https://notmedium.com/@someone.rss")
        ok("https://example.com/@someone", "https://example.com/@someone.rss")
    }
    @Test fun excludedServer_inHandleForm_isAlsoLeftAlone() { no("@someone@medium.com"); no("@someone@youtube.com") }

    // --- safety of the result
    @Test fun theResultIsAlwaysHttps_andHasNoUserInfoOrQuery() {
        val inputs = listOf("@Mastodon@mastodon.social", "https://mastodon.social/@Mastodon?x=1#y", "@bsky.app", "https://bsky.app/profile/bsky.app?x=1")
        for (i in inputs) {
            val out = SocialFeedUrlResolver.resolve(i)!!
            assertTrue(out, out.startsWith("https://"))
            assertTrue(out, '?' !in out && '#' !in out && '@' !in out.removePrefix("https://").substringBefore('/'))
        }
    }
    @Test fun resolvingTheResultAgain_isStable_orNull() {
        // A result is an RSS address; feeding it back must not produce a different, wrong address.
        val rss = SocialFeedUrlResolver.resolve("@Mastodon@mastodon.social")!!
        assertNull(rss, SocialFeedUrlResolver.resolve(rss))   // the name "Mastodon.rss" ends in .rss, so it is not taken as a profile
        val b = SocialFeedUrlResolver.resolve("@bsky.app")!!
        assertEquals(b, SocialFeedUrlResolver.resolve(b))            // Bluesky RSS link is idempotent
    }
}
```

Ghi chú về hai ca cuối: một địa chỉ Mastodon đã có đuôi `.rss` (`…/@Mastodon.rss`) có tên là `Mastodon.rss`, vì dấu chấm hợp lệ trong tên; để không biến `…/@Mastodon.rss` thành `…/@Mastodon.rss.rss`, nhánh "link Mastodon" phải **từ chối** tên có đuôi `.rss` hoặc `.atom` (đã đúng trong mã ở Step 3); người dùng dán thẳng địa chỉ RSS đó sẽ đi luồng cũ (đã đọc được như một RSS thường).

- [ ] **Step 2: Chạy đỏ** (nền): `./gradlew :app:testDevDebugUnitTest --tests '*SocialFeedUrlResolverTest' > /tmp/f2-t1-red.log 2>&1; echo $? > /tmp/f2-t1.exit`; chờ `.exit`; đọc `grep -E '^e: '`: `Unresolved reference 'SocialFeedUrlResolver'`.

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.social

import java.net.URI

/**
 * Turns what a person pastes ("@name@server", "@name.bsky.social", or a profile link) into the public RSS address of a
 * Mastodon or Bluesky account (FRONT-02). Pure: no Android, no network. Anything it does not recognise returns null so
 * the existing add-a-source flow keeps handling it, and it only ever builds https addresses.
 */
object SocialFeedUrlResolver {
    private const val BLUESKY_HOST = "bsky.app"
    private const val MAX_DOMAIN_LENGTH = 253
    private const val MIN_DOMAIN_LABELS = 2
    private const val HTTPS = "https"
    private const val DEFAULT_PORT = -1

    /** Sites that also use "/@name" paths but are not Mastodon; they keep their own handling. */
    private val NOT_MASTODON_HOSTS = listOf(
        "youtube.com", "youtu.be", "medium.com", "substack.com", "tiktok.com",
        "instagram.com", "x.com", "twitter.com", "threads.net", "bsky.app",
    )
    private val NAME = Regex("^[A-Za-z0-9_.-]+$")
    private val LABEL = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$")
    private val DID = Regex("^did:(?:plc|web):[A-Za-z0-9._:-]+$")
    private val FEED_SUFFIXES = listOf(".rss", ".atom")

    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any(Char::isWhitespace)) return null
        return when {
            text.startsWith("@") -> fromHandle(text)
            text.startsWith("$HTTPS://") -> fromLink(text)
            else -> null
        }
    }

    private fun fromHandle(text: String): String? {
        val parts = text.removePrefix("@").split("@")
        return when (parts.size) {
            2 -> {
                val (name, server) = parts
                if (isName(name) && isDomain(server) && !isExcluded(server.lowercase())) {
                    "$HTTPS://${server.lowercase()}/@$name.rss"
                } else {
                    null
                }
            }
            1 -> parts[0].takeIf(::isDomain)?.let { "$HTTPS://$BLUESKY_HOST/profile/${it.lowercase()}/rss" }
            else -> null
        }
    }

    private fun fromLink(text: String): String? {
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (uri.userInfo != null || uri.port != DEFAULT_PORT) return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath.orEmpty().split("/").filter { it.isNotEmpty() }
        return if (host == BLUESKY_HOST) blueskyLink(segments) else mastodonLink(host, segments)
    }

    private fun blueskyLink(segments: List<String>): String? {
        val isProfile = segments.size == 2 || (segments.size == 3 && segments[2] == "rss")
        if (!isProfile || segments[0] != "profile") return null
        val id = segments[1]
        return when {
            // A DID is an identifier whose case matters, so it is kept exactly as given.
            DID.matches(id) -> "$HTTPS://$BLUESKY_HOST/profile/$id/rss"
            isDomain(id) -> "$HTTPS://$BLUESKY_HOST/profile/${id.lowercase()}/rss"
            else -> null
        }
    }

    private fun mastodonLink(host: String, segments: List<String>): String? {
        if (isExcluded(host) || !isDomain(host) || segments.size != 1) return null
        val first = segments[0]
        if (!first.startsWith("@")) return null
        val name = first.removePrefix("@")
        return if (isName(name)) "$HTTPS://$host/@$name.rss" else null
    }

    private fun isName(name: String): Boolean =
        NAME.matches(name) && FEED_SUFFIXES.none { name.endsWith(it, ignoreCase = true) }

    private fun isDomain(value: String): Boolean {
        if (value.length > MAX_DOMAIN_LENGTH) return false
        val labels = value.split(".")
        return labels.size >= MIN_DOMAIN_LABELS && labels.all(LABEL::matches)
    }

    private fun isExcluded(host: String): Boolean = NOT_MASTODON_HOSTS.any { host == it || host.endsWith(".$it") }
}
```

Ghi chú: `fromLink` đã **loại** `bsky.app` khỏi nhánh Mastodon vì `isExcluded` chứa `bsky.app`; nhánh Bluesky trả sớm trước đó nên đường dẫn hợp lệ không bao giờ rơi vào `mastodonLink`. DID **giữ nguyên chữ hoa/thường** (định danh phân biệt chữ hoa thường); handle thì chuyển chữ thường.

- [ ] **Step 4: Chạy xanh** (đọc XML sau `.exit`): toàn bộ lớp xanh. Nếu có ca đỏ, **đọc lý do**: sửa mã nếu mã sai, **sửa test chỉ khi test sai** (ghi lý do vào báo cáo).

- [ ] **Step 5: Mutation** (script nền, `ALLDONE`): bỏ `uri.userInfo != null` (đỏ ở ca có tài khoản); bỏ kiểm cổng (đỏ ở ca cổng); `"$HTTPS://"` → `"http://"` ở nhánh nhận link (đỏ ở ca `http`); bỏ `isExcluded` ở `mastodonLink` (đỏ ở Medium/YouTube…); bỏ `|| host.endsWith(".$it")` (đỏ ở tên miền con); `segments.size != 1` → `segments.size > 2` (đỏ ở đoạn thừa); bỏ `text.any(Char::isWhitespace)`; `MIN_DOMAIN_LABELS = 2` → `1` (đỏ ở `@abc` và `localhost`); bỏ `FEED_SUFFIXES.none` (đỏ ở ca idempotent); `server.lowercase()` → `server` (đỏ ở chữ hoa); bỏ `lowercase()` của handle Bluesky; bỏ `parts.size` nhánh `else -> null` (đỏ ở ba dấu `@`); đổi `LABEL` thành `[A-Za-z0-9-]+` (đỏ ở nhãn bắt đầu/kết thúc bằng `-`); đổi `NAME` cho phép `/` (đỏ ở ký tự lạ); `segments[0] != "profile"` → bỏ (đỏ ở ca `/profile` thiếu).
- [ ] **Step 6: Commit** (kiểm `exit=0`): `feat(front-02): add the pure rule that turns a Mastodon or Bluesky account into its RSS address`.

---

### Task 2: Tiêu đề thay thế thuần `RssDescriptionParser.titleFromBody`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssDescriptionParser.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssTitleFromBodyTest.kt`

**Interfaces:**
- Consumes: `RssDescriptionParser.plainText` (private, đã có: HTML → chữ thuần, gộp khoảng trắng).
- Produces: `internal fun RssDescriptionParser.titleFromBody(html: String?, maxLength: Int = FALLBACK_TITLE_MAX_LENGTH): String` và hằng `const val FALLBACK_TITLE_MAX_LENGTH = 80`, `const val ELLIPSIS = "…"` (trong `RssDescriptionParser`). Trả `""` nếu nội dung trống hoặc chỉ có thẻ/khoảng trắng.

Quy tắc:
- Lấy văn bản thuần (`plainText`); rỗng ⇒ `""`.
- Nếu độ dài tính theo **điểm mã Unicode** (`codePointCount`) ≤ `maxLength` ⇒ trả nguyên văn bản.
- Ngược lại cắt đúng `maxLength` điểm mã (**không cắt giữa cặp thay thế/emoji**), bỏ khoảng trắng cuối, thêm `ELLIPSIS`.
- `maxLength <= 0` ⇒ `""` (không ném lỗi).
- Dùng `plainText` chứ **không** dùng `shortDescription` (nó chạy Readability4J và cắt cứng 110 ký tự, không phù hợp).

Vì `plainText` dùng `android.text.Html` nên test chạy dưới Robolectric.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssTitleFromBodyTest {

    private fun title(html: String?, max: Int = RssDescriptionParser.FALLBACK_TITLE_MAX_LENGTH) =
        RssDescriptionParser.titleFromBody(html, max)

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(80, RssDescriptionParser.FALLBACK_TITLE_MAX_LENGTH)
        assertEquals("…", RssDescriptionParser.ELLIPSIS)
    }

    @Test fun nullAndBlank_giveAnEmptyTitle() {
        assertEquals("", title(null)); assertEquals("", title("")); assertEquals("", title("   \n\t "))
    }

    @Test fun onlyTagsAndSpaces_giveAnEmptyTitle() {
        assertEquals("", title("<p></p>")); assertEquals("", title("<p>  </p><br/>"))
    }

    @Test fun shortBody_isUsedAsIs() = assertEquals("Hello fediverse", title("<p>Hello fediverse</p>"))

    @Test fun htmlTagsAreRemoved() = assertEquals("Hello world", title("<p>Hello <strong>world</strong></p>"))

    @Test fun entitiesAreDecoded() = assertEquals("Tom & Jerry <3", title("<p>Tom &amp; Jerry &lt;3</p>"))

    @Test fun whitespaceIsCollapsed() = assertEquals("one two three", title("<p>one\n\n  two\t three</p>"))

    @Test fun exactlyTheLimit_isNotCut_andGetsNoEllipsis() {
        val body = "a".repeat(80)
        assertEquals(body, title(body))
    }

    @Test fun oneOverTheLimit_isCutAndGetsAnEllipsis() {
        val out = title("a".repeat(81))
        assertEquals("a".repeat(80) + "…", out)
    }

    @Test fun aLongBody_isCutToTheLimit() {
        val out = title("word ".repeat(100))
        assertTrue(out.endsWith("…"))
        assertTrue("the cut text is at most the limit plus the ellipsis: ${out.length}", out.codePointCount(0, out.length) <= 81)
    }

    @Test fun trailingSpaceBeforeTheCut_isNotLeftBeforeTheEllipsis() {
        val body = "a".repeat(79) + " " + "b".repeat(20)    // the 80th character is a space
        val out = title(body)
        assertEquals("a".repeat(79) + "…", out)
    }

    @Test fun anEmojiAtTheBoundary_isNeverSplit() {
        val body = "a".repeat(79) + "😀" + "b".repeat(10)    // the emoji is the 80th code point (two UTF-16 chars)
        val out = title(body)
        assertEquals("a".repeat(79) + "😀…", out)
        assertTrue("no lone surrogate may remain", out.none { Character.isHighSurrogate(it) && !out.contains("😀") })
    }

    @Test fun anEmojiJustPastTheBoundary_isDroppedWhole() {
        val body = "a".repeat(80) + "😀"
        assertEquals("a".repeat(80) + "…", title(body))
    }

    @Test fun lengthIsCountedInCodePoints_notUtf16Units() {
        val body = "😀".repeat(80)        // 160 UTF-16 chars but only 80 code points: fits, so no cut
        assertEquals(body, title(body))
    }

    @Test fun vietnameseTextKeepsItsAccents() = assertEquals("Chào buổi sáng, đây là bản tin", title("<p>Chào buổi sáng, đây là bản tin</p>"))

    @Test fun aCustomLimit_isHonoured() = assertEquals("abc…", title("abcdef", 3))

    @Test fun aLimitOfZeroOrLess_givesAnEmptyTitle_notACrash() {
        assertEquals("", title("abc", 0)); assertEquals("", title("abc", -5))
    }
}
```

- [ ] **Step 2: Chạy đỏ** (nền, chờ `.exit`): lỗi biên dịch `Unresolved reference 'titleFromBody'`, `FALLBACK_TITLE_MAX_LENGTH`, `ELLIPSIS`.

- [ ] **Step 3: Code tối thiểu** (trong `RssDescriptionParser`, cạnh `shortDescription`):

```kotlin
    const val FALLBACK_TITLE_MAX_LENGTH = 80
    const val ELLIPSIS = "…"

    /**
     * A title for an entry that has none (Mastodon and Bluesky posts never do): the first words of the body as plain
     * text. Counted in Unicode code points so an emoji at the cut is kept or dropped whole; empty when there is no text.
     */
    fun titleFromBody(html: String?, maxLength: Int = FALLBACK_TITLE_MAX_LENGTH): String {
        if (maxLength <= 0) return ""
        val source = html.orEmpty()
        if (source.isBlank()) return ""
        val text = runCatching { plainText(source) }.getOrNull().orEmpty()
        if (text.isBlank()) return ""
        if (text.codePointCount(0, text.length) <= maxLength) return text
        val end = text.offsetByCodePoints(0, maxLength)
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }
```

(Mục tiêu: **không** đổi `shortDescription` hay hằng `MAX_SHORT_DESCRIPTION_LENGTH`.)

- [ ] **Step 4: Chạy xanh.** Nếu `anEmojiAtTheBoundary_isNeverSplit` đỏ vì kiểm tra "không còn dấu thay thế lẻ" viết quá lỏng, **siết test** (kiểm `out` không chứa `\uD83D` đứng một mình bằng cách duyệt từng ký tự và so cặp), không nới mã.
- [ ] **Step 5: Mutation:** `maxLength <= 0` → `< 0` (đỏ ở ca 0); `codePointCount(...) <= maxLength` → `<` (đỏ ở "đúng giới hạn"); `offsetByCodePoints(0, maxLength)` → `maxLength` thẳng (đỏ ở emoji biên); bỏ `.trimEnd()` (đỏ ở ca dấu cách); `ELLIPSIS = "…"` → `"..."` (đỏ); `FALLBACK_TITLE_MAX_LENGTH = 80` → `100`; bỏ `if (text.isBlank()) return ""` (đỏ ở ca chỉ thẻ); đổi `plainText` sang `source` thô (đỏ ở ca bỏ thẻ).
- [ ] **Step 6: Commit:** `feat(front-02): add the pure rule that makes a title from the first words of a body`.

---

### Task 3: `RssHelper` không bỏ bài thiếu tiêu đề, và không để một bài hỏng làm rỗng cả nguồn

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt` (`article`, `queryRssXml`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperNoTitleTest.kt`

**Interfaces:**
- Consumes: `RssDescriptionParser.titleFromBody` (Task 2).
- Produces: hành vi: `article(...)` luôn có tiêu đề khi có nội dung; `queryRssXml` trả mọi bài hợp lệ kể cả khi một bài hỏng.

Quy tắc ở `article`:
- Tiêu đề thô = `syndEntry.title`. Nếu **null hoặc trống/khoảng trắng** ⇒ thử `RssDescriptionParser.titleFromBody(cleanRaw)` (nội dung đã làm sạch); nếu vẫn rỗng ⇒ **ném lỗi** (`IllegalArgumentException`, thông điệp không chứa nội dung) để bài đó bị bỏ như cũ.
- Tiêu đề có sẵn vẫn qua `Html.fromHtml(...)` như hiện nay; **không** bị thay.
- Chỉ ghi log **tên lớp** hoặc độ dài, không ghi nội dung; **không** in `title=` thô như dòng log hiện có khi tiêu đề null (dòng đó in `syndEntry.title`; đổi thành cờ `titleSource=feed|body`).

Quy tắc ở `queryRssXml`: bỏ `.map { article(...) }` thành `.mapNotNull { runCatching { article(...) }.onFailure { Log.w("RLog", "Skip malformed RSS entry errorType=${it.javaClass.simpleName}") }.getOrNull() }` (giống `searchFeed`); giữ `takeWhile` và `try/catch` ngoài; một bài hỏng chỉ làm mất bài đó. **Quan trọng:** một bài bị bỏ **không được** làm `takeWhile { latestLink != it.link }` đọc sai: `takeWhile` chạy trên danh sách bài gốc **trước** `mapNotNull`, nên thứ tự giữ nguyên.

- [ ] **Step 1: Viết test đỏ** (Robolectric + MockWebServer, khuôn `RssHelperDescriptionTest`)

```kotlin
package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RssHelperNoTitleTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        ShadowLog.clear()
        server = MockWebServer().apply { start() }
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    private fun feedOf(vararg items: String) = """<rss version="2.0"><channel><title>Social</title><link>https://example.test</link><description>d</description>${items.joinToString("")}</channel></rss>"""

    private fun enqueue(body: String) =
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(body))

    private fun item(link: String, title: String? = null, desc: String? = null) =
        "<item>" + (title?.let { "<title>$it</title>" } ?: "") + "<link>$link</link>" + (desc?.let { "<description>$it</description>" } ?: "") + "</item>"

    private fun url() = server.url("/feed").toString()
    private fun logText() = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg}" }

    // ---- searchFeed (the preview)
    @Test fun searchFeed_keepsAnEntryThatHasNoTitle_usingTheBody() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;Hello fediverse, this is a post&lt;/p&gt;")))
        val r = helper.searchFeed(url())
        assertEquals(1, r.articles.size)
        assertEquals("Hello fediverse, this is a post", r.articles[0].title)
    }

    @Test fun searchFeed_keepsTheFullBodyWhenOnlyTheTitleIsDerived() = runBlocking {
        val long = "word ".repeat(60)
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;$long&lt;/p&gt;")))
        val a = helper.searchFeed(url()).articles.single()
        assertTrue(a.title.endsWith("…"))
        assertTrue("the whole body stays in the article", a.rawDescription.length > a.title.length)
        assertTrue(a.rawDescription.contains("word word word"))
    }

    @Test fun searchFeed_anEntryWithATitle_isNotChanged() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", title = "Real title", desc = "&lt;p&gt;Body that is different&lt;/p&gt;")))
        assertEquals("Real title", helper.searchFeed(url()).articles.single().title)
    }

    @Test fun searchFeed_aBlankTitle_isTreatedAsMissing() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", title = "   ", desc = "&lt;p&gt;Body text here&lt;/p&gt;")))
        assertEquals("Body text here", helper.searchFeed(url()).articles.single().title)
    }

    @Test fun searchFeed_noTitleAndNoBody_dropsOnlyThatEntry() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/empty"),
            item("https://example.test/ok", desc = "&lt;p&gt;Has a body&lt;/p&gt;"),
        ))
        val r = helper.searchFeed(url())
        assertEquals(listOf("Has a body"), r.articles.map { it.title })
    }

    @Test fun searchFeed_mixedFeed_keepsEveryUsableEntryInOrder() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/1", desc = "&lt;p&gt;First post&lt;/p&gt;"),
            item("https://example.test/2", title = "Second", desc = "&lt;p&gt;x&lt;/p&gt;"),
            item("https://example.test/3", desc = "&lt;p&gt;Third post&lt;/p&gt;"),
        ))
        assertEquals(listOf("First post", "Second", "Third post"), helper.searchFeed(url()).articles.map { it.title })
    }

    // ---- queryRssXml (the sync)
    @Test fun sync_keepsEntriesThatHaveNoTitle() = runBlocking {
        enqueue(feedOf(item("https://example.test/1", desc = "&lt;p&gt;Sync post&lt;/p&gt;")))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals(listOf("Sync post"), list.map { it.title })
    }

    @Test fun sync_aMalformedEntry_losesOnlyItself() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/a", title = "A", desc = "ok"),
            item("https://example.test/b"),                                    // no title, no body: cannot become an article
            item("https://example.test/c", title = "C", desc = "ok"),
        ))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals("one bad entry must not empty the feed", listOf("A", "C"), list.map { it.title })
    }

    @Test fun sync_stopsAtTheLatestKnownLink_evenWhenAnEarlierEntryWasSkipped() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/new", title = "New", desc = "ok"),
            item("https://example.test/bad"),
            item("https://example.test/known", title = "Known", desc = "ok"),
            item("https://example.test/older", title = "Older", desc = "ok"),
        ))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), "https://example.test/known")
        assertEquals(listOf("New"), list.map { it.title })
    }

    @Test fun sync_aFeedThatCannotBeRead_stillGivesAnEmptyList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val list = helper.queryRssXml(Feed(id = "f", name = "Social", url = url(), groupId = "g", accountId = 1), null)
        assertEquals(emptyList<Any>(), list)
    }

    // ---- logging must not leak
    @Test fun theLog_neverContainsTheBodyOrTheAccount() = runBlocking {
        enqueue(feedOf(
            item("https://example.test/@SecretAccount/1", desc = "&lt;p&gt;PRIVATE_POST_TEXT_12345&lt;/p&gt;"),
            item("https://example.test/@SecretAccount/2"),
        ))
        helper.searchFeed(url())
        val log = logText()
        assertFalse("post text leaked into the log", log.contains("PRIVATE_POST_TEXT_12345"))
        assertFalse("account leaked into the log", log.contains("SecretAccount/2"))
    }
}
```

Ghi chú: ca cuối chỉ khẳng định **không lộ**; nếu mã hiện có đã ghi `entryUrl=` đã qua `safeEntryUrl` (có đường dẫn nhưng không có truy vấn), thì `SecretAccount/1` có thể xuất hiện ở dòng log có sẵn: **chỉ** khẳng định về **nội dung bài** (`PRIVATE_POST_TEXT_12345`) và về dòng log **mới** của bài bị bỏ (không có tên tài khoản). Đo bằng cách chạy test; nếu dòng log cũ chứa đường dẫn (đã tồn tại trước task này), **đổi khẳng định về `SecretAccount/2` thành dòng "Skip malformed" không chứa URL**, và ghi nhận trong báo cáo, không sửa `safeEntryUrl`.

- [ ] **Step 2: Chạy đỏ.** Lý do mong đợi: các ca `searchFeed_keepsAnEntryThatHasNoTitle...` và `sync_keepsEntriesThatHaveNoTitle` đỏ vì bài bị bỏ (`entries=0` hoặc thiếu), `sync_aMalformedEntry_losesOnlyItself` đỏ vì danh sách rỗng. **Đọc đúng thông điệp lỗi từ XML** trước khi viết mã.

- [ ] **Step 3: Code tối thiểu** trong `RssHelper.kt`:

`article(...)`: thay khối `title = if (SDK_INT >= N) {...}` bằng:

```kotlin
        val feedTitle = syndEntry.title?.takeIf { it.isNotBlank() }
        // Mastodon and Bluesky posts have no title at all; the first words of the body stand in for it.
        val title = feedTitle ?: RssDescriptionParser.titleFromBody(cleanRaw).takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("RSS entry has neither a title nor a body")
```

và `title = if (feedTitle != null) { <khối Html.fromHtml cũ dùng feedTitle> } else title,` (giữ phép `Html.fromHtml` cho tiêu đề có sẵn, **không** chạy lại cho tiêu đề tự dựng vì nó đã là chữ thuần).

Đổi dòng log: bỏ `title=${syndEntry.title}`, thay bằng `titleSource=${if (feedTitle != null) "feed" else "body"}`.

`queryRssXml`: 

```kotlin
                    .takeWhile { latestLink == null || latestLink != it.link }
                    .mapNotNull { entry ->
                        runCatching { article(feed, accountId, entry) }
                            .onFailure { Log.w("RLog", "Skip malformed RSS entry errorType=${it.javaClass.simpleName}") }
                            .getOrNull()
                    }
                    .toList()
```

- [ ] **Step 4: Chạy xanh** + hồi quy toàn bộ `RssHelper*Test` hiện có (`Audio`, `Description`, `Favicon`, `FullContent`, `Sanitizer`) và `LocalRssSv*`, `AbstractRssRepository*` liên quan; **tất cả xanh**.
- [ ] **Step 5: Mutation:** bỏ `?.takeIf { it.isNotBlank() }` (đỏ ở tiêu đề trống); đổi `titleFromBody(cleanRaw)` thành `titleFromBody(rawSource)` (đỏ nếu `cleanRaw` đã được làm sạch khác: nếu sống sót, xem lại có test tương đương); bỏ `throw IllegalArgumentException(...)` thành tiêu đề `""` (đỏ ở "trống cả hai thì bỏ"); `.mapNotNull { runCatching ...}` → `.map { article(...) }` (đỏ ở "một bài hỏng chỉ mất chính nó"); đảo thứ tự `takeWhile` và `mapNotNull` (đỏ ở "dừng ở link đã biết"); bỏ `.onFailure` log (không đỏ nếu không có test log: giữ test log cho dòng này); in lại `body` trong dòng log (đỏ ở "log không lộ"); `feedTitle != null` → `true` ở đoạn `Html.fromHtml` (đỏ hoặc NPE: ca không tiêu đề).
- [ ] **Step 6: Commit:** `fix(front-02): keep RSS entries that have no title, and stop one bad entry from emptying a synced feed`.

---

### Task 4: Cắm bộ nhận dạng vào ô Thêm nguồn (`SubscribeViewModel.search`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` (`search()`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelSocialTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelSocialSourceTest.kt`

**Interfaces:**
- Consumes: `SocialFeedUrlResolver.resolve(input): String?` (Task 1); `rssHelper.searchFeed(link)`, `rssService.get().isFeedExist(url)`, `newsletterUrlResolver.resolveToRssUrl(link)`, `webFeedScraper.discover(link)` (đã có, giữ nguyên).
- Produces: hành vi của `search()`:
  1. Lấy `typed = linkContent` (chữ **gốc** người dùng gõ).
  2. `socialUrl = SocialFeedUrlResolver.resolve(typed)` **trước** `formatUrl()`. Nếu có thì `linkContent` được thay bằng `socialUrl` (hộp nhập hiển thị địa chỉ RSS), rồi **bỏ qua `formatUrl()`** (địa chỉ đã là `https`).
  3. `isFeedExist(linkContent)` dùng địa chỉ hiện tại (đúng địa chỉ RSS), báo "đã đăng ký" như cũ.
  4. `searchFeed(link)`; nếu thất bại và đã dùng `socialUrl` ⇒ **quay về luồng cũ trên `typed`**: `formatUrl(typed)` rồi `searchFeed`, rồi `newsletterUrlResolver`, rồi `webFeedScraper` như hiện nay. Nếu không dùng `socialUrl` thì giữ nguyên toàn bộ luồng hiện có.
  5. Không thêm trạng thái, không thêm chuỗi.

> **Thận trọng:** không đoán thân hàm; **đọc `search()` ngay trước khi sửa** (dòng ~114 đến ~245) và giữ nguyên mọi nhánh còn lại (`isFeedExist`, `newsletterUrlResolver`, `webFeedScraper`, xử lý lỗi, `lockLinkInput`, `title = searching`). Sửa **tối thiểu** ở đầu hàm và ở chỗ chọn `link`.

- [ ] **Step 1: Viết test đỏ — hành vi** (khuôn `SubscribeViewModelNewsletterTest`: Robolectric, `StandardTestDispatcher`, MockK, `DataStore` đặt `CurrentAccountType = Local`)

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelSocialTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        viewModel = SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rssService, rssHelper = rssHelper,
            androidStringsHelper = strings, webFeedScraper = scraper, newsletterUrlResolver = resolver,
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun feedAt(url: String, name: String = "Social") = FeedWithArticle(
        Feed(id = "feed_1", name = name, url = url, groupId = "group_1", accountId = 1),
        listOf(Article(id = "a1", date = Date(), title = "Hello", rawDescription = "", shortDescription = "", link = "$url/1", feedId = "feed_1", accountId = 1)),
    )

    @Test
    fun aMastodonHandle_isSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss, "Mastodon")

        viewModel.inputLink("@Mastodon@mastodon.social")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { resolver.resolveToRssUrl(any()) }
        assertEquals(rss, viewModel.subscribeUiState.value.feed?.url)
        assertEquals(1, viewModel.subscribeUiState.value.articles.size)
        assertFalse(viewModel.subscribeUiState.value.webPreview)
    }

    @Test
    fun aBlueskyHandle_isNotMangledByFormatUrl_andIsSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://bsky.app/profile/bsky.app/rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss, "@bsky.app - Bluesky")

        viewModel.inputLink("@bsky.app")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
        coVerify(exactly = 0) { rssHelper.searchFeed("https://@bsky.app") }
        assertEquals(rss, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun aBlueskyProfileLink_isSearchedAsItsRssAddress() = runTest(dispatcher) {
        val rss = "https://bsky.app/profile/bsky.app/rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss)

        viewModel.inputLink("https://bsky.app/profile/bsky.app")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(rss) }
    }

    @Test
    fun theAlreadySubscribedCheck_usesTheRssAddress() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { localService.isFeedExist(rss) } returns true

        viewModel.inputLink("@Mastodon@mastodon.social")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { localService.isFeedExist(rss) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test
    fun ifTheGuessedRssCannotBeRead_theOldFlowRunsOnWhatTheUserTyped() = runTest(dispatcher) {
        val typed = "https://mastodon.social/@Mastodon"
        val guessed = "https://mastodon.social/@Mastodon.rss"
        val resolved = "https://mastodon.social/@Mastodon/feed"
        coEvery { rssHelper.searchFeed(guessed) } throws IllegalStateException("blocked")
        coEvery { rssHelper.searchFeed(typed) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(typed) } returns resolved
        coEvery { rssHelper.searchFeed(resolved) } returns feedAt(resolved)

        viewModel.inputLink(typed)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(guessed) }
        coVerify(exactly = 1) { rssHelper.searchFeed(typed) }
        coVerify(exactly = 1) { resolver.resolveToRssUrl(typed) }
        assertEquals(resolved, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun anOrdinaryAddress_followsTheOldFlowUntouched() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        coEvery { rssHelper.searchFeed(url) } returns feedAt(url, "Example")

        viewModel.inputLink("example.com/feed.xml")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 1) { rssHelper.searchFeed(url) }
        assertEquals(url, viewModel.subscribeUiState.value.feed?.url)
    }

    @Test
    fun aMediumHandleStyleLink_stillGoesThroughTheNewsletterFlow() = runTest(dispatcher) {
        val typed = "https://medium.com/@someone"
        coEvery { rssHelper.searchFeed(typed) } throws IllegalStateException("not a feed")
        coEvery { resolver.resolveToRssUrl(typed) } returns "https://medium.com/feed/@someone"
        coEvery { rssHelper.searchFeed("https://medium.com/feed/@someone") } returns feedAt("https://medium.com/feed/@someone", "Someone")

        viewModel.inputLink(typed)
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 0) { rssHelper.searchFeed("https://medium.com/@someone.rss") }
        coVerify(exactly = 1) { resolver.resolveToRssUrl(typed) }
    }
}
```

Ghi chú: các ca dựa vào hành vi `inputLink` + `search()` + `subscribeUiState`; nếu `inputLink` có tên khác, **đọc `SubscribeViewModel`** rồi sửa **test** cho đúng tên. Khẳng định "không bị bóp méo bởi `formatUrl`" ở ca Bluesky là ca then chốt (nếu thứ tự sai thì `searchFeed("https://@bsky.app")` được gọi).

- [ ] **Step 2: Viết test nguồn đỏ** (chỉ xét mã không comment/import)

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubscribeViewModelSocialSourceTest {

    private val vm = File("src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val search = vm.substringAfter("fun search()").substringBefore("fun ")

    @Test fun theResolverRunsBeforeFormatUrl() {
        assertTrue(search.contains("SocialFeedUrlResolver.resolve("))
        assertTrue(search.contains("formatUrl()"))
        assertTrue("the resolver must run first", search.indexOf("SocialFeedUrlResolver.resolve(") < search.indexOf("formatUrl()"))
    }

    @Test fun theResolverIsCalledExactlyOnce() {
        assertEquals(1, Regex("""SocialFeedUrlResolver\.resolve\(""").findAll(vm).count())
    }

    @Test fun theOldFlowIsStillThere() {
        assertTrue(search.contains("newsletterUrlResolver.resolveToRssUrl("))
        assertTrue(search.contains("webFeedScraper.discover("))
        assertTrue(search.contains("rssService.get().isFeedExist("))
    }

    @Test fun noNetworkOrAccountDetailGoesIntoTheNewCode() {
        val region = search.substringBefore("rssService.get().isFeedExist(")
        listOf("Log.", "println", "printStackTrace").forEach { assertTrue("the new branch must not log: $it", !region.contains(it)) }
    }
}
```

- [ ] **Step 3: Chạy đỏ.** Lý do mong đợi: lỗi biên dịch `Unresolved reference 'SocialFeedUrlResolver'` ở phía mã (test nguồn đỏ vì chuỗi chưa có), và ca hành vi đỏ vì `searchFeed(rss)` không được gọi. **Đọc đúng lý do từ XML** (`.exit` đã tồn tại).

- [ ] **Step 4: Code tối thiểu.** Ở đầu thân `search()` trong `viewModelScope.launch`, **thay** khối `linkContent.formatUrl().let { ... }` bằng:

```kotlin
                // FRONT-02: a pasted Mastodon or Bluesky account becomes its public RSS address. This runs BEFORE
                // formatUrl(), which would turn "@bsky.app" into "https://@bsky.app".
                val typed = _subscribeUiState.value.linkContent
                val socialUrl = SocialFeedUrlResolver.resolve(typed)
                val normalised = socialUrl ?: typed.formatUrl()
                if (normalised != typed) {
                    _subscribeUiState.update { it.copy(linkContent = normalised) }
                }
```

(giữ nguyên nhánh `if (str != linkContent)` cũ cho đường không phải mạng xã hội: `normalised` đã tương đương.) Ở chỗ chọn `link` và gọi `searchFeed` (mã hiện có là `val link = ...linkContent`, rồi `searchFeed(link)`, rồi khối `newsletterUrlResolver`, rồi nhánh `webFeedScraper.discover(link)`): đổi `val link` thành `var link` và thêm **một** khối giữa lần `searchFeed` đầu và khối newsletter; **không đụng** khối newsletter và nhánh scraper, vì chúng đã dùng biến `link`:

```kotlin
                var link = _subscribeUiState.value.linkContent
                var feedWithArticle = runCatching { rssHelper.searchFeed(link) }.getOrNull()
                if (feedWithArticle == null && socialUrl != null) {
                    // The guessed RSS address could not be read (server blocks it, account gone, RSS switched off):
                    // carry on with exactly what the user typed, as if this feature did not exist. Every later step
                    // (newsletter resolver, web scraper) then works on that original address.
                    link = typed.formatUrl()
                    _subscribeUiState.update { it.copy(linkContent = link) }
                    feedWithArticle = runCatching { rssHelper.searchFeed(link) }.getOrNull()
                }
                if (feedWithArticle == null) {
                    // existing block, unchanged: newsletterUrlResolver.resolveToRssUrl(link) ...
                }
```

(Dòng comment cuối chỉ nhắc rằng khối có sẵn **giữ nguyên chữ**; khi sửa, **không xóa hay chép lại** khối đó, chỉ chèn khối `if (feedWithArticle == null && socialUrl != null)` phía trên nó.) Vì `link` giờ là `var`, kiểm biên dịch: các chỗ dùng `link` bên trong lambda (`Feed(... url = link ...)`) vẫn hợp lệ vì chỉ đọc một `String`.

- [ ] **Step 5: Chạy xanh** (cả hai lớp) và hồi quy `SubscribeViewModelWebFeedTest`, `SubscribeViewModelNewsletterTest` (**cả hai phải xanh**: chúng khóa luồng cũ).
- [ ] **Step 6: Mutation:** đặt `SocialFeedUrlResolver.resolve` **sau** `formatUrl()` (đỏ ở ca Bluesky và test nguồn); bỏ nhánh `if (feedWithArticle == null && socialUrl != null)` (đỏ ở ca "quay về luồng cũ"); đổi `original` thành `socialUrl` (đỏ ở cùng ca); `isFeedExist(linkContent)` dùng `typed` (đỏ ở ca "đã đăng ký"); bỏ gọi `resolver` ở nhánh Mastodon (đỏ nếu `exactly = 0` ở ca đầu bị phá); thêm `Log.i(..., typed)` vào nhánh mới (đỏ ở test nguồn "không log").
- [ ] **Step 7: Commit** (kiểm `exit=0`): `feat(front-02): turn a pasted Mastodon or Bluesky account into its RSS address when adding a source`.

---

### Task 5: Toàn bộ test, lint, build, máy thật Tecno, báo cáo

**Files:**
- Tạm (**không vào repo**): `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/social/SocialFeedsNetworkProbe.kt` (copy từ `/tmp/f2-androidtest/` **chỉ** khi chạy rồi xóa): gọi **mạng thật** qua `RssHelper.searchFeed` với địa chỉ do `SocialFeedUrlResolver` sinh từ `@Mastodon@mastodon.social` và `@bsky.app`; `Log.i("F2", ...)` số bài và 3 tiêu đề đầu; cũng gọi `queryRssXml` (đường đồng bộ) và in số bài. `RssHelper` dựng bằng `RssHelper(context, Dispatchers.IO, okHttpClient)` với `okHttpClient` mặc định của dự án (lấy từ Hilt EntryPoint hoặc dựng `OkHttpClient()` với `followRedirects(true)`; **ghi rõ trong báo cáo cái nào được dùng**).
- Báo cáo: `doc/task/done/FRONT-02_decentralized-web3-social_DONE.md`.

**Cổng thiết bị (chỉ đọc) trước mọi thao tác:**
1. `adb devices` (KJ7 `115333744A005844` phải có mặt; thiết bị khác **không chạm**).
2. `adb -s 115333744A005844 shell dumpsys activity activities | grep -E "Active instrumentation|topResumedActivity"`: không có instrumentation; foreground là launcher hoặc app đã biết; app lạ ở foreground ⇒ **dừng và hỏi**.
3. `adb -s ... shell ps -A | grep -iE "instrument|monkey|uiautomator"`: trống.
4. Màn hình mở khóa, không hộp sinh trắc; khóa hoặc có hộp thoại ⇒ nhờ owner xử lý, **không tự thao tác**.

- [ ] **Step 1: Full suite sạch** (nền): `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f2-full.log 2>&1`, chờ `.exit`/`ALLDONE`; đếm từ XML `tests/skipped/failures/errors` và tệp cũ (`find ... -mmin +180`). Kỳ vọng **0 lỗi** (nền STRAT-01: **3.352**).
- [ ] **Step 2: Lint** (nền): `./gradlew lintDevDebug`; đếm `severity` (nền **2.465** cảnh báo, 0 lỗi) và liệt kê vấn đề ở tệp FRONT-02 (kỳ vọng 0).
- [ ] **Step 3: Build** (nền): `./gradlew assembleDevDebug assembleDevDebugAndroidTest`, `exit=0`. Lược đồ không đổi: `git status --short app/schemas` rỗng; **không chuỗi mới**: `git diff --stat -- app/src/main/res` rỗng.
- [ ] **Step 4: Cổng thiết bị rồi sao lưu** (`python3 /tmp/o03p-backup.py`; ghi `integrity`, `user_version`, số bài; `shasum` DataStore). Cài APK dev + test APK: `adb -s 115333744A005844 install -r -t <apk>`.
- [ ] **Step 5: Probe mạng thật** (helper tạm) bằng `am instrument -e class ...SocialFeedsNetworkProbe`, **không bấm màn hình**; đọc kết quả bằng `adb logcat -d -s F2`. Kỳ vọng: Mastodon trả ≥1 bài với tiêu đề không rỗng; Bluesky trả ≥1 bài (qua chuyển hướng 302). **Nếu mạng/instance lỗi thì ghi nguyên trạng**, không sửa test cho khớp.
- [ ] **Step 6: Không chạm giao diện.** Ô Thêm nguồn trên màn hình thật **không** được kiểm trên máy (nói thẳng trong báo cáo): chỉ kiểm bằng test view model và probe mạng thật.
- [ ] **Step 7: Khôi phục byte-identical** (`python3 /tmp/o03p-restore.py`): Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; kiểm băm DataStore; gỡ test package (`adb -s 115333744A005844 uninstall com.mckimquyen.reader.test`); xóa helper tạm khỏi cây mã (`git status` sạch, **không** commit); `force-stop com.mckimquyen.reader`; cài lại bản HEAD sạch; `git diff --stat -- app/src/main` rỗng.
- [ ] **Step 8:** Nếu bất kỳ ca nào đỏ: sửa mã bằng TDD (viết test JVM tái hiện trước), không sửa test cho khớp. Ghi mọi phát hiện vào báo cáo.
- [ ] **Step 9: Báo cáo và tài liệu**
  - Create `doc/task/done/FRONT-02_decentralized-web3-social_DONE.md` (tiếng Việt, khuôn `STRAT-01_..._DONE.md`: Phạm vi nói thẳng, Đã làm, Kiểm chứng JVM, Kiểm chứng Tecno, Phát hiện, Chưa kiểm, Giới hạn, Commits). Chỉ số liệu thật. Nói thẳng: **Nostr chưa làm** (task gốc nêu ba mạng, làm hai); bài thiếu tiêu đề trước đây **bị bỏ** (đã sửa, áp cho mọi nguồn RSS); Bluesky chỉ chữ, vài bài; **ô Thêm nguồn chưa thử trên màn hình thật**.
  - Update `doc/feature.md` (FRONT-02 sang ✅ bản thu hẹp, nợ: Nostr, ảnh Bluesky, gõ thật trên màn hình, máy chủ Mastodon khác).
  - Update spec nếu thực thi lệch (mục "Điều chỉnh khi thực thi").
  - Task gốc `doc/task/todo/FRONT-02_*.md`: **hỏi owner bằng AskUserQuestion** giữ trong `todo/` + ghi chú đầu file, hay chuyển `done/`. Khuyến nghị giữ trong `todo/` (Nostr và WebSocket chưa làm).
  - Commit `docs(front-02): report the social feeds with their verified and unverified parts`. Không push, không PR, không `.idea`.

**Không kiểm được (ghi thẳng vào báo cáo):** gõ chữ thật vào ô Thêm nguồn trên màn hình; ảnh trong bài Bluesky (RSS không có); máy chủ Mastodon khác `mastodon.social`; tài khoản bị khóa hoặc đã xóa; Nostr; TalkBack; ngôn ngữ ngoài EN và VI.

---

## Self-review

**Spec coverage (từng mục của spec → task):**
- Nhận ra `@tên@máy_chủ`, link Mastodon, `@tên.miền`, link Bluesky, DID, địa chỉ đã là RSS; từ chối mọi dạng sai (không `@`, nhiều `@`, `@abc`, `http`, cổng, `user:pass@`, đoạn thừa, ký tự lạ, nhãn sai); loại trừ Medium, YouTube, Substack, TikTok, Instagram, X, Twitter, Threads (cả tên miền con) mà không loại nhầm `notmedium.com`; kết quả luôn `https`: **Task 1**.
- Gõ trần `bsky.app` không được nhận (đính chính spec): **Task 1** (ca `bareBlueskyDomain_withoutAt_isNotBluesky`).
- Bài thiếu tiêu đề lấy 80 ký tự đầu, bỏ HTML, cắt theo điểm mã Unicode (không vỡ emoji), thêm `…`, rỗng thì bỏ: **Task 2** (hàm thuần) và **Task 3** (nối vào `RssHelper.article`).
- Áp cho mọi nguồn RSS, nội dung đầy đủ giữ nguyên, tiêu đề có sẵn không bị thay: **Task 3**.
- Lưới an toàn từng bài ở đồng bộ, `takeWhile` vẫn đúng, log chỉ tên lớp: **Task 3**.
- Bộ nhận dạng chạy **trước** `formatUrl`, rơi về luồng cũ trên chữ gốc nếu địa chỉ đoán không đọc được, "đã đăng ký" dùng địa chỉ RSS, không làm hỏng luồng Medium/newsletter/web: **Task 4** (test hành vi + test nguồn + hai test hồi quy cũ).
- Không đăng nhập, không dependency mới, không Room/DataStore/manifest, **không chuỗi mới**: Global Constraints và **Task 5** Step 3.
- Kiểm trên máy thật chỉ Tecno KJ7, không bấm tọa độ, sao lưu/khôi phục byte-exact, probe mạng thật không commit, nói thẳng phần chưa kiểm: **Task 5**.
- Ngoài phạm vi (Nostr, hashtag, danh sách, ảnh Bluesky, đăng nhập): không có task; ghi vào báo cáo.

**Quét placeholder:** không còn "TBD"/"TODO"/"sau này". Ba chỗ có điều kiện đã kèm quyết định cụ thể: Task 2 Step 4 (siết test emoji nếu kiểm tra surrogate lỏng), Task 3 ghi chú log (đo trước khi khẳng định, không sửa `safeEntryUrl`), Task 4 ghi chú (đọc `search()` trước khi sửa; tên `inputLink` kiểm lại).

**Nhất quán kiểu và tên:** `SocialFeedUrlResolver.resolve(input): String?` (Task 1) dùng đúng ở Task 4; `RssDescriptionParser.titleFromBody(html, maxLength)`, `FALLBACK_TITLE_MAX_LENGTH`, `ELLIPSIS` (Task 2) dùng đúng ở Task 3; `RssHelper.searchFeed/queryRssXml/article` giữ chữ ký; `SubscribeViewModel.search()` giữ chữ ký; hằng 80 và `…` khớp spec.

**Rủi ro đã nêu:** Mastodon/Bluesky có thể tắt RSS hoặc chặn bot (nhánh quay về luồng cũ); cắt tiêu đề giữa câu; test mạng thật phụ thuộc ngày; tôi **chưa thể gõ vào ô trên màn hình thật** (bị cấm bấm tọa độ) nên phần giao diện chỉ kiểm bằng view model; `SubscribeViewModel.search()` đã dài, nên sửa nhỏ nhất có thể và giữ hai test cũ làm lưới an toàn; một sai lệch giữa test log (`SecretAccount`) và log hiện có (`entryUrl=` đường dẫn) cần đo trước khi khẳng định (ghi trong Task 3).
