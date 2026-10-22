# FIX-18/19 Audit Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Khép các sai lệch log URL, OfflinePrecache, forensic và tài liệu đã xác minh khi rà soát FIX-18/19.

**Architecture:** Dùng lại URL redaction và FullContentPolicy sẵn có; chỉ gate nội dung precache tải mới. Forensic dùng helper nhỏ kiểm metadata và self-test Python stdlib, không phụ thuộc backup trong test. Không refactor parser/DAO hay thêm framework.

**Tech Stack:** Kotlin, Robolectric, MockK, MockWebServer, Python sqlite3/unittest, Gradle Java 17.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-22-fix-18-19-audit-followup-design.md`.
- Không tự commit/push, kể cả spec/plan. Mỗi bước commit mặc định của workflow được thay bằng review diff.
- Không tạo worktree hoặc tương tác thiết bị; giữ device lock Pixel nếu sau này người dùng yêu cầu device riêng.
- Không sửa/xoá body lịch sử; backup `/Users/loitran/podcast_bak_emulator/Reader` chỉ `mode=ro`, chỉ xuất hash/host/count, không body/ID/title thô.
- Không đổi schema, cap rawDescription, parser/request semantics, dependency, ad/IAP/money hay FIX-09.
- Cap nội dung mới: `FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES = 1_048_576` UTF-8 bytes; không truncate.
- Cache lịch sử nonblank tiếp tục reuse, không gate lại/xoá/fetch lại/ghi lại.
- Không sửa `.idea/deviceManager.xml`.
- Test regression phải RED vì assertion hành vi, không coi compile error là RED. Không báo test pass từ cache là chạy mới.
- Code/comment mới dùng tiếng Việt, technical identifiers giữ nguyên. Không thêm magic number nghiệp vụ hoặc `lateinit`/`!!` mới.

## Files và trách nhiệm

- `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`: sửa ba log URL đã xác định; không đổi request URL/parser mapping.
- `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperFullContentTest.kt`: hai log network và tên near-limit.
- `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperDescriptionTest.kt`: lỗi entry không lộ throwable/URL.
- `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperAudioTest.kt`: bổ sung userinfo/fragment cho entry log.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt`: gate nội dung mới.
- `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheServiceTest.kt`: cap/cached-history/tiếp tục bài sau.
- `tools/fix19_forensic.py`: counters, validation, lỗi metadata-only.
- `tools/test_fix19_forensic.py`: self-test không cần backup.
- Tài liệu FIX-18/19 và `doc/feature.md`: trạng thái/bằng chứng sau sửa.

---

### Task 1: Redact URL và bỏ throwable chứa dữ liệu nhạy cảm

**Interfaces:** Dùng `safeEntryUrl(url: String): String` private hiện có. Không API mới. `parseFullContent` và `searchFeed` giữ signature/hành vi return.

- [x] **Step 1: Thêm RED network regression** vào `RssHelperFullContentTest.kt` (thêm `assertNull` import).

```kotlin
@Test
fun oversizeLogsRedactUrlForKnownAndChunkedBodies() = runBlocking {
    val payload = "x".repeat(MAX_BYTES + 1)
    for (chunked in listOf(false, true)) {
        val response = MockResponse().setHeader("Content-Type", "text/html")
        if (chunked) response.setChunkedBody(payload, CHUNK_BYTES) else response.setBody(payload)
        server.enqueue(response)
        val link = server.url("/private-story").newBuilder()
            .username("PRIVATE_USER").password("PRIVATE_PASSWORD")
            .addQueryParameter("token", "PRIVATE_QUERY_TOKEN")
            .fragment("PRIVATE_FRAGMENT").build().toString()
        ShadowLog.clear()
        assertEquals("", helper.parseFullContent(link, TITLE))
        val entries = ShadowLog.getLogs().filter { it.msg.contains("source=Readability") }
        assertTrue(entries.isNotEmpty())
        val logs = entries.joinToString("\n") { it.msg }
        assertTrue(logs.contains("/private-story"))
        assertTrue(logs.contains(if (chunked) "bytes>$MAX_BYTES" else "bytes=${MAX_BYTES + 1}"))
        for (secret in listOf("PRIVATE_USER", "PRIVATE_PASSWORD", "PRIVATE_QUERY_TOKEN", "PRIVATE_FRAGMENT")) {
            assertFalse(logs.contains(secret))
        }
        entries.forEach { assertNull(it.throwable) }
    }
}
```

