# ORACLE-01 Predictive Claim Extractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sau mỗi lần sync thành công, AI chạy nền tìm khẳng định dự báo có thể kiểm chứng trong tối đa 5 bài mới (tiêu đề + mô tả ≤ 500 ký tự), lưu thành kèo Yes/No trong bảng Room `oracle_market`, và hiện huy hiệu "Kèo Dự Đoán" ở danh sách và màn đọc; bấm huy hiệu mở thẻ chỉ xem.

**Architecture:** Phần thuần `OracleClaimPolicy` (hằng số, sanitize, prompt, parse + kiểm hợp lệ có `now`) tách khỏi I/O. `OracleClaimExtractor` (Hilt `@Singleton`) kiểm consent, chọn ứng viên qua `OracleMarketDao`, gọi `AiRequestGateway` mỗi bài một request, dừng cả lượt khi có lỗi. `SyncWorker` gọi extractor trong `runCatching` cạnh semantic indexer. UI theo khuôn Retraction Radar: `OraclePendingFlags`-kiểu `CompositionLocal` do `HomeEntry` cấp, `OracleBadge`, `OracleClaimSheet` (Dialog).

**Tech Stack:** Kotlin, Room v21, Compose Material3, Hilt, DataStore, `AiRequestGateway`, org.json, `java.time`, JUnit4 + MockK + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-12-oracle-01-claim-extractor-design.md` (commit `94da3d1a`). Owner đã duyệt từng phần.
- **Một chỉnh nhỏ so với spec (cần ghi vào spec ở Task 12):** chỉ xét bài trong cửa sổ `RECENT_WINDOW_DAYS = 3` ngày gần nhất, để lần bật consent đầu tiên không quét tồn đọng cũ và đốt quota. Mọi lỗi gọi AI (không chỉ quota/khóa) đều dừng cả lượt và không ghi gì cho bài đó.
- **Dữ liệu gửi Gemini:** chỉ tiêu đề + mô tả ≤ **500** ký tự (bỏ HTML, không cắt giữa surrogate), tối đa **5** bài mỗi lần sync. Không gửi thân bài, link, nguồn, tài khoản.
- Consent **riêng**: khóa `oracle_claims_gemini_consent` (`Boolean?`, null = chưa hỏi), mặc định **TẮT**; không đụng `podcast_studio_`, `quiz_`, `deep_dive_gemini_consent`. Bật công tắc ở Cài đặt → Quyền riêng tư là hành động đồng ý.
- **Room v21** + `MIGRATION_20_21` chỉ `CREATE TABLE/INDEX IF NOT EXISTS` (bảng `oracle_market`), không sửa bảng cũ, không `fallbackToDestructiveMigration`, xuất `app/schemas/.../21.json`.
- Bảng `oracle_market`: `id` PK tự tăng, `articleId` FK→`article(id)` CASCADE **unique**, `status` (`PENDING`/`HAS_CLAIM`/`NO_CLAIM`), `attempts`, `question`, `expiresAt`, `resolutionCriteria`, `createdAt`. Không cột tiền/điểm/cược.
- Hằng số: `MAX_INPUT_CHARS=500`, `MAX_ARTICLES_PER_RUN=5`, `MAX_ATTEMPTS=2`, `MAX_QUESTION_CHARS=160`, `MAX_CRITERIA_CHARS=240`, `MAX_HORIZON_YEARS=3`, `RETENTION_DAYS=30`, `RECENT_WINDOW_DAYS=3`, use case `oracle_claim`.
- Không dependency mới, không quảng cáo/IAP/tiền, không đặt cược/ví/cộng đồng (ORACLE-02/03/04), manifest không đổi.
- UI chỉ xem; thẻ có nhãn "Do AI soạn từ tiêu đề và mô tả; có thể sai; chưa tham gia cược được". Animation lấp lánh tắt khi hệ thống tắt animation (`ANIMATOR_DURATION_SCALE == 0`). Mọi phần tử tương tác có `contentDescription`, đích chạm ≥ 48 dp cho nút trong thẻ.
- Chuỗi mới đủ **38 locale** (EN và VI viết tay, 36 còn lại English); không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` xanh. Thêm bằng **một script all-or-none** ở Task 9 (mẫu `/tmp/f01_add_strings.py`).
- Code: không `!!`/`late` thiếu lý do, không magic number (hằng có tên), mọi Job huỷ khi ViewModel clear, log không chứa tiêu đề/mô tả/nội dung kèo (chỉ loại exception hoặc lý do từ chối).
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML kết quả), code tối thiểu, xanh, mutation cho guard quan trọng (backup + `cmp`), commit riêng từng task, không chain `git commit` sau build nếu chưa kiểm exit code. Ghi output Gradle vào file. `grep -r` có dấu nháy. Không dùng `echo ====` trong zsh. Test giới hạn phải có test **số cứng** (không chỉ so với hằng).
- Bài học: (1) đổi constructor `@HiltWorker`/`@Inject`/`@HiltViewModel` thì biên dịch lại sạch: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`; (2) test ViewModel có DataStore dùng dispatcher unconfined + chờ thời gian thực có hạn, không `StandardTestDispatcher` nếu cần chờ flow; (3) `*/` trong KDoc đóng comment; (4) `HorizontalDivider` không có, dùng `Divider`; (5) FileProvider/Robolectric: không liên quan task này; (6) Gradle test JVM có thể treo: kill "Gradle Test Executor".
- Thiết bị: **chỉ Tecno KJ7 `115333744A005844`** (Android 14). Không chạm Pixel `2B051FDH3006MU`, BG6, S24U, A50s. Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation, `topResumedActivity`), `ps` tiến trình lạ; dừng và hỏi nếu có. Chỉ `force-stop com.mckimquyen.reader`; `run-as` không `--user`; sao lưu byte-exact Room (+`-shm`/`-wal`), `files/datastore/settings.preferences_pb`, `shared_prefs/*.xml`; không xóa DataStore; không dùng `connected*` (dùng `adb -s <serial> shell am instrument` với `timeout`); màn hình khóa thì nhờ owner mở. Quảng cáo toàn màn hình: chỉ Back, không bấm "Continue to app".
- **Không có key Gemini riêng** (owner 2026-11-12): không gọi Gemini thật. Key chung hỏng (FIX-09). Báo cáo phải nói rõ phần chưa kiểm.
- Báo cáo `doc/task/done/ORACLE-01_ai-predictive-claim-extractor_DONE.md`; task gốc xử lý theo owner chọn ở Task 12; cập nhật `doc/feature.md`. Không push, không PR, không `.idea`.

---

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `ui/ext/ExtDataStore.kt` (sửa) | `DataStoreKeys.OracleClaimsGeminiConsent`, `Context.oracleClaimsGeminiConsent`, `setOracleClaimsGeminiConsent` | 1 |
| `ui/page/setting/privacy/PrivacySettingsViewModel.kt` (sửa) | StateFlow + setter consent | 1 |
| `domain/ai/agents/OracleClaimPolicy.kt` (mới) | Hằng số, sanitize, prompt, body, parse + kiểm (thuần) | 2, 3 |
| `domain/model/oracle/OracleMarket.kt`, `OracleMarketStatus.kt`, `OracleMarketStatusConverters.kt` (mới) | Entity + enum + converter | 4 |
| `domain/repository/OracleMarketDao.kt` (mới) | Ứng viên, upsert, quan sát kèo, dọn | 4 |
| `infrastructure/db/AndroidDb.kt`, `infrastructure/di/DbModule.kt` (sửa), `app/schemas/.../21.json` (sinh) | Room v21, `MIGRATION_20_21`, DAO | 4 |
| `infrastructure/ai/OracleClaimExtractor.kt` (mới) | Consent, chọn bài, gọi AI, ghi, dọn | 5 |
| `domain/sv/SyncWorker.kt` (sửa) | Gọi extractor sau sync | 6 |
| `ui/page/oracle/OracleClaims.kt` (mới) | `OracleClaimCard`, `LocalOracleClaims`, `LocalOpenOracleClaim`, `OracleClaimsViewModel` | 7 |
| `ui/component/oracle/OracleBadge.kt`, `OracleClaimSheet.kt` (mới) | Huy hiệu và thẻ chỉ xem | 7, 8 |
| `ui/page/home/flow/ArticleItem.kt`, `ui/page/home/read/Content.kt`, `ui/page/common/HomeEntry.kt` (sửa) | Gắn huy hiệu và mở thẻ | 8 |
| `res/values*/strings.xml` ×38 (sửa), `PrivacySettingsPage.kt` (sửa) | Chuỗi `oracle_*` (một script) và công tắc | 9 |
| `androidTest/.../OracleMigration21Test.kt`, `OracleBadgeSheetWidgetTest.kt` (mới) | Máy thật KJ7 | 10 |
| tài liệu | Báo cáo, feature.md, spec ghi chú | 12 |

Quy ước lệnh test (mỗi lần chạy):
`./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/o01-<ten>.log 2>&1; echo exit=$? > /tmp/o01-<ten>.exit`
rồi đọc `app/build/test-results/testDevDebugUnitTest/TEST-<FQCN>.xml` (`tests=`, `failures=`, `errors=`, thông điệp lỗi) trước khi tin kết quả.

---

### Task 1: Consent riêng (DataStore + StateFlow trong `PrivacySettingsViewModel`)

Công tắc UI và chuỗi làm ở Task 9 (một lượt cho cả 38 locale để parity không đỏ giữa chừng).

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` (cạnh `DeepDiveGeminiConsent`, dòng ~128-134 và ~689-693)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`
- Test (tạo): `app/src/test/java/com/mckimquyen/reader/ui/ext/OracleClaimsConsentTest.kt`
- Test (sửa): `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt` (thêm 2 test cuối file)

**Interfaces:**
- Produces: `DataStoreKeys.OracleClaimsGeminiConsent` (khóa `oracle_claims_gemini_consent`), `val Context.oracleClaimsGeminiConsent: Boolean?`, `suspend fun Context.setOracleClaimsGeminiConsent(agreed: Boolean)`, `PrivacySettingsViewModel.oracleClaimsGeminiConsent: StateFlow<Boolean>`, `PrivacySettingsViewModel.setOracleClaimsGeminiConsent(agreed: Boolean)`.

- [ ] **Step 1: Viết test đỏ `OracleClaimsConsentTest`**

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
class OracleClaimsConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.oracleClaimsGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        assertEquals(true, context.oracleClaimsGeminiConsent)
        context.setOracleClaimsGeminiConsent(false)
        assertEquals(false, context.oracleClaimsGeminiConsent)
    }

    @Test
    fun isIndependentOfTheOtherGeminiConsents(): Unit = runBlocking {
        context.setQuizGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        context.setDeepDiveGeminiConsent(true)
        assertEquals("other consents must not agree for Oracle", null, context.oracleClaimsGeminiConsent)

        context.setOracleClaimsGeminiConsent(false)
        assertEquals(true, context.quizGeminiConsent)
        assertEquals(true, context.podcastStudioGeminiConsent)
        assertEquals(true, context.deepDiveGeminiConsent)
    }

    @Test
    fun keyNameIsHardCodedAndDistinct() {
        val name = DataStoreKeys.OracleClaimsGeminiConsent.key.name
        assertEquals("oracle_claims_gemini_consent", name)
        assertNotEquals(DataStoreKeys.QuizGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.DeepDiveGeminiConsent.key.name, name)
    }
}
```

Thêm vào cuối `PrivacySettingsViewModelTest` (cùng khuôn hai test Deep Dive; thêm `import com.mckimquyen.reader.ui.ext.oracleClaimsGeminiConsent`):

```kotlin
    @Test
    fun oracleClaimsConsent_isOffUntilTheUserAgreesAndPersistsBothWays() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()
        assertFalse(viewModel.oracleClaimsGeminiConsent.value)
        assertEquals(null, context.oracleClaimsGeminiConsent)

        viewModel.setOracleClaimsGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.oracleClaimsGeminiConsent.first { it } })
        assertEquals(true, context.oracleClaimsGeminiConsent)

        viewModel.setOracleClaimsGeminiConsent(false)
        advanceUntilIdle()
        assertFalse(awaitReal { viewModel.oracleClaimsGeminiConsent.first { !it } })
        assertEquals(false, context.oracleClaimsGeminiConsent)
    }

    @Test
    fun oracleSwitchDoesNotTouchTheDeepDiveSwitch() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()

        viewModel.setOracleClaimsGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.oracleClaimsGeminiConsent.first { it } })
        assertFalse("the Deep Dive switch must stay off", viewModel.deepDiveGeminiConsent.value)
        assertEquals(null, context.deepDiveGeminiConsent)
    }
```

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.ext.OracleClaimsConsentTest" --tests "com.mckimquyen.reader.ui.page.setting.privacy.PrivacySettingsViewModelTest" > /tmp/o01-t1.log 2>&1; echo exit=$? > /tmp/o01-t1.exit`
Expected: biên dịch lỗi `Unresolved reference: oracleClaimsGeminiConsent` / `OracleClaimsGeminiConsent` (đọc trong `/tmp/o01-t1.log`).

