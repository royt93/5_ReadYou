# FRONT-02g — Nguồn Bluesky đọc bằng API công khai riêng (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Dán `@handle` hoặc link `bsky.app/profile/…` vào ô **Thêm nguồn** thì nguồn đọc bằng API công khai của Bluesky (tên hiển thị, ảnh đại diện làm icon, tối đa 20 bài chính chủ) thay cho RSS; API lỗi thì quay về RSS cũ.

**Architecture:** Đi đúng đường Nostr (FRONT-02b/02c) đã đi: `BlueskyFeedMapper` (hàm thuần), `BlueskyApiClient` (OkHttp, hai GET, giới hạn kích thước), `Feed.BLUESKY_SOURCE_TYPE`, một nhánh trong `LocalRssSv.queryFeedArticles`, và một nhánh trong `SubscribeViewModel.search()`. Địa chỉ nguồn `bsky:<handle|did>`; không đổi Room.

**Tech Stack:** Kotlin 2.1, OkHttp 5.0.0-alpha.11, `org.json` (Robolectric trên JVM), MockWebServer3, JUnit4, MockK (chỉ JVM), Hilt `@Inject`.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** dependency, Room (giữ v22), DataStore, manifest, chuỗi mới (`app/src/main/res` không đổi), Gemini. **Không đụng quảng cáo, IAP, VIP**: owner hoãn Ad/IAP/VIP (2026-11-16), không gọi `AdManager`, không đọc/đổi `VipKeys`, `AdKeys`, `RApp.setupAdmob`.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; **Pixel `2B051FDH3006MU` CẤM đụng, kể cả đọc**. Không MockK trên máy; không tọa độ; **không chạy test thiết bị trên `MainActivity`** (quảng cáo xen kẽ che màn hình ~18 giây). Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (`gate.sh`, `backup.py`, `restore.py`, `loop.sh`, `dev_mut.sh`, `batch.sh`); **không để công cụ trong `/tmp`** (bị dọn).
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Đọc XML chỉ sau khi `.exit` tồn tại. Mutation: một script nền, **không sửa tệp đột biến tới `ALLDONE`**, `cmp` sau mỗi đợt, `git diff` phải sạch.
- zsh: không `echo ====`; `grep -r` phải quote mẫu và **không** dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i` cần `''`.
- Log **không** được ghi handle, tên, nội dung bài, địa chỉ ảnh. Không gửi gì ngoài `actor`, `limit`, `filter`.
- R2: cập nhật `doc/feature.md`. R5: không magic number (hằng số có tên), mỗi thay đổi có test, không báo "done" khi thiếu test.
- Nói thẳng phần không kiểm: hiển thị thật tên/icon trên màn hình, tải ảnh đại diện thật, giới hạn tốc độ của Bluesky, quảng cáo che `MainActivity`.

## Điều chỉnh so với spec (kiểm bằng mã và API thật, 2026-11-16)

1. **Không cần so `author.did` với DID hồ sơ để lọc trả lời:** đo trên API thật, trong mục **không có `reason`** (chính chủ) có **0** mục có `reply` và đúng **1** DID tác giả (23 mục của `jay.bsky.team`, 12 của `bsky.app`). Vẫn giữ kiểm `author.did == did hồ sơ` như **phòng thủ** (rẻ, chặn dữ liệu lạ), có test, nhưng ghi rõ nó chưa từng thấy kích hoạt.
2. **Đăng lại có trường `reply` ở cấp mục** (2 đến 5 mục), nhưng đã bị loại bởi `reason`. Bài chính chủ nào có `record.reply` cũng bị loại (phòng thủ nếu `filter` đổi hành vi).
3. **`uri` có dạng `at://<did>/app.bsky.feed.post/<rkey>`** (4 dấu `/`). Địa chỉ bài người đọc mở được: `https://bsky.app/profile/<did>/post/<rkey>`; dùng **DID** (không phải handle) vì handle có thể đổi và DID luôn khớp `author.did`.
4. **Hình dạng `embed` thật** (chỉ tên trường): `images#view {images[].alt,fullsize,thumb}`, `video#view {playlist,thumbnail}`, `external#view {external{title,uri,description,thumb}}`, `record#view {record{...}}`, `recordWithMedia#view {media,record}`. Plan chỉ đọc `$type`, và `external.uri` (HTTPS) cho một dòng chữ; **không** đọc `fullsize/thumb/playlist/thumbnail`.
5. **`SubscribeViewModel` đã dựng bằng `nostrRelayClient = mockk(relaxed = true)` ở 7 lớp test và `LocalRssSv` ở 4 lớp test** (đếm bằng grep). Thêm tham số constructor mới (`blueskyApiClient`) **phải kèm sửa 11 lớp test đó** trong cùng task, nếu không build test đỏ. Plan thêm tham số có **thứ tự cuối** và chỉ ở hai lớp thật; mỗi lớp test sửa bằng một dòng `blueskyApiClient = mockk(relaxed = true)`.
6. **Hai test hiện có của Bluesky RSS** (`SubscribeViewModelSocialTest.aBlueskyHandle_isNotMangledByFormatUrl_andIsSearchedAsItsRssAddress` và `aBlueskyProfileLink_isSearchedAsItsRssAddress`) mock `rssHelper.searchFeed(rss)` và mong `searchFeed` được gọi đúng 1 lần. Sau thay đổi, nhánh API chạy **trước**: với `blueskyApiClient = mockk(relaxed = true)` kết quả "không có bài" nên rơi về RSS và hai test **vẫn xanh**; plan phải kiểm điều đó chứ không cho là hiển nhiên (Task 4, Step 1).
7. **`OkHttpClient` của app đã bật `followRedirects(true)`** (`OkHttpClientModule.kt:76`). Chặn "chuyển hướng ra host lạ" phải làm **trong client Bluesky** (dựng client riêng bằng `client.newBuilder().followRedirects(false)`) hoặc kiểm host của `response.request.url` sau khi đọc; plan chọn `newBuilder().followRedirects(false).followSslRedirects(false)` và coi mọi mã 3xx là lỗi mạng.
8. **`/tmp/s01_mut.sh` không còn** (`/tmp` bị dọn). Task 1 Step 0 dựng lại một script đột biến JVM ở `/Users/loitran/.claude/tmp-f29/jvm_mut.sh` (không ở `/tmp`).
9. **Chạy thử trên máy thật chỉ bằng helper `androidTest` tạm, không commit**, gọi **API thật** qua `OkHttpClient` dựng như `OkHttpClientModule` (như helper FRONT-02c); **không** dùng `MainActivity`, không quảng cáo.

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt` | hàm thuần: tên nguồn, địa chỉ `bsky:`, chọn bài chính chủ, ánh xạ sang `Article`, icon, thoát HTML |
| Create `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFetchResult.kt` | `data class BlueskyFetchResult(posts, displayName, avatarUrl)` + `BlueskyOutcome` (Found, NotFound, Failed) |
| Create `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyApiClient.kt` | hai GET, giới hạn thời gian/kích thước, chặn chuyển hướng |
| Modify `app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt` | `BLUESKY_SOURCE_TYPE = "BLUESKY"` |
| Modify `.../domain/sv/LocalRssSv.kt` | tham số `blueskyApiClient`, nhánh `queryBlueskyArticles` |
| Modify `.../infrastructure/rss/RssHelper.kt`, `.../OfflinePrecacheService.kt` | bỏ qua BLUESKY như NOSTR |
| Modify `.../ui/page/home/feed/subs/SubscribeViewModel.kt` | tham số `blueskyApiClient`, nhánh API trong `search()`, `subscribe()` ép `isFullContent=false` |
| Modify 11 lớp test dựng `SubscribeViewModel`/`LocalRssSv` | thêm `blueskyApiClient = mockk(relaxed = true)` |
| Create tests dưới `app/src/test/.../social/bluesky/` | `BlueskyFeedMapperTest`, `BlueskyApiClientTest`, `BlueskyTestPayloads.kt` |
| Create `app/src/test/.../ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt`, `.../domain/sv/LocalRssSvBlueskySyncTest.kt` | nối ViewModel và đồng bộ nền |
| Tạm, **không commit**: `/Users/loitran/.claude/tmp-f29/jvm_mut.sh`, `bsky_*_batch.sh`, helper `androidTest` `BlueskyProbe` | mutation JVM và kiểm máy thật |
| Create `doc/task/done/FRONT-02g_bluesky-api_DONE.md`; modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec | tài liệu |

---

### Task 1: `BlueskyFeedMapper` (hàm thuần, chưa mạng)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFetchResult.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt`, `.../BlueskyTestPayloads.kt`

**Interfaces:**
- Consumes: `RssDescriptionParser.titleFromBody(html, maxLength)`, `RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH`, `spacerDollar` (`ui.ext`), `Article(...)`.
- Produces:
  - `object BlueskyFeedMapper { const val FEED_URL_PREFIX = "bsky:"; const val FEED_NAME_PREFIX = "Bluesky · "; const val MAX_NAME_CODE_POINTS = 40; const val MAX_POSTS_KEPT = 20; fun feedUrl(actor: String): String; fun feedName(actor: String, displayName: String?): String; fun cleanDisplayName(raw: String?): String?; fun iconUrl(avatar: String?): String?; fun toArticles(profileDid: String, items: JSONArray, feedId: String, accountId: Int, nowMillis: Long, newId: () -> String = …): List<Article> }`
  - `data class BlueskyFetchResult(val items: JSONArray, val did: String, val handle: String, val displayName: String?, val avatarUrl: String?)`
  - `sealed interface BlueskyOutcome { data class Found(val result: BlueskyFetchResult) : BlueskyOutcome; object NotFound : BlueskyOutcome; object Failed : BlueskyOutcome }`

- [ ] **Step 0: Dựng lại script đột biến JVM ngoài `/tmp`**

```bash
cat > /Users/loitran/.claude/tmp-f29/jvm_mut.sh <<'SH'
#!/bin/zsh
# usage: jvm_mut.sh <label> <file> <old> <new> <test FQCN>...   (backup, replace once, run, restore, cmp)
D=/Users/loitran/.claude/tmp-f29
label="$1"; file="$2"; old="$3"; new="$4"; shift 4
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
cp "$file" "$D/jvm_mut_backup.kt"
python3 - "$file" "$old" "$new" <<'PY'
import sys
f, o, n = sys.argv[1:4]
s = open(f, encoding="utf-8").read()
assert s.count(o) >= 1, "pattern not found: " + o
open(f, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
[ $? -eq 0 ] || { cp "$D/jvm_mut_backup.kt" "$file"; echo "$label: PATTERN-MISSING"; exit 2; }
args=()
for t in "$@"; do args+=(--tests "$t"); done
timeout 540 ./gradlew testDevDebugUnitTest "${args[@]}" > "$D/jvm-mut-$label.log" 2>&1
code=$?
cp "$D/jvm_mut_backup.kt" "$file"
cmp -s "$D/jvm_mut_backup.kt" "$file" && rest=restored || rest=NOT-RESTORED
fails=$(grep -cE " FAILED$" "$D/jvm-mut-$label.log")
echo "$label: gradle_exit=$code failed_tests=$fails $rest"
SH
chmod +x /Users/loitran/.claude/tmp-f29/jvm_mut.sh; ls -la /Users/loitran/.claude/tmp-f29/jvm_mut.sh | awk '{print $5, $9}'
```

