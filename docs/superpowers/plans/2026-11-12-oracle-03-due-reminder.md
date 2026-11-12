# ORACLE-03 Due Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khi một cược đã đến hạn mà người dùng chưa báo kết quả, app nhắc bằng một thông báo gộp sau mỗi lần sync thành công, và khi mở thẻ kèo thì hiện tối đa 3 bài liên quan trong tin đã lưu làm gợi ý bằng chứng; người dùng vẫn tự bấm Có / Không / Không rõ.

**Architecture:** Hai phần thuần (`OracleEvidencePolicy`, mã hóa tập "đã nhắc") tách khỏi I/O. `OracleEvidenceFinder` dùng `SemanticSearchEngine.rank` trên các bài mới nhất (chỉ cột danh sách) rồi lọc theo ngày đặt cược. `OracleDueReminder` đọc cược `OPEN` đã đến hạn qua một truy vấn mới của `OracleWalletDao` (không đổi lược đồ), đăng **một** thông báo ở kênh riêng và chỉ đánh dấu "đã nhắc" bằng DataStore **sau khi đăng**. `SyncWorker` gọi nó trong `runCatching`; `OracleBettingViewModel` tải bằng chứng khi pha là `AWAITING_RESULT` và `OracleBettingSection` hiển thị.

**Tech Stack:** Kotlin, Room v22 (giữ nguyên), DataStore, Compose Material3, Hilt, JUnit4 + MockK + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-12-oracle-03-due-reminder-design.md` (commit `a2fcc5cb`). Owner đã duyệt từng phần.
- **Room GIỮ v22**: không migration, không bảng/cột mới, `22.json` không đổi. Chỉ thêm một **truy vấn** vào `OracleWalletDao`.
- **Không tự chốt bằng AI**, **không Gemini/gateway**, không consent mới (không có dữ liệu nào rời máy), không dependency mới, không quảng cáo/IAP/tiền, không đổi luật điểm hay `settleBet`.
- Hằng số (đặt tên, test số cứng): `MAX_EVIDENCE = 3`, `RECENT_SCAN_LIMIT = 200`, `REMINDED_CAP = 200`.
- **Chỉ bài có `date >= placedAt`** của cược mới được tính là bằng chứng. Bằng chứng **không lưu**, tìm lại mỗi lần mở thẻ. Không đọc thân bài (chỉ cột danh sách `ARTICLE_LIST_COLUMNS`).
- Nhắc: chỉ cược `OPEN` có `expiresAt <= now`; bỏ cược đã nhắc; **một** thông báo gộp mỗi lần chạy (một cược: nội dung là câu hỏi; nhiều cược: "N kèo đã đến hạn"); **không ghi số điểm** vào thông báo; nếu `canPostNotifications()` sai thì **không đăng và không đánh dấu**; chỉ đánh dấu **sau khi đăng**; tập "đã nhắc" lưu bằng DataStore, trần **200**, cắt phần cũ nhất, dọn cược đã chốt/hoàn. Chạy **cả khi consent AI Oracle tắt**. Lỗi chỉ log loại exception, không nuốt `CancellationException`, không làm hỏng sync.
- Kênh thông báo riêng `oracle_due`, bật sẵn; tên và mô tả kênh đọc từ chuỗi.
- UI: mục bằng chứng ở pha `AWAITING_RESULT`, **trên** ba nút kết quả; tối đa 3 hàng; không có bài thì hiện "Chưa thấy bài nào liên quan", **không bịa**; nhãn "gợi ý từ tin đã lưu, chưa chắc đúng; bạn tự quyết định"; đích chạm ≥ 48 dp, `contentDescription`.
- Chuỗi mới đủ **38 locale** (8 khóa `oracle_due_*`; EN và VI viết tay, 36 còn lại English), một script **all-or-none** (thư mục locale thật như `values-de-rDE`, không đoán; kiểm lại số khóa); không `%d` ngay trước danh từ số nhiều (dùng `%1$s`); `StringResourceParityTest` xanh.
- Đổi constructor Hilt (`SyncWorker`, `OracleBettingViewModel`) và thêm lớp `@Singleton` mới thì biên dịch lại sạch: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`.
- Code: không `!!`/`late` thiếu lý do, không magic number, mọi Job huỷ khi ViewModel clear, log không chứa câu hỏi hay tiêu đề bài (chỉ loại exception). Đích chạm ≥ 48 dp.
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML kết quả), code tối thiểu, xanh, mutation cho guard quan trọng (backup + `cmp`), commit riêng từng task, không chain `git commit` sau build nếu chưa kiểm exit code. Test giới hạn phải có test **số cứng**.
- **Cách chạy Gradle (bài học đã trả giá):** chạy **nền** (`( ... ; echo exit=$? > /tmp/o03-<ten>.exit ) &`) rồi chờ tệp `.exit` tồn tại **trước khi** đọc `app/build/test-results/.../*.xml`. Thông báo "completed" của lệnh nền chỉ nói launcher đã thoát, không nói Gradle đã xong; đọc XML khi chưa có `.exit` sẽ ra kết quả **cũ**. Khi biên dịch lỗi thì XML cũ còn nguyên: luôn kiểm `exit=0` và đọc `grep -E '^e: '` trong log. Output Gradle ra file; không dùng `echo ====` trong zsh; `grep -r` có dấu nháy.
- **Mutation:** dùng `/tmp/o03_mut.sh <nhãn> <tệp> '<mẫu cũ>' '<mẫu mới>' <FQCN test>...` (sao từ `/tmp/o02_mut.sh`; sao tệp, thay đúng một mẫu bằng `python3`, chạy test, **luôn khôi phục** rồi in `restored`/`NOT-RESTORED`; mẫu không tồn tại in `PATTERN-MISSING`, **không tính là đã bắt**). Truyền mẫu chứa `$` bằng **nháy đơn** (zsh nội suy `$` trong nháy kép). Chạy cả đợt mutation bằng một script ở nền và **không sửa các tệp đó** cho tới khi thấy `ALLDONE`. Sau mỗi đợt, kiểm các tệp đã khôi phục (`grep -c` mẫu gốc = 1, hoặc `cmp`), nhất là khi một lệnh bị hủy giữa chừng. Mutation **sống sót** là tín hiệu test yếu: viết thêm test rồi chạy lại cho tới khi bị bắt, hoặc ghi rõ vào báo cáo vì sao không bắt được.
- Bài học thêm: `sed -i` trên macOS cần đối số rỗng nên sửa tệp bằng `python3`; `org.json` cần Robolectric; test nguồn chỉ xét mã (bỏ dòng comment và `import`); mutation sống sót từng lộ test yếu (thẻ `null` → Dialog rỗng cần `isDialog()`; `@Transaction` cần test trigger SQL; ngoại lệ trong coroutine cần chặn `Thread.UncaughtExceptionHandler`); Room cập nhật hai luồng riêng nên test chờ **trạng thái cuối**, không chờ một khung giữa.
- Thiết bị: **chỉ Pixel `2B051FDH3006MU`** (Android 17; owner: "dùng pixel cho mọi phiên"). Không chạm Tecno KJ7, BG6, S24U, A50s. Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation, `topResumedActivity`), `ps` tiến trình lạ; trên Pixel `bemytester`, `loyalty.skyjoy.vn.stg` ở nền là bình thường, chỉ dừng và hỏi nếu một trong số đó ở **foreground** hoặc có instrumentation, hoặc màn hình khóa/hộp thoại sinh trắc (nhờ owner xử lý, **không tự thao tác**). Chỉ `force-stop com.mckimquyen.reader`; `run-as` không `--user`; sao lưu byte-exact Room (+`-shm`/`-wal`), `files/datastore/settings.preferences_pb`, `shared_prefs/*.xml`; không xóa DataStore; không `connected*` (dùng `adb -s <serial> shell am instrument` với `timeout`). Quảng cáo toàn màn hình khi mở app: chỉ Back, nhiều lần, kiểm `topResumedActivity` sau mỗi lần, không bấm "Continue to app"; không cài gì từ trang Play Store của quảng cáo. Chỉ chạy runner `com.mckimquyen.reader.test`.
- **Không Gemini, không key**: mọi thứ cục bộ. Kèo và cược mẫu để thử chèn bằng một instrumentation trợ giúp tạm **không đưa vào repo** (mẫu `OracleBetSeedTest`).
- Báo cáo `doc/task/done/ORACLE-03_automated-arbiter-agent_DONE.md`; task gốc xử lý theo owner chọn ở Task 8; cập nhật `doc/feature.md`. Không push, không PR, không `.idea`.

---

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `domain/oracle/OracleEvidencePolicy.kt` (mới) | Hằng số, `OracleEvidenceCandidate`, `select` (thuần) | 1 |
| `domain/oracle/OracleRemindedCodec.kt` (mới) | Mã hóa/giải mã và cắt tập "đã nhắc" (thuần) | 2 |
| `ui/ext/ExtDataStore.kt` (sửa) | `DataStoreKeys.OracleRemindedBets`, `Context.oracleRemindedBets`, `setOracleRemindedBets` | 2 |
| `domain/repository/OracleWalletDao.kt` (sửa) | `dueOpenBets(nowMillis)` (truy vấn, không đổi lược đồ) | 3 |
| `infrastructure/ai/OracleEvidenceFinder.kt` (mới) | Tìm bài liên quan cục bộ | 4 |
| `res/values*/strings.xml` ×38 (sửa) | 8 chuỗi `oracle_due_*` | 5 |
| `infrastructure/android/NotificationHelper.kt` (sửa) | Kênh `oracle_due`, `notifyOracleDue` | 6 |
| `infrastructure/ai/OracleDueReminder.kt` (mới), `domain/sv/SyncWorker.kt` (sửa) | Nhắc gộp, đánh dấu sau khi đăng; nối sync | 6 |
| `ui/page/oracle/OracleBetting.kt` (sửa), `ui/component/oracle/OracleBettingSection.kt` (sửa), `ui/page/common/HomeEntry.kt` (sửa) | Trạng thái bằng chứng và hiển thị | 7 |
| `androidTest/.../OracleEvidenceWidgetTest.kt` (mới) | Máy thật Pixel | 8 |
| tài liệu | Báo cáo, feature.md, spec | 8 |

**Thứ tự thực thi:** 1 → 2 → 3 → 4 → **5 (chuỗi 38 locale) → 6 → 7** (chuỗi trước thông báo và giao diện để `R.string.oracle_due_*` có sẵn mà parity không đỏ) → 8 (máy thật, full suite, báo cáo).

Quy ước lệnh test (mỗi lần chạy, **ở nền**):
```bash
rm -f /tmp/o03-<ten>.exit
(./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/o03-<ten>.log 2>&1; echo exit=$? > /tmp/o03-<ten>.exit) &
# chờ: until [ -f /tmp/o03-<ten>.exit ]; do sleep 10; done; cat /tmp/o03-<ten>.exit
```
rồi đọc `app/build/test-results/testDevDebugUnitTest/TEST-<FQCN>.xml` (`tests=`, `failures=`, `errors=`, thông điệp lỗi) **chỉ sau khi** `.exit` tồn tại và bằng `exit=0` (hoặc với lần đỏ có chủ đích: `exit=1` kèm `grep -E '^e: '` hoặc XML có `failures>0`).

---

### Task 1: `OracleEvidencePolicy` — chọn bằng chứng (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/oracle/OracleEvidencePolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/oracle/OracleEvidencePolicyTest.kt`

**Interfaces:**
- Produces:
  - `data class OracleEvidenceCandidate(val articleId: String, val title: String, val feedName: String, val dateMillis: Long, val score: Float)`.
  - `data class OracleEvidence(val articleId: String, val title: String, val feedName: String, val dateMillis: Long)`.
  - `object OracleEvidencePolicy`: `MAX_EVIDENCE: Int = 3`, `RECENT_SCAN_LIMIT: Int = 200`; `fun select(candidates: List<OracleEvidenceCandidate>, placedAt: Long, limit: Int = MAX_EVIDENCE): List<OracleEvidence>`.
- Quy tắc `select`: giữ ứng viên có `dateMillis >= placedAt` (bằng nhau thì **giữ**); loại trùng `articleId` (giữ bản điểm cao nhất, hòa thì bản mới hơn); sắp theo `score` giảm dần, hòa thì `dateMillis` giảm dần, hòa nữa thì `articleId` tăng dần (xác định, không phụ thuộc thứ tự đầu vào); cắt `limit`; `limit <= 0` trả rỗng; không mất tiêu đề hay tên nguồn.

- [ ] **Step 1: Viết test đỏ** (JUnit thường, không `org.json`)

