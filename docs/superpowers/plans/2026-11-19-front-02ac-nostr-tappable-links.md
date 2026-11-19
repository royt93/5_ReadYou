# FRONT-02ac — Link https trong bài Nostr bấm được: kế hoạch thực thi (TDD)

> **Cho người thực thi:** dùng superpowers:executing-plans (inline). Các bước dùng ô `- [ ]`.

**Mục tiêu:** link `https` trong chữ bài Nostr thành thẻ `<a href>` an toàn trong `rawDescription`, để trang đọc (đã có sẵn cách vẽ link bấm được và mở bằng `openURL`) cho người đọc bấm. Không tải gì khi mở bài.

**Kiến trúc:** hàm thuần `NostrFeedMapper.linkSpans(content)` tìm các đoạn link; `escapeToHtml` thoát phần chữ thường như cũ và bọc từng đoạn link bằng thẻ `a` có địa chỉ đã thoát. Chỉ một tệp production đổi.

**Công nghệ:** Kotlin, JUnit4 + Robolectric (`toArticles` dùng `titleFromBody`), Jsoup (đã có, để kiểm đúng điều trang đọc thấy), Gradle chạy nền.

**Spec:** `docs/superpowers/specs/2026-11-19-front-02ac-nostr-tappable-links-design.md` (commit `b7705cbf`).

## Global Constraints
- Nhánh `dev`; không worktree, không push, không `.idea`. **Chỉ JVM, không chạm thiết bị nào (không adb).**
- Production đổi **đúng một tệp**: `NostrFeedMapper.kt`. Không đổi trang đọc, `openURL`, UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay; không đổi chữ ký `toArticles`.
- Luật link: bắt đầu bằng `https://` (không phân biệt hoa thường), chạy tới khoảng trắng, **cắt dấu câu cuối** (`. , ; : ! ? ) ] } ' "`), phải còn ít nhất một ký tự sau `https://`, tối đa `MAX_LINK_URL_LENGTH = 2048` (quá dài thì giữ là chữ thường). Không `http`, `javascript:`, `data:`, `mailto:`, `nostr:`.
- Địa chỉ trong `href` và chữ hiển thị được thoát bằng **đúng hàm thoát cũ** (nay tên `escapePlain`), nên chữ bài không thể thoát khỏi thuộc tính hay chèn thẻ.
- R5: không magic number (hằng có tên), test kèm. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, ghi file, chờ `.exit`; lệnh chờ quá 595 giây thì tiếp tục bằng vòng chờ riêng. zsh: không `echo ====`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `encoding="utf-8"`); chuỗi Kotlin có dấu nháy ghi bằng heredoc `'EOF'`, không gõ qua đối số shell.
- Nói thẳng: link do tác giả chọn, người đọc tự chịu khi bấm; link kết thúc bằng `)` bị cắt mất `)`; bài đã lưu không đổi (chỉ bài mới có link bấm được); chưa kiểm bấm link trên máy.

## Điều chỉnh so với spec (đã đọc code thật)
1. **Không có test hiện có nào chứa link trong chữ bài** (đã grep `src/test`), nên đổi `escapeToHtml` không đổi kết quả test cũ; test `htmlInTheContent_isEscaped_…` (RICH) giữ nguyên.
2. **Hàm thoát cũ đổi tên** thành `escapePlain` (nội dung không đổi), `escapeToHtml` mới gọi nó cho từng đoạn. `visibleNotes` và `toArticles` vẫn gọi `escapeToHtml`; tiêu đề, `shortDescription`, `Article.link` không đổi vì thẻ `a` không đổi văn bản.
3. Test kiểm điều trang đọc thật sự thấy bằng Jsoup: `Jsoup.parse(raw, "https://njump.me/").selectFirst("a").attr("abs:href")` (đúng thuộc tính trang đọc dùng ở `HtmlToComposable` dòng 463).
4. Tiền tố trước link (vd `xhttps://…`) không được xử lý riêng: phần `https://…` vẫn thành link. Chấp nhận, nói trong báo cáo.
5. Mẫu đột biến tránh dấu `'` (đổi `.,;:!?)]}` thay vì cả hằng) để không phải lồng dấu nháy trong shell.

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN, kiểm tự động ở Task 2)
Test mới (lớp `NostrTappableLinksTest`): **L1** `anHttpsLink_becomesAnAnchor`, **L2** `trailingPunctuation_isLeftOutOfTheLink`, **L3** `anHttpLink_orAnotherScheme_staysPlainText`, **L4** `aLinkLongerThanTheLimit_staysPlainText_andOneOfExactlyTheLimitIsKept`, **L5** `theSchemeAlone_isNotALink`, **L6** `theScheme_isCaseInsensitive`, **L7** `markupInsideTheLink_cannotEscapeTheAttribute`, **L8** `anAmpersandInTheQuery_isEscapedInTheHref_andReadBackByTheParser`, **L9** `aLink_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink`, **L10** `aNoteWithoutALink_isEscapedAsBefore`, **L11** `twoLinksAndNewLines_keepTheTextBetween`.