- [ ] **Step 3: Code tối thiểu**

`ExtDataStore.kt`, ngay sau `setDeepDiveGeminiConsent`:

```kotlin
/** ORACLE-01: null until the user decides about background prediction finding; true only when explicitly agreed. */
val Context.oracleClaimsGeminiConsent: Boolean?
    get() = this.dataStore.get(DataStoreKeys.OracleClaimsGeminiConsent)

suspend fun Context.setOracleClaimsGeminiConsent(agreed: Boolean) {
    this.dataStore.put(DataStoreKeys.OracleClaimsGeminiConsent, agreed)
}
```

Trong `sealed class DataStoreKeys`, sau `DeepDiveGeminiConsent`:

```kotlin
    /** ORACLE-01: answer to "send the title and a short description of up to 5 new articles per sync to Gemini to find predictions?". Absent means not asked yet; off by default. */
    object OracleClaimsGeminiConsent : DataStoreKeys<Boolean>() {
        override val key: Preferences.Key<Boolean>
            get() = booleanPreferencesKey("oracle_claims_gemini_consent")
    }
```

`PrivacySettingsViewModel.kt`: thêm

```kotlin
    // ORACLE-01: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _oracleClaimsGeminiConsent = MutableStateFlow(false)
    val oracleClaimsGeminiConsent = _oracleClaimsGeminiConsent.asStateFlow()
```

trong `collect`: `_oracleClaimsGeminiConsent.value = preferences[DataStoreKeys.OracleClaimsGeminiConsent.key] == true`, và hàm:

```kotlin
    fun setOracleClaimsGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.OracleClaimsGeminiConsent.key] = agreed }
            }
        }
    }
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (cùng lệnh Step 2; đọc XML: `failures=0 errors=0`, 4 + số test cũ của `PrivacySettingsViewModelTest` + 2).

- [ ] **Step 5: Mutation (backup + `cmp`)**: đổi tên khóa thành `deep_dive_gemini_consent` → `keyNameIsHardCodedAndDistinct` đỏ; đổi `== true` thành `!= false` trong ViewModel → test "off until agrees" đỏ (null đọc ra true). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/ext/OracleClaimsConsentTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt
git commit -m "feat(oracle-01): add a separate off-by-default Gemini consent for prediction finding"
```
(sau khi kiểm `exit=0` và XML)

---

### Task 2: `OracleClaimPolicy` phần 1 — hằng số, sanitize, prompt, request body (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/OracleClaimPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/ai/agents/OracleClaimPolicyPromptTest.kt`

**Interfaces:**
- Produces: `object OracleClaimPolicy` với `USE_CASE = "oracle_claim"`, `MAX_INPUT_CHARS = 500`, `MAX_ARTICLES_PER_RUN = 5`, `MAX_ATTEMPTS = 2`, `MAX_QUESTION_CHARS = 160`, `MAX_CRITERIA_CHARS = 240`, `MAX_HORIZON_YEARS = 3`, `RETENTION_DAYS = 30`, `RECENT_WINDOW_DAYS = 3`; `fun sanitizeInput(title: String, description: String): String` (trả đoạn đã làm sạch, ≤ 500 ký tự gồm cả tiêu đề); `fun hasContentToAnalyze(input: String): Boolean`; `fun buildPrompt(input: String, languageTag: String): String`; `fun buildRequestBody(prompt: String): String`.