```kotlin
package com.mckimquyen.reader.domain.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleEvidencePolicyTest {

    private val placedAt = 1_000L

    private fun c(id: String, date: Long, score: Float, title: String = "T-$id", feed: String = "F-$id") =
        OracleEvidenceCandidate(id, title, feed, date, score)

    private fun ids(list: List<OracleEvidence>) = list.map { it.articleId }

    @Test
    fun constants_areHardCodedNumbers() {
        assertEquals(3, OracleEvidencePolicy.MAX_EVIDENCE)
        assertEquals(200, OracleEvidencePolicy.RECENT_SCAN_LIMIT)
    }

    @Test
    fun emptyInput_isEmpty() {
        assertTrue(OracleEvidencePolicy.select(emptyList(), placedAt).isEmpty())
    }

    @Test
    fun onlyArticlesFromThePlacingMomentOn_areEvidence_andTheBoundaryIsKept() {
        val out = OracleEvidencePolicy.select(
            listOf(c("before", placedAt - 1, 0.9f), c("same", placedAt, 0.5f), c("after", placedAt + 1, 0.4f)),
            placedAt,
        )
        assertEquals(setOf("same", "after"), ids(out).toSet())
        assertTrue("an article older than the bet can never confirm it", "before" !in ids(out))
        assertTrue("an article published exactly when the bet was placed is kept", "same" in ids(out))
    }

    @Test
    fun orderedByScoreDescending() {
        val out = OracleEvidencePolicy.select(
            listOf(c("low", 2_000, 0.3f), c("high", 2_000, 0.9f), c("mid", 2_000, 0.6f)), placedAt,
        )
        assertEquals(listOf("high", "mid", "low"), ids(out))
    }

    @Test
    fun equalScore_newerFirst_thenByIdForADeterministicOrder() {
        val out = OracleEvidencePolicy.select(
            listOf(c("b", 2_000, 0.5f), c("a", 2_000, 0.5f), c("newer", 3_000, 0.5f)), placedAt,
        )
        assertEquals(listOf("newer", "a", "b"), ids(out))
    }

    @Test
    fun theOrderOfTheInputDoesNotChangeTheResult() {
        val list = listOf(c("a", 2_000, 0.5f), c("b", 2_500, 0.5f), c("c", 2_200, 0.8f), c("d", 2_100, 0.8f))
        assertEquals(ids(OracleEvidencePolicy.select(list, placedAt)), ids(OracleEvidencePolicy.select(list.reversed(), placedAt)))
    }

    @Test
    fun atMostThree_byDefault() {
        val many = (1..10).map { c("a$it", 2_000, it / 10f) }
        val out = OracleEvidencePolicy.select(many, placedAt)
        assertEquals(3, out.size)
        assertEquals(listOf("a10", "a9", "a8"), ids(out))
    }

    @Test
    fun theLimitIsHonoured_andNonPositiveLimitIsEmpty() {
        val many = (1..5).map { c("a$it", 2_000, it / 10f) }
        assertEquals(2, OracleEvidencePolicy.select(many, placedAt, limit = 2).size)
        assertTrue(OracleEvidencePolicy.select(many, placedAt, limit = 0).isEmpty())
        assertTrue(OracleEvidencePolicy.select(many, placedAt, limit = -1).isEmpty())
    }

    @Test
    fun duplicatesOfTheSameArticle_collapseToTheBestScore() {
        val out = OracleEvidencePolicy.select(
            listOf(c("x", 2_000, 0.4f), c("x", 2_000, 0.8f), c("y", 2_000, 0.5f)), placedAt,
        )
        assertEquals(listOf("x", "y"), ids(out))
    }

    @Test
    fun titleAndFeedNameSurvive() {
        val out = OracleEvidencePolicy.select(listOf(c("a", 2_000, 0.5f, title = "Hello", feed = "Wire")), placedAt)
        assertEquals("Hello", out.single().title)
        assertEquals("Wire", out.single().feedName)
        assertEquals(2_000L, out.single().dateMillis)
    }

    @Test
    fun whenNothingIsNewEnough_theResultIsEmpty_notFilledWithOldArticles() {
        val out = OracleEvidencePolicy.select(listOf(c("old1", 10, 0.99f), c("old2", 20, 0.98f)), placedAt)
        assertTrue(out.isEmpty())
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: OracleEvidencePolicy`, `OracleEvidenceCandidate`, `OracleEvidence`; đọc `grep -E '^e: '` trong `/tmp/o03-t1.log`).

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.oracle

data class OracleEvidenceCandidate(
    val articleId: String,
    val title: String,
    val feedName: String,
    val dateMillis: Long,
    val score: Float,
)

data class OracleEvidence(
    val articleId: String,
    val title: String,
    val feedName: String,
    val dateMillis: Long,
)

/**
 * Pure rules for which saved articles are offered as hints when a bet is due (ORACLE-03). Only an article published at
 * or after the moment the bet was placed can say anything about what happened afterwards.
 */
object OracleEvidencePolicy {
    const val MAX_EVIDENCE = 3
    const val RECENT_SCAN_LIMIT = 200

    fun select(candidates: List<OracleEvidenceCandidate>, placedAt: Long, limit: Int = MAX_EVIDENCE): List<OracleEvidence> {
        if (limit <= 0) return emptyList()
        return candidates
            .filter { it.dateMillis >= placedAt }
            .groupBy { it.articleId }
            .map { (_, sameArticle) -> sameArticle.maxWith(compareBy({ it.score }, { it.dateMillis })) }
            .sortedWith(compareByDescending<OracleEvidenceCandidate> { it.score }.thenByDescending { it.dateMillis }.thenBy { it.articleId })
            .take(limit)
            .map { OracleEvidence(it.articleId, it.title, it.feedName, it.dateMillis) }
    }
}
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (`OracleEvidencePolicyTest` 11 test, `failures=0`).

- [ ] **Step 5: Mutation (backup + `cmp`)** trên `OracleEvidencePolicy.kt`, test `com.mckimquyen.reader.domain.oracle.OracleEvidencePolicyTest`:
  - `const val MAX_EVIDENCE = 3` → `4` (số cứng đỏ); `RECENT_SCAN_LIMIT = 200` → `201`.
  - `.filter { it.dateMillis >= placedAt }` → `> placedAt` (đỏ ở biên) và → bỏ hẳn bộ lọc (đỏ ở test bài cũ).
  - `if (limit <= 0) return emptyList()` → bỏ dòng (đỏ ở `limit = 0`).
  - `.groupBy { it.articleId }` + `maxWith` → bỏ dedupe (đỏ ở test trùng).
  - `compareByDescending<OracleEvidenceCandidate> { it.score }` → `compareBy { it.score }` (đỏ thứ tự).
  - bỏ `.thenByDescending { it.dateMillis }` (đỏ ở "newer first").
  - bỏ `.thenBy { it.articleId }` (đỏ ở test xác định và đảo thứ tự đầu vào).
  - `.take(limit)` → `.take(limit + 1)` (đỏ ở cắt 3).

- [ ] **Step 6: Commit** (kiểm `exit=0` và XML): `feat(oracle-03): add the pure evidence selection rules`.

---

### Task 2: Tập "đã nhắc" — mã hóa thuần và khóa DataStore

Mã hóa là một chuỗi, các `articleId` nối bằng ký tự xuống dòng (`'\n'`; `articleId` của app có dạng `1$<uuid>` nên không chứa xuống dòng). Thứ tự trong chuỗi là thứ tự nhắc (**cũ nhất trước**), nên cắt trần là bỏ phần đầu.

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/oracle/OracleRemindedCodec.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` (cạnh `DeepDiveGeminiConsent`/`OracleClaimsGeminiConsent`, thêm khóa và hai hàm tiện ích)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/oracle/OracleRemindedCodecTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/ext/OracleRemindedBetsDataStoreTest.kt`

**Interfaces:**
- Produces:
  - `object OracleRemindedCodec`: `REMINDED_CAP: Int = 200`; `fun decode(raw: String?): List<String>` (bỏ dòng rỗng, bỏ khoảng trắng đầu/cuối, loại trùng giữ lần đầu); `fun encode(ids: List<String>): String`; `fun append(existing: List<String>, newIds: List<String>): List<String>` (nối, loại trùng giữ vị trí cũ, **cắt còn `REMINDED_CAP` phần mới nhất**); `fun prune(existing: List<String>, stillOpen: Set<String>): List<String>` (chỉ giữ id còn cược mở).
  - `DataStoreKeys.OracleRemindedBets` (`String`, khóa `oracle_reminded_bets`), `val Context.oracleRemindedBets: String?`, `suspend fun Context.setOracleRemindedBets(encoded: String)`.

- [ ] **Step 1: Viết test đỏ — codec** (JUnit thường)

```kotlin
package com.mckimquyen.reader.domain.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleRemindedCodecTest {

    @Test fun capIsAHardCodedNumber() = assertEquals(200, OracleRemindedCodec.REMINDED_CAP)

    @Test fun decode_nullOrBlank_isEmpty() {
        assertTrue(OracleRemindedCodec.decode(null).isEmpty())
        assertTrue(OracleRemindedCodec.decode("").isEmpty())
        assertTrue(OracleRemindedCodec.decode("\n \n").isEmpty())
    }

    @Test fun roundTrip_keepsOrderAndIds() {
        val ids = listOf("1\$aaa", "1\$bbb", "2\$ccc")
        assertEquals(ids, OracleRemindedCodec.decode(OracleRemindedCodec.encode(ids)))
    }

    @Test fun decode_trimsAndDropsDuplicates_keepingTheFirst() {
        assertEquals(listOf("a", "b"), OracleRemindedCodec.decode(" a \nb\na\n\nb"))
    }

    @Test fun append_addsNewIdsAtTheEnd_withoutDuplicates() {
        assertEquals(listOf("a", "b", "c"), OracleRemindedCodec.append(listOf("a", "b"), listOf("b", "c")))
    }

    @Test fun append_capsToTheNewestTwoHundred_droppingTheOldest() {
        val existing = (1..200).map { "old$it" }
        val out = OracleRemindedCodec.append(existing, listOf("new1", "new2"))
        assertEquals(200, out.size)
        assertEquals("old3", out.first())
        assertEquals("new2", out.last())
        assertTrue("old1" !in out && "old2" !in out)
    }

    @Test fun append_exactlyAtTheCap_dropsNothing() {
        val existing = (1..199).map { "old$it" }
        val out = OracleRemindedCodec.append(existing, listOf("new1"))
        assertEquals(200, out.size)
        assertEquals("old1", out.first())
    }

    @Test fun prune_keepsOnlyIdsThatStillHaveAnOpenBet() {
        assertEquals(listOf("b"), OracleRemindedCodec.prune(listOf("a", "b", "c"), setOf("b", "z")))
        assertTrue(OracleRemindedCodec.prune(listOf("a"), emptySet()).isEmpty())
    }

    @Test fun encode_emptyIsEmpty() = assertEquals("", OracleRemindedCodec.encode(emptyList()))
}
```

- [ ] **Step 2: Viết test đỏ — DataStore** (Robolectric, khuôn `OracleClaimsConsentTest`)

```kotlin
package com.mckimquyen.reader.ui.ext

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleRemindedBetsDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverWritten_isNull() = assertEquals(null, context.oracleRemindedBets)

    @Test
    fun writtenValue_roundTrips(): Unit = runBlocking {
        context.setOracleRemindedBets("a\nb")
        assertEquals("a\nb", context.oracleRemindedBets)
    }

    @Test
    fun keyNameIsHardCoded_andDistinctFromTheConsentKeys() {
        val name = DataStoreKeys.OracleRemindedBets.key.name
        assertEquals("oracle_reminded_bets", name)
        assertNotEquals(DataStoreKeys.OracleClaimsGeminiConsent.key.name, name)
    }

    @Test
    fun doesNotTouchTheOracleConsent(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        context.setOracleRemindedBets("x")
        assertEquals(true, context.oracleClaimsGeminiConsent)
    }
}
```

- [ ] **Step 3: Chạy hai lớp test, xác nhận đỏ** (`Unresolved reference: OracleRemindedCodec`, `oracleRemindedBets`, `setOracleRemindedBets`, `OracleRemindedBets`).

- [ ] **Step 4: Code tối thiểu**

`domain/oracle/OracleRemindedCodec.kt`:

```kotlin
package com.mckimquyen.reader.domain.oracle

/**
 * The set of bets the user was already reminded about (ORACLE-03), stored as one string. Oldest first, so trimming to the
 * cap drops the front. Article ids never contain a line break, which makes it a safe separator.
 */
object OracleRemindedCodec {
    const val REMINDED_CAP = 200
    private const val SEPARATOR = "\n"

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun encode(ids: List<String>): String = ids.joinToString(SEPARATOR)

    fun append(existing: List<String>, newIds: List<String>): List<String> =
        (existing + newIds).distinct().takeLast(REMINDED_CAP)

    fun prune(existing: List<String>, stillOpen: Set<String>): List<String> = existing.filter { it in stillOpen }
}
```

`ExtDataStore.kt`, ngay sau `setOracleClaimsGeminiConsent`:

```kotlin
/** ORACLE-03: the bets the user was already reminded about, newest last; absent until the first reminder. */
val Context.oracleRemindedBets: String?
    get() = this.dataStore.get(DataStoreKeys.OracleRemindedBets)

suspend fun Context.setOracleRemindedBets(encoded: String) {
    this.dataStore.put(DataStoreKeys.OracleRemindedBets, encoded)
}
```

và trong `sealed class DataStoreKeys`, sau `OracleClaimsGeminiConsent`:

```kotlin
    /** ORACLE-03: ids of the bets already announced as due (see OracleRemindedCodec). */
    object OracleRemindedBets : DataStoreKeys<String>() {
        override val key: Preferences.Key<String>
            get() = stringPreferencesKey("oracle_reminded_bets")
    }
```

- [ ] **Step 5: Chạy lại, xác nhận xanh** (`OracleRemindedCodecTest` 9, `OracleRemindedBetsDataStoreTest` 4).

- [ ] **Step 6: Mutation (backup + `cmp`)**: `REMINDED_CAP = 200` → `201`; `.takeLast(REMINDED_CAP)` → `.take(REMINDED_CAP)` (đỏ: giữ phần cũ thay vì mới); bỏ `.distinct()` ở `append` (đỏ); bỏ `.distinct()` ở `decode` (đỏ); bỏ `.map { it.trim() }` (đỏ); `prune` đổi `in` thành `!in` (đỏ); đổi tên khóa `oracle_reminded_bets` thành `oracle_claims_gemini_consent` (đỏ).

- [ ] **Step 7: Commit** `feat(oracle-03): add the reminded-bets codec and its DataStore key`.

