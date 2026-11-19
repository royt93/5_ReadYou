# FRONT-02ad — Nostr hiện mọi trả lời, kèm link tới bài gốc: kế hoạch thực thi (TDD)

> **Cho người thực thi:** dùng superpowers:executing-plans (inline). Các bước dùng ô `- [ ]`.

**Mục tiêu:** bài Nostr trả lời (cả trả lời chính mình lẫn người khác) được hiện như bài thường, có thêm một dòng cuối `↩ <link njump của bài gốc>` bấm được. Bộ lọc chuỗi của FRONT-02x bị xóa.

**Kiến trúc:** `visibleNotes` chỉ còn lọc "có `noteId` hợp lệ và tiêu đề không rỗng". Hàm thuần mới `NostrFeedMapper.replyTargetUrl(event)` dựng link `https://njump.me/note1…` từ `event.repliedToId` (id đã kiểm dạng hex thường). `toArticles` thêm `<br>↩ <a href="URL">URL</a>` vào `rawDescription`. `NostrEvent.isReply` bị xóa nếu không còn nơi dùng.

**Công nghệ:** Kotlin, JUnit4 + Robolectric (`toArticles` dùng `titleFromBody`), Jsoup (đã có), Gradle chạy nền.

**Spec:** `docs/superpowers/specs/2026-11-19-front-02ad-nostr-show-all-replies-design.md` (commit `3461d93f`).

## Global Constraints
- Nhánh `dev`; không worktree, không push, không `.idea`. **Chỉ JVM, không chạm thiết bị nào (không adb).**
- Production đổi: `NostrFeedMapper.kt` (bỏ `keepOriginalsAndOwnThreads`, thêm `replyTargetUrl`, `REPLY_MARK`, dòng link, KDoc), `NostrEvent.kt` (xóa `isReply` và `answeredTags` nếu hết dùng; `repliedToId` giữ), KDoc `NostrFetchResult.kt`. **Không đổi** `NostrRelayClient.kt` (`fetch` vẫn gọi `visibleNotes`), `LocalRssSv`, `SubscribeViewModel`, UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay; không đổi chữ ký `toArticles`, `visibleNotes`, `fetch`.
- Link bài gốc: `NJUMP_BASE + NostrKeys.noteId(repliedToId)`; dựng từ id đã kiểm (64 hex thường), **không** lấy từ chữ bài; thoát bằng `escapePlain`. Dòng link chỉ thêm khi `repliedToId != null`. `title`, `shortDescription`, `link` giữ như cũ (tính từ chữ bài, không từ dòng link).
- R5: không magic number (hằng `REPLY_MARK = "↩"`), test kèm. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, ghi file, chờ `.exit`; lệnh chờ quá 595 giây thì tiếp tục bằng vòng chờ riêng. zsh: không `echo ====`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `encoding="utf-8"`); đoạn Kotlin có `"` ghi bằng heredoc `'EOF'`.
- Nói thẳng: nguồn đã đăng ký nhận thêm bài trả lời chưa đọc ở lần đồng bộ sau; tác giả hay trả lời thì 20 bài có thể toàn trả lời; mất phòng thủ vòng lặp của 02x (không còn đi theo chuỗi); số test **giảm** vì xóa test chuỗi; chưa kiểm máy thật.

## Điều chỉnh so với spec (đã đọc code và test thật)
1. **`isReply` còn dùng ở đâu:** chỉ `keepOriginalsAndOwnThreads` (xóa) và test `NostrEventTest.isReply_isTrueOnlyWithAnETag`, `NostrEventThreadTest`. Task 3 chạy `grep` xác nhận rồi xóa `isReply`, `answeredTags` **không** (còn `repliedToId` dùng `answeredTags`): giữ `answeredTags`, chỉ xóa `isReply`.
2. **Test `NostrFeedMapperThreadTest` (18 test):** giữ lớp, xóa các test kiểm bộ lọc chuỗi (`aReplyToSomeoneElse_isDropped`, `aReplyToADroppedNote_isDropped`, `aLoopOfReplies_endsAndDropsBoth`, `aNoteReplyingToItself_isDropped`, `aThreadBehindManyAnswersToOthers_isStillShown`, `aReplyWhoseTargetIdIsBroken_isDropped`, `aReplyNamingATargetInCapitals_isDropped`) và `visibleNotes_dropsRepliesToOthersAndBlankNotes` (sửa lại cho luật mới); sửa `anOwnThread_isKeptWholeNewestFirst` etc. nếu tiêu đề đổi: **tiêu đề không đổi**, nên các test thứ tự/tiêu đề/link/hai lần/20 bài còn đúng. `visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles`: đổi dữ liệu `mix` (bỏ kỳ vọng "trả lời người lạ bị bỏ").
3. **Test relay 02z dùng `REPLY` làm "chỉ có trả lời người lạ"** (A1, A3, A5, A6, A7 + `fetch_returnsEmpty…`): `REPLY` nay là bài hiện được. Đổi chúng sang dùng `EMPTY` (bài trống, vẫn không hiện được). A1 (`…OnlyRepliesToOthers`) sau khi `REPLY` thành bài hiện được **trùng với A4** (`…OnlyABlankNote`, vốn dùng `EMPTY`): xóa A1, giữ A4. `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped` đổi thành `aBlankNote_isSkipped_butAReplyIsShown`. Test `fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes` (FOREIGN) không đổi.
4. **`REPLY` mẫu** là kind 1, thẻ `["e","aaaa…","","reply"]`, id `36432db1e953c2945198a3326740ce3c57c06f0fffd075d3762bb743704b0bed`; link bài gốc kỳ vọng `https://njump.me/` + `NostrKeys.noteId("a" * 64)` (tính trong test, không chép tay).
5. **Test mới đặt trong lớp mới `NostrRepliesTest`** (Robolectric), không nhét vào lớp cũ, để dễ thấy phần xóa và phần thêm.
6. Mẫu đột biến `m*` dưới đây đã dò duy nhất ở Task 2 Step 1.
7. **Phát sinh khi chạy (không có trong plan ban đầu):** `LocalRssSvNostrSyncTest.anOwnThread_isSyncedWhole` cũng đỏ (còn kỳ vọng "trả lời người lạ bị bỏ"). Đổi tên thành `anOwnThread_andAnAnswerToAStranger_areSynced`, kỳ vọng thêm bài trả lời người lạ và dòng `↩ link`; không xóa test. Số test không đổi vì chỉ đổi tên.