- [ ] **Step 1: Đọc lại API thật trước khi chép**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "fun titleFromBody\|const val MAX_SHORT_DESCRIPTION_LENGTH\|FALLBACK_TITLE_MAX_LENGTH" $J/infrastructure/rss/RssDescriptionParser.kt
grep -n "fun Int.spacerDollar\|fun .*spacerDollar" -r $J/ui/ext | head -2
grep -n "class Article(" -A14 $J/domain/model/article/Article.kt | head -20
```
Expected: `titleFromBody(html: String?, maxLength: Int = FALLBACK_TITLE_MAX_LENGTH)`, `MAX_SHORT_DESCRIPTION_LENGTH = 110`; `Article` có `id, date, title, rawDescription, shortDescription, link, feedId, accountId`. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Dữ liệu mẫu (đo từ API thật, rút gọn, chữ giả không có nội dung thật)** `BlueskyTestPayloads.kt`

```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

/** Shapes measured on the real public API (2026-11-16), with invented text. Only keys the mapper reads are present. */
object BlueskyTestPayloads {
    const val DID = "did:plc:z72i7hdynmk6r22z27h6tvur"
    const val OTHER_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
    const val HANDLE = "alice.bsky.social"
    const val NOW = 1_800_000_000_000L

    fun post(
        rkey: String,
        text: String,
        createdAt: String = "2026-11-10T08:00:00.000Z",
        did: String = DID,
        embed: String? = null,
        recordExtra: String = "",
    ): String = """{"post":{"uri":"at://$did/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$did","handle":"$HANDLE"},""" +
        """"record":{"${'$'}type":"app.bsky.feed.post","text":${quote(text)},"createdAt":"$createdAt"$recordExtra}""" +
        (embed?.let { ""","embed":$it""" } ?: "") + "}}"

    fun repost(rkey: String, did: String = OTHER_DID): String =
        """{"post":{"uri":"at://$did/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$did","handle":"bob.bsky.social"},""" +
            """"record":{"text":"someone else","createdAt":"2026-11-10T08:00:00.000Z"}},""" +
            """"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost"}}"""

    const val EMBED_IMAGES = """{"${'$'}type":"app.bsky.embed.images#view","images":[{"alt":"a","fullsize":"https://cdn.bsky.app/x","thumb":"https://cdn.bsky.app/y"}]}"""
    const val EMBED_VIDEO = """{"${'$'}type":"app.bsky.embed.video#view","playlist":"https://video.bsky.app/p","thumbnail":"https://video.bsky.app/t"}"""
    const val EMBED_QUOTE = """{"${'$'}type":"app.bsky.embed.record#view","record":{"uri":"at://x/y/z"}}"""
    fun embedExternal(uri: String, title: String = "A page") =
        """{"${'$'}type":"app.bsky.embed.external#view","external":{"uri":${quote(uri)},"title":${quote(title)},"description":"d"}}"""

    fun array(vararg items: String): org.json.JSONArray = org.json.JSONArray("[" + items.joinToString(",") + "]")

    private fun quote(text: String): String = org.json.JSONObject.quote(text)
}
```

- [ ] **Step 3: Viết test đỏ** `BlueskyFeedMapperTest.kt`

```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.HANDLE
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.NOW
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.OTHER_DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.array
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.embedExternal
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.post
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.repost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyFeedMapperTest {
    private fun map(vararg items: String, did: String = DID, now: Long = NOW): List<Article> {
        val counter = AtomicInteger()
        return BlueskyFeedMapper.toArticles(did, array(*items), "feed-1", 7, now) { "u${counter.incrementAndGet()}" }
    }

    // ---- address and name
    @Test fun feedUrl_isLowercaseForAHandle_andKeepsTheCaseOfADid() {
        assertEquals("bsky:alice.bsky.social", BlueskyFeedMapper.feedUrl("Alice.Bsky.Social"))
        assertEquals("bsky:did:plc:AbC123", BlueskyFeedMapper.feedUrl("did:plc:AbC123"))
    }

    @Test fun feedName_usesTheDisplayNameBehindThePrefix() =
        assertEquals("Bluesky · Alice A", BlueskyFeedMapper.feedName("alice.bsky.social", "Alice A"))

    @Test fun feedName_withoutADisplayName_usesTheHandle() {
        assertEquals("Bluesky · @alice.bsky.social", BlueskyFeedMapper.feedName("alice.bsky.social", null))
        assertEquals("Bluesky · @alice.bsky.social", BlueskyFeedMapper.feedName("alice.bsky.social", ""))
    }

    @Test fun cleanDisplayName_dropsControlCharsAndCollapsesSpaces() {
        assertEquals("a b c", BlueskyFeedMapper.cleanDisplayName("  a\n\tb\u0007  c  "))
        assertEquals("x y", BlueskyFeedMapper.cleanDisplayName("x y"))
    }

    @Test fun cleanDisplayName_isNullWhenNothingUsableIsLeft() {
        assertNull(BlueskyFeedMapper.cleanDisplayName(null))
        assertNull(BlueskyFeedMapper.cleanDisplayName("  \n\u0007 "))
    }

    @Test fun cleanDisplayName_cutsAtFortyCodePoints_notInTheMiddleOfAnEmoji() {
        val emoji = "😀"
        assertEquals("a".repeat(39) + emoji, BlueskyFeedMapper.cleanDisplayName("a".repeat(39) + emoji + "zzz"))
        assertEquals("a".repeat(40), BlueskyFeedMapper.cleanDisplayName("a".repeat(45)))
    }

    // ---- icon
    @Test fun iconUrl_acceptsHttpsOnTheBlueskyHosts() {
        assertEquals("https://cdn.bsky.app/img/avatar/x@jpeg", BlueskyFeedMapper.iconUrl("https://cdn.bsky.app/img/avatar/x@jpeg"))
        assertEquals("https://a.bsky.social/p.png", BlueskyFeedMapper.iconUrl("https://a.bsky.social/p.png"))
    }

    @Test fun iconUrl_refusesOtherSchemesHostsAndLookalikes() {
        listOf(
            null, "", "http://cdn.bsky.app/x", "https://evil.example/x", "https://cdn.bsky.app.evil.example/x",
            "https://evilbsky.app/x", "https://user@cdn.bsky.app/x", "javascript:alert(1)", "https://cdn.bsky.app:8443/x",
        ).forEach { assertNull(it, BlueskyFeedMapper.iconUrl(it)) }
    }

    // ---- which posts
    @Test fun anOwnPost_becomesAnArticle_linkedByDidAndRkey() {
        val a = map(post("3k1", "Hello world")).single()
        assertEquals("Hello world", a.title)
        assertEquals("https://bsky.app/profile/$DID/post/3k1", a.link)
        assertEquals("feed-1", a.feedId)
        assertEquals(7, a.accountId)
    }

    @Test fun aRepost_isLeftOut() = assertTrue(map(repost("r1"), post("3k1", "mine")).map { it.title } == listOf("mine"))

    @Test fun aPostByAnotherDid_isLeftOut_evenWithoutAReason() =
        assertEquals(listOf("mine"), map(post("x1", "stranger", did = OTHER_DID), post("3k1", "mine")).map { it.title })

    @Test fun aPostThatIsAReply_isLeftOut() =
        assertEquals(listOf("mine"), map(post("r2", "reply", recordExtra = ""","reply":{"root":{"uri":"u"}}"""), post("3k1", "mine")).map { it.title })

    @Test fun anEmptyOrBlankPost_isLeftOut() =
        assertEquals(emptyList<String>(), map(post("e1", ""), post("e2", "   ")).map { it.title })

    @Test fun duplicates_arePostedOnce_newestFirst_andAtMostTwentyAreKept() {
        val many = (1..30).map { post("p$it", "post $it", createdAt = "2026-11-%02dT08:00:00.000Z".format(1 + it % 28)) }
        val out = map(*(many + post("p1", "post 1 again")).toTypedArray())
        assertEquals(20, out.size)
        assertEquals(out.map { it.date }.sortedDescending(), out.map { it.date })
        assertEquals(out.size, out.map { it.link }.distinct().size)
    }

    @Test fun aMalformedItem_isSkipped_notFatal() {
        val items = org.json.JSONArray("""[42, null, {"post":"x"}, {"post":{"uri":"u"}}, ${post("3k1", "ok")}]""")
        val out = BlueskyFeedMapper.toArticles(DID, items, "f", 1, NOW)
        assertEquals(listOf("ok"), out.map { it.title })
    }

    // ---- text and dates
    @Test fun theText_isEscapedBeforeItIsStoredAsHtml() {
        val a = map(post("h1", "<b>x</b> & \"y\" 'z'\nnext")).single()
        assertEquals("&lt;b&gt;x&lt;/b&gt; &amp; &quot;y&quot; &#39;z&#39;<br>next", a.rawDescription)
    }

    @Test fun theTitle_isTheFirstEightyCodePointsOfTheBody() {
        val a = map(post("t1", "a".repeat(100))).single()
        assertEquals("a".repeat(80) + "…", a.title)
    }

    @Test fun aPostDatedInTheFuture_isClampedToNow() {
        val a = map(post("f1", "future", createdAt = "2999-01-01T00:00:00.000Z")).single()
        assertEquals(NOW, a.date.time)
    }

    @Test fun anUnreadableDate_fallsBackToNow() {
        val a = map(post("d1", "no date", createdAt = "not a date")).single()
        assertEquals(NOW, a.date.time)
    }

    // ---- embeds: one line of text, nothing downloaded
    @Test fun anImageEmbed_addsOneLine_andNeverAnImageAddress() {
        val a = map(post("i1", "pic", embed = BlueskyTestPayloads.EMBED_IMAGES)).single()
        assertTrue(a.rawDescription.endsWith("<br>[image]"))
        assertFalse(a.rawDescription.contains("cdn.bsky.app"))
    }

    @Test fun aVideoAndAQuoteEmbed_addOneWordEach() {
        assertTrue(map(post("v1", "v", embed = BlueskyTestPayloads.EMBED_VIDEO)).single().rawDescription.endsWith("<br>[video]"))
        assertTrue(map(post("q1", "q", embed = BlueskyTestPayloads.EMBED_QUOTE)).single().rawDescription.endsWith("<br>[quoted post]"))
    }

    @Test fun anExternalLinkEmbed_showsTheAddress_escaped_andOnlyForHttps() {
        val ok = map(post("x1", "l", embed = embedExternal("https://example.test/a?b=1&c=2"))).single()
        assertTrue(ok.rawDescription, ok.rawDescription.endsWith("<br>[link] https://example.test/a?b=1&amp;c=2"))
        val bad = map(post("x2", "l", embed = embedExternal("javascript:alert(1)"))).single()
        assertTrue(bad.rawDescription, bad.rawDescription.endsWith("<br>[link]"))
        assertFalse(bad.rawDescription.contains("javascript"))
    }

    @Test fun aPostWithOnlyAnEmbed_andNoText_isLeftOut() =
        assertEquals(emptyList<String>(), map(post("o1", "", embed = BlueskyTestPayloads.EMBED_IMAGES)).map { it.title })
}
```

- [ ] **Step 4: Chạy đỏ (nền, đọc XML sau `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29
rm -f $D/f30-t1-red.exit
( ./gradlew testDevDebugUnitTest --tests '*BlueskyFeedMapperTest' > $D/f30-t1-red.log 2>&1; echo $? > $D/f30-t1-red.exit ) > /dev/null 2>&1 &
```
Chờ `f30-t1-red.exit`; exit phải khác 0; `grep -n "^e: " $D/f30-t1-red.log | head` phải báo **Unresolved reference: BlueskyFeedMapper** (thiếu hàm, không phải gõ nhầm). Nếu đỏ vì lý do khác (ví dụ `BlueskyTestPayloads` lỗi cú pháp): sửa test, chạy lại.

- [ ] **Step 5: Viết mã tối thiểu**

`BlueskyFetchResult.kt`:
```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import org.json.JSONArray