---

### Task 3: `OracleWalletDao.dueOpenBets` — truy vấn cược đã đến hạn (không đổi lược đồ)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/OracleWalletDao.kt` (thêm **một** truy vấn công khai cạnh `betFor`)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/OracleWalletDaoDueTest.kt`

**Interfaces:**
- Consumes: `OracleBet`, `OracleBetStatus` (ORACLE-02).
- Produces: `abstract suspend fun dueOpenBets(nowMillis: Long): List<OracleBet>` — cược có `status = 'OPEN'` và `expiresAt <= :nowMillis`, sắp theo `expiresAt` tăng dần rồi `id` tăng dần (xác định). Không sửa bảng, không sửa `22.json`.

- [ ] **Step 1: Viết test đỏ** (Room in-memory; chỉ cần `OracleWalletDao`, vì cược không có khóa ngoại)

```kotlin
package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleWalletDaoDueTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao
    private val day = 86_400_000L
    private val placedAt = 100 * day

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
    }

    @After
    fun tearDown() = db.close()

    private fun bet(article: String, expiresAt: Long, stake: Int = 50) = runBlocking {
        dao.placeBet(article, "Q-$article?", expiresAt, OracleBetSide.YES, stake, placedAt)
    }

    private fun due(now: Long) = runBlocking { dao.dueOpenBets(now) }.map { it.articleId }

    @Test
    fun noBets_isEmpty() = assertTrue(due(placedAt + 10 * day).isEmpty())

    @Test
    fun aBetIsDueAtTheDeadlineExactly_notBefore() {
        val deadline = placedAt + 5 * day
        bet("a", deadline)
        assertTrue("one millisecond early", due(deadline - 1).isEmpty())
        assertEquals(listOf("a"), due(deadline))
        assertEquals(listOf("a"), due(deadline + 1))
    }

    @Test
    fun settledAndRefundedBets_areNeverDue() {
        bet("won", placedAt + day); bet("refunded", placedAt + day); bet("open", placedAt + day)
        runBlocking {
            dao.settleBet("won", OracleOutcome.YES, placedAt + day)
            dao.settleBet("refunded", OracleOutcome.UNCLEAR, placedAt + day)
        }
        assertEquals(listOf("open"), due(placedAt + 2 * day))
    }

    @Test
    fun aBetThatIsNotYetDue_isLeftOut() {
        bet("soon", placedAt + 2 * day); bet("later", placedAt + 9 * day)
        assertEquals(listOf("soon"), due(placedAt + 3 * day))
    }

    @Test
    fun orderedByDeadline_thenById() {
        bet("late", placedAt + 4 * day); bet("early", placedAt + 2 * day); bet("tie1", placedAt + 3 * day); bet("tie2", placedAt + 3 * day)
        assertEquals(listOf("early", "tie1", "tie2", "late"), due(placedAt + 10 * day))
    }

    @Test
    fun theQueryOnlyReads_itChangesNoBetAndNoBalance() {
        bet("a", placedAt + day, stake = 100)
        val before = runBlocking { dao.wallet()?.balance }
        due(placedAt + 5 * day)
        assertEquals(before, runBlocking { dao.wallet()?.balance })
        assertEquals(com.mckimquyen.reader.domain.model.oracle.OracleBetStatus.OPEN, runBlocking { dao.betFor("a") }!!.status)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: dueOpenBets`).

- [ ] **Step 3: Code tối thiểu** — trong `OracleWalletDao`, ngay sau `betFor`:

```kotlin
    /** Open bets whose deadline has passed, oldest deadline first. Read-only; used to remind the user (ORACLE-03). */
    @Query("SELECT * FROM oracle_bet WHERE status = 'OPEN' AND expiresAt <= :nowMillis ORDER BY expiresAt ASC, id ASC")
    abstract suspend fun dueOpenBets(nowMillis: Long): List<OracleBet>
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (6 test) và chạy lại `OracleWalletDaoTest` (25 test của ORACLE-02) để chắc không hồi quy. Kiểm `git diff --stat -- app/schemas` rỗng (không đổi lược đồ).

- [ ] **Step 5: Mutation (backup + `cmp`)** trên `OracleWalletDao.kt`: `expiresAt <= :nowMillis` → `expiresAt < :nowMillis` (đỏ ở biên); `status = 'OPEN' AND` → bỏ điều kiện (đỏ: cược đã chốt lọt vào); `ORDER BY expiresAt ASC, id ASC` → `ORDER BY expiresAt DESC, id ASC` (đỏ thứ tự); → bỏ `, id ASC` (đỏ ở hòa; nếu **sống sót** vì SQLite trả theo id khi hòa, thêm test chèn theo thứ tự ngược rồi chạy lại).

- [ ] **Step 6: Commit** `feat(oracle-03): add a read-only query for open bets past their deadline`.

---

### Task 4: `OracleEvidenceFinder` — tìm bài liên quan cục bộ (không AI)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleEvidenceFinder.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/OracleEvidenceFinderTest.kt`

**Interfaces:**
- Consumes: `ArticleDao.queryRecentArticleListWithFeed(accountId: Int, limit: Int): List<ArticleListItemWithFeed>` (chỉ cột danh sách), `ArticleListItemWithFeed.toArticleWithFeed()`, `SemanticSearchEngine.rank(query: String, articles: List<ArticleWithFeed>, minScoreThreshold: Float, limit: Int): List<SemanticSearchResult>` (`SemanticSearchResult.articleWithFeed`, `.score`), `OracleEvidencePolicy` (Task 1), `OracleBet` (ORACLE-02).
- Produces: `@Singleton class OracleEvidenceFinder @Inject constructor(articleDao: ArticleDao, searchEngine: SemanticSearchEngine)` với `suspend fun find(accountId: Int, bet: OracleBet): List<OracleEvidence>`. Quy trình: lấy `RECENT_SCAN_LIMIT` bài mới nhất → `rank(bet.question, bài, DEFAULT_MIN_SCORE_THRESHOLD, limit = RECENT_SCAN_LIMIT)` → ánh xạ sang `OracleEvidenceCandidate(id, title, feed.name, date.time, score)` → `OracleEvidencePolicy.select(…, bet.placedAt)`. Không dùng mạng, Gemini hay `AiRequestGateway`; không đọc thân bài; lỗi (ngoại lệ, trừ `CancellationException`) trả **danh sách rỗng** và chỉ log loại exception.

- [ ] **Step 1: Viết test đỏ** (Robolectric + Room in-memory + `SemanticSearchEngine` thật; câu hỏi và bài dùng từ khóa **trùng lặp rõ ràng** để điểm vượt ngưỡng một cách đoán trước được)

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleEvidenceFinderTest {

    private lateinit var db: AndroidDatabase
    private lateinit var finder: OracleEvidenceFinder
    private val day = 86_400_000L
    private val placedAt = 100 * day
    private val question = "Will the ceasefire hold until the end of next month?"

    @Before
    fun setUp() = runBlocking {
        ShadowLog.clear()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f", name = "Wire News", url = "https://e.com/f", groupId = "g", accountId = 1))
        db.feedDao().insert(Feed(id = "f2", name = "Other", url = "https://e.com/f2", groupId = "g2", accountId = 2))
        finder = OracleEvidenceFinder(db.articleDao(), SemanticSearchEngine(db.articleEmbeddingDao()))
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, title: String, date: Long, desc: String = "", account: Int = 1) = Article(
        id = id, date = Date(date), title = title, rawDescription = "RAW-SECRET-$id", shortDescription = desc,
        link = "https://secret.example/$id", feedId = if (account == 1) "f" else "f2", accountId = account,
    )

    private fun put(vararg a: Article) = runBlocking { db.articleDao().insertOnConflictIgnore(*a) }

    private fun bet(placed: Long = placedAt) = OracleBet(
        id = 1, articleId = "bet-article", question = question, expiresAt = placed + day, side = OracleBetSide.YES,
        stake = 100, status = OracleBetStatus.OPEN, placedAt = placed,
    )

    private fun find(b: OracleBet = bet()) = runBlocking { finder.find(1, b) }

    @Test
    fun relatedArticleAfterTheBet_isFound_withItsFeedNameAndDate() {
        put(article("a", "Ceasefire holds as talks continue next month", placedAt + 2 * day, "The ceasefire hold continues"))
        val out = find()
        assertEquals(listOf("a"), out.map { it.articleId })
        assertEquals("Wire News", out.single().feedName)
        assertEquals(placedAt + 2 * day, out.single().dateMillis)
        assertEquals("Ceasefire holds as talks continue next month", out.single().title)
    }

    @Test
    fun anArticleOlderThanTheBet_isNeverEvidence() {
        put(article("old", "Ceasefire hold expected until the end of next month", placedAt - day, "ceasefire hold"))
        assertTrue(find().isEmpty())
    }

    @Test
    fun anUnrelatedArticle_isNotEvidence() {
        put(article("x", "Recipe for a vegetarian lasagna with spinach", placedAt + day, "Layer the pasta and cheese"))
        assertTrue(find().isEmpty())
    }

    @Test
    fun atMostThree_evenWithManyRelatedArticles() {
        put(*(1..8).map { article("r$it", "Ceasefire hold update number $it next month", placedAt + it * day, "ceasefire hold") }.toTypedArray())
        assertEquals(3, find().size)
    }

    @Test
    fun onlyTheBetsAccount_isSearched() {
        put(article("mine", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 1))
        put(article("theirs", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 2))
        assertEquals(listOf("mine"), find().map { it.articleId })
    }

    @Test
    fun nothingSaved_isEmpty() = assertTrue(find().isEmpty())

    @Test
    fun theScanIsLimitedToTheTwoHundredNewestArticles() {
        // The single related article is the 201st newest, so it must not be seen.
        put(*(1..200).map { article("filler$it", "Gardening tips for tomatoes part $it", placedAt + 10 * day + it, "soil and water") }.toTypedArray())
        put(article("buried", "Ceasefire hold update next month", placedAt + day, "ceasefire hold"))
        assertTrue(find().isEmpty())
    }

    @Test
    fun aFailureInTheSearch_returnsEmpty_andLogsOnlyTheExceptionType() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryRecentArticleListWithFeed(any(), any()) } throws IllegalStateException("SecretQuestionText ceasefire")
        val failing = OracleEvidenceFinder(dao, SemanticSearchEngine(db.articleEmbeddingDao()))
        assertTrue(runBlocking { failing.find(1, bet()) }.isEmpty())
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertTrue(log.contains("IllegalStateException"))
        assertFalse("the log must not carry the question", log.contains("SecretQuestionText"))
    }

    @Test
    fun cancellation_isRethrown() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryRecentArticleListWithFeed(any(), any()) } throws CancellationException("cancelled")
        val cancelling = OracleEvidenceFinder(dao, SemanticSearchEngine(db.articleEmbeddingDao()))
        val thrown = runCatching { runBlocking { cancelling.find(1, bet()) } }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    // --- source: no network, no Gemini, no article bodies

    private val source = File("src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleEvidenceFinder.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    @Test
    fun sourceNeverReachesForTheNetworkGeminiOrBodies() {
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "rawDescription", "fullContent", "queryArticleBody", "http").forEach {
            assertFalse("OracleEvidenceFinder must not mention '$it'", source.contains(it, ignoreCase = true))
        }
        assertTrue(source.contains("queryRecentArticleListWithFeed"))
    }
}
```

Ghi chú: các từ khóa bài và câu hỏi được chọn để chắc chắn vượt ngưỡng `0.22` (chia sẻ nhiều token "ceasefire", "hold", "next", "month"); nếu một test "tìm thấy" đỏ vì điểm dưới ngưỡng, **tăng độ trùng từ của bài mẫu**, không hạ ngưỡng trong mã sản xuất. Test `anUnrelatedArticle...` và `theScanIsLimited...` phải đỏ/xanh đúng vì lý do ngưỡng/giới hạn, không vì dữ liệu mẫu quá ít.

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: OracleEvidenceFinder`).

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.util.Log
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.domain.oracle.OracleEvidenceCandidate
import com.mckimquyen.reader.domain.oracle.OracleEvidencePolicy
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds up to [OracleEvidencePolicy.MAX_EVIDENCE] saved articles that may say how a due bet turned out (ORACLE-03). Purely
 * local: it ranks the newest saved articles (list columns only, never a body) against the bet's own question with the
 * on-device search engine. Nothing leaves the device, and the result is a hint, not a verdict.
 */
