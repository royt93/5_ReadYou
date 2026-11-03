# Galaxy Title-Word Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nối thêm các bài chung ít nhất 2 từ hiếm trong tiêu đề trong chế độ Ngân Hà, và cho thẻ sao nói rõ vì sao hai bài được nối.

**Architecture:** `TitleWords` (thuần) tách từ tiêu đề; `GalaxyGraphBuilder` thêm đường `TITLE_WORDS` bên cạnh đường `CONCEPT` bằng chỉ mục ngược và điểm `sum(1/df)`; `GalaxyNeighbours.of` (thuần) lấy láng giềng của một sao; `GalaxyStarSheet` hiện tối đa 3 hàng "Chung từ" và chạm một hàng chọn sao kia. Không đổi `GalaxyLayout`, Room, hay cách vẽ đường.

**Tech Stack:** Kotlin, Jetpack Compose Material3 1.0.1, JUnit4, Robolectric, MockK, Hilt (không đổi).

## Global Constraints

- Chỉ nhánh `dev`; không worktree; không commit `.idea/*`; không push; không nhắc hay gợi ý PR.
- Không đổi Room (vẫn v20), không thêm dependency, không AI/mạng/API key/quảng cáo/IAP, không đổi `GalaxyLayout`, không đổi kiểu nét vẽ.
- Hằng đúng tên và giá trị theo spec: `MIN_WORD_CHARS = 4`, `MAX_WORD_DOC_FREQ = 8`, `MIN_SHARED_TERMS = 2`, `MAX_NEIGHBORS_PER_STAR = 3`, `MAX_LINES = 400`, `MAX_STARS = 600`. Điểm cặp = tổng `1 / df` của các từ chung; xếp giảm dần rồi theo `a`, `b`.
- Từ: chữ thường theo `Locale.ROOT`, chỉ chữ/số Unicode, độ dài >= 4, bỏ stop-word của app, bỏ token toàn chữ số. `df` tính trên tập sao đã lọc theo ngày.
- Một cặp chỉ có **một** đường. Cặp đã nối theo khái niệm mà cũng chung >= 2 từ hiếm giữ `kind = CONCEPT` nhưng vẫn điền `sharedTerms`. Đường khái niệm không tính vào giới hạn láng giềng của đường từ-tiêu-đề. Tổng đường <= `MAX_LINES`.
- `GalaxyLine` thêm hai trường **có giá trị mặc định**: `kind: GalaxyLineKind = GalaxyLineKind.CONCEPT`, `sharedTerms: List<String> = emptyList()`. Mọi nơi đang gọi `GalaxyLine(a, b, n)` ba tham số vẫn biên dịch và vẫn bằng nhau.
- Chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh): `galaxy_related_title` ("Related articles") và `galaxy_shared_terms` ("Shared words: %1$s"). Không dạng số đi liền từ (lint `PluralsCandidate`); mọi chuỗi phải được dùng (lint `UnusedResources`).
- Giữ nút Back ở action bar, inset edge-to-edge và Material You của `SecondBrainPage`; thẻ sao đã cuộn được.
- Thiết bị: **Tecno KJ7 `115333744A005844` duy nhất** (owner: "tiếp tục với tecno đi"). Không đụng A50s `R58MA6WYRPE` (đang offline) hay Pixel `2B051FDH3006MU`. Không `connectedDevDebugAndroidTest`; dùng `adb -s 115333744A005844 shell am instrument -w -e class <Class> com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
- Dữ liệu smoke: Room của Tecno chỉ có 9 bài đã đọc nên smoke dùng **bản sao** DB với 152 bài đánh dấu đã đọc. **Sao lưu mới trước** (dữ liệu Tecno có thể đã đổi), khôi phục từ bản sao lưu đó bằng `run-as sh -c 'cat > PATH'`, `cmp` cả 4 file (`Reader`, `Reader-wal`, `Reader-shm`, `settings.preferences_pb`), không bao giờ xoá DataStore.
- Quảng cáo toàn màn hình: chỉ đóng bằng nút có nhãn ("Tiếp tục đến ứng dụng" hoặc "Continue to app") từ **dump mới**, thử lại khoảng một phút, không bấm creative, kiểm foreground trước mỗi lần chạm.
- Mỗi test hồi quy phải **mutation-check**: tên đột biến chính xác và test phải bắt nó đều ghi trong từng task. Nhiều đột biến trước đây không bị bắt ngay lần đầu, nên nếu đột biến sống sót thì thêm test rồi chạy lại tới khi đỏ. Đọc lỗi thật từ `app/build/test-results/testDevDebugUnitTest/*.xml`. `git status --short | grep -v .idea` phải sạch trước mỗi commit; trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Viết file lớn thành nhiều khối nhỏ (một lượt trước đã chạm giới hạn đầu ra); script ghi ra file, đọc kết quả bằng Read; dùng `printf`, không `echo =====`.

---

## Công cụ phiên làm việc (tạo trước, vì các script cũ trong `/tmp` đã bị dọn)

Các script `exc13-run.sh`, `exc13-mutate.sh`, `exc13-extract.py` và script chuỗi **không còn**. Task 0 tạo lại ba script thật cần dùng; Task 00 (đo trước) chỉ cần `exc13-tecno-lib.sh` và chạy đầu tiên. Các helper Tecno còn sống: `/tmp/exc13-tecno-lib.sh` (có `S`, `P`, `top`, `guard`, `awake`, `dump`, `center_of`, `tap`), `/tmp/exc13-tecno-ad.sh`, `/tmp/exc13-tecno-open.sh`.

## File map

Tạo (main, `app/src/main/java/com/mckimquyen/reader/`): `domain/model/galaxy/TitleWords.kt`, `domain/model/galaxy/GalaxyNeighbours.kt`.
Sửa: `domain/model/galaxy/GalaxyGraph.kt` (thêm `GalaxyLineKind`, hai trường mặc định), `domain/model/galaxy/GalaxyGraphBuilder.kt`, `infrastructure/ai/search/SemanticSearchEngine.kt` (mở `STOP_WORDS`), `ui/page/secondbrain/galaxy/GalaxyStarSheet.kt`, `ui/page/secondbrain/galaxy/GalaxyPanel.kt`, `app/src/main/res/values*/strings.xml`.
Test (`app/src/test/java/com/mckimquyen/reader/`): `domain/model/galaxy/TitleWordsTest.kt`, `domain/model/galaxy/GalaxyGraphBuilderTest.kt` (mở rộng), `domain/model/galaxy/GalaxyNeighboursTest.kt`, `infrastructure/ai/search/StopWordsTest.kt`, `ui/page/secondbrain/GalaxyPageTest.kt` (mở rộng); instrumentation `app/src/androidTest/.../ui/page/secondbrain/KnowledgeGalaxyDeviceTest.kt` (mở rộng).
Hoàn tất: `doc/task/done/FRONT-03b_galaxy-title-word-links_DONE.md`, `doc/feature.md`, `doc/task/done/FRONT-03_3d-knowledge-galaxy_DONE.md` (mục 2 của "Giới hạn đã biết").

## Interfaces (tên và kiểu dùng xuyên suốt)

```kotlin
enum class GalaxyLineKind { CONCEPT, TITLE_WORDS }
data class GalaxyLine(val a: String, val b: String, val sharedConcepts: Int,
    val kind: GalaxyLineKind = GalaxyLineKind.CONCEPT, val sharedTerms: List<String> = emptyList())