/** What one successful read of a Bluesky account found. The posts are still untrusted JSON; the mapper picks the usable ones. */
data class BlueskyFetchResult(
    val items: JSONArray,
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/** The three ways a read can end; "not found" is not a network failure and must not fall back to RSS. */
sealed interface BlueskyOutcome {
    data class Found(val result: BlueskyFetchResult) : BlueskyOutcome
    object NotFound : BlueskyOutcome
    object Failed : BlueskyOutcome
}
```

`BlueskyFeedMapper.kt`:
```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.ui.ext.spacerDollar
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Turns the public Bluesky API answer into articles. Everything in it is written by strangers: text is escaped before it is
 * stored as HTML, nothing is downloaded, and only the account's own posts are kept (no reposts, no replies).
 */
object BlueskyFeedMapper {
    const val FEED_URL_PREFIX = "bsky:"
    const val FEED_NAME_PREFIX = "Bluesky · "
    const val MAX_NAME_CODE_POINTS = 40
    const val MAX_POSTS_KEPT = 20
    private const val POST_LINK_BASE = "https://bsky.app/profile/"
    private const val HTTPS = "https"
    private const val DID_PREFIX = "did:"
    private const val BSKY_APP = "bsky.app"
    private const val BSKY_SOCIAL = "bsky.social"
    private const val DEFAULT_PORT = -1
    private const val EMBED_IMAGES = "app.bsky.embed.images#view"
    private const val EMBED_VIDEO = "app.bsky.embed.video#view"
    private const val EMBED_RECORD = "app.bsky.embed.record#view"
    private const val EMBED_RECORD_WITH_MEDIA = "app.bsky.embed.recordWithMedia#view"
    private const val EMBED_EXTERNAL = "app.bsky.embed.external#view"

    /** A handle is case-insensitive, a DID is not, so only a handle is lower-cased. */
    fun feedUrl(actor: String): String {
        val trimmed = actor.trim()
        return FEED_URL_PREFIX + if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)
    }

    fun feedName(actor: String, displayName: String?): String =
        FEED_NAME_PREFIX + (displayName?.takeIf { it.isNotEmpty() } ?: "@" + actor.trim().lowercase(Locale.ROOT))

    /** Same cleaning as a Nostr profile name: control characters and spaces become one space, at most 40 code points. */
    fun cleanDisplayName(raw: String?): String? {
        if (raw == null) return null
        val spaced = buildString(raw.length) {
            for (c in raw) {
                val blank = Character.getType(c) == Character.CONTROL.toInt() || Character.isSpaceChar(c)
                append(if (blank) ' ' else c)
            }
        }
        val cleaned = spaced.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        if (cleaned.isEmpty()) return null
        if (cleaned.codePointCount(0, cleaned.length) <= MAX_NAME_CODE_POINTS) return cleaned
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)).trimEnd()
    }

    /** The avatar address, only when it is plain https on a Bluesky host (the address is chosen by the account owner). */
    fun iconUrl(avatar: String?): String? {
        if (avatar.isNullOrEmpty()) return null
        val uri = runCatching { URI(avatar) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val onBluesky = host == BSKY_APP || host.endsWith(".$BSKY_APP") || host == BSKY_SOCIAL || host.endsWith(".$BSKY_SOCIAL")
        val plain = uri.scheme == HTTPS && uri.userInfo == null && uri.port == DEFAULT_PORT
        return avatar.takeIf { onBluesky && plain }
    }

    fun toArticles(
        profileDid: String,
        items: JSONArray,
        feedId: String,
        accountId: Int,
        nowMillis: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<Article> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<Long, Article>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.has("reason")) continue
            val post = item.optJSONObject("post") ?: continue
            val uri = post.optString("uri")
            val rkey = uri.substringAfterLast('/', "")
            if (rkey.isEmpty() || !seen.add(rkey)) continue
            if (post.optJSONObject("author")?.optString("did") != profileDid) continue
            val record = post.optJSONObject("record") ?: continue
            if (record.has("reply")) continue
            val text = record.optString("text")
            if (text.isBlank()) continue
            val html = escapeToHtml(text) + embedLine(post.optJSONObject("embed"))
            val title = RssDescriptionParser.titleFromBody(html)
            if (title.isEmpty()) continue
            val millis = minOf(parseMillis(record.optString("createdAt"), nowMillis), nowMillis)
            out += millis to Article(
                id = accountId.spacerDollar(newId()),
                date = Date(millis),
                title = title,
                rawDescription = html,
                shortDescription = title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                link = "$POST_LINK_BASE$profileDid/post/$rkey",
                feedId = feedId,
                accountId = accountId,
            )
        }
        return out.sortedByDescending { it.first }.take(MAX_POSTS_KEPT).map { it.second }
    }

    private fun parseMillis(text: String, fallback: Long): Long =
        runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(fallback)

    /** One short line, never an image address: nothing in a post is downloaded. */
    private fun embedLine(embed: JSONObject?): String {
        if (embed == null) return ""
        return when (embed.optString("\$type")) {
            EMBED_IMAGES -> "<br>[image]"
            EMBED_VIDEO -> "<br>[video]"
            EMBED_RECORD, EMBED_RECORD_WITH_MEDIA -> "<br>[quoted post]"
            EMBED_EXTERNAL -> {
                val link = embed.optJSONObject("external")?.optString("uri").orEmpty()
                if (link.startsWith("$HTTPS://")) "<br>[link] " + escapeToHtml(link) else "<br>[link]"
            }
            else -> ""
        }
    }

    private fun escapeToHtml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '\n' -> append("<br>")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }
}
```
Ghi chú: hàm **không** nhận handle (đã bỏ để khỏi có tham số không dùng); địa chỉ bài luôn dựng từ DID.

- [ ] **Step 6: Chạy xanh** (nền, như Step 4, `--tests '*BlueskyFeedMapperTest'`). Expected: exit 0, đọc XML (sau `.exit`): 22 test, 0 lỗi.

- [ ] **Step 7: Mutation (script nền duy nhất `/Users/loitran/.claude/tmp-f29/bsky_t1_batch.sh`, cuối `cmp` + `ALLDONE`)**

Tệp đột biến: `BlueskyFeedMapper.kt`; lớp test: `*BlueskyFeedMapperTest`. Đột biến: đảo thứ tự (bỏ `sortedByDescending`); `take(MAX_POSTS_KEPT)` thành `take(21)`; bỏ `seen.add` (trùng); bỏ `item.has("reason")`; bỏ kiểm `author.did`; bỏ `record.has("reply")`; bỏ `text.isBlank()`; `minOf(...)` thành giá trị thô (không kẹp tương lai); bỏ nhánh `else` ngày hỏng (`getOrDefault`); đổi `"[image]"` thành địa chỉ; bỏ `startsWith("https://")` ở embed external; bỏ một ký tự thoát HTML (`&`); `MAX_NAME_CODE_POINTS` 40→39 và 40→41; cắt bằng `take(40)` (vỡ emoji); bỏ `Character.isSpaceChar`; `iconUrl`: bỏ kiểm `scheme`, bỏ kiểm `userInfo`, bỏ kiểm `port`, đổi `endsWith(".bsky.app")` thành `contains("bsky.app")` (nhận `evilbsky.app` và `cdn.bsky.app.evil.example`); `feedUrl` bỏ nhánh DID (hạ chữ DID). Đột biến sống sót ⇒ thêm test (đúng tinh thần FRONT-02c); guard thừa không đổi hành vi ⇒ gỡ guard.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt \
  app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFetchResult.kt \
  app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt \
  app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyTestPayloads.kt
git commit -m "feat(front-02g): turn a Bluesky account's own posts into articles, never downloading anything in them

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Kiểm `git status --short | grep -v idea` sạch và `git diff --stat -- app/src/main/res app/schemas` rỗng trước khi commit.

---

### Task 2: `BlueskyApiClient` (hai GET, giới hạn, 400 khác lỗi mạng)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyApiClient.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyApiClientTest.kt`

**Interfaces:**
- Consumes: `BlueskyOutcome`, `BlueskyFetchResult` (Task 1); `OkHttpClient`; `@IODispatcher CoroutineDispatcher`.
- Produces: `@Singleton class BlueskyApiClient internal constructor(client: OkHttpClient, ioDispatcher: CoroutineDispatcher, baseUrl: String, timeoutMs: Long, maxBodyBytes: Long) { @Inject constructor(client: OkHttpClient, @IODispatcher ioDispatcher: CoroutineDispatcher); suspend fun fetch(actor: String): BlueskyOutcome; companion object { const val API_BASE = "https://public.api.bsky.app"; const val TIMEOUT_MS = 8_000L; const val MAX_BODY_BYTES = 512 * 1024L; const val POST_LIMIT = 50 } }`

- [ ] **Step 1: Đọc lại API thật trước khi chép**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 1,40p $J/infrastructure/di/CoroutineQualifier.kt | grep -n "IODispatcher"
grep -n "okhttp3.coroutines\|executeAsync\|\.await()" -r $J | head -3
grep -n "withTimeout\|TimeoutCancellationException" $J/infrastructure/social/nostr/NostrRelayClient.kt | head -3
```
Expected: `IODispatcher` là qualifier; không có `executeAsync` sẵn: dùng `Call.execute()` trong `withContext(ioDispatcher)` (chặn nhưng nằm trên IO) và `withTimeout`; hủy coroutine phải `call.cancel()` (dùng `invokeOnCompletion` hoặc `ensureActive` trước/sau). Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết test đỏ** `BlueskyApiClientTest.kt` (MockWebServer3 `http://127.0.0.1`; `baseUrl` trỏ vào server, nên cần hàm tạo `internal`)