@Singleton
class OracleEvidenceFinder @Inject constructor(
    private val articleDao: ArticleDao,
    private val searchEngine: SemanticSearchEngine,
) {

    suspend fun find(accountId: Int, bet: OracleBet): List<OracleEvidence> = try {
        val recent = articleDao.queryRecentArticleListWithFeed(accountId, OracleEvidencePolicy.RECENT_SCAN_LIMIT)
            .map { it.toArticleWithFeed() }
        val ranked = searchEngine.rank(
            query = bet.question,
            articles = recent,
            minScoreThreshold = SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD,
            limit = OracleEvidencePolicy.RECENT_SCAN_LIMIT,
        )
        OracleEvidencePolicy.select(
            candidates = ranked.map {
                OracleEvidenceCandidate(
                    articleId = it.articleWithFeed.article.id,
                    title = it.articleWithFeed.article.title,
                    feedName = it.articleWithFeed.feed.name,
                    dateMillis = it.articleWithFeed.article.date.time,
                    score = it.score,
                )
            },
            placedAt = bet.placedAt,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Only the exception type: a message could carry the question.
        Log.w(TAG, "Evidence search failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    private companion object {
        const val TAG = "OracleEvidenceFinder"
    }
}
```

- [ ] **Step 4: Biên dịch sạch Hilt** (thêm lớp `@Singleton @Inject` mới): lệnh `--rerun-tasks` ở Global Constraints, chờ `.exit`, phải `exit=0`. Rồi chạy `OracleEvidenceFinderTest` xanh (10 test).

- [ ] **Step 5: Mutation (backup + `cmp`)** trên `OracleEvidenceFinder.kt`: `OracleEvidencePolicy.RECENT_SCAN_LIMIT)` ở lời gọi `queryRecentArticleListWithFeed` → `1000` (đỏ ở test giới hạn 200); `placedAt = bet.placedAt` → `placedAt = 0L` (đỏ ở bài cũ hơn cược); `bet.question` → `bet.articleId` (đỏ: không tìm thấy gì); `SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD` → `0f` (đỏ ở bài không liên quan); bỏ `catch (e: CancellationException) { throw e }` (đỏ); `${e.javaClass.simpleName}` → `${e.message}` (đỏ ở test log); trả `emptyList()` ở nhánh lỗi → `throw e` (đỏ); đổi `accountId` thành `1` cứng trong lời gọi DAO (đỏ ở test tài khoản); thêm chữ `fullContent` vào mã (đỏ ở test nguồn).

- [ ] **Step 6: Commit** `feat(oracle-03): find related saved articles for a due bet, locally`.

---

### Task 5: Chuỗi `oracle_due_*` trong cả 38 locale (làm TRƯỚC Task 6 và 7)

**Files:**
- Modify: `app/src/main/res/values*/strings.xml` ×38 (một script all-or-none)
- Create: `/tmp/o03_add_strings.py` (khuôn `/tmp/o02_add_strings.py`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/OracleDueStringsTest.kt`

**Interfaces:**
- Produces: 8 khóa mới (không đổi khóa cũ). Thư mục locale thật lấy bằng `glob("app/src/main/res/values*/strings.xml")` (ví dụ `values-de-rDE`, `values-fr-rFR`, `values-es-rES`, `values-ja`, `values-vi`), **không** đoán tên.

| Khóa | EN | VI |
|---|---|---|
| `oracle_due_channel_name` | Prediction reminders | Nhắc kèo dự đoán |
| `oracle_due_channel_desc` | Tells you when a prediction you bet on has reached its date | Báo khi một kèo dự đoán bạn đã cược đến ngày đáo hạn |
| `oracle_due_notification_title` | A prediction has reached its date | Một kèo dự đoán đã đến hạn |
| `oracle_due_notification_title_many` | Predictions have reached their date | Các kèo dự đoán đã đến hạn |
| `oracle_due_notification_body_many` | %1$s predictions are waiting for your result. | Có %1$s kèo đang chờ bạn báo kết quả. |
| `oracle_due_evidence_title` | Related articles in your news | Bài liên quan trong tin của bạn |
| `oracle_due_evidence_none` | No related article found yet. | Chưa thấy bài nào liên quan. |
| `oracle_due_evidence_hint` | Hints from your saved news; they may be wrong. You decide. | Gợi ý từ tin đã lưu, chưa chắc đúng. Bạn tự quyết định. |

Quy ước: nội dung thông báo **một** kèo là chính câu hỏi của cược (không cần chuỗi); thông báo nhiều kèo dùng `oracle_due_notification_title_many` và `oracle_due_notification_body_many` (một `%1$s` là số, truyền chuỗi đã định dạng theo locale). Không có `%d`. `oracle_due_notification_body_many` có **đúng một** `%1$s`, các khóa khác **không** có chỗ giữ chỗ.

- [ ] **Step 1: Viết test đỏ `OracleDueStringsTest`**

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleDueStringsTest {

    private val keys = listOf(
        "oracle_due_channel_name", "oracle_due_channel_desc", "oracle_due_notification_title",
        "oracle_due_notification_title_many", "oracle_due_notification_body_many", "oracle_due_evidence_title",
        "oracle_due_evidence_none", "oracle_due_evidence_hint",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun thereAreExactlyEightKeys() = assertEquals(8, keys.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun onlyTheManyBodyHasAPlaceholder_andItIsExactlyOneOrdinalString() {
        for (f in files) for (k in keys) {
            val v = value(f, k)!!
            val expected = if (k == "oracle_due_notification_body_many") 1 else 0
            assertEquals("${f.parentFile.name} $k placeholders", expected, Regex("%\\d+\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k must not use %d or %s", 0, Regex("%\\d*d|%s").findAll(v).count())
        }
    }

    @Test fun englishAndVietnameseSayItIsAHint_andThatTheUserDecides() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        assertTrue(value(en, "oracle_due_evidence_hint")!!.contains("may be wrong") && value(en, "oracle_due_evidence_hint")!!.contains("You decide"))
        assertTrue(value(vi, "oracle_due_evidence_hint")!!.contains("chưa chắc đúng") && value(vi, "oracle_due_evidence_hint")!!.contains("tự quyết định"))
    }

    @Test fun noStringPromisesAnAutomaticVerdict() {
        for (f in files) for (k in keys) {
            val v = value(f, k)!!
            assertFalse("${f.parentFile.name} $k must not claim an automatic result", v.contains("automatically", ignoreCase = true) && v.contains("won", ignoreCase = true))
        }
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (khóa thiếu ở mọi locale; đọc XML: `failures>0`).

- [ ] **Step 3: Viết `/tmp/o03_add_strings.py`** (all-or-none): kiểm đủ 38 file, kiểm **chưa file nào** có khóa mới, kiểm mỗi file có đúng một `</resources>`, rồi mới ghi; thoát `'` thành `\'`; `assert len(KEYS) == 8`. Hai dict `EN`/`VI` đủ 8 khóa như bảng:

```python
import glob
EN = {
    "oracle_due_channel_name": "Prediction reminders",
    "oracle_due_channel_desc": "Tells you when a prediction you bet on has reached its date",
    "oracle_due_notification_title": "A prediction has reached its date",
    "oracle_due_notification_title_many": "Predictions have reached their date",
    "oracle_due_notification_body_many": "%1$s predictions are waiting for your result.",
    "oracle_due_evidence_title": "Related articles in your news",
    "oracle_due_evidence_none": "No related article found yet.",
    "oracle_due_evidence_hint": "Hints from your saved news; they may be wrong. You decide.",
}
VI = {
    "oracle_due_channel_name": "Nhắc kèo dự đoán",
    "oracle_due_channel_desc": "Báo khi một kèo dự đoán bạn đã cược đến ngày đáo hạn",
    "oracle_due_notification_title": "Một kèo dự đoán đã đến hạn",
    "oracle_due_notification_title_many": "Các kèo dự đoán đã đến hạn",
    "oracle_due_notification_body_many": "Có %1$s kèo đang chờ bạn báo kết quả.",
    "oracle_due_evidence_title": "Bài liên quan trong tin của bạn",
    "oracle_due_evidence_none": "Chưa thấy bài nào liên quan.",
    "oracle_due_evidence_hint": "Gợi ý từ tin đã lưu, chưa chắc đúng. Bạn tự quyết định.",
}
KEYS = list(EN.keys())
assert len(KEYS) == 8 and set(KEYS) == set(VI.keys())
files = sorted(glob.glob("app/src/main/res/values*/strings.xml"))
assert len(files) == 38, len(files)
esc = lambda t: t.replace("'", "\\'")
plan = []
for f in files:
    src = open(f, encoding="utf-8").read()
    assert not any(f'name="{k}"' in src for k in KEYS), f"đã có khoá ở {f}"
    assert src.count("</resources>") == 1, f
    table = VI if f.endswith("values-vi/strings.xml") else EN
    block = "".join(f'    <string name="{k}">{esc(table[k])}</string>\n' for k in KEYS)
    plan.append((f, src.replace("</resources>", block + "</resources>")))
for f, out in plan:
    open(f, "w", encoding="utf-8").write(out)
print("ok", len(plan))
```

Chạy từ thư mục gốc repo: `python3 /tmp/o03_add_strings.py`; kỳ vọng `ok 38` (đầu ra có thể bị nuốt: xác nhận lại bằng `git status --short | grep -c strings.xml` = 38 và `git diff --shortstat -- app/src/main/res` = `38 files changed, 304 insertions(+)`, tức 38 × 8).

- [ ] **Step 4: Chạy** `OracleDueStringsTest`, `StringResourceParityTest` xanh (chờ `.exit`, đọc XML).

- [ ] **Step 5: Mutation (backup + `cmp`)**, dùng thư mục locale thật: xóa `oracle_due_evidence_none` khỏi `values-de-rDE/strings.xml` (đỏ); đổi `%1$s` thành `%s` ở `oracle_due_notification_body_many` của `values-fr-rFR` (đỏ); thêm `%1$s` vào `oracle_due_evidence_title` của `values-ja` (đỏ); bỏ `You decide` khỏi `oracle_due_evidence_hint` EN (đỏ).

- [ ] **Step 6: Commit** `feat(oracle-03): add the reminder strings in 38 locales`.

---

### Task 6: Kênh `oracle_due`, `OracleDueReminder` và nối `SyncWorker`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt` (kênh mới trong khối `createNotificationChannel`, hằng `ORACLE_DUE_CHANNEL_ID`/`ORACLE_DUE_NOTIFICATION_ID`, hàm `notifyOracleDue`)
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleDueReminder.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt` (thêm tham số constructor `oracleDueReminder`, gọi trong `runCatching` **sau** `OracleClaimExtractor`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/android/NotificationHelperOracleDueTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/OracleDueReminderTest.kt`, `app/src/test/java/com/mckimquyen/reader/domain/sv/OracleDueSyncWiringSourceTest.kt`

**Interfaces:**
- Consumes: `OracleWalletDao.dueOpenBets(nowMillis)` (Task 3), `OracleRemindedCodec`, `Context.oracleRemindedBets`/`setOracleRemindedBets` (Task 2), chuỗi `oracle_due_*` (Task 5), `NotificationHelper.canPostNotifications()`.
- Produces:
  - `NotificationHelper.ORACLE_DUE_CHANNEL_ID = "oracle_due_channel"`, `ORACLE_DUE_NOTIFICATION_ID = 9955`, `fun notifyOracleDue(title: String, body: String)` (mở `MainActivity`, `PendingIntent.FLAG_IMMUTABLE`, `setAutoCancel(true)`, **một** id cố định nên lần đăng sau thay lần trước, không chồng).
  - `open class OracleDueReminder @Inject constructor(@ApplicationContext context: Context, walletDao: OracleWalletDao, notificationHelper: NotificationHelper)` (`@Singleton`) với `suspend fun remindDue(): Int` (số thông báo đã đăng, 0 hoặc 1), `protected open fun nowMillis(): Long`, `protected open suspend fun readReminded(): List<String>`, `protected open suspend fun writeReminded(ids: List<String>)` (mặc định đọc/ghi DataStore qua `Context`; test ghi đè bằng bộ nhớ để khỏi phụ thuộc DataStore).

Quy tắc `remindDue()`:
1. `due = walletDao.dueOpenBets(now)`; `reminded = readReminded()`.
2. **Dọn:** `dueIds = due.map { articleId }.toSet()`; `pruned = reminded.filter { it in dueIds }`. Một id "đã nhắc" chỉ có nghĩa khi cược đó **vẫn còn `OPEN` và đã đến hạn**, tức vẫn nằm trong `due` (`dueOpenBets` chỉ trả cược `OPEN` đã đến hạn); cược đã chốt hoặc đã hoàn thì rơi khỏi `due` nên marker bị bỏ.
3. `fresh = due.filter { it.articleId !in reminded }` (so với `reminded` **trước khi** dọn).
4. `fresh.isEmpty()` → ghi lại `pruned` (nếu đổi) và trả `0`.
5. `!notificationHelper.canPostNotifications()` → **không đăng, không đánh dấu** (không ghi gì thêm), trả `0`.
6. Đăng **một** thông báo: `fresh.size == 1` → `title = oracle_due_notification_title`, `body = fresh.first().question`; ngược lại `title = oracle_due_notification_title_many`, `body = oracle_due_notification_body_many` với số `fresh.size` định dạng theo locale. **Không** đưa số điểm vào chuỗi.
7. **Sau khi đăng**, ghi `OracleRemindedCodec.append(pruned, fresh.map { articleId })`.
8. `CancellationException` ném lại; ngoại lệ khác: log **chỉ tên lớp**, trả `0`, **không ghi** gì (để lần sau thử lại).

- [ ] **Step 1: Viết test đỏ — kênh và thông báo** (khuôn `NotificationHelperRpgDecayTest`)

```kotlin
package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperOracleDueTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    @Test fun constants_areHardCoded() {
        assertEquals("oracle_due_channel", NotificationHelper.ORACLE_DUE_CHANNEL_ID)
        assertEquals(9955, NotificationHelper.ORACLE_DUE_NOTIFICATION_ID)
    }

    @Test fun theChannelExists_withTheLocalisedNameAndDescription() {
        val channel = manager.getNotificationChannel(NotificationHelper.ORACLE_DUE_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(context.getString(com.mckimquyen.reader.R.string.oracle_due_channel_name), channel.name.toString())
        assertEquals(context.getString(com.mckimquyen.reader.R.string.oracle_due_channel_desc), channel.description)
    }

    @Test fun notifyOracleDue_postsTitleAndBody_onItsOwnChannel() {
        helper.notifyOracleDue("Title", "Will it ship?")
        val n = shadowOf(manager).getNotification(NotificationHelper.ORACLE_DUE_NOTIFICATION_ID)
        assertEquals("Title", n.extras.getCharSequence("android.title").toString())
        assertEquals("Will it ship?", n.extras.getCharSequence("android.text").toString())
        assertEquals(NotificationHelper.ORACLE_DUE_CHANNEL_ID, n.channelId)
    }

    @Test fun aSecondPost_replacesTheFirst_insteadOfStacking() {
        helper.notifyOracleDue("T1", "B1")
        helper.notifyOracleDue("T2", "B2")
        val active = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.ORACLE_DUE_CHANNEL_ID }
        assertEquals(1, active.size)
        assertEquals("B2", active.single().extras.getCharSequence("android.text").toString())
    }

    @Test fun tappingIt_opensTheApp_withAnImmutableIntent() {
        helper.notifyOracleDue("T", "B")
        val pending = shadowOf(manager).getNotification(NotificationHelper.ORACLE_DUE_NOTIFICATION_ID).contentIntent
        assertTrue(shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals("com.mckimquyen.reader.infrastructure.android.MainActivity", shadowOf(pending).savedIntent.component?.className)
    }

    @Test fun notificationsDisabled_doesNotPostOrCrash() {
        shadowOf(manager).setNotificationsEnabled(false)
        helper.notifyOracleDue("T", "B")
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test fun postingDoesNotTouchTheOtherChannelsNotifications() {
        helper.notifyRpgDecay("RPG", "decay")
        helper.notifyOracleDue("T", "B")
        assertNotNull(shadowOf(manager).getNotification(NotificationHelper.RPG_DECAY_NOTIFICATION_ID))
        assertNull(shadowOf(manager).getNotification(NotificationHelper.ZEN_DAILY_EDITION_NOTIFICATION_ID))
    }
}
```

- [ ] **Step 2: Viết test đỏ — `OracleDueReminderTest`** (Robolectric + Room in-memory; `NotificationHelper` **thật** để kiểm thông báo đã đăng; tập "đã nhắc" ghi đè bằng bộ nhớ)

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleDueReminderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao
    private lateinit var helper: NotificationHelper
    private val day = 86_400_000L
    private val placedAt = 100 * day
    private var now = placedAt + 10 * day
    private var stored: List<String> = emptyList()
    private var writes = 0

    private fun reminder(walletDao: OracleWalletDao = dao) = object : OracleDueReminder(context, walletDao, helper) {
        override fun nowMillis() = now
        override suspend fun readReminded() = stored
        override suspend fun writeReminded(ids: List<String>) { stored = ids; writes++ }
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
        helper = NotificationHelper(context)
    }

    @After
    fun tearDown() = db.close()

    private fun bet(article: String, expiresAt: Long, question: String = "Q-$article?", stake: Int = 500) = runBlocking {
        dao.placeBet(article, question, expiresAt, OracleBetSide.YES, stake, placedAt)
    }

    private fun run(r: OracleDueReminder = reminder()) = runBlocking { r.remindDue() }

    private fun posted() = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.ORACLE_DUE_CHANNEL_ID }

    private fun text(n: android.app.Notification, key: String) = n.extras.getCharSequence(key).toString()

    // --- what is reminded

    @Test fun nothingDue_postsNothing_andWritesNothing() {
        bet("later", now + 5 * day)
        assertEquals(0, run())
        assertTrue(posted().isEmpty())
        assertEquals(0, writes)
    }

    @Test fun aDueBet_postsOneNotification_withItsQuestion() {
        bet("a", now - day, question = "Will the ceasefire hold?")
        assertEquals(1, run())
        val n = posted().single()
        assertEquals(context.getString(R.string.oracle_due_notification_title), text(n, "android.title"))
        assertEquals("Will the ceasefire hold?", text(n, "android.text"))
    }

    @Test fun severalDueBets_postExactlyOneGroupedNotification() {
        bet("a", now - day); bet("b", now - 2 * day); bet("c", now - 3 * day)
        assertEquals(1, run())
        val n = posted().single()
        assertEquals(context.getString(R.string.oracle_due_notification_title_many), text(n, "android.title"))
        assertEquals(context.getString(R.string.oracle_due_notification_body_many, "3"), text(n, "android.text"))
    }

    @Test fun theNotificationNeverCarriesTheStake() {
        bet("a", now - day, stake = 500); bet("b", now - day, stake = 50)
        run()
        val n = posted().single()
        val all = text(n, "android.title") + text(n, "android.text")
        assertFalse("the points staked must not show on a lock screen", all.contains("500") || all.contains("50 "))
    }

    @Test fun settledAndRefundedBets_areNotReminded() {
        bet("won", now - day); bet("refunded", now - day)
        runBlocking {
            dao.settleBet("won", OracleOutcome.YES, now)
            dao.settleBet("refunded", OracleOutcome.UNCLEAR, now)
        }
        assertEquals(0, run())
    }

    // --- remembering what was already announced

    @Test fun afterPosting_theBetsAreMarkedReminded() {
        bet("a", now - day); bet("b", now - day)
        run()
        assertEquals(setOf("a", "b"), stored.toSet())
    }

    @Test fun theSecondRun_doesNotRemindAgain() {
        bet("a", now - day)
        assertEquals(1, run())
        assertEquals(0, run())
        assertEquals(1, posted().size)
    }

    @Test fun aNewlyDueBet_isRemindedAlone_whileTheOldOneIsNotRepeated() {
        bet("a", now - 2 * day)
        run()
        bet("b", now - day, question = "Second question?")
        assertEquals(1, run())
        assertEquals("Second question?", text(posted().single(), "android.text"))
        assertEquals(setOf("a", "b"), stored.toSet())
    }

    @Test fun markersOfBetsThatAreSettled_areDropped() {
        bet("a", now - day); bet("b", now - day)
        run()
        runBlocking { dao.settleBet("a", OracleOutcome.NO, now) }
        run()
        assertEquals(listOf("b"), stored)
    }

    @Test fun theMarkerSetIsCappedAtTwoHundred() {
        (1..205).forEach { bet("a$it", now - day) }
        run()
        assertEquals(200, stored.size)
        assertTrue("the oldest markers are the ones cut", "a205" in stored)
    }

    // --- permission

    @Test fun withoutPermission_nothingIsPosted_andNothingIsMarked() {
        shadowOf(manager).setNotificationsEnabled(false)
        bet("a", now - day)
        assertEquals(0, run())
        assertTrue(stored.isEmpty())
        assertEquals(0, writes)
    }

    @Test fun onceThePermissionIsBack_theSameBetIsRemindedThen() {
        shadowOf(manager).setNotificationsEnabled(false)
        bet("a", now - day)
        run()
        shadowOf(manager).setNotificationsEnabled(true)
        assertEquals(1, run())
        assertEquals(listOf("a"), stored)
    }

    // --- failures

    @Test fun aFailingQuery_postsNothing_marksNothing_andLogsOnlyTheType() {
        val failing = mockk<OracleWalletDao>()
        coEvery { failing.dueOpenBets(any()) } throws IllegalStateException("SecretQuestionText")
        assertEquals(0, run(reminder(failing)))
        assertEquals(0, writes)
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertTrue(log.contains("IllegalStateException"))
        assertFalse(log.contains("SecretQuestionText"))
    }

    @Test fun cancellation_isRethrown_andMarksNothing() {
        val cancelling = mockk<OracleWalletDao>()
        coEvery { cancelling.dueOpenBets(any()) } throws CancellationException("cancelled")
        val thrown = runCatching { run(reminder(cancelling)) }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(0, writes)
    }

    @Test fun aFailureWhileWritingTheMarkers_isAbsorbed() {
        bet("a", now - day)
        val r = object : OracleDueReminder(context, dao, helper) {
            override fun nowMillis() = now
            override suspend fun readReminded() = emptyList<String>()
            override suspend fun writeReminded(ids: List<String>) { error("disk full") }
        }
        assertEquals(0, run(r))
    }
}
```

Ghi chú: test `aFailureWhileWritingTheMarkers_isAbsorbed` kỳ vọng `0` vì thông báo **đã đăng** nhưng việc ghi lỗi nên không đếm; nếu muốn thông báo vẫn được tính, đổi kỳ vọng và ghi lý do, nhưng **không** để ngoại lệ thoát ra ngoài.

- [ ] **Step 3: Viết test nguồn đỏ — nối `SyncWorker`** (`OracleDueSyncWiringSourceTest`, khuôn `OracleSyncWiringSourceTest`, bỏ dòng comment và `import`)

```kotlin
package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleDueSyncWiringSourceTest {

    private val worker = File("src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt").readLines()
        .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")
    private val reminderSource = File("src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleDueReminder.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ")
        }.joinToString("\n")

    @Test fun workerTakesTheReminderThroughItsConstructor() {
        assertTrue(worker.contains("private val oracleDueReminder: OracleDueReminder"))
        assertTrue(worker.contains("import com.mckimquyen.reader.infrastructure.ai.OracleDueReminder"))
    }

    @Test fun reminderRunsOnlyInsideTheSuccessfulSyncBranch_andExactlyOnce() {
        val success = worker.substringAfter("if (syncResult.isSuccessfulSync()) {").substringBefore("            syncResult\n")
        assertTrue(success.contains("oracleDueReminder.remindDue("))
        assertEquals(1, Regex("remindDue\\(").findAll(worker).count())
    }

    @Test fun reminderFailureNeverFailsTheSync() {
        assertTrue(worker.substringBefore("oracleDueReminder.remindDue(").trimEnd().endsWith("runCatching {"))
        assertTrue(worker.contains("Due reminder failed"))
    }

    @Test fun reminderRunsAfterThePredictionFinder() {
        assertTrue(worker.indexOf("oracleClaimExtractor.extractRecent") < worker.indexOf("oracleDueReminder.remindDue"))
    }

    @Test fun reminderIsNotGatedOnTheGeminiConsent() {
        // Local data only: the consent that guards the AI finder must not switch the reminder off.
        assertTrue(!worker.substringAfter("oracleDueReminder.remindDue(").take(200).contains("Consent"))
        assertTrue(!reminderSource.contains("oracleClaimsGeminiConsent"))
    }

    @Test fun reminderNeverTouchesTheNetworkGeminiOrAds() {
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded", "http").forEach {
            assertTrue("OracleDueReminder must not mention '$it'", !reminderSource.contains(it, ignoreCase = true))
        }
    }
}
```

- [ ] **Step 4: Chạy ba lớp test, xác nhận đỏ** (biên dịch lỗi: `ORACLE_DUE_CHANNEL_ID`, `notifyOracleDue`, `OracleDueReminder` chưa có).

- [ ] **Step 5: Code tối thiểu**

`NotificationHelper.kt`: trong khối `createNotificationChannel` (sau kênh Watchdog):

```kotlin
            createNotificationChannel(
                NotificationChannel(
                    ORACLE_DUE_CHANNEL_ID,
                    context.getString(R.string.oracle_due_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.oracle_due_channel_desc)
                }
            )
```

hàm (cạnh `notifyRpgDecay`):

```kotlin
    fun notifyOracleDue(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ORACLE_DUE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ORACLE_DUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        post(ORACLE_DUE_NOTIFICATION_ID, notification)
    }
```

và trong `companion object`: `const val ORACLE_DUE_CHANNEL_ID = "oracle_due_channel"` và `const val ORACLE_DUE_NOTIFICATION_ID = 9955`.

`infrastructure/ai/OracleDueReminder.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.oracle.OracleRemindedCodec
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.oracleRemindedBets
import com.mckimquyen.reader.ui.ext.setOracleRemindedBets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, once, that bets have reached their date (ORACLE-03). Purely local: it reads the user's own open bets
 * and posts at most one grouped notification per run. It never decides an outcome and never mentions the points at stake.
 * A bet is marked as reminded only AFTER its notification was posted, so a missing permission or a crash retries later.
 */
@Singleton
open class OracleDueReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletDao: OracleWalletDao,
    private val notificationHelper: NotificationHelper,
) {

    protected open fun nowMillis(): Long = System.currentTimeMillis()

    protected open suspend fun readReminded(): List<String> = OracleRemindedCodec.decode(context.oracleRemindedBets)

    protected open suspend fun writeReminded(ids: List<String>) {
        context.setOracleRemindedBets(OracleRemindedCodec.encode(ids))
    }

    /** Returns how many notifications were posted: 0 or 1. */
    suspend fun remindDue(): Int = try {
        val due = walletDao.dueOpenBets(nowMillis())
        val reminded = readReminded()
        val dueIds = due.map { it.articleId }.toSet()
        val pruned = reminded.filter { it in dueIds }
        val fresh = due.filter { it.articleId !in reminded }
        when {
            fresh.isEmpty() -> {
                if (pruned != reminded) writeReminded(pruned)
                0
            }
            !notificationHelper.canPostNotifications() -> 0
            else -> {
                if (fresh.size == 1) {
                    notificationHelper.notifyOracleDue(context.getString(R.string.oracle_due_notification_title), fresh.first().question)
                } else {
                    val count = NumberFormat.getIntegerInstance().format(fresh.size)
                    notificationHelper.notifyOracleDue(
                        context.getString(R.string.oracle_due_notification_title_many),
                        context.getString(R.string.oracle_due_notification_body_many, count),
                    )
                }
                writeReminded(OracleRemindedCodec.append(pruned, fresh.map { it.articleId }))
                1
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Only the exception type: a message could carry a question.
        Log.w(TAG, "Due reminder failed: ${e.javaClass.simpleName}")
        0
    }

    private companion object {
        const val TAG = "OracleDueReminder"
    }
}
```

`SyncWorker.kt`: thêm `import com.mckimquyen.reader.infrastructure.ai.OracleDueReminder`, tham số `private val oracleDueReminder: OracleDueReminder,` sau `oracleClaimExtractor`, và **sau** khối `oracleClaimExtractor.extractRecent`:

```kotlin
                // Local only: reminds about bets that reached their date, whether or not the AI finder is on (ORACLE-03).
                runCatching {
                    oracleDueReminder.remindDue()
                }.onFailure { Log.w("RLog", "Due reminder failed", it) }
```

- [ ] **Step 6: Biên dịch sạch Hilt** (đổi constructor `@HiltWorker`, thêm `@Singleton @Inject` mới), chờ `.exit`, `exit=0`. Rồi chạy `NotificationHelperOracleDueTest` (7), `OracleDueReminderTest` (16), `OracleDueSyncWiringSourceTest` (6) **và** `NotificationHelperTest`, `NotificationHelperRpgDecayTest`, `OracleSyncWiringSourceTest` (hồi quy), tất cả xanh (đọc XML sau khi `.exit` tồn tại).

- [ ] **Step 7: Mutation (backup + `cmp`)**:
  - `OracleDueReminder.kt`: bỏ `!notificationHelper.canPostNotifications() -> 0` (đỏ: đăng khi không có quyền); đổi thứ tự (ghi `writeReminded(...)` **trước** `notifyOracleDue`: đỏ ở test "không đánh dấu khi chưa đăng"/lỗi ghi); `fresh.size == 1` → `fresh.size <= 1` hoặc `> 1` (đỏ nhánh gộp); bỏ `filter { it !in reminded }` (đỏ: nhắc lặp); `pruned = reminded.filter { it in dueIds }` → `reminded` (đỏ ở "marker của cược đã chốt"); thêm `fresh.first().stake` vào nội dung (đỏ ở test không lộ số điểm); bỏ `catch (e: CancellationException)` (đỏ); `${e.javaClass.simpleName}` → `${e.message}` (đỏ); thêm `import ...oracleClaimsGeminiConsent` và chặn bởi consent (đỏ ở test nguồn).
  - `NotificationHelper.kt`: `ORACLE_DUE_NOTIFICATION_ID = 9955` → `9977` (đỏ: trùng id Daily Edition và hằng số); bỏ `FLAG_IMMUTABLE` (đỏ); đổi kênh trong builder sang `RPG_DECAY_CHANNEL_ID` (đỏ).
  - `SyncWorker.kt`: bỏ `runCatching` quanh lời gọi (đỏ); chuyển khối lên **trước** `oracleClaimExtractor` (đỏ ở thứ tự); đưa khối ra ngoài nhánh thành công (đỏ).

- [ ] **Step 8: Commit** (kiểm `exit=0`, XML): `feat(oracle-03): remind once when bets reach their date, after each sync`.

---

### Task 7: Trạng thái bằng chứng trong `OracleBettingViewModel`, mục "Bài liên quan" trong thẻ, nối `HomeEntry`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/oracle/OracleBetting.kt` (thêm `OracleEvidenceUiState`, tham số `finder` và `accountId`, `val evidence`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/oracle/OracleBettingSection.kt` (thêm tham số `evidence`, `onOpenArticle`, mục bằng chứng ở pha `AWAITING_RESULT`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` (truyền `evidence` và mở bài)
- Modify (test sẵn có, đổi cách dựng): `OracleBettingViewModelTest.kt`, `OracleBettingSectionTest.kt`
- Test mới: `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleEvidenceViewModelTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/component/oracle/OracleEvidenceSectionTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleEvidenceWiringSourceTest.kt`

**Interfaces:**
- Consumes: `OracleEvidenceFinder.find(accountId: Int, bet: OracleBet): List<OracleEvidence>` (Task 4), `OracleEvidence` (Task 1), `OracleBetUiState`, `OracleBetPhase` (ORACLE-02), chuỗi `oracle_due_evidence_*` (Task 5), `Context.currentAccountId` (`ui/ext/ExtDataStore.kt`).
- Produces:
  - `sealed interface OracleEvidenceUiState { data object Idle; data object Loading; data class Ready(val items: List<OracleEvidence>) }`.
  - `OracleBettingViewModel` có thêm `val evidence: StateFlow<OracleEvidenceUiState>`. Constructor `internal` đổi thành `(dao: OracleWalletDao, finder: OracleEvidenceFinder, io: CoroutineDispatcher, accountId: () -> Int, clock: () -> Long)`; constructor `@Inject` đổi thành `(dao, finder, @ApplicationContext context: Context, @IODispatcher io)` và ủy quyền với `accountId = { context.currentAccountId }`, `clock = System::currentTimeMillis`.
  - Hành vi `evidence`: chỉ tải khi `state.phase == AWAITING_RESULT` **và** có `state.bet`; khi đó phát `Loading` rồi `Ready(items)`; ở mọi pha khác hoặc khi chưa mở thẻ phát `Idle`; chạy trên `io`; đổi thẻ hoặc `close()` hủy lần tải đang chạy; lỗi → `Ready(emptyList())` (finder đã tự bắt lỗi, nên đây là lưới thứ hai); mỗi cược chỉ tìm **một lần** khi `(phase, articleId)` không đổi.
  - `OracleBettingSection(state, failed, onPlaceBet, onSettle, evidence: OracleEvidenceUiState = OracleEvidenceUiState.Idle, onOpenArticle: (String) -> Unit = {})` — giá trị mặc định giữ nguyên mọi chỗ gọi và test cũ.
  - Test tag mới: `ORACLE_EVIDENCE_SECTION_TAG = "oracle_evidence_section"`, `ORACLE_EVIDENCE_ITEM_TAG_PREFIX = "oracle_evidence_item_"` (ghép `articleId`), `ORACLE_EVIDENCE_NONE_TAG = "oracle_evidence_none"`, `ORACLE_EVIDENCE_LOADING_TAG = "oracle_evidence_loading"`, `ORACLE_EVIDENCE_HINT_TAG = "oracle_evidence_hint"`.
- Hiển thị: chỉ ở pha `AWAITING_RESULT`, **trên** ba nút kết quả: tiêu đề `oracle_due_evidence_title`; `Loading` → một dòng tiến trình; `Ready` rỗng → `oracle_due_evidence_none`; `Ready` có bài → tối đa 3 hàng (tiêu đề, "nguồn · ngày" theo locale), bấm gọi `onOpenArticle(articleId)`; luôn kèm nhãn `oracle_due_evidence_hint`. Ở mọi pha khác **không** có mục này. Đích chạm ≥ 48 dp, `contentDescription` = tiêu đề bài.

- [ ] **Step 1: Sửa test sẵn có cho khớp chữ ký mới** (hành vi cũ giữ nguyên, nên chúng phải xanh sau khi code)

`OracleBettingViewModelTest.kt`: chỗ dựng `OracleBettingViewModel(db.oracleWalletDao(), Dispatchers.Unconfined) { nowMs }` (và các bản dựng bằng `mockk<OracleWalletDao>()` trong ba test lỗi) đổi thành
`OracleBettingViewModel(dao, NoEvidenceFinder, Dispatchers.Unconfined, accountId = { 1 }, clock = { nowMs })`, với một hằng test:

```kotlin
    // The evidence part has its own tests; these ones are about the wallet and the bet.
    private val NoEvidenceFinder: OracleEvidenceFinder = mockk { coEvery { find(any(), any()) } returns emptyList() }
```

`OracleBettingSectionTest.kt` **không cần đổi** (tham số mới có giá trị mặc định).

- [ ] **Step 2: Viết test đỏ — view model bằng chứng** (`OracleEvidenceViewModelTest`: Robolectric + Room in-memory, `OracleEvidenceFinder` giả bằng MockK, dispatcher unconfined, chờ thời gian thực có hạn; **chờ trạng thái cuối**, không chờ một khung giữa)

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.infrastructure.ai.OracleEvidenceFinder
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleEvidenceViewModelTest {

    private lateinit var db: AndroidDatabase
    private val finder: OracleEvidenceFinder = mockk()
    private val day = 86_400_000L
    private var nowMs = 100 * day
    private val card get() = OracleClaimCard("a", "Will it ship?", nowMs + 5 * day, "Release notes.")
    private val evidence = OracleEvidence("e1", "Ship date confirmed", "Wire", 120 * day)
    private lateinit var vm: OracleBettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        coEvery { finder.find(any(), any()) } returns listOf(evidence)
        vm = OracleBettingViewModel(db.oracleWalletDao(), finder, Dispatchers.Unconfined, accountId = { 7 }, clock = { nowMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    private fun evidenceWhere(p: (OracleEvidenceUiState) -> Boolean) = awaitReal { vm.evidence.first(p) }

    private fun stateWhere(p: (OracleBetUiState) -> Boolean) = awaitReal { vm.state.first { it != null && p(it) }!! }

    private fun placeBetAndLetItExpire() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.bet != null && it.balance == 900L }
        nowMs += 6 * day
        vm.close(); vm.open(card)
        stateWhere { it.phase == com.mckimquyen.reader.domain.oracle.OracleBetPhase.AWAITING_RESULT }
    }

    @Test fun nothingOpen_isIdle() = assertEquals(OracleEvidenceUiState.Idle, vm.evidence.value)

    @Test fun whileTheCardCanStillBeBetOn_noSearchIsMade() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { true })
        coVerify(exactly = 0) { finder.find(any(), any()) }
    }

    @Test fun whenTheBetIsDue_theEvidenceIsLoaded_forTheRightAccountAndBet() {
        placeBetAndLetItExpire()
        val ready = evidenceWhere { it is OracleEvidenceUiState.Ready } as OracleEvidenceUiState.Ready
        assertEquals(listOf(evidence), ready.items)
        coVerify { finder.find(7, match { it.articleId == "a" && it.question == "Will it ship?" }) }
    }

    @Test fun whileSearching_itShowsLoading() {
        val gate = CompletableDeferred<List<OracleEvidence>>()
        coEvery { finder.find(any(), any()) } coAnswers { gate.await() }
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Loading, evidenceWhere { it == OracleEvidenceUiState.Loading })
        gate.complete(listOf(evidence))
        assertTrue(evidenceWhere { it is OracleEvidenceUiState.Ready } is OracleEvidenceUiState.Ready)
    }

    @Test fun nothingFound_isAnEmptyReady_notAnError() {
        coEvery { finder.find(any(), any()) } returns emptyList()
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Ready(emptyList()), evidenceWhere { it is OracleEvidenceUiState.Ready })
    }

    @Test fun aThrowingFinder_degradesToAnEmptyReady() {
        coEvery { finder.find(any(), any()) } throws IllegalStateException("boom")
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Ready(emptyList()), evidenceWhere { it is OracleEvidenceUiState.Ready })
    }

    @Test fun closingTheCard_goesBackToIdle() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.close()
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { it == OracleEvidenceUiState.Idle })
    }

    @Test fun settlingTheBet_removesTheEvidenceSection() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.settle(com.mckimquyen.reader.domain.model.oracle.OracleOutcome.UNCLEAR)
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { it == OracleEvidenceUiState.Idle })
    }

    @Test fun theSameBetIsSearchedOnlyOnce_whileTheCardStaysOpen() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.failed.value // touching another flow must not trigger another search
        coVerify(exactly = 1) { finder.find(any(), any()) }
    }
}
```

- [ ] **Step 3: Viết test đỏ — hiển thị** (`OracleEvidenceSectionTest`: Robolectric Compose, `en-rUS`, khuôn `OracleBettingSectionTest`)

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import com.mckimquyen.reader.ui.page.oracle.OracleEvidenceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OracleEvidenceSectionTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val date = LocalDate.of(2026, 12, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun item(id: String) = OracleEvidence(id, "Title $id", "Wire", date)
    private fun bet(status: OracleBetStatus = OracleBetStatus.OPEN) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = OracleBetSide.YES, stake = 100, status = status, placedAt = 1)

    private fun show(phase: OracleBetPhase, evidence: OracleEvidenceUiState, withBet: Boolean = true) = compose.setContent {
        OracleBettingSection(
            state = OracleBetUiState(phase, 900, if (withBet) bet(if (phase == OracleBetPhase.SETTLED) OracleBetStatus.WON else OracleBetStatus.OPEN) else null),
            failed = false, onPlaceBet = { _, _ -> }, onSettle = {},
            evidence = evidence, onOpenArticle = { opened += it },
        )
    }

    private fun str(id: Int) = compose.activity.getString(id)
    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test fun awaiting_withItems_showsTitleRowsAndTheHint() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"), item("e2"))))
        assertEquals(1, count(ORACLE_EVIDENCE_SECTION_TAG))
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Title e1", substring = true)
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Wire", substring = true)
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Dec 20, 2026", substring = true)
        assertEquals(1, count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e2"))
        compose.onNodeWithTag(ORACLE_EVIDENCE_HINT_TAG).assertTextContains(str(R.string.oracle_due_evidence_hint))
    }

    @Test fun clickingARow_opensThatArticle() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"), item("e2"))))
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e2").performClick()
        assertEquals(listOf("e2"), opened)
    }

    @Test fun awaiting_withNothingFound_saysSo_andStillShowsTheHint() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(emptyList()))
        compose.onNodeWithTag(ORACLE_EVIDENCE_NONE_TAG).assertTextContains(str(R.string.oracle_due_evidence_none))
        assertEquals(1, count(ORACLE_EVIDENCE_HINT_TAG))
    }

    @Test fun awaiting_whileLoading_showsProgress_notTheNoneMessage() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Loading)
        assertEquals(1, count(ORACLE_EVIDENCE_LOADING_TAG))
        assertEquals(0, count(ORACLE_EVIDENCE_NONE_TAG))
    }

    @Test fun idle_showsNothingOfTheEvidenceSection() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Idle)
        assertEquals(0, count(ORACLE_EVIDENCE_SECTION_TAG))
    }

    @Test fun otherPhases_neverShowTheSection_evenIfEvidenceIsPassed() {
        listOf(OracleBetPhase.CAN_BET, OracleBetPhase.OUT_OF_POINTS, OracleBetPhase.BET_OPEN, OracleBetPhase.SETTLED, OracleBetPhase.CLOSED_NO_BET).forEach { phase ->
            compose.activityRule.scenario.recreate()
            show(phase, OracleEvidenceUiState.Ready(listOf(item("e1"))), withBet = phase == OracleBetPhase.BET_OPEN || phase == OracleBetPhase.SETTLED)
            assertEquals("$phase", 0, count(ORACLE_EVIDENCE_SECTION_TAG))
        }
    }

    @Test fun theSectionIsAboveTheThreeResultButtons() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"))))
        val evidenceTop = compose.onNodeWithTag(ORACLE_EVIDENCE_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        val buttonTop = compose.onNodeWithTag(ORACLE_BET_RESULT_YES_TAG).fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, evidenceTop < buttonTop)
    }

    @Test fun theExistingResultButtonsStillWork_withEvidenceShown() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"))))
        compose.onNodeWithTag(ORACLE_BET_RESULT_UNCLEAR_TAG).performClick()
        assertEquals(emptyList<String>(), opened)
    }
}
```

