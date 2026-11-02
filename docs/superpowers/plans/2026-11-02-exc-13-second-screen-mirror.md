# EXC-13 Second Screen Mirror (một chiều, qua LAN) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Từ `ReadingPage`, người dùng bật "Second Screen" để xem bài đang đọc (văn bản thuần) trên trình duyệt máy tính cùng Wi-Fi, một chiều, không tài khoản, không thư viện mới.

**Architecture:** Logic thuần Kotlin ở `domain/mirror/` (token, bộ phân tích yêu cầu, giới hạn tốc độ, router, snapshot, trang HTML tĩnh) được test trên JVM. `infrastructure/mirror/MirrorServer` là `ServerSocket` JDK bind đúng địa chỉ LAN, phục vụ 3 đường GET và Server-Sent Events. `SecondScreenViewModel` điều khiển vòng đời; sheet nội tuyến trong `ReadingPage` theo mẫu `DualCardSheetContent`.

**Tech Stack:** Kotlin, JDK `ServerSocket`/`NetworkInterface`, `ConnectivityManager`, Hilt, coroutines/StateFlow, Compose Material3 1.0.1, JUnit4, MockK, Robolectric, `lifecycle-process:2.6.2` (đã có).

## Global Constraints

- Chỉ nhánh `dev`; không worktree; không commit `.idea/*`; không push; không nhắc hay gợi ý PR.
- Một chiều, chỉ đọc; chỉ văn bản thuần (tiêu đề, nguồn, ngày, đoạn văn). Không QR, không WebSocket (chỉ SSE), không TLS, không NSD/mDNS, không đổi Room (vẫn v20), không AI/API key/quảng cáo/IAP, **không thêm dependency** (không NanoHTTPD/Ktor/ZXing).
- Server bind **đúng địa chỉ IPv4 riêng** (10/8, 172.16/12, 192.168/16) của Wi-Fi hoặc Ethernet đang hoạt động, cổng do OS cấp (`port 0`), **không bao giờ** `0.0.0.0`. Không có địa chỉ thì trạng thái `NoNetwork` và không mở server.
- Token 128 bit từ `SecureRandom`, hex 32 ký tự, so sánh hằng thời gian, hết hạn 30 phút. Token sai, hết hạn hay chưa có server **trả cùng một 404**.
- Chỉ 3 đường: `GET /m/<token>`, `GET /m/<token>/state`, `GET /m/<token>/events`. Không query, không `%`, không dấu chấm, không dấu gạch chéo cuối.
- Hằng giới hạn (đúng tên): `TOKEN_BYTES=16`, `TOKEN_TTL_MILLIS=30 phút`, `MAX_CLIENTS=3`, `MAX_REQUESTS_PER_MINUTE=20` mỗi IP, `MAX_REQUEST_BYTES=8 KiB`, `MAX_HEADER_LINES=32`, `SOCKET_TIMEOUT_MILLIS=10 giây`, `KEEPALIVE_MILLIS=15 giây`, `MAX_PARAGRAPHS=400`, `MAX_PARAGRAPH_CHARS=4000`, `MAX_SNAPSHOT_BYTES=512 KiB`, `WORKER_THREADS=4`. Bổ sung so với spec: `MAX_PATH_CHARS=512`, `MAX_TRACKED_IPS=256`.
- Server tắt khi: tắt công tắc; rời `ReadingPage`; app xuống nền (`ProcessLifecycleOwner` ON_STOP); token hết hạn; mất mạng. Không foreground service.
- Chỉ gửi: tiêu đề, nguồn, ngày, đoạn văn thuần của đúng bài đang mở. Không bao giờ cài đặt, DataStore, API key, danh sách feed/bài khác.
- Spec amendment: thêm trạng thái `Expired` vào `MirrorUiState` (spec liệt kê `Off/NoNetwork/Starting/Running/Error`) để người dùng thấy vì sao phiên dừng.
- Quyền: `ACCESS_NETWORK_STATE` **đã có** trong manifest (dùng cho `ConnectivityManager`). **Không thêm** `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, foreground service hay quyền mới nào; địa chỉ lấy từ `LinkProperties`.
- Mọi `@HiltViewModel` chỉ có một constructor `@Inject`, không tham số kiểu hàm (dùng `internal var`). Test ViewModel tiêm dispatcher thử.
- UI: Material You, mọi chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh), không `ModalBottomSheet`, mọi chuỗi mới phải được dùng (lint `UnusedResources`), không dạng `%d` đi liền từ (lint `PluralsCandidate`; dùng "Viewers: %1$d").
- Thiết bị: **Samsung A50s `R58MA6WYRPE`** (Android 11: dùng `mResumedActivity`). Không đụng Tecno `115333744A005844` hay Pixel `2B051FDH3006MU`; không `connectedDevDebugAndroidTest` mà `adb -s R58MA6WYRPE shell am instrument -w -e class <Class> com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`. Helper có sẵn: `/tmp/a50-lib.sh`, `/tmp/a50-ad.sh`.
- Dữ liệu smoke: A50s hiện **không có Room** và có DataStore 50 byte do instrumentation tạo (bản sao `/tmp/front03-a50-initial/settings.preferences_pb`). Sao lưu trước, không bao giờ xoá DataStore, chỉ xoá 3 file Room tạm sau smoke. Seed 152 bài đã đọc: `/tmp/front03-seed/Reader`.
- Quảng cáo toàn màn hình: chỉ đóng bằng nút có nhãn ("Tiếp tục đến ứng dụng" hoặc "Continue to app") từ **dump mới**, thử lại khoảng một phút, không bấm creative, kiểm `mResumedActivity` trước mỗi lần chạm.
- Mỗi test hồi quy phải **mutation-check** (tắt bản sửa, thấy đỏ, khôi phục). Đọc lỗi thật từ `app/build/test-results/testDevDebugUnitTest/*.xml`. Viết script helper ra file, đọc output bằng Read; dùng `printf`, không `echo =====`. `git status --short | grep -v .idea` phải sạch trước mỗi commit; trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

---

## File map

Tạo mới (main): `domain/mirror/{MirrorLimits,MirrorToken,MirrorRateLimiter,MirrorRequestParser,MirrorRouter,MirrorSnapshot,MirrorSnapshotBuilder,MirrorHtml,LanAddress}.kt`, `infrastructure/mirror/{MirrorEngine,MirrorServer,LanAddressProvider,NetworkLossWatcher}.kt`, `ui/page/home/read/mirror/{MirrorUiState,SecondScreenViewModel,SecondScreenSheetContent}.kt`.
Sửa: `ui/page/home/read/{ReadingViewModel,ReadingPage,TopBar}.kt`, `app/src/main/res/values*/strings.xml`.
Test: `app/src/test/java/com/mckimquyen/reader/domain/mirror/*Test.kt`, `.../infrastructure/mirror/MirrorServerTest.kt`, `.../ui/page/home/read/mirror/{SecondScreenViewModelTest,SecondScreenSheetTest}.kt`, `.../ui/page/home/read/ReadingViewModelSecondScreenTest.kt`; instrumentation `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServerDeviceTest.kt`.
Hoàn tất: `doc/task/done/EXC-13_second-screen-mirror_DONE.md`, `doc/feature.md`, `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md` (dòng 23), `git rm doc/task/todo/EXC-13_second-screen-mirror.md`.

## Interfaces (tên và kiểu dùng xuyên suốt các task)

```kotlin
object MirrorLimits { const val TOKEN_BYTES = 16; const val TOKEN_TTL_MILLIS = 30L * 60_000; const val MAX_CLIENTS = 3
  const val MAX_REQUESTS_PER_MINUTE = 20; const val MAX_REQUEST_BYTES = 8 * 1024; const val MAX_HEADER_LINES = 32
  const val MAX_PATH_CHARS = 512; const val MAX_TRACKED_IPS = 256; const val SOCKET_TIMEOUT_MILLIS = 10_000
  const val KEEPALIVE_MILLIS = 15_000L; const val MAX_PARAGRAPHS = 400; const val MAX_PARAGRAPH_CHARS = 4_000
  const val MAX_SNAPSHOT_BYTES = 512 * 1024; const val WORKER_THREADS = 4; const val RATE_WINDOW_MILLIS = 60_000L }
class MirrorToken(val value: String, val expiresAtMillis: Long) {
  fun matches(candidate: String?, nowMillis: Long): Boolean
  companion object { fun generate(nowMillis: Long, random: SecureRandom = SecureRandom()): MirrorToken } }
class MirrorRateLimiter(clock: () -> Long) { fun tryRequest(ip: String): Boolean; fun tryOpenStream(): Boolean
  fun closeStream(); fun trackedIps(): Int }
data class MirrorRequest(val path: String)
sealed interface MirrorParseResult { data class Ok(val request: MirrorRequest) : MirrorParseResult
  data class Rejected(val status: Int) : MirrorParseResult }
object MirrorRequestParser { fun parse(head: String): MirrorParseResult }
sealed interface MirrorResponse { data class Page(val html: String); data class State(val json: String)
  object Events; object NotFound; companion object { const val NOT_FOUND_BODY = "Not found" } }
class MirrorRouter(token: () -> MirrorToken?, snapshot: () -> MirrorSnapshot?) { fun route(request: MirrorRequest, nowMillis: Long): MirrorResponse }
data class MirrorSnapshot(val title: String, val source: String, val dateMillis: Long, val paragraphs: List<String>,
  val truncated: Boolean, val version: Long) { fun toJson(): String }
object MirrorSnapshotBuilder { fun fromPlainText(title: String, source: String, dateMillis: Long, plainText: String, version: Long): MirrorSnapshot }
object MirrorHtml { const val PAGE: String }
object LanAddress { fun isPrivateIpv4(address: String): Boolean; fun pick(addresses: List<String>): String? }
sealed interface MirrorStartResult { data class Started(val port: Int, val token: MirrorToken) : MirrorStartResult
  data class Failed(val reason: MirrorFailure) : MirrorStartResult }
enum class MirrorFailure { BIND, ALREADY_RUNNING }
interface MirrorEngine { fun start(bindAddress: InetAddress): MirrorStartResult; fun stop(); fun publish(snapshot: MirrorSnapshot)
  val clients: StateFlow<Int>; val isRunning: Boolean }
sealed interface MirrorUiState { Off; NoNetwork; Starting; data class Running(val url: String, val clients: Int, val expiresAtMillis: Long)
  Expired; data class Error(val kind: MirrorFailure) }
```

---

### Task 1: Giới hạn, token và bộ giới hạn tốc độ (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/mirror/MirrorLimits.kt`, `MirrorToken.kt`, `MirrorRateLimiter.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/mirror/MirrorTokenTest.kt`, `MirrorRateLimiterTest.kt`

**Interfaces:**
- Consumes: không.
- Produces: `MirrorLimits`, `MirrorToken`, `MirrorRateLimiter` đúng như mục Interfaces.

- [ ] **Step 1: Viết test đỏ cho token.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class MirrorTokenTest {
    private val now = 1_000_000L

    @Test fun generate_makesHexOfTokenBytesTimesTwo() {
        val token = MirrorToken.generate(now)
        assertEquals(MirrorLimits.TOKEN_BYTES * 2, token.value.length)
        assertTrue(token.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun generate_isDifferentEveryCall() {
        assertEquals(200, (1..200).map { MirrorToken.generate(now).value }.toSet().size)
    }

    @Test fun generate_usesTheGivenRandomSourceAndTtl() {
        val fixed = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { bytes.fill(0xAB.toByte()) } }
        val token = MirrorToken.generate(now, fixed)
        assertEquals("ab".repeat(MirrorLimits.TOKEN_BYTES), token.value)
        assertEquals(now + MirrorLimits.TOKEN_TTL_MILLIS, token.expiresAtMillis)
    }

    @Test fun matches_trueOnlyForTheExactValueBeforeExpiry() {
        val token = MirrorToken.generate(now)
        assertTrue(token.matches(token.value, now))
        assertTrue(token.matches(token.value, token.expiresAtMillis - 1))
        assertFalse(token.matches(token.value, token.expiresAtMillis))
        assertFalse(token.matches(token.value, token.expiresAtMillis + 1))
    }

    @Test fun matches_falseForNullEmptyShorterLongerAndChangedCase() {
        val token = MirrorToken("abcdef0123456789abcdef0123456789", now + 10)
        assertFalse(token.matches(null, now))
        assertFalse(token.matches("", now))
        assertFalse(token.matches("abcdef", now))
        assertFalse(token.matches(token.value + "0", now))
        assertFalse(token.matches(token.value.uppercase(), now))
        assertFalse(token.matches("abcdef0123456789abcdef012345678a", now))
    }
}
```

- [ ] **Step 2: Viết test đỏ cho bộ giới hạn.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRateLimiterTest {
    private var now = 0L
    private val limiter = MirrorRateLimiter { now }

    @Test fun allowsExactlyTheLimitPerMinuteThenRejects() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { assertTrue("request $it", limiter.tryRequest("10.0.0.2")) }
        assertFalse(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun windowSlides() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        now = MirrorLimits.RATE_WINDOW_MILLIS - 1
        assertFalse(limiter.tryRequest("10.0.0.2"))
        now = MirrorLimits.RATE_WINDOW_MILLIS
        assertTrue(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun rejectedRequestsDoNotExtendTheBlock() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        now = 30_000
        repeat(50) { limiter.tryRequest("10.0.0.2") }
        now = MirrorLimits.RATE_WINDOW_MILLIS
        assertTrue(limiter.tryRequest("10.0.0.2"))
    }

    @Test fun ipsAreIndependent() {
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { limiter.tryRequest("10.0.0.2") }
        assertTrue(limiter.tryRequest("10.0.0.3"))
    }

    @Test fun streamsAreCappedAndFreedOnClose() {
        repeat(MirrorLimits.MAX_CLIENTS) { assertTrue(limiter.tryOpenStream()) }
        assertFalse(limiter.tryOpenStream())
        limiter.closeStream()
        assertTrue(limiter.tryOpenStream())
    }

    @Test fun closingMoreThanOpenedNeverGoesNegative() {
        repeat(5) { limiter.closeStream() }
        repeat(MirrorLimits.MAX_CLIENTS) { assertTrue(limiter.tryOpenStream()) }
        assertFalse(limiter.tryOpenStream())
    }

    @Test fun trackedIpsStayBoundedWhenManyAddressesAppear() {
        repeat(1_000) { limiter.tryRequest("10.1.${it / 250}.${it % 250}") }
        assertTrue(limiter.trackedIps() <= MirrorLimits.MAX_TRACKED_IPS)
    }
}
```

- [ ] **Step 3: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*MirrorTokenTest" --tests "*MirrorRateLimiterTest" -q`. Mong đợi: lỗi biên dịch `Unresolved reference` (chưa có `MirrorToken`, `MirrorLimits`, `MirrorRateLimiter`).
- [ ] **Step 4: Cài đặt tối thiểu.** `MirrorLimits` đúng bảng hằng ở mục Interfaces. `MirrorToken.generate` lấy `TOKEN_BYTES` byte từ `random.nextBytes`, mã hoá hex chữ thường, `expiresAtMillis = nowMillis + TOKEN_TTL_MILLIS`. `matches` trả `false` nếu `candidate == null`, hết hạn (`nowMillis >= expiresAtMillis`), hoặc khác độ dài; còn lại so bằng `MessageDigest.isEqual(value.toByteArray(Charsets.US_ASCII), candidate.toByteArray(Charsets.US_ASCII))` (hằng thời gian, không `==`). `MirrorRateLimiter`: `HashMap<String, ArrayDeque<Long>>` dấu thời gian của các request **được chấp nhận**; `tryRequest` bỏ mốc `<= now - RATE_WINDOW_MILLIS`, từ chối nếu còn `>= MAX_REQUESTS_PER_MINUTE`, ngược lại ghi nhận (request bị từ chối không ghi mốc). Khi `size >= MAX_TRACKED_IPS` mà IP mới xuất hiện thì xoá các IP có hàng đợi rỗng sau khi dọn, rồi nếu vẫn đầy thì xoá IP có mốc cũ nhất. `tryOpenStream`/`closeStream` dùng một bộ đếm `Int` có `synchronized`, `closeStream` không xuống dưới 0.
- [ ] **Step 5: Chạy xanh.** Cùng lệnh Step 3; đọc XML: 5 + 7 test, 0 lỗi.
- [ ] **Step 6: Mutation-check.** Đổi `MessageDigest.isEqual` thành kiểm tra `startsWith`: test `matches_falseForNullEmptyShorterLongerAndChangedCase` phải đỏ (nó có ca `+ "0"`). Khôi phục, chạy lại xanh.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/mirror app/src/test/java/com/mckimquyen/reader/domain/mirror && git commit -m "feat(exc-13): add mirror token and rate limiter"` (+ trailer).

---

### Task 2: Bộ phân tích yêu cầu, router và trang HTML tĩnh (thuần)

**Files:**
- Create: `domain/mirror/MirrorRequestParser.kt`, `MirrorRouter.kt`, `MirrorHtml.kt` (và `MirrorSnapshot.kt` rút gọn dưới đây)
- Test: `MirrorRequestParserTest.kt`, `MirrorRouterTest.kt`, `MirrorHtmlTest.kt`

**Interfaces:**
- Consumes: `MirrorToken`, `MirrorLimits` từ Task 1.
- Produces: `MirrorRequest`, `MirrorParseResult`, `MirrorRequestParser.parse`, `MirrorResponse`, `MirrorRouter.route`, `MirrorHtml.PAGE`, và lớp `MirrorSnapshot` (khai báo ở đây để router biên dịch; `toJson()` hoàn thiện ở Task 3, tạm trả `"{}"` cho tới khi test Task 3 đỏ).

- [ ] **Step 1: Test đỏ cho bộ phân tích.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRequestParserTest {
    private fun get(path: String) = "GET $path HTTP/1.1\r\nHost: x\r\n\r\n"
    private fun rejected(head: String) = (MirrorRequestParser.parse(head) as MirrorParseResult.Rejected).status

    @Test fun parsesAGetRequestLine() {
        assertEquals(MirrorParseResult.Ok(MirrorRequest("/m/abc")), MirrorRequestParser.parse(get("/m/abc")))
        assertEquals(MirrorParseResult.Ok(MirrorRequest("/")), MirrorRequestParser.parse("GET / HTTP/1.0\r\n\r\n"))
    }

    @Test fun nonGetMethodsGet405() {
        listOf("POST", "HEAD", "PUT", "DELETE", "OPTIONS", "get").forEach {
            assertEquals(it, 405, rejected("$it / HTTP/1.1\r\n\r\n"))
        }
    }

    @Test fun malformedRequestLinesGet400() {
        listOf("GET /\r\n\r\n", "GET / HTTP/1.1 extra\r\n\r\n", "\r\n\r\n", "GET  / HTTP/1.1\r\n\r\n",
            "GET / FTP/1.1\r\n\r\n", "GET http://x/ HTTP/1.1\r\n\r\n", "GET m HTTP/1.1\r\n\r\n").forEach {
            assertEquals(it, 400, rejected(it))
        }
    }

    @Test fun lineEndingsMustBeCrlf() {
        assertEquals(400, rejected("GET / HTTP/1.1\n\n"))
    }

    @Test fun controlAndNonAsciiCharactersGet400() {
        assertEquals(400, rejected(get("/m/\u0000")))
        assertEquals(400, rejected(get("/m/é")))
        assertEquals(400, rejected(get("/m/a\tb")))
    }

    @Test fun overlongPathGets414() {
        assertEquals(414, rejected(get("/" + "a".repeat(MirrorLimits.MAX_PATH_CHARS))))
    }

    @Test fun tooManyHeaderLinesGet400() {
        val headers = (1..MirrorLimits.MAX_HEADER_LINES + 1).joinToString("") { "H$it: v\r\n" }
        assertEquals(400, rejected("GET / HTTP/1.1\r\n$headers\r\n"))
    }

    @Test fun oversizedHeadGets431() {
        val big = "X: " + "a".repeat(MirrorLimits.MAX_REQUEST_BYTES) + "\r\n"
        assertEquals(431, rejected("GET / HTTP/1.1\r\n$big\r\n"))
    }
}
```

- [ ] **Step 2: Test đỏ cho router.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRouterTest {
    private val now = 5_000L
    private val token = MirrorToken("0123456789abcdef0123456789abcdef", now + 60_000)
    private val snapshot = MirrorSnapshot("T", "S", 1L, listOf("p"), false, 1L)
    private fun router(t: MirrorToken? = token, s: MirrorSnapshot? = snapshot) = MirrorRouter({ t }, { s })
    private fun route(path: String, at: Long = now, r: MirrorRouter = router()) = r.route(MirrorRequest(path), at)
    private val t = token.value

    @Test fun threeRoutesWithAValidToken() {
        assertEquals(MirrorResponse.Page(MirrorHtml.PAGE), route("/m/$t"))
        assertEquals(MirrorResponse.State(snapshot.toJson()), route("/m/$t/state"))
        assertEquals(MirrorResponse.Events, route("/m/$t/events"))
    }

    @Test fun stateWithoutASnapshotIsAnEmptyButValidJson() {
        val state = route("/m/$t/state", r = router(s = null)) as MirrorResponse.State
        assertTrue(state.json.startsWith("{") && state.json.endsWith("}"))
    }

    @Test fun wrongExpiredAndMissingTokensAllGiveTheSameNotFound() {
        val wrong = route("/m/" + "f".repeat(32))
        val expired = route("/m/$t", at = token.expiresAtMillis)
        val none = route("/m/$t", r = router(t = null))
        assertEquals(MirrorResponse.NotFound, wrong)
        assertEquals(wrong, expired)
        assertEquals(wrong, none)
    }

    @Test fun everyOtherPathIsNotFound() {
        listOf("/", "/m", "/m/", "/m/$t/", "/m/$t/state/", "/m/$t/other", "/m/$t/../state", "/m/$t%2fstate",
            "/m/$t/%73tate", "/m/$t?x=1", "/m/${t.uppercase()}", "//m/$t", "/m/$t/events/x", "/m/$t/state?a", "/m//$t",
            "/favicon.ico", "/robots.txt", "/m/${t}x", "/m/${t.dropLast(1)}", "/../etc/passwd", "/m/$t/./state").forEach {
            assertEquals(it, MirrorResponse.NotFound, route(it))
        }
    }

    @Test fun routingNeverTouchesTheSnapshotForAWrongToken() {
        var reads = 0
        val r = MirrorRouter({ token }, { reads++; snapshot })
        r.route(MirrorRequest("/m/" + "0".repeat(32) + "/state"), now)
        assertEquals(0, reads)
    }
}
```

- [ ] **Step 3: Test đỏ cho trang HTML.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorHtmlTest {
    private val page = MirrorHtml.PAGE

    @Test fun buildsTheDomFromTextContentOnly() {
        assertTrue(page.contains("textContent"))
        listOf("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function", "srcdoc").forEach {
            assertFalse(it, page.contains(it))
        }
    }

    @Test fun referencesNoExternalResource() {
        assertFalse(page.contains("http://"))
        assertFalse(page.contains("https://"))
        assertFalse(page.contains("//cdn", ignoreCase = true))
        assertFalse(Regex("""<(script|link|img|iframe|object|embed)[^>]+(src|href)\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(page))
    }

    @Test fun usesTheCurrentPathSoTheTokenStaysInTheAddress() {
        assertTrue(page.contains("location.pathname"))
        assertTrue(page.contains("EventSource"))
        assertTrue(page.contains("'/state'") && page.contains("'/events'"))
    }

    @Test fun supportsLightAndDarkAndDeclaresTheDocumentLanguageAndViewport() {
        assertTrue(page.contains("prefers-color-scheme"))
        assertTrue(page.contains("<meta name=\"viewport\""))
        assertTrue(page.contains("<html lang=\"en\">"))
    }
}
```

- [ ] **Step 4: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*MirrorRequestParserTest" --tests "*MirrorRouterTest" --tests "*MirrorHtmlTest" -q`; mong đợi `Unresolved reference`.
- [ ] **Step 5: Cài đặt.** `MirrorRequestParser.parse(head)`: nếu `head.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES` → `Rejected(431)`; tách theo `"\r\n"`, dòng đầu là request line; nếu `head` chứa `\n` không đi sau `\r` → `Rejected(400)`; số dòng header (trước dòng rỗng) `> MAX_HEADER_LINES` → 400; request line phải đúng 3 phần tách bằng **một** khoảng trắng; method khác `"GET"` (phân biệt hoa thường) → 405 (kiểm sau khi đã đủ 3 phần); version phải khớp `HTTP/1\.[01]`; path phải bắt đầu bằng `/` (không `http://`), mọi ký tự trong `0x21..0x7E` (control, tab, non-ASCII → 400); độ dài path `> MAX_PATH_CHARS` → 414 (kiểm trước ký tự lạ nếu path chỉ dài). `MirrorRouter.route`: một `Regex("^/m/([0-9a-f]{32})(/state|/events)?$")`, không giải mã gì cả; chỉ khi khớp mới đọc `token()` và gọi `matches(group1, nowMillis)`; sai bất kỳ điều gì → `MirrorResponse.NotFound` (cùng một object). Chỉ sau khi token đúng mới gọi `snapshot()`; `/state` trả `State(snapshot?.toJson() ?: MirrorSnapshot.EMPTY_JSON)` với `EMPTY_JSON = """{"title":"","source":"","date":0,"paragraphs":[],"truncated":false,"version":0}"""`. `MirrorHtml.PAGE`: một hằng chuỗi `<!doctype html><html lang="en">` có `<meta name="viewport" content="width=device-width,initial-scale=1">`, CSS nội tuyến với `prefers-color-scheme`, và một `<script>` nội tuyến dùng `const base = location.pathname`, `fetch(base + '/state')`, `new EventSource(base + '/events')`, đặt mọi nội dung bằng `element.textContent = ...` và `document.createElement`, xử lý sự kiện `ended` bằng dòng "Session ended" và dừng EventSource (`es.close()`). Không có URL tuyệt đối, không thẻ tải tài nguyên ngoài. Chữ trên trang là tiếng Anh cố định (trình duyệt máy tính không theo ngôn ngữ app); ghi vào DONE doc.
- [ ] **Step 6: Chạy xanh.** Cùng lệnh Step 4; đọc XML: parser 8, router 5, html 4, 0 lỗi.
- [ ] **Step 7: Mutation-check.** (a) Đổi regex router thành `^/m/([0-9a-f]{32})(/state|/events)?/?$`: `everyOtherPathIsNotFound` phải đỏ. (b) Thêm `innerHTML` vào chuỗi trang: `buildsTheDomFromTextContentOnly` phải đỏ. (c) Trả `Rejected(400)` thay vì 405 cho `POST`: `nonGetMethodsGet405` đỏ. Khôi phục sau mỗi lần, chạy lại xanh.
- [ ] **Step 8: Commit.** `feat(exc-13): add request parser, router and static page` (+ trailer).

### Task 3: Snapshot nội dung và bộ dựng văn bản thuần

**Files:**
- Modify: `domain/mirror/MirrorSnapshot.kt` (hoàn thiện `toJson`, `EMPTY_JSON`)
- Create: `domain/mirror/MirrorSnapshotBuilder.kt`
- Test: `MirrorSnapshotBuilderTest.kt`, `MirrorSnapshotJsonTest.kt`

**Interfaces:**
- Consumes: `MirrorLimits`.
- Produces: `MirrorSnapshotBuilder.fromPlainText(title, source, dateMillis, plainText, version): MirrorSnapshot` và `MirrorSnapshot.toJson()` (JSON hợp lệ, escape đầy đủ).
- Ghi chú: HTML→text **không** nằm trong builder thuần. App đã dùng `androidx.core.text.HtmlCompat.fromHtml(content, FROM_HTML_MODE_LEGACY).toString()` ở `ReadingViewModel` (dòng 306, 642, 716, 775, 849); Task 6 gọi đúng cách đó trên `Dispatchers.Default` rồi đưa kết quả cho builder. Nhờ vậy builder test được trên JVM thuần.

- [ ] **Step 1: Test đỏ cho builder.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSnapshotBuilderTest {
    private fun build(text: String, title: String = "T", source: String = "S") =
        MirrorSnapshotBuilder.fromPlainText(title, source, 1L, text, 7L)

    @Test fun splitsParagraphsOnBlankLinesAndTrims() {
        assertEquals(listOf("one", "two", "three"), build("  one \n\n two\n \n\n\nthree  ").paragraphs)
    }

    @Test fun singleNewlinesInsideAParagraphBecomeSpaces() {
        assertEquals(listOf("line one line two"), build("line one\nline two").paragraphs)
    }

    @Test fun emptyAndBlankTextGiveNoParagraphs() {
        assertTrue(build("").paragraphs.isEmpty())
        assertTrue(build(" \n\n \t ").paragraphs.isEmpty())
        assertFalse(build("").truncated)
    }

    @Test fun controlCharactersAreRemovedButTabsAndUnicodeSurvive() {
        val s = build("a\u0000b\u0007c\u001B[31m d\u0085e f 😀 Tiếng Việt")
        assertEquals(1, s.paragraphs.size)
        assertFalse(s.paragraphs[0].any { it.isISOControl() && it != '\t' })
        assertTrue(s.paragraphs[0].contains("Tiếng Việt"))
        assertTrue(s.paragraphs[0].contains("😀"))
    }

    @Test fun unpairedSurrogatesAreDropped() {
        val s = build("ok \uD83D end")
        assertEquals("ok  end".replace("  ", " "), s.paragraphs.single().replace("  ", " "))
    }

    @Test fun longParagraphsAreCutAtTheCharCapAndFlagged() {
        val s = build("x".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS + 500))
        assertEquals(MirrorLimits.MAX_PARAGRAPH_CHARS, s.paragraphs.single().length)
        assertTrue(s.truncated)
    }

    @Test fun aCutNeverSplitsASurrogatePair() {
        val text = "a".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS - 1) + "😀" + "tail"
        val p = build(text).paragraphs.single()
        assertTrue(p.length <= MirrorLimits.MAX_PARAGRAPH_CHARS)
        assertFalse(Character.isHighSurrogate(p.last()))
    }

    @Test fun paragraphCountIsCapped() {
        val s = build((1..MirrorLimits.MAX_PARAGRAPHS + 50).joinToString("\n\n") { "p$it" })
        assertEquals(MirrorLimits.MAX_PARAGRAPHS, s.paragraphs.size)
        assertTrue(s.truncated)
    }

    @Test fun serialisedSizeStaysUnderTheByteCap() {
        val big = (1..MirrorLimits.MAX_PARAGRAPHS).joinToString("\n\n") { "é".repeat(MirrorLimits.MAX_PARAGRAPH_CHARS) }
        val s = build(big)
        assertTrue(s.toJson().toByteArray(Charsets.UTF_8).size <= MirrorLimits.MAX_SNAPSHOT_BYTES)
        assertTrue(s.truncated)
        assertTrue(s.paragraphs.isNotEmpty())
    }

    @Test fun titleAndSourceAreCappedAndCleaned() {
        val s = build("x", title = "t\u0000" + "a".repeat(2_000), source = "s\n" + "b".repeat(500))
        assertTrue(s.title.length <= MirrorSnapshotBuilder.MAX_TITLE_CHARS)
        assertTrue(s.source.length <= MirrorSnapshotBuilder.MAX_SOURCE_CHARS)
        assertFalse(s.title.contains('\u0000'))
        assertFalse(s.source.contains('\n'))
    }

    @Test fun versionAndDateAreKept() {
        val s = build("x")
        assertEquals(7L, s.version)
        assertEquals(1L, s.dateMillis)
    }
}
```

- [ ] **Step 2: Test đỏ cho JSON.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MirrorSnapshotJsonTest {
    private fun parse(s: MirrorSnapshot) = JSONObject(s.toJson())

    @Test fun roundTripsEveryField() {
        val json = parse(MirrorSnapshot("Title", "BBC", 123L, listOf("a", "b"), true, 9L))
        assertEquals("Title", json.getString("title"))
        assertEquals("BBC", json.getString("source"))
        assertEquals(123L, json.getLong("date"))
        assertEquals(listOf("a", "b"), (0 until json.getJSONArray("paragraphs").length()).map { json.getJSONArray("paragraphs").getString(it) })
        assertTrue(json.getBoolean("truncated"))
        assertEquals(9L, json.getLong("version"))
    }

    @Test fun escapesQuotesBackslashesNewlinesAndAngleBrackets() {
        val nasty = "\"quoted\" \\ back \n line </script><script>alert(1)</script>    "
        val raw = MirrorSnapshot(nasty, nasty, 0, listOf(nasty), false, 1).toJson()
        assertEquals(nasty, JSONObject(raw).getString("title"))
        assertFalse("raw JSON must not close a script element", raw.contains("</script>"))
        assertFalse(raw.contains(' '))
        assertFalse(raw.contains(' '))
    }

    @Test fun emptyJsonConstantIsValid() {
        val json = JSONObject(MirrorSnapshot.EMPTY_JSON)
        assertEquals(0, json.getJSONArray("paragraphs").length())
    }
}
```

- [ ] **Step 3: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*MirrorSnapshotBuilderTest" --tests "*MirrorSnapshotJsonTest" -q`. Mong đợi: lỗi biên dịch cho `MirrorSnapshotBuilder`, `MAX_TITLE_CHARS`, `MAX_SOURCE_CHARS`; hoặc test đỏ do `toJson()` còn trả `"{}"`.
- [ ] **Step 4: Cài đặt.** `MirrorSnapshotBuilder.MAX_TITLE_CHARS = 300`, `MAX_SOURCE_CHARS = 120`. `clean(text)`: bỏ mọi ký tự `isISOControl()` trừ `\t`, `\n`, `\r`; bỏ ` `/` `/`\u0085`; bỏ surrogate không thành cặp; chuẩn hoá `\r\n`→`\n`. Tách đoạn bằng regex `\n\s*\n+`, `trim`, đổi `\n` còn lại thành một khoảng trắng, bỏ đoạn rỗng. Mỗi đoạn `> MAX_PARAGRAPH_CHARS` thì cắt và nếu ký tự cuối là high surrogate thì bỏ nó, đặt `truncated`. Lấy tối đa `MAX_PARAGRAPHS`. Sau đó lặp: nếu `toJson().toByteArray(UTF_8).size > MAX_SNAPSHOT_BYTES` thì bỏ đoạn cuối, đặt `truncated`. `toJson()` dựng bằng `StringBuilder`, tự escape `"`, `\`, `\n`, `\r`, `\t`, mọi ký tự `< 0x20` (`\u00XX`), **và** `<`, `>`, `&`, ` `, ` ` dưới dạng `\uXXXX`, để chuỗi không bao giờ chứa `</script>`. Không dùng `org.json` trong mã chính (test dùng Robolectric chỉ để đọc lại).
- [ ] **Step 5: Chạy xanh.** Cùng lệnh Step 3; đọc XML: builder 11, JSON 3.
- [ ] **Step 6: Mutation-check.** Bỏ phần escape `<`: `escapesQuotes...AngleBrackets` phải đỏ. Bỏ vòng lặp giảm theo byte: `serialisedSizeStaysUnderTheByteCap` phải đỏ. Khôi phục, chạy lại xanh.
- [ ] **Step 7: Commit.** `feat(exc-13): build capped plain-text snapshots` (+ trailer).

---

### Task 4: Chọn địa chỉ LAN (thuần) và nguồn địa chỉ Android

**Files:**
- Create: `domain/mirror/LanAddress.kt`, `infrastructure/mirror/LanAddressProvider.kt`
- Test: `LanAddressTest.kt`

**Interfaces:**
- Consumes: không.
- Produces: `LanAddress.isPrivateIpv4(address)`, `LanAddress.pick(addresses): String?`; `LanAddressProvider(context).current(): InetAddress?` và `LanAddressProvider.networkLosses(): Flow<Unit>` (dùng ở Task 7).

- [ ] **Step 1: Test đỏ.**

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressTest {
    @Test fun acceptsExactlyThePrivateIpv4Ranges() {
        listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.0.1", "192.168.255.254").forEach {
            assertTrue(it, LanAddress.isPrivateIpv4(it))
        }
    }

    @Test fun rejectsPublicLoopbackLinkLocalAnyAndOutOfRangeAddresses() {
        listOf("0.0.0.0", "127.0.0.1", "169.254.1.1", "8.8.8.8", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.1.1",
            "100.64.0.1", "224.0.0.1", "255.255.255.255", "11.0.0.1").forEach {
            assertFalse(it, LanAddress.isPrivateIpv4(it))
        }
    }

    @Test fun rejectsMalformedAndNonIpv4Text() {
        listOf("", " ", "10.0.0", "10.0.0.256", "10.0.0.1.1", "10.0.0.x", "010.0.0.1", "fe80::1", "::1", "10.0.0.-1", "10.0.0.1 ", "+10.0.0.1")
            .forEach { assertFalse("'$it'", LanAddress.isPrivateIpv4(it)) }
    }

    @Test fun pickReturnsTheFirstPrivateAddressInGivenOrder() {
        assertEquals("192.168.1.20", LanAddress.pick(listOf("fe80::1", "8.8.8.8", "192.168.1.20", "10.0.0.5")))
    }

    @Test fun pickReturnsNullWhenThereIsNoPrivateAddress() {
        assertNull(LanAddress.pick(emptyList()))
        assertNull(LanAddress.pick(listOf("127.0.0.1", "8.8.8.8", "169.254.9.9")))
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*LanAddressTest" -q`; mong đợi `Unresolved reference: LanAddress`.
- [ ] **Step 3: Cài đặt `LanAddress`.** `isPrivateIpv4`: khớp `^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$`, mỗi phần **không có số 0 đứng đầu** (trừ "0") và `0..255`; rồi kiểm `a==10`, hoặc `a==172 && b in 16..31`, hoặc `a==192 && b==168`. `pick` trả phần tử đầu thoả `isPrivateIpv4`, hoặc `null`.
- [ ] **Step 4: Chạy xanh** (5 test). Mutation-check: đổi `b in 16..31` thành `b in 16..32`: `rejectsPublic...` phải đỏ (172.32.0.1). Khôi phục.
- [ ] **Step 5: Cài `LanAddressProvider` (hạ tầng, không test JVM).** Một class `@Singleton` với `@ApplicationContext context`: `current()` lấy `ConnectivityManager.activeNetwork` rồi `getLinkProperties(network)?.linkAddresses`, lọc `Inet4Address`, chỉ lấy khi `getNetworkCapabilities(network)` có `TRANSPORT_WIFI` hoặc `TRANSPORT_ETHERNET` (không dùng mạng di động), đưa chuỗi `hostAddress` vào `LanAddress.pick`, trả `InetAddress.getByName(picked)` hoặc `null`. `networkLosses()` là `callbackFlow` đăng ký `registerDefaultNetworkCallback`; `onLost` và `onLinkPropertiesChanged` (khi địa chỉ đổi) đẩy `Unit`; `awaitClose { unregisterNetworkCallback }` (không rò callback). Dùng `ACCESS_NETWORK_STATE` đã có; **không thêm quyền**. Không dùng `WifiManager` (cần `ACCESS_WIFI_STATE`, và `connectionInfo.ipAddress` đã bị loại bỏ).
- [ ] **Step 6: Biên dịch.** `./gradlew compileDevDebugKotlin -q`; mong đợi thành công.
- [ ] **Step 7: Commit.** `feat(exc-13): pick a private LAN address for the mirror` (+ trailer).

---

### Task 5: `MirrorServer` trên `ServerSocket` thật

**Files:**
- Create: `infrastructure/mirror/MirrorEngine.kt`, `MirrorServer.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServerTest.kt`

**Interfaces:**
- Consumes: `MirrorToken`, `MirrorRateLimiter`, `MirrorRequestParser`, `MirrorRouter`, `MirrorSnapshot`, `MirrorLimits`.
- Produces: `MirrorEngine` (interface), `MirrorServer(clock: () -> Long = System::currentTimeMillis, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : MirrorEngine`; `MirrorStartResult`, `MirrorFailure` (xem mục Interfaces).

Thiết kế: `start(bindAddress)` tạo `ServerSocket(0, 50, bindAddress)`; nếu đã chạy trả `Failed(ALREADY_RUNNING)` mà không thay token; lỗi `IOException` trả `Failed(BIND)`. Một luồng accept và `Executors.newFixedThreadPool(WORKER_THREADS)`. Mỗi kết nối: `soTimeout = SOCKET_TIMEOUT_MILLIS`; đọc đến `\r\n\r\n` nhưng **không quá** `MAX_REQUEST_BYTES` (quá thì 431 rồi đóng); `parse` → router. Phản hồi luôn có `Connection: close`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`; trang HTML thêm `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'`. Quá tốc độ → 429 kèm `Retry-After: 60`. SSE: `tryOpenStream()` sai → 503; đúng thì gửi `event: article` kèm `data: <json>` ngay và mỗi lần `publish`, nhịp `: keepalive` mỗi `KEEPALIVE_MILLIS`, đóng khi stop, hết hạn (gửi `event: ended`) hoặc client ngắt; luôn `closeStream()` trong `finally`. `stop()` idempotent: đặt cờ, đóng `ServerSocket`, đóng mọi socket đang theo dõi, `shutdownNow` pool; `clients` về 0.

- [ ] **Step 1: Test đỏ (JVM thuần, loopback thật).**

```kotlin
package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorLimits
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

class MirrorServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private var now = 1_000L
    private val server = MirrorServer(clock = { now })
    private val snapshot = MirrorSnapshot("Hello", "BBC", 5L, listOf("p1", "p2"), false, 1L)

    @After fun tearDown() = server.stop()

    private fun startOk(): MirrorStartResult.Started =
        server.start(loopback) as MirrorStartResult.Started

    private data class Reply(val status: Int, val headers: Map<String, String>, val body: String)

    private fun get(port: Int, path: String, method: String = "GET"): Reply =
        Socket(loopback, port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write("$method $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val raw = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = raw.substringBefore("\r\n\r\n")
            val lines = head.split("\r\n")
            Reply(
                lines[0].split(" ")[1].toInt(),
                lines.drop(1).associate { it.substringBefore(": ").lowercase() to it.substringAfter(": ") },
                raw.substringAfter("\r\n\r\n", ""),
            )
        }

    @Test fun startBindsTheGivenAddressOnARandomPortAndReturnsAToken() {
        val started = startOk()
        assertTrue(started.port in 1..65535)
        assertEquals(32, started.token.value.length)
        assertTrue(server.isRunning)
    }

    @Test fun servesThePageAndStateForTheTokenAndNothingElse() {
        val started = startOk()
        server.publish(snapshot)
        val page = get(started.port, "/m/${started.token.value}")
        assertEquals(200, page.status)
        assertTrue(page.headers.getValue("content-type").startsWith("text/html"))
        assertTrue(page.body.contains("EventSource"))
        val state = get(started.port, "/m/${started.token.value}/state")
        assertEquals(200, state.status)
        assertTrue(state.headers.getValue("content-type").startsWith("application/json"))
        assertTrue(state.body.contains("\"title\":\"Hello\""))
        assertEquals(404, get(started.port, "/").status)
        assertEquals(404, get(started.port, "/m/" + "0".repeat(32)).status)
    }

    @Test fun securityHeadersAreSentOnEveryReply() {
        val started = startOk()
        listOf("/m/${started.token.value}", "/m/${started.token.value}/state", "/nope").forEach {
            val h = get(started.port, it).headers
            assertEquals(it, "no-store", h["cache-control"])
            assertEquals(it, "nosniff", h["x-content-type-options"])
            assertEquals(it, "no-referrer", h["referrer-policy"])
            assertEquals(it, "close", h["connection"])
        }
        val csp = get(started.port, "/m/${started.token.value}").headers["content-security-policy"]
        assertNotNull(csp)
        assertTrue(csp!!.contains("default-src 'none'"))
    }

    @Test fun nonGetAndBrokenRequestsAreRejectedWithoutKillingTheServer() {
        val started = startOk()
        assertEquals(405, get(started.port, "/m/${started.token.value}", method = "POST").status)
        Socket(loopback, started.port).use { it.getOutputStream().write("garbage\r\n\r\n".toByteArray()) }
        assertEquals(200, get(started.port, "/m/${started.token.value}/state").status)
    }

    @Test fun anOversizedRequestHeadGets431() {
        val started = startOk()
        val reply = Socket(loopback, started.port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write(("GET / HTTP/1.1\r\nX: " + "a".repeat(MirrorLimits.MAX_REQUEST_BYTES * 2) + "\r\n\r\n").toByteArray())
            s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(reply.startsWith("HTTP/1.1 431"))
    }

    @Test fun requestsOverTheMinuteLimitGet429AndAnotherMinuteResetsIt() {
        val started = startOk()
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { assertEquals(404, get(started.port, "/x").status) }
        val limited = get(started.port, "/x")
        assertEquals(429, limited.status)
        assertEquals("60", limited.headers["retry-after"])
        now += MirrorLimits.RATE_WINDOW_MILLIS
        assertEquals(404, get(started.port, "/x").status)
    }

    @Test fun eventsStreamSendsTheCurrentArticleThenEachNewOne() {
        val started = startOk()
        server.publish(snapshot)
        Socket(loopback, started.port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val reader = s.getInputStream().bufferedReader()
            val lines = mutableListOf<String>()
            fun readUntil(text: String) { while (lines.none { it.contains(text) }) lines += reader.readLine() ?: error("closed early: $lines") }
            readUntil("\"title\":\"Hello\"")
            assertTrue(lines.any { it.startsWith("HTTP/1.1 200") })
            assertTrue(lines.any { it.lowercase().startsWith("content-type: text/event-stream") })
            assertTrue(lines.contains("event: article"))
            server.publish(snapshot.copy(title = "Second", version = 2))
            readUntil("\"title\":\"Second\"")
        }
    }

    @Test fun aFourthStreamIsRefusedWith503() {
        val started = startOk()
        val open = (1..MirrorLimits.MAX_CLIENTS).map {
            Socket(loopback, started.port).also { s ->
                s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                s.soTimeout = 5_000
                s.getInputStream().bufferedReader().readLine()
            }
        }
        try {
            waitUntil { server.clients.value == MirrorLimits.MAX_CLIENTS }
            // a refused stream must not be rate-limited out of the test by the three above, so spread over IPs is not possible on loopback:
            now += MirrorLimits.RATE_WINDOW_MILLIS
            assertEquals(503, get(started.port, "/m/${started.token.value}/events").status)
        } finally { open.forEach { it.close() } }
    }

    @Test fun aSlowClientDoesNotBlockOthers() {
        val started = startOk()
        val stalled = Socket(loopback, started.port)
        try {
            stalled.getOutputStream().write("GET /m/".toByteArray()) // never finishes its request
            val t0 = System.nanoTime()
            assertEquals(200, get(started.port, "/m/${started.token.value}/state").status)
            assertTrue("took too long", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 3_000)
        } finally { stalled.close() }
    }

    @Test fun stopClosesStreamsAndRefusesNewConnections() {
        val started = startOk()
        val stream = Socket(loopback, started.port)
        stream.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
        stream.soTimeout = 5_000
        stream.getInputStream().bufferedReader().readLine()
        server.stop()
        assertFalse(server.isRunning)
        assertEquals(0, server.clients.value)
        val end = runCatching { stream.getInputStream().readBytes(); true }.getOrDefault(true)
        assertTrue(end)
        assertTrue(runCatching { Socket(loopback, started.port).close() }.isFailure)
        stream.close()
    }

    @Test fun stopIsIdempotentAndSafeBeforeStart() {
        server.stop()
        startOk()
        server.stop()
        server.stop()
        assertFalse(server.isRunning)
    }

    @Test fun startingTwiceKeepsTheFirstSessionAndReportsAlreadyRunning() {
        val first = startOk()
        val second = server.start(loopback)
        assertEquals(MirrorStartResult.Failed(MirrorFailure.ALREADY_RUNNING), second)
        assertEquals(200, get(first.port, "/m/${first.token.value}/state").status)
    }

    @Test fun canRestartAfterStopWithANewTokenAndTheOldOneStopsWorking() {
        val first = startOk()
        server.stop()
        val second = startOk()
        assertTrue(first.token.value != second.token.value)
        assertEquals(404, get(second.port, "/m/${first.token.value}/state").status)
    }

    @Test fun bindFailureIsATypedErrorNotACrash() {
        val taken = ServerSocket(0, 1, loopback)
        taken.use {
            // 203.0.113.1 (TEST-NET-3) is not an address of this machine, so bind must fail.
            val result = server.start(InetAddress.getByName("203.0.113.1"))
            assertEquals(MirrorStartResult.Failed(MirrorFailure.BIND), result)
            assertFalse(server.isRunning)
        }
    }

    @Test fun theSocketIsBoundToTheGivenAddressNotToTheWildcard() {
        val started = startOk()
        assertEquals(loopback, server.boundAddress)
        assertFalse(server.boundAddress!!.isAnyLocalAddress)
        assertTrue(started.port > 0)
    }

    @Test fun anExpiredTokenStopsServingContent() {
        val started = startOk()
        now = started.token.expiresAtMillis
        assertEquals(404, get(started.port, "/m/${started.token.value}/state").status)
    }

    private fun waitUntil(timeoutMs: Long = 5_000, block: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!block()) { check(System.nanoTime() < end) { "condition not met" }; Thread.sleep(20) }
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*MirrorServerTest" -q`; mong đợi `Unresolved reference: MirrorServer`, `MirrorEngine`, `boundAddress`.
- [ ] **Step 3: Cài đặt** theo thiết kế ở đầu task. `MirrorEngine` khai báo `start/stop/publish/clients/isRunning`; `MirrorServer` thêm `val boundAddress: InetAddress?` (chỉ để test). `MirrorStartResult`/`MirrorFailure` đặt trong `MirrorEngine.kt`. Mã đọc yêu cầu đọc từng byte (hoặc khối nhỏ) vào bộ đệm có trần `MAX_REQUEST_BYTES + 1`, dừng khi gặp `\r\n\r\n`; tràn trần → trả 431. Trả lỗi `SocketTimeoutException` bằng việc đóng im lặng. Mỗi worker bọc toàn bộ trong `try/catch(Throwable)` rồi `close()` socket trong `finally`, không bao giờ ném ra ngoài luồng.
- [ ] **Step 4: Chạy xanh.** Cùng lệnh Step 2; đọc XML: 16 test, 0 lỗi. Chạy lại **3 lần** để bắt test chập chờn do luồng; không chấp nhận lần nào đỏ.
- [ ] **Step 5: Mutation-check (từng cái một, khôi phục sau mỗi lần).** (a) Đổi `ServerSocket(0, 50, bindAddress)` thành `ServerSocket(0)`: `theSocketIsBoundToTheGivenAddressNotToTheWildcard` phải đỏ. (b) Bỏ `closeStream()` trong `finally`: `aFourthStreamIsRefusedWith503` hoặc một test sau đó phải đỏ. (c) Bỏ `soTimeout`: `aSlowClientDoesNotBlockOthers` phải đỏ (hoặc treo đến timeout test). (d) Cho `stop()` không đóng socket SSE: `stopClosesStreamsAndRefusesNewConnections` phải đỏ.
- [ ] **Step 6: Commit.** `feat(exc-13): serve the mirror from a bounded loopback-safe server socket` (+ trailer).

### Task 6: `SecondScreenViewModel`, trạng thái và DI

**Files:**
- Modify: `infrastructure/mirror/LanAddressProvider.kt` (thêm interface `LanAddressSource`)
- Create: `infrastructure/di/MirrorModule.kt`, `ui/page/home/read/mirror/MirrorUiState.kt`, `ui/page/home/read/mirror/SecondScreenViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/mirror/SecondScreenViewModelTest.kt`

**Interfaces:**
- Consumes: `MirrorEngine`, `MirrorStartResult`, `MirrorFailure`, `MirrorSnapshotBuilder`, `MirrorSnapshot` (Task 3, 5); `LanAddress` (Task 4).
- Produces:
  - `interface LanAddressSource { fun current(): InetAddress?; fun networkLosses(): Flow<Unit> }`, `LanAddressProvider : LanAddressSource` (Task 4 class đổi sang implement nó).
  - `MirrorUiState` (xem mục Interfaces, kèm `Expired`).
  - `SecondScreenViewModel`: `val state: StateFlow<MirrorUiState>`, `fun start()`, `fun stop()`, `fun onAppBackgrounded()`, `fun updateArticle(title: String, source: String, dateMillis: Long, html: String?)`, `internal var clock: () -> Long`.
  - `MirrorModule`: `@Provides MirrorEngine = MirrorServer()` trong `ViewModelComponent` (mỗi ViewModel một server riêng), `@Binds LanAddressSource` trong `SingletonComponent`.

Quy tắc hành vi (đều có test bên dưới):
- `start()` chỉ chạy khi trạng thái là `Off`, `NoNetwork`, `Expired` hoặc `Error`; đang `Starting`/`Running` thì bỏ qua (không mở hai server).
- Không có địa chỉ LAN → `NoNetwork` và **không gọi** `engine.start`.
- `engine.start` thất bại → `Error(kind)`; thành công → `Running(url="http://<ip>:<port>/m/<token>", clients, expiresAtMillis)`, đẩy snapshot mới nhất nếu đã có.
- `updateArticle` dựng snapshot trên `defaultDispatcher` (HTML→text bằng `HtmlCompat.fromHtml(..., FROM_HTML_MODE_LEGACY).toString()`), tăng `version`, luôn nhớ bản mới nhất, chỉ `publish` khi đang `Running`. `html == null` hoặc rỗng → snapshot không có đoạn văn.
- Hết hạn: sau `expiresAtMillis - clock()` → `engine.stop()` và `Expired`.
- Mất mạng (`networkLosses` phát) → `engine.stop()` và `NoNetwork`.
- `onAppBackgrounded()` và `stop()` → `engine.stop()` và `Off` (idempotent). `onCleared()` → `engine.stop()`.
- `clients` của engine cập nhật vào `Running.clients`.

- [ ] **Step 1: Viết test đỏ.**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.MirrorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

private class FakeEngine : MirrorEngine {
    var startCalls = 0
    var stopCalls = 0
    var result: MirrorStartResult = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), LONG_LIVED))
    val published = mutableListOf<MirrorSnapshot>()
    val clientCount = MutableStateFlow(0)
    override val clients: StateFlow<Int> get() = clientCount
    override var isRunning = false
    override fun start(bindAddress: InetAddress): MirrorStartResult {
        startCalls++
        if (result is MirrorStartResult.Started) isRunning = true
        return result
    }
    override fun stop() { stopCalls++; isRunning = false }
    override fun publish(snapshot: MirrorSnapshot) { published += snapshot }
}

private class FakeLan(var address: InetAddress? = InetAddress.getByName("192.168.1.20")) : LanAddressSource {
    val losses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override fun current(): InetAddress? = address
    override fun networkLosses(): Flow<Unit> = losses
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class SecondScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeEngine()
    private val lan = FakeLan()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SecondScreenViewModel(engine, lan, dispatcher).also { it.clock = { 1_000L } }

    @Test fun startsOff() = runTest(dispatcher) {
        assertEquals(MirrorUiState.Off, vm().state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun startOpensTheServerAndShowsTheUrl() = runTest(dispatcher) {
        val vm = vm()
        vm.start()
        assertEquals(MirrorUiState.Starting, vm.state.value)
        runCurrent()
        val running = vm.state.value as MirrorUiState.Running
        assertEquals("http://192.168.1.20:8080/m/" + "a".repeat(32), running.url)
        assertEquals(LONG_LIVED, running.expiresAtMillis)
        assertEquals(0, running.clients)
        assertEquals(1, engine.startCalls)
    }

    @Test fun noLanAddressMeansNoNetworkAndTheServerIsNeverOpened() = runTest(dispatcher) {
        lan.address = null
        val vm = vm()
        vm.start(); runCurrent()
        assertEquals(MirrorUiState.NoNetwork, vm.state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun aBindFailureShowsTheErrorKind() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Failed(MirrorFailure.BIND)
        val vm = vm()
        vm.start(); runCurrent()
        assertEquals(MirrorUiState.Error(MirrorFailure.BIND), vm.state.value)
    }

    @Test fun startWhileRunningDoesNotOpenASecondServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.start(); vm.start(); runCurrent()
        assertEquals(1, engine.startCalls)
    }

    @Test fun canRetryAfterAnError() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Failed(MirrorFailure.BIND)
        val vm = vm()
        vm.start(); runCurrent()
        engine.result = MirrorStartResult.Started(9000, MirrorToken("b".repeat(32), LONG_LIVED))
        vm.start(); runCurrent()
        assertTrue(vm.state.value is MirrorUiState.Running)
    }

    @Test fun stopClosesTheServerAndGoesOffIdempotently() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop(); vm.stop()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertTrue(engine.stopCalls >= 1)
        assertTrue(!engine.isRunning)
    }

    @Test fun goingToTheBackgroundStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.onAppBackgrounded()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun backgroundingWhileOffDoesNothingHarmful() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppBackgrounded()
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertEquals(0, engine.startCalls)
    }

    @Test fun theSessionExpiresWhenTheTokenDoes() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        advanceTimeBy(8_999L)
        assertTrue(vm.state.value is MirrorUiState.Running)
        advanceTimeBy(2L)
        assertEquals(MirrorUiState.Expired, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun aStoppedSessionNeverTurnsIntoExpiredLater() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        val stopsAfterStop = engine.stopCalls
        advanceTimeBy(SHORT_LIVED * 3)
        assertEquals(MirrorUiState.Off, vm.state.value)
        assertEquals("a cancelled expiry timer must not stop the engine again", stopsAfterStop, engine.stopCalls)
    }

    @Test fun aRestartedSessionIsNotCutShortByTheOldTimer() = runTest(dispatcher) {
        engine.result = MirrorStartResult.Started(8080, MirrorToken("a".repeat(32), SHORT_LIVED))
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        engine.result = MirrorStartResult.Started(8081, MirrorToken("b".repeat(32), LONG_LIVED))
        vm.start(); runCurrent()
        advanceTimeBy(SHORT_LIVED * 3)
        assertTrue(vm.state.value is MirrorUiState.Running)
        assertTrue(engine.isRunning)
    }

    @Test fun losingTheNetworkStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        lan.losses.tryEmit(Unit)
        runCurrent()
        assertEquals(MirrorUiState.NoNetwork, vm.state.value)
        assertTrue(!engine.isRunning)
    }

    @Test fun theViewerCountFollowsTheEngine() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        engine.clientCount.value = 2
        runCurrent()
        assertEquals(2, (vm.state.value as MirrorUiState.Running).clients)
    }

    @Test fun anArticleSentBeforeStartIsPublishedWhenTheServerStarts() = runTest(dispatcher) {
        val vm = vm()
        vm.updateArticle("Title", "BBC", 5L, "<p>Hello</p><p>World</p>")
        runCurrent()
        assertTrue(engine.published.isEmpty())
        vm.start(); runCurrent()
        val s = engine.published.last()
        assertEquals("Title", s.title)
        assertEquals("BBC", s.source)
        assertEquals(listOf("Hello", "World"), s.paragraphs)
    }

    @Test fun eachNewArticleIsPublishedWithAHigherVersionWhileRunning() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("One", "S", 1L, "<p>a</p>"); runCurrent()
        vm.updateArticle("Two", "S", 2L, "<p>b</p>"); runCurrent()
        assertEquals(listOf("One", "Two"), engine.published.map { it.title }.takeLast(2))
        assertTrue(engine.published.last().version > engine.published[engine.published.size - 2].version)
    }

    @Test fun nothingIsPublishedAfterStop() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.stop()
        val before = engine.published.size
        vm.updateArticle("Late", "S", 1L, "<p>x</p>"); runCurrent()
        assertEquals(before, engine.published.size)
    }

    @Test fun nullOrBlankHtmlGivesAnEmptyBodyNotACrash() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("T", "S", 1L, null); runCurrent()
        assertTrue(engine.published.last().paragraphs.isEmpty())
        vm.updateArticle("T2", "S", 1L, "   "); runCurrent()
        assertTrue(engine.published.last().paragraphs.isEmpty())
    }

    @Test fun scriptsInTheArticleNeverReachTheSnapshotAsMarkup() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.updateArticle("T", "S", 1L, "<p>safe</p><script>alert(1)</script><img src=x onerror=y>"); runCurrent()
        val text = engine.published.last().paragraphs.joinToString(" ")
        assertTrue(!text.contains("<script") && !text.contains("onerror") && !text.contains("<img"))
    }

    @Test fun clearingTheViewModelStopsTheServer() = runTest(dispatcher) {
        val vm = vm()
        vm.start(); runCurrent()
        vm.callOnCleared()
        assertTrue(!engine.isRunning)
    }
}
```

`callOnCleared()` là hàm trợ giúp `internal fun callOnCleared() = onCleared()` trong ViewModel (đúng mẫu dự án; `onCleared` là `protected`).

- [ ] **Step 2: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*SecondScreenViewModelTest" -q`; mong đợi `Unresolved reference` cho `SecondScreenViewModel`, `MirrorUiState`, `LanAddressSource`, `clientCount`.
- [ ] **Step 3: Cài đặt.** (a) Trong `MirrorEngine.kt` đúng như mục Interfaces (thêm `val clients: StateFlow<Int>`); `MirrorServer` giữ `MutableStateFlow` và cập nhật `clients`. (b) `LanAddressSource` khai báo trong `LanAddressProvider.kt`; `LanAddressProvider` implement. (c) `MirrorUiState` sealed interface; `Off`, `NoNetwork`, `Starting`, `Expired` là `data object`. (d) `SecondScreenViewModel` đúng quy tắc hành vi ở đầu task, **một** constructor `@Inject constructor(private val engine: MirrorEngine, private val lan: LanAddressSource, @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher)`; giữ `Job` cho: collector `clients`, collector `networkLosses`, timer hết hạn; `shutdown(finalState)` huỷ cả ba, gọi `engine.stop()`, đặt trạng thái. Mọi tác vụ nặng trên `defaultDispatcher`; không giữ `Context`/`Activity`. (e) `MirrorModule` như mục Interfaces.
- [ ] **Step 4: Chạy xanh.** Cùng lệnh Step 2; đọc XML: 18 test, 0 lỗi. Rồi `./gradlew assembleDevDebug -q` để Hilt kiểm tra đồ thị phụ thuộc (không `MissingBinding`).
- [ ] **Step 5: Mutation-check.** (a) Bỏ điều kiện "chỉ khởi từ Off/NoNetwork/Expired/Error": `startWhileRunningDoesNotOpenASecondServer` phải đỏ. (b) Bỏ lệnh huỷ timer hết hạn trong `shutdown`: thêm kiểm tra `engine.stopCalls` không tăng sau khi `stop()` rồi `advanceTimeBy(20_000)`; nếu test hiện có không bắt được thì thêm test đó và chạy lại mutation tới khi đỏ. (c) Đổi `publish` để phát cả khi `Off`: `nothingIsPublishedAfterStop` đỏ. Khôi phục sau mỗi lần.
- [ ] **Step 6: Commit.** `feat(exc-13): add the second screen view model and wiring` (+ trailer).

---

### Task 7: Chuỗi ở 38 locale

**Files:** Modify `app/src/main/res/values*/strings.xml`. Tạo script `/tmp/exc13_strings.py` (mẫu `/tmp/front03_strings.py`).

Chỉ thêm chuỗi **sẽ được dùng ở Task 8/9** (lint `UnusedResources`). Danh sách và nơi dùng:

| Khoá | EN | VI | Dùng ở |
|---|---|---|---|
| `second_screen_title` | Second Screen | Màn hình thứ hai | TopBar + sheet |
| `second_screen_switch` | Show this article on a computer | Hiện bài này trên máy tính | sheet |
| `second_screen_url_label` | Open this address in a browser on the same Wi-Fi | Mở địa chỉ này trên trình duyệt cùng Wi-Fi | sheet |
| `second_screen_copy` | Copy link | Sao chép liên kết | sheet |
| `second_screen_copied` | Link copied | Đã sao chép liên kết | sheet (Toast) |
| `second_screen_share` | Share link | Chia sẻ liên kết | sheet |
| `second_screen_share_chooser` | Share Second Screen link | Chia sẻ liên kết Màn hình thứ hai | sheet |
| `second_screen_viewers` | Viewers: %1$d | Đang xem: %1$d | sheet |
| `second_screen_expires` | Expires at %1$s | Hết hạn lúc %1$s | sheet |
| `second_screen_warning` | Not encrypted. Anyone on this Wi-Fi who sees the link can read the article. Use only on a network you trust. | Không mã hoá. Ai cùng Wi-Fi thấy liên kết đều đọc được bài. Chỉ dùng trên mạng bạn tin cậy. | sheet |
| `second_screen_starting` | Starting… | Đang khởi động… | sheet |
| `second_screen_no_network` | No Wi-Fi or Ethernet network with a private address. Connect to Wi-Fi and try again. | Không có Wi-Fi hoặc mạng dây với địa chỉ nội bộ. Hãy kết nối Wi-Fi rồi thử lại. | sheet |
| `second_screen_expired` | The session ended. Turn it on again to get a new link. | Phiên đã kết thúc. Bật lại để nhận liên kết mới. | sheet |
| `second_screen_error` | Could not start the server. Try again. | Không khởi động được máy chủ. Hãy thử lại. | sheet |
| `second_screen_retry` | Try again | Thử lại | sheet |

- [ ] **Step 1: Viết `/tmp/exc13_strings.py`** theo đúng cấu trúc của `/tmp/front03_strings.py` (hai từ điển `EN` và `VI`, `esc()` thoát `&`, `'`, `"`, chèn khối `<!-- EXC-13 second screen -->` ngay trước `</resources>`, bỏ qua khoá đã có, in `N locale files`). Giá trị đúng như bảng trên.
- [ ] **Step 2: Chạy.** `python3 /tmp/exc13_strings.py > /tmp/exc13-str.out 2>&1; cat /tmp/exc13-str.out`; mong đợi `38 locale files`. `grep -L 'name="second_screen_error"' app/src/main/res/values*/strings.xml | wc -l` phải in `0`.
- [ ] **Step 3: Parity.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" -q`; đọc XML: 6/6.
- [ ] **Step 4: Commit.** `feat(exc-13): add second screen strings across 38 locales` (+ trailer). Lint `UnusedResources` sẽ chỉ sạch sau Task 8/9; chạy lint ở Task 10 và sửa nếu còn khoá thừa.