```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import android.app.Application
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.HANDLE
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketPolicy
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().followRedirects(true).callTimeout(15, TimeUnit.SECONDS).build()
    }

    @After fun tearDown() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun client(timeoutMs: Long = 800L, maxBody: Long = 64 * 1024L, base: String = server.url("/").toString().trimEnd('/')) =
        BlueskyApiClient(http, Dispatchers.IO, base, timeoutMs, maxBody)

    private val profile = """{"did":"$DID","handle":"$HANDLE","displayName":"Alice A","avatar":"https://cdn.bsky.app/img/avatar/a@jpeg"}"""
    private fun feed(vararg items: String) = """{"feed":[${items.joinToString(",")}]}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun found(outcome: BlueskyOutcome): BlueskyFetchResult = (outcome as BlueskyOutcome.Found).result

    @Test fun twoCalls_profileThenFeed_andTheResultCarriesBoth() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed(post("3k1", "hi"))))
        val result = found(client().fetch(HANDLE))
        assertEquals(DID, result.did)
        assertEquals(HANDLE, result.handle)
        assertEquals("Alice A", result.displayName)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", result.avatarUrl)
        assertEquals(1, result.items.length())
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!.requestLine
        val second = server.takeRequest(1, TimeUnit.SECONDS)!!.requestLine
        assertTrue(first, first.startsWith("GET /xrpc/app.bsky.actor.getProfile?actor=$HANDLE "))
        assertTrue(second, second.contains("/xrpc/app.bsky.feed.getAuthorFeed?actor=$DID&limit=50&filter=posts_no_replies "))
    }

    @Test fun theFeedIsAskedByDid_notByTheTypedHandle() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        client().fetch("Alice.Bsky.Social")
        server.takeRequest(1, TimeUnit.SECONDS)
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)!!.requestLine.contains("actor=$DID&"))
    }

    @Test fun http400_isNotFound_andTheFeedIsNeverAsked() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Profile not found"}"""))
        assertEquals(BlueskyOutcome.NotFound, client().fetch("nobody.bsky.social"))
        assertEquals(1, server.requestCount)
    }

    @Test fun otherHttpErrors_areFailed_notNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun http400WithAnotherMessage_isFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Invalid actor"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aRedirect_isFailed_andNeverFollowed() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            other.enqueue(json(profile))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/").toString()))
            assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test fun brokenJson_isFailed() = runBlocking {
        server.enqueue(json("not json"))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aProfileWithoutADid_isFailed() = runBlocking {
        server.enqueue(json("""{"handle":"$HANDLE"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aFeedThatIsNotAnArray_isFailed() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json("""{"feed":"nope"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aBodyOverTheLimit_isFailed_evenWhenTheLengthIsNotAnnounced() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setChunkedBody("{\"feed\":[" + "0,".repeat(40_000) + "0]}", 4096))
        assertEquals(BlueskyOutcome.Failed, client(maxBody = 8 * 1024L).fetch(HANDLE))
    }

    @Test fun aBodyJustUnderTheLimit_isAccepted() = runBlocking {
        server.enqueue(json(profile))
        val body = feed(post("3k1", "hi"))
        server.enqueue(json(body))
        assertTrue(client(maxBody = body.toByteArray().size.toLong()).fetch(HANDLE) is BlueskyOutcome.Found)
    }

    @Test fun aStalledServer_endsInBoundedTime_asFailed() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val t0 = System.nanoTime()
        assertEquals(BlueskyOutcome.Failed, client(timeoutMs = 500L).fetch(HANDLE))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 4_000)
    }

    @Test fun aRefusedConnection_isFailed() = runBlocking {
        val port = server.port
        server.shutdown()
        assertEquals(BlueskyOutcome.Failed, client(base = "http://127.0.0.1:$port").fetch(HANDLE))
    }

    @Test fun anActorThatIsNotAHandleOrADid_isFailed_andNothingIsSent() = runBlocking {
        listOf("", "a b", "a/b", "a?x=1", "a&b", "ａ.bsky.social", "..", "x\u0000y", "a".repeat(300)).forEach {
            assertEquals(it, BlueskyOutcome.Failed, client().fetch(it))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun aDidIsAccepted_asTheActor() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        assertTrue(client().fetch(DID) is BlueskyOutcome.Found)
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)!!.requestLine.contains("actor=$DID "))
    }

    @Test fun cancellingTheCaller_stopsTheCall_andIsRethrown() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { client(timeoutMs = 10_000L).fetch(HANDLE) }
        delay(200)
        job.cancel()
        var cancelled = false
        try { job.await() } catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(job.isActive)
    }

    @Test fun theUserAgentOrCookiesOfTheCaller_areNotAdded_andNoSecretIsSent() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        client().fetch(HANDLE)
        val headers = server.takeRequest(1, TimeUnit.SECONDS)!!.headers
        assertTrue(headers.names().none { it.equals("Authorization", true) || it.equals("Cookie", true) })
    }
}
```

- [ ] **Step 3: Chạy đỏ** (nền, đọc XML sau `.exit`): lệnh như Task 1 Step 4 với `--tests '*BlueskyApiClientTest'`. Expected: biên dịch đỏ vì thiếu `BlueskyApiClient` (không phải lỗi cú pháp test).

- [ ] **Step 4: Viết mã tối thiểu** `BlueskyApiClient.kt`

```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import com.mckimquyen.reader.infrastructure.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads one account from the public Bluesky API: its profile, then its latest own posts. Read-only, no login, nothing sent
 * but the account name. Nothing here logs: the API sees the account being read, so the app must not also write it down.
 */
@Singleton
class BlueskyApiClient internal constructor(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val baseUrl: String,
    private val timeoutMs: Long,
    private val maxBodyBytes: Long,
) {
    @Inject constructor(client: OkHttpClient, @IODispatcher ioDispatcher: CoroutineDispatcher) :
        this(client, ioDispatcher, API_BASE, TIMEOUT_MS, MAX_BODY_BYTES)

    // The app-wide client follows redirects; here a redirect would let the answer come from another host, so none is followed.
    private val strict: OkHttpClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(actor: String): BlueskyOutcome = withContext(ioDispatcher) {
        if (!ACTOR_REGEX.matches(actor)) return@withContext BlueskyOutcome.Failed
        try {
            withTimeout(timeoutMs) { read(actor) }
        } catch (_: TimeoutCancellationException) {
            BlueskyOutcome.Failed
        }
    }

    private suspend fun read(actor: String): BlueskyOutcome {
        val profileCall = when (val answer = get("/xrpc/app.bsky.actor.getProfile", "actor" to actor)) {
            is Answer.Body -> answer
            Answer.NotFound -> return BlueskyOutcome.NotFound
            Answer.Failed -> return BlueskyOutcome.Failed
        }
        val profile = parseObject(profileCall.text) ?: return BlueskyOutcome.Failed
        val did = profile.optString("did").takeIf { it.startsWith("did:") && ACTOR_REGEX.matches(it) } ?: return BlueskyOutcome.Failed
        val feed = when (val answer = get(
            "/xrpc/app.bsky.feed.getAuthorFeed", "actor" to did, "limit" to POST_LIMIT.toString(), "filter" to FEED_FILTER,
        )) {
            is Answer.Body -> answer
            Answer.NotFound, Answer.Failed -> return BlueskyOutcome.Failed
        }
        val items: JSONArray = parseObject(feed.text)?.optJSONArray("feed") ?: return BlueskyOutcome.Failed
        return BlueskyOutcome.Found(
            BlueskyFetchResult(
                items = items,
                did = did,
                handle = profile.optString("handle"),
                displayName = BlueskyFeedMapper.cleanDisplayName(profile.opt("displayName") as? String),
                avatarUrl = BlueskyFeedMapper.iconUrl(profile.opt("avatar") as? String),
            ),
        )
    }

    private sealed interface Answer {
        class Body(val text: String) : Answer
        object NotFound : Answer
        object Failed : Answer
    }

    private suspend fun get(path: String, vararg query: Pair<String, String>): Answer {
        val url = (baseUrl + path).toHttpUrlOrNull()?.newBuilder()?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }?.build()
            ?: return Answer.Failed
        val call: Call = strict.newCall(Request.Builder().url(url).header("Accept", JSON).build())
        val cancel = currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { response ->
                currentCoroutineContext().ensureActive()
                when {
                    response.isRedirect -> Answer.Failed
                    response.code == HTTP_BAD_REQUEST -> {
                        val text = readLimited(response.body?.source(), NOT_FOUND_PEEK_BYTES)
                        if (text != null && text.contains(PROFILE_NOT_FOUND)) Answer.NotFound else Answer.Failed
                    }
                    !response.isSuccessful -> Answer.Failed
                    else -> {
                        val body = response.body ?: return@use Answer.Failed
                        if (body.contentLength() > maxBodyBytes) return@use Answer.Failed
                        readLimited(body.source(), maxBodyBytes)?.let { Answer.Body(it) } ?: Answer.Failed
                    }
                }
            }
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            Answer.Failed
        } finally {
            cancel?.dispose()
        }
    }

    /** Reads at most [limit] bytes; returns null when the body is longer (nothing partial is ever parsed). */
    private fun readLimited(source: okio.BufferedSource?, limit: Long): String? {
        if (source == null) return null
        val buffer = Buffer()
        while (buffer.size <= limit) {
            if (source.read(buffer, minOf(limit + 1 - buffer.size, READ_CHUNK_BYTES)) == -1L) break
        }
        return if (buffer.size > limit) null else buffer.readUtf8()
    }

    private fun parseObject(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (_: JSONException) {
        null
    }

    companion object {
        const val API_BASE = "https://public.api.bsky.app"
        const val TIMEOUT_MS = 8_000L
        const val MAX_BODY_BYTES = 512 * 1024L
        const val POST_LIMIT = 50
        private const val FEED_FILTER = "posts_no_replies"
        private const val JSON = "application/json"
        private const val HTTP_BAD_REQUEST = 400
        private const val PROFILE_NOT_FOUND = "Profile not found"
        private const val NOT_FOUND_PEEK_BYTES = 2_048L
        private const val READ_CHUNK_BYTES = 8_192L
        private val ACTOR_REGEX = Regex("^[A-Za-z0-9:._-]{1,253}$")
    }
}
```
Điểm cần xác nhận bằng lần chạy đầu, không đoán: (1) `call.execute()` trên luồng IO bị chặn; `invokeOnCompletion { call.cancel() }` phải đủ để test hủy xanh; nếu không thì dùng `kotlinx.coroutines.suspendCancellableCoroutine` với `Call.enqueue`; (2) `MockResponse.setChunkedBody` và `SocketPolicy.NO_RESPONSE` có trong `mockwebserver3` 5.0.0-alpha.11 (đã dùng ở `NostrRelayNetworkTest`: `SocketPolicy` có; `setChunkedBody` chưa thấy, đọc API trước, nếu thiếu thì dùng `setBody` với `Buffer` lớn và bỏ `Content-Length` bằng `setHeader`/`setBodyDelay`).