| Đột biến | Đổi gì | Test đỏ mong đợi |
|---|---|---|
| base | không | không |
| l1 | nhận cả `http` (`https?://`) | L3 |
| l2 | không cắt dấu câu cuối | L2, L5 |
| l3 | bỏ giới hạn độ dài | L4 |
| l4 | địa chỉ trong `href` không thoát | L7, L8 |
| l5 | không bọc thẻ `a` (chỉ chữ) | L1, L7, L8, L11 |
| l6 | phân biệt hoa thường | L6 |
| l7 | hằng `LINK_SCHEME` thành `https:/` | L5 |
| l8 | bỏ `)` khỏi dấu câu cắt | L2 |
| l9 | giới hạn `>` thành `>=` | L4 |

Kiểu so: **đúng** (tập đỏ phải trùng chính xác). Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng, ghi trong báo cáo.

## Cấu trúc file
- Sửa: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt` (hằng, `linkSpans`, `escapeToHtml` mới, `escapePlain`).
- Tạo: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTappableLinksTest.kt`.
- Tài liệu: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02ac_nostr-tappable-links_DONE.md`, spec (mục "Điều chỉnh khi thực thi").

---

### Task 1: `linkSpans` và `escapeToHtml` bọc link (test đỏ trước)

**Files:** Tạo `NostrTappableLinksTest.kt`; sửa `NostrFeedMapper.kt`.

**Interfaces:** Produces: `NostrFeedMapper.linkSpans(content: String): List<IntRange>` (vị trí gồm cả hai đầu, theo chỉ số chữ gốc), `NostrFeedMapper.MAX_LINK_URL_LENGTH: Int` (= 2048).

- [ ] **Step 1: Viết test (đỏ vì lỗi biên dịch: chưa có `linkSpans`, `MAX_LINK_URL_LENGTH`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTappableLinksTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An https link in the text of a Nostr note becomes an anchor in the stored HTML, so the reading page makes it tappable. The text is
 * untrusted: the address is escaped like any other text, so it cannot leave the attribute or add a tag. Nothing is downloaded here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrTappableLinksTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private val limit = NostrFeedMapper.MAX_LINK_URL_LENGTH

    private fun articleOf(content: String): Article {
        val event = requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", "%064x".format(1)); put("pubkey", author); put("created_at", 1_700_000_000L); put("kind", 1)
                    put("tags", org.json.JSONArray()); put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
        return NostrFeedMapper.toArticles(listOf(event), "feed-1", 7, now).single()
    }

    private fun linksIn(content: String): List<String> = NostrFeedMapper.linkSpans(content).map { content.substring(it.first, it.last + 1) }

    /** A link of exactly [length] characters. */
    private fun linkOfLength(length: Int): String = "https://x.example/" + "a".repeat(length - "https://x.example/".length)

    @Test fun anHttpsLink_becomesAnAnchor() {
        assertEquals(
            "look <a href=\"https://x.example/a\">https://x.example/a</a> ok",
            articleOf("look https://x.example/a ok").rawDescription,
        )
    }

    @Test fun trailingPunctuation_isLeftOutOfTheLink() {
        assertEquals(
            listOf("https://x.example/a", "https://y.example/b"),
            linksIn("see https://x.example/a, and (https://y.example/b)."),
        )
        assertEquals(listOf("https://x.example/a"), linksIn("\"https://x.example/a\"!?"))
    }

    @Test fun anHttpLink_orAnotherScheme_staysPlainText() {
        val text = "http://x.example/a javascript:alert(1) data:text/html,x mailto:a@b.c nostr:npub1abc ftp://x.example/f"
        assertTrue(linksIn(text).isEmpty())
        assertTrue(!articleOf(text).rawDescription.contains("<a "))
    }

    @Test fun aLinkLongerThanTheLimit_staysPlainText_andOneOfExactlyTheLimitIsKept() {
        assertTrue(linksIn(linkOfLength(limit + 1)).isEmpty())
        val exact = linkOfLength(limit)
        assertEquals(limit, exact.length)
        assertEquals(listOf(exact), linksIn(exact))
    }

    @Test fun theSchemeAlone_isNotALink() {
        for (text in listOf("https://", "https://.", "https://,,", "say https:// now")) {
            assertTrue("'$text' must not be a link", linksIn(text).isEmpty())
        }
    }

    @Test fun theScheme_isCaseInsensitive() {
        assertEquals(listOf("HTTPS://X.EXAMPLE/A"), linksIn("HTTPS://X.EXAMPLE/A"))
    }

    @Test fun markupInsideTheLink_cannotEscapeTheAttribute() {
        val hostile = "https://x.example/a\"onmouseover=\"alert(1)<script>"
        val raw = articleOf(hostile).rawDescription
        val escaped = "https://x.example/a&quot;onmouseover=&quot;alert(1)&lt;script&gt;"
        assertEquals("<a href=\"$escaped\">$escaped</a>", raw)
        val anchors = Jsoup.parse(raw).select("a")
        assertEquals(1, anchors.size)
        assertEquals(1, anchors.single().attributes().size())
        assertTrue(Jsoup.parse(raw).select("script").isEmpty())
    }

    @Test fun anAmpersandInTheQuery_isEscapedInTheHref_andReadBackByTheParser() {
        val raw = articleOf("https://x.example/a?a=1&b=2").rawDescription
        assertTrue(raw, raw.contains("href=\"https://x.example/a?a=1&amp;b=2\""))
        // The reading page takes the address from this attribute (HtmlToComposable, "abs:href"), with the note's own address as base.
        assertEquals("https://x.example/a?a=1&b=2", Jsoup.parse(raw, "https://njump.me/").selectFirst("a")!!.attr("abs:href"))
    }

    @Test fun aLink_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink() {
        val a = articleOf("look https://x.example/a")
        assertEquals("look https://x.example/a", a.title)
        assertEquals("look https://x.example/a", a.shortDescription)
        assertTrue(a.link.startsWith(NostrFeedMapper.NJUMP_BASE + "note1"))
    }

    @Test fun aNoteWithoutALink_isEscapedAsBefore() {
        assertEquals("a &amp; b &lt;i&gt;<br>c", articleOf("a & b <i>\nc").rawDescription)
    }

    @Test fun twoLinksAndNewLines_keepTheTextBetween() {
        assertEquals(
            "one <a href=\"https://x.example/1\">https://x.example/1</a><br>two <a href=\"https://y.example/2\">https://y.example/2</a>",
            articleOf("one https://x.example/1\ntwo https://y.example/2").rawDescription,
        )
    }
}
EOF
rm -f /tmp/aac-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrTappableLinksTest" > /tmp/aac-t1.log 2>&1; echo $? > /tmp/aac-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aac-t1.exit && break; sleep 5; done; cat /tmp/aac-t1.exit; grep -E "^e: " /tmp/aac-t1.log | cut -c1-200 | head -4
```
Expected: `1`, lỗi biên dịch `Unresolved reference` cho `linkSpans` và `MAX_LINK_URL_LENGTH`.