### Task 8: Sheet nội tuyến "Second Screen" (Compose)

**Files:**
- Create: `ui/page/home/read/mirror/SecondScreenSheetContent.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/mirror/SecondScreenSheetTest.kt`

**Interfaces:**
- Consumes: `MirrorUiState` (Task 6), chuỗi `second_screen_*` (Task 7).
- Produces: `@Composable fun SecondScreenSheetContent(state: MirrorUiState, onToggle: (Boolean) -> Unit, onCopy: (String) -> Unit, onShare: (String) -> Unit, modifier: Modifier = Modifier)` và các tag `SECOND_SCREEN_SWITCH_TAG`, `SECOND_SCREEN_URL_TAG`, `SECOND_SCREEN_COPY_TAG`, `SECOND_SCREEN_SHARE_TAG`, `SECOND_SCREEN_WARNING_TAG`, `SECOND_SCREEN_VIEWERS_TAG`, `SECOND_SCREEN_STATUS_TAG`, `SECOND_SCREEN_RETRY_TAG`.

Bố cục (theo `DualCardSheetContent`): `Column` với `verticalScroll`, `navigationBarsPadding()`, tiêu đề, một `Switch` (hàng có nhãn, vùng chạm ≥ 48dp, `semantics` `toggleable`), cảnh báo luôn hiện khi **không** `Off`, URL trong `Surface` có `SelectionContainer` để chọn tay được, hai nút 48dp **Sao chép**/**Chia sẻ** chỉ khi `Running`, số người xem, giờ hết hạn (`DateFormat.getTimeInstance(SHORT)`), trạng thái `Starting`/`NoNetwork`/`Expired`/`Error` có văn bản và nút **Thử lại** cho `NoNetwork`/`Expired`/`Error` (gọi `onToggle(true)`). Không dùng `ModalBottomSheet`. Công tắc bật khi `Starting` hoặc `Running`.

- [ ] **Step 1: Viết test đỏ** (`@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = Application::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`, **một `setContent` mỗi test**).

```kotlin
package com.mckimquyen.reader.ui.page.home.read.mirror

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.mirror.MirrorFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondScreenSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun str(id: Int, vararg a: Any) = compose.activity.getString(id, *a)
    private val url = "http://192.168.1.20:8080/m/" + "a".repeat(32)

    private fun show(
        state: MirrorUiState,
        onToggle: (Boolean) -> Unit = {},
        onCopy: (String) -> Unit = {},
        onShare: (String) -> Unit = {},
    ) = compose.setContent { SecondScreenSheetContent(state, onToggle, onCopy, onShare) }

    @Test fun off_showsAnOffSwitchAndNoUrlNoButtonsNoWarning() {
        show(MirrorUiState.Off)
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOff()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_COPY_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_SHARE_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_WARNING_TAG).assertCountEquals(0)
    }

    @Test fun turningTheSwitchOnReportsTrue() {
        var toggled: Boolean? = null
        show(MirrorUiState.Off, onToggle = { toggled = it })
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun running_showsUrlWarningViewersAndBothButtons() {
        show(MirrorUiState.Running(url, clients = 2, expiresAtMillis = 3_600_000L))
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOn()
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_WARNING_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.second_screen_warning)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.second_screen_viewers, 2)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_COPY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_SHARE_TAG).assertIsDisplayed()
    }

    @Test fun copyAndShareReportTheExactUrl() {
        var copied: String? = null
        var shared: String? = null
        show(MirrorUiState.Running(url, 0, 1L), onCopy = { copied = it }, onShare = { shared = it })
        compose.onNodeWithTag(SECOND_SCREEN_COPY_TAG).performClick()
        compose.onNodeWithTag(SECOND_SCREEN_SHARE_TAG).performClick()
        compose.runOnIdle { assertEquals(url, copied); assertEquals(url, shared) }
    }

    @Test fun turningTheSwitchOffWhileRunningReportsFalse() {
        var toggled: Boolean? = null
        show(MirrorUiState.Running(url, 0, 1L), onToggle = { toggled = it })
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).performClick()
        compose.runOnIdle { assertEquals(false, toggled) }
    }

    @Test fun starting_showsTheStatusAndNoUrl() {
        show(MirrorUiState.Starting)
        compose.onNodeWithText(str(R.string.second_screen_starting)).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onNodeWithTag(SECOND_SCREEN_SWITCH_TAG).assertIsOn()
    }

    @Test fun noNetwork_explainsAndOffersRetry() {
        var toggled: Boolean? = null
        show(MirrorUiState.NoNetwork, onToggle = { toggled = it })
        compose.onNodeWithText(str(R.string.second_screen_no_network)).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onNodeWithTag(SECOND_SCREEN_RETRY_TAG).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun expired_explainsAndHidesTheOldUrl() {
        show(MirrorUiState.Expired)
        compose.onNodeWithText(str(R.string.second_screen_expired)).assertIsDisplayed()
        compose.onAllNodesWithTag(SECOND_SCREEN_URL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SECOND_SCREEN_COPY_TAG).assertCountEquals(0)
    }

    @Test fun error_showsTheMessageAndRetry() {
        show(MirrorUiState.Error(MirrorFailure.BIND))
        compose.onNodeWithText(str(R.string.second_screen_error)).assertIsDisplayed()
        compose.onNodeWithTag(SECOND_SCREEN_RETRY_TAG).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*SecondScreenSheetTest" -q`; mong đợi `Unresolved reference: SecondScreenSheetContent`.
- [ ] **Step 3: Cài đặt** đúng bố cục ở đầu task. Tag đặt bằng `Modifier.testTag`. Cảnh báo có `testTag(SECOND_SCREEN_WARNING_TAG)` và màu `MaterialTheme.colorScheme.error` cho biểu tượng + chữ (không chỉ màu: luôn có văn bản). `Switch` bọc trong `Row` với `Modifier.toggleable(role = Role.Switch)` để một vùng chạm và có nhãn cho TalkBack; tag đặt trên `Row`.
- [ ] **Step 4: Chạy xanh** (9 test). **Mutation-check:** đổi điều kiện hiện cảnh báo thành `state is Running` (thay vì "không `Off`"): test `starting_...` không bắt được; thêm kiểm `onNodeWithTag(SECOND_SCREEN_WARNING_TAG).assertIsDisplayed()` vào `starting_showsTheStatusAndNoUrl` và `noNetwork_...`, chạy lại mutation tới khi đỏ. Khôi phục.
- [ ] **Step 5: Commit.** `feat(exc-13): add the second screen sheet` (+ trailer).

---

### Task 9: Nối `ReadingViewModel`, `ReadingPage` và `TopBar`

**Files:**
- Modify: `ui/page/home/read/ReadingViewModel.kt` (cờ sheet), `ReadingPage.kt`, `TopBar.kt`
- Test: `ui/page/home/read/ReadingViewModelSecondScreenTest.kt`

**Interfaces:**
- Consumes: `SecondScreenViewModel`, `SecondScreenSheetContent` (Task 6, 8).
- Produces: `ReadingUiState.showSecondScreenSheet: Boolean = false`; `ReadingViewModel.openSecondScreen()`, `dismissSecondScreen()`; tham số `TopBar(onSecondScreen: () -> Unit = {})`.

Quyết định nối (theo mẫu `DualCardSheetContent`):
- `ReadingViewModel`: `openSecondScreen()` đặt `showSecondScreenSheet = true` **chỉ khi** có `articleWithFeed`; `dismissSecondScreen()` đặt `false`. **Không** để `ReadingViewModel` biết gì về server (giữ constructor nguyên vẹn, không thêm tham số, tránh phá 15+ test hiện có).
- `ReadingPage`: `val secondScreenViewModel: SecondScreenViewModel = hiltViewModel()`; thêm `readingUiState.showSecondScreenSheet` vào `isAnySheetOpen`; thêm `readingViewModel.dismissSecondScreen()` vào khối đồng bộ ngược khi sheet bị vuốt đóng; thêm nhánh `showSecondScreenSheet` vào `BackHandler` (đặt **trước** `showDualCardSheet`); thêm nhánh `if (readingUiState.showSecondScreenSheet)` vào `sheetContent` hiển thị `SecondScreenSheetContent`. **Đóng sheet không tắt server** (người dùng vẫn đọc tiếp và trình duyệt vẫn theo bài); chỉ công tắc, rời trang, nền, hết hạn, mất mạng mới tắt.
- Đẩy bài: `LaunchedEffect(articleId, content)` gọi `secondScreenViewModel.updateArticle(title, feedName, dateMillis, content)` mỗi khi bài/nội dung đổi (nội dung đổi khi bật đọc toàn văn).
- Vòng đời: `DisposableEffect(Unit) { onDispose { secondScreenViewModel.stop() } }` (rời `ReadingPage`); `DisposableEffect(lifecycleOwner)` đăng ký `LifecycleEventObserver` trên `ProcessLifecycleOwner.get().lifecycle` gọi `onAppBackgrounded()` ở `ON_STOP` và gỡ trong `onDispose`.
- Sao chép dùng `LocalClipboardManager`, chia sẻ dùng `Intent.ACTION_SEND` (`text/plain`) trong `Intent.createChooser(..., stringResource(second_screen_share_chooser))`, hiện `Toast` `second_screen_copied`.
- `TopBar`: thêm mục menu "Second Screen" (biểu tượng `Icons.Outlined.Devices` hoặc `Icons.Rounded.Cast`) giữa "Deep Read" và "Highlight", gọi `onSecondScreen()`.

- [ ] **Step 1: Test đỏ cho ViewModel** (mẫu `ReadingViewModelContrastTest`: `StandardTestDispatcher`, tiêm `defaultDispatcher = dispatcher` và `ioDispatcher = dispatcher`).

```kotlin
package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelSecondScreenTest {
    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val feed = Feed("f1", "BBC", url = "https://x.test", groupId = "g", accountId = 1)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After fun tearDown() { Dispatchers.resetMain(); io.mockk.unmockkAll() }

    private fun vm() = ReadingViewModel(
        rssService = rssService, rssHelper = mockk(relaxed = true), ttsManager = ttsManager,
        summaryService = mockk<GeminiSummaryService>(relaxed = true), zenAudioManager = zenAudioManager,
        defaultDispatcher = dispatcher, ioDispatcher = dispatcher,
    )

    private fun article() = ArticleWithFeed(
        Article(id = "a1", date = Date(1L), title = "Title", rawDescription = "<p>x</p>", shortDescription = "x",
            link = "https://x.test/a1", feedId = "f1", accountId = 1), feed)

    @Test fun openWithoutAnArticleIsANoOp() = runTest(dispatcher) {
        val vm = vm()
        vm.openSecondScreen()
        assertFalse(vm.readingUiState.value.showSecondScreenSheet)
    }

    @Test fun openAndDismissToggleTheFlagWhenAnArticleIsLoaded() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = vm()
        vm.initData("a1"); advanceUntilIdle()
        vm.openSecondScreen()
        assertTrue(vm.readingUiState.value.showSecondScreenSheet)
        vm.dismissSecondScreen()
        assertFalse(vm.readingUiState.value.showSecondScreenSheet)
    }

    @Test fun openingItDoesNotOpenAnyOtherSheet() = runTest(dispatcher) {
        coEvery { repo.findArticleById("a1") } returns article()
        val vm = vm()
        vm.initData("a1"); advanceUntilIdle()
        vm.openSecondScreen()
        val s = vm.readingUiState.value
        assertFalse(s.showSummarySheet || s.showMindMapSheet || s.showDeepReadSheet || s.showQuoteSheet ||
            s.showBountySheet || s.showEchoSheet || s.showDossierSheet || s.showDualCardSheet)
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelSecondScreenTest" -q`; mong đợi `Unresolved reference: openSecondScreen`, `showSecondScreenSheet`.
- [ ] **Step 3: Cài đặt phần ViewModel + UI** theo "Quyết định nối". Thêm chuỗi mục menu dùng lại `second_screen_title` (không thêm khoá mới).
- [ ] **Step 4: Chạy xanh** (3 test) và **toàn bộ test `ReadingViewModel*` hiện có** để chắc không phá: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModel*" --tests "*SecondScreen*" -q`; đọc XML, 0 lỗi.
- [ ] **Step 5: Build + Hilt.** `./gradlew assembleDevDebug -q`; mong đợi thành công.
- [ ] **Step 6: Mutation-check.** Bỏ `showSecondScreenSheet` khỏi `isAnySheetOpen`: không có test JVM nào bắt được (nó nằm trong Compose của `ReadingPage`), nên **ghi rõ** vào DONE doc là chỉ kiểm bằng smoke trên máy: mở sheet rồi nhấn Back phải chỉ đóng sheet. Bỏ `if (_readingUiState.value.articleWithFeed == null) return` trong `openSecondScreen`: `openWithoutAnArticleIsANoOp` phải đỏ. Khôi phục.
- [ ] **Step 7: Commit.** `feat(exc-13): wire the second screen sheet into the reading page` (+ trailer).

---

### Task 10: Instrumentation, kiểm tra đầy đủ, smoke trên A50s, tài liệu

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServerDeviceTest.kt`
- Create: `doc/task/done/EXC-13_second-screen-mirror_DONE.md`
- Modify: `doc/feature.md`, `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md` (dòng 23)
- Delete: `doc/task/todo/EXC-13_second-screen-mirror.md` (`git rm`)

- [ ] **Step 1: Test instrumentation** (server thật trên máy, địa chỉ loopback, không DB, không mạng ngoài).

```kotlin
package com.mckimquyen.reader.infrastructure.mirror

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

@RunWith(AndroidJUnit4::class)
class MirrorServerDeviceTest {
    private val server = MirrorServer()
    @After fun tearDown() = server.stop()

    private fun http(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 5_000
        return try {
            val code = c.responseCode
            code to (if (code < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    @Test fun serverRunsOnARealDeviceServesTheTokenedPageAndHidesEverythingElse() {
        val started = server.start(InetAddress.getByName("127.0.0.1")) as MirrorStartResult.Started
        server.publish(MirrorSnapshot("Hello", "BBC", 5L, listOf("p"), false, 1L))
        val (pageCode, page) = http(started.port, "/m/${started.token.value}")
        assertEquals(200, pageCode)
        assertTrue(page.contains("EventSource"))
        val (stateCode, state) = http(started.port, "/m/${started.token.value}/state")
        assertEquals(200, stateCode)
        assertTrue(state.contains("\"title\":\"Hello\""))
        assertEquals(404, http(started.port, "/").first)
        assertEquals(404, http(started.port, "/m/" + "0".repeat(32) + "/state").first)
        server.stop()
        assertTrue(runCatching { http(started.port, "/m/${started.token.value}") }.isFailure)
    }
}
```

- [ ] **Step 2: Kiểm tra đầy đủ tuần tự** (script nền ghi ra file; xoá `/tmp/exc13-full.out` trước): `./gradlew testDevDebugUnitTest --rerun -q`, rồi `./gradlew lintDevDebug --rerun-tasks -q`, rồi `./gradlew assembleDevDebug assembleDevDebugAndroidTest -q`. Đọc đếm unit từ mọi `app/build/test-results/testDevDebugUnitTest/*.xml` (mong đợi nền 1.953 + khoảng 85 test mới, 0 lỗi, 0 skip) và lint từ `app/build/reports/lint-results-devDebug.xml` (mong đợi 0 error, warning không vượt 2.463; liệt kê mọi issue nhắc `second_screen`/`mirror`, ví dụ `UnusedResources`, và sửa). Chạy bộ unit mirror **3 lần** (`--tests "*mirror*" --tests "*Mirror*" --tests "*SecondScreen*"`) để bắt test chập chờn.
- [ ] **Step 3: Instrumentation trên A50s.** `adb devices -l` phải liệt kê `R58MA6WYRPE` model `SM_A507FN`. Tìm APK bằng `find` (tên có dấu cách), `adb -s R58MA6WYRPE install -r -d -t ...` app và test, rồi `adb -s R58MA6WYRPE shell am instrument -w -e class com.mckimquyen.reader.infrastructure.mirror.MirrorServerDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; mong đợi `OK (1 test)`. Gỡ `com.mckimquyen.reader.test` sau đó. (Cài app dùng `-r`, giữ dữ liệu.)
- [ ] **Step 4: Sao lưu trước smoke.** Force-stop app. `run-as ... cat` lưu `databases/Reader*` (hiện không có: ghi nhận là rỗng) và `files/datastore/settings.preferences_pb` vào `/tmp/exc13-a50-backup`; DataStore phải không rỗng. Máy chưa có Room nên nạp **bản seed**: `adb -s R58MA6WYRPE shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader'" < /tmp/front03-seed/Reader` cùng `-wal`, `-shm` rỗng; đọc lại và `cmp`. Kiểm `PRAGMA integrity_check` = `ok` trên bản sao trước khi nạp.
- [ ] **Step 5: Smoke (A50s + Mac cùng Wi-Fi).** Xác nhận cả hai cùng mạng: lấy IP Wi-Fi của A50s bằng `adb -s R58MA6WYRPE shell ip -4 addr show wlan0` và IP của Mac (`ipconfig getifaddr en0`); nếu khác dải, **dừng và báo** (không tự đổi mạng). Mở app, đóng quảng cáo theo quy tắc, mở một bài từ danh sách, menu ⋮ → "Second Screen", bật công tắc; đọc URL từ `uiautomator dump` mới. Từ Mac: `curl -s -o /dev/null -w '%{http_code}\n' <url>` (mong đợi 200), `curl -s <url>/state` (có tiêu đề bài), `curl -s -o /dev/null -w '%{http_code}\n' http://<ip>:<port>/` (mong đợi 404), cùng đường với token sai (404, **cùng thân** "Not found"), `curl -s -X POST <url>` (405). Mở URL trong trình duyệt Mac, chụp màn hình, **xem ảnh**. Chuyển sang bài khác trên điện thoại và xác nhận trình duyệt đổi tiêu đề (đo độ trễ tương đối bằng `curl -N <url>/events` kèm dấu thời gian). Mở 4 `curl -N` song song tới `/events`: kết nối thứ 4 phải 503. Rời `ReadingPage`: `curl` phải bị từ chối kết nối. Bật lại, đưa app xuống nền (`input keyevent KEYCODE_HOME`): `curl` phải bị từ chối. Mỗi lần chạm UI: `mResumedActivity` phải là app, dump mới. Ghi rõ **không** kiểm được: nhiều máy khác nhau, `client isolation` của router, trình duyệt khác Safari/Chrome trên Mac, TalkBack.
- [ ] **Step 6: Khôi phục (luôn làm).** Force-stop; xoá chỉ 3 file Room tạm; DataStore **không xoá**, `cmp` với bản sao lưu; `pm list packages com.mckimquyen.reader.test` rỗng; xác nhận không còn tiến trình nghe cổng nào (`adb -s R58MA6WYRPE shell cat /proc/net/tcp6 /proc/net/tcp | grep -i ':1F90'` không cần; thay vào đó kiểm `curl` bị từ chối).
- [ ] **Step 7: Tài liệu.** Viết `doc/task/done/EXC-13_second-screen-mirror_DONE.md` theo mẫu `FRONT-03_3d-knowledge-galaxy_DONE.md`: phạm vi trung thực (một chiều, văn bản thuần, URL kèm token, **không QR, không đồng bộ vị trí cuộn, không TLS, không NSD**, trang web chữ tiếng Anh cố định), cấu trúc mã, quyết định bảo mật và **giới hạn bảo mật nói thẳng** (không mã hoá; token không ngăn nghe lén; client isolation), mutation-check đã làm, số test và lint thật, kết quả smoke (đã thấy và **chưa** thấy), phần chỉ kiểm bằng smoke (BackHandler của sheet), dữ liệu máy đã khôi phục. Thêm mục EXC-13 vào "Recently implemented" của `doc/feature.md`. Đổi dòng 23 của `05_EXCLUSIVE_KILLER_FEATURES.md` thành `✅ Done (một chiều, văn bản thuần, không QR/không đồng bộ cuộn)` kèm liên kết `../done/EXC-13_second-screen-mirror_DONE.md`. `git rm doc/task/todo/EXC-13_second-screen-mirror.md`.
- [ ] **Step 8: Commit.** `git add app/src doc docs && git commit -m "feat(exc-13): complete the one-way second screen mirror"` (+ trailer). Xác nhận `git status --short | grep -v .idea` rỗng.

---

## Self-review

**1. Phủ spec.** Mục tiêu một chiều: Task 5, 6, 9. URL + Sao chép/Chia sẻ, không QR: Task 8. `ServerSocket` JDK, SSE: Task 5. Văn bản thuần, cắt trần: Task 3, và `MirrorHtml` dựng bằng `textContent`: Task 2. Token 128 bit/hết hạn/404 giống nhau/3 đường: Task 1, 2. Giới hạn 3 client, 20 req/phút: Task 1, 5. Bind đúng địa chỉ, không `0.0.0.0`: Task 4, 5. Vòng đời (rời trang, nền, hết hạn, mất mạng, công tắc): Task 6, 9. Không gửi dữ liệu ngoài bài: Task 2 (router), Task 3 (snapshot). Lỗi có kiểu, không crash: Task 5, 6. 38 locale: Task 7. Smoke bằng Mac: Task 10. Giới hạn nói thẳng + "chưa làm QR/cuộn": Task 10 DONE doc. Hai chỗ lệch spec được ghi rõ: thêm `Expired` vào trạng thái (Global Constraints) và thêm hằng `MAX_PATH_CHARS`, `MAX_TRACKED_IPS`.

**2. Placeholder.** Không còn "TBD"/"TODO". Task 8 Step 4 và Task 6 Step 5 yêu cầu **thêm test nếu mutation không bị bắt**; đó là bước kiểm chứng có chủ đích, không phải mục bỏ trống. Mã Task 3–5 mô tả thuật toán bằng lời kèm test đầy đủ làm đặc tả; đây là lựa chọn có chủ ý vì test là hợp đồng.

**3. Nhất quán kiểu.** `MirrorEngine.clients: StateFlow<Int>` dùng ở Task 5, 6; `FakeEngine.clientCount` chỉ là chi tiết test. `LanAddressSource` định nghĩa ở Task 6 và `LanAddressProvider` (Task 4) phải implement nó, được ghi ở Task 6 Step 3. `MirrorStartResult.Started(port, token)` thống nhất giữa Task 5 và 6. `MirrorSnapshot` khai báo ở Task 2 (rút gọn) và hoàn thiện ở Task 3.

**Rủi ro đã biết, để người thực thi cân nhắc:** (a) `SecureRandom` giả trong test Task 1 ghi đè `nextBytes`; nếu JDK bản mới đổi, dùng `java.util.Random` bọc. (b) Test `bindFailureIsATypedErrorNotACrash` dựa vào việc `203.0.113.1` không phải địa chỉ cục bộ; nếu máy CI có địa chỉ đó, đổi sang địa chỉ khác trong 203.0.113.0/24. (c) Robolectric cho `org.json` ở Task 3 JSON test; JVM thuần không có `org.json`.