- [ ] **Step 5: Chạy xanh** (nền). Expected: 18 test, 0 lỗi; chạy hai lần để chắc không chập chờn (test thời gian: dùng `timeoutMs` ngắn, không `Thread.sleep` mù).

- [ ] **Step 6: Mutation** (`bsky_t2_batch.sh`, một script nền, tệp `BlueskyApiClient.kt`, lớp `*BlueskyApiClientTest`): bỏ `followRedirects(false)`; bỏ `response.isRedirect`; bỏ `HTTP_BAD_REQUEST` (400 thành Failed); 400 nhận mọi thông điệp (bỏ `contains(PROFILE_NOT_FOUND)`); bỏ kiểm `contentLength`; `maxBodyBytes` thành vô hạn (bỏ `buffer.size > limit`); `<=` thành `<` trong vòng đọc; bỏ `withTimeout`; bỏ `invokeOnCompletion`; bỏ `ensureActive` (hủy bị nuốt thành Failed); lấy feed theo `actor` gõ tay thay vì `did`; bỏ `limit` hay `filter`; bỏ `ACTOR_REGEX` (nhận `a b`); bỏ kiểm `did:`; `cleanDisplayName` bỏ gọi (tên thô); `iconUrl` bỏ gọi (avatar thô). Sống sót ⇒ thêm test; guard thừa ⇒ gỡ.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyApiClient.kt \
  app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyApiClientTest.kt