Thêm `const val CHUNK_BYTES = 8192` trong companion object; không đổi các fixture cũ. Đổi tên `acceptsResponseExactlyAtPolicyLimit` thành `acceptsReadableResponseNearPolicyLimit`, `MIN_EXACT_LIMIT_MARGIN_BYTES` thành `NEAR_LIMIT_MARGIN_BYTES`; giữ phép kiểm near-limit và fixture hiện tại.

- [x] **Step 2: Mở rộng RED malformed-entry regression** trong `RssHelperDescriptionTest.kt`.

Giữ `searchFeed_mappingFailureSkipsOnlyBadEntry`; đổi URL item Malformed thành `https://PRIVATE_USER:PRIVATE_PASSWORD@example.test/bad?token=PRIVATE_QUERY_TOKEN#PRIVATE_FRAGMENT`. Đổi exception giả lập và bổ sung assertion sau `searchFeed`:

```kotlin
every { RssDescriptionParser.shortDescription("Bad summary") } throws
    IllegalArgumentException("PRIVATE_EXCEPTION_SENTINEL https://example.test/?token=PRIVATE_QUERY_TOKEN")
org.robolectric.shadows.ShadowLog.clear()
val result = rssHelper.searchFeed(server.url("/guardian.xml").toString())
assertEquals(listOf("First", "Last"), result.articles.map { it.title })
val entries = org.robolectric.shadows.ShadowLog.getLogs()
    .filter { it.msg.contains("Skip malformed RSS entry") }
assertTrue(entries.isNotEmpty())
assertTrue(entries.any { it.msg.contains("IllegalArgumentException") })
val logs = entries.joinToString("\n") { it.msg }
for (secret in listOf("PRIVATE_USER", "PRIVATE_PASSWORD", "PRIVATE_QUERY_TOKEN", "PRIVATE_FRAGMENT", "PRIVATE_EXCEPTION_SENTINEL")) {
    org.junit.Assert.assertFalse(logs.contains(secret))
}
entries.forEach { org.junit.Assert.assertNull(it.throwable) }
```

Trong `RssHelperAudioTest.articleLogsOnlySizesAndRedactsSensitiveUrlQuery`, đổi URL thành `https://PRIVATE_USER:PRIVATE_PASSWORD@example.test/feed/item?token=SECRET_TOKEN#PRIVATE_FRAGMENT`; kiểm `/feed/item` tồn tại, user/password/query/fragment vắng mặt. Helper hiện có có thể bỏ hoàn toàn query marker nếu có userinfo; không ép marker `?<redacted>` ở trường hợp đó. Giữ test query-only riêng bằng lần gọi entry gốc hoặc thêm case query-only vào cùng test.

- [x] **Step 3: Chạy RED**:

```bash
./gradlew testDevDebugUnitTest --tests '*RssHelperFullContentTest' --tests '*RssHelperDescriptionTest' --tests '*RssHelperAudioTest' --rerun
```

Phải thấy secret assertion fail tại log Readability/malformed-entry. Entry userinfo có thể đã pass; không bịa RED cho hành vi đã đúng.

- [x] **Step 4: Sửa tối thiểu ba log** trong `RssHelper.kt`.

```kotlin
Log.w("RLog", "Skip malformed RSS entry: ${safeEntryUrl(entry.link.orEmpty())} errorType=${error.javaClass.simpleName}")
```

Hai nhánh oversized chỉ thay nội suy URL, giữ bytes và return:

```kotlin
Log.w("RLog", "Reject oversized fullContent source=Readability url=${safeEntryUrl(link)} bytes=$declaredBytes")
Log.w("RLog", "Reject oversized fullContent source=Readability url=${safeEntryUrl(link)} bytes>${FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES}")
```

Không truyền throwable/message vào malformed log. Không thay `Request.Builder().url(url)`, không scrub dữ liệu trước parser. Không mở rộng thành global logger.

- [x] **Step 5: Chạy GREEN cùng lệnh Step 3**, kiểm XML mới, rồi review diff. Không commit.

---

### Task 2: Gate fetched content tại OfflinePrecache