- [ ] **Step 2: Viết mã tối thiểu trong `NostrFeedMapper.kt`**

```bash
cat > /tmp/aac-ins1.txt <<'EOF'

    /** The longest link made tappable. A longer one stays plain text, so one line of a note cannot fill the database. */
    const val MAX_LINK_URL_LENGTH = 2048
    private const val LINK_SCHEME = "https://"
    private const val LINK_TRAILING_PUNCTUATION = ".,;:!?)]}'\""
    private val LINK_CANDIDATE = Regex("""https://\S+""", RegexOption.IGNORE_CASE)

    /**
     * Where the https links are in the note text as the author typed it: from the scheme to the next space, without the punctuation that
     * ends a sentence, at least one character after the scheme, not longer than [MAX_LINK_URL_LENGTH]. Each range holds both ends.
     */
    fun linkSpans(content: String): List<IntRange> =
        LINK_CANDIDATE.findAll(content).mapNotNull { match ->
            val link = match.value.trimEnd { it in LINK_TRAILING_PUNCTUATION }
            if (link.length <= LINK_SCHEME.length || link.length > MAX_LINK_URL_LENGTH) null
            else match.range.first until match.range.first + link.length
        }.toList()
EOF
cat > /tmp/aac-ins2.txt <<'EOF'
    /** The note text as HTML: the plain parts escaped, each https link (see [linkSpans]) wrapped as an anchor whose address is escaped too. */
    private fun escapeToHtml(text: String): String = buildString {
        var cursor = 0
        for (span in linkSpans(text)) {
            append(escapePlain(text.substring(cursor, span.first)))
            val url = escapePlain(text.substring(span.first, span.last + 1))
            append("<a href=\"").append(url).append("\">").append(url).append("</a>")
            cursor = span.last + 1
        }
        append(escapePlain(text.substring(cursor)))
    }

    private fun escapePlain(text: String): String = buildString {
EOF
python3 - <<'EOF'
# -*- coding: utf-8 -*-
P = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(P, encoding="utf-8").read()
a1 = "        COVER_IMAGE_URL.findAll(content).map { it.value }.firstOrNull { it.length <= MAX_COVER_URL_LENGTH }\n"
a2 = "    private fun escapeToHtml(text: String): String = buildString {\n"
assert s.count(a1) == 1 and s.count(a2) == 1, "anchors not unique"
s = s.replace(a1, a1 + open("/tmp/aac-ins1.txt", encoding="utf-8").read(), 1)
s = s.replace(a2, open("/tmp/aac-ins2.txt", encoding="utf-8").read(), 1)
open(P, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/main | tail -1
```