object TitleWords { const val MIN_WORD_CHARS = 4; fun extract(title: String): Set<String> }
data class GalaxyNeighbour(val star: GalaxyStar, val sharedTerms: List<String>, val kind: GalaxyLineKind)
object GalaxyNeighbours { fun of(graph: GalaxyGraph, starId: String, limit: Int = 3): List<GalaxyNeighbour> }
// SemanticSearchEngine: companion `internal val STOP_WORDS: Set<String>` (was private)
// GalaxyStarSheet(star, neighbours: List<GalaxyNeighbour> = emptyList(), onOpen, onSelectNeighbour: (String) -> Unit = {}, modifier)
```

---

### Task 00: Đo khung hình TRƯỚC khi sửa mã (làm đầu tiên, không commit)

Phải đo bản **chưa có** đường từ-tiêu-đề, nên bước này đứng trước Task 1. Dùng bản app hiện tại trên `dev`.

- [ ] **Step 1: Sao lưu MỚI và nạp bản sao 152 bài đã đọc** (đúng Task 6 Step 4 và 5, dùng `/tmp/gtw-tecno-backup` và `/tmp/gtw-seed`); `awake` phải đạt, nếu màn hình khoá thì dừng và nhờ owner mở khoá.
- [ ] **Step 2: Script `/tmp/gtw-frames.sh <label>`** (chỉ Tecno): `source /tmp/exc13-tecno-lib.sh`; `awake`, `guard`; `adb -s $S shell dumpsys gfxinfo $P reset`; lặp 8 lần `adb -s $S shell input swipe 300 1200 800 1000 400` rồi `adb -s $S shell input swipe 800 900 300 1400 400` (kiểm `guard` giữa các lần); rồi `adb -s $S shell dumpsys gfxinfo $P > /tmp/gtw-gfx-$1-$RUN.txt` và in `Total frames rendered`, `Janky frames:`, `50th/90th/95th/99th percentile`. Cũng in dòng đếm sao/đường lấy từ `dump` (mô tả `Knowledge galaxy. Stars: N, links: M`).
- [ ] **Step 3: Mở Ngân Hà** bằng Settings, Second Brain, Galaxy (mọi chạm: `dump` mới, `center_of`, `tap` có `guard`; quảng cáo đóng bằng `/tmp/exc13-tecno-ad.sh` chỉ qua nút có nhãn).
- [ ] **Step 4: Chạy `/tmp/gtw-frames.sh before` ba lượt**, chép số vào `/tmp/gtw-before.txt`; ghi `Links: M` (mong đợi 5). **Giữ ứng dụng ở trạng thái này, chưa sửa mã.**
- [ ] **Step 5: Khôi phục dữ liệu** như Task 6 Step 9 và `cmp` cả 4 file, rồi mới bắt đầu Task 1.

---

### Task 0: Tạo lại ba script dùng cho cả plan (không commit)

Các script nằm trong `/tmp`, không thuộc repo.

- [ ] **Step 1: `/tmp/gtw-run.sh`** chạy test Gradle và in số liệu từng lớp từ XML.

```zsh
#!/bin/zsh
setopt nullglob
# usage: gtw-run.sh "<gradle --tests args>" [label]
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
ARGS=$1; LABEL=${2:-run}
rm -f app/build/test-results/testDevDebugUnitTest/*.xml
./gradlew testDevDebugUnitTest ${=ARGS} -q > /tmp/gtw-$LABEL.log 2>&1
printf 'gradle exit %s\n' $?
python3 - <<'PY'
import glob,re
shown=0
for p in sorted(glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml')):
    t=open(p,encoding='utf-8').read()
    m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',t[:3000])
    if not m: continue
    shown+=1
    print(p.split('TEST-')[-1].replace('.xml','').split('.')[-1],'tests',m[1],'skip',m[2],'fail',m[3],'err',m[4])
    for f in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error) message="([^"]{0,240})',t):
        print('   FAIL',f.group(1),'|',f.group(2).replace('&#10;',' ')[:200])
if not shown: print('NO XML')
PY
grep -m4 "^e: " /tmp/gtw-$LABEL.log | sed 's#file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/##' | cut -c1-230
```

- [ ] **Step 2: `/tmp/gtw-mutate.sh`** áp **một** đột biến, chạy, và **luôn khôi phục**.

```zsh
#!/bin/zsh
setopt nullglob
# usage: gtw-mutate.sh <file> <python-regex-old> <new text> "<gradle --tests args>" <label>
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
FILE=$1; OLD=$2; NEW=$3; ARGS=$4; LABEL=$5
cp "$FILE" /tmp/gtw-mut-backup
python3 - "$FILE" "$OLD" "$NEW" <<'PY'
import sys,re
path,old,new=sys.argv[1:4]
t=open(path,encoding='utf-8').read()
n=len(re.findall(old,t,re.S))
if n!=1:
    print('MUTATION_PATTERN_MATCHES',n); sys.exit(3)
open(path,'w',encoding='utf-8').write(re.sub(old,lambda m:new,t,count=1,flags=re.S))
print('mutation applied')
PY
RC=$?
[ $RC -eq 0 ] && /tmp/gtw-run.sh "$ARGS" "mut-$LABEL"
cp /tmp/gtw-mut-backup "$FILE"
printf 'restored; differs from backup: '; diff -q /tmp/gtw-mut-backup "$FILE" >/dev/null && echo no || echo YES
```

- [ ] **Step 3: `/tmp/gtw-extract.py`** lấy khối ```kotlin theo chỉ số từ plan.

```python
import re, sys
PLAN='/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/docs/superpowers/plans/2026-11-03-galaxy-title-word-links.md'
blocks=re.findall(r'```kotlin\n(.*?)```', open(PLAN,encoding='utf-8').read(), re.S)
if len(sys.argv)==1:
    for i,b in enumerate(blocks):
        m=re.search(r'^(?:class|object|data class|enum class) (\w+)', b, re.M)
        print(i, m.group(1) if m else b.splitlines()[0][:60])
else:
    open(sys.argv[2],'w',encoding='utf-8').write(blocks[int(sys.argv[1])]); print('wrote',sys.argv[2])
```

- [ ] **Step 4: Chạy `chmod +x /tmp/gtw-run.sh /tmp/gtw-mutate.sh`** rồi `python3 /tmp/gtw-extract.py` để liệt kê khối (sau khi plan đã có đủ). Không có gì để commit ở task này.

---

### Task 1: `TitleWords` và mở `STOP_WORDS`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt` (dòng ~79: `private val STOP_WORDS` thành `internal val STOP_WORDS`)
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/TitleWords.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/TitleWordsTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/search/StopWordsTest.kt`

**Interfaces:**
- Consumes: không.
- Produces: `TitleWords.MIN_WORD_CHARS = 4`, `TitleWords.extract(title: String): Set<String>`; `SemanticSearchEngine.STOP_WORDS` (internal, trong companion).

Ghi chú thiết kế: `domain` không được phụ thuộc `infrastructure`, nên `TitleWords` **không** import `SemanticSearchEngine`. Nó giữ **bộ stop-word riêng nhỏ** cho tiêu đề (Task 1 Step 3) và `StopWordsTest` chứng minh danh sách đó **bao gồm** `SemanticSearchEngine.STOP_WORDS` để hai danh sách không lệch nhau. Vì vậy `STOP_WORDS` chỉ cần `internal` cho test.

- [ ] **Step 1: Test đỏ cho `TitleWords`.**

```kotlin
package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleWordsTest {
    @Test fun keepsWordsOfAtLeastFourLettersLowercased() {
        assertEquals(setOf("trump", "zelensky", "putin"), TitleWords.extract("Trump, Zelensky & Putin"))
    }

    @Test fun dropsWordsShorterThanTheMinimum() {
        val words = TitleWords.extract("The EU and UK tax war")
        assertFalse("eu" in words)
        assertFalse("uk" in words)
        assertFalse("tax" in words)
        assertEquals(TitleWords.MIN_WORD_CHARS, 4)
        assertTrue("exactly four letters is kept", "gold" in TitleWords.extract("Gold rush"))
        assertFalse("three letters is dropped", "war" in TitleWords.extract("war"))
    }

    @Test fun dropsStopWordsEvenWhenLongEnough() {
        val words = TitleWords.extract("About this with from their there where")
        assertTrue("stop words must not survive: $words", words.isEmpty())
    }

    @Test fun dropsDigitOnlyTokens() {
        val words = TitleWords.extract("Fined 45000 over 2026 rules")
        assertFalse("45000" in words)
        assertFalse("2026" in words)
        assertTrue("fined" in words)
        assertTrue("rules" in words)
    }

    @Test fun keepsAlphanumericWordsThatAreNotPureDigits() {
        assertTrue("covid19" in TitleWords.extract("Covid19 inquiry"))
    }

    @Test fun splitsOnPunctuationAndApostrophes() {
        val words = TitleWords.extract("Trump's 'energy ceasefire' claim — welcomed")
        assertTrue("energy" in words)
        assertTrue("ceasefire" in words)
        assertTrue("welcomed" in words)
        assertFalse(words.any { it.contains("'") || it.contains("—") })
    }

    @Test fun handlesVietnameseAndOtherUnicodeLetters() {
        val words = TitleWords.extract("Chính phủ công bố gói hỗ trợ mới")
        assertTrue("chính" in words)
        assertTrue("phủ".length < TitleWords.MIN_WORD_CHARS && "phủ" !in words)
        assertTrue("công" !in words || "công".length >= TitleWords.MIN_WORD_CHARS)
        assertTrue("hỗ".length < TitleWords.MIN_WORD_CHARS)
    }

    @Test fun emptyBlankAndSymbolOnlyTitlesGiveNoWords() {
        assertTrue(TitleWords.extract("").isEmpty())
        assertTrue(TitleWords.extract("   ").isEmpty())
        assertTrue(TitleWords.extract("!!! --- ???").isEmpty())
    }

    @Test fun isCaseInsensitiveAndDeduplicated() {
        assertEquals(setOf("burnham"), TitleWords.extract("BURNHAM burnham Burnham"))
    }
}
```

- [ ] **Step 2: Test đỏ cho danh sách stop-word chung.**

```kotlin
package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.galaxy.TitleWords
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWordsTest {
    @Test fun everySearchEngineStopWordIsAlsoIgnoredInGalaxyTitles() {
        // The two lists live in different layers; this keeps the galaxy from linking on a word the app already
        // treats as meaningless elsewhere.
        val leaked = SemanticSearchEngine.STOP_WORDS.filter { word ->
            word.length >= TitleWords.MIN_WORD_CHARS && word in TitleWords.extract("$word zzzz")
        }
        assertTrue("stop words that still reach a title: $leaked", leaked.isEmpty())
    }

    @Test fun theSharedListIsNotEmpty() {
        assertTrue(SemanticSearchEngine.STOP_WORDS.size >= 40)
    }
}
```

- [ ] **Step 3: Chạy đỏ.** `/tmp/gtw-run.sh '--tests *TitleWordsTest --tests *StopWordsTest' t1red`. Mong đợi lỗi biên dịch `Unresolved reference 'TitleWords'` và `STOP_WORDS` (đang `private`).
- [ ] **Step 4: Cài đặt.** Đổi `private val STOP_WORDS` thành `internal val STOP_WORDS` trong companion của `SemanticSearchEngine`. Tạo `TitleWords`:

```kotlin
package com.mckimquyen.reader.domain.model.galaxy

import java.util.Locale

/** Meaningful words of a headline, used to relate two articles in the galaxy. Pure and deterministic. */
object TitleWords {
    const val MIN_WORD_CHARS = 4