**Interfaces:** Dùng `FullContentPolicy.accept(String?): String?`, `logOversize(source, articleId, content)`, `ArticleDao.updateFullContent(accountId, articleId, content)`. Không đổi public signature.

- [x] **Step 1: Thêm regression** trong `OfflinePrecacheServiceTest.kt`, import `FullContentPolicy`.

```kotlin
@Test
fun precache_rejectsOversizeAndContinuesWithFollowingArticle() = runBlocking {
    every { service.isOnWifi() } returns true
    val following = sampleArticle.copy(id = "following", link = "https://example.com/following")
    val sentinel = "PRECACHE_PRIVATE_SENTINEL"
    val oversized = sentinel + "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1 - sentinel.length)
    coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns
        listOf(sampleArticle.toListItem(), following.toListItem())
    coEvery { articleDao.queryArticleBody(1, any()) } returns null
    coEvery { rssHelper.parseFullContent(sampleArticle.link, sampleArticle.title, any()) } returns oversized
    coEvery { rssHelper.parseFullContent(following.link, following.title, any()) } returns "<p>Accepted</p>"
    org.robolectric.shadows.ShadowLog.clear()
    assertEquals(1, service.precacheLatestUnread(1))
    coVerify(exactly = 0) { articleDao.updateFullContent(1, sampleArticle.id, any()) }
    coVerify(exactly = 1) { articleDao.updateFullContent(1, following.id, "<p>Accepted</p>") }
    coVerify(exactly = 0) { articleDao.update(any()) }
    io.mockk.verify(exactly = 0) { rssHelper.extractImageUrls(oversized) }
    val logs = org.robolectric.shadows.ShadowLog.getLogs().joinToString("\n") { it.msg }
    assertTrue(logs.contains("source=OfflinePrecache"))
    assertTrue(logs.contains("bytes=${FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1}"))
    org.junit.Assert.assertFalse(logs.contains(sentinel))
}

@Test
fun precache_acceptsExactCapFetchedContent() = runBlocking {
    every { service.isOnWifi() } returns true
    val content = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES)
    coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns listOf(sampleArticle.toListItem())
    coEvery { articleDao.queryArticleBody(1, sampleArticle.id) } returns null
    coEvery { rssHelper.parseFullContent(sampleArticle.link, sampleArticle.title, any()) } returns content
    assertEquals(1, service.precacheLatestUnread(1))
    coVerify(exactly = 1) { articleDao.updateFullContent(1, sampleArticle.id, content) }
}

@Test
fun precache_preservesOversizeHistoricalCache() = runBlocking {
    every { service.isOnWifi() } returns true
    val historical = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1)
    coEvery { articleDao.queryUnreadArticleList(1, OfflinePrecacheService.DEFAULT_LIMIT) } returns listOf(sampleArticle.toListItem())
    coEvery { articleDao.queryArticleBody(1, sampleArticle.id) } returns ArticleBody(sampleArticle.id, "raw", historical)
    assertEquals(1, service.precacheLatestUnread(1))
    coVerify(exactly = 0) { rssHelper.parseFullContent(any(), any(), any()) }
    coVerify(exactly = 0) { articleDao.updateFullContent(any(), any(), any()) }
    coVerify(exactly = 0) { articleDao.update(any()) }
}
```

Không in expected/actual body lớn trong assertion failure. Test exact/historical là preservation tests, không bắt buộc RED.

- [x] **Step 2: Chạy RED**:

```bash
./gradlew testDevDebugUnitTest --tests '*OfflinePrecacheServiceTest' --rerun
```

Case oversized phải fail vì cache count/write xảy ra trên code cũ.

- [x] **Step 3: Gate chỉ kết quả tải mới** trong `OfflinePrecacheService.kt`, thêm import policy và thay biểu thức `fullContent`:

```kotlin
val fullContent = storedContent
    ?.takeIf { it.isNotBlank() }
    ?: run {
        val fetched = runCatching {
            rssHelper.parseFullContent(article.link, article.title, sourceType)
        }.getOrNull().orEmpty()
        val accepted = FullContentPolicy.accept(fetched)
        if (accepted == null) {
            FullContentPolicy.logOversize("OfflinePrecache", article.id, fetched)
        }
        accepted.orEmpty()
    }
```