- [ ] **Step 3: Chạy các lớp Nostr + sync + SubscribeViewModelNostr* (xanh)**

```bash
rm -f /tmp/aac-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/aac-t1.log 2>&1; echo $? > /tmp/aac-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/aac-t1.exit && break; sleep 5; done; cat /tmp/aac-t1.exit; grep -E " FAILED$|^e: " /tmp/aac-t1.log | head -5
```
Expected: `0`. Đỏ ở đâu thì đọc lý do, sửa mã hoặc test (không bỏ qua).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTappableLinksTest.kt
git commit -m "feat(front-02ac): an https link in a Nostr note becomes a tappable link on the reading page

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline -1; git status --short
```

---

### Task 2: Mutation JVM (9 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/aac_mut.sh`, `/tmp/aac_batch.sh`, `/tmp/aac_check.py`. Đột biến tạm trong `NostrFeedMapper.kt` (luôn khôi phục).

- [ ] **Step 1: Kiểm mẫu duy nhất (mỗi mẫu đúng 1 lần)**

```bash
cat > /tmp/aac_pat.py <<'EOF'
# -*- coding: utf-8 -*-
mp = open("app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt", encoding="utf-8").read()
pats = {
 "l1": 'Regex("""https://\\S+"""',
 "l2": "match.value.trimEnd { it in LINK_TRAILING_PUNCTUATION }",
 "l3": " || link.length > MAX_LINK_URL_LENGTH",
 "l4": "val url = escapePlain(text.substring(span.first, span.last + 1))",
 "l5": 'append("<a href=\\"").append(url).append("\\">").append(url).append("</a>")',
 "l6": 'https://\\S+""", RegexOption.IGNORE_CASE)',
 "l7": 'LINK_SCHEME = "https://"',
 "l8": ".,;:!?)]}",
 "l9": "link.length > MAX_LINK_URL_LENGTH",
}
print({k: mp.count(v) for k, v in pats.items()})
EOF
python3 /tmp/aac_pat.py
```
Expected: mọi nhãn `1`. Khác 1: đọc tệp, sửa mẫu ở `aac_batch.sh`.