    private val nonWord = Regex("[^\\p{L}\\p{Nd}]+")

    /** Mirrors the search engine's list plus common English function words that are 4+ letters long. */
    private val stopWords = setOf(
        "about", "after", "also", "been", "before", "from", "have", "into", "just", "like", "more", "most", "over",
        "says", "said", "than", "that", "their", "them", "then", "there", "these", "they", "this", "those", "through",
        "what", "when", "where", "which", "while", "will", "with", "would", "your",
        "watch", "video", "live", "news", "update", "updates",
        "như", "những", "được", "trong", "người", "cũng", "nhưng", "khi",
    )

    fun extract(title: String): Set<String> =
        title.lowercase(Locale.ROOT)
            .split(nonWord)
            .filter { it.length >= MIN_WORD_CHARS && it !in stopWords && !it.all(Char::isDigit) }
            .toSet()
}
```

Quyết định: các từ "says", "said", "watch", "video", "live", "news", "update(s)" là từ tiêu đề báo chí lặp lại ở mọi bài (đo thật: "says" 15 bài, "watch" 10 bài), nên bỏ chúng bằng danh sách rõ ràng thay vì chỉ dựa vào ngưỡng `df`. Ngưỡng `df` vẫn là lớp bảo vệ thứ hai.

- [ ] **Step 5: Chạy xanh.** Cùng lệnh Step 3; đọc XML: `TitleWordsTest` 9, `StopWordsTest` 2, 0 lỗi. Nếu `StopWordsTest.everySearchEngineStopWordIsAlsoIgnoredInGalaxyTitles` đỏ vì một stop-word của engine có độ dài >= 4 mà `stopWords` chưa chứa, **thêm từ đó vào `TitleWords.stopWords`** (đó là mục đích của test).
- [ ] **Step 6: Mutation-check (từng cái, khôi phục sau mỗi lần).**
  - (a) Đổi `it.length >= MIN_WORD_CHARS` thành `it.length >= 2`: `dropsWordsShorterThanTheMinimum` phải đỏ.
  - (b) Bỏ `!it.all(Char::isDigit)`: `dropsDigitOnlyTokens` phải đỏ.
  - (c) Bỏ `it !in stopWords`: `dropsStopWordsEvenWhenLongEnough` phải đỏ.
  - (d) Đổi `lowercase(Locale.ROOT)` thành giữ nguyên: `isCaseInsensitiveAndDeduplicated` phải đỏ.
  - Lệnh mẫu: `/tmp/gtw-mutate.sh app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/TitleWords.kt 'it\.length >= MIN_WORD_CHARS' 'it.length >= 2' '--tests *TitleWordsTest' minlen`.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/TitleWords.kt app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/TitleWordsTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/ai/search/StopWordsTest.kt && git commit -m "feat(front-03b): extract meaningful title words"` (+ trailer).

---

### Task 2: `GalaxyLine` mở rộng và đường từ-tiêu-đề trong `GalaxyGraphBuilder`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyGraph.kt`, `GalaxyGraphBuilder.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyTitleLinksTest.kt` (tệp mới, để không làm phình `GalaxyGraphBuilderTest`)

**Interfaces:**
- Consumes: `TitleWords.extract` (Task 1).
- Produces: `GalaxyLineKind`, `GalaxyLine(a, b, sharedConcepts, kind, sharedTerms)`, `GalaxyGraphBuilder.MAX_WORD_DOC_FREQ = 8`, `GalaxyGraphBuilder.MIN_SHARED_TERMS = 2`; `build(...)` trả thêm đường `TITLE_WORDS`.

Tương thích ngược (đã kiểm với code hiện tại): `GalaxyLine("a","b",2)` ở `GalaxyGraphBuilderTest` (2 chỗ), `GalaxyPageTest`, `GalaxyViewModelTest` và `KnowledgeGalaxyDeviceTest` đều gọi 3 tham số vị trí; hai trường mới có mặc định nên **không phải sửa test cũ**. Các test cũ dùng tiêu đề `"Title $id"` mà `"title"` có `df` bằng số sao, vượt `MAX_WORD_DOC_FREQ` khi có hơn 8 sao, và dưới đó chỉ 1 từ chung nên không đủ `MIN_SHARED_TERMS`: không sinh đường từ-tiêu-đề, nên mọi assert bằng `assertEquals` của test cũ giữ nguyên.