Giữ nguyên narrow update, Wi-Fi check, image metadata prefetch, sourceType WEB guard và cachedCount logic. Nội dung từ chối thành rỗng nên không lấy ảnh từ body đó; ảnh metadata vẫn có thể prefetch theo hành vi cũ.

- [x] **Step 4: Chạy GREEN** cùng lệnh Step 2, review diff và resource ownership. Không thêm timer/listener/stream. Không commit.

---

### Task 3: Forensic counters, validation và self-test độc lập backup

**Files:** sửa `tools/fix19_forensic.py`, tạo `tools/test_fix19_forensic.py`.

**Interfaces:** thêm `inspect_row(row) -> dict` thuần, dùng `EXPECTED` làm baseline validation. `main() -> int` giữ entrypoint, output chỉ JSON metadata an toàn; test patch constants/SQLite bằng unittest.mock. Không thêm runtime dependency hay CLI/config.

- [x] **Step 1: Tạo self-test**; bản đầu thiếu `inspect_row` sẽ là lỗi import, không tính RED. Trước khi chạy RED, thêm helper tạm dùng logic cũ (counter escape sai và chỉ so body length/hash), rồi bảo đảm assertion hành vi thất bại.

```python
import contextlib
import hashlib
import io
import json
import sqlite3
import unittest
from unittest.mock import patch

import fix19_forensic as audit


class ForensicTest(unittest.TestCase):
    def setUp(self):
        self.body = "\0\n\r\t�A"
        self.row = (
            "1$7f6946f8-09d6-4a88-a24a-5a3ce6f803a0",
            audit.ARTICLE_TITLE,
            "https://example.test/item?token=PRIVATE_LINK_SENTINEL",
            "", self.body, audit.FEED_URL,
        )
        expected = {
            "full_chars": len(self.body),
            "full_utf8_bytes": len(self.body.encode("utf-8")),
            "sha256": hashlib.sha256(self.body.encode("utf-8")).hexdigest(),
            "nul_chars": 1, "replacement_chars": 1, "control_chars": 1,
        }
        patcher = patch.object(audit, "EXPECTED", expected)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_control_count_excludes_lf_cr_tab_but_includes_nul(self):
        result = audit.inspect_row(self.row)
        self.assertEqual(1, result["control_chars"])
        self.assertEqual(1, result["nul_chars"])
        self.assertTrue(result["expected_metadata_match"])

    def test_wrong_metadata_is_rejected(self):
        changes = {
            0: ("not-a-uuid", "1$------------------------------------", "2$7f6946f8-09d6-4a88-a24a-5a3ce6f803a0"),
            1: ("wrong title",), 3: ("raw drift", None),
            4: (self.body + "drift", None), 5: ("https://wrong.example/feed",),
        }
        for index, alternatives in changes.items():
            for replacement in alternatives:
                with self.subTest(index=index):
                    row = list(self.row)
                    row[index] = replacement
                    self.assertFalse(audit.inspect_row(row)["expected_metadata_match"])

    def test_counter_drift_is_rejected(self):
        with patch.dict(audit.EXPECTED, control_chars=2):
            self.assertFalse(audit.inspect_row(self.row)["expected_metadata_match"])

    def run_main(self, rows):
        class Connection:
            def __init__(self):
                self.closed = False
            def execute(self, query, params):
                if not query.startswith("SELECT"):
                    raise AssertionError("Chỉ được SELECT")
                return self
            def fetchall(self):
                return rows
            def close(self):
                self.closed = True
        connection = Connection()
        out, err = io.StringIO(), io.StringIO()
        with patch.object(audit.sqlite3, "connect", return_value=connection) as connect:
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = audit.main()
        self.assertTrue(connect.call_args.kwargs["uri"])
        self.assertTrue(connect.call_args.args[0].endswith("?mode=ro"))
        self.assertTrue(connection.closed)
        return code, out.getvalue(), err.getvalue()

    def test_main_success_outputs_only_safe_metadata(self):
        code, out, err = self.run_main([self.row])
        self.assertEqual(0, code)
        self.assertTrue(json.loads(out)["expected_metadata_match"])
        for secret in (self.row[0], self.row[1], self.row[2], "PRIVATE_LINK_SENTINEL", self.body):
            self.assertNotIn(secret, out + err)

    def test_main_metadata_failure_returns_nonzero(self):
        row = list(self.row)
        row[3] = "PRIVATE_RAW_SENTINEL"
        code, out, err = self.run_main([row])
        self.assertNotEqual(0, code)
        self.assertNotIn("PRIVATE_RAW_SENTINEL", out + err)

    def test_main_missing_or_multiple_rows_returns_nonzero(self):
        for rows in ([], [self.row, self.row]):
            with self.subTest(count=len(rows)):
                code, _, _ = self.run_main(rows)
                self.assertNotEqual(0, code)

    def test_read_errors_do_not_print_exception_message(self):
        class FailingConnection:
            closed = False
            def execute(self, *args):
                raise sqlite3.DatabaseError("PRIVATE_DATABASE_SENTINEL")
            def close(self):
                self.closed = True
        for fail_open in (False, True):
            connection = FailingConnection()
            out, err = io.StringIO(), io.StringIO()
            with patch.object(audit.sqlite3, "connect", side_effect=sqlite3.OperationalError("PRIVATE_DATABASE_SENTINEL") if fail_open else None, return_value=connection):
                with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    code = audit.main()
            self.assertNotEqual(0, code)
            self.assertNotIn("PRIVATE_DATABASE_SENTINEL", out.getvalue() + err.getvalue())
            if not fail_open:
                self.assertTrue(connection.closed)


if __name__ == "__main__":
    unittest.main()
```