## Danh sách test xóa (nói thẳng trong báo cáo)
`NostrFeedMapperThreadTest`: `aReplyToSomeoneElse_isDropped`, `aReplyToADroppedNote_isDropped`, `aLoopOfReplies_endsAndDropsBoth`, `aNoteReplyingToItself_isDropped`, `aThreadBehindManyAnswersToOthers_isStillShown`, `visibleNotes_dropsRepliesToOthersAndBlankNotes`, `aReplyWhoseTargetIdIsBroken_isDropped`, `aReplyNamingATargetInCapitals_isDropped` (8). `NostrEventTest`: `isReply_isTrueOnlyWithAnETag` (1). `NostrEventThreadTest`: đổi các khẳng định `isReply` thành chỉ kiểm `repliedToId` (không xóa test, bỏ dòng `isReply`). `NostrRelayClientTest`: `fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers` (A1, trùng với test bài trống A4 sau khi `REPLY` thành bài hiện được) (1). Tổng **10 test xóa**, thêm **11** test mới (10 + `fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply`).

## Bảng tập test đỏ mong đợi của đột biến (DỰ ĐOÁN, kiểm tự động ở Task 2)
Test mới (lớp `NostrRepliesTest`): **R1** `aReplyToAStranger_isShown_withALinkToTheNoteItAnswers`, **R2** `anOwnReply_isShown_withALinkToItsTarget`, **R3** `theTarget_isTheReplyMarker_thenRoot_thenTheLastOldStyleTag`, **R4** `aReplyWhoseTargetIsBroken_isShown_withoutALink`, **R5** `aMentionOnlyNote_hasNoReplyLine`, **R6** `anOrdinaryNote_hasNoReplyLine`, **R7** `theReplyLine_isAnEscapedAnchorAfterABreak`, **R8** `aReply_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink`, **R9** `aBlankReply_isStillNotShown`, **R10** `repliesAndOrdinaryNotes_shareTheTwentyNewestFirst`. Chạy cùng: M1 `NostrFeedMapperTest.aBlankNote_isSkipped_butAReplyIsShown`, V1 `NostrFeedMapperThreadTest.visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles`, A1 `NostrRelayClientTest.fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote_orOnlyUnshowable…` (tên chốt khi viết, xem Task 1).

| Đột biến | Đổi gì | Test đỏ mong đợi |
|---|---|---|
| base | không | không |
| m1 | `visibleNotes` lại bỏ trả lời (`repliedToId == null`) | R1, R2, R7, R8, R10, M1, V1, `fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply` (ít nhất) |
| m2 | bỏ dòng link (không thêm) | R1, R2, R7 và `anOwnThread_andAnAnswerToAStranger_areSynced` (**đo được**; dự đoán đầu thiếu test đồng bộ) |
| m3 | link dùng id của chính trả lời (`event.id`) | R1, R2, R3, R7 |
| m4 | bỏ `REPLY_MARK` (chỉ còn `<br> <a …`) | R1, R7 và test đồng bộ (**đo được**) |
| m5 | mọi bài đều có dòng link (id bài gốc, hoặc id chính nó khi không có) | R2, R4, R5, R6 (ít nhất) |
| m6 | bài có thẻ `e` mà không tìm được bài gốc bị bỏ | R4, R5 và `aMentionOnlyNote_isKeptLikeAnyNote` |
| m7 | bỏ `<br>` trước `↩` | R1, R7 và test đồng bộ (**đo được**) |
| m8 | bỏ khoảng trắng sau `↩` | R1, R7 và test đồng bộ (**đo được**) |
| m9 | bỏ bộ lọc tiêu đề rỗng khỏi `visibleNotes` | R9, M1, V1 (ít nhất) |

Kiểu so: **đúng** (tập đỏ phải trùng chính xác), trừ khi ghi chú ở báo cáo. Đây là **dự đoán**; lệch thì đọc lý do, sửa bảng, ghi trong báo cáo. (Nhiều đột biến sẽ làm đỏ thêm test cũ còn lại như `eachPieceOfAThreadHasItsOwnTitleAndLink`: kiểm khi chạy, không đoán thêm ở đây.)

## Cấu trúc file
- Sửa: `NostrFeedMapper.kt`, `NostrEvent.kt`, `NostrFetchResult.kt` (KDoc).
- Tạo: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRepliesTest.kt`.
- Sửa test: `NostrFeedMapperThreadTest.kt`, `NostrFeedMapperTest.kt`, `NostrEventTest.kt`, `NostrEventThreadTest.kt`, `NostrRelayClientTest.kt`.
- Tài liệu: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02ad_nostr-show-all-replies_DONE.md`, báo cáo 02x/02y/02z (ghi chú "thay bằng 02ad"), spec (mục "Điều chỉnh khi thực thi").

---

### Task 1: Dòng link bài gốc và luật `visibleNotes` mới (test đỏ trước)

**Files:** Tạo `NostrRepliesTest.kt`; sửa `NostrFeedMapper.kt`.

**Interfaces:** Produces: `NostrFeedMapper.replyTargetUrl(event: NostrEvent): String?`, `NostrFeedMapper.REPLY_MARK: String` (= "↩").