- [ ] **Step 1: Test đỏ.** Tệp `GalaxyTitleLinksTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyTitleLinksTest {
    private fun star(id: String, title: String, date: Long = 1L, vararg concepts: String) =
        GalaxyStar(id, title, "BBC", date, concepts.toSet())

    private fun titleLines(graph: GalaxyGraph) = graph.lines.filter { it.kind == GalaxyLineKind.TITLE_WORDS }

    @Test fun twoSharedRareWordsLinkTheStarsAndNameTheWords() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Russell Brand arrives at court for rape trial"),
                star("b", "Russell Brand was a sexual predator as rape trial opens"),
            ),
        )
        val line = titleLines(graph).single()
        assertEquals(setOf("a", "b"), setOf(line.a, line.b))
        assertEquals(listOf("brand", "rape", "russell", "trial"), line.sharedTerms)
        assertEquals(0, line.sharedConcepts)
    }

    @Test fun oneSharedWordIsNotEnough() {
        val graph = GalaxyGraphBuilder.build(
            listOf(star("a", "Police raid opposition station"), star("b", "Robot seized by police after incident")),
        )
        assertTrue(titleLines(graph).isEmpty())
    }

    @Test fun aWordInMoreThanTheDocFreqLimitNeverLinks() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        // "sanctions" and "russia" occur in limit + 1 headlines, so both are too common to mean anything.
        val common = (0..limit).map { star("c$it", "Sanctions russia item$it${"x".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(common)
        assertTrue("common words must not link: ${titleLines(graph)}", titleLines(graph).isEmpty())
    }

    @Test fun aWordAtExactlyTheDocFreqLimitStillCounts() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        val rare = (1..limit).map { star("r$it", "Sanctions russia topic$it${"y".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(rare)
        assertTrue("at the limit the words are still rare", titleLines(graph).isNotEmpty())
    }

    @Test fun rarerSharedWordsRankAheadOfCommonerOnes() {
        // strong: 2 shared words that appear only in this pair. weak: 2 shared words that appear in 4 headlines.
        val stars = listOf(
            star("s1", "Orangutan family released borneo"),
            star("s2", "Orangutans released borneo forest after fires"),
            star("w1", "Energy ceasefire claim alpha"),
            star("w2", "Energy ceasefire claim bravo"),
            star("w3", "Energy ceasefire claim charlie"),
            star("w4", "Energy ceasefire claim delta"),
        )
        val lines = titleLines(GalaxyGraphBuilder.build(stars))
        assertTrue(lines.isNotEmpty())
        assertEquals(setOf("s1", "s2"), setOf(lines.first().a, lines.first().b))
    }

    @Test fun eachStarKeepsAtMostTheNeighbourLimitOfTitleLines() {
        val stars = (1..8).map { star("n$it", "Burnham climate revolt crisis report$it${"z".repeat(it)}") }
        val graph = GalaxyGraphBuilder.build(stars)
        val degree = titleLines(graph).flatMap { listOf(it.a, it.b) }.groupingBy { it }.eachCount()
        assertTrue("degree: $degree", degree.values.all { it <= GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR })
    }

    @Test fun totalLinesNeverExceedTheDrawBudget() {
        val stars = (0 until GalaxyGraphBuilder.MAX_STARS).map {
            star("%04d".format(it), "Alpha${it % 40} beta${it % 41} gamma${it % 43} delta${it % 47}", it.toLong(), "common")
        }
        assertTrue(GalaxyGraphBuilder.build(stars).lines.size <= GalaxyGraphBuilder.MAX_LINES)
    }

    @Test fun aConceptLinkKeepsItsKindButStillNamesTheSharedWords() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Trump energy ceasefire claim", 1L, "politics"),
                star("b", "Trump energy ceasefire welcomed", 2L, "politics"),
            ),
        )
        val line = graph.lines.single()
        assertEquals(GalaxyLineKind.CONCEPT, line.kind)
        assertEquals(1, line.sharedConcepts)
        assertEquals(listOf("ceasefire", "energy", "trump"), line.sharedTerms)
    }

    @Test fun aPairIsNeverLinkedTwice() {
        val graph = GalaxyGraphBuilder.build(
            listOf(
                star("a", "Trump energy ceasefire claim", 1L, "politics"),
                star("b", "Trump energy ceasefire welcomed", 2L, "politics"),
            ),
        )
        assertEquals(1, graph.lines.size)
        assertEquals(graph.lines.size, graph.lines.map { setOf(it.a, it.b) }.toSet().size)
    }

    @Test fun theConceptLinkDoesNotUseUpTheTitleNeighbourBudget() {
        val concept = star("a", "Alpha bravo charlie delta", 1L, "x")
        val conceptPartner = star("b", "Alpha bravo charlie delta", 2L, "x")
        val titleOnly = (1..GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR).map {
            star("t$it", "Alpha bravo charlie delta echo$it${"q".repeat(it)}", 3L + it)
        }
        val graph = GalaxyGraphBuilder.build(listOf(concept, conceptPartner) + titleOnly)
        val aTitleLines = titleLines(graph).count { "a" == it.a || "a" == it.b }
        assertTrue("a keeps its title neighbours next to the concept line: $aTitleLines", aTitleLines >= 1)
    }

    @Test fun sharedTermsAreSortedAndDeterministicRegardlessOfInputOrder() {
        val input = listOf(
            star("a", "Zelensky putin trump summit"),
            star("b", "Putin zelensky trump talks"),
            star("c", "Unrelated gardening tips"),
        )
        assertEquals(GalaxyGraphBuilder.build(input), GalaxyGraphBuilder.build(input.reversed()))
        assertEquals(listOf("putin", "trump", "zelensky"), titleLines(GalaxyGraphBuilder.build(input)).single().sharedTerms)
    }

    @Test fun theDateWindowChangesWhichWordsAreRare() {
        val limit = GalaxyGraphBuilder.MAX_WORD_DOC_FREQ
        val pair = listOf(star("p1", "Pope cancer diagnosis surgery", 100L), star("p2", "Pope cancer diagnosis vatican", 101L))
        val noise = (1..limit).map { star("z$it", "Pope cancer diagnosis filler$it${"k".repeat(it)}", 10L + it) }
        val all = GalaxyGraphBuilder.build(pair + noise)
        val windowed = GalaxyGraphBuilder.build(pair + noise, from = 100L, to = 200L)
        assertTrue("with the noise the shared words are too common: ${titleLines(all)}", titleLines(all).none { setOf(it.a, it.b) == setOf("p1", "p2") })
        assertNotNull(titleLines(windowed).firstOrNull { setOf(it.a, it.b) == setOf("p1", "p2") })
    }

    @Test fun noTitleLinkWhenTitlesAreEmptyOrShort() {
        val graph = GalaxyGraphBuilder.build(listOf(star("a", ""), star("b", "   "), star("c", "UK EU US")))
        assertTrue(graph.lines.isEmpty())
        assertFalse(graph.stars.isEmpty())
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `/tmp/gtw-run.sh '--tests *GalaxyTitleLinksTest' t2red`. Mong đợi lỗi biên dịch `Unresolved reference 'GalaxyLineKind'`, `MAX_WORD_DOC_FREQ`, `sharedTerms`.
- [ ] **Step 3: Cài đặt `GalaxyGraph.kt`.**

```kotlin
enum class GalaxyLineKind { CONCEPT, TITLE_WORDS }