Ghi chú: ở test `otherPhases_...` việc gọi `setContent` nhiều lần trong một test có thể bị Compose từ chối; nếu vậy **tách thành một test mỗi pha** (cùng khẳng định), không bỏ khẳng định. Test cuối (`theExistingResultButtonsStillWork_...`) chỉ chứng minh nút kết quả vẫn bấm được; số lần gọi `onSettle` đã được `OracleBettingSectionTest` của ORACLE-02 kiểm, nên ở đây chỉ cần không crash và không mở bài.

- [ ] **Step 4: Viết test nguồn đỏ — nối giao diện** (`OracleEvidenceWiringSourceTest`, chỉ xét mã: bỏ dòng comment và `import`)

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleEvidenceWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val viewModel = code("ui/page/oracle/OracleBetting.kt")
    private val section = code("ui/component/oracle/OracleBettingSection.kt")
    private val home = code("ui/page/common/HomeEntry.kt")

    @Test fun viewModelSearchesOnlyThroughTheLocalFinder_onTheIoDispatcher() {
        assertTrue(viewModel.contains("OracleEvidenceFinder"))
        assertTrue(viewModel.contains("val evidence: StateFlow<OracleEvidenceUiState>"))
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded").forEach {
            assertFalse("OracleBetting.kt must not mention '$it'", viewModel.contains(it, ignoreCase = true))
        }
    }

    @Test fun viewModelSearchesOnlyWhileTheBetAwaitsItsResult() {
        assertTrue(viewModel.contains("OracleBetPhase.AWAITING_RESULT"))
    }

    @Test fun everyLaunchStillGoesThroughViewModelScope() {
        assertEquals(
            "every launch goes through viewModelScope",
            Regex("""\blaunch\b""").findAll(viewModel).count(),
            Regex("""viewModelScope\.launch""").findAll(viewModel).count(),
        )
    }

    @Test fun sectionReadsItsTextFromStringResources_andShowsNoLinkOrBody() {
        assertTrue(section.contains("R.string.oracle_due_evidence_title"))
        assertTrue(section.contains("R.string.oracle_due_evidence_none"))
        assertTrue(section.contains("R.string.oracle_due_evidence_hint"))
        listOf("rawDescription", "fullContent", "link", "Rewarded", "AdManager").forEach {
            assertFalse("the evidence section must not mention '$it'", section.contains(it, ignoreCase = true))
        }
    }

    @Test fun sectionShowsAtMostTheThreeBestItems_fromThePolicyConstant() {
        assertTrue(section.contains("OracleEvidencePolicy.MAX_EVIDENCE"))
        assertFalse(Regex("""\.take\(\s*3\s*\)""").containsMatchIn(section))
    }

    @Test fun homeEntryPassesTheEvidenceAndOpensTheArticleThroughTheReadingRoute() {
        assertTrue(home.contains("oracleBettingViewModel.evidence.collectAsState()"))
        assertTrue(home.contains("evidence = oracleEvidence"))
        assertTrue(home.contains("onOpenArticle"))
        assertTrue(home.contains("\"\${RouteName.READING}/"))
    }
}
```

- [ ] **Step 5: Chạy các lớp test mới, xác nhận đỏ** (biên dịch lỗi: `OracleEvidenceUiState`, `evidence`, tham số mới, tag mới chưa có; đọc `grep -E '^e: '`).

- [ ] **Step 6: Code tối thiểu**

`OracleBetting.kt` — thêm trạng thái và đổi constructor (toàn bộ phần còn lại của lớp **giữ nguyên**):

```kotlin
sealed interface OracleEvidenceUiState {
    data object Idle : OracleEvidenceUiState
    data object Loading : OracleEvidenceUiState
    data class Ready(val items: List<OracleEvidence>) : OracleEvidenceUiState
}
```

```kotlin
@HiltViewModel
class OracleBettingViewModel internal constructor(
    private val dao: OracleWalletDao,
    private val finder: OracleEvidenceFinder,
    private val io: CoroutineDispatcher,
    private val accountId: () -> Int,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        dao: OracleWalletDao,
        finder: OracleEvidenceFinder,
        @ApplicationContext context: Context,
        @IODispatcher io: CoroutineDispatcher,
    ) : this(dao, finder, io, { context.currentAccountId }, System::currentTimeMillis)