- [ ] **Step 2: Dựng script**

```bash
sed -e 's#/tmp/z-#/tmp/aac-#g' /tmp/z_mut.sh > /tmp/aac_mut.sh; chmod +x /tmp/aac_mut.sh
cat > /tmp/aac_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
MP=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt
cp -p $MP /tmp/aac-orig-mp.kt
M=/tmp/aac_mut.sh
zsh $M base
zsh $M l1 $MP 'Regex("""https://\S+"""' 'Regex("""https?://\S+"""'
zsh $M l2 $MP 'match.value.trimEnd { it in LINK_TRAILING_PUNCTUATION }' 'match.value'
zsh $M l3 $MP ' || link.length > MAX_LINK_URL_LENGTH' ''
zsh $M l4 $MP 'val url = escapePlain(text.substring(span.first, span.last + 1))' 'val url = text.substring(span.first, span.last + 1)'
zsh $M l5 $MP 'append("<a href=\"").append(url).append("\">").append(url).append("</a>")' 'append(url)'
zsh $M l6 $MP 'https://\S+""", RegexOption.IGNORE_CASE)' 'https://\S+""")'
zsh $M l7 $MP 'LINK_SCHEME = "https://"' 'LINK_SCHEME = "https:/"'
zsh $M l8 $MP '.,;:!?)]}' '.,;:!?]}'
zsh $M l9 $MP 'link.length > MAX_LINK_URL_LENGTH' 'link.length >= MAX_LINK_URL_LENGTH'
zsh $M base
cmp -s /tmp/aac-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "uncommitted production diff: $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
cat > /tmp/aac_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
N = {"L1": "anHttpsLink_becomesAnAnchor", "L2": "trailingPunctuation_isLeftOutOfTheLink", "L3": "anHttpLink_orAnotherScheme_staysPlainText",
     "L4": "aLinkLongerThanTheLimit_staysPlainText_andOneOfExactlyTheLimitIsKept", "L5": "theSchemeAlone_isNotALink",
     "L6": "theScheme_isCaseInsensitive", "L7": "markupInsideTheLink_cannotEscapeTheAttribute",
     "L8": "anAmpersandInTheQuery_isEscapedInTheHref_andReadBackByTheParser",
     "L9": "aLink_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink", "L10": "aNoteWithoutALink_isEscapedAsBefore",
     "L11": "twoLinksAndNewLines_keepTheTextBetween"}
def S(*ks): return {N[k] for k in ks}
EXPECTED = {"l1": S("L3"), "l2": S("L2", "L5"), "l3": S("L4"), "l4": S("L7", "L8"), "l5": S("L1", "L7", "L8", "L11"),
            "l6": S("L6"), "l7": S("L5"), "l8": S("L2"), "l9": S("L4")}
bad = 0
for label, want in EXPECTED.items():
    try: text = open(f"/tmp/aac-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError: print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M): print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = got == want; bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/aac-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN"); bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
grep -c "aac-" /tmp/aac_mut.sh
```

- [ ] **Step 3: Chạy batch nền, chờ ALLDONE**

```bash
rm -f /tmp/aac-batch.log; ( zsh /tmp/aac_batch.sh > /tmp/aac-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/aac-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/aac-batch.log; cut -c1-160 /tmp/aac-batch.log | tail -3
```
Chưa có `ALLDONE`: chạy lại **chỉ** vòng chờ. Không sửa `NostrFeedMapper.kt` cho tới `ALLDONE`.