/** [sharedTerms] names the headline words two stars have in common (sorted); it may be set on a CONCEPT line too. */
data class GalaxyLine(
    val a: String,
    val b: String,
    val sharedConcepts: Int,
    val kind: GalaxyLineKind = GalaxyLineKind.CONCEPT,
    val sharedTerms: List<String> = emptyList(),
)
```

- [ ] **Step 4: Cài đặt `GalaxyGraphBuilder`.** Thêm hằng `MAX_WORD_DOC_FREQ = 8`, `MIN_SHARED_TERMS = 2`. Trong `build`, sau khi có `stars` và đường khái niệm đã sắp (`conceptLines`, trước `take(MAX_LINES)`), làm:
  1. `words = stars.associate { it.id to TitleWords.extract(it.title) }`; `df = Map<String, Int>` đếm số sao chứa từ; `rare = df.filterValues { it <= MAX_WORD_DOC_FREQ }.keys`.
  2. Chỉ mục ngược `word -> List<starId>` chỉ cho từ trong `rare`; với mỗi từ, duyệt mọi cặp trong danh sách của nó (danh sách <= 8 phần tử nên tối đa 28 cặp/từ) và cộng vào `Map<Pair<String,String>, MutableList<String>>` (cặp có thứ tự `a < b` theo id).
  3. Giữ cặp có `terms.size >= MIN_SHARED_TERMS`; điểm = `terms.sumOf { 1.0 / df.getValue(it) }`; sắp điểm giảm dần rồi theo `a`, rồi `b`; `sharedTerms = terms.sorted()`.
  4. Duyệt theo thứ tự đó, đếm bậc **chỉ của đường từ-tiêu-đề** (`titleDegree`), bỏ cặp khi một đầu đã đủ `MAX_NEIGHBORS_PER_STAR`.
  5. Nếu cặp đã có đường khái niệm: **thay** đường đó bằng bản copy có `sharedTerms` (giữ `kind = CONCEPT`, giữ `sharedConcepts`); không tăng `titleDegree` và không thêm đường thứ hai. Ngược lại thêm `GalaxyLine(a, b, 0, TITLE_WORDS, terms)`.
  6. Gộp, sắp theo thứ tự cũ (`sharedConcepts` giảm dần, rồi điểm, rồi `a`, `b`), `take(MAX_LINES)`. Đường khái niệm đứng trước đường từ-tiêu-đề khi `sharedConcepts` bằng nhau, để ngân sách 400 không đẩy đường khái niệm ra.
  Không đổi cách chọn và giới hạn sao (`MAX_STARS`, lọc ngày).
- [ ] **Step 5: Chạy xanh, rồi chạy cả bộ builder cũ.** `/tmp/gtw-run.sh '--tests *GalaxyTitleLinksTest --tests *GalaxyGraphBuilderTest --tests *GalaxyLayoutTest --tests *GalaxySvTest' t2`. Mong đợi `GalaxyTitleLinksTest` 13, `GalaxyGraphBuilderTest` 9 (không đổi), 0 lỗi. Nếu một test cũ đỏ vì đường từ-tiêu-đề xuất hiện ngoài ý muốn thì **dừng và hỏi** trước khi sửa test cũ.
- [ ] **Step 6: Mutation-check (từng cái, khôi phục sau mỗi lần).**
  - (a) `MIN_SHARED_TERMS = 2` thành `1`: `oneSharedWordIsNotEnough` phải đỏ.
  - (b) `it <= MAX_WORD_DOC_FREQ` thành `it <= MAX_WORD_DOC_FREQ + 1`: `aWordInMoreThanTheDocFreqLimitNeverLinks` phải đỏ.
  - (c) `it <= MAX_WORD_DOC_FREQ` thành `it < MAX_WORD_DOC_FREQ`: `aWordAtExactlyTheDocFreqLimitStillCounts` phải đỏ.
  - (d) Đổi điểm `1.0 / df` thành `1.0` (mọi từ bằng nhau): `rarerSharedWordsRankAheadOfCommonerOnes` phải đỏ.
  - (e) Bỏ kiểm `titleDegree` (bỏ giới hạn láng giềng): `eachStarKeepsAtMostTheNeighbourLimitOfTitleLines` phải đỏ.
  - (f) Bỏ bước thay đường khái niệm bằng bản có `sharedTerms` (luôn thêm đường mới): `aPairIsNeverLinkedTwice` và `aConceptLinkKeepsItsKindButStillNamesTheSharedWords` phải đỏ.
  - (g) Bỏ `.sorted()` của `sharedTerms`: `sharedTermsAreSortedAndDeterministicRegardlessOfInputOrder` phải đỏ.
  - Nếu một đột biến sống sót, thêm test nhắm đúng nó rồi chạy lại mutation tới khi đỏ, và ghi vào DONE doc.
- [ ] **Step 7: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/model/galaxy app/src/test/java/com/mckimquyen/reader/domain/model/galaxy && git commit -m "feat(front-03b): link galaxy stars that share rare title words"` (+ trailer).

---

### Task 3: `GalaxyNeighbours.of` (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyNeighbours.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/galaxy/GalaxyNeighboursTest.kt`

**Interfaces:**
- Consumes: `GalaxyGraph`, `GalaxyLine`, `GalaxyLineKind`, `GalaxyStar` (Task 2).
- Produces: `GalaxyNeighbour(star, sharedTerms, kind)` và `GalaxyNeighbours.of(graph, starId, limit = GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR): List<GalaxyNeighbour>`.

Quy tắc: lấy mọi đường chạm `starId`, đổi sang sao ở đầu kia, **bỏ đường không có `sharedTerms`** (đường khái niệm thuần không có từ để hiển thị; thẻ sao chỉ nói điều nó chứng minh được là "từ chung"), sắp theo thứ tự của `graph.lines` (đã sắp mạnh nhất trước), cắt `limit`. Sao lạ hoặc không có láng giềng trả danh sách rỗng.

- [ ] **Step 1: Test đỏ.**

```kotlin
package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyNeighboursTest {
    private fun star(id: String) = GalaxyStar(id, "Title $id", "BBC", 1L, emptySet())
    private fun title(a: String, b: String, vararg terms: String) =
        GalaxyLine(a, b, 0, GalaxyLineKind.TITLE_WORDS, terms.toList())

    private val stars = listOf("a", "b", "c", "d", "e").map(::star)

    @Test fun returnsTheOtherEndOfEachLineWithItsSharedWords() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "trump", "putin"), title("c", "a", "burnham", "city")))
        val result = GalaxyNeighbours.of(graph, "a")
        assertEquals(listOf("b", "c"), result.map { it.star.id })
        assertEquals(listOf("trump", "putin"), result[0].sharedTerms)
        assertEquals(listOf("burnham", "city"), result[1].sharedTerms)
        assertEquals(GalaxyLineKind.TITLE_WORDS, result[0].kind)
    }

    @Test fun worksWhicheverEndTheStarIsOn() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "x1", "x2")))
        assertEquals(listOf("b"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
        assertEquals(listOf("a"), GalaxyNeighbours.of(graph, "b").map { it.star.id })
    }

    @Test fun keepsTheStrengthOrderOfTheGraphAndCutsAtTheLimit() {
        val graph = GalaxyGraph(
            stars,
            listOf(title("a", "b", "t1", "t2"), title("a", "c", "t3", "t4"), title("a", "d", "t5", "t6"), title("a", "e", "t7", "t8")),
        )
        assertEquals(listOf("b", "c", "d"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
        assertEquals(listOf("b"), GalaxyNeighbours.of(graph, "a", limit = 1).map { it.star.id })
        assertEquals(4, GalaxyNeighbours.of(graph, "a", limit = 10).size)
    }

    @Test fun skipsLinesThatHaveNoWordsToShow() {
        val graph = GalaxyGraph(stars, listOf(GalaxyLine("a", "b", 2), title("a", "c", "t1", "t2")))
        assertEquals(listOf("c"), GalaxyNeighbours.of(graph, "a").map { it.star.id })
    }

    @Test fun aConceptLineThatCarriesWordsIsShownAsAConceptNeighbour() {
        val graph = GalaxyGraph(stars, listOf(GalaxyLine("a", "b", 1, GalaxyLineKind.CONCEPT, listOf("trump", "energy"))))
        val only = GalaxyNeighbours.of(graph, "a").single()
        assertEquals(GalaxyLineKind.CONCEPT, only.kind)
        assertEquals(listOf("trump", "energy"), only.sharedTerms)
    }

    @Test fun unknownStarAndIsolatedStarGiveNothing() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "t1", "t2")))
        assertTrue(GalaxyNeighbours.of(graph, "ghost").isEmpty())
        assertTrue(GalaxyNeighbours.of(graph, "e").isEmpty())
        assertTrue(GalaxyNeighbours.of(GalaxyGraph.EMPTY, "a").isEmpty())
    }

    @Test fun neverReturnsTheStarItselfOrAStarMissingFromTheGraph() {
        val graph = GalaxyGraph(listOf(star("a")), listOf(title("a", "a", "t1", "t2"), title("a", "ghost", "t3", "t4")))
        assertTrue(GalaxyNeighbours.of(graph, "a").isEmpty())
    }

    @Test fun aNonPositiveLimitGivesNothing() {
        val graph = GalaxyGraph(stars, listOf(title("a", "b", "t1", "t2")))
        assertTrue(GalaxyNeighbours.of(graph, "a", limit = 0).isEmpty())
        assertTrue(GalaxyNeighbours.of(graph, "a", limit = -3).isEmpty())
    }
}
```