git commit -m "feat(front-02g): read a Bluesky profile and its own posts from the public API, bounded in time and size

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Loại nguồn BLUESKY và đường đồng bộ nền (chưa động tới `search()`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt` (thêm hằng số)
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt` (tham số cuối + nhánh)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt` (hai chỗ bỏ qua)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt` (một chỗ)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` (chỉ tham số constructor ở task này)
- Modify (mỗi lớp một dòng `blueskyApiClient = mockk(relaxed = true)`): `LocalRssSvWebFeedSyncTest`, `WebFeedRoomEndToEndTest`, `LocalRssSvNostrSyncTest`, `RetractionRadarSyncTest`, `SubscribeViewModelWebFeedTest`, `SubscribeViewModelNewsletterTest`, `SubscribeViewModelNostrTest`, `SubscribeViewModelNostrProfileTest`, `SubscribeViewModelInterruptionTest`, `SubscribeDialogTypingTest`, `SubscribeViewModelSocialTest`; và `app/src/androidTest/.../SubscribeViewModelFactory.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvBlueskySyncTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/BlueskyNoWebFetchTest.kt`

**Interfaces:**
- Consumes: `BlueskyApiClient.fetch(actor): BlueskyOutcome`, `BlueskyFeedMapper.toArticles(...)`, `BlueskyFeedMapper.FEED_URL_PREFIX` (Task 1, 2).
- Produces: `Feed.BLUESKY_SOURCE_TYPE`; `LocalRssSv(..., nostrRelayClient, blueskyApiClient: BlueskyApiClient)`; `SubscribeViewModel(..., nostrRelayClient, blueskyApiClient: BlueskyApiClient)`; `LocalRssSv.queryFeedArticles` đọc nguồn `BLUESKY` bằng API.

- [ ] **Step 1: Đọc lại các chỗ sẽ sửa** (đã đọc 2026-11-16; đọc lại để chắc không lệch)

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "NOSTR_SOURCE_TYPE" $J/domain/model/feed/Feed.kt $J/domain/sv/LocalRssSv.kt $J/infrastructure/rss/RssHelper.kt $J/infrastructure/rss/OfflinePrecacheService.kt
grep -n "private val nostrRelayClient" $J/domain/sv/LocalRssSv.kt $J/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -rln "nostrRelayClient = " app/src/test app/src/androidTest | wc -l
```
Expected: 5 dòng `NOSTR_SOURCE_TYPE` ngoài `SubscribeViewModel`; 2 dòng tham số; **12** tệp (11 test JVM + 1 androidTest). Nếu số khác: dừng, cập nhật danh sách tệp trong task này.

- [ ] **Step 2: Viết test đỏ** `LocalRssSvBlueskySyncTest.kt` (cùng khuôn `LocalRssSvNostrSyncTest`; `BlueskyApiClient` là lớp `final` nên MockK mock được qua `mockk<BlueskyApiClient>()` như `NostrRelayClient`)

```kotlin
package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFetchResult
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvBlueskySyncTest {
    private val api = mockk<BlueskyApiClient>()
    private val rss = mockk<RssHelper>(relaxed = true)
    private val filters = mockk<com.mckimquyen.reader.infrastructure.filter.SmartFilterManager> {
        io.mockk.every { applyRules(any()) } answers { firstArg() }
    }
    private val service = LocalRssSv(
        context = ApplicationProvider.getApplicationContext(), articleDao = mockk<ArticleDao>(relaxed = true),
        feedDao = mockk(relaxed = true), rssHelper = rss, notificationHelper = mockk(relaxed = true),
        accountDao = mockk(relaxed = true), groupDao = mockk(relaxed = true), feedHealthDao = mockk<FeedHealthDao>(relaxed = true),
        ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
        workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
        smartFilterManager = filters, webFeedScraper = mockk<WebFeedScraper>(),
        smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
        retractionRadar = mockk(relaxed = true), nostrRelayClient = mockk(relaxed = true), blueskyApiClient = api,
    )
    private val feed = Feed(
        id = "bsky-feed", name = "Bluesky · Alice", url = "bsky:alice.bsky.social", groupId = "group", accountId = 1,
        icon = "https://cdn.bsky.app/img/avatar/a@jpeg", sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = "bsky:alice.bsky.social",
    )

    private fun found(vararg items: String, displayName: String? = "Someone New") = BlueskyOutcome.Found(
        BlueskyFetchResult(BlueskyTestPayloads.array(*items), BlueskyTestPayloads.DID, BlueskyTestPayloads.HANDLE, displayName, "https://cdn.bsky.app/x"),
    )

    @Test fun theSourceTypeConstant_isBluesky() = assertEquals("BLUESKY", Feed.BLUESKY_SOURCE_TYPE)

    @Test fun aSync_readsTheOwnPostsOfTheAccount_throughTheApiClient() = runBlocking {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "one"), BlueskyTestPayloads.post("3k2", "two"))
        val articles = service.syncSingleFeed(feed)?.articles.orEmpty()
        assertEquals(setOf("one", "two"), articles.map { it.title }.toSet())
        assertTrue(articles.all { it.feedId == "bsky-feed" && it.link.startsWith("https://bsky.app/profile/") })
        coVerify(exactly = 1) { api.fetch("alice.bsky.social") }
    }

    @Test fun aSync_neverCallsTheRssHelper_orFavicons() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(feed)
        coVerify(exactly = 0) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { rss.queryRssIcon(any(), any()) }
    }

    @Test fun aSync_neverRenamesTheFeed_nor_changesItsIcon() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.post("3k1", "one"), displayName = "Totally New Name")
        val result = service.syncSingleFeed(feed)
        assertEquals("Bluesky · Alice", result?.feed?.name)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", result?.feed?.icon)
    }

    @Test fun aFeedStoredByDid_isReadByDid() = runBlocking {
        val byDid = feed.copy(url = "bsky:${BlueskyTestPayloads.DID}", sourceUrl = "bsky:${BlueskyTestPayloads.DID}")
        coEvery { api.fetch(BlueskyTestPayloads.DID) } returns found(BlueskyTestPayloads.post("3k1", "one"))
        assertEquals(1, service.syncSingleFeed(byDid)?.articles?.size)
    }

    @Test fun failed_andNotFound_areBothSyncFailures_notEmptyFeeds() = runBlocking {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        assertNull(service.syncSingleFeed(feed))
        coEvery { api.fetch(any()) } returns BlueskyOutcome.NotFound
        assertNull(service.syncSingleFeed(feed))
    }

    @Test fun anAnswerWithNoUsablePosts_isASyncFailure() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.repost("r1"))
        assertNull(service.syncSingleFeed(feed))
    }

    @Test fun aStoredUrlThatIsNotAnActor_isAFailure_andNeverReachesTheApi() = runBlocking {
        val broken = feed.copy(url = "bsky:not an actor", sourceUrl = "bsky:not an actor")
        assertNull(service.syncSingleFeed(broken))
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun anRssFeed_stillUsesTheRssHelper_andNeverTheApi() = runBlocking {
        coEvery { rss.queryRssXml(any(), any()) } returns emptyList()
        service.syncSingleFeed(feed.copy(sourceType = Feed.RSS_SOURCE_TYPE, url = "https://bsky.app/profile/x/rss", sourceUrl = null))
        coVerify(exactly = 1) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { api.fetch(any()) }
    }
}
```

`BlueskyNoWebFetchTest.kt` (cùng khuôn `NostrNoWebFetchTest`; kiểm `parseFullContent` và `queryRssIcon` cho BLUESKY và `OfflinePrecacheService`):

```kotlin
package com.mckimquyen.reader.infrastructure.rss

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyNoWebFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: RssHelper

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><p>should never be read</p></body></html>"))
        helper = RssHelper(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parseFullContent_forABlueskyArticle_returnsEmptyWithoutOpeningAConnection() = runBlocking {
        assertEquals("", helper.parseFullContent(server.url("/post").toString(), "t", Feed.BLUESKY_SOURCE_TYPE))
        assertEquals(0, server.requestCount)
    }

    @Test fun queryRssIcon_forABlueskyFeed_doesNotWriteAFaviconAddress() = runBlocking {
        val feedDao = mockk<FeedDao>(relaxed = true)
        val feed = Feed(id = "b", name = "Bluesky", url = "https://x.test/x", groupId = "g", accountId = 1, sourceType = Feed.BLUESKY_SOURCE_TYPE)
        helper.queryRssIcon(feedDao, feed)
        coVerify(exactly = 0) { feedDao.update(any()) }
    }

    @Test fun precache_forABlueskyArticle_neverFetchesFullContentOrImages() = runBlocking {
        val article = Article(
            id = "a1", date = Date(), title = "t", rawDescription = "<p>x</p>", shortDescription = "x",
            img = "https://example.com/banner.jpg", link = server.url("/post").toString(), feedId = "bsky-feed", accountId = 1,
        )
        val articleDao = mockk<ArticleDao>(relaxed = true)
        val feedDao = mockk<FeedDao>(relaxed = true)
        val rss = mockk<RssHelper>(relaxed = true)
        val images = mockk<ImageLoader>(relaxed = true)
        coEvery { feedDao.queryById("bsky-feed") } returns Feed(id = "bsky-feed", name = "B", url = "bsky:x", groupId = "g", accountId = 1, sourceType = Feed.BLUESKY_SOURCE_TYPE)
        coEvery { articleDao.queryUnreadArticleList(accountId = 1, limit = 50) } returns listOf(article.toListItem())
        val service = spyk(OfflinePrecacheService(ApplicationProvider.getApplicationContext(), articleDao, rss, images, Dispatchers.Unconfined, feedDao))
        every { service.isOnWifi() } returns true

        service.precacheLatestUnread(1)

        coVerify(exactly = 0) { rss.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { rss.extractImageUrls(any()) }
        io.mockk.verify(exactly = 0) { images.enqueue(any()) }
        assertEquals(0, server.requestCount)
    }
}
```
Đọc nguyên văn `NostrNoWebFetchTest` (import `toListItem`, `ImageLoader`) trước khi chép; nếu import khác thì sửa theo tệp thật, không theo plan.

- [ ] **Step 3: Chạy đỏ** (nền; **trước** khi thêm tham số vào 12 tệp test cũ, chạy riêng hai lớp mới): lệnh như Task 1 Step 4 với `--tests '*LocalRssSvBlueskySyncTest' --tests '*BlueskyNoWebFetchTest'`. Expected: biên dịch đỏ vì thiếu `Feed.BLUESKY_SOURCE_TYPE` và tham số `blueskyApiClient` (không phải lỗi cú pháp). Biên dịch test đỏ chung cả project, nên các lớp cũ cũng chưa dựng được; đây là bình thường ở bước đỏ.

- [ ] **Step 4: Viết mã tối thiểu**

`Feed.kt`: thêm `const val BLUESKY_SOURCE_TYPE = "BLUESKY"` ngay sau `NOSTR_SOURCE_TYPE`.

`LocalRssSv.kt`: thêm tham số cuối `private val blueskyApiClient: com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient,` sau `nostrRelayClient`; trong `queryFeedArticles`, ngay sau dòng `if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return queryNostrArticles(feed)` thêm `if (feed.sourceType == Feed.BLUESKY_SOURCE_TYPE) return queryBlueskyArticles(feed)`; thêm hàm cạnh `queryNostrArticles`:

```kotlin
    private suspend fun queryBlueskyArticles(feed: Feed): List<Article> {
        val actor = (feed.sourceUrl ?: feed.url).removePrefix(
            com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.FEED_URL_PREFIX,
        )
        // Neither the display name nor the avatar is used here: a background sync must never rename a source the user may have renamed.
        val found = blueskyApiClient.fetch(actor)
            as? com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.Found
            ?: throw java.io.IOException("Bluesky API returned no account")
        val articles = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.toArticles(
            profileDid = found.result.did,
            items = found.result.items,
            feedId = feed.id,
            accountId = feed.accountId,
            nowMillis = System.currentTimeMillis(),
        )
        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")
        return articles
    }
```

`RssHelper.kt`: `parseFullContent`: đổi `if (sourceType == Feed.NOSTR_SOURCE_TYPE) return ""` thành `if (sourceType == Feed.NOSTR_SOURCE_TYPE || sourceType == Feed.BLUESKY_SOURCE_TYPE) return ""` và sửa chú thích thành "A Nostr note or a Bluesky post has no web page of its own to read on the app's behalf"; `queryRssIcon`: đổi `if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return` thành `if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE || feed.sourceType == Feed.BLUESKY_SOURCE_TYPE) return`.

`OfflinePrecacheService.kt`: đổi `if (sourceType == com.mckimquyen.reader.domain.model.feed.Feed.NOSTR_SOURCE_TYPE) continue` thành `if (sourceType == com.mckimquyen.reader.domain.model.feed.Feed.NOSTR_SOURCE_TYPE || sourceType == com.mckimquyen.reader.domain.model.feed.Feed.BLUESKY_SOURCE_TYPE) continue`.

`SubscribeViewModel.kt`: thêm tham số cuối `private val blueskyApiClient: com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient,` sau `nostrRelayClient`; trong `subscribe()` đổi điều kiện `feed.sourceType != Feed.NOSTR_SOURCE_TYPE` thành `feed.sourceType != Feed.NOSTR_SOURCE_TYPE && feed.sourceType != Feed.BLUESKY_SOURCE_TYPE`. Task này **chưa** đụng `search()`.

12 tệp test + `SubscribeViewModelFactory.kt`: thêm `blueskyApiClient = mockk(relaxed = true)` (JVM) hoặc, ở androidTest, `blueskyApiClient = BlueskyApiClient(app.okHttpClient, Dispatchers.IO)`, ngay sau `nostrRelayClient = …`. Dùng python, mỗi tệp đúng một chỗ (`count == 1`), nếu không khớp thì dừng và đọc tệp.

- [ ] **Step 5: Chạy xanh**: các lớp mới (`LocalRssSvBlueskySyncTest` 9, `BlueskyNoWebFetchTest` 3) **và** mọi lớp đã sửa tham số: `--tests '*LocalRssSv*' --tests '*SubscribeViewModel*' --tests '*SubscribeDialog*' --tests '*WebFeedRoomEndToEnd*' --tests '*RetractionRadarSync*' --tests '*BlueskyFeedMapper*' --tests '*NostrNoWebFetch*'`. Expected: exit 0, 0 lỗi.

- [ ] **Step 6: Mutation** (`bsky_t3_batch.sh`; tệp `LocalRssSv.kt`, `RssHelper.kt`, `OfflinePrecacheService.kt`, `SubscribeViewModel.kt`; lớp `LocalRssSvBlueskySyncTest`, `BlueskyNoWebFetchTest`, `SubscribeViewModelNostrTest`): bỏ nhánh `BLUESKY` trong `queryFeedArticles`; thêm đổi tên feed trong sync (`feed.name = …`); thêm ghi đè `feed.icon` từ avatar; bỏ `throw` khi `articles.isEmpty()`; `Found` thay bằng nhận mọi kết quả; bỏ `BLUESKY` ở `parseFullContent`, `queryRssIcon`, precache; bỏ `BLUESKY` trong `subscribe()` (`isFullContent` thành `true`). Sống sót ⇒ thêm test.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java app/src/test/java app/src/androidTest/java
git status --short | grep -v idea
git commit -m "feat(front-02g): sync a Bluesky source from the API without renaming it, fetching pages or favicons

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Trước khi commit: `git diff --stat -- app/src/main/res app/schemas app/src/main/AndroidManifest.xml app/build.gradle` phải rỗng.

---

### Task 4: Nhánh API trong `SubscribeViewModel.search()`, quay về RSS, 400, hủy

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt` (thêm `actorFromRssUrl`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` (nhánh Bluesky trong `search()`)
- Test: `.../social/bluesky/BlueskyFeedMapperTest.kt` (thêm), `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt` (mới)

**Interfaces:**
- Consumes: `SocialFeedUrlResolver.resolve(input): String?` (đã có, không sửa), `BlueskyApiClient.fetch`, `BlueskyOutcome`, `BlueskyFeedMapper.{feedUrl, feedName, toArticles, iconUrl}`, `Feed.BLUESKY_SOURCE_TYPE`.
- Produces: `BlueskyFeedMapper.actorFromRssUrl(rssUrl: String?): String?` (nhận `https://bsky.app/profile/<handle|did>/rss`, trả `<handle|did>`, ngược lại `null`).

**Cách nhận dạng (không sửa resolver):** resolver đã chuẩn hóa mọi dạng Bluesky (`@handle`, link hồ sơ có hoặc không `/rss`) thành đúng một dạng `https://bsky.app/profile/<actor>/rss`. `search()` gọi resolver như hiện nay rồi `actorFromRssUrl` trên kết quả; Mastodon và mọi thứ khác cho `null` nên không đổi hành vi.

- [ ] **Step 1: Đọc lại và kiểm hai test cũ không vỡ**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 124,128p $J/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -n "val socialUrl = SocialFeedUrlResolver.resolve(typed)" $J/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -n "mockk(relaxed = true)" app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelSocialTest.kt | head -3
```
Expected: dòng `val socialUrl = …` xuất hiện đúng 1 lần. **Quan trọng:** với `blueskyApiClient = mockk(relaxed = true)` kết quả `fetch` là một **đối tượng giả** của `BlueskyOutcome`, **không phải** `Found`, `NotFound` hay `Failed`. Vì vậy mã `search()` **không được** dùng `when` toàn diện trên `BlueskyOutcome` (sẽ ném `NoWhenBranchMatchedException`); dùng `if (outcome is Found) … else if (outcome == NotFound) … else (rơi về RSS)`. Test ở Step 2 kiểm đúng điều này.

- [ ] **Step 2: Viết test đỏ**

Thêm vào `BlueskyFeedMapperTest.kt` (cuối lớp, trước dấu `}` cuối):

```kotlin
    // ---- the address the resolver already produced
    @Test fun actorFromRssUrl_readsAHandleAndADid() {
        assertEquals("alice.bsky.social", BlueskyFeedMapper.actorFromRssUrl("https://bsky.app/profile/alice.bsky.social/rss"))
        assertEquals("did:plc:AbC123", BlueskyFeedMapper.actorFromRssUrl("https://bsky.app/profile/did:plc:AbC123/rss"))
    }

    @Test fun actorFromRssUrl_refusesEverythingElse() {
        listOf(
            null, "", "https://mastodon.social/@x.rss", "http://bsky.app/profile/a.b/rss", "https://bsky.app/profile/a.b",
            "https://bsky.app/profile//rss", "https://bsky.app/profile/a b/rss", "https://bsky.app/profile/a.b/rss/extra",
            "https://evil.example/profile/a.b/rss", "https://bsky.app.evil.example/profile/a.b/rss", "https://bsky.app/profile/a/b/rss",
        ).forEach { assertNull(it, BlueskyFeedMapper.actorFromRssUrl(it)) }
    }