- [ ] **Step 4: Đối chiếu tự động**

```bash
python3 /tmp/aac_check.py
```
Expected: `ALL-MATCH`; cuối batch: `final mp: identical`, `uncommitted production diff: 0 lines`. Lệch: đọc lý do (tập đỏ + thông báo trong `/tmp/aac-mut-<nhãn>.log`): (a) dự đoán sai thì sửa bảng và `aac_check.py`, ghi báo cáo; (b) đột biến sống sót thì thêm khẳng định (Task 1) rồi chạy lại; (c) đột biến tương đương thì đổi thành đột biến thật.

---

### Task 3: JVM đầy đủ, phạm vi, tài liệu, commit

**Files:** Sửa `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi"); tạo `doc/task/done/FRONT-02ac_nostr-tappable-links_DONE.md`.

- [ ] **Step 1: JVM full (nền, chờ `.exit`)**

```bash
rm -f /tmp/aac-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/aac-jvm.log 2>&1; echo $? > /tmp/aac-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/aac-jvm.exit && break; sleep 5; done; cat /tmp/aac-jvm.exit 2>/dev/null || echo "still running"
```
Rồi: `grep -E "FAILED|tests completed" /tmp/aac-jvm.log | head -5; tail -2 /tmp/aac-jvm.log` và đếm test bằng đoạn python đọc `app/build/test-results/testDevDebugUnitTest/*.xml` (như FRONT-02z). Số trước **3.874** (chỉ để đối chiếu), kỳ vọng **3.885** (+11). Đỏ không liên quan: chạy riêng lớp đó 3 lần và chạy lại cả bộ, ghi thẳng.

- [ ] **Step 2: Kiểm phạm vi**

```bash
git diff --name-only b7705cbf HEAD -- app
```
Expected: đúng `NostrFeedMapper.kt` và `NostrTappableLinksTest.kt`.

- [ ] **Step 3: Báo cáo, feature.md, task gốc, spec**

Báo cáo `FRONT-02ac_nostr-tappable-links_DONE.md` (tiếng Việt đơn giản): việc đã làm; luật link; bảng 9 đột biến với tập đỏ **đo được**; JVM đo lại; **nói thẳng:** link do tác giả chọn nên người đọc tự chịu khi bấm (app không tải gì khi mở bài, `openURL` cắt tham số theo dõi theo cài đặt có sẵn); link kết thúc bằng `)` bị cắt mất `)`; link `http` và giao thức khác vẫn là chữ; chỉ bài mới có link bấm được (bài đã lưu giữ `rawDescription` cũ); `xhttps://…` vẫn tạo link ở phần `https://…`; chưa kiểm bấm link trên máy thật. `doc/feature.md`: thêm dòng FRONT-02ac sau dòng FRONT-02ab. Task gốc FRONT-02 (giữ trong `todo/`): mục "Cập nhật" ghi FRONT-02ac; ảnh trong trang đọc được thay bằng "link bấm được" (không tự tải ảnh). Spec: nối "Điều chỉnh khi thực thi".

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02ac): report tappable links in Nostr notes

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review
- **Spec coverage:** luật link (https, cắt dấu câu, độ dài, chỉ sau scheme còn ký tự, không phân biệt hoa thường) và hằng có tên → Task 1; thoát an toàn cho `href` và chữ hiển thị, tiêu đề/`shortDescription`/`link` không đổi, bài không link giống trước, nhiều link và xuống dòng → Task 1 (L1 đến L11); mutation → Task 2; JVM, phạm vi một tệp production, báo cáo nói thẳng riêng tư và `)` → Task 3; không tự tải, không công tắc/chuỗi → Global Constraints.
- **Placeholder scan:** không có.
- **Type consistency:** `linkSpans(String): List<IntRange>`, `MAX_LINK_URL_LENGTH`, `LINK_SCHEME`, `LINK_TRAILING_PUNCTUATION`, `LINK_CANDIDATE`, `escapePlain`, `escapeToHtml` dùng đồng nhất ở test, mã và mẫu đột biến; tên L1 đến L11 khớp `aac_check.py`.