- [ ] **Step 2: Chạy đỏ.** `/tmp/gtw-run.sh '--tests *GalaxyNeighboursTest' t3red`; mong đợi `Unresolved reference 'GalaxyNeighbours'`.
- [ ] **Step 3: Cài đặt.**

```kotlin
package com.mckimquyen.reader.domain.model.galaxy

/** One star related to the selected star, plus the words that justify showing it. */
data class GalaxyNeighbour(val star: GalaxyStar, val sharedTerms: List<String>, val kind: GalaxyLineKind)

/** Pure lookup used by the star card; keeps the Compose code free of graph logic. */
object GalaxyNeighbours {
    fun of(
        graph: GalaxyGraph,
        starId: String,
        limit: Int = GalaxyGraphBuilder.MAX_NEIGHBORS_PER_STAR,
    ): List<GalaxyNeighbour> {
        if (limit <= 0) return emptyList()
        val byId = graph.stars.associateBy { it.id }
        return graph.lines.asSequence()
            .filter { it.sharedTerms.isNotEmpty() && (it.a == starId || it.b == starId) }
            .mapNotNull { line ->
                val otherId = if (line.a == starId) line.b else line.a
                if (otherId == starId) return@mapNotNull null
                byId[otherId]?.let { GalaxyNeighbour(it, line.sharedTerms, line.kind) }
            }
            .take(limit)
            .toList()
    }
}
```

- [ ] **Step 4: Chạy xanh.** Cùng lệnh Step 2; đọc XML: 8 test, 0 lỗi.
- [ ] **Step 5: Mutation-check.**
  - (a) Bỏ `it.sharedTerms.isNotEmpty() &&`: `skipsLinesThatHaveNoWordsToShow` phải đỏ.
  - (b) Bỏ `.take(limit)`: `keepsTheStrengthOrderOfTheGraphAndCutsAtTheLimit` phải đỏ.
  - (c) Bỏ phép chọn đầu kia (luôn lấy `line.b`): `worksWhicheverEndTheStarIsOn` phải đỏ.
  - (d) Bỏ `if (limit <= 0) return emptyList()`: `aNonPositiveLimitGivesNothing` phải đỏ (với `limit = -3` `take` ném `IllegalArgumentException`, với `0` trả rỗng; test phải bắt ca âm).
- [ ] **Step 6: Commit.** `feat(front-03b): look up the related stars of a galaxy star` (+ trailer).

---

### Task 4: Chuỗi ở 38 locale

**Files:** Modify `app/src/main/res/values*/strings.xml`. Tạo `/tmp/gtw_strings.py`.

| Khoá | EN | VI |
|---|---|---|
| `galaxy_related_title` | Related articles | Bài liên quan |
| `galaxy_shared_terms` | Shared words: %1$s | Chung từ: %1$s |

- [ ] **Step 1: Script `/tmp/gtw_strings.py`** (cùng mẫu `/tmp/exc13_strings.py` cũ: `glob('app/src/main/res/values*/strings.xml')`, từ điển `EN`/`VI`, `esc()` thoát `&`, `'`, `"`, chèn khối `<!-- FRONT-03b galaxy title links -->` ngay trước `</resources>`, bỏ qua khoá đã có, in `N locale files`):

```python
# -*- coding: utf-8 -*-
import glob, os
os.chdir('/Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou')
EN = {'galaxy_related_title': 'Related articles', 'galaxy_shared_terms': 'Shared words: %1$s'}
VI = {'galaxy_related_title': 'Bài liên quan', 'galaxy_shared_terms': 'Chung từ: %1$s'}
def esc(v): return v.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')
files = glob.glob('app/src/main/res/values*/strings.xml')
for f in files:
    values = VI if f.endswith('values-vi/strings.xml') else EN
    text = open(f, encoding='utf-8').read()
    missing = {k: v for k, v in values.items() if f'<string name="{k}"' not in text}
    if not missing: continue
    assert text.count('</resources>') == 1, f
    block = '\n    <!-- FRONT-03b galaxy title links -->\n' + ''.join(
        f'    <string name="{k}">{esc(v)}</string>\n' for k, v in missing.items())
    open(f, 'w', encoding='utf-8').write(text.replace('</resources>', block + '</resources>'))
print(len(files), 'locale files')
```

- [ ] **Step 2: Chạy.** `python3 /tmp/gtw_strings.py > /tmp/gtw-str.out 2>&1; cat /tmp/gtw-str.out` (mong đợi `38 locale files`) và `grep -L 'name="galaxy_shared_terms"' app/src/main/res/values*/strings.xml | wc -l` phải in `0`.
- [ ] **Step 3: Parity.** `/tmp/gtw-run.sh '--tests *StringResourceParityTest' t4`; đọc XML: 6/6.
- [ ] **Step 4: Commit.** `feat(front-03b): add related-article strings across 38 locales` (+ trailer). Lint `UnusedResources` chỉ sạch sau Task 5 (khi hai chuỗi được dùng); chạy lint ở Task 6.

---

### Task 5: Thẻ sao hiện bài liên quan và chạm để chuyển sao

**Files:**
- Modify: `ui/page/secondbrain/galaxy/GalaxyStarSheet.kt`, `GalaxyPanel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/secondbrain/GalaxyNeighbourCardTest.kt` (tệp mới) và giữ nguyên `GalaxyPageTest`

**Interfaces:**
- Consumes: `GalaxyNeighbours.of`, `GalaxyNeighbour` (Task 3); chuỗi `galaxy_related_title`, `galaxy_shared_terms` (Task 4); `GalaxyPanel.onSelectStar` đã có.
- Produces: `GalaxyStarSheet(star, onOpen, modifier, neighbours = emptyList(), onSelectNeighbour = {})` (tham số mới **cuối** và có mặc định, nên caller và test cũ không đổi); tag `GALAXY_NEIGHBOURS_TAG`, `GALAXY_NEIGHBOUR_ROW_TAG_PREFIX = "galaxy_neighbour_"`.

Hành vi: khi `neighbours` rỗng, **không** hiện tiêu đề mục và không có hàng nào. Mỗi hàng là một vùng chạm >= 48dp có `role = Role.Button`, hiện tiêu đề bài kia (tối đa 2 dòng) và dòng `galaxy_shared_terms` với các từ nối bằng ", ". Chạm hàng gọi `onSelectNeighbour(star.id)` và **không** mở bài; nút "Mở bài" vẫn riêng. `GalaxyPanel` tính `GalaxyNeighbours.of(state.graph, star.id)` trong `remember(state.graph, star.id)` và truyền `onSelectNeighbour = onSelectStar`.

- [ ] **Step 1: Test đỏ** (Robolectric: `@Config(sdk = [34], application = Application::class) @GraphicsMode(NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`, **một `setContent` mỗi test**).