Quy tắc `sanitizeInput`: ghép `"$title\n$description"` sau khi mỗi phần bỏ thẻ HTML, gộp khoảng trắng, trim; nếu một phần rỗng thì bỏ nó (không để dòng trống); tổng ≤ 500 ký tự, không cắt giữa surrogate; tiêu đề luôn được giữ trước, mô tả chỉ lấy phần còn lại.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleClaimPolicyPromptTest {

    @Test
    fun limits_areHardCodedNumbers() {
        assertEquals("oracle_claim", OracleClaimPolicy.USE_CASE)
        assertEquals(500, OracleClaimPolicy.MAX_INPUT_CHARS)
        assertEquals(5, OracleClaimPolicy.MAX_ARTICLES_PER_RUN)
        assertEquals(2, OracleClaimPolicy.MAX_ATTEMPTS)
        assertEquals(160, OracleClaimPolicy.MAX_QUESTION_CHARS)
        assertEquals(240, OracleClaimPolicy.MAX_CRITERIA_CHARS)
        assertEquals(3, OracleClaimPolicy.MAX_HORIZON_YEARS)
        assertEquals(30, OracleClaimPolicy.RETENTION_DAYS)
        assertEquals(3, OracleClaimPolicy.RECENT_WINDOW_DAYS)
    }

    @Test
    fun sanitize_stripsHtmlAndCollapsesWhitespace() {
        val s = OracleClaimPolicy.sanitizeInput("  Rates <b>rise</b> ", "<p>The   bank\n will  act.</p>")
        assertEquals("Rates rise\nThe bank will act.", s)
    }

    @Test
    fun sanitize_dropsAnEmptyPartWithoutABlankLine() {
        assertEquals("Only title", OracleClaimPolicy.sanitizeInput("Only title", "  <br> "))
        assertEquals("Only description", OracleClaimPolicy.sanitizeInput(" ", "Only description"))
    }

    @Test
    fun sanitize_capsTheWholeInputAt500_keepingTheTitleFirst() {
        val s = OracleClaimPolicy.sanitizeInput("T".repeat(100), "d".repeat(900))
        assertEquals(500, s.length)
        assertTrue(s.startsWith("T".repeat(100) + "\n"))
    }

    @Test
    fun sanitize_aTitleLongerThanTheCapIsCutAt500() {
        assertEquals(500, OracleClaimPolicy.sanitizeInput("x".repeat(800), "ignored").length)
    }

    @Test
    fun sanitize_neverLeavesALoneHighSurrogateAtTheCut() {
        // 499 ASCII chars + one emoji (2 UTF-16 units): the cut at 500 would split the pair.
        val s = OracleClaimPolicy.sanitizeInput("a".repeat(499) + "😀", "")
        assertEquals(499, s.length)
        assertFalse(Character.isHighSurrogate(s.last()))
    }

    @Test
    fun hasContent_isFalseForBlank() {
        assertFalse(OracleClaimPolicy.hasContentToAnalyze(""))
        assertFalse(OracleClaimPolicy.hasContentToAnalyze("   "))
        assertTrue(OracleClaimPolicy.hasContentToAnalyze("x"))
    }

    @Test
    fun prompt_carriesOnlyTheInputAndTheLanguage() {
        val p = OracleClaimPolicy.buildPrompt("Rates rise\nThe bank will act.", "vi-VN")
        assertTrue(p.contains("Rates rise\nThe bank will act."))
        assertTrue(p.contains("vi-VN"))
        assertFalse(p.contains("http"))
    }

    @Test
    fun prompt_forbidsInventingAndAllowsNoClaim() {
        val p = OracleClaimPolicy.buildPrompt("x", "en")
        assertTrue(p.contains("hasClaim"))
        assertTrue(p.contains("Do not invent"))
        assertTrue(p.contains("no web access"))
        assertTrue(p.contains("yyyy-MM-dd"))
        assertTrue(p.contains("${OracleClaimPolicy.MAX_QUESTION_CHARS}"))
    }

    @Test
    fun requestBody_isJsonModeAndCarriesThePrompt() {
        val body = JSONObject(OracleClaimPolicy.buildRequestBody("PROMPT"))
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("PROMPT", text)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: OracleClaimPolicy`).

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the background prediction finder (ORACLE-01): what may leave the device, how the request looks and which
 * answers are accepted. Privacy boundary: the only article data reachable from here is the title and a short
 * description, together at most [MAX_INPUT_CHARS]; the caller never hands over a link, a feed name or an account.
 */
object OracleClaimPolicy {
    const val USE_CASE = "oracle_claim"
    const val MAX_INPUT_CHARS = 500
    const val MAX_ARTICLES_PER_RUN = 5
    const val MAX_ATTEMPTS = 2
    const val MAX_QUESTION_CHARS = 160
    const val MAX_CRITERIA_CHARS = 240
    const val MAX_HORIZON_YEARS = 3
    const val RETENTION_DAYS = 30
    const val RECENT_WINDOW_DAYS = 3
    private const val TEMPERATURE = 0.2
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeInput(title: String, description: String): String {
        val parts = listOf(title, description).map { it.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
        val joined = parts.joinToString("\n")
        if (joined.length <= MAX_INPUT_CHARS) return joined
        var end = MAX_INPUT_CHARS
        // Never cut between the two halves of a surrogate pair.
        if (Character.isHighSurrogate(joined[end - 1])) end--
        return joined.substring(0, end).trim()
    }

    fun hasContentToAnalyze(input: String): Boolean = input.isNotBlank()

    fun buildPrompt(input: String, languageTag: String): String = buildString {
        append("You find a verifiable prediction in ONE news item (title, then a short description).\n")
        append("Use ONLY the text below; no web access, no browsing, no sources.\n")
        append("Do not invent dates, figures, names or events that the text does not state.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown.\n")
        append("If the text states no future event that can later be checked as true or false, output {\"hasClaim\": false}.\n")
        append("Otherwise output {\"hasClaim\": true, \"question\": \"...\", \"expiresOn\": \"yyyy-MM-dd\", \"resolutionCriteria\": \"...\"} where:\n")
        append("\"question\" is a Yes/No question ending with a question mark, at most $MAX_QUESTION_CHARS characters;\n")
        append("\"expiresOn\" is the date the event is expected by, in the future, as yyyy-MM-dd;\n")
        append("\"resolutionCriteria\" says how anyone can later check the answer, at most $MAX_CRITERIA_CHARS characters.\n")
        append("Write the question and criteria in the language with tag $languageTag.\n\n")
        append("Text:\n")
        append(input)
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (10 test).

- [ ] **Step 5: Mutation (backup + `cmp`)**: `MAX_INPUT_CHARS` 500→501 (test số cứng đỏ); bỏ dòng `if (Character.isHighSurrogate…) end--` (test surrogate đỏ); bỏ `.filter { it.isNotEmpty() }` (test "empty part" đỏ); đổi `responseMimeType` giá trị (test body đỏ); xóa dòng "Do not invent" (test prompt đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit** `feat(oracle-01): add the claim policy limits, sanitising and prompt`.

---

### Task 3: `OracleClaimPolicy` phần 2 — parse và kiểm hợp lệ (thuần)

Ngày xét theo **ngày UTC**: `today` = ngày UTC của `now`. Hợp lệ khi `today <= expiresOn <= today + 3 năm`. `expiresAt` = cuối ngày `expiresOn` theo UTC (`23:59:59.999`).

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/OracleClaimPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/ai/agents/OracleClaimPolicyParseTest.kt`

**Interfaces:**
- Consumes: hằng số Task 2.
- Produces: `enum class OracleRejection { NOT_JSON, MISSING_FIELD, QUESTION_NOT_YES_NO, QUESTION_TOO_LONG, CRITERIA_EMPTY, CRITERIA_TOO_LONG, BAD_DATE, DATE_IN_PAST, DATE_TOO_FAR }`; `sealed interface OracleParse { data class Claim(val question: String, val expiresAt: Long, val resolutionCriteria: String); data object NoClaim; data class Rejected(val reason: OracleRejection) }`; `OracleClaimPolicy.parseAndValidate(raw: String, nowMillis: Long): OracleParse`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OracleClaimPolicyParseTest {

    // 2026-11-12T10:00:00Z
    private val now = LocalDate.of(2026, 11, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10 * 3_600_000L

    private fun endOfDay(date: String): Long =
        LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1

    private fun json(
        question: String = "Will the bank cut rates by 2026-12-31?",
        on: String = "2026-12-31",
        criteria: String = "The bank's own press release.",
    ) = """{"hasClaim":true,"question":"$question","expiresOn":"$on","resolutionCriteria":"$criteria"}"""

    private fun parse(raw: String) = OracleClaimPolicy.parseAndValidate(raw, now)

    private fun rejected(raw: String): OracleRejection = (parse(raw) as OracleParse.Rejected).reason

    @Test
    fun validClaim_isAccepted_withEndOfDayUtc() {
        val c = parse(json()) as OracleParse.Claim
        assertEquals("Will the bank cut rates by 2026-12-31?", c.question)
        assertEquals(endOfDay("2026-12-31"), c.expiresAt)
        assertEquals("The bank's own press release.", c.resolutionCriteria)
    }

    @Test
    fun hasClaimFalse_isNoClaim() {
        assertEquals(OracleParse.NoClaim, parse("""{"hasClaim":false}"""))
    }

    @Test
    fun markdownFence_isStripped() {
        assertTrue(parse("```json\n${json()}\n```") is OracleParse.Claim)
    }

    @Test
    fun notJson_isRejected() {
        assertEquals(OracleRejection.NOT_JSON, rejected("Sure! Here you go"))
        assertEquals(OracleRejection.NOT_JSON, rejected(""))
    }

    @Test
    fun missingHasClaim_orNonBoolean_isMissingField() {
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"question":"Q?"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":"yes"}"""))
    }

    @Test
    fun claimWithAMissingField_isMissingField() {
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"expiresOn":"2026-12-31","resolutionCriteria":"c"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"question":"Q?","resolutionCriteria":"c"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"question":"Q?","expiresOn":"2026-12-31"}"""))
    }

    @Test
    fun question_mustBeYesNoShaped_endingInAQuestionMark() {
        assertEquals(OracleRejection.QUESTION_NOT_YES_NO, rejected(json(question = "The bank will cut rates")))
        assertEquals(OracleRejection.QUESTION_NOT_YES_NO, rejected(json(question = "   ")))
        assertTrue(parse(json(question = "Will it launch？")) is OracleParse.Claim)
    }

    @Test
    fun question_lengthBoundary_is160() {
        val ok = "a".repeat(159) + "?"
        val tooLong = "a".repeat(160) + "?"
        assertEquals(160, ok.length)
        assertTrue(parse(json(question = ok)) is OracleParse.Claim)
        assertEquals(OracleRejection.QUESTION_TOO_LONG, rejected(json(question = tooLong)))
    }

    @Test
    fun criteria_emptyAndLengthBoundary_is240() {
        assertEquals(OracleRejection.CRITERIA_EMPTY, rejected(json(criteria = "  ")))
        assertTrue(parse(json(criteria = "c".repeat(240))) is OracleParse.Claim)
        assertEquals(OracleRejection.CRITERIA_TOO_LONG, rejected(json(criteria = "c".repeat(241))))
    }

    @Test
    fun date_badFormat_isBadDate() {
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "next month")))
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "31/12/2026")))
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "2026-02-30")))
    }

    @Test
    fun date_pastIsRejected_todayIsAccepted() {
        assertEquals(OracleRejection.DATE_IN_PAST, rejected(json(on = "2026-11-11")))
        val today = parse(json(on = "2026-11-12")) as OracleParse.Claim
        assertEquals(endOfDay("2026-11-12"), today.expiresAt)
        assertTrue(today.expiresAt > now)
    }

    @Test
    fun date_horizonBoundary_isThreeYears() {
        assertTrue(parse(json(on = "2029-11-12")) is OracleParse.Claim)
        assertEquals(OracleRejection.DATE_TOO_FAR, rejected(json(on = "2029-11-13")))
    }

    @Test
    fun text_isTidied() {
        val c = parse(json(question = "  Will   it\\nlaunch?  ", criteria = "  see   the\\nnotice ")) as OracleParse.Claim
        assertEquals("Will it launch?", c.question)
        assertEquals("see the notice", c.resolutionCriteria)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: parseAndValidate`, `OracleParse`).

- [ ] **Step 3: Code tối thiểu** — thêm vào `OracleClaimPolicy.kt` (trên `object`: hai khai báo `enum`/`sealed`; trong `object`: hàm và regex):

```kotlin
enum class OracleRejection {
    NOT_JSON, MISSING_FIELD, QUESTION_NOT_YES_NO, QUESTION_TOO_LONG, CRITERIA_EMPTY, CRITERIA_TOO_LONG,
    BAD_DATE, DATE_IN_PAST, DATE_TOO_FAR,
}

sealed interface OracleParse {
    data class Claim(val question: String, val expiresAt: Long, val resolutionCriteria: String) : OracleParse
    data object NoClaim : OracleParse
    data class Rejected(val reason: OracleRejection) : OracleParse
}
```

```kotlin
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")
    private val QUESTION_MARKS = charArrayOf('?', '？')

    /** Structure and dates only: nothing here can tell whether the claim is true. */
    fun parseAndValidate(raw: String, nowMillis: Long): OracleParse {
        fun reject(r: OracleRejection): OracleParse = OracleParse.Rejected(r)
        val obj = try {
            JSONObject(raw.trim().replace(FENCE, "").trim())
        } catch (_: JSONException) {
            return reject(OracleRejection.NOT_JSON)
        }
        val hasClaim = obj.opt("hasClaim") as? Boolean ?: return reject(OracleRejection.MISSING_FIELD)
        if (!hasClaim) return OracleParse.NoClaim

        val question = (obj.opt("question") as? String)?.let(::tidy) ?: return reject(OracleRejection.MISSING_FIELD)
        val onText = obj.opt("expiresOn") as? String ?: return reject(OracleRejection.MISSING_FIELD)
        val criteria = (obj.opt("resolutionCriteria") as? String)?.let(::tidy) ?: return reject(OracleRejection.MISSING_FIELD)

        if (question.isEmpty() || question.last() !in QUESTION_MARKS) return reject(OracleRejection.QUESTION_NOT_YES_NO)
        if (question.length > MAX_QUESTION_CHARS) return reject(OracleRejection.QUESTION_TOO_LONG)
        if (criteria.isEmpty()) return reject(OracleRejection.CRITERIA_EMPTY)
        if (criteria.length > MAX_CRITERIA_CHARS) return reject(OracleRejection.CRITERIA_TOO_LONG)

        val date = try {
            LocalDate.parse(onText.trim())
        } catch (_: DateTimeParseException) {
            return reject(OracleRejection.BAD_DATE)
        }
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate()
        if (date.isBefore(today)) return reject(OracleRejection.DATE_IN_PAST)
        if (date.isAfter(today.plusYears(MAX_HORIZON_YEARS.toLong()))) return reject(OracleRejection.DATE_TOO_FAR)
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
        return OracleParse.Claim(question, endOfDay, criteria)
    }

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()
```

Imports thêm: `org.json.JSONException`, `java.time.Instant`, `java.time.LocalDate`, `java.time.ZoneOffset`, `java.time.format.DateTimeParseException`. (`java.time` dùng được với minSdk 26.)

- [ ] **Step 4: Chạy lại, xác nhận xanh** (13 test mới + 10 cũ). Lưu ý: test `text_isTidied` dùng `\\n` trong chuỗi JSON thô để JSON tự giải mã thành xuống dòng.

- [ ] **Step 5: Mutation (backup + `cmp`)**: `isBefore`→`isBefore` + 1 ngày (today bị loại → test "today accepted" đỏ); `isAfter(today.plusYears` đổi thành `plusYears(4)` (test biên 3 năm đỏ); `> MAX_QUESTION_CHARS` thành `>=` (test biên 160 đỏ); `last() !in QUESTION_MARKS` bỏ ký tự full-width (test `？` đỏ); `endOfDay` bỏ `- 1` (test end-of-day đỏ); bỏ `.replace(FENCE…)` (test fence đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit** `feat(oracle-01): validate the AI answer, its dates and its lengths`.

---

### Task 4: Bảng `oracle_market`, DAO, Room v21 và `MIGRATION_20_21`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/oracle/OracleMarket.kt` (entity + `OracleMarketStatus` + converter)
- Create: `app/src/main/java/com/mckimquyen/reader/domain/repository/OracleMarketDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt` (entities, `version = 21`, `@TypeConverters`, `abstract fun oracleMarketDao()`, `allMigrations`, `MIGRATION_20_21`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/DbModule.kt` (provide DAO, cạnh `provideArticleRadarDao`)
- Sinh: `app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/21.json`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration20To21Test.kt`, `app/src/test/java/com/mckimquyen/reader/domain/repository/OracleMarketDaoTest.kt`

**Interfaces:**
- Produces:
  - `enum class OracleMarketStatus { PENDING, HAS_CLAIM, NO_CLAIM }`, `class OracleMarketStatusConverters` (`toStorage(value): String = value.name`, `fromStorage(value): OracleMarketStatus`).
  - `@Entity(tableName = "oracle_market") data class OracleMarket(@PrimaryKey(autoGenerate = true) val id: Long = 0, val articleId: String, val status: OracleMarketStatus, val attempts: Int = 0, val question: String? = null, val expiresAt: Long? = null, val resolutionCriteria: String? = null, val createdAt: Long)`; FK `articleId`→`article(id)` CASCADE; unique index `articleId`.
  - `data class OracleCandidateRow(val id: String, val title: String, val shortDescription: String, val attempts: Int)`.
  - `OracleMarketDao`: `suspend fun candidates(accountId: Int, sinceMillis: Long, limit: Int): List<OracleCandidateRow>`; `suspend fun upsert(market: OracleMarket)` (`REPLACE`); `suspend fun byArticle(articleId: String): OracleMarket?`; `fun observeClaims(): Flow<List<OracleMarket>>` (chỉ `HAS_CLAIM`); `suspend fun deleteStale(cutoffMillis: Long): Int`.

- [ ] **Step 1: Viết test migration đỏ** (khuôn `RoomMigration19To20Test`; bảng `article` giả có `id`):

```kotlin
package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigration20To21Test {

    private fun openAtVersion20(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE article (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO article (id, title) VALUES ('a1', 'Existing article')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun migration20to21_createsOracleMarketAndKeepsArticles() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM article"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        assertEquals(
            listOf("id", "articleId", "status", "attempts", "question", "expiresAt", "resolutionCriteria", "createdAt"),
            columns(db, "oracle_market"),
        )
        assertTrue(indices(db, "oracle_market").contains("index_oracle_market_articleId"))
        db.close()
    }

    @Test
    fun migration20to21_isIdempotent() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        MIGRATION_20_21.migrate(db)
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        db.close()
    }

    @Test
    fun articleIdIsUnique() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 1)")
        val failed = runCatching {
            db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 2)")
        }.isFailure
        assertTrue("a second row for the same article must be refused", failed)
        db.close()
    }

    @Test
    fun deletingTheArticleCascades() {
        val db = openAtVersion20()
        MIGRATION_20_21.migrate(db)
        db.execSQL("INSERT INTO oracle_market (articleId, status, attempts, createdAt) VALUES ('a1', 'NO_CLAIM', 0, 1)")
        db.execSQL("DELETE FROM article WHERE id='a1'")
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_market"))
        db.close()
    }

    @Test
    fun migrationTouchesNothingButOracleMarket() {
        val db = openAtVersion20()
        val before = tableNames(db)
        MIGRATION_20_21.migrate(db)
        assertEquals(before + "oracle_market", tableNames(db))
        db.close()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun indices(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA index_list($table)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> c.moveToFirst(); c.getLong(0) }
}
```

- [ ] **Step 2: Viết test DAO đỏ** (khuôn `RetractionRadarServiceTest`: Room in-memory, Account/Group/Feed như mẫu):

```kotlin
package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleMarketDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleMarketDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        db.feedDao().insert(Feed(id = "f2", name = "F2", url = "https://e.com/f2", groupId = "g2", accountId = 2))
        dao = db.oracleMarketDao()
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, date: Long, account: Int = 1) = Article(
        id = id, date = Date(date), title = "T-$id", rawDescription = "RAW-$id", shortDescription = "S-$id",
        link = "https://e.com/$id", feedId = if (account == 1) "f" else "f2", accountId = account,
    )

    private fun market(id: String, status: OracleMarketStatus, attempts: Int = 0, createdAt: Long = 1L, expiresAt: Long? = null) =
        OracleMarket(articleId = id, status = status, attempts = attempts, question = "Q?".takeIf { status == OracleMarketStatus.HAS_CLAIM },
            expiresAt = expiresAt, resolutionCriteria = "c".takeIf { status == OracleMarketStatus.HAS_CLAIM }, createdAt = createdAt)

    @Test
    fun candidates_areNewestFirst_withinTheWindow_andLimited() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("old", 100), article("a", 1_000), article("b", 2_000), article("c", 3_000))
        val rows = dao.candidates(accountId = 1, sinceMillis = 500, limit = 2)
        assertEquals(listOf("c", "b"), rows.map { it.id })
        assertEquals("T-c", rows.first().title)
        assertEquals("S-c", rows.first().shortDescription)
        assertEquals(listOf("c", "b", "a"), dao.candidates(1, 500, 10).map { it.id })
    }

    @Test
    fun candidates_skipDecidedArticles_butKeepPendingOnesWithTheirAttempts() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("has", 1_000), article("none", 1_000), article("pend", 1_000), article("new", 1_000))
        dao.upsert(market("has", OracleMarketStatus.HAS_CLAIM, expiresAt = 9))
        dao.upsert(market("none", OracleMarketStatus.NO_CLAIM))
        dao.upsert(market("pend", OracleMarketStatus.PENDING, attempts = 1))

        val rows = dao.candidates(1, 0, 10).associateBy { it.id }
        assertEquals(setOf("pend", "new"), rows.keys)
        assertEquals(1, rows.getValue("pend").attempts)
        assertEquals(0, rows.getValue("new").attempts)
    }

    @Test
    fun candidates_onlyTheGivenAccount() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("mine", 1_000, account = 1), article("theirs", 1_000, account = 2))
        assertEquals(listOf("mine"), dao.candidates(1, 0, 10).map { it.id })
    }

    @Test
    fun upsert_replacesTheRowOfTheSameArticle() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("a", 1_000))
        dao.upsert(market("a", OracleMarketStatus.PENDING, attempts = 1))
        dao.upsert(market("a", OracleMarketStatus.HAS_CLAIM, expiresAt = 50))
        val row = dao.byArticle("a")
        assertNotNull(row)
        assertEquals(OracleMarketStatus.HAS_CLAIM, row?.status)
        assertEquals("Q?", row?.question)
        assertEquals(1, db.query("SELECT COUNT(*) FROM oracle_market", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun observeClaims_returnsOnlyRealClaims() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("has", 1_000), article("none", 1_000), article("pend", 1_000))
        dao.upsert(market("has", OracleMarketStatus.HAS_CLAIM, expiresAt = 9))
        dao.upsert(market("none", OracleMarketStatus.NO_CLAIM))
        dao.upsert(market("pend", OracleMarketStatus.PENDING, attempts = 1))
        assertEquals(listOf("has"), dao.observeClaims().first().map { it.articleId })
    }

    @Test
    fun deletingTheArticleCascades() = runBlocking {
        db.articleDao().insertOnConflictIgnore(article("a", 1_000))
        dao.upsert(market("a", OracleMarketStatus.NO_CLAIM))
        db.openHelper.writableDatabase.execSQL("DELETE FROM article WHERE id='a'")
        assertNull(dao.byArticle("a"))
    }

    @Test
    fun deleteStale_dropsExpiredClaimsAndOldUndecidedRowsOnly() = runBlocking {
        val ids = listOf("expired", "live", "oldNone", "freshNone", "oldPend")
        db.articleDao().insertOnConflictIgnore(*ids.map { article(it, 1_000) }.toTypedArray())
        dao.upsert(market("expired", OracleMarketStatus.HAS_CLAIM, createdAt = 500, expiresAt = 900))
        dao.upsert(market("live", OracleMarketStatus.HAS_CLAIM, createdAt = 500, expiresAt = 5_000))
        dao.upsert(market("oldNone", OracleMarketStatus.NO_CLAIM, createdAt = 900))
        dao.upsert(market("freshNone", OracleMarketStatus.NO_CLAIM, createdAt = 2_000))
        dao.upsert(market("oldPend", OracleMarketStatus.PENDING, attempts = 1, createdAt = 100))

        val removed = dao.deleteStale(cutoffMillis = 1_000)

        assertEquals(3, removed)
        assertEquals(listOf("live"), dao.observeClaims().first().map { it.articleId })
        assertNotNull(dao.byArticle("freshNone"))
        assertNull(dao.byArticle("expired"))
    }
}
```

- [ ] **Step 3: Chạy, xác nhận đỏ** (`Unresolved reference: MIGRATION_20_21`, `oracleMarketDao`, `OracleMarket`).

- [ ] **Step 4: Code tối thiểu**

`OracleMarket.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.mckimquyen.reader.domain.model.article.Article

/** PENDING: the AI answer was rejected but attempts remain; HAS_CLAIM: a usable prediction; NO_CLAIM: nothing to ask again. */
enum class OracleMarketStatus { PENDING, HAS_CLAIM, NO_CLAIM }

class OracleMarketStatusConverters {
    @TypeConverter
    fun toStorage(value: OracleMarketStatus): String = value.name

    @TypeConverter
    fun fromStorage(value: String): OracleMarketStatus = OracleMarketStatus.valueOf(value)
}

/** One row per article the prediction finder has looked at (ORACLE-01). No points, bets or money live here. */
@Entity(
    tableName = "oracle_market",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId"], unique = true)],
)
@Keep
data class OracleMarket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: String,
    val status: OracleMarketStatus,
    val attempts: Int = 0,
    val question: String? = null,
    val expiresAt: Long? = null,
    val resolutionCriteria: String? = null,
    val createdAt: Long,
)
```

`OracleMarketDao.kt`:

```kotlin
package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import kotlinx.coroutines.flow.Flow

/** Title and short description only: no body column is ever read for the prediction finder (FIX-17). */
data class OracleCandidateRow(val id: String, val title: String, val shortDescription: String, val attempts: Int)

@Dao
interface OracleMarketDao {

    /** Newest articles since [sinceMillis] that are undecided: no row yet, or a PENDING row with attempts left. */
    @Query(
        """
        SELECT a.id AS id, a.title AS title, a.shortDescription AS shortDescription, COALESCE(m.attempts, 0) AS attempts
        FROM article a LEFT JOIN oracle_market m ON m.articleId = a.id
        WHERE a.accountId = :accountId AND a.date >= :sinceMillis AND (m.id IS NULL OR m.status = 'PENDING')
        ORDER BY a.date DESC, a.id DESC LIMIT :limit
        """
    )
    suspend fun candidates(accountId: Int, sinceMillis: Long, limit: Int): List<OracleCandidateRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(market: OracleMarket)

    @Query("SELECT * FROM oracle_market WHERE articleId = :articleId")
    suspend fun byArticle(articleId: String): OracleMarket?

    @Query("SELECT * FROM oracle_market WHERE status = 'HAS_CLAIM'")
    fun observeClaims(): Flow<List<OracleMarket>>

    /** Drops claims that expired before [cutoffMillis] and undecided rows created before it; returns the number removed. */
    @Query(
        """
        DELETE FROM oracle_market
        WHERE (status = 'HAS_CLAIM' AND expiresAt < :cutoffMillis)
           OR (status != 'HAS_CLAIM' AND createdAt < :cutoffMillis)
        """
    )
    suspend fun deleteStale(cutoffMillis: Long): Int
}
```

`AndroidDb.kt`: thêm `OracleMarket::class` vào `entities`, `version = 21`, `OracleMarketStatusConverters::class` vào `@TypeConverters`, `abstract fun oracleMarketDao(): OracleMarketDao`, `MIGRATION_20_21` cuối `allMigrations`, và:

```kotlin
@Suppress("ClassName")
object MIGRATION_20_21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `oracle_market` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `articleId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `attempts` INTEGER NOT NULL,
                `question` TEXT,
                `expiresAt` INTEGER,
                `resolutionCriteria` TEXT,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`articleId`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_oracle_market_articleId` ON `oracle_market` (`articleId`)")
    }
}
```

`DbModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun provideOracleMarketDao(androidDatabase: AndroidDatabase): OracleMarketDao =
        androidDatabase.oracleMarketDao()
```

- [ ] **Step 5: Sinh schema và đối chiếu SQL migration với `21.json`**

Run: `./gradlew :app:kaptDevDebugKotlin > /tmp/o01-t4-kapt.log 2>&1; echo exit=$?` (exit 0). Rồi so `createSql` của `oracle_market` trong `app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/21.json` với SQL trong `MIGRATION_20_21` (cột, thứ tự, `NOT NULL`, FK, chỉ mục): phải giống từng ký tự sau khi bỏ khoảng trắng (Room kiểm khi mở DB). Sai thì sửa migration, không sửa entity.

- [ ] **Step 6: Chạy lại hai test, xác nhận xanh** (`RoomMigration20To21Test` 5, `OracleMarketDaoTest` 7).

- [ ] **Step 7: Mutation (backup + `cmp`)**: bỏ `UNIQUE` ở index migration (test `articleIdIsUnique` đỏ); bỏ `ON DELETE CASCADE` trong migration (test cascade đỏ); đổi `m.status = 'PENDING'` thành `m.status != 'NO_CLAIM'` (test "skip decided" đỏ vì HAS_CLAIM lọt vào); bỏ `ORDER BY ... DESC` thành ASC (test thứ tự đỏ); đổi `<` thành `<=` ở `deleteStale` nhánh `createdAt` (cần dòng có `createdAt == cutoff`: thêm một hàng `edge` với `createdAt = 1_000` trong test `deleteStale` và kỳ vọng còn lại; nếu chưa có thì thêm trước khi chạy mutation). Khôi phục bằng `cmp`.

- [ ] **Step 8: Commit** (kiểm exit code): `feat(oracle-01): add the oracle_market table, DAO and Room v21 migration` — gồm `21.json`.

---

### Task 5: `OracleClaimExtractor` — consent, chọn bài, một request mỗi bài, ghi kết quả, dọn

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleClaimExtractor.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/OracleClaimExtractorTest.kt`

**Interfaces:**
- Consumes: `OracleClaimPolicy` + `OracleParse` (Task 2, 3), `OracleMarketDao`, `OracleMarket`, `OracleMarketStatus` (Task 4), `Context.oracleClaimsGeminiConsent` (Task 1), `AiRequestGateway.execute(useCase, requestBody, keys)`, `GeminiSummaryService.resolveApiKeys()`.
- Produces: `data class OracleRunResult(val requests: Int, val claims: Int, val stopped: Boolean)`; `open class OracleClaimExtractor @Inject constructor(@ApplicationContext context: Context, dao: OracleMarketDao, gateway: AiRequestGateway, summaryService: GeminiSummaryService)` (`@Singleton`) với `suspend fun extractRecent(accountId: Int): OracleRunResult`, `protected open fun isConsented(): Boolean`, `protected open fun nowMillis(): Long`.

Quy tắc `extractRecent`:
1. Dọn trước, luôn luôn (kể cả khi consent tắt, vì chỉ xóa dữ liệu cục bộ): `dao.deleteStale(now - RETENTION_DAYS ngày)`; lỗi dọn chỉ log loại exception, không làm hỏng bước sau.
2. Consent không phải `true` → trả `OracleRunResult(0, 0, false)`, 0 request, không gọi `resolveApiKeys`.
3. `resolveApiKeys()` rỗng → `OracleRunResult(0, 0, true)`.
4. Ứng viên: `dao.candidates(accountId, now - RECENT_WINDOW_DAYS ngày, MAX_ARTICLES_PER_RUN)`.
5. Với mỗi ứng viên: `input = sanitizeInput(title, shortDescription)`; rỗng → ghi `NO_CLAIM`, không request. Ngược lại đúng 1 request (use case `oracle_claim`). `CancellationException` ném lại. Mọi exception khác (`SummaryException`, mạng, `IOException`…) → log **chỉ tên lớp**, **dừng cả lượt**, không ghi gì cho bài đó, trả `stopped = true`.
6. `Claim` → `HAS_CLAIM` (question, expiresAt, criteria, `attempts` giữ nguyên); `NoClaim` → `NO_CLAIM`; `Rejected` → `attempts + 1`: nếu `>= MAX_ATTEMPTS` thì `NO_CLAIM`, ngược lại `PENDING`. Log lý do từ chối (enum), không log nội dung.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.setOracleClaimsGeminiConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleClaimExtractorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var db: AndroidDatabase
    private var consented = true

    // 2026-11-12T10:00:00Z
    private val now = LocalDate.of(2026, 11, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10 * HOUR
    private val day = 24 * HOUR

    private lateinit var extractor: OracleClaimExtractor

    private fun claimJson(on: String = "2026-12-31") =
        """{"hasClaim":true,"question":"Will it ship?","expiresOn":"$on","resolutionCriteria":"Vendor release notes."}"""

    @Before
    fun setUp() = runBlocking {
        context.dataStore.edit { it.clear() }
        ShadowLog.clear()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        coEvery { gateway.execute(any(), any(), any()) } returns claimJson()
        extractor = object : OracleClaimExtractor(context, db.oracleMarketDao(), gateway, summaryService) {
            override fun isConsented() = consented
            override fun nowMillis() = now
        }
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, ageMillis: Long = HOUR, title: String = "T-$id", desc: String = "S-$id") = Article(
        id = id, date = Date(now - ageMillis), title = title, rawDescription = "RAW-$id", shortDescription = desc,
        link = "https://secret.example/$id", feedId = "f", accountId = 1,
    )

    private fun put(vararg a: Article) = runBlocking { db.articleDao().insertOnConflictIgnore(*a) }
    private fun run() = runBlocking { extractor.extractRecent(1) }
    private fun row(id: String) = runBlocking { db.oracleMarketDao().byArticle(id) }

    @Test
    fun consentOff_makesNoRequestAndWritesNothing() {
        put(article("a"))
        consented = false
        val r = run()
        assertEquals(OracleRunResult(0, 0, false), r)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
        verifyNoKeyLookup()
        assertNull(row("a"))
    }

    private fun verifyNoKeyLookup() = io.mockk.verify(exactly = 0) { summaryService.resolveApiKeys() }

    @Test
    fun realConsentReader_neverAskedAndDeclinedMeanOff_agreedMeansOn() {
        val real = object : OracleClaimExtractor(context, db.oracleMarketDao(), gateway, summaryService) {
            override fun nowMillis() = now
        }
        put(article("a"))
        assertEquals(0, runBlocking { real.extractRecent(1) }.requests)

        runBlocking { context.setOracleClaimsGeminiConsent(false) }
        assertEquals(0, runBlocking { real.extractRecent(1) }.requests)

        runBlocking { context.setOracleClaimsGeminiConsent(true) }
        assertEquals(1, runBlocking { real.extractRecent(1) }.requests)
    }

    @Test
    fun noKey_makesNoRequestAndReportsStopped() {
        put(article("a"))
        every { summaryService.resolveApiKeys() } returns emptyList()
        assertEquals(OracleRunResult(0, 0, true), run())
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun sendsAtMostFiveArticles_newestFirst() {
        put(*(1..8).map { article("a$it", ageMillis = it * HOUR) }.toTypedArray())
        val r = run()
        assertEquals(5, r.requests)
        coVerify(exactly = 5) { gateway.execute("oracle_claim", any(), listOf("key-a")) }
        (1..5).forEach { assertEquals("a$it", "a$it", row("a$it")?.articleId) }
        (6..8).forEach { assertNull(row("a$it")) }
    }

    @Test
    fun onlyArticlesFromTheLastThreeDays_areConsidered() {
        put(article("fresh", ageMillis = 2 * day), article("stale", ageMillis = 4 * day))
        assertEquals(1, run().requests)
        assertNull(row("stale"))
        assertEquals(OracleMarketStatus.HAS_CLAIM, row("fresh")?.status)
    }

    @Test
    fun requestBody_holdsTheTitleAndDescriptionButNeverTheBodyOrTheLink() {
        put(article("a", title = "Chipmaker plans fab", desc = "<p>Opens next year.</p>"))
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns claimJson()
        run()
        assertTrue(body.captured.contains("Chipmaker plans fab"))
        assertTrue(body.captured.contains("Opens next year."))
        assertFalse(body.captured.contains("RAW-a"))
        assertFalse(body.captured.contains("secret.example"))
        assertFalse(body.captured.contains("<p>"))
    }

    @Test
    fun claim_isStoredWithItsFields() {
        put(article("a"))
        val r = run()
        assertEquals(OracleRunResult(1, 1, false), r)
        val m = row("a")!!
        assertEquals(OracleMarketStatus.HAS_CLAIM, m.status)
        assertEquals("Will it ship?", m.question)
        assertEquals("Vendor release notes.", m.resolutionCriteria)
        assertEquals(LocalDate.of(2026, 12, 31).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1, m.expiresAt)
        assertEquals(now, m.createdAt)
    }

    @Test
    fun noClaim_isRememberedSoItIsNeverAskedAgain() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returns """{"hasClaim":false}"""
        assertEquals(OracleRunResult(1, 0, false), run())
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)

        assertEquals(0, run().requests)
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun decidedArticles_areNotAskedAgain() {
        put(article("a"))
        run()
        assertEquals(0, run().requests)
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun rejectedAnswer_isPendingThenGivenUpAfterTwoAttempts() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returns "not json"

        run()
        assertEquals(OracleMarketStatus.PENDING, row("a")?.status)
        assertEquals(1, row("a")?.attempts)

        run()
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)
        assertEquals(2, row("a")?.attempts)

        assertEquals(0, run().requests)
        coVerify(exactly = 2) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun aPendingArticleThatLaterSucceeds_becomesAClaim() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returnsMany listOf("not json", claimJson())
        run()
        run()
        assertEquals(OracleMarketStatus.HAS_CLAIM, row("a")?.status)
    }

    @Test
    fun emptyInput_isNoClaimWithoutARequest() {
        put(article("a", title = " ", desc = "<br>"))
        val r = run()
        assertEquals(0, r.requests)
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)
    }

    private fun stopsTheWholeRunOn(error: Throwable) {
        put(article("a", ageMillis = 1 * HOUR), article("b", ageMillis = 2 * HOUR), article("c", ageMillis = 3 * HOUR))
        coEvery { gateway.execute(any(), any(), any()) } throws error
        val r = run()
        assertEquals("one failed request ends the run", 1, r.requests)
        assertTrue(r.stopped)
        assertNull(row("a"))
        assertNull(row("b"))
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test fun rateLimited_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.RateLimited)
    @Test fun invalidKey_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.InvalidApiKey)
    @Test fun missingKey_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.MissingApiKey)
    @Test fun network_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.Network)
    @Test fun ioError_stopsTheRun() = stopsTheWholeRunOn(IOException("boom"))

    @Test
    fun cancellation_isRethrown_andWritesNothing() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")
        val thrown = runCatching { run() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertNull(row("a"))
    }

    @Test
    fun logs_neverContainTheTitleTheDescriptionOrTheAnswer() {
        put(article("a", title = "SecretTitleXyz", desc = "SecretDescXyz"))
        coEvery { gateway.execute(any(), any(), any()) } returns "SecretAnswerXyz not json"
        run()
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("SecretTitleXyz leaked in message")
        put(article("b", title = "SecretTitleXyz", desc = "SecretDescXyz"))
        run()
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertFalse(log.contains("SecretTitleXyz"))
        assertFalse(log.contains("SecretDescXyz"))
        assertFalse(log.contains("SecretAnswerXyz"))
    }

    @Test
    fun cleanup_runsEvenWhenConsentIsOff_andKeepsFreshRows() {
        put(article("old"), article("fresh"))
        runBlocking {
            db.oracleMarketDao().upsert(OracleMarket(articleId = "old", status = OracleMarketStatus.NO_CLAIM, createdAt = now - 31 * day))
            db.oracleMarketDao().upsert(OracleMarket(articleId = "fresh", status = OracleMarketStatus.NO_CLAIM, createdAt = now - 29 * day))
        }
        consented = false
        run()
        assertNull(row("old"))
        assertEquals(OracleMarketStatus.NO_CLAIM, row("fresh")?.status)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: OracleClaimExtractor`, `OracleRunResult`).

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.ai.agents.OracleClaimPolicy
import com.mckimquyen.reader.domain.ai.agents.OracleParse
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.domain.repository.OracleCandidateRow
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import com.mckimquyen.reader.ui.ext.oracleClaimsGeminiConsent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OracleRunResult(val requests: Int, val claims: Int, val stopped: Boolean)

/**
 * Background prediction finder (ORACLE-01). Privacy boundary: the only article data that can leave the device is a title
 * and a short description (see [OracleClaimPolicy.MAX_INPUT_CHARS]); this class reads exactly those two columns through
 * [OracleMarketDao.candidates] and runs only when the user's own, separate consent is exactly `true`.
 */
@Singleton
open class OracleClaimExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OracleMarketDao,
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    protected open fun isConsented(): Boolean = context.oracleClaimsGeminiConsent == true

    protected open fun nowMillis(): Long = System.currentTimeMillis()

    suspend fun extractRecent(accountId: Int): OracleRunResult {
        val now = nowMillis()
        cleanUp(now)
        if (!isConsented()) return OracleRunResult(requests = 0, claims = 0, stopped = false)
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return OracleRunResult(requests = 0, claims = 0, stopped = true)

        val since = now - TimeUnit.DAYS.toMillis(OracleClaimPolicy.RECENT_WINDOW_DAYS.toLong())
        val candidates = dao.candidates(accountId, since, OracleClaimPolicy.MAX_ARTICLES_PER_RUN)
        val languageTag = Locale.getDefault().toLanguageTag()
        var requests = 0
        var claims = 0
        for (candidate in candidates) {
            val input = OracleClaimPolicy.sanitizeInput(candidate.title, candidate.shortDescription)
            if (!OracleClaimPolicy.hasContentToAnalyze(input)) {
                dao.upsert(OracleMarket(articleId = candidate.id, status = OracleMarketStatus.NO_CLAIM, attempts = candidate.attempts, createdAt = now))
                continue
            }
            val body = OracleClaimPolicy.buildRequestBody(OracleClaimPolicy.buildPrompt(input, languageTag))
            requests++
            val raw = try {
                gateway.execute(OracleClaimPolicy.USE_CASE, body, keys)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only the exception type: a message could carry the title or the description.
                Log.w(TAG, "Prediction request failed: ${e.javaClass.simpleName}")
                return OracleRunResult(requests, claims, stopped = true)
            }
            if (record(candidate, OracleClaimPolicy.parseAndValidate(raw, now), now)) claims++
        }
        return OracleRunResult(requests, claims, stopped = false)
    }

    /** Returns true when a usable claim was stored. */
    private suspend fun record(candidate: OracleCandidateRow, parsed: OracleParse, now: Long): Boolean {
        when (parsed) {
            is OracleParse.Claim -> dao.upsert(
                OracleMarket(
                    articleId = candidate.id, status = OracleMarketStatus.HAS_CLAIM, attempts = candidate.attempts,
                    question = parsed.question, expiresAt = parsed.expiresAt, resolutionCriteria = parsed.resolutionCriteria,
                    createdAt = now,
                )
            )
            OracleParse.NoClaim -> dao.upsert(
                OracleMarket(articleId = candidate.id, status = OracleMarketStatus.NO_CLAIM, attempts = candidate.attempts, createdAt = now)
            )
            is OracleParse.Rejected -> {
                Log.w(TAG, "Prediction answer rejected: ${parsed.reason}")
                val attempts = candidate.attempts + 1
                val status = if (attempts >= OracleClaimPolicy.MAX_ATTEMPTS) OracleMarketStatus.NO_CLAIM else OracleMarketStatus.PENDING
                dao.upsert(OracleMarket(articleId = candidate.id, status = status, attempts = attempts, createdAt = now))
            }
        }
        return parsed is OracleParse.Claim
    }

    private suspend fun cleanUp(now: Long) {
        try {
            dao.deleteStale(now - TimeUnit.DAYS.toMillis(OracleClaimPolicy.RETENTION_DAYS.toLong()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Prediction cleanup failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "OracleClaimExtractor"
    }
}
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (20 test). Cần `open class` với `@Singleton`: Hilt chấp nhận lớp mở; nếu kapt báo lỗi `final` thì kiểm lại, không thêm `allopen`.

- [ ] **Step 5: Mutation (backup + `cmp`)**: bỏ `if (!isConsented()) return …` (test consent đỏ); `MAX_ARTICLES_PER_RUN` bị bỏ khỏi lời gọi `candidates` (đổi thành 50; test "at most five" đỏ); trong `catch (e: Exception)` bỏ `return` (đổi thành `continue`; test "stops the run" đỏ, request = 3); đổi `>= MAX_ATTEMPTS` thành `> MAX_ATTEMPTS` (test give-up đỏ); bỏ `throw e` của `CancellationException` (test cancel đỏ); log `${e.message}` thay `javaClass.simpleName` (test log đỏ); bỏ `cleanUp(now)` (test cleanup đỏ); đặt `cleanUp` sau kiểm consent (test "runs even when consent off" đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit** `feat(oracle-01): find predictions in recent articles, one request each`.

---

### Task 6: Nối `SyncWorker` — gọi extractor sau sync thành công

`SyncWorker` không có test dựng trực tiếp (kiểm bằng `grep -rn "SyncWorker(" app/src` trước khi sửa, kỳ vọng 0 kết quả ngoài chính lớp), nên dùng test nguồn như `DeepDiveWiringSourceTest`.

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt` (thêm tham số constructor `private val oracleClaimExtractor: OracleClaimExtractor` sau `webDavReadPositionSyncer`; thêm khối `runCatching` sau khối semantic indexer)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/OracleSyncWiringSourceTest.kt`

**Interfaces:**
- Consumes: `OracleClaimExtractor.extractRecent(accountId: Int): OracleRunResult` (Task 5).
- Produces: hành vi: chỉ khi `syncResult.isSuccessfulSync()`, gọi `extractRecent(accountId)` trong `runCatching`, lỗi chỉ `Log.w` (không làm hỏng sync); `CancellationException` không bị nuốt.

- [ ] **Step 1: Viết test nguồn đỏ**

```kotlin
package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleSyncWiringSourceTest {

    private val worker = File("src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt").readText()

    @Test fun workerTakesTheExtractorThroughItsConstructor() {
        assertTrue(worker.contains("private val oracleClaimExtractor: OracleClaimExtractor"))
        assertTrue(worker.contains("import com.mckimquyen.reader.infrastructure.ai.OracleClaimExtractor"))
    }

    @Test fun extractorRunsOnlyInsideTheSuccessfulSyncBranch() {
        val success = worker.substringAfter("if (syncResult.isSuccessfulSync()) {").substringBefore("            syncResult\n")
        assertTrue("extractor call must sit in the success branch", success.contains("oracleClaimExtractor.extractRecent("))
        assertEquals("called exactly once", 1, Regex("extractRecent\\(").findAll(worker).count())
    }

    @Test fun extractorFailureNeverFailsTheSync() {
        val block = worker.substringAfter("oracleClaimExtractor.extractRecent(").substringBefore("}.onFailure")
        assertTrue(worker.substringBefore("oracleClaimExtractor.extractRecent(").trimEnd().endsWith("runCatching {"))
        assertTrue(worker.contains("Prediction finding failed"))
        assertTrue(block.isNotBlank())
    }

    @Test fun extractorRunsAfterTheSemanticIndexer() {
        assertTrue(worker.indexOf("semanticEmbeddingIndexer.indexRecent") < worker.indexOf("oracleClaimExtractor.extractRecent"))
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (assert nội dung `SyncWorker.kt` thiếu extractor; XML ghi `AssertionError`, không phải lỗi biên dịch).

- [ ] **Step 3: Code tối thiểu**

Thêm import `com.mckimquyen.reader.infrastructure.ai.OracleClaimExtractor`, tham số constructor, và sau khối `semanticEmbeddingIndexer.indexRecent`:

```kotlin
                // Background prediction finding (ORACLE-01); a no-op unless the user agreed to it separately.
                runCatching {
                    oracleClaimExtractor.extractRecent(accountId)
                }.onFailure { Log.w("RLog", "Prediction finding failed", it) }
```

Lưu ý: `runCatching` bắt cả `CancellationException`; khối đó là khối cuối của nhánh (theo sau chỉ có `notifyDataChanged`), và các khối `runCatching` hiện có (`indexRecent`) làm y hệt, nên giữ nhất quán. Nếu worker bị hủy, `withContext` kế tiếp sẽ tự ném lại khi gặp điểm treo.

- [ ] **Step 4: Biên dịch sạch Hilt vì đổi constructor `@HiltWorker`**

Run: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks > /tmp/o01-t6-hilt.log 2>&1; echo exit=$? > /tmp/o01-t6-hilt.exit` — phải `exit=0`. Rồi chạy `OracleSyncWiringSourceTest` xanh (4 test).

- [ ] **Step 5: Mutation (backup + `cmp`)**: chuyển khối ra ngoài nhánh thành công (test nhánh đỏ); bỏ `runCatching` (test "never fails the sync" đỏ); đặt khối trước `indexRecent` (test thứ tự đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit** `feat(oracle-01): run the prediction finder after a successful sync`.

---

### Task 7: Dữ liệu cho UI — `OracleClaimsViewModel`, `LocalOracleClaims`, `OracleBadge`

Khuôn Retraction Radar: bảng nhỏ nên một `Flow` toàn bộ kèo, danh sách tra theo `articleId` bằng `CompositionLocal`, không truy vấn từng dòng.

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/oracle/OracleClaims.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/oracle/OracleBadge.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleClaimsViewModelTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/component/oracle/OracleBadgeTest.kt`

**Interfaces:**
- Consumes: `OracleMarketDao.observeClaims()`, `OracleMarket` (Task 4); chuỗi `R.string.oracle_badge_label`, `R.string.oracle_badge_open` (thêm ở Task 9, nên Task 9 chạy trước Task 7).
- Produces: `data class OracleClaimCard(val articleId: String, val question: String, val expiresAt: Long, val resolutionCriteria: String)`; `OracleMarket.toCardOrNull(): OracleClaimCard?` (null nếu thiếu `question`/`expiresAt`/`resolutionCriteria`); `val LocalOracleClaims = compositionLocalOf { emptyMap<String, OracleClaimCard>() }`; `val LocalOpenOracleClaim = compositionLocalOf<(String) -> Unit> { {} }`; `@HiltViewModel class OracleClaimsViewModel @Inject constructor(dao: OracleMarketDao) : ViewModel()` với `val claims: StateFlow<Map<String, OracleClaimCard>>` (`WhileSubscribed(5_000)`, ban đầu rỗng); `@Composable fun OracleBadge(onClick: () -> Unit, modifier: Modifier = Modifier)` (test tag `oracle_badge`).

**Thứ tự thực thi:** làm **Task 9 (chuỗi 38 locale) ngay trước Task 7** để các test Compose có `R.string.oracle_*` mà parity không bao giờ đỏ. Số task giữ nguyên để tham chiếu.

- [ ] **Step 1: Viết test ViewModel đỏ** (dispatcher unconfined, chờ thời gian thực có hạn; DAO mock trả `MutableStateFlow`):

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OracleClaimsViewModelTest {

    private val source = MutableStateFlow<List<OracleMarket>>(emptyList())
    private val dao: OracleMarketDao = mockk { every { observeClaims() } returns source }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun claim(id: String, q: String? = "Will it ship?", exp: Long? = 9L, crit: String? = "Release notes.") =
        OracleMarket(id = 1, articleId = id, status = OracleMarketStatus.HAS_CLAIM, question = q, expiresAt = exp, resolutionCriteria = crit, createdAt = 1)

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    @Test fun startsEmpty() {
        assertEquals(emptyMap<String, OracleClaimCard>(), OracleClaimsViewModel(dao).claims.value)
    }

    @Test fun mapsClaimsByArticleId() {
        val vm = OracleClaimsViewModel(dao)
        source.value = listOf(claim("a"), claim("b"))
        val map = awaitReal { vm.claims.first { it.size == 2 } }
        assertEquals("Will it ship?", map.getValue("a").question)
        assertEquals(9L, map.getValue("b").expiresAt)
        assertEquals("Release notes.", map.getValue("a").resolutionCriteria)
    }

    @Test fun rowsMissingAnyFieldAreDropped() {
        val vm = OracleClaimsViewModel(dao)
        source.value = listOf(claim("a", q = null), claim("b", exp = null), claim("c", crit = null), claim("ok"))
        val map = awaitReal { vm.claims.first { it.isNotEmpty() } }
        assertEquals(setOf("ok"), map.keys)
        assertNull(map["a"])
    }

    @Test fun toCardOrNull_isNullForABlankField() {
        assertNull(claim("a", q = "  ").toCardOrNull())
        assertNull(claim("a", crit = "").toCardOrNull())
    }
}
```

- [ ] **Step 2: Viết test Compose của badge đỏ** (Robolectric, khuôn `DeepDiveSheetTest`):

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
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
class OracleBadgeTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var clicks = 0

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun showsTheLabelAndIsClickable() {
        compose.setContent { OracleBadge(onClick = { clicks++ }) }
        compose.onNodeWithText(str(R.string.oracle_badge_label)).assertIsDisplayed()
        compose.onNodeWithTag("oracle_badge").assertHasClickAction()
    }

    @Test fun clickingCallsBack() {
        compose.setContent { OracleBadge(onClick = { clicks++ }) }
        compose.onNodeWithTag("oracle_badge").performClick()
        assertEquals(1, clicks)
    }

    @Test fun spokenDescriptionNamesTheLabelAndTheAction() {
        compose.setContent { OracleBadge(onClick = {}) }
        val expected = "${str(R.string.oracle_badge_label)}. ${str(R.string.oracle_badge_open)}"
        compose.onNodeWithContentDescription(expected).assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Chạy, xác nhận đỏ** (`Unresolved reference: OracleClaimsViewModel`, `OracleBadge`).

- [ ] **Step 4: Code tối thiểu**

`OracleClaims.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the read-only prediction card shows; no points, odds or votes exist yet (ORACLE-02). */
data class OracleClaimCard(
    val articleId: String,
    val question: String,
    val expiresAt: Long,
    val resolutionCriteria: String,
)

fun OracleMarket.toCardOrNull(): OracleClaimCard? {
    val q = question?.takeIf { it.isNotBlank() } ?: return null
    val e = expiresAt ?: return null
    val c = resolutionCriteria?.takeIf { it.isNotBlank() } ?: return null
    return OracleClaimCard(articleId, q, e, c)
}

/** articleId -> card for articles that have a claim; empty when none. */
val LocalOracleClaims = compositionLocalOf { emptyMap<String, OracleClaimCard>() }

/** Opens the read-only claim card for an article; no-op where unavailable (previews, tests). */
val LocalOpenOracleClaim = compositionLocalOf<(String) -> Unit> { {} }

/** Claims are few (at most 5 per sync, dropped after expiry), so the lists observe them whole. */
@HiltViewModel
class OracleClaimsViewModel @Inject constructor(dao: OracleMarketDao) : ViewModel() {
    val claims: StateFlow<Map<String, OracleClaimCard>> = dao.observeClaims()
        .map { rows -> rows.mapNotNull { it.toCardOrNull() }.associateBy { it.articleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

`OracleBadge.kt` (khuôn `RadarBadge`; lấp lánh = `infiniteTransition` đổi alpha của viền/nền, tắt khi `ANIMATOR_DURATION_SCALE == 0f`; đích chạm bảo đảm bằng `minimumInteractiveComponentSize` không áp ở hàng danh sách nhỏ, nên giữ giống `RadarBadge` và đảm bảo 48 dp ở thẻ/sheet thay vì ở huy hiệu):

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

private const val SHIMMER_MIN_ALPHA = 0.6f
private const val SHIMMER_MS = 1_200
private const val NO_ANIMATION_SCALE = 0f

/** "Prediction market" marker for an article (ORACLE-01); tapping opens the read-only claim card. */
@Composable
fun OracleBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.oracle_badge_label)
    val action = stringResource(R.string.oracle_badge_open)
    val context = LocalContext.current
    // The shimmer is decoration only: it stops when the user turned system animations off.
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != NO_ANIMATION_SCALE
    }
    val shimmer by rememberInfiniteTransition(label = "oracle_shimmer").animateFloat(
        initialValue = if (animate) SHIMMER_MIN_ALPHA else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS), RepeatMode.Reverse),
        label = "oracle_shimmer_alpha",
    )
    Surface(
        modifier = modifier
            .testTag("oracle_badge")
            .alpha(if (animate) shimmer else 1f)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. $action"
            }
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
```

- [ ] **Step 5: Chạy lại, xác nhận xanh** (ViewModel 4, badge 3). Biên dịch sạch Hilt vì thêm `@HiltViewModel` mới: cùng lệnh `--rerun-tasks` như Task 6.

- [ ] **Step 6: Mutation (backup + `cmp`)**: bỏ `takeIf { it.isNotBlank() }` ở câu hỏi (test blank đỏ); đổi `associateBy` khóa thành `question` (test map theo id đỏ); đổi `contentDescription` thành chỉ `label` (test mô tả đỏ); bỏ `.clickable` (test click đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 7: Commit** `feat(oracle-01): expose claims to the lists and add the prediction badge`.

---

### Task 8: `OracleClaimSheet` (thẻ chỉ xem) và nối vào danh sách, màn đọc, `HomeEntry`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/oracle/OracleClaimSheet.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/ArticleItem.kt` (sau khối `radarFlag`, dòng ~177-185)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt` (cạnh `RadarBadge`, dòng ~177)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` (cấp `LocalOracleClaims`, `LocalOpenOracleClaim`, giữ thẻ đang mở; cạnh `LocalRadarFlags`, dòng ~236-247)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/component/oracle/OracleClaimSheetTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleWiringSourceTest.kt`

**Interfaces:**
- Consumes: `OracleClaimCard`, `LocalOracleClaims`, `LocalOpenOracleClaim`, `OracleClaimsViewModel` (Task 7), `OracleBadge`, chuỗi `oracle_*` (Task 9), `SheetDialog`-kiểu của `DeepDiveSheet` (sao chép bản nhỏ, không export: file đó giữ `private`).
- Produces: `@Composable fun OracleClaimSheet(card: OracleClaimCard?, onDismiss: () -> Unit)` (vẽ rỗng khi `card == null`; Dialog); `@Composable fun OracleClaimSheetContent(card: OracleClaimCard, onDismiss: () -> Unit)` (thân, test tag `ORACLE_SHEET_TAG = "oracle_sheet"`, `ORACLE_QUESTION_TAG`, `ORACLE_EXPIRES_TAG`, `ORACLE_CRITERIA_TAG`, `ORACLE_AI_LABEL_TAG`, `ORACLE_CLOSE_TAG`).

Thẻ hiển thị: tiêu đề, câu hỏi, "Dự kiến trước <ngày>" (định dạng bằng `java.text.DateFormat.getDateInstance(DateFormat.MEDIUM, locale)` ở múi giờ **UTC** để khớp ngày AI trả, chuỗi `oracle_sheet_expires` có đúng một `%1$s` là chuỗi ngày), tiêu chí giải quyết, nhãn AI, nút Đóng ≥ 48 dp. **Không có nút cược, ví, điểm, quảng cáo.**

- [ ] **Step 1: Viết test Compose đỏ**

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.oracle.OracleClaimCard
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
class OracleClaimSheetTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val card = OracleClaimCard(
        articleId = "a",
        question = "Will the chipmaker open the fab?",
        expiresAt = LocalDate.of(2026, 12, 31).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1,
        resolutionCriteria = "The company's own announcement.",
    )
    private var dismissed = 0

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun nullCard_drawsNothing() {
        compose.setContent { OracleClaimSheet(card = null, onDismiss = { dismissed++ }) }
        compose.onAllNodesWithTag(ORACLE_SHEET_TAG).assertCountEquals(0)
    }

    @Test fun showsQuestionDateCriteriaAndTheAiLabel() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = { dismissed++ }) }
        compose.onNodeWithTag(ORACLE_QUESTION_TAG).assertTextContains(card.question)
        compose.onNodeWithTag(ORACLE_EXPIRES_TAG).assertTextContains("Dec 31, 2026", substring = true)
        compose.onNodeWithTag(ORACLE_CRITERIA_TAG).assertTextContains(card.resolutionCriteria)
        compose.onNodeWithTag(ORACLE_AI_LABEL_TAG).assertTextContains(str(R.string.oracle_sheet_ai_label))
    }

    @Test fun theDateIsTheUtcDay_notTheLocalDayOfTheDevice() {
        // 23:59:59.999 UTC on Dec 31 must still read Dec 31 in a timezone behind UTC.
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            compose.setContent { OracleClaimSheetContent(card, onDismiss = {}) }
            compose.onNodeWithTag(ORACLE_EXPIRES_TAG).assertTextContains("Dec 31, 2026", substring = true)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }

    @Test fun offersNoBettingOrPointsControl() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = {}) }
        // Only the close button is interactive: a read-only card.
        compose.onAllNodesWithTag(ORACLE_CLOSE_TAG).assertCountEquals(1)
        assertEquals(false, str(R.string.oracle_sheet_ai_label).isBlank())
    }

    @Test fun closeCallsDismiss() {
        compose.setContent { OracleClaimSheetContent(card, onDismiss = { dismissed++ }) }
        compose.onNodeWithTag(ORACLE_CLOSE_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test fun dialogVersion_showsTheContentForACard() {
        compose.setContent { OracleClaimSheet(card = card, onDismiss = {}) }
        compose.onNodeWithTag(ORACLE_SHEET_TAG).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Viết test nguồn đỏ** (`OracleWiringSourceTest`):

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private val item = File("$root/ui/page/home/flow/ArticleItem.kt").readText()
    private val content = File("$root/ui/page/home/read/Content.kt").readText()
    private val home = File("$root/ui/page/common/HomeEntry.kt").readText()
    private val sheet = File("$root/ui/component/oracle/OracleClaimSheet.kt").readText()

    @Test fun articleItemShowsTheBadgeFromTheLocalAndOpensTheCard() {
        assertTrue(item.contains("LocalOracleClaims.current[articleWithFeed.article.id]"))
        assertTrue(item.contains("OracleBadge("))
        assertTrue(item.contains("openOracleClaim(articleWithFeed.article.id)"))
    }

    @Test fun readingContentShowsTheBadgeForTheOpenArticle() {
        assertTrue(content.contains("LocalOracleClaims.current[articleId]"))
        assertTrue(content.contains("OracleBadge("))
        assertTrue(content.contains("openOracleClaim(articleId)"))
    }

    @Test fun homeEntryProvidesBothLocalsAndHostsTheSheet() {
        assertTrue(home.contains("val oracleClaimsViewModel: OracleClaimsViewModel = hiltViewModel()"))
        assertTrue(home.contains("LocalOracleClaims provides oracleClaims"))
        assertTrue(home.contains("LocalOpenOracleClaim provides"))
        assertTrue(home.contains("OracleClaimSheet("))
    }

    @Test fun theSheetHasNoLinkSourceOrBettingWords() {
        listOf("link", "feed", "bet", "wallet", "points", "reward", "ad").forEach { word ->
            if (word == "ad") {
                assertFalse("no ads", Regex("""\bAdManager\b|\bshowRewarded""").containsMatchIn(sheet))
            } else {
                assertFalse("sheet must not mention '$word'", sheet.contains(word, ignoreCase = true))
            }
        }
    }
}
```

- [ ] **Step 3: Chạy, xác nhận đỏ** (`Unresolved reference: OracleClaimSheet`, và các assert nguồn).

- [ ] **Step 4: Code tối thiểu**

`OracleClaimSheet.kt`:

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import com.mckimquyen.reader.ui.page.oracle.OracleClaimCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val ORACLE_SHEET_TAG = "oracle_sheet"
const val ORACLE_QUESTION_TAG = "oracle_question"
const val ORACLE_EXPIRES_TAG = "oracle_expires"
const val ORACLE_CRITERIA_TAG = "oracle_criteria"
const val ORACLE_AI_LABEL_TAG = "oracle_ai_label"
const val ORACLE_CLOSE_TAG = "oracle_close"

private val MIN_TOUCH_TARGET = 48.dp
private const val SCRIM_ALPHA = 0.5f

/** Read-only claim card (ORACLE-01). Draws nothing for a null [card]. Betting arrives with ORACLE-02. */
@Composable
fun OracleClaimSheet(card: OracleClaimCard?, onDismiss: () -> Unit) {
    if (card == null) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        DialogEdgeToEdge()
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Surface(
                // Swallows taps so they do not reach the scrim, WITHOUT clickable (clickable would merge the card's children).
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) { OracleClaimSheetContent(card, onDismiss) }
        }
    }
}

/** The card body without the Dialog window, so a plain ComposeView (or a test) can compose it. */
@Composable
fun OracleClaimSheetContent(card: OracleClaimCard, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val date = remember(card.expiresAt, locale) { formatUtcDay(card.expiresAt, locale) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()).testTag(ORACLE_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.oracle_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_CLOSE_TAG)) {
                Text(stringResource(R.string.oracle_sheet_close))
            }
        }
        Text(card.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag(ORACLE_QUESTION_TAG))
        Text(stringResource(R.string.oracle_sheet_expires, date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ORACLE_EXPIRES_TAG))
        Text(stringResource(R.string.oracle_sheet_criteria_heading), style = MaterialTheme.typography.labelLarge)
        Text(card.resolutionCriteria, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ORACLE_CRITERIA_TAG))
        Text(
            stringResource(R.string.oracle_sheet_ai_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ORACLE_AI_LABEL_TAG),
        )
    }
}

/** The AI gives a calendar day, stored as the end of that UTC day, so it is shown as that UTC day on every device. */
private fun formatUtcDay(epochMillis: Long, locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMillis))
```

Gắn `ArticleItem.kt` (cạnh `radarFlag`, thêm dòng khai báo cùng chỗ `val radarFlag = …`):

```kotlin
    val oracleCard = LocalOracleClaims.current[articleWithFeed.article.id]
    val openOracleClaim = LocalOpenOracleClaim.current
```
```kotlin
                // Prediction badge (ORACLE-01)
                if (oracleCard != null) {
                    OracleBadge(
                        onClick = { openOracleClaim(articleWithFeed.article.id) },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
```

`Content.kt` (ngay sau khối `RadarBadge`, trong cùng `DisableSelection`; khai báo `val openOracleClaim = LocalOpenOracleClaim.current` cạnh `openRadarDiff`):

```kotlin
                        if (LocalOracleClaims.current[articleId] != null) {
                            OracleBadge(
                                onClick = { openOracleClaim(articleId) },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
```

`HomeEntry.kt` (cạnh `radarFlagsViewModel`; `openOracleArticleId` giữ trạng thái thẻ đang mở theo `articleId`, tự đóng nếu kèo biến mất):

```kotlin
        val oracleClaimsViewModel: OracleClaimsViewModel = hiltViewModel()
        val oracleClaims by oracleClaimsViewModel.claims.collectAsState()
        var openOracleArticleId by remember { mutableStateOf<String?>(null) }
```
và trong `CompositionLocalProvider(...)` thêm `LocalOracleClaims provides oracleClaims,` và `LocalOpenOracleClaim provides { articleId -> openOracleArticleId = articleId },`; ngay trong khối (trước `NavHost`):

```kotlin
            OracleClaimSheet(
                card = openOracleArticleId?.let { oracleClaims[it] },
                onDismiss = { openOracleArticleId = null },
            )
```
(Thêm import tương ứng. `OracleClaimSheet` là `Dialog`, nên đặt cạnh NavHost là đủ.)

- [ ] **Step 5: Chạy lại, xác nhận xanh** (6 test sheet + 4 test nguồn). Chạy thêm `ArticleItemTest` và các test Compose có `ArticleItem`/`Content` để chắc không hồi quy: `--tests "*ArticleItem*" --tests "*Radar*"`.

- [ ] **Step 6: Mutation (backup + `cmp`)**: bỏ `timeZone = UTC` (test múi giờ Los_Angeles đỏ); xóa `if (card == null) return` (test null đỏ… sẽ lỗi biên dịch hoặc crash, ghi là đỏ); bỏ `onClick` của badge ở `ArticleItem` (test nguồn đỏ); bỏ `LocalOracleClaims provides` ở `HomeEntry` (test nguồn đỏ); thêm chữ "reward" vào sheet (test từ cấm đỏ). Khôi phục bằng `cmp`.

- [ ] **Step 7: Commit** `feat(oracle-01): show the prediction badge and a read-only claim card`.

---

### Task 9: Chuỗi `oracle_*` trong cả 38 locale và công tắc Quyền riêng tư (làm TRƯỚC Task 7)

**Files:**
- Modify: `app/src/main/res/values*/strings.xml` ×38 (một script all-or-none)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt` (thêm một `item` sau mục Deep Dive, dòng ~164-175, và `val oracleClaimsGeminiConsent by viewModel.oracleClaimsGeminiConsent.collectAsState()` cạnh dòng 46)
- Create: `/tmp/o01_add_strings.py` (sao từ `/tmp/f01_add_strings.py`, đổi `EN`/`VI`, `assert len(KEYS) == 9`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/OracleStringsTest.kt`

**Interfaces:**
- Produces: 9 khóa `oracle_badge_label`, `oracle_badge_open`, `oracle_sheet_title`, `oracle_sheet_expires` (đúng một `%1$s`), `oracle_sheet_criteria_heading`, `oracle_sheet_ai_label`, `oracle_sheet_close`, `oracle_consent_title`, `oracle_consent_message`.

Bản EN/VI (viết tay):

| Khóa | EN | VI |
|---|---|---|
| `oracle_badge_label` | Prediction | Kèo Dự Đoán |
| `oracle_badge_open` | Open the prediction | Mở kèo dự đoán |
| `oracle_sheet_title` | Prediction found in this article | Dự báo trong bài này |
| `oracle_sheet_expires` | Expected by %1$s | Dự kiến trước %1$s |
| `oracle_sheet_criteria_heading` | How to check the answer | Cách kiểm chứng kết quả |
| `oracle_sheet_ai_label` | Written by AI from the title and description; it can be wrong. You cannot bet on it yet. | Do AI soạn từ tiêu đề và mô tả; có thể sai. Chưa tham gia cược được. |
| `oracle_sheet_close` | Close | Đóng |
| `oracle_consent_title` | Find predictions in new articles | Tìm dự báo trong bài mới |
| `oracle_consent_message` | After each sync, the title and a short description (up to 500 characters in total) of up to 5 new articles are sent to Google Gemini to look for a prediction. Article text, links, the feed name and your reading history are never sent. Off by default. | Sau mỗi lần đồng bộ, tiêu đề và mô tả ngắn (tối đa 500 ký tự gộp lại) của tối đa 5 bài mới được gửi tới Google Gemini để tìm dự báo. Nội dung bài, đường dẫn, tên nguồn và lịch sử đọc không bao giờ được gửi. Mặc định tắt. |

`oracle_sheet_expires` có `%1$s` ở 36 bản tiếng Anh giữ nguyên `%1$s`; script kiểm mỗi file có đúng một `%1$s` cho khóa này.

- [ ] **Step 1: Viết test đỏ `OracleStringsTest`** (khuôn `DeepDiveStringsTest`: đọc 38 `values*/strings.xml` bằng `File`, kiểm khóa có ở tất cả; `oracle_sheet_expires` đúng một `%1$s` mọi nơi; không `%d` ngay trước danh từ; `oracle_consent_message` EN chứa `500` và `5 new articles`, VI chứa `500` và `5 bài`; `oracle_sheet_ai_label` EN chứa `can be wrong`; không khóa nào trống). Khẳng định cứng số file là 38.

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleStringsTest {

    private val keys = listOf(
        "oracle_badge_label", "oracle_badge_open", "oracle_sheet_title", "oracle_sheet_expires",
        "oracle_sheet_criteria_heading", "oracle_sheet_ai_label", "oracle_sheet_close",
        "oracle_consent_title", "oracle_consent_message",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        .map { File(it, "strings.xml") }.filter { it.exists() }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun everyLocaleHasEveryKeyNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
        }
    }

    @Test fun expiresHasExactlyOneStringPlaceholder_everywhere() {
        for (f in files) {
            val v = value(f, "oracle_sheet_expires")!!
            assertEquals("${f.parentFile.name}", 1, Regex("%1\\\$s").findAll(v).count())
            assertEquals(0, Regex("%\\d*d").findAll(v).count())
        }
    }

    @Test fun consentMessageStatesTheLimitsAndTheAiLabelStatesTheRisk() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        val enMsg = value(en, "oracle_consent_message")!!
        val viMsg = value(vi, "oracle_consent_message")!!
        assertTrue(enMsg.contains("500") && enMsg.contains("5 new articles"))
        assertTrue(viMsg.contains("500") && viMsg.contains("5 bài"))
        assertTrue(value(en, "oracle_sheet_ai_label")!!.contains("can be wrong"))
        assertTrue(value(vi, "oracle_sheet_ai_label")!!.contains("có thể sai"))
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (khóa thiếu ở mọi locale).

- [ ] **Step 3: Viết và chạy script `/tmp/o01_add_strings.py`** (all-or-none: kiểm đủ 38 file, chưa file nào có khóa, đúng một `</resources>`, rồi mới ghi; thoát ký tự `'` thành `\'`; `%1$s` ở bản EN và VI nằm trong chuỗi Python thường nên viết `%1$s` nguyên văn). Chạy: `python3 /tmp/o01_add_strings.py` từ thư mục gốc repo; kỳ vọng in `ok 38`.

- [ ] **Step 4: Thêm công tắc vào `PrivacySettingsPage.kt`** (khuôn mục Deep Dive):

```kotlin
                item {
                    SettingItem(
                        title = stringResource(R.string.oracle_consent_title),
                        desc = stringResource(R.string.oracle_consent_message),
                        onClick = { viewModel.setOracleClaimsGeminiConsent(!oracleClaimsGeminiConsent) },
                        action = {
                            BaseSwitch(activated = oracleClaimsGeminiConsent) {
                                viewModel.setOracleClaimsGeminiConsent(!oracleClaimsGeminiConsent)
                            }
                        },
                    )
                }
```

- [ ] **Step 5: Chạy `OracleStringsTest`, `StringResourceParityTest`, và các test Privacy xanh.** Đọc XML từng lớp: `failures=0`.

- [ ] **Step 6: Mutation (backup + `cmp`)**: xóa một khóa ở một locale (ví dụ `values-de`) → `everyLocaleHasEveryKeyNonBlank` và parity đỏ; đổi `%1$s` thành `%s` ở một locale → test placeholder đỏ; bỏ chữ `500` khỏi EN consent → test đỏ. Khôi phục bằng `cmp`.

- [ ] **Step 7: Commit** `feat(oracle-01): add the prediction strings in 38 locales and the privacy switch`.

---

### Task 10: androidTest + kiểm máy thật Tecno KJ7 (SỚM, trước báo cáo)

Chỉ làm sau khi owner duyệt plan. Mục tiêu: bắt lỗi chỉ lộ trên máy thật (nâng cấp DB thật, sheet trong cây semantics thật) trước khi viết báo cáo.

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/db/OracleMigration21Test.kt` (khuôn `BrainRpgMigration19Test`: dựng DB v20 từ `20.json`, seed bài, mở bằng `allMigrations`, kiểm `oracle_market` tồn tại, bài còn nguyên, mở lại Room không lỗi; test thứ hai: chèn một kèo + xóa bài → cascade)
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/oracle/OracleBadgeSheetWidgetTest.kt` (khuôn `DeepDiveSheetWidgetTest`: `ActivityScenario<ComponentActivity>` + `ComposeView` + duyệt cây semantics, **không** dùng `ComposeTestRule`; 4 test: badge hiện và bấm gọi lại; sheet hiện câu hỏi/ngày/tiêu chí/nhãn AI; chỉ có một điều khiển (Đóng); đóng gọi `onDismiss`)
- Create: `/tmp/o01-backup.py`, `/tmp/o01-restore.py` (sao từ `/tmp/f01-backup.py`, `/tmp/f01-restore.py`; **chỉ `SERIALS = {'tecno': '115333744A005844'}`**; sao lưu `databases/Reader`, `-shm`, `-wal`, `files/datastore/settings.preferences_pb`, mọi `shared_prefs/*.xml`; restore so byte)

**Cổng thiết bị (chỉ đọc) trước mọi thao tác:**
1. `adb devices` (KJ7 phải có mặt; không chạm máy khác).
2. `adb -s 115333744A005844 shell dumpsys activity activities | grep -E "Active instrumentation|topResumedActivity"`: không có instrumentation; foreground là launcher hoặc app của owner đã biết (`bemytester`, `loyalty.skyjoy.vn.stg` ở nền là bình thường); nếu có app lạ ở foreground thì **dừng và hỏi**.
3. `adb -s ... shell ps -A | grep -iE "instrument|monkey|uiautomator"`: trống.
4. Màn hình mở khóa (khóa thì **nhờ owner mở**, không tự mở).

- [ ] **Step 1: Viết hai androidTest, biên dịch** `./gradlew assembleDevDebugAndroidTest > /tmp/o01-t10-build.log 2>&1` (exit 0).
- [ ] **Step 2: Cổng thiết bị rồi sao lưu** (`python3 /tmp/o01-backup.py`), cài APK dev + test APK bằng `adb -s 115333744A005844 install -r`.
- [ ] **Step 3: Chạy bằng instrument, không `connected*`**: `timeout 300 adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.infrastructure.db.OracleMigration21Test,com.mckimquyen.reader.ui.component.oracle.OracleBadgeSheetWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/o01-t10-instr.log 2>&1`; kỳ vọng `OK (6 tests)`. Quảng cáo toàn màn hình nếu hiện: chỉ Back.
- [ ] **Step 4: Smoke tay với app thật (không gọi Gemini thật)**:
  1. Mở Cài đặt → Quyền riêng tư: có công tắc "Tìm dự báo trong bài mới", **mặc định tắt**; đọc DataStore: không có khóa `oracle_claims_gemini_consent`.
  2. Kích sync khi consent tắt: log (`adb logcat -d | grep -c oracle_claim`) **0** dòng; bảng `oracle_market` rỗng (đọc DB chỉ đọc).
  3. Bật công tắc, kích sync: gateway thử key và thất bại (key chung hỏng, FIX-09): log có `Prediction request failed: <ExceptionType>`, **đúng 1** lần gọi `oracle_claim` trong lượt, bảng vẫn rỗng (lỗi dừng cả lượt, không ghi). **Đây là kiểm "dừng khi lỗi", không phải kiểm kèo thật.**
  4. Chèn tay **một dòng kèo mẫu** vào DB đã sao lưu (`HAS_CLAIM`, `expiresAt` tương lai, gắn một `articleId` có thật) bằng `run-as` + `sqlite3` nếu có, hoặc bằng một androidTest nhỏ ghi qua DAO; mở app: bài đó có huy hiệu "Kèo Dự Đoán" ở danh sách; mở bài thấy huy hiệu; bấm mở thẻ: câu hỏi, "Dự kiến trước …", tiêu chí, nhãn AI, **không** có nút cược.
  5. Xóa bài đó khỏi app (hoặc dọn dòng): huy hiệu biến mất.
- [ ] **Step 5: Khôi phục byte-identical** (`python3 /tmp/o01-restore.py`): Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều **BYTE IDENTICAL**; gỡ test package; `force-stop com.mckimquyen.reader`; cài lại bản HEAD sạch.
- [ ] **Step 6: Nếu bất kỳ ca nào đỏ**: sửa mã (TDD: viết test JVM tái hiện trước), không sửa test cho khớp. Ghi mọi phát hiện vào báo cáo.
- [ ] **Step 7: Commit** `test(oracle-01): cover the migration, badge and card on a real device`.

**Không kiểm được (ghi thẳng vào báo cáo):** kèo thật từ Gemini; tỷ lệ JSON hợp lệ; chất lượng câu hỏi và ngày; ngôn ngữ ngoài EN/VI; TalkBack; Pixel và máy thứ hai.

---

### Task 11: Full suite, lint, build

**Files:** không sửa mã (chỉ sửa nếu phát hiện lỗi thật, theo TDD).

- [ ] **Step 1: Full suite sạch:** `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/o01-full.log 2>&1; echo exit=$? > /tmp/o01-full.exit`. Đọc exit, rồi đếm từ XML: tổng `tests=`, `failures=`, `errors=`, `skipped=` trên toàn bộ `app/build/test-results/testDevDebugUnitTest/*.xml` (kỳ vọng 0 lỗi; nền trước 2,901). Nếu treo thì kill "Gradle Test Executor" và chạy lại. Nếu có lỗi chỉ khi chạy chung (như bài học FileProvider), tìm trạng thái tĩnh bị rò thay vì loại test.
- [ ] **Step 2: Lint:** `./gradlew lintDevDebug > /tmp/o01-lint.log 2>&1; echo exit=$?`; báo số lỗi và cảnh báo (nền 2,465 cảnh báo, 0 lỗi) và liệt kê cảnh báo nằm trong tệp ORACLE-01 (kỳ vọng 0; nếu có thì sửa).
- [ ] **Step 3: Build:** `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/o01-assemble.log 2>&1; echo exit=$?` (exit 0).
- [ ] **Step 4: Kiểm tra schema:** `git status --short app/schemas` chỉ thấy `21.json` mới; `20.json` không đổi (`git diff --stat`).
- [ ] **Step 5: Không commit** (chỉ kiểm). Có sửa thì commit riêng theo TDD.

---

### Task 12: Báo cáo DONE và tài liệu

**Files:**
- Create: `doc/task/done/ORACLE-01_ai-predictive-claim-extractor_DONE.md` (tiếng Việt, cùng khuôn `FRONT-01_ai-multi-agent-deep-dive_DONE.md`: Phạm vi nói thẳng, Đã làm, Kiểm chứng JVM, Kiểm chứng KJ7, Phát hiện khi làm, Chưa kiểm, Giới hạn, Commits)
- Modify: `doc/feature.md` (chuyển ORACLE-01 từ "đang brainstorm → spec" sang ✅ với số liệu thật; nợ: kèo thật từ Gemini, tỷ lệ JSON, TalkBack)
- Modify: `docs/superpowers/specs/2026-11-12-oracle-01-claim-extractor-design.md` (ghi chú hai điều chỉnh: cửa sổ `RECENT_WINDOW_DAYS = 3`; mọi lỗi gọi AI dừng cả lượt, không ghi)
- Task gốc `doc/task/todo/ORACLE-01_*.md`: **hỏi owner bằng AskUserQuestion** giữ trong `todo/` + ghi chú đầu file, hay chuyển sang `done/`. Gherkin có "hiển thị huy hiệu phát sáng": đã làm; nhưng chưa kiểm kèo thật từ AI trên máy, nên khuyến nghị giữ trong `todo/` với ghi chú.

- [ ] **Step 1:** Viết báo cáo chỉ bằng số liệu thật từ XML, log và lệnh đã chạy ở Task 10 và 11 (không làm tròn, không ước đoán). Phần "Chưa kiểm" phải liệt kê đúng danh sách ở Task 10.
- [ ] **Step 2:** Cập nhật `doc/feature.md` và spec; hỏi owner về file task gốc; làm theo lựa chọn.
- [ ] **Step 3: Commit** `docs(oracle-01): report the prediction finder with its verified and unverified parts`. Không push, không PR, không `.idea`.

---

## Self-review

**Spec coverage:** kích hoạt nền khi sync (Task 5, 6); bảng `oracle_market` v21 + migration (Task 4); consent riêng mặc định tắt, chỉ tiêu đề + mô tả ≤ 500, ≤ 5 bài mỗi lần (Task 1, 2, 5, 9); `attempts` 2 lần (Task 5); kiểm hợp lệ câu hỏi/ngày/tiêu chí (Task 3); dừng khi lỗi quota/khóa (Task 5, mở rộng thành mọi lỗi gọi); dọn sau 30 ngày (Task 4, 5); huy hiệu danh sách + màn đọc + thẻ chỉ xem (Task 7, 8); chuỗi 38 locale (Task 9); máy thật chỉ KJ7 và giới hạn không kiểm kèo thật (Task 10); báo cáo trung thực (Task 12). Hai chỉnh so với spec (cửa sổ 3 ngày; mọi lỗi dừng lượt) được ghi ở Global Constraints và sẽ vào spec ở Task 12.

**Thứ tự thực thi:** 1 → 2 → 3 → 4 → 5 → 6 → **9** → 7 → 8 → 10 → 11 → 12 (Task 9 trước 7 để `R.string.oracle_*` có sẵn mà parity không đỏ).

**Rủi ro đã nêu:** `open class` + `@Singleton` với Hilt (Task 5 Step 4); đổi constructor `@HiltWorker` cần clean rebuild (Task 6 Step 4); migration chạm DB thật (chỉ tạo bảng, có test và so với `21.json`); huy hiệu lấp lánh phải tắt khi tắt animation (Task 7); ngày hiển thị theo UTC để không lệch ngày (Task 8).