```

và, ngay sau `val state`:

```kotlin
    /**
     * Hints for a bet that reached its date: loaded once per (card, phase), only while the bet awaits its result, and only
     * from the on-device finder. Any failure degrades to "nothing found".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val evidence: StateFlow<OracleEvidenceUiState> = state
        .map { ui -> ui?.takeIf { it.phase == OracleBetPhase.AWAITING_RESULT }?.bet }
        .distinctUntilChanged { old, new -> old?.articleId == new?.articleId }
        .flatMapLatest { bet ->
            if (bet == null) flowOf(OracleEvidenceUiState.Idle)
            else flow {
                emit(OracleEvidenceUiState.Loading)
                emit(OracleEvidenceUiState.Ready(guarded { finder.find(accountId(), bet) }.orEmpty()))
            }.flowOn(io)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OracleEvidenceUiState.Idle)
```

(thêm các `import` còn thiếu: `OracleEvidence`, `OracleEvidenceFinder`, `Context`, `ApplicationContext`, `currentAccountId`, `map`, `distinctUntilChanged`, `flow`, `flowOn`, và `OracleBetPhase` nếu chưa có). Khi bet đổi từ `AWAITING_RESULT` sang đã chốt thì `bet` của `state` vẫn có nhưng `phase` là `SETTLED`, nên `takeIf` trả `null` và `evidence` về `Idle` (đã có test).

`OracleBettingSection.kt` — thêm tham số và mục (đặt **trước** `ResultButtons(onSettle)` trong nhánh `AWAITING_RESULT`):

```kotlin
@Composable
fun OracleBettingSection(
    state: OracleBetUiState,
    failed: Boolean,
    onPlaceBet: (OracleBetSide, Int) -> Unit,
    onSettle: (OracleOutcome) -> Unit,
    evidence: OracleEvidenceUiState = OracleEvidenceUiState.Idle,
    onOpenArticle: (String) -> Unit = {},
) {
```

```kotlin
            OracleBetPhase.AWAITING_RESULT -> state.bet?.let { bet ->
                Text(stringResource(R.string.oracle_bet_awaiting_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.oracle_bet_awaiting_hint, number.format(bet.stake), sideName(bet.side)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(ORACLE_BET_OPEN_TAG),
                )
                EvidenceSection(evidence, locale, onOpenArticle)
                ResultButtons(onSettle)
            }
```

```kotlin
const val ORACLE_EVIDENCE_SECTION_TAG = "oracle_evidence_section"
const val ORACLE_EVIDENCE_ITEM_TAG_PREFIX = "oracle_evidence_item_"
const val ORACLE_EVIDENCE_NONE_TAG = "oracle_evidence_none"
const val ORACLE_EVIDENCE_LOADING_TAG = "oracle_evidence_loading"
const val ORACLE_EVIDENCE_HINT_TAG = "oracle_evidence_hint"

/** Hints from the user's own saved news; they can be wrong, so the user still decides (ORACLE-03). */
@Composable
private fun EvidenceSection(evidence: OracleEvidenceUiState, locale: Locale, onOpenArticle: (String) -> Unit) {
    if (evidence == OracleEvidenceUiState.Idle) return
    val dateFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    Column(Modifier.fillMaxWidth().testTag(ORACLE_EVIDENCE_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.oracle_due_evidence_title), style = MaterialTheme.typography.titleSmall)
        when (evidence) {
            OracleEvidenceUiState.Loading -> CircularProgressIndicator(Modifier.size(20.dp).testTag(ORACLE_EVIDENCE_LOADING_TAG))
            is OracleEvidenceUiState.Ready -> if (evidence.items.isEmpty()) {
                Text(stringResource(R.string.oracle_due_evidence_none), modifier = Modifier.testTag(ORACLE_EVIDENCE_NONE_TAG))
            } else {
                evidence.items.take(OracleEvidencePolicy.MAX_EVIDENCE).forEach { item ->
                    val line = "${item.title}\n${item.feedName} · ${dateFormat.format(Date(item.dateMillis))}"
                    TextButton(
                        onClick = { onOpenArticle(item.articleId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .semantics { contentDescription = item.title }
                            .testTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + item.articleId),
                    ) { Text(line, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall) }
                }
            }
            OracleEvidenceUiState.Idle -> Unit
        }
        Text(
            stringResource(R.string.oracle_due_evidence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ORACLE_EVIDENCE_HINT_TAG),
        )
    }
}
```

(thêm `import`: `CircularProgressIndicator`, `TextButton`, `Date`, `java.text.DateFormat`, `size`, `OracleEvidencePolicy`, `OracleEvidenceUiState`).

`HomeEntry.kt` — cạnh `oracleBetFailed`: `val oracleEvidence by oracleBettingViewModel.evidence.collectAsState()`, và trong lời gọi `OracleBettingSection(...)` thêm:

```kotlin
                            evidence = oracleEvidence,
                            onOpenArticle = { id ->
                                openOracleArticleId = null
                                oracleBettingViewModel.close()
                                navController.navigate("${RouteName.READING}/$id") { launchSingleTop = true }
                            },