```kotlin
package com.mckimquyen.reader.ui.page.secondbrain

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLineKind
import com.mckimquyen.reader.domain.model.galaxy.GalaxyNeighbour
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_NEIGHBOURS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_NEIGHBOUR_ROW_TAG_PREFIX
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_OPEN_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GalaxyStarSheet
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
class GalaxyNeighbourCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun str(id: Int, vararg a: Any) = compose.activity.getString(id, *a)

    private val star = GalaxyStar("s1", "Trump and Zelensky at loggerheads", "BBC News", 1_000L, emptySet())
    private fun neighbour(id: String, title: String, vararg terms: String) =
        GalaxyNeighbour(GalaxyStar(id, title, "BBC News", 2_000L, emptySet()), terms.toList(), GalaxyLineKind.TITLE_WORDS)

    private fun show(
        neighbours: List<GalaxyNeighbour>,
        onOpen: (String) -> Unit = {},
        onSelectNeighbour: (String) -> Unit = {},
    ) = compose.setContent {
        GalaxyStarSheet(star = star, onOpen = onOpen, neighbours = neighbours, onSelectNeighbour = onSelectNeighbour)
    }

    @Test fun noNeighboursShowsNoSectionAndNoRows() {
        show(emptyList())
        compose.onAllNodesWithTag(GALAXY_NEIGHBOURS_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "x").assertCountEquals(0)
        compose.onNodeWithTag(GALAXY_OPEN_TAG).assertIsDisplayed()
    }

    @Test fun showsTheSectionTitleTheOtherTitleAndTheSharedWords() {
        show(listOf(neighbour("s2", "Why is Trump criticising Zelensky", "trump", "zelensky")))
        compose.onNodeWithTag(GALAXY_NEIGHBOURS_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_related_title)).assertIsDisplayed()
        compose.onNodeWithText("Why is Trump criticising Zelensky").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_shared_terms, "trump, zelensky")).assertIsDisplayed()
    }

    @Test fun tappingARowSelectsThatNeighbourAndDoesNotOpenAnyArticle() {
        var selected: String? = null
        var opened: String? = null
        show(
            listOf(neighbour("s2", "Second", "alpha", "bravo"), neighbour("s3", "Third", "charlie", "delta")),
            onOpen = { opened = it },
            onSelectNeighbour = { selected = it },
        )
        compose.onNodeWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "s3").performClick()
        compose.runOnIdle {
            assertEquals("s3", selected)
            assertEquals(null, opened)
        }
    }

    @Test fun theOpenButtonStillOpensTheSelectedStarNotTheNeighbour() {
        var opened: String? = null
        show(listOf(neighbour("s2", "Second", "alpha", "bravo")), onOpen = { opened = it })
        compose.onNodeWithTag(GALAXY_OPEN_TAG).performClick()
        compose.runOnIdle { assertEquals("s1", opened) }
    }

    @Test fun threeNeighboursProduceThreeRows() {
        show((1..3).map { neighbour("n$it", "Neighbour $it", "word$it", "term$it") })
        (1..3).forEach { compose.onNodeWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "n$it").assertIsDisplayed() }
    }
}
```

Ngoài tệp trên, thêm vào `GalaxyPageTest` một test **cấp trang** chứng minh `GalaxyPanel` nối đúng: dựng `GalaxyUiState.Ready` với đồ thị có một đường `TITLE_WORDS` giữa `s1` và `s2`, chọn `s1`, kiểm hàng của `s2` hiện, chạm hàng và kiểm `onSelectStar` nhận `"s2"`.

```kotlin
    @Test
    fun selectedStarListsItsTitleWordNeighbour_andTappingItSelectsIt() {
        var selected: String? = null
        val linked = GalaxyGraph(
            stars,
            listOf(GalaxyLine("s1", "s2", 0, GalaxyLineKind.TITLE_WORDS, listOf("alpha", "bravo"))),
        )
        val state = GalaxyUiState.Ready(linked, "s1", 1_000L, 3_000L, 1_000L, 3_000L)
        show(state, onSelectStar = { selected = it })
        openGalaxy()
        compose.onNodeWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "s2").performClick()
        compose.runOnIdle { assertEquals("s2", selected) }
    }
```

(thêm import `GalaxyLineKind` và `GALAXY_NEIGHBOUR_ROW_TAG_PREFIX` vào `GalaxyPageTest`.)

- [ ] **Step 2: Chạy đỏ.** `/tmp/gtw-run.sh '--tests *GalaxyNeighbourCardTest --tests *GalaxyPageTest' t5red`; mong đợi `Unresolved reference` cho tham số `neighbours`, `onSelectNeighbour` và các tag mới.
- [ ] **Step 3: Cài đặt `GalaxyStarSheet`.** Thêm hai tham số cuối và hai hằng tag. Sau nút "Mở bài", nếu `neighbours.isNotEmpty()`: một `Column` có `testTag(GALAXY_NEIGHBOURS_TAG)` gồm `Text(stringResource(R.string.galaxy_related_title), titleSmall)` rồi mỗi neighbour một `Column` với `Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onSelectNeighbour(n.star.id) }.testTag(PREFIX + n.star.id)`, chứa tiêu đề (`bodyMedium`, `maxLines = 2`, ellipsis) và `Text(stringResource(R.string.galaxy_shared_terms, n.sharedTerms.joinToString(", ")), bodySmall, color = onSurfaceVariant)`. Giữ `Surface` hiện có; cho cả thẻ cuộn dọc (`verticalScroll(rememberScrollState())` bọc `Column` bên trong, giới hạn chiều cao bằng `heightIn(max = 280.dp)`) để màn hình thấp và edge-to-edge không bị tràn.
- [ ] **Step 4: Nối `GalaxyPanel`.** Thay khối chọn sao bằng:

```kotlin
state.graph.stars.firstOrNull { it.id == state.selectedStarId }?.let { star ->
    val neighbours = remember(state.graph, star.id) { GalaxyNeighbours.of(state.graph, star.id) }
    GalaxyStarSheet(
        star = star,
        onOpen = onOpenArticle,
        neighbours = neighbours,
        onSelectNeighbour = onSelectStar,
    )
}
```

- [ ] **Step 5: Chạy xanh, kèm test cũ.** `/tmp/gtw-run.sh '--tests *GalaxyNeighbourCardTest --tests *GalaxyPageTest --tests *SecondBrainViewportUiTest --tests *GalaxyViewModelTest' t5`. Mong đợi `GalaxyNeighbourCardTest` 5, `GalaxyPageTest` 14 (13 cũ + 1), 0 lỗi.
- [ ] **Step 6: Mutation-check.**
  - (a) Luôn hiện tiêu đề mục kể cả khi `neighbours` rỗng (bỏ `isNotEmpty()`): `noNeighboursShowsNoSectionAndNoRows` phải đỏ.
  - (b) Cho hàng gọi `onOpen(n.star.id)` thay vì `onSelectNeighbour`: `tappingARowSelectsThatNeighbourAndDoesNotOpenAnyArticle` phải đỏ.
  - (c) Bỏ truyền `onSelectNeighbour = onSelectStar` trong `GalaxyPanel`: test cấp trang `selectedStarListsItsTitleWordNeighbour_andTappingItSelectsIt` phải đỏ.
  - (d) Bỏ `.joinToString(", ")` thành `.firstOrNull()`: `showsTheSectionTitleTheOtherTitleAndTheSharedWords` phải đỏ.
- [ ] **Step 7: Build + Hilt.** `./gradlew assembleDevDebug -q`.
- [ ] **Step 8: Commit.** `feat(front-03b): show related articles on the star card` (+ trailer).

---