- [ ] **Step 1: Viết test mới (đỏ vì lỗi biên dịch: chưa có `replyTargetUrl`, `REPLY_MARK`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRepliesTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every reply is shown, with one last line "<mark> <link>" that points at the note it answers on njump. The link is built by the app from
 * a checked id (never taken from the note text) and nothing is downloaded. The notes are built by hand (the mapper never checks the signature).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRepliesTest {
    private val now = 1_800_000_000_000L
    private val author = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    private fun id(n: Int) = "%064x".format(n)
    private fun note(n: Int, createdAt: Long, content: String, vararg tags: List<String>): NostrEvent =
        requireNotNull(
            NostrEvent.parse(
                org.json.JSONObject().apply {
                    put("id", id(n)); put("pubkey", author); put("created_at", createdAt); put("kind", 1)
                    put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
                    put("content", content); put("sig", "0".repeat(128))
                }.toString(),
            ),
        )
    private fun reply(n: Int, createdAt: Long, content: String, to: String, marker: String = "reply") =
        note(n, createdAt, content, listOf("e", to, "", marker))

    private fun articlesOf(vararg events: NostrEvent) = NostrFeedMapper.toArticles(events.toList(), "feed-1", 7, now)
    private fun njump(targetId: String) = NostrFeedMapper.NJUMP_BASE + requireNotNull(NostrKeys.noteId(targetId))

    @Test fun aReplyToAStranger_isShown_withALinkToTheNoteItAnswers() {
        val a = articlesOf(reply(1, 1_700_000_100L, "Answer to a stranger", id(99))).single()
        assertEquals("Answer to a stranger", a.title)
        assertTrue(a.rawDescription, a.rawDescription.endsWith("<br>${NostrFeedMapper.REPLY_MARK} <a href=\"${njump(id(99))}\">${njump(id(99))}</a>"))
    }

    @Test fun anOwnReply_isShown_withALinkToItsTarget() {
        val articles = articlesOf(note(1, 1_700_000_100L, "Part one"), reply(2, 1_700_000_200L, "Part two", id(1)))
        assertEquals(listOf("Part two", "Part one"), articles.map { it.title })
        assertTrue(articles[0].rawDescription.contains("href=\"${njump(id(1))}\""))
        assertFalse(articles[1].rawDescription.contains("<a "))
    }

    @Test fun theTarget_isTheReplyMarker_thenRoot_thenTheLastOldStyleTag() {
        val both = note(1, 1_700_000_100L, "both", listOf("e", id(10), "", "root"), listOf("e", id(11), "", "reply"))
        val rootOnly = note(2, 1_700_000_200L, "root only", listOf("e", id(12), "", "root"))
        val old = note(3, 1_700_000_300L, "old style", listOf("e", id(13)), listOf("e", id(14)))
        assertEquals(njump(id(11)), NostrFeedMapper.replyTargetUrl(both))
        assertEquals(njump(id(12)), NostrFeedMapper.replyTargetUrl(rootOnly))
        assertEquals(njump(id(14)), NostrFeedMapper.replyTargetUrl(old))
    }

    @Test fun aReplyWhoseTargetIsBroken_isShown_withoutALink() {
        val broken = note(1, 1_700_000_100L, "Answers nothing findable", listOf("e", "not-an-id", "", "reply"))
        assertNull(NostrFeedMapper.replyTargetUrl(broken))
        val a = articlesOf(broken).single()
        assertEquals("Answers nothing findable", a.rawDescription)
    }

    @Test fun aMentionOnlyNote_hasNoReplyLine() {
        val quoting = note(1, 1_700_000_100L, "Look at this", listOf("e", id(9), "", "mention"))
        assertNull(NostrFeedMapper.replyTargetUrl(quoting))
        assertEquals("Look at this", articlesOf(quoting).single().rawDescription)
    }

    @Test fun anOrdinaryNote_hasNoReplyLine() {
        val plain = note(1, 1_700_000_100L, "Just a note", listOf("t", "news"))
        assertNull(NostrFeedMapper.replyTargetUrl(plain))
        assertFalse(articlesOf(plain).single().rawDescription.contains(NostrFeedMapper.REPLY_MARK))
    }

    @Test fun theReplyLine_isAnEscapedAnchorAfterABreak() {
        val raw = articlesOf(reply(1, 1_700_000_100L, "Hi & <b>bye</b>", id(5))).single().rawDescription
        assertTrue(raw, raw.startsWith("Hi &amp; &lt;b&gt;bye&lt;/b&gt;<br>${NostrFeedMapper.REPLY_MARK} <a href=\"https://njump.me/note1"))
        // What the reading page reads (HtmlToComposable, "abs:href"): exactly the one address the app built.
        assertEquals(njump(id(5)), Jsoup.parse(raw, "https://njump.me/").selectFirst("a")!!.attr("abs:href"))
        assertEquals(1, Jsoup.parse(raw).select("a").size)
    }

    @Test fun aReply_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink() {
        val a = articlesOf(reply(1, 1_700_000_100L, "Short answer", id(5))).single()
        assertEquals("Short answer", a.title)
        assertEquals("Short answer", a.shortDescription)
        assertEquals(njump(id(1)), a.link)
    }

    @Test fun aBlankReply_isStillNotShown() {
        assertTrue(articlesOf(reply(1, 1_700_000_100L, "   ", id(5))).isEmpty())
    }

    @Test fun repliesAndOrdinaryNotes_shareTheTwentyNewestFirst() {
        val ordinary = (0 until 10).map { n -> note(100 + n, 1_700_000_000L + n, "ordinary $n") }
        val replies = (0 until 15).map { n -> reply(200 + n, 1_700_001_000L + n, "reply $n", id(900 + n)) }
        val articles = articlesOf(*(ordinary + replies).toTypedArray())
        assertEquals(NostrFeedMapper.MAX_ARTICLES_SHOWN, articles.size)
        assertEquals("reply 14", articles.first().title)
        assertEquals("ordinary 5", articles.last().title)
    }
}
EOF
rm -f /tmp/ad-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRepliesTest" > /tmp/ad-t1.log 2>&1; echo $? > /tmp/ad-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/ad-t1.exit && break; sleep 5; done; cat /tmp/ad-t1.exit; grep -E "^e: " /tmp/ad-t1.log | cut -c1-200 | head -3
```
Expected: `1`, lỗi biên dịch `Unresolved reference` cho `replyTargetUrl` và `REPLY_MARK`.

- [ ] **Step 2: Thêm hằng, `replyTargetUrl`, dòng link (chưa động bộ lọc)**

```bash
cat > /tmp/ad_t1_main.py <<'EOF'
# -*- coding: utf-8 -*-
P = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(P, encoding="utf-8").read()
# 1) constant
a = "    const val MAX_ARTICLES_SHOWN = 20\n"
assert s.count(a) == 1
s = s.replace(a, a + '    /** The sign that starts the last line of a reply: it is a symbol, not a word, so it needs no translation. */\n    const val REPLY_MARK = "↩"\n', 1)
# 2) toArticles: add the reply line to the stored html
b = "                val html = escapeToHtml(event.content)\n                val title = titleOf(html)\n"
assert s.count(b) == 1
s = s.replace(b, "                val html = escapeToHtml(event.content)\n                val title = titleOf(html)\n                val body = html + replyLine(event)\n", 1)
c = "                    rawDescription = html,\n"
assert s.count(c) == 1
s = s.replace(c, "                    rawDescription = body,\n", 1)
# 3) replyTargetUrl + replyLine, before visibleNotes KDoc
d = "    private fun titleOf(html: String): String"
assert s.count(d) == 1
s = s.replace(d, '''    /** The njump address of the note this one answers, built from a checked id (never from the text); null when it names nothing findable. */
    fun replyTargetUrl(event: NostrEvent): String? =
        event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }

    /** The last line of a reply: a break, the mark, and the address as an anchor. Empty for a note that answers nothing findable. */
    private fun replyLine(event: NostrEvent): String {
        val url = escapePlain(replyTargetUrl(event) ?: return "")
        return "<br>" + REPLY_MARK + " <a href=\\"" + url + "\\">" + url + "</a>"
    }

''' + d, 1)
open(P, "w", encoding="utf-8").write(s)
EOF
python3 /tmp/ad_t1_main.py; git diff --stat -- app/src/main | tail -1
```

- [ ] **Step 3: Chạy lớp mới: đỏ đúng lý do (trả lời người lạ còn bị lọc)**

```bash
rm -f /tmp/ad-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRepliesTest" > /tmp/ad-t1.log 2>&1; echo $? > /tmp/ad-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/ad-t1.exit && break; sleep 5; done; cat /tmp/ad-t1.exit; grep -E " FAILED$" /tmp/ad-t1.log | sed -E 's/^.* > //' | sort
```
Expected: `1`; **đỏ đúng năm test** (dự đoán, kiểm ở đây): `aReplyToAStranger_isShown_withALinkToTheNoteItAnswers`, `aReplyWhoseTargetIsBroken_isShown_withoutALink`, `theReplyLine_isAnEscapedAnchorAfterABreak`, `aReply_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink`, `repliesAndOrdinaryNotes_shareTheTwentyNewestFirst` (trả lời người lạ còn bị bộ lọc cũ bỏ: `.single()` không có bài, hoặc ra 10 bài thay vì 20). **Xanh sẵn năm test**: `anOwnReply_isShown_withALinkToItsTarget` (trả lời chính mình vốn được giữ, và đã có dòng link), `theTarget_isTheReplyMarker_thenRoot_thenTheLastOldStyleTag` (chỉ gọi `replyTargetUrl`), `aMentionOnlyNote_hasNoReplyLine`, `anOrdinaryNote_hasNoReplyLine`, `aBlankReply_isStillNotShown` (bài trống bị bỏ cả khi có lọc). Lệch dự đoán: đọc lý do trong `/tmp/ad-t1.log`, sửa dự đoán hoặc test, không bỏ qua.

- [ ] **Step 4: Bỏ bộ lọc chuỗi (xanh cho lớp mới)**

```bash
cat > /tmp/ad_t1_filter.py <<'EOF'
# -*- coding: utf-8 -*-
import re
P = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt"
s = open(P, encoding="utf-8").read()
# visibleNotes: no thread filter any more
old = "        keepOriginalsAndOwnThreads(events).filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }\n"
assert s.count(old) == 1
s = s.replace(old, "        events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }\n", 1)
# drop keepOriginalsAndOwnThreads (KDoc + function)
a = s.index("    /**\n     * The notes the feed shows: every note that is not a reply")
b = s.index("    private fun escapeToHtml")
# keep the escapeToHtml KDoc line that precedes it, if any
pre = s[a:b]
k = pre.rfind("    /** The note text as HTML")
tail = pre[k:] if k != -1 else ""
s = s[:a] + tail + s[b:]
open(P, "w", encoding="utf-8").write(s)
print("keepOriginals left:", s.count("keepOriginalsAndOwnThreads"))
EOF
python3 /tmp/ad_t1_filter.py
rm -f /tmp/ad-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrRepliesTest" > /tmp/ad-t1.log 2>&1; echo $? > /tmp/ad-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/ad-t1.exit && break; sleep 5; done; cat /tmp/ad-t1.exit; grep -E " FAILED$|^e: " /tmp/ad-t1.log | head -5
```
Expected: `0` (10 test xanh). Chưa commit: các test chuỗi cũ còn đỏ, xử lý ở Task 2; **không commit khi bộ test chưa xanh**.

---

### Task 2: Xóa test chuỗi cũ, sửa test mapper/event/relay, xanh, commit

**Files:** Sửa `NostrFeedMapperThreadTest.kt`, `NostrFeedMapperTest.kt`, `NostrEventTest.kt`, `NostrEventThreadTest.kt`, `NostrRelayClientTest.kt`.

- [ ] **Step 1: Chạy script sửa test (lặp lại được: chạy từ bản gốc, có `assert` ở mọi chỗ thay)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > /tmp/ad_t2_tests.py <<'EOF'
# -*- coding: utf-8 -*-
import re
T = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/"

def remove_tests(src, names):
    """Delete each named @Test fun (with its leading comment lines and @Test line) up to the next blank line + @Test/comment/closing brace."""
    for n in names:
        m = re.search(r"(?:^[ \t]*//[^\n]*\n)*(?:^[ \t]*@Test[^\n]*\n)+^[ \t]*(?:override )?fun " + re.escape(n) + r"\(", src, re.M)
        if not m:
            m = re.search(r"(?:^[ \t]*//[^\n]*\n)*^[ \t]*@Test fun " + re.escape(n) + r"\(", src, re.M)
        assert m, "test not found: " + n
        start = m.start()
        # find end: the matching closing brace of the function body
        i = src.index("{", m.end())
        depth = 0
        j = i
        while True:
            c = src[j]
            if c == "{": depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0: break
            j += 1
        end = j + 1
        while src[end:end + 1] == "\n": end += 1
        src = src[:start] + src[end:]
    return src

# --- NostrFeedMapperThreadTest: delete the filter tests
p = T + "NostrFeedMapperThreadTest.kt"; s = open(p, encoding="utf-8").read()
gone = ["aReplyToSomeoneElse_isDropped", "aReplyToADroppedNote_isDropped", "aLoopOfReplies_endsAndDropsBoth", "aNoteReplyingToItself_isDropped",
        "aThreadBehindManyAnswersToOthers_isStillShown", "visibleNotes_dropsRepliesToOthersAndBlankNotes",
        "aReplyWhoseTargetIdIsBroken_isDropped", "aReplyNamingATargetInCapitals_isDropped"]
s = remove_tests(s, gone)
open(p, "w", encoding="utf-8").write(s)
print("ThreadTest left @Test:", s.count("@Test"))

# --- V1: the answer to a stranger is shown now
p = T + "NostrFeedMapperThreadTest.kt"; s = open(p, encoding="utf-8").read()
old = '        assertEquals(listOf("Own thread piece", "Titled two", "Titled one"), articles.map { it.title })\n'
assert s.count(old) == 1
s = s.replace(old, '        assertEquals(listOf("Own thread piece", "Titled two", "Answer to a stranger", "Titled one"), articles.map { it.title })\n', 1)
old = "    /** A mix: two titled notes, a blank one, an answer to a stranger, an own thread piece and another blank one (ids 1..6, oldest first). */\n"
assert s.count(old) == 1
open(p, "w", encoding="utf-8").write(s)

# --- NostrFeedMapperTest: a reply is shown now
p = T + "NostrFeedMapperTest.kt"; s = open(p, encoding="utf-8").read()
old = '''    @Test fun repliesAndBlankNotesAreSkipped() {
        assertEquals(listOf("Hello nostr, first note"), map(NostrTestEvents.PLAIN, NostrTestEvents.REPLY, NostrTestEvents.EMPTY).map { it.title })
    }
'''
assert s.count(old) == 1
s = s.replace(old, '''    @Test fun aBlankNote_isSkipped_butAReplyIsShown() {
        assertEquals(
            listOf("Hello nostr, first note", "This one is a reply"),
            map(NostrTestEvents.PLAIN, NostrTestEvents.REPLY, NostrTestEvents.EMPTY).map { it.title },
        )
    }
''', 1)
open(p, "w", encoding="utf-8").write(s)

# --- NostrEventTest: isReply goes away
p = T + "NostrEventTest.kt"; s = open(p, encoding="utf-8").read()
s = remove_tests(s, ["isReply_isTrueOnlyWithAnETag"])
s = s.replace("    // ---- isReply\n", "", 1)
open(p, "w", encoding="utf-8").write(s)

# --- NostrEventThreadTest: only repliedToId is left to check
p = T + "NostrEventThreadTest.kt"; s = open(p, encoding="utf-8").read()
n0 = s.count("isReply")
s = re.sub(r"^[ \t]*assert(?:True|False)\((?:[^\n]*, )?\w+\.isReply\)\n", "", s, flags=re.M)
print("isReply left in NostrEventThreadTest:", s.count("isReply"), "of", n0)
open(p, "w", encoding="utf-8").write(s)

# --- NostrEventThreadTest: rename the tests whose names said isReply
p = T + "NostrEventThreadTest.kt"; s = open(p, encoding="utf-8").read()
for old, new in [("aNoteWithoutETags_isNotAReply", "aNoteWithoutETags_namesNoTarget"), ("aMentionOnlyNote_isNotAReply", "aMentionOnlyNote_namesNoTarget"),
                 ("aReplyMarker_isAReply_andNamesItsTarget", "aReplyMarker_namesItsTarget"), ("aRootOnlyNote_repliesDirectlyToTheRoot", "aRootOnlyNote_namesTheRoot"),
                 ("oldStyleOneTag_isAReplyToThatNote", "oldStyleOneTag_namesThatNote"), ("aBrokenTargetId_isStillAReply_butNamesNothing", "aBrokenTargetId_namesNothing"),
                 ("anETagWithNoId_isAReplyThatNamesNothing", "anETagWithNoId_namesNothing")]:
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)
open(p, "w", encoding="utf-8").write(s)

# --- NostrRelayClientTest: a reply is a note that can be shown now, so "nothing to show" is a BLANK note (EMPTY) in these tests
p = T + "NostrRelayClientTest.kt"; s = open(p, encoding="utf-8").read()
s = remove_tests(s, ["fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers"])  # same as the blank-note test below once EMPTY is used
def edit_fun(src, name, fn):
    m = re.search(r"@Test fun " + re.escape(name) + r"\(", src)
    assert m, name
    i = src.index("{", m.end()); depth = 0; j = i
    while True:
        if src[j] == "{": depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0: break
        j += 1
    return src[:m.start()] + fn(src[m.start():j + 1]) + src[j + 1:]
for name in ["fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers", "fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay",
             "fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote", "fetch_doesNotMergeTheTwoRelays"]:
    s = edit_fun(s, name, lambda body: body.replace("NostrTestEvents.REPLY", "NostrTestEvents.EMPTY"))
s = s.replace("fetch_returnsEmpty_whenBothRelaysHaveOnlyRepliesToOthers", "fetch_returnsEmpty_whenBothRelaysHaveOnlyABlankNote", 1)
old = "    // ---- FRONT-02z: a relay \"had something\" only when at least one of its notes can be SHOWN (not an answer to a stranger, not blank).\n"
assert s.count(old) == 1
s = s.replace(old, "    // ---- FRONT-02z: a relay \"had something\" only when at least one of its notes can be SHOWN (not blank). Since FRONT-02ad a reply can be shown.\n", 1)
old = "    // The relay that is used is used ALONE: what the first relay had (an answer to a stranger) does not travel along.\n"
assert s.count(old) == 1
s = s.replace(old, "    // The relay that is used is used ALONE: what the first relay had (a blank note) does not travel along.\n", 1)
anchor = "    @Test fun fetch_stopsAtTheFirstRelay_whenItHasAShowableNote()"
assert s.count(anchor) == 1
s = s.replace(anchor, '''    // FRONT-02ad: a reply is shown like any note, so a relay that has only a reply has "something" and the second relay is not asked.
    @Test fun fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val notes = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(1, notes.size)
        assertEquals("the second relay must not be asked", 1, server.requestCount)
    }

''' + anchor, 1)
assert "NostrTestEvents.REPLY" in s
open(p, "w", encoding="utf-8").write(s)
print("REPLY uses left in relay test:", s.count("NostrTestEvents.REPLY"))
EOF
git checkout app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/
python3 /tmp/ad_t2_tests.py
grep -rn "isReply" app/src | cut -c1-140
```
Expected: `ThreadTest left @Test: 10`; `isReply left in NostrEventThreadTest: 0 of 8`; `REPLY uses left in relay test: 2`; `grep isReply` chỉ còn `NostrEvent.kt` (Step 2 xóa).

- [ ] **Step 2: Xóa `isReply` ở `NostrEvent.kt` (không còn nơi dùng)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
P = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrEvent.kt"
s = open(P, encoding="utf-8").read()
a = s.index("    /**\n     * A reply carries an `e` tag that is not just a mention")
b = s.index("    /**\n     * The id of the note this one answers")
s = s[:a] + s[b:]
open(P, "w", encoding="utf-8").write(s)
print("isReply left:", s.count("isReply"))
EOF
grep -rn "isReply" app/src | wc -l
```
Expected: `isReply left: 0`, rồi `0` dòng `grep` (cả `src/main` lẫn `src/test`).

- [ ] **Step 3: Cập nhật KDoc cho khớp luật mới**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/"
p = J + "NostrFeedMapper.kt"; s = open(p, encoding="utf-8").read()
old = "    /** The most articles one Nostr source shows. The cut comes AFTER replies to other people are filtered out, never before. */\n"
assert s.count(old) == 1
s = s.replace(old, "    /** The most articles one Nostr source shows: the newest, replies and ordinary notes alike (see [visibleNotes]). */\n", 1)
a = s.index("    /**\n     * The notes that become articles, in the order they came:")
b = s.index("    fun visibleNotes")
new_doc = (
    "    /**\n"
    "     * The notes that become articles, in the order they came, each once: a valid id to link to and a title that is not empty (a note of\n"
    "     * nothing but spaces has none). A reply is a note like any other (it only gets a last line that links to the note it answers). This is\n"
    "     * the ONE rule for \"which notes are shown\": [toArticles] cuts to [MAX_ARTICLES_SHOWN] after it, and the relay client asks it before\n"
    "     * deciding that a relay had something to show, so a blank note never stops the search at the first relay.\n"
    "     */\n"
)
s = s[:a] + new_doc + s[b:]
open(p, "w", encoding="utf-8").write(s)
p = J + "NostrFetchResult.kt"; s = open(p, encoding="utf-8").read()
old = "The list is NOT cut to the feed size: replies to other people are\n * still in it, and the mapper cuts to [NostrFeedMapper.MAX_ARTICLES_SHOWN] only after filtering them out."
assert s.count(old) == 1
s = s.replace(old, "The list is NOT cut to the feed size: the mapper cuts to\n * [NostrFeedMapper.MAX_ARTICLES_SHOWN] after it has dropped the notes that cannot be shown.", 1)
open(p, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app/src/main | tail -4
```

- [ ] **Step 4: Chạy các lớp Nostr + sync + SubscribeViewModelNostr* (xanh)**

```bash
rm -f /tmp/ad-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" --tests "com.mckimquyen.reader.domain.sv.LocalRssSvNostrSyncTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/ad-t2.log 2>&1; echo $? > /tmp/ad-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/ad-t2.exit && break; sleep 5; done; cat /tmp/ad-t2.exit; grep -E " FAILED$|^e: " /tmp/ad-t2.log | head -6
```
Expected: `0`. Đỏ: đọc lý do, sửa test hoặc mã (không bỏ qua; không đổi `fetch`).

- [ ] **Step 5: Commit**

```bash
git add -A app/src docs/superpowers/plans/2026-11-19-front-02ad-nostr-show-all-replies.md
git commit -m "feat(front-02ad): every Nostr reply is shown, with a last line that links to the note it answers

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline -1; git status --short
```

---

### Task 3: Mutation JVM (9 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/ad_mut.sh`, `/tmp/ad_batch.sh`, `/tmp/ad_check.py`. Đột biến tạm trong `NostrFeedMapper.kt` (luôn khôi phục).

- [ ] **Step 1: Kiểm mẫu duy nhất (mỗi mẫu đúng 1 lần), trên mã sau Task 2**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
cat > /tmp/ad_pat.py <<'EOF'
# -*- coding: utf-8 -*-
mp = open("app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt", encoding="utf-8").read()
pats = {
 "m1": "        events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null && titleOf(escapeToHtml(it.content)).isNotEmpty() }\n",
 "m2": "val body = html + replyLine(event)",
 "m3": "event.repliedToId?.let { NostrKeys.noteId(it) }",
 "m4/m7/m8": 'return "<br>" + REPLY_MARK + " <a href',
 "m6": "events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null &&",
 "m5": "event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }",
 "m9": " && titleOf(escapeToHtml(it.content)).isNotEmpty() }",
}
print({k: mp.count(v) for k, v in pats.items()})
EOF
python3 /tmp/ad_pat.py
```
Expected: mọi nhãn `1`. Khác 1: đọc tệp, sửa mẫu ở `ad_batch.sh`.

- [ ] **Step 2: Dựng script (đột biến m1 dùng `repliedToId == null` để mô phỏng bộ lọc cũ)**

```bash
sed -e 's#/tmp/z-#/tmp/ad-#g' /tmp/z_mut.sh > /tmp/ad_mut.sh; chmod +x /tmp/ad_mut.sh
cat > /tmp/ad_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
MP=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrFeedMapper.kt
cp -p $MP /tmp/ad-orig-mp.kt
M=/tmp/ad_mut.sh
zsh $M base
zsh $M m1 $MP 'events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null &&' 'events.distinctBy { it.id }.filter { it.repliedToId == null && NostrKeys.noteId(it.id) != null &&'
zsh $M m2 $MP 'val body = html + replyLine(event)' 'val body = html'
zsh $M m3 $MP 'event.repliedToId?.let { NostrKeys.noteId(it) }' 'NostrKeys.noteId(event.id).takeIf { event.repliedToId != null }'
zsh $M m4 $MP 'return "<br>" + REPLY_MARK + " <a href' 'return "<br>" + " <a href'
zsh $M m5 $MP 'event.repliedToId?.let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }' '(event.repliedToId ?: event.id).let { NostrKeys.noteId(it) }?.let { NJUMP_BASE + it }'
zsh $M m6 $MP 'events.distinctBy { it.id }.filter { NostrKeys.noteId(it.id) != null &&' 'events.distinctBy { it.id }.filter { (it.repliedToId != null || it.tags.none { t -> t.firstOrNull() == "e" }) && NostrKeys.noteId(it.id) != null &&'
zsh $M m7 $MP 'return "<br>" + REPLY_MARK + " <a href' 'return REPLY_MARK + " <a href'
zsh $M m8 $MP 'return "<br>" + REPLY_MARK + " <a href' 'return "<br>" + REPLY_MARK + "<a href'
zsh $M m9 $MP ' && titleOf(escapeToHtml(it.content)).isNotEmpty() }' ' }'
zsh $M base
cmp -s /tmp/ad-orig-mp.kt $MP && echo "final mp: identical" || echo "final mp: DIFFERENT"
echo "uncommitted production diff: $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
cat > /tmp/ad_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re
R = {"R1": "aReplyToAStranger_isShown_withALinkToTheNoteItAnswers", "R2": "anOwnReply_isShown_withALinkToItsTarget",
     "R3": "theTarget_isTheReplyMarker_thenRoot_thenTheLastOldStyleTag", "R4": "aReplyWhoseTargetIsBroken_isShown_withoutALink",
     "R5": "aMentionOnlyNote_hasNoReplyLine", "R6": "anOrdinaryNote_hasNoReplyLine", "R7": "theReplyLine_isAnEscapedAnchorAfterABreak",
     "R8": "aReply_doesNotChangeTheTitleTheShortDescriptionOrTheNoteLink", "R9": "aBlankReply_isStillNotShown",
     "R10": "repliesAndOrdinaryNotes_shareTheTwentyNewestFirst"}
M1 = "aBlankNote_isSkipped_butAReplyIsShown"
V1 = "visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles"
STOP = "fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply"
MENTION = "aMentionOnlyNote_isKeptLikeAnyNote"
SYNC = "anOwnThread_andAnAnswerToAStranger_areSynced"
def S(*ks): return {R.get(k, k) for k in ks}
# exact: the failing set must be equal. atleast: it must contain these (the old chain tests and relay tests fail too, they are not all listed).
EXPECTED = {
 "m1": ("atleast", S("R1", "R2", "R7", "R8", "R10", M1, V1, STOP)),   # replies filtered out again
 "m2": ("exact", S("R1", "R2", "R7", SYNC)),                                 # no reply line at all
 "m3": ("exact", S("R1", "R2", "R3", "R7")),                           # link built from the reply's own id
 "m4": ("exact", S("R1", "R7", SYNC)),                                       # the mark is gone
 "m5": ("atleast", S("R2", "R4", "R5", "R6")),                         # every note gets a line (also old rawDescription tests fail)
 "m6": ("exact", S("R4", "R5", MENTION)),                              # a note with an e tag but no findable target is dropped
 "m7": ("exact", S("R1", "R7", SYNC)),                                       # no break before the mark
 "m8": ("exact", S("R1", "R7", SYNC)),                                       # no space after the mark
 "m9": ("atleast", S("R9", M1, V1)),                                   # blank notes are shown again
}
bad = 0
for label, (kind, want) in EXPECTED.items():
    try: text = open(f"/tmp/ad-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError: print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M): print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = (got == want) if kind == "exact" else want <= got
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, kind, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/ad-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN"); bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
grep -c "ad-" /tmp/ad_mut.sh
```

- [ ] **Step 3: Chạy batch nền, chờ ALLDONE**

```bash
rm -f /tmp/ad-batch.log; ( zsh /tmp/ad_batch.sh > /tmp/ad-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/ad-batch.log 2>/dev/null && break; sleep 5; done; grep -c ALLDONE /tmp/ad-batch.log; cut -c1-160 /tmp/ad-batch.log | tail -3
```
Chưa có `ALLDONE`: chạy lại **chỉ** vòng chờ. Không sửa `NostrFeedMapper.kt` cho tới `ALLDONE`.

- [ ] **Step 4: Đối chiếu tự động**

```bash
python3 /tmp/ad_check.py
```
Expected: `ALL-MATCH`; cuối batch: `final mp: identical`, `uncommitted production diff: 0 lines`. Lệch: đọc lý do (tập đỏ + thông báo trong `/tmp/ad-mut-<nhãn>.log`): (a) dự đoán sai thì sửa bảng ở đầu plan và `ad_check.py`, ghi báo cáo; (b) đột biến sống sót thì thêm khẳng định rồi chạy lại; (c) đột biến tương đương thì đổi thành đột biến thật. Bảng đầu plan chỉ là bản nháp: **bảng đúng là `EXPECTED` trong `ad_check.py`** sau khi suy luận từng test (Step 4 sửa bảng đầu plan cho khớp).

---

### Task 4: JVM đầy đủ, phạm vi, tài liệu, commit

**Files:** Sửa `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02x_nostr-own-threads_DONE.md`, `FRONT-02y_…_DONE.md`, `FRONT-02z_…_DONE.md` (ghi chú "đã thay bằng 02ad"), spec (mục "Điều chỉnh khi thực thi"); tạo `doc/task/done/FRONT-02ad_nostr-show-all-replies_DONE.md`.

- [ ] **Step 1: JVM full (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/ad-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/ad-jvm.log 2>&1; echo $? > /tmp/ad-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/ad-jvm.exit && break; sleep 5; done; cat /tmp/ad-jvm.exit 2>/dev/null || echo "still running"
```
Rồi: `grep -E "FAILED|tests completed" /tmp/ad-jvm.log | head -5; tail -2 /tmp/ad-jvm.log` và đếm test bằng đoạn python đọc `app/build/test-results/testDevDebugUnitTest/*.xml` (như FRONT-02z). Số trước **3.885** (chỉ để đối chiếu). **Kỳ vọng 3.885 + 10 (`NostrRepliesTest`) + 1 (`fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply`) − 10 (xóa: 8 test chuỗi của `NostrFeedMapperThreadTest`, `isReply_isTrueOnlyWithAnETag`, và `fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers` vì trùng với test bài trống) = 3.886**; ghi **số đo được** và đối chiếu từng test xóa. Đỏ không liên quan: chạy riêng lớp đó 3 lần và chạy lại cả bộ, ghi thẳng.

- [ ] **Step 2: Phạm vi**

```bash
git diff --name-only 3461d93f HEAD -- app
git status --short
```
Expected: `app/src/main` chỉ `NostrFeedMapper.kt`, `NostrEvent.kt`, `NostrFetchResult.kt` (KDoc); **không** `NostrRelayClient.kt`, `LocalRssSv.kt`, `SubscribeViewModel.kt`, `app/build.gradle`, `app/schemas`, manifest, `res`; test: `NostrRepliesTest.kt` (mới) và năm tệp test đã sửa.

- [ ] **Step 3: Báo cáo, `feature.md`, task gốc, báo cáo cũ, spec**

Báo cáo `FRONT-02ad_nostr-show-all-replies_DONE.md` (tiếng Việt đơn giản): việc đã làm (luật mới, dòng `↩ link`); bảng 9 đột biến với tập đỏ **đo được** (ghi mọi dự đoán sai đã sửa); **danh sách test đã xóa và lý do** (từng tên); JVM đo lại; **nói thẳng:** nguồn đã đăng ký nhận thêm bài trả lời chưa đọc ở lần đồng bộ sau; tác giả hay trả lời thì 20 bài có thể toàn trả lời (bài thường bị đẩy ra); mất phòng thủ vòng lặp của 02x (không còn đi theo chuỗi); link bài gốc có thể trỏ tới bài người lạ đã xóa; app không tải gì khi mở bài; link do app tự dựng từ id đã kiểm; chưa kiểm máy thật hay relay thật. `doc/feature.md`: ghi chú ở các dòng FRONT-02x, 02y, 02z ("trả lời người khác vẫn bị bỏ" nay thay bằng FRONT-02ad) và thêm dòng FRONT-02ad sau dòng FRONT-02ac. Task gốc FRONT-02 (giữ trong `todo/`): mục "Cập nhật" ghi FRONT-02ad; bỏ "trả lời bài người khác" khỏi phần còn lại. Spec: nối "Điều chỉnh khi thực thi".

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02ad): report that every Nostr reply is shown with a link to the note it answers

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review
- **Spec coverage:** hiện mọi trả lời, dòng `↩ link njump` từ id đã kiểm, không chữ mới → Task 1; xóa bộ lọc chuỗi, xóa `isReply`, sửa test relay 02z, KDoc → Task 2; mutation 9 đột biến → Task 3; JVM đo lại, phạm vi, báo cáo nói thẳng các thay đổi hành vi → Task 4; `fetch` và REQ không đổi → Global Constraints, Task 4 Step 2.
- **Placeholder scan:** không có.
- **Type consistency:** `replyTargetUrl(NostrEvent): String?`, `REPLY_MARK`, `replyLine`, `escapePlain`, `visibleNotes` dùng đồng nhất; tên R1 đến R10, M1, V1 khớp `ad_check.py`.