```

(đóng thẻ trước khi điều hướng để không để Dialog treo trên màn đọc).

- [ ] **Step 7: Biên dịch sạch Hilt** (đổi constructor `@HiltViewModel`), chờ `.exit`, `exit=0`. Rồi chạy `OracleEvidenceViewModelTest` (9), `OracleEvidenceSectionTest` (8), `OracleEvidenceWiringSourceTest` (6) **và** hồi quy `OracleBettingViewModelTest` (đã đổi cách dựng), `OracleBettingSectionTest`, `OracleBetUiStateTest`, `OracleBettingWiringSourceTest`, `OracleClaimSheetTest`, `OracleWiringSourceTest`: tất cả xanh (đọc XML sau khi `.exit` tồn tại). **Chạy 3 lần liên tiếp** các lớp có luồng Room để chắc không chập chờn.

- [ ] **Step 8: Mutation (backup + `cmp`)**:
  - `OracleBetting.kt`: `it.phase == OracleBetPhase.AWAITING_RESULT` → `true` (đỏ: tìm ở mọi pha); bỏ `.distinctUntilChanged {...}` (đỏ: tìm lại); `accountId()` → `0` (đỏ: sai tài khoản); `guarded { finder.find(...) }.orEmpty()` → `finder.find(...)` (đỏ: lỗi không được hấp thụ); bỏ `.flowOn(io)` (nếu không đỏ vì dispatcher test là Unconfined, ghi nhận và giữ bằng test nguồn `@IODispatcher`); bỏ `emit(OracleEvidenceUiState.Loading)` (đỏ ở test Loading); `OracleEvidenceUiState.Idle` ban đầu → `Loading` (đỏ).
  - `OracleBettingSection.kt`: bỏ dòng gọi `EvidenceSection(...)` (đỏ); đặt `EvidenceSection` **sau** `ResultButtons` (đỏ ở test thứ tự); `OracleEvidencePolicy.MAX_EVIDENCE` → `10` (đỏ nếu có test với 5 mục; **thêm** test `fiveItems_showsOnlyThree` trước khi chạy mutation nếu chưa có); bỏ `if (evidence == Idle) return` (đỏ: mục xuất hiện khi Idle); bỏ nhãn gợi ý (đỏ); `onOpenArticle(item.articleId)` → `onOpenArticle("")` (đỏ).
  - `HomeEntry.kt`: bỏ `evidence = oracleEvidence,` (đỏ ở test nguồn); bỏ `oracleBettingViewModel.close()` trong `onOpenArticle` (đỏ).

- [ ] **Step 9: Commit** (kiểm `exit=0` và XML): `feat(oracle-03): suggest related saved articles on a due bet's card`.

---