```

`SubscribeViewModelBlueskyTest.kt` (khuôn `SubscribeViewModelNostrProfileTest`; **đọc nguyên văn tệp đó trước khi chép phần dựng**, vì import và `setUp` phải khớp):

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
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
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFetchResult
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelBlueskyTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val api = mockk<BlueskyApiClient>()
    private val rssUrl = "https://bsky.app/profile/alice.bsky.social/rss"
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
            nostrRelayClient = mockk(relaxed = true), blueskyApiClient = api,
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun found(vararg items: String, name: String? = "Alice A", avatar: String? = "https://cdn.bsky.app/img/avatar/a@jpeg") =
        BlueskyOutcome.Found(BlueskyFetchResult(BlueskyTestPayloads.array(*items), BlueskyTestPayloads.DID, BlueskyTestPayloads.HANDLE, name, avatar))

    private fun feedAt(url: String) = FeedWithArticle(
        Feed(id = "feed_1", name = "RSS name", url = url, groupId = "group_1", accountId = 1),
        listOf(Article(id = "a1", date = Date(), title = "rss item", rawDescription = "", shortDescription = "", link = "$url/1", feedId = "feed_1", accountId = 1)),
    )

    private val state get() = viewModel.subscribeUiState.value

    private fun TestScope.search(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        advanceUntilIdle()
    }

    @Test fun aHandle_isReadThroughTheApi_andShowsTheDisplayNameAndTheAvatar() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@alice.bsky.social")
        assertEquals("Bluesky · Alice A", state.feed?.name)
        assertEquals("bsky:alice.bsky.social", state.feed?.url)
        assertEquals("bsky:alice.bsky.social", state.feed?.sourceUrl)
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, state.feed?.sourceType)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", state.feed?.icon)
        assertEquals(listOf("hello"), state.articles.map { it.title })
        assertTrue(state.lockLinkInput)
        assertFalse(state.webPreview)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun withoutADisplayName_theNameIsTheHandle_andWithoutAnAvatarThereIsNoIcon() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"), name = null, avatar = null)
        search("@alice.bsky.social")
        assertEquals("Bluesky · @alice.bsky.social", state.feed?.name)
        assertNull(state.feed?.icon)
    }

    @Test fun aProfileLink_andADidLink_alsoUseTheApi() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("https://bsky.app/profile/alice.bsky.social")
        assertEquals("bsky:alice.bsky.social", state.feed?.url)
        coEvery { api.fetch("did:plc:AbC123") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        viewModel.reset(); viewModel.init()
        search("https://bsky.app/profile/did:plc:AbC123")
        assertEquals("bsky:did:plc:AbC123", state.feed?.url)
    }

    @Test fun whileTheApiAnswers_theBoxIsLocked_andShowsSearching() = runTest(dispatcher) {
        val gate = CompletableDeferred<BlueskyOutcome>()
        coEvery { api.fetch(any()) } coAnswers { gate.await() }
        viewModel.inputLink("@alice.bsky.social"); viewModel.search(); runCurrent()
        assertTrue(state.lockLinkInput)
        assertEquals(strings.getString(R.string.searching), state.title)
        gate.complete(found(BlueskyTestPayloads.post("3k1", "hello")))
        advanceUntilIdle()
        assertEquals("Bluesky · Alice A", state.feed?.name)
    }

    @Test fun whenTheApiFails_theOldRssFlowRuns_withTheSameAddressAsBefore() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
        assertEquals(rssUrl, state.feed?.url)
        assertEquals(Feed.RSS_SOURCE_TYPE, state.feed?.sourceType)
    }

    @Test fun whenTheApiThrows_theOldRssFlowRuns() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } throws java.io.IOException("boom")
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun whenTheApiHasNoUsablePosts_theOldRssFlowRuns() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.repost("r1"))
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun aNotFoundAccount_isAnError_neverTriedAsRss_andTheBoxKeepsWhatWasTyped() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.NotFound
        search("@alice.bsky.social")
        assertEquals(strings.getString(R.string.web_feed_fetch_failed), state.errorMessage)
        assertEquals("@alice.bsky.social", state.linkContent)
        assertFalse(state.lockLinkInput)
        assertNull(state.feed)
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun anAnswerThatIsNeitherFoundNorNotFound_fallsBackToRss_notACrash() = runTest(dispatcher) {
        coEvery { api.fetch(any()) } returns mockk<BlueskyOutcome>()
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 1) { rssHelper.searchFeed(rssUrl) }
    }

    @Test fun anAlreadySubscribedSource_isReportedWithoutCallingTheApi() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("bsky:alice.bsky.social") } returns true
        search("@alice.bsky.social")
        assertEquals(strings.getString(R.string.already_subscribed), state.errorMessage)
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aNonLocalAccount_neverUsesTheApi() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aMastodonHandle_neverTouchesTheApi() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { rssHelper.searchFeed(rss) } returns feedAt(rss)
        search("@Mastodon@mastodon.social")
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun closingTheDialogWhileTheApiAnswers_showsNoErrorAndNoLatePreview() = runTest(dispatcher) {
        val gate = CompletableDeferred<BlueskyOutcome>()
        coEvery { api.fetch(any()) } coAnswers { gate.await() }
        viewModel.inputLink("@alice.bsky.social"); viewModel.search(); runCurrent()
        viewModel.hideDrawer(); viewModel.reset(); runCurrent()
        gate.complete(found(BlueskyTestPayloads.post("3k1", "hello")))
        advanceUntilIdle()
        assertNull(state.feed)
        assertEquals("", state.errorMessage)
        assertFalse(state.lockLinkInput)
    }

    @Test fun aSecretKeyIsStillBlockedFirst_andNeverReachesTheApi() = runTest(dispatcher) {
        search("nsec1abc")
        coVerify(exactly = 0) { api.fetch(any()) }
        assertEquals("", state.linkContent)
    }

    @Test fun subscribing_keepsTheAvatarAsTheIcon_andNeverAsksForFullContent() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@alice.bsky.social")
        viewModel.changeParseFullContentPreset()
        viewModel.subscribe()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            localService.subscribe(
                match { it.sourceType == Feed.BLUESKY_SOURCE_TYPE && it.url == "bsky:alice.bsky.social" && it.icon == "https://cdn.bsky.app/img/avatar/a@jpeg" && !it.isFullContent },
                any(),
            )
        }
    }
}
```
Điều xác nhận bằng chính lần chạy đầu (không đoán): `AccountType.Fever` tồn tại (kiểm `AccountType.kt`); `mockk<BlueskyOutcome>()` được với `sealed interface`; `localService.isFeedExist` mặc định `false` ở `setUp`.

- [ ] **Step 3: Chạy đỏ** (nền): `--tests '*BlueskyFeedMapperTest' --tests '*SubscribeViewModelBlueskyTest'`. Expected: biên dịch đỏ vì thiếu `actorFromRssUrl` (không phải lỗi cú pháp test).

- [ ] **Step 4: Viết mã tối thiểu**

`BlueskyFeedMapper.kt` thêm:

```kotlin
    private const val RSS_PROFILE_PREFIX = "https://bsky.app/profile/"
    private const val RSS_SUFFIX = "/rss"
    private val ACTOR = Regex("^[A-Za-z0-9:._-]{1,253}$")

    /** The handle or DID inside the RSS address the resolver builds for a Bluesky account; null for any other address. */
    fun actorFromRssUrl(rssUrl: String?): String? {
        if (rssUrl == null || !rssUrl.startsWith(RSS_PROFILE_PREFIX) || !rssUrl.endsWith(RSS_SUFFIX)) return null
        val actor = rssUrl.removePrefix(RSS_PROFILE_PREFIX).removeSuffix(RSS_SUFFIX)
        return actor.takeIf { ACTOR.matches(it) }
    }
```

`SubscribeViewModel.kt`: **sau** dòng `val socialUrl = SocialFeedUrlResolver.resolve(typed)` và **trước** `(socialUrl ?: typed.formatUrl()).let { … }` chèn nhánh dưới (nó chạy trong cùng khối `try` của `search()`; các `return@launch` như nhánh Nostr):

```kotlin
                val blueskyActor = if (context.currentAccountType == AccountType.Local.id) {
                    com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.actorFromRssUrl(socialUrl)
                } else {
                    null
                }
                if (blueskyActor != null) {
                    val feedUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(blueskyActor)
                    if (rssService.get().isFeedExist(feedUrl)) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                lockLinkInput = false,
                            )
                        }
                        return@launch
                    }
                    _subscribeUiState.update {
                        it.copy(title = androidStringsHelper.getString(R.string.searching), lockLinkInput = true)
                    }
                    val outcome = runCatching { blueskyApiClient.fetch(blueskyActor) }
                        // A cancelled search (dialog closed, newer search) must stop here, not be reported as a failure.
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrNull()
                    // Not an exhaustive `when`: anything that is neither Found nor NotFound simply falls back to the RSS flow below.
                    if (outcome == com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.NotFound) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.web_feed_fetch_failed),
                                lockLinkInput = false,
                            )
                        }
                        return@launch
                    }
                    if (outcome is com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.Found) {
                        val found = outcome.result
                        val feedId = context.currentAccountId.spacerDollar(UUID.randomUUID().toString())
                        val articles = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.toArticles(
                            profileDid = found.did,
                            items = found.items,
                            feedId = feedId,
                            accountId = context.currentAccountId,
                            nowMillis = System.currentTimeMillis(),
                        )
                        if (articles.isNotEmpty()) {
                            val feed = Feed(
                                id = feedId,
                                name = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedName(blueskyActor, found.displayName),
                                url = feedUrl,
                                sourceUrl = feedUrl,
                                groupId = "",
                                accountId = context.currentAccountId,
                                sourceType = Feed.BLUESKY_SOURCE_TYPE,
                                icon = found.avatarUrl,
                            )
                            _subscribeUiState.update {
                                it.copy(
                                    linkContent = feedUrl,
                                    feed = feed,
                                    articles = articles,
                                    title = feed.name,
                                    errorMessage = "",
                                    lockLinkInput = true,
                                    webPreview = false,
                                    isSearchPage = false,
                                )
                            }
                            return@launch
                        }
                    }
                    // API failed, threw, or had nothing usable: the box was never rewritten, so the old RSS flow runs on what was typed.
                }
```
Lưu ý khớp mã thật: `Feed` đã import trong `SubscribeViewModel` (dùng ở nhánh Nostr bằng tên đầy đủ; nếu không import thì viết tên đầy đủ `com.mckimquyen.reader.domain.model.feed.Feed`, như nhánh Nostr), `UUID` và `spacerDollar` đã import (dùng ở nhánh Nostr). Đọc lại tệp thật trước khi chép để chắc các import có sẵn.

- [ ] **Step 5: Chạy xanh** (nền): `--tests '*BlueskyFeedMapperTest' --tests '*SubscribeViewModel*' --tests '*SubscribeDialogTypingTest'`. Expected: exit 0; 0 lỗi; hai test Bluesky RSS cũ của `SubscribeViewModelSocialTest` vẫn xanh (cho thấy relaxed mock rơi về RSS); 14+2 test mới xanh.

- [ ] **Step 6: Mutation** (`bsky_t4_batch.sh`; tệp `SubscribeViewModel.kt` và `BlueskyFeedMapper.kt`; lớp `SubscribeViewModelBlueskyTest`, `BlueskyFeedMapperTest`, `SubscribeViewModelSocialTest`): bỏ điều kiện `Local`; bỏ `isFeedExist`; bỏ `title = searching`/`lockLinkInput = true` trước khi gọi API; `NotFound` rơi về RSS (xóa `return@launch`); bỏ `.onFailure { if (CancellationException) throw }` (hủy bị nuốt); `articles.isNotEmpty()` bỏ (xem trước rỗng); bỏ `icon = found.avatarUrl`; `feedName(blueskyActor, found.displayName)` thành `feedName(blueskyActor, null)`; ghi `linkContent = feedUrl` vào nhánh thất bại (lỗi kiểu FRONT-02b); `actorFromRssUrl` bỏ `startsWith`/`endsWith`/`ACTOR.matches`; `subscribe()` bỏ nhánh BLUESKY của `isFullContent` (Task 3). Sống sót ⇒ thêm test; guard thừa ⇒ gỡ.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt \
  app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt \
  app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt \
  app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt
git commit -m "feat(front-02g): name a new Bluesky source after its profile, and fall back to RSS when the API cannot answer

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Kiểm chứng toàn bộ, máy thật (chỉ Tecno), tài liệu

**Files:**
- Tạm, **không commit**: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyProbe.kt` (xóa sau khi chạy)
- Create: `doc/task/done/FRONT-02g_bluesky-api_DONE.md`
- Modify: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi")

- [ ] **Step 1: Full suite JVM, assemble, lint (nền, ra file, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29
rm -f $D/f30-full.exit $D/f30-assemble.exit $D/f30-lint.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > $D/f30-full.log 2>&1; echo $? > $D/f30-full.exit
  ./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f30-assemble.log 2>&1; echo $? > $D/f30-assemble.exit
  ./gradlew lintDevDebug > $D/f30-lint.log 2>&1; echo $? > $D/f30-lint.exit ) > /dev/null 2>&1 &
```
Chờ cả ba `.exit` bằng `0`. Đếm từ XML (`app/build/test-results/testDevDebugUnitTest/*.xml`): kỳ vọng 3.627 + số test mới, 0 lỗi, 0 bỏ qua. Lint: 0 lỗi, 2.465 cảnh báo (nền), 0 mục trong tệp `bluesky/`. `git diff --stat -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` rỗng; `git diff --check` sạch.

- [ ] **Step 2: Helper máy thật tạm `BlueskyProbe` (không commit; chỉ ghi số lượng)**

```kotlin
package com.mckimquyen.reader.infrastructure.social.bluesky

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Throw-away check against the real public API on the Tecno KJ7. Logs counts and lengths only, never a handle, name, text or address. */
@RunWith(AndroidJUnit4::class)
class BlueskyProbe {
    private val client by lazy {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RApp
        BlueskyApiClient(app.okHttpClient, Dispatchers.IO)
    }

    private fun probe(label: String, actor: String) = runBlocking {
        val started = System.currentTimeMillis()
        val outcome = client.fetch(actor)
        val ms = System.currentTimeMillis() - started
        when (outcome) {
            is BlueskyOutcome.Found -> {
                val r = outcome.result
                val articles = BlueskyFeedMapper.toArticles(r.did, r.items, "probe", 1, System.currentTimeMillis())
                val withEmbed = articles.count { it.rawDescription.contains("<br>[") }
                Log.i("BskyProbe", "$label found items=${r.items.length()} ownPosts=${articles.size} withEmbedLine=$withEmbed " +
                    "hasName=${r.displayName != null} nameCodePoints=${r.displayName?.let { it.codePointCount(0, it.length) }} " +
                    "hasAvatar=${r.avatarUrl != null} ms=$ms")
            }
            BlueskyOutcome.NotFound -> Log.i("BskyProbe", "$label notFound ms=$ms")
            BlueskyOutcome.Failed -> Log.i("BskyProbe", "$label failed ms=$ms")
        }
    }

    @Test fun existingAccount() = probe("existing", "bsky.app")
    @Test fun missingAccount() = probe("missing", "no-such-handle-zzzq.bsky.social")
}
```
Mong đợi (đã đo bằng curl trên máy tính ngày 2026-11-16): `existing found`, `ownPosts` từ 1 đến 20, `hasName=true`, `hasAvatar=true`; `missing notFound`. Nếu `RApp.okHttpClient` chưa gán lúc chạy (Hilt chưa inject), đọc `lateinit` lỗi: dừng, báo, không đoán.

- [ ] **Step 3: Chạy trên Tecno bằng script đã có (cổng riêng trước, rồi lệnh riêng)**

1. `zsh /Users/loitran/.claude/tmp-f29/gate.sh > /Users/loitran/.claude/tmp-f29/gate-f30.log 2>&1; echo gate_rc=$?; head -1 …` phải in `GATE OK` **trong một lệnh riêng**. Nếu dừng vì app khác ở foreground hay máy khóa: **dừng và hỏi owner**; **không bao giờ gửi lệnh tới Pixel**.
2. Build như Step 1 (`assembleDevDebug assembleDevDebugAndroidTest`) với helper có mặt; rồi chạy `zsh /Users/loitran/.claude/tmp-f29/loop.sh 1 BlueskyProbe` (nền, chờ `ALLDONE`). `loop.sh` lo sao lưu, cài, `am instrument --user 0 -w -r -e class …BlueskyProbe`, khôi phục byte-exact, gỡ gói test, kiểm xoay `1 0`.
3. Đọc log đã ghi (`adb -s 115333744A005844 logcat -d -s BskyProbe` **trước** khi gỡ; hoặc thêm `-e` bắt `INSTRUMENTATION_STATUS` trong `loop-run1.log`): chỉ các dòng `BskyProbe`.
4. **Không** chạy `MainActivity`, không quảng cáo, không bấm gì.

- [ ] **Step 4: Xóa helper, kiểm cây sạch**, `git status --short | grep -v idea` không còn `BlueskyProbe`.

- [ ] **Step 5: Tài liệu** (tiếng Việt, số thật)

Báo cáo `doc/task/done/FRONT-02g_bluesky-api_DONE.md` theo khuôn `FRONT-02c`: phạm vi, "Nói thẳng về phạm vi" (tên và ảnh đại diện tự khai, không xác minh; ảnh trong bài không tải; chỉ bài chính chủ; nguồn RSS cũ không tự chuyển; sync nền không đổi tên/icon; quay về RSS khi API lỗi; API có thể giới hạn tốc độ/đổi), đã làm, kiểm chứng JVM (số test thật, mutation: số đột biến, sống sót nào đã thêm test), kiểm chứng máy thật Tecno (số lượng từ `BskyProbe`; **không** ghi handle, tên, nội dung), chưa kiểm (hiển thị tên/icon trên màn hình, tải ảnh đại diện thật, giới hạn tốc độ, `MainActivity` bị quảng cáo che), commit. `doc/feature.md`: thêm mục FRONT-02g trước `## 🟡 In progress`. `doc/task/todo/FRONT-02_decentralized-web3-social.md`: cập nhật ghi chú đầu tệp (giữ trong `todo/`). Spec: thêm "Điều chỉnh khi thực thi" (nhận dạng bằng `actorFromRssUrl`, không sửa resolver; bỏ `profileHandle`; feed theo DID; chặn chuyển hướng bằng client riêng; `when` không toàn diện vì relaxed mock).

- [ ] **Step 6: Commit**

```bash
git add doc/feature.md doc/task/done/FRONT-02g_bluesky-api_DONE.md doc/task/todo/FRONT-02_decentralized-web3-social.md docs/superpowers/specs/2026-11-16-front-02g-bluesky-api-design.md
git commit -m "docs(front-02g): report the Bluesky API source with its verified and unverified parts

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
```

---

## Self-review

**Phủ spec.** Nhận dạng và đường đi: Task 4 (`actorFromRssUrl`, nhánh `search()`, quay về RSS, 400, `isFeedExist`, Local). Lấy dữ liệu (hai GET, host cố định, giới hạn thời gian/kích thước, chặn chuyển hướng, phân biệt 400): Task 2. Ánh xạ (bài chính chủ, tiêu đề 80 điểm mã, thoát HTML, embed một dòng, tên làm sạch, icon chỉ HTTPS Bluesky): Task 1. Lưu và đồng bộ nền (`BLUESKY_SOURCE_TYPE`, không đổi Room, sync không đổi tên/icon, `RssHelper`, precache, `subscribe()` giữ icon và `isFullContent=false`): Task 3. Kiểm chứng JVM, mutation, máy thật Tecno, báo cáo, `feature.md`: Task 5. **Lệch có chủ đích so với spec, đã ghi ở "Điều chỉnh":** không so DID để lọc trả lời (đo: 0 trả lời lọt); link bài dùng DID; client riêng chặn chuyển hướng; nhận dạng bằng địa chỉ RSS đã có; hàm `toArticles` không nhận handle.

**Quét placeholder.** Không còn các cụm placeholder bị cấm trong skill. Phần dựng `setUp` của `SubscribeViewModelBlueskyTest` đã được dán nguyên văn từ `SubscribeViewModelSocialTest` (đã đọc 2026-11-16), không còn tham chiếu. Hai điều xác nhận bằng lần chạy đầu (không đoán) đã được liệt kê (`setChunkedBody`, `mockk<BlueskyOutcome>()`, `AccountType.Fever`).

**Nhất quán kiểu và tên.** `BlueskyOutcome.{Found(result), NotFound, Failed}`, `BlueskyFetchResult(items, did, handle, displayName, avatarUrl)`, `BlueskyApiClient.fetch(actor): BlueskyOutcome`, `BlueskyFeedMapper.{feedUrl, feedName, cleanDisplayName, iconUrl, toArticles(profileDid, items, feedId, accountId, nowMillis, newId), actorFromRssUrl}`, `Feed.BLUESKY_SOURCE_TYPE` dùng nhất quán ở Task 1 đến 5. Số test: Task 1 là 22, Task 2 là 18, Task 3 là 9 + 3, Task 4 là 2 + 14; số thật lấy từ XML, không từ plan.

**Rủi ro còn lại, nói thẳng.** (1) `Call.execute()` trên luồng IO là chặn; hủy dựa vào `invokeOnCompletion { call.cancel() }`; nếu test hủy đỏ thì chuyển sang `suspendCancellableCoroutine` + `enqueue`. (2) Mỗi lần xem trước là hai yêu cầu mạng (RSS cũ: một). (3) `BlueskyApiClient` dùng `OkHttpClient` chung (có `ConditionalCacheInterceptor`, `UserAgentInterceptor`, DoH): có thể làm đệm HTTP lưu phản hồi API; kiểm khi chạy máy thật nếu `Cache-Control` làm tin cũ. (4) Chập chờn bước gõ chữ ở FRONT-02d chưa rõ gốc và quảng cáo xen kẽ che `MainActivity` (owner hoãn Ad/IAP/VIP): plan **không** có test thiết bị trên `MainActivity` hay gõ vào hộp thoại. (5) `/tmp` từng bị dọn; công cụ máy ở `/Users/loitran/.claude/tmp-f29`. (6) Lint không quét `androidTest`. (7) Nguồn RSS cũ và nguồn API của cùng tài khoản có địa chỉ khác nhau nên có thể đăng ký trùng; chấp nhận, đã ghi vào giới hạn.