Thêm `FEED_URL = "https://www.npr.org/sections/news/"` vào script trước RED để test chạy được. Helper characterization tạm cụ thể (chỉ tồn tại trong RED, thay bằng implementation Step 3):

```python
def inspect_row(row):
    body = row[4] or ""
    encoded = body.encode("utf-8")
    return {
        "control_chars": sum(ord(ch) < 32 and ch not in "\\\\n\\\\r\\\\t" for ch in body),
        "nul_chars": body.count(chr(0)),
        "expected_metadata_match": (
            len(body) == EXPECTED["full_chars"]
            and len(encoded) == EXPECTED["full_utf8_bytes"]
            and hashlib.sha256(encoded).hexdigest() == EXPECTED["sha256"]
        ),
    }
```

Dùng connection giả cho test; không copy body lịch sử. RED chỉ cần xác minh hai test helper counter/metadata thất bại do assertion; các main tests sẽ GREEN sau Step 3.

- [x] **Step 2: Chạy RED**:

```bash
python3 -B -m unittest discover -s tools -p 'test_fix19_forensic.py' -v
```

Helper characterization tạm phải đếm 4 thay vì 1 hoặc chấp nhận metadata sai. Chỉ ghi số/test name khi báo RED; không dump record/body.

- [x] **Step 3: Hoàn thiện helper và main**. Giữ `DB_PATH`, `ARTICLE_TITLE` hiện có; thêm imports `json`, `urlsplit` và mở rộng baseline:

```python
EXPECTED = {
    "full_chars": 12_324_490,
    "full_utf8_bytes": 15_890_764,
    "sha256": "2ea6f03e5654eb3eda859d29a78e5111820c98b6fca14b0cf3461d8f752e18e1",
    "nul_chars": 63_610,
    "replacement_chars": 1_713_674,
    "control_chars": 518_528,
}
FEED_URL = "https://www.npr.org/sections/news/"
LOCAL_ID_PATTERN = r"1\$[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
IGNORED_C0_WHITESPACE = "\n\r\t"
C0_LIMIT = 32


def inspect_row(row):
    article_id, title, link, raw, full, feed_url = row
    is_text_body = isinstance(full, str)
    body = full if is_text_body else ""
    encoded = body.encode("utf-8")
    counters = {
        "full_chars": len(body),
        "full_utf8_bytes": len(encoded),
        "sha256": hashlib.sha256(encoded).hexdigest(),
        "nul_chars": body.count(chr(0)),
        "replacement_chars": body.count(chr(0xfffd)),
        "control_chars": sum(ord(ch) < C0_LIMIT and ch not in IGNORED_C0_WHITESPACE for ch in body),
    }
    local_id = isinstance(article_id, str) and re.fullmatch(LOCAL_ID_PATTERN, article_id) is not None
    valid = (
        local_id and title == ARTICLE_TITLE and raw == "" and is_text_body
        and feed_url == FEED_URL
        and all(counters[key] == value for key, value in EXPECTED.items())
    )
    def digest(value):
        return hashlib.sha256(value.encode("utf-8")).hexdigest() if isinstance(value, str) else None
    return {
        "article_id_sha256": digest(article_id),
        "id_is_local_uuid": local_id,
        "title_sha256": digest(title),
        "link_host": urlsplit(link).hostname if isinstance(link, str) else None,
        "feed_url_host": urlsplit(feed_url).hostname if isinstance(feed_url, str) else None,
        "raw_utf8_bytes": len(raw.encode("utf-8")) if isinstance(raw, str) else None,
        **counters,
        "expected_metadata_match": bool(valid),
        "writer_assessment": "local UUID consistent with RSS ingestion; later enrichment cannot be excluded; historical writer unproven",
    }


def main() -> int:
    con = None
    try:
        con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
        rows = con.execute(
            "SELECT a.id,a.title,a.link,a.rawDescription,a.fullContent,f.url "
            "FROM article a JOIN feed f ON f.id=a.feedId "
            "WHERE f.url=? AND a.title=?",
            (FEED_URL, ARTICLE_TITLE),
        ).fetchall()
        if len(rows) != 1:
            print(f"forensic_error=row_count count={len(rows)}", file=sys.stderr)
            return 2
        result = inspect_row(rows[0])
        print(json.dumps(result, ensure_ascii=True, sort_keys=True))
        return 0 if result["expected_metadata_match"] else 1
    except (sqlite3.Error, ValueError, TypeError) as exc:
        print(f"forensic_error={type(exc).__name__}", file=sys.stderr)
        return 2
    finally:
        if con is not None:
            con.close()
```

Giữ `if __name__ == "__main__": raise SystemExit(main())`. Không in `exc`, row hoặc traceback. `UnicodeError` thuộc `ValueError`, nên decode/encode failures đi qua thông báo loại lỗi. Dùng JSON metadata không làm thay đổi quyết định writer lịch sử; docs ghi rõ output đổi từ key=value sang JSON.

- [x] **Step 4: GREEN + kiểm backup read-only**:

```bash
python3 -B -m unittest discover -s tools -p 'test_fix19_forensic.py' -v
python3 -B tools/fix19_forensic.py
```

Expected self-test pass không cần DB thật; script thật exit 0, hash giữ nguyên, control_chars 518528. Không chuyển body sang test fixture. Review diff, không commit.

---

### Task 4: Kiểm chứng mới và đồng bộ tài liệu

**Files:**
- `doc/feature.md`
- `doc/task/done/FIX-18_oversized-rss-full-content_DONE.md`
- `doc/task/done/FIX-19_oversized-body-forensics_DONE.md`
- `docs/superpowers/plans/2026-10-22-fix-18-oversized-rss-full-content.md`
- `docs/superpowers/plans/2026-10-22-fix-19-oversized-body-forensics.md`
- Spec/plan follow-up này: cập nhật trạng thái/checklist khi thực sự xong.

**Interfaces:** Không production API; chỉ bằng chứng actual run mới và commit ancestry đã có.

- [x] **Step 1: Chạy kiểm chứng**; không cần device. Build chỉ tạo APK, không install/run.

```bash
python3 -B -m unittest discover -s tools -p 'test_fix19_forensic.py' -v
python3 -B tools/fix19_forensic.py
./gradlew testDevDebugUnitTest --rerun
./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin
git diff --check
```

Không dùng `JAVA_TOOL_OPTIONS` để kiểm cấu hình ByteBuddy trong repo. Nếu task test ghi `FROM-CACHE`/`UP-TO-DATE`, không nhận đó là fresh execution; chạy task với `--rerun`. Nếu test fail, giữ trạng thái in progress và điều tra, không đóng bằng số pass cũ.

- [x] **Step 2: Đếm XML mới**, không đoán test count tăng bao nhiêu:

```bash
python3 - <<'PY'
from pathlib import Path
import json
import xml.etree.ElementTree as ET
files = list(Path('app/build/test-results/testDevDebugUnitTest').glob('TEST-*.xml'))
assert files
counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
for file in files:
    result = ET.parse(file).getroot()
    for key in counts:
        counts[key] += int(result.get(key, 0))
print(json.dumps({'classes': len(files), **counts}))
assert counts['failures'] == counts['errors'] == counts['skipped'] == 0
PY
```