### Task 8: Full suite, lint, androidTest, máy thật Pixel, báo cáo

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/oracle/OracleEvidenceWidgetTest.kt` (khuôn `OracleBettingSectionWidgetTest`, **không** dùng `ComposeTestRule`: `ActivityScenario` + `ComposeView` + duyệt cây semantics; 4 test: pha chờ kết quả có hàng bài liên quan và bấm một hàng gọi `onOpenArticle(id)`; không có bài thì hiện "không thấy" và nhãn gợi ý; pha khác không có mục này; ba nút kết quả vẫn bấm được khi có bằng chứng)
- Tạm (không vào repo): `/tmp/o03-androidtest/OracleDueSeedTest.kt` copy vào `app/src/androidTest/.../OracleDueSeedTest.kt` **chỉ** khi chạy smoke rồi xóa; đầu vào qua `-e mode`: `claimBet` (chèn một kèo `HAS_CLAIM` cho bài mới nhất rồi đặt cược 100 qua `OracleWalletDao.placeBet` với `placedAt` quá khứ, `expiresAt` quá khứ gần), `claimBet2` (cược thứ hai ở bài khác, cũng đã đến hạn), `relatedArticle` (chèn một bài **sau** `placedAt` có tiêu đề trùng từ với câu hỏi), `clearReminded` (xóa khóa `oracle_reminded_bets` khỏi DataStore bằng API, **không** xóa tệp DataStore), `clearBets`, `restoreNothing`
- Script: `/tmp/o03p-backup.py`, `/tmp/o03p-restore.py` (sao từ `/tmp/o02p-backup.py` và `/tmp/o02p-restore.py` bằng `sed 's#/tmp/o02p-root#/tmp/o03p-root#g; s/o02-/o03-/g'`; **chỉ** `SERIALS = {'pixel': '2B051FDH3006MU'}`)

**Cổng thiết bị (chỉ đọc) trước mọi thao tác:**
1. `adb devices` (Pixel phải có mặt; thiết bị khác nếu có mặt **không chạm**).
2. `adb -s 2B051FDH3006MU shell dumpsys activity activities | grep -E "Active instrumentation|topResumedActivity"`: không có instrumentation; foreground là launcher hoặc app của owner đã biết (trên Pixel `bemytester`, `loyalty.skyjoy.vn.stg` ở nền là bình thường); app lạ ở foreground **thì dừng và hỏi**.
3. `adb -s ... shell ps -A | grep -iE "instrument|monkey|uiautomator"`: trống.
4. Màn hình mở khóa và **không có hộp thoại sinh trắc** (`dumpsys window | grep mCurrentFocus` không phải `BiometricPrompt`); nếu khóa hoặc có hộp thoại: **nhờ owner xử lý trên máy, không tự thao tác**, rồi chạy lại cổng.

- [ ] **Step 1: Full suite sạch** (ở nền): `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/o03-full.log 2>&1; echo exit=$? > /tmp/o03-full.exit`; **chờ `.exit`**, rồi đếm từ XML: tổng `tests=`, `failures=`, `errors=`, `skipped=` trên `app/build/test-results/testDevDebugUnitTest/*.xml` bằng script Python, và `find ... -mmin +120` không ra tệp cũ. Kỳ vọng **0 lỗi** (nền trước **3.099**). Nếu treo thì kill "Gradle Test Executor" rồi chạy lại. Lỗi chỉ khi chạy chung = trạng thái tĩnh bị rò: tìm gốc, không loại test.
- [ ] **Step 2: Lint** (ở nền): `./gradlew lintDevDebug > /tmp/o03-lint.log 2>&1; echo exit=$? > /tmp/o03-lint.exit`; đếm `severity` từ `app/build/reports/lint-results-devDebug.xml` (nền **2.465** cảnh báo, 0 lỗi) và liệt kê cảnh báo trong tệp ORACLE-03 (kỳ vọng 0; nếu có thì sửa).
- [ ] **Step 3: Build** (ở nền): `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/o03-assemble.log 2>&1; echo exit=$? > /tmp/o03-assemble.exit`; `exit=0`.
- [ ] **Step 4: Schema:** `git status --short app/schemas` **rỗng** và `git diff --stat -- app/schemas` rỗng (không đổi lược đồ; `22.json` là bản mới nhất).
- [ ] **Step 5: Viết `OracleEvidenceWidgetTest`, build, commit riêng** (kiểm `exit=0`): `test(oracle-03): cover the evidence section on a real device`, **trước** khi chạm máy.
- [ ] **Step 6: Cổng thiết bị rồi sao lưu** (`python3 /tmp/o03p-backup.py`; ghi `integrity ok`, kích thước DataStore, số bài). Cài APK dev + test APK: `adb -s 2B051FDH3006MU install -r -t <apk>`.
- [ ] **Step 7: Chạy androidTest** (không `connected*`): `timeout 300 adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.component.oracle.OracleEvidenceWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/o03-instr.log 2>&1`; kỳ vọng `OK (4 tests)`. Ba mutation trên máy (mỗi ca: sửa, build, cài, chạy, **phải đỏ**, khôi phục bằng `cmp`, cài lại HEAD sạch): đặt `EvidenceSection` sau `ResultButtons`; bỏ `testTag` của hàng bài; bỏ nhãn gợi ý.
- [ ] **Step 8: Smoke tay với app thật (không Gemini, mọi thứ cục bộ):** mỗi lần helper chạy xong app bị dừng nên phải mở lại bằng `SplashActivity` và đóng quảng cáo **chỉ bằng Back** (kiểm `topResumedActivity` sau mỗi lần Back).
  1. Mở app trên DB hiện có: không có thông báo `oracle_due`; `dumpsys notification` không có kênh đăng.
  2. `claimBet` rồi **kích đồng bộ** (nút refresh): sync `SUCCESS`; **đúng một** thông báo ở kênh `oracle_due_channel`, tiêu đề "Một kèo dự đoán đã đến hạn" (hoặc "A prediction has reached its date"), nội dung là câu hỏi, **không có số điểm**; DataStore có khóa `oracle_reminded_bets` chứa `articleId` của cược.
  3. Đồng bộ lần hai: **không** thêm thông báo mới (đếm trong `dumpsys notification` vẫn là một và nội dung không đổi); log `0` dòng `oracle_claim` (consent AI tắt, không Gemini).
  4. `claimBet2` rồi đồng bộ: thông báo **gộp** ("N kèo..." hoặc câu hỏi của kèo mới nếu chỉ một kèo mới chưa nhắc), vẫn **một** thông báo ở kênh, thay chứ không chồng.
  5. Mở thẻ của kèo đã đến hạn: thấy "Kết quả là gì?", **mục "Bài liên quan trong tin của bạn"** phía trên ba nút, nhãn gợi ý; khi chưa có bài sau ngày đặt cược: "Chưa thấy bài nào liên quan."
  6. `relatedArticle` (chèn bài sau `placedAt` có từ trùng câu hỏi), mở lại thẻ: bài hiện trong danh sách với "nguồn · ngày"; bấm hàng đó: thẻ đóng và **mở màn đọc bài đúng**.
  7. Bấm **Không rõ**: thẻ chuyển sang "Bạn được hoàn N điểm." (hành vi ORACLE-02 nguyên vẹn), mục bằng chứng biến mất; đồng bộ lần nữa: marker của cược đã chốt bị dọn khỏi DataStore (đọc khóa).
  8. Tắt thông báo của app trong cài đặt hệ thống (`cmd notification set_notifications_enabled_for_package com.mckimquyen.reader ... false` hoặc `settings`), `claimBet` mới, đồng bộ: **không** đăng và **không** đánh dấu; bật lại, đồng bộ: **đăng** và đánh dấu.
  9. Tắt riêng **kênh** `oracle_due_channel` trong cài đặt hệ thống: các kênh khác của app không bị ảnh hưởng (kiểm bằng `dumpsys notification`).
- [ ] **Step 9: Khôi phục byte-identical** (`python3 /tmp/o03p-restore.py`): Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; trả quyền thông báo và kênh về trạng thái ban đầu; gỡ test package (`adb -s 2B051FDH3006MU uninstall com.mckimquyen.reader.test`); xóa `OracleDueSeedTest.kt` khỏi cây mã (`git status` sạch, **không** commit); `force-stop com.mckimquyen.reader`; cài lại bản HEAD sạch; xác nhận `git diff --stat -- app/src/main` rỗng.
- [ ] **Step 10:** Nếu bất kỳ ca nào đỏ: sửa mã bằng TDD (viết test JVM tái hiện trước), không sửa test cho khớp. Ghi mọi phát hiện vào báo cáo.
- [ ] **Step 11: Báo cáo và tài liệu**
  - Create `doc/task/done/ORACLE-03_automated-arbiter-agent_DONE.md` (tiếng Việt, khuôn `ORACLE-02_intel-points-betting-sheet_DONE.md`: Phạm vi nói thẳng, Đã làm, Kiểm chứng JVM, Kiểm chứng Pixel, Phát hiện khi làm, Chưa kiểm, Giới hạn, Commits). Chỉ số liệu thật từ XML, log và lệnh đã chạy. Nói thẳng: **không tự chốt**, không "chia thưởng nhiều người", AI không tra web; gợi ý có thể sai hoặc vắng; nhắc phụ thuộc tần suất sync.
  - Update `doc/feature.md` (ORACLE-03 từ "đang brainstorm → spec" sang ✅ với số liệu thật; nợ và ghi chú: ORACLE-04 vẫn hoãn).
  - Update spec nếu thực thi lệch (ghi mục "Điều chỉnh khi thực thi").
  - Task gốc `doc/task/todo/ORACLE-03_*.md`: **hỏi owner bằng AskUserQuestion** giữ trong `todo/` + ghi chú đầu file, hay chuyển sang `done/`. Gherkin gốc ("agent tìm bài xác nhận, chuyển RESOLVED_YES, cộng điểm cho tất cả người chọn đúng, push '+850 Intel Points'") **không làm**: khuyến nghị giữ trong `todo/` với ghi chú rõ các điểm đó.
  - Commit `docs(oracle-03): report the due reminder with its verified and unverified parts`. Không push, không PR, không `.idea`.

**Không kiểm được (ghi thẳng vào báo cáo):** chất lượng gợi ý trên tin thật lâu dài (chỉ bài mẫu); hành vi khi thiết bị tiết kiệm pin trì hoãn sync; TalkBack; ngôn ngữ ngoài EN và VI; Tecno KJ7 (rơi khỏi adb) và máy thứ hai.

---

## Self-review

**Spec coverage:**
- Không tự chốt, chỉ nhắc và gợi ý: không có task nào gọi `settleBet` hay AI; test nguồn cấm `AiRequestGateway`/`GeminiSummaryService`/quảng cáo ở `OracleDueReminder`, `OracleEvidenceFinder`, view model và thẻ (Task 4, 6, 7).
- Chạy sau mỗi sync thành công, không Worker mới, không phụ thuộc consent AI: Task 6 (`SyncWorker` trong `runCatching`, test nguồn cả nhánh thành công và "không gated consent").
- Giữ Room v22, chỉ một truy vấn: Task 3 (không đổi lược đồ), Task 8 Step 4 (`app/schemas` rỗng).
- Đã nhắc lưu DataStore, trần 200, dọn cược đã chốt, đánh dấu sau khi đăng: Task 2 (codec, khóa), Task 6 (`OracleDueReminder`).
- Một thông báo gộp, kênh riêng bật sẵn, không ghi số điểm, không đăng khi thiếu quyền: Task 6.
- Tìm tối đa 3 bài, chỉ bài sau ngày đặt cược, quét 200 bài mới nhất, không đọc thân bài, cục bộ: Task 1 (luật thuần), Task 4 (finder).
- Mục bằng chứng ở pha chờ kết quả, trên ba nút, không bịa khi rỗng, nhãn gợi ý, mở bài: Task 7.
- 8 chuỗi × 38 locale: Task 5. Máy thật chỉ Pixel, không Gemini, helper tạm không vào repo, khôi phục byte-identical: Task 8.
- Ngoài phạm vi (tự chốt AI, nhiều người, ORACLE-04, đổi luật điểm, Room v23): không có task.

**Thứ tự thực thi:** 1 → 2 → 3 → 4 → **5 (chuỗi)** → 6 → 7 → 8.

**Kiểu và tên đã kiểm nhất quán:** `OracleEvidenceCandidate`/`OracleEvidence`/`OracleEvidencePolicy.select(candidates, placedAt, limit)` (Task 1) được `OracleEvidenceFinder` (Task 4), view model và thẻ (Task 7) dùng đúng; `OracleRemindedCodec.decode/encode/append/prune` và `REMINDED_CAP` (Task 2) khớp `OracleDueReminder` (Task 6); `OracleWalletDao.dueOpenBets(nowMillis)` (Task 3) được `OracleDueReminder` gọi; `NotificationHelper.notifyOracleDue(title, body)`, `ORACLE_DUE_CHANNEL_ID`, `ORACLE_DUE_NOTIFICATION_ID` (Task 6) khớp test; `OracleEvidenceUiState.Idle/Loading/Ready(items)` (Task 7) khớp thẻ và HomeEntry; chuỗi `oracle_due_*` (Task 5) được Task 6 và 7 tham chiếu đúng tên; constructor `OracleBettingViewModel(dao, finder, io, accountId, clock)` (Task 7) khớp mọi chỗ dựng trong test, constructor `@Inject` thứ hai dùng `@ApplicationContext`.

**Rủi ro đã nêu:** điểm semantic của bài mẫu có thể dưới ngưỡng (Task 4 ghi chú: tăng độ trùng từ, không hạ ngưỡng); `setContent` nhiều lần trong một test Compose (Task 7 ghi chú: tách test); test chập chờn do hai luồng Room (chạy 3 lần liên tiếp ở Task 7 Step 7); mutation `.flowOn(io)` có thể sống sót vì dispatcher test là `Unconfined` (ghi nhận và giữ bằng test nguồn); không có sinh trắc/khóa màn hình khi smoke (cổng thiết bị bước 4); kênh thông báo chỉ tắt bằng cài đặt hệ thống nên cần khôi phục trạng thái ban đầu (Task 8 Step 9).