### Task 6: Xác minh đầy đủ, đo khung hình và smoke trên Tecno, tài liệu

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/secondbrain/KnowledgeGalaxyDeviceTest.kt`
- Create: `doc/task/done/FRONT-03b_galaxy-title-word-links_DONE.md`
- Modify: `doc/feature.md`, `doc/task/done/FRONT-03_3d-knowledge-galaxy_DONE.md`

- [ ] **Step 1: Mở rộng test instrumentation** (thêm một test, giữ hai test cũ): dựng `GalaxyGraph` có đường `TITLE_WORDS` giữa `s1` và `s2`, chọn `s1`, chạm hàng `s2` rồi kiểm callback `onSelectStar` nhận `"s2"`; dùng `SecondBrainPage(galaxyState = ..., onSelectStar = ...)` như test cũ.
- [ ] **Step 2: Kiểm tra đầy đủ tuần tự** (script nền ghi ra file): `./gradlew testDevDebugUnitTest --rerun -q`, `./gradlew lintDevDebug --rerun-tasks -q`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest -q`. Đọc đếm unit từ mọi XML (mong đợi nền **2.052** cộng khoảng 9 (`TitleWords`) + 2 (`StopWords`) + 13 (`GalaxyTitleLinks`) + 8 (`GalaxyNeighbours`) + 5 (`GalaxyNeighbourCard`) + 1 (`GalaxyPageTest`) = khoảng 2.090; ghi số thật), lint (mong đợi 0 error, warning **không vượt 2.463**, liệt kê issue nhắc `galaxy`, ví dụ `UnusedResources`, `PluralsCandidate`). Chạy bộ galaxy **3 lần**.
- [ ] **Step 3: Instrumentation trên Tecno.** `adb devices -l` phải có `115333744A005844`; kiểm `/tmp/exc13-tecno-lib.sh` (`awake`); cài app và test bằng `adb -s 115333744A005844 install -r -d -t ...` (tìm APK bằng `find`, tên có dấu cách); `am instrument -w -e class com.mckimquyen.reader.ui.page.secondbrain.KnowledgeGalaxyDeviceTest ...`; mong đợi `OK (3 tests)`; gỡ `com.mckimquyen.reader.test`.
- [ ] **Step 4: Sao lưu MỚI trước smoke** (dữ liệu Tecno có thể đã đổi): force-stop; `exec-out run-as ... cat` lưu `databases/Reader`, `Reader-wal`, `Reader-shm`, `files/datastore/settings.preferences_pb` vào `/tmp/gtw-tecno-backup`; `Reader`, `Reader-shm`, DataStore phải không rỗng; kiểm `PRAGMA integrity_check` = `ok` trên bản sao 3 file.
- [ ] **Step 5: Tạo bản sao đánh dấu đã đọc.** Chép `Reader`, `-wal`, `-shm` sang `/tmp/gtw-seed`, `sqlite3 Reader "PRAGMA wal_checkpoint(TRUNCATE);"`, `UPDATE article SET isUnread=0`, kiểm `integrity_check` và `foreign_key_check`, tạo `-wal` và `-shm` rỗng, nạp lên máy bằng `adb -s 115333744A005844 shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader'" < /tmp/gtw-seed/Reader` (cùng `-wal`, `-shm` rỗng), đọc lại và `cmp`. **Không động tới DataStore.**
- [ ] **Step 6: Đo khung hình SAU** là Step 7 bên dưới; số "TRƯỚC" đã đo ở **Task 00** trước khi sửa mã.
- [ ] **Step 7: Đo SAU** với bản mới, dùng `/tmp/gtw-frames.sh after` (Task 00), 3 lượt; ghi `Links: M` (mong đợi khoảng 50 so với 5 trước). So số và **báo trung thực** kể cả khi hiệu năng xấu đi hay không khác (Tecno nền 3,67% giật; A50s 65–71% không so sánh được).
- [ ] **Step 8: Smoke thẻ sao.** Chạm một sao có láng giềng (từ ảnh chụp để chọn toạ độ), xác nhận thẻ hiện "Bài liên quan" và các hàng "Chung từ: ...", chạm một hàng và xác nhận sao được chọn đổi sang bài kia (không mở bài), nút "Mở bài" vẫn mở đúng bài. Chạm sao rời và xác nhận **không** có mục "Bài liên quan". Chụp màn hình và xem ảnh.
- [ ] **Step 9: Khôi phục (luôn làm).** Force-stop; nạp lại 3 file Room và `settings.preferences_pb` từ `/tmp/gtw-tecno-backup` bằng `run-as sh -c 'cat > PATH'`; đọc lại và `cmp` cả 4 file; `integrity_check` ok; `pm list packages com.mckimquyen.reader.test` rỗng. **Không xoá DataStore.**
- [ ] **Step 10: Tài liệu.** Viết `doc/task/done/FRONT-03b_galaxy-title-word-links_DONE.md` theo mẫu `FRONT-03_3d-knowledge-galaxy_DONE.md`: phạm vi trung thực (một quan hệ mới, không đổi nét vẽ, không đổi layout), số đo trước/sau thật (đường, sao có đường, khung hình), giới hạn nói thẳng từ spec (trùng từ không phải hiểu nghĩa, tiếng Anh, thưa 56/152, một nguồn, đổi theo khoảng ngày, tiếng Việt chưa đo, hiệu năng A50s chưa giải quyết), mutation-check đã làm và những đột biến sống sót lần đầu cùng test thêm, phần chưa kiểm. Thêm mục vào `doc/feature.md`. Trong `FRONT-03_3d-knowledge-galaxy_DONE.md`, mục 2 của "Giới hạn đã biết", thêm câu trỏ tới `FRONT-03b_galaxy-title-word-links_DONE.md`.
- [ ] **Step 11: Commit.** `git add app/src doc docs && git commit -m "feat(front-03b): complete title-word links for the galaxy"` (+ trailer); `git status --short | grep -v .idea` phải rỗng.

---

## Self-review

**1. Phủ spec.** Từ chung trong tiêu đề, ≥ 4 ký tự, bỏ stop-word và số: Task 1. Ngưỡng ≥ 2 từ chung, `df ≤ 8`, điểm `sum(1/df)`, tối đa 3 láng giềng, tổng ≤ 400, một cặp một đường, đường khái niệm giữ `kind`: Task 2. Thẻ sao ghi "Chung từ", tối đa 3 hàng, chạm hàng chọn sao kia, không hiện khi rời: Task 3 và 5. Chuỗi 38 locale: Task 4. Đo trước/sau trên Tecno và báo số thật, smoke bằng bản sao DB: Task 6. Giới hạn nói thẳng và DONE doc: Task 6 Step 10. Không đổi `GalaxyLayout`, Room, nét vẽ: Global Constraints.

**2. Placeholder.** Không còn "TBD"/"TODO". Task 2 Step 4 mô tả thuật toán bằng lời kèm 13 test làm hợp đồng; đây là chủ ý, vì các test mã hoá mọi quy tắc của spec.

**3. Nhất quán kiểu.** `GalaxyLine(a, b, sharedConcepts, kind, sharedTerms)` định nghĩa ở Task 2 và dùng ở Task 3, 5, 6; `GalaxyNeighbour(star, sharedTerms, kind)` ở Task 3 và dùng ở Task 5; `GalaxyStarSheet(..., neighbours, onSelectNeighbour)` ở Task 5 và `GalaxyPanel` truyền đúng tên. `TitleWords.MIN_WORD_CHARS` dùng ở Task 1 và `StopWordsTest`.

**Rủi ro, để người thực thi cân nhắc:** (a) Số đo "trước" bắt buộc làm ở Task 00, vì sau Task 2 không còn bản app cũ để đo. (b) Số test tổng ở Step 2 là ước lượng; ghi số thật từ XML. (c) Danh sách `stopWords` của `TitleWords` có thể cần thêm từ khi `StopWordsTest` đỏ; đó là mục đích của test. (d) Nếu một test cũ của `GalaxyGraphBuilderTest` đỏ vì đường từ-tiêu-đề xuất hiện ngoài ý muốn thì dừng và hỏi.