Lưu log command full run và đối chiếu task thực thi trước khi dùng số này trong báo cáo. Lượt focused phải chạy trước full; không chạy focused sau rồi đếm nhầm subset.

- [x] **Step 3: Khôi phục index và sửa báo cáo**.

Lấy entry FIX-18 từ lịch sử để tham khảo:

```bash
git show d236f3a6^:doc/feature.md
git merge-base --is-ancestor ee236873 origin/dev
git merge-base --is-ancestor cff1472f origin/dev
```

Trong `doc/feature.md`, chèn mục riêng trước FIX-17, không thay FIX-19:

```markdown
- **FIX-18 — Giới hạn fullContent RSS** (`doc/task/done/FIX-18_oversized-rss-full-content_DONE.md`): policy 1 MiB UTF-8 cho fullContent mới; giữ metadata và dữ liệu lịch sử. RSS/provider/ReadLater được cap; OfflinePrecache nay có gate trực tiếp cho nội dung tải mới sau bản sửa rà soát. Nguồn body lịch sử 15,9 MB chưa xác định. Các commit FIX-18 gốc đã push; bản sửa rà soát chưa commit/push. Kết quả kiểm chứng mới xem báo cáo FIX-18/19.
```

Chỉ ghi “nay có gate” sau Task 2 GREEN. Chuyển picked/in-progress follow-up thành ghi chú hoàn tất kiểm chứng nhưng chưa commit/push; giữ feature trạng thái gốc implemented. Không sửa trạng thái push của tính năng khác ngoài scope.

Báo cáo FIX-18/19: thay status đầu bằng `Bản gốc đã push đến origin/dev cff1472f; bản sửa sau rà soát chưa commit/push` nếu ancestry còn đúng. Thêm mục follow-up ngày 2026-10-22 nêu từng thay đổi, actual commands/counts, output JSON forensic, counter C0 gồm NUL. Không xoá bằng chứng 1245/1246 lịch sử; phân biệt với lượt chạy mới. Với focused matrix cũ 45 vs tổng list 46, ghi rõ `Số 45 trong báo cáo cũ không nhất quán với danh sách class trong plan; không dùng làm bằng chứng cho bản sửa này.` và dùng XML mới cho follow-up. Không claim mọi URL/log toàn app đã scrub.

Plan FIX-18: thêm ghi chú kế dòng BLOCKED rằng trạng thái đó là trước override Pixel; báo cáo sau ghi targeted isolated instrumentation 1/1 trên Pixel, không phải UI Subscribe smoke đầy đủ đã lên kế hoạch. Step device ghi `được thay bằng targeted instrumentation; UI smoke Subscribe không chạy`, không tick giả toàn bộ. Step report/commit chỉ tick khi git history/báo cáo chứng minh; commit follow-up vẫn chưa thực hiện.

Plan FIX-19: Task 4 report/validation/index có bằng chứng lịch sử thì tick kèm ghi chú follow-up; bỏ chỉ dẫn xoá standalone todo không tồn tại, thay bằng `Không tạo standalone todo trong đợt gốc; không có file cần xoá.` Không giả số lint/device. Checklist commit mô tả commit cũ đã có, không ngụ ý follow-up đã commit.

- [x] **Step 4: Kiểm cuối**:

```bash
git diff --check
git status -sb
git diff --stat
```

Đọc diff code/test/doc, soát null safety và tài nguyên; servers đóng trong tearDown, mock objects unmock trong tearDown, SQLite đóng trong finally. Không thêm stream/timer/listener. Không báo exact historical writer đã xác định. Không commit/push. Báo kết quả và chờ yêu cầu tiếp theo.

## Tự kiểm plan

- Log URL: Task 1, cả network và lỗi mapping, query-only + userinfo/fragment.
- Gate mới/cache lịch sử/tiếp tục bài sau: Task 2.
- Forensic counters/metadata/read errors/self-test/backup read-only: Task 3.
- Tên near-limit/index/push/checklist/validation: Tasks 1 và 4.
- Không có dependency/schema/device/historical cleanup. Không thực thi tự động commit/worktree từ skill.
