# FRONT-01 AI Deep Dive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menu ⋮ của màn đọc có mục `🔬 AI Deep Dive`, mở sheet báo cáo một trang (Bối cảnh, Phản biện, Ma trận tác động) do Gemini soạn từ tiêu đề và trích thân bài ≤ 6.000 ký tự, có consent riêng, và xuất được PDF A4 một trang.

**Architecture:** Phần thuần (`DeepDivePolicy`: hằng số, sanitize, prompt, parse + kiểm cấu trúc; `DeepDivePdfLayout`: chia dòng, cắt ý) tách khỏi I/O. `AiDeepDiveService` gọi `AiRequestGateway` đúng một request. `DeepDiveViewModel` (riêng, theo mẫu `PodcastStudioViewModel`, không đụng constructor `ReadingViewModel`) giữ `DeepDiveUiState`, là nơi duy nhất quyết định có gọi mạng (chỉ khi consent `true`) và dùng `DeepDiveCache` (Hilt singleton, LRU 10). `DeepDivePdfRenderer` dùng `android.graphics.pdf.PdfDocument`, `DeepDiveSharer` ghi `cacheDir/deepdive/` và chia sẻ qua `FileProvider` có sẵn.

**Tech Stack:** Kotlin, Compose Material3, Hilt, DataStore, `AiRequestGateway`, org.json, `PdfDocument` + `StaticLayout` (framework), JUnit4 + MockK + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-11-front-01-ai-deep-dive-design.md` (commit `943a96b0`, sau đó sửa phần ViewModel thành `DeepDiveViewModel` riêng). Owner đã duyệt từng phần.
- **Dữ liệu gửi Gemini:** chỉ tiêu đề + trích thân bài ≤ **6000** ký tự (bỏ HTML, không cắt giữa surrogate). Không link, tên feed, account, tên người dùng. Consent phải nói rõ có gửi một phần thân bài.
- Consent **riêng**: khoá mới `deep_dive_gemini_consent` (`Boolean?`, null = chưa hỏi). Không đụng `podcast_studio_gemini_consent` hay `quiz_gemini_consent`. "Để sau" không lưu gì.
- **Một request** Gemini mỗi báo cáo (use case `deep_dive`), JSON mode. Không chuỗi 3 request. Cache bộ nhớ Hilt `@Singleton` LRU 10 theo `articleId`; lỗi không cache; thử lại chỉ khi người dùng bấm.
- Nói thẳng: "3 agent" là 3 vai trong một prompt; không tra cứu web; kiểm tra chỉ là cấu trúc. UI và PDF có nhãn "AI soạn từ tiêu đề và trích đoạn bài; không tra cứu web; có thể sai". Không log tiêu đề, trích đoạn hay nội dung báo cáo.
- PDF: A4 595×842 pt, **đúng một trang**, không dependency; ghi `cacheDir/deepdive/deepdive_<slug>_<millis>.pdf`, slug ASCII ≤ 40, giữ 24 giờ; PDF không chứa link hay tên nguồn.
- Không đổi Room (giữ **v20**), không migration, không dependency mới, không quảng cáo/IAP, manifest và `file_paths.xml` không đổi (`cache-path "."` đã phủ `deepdive/`).
- Chuỗi mới đủ **38 locale** (EN và VI viết tay, 36 còn lại English); không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` phải xanh. Thêm bằng script một lượt ở Task 8 để parity không bao giờ đỏ giữa chừng.
- Code: không `!!`/`late` thiếu lý do, không magic number (hằng có tên), mọi Job huỷ khi ViewModel clear.
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML kết quả), code tối thiểu, xanh, mutation cho guard quan trọng (backup + `cmp`), commit riêng từng task. Không chain `git commit` sau build nếu chưa kiểm exit code. Ghi output Gradle vào file. `grep -r` có dấu nháy; không dùng `echo ====`. Test ViewModel có DataStore: dispatcher unconfined + chờ thời gian thực có hạn, **không** `StandardTestDispatcher`.
- Bài học: (1) Robolectric không thực thi giới hạn CursorWindow; (2) đổi constructor `@HiltViewModel`/`@Inject` thì biên dịch lại sạch (`./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`), `NoClassDefFoundError: RApp_GeneratedInjector` là lỗi môi trường; (3) trước khi xoá, `grep -r` mọi nơi dùng; (4) cache phải sống lâu hơn ViewModel vì `hiltViewModel()` tạo mới mỗi lần mở bài; (5) có lỗi chỉ lộ trên máy thật nên kiểm thiết bị sớm (Task 10, ngay khi sheet + PDF chạy).
- Thiết bị: chỉ Pixel `2B051FDH3006MU` và Tecno KJ7 `115333744A005844`. Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation, `topResumedActivity`), `ps` tiến trình lạ; dừng và hỏi nếu có. Chỉ `force-stop com.mckimquyen.reader`. `run-as com.mckimquyen.reader` (không `--user`). Backup/restore **byte-exact** Room (+`-shm`/`-wal`), `settings.preferences_pb`, `shared_prefs/*.xml` liên quan. Không xoá DataStore. **Không** `connected*`: dùng `adb -s <serial> shell am instrument` có `timeout`. Quảng cáo toàn màn hình chỉ Back, không bấm "Tiếp tục đến ứng dụng". Màn khoá thì hỏi owner. Trước khi plan được duyệt, thiết bị chỉ đọc.
- **Quota Gemini:** key chung đang `RateLimited`, key #1 `API_KEY_INVALID` (FIX-09 hoãn). Smoke thật tối đa ~5 request; gặp `RateLimited` thì dừng và hỏi owner đặt key riêng, không gọi dồn.
- Báo cáo `doc/task/done/FRONT-01_ai-multi-agent-deep-dive_DONE.md`, chuyển file task từ `todo/`, cập nhật `doc/feature.md`. Không push, không PR, không `.idea`.

---

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `ui/ext/ExtDataStore.kt` (sửa) | `DataStoreKeys.DeepDiveGeminiConsent`, `Context.deepDiveGeminiConsent`, `setDeepDiveGeminiConsent` | 1 |
| `ui/page/setting/privacy/PrivacySettingsViewModel.kt`, `PrivacySettingsPage.kt` (sửa) | Công tắc consent Deep Dive | 1 |
| `domain/ai/agents/DeepDiveReport.kt` (mới) | Model báo cáo | 2 |
| `domain/ai/agents/DeepDivePolicy.kt` (mới) | Hằng số, sanitize, prompt, request body, parse + kiểm (thuần) | 2, 3 |
| `domain/sv/AiDeepDiveService.kt` (mới) | Gọi `AiRequestGateway` | 4 |
| `ui/page/home/read/deepdive/DeepDiveUiState.kt` (mới) | `DeepDiveUiState`, `DeepDiveCache` | 5 |
| `ui/page/home/read/deepdive/DeepDiveViewModel.kt` (mới) | Điều phối consent, gọi, cache, thử lại, chia sẻ | 7 |
| `domain/ai/agents/DeepDivePdfLayout.kt` (mới) | Chọn cỡ chữ và cắt ý để vừa một trang (thuần) | 6 |
| `infrastructure/ai/DeepDivePdfRenderer.kt`, `DeepDiveSharer.kt` (mới) | Vẽ PDF, ghi tệp, intent chia sẻ | 6 |
| `res/values*/strings.xml` ×38 (sửa), `PrivacySettingsPage.kt` | Chuỗi `deep_dive_*` (một script, parity không bao giờ đỏ) và công tắc | 8 |
| `ui/page/home/read/deepdive/DeepDiveSheet.kt` (mới) | Sheet 6 trạng thái | 9 |
| `ui/page/home/read/TopBar.kt`, `ReadingPage.kt` (sửa) | Mục menu và nối sheet | 9 |
| `androidTest/.../DeepDiveSheetWidgetTest.kt` (mới) | Test Compose trên máy | 10 |
| tài liệu | Báo cáo, feature.md, task | 12 |

Quy ước lệnh test (mỗi lần chạy):
`./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/f01-<ten>.log 2>&1; echo exit=$? > /tmp/f01-<ten>.exit`
rồi đọc `app/build/test-results/testDevDebugUnitTest/TEST-<FQCN>.xml` (`tests=`, `failures=`, `errors=`, thông điệp lỗi) trước khi tin kết quả.

---

### Task 1: Consent riêng cho Deep Dive (DataStore + công tắc Quyền riêng tư)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` (sau `QuizGeminiConsent`, và sau `setQuizGeminiConsent`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/ext/DeepDiveConsentTest.kt` (mới), `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt` (thêm 2 test)

**Interfaces:**
- Produces: `DataStoreKeys.DeepDiveGeminiConsent` (khoá `deep_dive_gemini_consent`), `val Context.deepDiveGeminiConsent: Boolean?`, `suspend fun Context.setDeepDiveGeminiConsent(agreed: Boolean)`, `PrivacySettingsViewModel.deepDiveGeminiConsent: StateFlow<Boolean>`, `PrivacySettingsViewModel.setDeepDiveGeminiConsent(Boolean)`.
- Task này **chỉ** sửa `ExtDataStore.kt`, `PrivacySettingsViewModel.kt` và test; công tắc trong `PrivacySettingsPage.kt` thêm ở Task 8 cùng các chuỗi để build không vỡ.

- [ ] **Step 1: Viết test đỏ `DeepDiveConsentTest`**

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
class DeepDiveConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.deepDiveGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip(): Unit = runBlocking {
        context.setDeepDiveGeminiConsent(true)
        assertEquals(true, context.deepDiveGeminiConsent)
        context.setDeepDiveGeminiConsent(false)
        assertEquals(false, context.deepDiveGeminiConsent)
    }

    @Test
    fun deepDiveConsentIsIndependentOfQuizAndPodcastStudio(): Unit = runBlocking {
        context.setQuizGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("quiz/podcast consent must not agree for Deep Dive", null, context.deepDiveGeminiConsent)

        context.setDeepDiveGeminiConsent(false)
        assertEquals(true, context.quizGeminiConsent)
        assertEquals(true, context.podcastStudioGeminiConsent)
    }

    @Test
    fun keyNameIsDistinctFromTheOtherGeminiConsentKeys() {
        val name = DataStoreKeys.DeepDiveGeminiConsent.key.name
        assertEquals("deep_dive_gemini_consent", name)
        assertNotEquals(DataStoreKeys.QuizGeminiConsent.key.name, name)
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, name)
    }
}
```

- [ ] **Step 2: Thêm 2 test vào `PrivacySettingsViewModelTest` (cuối class, theo mẫu quiz)**

```kotlin
    @Test
    fun deepDiveGeminiConsent_isOffUntilTheUserAgreesAndPersistsBothWays() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()
        assertFalse(viewModel.deepDiveGeminiConsent.value)
        assertEquals(null, context.deepDiveGeminiConsent)

        viewModel.setDeepDiveGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.deepDiveGeminiConsent.first { it } })
        assertEquals(true, context.deepDiveGeminiConsent)

        viewModel.setDeepDiveGeminiConsent(false)
        advanceUntilIdle()
        assertFalse(awaitReal { viewModel.deepDiveGeminiConsent.first { !it } })
        assertEquals(false, context.deepDiveGeminiConsent)
    }

    @Test
    fun deepDiveSwitchDoesNotTouchTheQuizSwitch() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()

        viewModel.setDeepDiveGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.deepDiveGeminiConsent.first { it } })
        assertFalse("the quiz switch must stay off", viewModel.quizGeminiConsent.value)
        assertEquals(null, context.quizGeminiConsent)
    }
```

Thêm import `com.mckimquyen.reader.ui.ext.deepDiveGeminiConsent`.

- [ ] **Step 3: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.ext.DeepDiveConsentTest" --tests "com.mckimquyen.reader.ui.page.setting.privacy.PrivacySettingsViewModelTest" > /tmp/f01-t1-red.log 2>&1; echo exit=$? > /tmp/f01-t1-red.exit`
Expected: `exit=1`, lỗi biên dịch `Unresolved reference: deepDiveGeminiConsent` (đọc trong `/tmp/f01-t1-red.log`, không phải XML vì chưa biên dịch được).

- [ ] **Step 4: Code tối thiểu trong `ExtDataStore.kt`**

Thêm trong `sealed class DataStoreKeys`, ngay sau `QuizGeminiConsent`:

```kotlin
    /** FRONT-01: answer to "send the title and up to 6000 characters of the article to Gemini for a Deep Dive?". Absent means not asked yet. */
    object DeepDiveGeminiConsent : DataStoreKeys<Boolean>() {
        override val key: Preferences.Key<Boolean>
            get() = booleanPreferencesKey("deep_dive_gemini_consent")
    }
```

Thêm sau `setQuizGeminiConsent`:

```kotlin
/** FRONT-01: null until the user answers the Deep Dive sheet's Gemini question; then true (allowed) or false (declined). */
val Context.deepDiveGeminiConsent: Boolean?
    get() = this.dataStore.get(DataStoreKeys.DeepDiveGeminiConsent)

suspend fun Context.setDeepDiveGeminiConsent(agreed: Boolean) {
    this.dataStore.put(DataStoreKeys.DeepDiveGeminiConsent, agreed)
}
```

- [ ] **Step 5: Code tối thiểu trong `PrivacySettingsViewModel.kt`**

Sau khối `_quizGeminiConsent`:

```kotlin
    // FRONT-01: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _deepDiveGeminiConsent = MutableStateFlow(false)
    val deepDiveGeminiConsent = _deepDiveGeminiConsent.asStateFlow()
```

Trong `collect { preferences -> ... }` thêm một dòng:

```kotlin
                _deepDiveGeminiConsent.value = preferences[DataStoreKeys.DeepDiveGeminiConsent.key] == true
```

Sau `setQuizGeminiConsent`:

```kotlin
    fun setDeepDiveGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.DeepDiveGeminiConsent.key] = agreed }
            }
        }
    }
```

- [ ] **Step 6: Chạy lại, thấy xanh**

Cùng lệnh Step 3 (đổi tên log `-green`). Expected `exit=0`; `DeepDiveConsentTest tests="4"`, `PrivacySettingsViewModelTest` thêm 2, `failures="0" errors="0"`.

- [ ] **Step 7: Mutation check (backup + `cmp`)**

Với từng đột biến, sao lưu file, sửa, chạy lại test, thấy đỏ ở test dự kiến, khôi phục và `cmp`:
1. Đổi tên khoá thành `quiz_gemini_consent` → `keyNameIsDistinctFromTheOtherGeminiConsentKeys` đỏ.
2. `getter` đọc `DataStoreKeys.QuizGeminiConsent` → `deepDiveConsentIsIndependentOfQuizAndPodcastStudio` đỏ.
3. Trong ViewModel, `== true` thành `!= false` → `deepDiveGeminiConsent_isOffUntilTheUserAgreesAndPersistsBothWays` đỏ.

```bash
F=app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt
cp "$F" /tmp/f01-ExtDataStore.bak
# sửa bằng python, chạy test, đọc XML, rồi:
cp /tmp/f01-ExtDataStore.bak "$F"; cmp /tmp/f01-ExtDataStore.bak "$F" && echo restored
```

- [ ] **Step 8: Commit (kiểm exit code trước)**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/ext/DeepDiveConsentTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt
git commit -m "feat(front-01): add a separate Gemini consent for AI Deep Dive

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 2: `DeepDiveReport` và `DeepDivePolicy` phần 1 — hằng số, sanitize, prompt, request body (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDiveReport.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicyPromptTest.kt`

**Interfaces:**
- Produces:
  - `data class DeepDiveReport(val articleId: String, val context: List<String>, val perspectives: List<DeepDivePerspective>, val impacts: List<DeepDiveImpact>)`
  - `data class DeepDivePerspective(val school: String, val view: String)`
  - `data class DeepDiveImpact(val area: String, val shortTerm: String, val longTerm: String)`
  - `object DeepDivePolicy { const val USE_CASE = "deep_dive"; const val MAX_EXCERPT_CHARS = 6000; fun sanitizeExcerpt(raw: String): String; fun hasContentToAnalyze(title: String, excerpt: String): Boolean; fun buildPrompt(title: String, excerpt: String, languageTag: String): String; fun buildRequestBody(prompt: String): String }`

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDivePolicyPromptTest {

    @Test
    fun sanitize_stripsHtmlAndCollapsesWhitespace() {
        assertEquals("Hello big world", DeepDivePolicy.sanitizeExcerpt("<p>Hello   <b>big</b>\n\nworld</p>"))
    }

    @Test
    fun sanitize_capsAtTheExcerptLimit() {
        val out = DeepDivePolicy.sanitizeExcerpt("a".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS + 500))
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS, out.length)
    }

    @Test
    fun sanitize_exactlyAtTheLimitIsKept() {
        val text = "b".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS)
        assertEquals(text, DeepDivePolicy.sanitizeExcerpt(text))
    }

    @Test
    fun sanitize_neverLeavesALoneHighSurrogateAtTheCut() {
        val raw = "a".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS - 1) + "😀" + "tail"
        val out = DeepDivePolicy.sanitizeExcerpt(raw)
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS - 1, out.length)
        assertFalse(Character.isHighSurrogate(out.last()))
    }

    @Test
    fun sanitize_blankStaysBlank() {
        assertEquals("", DeepDivePolicy.sanitizeExcerpt("  <br/>  \n "))
    }

    @Test
    fun hasContentToAnalyze_needsATitleOrAnExcerpt() {
        assertFalse(DeepDivePolicy.hasContentToAnalyze("  ", ""))
        assertTrue(DeepDivePolicy.hasContentToAnalyze("Title", ""))
        assertTrue(DeepDivePolicy.hasContentToAnalyze("", "Body"))
    }

    @Test
    fun prompt_carriesOnlyTitleAndExcerptAndTheLanguage() {
        val prompt = DeepDivePolicy.buildPrompt("Rates rise", "Central bank lifted rates.", "vi-VN")
        assertTrue(prompt.contains("Title: Rates rise"))
        assertTrue(prompt.contains("Excerpt: Central bank lifted rates."))
        assertTrue(prompt.contains("vi-VN"))
        assertFalse(prompt.contains("http"))
    }

    @Test
    fun prompt_forbidsInventingFactsAndWebLookups() {
        val prompt = DeepDivePolicy.buildPrompt("T", "E", "en-US")
        assertTrue(prompt.contains("Do not invent"))
        assertTrue(prompt.contains("no web"))
    }

    @Test
    fun prompt_describesTheThreeSectionsAndTheirLimits() {
        val prompt = DeepDivePolicy.buildPrompt("T", "E", "en-US")
        listOf("\"context\"", "\"critical\"", "\"impact\"", "\"school\"", "\"shortTerm\"", "\"longTerm\"")
            .forEach { assertTrue("missing $it", prompt.contains(it)) }
    }

    @Test
    fun requestBody_isJsonModeAndCarriesThePrompt() {
        val body = JSONObject(DeepDivePolicy.buildRequestBody("PROMPT"))
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("PROMPT", text)
    }

    @Test
    fun useCaseNameIsStable() {
        assertEquals("deep_dive", DeepDivePolicy.USE_CASE)
    }
}
```

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.ai.agents.DeepDivePolicyPromptTest" > /tmp/f01-t2-red.log 2>&1; echo exit=$? > /tmp/f01-t2-red.exit`
Expected: `exit=1`, `Unresolved reference: DeepDivePolicy` trong log.

- [ ] **Step 3: Code `DeepDiveReport.kt`**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

/** One AI-written Deep Dive (FRONT-01). Plain data: validated by [DeepDivePolicy.parseAndValidate] before it exists. */
data class DeepDiveReport(
    val articleId: String,
    val context: List<String>,
    val perspectives: List<DeepDivePerspective>,
    val impacts: List<DeepDiveImpact>,
)

data class DeepDivePerspective(val school: String, val view: String)

data class DeepDiveImpact(val area: String, val shortTerm: String, val longTerm: String)
```

- [ ] **Step 4: Code `DeepDivePolicy.kt` (phần 1; phần parse thêm ở Task 3)**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the AI Deep Dive: what may leave the device, how the request looks, and (Task 3) which answers are
 * accepted. Privacy boundary: the only article data reachable from here is the title and an excerpt of at most
 * [MAX_EXCERPT_CHARS]; the caller never hands over a link, a feed name or an account.
 */
object DeepDivePolicy {
    const val USE_CASE = "deep_dive"
    const val MAX_EXCERPT_CHARS = 6000
    const val CONTEXT_MIN = 3
    const val CONTEXT_MAX = 5
    const val PERSPECTIVE_MIN = 2
    const val PERSPECTIVE_MAX = 4
    const val IMPACT_MIN = 2
    const val IMPACT_MAX = 5
    const val MAX_POINT_CHARS = 220
    const val MAX_SCHOOL_CHARS = 40
    const val MAX_VIEW_CHARS = 240
    const val MAX_AREA_CHARS = 40
    const val MAX_IMPACT_CHARS = 160
    private const val TEMPERATURE = 0.4
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeExcerpt(raw: String): String {
        val cleaned = raw.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim()
        if (cleaned.length <= MAX_EXCERPT_CHARS) return cleaned
        var end = MAX_EXCERPT_CHARS
        // Never cut between the two halves of a surrogate pair.
        if (Character.isHighSurrogate(cleaned[end - 1])) end--
        return cleaned.substring(0, end).trim()
    }

    fun hasContentToAnalyze(title: String, excerpt: String): Boolean = title.isNotBlank() || excerpt.isNotBlank()

    fun buildPrompt(title: String, excerpt: String, languageTag: String): String = buildString {
        append("You are three analysts writing ONE one-page briefing about ONE news article.\n")
        append("Use ONLY the title and excerpt below plus general background knowledge; no web access, no browsing, no sources.\n")
        append("Do not invent figures, quotes, names or dates that the text does not state. If unsure, say so briefly.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown, with exactly these keys:\n")
        append("\"context\": array of $CONTEXT_MIN to $CONTEXT_MAX short strings (Context analyst: root causes and earlier developments),\n")
        append("\"critical\": array of $PERSPECTIVE_MIN to $PERSPECTIVE_MAX objects ")
        append("{\"school\": \"short label of the school of thought\", \"view\": \"its opposing or critical view\"} ")
        append("(Critical-thinking analyst),\n")
        append("\"impact\": array of $IMPACT_MIN to $IMPACT_MAX objects ")
        append("{\"area\": \"short area\", \"shortTerm\": \"effect within months\", \"longTerm\": \"effect over years\"} ")
        append("(Synthesis analyst: the impact matrix).\n")
        append("Keep every string concise. Target language tag: $languageTag.\n\n")
        append("Title: $title\n")
        append("Excerpt: $excerpt")
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
```

- [ ] **Step 5: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `DeepDivePolicyPromptTest tests="11" failures="0" errors="0"`.

- [ ] **Step 6: Mutation check (backup + `cmp`)**

1. Đổi `MAX_EXCERPT_CHARS` thành `6001` → `sanitize_capsAtTheExcerptLimit` đỏ.
2. Bỏ dòng `if (Character.isHighSurrogate...) end--` → `sanitize_neverLeavesALoneHighSurrogateAtTheCut` đỏ.
3. Xoá `put("responseMimeType", ...)` → `requestBody_isJsonModeAndCarriesThePrompt` đỏ.
4. Đổi `Excerpt:` thành `Link:` → `prompt_carriesOnlyTitleAndExcerptAndTheLanguage` đỏ.
5. Bỏ câu "no web access" → `prompt_forbidsInventingFactsAndWebLookups` đỏ.

Khôi phục từng lần bằng `cp` từ `/tmp/f01-DeepDivePolicy.bak` rồi `cmp`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/ai/agents app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicyPromptTest.kt
git commit -m "feat(front-01): add the Deep Dive report model, excerpt limit and prompt

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `DeepDivePolicy` phần 2 — parse và kiểm cấu trúc (thuần)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicy.kt` (thêm vào cuối `object`, và thêm hai kiểu ngoài `object`)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicyParseTest.kt`

**Interfaces:**
- Consumes: `DeepDiveReport`, `DeepDivePerspective`, `DeepDiveImpact`, các hằng số Task 2.
- Produces: `enum class DeepDiveRejection { NOT_JSON, MISSING_FIELD, WRONG_CONTEXT_COUNT, WRONG_PERSPECTIVE_COUNT, WRONG_IMPACT_COUNT, EMPTY_TEXT, TEXT_TOO_LONG }`, `sealed interface DeepDiveParse { data class Valid(val report: DeepDiveReport); data class Rejected(val reason: DeepDiveRejection) }`, `DeepDivePolicy.parseAndValidate(raw: String, articleId: String): DeepDiveParse`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDivePolicyParseTest {

    private fun arr(items: List<String>) = items.joinToString(",", "[", "]") { "\"$it\"" }

    private fun json(
        context: List<String> = listOf("Rates were low for a decade", "Inflation rose after 2021", "The bank signalled a pivot"),
        critical: List<Pair<String, String>> = listOf("Monetarist" to "Tightening is overdue", "Keynesian" to "It risks a recession"),
        impact: List<Triple<String, String, String>> = listOf(
            Triple("Housing", "Mortgage costs rise", "Prices cool"),
            Triple("Exports", "Currency firms", "Margins shrink"),
        ),
    ): String {
        val c = critical.joinToString(",", "[", "]") { """{"school":"${it.first}","view":"${it.second}"}""" }
        val i = impact.joinToString(",", "[", "]") { """{"area":"${it.first}","shortTerm":"${it.second}","longTerm":"${it.third}"}""" }
        return """{"context":${arr(context)},"critical":$c,"impact":$i}"""
    }

    private fun parse(raw: String) = DeepDivePolicy.parseAndValidate(raw, articleId = "art-1")
    private fun rejected(raw: String): DeepDiveRejection = (parse(raw) as DeepDiveParse.Rejected).reason
    private fun valid(raw: String): DeepDiveReport = (parse(raw) as DeepDiveParse.Valid).report

    @Test fun validAnswer_isAccepted() {
        val r = valid(json())
        assertEquals("art-1", r.articleId)
        assertEquals(3, r.context.size)
        assertEquals("Monetarist", r.perspectives[0].school)
        assertEquals("Mortgage costs rise", r.impacts[0].shortTerm)
        assertEquals("Prices cool", r.impacts[0].longTerm)
    }

    @Test fun markdownFenceIsStripped() {
        assertTrue(parse("```json\n" + json() + "\n```") is DeepDiveParse.Valid)
    }

    @Test fun surroundingWhitespaceAndInnerRunsAreTidied() {
        val r = valid(json(context = listOf("  a   b  ", "c", "d")))
        assertEquals("a b", r.context[0])
    }

    @Test fun notJson_isRejected() {
        assertEquals(DeepDiveRejection.NOT_JSON, rejected("Sure! here is your report"))
        assertEquals(DeepDiveRejection.NOT_JSON, rejected(""))
    }

    @Test fun missingSection_isRejected() {
        assertEquals(DeepDiveRejection.MISSING_FIELD, rejected("""{"context":["a","b","c"],"critical":[]}"""))
        assertEquals(DeepDiveRejection.MISSING_FIELD, rejected("""{"context":"text","critical":[],"impact":[]}"""))
    }

    @Test fun contextCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_CONTEXT_COUNT, rejected(json(context = listOf("a", "b"))))
        assertTrue(parse(json(context = listOf("a", "b", "c"))) is DeepDiveParse.Valid)
        assertTrue(parse(json(context = listOf("a", "b", "c", "d", "e"))) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_CONTEXT_COUNT, rejected(json(context = listOf("a", "b", "c", "d", "e", "f"))))
    }

    @Test fun perspectiveCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT, rejected(json(critical = listOf("s" to "v"))))
        val four = (1..4).map { "s$it" to "v$it" }
        assertTrue(parse(json(critical = four)) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT, rejected(json(critical = four + ("s5" to "v5"))))
    }

    @Test fun impactCountBoundaries() {
        assertEquals(DeepDiveRejection.WRONG_IMPACT_COUNT, rejected(json(impact = listOf(Triple("a", "b", "c")))))
        val five = (1..5).map { Triple("a$it", "b$it", "c$it") }
        assertTrue(parse(json(impact = five)) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.WRONG_IMPACT_COUNT, rejected(json(impact = five + Triple("a6", "b6", "c6"))))
    }

    @Test fun emptyOrBlankText_isRejectedInEveryField() {
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(context = listOf("a", " ", "c"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(critical = listOf("" to "v", "s" to "v"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(critical = listOf("s" to " ", "s" to "v"))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("", "b", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("a", "", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected(json(impact = listOf(Triple("a", "b", ""), Triple("a", "b", "c")))))
    }

    @Test fun nonStringEntryInContext_isRejectedAsEmptyText() {
        assertEquals(DeepDiveRejection.EMPTY_TEXT, rejected("""{"context":["a",5,"c"],"critical":[{"school":"s","view":"v"},{"school":"s","view":"v"}],"impact":[{"area":"a","shortTerm":"b","longTerm":"c"},{"area":"a","shortTerm":"b","longTerm":"c"}]}"""))
    }

    @Test fun textLengthBoundaries() {
        val ok = "x".repeat(DeepDivePolicy.MAX_POINT_CHARS)
        val long = "x".repeat(DeepDivePolicy.MAX_POINT_CHARS + 1)
        assertTrue(parse(json(context = listOf(ok, "b", "c"))) is DeepDiveParse.Valid)
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(context = listOf(long, "b", "c"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(critical = listOf("s".repeat(DeepDivePolicy.MAX_SCHOOL_CHARS + 1) to "v", "s" to "v"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(critical = listOf("s" to "v".repeat(DeepDivePolicy.MAX_VIEW_CHARS + 1), "s" to "v"))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a".repeat(DeepDivePolicy.MAX_AREA_CHARS + 1), "b", "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a", "b".repeat(DeepDivePolicy.MAX_IMPACT_CHARS + 1), "c"), Triple("a", "b", "c")))))
        assertEquals(DeepDiveRejection.TEXT_TOO_LONG, rejected(json(impact = listOf(Triple("a", "b", "c".repeat(DeepDivePolicy.MAX_IMPACT_CHARS + 1)), Triple("a", "b", "c")))))
    }
}
```

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.ai.agents.DeepDivePolicyParseTest" > /tmp/f01-t3-red.log 2>&1; echo exit=$? > /tmp/f01-t3-red.exit`
Expected: `exit=1`, `Unresolved reference: parseAndValidate` / `DeepDiveParse`.

- [ ] **Step 3: Code (thêm trước `object DeepDivePolicy` hai kiểu, và vào trong object hàm parse)**

```kotlin
enum class DeepDiveRejection {
    NOT_JSON, MISSING_FIELD, WRONG_CONTEXT_COUNT, WRONG_PERSPECTIVE_COUNT, WRONG_IMPACT_COUNT, EMPTY_TEXT, TEXT_TOO_LONG,
}

sealed interface DeepDiveParse {
    data class Valid(val report: DeepDiveReport) : DeepDiveParse
    data class Rejected(val reason: DeepDiveRejection) : DeepDiveParse
}
```

Trong `object DeepDivePolicy`, thêm `import org.json.JSONException`, hằng `private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")` và:

```kotlin
    /** Structure only: nothing here can tell whether the content is true. */
    fun parseAndValidate(raw: String, articleId: String): DeepDiveParse {
        fun reject(r: DeepDiveRejection): DeepDiveParse = DeepDiveParse.Rejected(r)
        val obj = try {
            JSONObject(raw.trim().replace(FENCE, "").trim())
        } catch (_: JSONException) {
            return reject(DeepDiveRejection.NOT_JSON)
        }
        val contextArr = obj.optJSONArray("context")
        val criticalArr = obj.optJSONArray("critical")
        val impactArr = obj.optJSONArray("impact")
        if (contextArr == null || criticalArr == null || impactArr == null) return reject(DeepDiveRejection.MISSING_FIELD)
        if (contextArr.length() !in CONTEXT_MIN..CONTEXT_MAX) return reject(DeepDiveRejection.WRONG_CONTEXT_COUNT)
        if (criticalArr.length() !in PERSPECTIVE_MIN..PERSPECTIVE_MAX) return reject(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT)
        if (impactArr.length() !in IMPACT_MIN..IMPACT_MAX) return reject(DeepDiveRejection.WRONG_IMPACT_COUNT)

        var tooLong = false
        // Returns null for a missing, non-string or blank value; flags [tooLong] when the cleaned text exceeds [max].
        fun text(value: Any?, max: Int): String? {
            val s = (value as? String)?.let(::tidy).orEmpty()
            if (s.isEmpty()) return null
            if (s.length > max) tooLong = true
            return s
        }

        val context = (0 until contextArr.length()).map { text(contextArr.opt(it), MAX_POINT_CHARS) }
        val perspectives = (0 until criticalArr.length()).map {
            val o = criticalArr.optJSONObject(it)
            val school = text(o?.opt("school"), MAX_SCHOOL_CHARS)
            val view = text(o?.opt("view"), MAX_VIEW_CHARS)
            if (school == null || view == null) null else DeepDivePerspective(school, view)
        }
        val impacts = (0 until impactArr.length()).map {
            val o = impactArr.optJSONObject(it)
            val area = text(o?.opt("area"), MAX_AREA_CHARS)
            val short = text(o?.opt("shortTerm"), MAX_IMPACT_CHARS)
            val long = text(o?.opt("longTerm"), MAX_IMPACT_CHARS)
            if (area == null || short == null || long == null) null else DeepDiveImpact(area, short, long)
        }
        if (context.any { it == null } || perspectives.any { it == null } || impacts.any { it == null }) {
            return reject(DeepDiveRejection.EMPTY_TEXT)
        }
        if (tooLong) return reject(DeepDiveRejection.TEXT_TOO_LONG)
        return DeepDiveParse.Valid(
            DeepDiveReport(articleId, context.filterNotNull(), perspectives.filterNotNull(), impacts.filterNotNull())
        )
    }

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()
```

- [ ] **Step 4: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `DeepDivePolicyParseTest tests="11" failures="0" errors="0"`. Nếu lỗi biên dịch về `long`/`short` là từ khoá: đổi tên biến thành `shortText`/`longText`.

- [ ] **Step 5: Mutation check (backup + `cmp`)**

Mỗi biên (`CONTEXT_MAX`→`+1`, `CONTEXT_MIN`→`-1`, tương tự perspective/impact) phải làm `contextCountBoundaries`/`perspectiveCountBoundaries`/`impactCountBoundaries` đỏ. Thêm: bỏ `if (tooLong)` → `textLengthBoundaries` đỏ; đổi `MAX_VIEW_CHARS` → `+1` → đỏ; bỏ `.replace(FENCE, "")` → `markdownFenceIsStripped` đỏ; `text()` không `tidy` → `surroundingWhitespaceAndInnerRunsAreTidied` đỏ; bỏ `as? String` (dùng `optString`) → `nonStringEntryInContext_isRejectedAsEmptyText` đỏ. Khôi phục và `cmp` mỗi lần.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicy.kt app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePolicyParseTest.kt
git commit -m "feat(front-01): validate the structure of the Deep Dive answer

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 4: `AiDeepDiveService` — một request Gemini qua `AiRequestGateway`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/AiDeepDiveService.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/AiDeepDiveServiceTest.kt`

**Interfaces:**
- Consumes: `DeepDivePolicy` (Task 2, 3), `AiRequestGateway.execute(useCase, requestBody, keys)`, `GeminiSummaryService.resolveApiKeys()`.
- Produces: `enum class AiDeepDiveFailure { NOTHING_TO_ANALYZE, NO_KEY, NETWORK, REJECTED }`, `sealed interface AiDeepDiveResult { Ready(report: DeepDiveReport); Failed(reason: AiDeepDiveFailure) }`, `@Singleton class AiDeepDiveService @Inject constructor(gateway: AiRequestGateway, summaryService: GeminiSummaryService)` với `suspend fun generate(articleId: String, title: String, bodyText: String, languageTag: String): AiDeepDiveResult`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.sv

import android.app.Application
import com.mckimquyen.reader.domain.ai.agents.DeepDivePolicy
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiDeepDiveServiceTest {

    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var service: AiDeepDiveService

    private val good = """{"context":["a","b","c"],"critical":[{"school":"s1","view":"v1"},{"school":"s2","view":"v2"}],"impact":[{"area":"x","shortTerm":"y","longTerm":"z"},{"area":"x2","shortTerm":"y2","longTerm":"z2"}]}"""

    @Before
    fun setUp() {
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        service = AiDeepDiveService(gateway, summaryService)
    }

    private fun run(title: String = "Rates rise", body: String = "<p>The bank lifted rates.</p>"): AiDeepDiveResult =
        runBlocking { service.generate("art-1", title, body, "en-US") }

    @Test
    fun validAnswer_isReady() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        val r = run() as AiDeepDiveResult.Ready
        assertEquals("art-1", r.report.articleId)
        assertEquals(3, r.report.context.size)
    }

    @Test
    fun usesTheDeepDiveUseCaseAndTheResolvedKeys() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        run()
        coVerify(exactly = 1) { gateway.execute("deep_dive", any(), listOf("key-a")) }
    }

    @Test
    fun requestBodyHoldsOnlyTitleAndCleanedExcerpt() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good
        run(title = "Rates rise", body = "<p>Central <b>bank</b> acts.</p>")

        val prompt = JSONObject(body.captured).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(prompt.contains("Title: Rates rise"))
        assertTrue(prompt.contains("Excerpt: Central bank acts."))
        assertFalse(prompt.contains("<p>"))
        assertFalse(prompt.contains("art-1"))
    }

    @Test
    fun longBodyIsCutToTheExcerptLimitBeforeSending() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good
        run(body = "z".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS * 2))
        val prompt = JSONObject(body.captured).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS, prompt.count { it == 'z' })
    }

    @Test
    fun nothingToAnalyze_neverCallsTheGateway() {
        val r = run(title = "  ", body = " <br/> ")
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NOTHING_TO_ANALYZE), r)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun noKey_neverCallsTheGateway() {
        every { summaryService.resolveApiKeys() } returns emptyList()
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY), run())
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun missingApiKeyException_mapsToNoKey() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.MissingApiKey
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY), run())
    }

    @Test
    fun rateLimited_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.RateLimited
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun httpError_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.Http(500)
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun ioFailure_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("boom")
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun rejectedAnswer_mapsToRejected() {
        coEvery { gateway.execute(any(), any(), any()) } returns "not json at all"
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED), run())
    }

    @Test
    fun cancellation_isRethrown_notSwallowed() {
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")
        var thrown: Throwable? = null
        try { run() } catch (e: CancellationException) { thrown = e }
        assertTrue(thrown is CancellationException)
    }
}
```


- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.AiDeepDiveServiceTest" > /tmp/f01-t4-red.log 2>&1; echo exit=$? > /tmp/f01-t4-red.exit`
Expected: `exit=1`, `Unresolved reference: AiDeepDiveService`.

- [ ] **Step 3: Code `AiDeepDiveService.kt`**

```kotlin
package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.ai.agents.DeepDiveParse
import com.mckimquyen.reader.domain.ai.agents.DeepDivePolicy
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

enum class AiDeepDiveFailure { NOTHING_TO_ANALYZE, NO_KEY, NETWORK, REJECTED }

sealed interface AiDeepDiveResult {
    data class Ready(val report: DeepDiveReport) : AiDeepDiveResult
    data class Failed(val reason: AiDeepDiveFailure) : AiDeepDiveResult
}

/**
 * Writes one Deep Dive report with Gemini (FRONT-01) in a single request. Privacy boundary: the only article data that
 * can leave the device is the title and an excerpt of at most [DeepDivePolicy.MAX_EXCERPT_CHARS]; this class is handed
 * those strings, never an article. It does not check the user's consent: [DeepDiveViewModel] is the single place that
 * decides whether to call, and it only calls when the consent is `true`.
 */
@Singleton
class AiDeepDiveService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    suspend fun generate(articleId: String, title: String, bodyText: String, languageTag: String): AiDeepDiveResult {
        val cleanTitle = title.trim()
        val excerpt = DeepDivePolicy.sanitizeExcerpt(bodyText)
        if (!DeepDivePolicy.hasContentToAnalyze(cleanTitle, excerpt)) {
            return AiDeepDiveResult.Failed(AiDeepDiveFailure.NOTHING_TO_ANALYZE)
        }
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY)

        val body = DeepDivePolicy.buildRequestBody(DeepDivePolicy.buildPrompt(cleanTitle, excerpt, languageTag))
        val raw = try {
            gateway.execute(DeepDivePolicy.USE_CASE, body, keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiSummaryService.SummaryException) {
            // MissingApiKey is an object inside a sealed SummaryException, so it is matched by identity.
            // Only the exception type is logged: a message could carry the title or the excerpt.
            Log.w(TAG, "Deep Dive request failed: ${e.javaClass.simpleName}")
            return AiDeepDiveResult.Failed(
                if (e === GeminiSummaryService.SummaryException.MissingApiKey) AiDeepDiveFailure.NO_KEY else AiDeepDiveFailure.NETWORK
            )
        } catch (e: Exception) {
            Log.w(TAG, "Deep Dive request failed: ${e.javaClass.simpleName}")
            return AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK)
        }
        return when (val parsed = DeepDivePolicy.parseAndValidate(raw, articleId)) {
            is DeepDiveParse.Valid -> AiDeepDiveResult.Ready(parsed.report)
            is DeepDiveParse.Rejected -> {
                Log.w(TAG, "Deep Dive answer rejected: ${parsed.reason}")
                AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED)
            }
        }
    }

    private companion object {
        const val TAG = "AiDeepDiveService"
    }
}
```

- [ ] **Step 4: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `failures="0" errors="0"`. Nếu `GeminiSummaryService` là `final` và mockk lỗi, dùng cách dựng giống `AiQuizServiceTest` (đã chạy được với `mockk()`).

- [ ] **Step 5: Mutation check (backup + `cmp`)**

Bỏ `if (keys.isEmpty())` → `noKey_neverCallsTheGateway` đỏ; đổi `USE_CASE` đối số thành `"quiz_micro"` → `usesTheDeepDiveUseCaseAndTheResolvedKeys` đỏ; bỏ `catch (e: CancellationException) { throw e }` → `cancellation_isRethrown_notSwallowed` đỏ; bỏ `hasContentToAnalyze` → `nothingToAnalyze_neverCallsTheGateway` đỏ; truyền `bodyText` thay `excerpt` vào `buildPrompt` → `longBodyIsCutToTheExcerptLimitBeforeSending` đỏ; đổi `e ===` thành `e !==` → `missingApiKeyException_mapsToNoKey` đỏ. Khôi phục + `cmp`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/sv/AiDeepDiveService.kt app/src/test/java/com/mckimquyen/reader/domain/sv/AiDeepDiveServiceTest.kt
git commit -m "feat(front-01): ask Gemini once for the Deep Dive through the shared gateway

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: `DeepDiveUiState` và `DeepDiveCache` (LRU singleton)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveUiState.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveCacheTest.kt`

**Interfaces:**
- Consumes: `DeepDiveReport`, `AiDeepDiveFailure`.
- Produces: `sealed interface DeepDiveUiState { Idle; AwaitingConsent; ConsentDeclined; Loading; Ready(report); Failed(reason) }`, `@Singleton class DeepDiveCache @Inject constructor()` với `constructor(capacity: Int)` (chỉ để test), `get(articleId)`, `put(articleId, report)`, `size`, `DEFAULT_CAPACITY = 10`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import javax.inject.Singleton

class DeepDiveCacheTest {

    private fun report(id: String) = DeepDiveReport(id, listOf("a", "b", "c"), emptyList(), emptyList())

    @Test fun missIsNull_hitReturnsTheSameReport() {
        val cache = DeepDiveCache()
        assertNull(cache.get("x"))
        val r = report("x")
        cache.put("x", r)
        assertSame(r, cache.get("x"))
    }

    @Test fun defaultCapacityIsTen() {
        assertEquals(10, DeepDiveCache.DEFAULT_CAPACITY)
        val cache = DeepDiveCache()
        (1..11).forEach { cache.put("a$it", report("a$it")) }
        assertEquals(10, cache.size)
        assertNull("the oldest entry is evicted", cache.get("a1"))
        assertNotNull(cache.get("a11"))
    }

    @Test fun readingAnEntryProtectsItFromEviction() {
        val cache = DeepDiveCache(capacity = 2)
        cache.put("a", report("a"))
        cache.put("b", report("b"))
        cache.get("a")
        cache.put("c", report("c"))
        assertNotNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test fun putTwiceKeepsOneEntryWithTheNewValue() {
        val cache = DeepDiveCache(capacity = 3)
        cache.put("a", report("a"))
        val newer = report("a")
        cache.put("a", newer)
        assertEquals(1, cache.size)
        assertSame(newer, cache.get("a"))
    }

    @Test fun capacityBelowOneIsTreatedAsOne() {
        val cache = DeepDiveCache(capacity = 0)
        cache.put("a", report("a"))
        cache.put("b", report("b"))
        assertEquals(1, cache.size)
    }

    @Test fun cacheIsAHiltSingleton_soItOutlivesTheViewModel() {
        assertNotNull("without @Singleton every opened article would start with an empty cache", DeepDiveCache::class.java.getAnnotation(Singleton::class.java))
    }
}
```

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveCacheTest" > /tmp/f01-t5-red.log 2>&1; echo exit=$? > /tmp/f01-t5-red.exit`
Expected: `exit=1`, `Unresolved reference: DeepDiveCache`.

- [ ] **Step 3: Code `DeepDiveUiState.kt`**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import javax.inject.Inject
import javax.inject.Singleton

/** What the Deep Dive sheet shows (FRONT-01). */
sealed interface DeepDiveUiState {
    /** Nothing is open. */
    data object Idle : DeepDiveUiState
    data object AwaitingConsent : DeepDiveUiState
    data object ConsentDeclined : DeepDiveUiState
    data object Loading : DeepDiveUiState
    data class Ready(val report: DeepDiveReport) : DeepDiveUiState
    data class Failed(val reason: AiDeepDiveFailure) : DeepDiveUiState
}

/**
 * In-memory, least-recently-used cache of ready reports by article id. Only ready reports are stored. A singleton on
 * purpose: `hiltViewModel()` creates a new view model each time the sheet's screen is composed, so a cache held by the
 * view model would be empty again and the same article would call Gemini again (found on a phone in RPG-02).
 */
@Singleton
class DeepDiveCache @Inject constructor() {
    private var capacity: Int = DEFAULT_CAPACITY

    /** For tests only: a cache with a smaller capacity. */
    constructor(capacity: Int) : this() {
        this.capacity = capacity
    }

    private val limit get() = capacity.coerceAtLeast(1)

    // accessOrder = true: get() and put() move an entry to the end, so the eldest entry is the least recently used.
    private val entries = object : LinkedHashMap<String, DeepDiveReport>(INITIAL_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DeepDiveReport>?): Boolean = size > limit
    }

    val size: Int get() = synchronized(this) { entries.size }

    @Synchronized
    fun get(articleId: String): DeepDiveReport? = entries[articleId]

    @Synchronized
    fun put(articleId: String, report: DeepDiveReport) {
        entries[articleId] = report
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
        private const val INITIAL_SIZE = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
```

- [ ] **Step 4: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `DeepDiveCacheTest tests="6" failures="0"`.

- [ ] **Step 5: Mutation check**

Bỏ `true` (accessOrder) → `readingAnEntryProtectsItFromEviction` đỏ; `DEFAULT_CAPACITY` 11 → `defaultCapacityIsTen` đỏ; bỏ `@Singleton` → `cacheIsAHiltSingleton_soItOutlivesTheViewModel` đỏ; bỏ `coerceAtLeast(1)` → `capacityBelowOneIsTreatedAsOne` đỏ. Khôi phục + `cmp`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveUiState.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveCacheTest.kt
git commit -m "feat(front-01): keep ready Deep Dive reports in a singleton LRU cache

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 6: PDF một trang — `DeepDivePdfLayout` (thuần), `DeepDivePdfRenderer`, `DeepDiveSharer`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePdfLayout.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDivePdfRenderer.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDiveSharer.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePdfLayoutTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/DeepDivePdfRendererTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/DeepDiveSharerTest.kt`

**Interfaces:**
- Produces:
  - `fun interface DeepDiveTextMeasurer { fun height(text: String, sizePt: Float, widthPt: Float, bold: Boolean): Float }`
  - `data class DeepDivePdfPlan(val sizePt: Float, val report: DeepDiveReport, val truncated: Boolean)`
  - `object DeepDivePdfLayout { const val PAGE_WIDTH = 595; const val PAGE_HEIGHT = 842; const val MARGIN = 36; val SIZE_TIERS: List<Float>; fun plan(report: DeepDiveReport, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): DeepDivePdfPlan; fun contentHeight(plan, title, texts, measurer): Float }`
  - `data class DeepDivePdfTexts(val aiLabel: String, val headingContext: String, val headingCritical: String, val headingImpact: String, val colArea: String, val colShort: String, val colLong: String, val truncatedNote: String, val dateText: String)`
  - `object DeepDivePdfRenderer { fun drawPage(canvas: android.graphics.Canvas, plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts); fun render(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts): ByteArray }`
  - `class DeepDiveSharer @Inject constructor(@ApplicationContext context: Context, @IODispatcher ioDispatcher: CoroutineDispatcher)` với `suspend fun write(pdf: ByteArray, title: String, nowMillis: Long = System.currentTimeMillis()): File`, `fun buildShareIntent(file: File, chooserTitle: String): Intent`, `DIR_NAME = "deepdive"`, `RETENTION_MS`, `MIME_PDF = "application/pdf"`.

Quy tắc layout: thử cỡ chữ 10 pt rồi 9 pt; nếu vẫn tràn thì bỏ dần từ cuối theo thứ tự: dòng ma trận (giữ tối thiểu 2), ý Bối cảnh (giữ tối thiểu 3), góc Phản biện (giữ tối thiểu 2); có bỏ thì `truncated = true` và PDF in dòng `truncatedNote`. Không bao giờ tạo trang thứ hai; vẽ tới lề dưới thì dừng.

- [ ] **Step 1: Viết test layout đỏ (thuần, không Robolectric)**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class DeepDivePdfLayoutTest {

    private val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More in the app", "2026-11-11")

    // Fake measurer: every character is 0.5 * size wide, lines are 1.2 * size tall.
    private val measurer = DeepDiveTextMeasurer { text, size, width, _ ->
        val perLine = (width / (size * 0.5f)).toInt().coerceAtLeast(1)
        ceil(text.length.toFloat() / perLine).coerceAtLeast(1f) * size * 1.2f
    }

    private fun report(contextN: Int, perspN: Int, impactN: Int, len: Int = 40) = DeepDiveReport(
        "a",
        (1..contextN).map { "c$it".padEnd(len, 'x') },
        (1..perspN).map { DeepDivePerspective("s$it", "v$it".padEnd(len, 'x')) },
        (1..impactN).map { DeepDiveImpact("a$it", "s$it".padEnd(len, 'x'), "l$it".padEnd(len, 'x')) },
    )

    private fun fits(plan: DeepDivePdfPlan) =
        DeepDivePdfLayout.contentHeight(plan, "Title", texts, measurer) <= DeepDivePdfLayout.PAGE_HEIGHT - 2f * DeepDivePdfLayout.MARGIN

    @Test fun smallReport_keepsEverythingAtTheLargestSize() {
        val plan = DeepDivePdfLayout.plan(report(3, 2, 2), "Title", texts, measurer)
        assertEquals(DeepDivePdfLayout.SIZE_TIERS.first(), plan.sizePt)
        assertFalse(plan.truncated)
        assertEquals(report(3, 2, 2), plan.report)
        assertTrue(fits(plan))
    }

    @Test fun biggerReport_dropsToTheSmallerSizeBeforeDroppingAnything() {
        // Find a report that does not fit at the first size but fits at the second with nothing dropped.
        val candidate = (1..400 step 10).map { report(5, 4, 5, len = it) }.first { r ->
            val first = DeepDivePdfPlan(DeepDivePdfLayout.SIZE_TIERS[0], r, false)
            val second = DeepDivePdfPlan(DeepDivePdfLayout.SIZE_TIERS[1], r, false)
            !fits(first) && fits(second)
        }
        val plan = DeepDivePdfLayout.plan(candidate, "Title", texts, measurer)
        assertEquals(DeepDivePdfLayout.SIZE_TIERS[1], plan.sizePt)
        assertFalse(plan.truncated)
        assertEquals(candidate, plan.report)
    }

    @Test fun hugeReport_isTrimmedFromTheEndAndFlaggedTruncated() {
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5, len = 240), "Title", texts, measurer)
        assertTrue(plan.truncated)
        assertTrue(fits(plan))
        assertTrue(plan.report.impacts.size in 2..5)
        assertTrue(plan.report.context.size in 3..5)
        assertTrue(plan.report.perspectives.size in 2..4)
    }

    @Test fun trimmingKeepsTheFirstItemsAndTheirOrder() {
        val original = report(5, 4, 5, len = 240)
        val plan = DeepDivePdfLayout.plan(original, "Title", texts, measurer)
        assertEquals(original.impacts.take(plan.report.impacts.size), plan.report.impacts)
        assertEquals(original.context.take(plan.report.context.size), plan.report.context)
        assertEquals(original.perspectives.take(plan.report.perspectives.size), plan.report.perspectives)
    }

    @Test fun impactRowsAreTrimmedBeforeContextAndPerspectives() {
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5, len = 240), "Title", texts, measurer)
        if (plan.report.impacts.size > 2) {
            assertEquals("context must be intact while impact rows remain above the minimum", 5, plan.report.context.size)
            assertEquals(4, plan.report.perspectives.size)
        }
    }

    @Test fun neverTrimsBelowTheMinimums() {
        val tiny = DeepDiveTextMeasurer { _, size, _, _ -> size * 400f } // nothing can ever fit
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5), "Title", texts, tiny)
        assertEquals(2, plan.report.impacts.size)
        assertEquals(3, plan.report.context.size)
        assertEquals(2, plan.report.perspectives.size)
        assertTrue(plan.truncated)
    }

    @Test fun pageIsA4InPoints() {
        assertEquals(595, DeepDivePdfLayout.PAGE_WIDTH)
        assertEquals(842, DeepDivePdfLayout.PAGE_HEIGHT)
    }
}
```

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayoutTest" > /tmp/f01-t6a-red.log 2>&1; echo exit=$? > /tmp/f01-t6a-red.exit`
Expected: `exit=1`, `Unresolved reference: DeepDivePdfLayout`.

- [ ] **Step 3: Code `DeepDivePdfLayout.kt`**

```kotlin
package com.mckimquyen.reader.domain.ai.agents

fun interface DeepDiveTextMeasurer {
    /** Height in points of [text] wrapped to [widthPt] at [sizePt]. */
    fun height(text: String, sizePt: Float, widthPt: Float, bold: Boolean): Float
}

data class DeepDivePdfTexts(
    val aiLabel: String,
    val headingContext: String,
    val headingCritical: String,
    val headingImpact: String,
    val colArea: String,
    val colShort: String,
    val colLong: String,
    val truncatedNote: String,
    val dateText: String,
)

data class DeepDivePdfPlan(val sizePt: Float, val report: DeepDiveReport, val truncated: Boolean)

/** Pure page planning for the one-page PDF: picks a size and trims from the end until the report fits A4 (FRONT-01). */
object DeepDivePdfLayout {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 36
    const val GAP = 6f
    const val BULLET_INDENT = 12f
    const val TITLE_EXTRA_PT = 4f
    const val HEADING_EXTRA_PT = 1f
    const val SMALL_EXTRA_PT = -1f
    const val MIN_IMPACT_ROWS = 2
    const val MIN_CONTEXT_POINTS = 3
    const val MIN_PERSPECTIVES = 2
    private const val IMPACT_COLUMNS = 3
    val SIZE_TIERS = listOf(10f, 9f)

    private val availableHeight get() = (PAGE_HEIGHT - 2 * MARGIN).toFloat()
    val contentWidth get() = (PAGE_WIDTH - 2 * MARGIN).toFloat()

    fun plan(report: DeepDiveReport, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): DeepDivePdfPlan {
        for (size in SIZE_TIERS) {
            val candidate = DeepDivePdfPlan(size, report, false)
            if (fits(candidate, title, texts, measurer)) return candidate
        }
        val size = SIZE_TIERS.last()
        var current = report
        while (true) {
            val plan = DeepDivePdfPlan(size, current, true)
            if (fits(plan, title, texts, measurer)) return plan
            current = when {
                current.impacts.size > MIN_IMPACT_ROWS -> current.copy(impacts = current.impacts.dropLast(1))
                current.context.size > MIN_CONTEXT_POINTS -> current.copy(context = current.context.dropLast(1))
                current.perspectives.size > MIN_PERSPECTIVES -> current.copy(perspectives = current.perspectives.dropLast(1))
                else -> return plan // Nothing left to drop; the renderer stops at the bottom margin and never adds a page.
            }
        }
    }

    private fun fits(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): Boolean =
        contentHeight(plan, title, texts, measurer) <= availableHeight

    fun contentHeight(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): Float {
        val s = plan.sizePt
        val w = contentWidth
        var h = measurer.height(title, s + TITLE_EXTRA_PT, w, true) + GAP
        h += measurer.height("${texts.aiLabel} · ${texts.dateText}", s + SMALL_EXTRA_PT, w, false) + GAP * 2
        h += measurer.height(texts.headingContext, s + HEADING_EXTRA_PT, w, true) + GAP
        plan.report.context.forEach { h += measurer.height(it, s, w - BULLET_INDENT, false) + GAP / 2 }
        h += GAP + measurer.height(texts.headingCritical, s + HEADING_EXTRA_PT, w, true) + GAP
        plan.report.perspectives.forEach { h += measurer.height("${it.school}: ${it.view}", s, w - BULLET_INDENT, false) + GAP / 2 }
        h += GAP + measurer.height(texts.headingImpact, s + HEADING_EXTRA_PT, w, true) + GAP
        val col = w / IMPACT_COLUMNS
        h += listOf(texts.colArea, texts.colShort, texts.colLong).maxOf { measurer.height(it, s, col, true) } + GAP / 2
        plan.report.impacts.forEach { row ->
            h += listOf(row.area, row.shortTerm, row.longTerm).maxOf { measurer.height(it, s, col, false) } + GAP / 2
        }
        if (plan.truncated) h += GAP + measurer.height(texts.truncatedNote, s + SMALL_EXTRA_PT, w, false)
        return h
    }
}
```

- [ ] **Step 4: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `tests="7" failures="0"`. Nếu `biggerReport_dropsToTheSmallerSize...` ném `NoSuchElementException` (không có ứng viên), mở rộng dải `len` trong test (đây là lỗi dữ liệu test, ghi lại và sửa dải, đừng nới code).

- [ ] **Step 5: Mutation check**

`SIZE_TIERS` chỉ còn một cỡ → `biggerReport_dropsToTheSmallerSize...` đỏ; đổi thứ tự `when` (context trước impact) → `impactRowsAreTrimmedBeforeContextAndPerspectives` đỏ; `MIN_IMPACT_ROWS` 1 → `neverTrimsBelowTheMinimums` đỏ; `dropLast` thành `drop(1)` → `trimmingKeepsTheFirstItemsAndTheirOrder` đỏ; `truncated=false` ở nhánh cắt → `hugeReport_isTrimmed...` đỏ. Khôi phục + `cmp`.

- [ ] **Step 6: Commit layout**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePdfLayout.kt app/src/test/java/com/mckimquyen/reader/domain/ai/agents/DeepDivePdfLayoutTest.kt
git commit -m "feat(front-01): plan a one-page PDF by shrinking then trimming from the end

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 7: Viết test `DeepDiveSharerTest` và `DeepDivePdfRendererTest` đỏ**

`DeepDiveSharerTest` (Robolectric, theo `DossierSharerTest`):

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepDiveSharerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val sharer = DeepDiveSharer(app, Dispatchers.Unconfined)
    private val dir get() = File(app.cacheDir, DeepDiveSharer.DIR_NAME)

    @Before fun clean() { dir.deleteRecursively() }
    @After fun cleanAfter() { dir.deleteRecursively() }

    private val pdf = "%PDF-1.4 fake".toByteArray()

    @Test fun writesBytesUnderTheDeepDiveCacheDirectory() = runBlocking {
        val f = sharer.write(pdf, "Rates rise", nowMillis = 1_000L)
        assertEquals(dir.canonicalPath, f.parentFile!!.canonicalPath)
        assertEquals("deepdive_rates_rise_1000.pdf", f.name)
        assertTrue(f.readBytes().contentEquals(pdf))
    }

    @Test fun slugIsAsciiAndCannotEscapeTheDirectory() = runBlocking {
        val f = sharer.write(pdf, "../../etc/passwd Ünï \u0000 ${"x".repeat(100)}", nowMillis = 5L)
        assertEquals(dir.canonicalPath, f.parentFile!!.canonicalPath)
        assertTrue(Regex("deepdive_[a-z0-9_]{1,40}_5\\.pdf").matches(f.name))
    }

    @Test fun emptyTitleFallsBackToAFixedSlug() = runBlocking {
        assertEquals("deepdive_report_7.pdf", sharer.write(pdf, "  ", nowMillis = 7L).name)
    }

    @Test fun filesOlderThanTheRetentionAreDeletedOnWrite() = runBlocking {
        val old = sharer.write(pdf, "old", nowMillis = 1_000L)
        old.setLastModified(1_000L)
        val fresh = sharer.write(pdf, "new", nowMillis = DeepDiveSharer.RETENTION_MS + 2_000L)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test fun shareIntentIsASendOfAPdfWithAReadGrant() = runBlocking {
        val f = sharer.write(pdf, "t", nowMillis = 1L)
        val chooser = sharer.buildShareIntent(f, "Share")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
```

`DeepDivePdfRendererTest` (Robolectric native graphics; kiểm điều thật sự vẽ được):

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfPlan
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepDivePdfRendererTest {

    private val texts = DeepDivePdfTexts("AI wrote this", "Context", "Critical", "Impact", "Area", "Short", "Long", "More in the app", "2026-11-11")
    private val report = DeepDiveReport(
        "a",
        listOf("Rates were low", "Inflation rose", "The bank pivoted"),
        listOf(DeepDivePerspective("Monetarist", "Overdue"), DeepDivePerspective("Keynesian", "Risky")),
        listOf(DeepDiveImpact("Housing", "Costs rise", "Prices cool"), DeepDiveImpact("Exports", "Currency firms", "Margins shrink")),
    )

    private fun blankPage(): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(DeepDivePdfLayout.PAGE_WIDTH, DeepDivePdfLayout.PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
        return bmp to canvas
    }

    private fun anyInkInRows(bmp: Bitmap, from: Int, to: Int): Boolean {
        for (y in from until to) for (x in 0 until bmp.width) if (bmp.getPixel(x, y) != Color.WHITE) return true
        return false
    }

    @Test fun drawPage_paintsInsideTheMargins() {
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, report, false), "Rates rise", texts)
        assertTrue("something must be drawn", anyInkInRows(bmp, DeepDivePdfLayout.MARGIN, DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN))
    }

    @Test fun drawPage_leavesTheBottomMarginEmptyEvenForAHugeReport() {
        val long = "word ".repeat(60)
        val huge = DeepDiveReport("a", List(5) { long }, List(4) { DeepDivePerspective("s", long) }, List(5) { DeepDiveImpact("a", long, long) })
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, huge, true), "Title", texts)
        assertEquals(false, anyInkInRows(bmp, DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN + 1, DeepDivePdfLayout.PAGE_HEIGHT))
    }

    @Test fun drawPage_leavesTheSideMarginsEmpty() {
        val (bmp, canvas) = blankPage()
        DeepDivePdfRenderer.drawPage(canvas, DeepDivePdfPlan(10f, report, false), "Rates rise", texts)
        var ink = false
        for (y in 0 until bmp.height) for (x in 0 until DeepDivePdfLayout.MARGIN - 1) if (bmp.getPixel(x, y) != Color.WHITE) ink = true
        assertEquals(false, ink)
    }

    @Test fun render_returnsAPdfDocument() {
        val bytes = DeepDivePdfRenderer.render(DeepDivePdfPlan(10f, report, false), "Rates rise", texts)
        assertTrue(bytes.size > 100)
        assertEquals("%PDF-", String(bytes, 0, 5, Charsets.US_ASCII))
    }
}
```

- [ ] **Step 8: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.ai.DeepDiveSharerTest" --tests "com.mckimquyen.reader.infrastructure.ai.DeepDivePdfRendererTest" > /tmp/f01-t6b-red.log 2>&1; echo exit=$? > /tmp/f01-t6b-red.exit`
Expected: `exit=1`, `Unresolved reference: DeepDiveSharer` / `DeepDivePdfRenderer`.

- [ ] **Step 9: Code `DeepDiveSharer.kt`**

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.getFileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Writes the Deep Dive PDF to cacheDir/deepdive and builds a share intent through the existing FileProvider. */
class DeepDiveSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun write(pdf: ByteArray, title: String, nowMillis: Long = System.currentTimeMillis()): File =
        withContext(ioDispatcher) {
            val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.lastModified() < nowMillis - RETENTION_MS) it.delete() }
            File(dir, "deepdive_${slugOf(title)}_$nowMillis.pdf").apply { writeBytes(pdf) }
        }

    fun buildShareIntent(file: File, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.getFileProvider(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // The chooser is launched from an application context, so it needs NEW_TASK.
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Lowercase ASCII `[a-z0-9_]`, max [MAX_SLUG_LENGTH] chars; no path separators or dots can survive. */
    private fun slugOf(title: String): String =
        title.lowercase().replace(NON_SLUG_CHARS, "_").trim('_').take(MAX_SLUG_LENGTH).trim('_').ifEmpty { FALLBACK_SLUG }

    companion object {
        const val DIR_NAME = "deepdive"
        const val RETENTION_MS = 24L * 60 * 60 * 1000
        const val MIME_PDF = "application/pdf"
        private const val MAX_SLUG_LENGTH = 40
        private const val FALLBACK_SLUG = "report"
        private val NON_SLUG_CHARS = Regex("[^a-z0-9]+")
    }
}
```

- [ ] **Step 10: Code `DeepDivePdfRenderer.kt`**

```kotlin
package com.mckimquyen.reader.infrastructure.ai

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfPlan
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDiveTextMeasurer
import java.io.ByteArrayOutputStream

/** Draws the Deep Dive on one A4 page with the framework PdfDocument; system fonts, no dependency (FRONT-01). */
object DeepDivePdfRenderer {
    private const val ACCENT = 0xFF1F3A5F.toInt()
    private const val GREY = 0xFF555555.toInt()
    private const val RULE_WIDTH = 0.8f
    private const val BULLET = "• "
    private const val COLUMNS = 3

    /** Real measurer used by the view model so the plan and the drawing agree on heights. */
    val measurer = DeepDiveTextMeasurer { text, sizePt, widthPt, bold -> layoutOf(text, sizePt, widthPt, bold, Color.BLACK).height.toFloat() }

    private fun layoutOf(text: String, sizePt: Float, widthPt: Float, bold: Boolean, color: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePt
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPt.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }

    fun render(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts): ByteArray {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(DeepDivePdfLayout.PAGE_WIDTH, DeepDivePdfLayout.PAGE_HEIGHT, 1).create())
            drawPage(page.canvas, plan, title, texts)
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** Draws top to bottom and stops at the bottom margin: a second page is never started. */
    fun drawPage(canvas: Canvas, plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts) {
        val s = plan.sizePt
        val left = DeepDivePdfLayout.MARGIN.toFloat()
        val width = DeepDivePdfLayout.contentWidth
        val bottom = (DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN).toFloat()
        val gap = DeepDivePdfLayout.GAP
        var y = DeepDivePdfLayout.MARGIN.toFloat()

        // Returns false when the block would cross the bottom margin (nothing is drawn then).
        fun block(text: String, size: Float, x: Float, w: Float, bold: Boolean, color: Int = Color.BLACK): Boolean {
            val layout = layoutOf(text, size, w, bold, color)
            if (y + layout.height > bottom) return false
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
            return true
        }

        if (!block(title, s + DeepDivePdfLayout.TITLE_EXTRA_PT, left, width, true, ACCENT)) return
        y += gap
        if (!block("${texts.aiLabel} · ${texts.dateText}", s + DeepDivePdfLayout.SMALL_EXTRA_PT, left, width, false, GREY)) return
        y += gap
        val rule = Paint().apply { color = ACCENT; strokeWidth = RULE_WIDTH }
        canvas.drawLine(left, y, left + width, y, rule)
        y += gap

        fun heading(text: String): Boolean = block(text, s + DeepDivePdfLayout.HEADING_EXTRA_PT, left, width, true, ACCENT).also { if (it) y += gap }

        if (!heading(texts.headingContext)) return
        for (point in plan.report.context) {
            if (!block(BULLET + point, s, left + 0f, width, false)) return
            y += gap / 2
        }
        y += gap
        if (!heading(texts.headingCritical)) return
        for (p in plan.report.perspectives) {
            if (!block("$BULLET${p.school}: ${p.view}", s, left, width, false)) return
            y += gap / 2
        }
        y += gap
        if (!heading(texts.headingImpact)) return
        val col = width / COLUMNS
        fun row(a: String, b: String, c: String, bold: Boolean): Boolean {
            val cells = listOf(a, b, c).map { layoutOf(it, s, col - gap, bold, Color.BLACK) }
            val h = cells.maxOf { it.height }.toFloat()
            if (y + h > bottom) return false
            cells.forEachIndexed { i, l ->
                canvas.save(); canvas.translate(left + i * col, y); l.draw(canvas); canvas.restore()
            }
            y += h + gap / 2
            return true
        }
        if (!row(texts.colArea, texts.colShort, texts.colLong, true)) return
        for (r in plan.report.impacts) if (!row(r.area, r.shortTerm, r.longTerm, false)) return
        if (plan.truncated) {
            y += gap
            block(texts.truncatedNote, s + DeepDivePdfLayout.SMALL_EXTRA_PT, left, width, false, GREY)
        }
    }
}
```

Lưu ý quan trọng: `drawPage` dùng cùng `layoutOf` với `measurer`, nên kế hoạch và hình vẽ khớp nhau. Nếu `StaticLayout` bị Robolectric NATIVE từ chối (ném lỗi), ghi lỗi nguyên văn, không nới test: chuyển riêng hai test `drawPage_*` sang androidTest ở Task 10 và nói rõ trong báo cáo.

- [ ] **Step 11: Chạy lại, thấy xanh**

Cùng lệnh Step 8 (log `-green`). Expected `exit=0`, `DeepDiveSharerTest tests="5"`, `DeepDivePdfRendererTest tests="4"`, `failures="0" errors="0"`. Nếu `render_returnsAPdfDocument` đỏ vì shadow `PdfDocument` của Robolectric ghi nội dung khác `%PDF-`, đọc XML để biết nội dung thật; không đổi assertion cho xanh: chuyển test này sang androidTest (Task 10) và ghi giới hạn.

- [ ] **Step 12: Mutation check**

Sharer: `DIR_NAME` → `"dossiers"` đỏ; `MIME_PDF` → `"text/plain"` đỏ; bỏ `listFiles()?.forEach` → đỏ test retention; bỏ `.replace(NON_SLUG_CHARS...)` → đỏ test slug; bỏ `FLAG_GRANT_READ_URI_PERMISSION` → đỏ. Renderer: đổi `bottom` thành `PAGE_HEIGHT` (bỏ lề dưới) → `drawPage_leavesTheBottomMarginEmptyEvenForAHugeReport` đỏ; bỏ `if (y + layout.height > bottom) return false` → cùng test đỏ; đổi `left` thành `0f` trong `block(title...)` → `drawPage_leavesTheSideMarginsEmpty` đỏ; bỏ lời gọi `drawPage` trong `render` → `render_returnsAPdfDocument` hoặc kích thước đỏ. Khôi phục + `cmp` mỗi lần.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDivePdfRenderer.kt app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDiveSharer.kt app/src/test/java/com/mckimquyen/reader/infrastructure/ai/DeepDivePdfRendererTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/ai/DeepDiveSharerTest.kt
git commit -m "feat(front-01): render the Deep Dive as a one-page A4 PDF and share it

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 7: `DeepDiveViewModel` — consent, một request, cache, thử lại, chia sẻ PDF

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModelTest.kt`

**Interfaces:**
- Consumes: `AiDeepDiveService.generate(articleId, title, bodyText, languageTag)`, `DeepDiveCache`, `DeepDiveSharer`, `DeepDivePdfLayout.plan`, `DeepDivePdfRenderer.measurer/render`, `Context.deepDiveGeminiConsent`, `Context.setDeepDiveGeminiConsent`, `DeepDiveUiState`.
- Produces: `@HiltViewModel class DeepDiveViewModel @Inject constructor(@ApplicationContext context: Context, service: AiDeepDiveService, cache: DeepDiveCache, sharer: DeepDiveSharer, @IODispatcher ioDispatcher: CoroutineDispatcher)` với `val state: StateFlow<DeepDiveUiState>`, `fun open(articleId: String, title: String, bodyHtml: String)`, `fun onConsent(agreed: Boolean)`, `fun onConsentLater()`, `fun retry()`, `fun dismiss()`, `suspend fun prepareShare(texts: DeepDivePdfTexts, chooserTitle: String): Intent?`.

Thiết kế khớp spec: `open` kiểm cache trước (không cần consent để xem lại báo cáo đã có), rồi đọc consent trên IO; chỉ gọi service khi consent đúng bằng `true`. `bodyHtml` chỉ đi vào `AiDeepDiveService` (nơi cắt còn 6000 ký tự); ViewModel không đọc `Article`, không có link/feed/account.

- [ ] **Step 1: Viết test đỏ (dispatcher unconfined + chờ thời gian thực có hạn, theo `PodcastStudioViewModelTest`)**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import com.mckimquyen.reader.domain.sv.AiDeepDiveResult
import com.mckimquyen.reader.domain.sv.AiDeepDiveService
import com.mckimquyen.reader.infrastructure.ai.DeepDiveSharer
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.deepDiveGeminiConsent
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeepDiveViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val service: AiDeepDiveService = mockk()
    private val sharer: DeepDiveSharer = mockk()
    private lateinit var cache: DeepDiveCache
    private lateinit var viewModel: DeepDiveViewModel

    private val report = DeepDiveReport(
        "art-1", listOf("a", "b", "c"),
        listOf(DeepDivePerspective("s1", "v1"), DeepDivePerspective("s2", "v2")),
        listOf(DeepDiveImpact("x", "y", "z"), DeepDiveImpact("x2", "y2", "z2")),
    )
    private val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More", "2026-11-11")

    @Before fun setUp() {
        context.dataStore.putBlocking(DataStoreKeys.DeepDiveGeminiConsent, true)
        Dispatchers.setMain(mainDispatcher)
        cache = DeepDiveCache()
        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Ready(report)
        viewModel = DeepDiveViewModel(context, service, cache, sharer, Dispatchers.Unconfined)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun forgetConsent() = runBlocking { context.dataStore.edit { it.remove(DataStoreKeys.DeepDiveGeminiConsent.key) } }

    private fun awaitState(timeoutMs: Long = 3_000, condition: (DeepDiveUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition(viewModel.state.value)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out; state=${viewModel.state.value}")
            Thread.sleep(10)
        }
    }

    @Test fun startsIdle() { assertEquals(DeepDiveUiState.Idle, viewModel.state.value) }

    @Test fun neverAsked_showsTheConsentQuestionAndSendsNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun declined_showsGuidanceAndSendsNothing() {
        context.dataStore.putBlocking(DataStoreKeys.DeepDiveGeminiConsent, false)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.ConsentDeclined }
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun agreed_makesExactlyOneRequestAndShowsTheReport() {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        assertEquals(report, (viewModel.state.value as DeepDiveUiState.Ready).report)
        coVerify(exactly = 1) { service.generate("art-1", "Title", "<p>Body</p>", any()) }
        assertEquals(report, cache.get("art-1"))
    }

    @Test fun cachedReport_isShownWithoutAnyRequestEvenWithoutConsent() {
        forgetConsent()
        cache.put("art-1", report)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        assertEquals(DeepDiveUiState.Ready(report), viewModel.state.value)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun aNewViewModelReusesTheSharedCache() {
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        val second = DeepDiveViewModel(context, service, cache, sharer, Dispatchers.Unconfined)
        second.open("art-1", "Title", "<p>Body</p>")
        assertTrue(second.state.value is DeepDiveUiState.Ready)
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun openingTheSameArticleWhileLoading_doesNotStartASecondRequest() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.Loading }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        gate.complete(AiDeepDiveResult.Ready(report))
        awaitState { it is DeepDiveUiState.Ready }
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun failure_isShownAndNotCached_andRetryAsksAgainOnlyOnTap() {
        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED)
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Failed }
        assertEquals(DeepDiveUiState.Failed(AiDeepDiveFailure.REJECTED), viewModel.state.value)
        assertNull(cache.get("art-1"))
        coVerify(exactly = 1) { service.generate(any(), any(), any(), any()) }

        coEvery { service.generate(any(), any(), any(), any()) } returns AiDeepDiveResult.Ready(report)
        viewModel.retry()
        awaitState { it is DeepDiveUiState.Ready }
        coVerify(exactly = 2) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun retryDoesNothingUnlessTheStateIsFailed() {
        viewModel.retry()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun agreeingInTheSheet_storesTheConsentAndGenerates() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsent(true)
        awaitState { it is DeepDiveUiState.Ready }
        assertEquals(true, context.deepDiveGeminiConsent)
    }

    @Test fun decliningInTheSheet_storesFalseAndSendsNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsent(false)
        awaitState { it == DeepDiveUiState.ConsentDeclined }
        assertEquals(false, context.deepDiveGeminiConsent)
        coVerify(exactly = 0) { service.generate(any(), any(), any(), any()) }
    }

    @Test fun notNow_closesTheSheetAndStoresNothing() {
        forgetConsent()
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.AwaitingConsent }
        viewModel.onConsentLater()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        assertNull(context.deepDiveGeminiConsent)
    }

    @Test fun dismiss_cancelsTheRequestAndGoesIdle() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it == DeepDiveUiState.Loading }
        viewModel.dismiss()
        assertEquals(DeepDiveUiState.Idle, viewModel.state.value)
        gate.complete(AiDeepDiveResult.Ready(report))
        Thread.sleep(100)
        assertEquals("a late answer must not reopen the sheet", DeepDiveUiState.Idle, viewModel.state.value)
        assertNull("a cancelled request must not be cached", cache.get("art-1"))
    }

    @Test fun openingAnotherArticle_dropsTheOlderRequest() {
        val gate = CompletableDeferred<AiDeepDiveResult>()
        coEvery { service.generate("art-1", any(), any(), any()) } coAnswers { gate.await() }
        coEvery { service.generate("art-2", any(), any(), any()) } returns AiDeepDiveResult.Ready(report.copy(articleId = "art-2"))
        viewModel.open("art-1", "One", "")
        awaitState { it == DeepDiveUiState.Loading }
        viewModel.open("art-2", "Two", "")
        awaitState { it is DeepDiveUiState.Ready }
        gate.complete(AiDeepDiveResult.Ready(report))
        Thread.sleep(100)
        assertEquals("art-2", (viewModel.state.value as DeepDiveUiState.Ready).report.articleId)
        assertNull(cache.get("art-1"))
    }

    @Test fun prepareShare_isNullUntilAReportIsReady() = runBlocking {
        assertNull(viewModel.prepareShare(texts, "Share"))
    }

    @Test fun prepareShare_writesAPdfAndReturnsTheChooser() = runBlocking {
        val bytes = slot<ByteArray>()
        val file = File.createTempFile("dd", ".pdf")
        val chooser = Intent(Intent.ACTION_CHOOSER)
        coEvery { sharer.write(capture(bytes), "Title", any()) } returns file
        every { sharer.buildShareIntent(file, "Share") } returns chooser
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }

        assertEquals(chooser, viewModel.prepareShare(texts, "Share"))
        assertEquals("%PDF-", String(bytes.captured, 0, 5, Charsets.US_ASCII))
        file.delete()
    }

    @Test fun prepareShare_returnsNullWhenWritingFails() = runBlocking {
        coEvery { sharer.write(any(), any(), any()) } throws java.io.IOException("disk full")
        viewModel.open("art-1", "Title", "<p>Body</p>")
        awaitState { it is DeepDiveUiState.Ready }
        assertNull(viewModel.prepareShare(texts, "Share"))
    }

    @Test fun theViewModelNeverTouchesAnArticleOrALink() {
        val source = File("src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModel.kt").readText()
        listOf("Article", ".link", "feedId", "accountId", "ArticleWithFeed").forEach {
            assertTrue("$it must not appear in the view model", !source.contains(it))
        }
        assertNotNull(source)
    }
}
```

(`sharer.write` có tham số mặc định `nowMillis`: trong mockk phải khớp ba đối số, nên dùng `any()` cho đối số thứ ba như trên.)

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveViewModelTest" > /tmp/f01-t7-red.log 2>&1; echo exit=$? > /tmp/f01-t7-red.exit`
Expected: `exit=1`, `Unresolved reference: DeepDiveViewModel`.

- [ ] **Step 3: Code `DeepDiveViewModel.kt`**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.sv.AiDeepDiveResult
import com.mckimquyen.reader.domain.sv.AiDeepDiveService
import com.mckimquyen.reader.infrastructure.ai.DeepDivePdfRenderer
import com.mckimquyen.reader.infrastructure.ai.DeepDiveSharer
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.deepDiveGeminiConsent
import com.mckimquyen.reader.ui.ext.setDeepDiveGeminiConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the Deep Dive sheet (FRONT-01). The single place that decides whether Gemini is called: only when the stored
 * consent is exactly `true`, and never twice for an article whose report is cached. It is handed three strings (id,
 * title, body HTML) and never an article, a link, a feed or an account.
 */
@HiltViewModel
class DeepDiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: AiDeepDiveService,
    private val cache: DeepDiveCache,
    private val sharer: DeepDiveSharer,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<DeepDiveUiState>(DeepDiveUiState.Idle)
    val state: StateFlow<DeepDiveUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pending: Pending? = null

    private data class Pending(val articleId: String, val title: String, val bodyHtml: String)

    fun open(articleId: String, title: String, bodyHtml: String) {
        if (pending?.articleId == articleId && _state.value is DeepDiveUiState.Loading) return
        job?.cancel()
        val request = Pending(articleId, title, bodyHtml)
        pending = request
        cache.get(articleId)?.let {
            _state.value = DeepDiveUiState.Ready(it)
            return
        }
        // Loading first, so a report of the previous article is never shown for this one while consent is read.
        _state.value = DeepDiveUiState.Loading
        job = viewModelScope.launch {
            when (withContext(ioDispatcher) { context.deepDiveGeminiConsent }) {
                null -> _state.value = DeepDiveUiState.AwaitingConsent
                false -> _state.value = DeepDiveUiState.ConsentDeclined
                true -> generate(request)
            }
        }
    }

    /** The user answered the in-sheet question. Agreeing continues with the article that is waiting. */
    fun onConsent(agreed: Boolean) {
        val waiting = pending
        job?.cancel()
        job = viewModelScope.launch {
            withContext(ioDispatcher) { context.setDeepDiveGeminiConsent(agreed) }
            if (!agreed) {
                _state.value = DeepDiveUiState.ConsentDeclined
            } else if (waiting != null) {
                generate(waiting)
            }
        }
    }

    /** "Not now": nothing is stored and the sheet closes, so the question is asked again next time. */
    fun onConsentLater() = dismiss()

    /** Asks again after a failure. Only the user's tap gets here; nothing retries by itself. */
    fun retry() {
        val waiting = pending ?: return
        if (_state.value !is DeepDiveUiState.Failed) return
        job?.cancel()
        job = viewModelScope.launch { generate(waiting) }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        pending = null
        _state.value = DeepDiveUiState.Idle
    }

    private suspend fun generate(request: Pending) {
        _state.value = DeepDiveUiState.Loading
        val result = withContext(ioDispatcher) {
            service.generate(request.articleId, request.title, request.bodyHtml, Locale.getDefault().toLanguageTag())
        }
        // A newer article or dismiss cancels [job] first, so withContext above throws CancellationException
        // instead of returning an old answer here.
        _state.value = when (result) {
            is AiDeepDiveResult.Ready -> {
                cache.put(request.articleId, result.report)
                DeepDiveUiState.Ready(result.report)
            }
            is AiDeepDiveResult.Failed -> DeepDiveUiState.Failed(result.reason)
        }
    }

    /** Builds the one-page PDF for the report on screen and returns a chooser, or null (no report or I/O failure). */
    suspend fun prepareShare(texts: DeepDivePdfTexts, chooserTitle: String): Intent? {
        val ready = _state.value as? DeepDiveUiState.Ready ?: return null
        val title = pending?.title.orEmpty()
        return try {
            withContext(ioDispatcher) {
                val plan = DeepDivePdfLayout.plan(ready.report, title, texts, DeepDivePdfRenderer.measurer)
                val bytes = DeepDivePdfRenderer.render(plan, title, texts)
                sharer.buildShareIntent(sharer.write(bytes, title), chooserTitle)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the exception type: a message could carry the title.
            Log.w(TAG, "Deep Dive PDF failed: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DeepDiveViewModel"
    }
}
```

Lưu ý: test `theViewModelNeverTouchesAnArticleOrALink` cấm chuỗi `Article` trong nguồn; comment và tên đã tránh chữ đó, trừ `"never an article"` trong KDoc: **viết KDoc là "never a whole record"** nếu test đỏ vì chính comment (sửa comment, không nới test).

- [ ] **Step 4: Biên dịch lại sạch Hilt rồi chạy xanh**

Constructor `@HiltViewModel` mới: chạy `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks > /tmp/f01-t7-hilt.log 2>&1; echo exit=$? > /tmp/f01-t7-hilt.exit` (Expected `exit=0`), rồi cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `DeepDiveViewModelTest tests="17" failures="0" errors="0"`. Nếu `prepareShare_writesAPdfAndReturnsTheChooser` đỏ vì StaticLayout/PdfDocument dưới Robolectric: đọc XML, ghi nguyên văn, chuyển đúng test đó sang androidTest ở Task 10 và nói rõ trong báo cáo (không giả).

- [ ] **Step 5: Mutation check**

Bỏ nhánh `cache.get(...)` → `cachedReport_...`, `aNewViewModelReusesTheSharedCache` đỏ; `true ->` đổi thành `else ->` (gọi cả khi `null`) → `neverAsked_...` đỏ; `false ->` bỏ → `declined_...` đỏ; bỏ guard `pending?.articleId == articleId && Loading` → `openingTheSameArticleWhileLoading...` đỏ; `cache.put` cho cả Failed → `failure_isShownAndNotCached` đỏ; bỏ `if (_state.value !is Failed) return` → `retryDoesNothingUnlessTheStateIsFailed` đỏ; `onConsentLater` gọi `setDeepDiveGeminiConsent(false)` → `notNow_...` đỏ; bỏ `job?.cancel()` trong `dismiss` → `dismiss_cancelsTheRequestAndGoesIdle` đỏ; bỏ `job?.cancel()` trong `open` → `openingAnotherArticle_dropsTheOlderRequest` đỏ; truyền `ready.report` vào `buildShareIntent` sai file → `prepareShare_writes...` đỏ. Khôi phục + `cmp`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveViewModelTest.kt
git commit -m "feat(front-01): decide consent, request, cache and PDF sharing in one view model

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 8: Chuỗi `deep_dive_*` trong cả 38 locale và công tắc Quyền riêng tư

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `values-vi/strings.xml` và 36 tệp `values-*/strings.xml` còn lại (cùng danh sách `ls app/src/main/res | grep '^values-'` trừ `values-night`, `values-night-v31`, `values-v31` nếu chúng không có `strings.xml` — kiểm bằng `ls app/src/main/res/values-*/strings.xml`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveStringsTest.kt` (mới) và `StringResourceParityTest` (có sẵn)

**Interfaces:**
- Produces các khoá (đều dùng ở Task 9 và `PrivacySettingsPage`): `deep_dive_menu`, `deep_dive_title`, `deep_dive_consent_message`, `deep_dive_consent_agree`, `deep_dive_consent_later`, `deep_dive_declined_message`, `deep_dive_open_privacy`, `deep_dive_loading`, `deep_dive_failed`, `deep_dive_no_key`, `deep_dive_nothing_to_analyze`, `deep_dive_retry`, `deep_dive_label`, `deep_dive_section_context`, `deep_dive_section_critical`, `deep_dive_section_impact`, `deep_dive_col_area`, `deep_dive_col_short`, `deep_dive_col_long`, `deep_dive_export_pdf`, `deep_dive_chooser_title`, `deep_dive_export_error`, `deep_dive_pdf_truncated`, `deep_dive_close`.

Nội dung bắt buộc (EN, viết tay; `'` phải escape thành `\'`; không `%d`/`%s` ngay trước danh từ số nhiều; không dùng `%` ở chỗ khác):

| khoá | EN |
|---|---|
| `deep_dive_menu` | `🔬 AI Deep Dive` |
| `deep_dive_title` | `AI Deep Dive` |
| `deep_dive_consent_message` | `The article\'s title and up to 6,000 characters of its text are sent to Google Gemini to write this report. Links, the feed name and your reading history are never sent. Gemini does not search the web.` |
| `deep_dive_consent_agree` | `Agree` |
| `deep_dive_consent_later` | `Not now` |
| `deep_dive_declined_message` | `AI Deep Dive is off. Turn it on in Settings, Privacy, to write a report.` |
| `deep_dive_open_privacy` | `Open Privacy settings` |
| `deep_dive_loading` | `The AI is writing the report…` |
| `deep_dive_failed` | `No AI report yet` |
| `deep_dive_no_key` | `No Gemini key is available, so the AI could not write a report.` |
| `deep_dive_nothing_to_analyze` | `This article has no title or text to analyse.` |
| `deep_dive_retry` | `Try again` |
| `deep_dive_label` | `Written by AI from the title and an excerpt; no web search; it can be wrong` |
| `deep_dive_section_context` | `Context` |
| `deep_dive_section_critical` | `Opposing views` |
| `deep_dive_section_impact` | `Impact matrix` |
| `deep_dive_col_area` | `Area` |
| `deep_dive_col_short` | `Short term` |
| `deep_dive_col_long` | `Long term` |
| `deep_dive_export_pdf` | `Export PDF` |
| `deep_dive_chooser_title` | `Share Deep Dive PDF` |
| `deep_dive_export_error` | `Could not create the PDF` |
| `deep_dive_pdf_truncated` | `Trimmed to fit one page. See the full report in the app.` |
| `deep_dive_close` | `Close` |

VI (viết tay, đủ dấu): `🔬 AI Deep Dive`, `AI Deep Dive`, `Tiêu đề và tối đa 6.000 ký tự nội dung bài được gửi tới Google Gemini để soạn báo cáo này. Đường dẫn, tên nguồn và lịch sử đọc của bạn không bao giờ được gửi. Gemini không tìm kiếm trên web.`, `Đồng ý`, `Để sau`, `AI Deep Dive đang tắt. Hãy bật trong Cài đặt, Quyền riêng tư, để soạn báo cáo.`, `Mở cài đặt Quyền riêng tư`, `AI đang soạn báo cáo…`, `Chưa có báo cáo AI`, `Không có khoá Gemini nên AI không soạn được báo cáo.`, `Bài này không có tiêu đề hoặc nội dung để phân tích.`, `Thử lại`, `Do AI soạn từ tiêu đề và một đoạn trích; không tìm kiếm web; có thể sai`, `Bối cảnh`, `Góc nhìn đối lập`, `Ma trận tác động`, `Lĩnh vực`, `Ngắn hạn`, `Dài hạn`, `Xuất PDF`, `Chia sẻ PDF Deep Dive`, `Không tạo được PDF`, `Đã rút gọn cho vừa một trang. Xem đầy đủ trong ứng dụng.`, `Đóng`.

- [ ] **Step 1: Viết test đỏ `DeepDiveStringsTest`**

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The new keys must exist in every shipped values*/strings.xml, and the consent text must say the body is sent. */
class DeepDiveStringsTest {

    private val res = File("src/main/res")
    private val keys = listOf(
        "deep_dive_menu", "deep_dive_title", "deep_dive_consent_message", "deep_dive_consent_agree", "deep_dive_consent_later",
        "deep_dive_declined_message", "deep_dive_open_privacy", "deep_dive_loading", "deep_dive_failed", "deep_dive_no_key",
        "deep_dive_nothing_to_analyze", "deep_dive_retry", "deep_dive_label", "deep_dive_section_context",
        "deep_dive_section_critical", "deep_dive_section_impact", "deep_dive_col_area", "deep_dive_col_short",
        "deep_dive_col_long", "deep_dive_export_pdf", "deep_dive_chooser_title", "deep_dive_export_error",
        "deep_dive_pdf_truncated", "deep_dive_close",
    )

    private val localeFiles = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }

    @Test fun thereAre38LocaleFiles() {
        assertEquals(38, localeFiles.size)
    }

    @Test fun everyLocaleDefinesEveryKeyExactlyOnce() {
        for (file in localeFiles) {
            val text = file.readText()
            for (key in keys) {
                val count = Regex("""<string name="$key"[ >]""").findAll(text).count()
                assertEquals("${file.parentFile.name} must define $key once", 1, count)
            }
        }
    }

    @Test fun englishConsentSaysTheTextIsSent() {
        val en = File(res, "values/strings.xml").readText()
        val consent = Regex("""<string name="deep_dive_consent_message">(.*?)</string>""").find(en)!!.groupValues[1]
        assertTrue(consent.contains("6,000"))
        assertTrue(consent.contains("text"))
        assertFalse(consent.contains("%"))
    }

    @Test fun vietnameseConsentSaysTheTextIsSent() {
        val vi = File(res, "values-vi/strings.xml").readText()
        val consent = Regex("""<string name="deep_dive_consent_message">(.*?)</string>""").find(vi)!!.groupValues[1]
        assertTrue(consent.contains("6.000"))
        assertTrue(consent.contains("nội dung"))
    }

    @Test fun noPercentFormatAppearsInTheNewKeys() {
        for (file in localeFiles) {
            val text = file.readText()
            for (key in keys) {
                val body = Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
                assertFalse("${file.parentFile.name}/$key must not contain a format placeholder", body.contains("%"))
            }
        }
    }
}
```

Nếu `thereAre38LocaleFiles` ra số khác 38, dừng: đếm lại `ls app/src/main/res/values*/strings.xml | wc -l` và dùng đúng số mà `StringResourceParityTest` đang bảo vệ; không đổi test để xanh.

- [ ] **Step 2: Chạy, thấy đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveStringsTest" > /tmp/f01-t8-red.log 2>&1; echo exit=$? > /tmp/f01-t8-red.exit`
Expected: `exit=1`; XML cho thấy `everyLocaleDefinesEveryKeyExactlyOnce` đỏ với thông điệp `must define deep_dive_menu once` (0 ≠ 1).

- [ ] **Step 3: Thêm chuỗi bằng một script (không sửa tay 38 tệp)**

```bash
cat > /tmp/f01_add_strings.py <<'PY'
import glob, os, re, sys
EN = {
    "deep_dive_menu": "🔬 AI Deep Dive",
    "deep_dive_title": "AI Deep Dive",
    "deep_dive_consent_message": "The article's title and up to 6,000 characters of its text are sent to Google Gemini to write this report. Links, the feed name and your reading history are never sent. Gemini does not search the web.",
    "deep_dive_consent_agree": "Agree",
    "deep_dive_consent_later": "Not now",
    "deep_dive_declined_message": "AI Deep Dive is off. Turn it on in Settings, Privacy, to write a report.",
    "deep_dive_open_privacy": "Open Privacy settings",
    "deep_dive_loading": "The AI is writing the report…",
    "deep_dive_failed": "No AI report yet",
    "deep_dive_no_key": "No Gemini key is available, so the AI could not write a report.",
    "deep_dive_nothing_to_analyze": "This article has no title or text to analyse.",
    "deep_dive_retry": "Try again",
    "deep_dive_label": "Written by AI from the title and an excerpt; no web search; it can be wrong",
    "deep_dive_section_context": "Context",
    "deep_dive_section_critical": "Opposing views",
    "deep_dive_section_impact": "Impact matrix",
    "deep_dive_col_area": "Area",
    "deep_dive_col_short": "Short term",
    "deep_dive_col_long": "Long term",
    "deep_dive_export_pdf": "Export PDF",
    "deep_dive_chooser_title": "Share Deep Dive PDF",
    "deep_dive_export_error": "Could not create the PDF",
    "deep_dive_pdf_truncated": "Trimmed to fit one page. See the full report in the app.",
    "deep_dive_close": "Close",
}
VI = {
    "deep_dive_menu": "🔬 AI Deep Dive",
    "deep_dive_title": "AI Deep Dive",
    "deep_dive_consent_message": "Tiêu đề và tối đa 6.000 ký tự nội dung bài được gửi tới Google Gemini để soạn báo cáo này. Đường dẫn, tên nguồn và lịch sử đọc của bạn không bao giờ được gửi. Gemini không tìm kiếm trên web.",
    "deep_dive_consent_agree": "Đồng ý",
    "deep_dive_consent_later": "Để sau",
    "deep_dive_declined_message": "AI Deep Dive đang tắt. Hãy bật trong Cài đặt, Quyền riêng tư, để soạn báo cáo.",
    "deep_dive_open_privacy": "Mở cài đặt Quyền riêng tư",
    "deep_dive_loading": "AI đang soạn báo cáo…",
    "deep_dive_failed": "Chưa có báo cáo AI",
    "deep_dive_no_key": "Không có khoá Gemini nên AI không soạn được báo cáo.",
    "deep_dive_nothing_to_analyze": "Bài này không có tiêu đề hoặc nội dung để phân tích.",
    "deep_dive_retry": "Thử lại",
    "deep_dive_label": "Do AI soạn từ tiêu đề và một đoạn trích; không tìm kiếm web; có thể sai",
    "deep_dive_section_context": "Bối cảnh",
    "deep_dive_section_critical": "Góc nhìn đối lập",
    "deep_dive_section_impact": "Ma trận tác động",
    "deep_dive_col_area": "Lĩnh vực",
    "deep_dive_col_short": "Ngắn hạn",
    "deep_dive_col_long": "Dài hạn",
    "deep_dive_export_pdf": "Xuất PDF",
    "deep_dive_chooser_title": "Chia sẻ PDF Deep Dive",
    "deep_dive_export_error": "Không tạo được PDF",
    "deep_dive_pdf_truncated": "Đã rút gọn cho vừa một trang. Xem đầy đủ trong ứng dụng.",
    "deep_dive_close": "Đóng",
}
KEYS = list(EN.keys())
assert len(KEYS) == 24 and set(KEYS) == set(VI.keys()), "EN/VI phải đủ 24 khoá"
res = "app/src/main/res"
files = sorted(glob.glob(f"{res}/values*/strings.xml"))
assert len(files) == 38, len(files)
for f in files:
    src = open(f, encoding="utf-8").read()
    assert not any(f'name="{k}"' in src for k in KEYS), f"đã có khoá ở {f}"
    table = VI if f.endswith("values-vi/strings.xml") else EN
    # Android resources need an apostrophe escaped as \' (a bare one makes aapt fail); the dicts hold plain text.
    block = "".join(f'    <string name="{k}">{table[k].replace(chr(39), chr(92) + chr(39))}</string>\n' for k in KEYS)
    assert src.count("</resources>") == 1, f
    open(f, "w", encoding="utf-8").write(src.replace("</resources>", block + "</resources>"))
print("ok", len(files))
PY
```

Khi chạy: ghi script ra `/tmp/f01_add_strings.py` đúng như trên (dict chứa văn bản thường, script tự escape `'` thành `\'`; `…` là ký tự thật), rồi `python3 /tmp/f01_add_strings.py`. Script `assert` không ghi gì nếu một tệp đã có khoá. Chạy đúng một lần; nếu phải chạy lại, `git checkout -- app/src/main/res` trước.

- [ ] **Step 4: Chạy lại, thấy xanh, cùng `StringResourceParityTest`**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveStringsTest" --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" > /tmp/f01-t8-green.log 2>&1; echo exit=$? > /tmp/f01-t8-green.exit`
Expected: `exit=0`, `DeepDiveStringsTest tests="5"`, `StringResourceParityTest` xanh toàn bộ (6/6), `failures="0"`.

- [ ] **Step 5: Công tắc trong `PrivacySettingsPage.kt`**

Thêm sau `val quizGeminiConsent by viewModel.quizGeminiConsent.collectAsState()`:

```kotlin
    val deepDiveGeminiConsent by viewModel.deepDiveGeminiConsent.collectAsState()
```

và một `item { ... }` ngay sau item của quiz:

```kotlin
                item {
                    SettingItem(
                        title = stringResource(R.string.deep_dive_title),
                        desc = stringResource(R.string.deep_dive_consent_message),
                        onClick = { viewModel.setDeepDiveGeminiConsent(!deepDiveGeminiConsent) },
                        action = {
                            BaseSwitch(activated = deepDiveGeminiConsent) {
                                viewModel.setDeepDiveGeminiConsent(!deepDiveGeminiConsent)
                            }
                        },
                    )
                }
```

- [ ] **Step 6: Mutation check**

Xoá một khoá khỏi `values-ja/strings.xml` → `everyLocaleDefinesEveryKeyExactlyOnce` và `StringResourceParityTest` đỏ; thêm `%d` vào một chuỗi EN → `noPercentFormatAppearsInTheNewKeys` đỏ; bỏ chữ `text` khỏi consent EN → `englishConsentSaysTheTextIsSent` đỏ. Khôi phục bằng `git checkout -- <file>` rồi `cmp` với bản sao `/tmp`.

- [ ] **Step 7: Build và commit**

```bash
./gradlew :app:compileDevDebugKotlin > /tmp/f01-t8-build.log 2>&1; echo exit=$? > /tmp/f01-t8-build.exit
```

Expected `exit=0`. Rồi:

```bash
git add app/src/main/res app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveStringsTest.kt
git commit -m "feat(front-01): add the Deep Dive strings in all 38 locales and a privacy switch

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: `DeepDiveSheet`, mục menu TopBar và nối `ReadingPage`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveSheet.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt` (tham số `onDeepDive`, mục menu ngay sau mục `deep_read_title`, quanh dòng 197-209)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt` (khai báo ViewModel gần dòng 229, truyền `onDeepDive` vào `TopBar` gần dòng 484, đặt `DeepDiveSheet` ngay sau `PodcastStudioSheet` gần dòng 639)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveSheetTest.kt`, `DeepDiveMenuEntryTest.kt`, `DeepDiveWiringSourceTest.kt`

**Interfaces:**
- Consumes: `DeepDiveUiState` (Task 5), `DeepDiveViewModel` (Task 7), các khoá chuỗi Task 8, `LocalOpenPrivacySettings` (có sẵn trong `ui/page/rpg/QuizNavigation.kt`), `DialogEdgeToEdge` (có sẵn).
- Produces: `@Composable fun DeepDiveSheet(state, title, onDismiss, onConsent, onConsentLater, onRetry, onExportPdf)` (bọc `Dialog`) và `@Composable fun DeepDiveSheetContent(
    state: DeepDiveUiState,
    title: String,
    onDismiss: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onConsentLater: () -> Unit,
    onRetry: () -> Unit,
    onExportPdf: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp).testTag(DEEP_DIVE_SHEET_TAG)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.deep_dive_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.deep_dive_close))
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            DeepDiveUiState.Idle -> Unit
            DeepDiveUiState.AwaitingConsent -> ConsentPart(onConsent, onConsentLater)
            DeepDiveUiState.ConsentDeclined -> DeclinedPart()
            DeepDiveUiState.Loading -> LoadingPart()
            is DeepDiveUiState.Failed -> FailedPart(state.reason, onRetry)
            is DeepDiveUiState.Ready -> ReportPart(state.report, title, onExportPdf)
        }
    }
}

@Composable
private fun ConsentPart(onConsent: (Boolean) -> Unit, onLater: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_CONSENT_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.deep_dive_consent_message), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { onConsent(true) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_AGREE_TAG),
        ) { Text(stringResource(R.string.deep_dive_consent_agree)) }
        OutlinedButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_LATER_TAG),
        ) { Text(stringResource(R.string.deep_dive_consent_later)) }
    }
}

@Composable
private fun DeclinedPart() {
    val openPrivacy = LocalOpenPrivacySettings.current
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_DECLINED_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.deep_dive_declined_message), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = openPrivacy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_OPEN_PRIVACY_TAG),
        ) { Text(stringResource(R.string.deep_dive_open_privacy)) }
    }
}

@Composable
private fun LoadingPart() {
    Column(
        Modifier.fillMaxSize().testTag(DEEP_DIVE_LOADING_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.deep_dive_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FailedPart(reason: AiDeepDiveFailure, onRetry: () -> Unit) {
    val message = when (reason) {
        AiDeepDiveFailure.NO_KEY -> R.string.deep_dive_no_key
        AiDeepDiveFailure.NOTHING_TO_ANALYZE -> R.string.deep_dive_nothing_to_analyze
        AiDeepDiveFailure.NETWORK, AiDeepDiveFailure.REJECTED -> R.string.deep_dive_failed
    }
    Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_FAILED_TAG), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        if (reason != AiDeepDiveFailure.NOTHING_TO_ANALYZE) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_RETRY_TAG),
            ) { Text(stringResource(R.string.deep_dive_retry)) }
        }
    }
}

@Composable
private fun ReportPart(report: DeepDiveReport, title: String, onExportPdf: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(DEEP_DIVE_REPORT_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.deep_dive_label), style = MaterialTheme.typography.labelMedium)
        HorizontalDivider()
        Section(stringResource(R.string.deep_dive_section_context))
        report.context.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Section(stringResource(R.string.deep_dive_section_critical))
        report.perspectives.forEach {
            Text("• ${it.school}: ${it.view}", style = MaterialTheme.typography.bodyMedium)
        }
        Section(stringResource(R.string.deep_dive_section_impact))
        Column(Modifier.fillMaxWidth().testTag(DEEP_DIVE_MATRIX_TAG), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MatrixRow(
                stringResource(R.string.deep_dive_col_area),
                stringResource(R.string.deep_dive_col_short),
                stringResource(R.string.deep_dive_col_long),
                bold = true,
            )
            report.impacts.forEach { MatrixRow(it.area, it.shortTerm, it.longTerm, bold = false) }
        }
        Button(
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(DEEP_DIVE_EXPORT_TAG),
        ) { Text(stringResource(R.string.deep_dive_export_pdf)) }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MatrixRow(area: String, short: String, long: String, bold: Boolean) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(area, short, long).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = weight, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                    // Swallows taps so they do not reach the scrim, WITHOUT clickable (clickable would merge every
                    // descendant into one semantics node and hide the sheet's children from tests and TalkBack).
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) { content() }
        }
    }
}
```

- [ ] **Step 4: Sửa `TopBar.kt`**

Thêm tham số sau `onPodcastStudio: () -> Unit = {},`:

```kotlin
    onDeepDive: () -> Unit = {},
```

Thêm mục ngay sau khối `DropdownMenuItem` của `deep_read_title` (trước mục `podcast_studio_menu`):

```kotlin
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.deep_dive_menu)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Insights,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeepDive()
                                }
                            )
```

và import `androidx.compose.material.icons.rounded.Insights`. Nếu import không biên dịch (thiếu trong gói icon đã khai báo), đổi sang icon có sẵn đã được import trong file (ví dụ `Forum`) thay vì thêm dependency.

- [ ] **Step 5: Sửa `ReadingPage.kt`**

Gần khai báo `podcastStudioViewModel` (dòng ~229):

```kotlin
    val deepDiveViewModel: DeepDiveViewModel = hiltViewModel()
    val deepDiveState by deepDiveViewModel.state.collectAsState()
    val deepDiveScope = rememberCoroutineScope()
    val deepDiveChooserTitle = stringResource(R.string.deep_dive_chooser_title)
    val deepDiveExportError = stringResource(R.string.deep_dive_export_error)
    val deepDiveTexts = DeepDivePdfTexts(
        aiLabel = stringResource(R.string.deep_dive_label),
        headingContext = stringResource(R.string.deep_dive_section_context),
        headingCritical = stringResource(R.string.deep_dive_section_critical),
        headingImpact = stringResource(R.string.deep_dive_section_impact),
        colArea = stringResource(R.string.deep_dive_col_area),
        colShort = stringResource(R.string.deep_dive_col_short),
        colLong = stringResource(R.string.deep_dive_col_long),
        truncatedNote = stringResource(R.string.deep_dive_pdf_truncated),
        dateText = remember { java.text.DateFormat.getDateInstance().format(java.util.Date()) },
    )
```

(Nếu `rememberCoroutineScope` hoặc `quoteScope` đã có trong file thì dùng lại `quoteScope` thay vì khai báo thêm.)

Trong lời gọi `TopBar(...)` (gần dòng 484, cạnh `onPodcastStudio`):

```kotlin
                    onDeepDive = {
                        readingUiState.articleWithFeed?.article?.let {
                            deepDiveViewModel.open(it.id, it.title, readingUiState.content ?: it.fullContent ?: it.rawDescription)
                        }
                    },
```

Ngay sau `PodcastStudioSheet(...)`:

```kotlin
    DeepDiveSheet(
        state = deepDiveState,
        title = readingUiState.articleWithFeed?.article?.title.orEmpty(),
        onDismiss = deepDiveViewModel::dismiss,
        onConsent = deepDiveViewModel::onConsent,
        onConsentLater = deepDiveViewModel::onConsentLater,
        onRetry = deepDiveViewModel::retry,
        onExportPdf = {
            deepDiveScope.launch {
                val intent = deepDiveViewModel.prepareShare(deepDiveTexts, deepDiveChooserTitle)
                if (intent != null) context.startActivity(intent)
                else Toast.makeText(context, deepDiveExportError, Toast.LENGTH_SHORT).show()
            }
        },
    )
```

Thêm import `DeepDiveSheet`, `DeepDiveViewModel`, `DeepDivePdfTexts`. Thêm vào `LaunchedEffect(articleId)` hoặc chỗ đổi bài: khi `readingUiState.articleWithFeed?.article?.id` đổi thì `deepDiveViewModel.dismiss()` (một `LaunchedEffect(articleId) { deepDiveViewModel.dismiss() }` là đủ) để sheet không hiện báo cáo của bài trước. `Article` có đúng các thuộc tính `fullContent` và `rawDescription` (đã dùng trong test `PodcastStudioViewModelTest`); kiểm bằng biên dịch.

- [ ] **Step 6: Chạy lại, thấy xanh**

Cùng lệnh Step 2 (log `-green`). Expected `exit=0`, `DeepDiveSheetTest tests="10"`, `DeepDiveMenuEntryTest tests="3"`, `DeepDiveWiringSourceTest tests="4"`, `failures="0"`. Rồi `./gradlew assembleDevDebug > /tmp/f01-t9-build.log 2>&1; echo exit=$? > /tmp/f01-t9-build.exit` Expected `exit=0`.

- [ ] **Step 7: Mutation check**

Bỏ `if (state == Idle) return` → `idle_drawsNothing` đỏ (hoặc ném); `onConsent(true)` nút Agree thành `onConsent(false)` → đỏ; nút "Để sau" gọi `onConsent(false)` → `consent_showsTheQuestion...` đỏ (assert `true` còn nguyên); bỏ điều kiện `reason != NOTHING_TO_ANALYZE` → thêm test hoặc thấy `failed_nothingToAnalyzeHasItsOwnMessage` vẫn xanh: nếu mutation sống thì thêm `compose.onAllNodesWithTag(DEEP_DIVE_RETRY_TAG).assertCountEquals(0)` vào test đó; bỏ `onDeepDive()` trong menu → `tappingItReportsOneOpen...` đỏ; thêm `link` vào lời gọi `open(` ở ReadingPage → `readingPageOpensDeepDiveWithOnlyIdTitleAndBody` đỏ. Khôi phục + `cmp`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read app/src/test/java/com/mckimquyen/reader/ui/page/home/read/deepdive
git commit -m "feat(front-01): add the Deep Dive sheet to the reader menu

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 10: androidTest + kiểm máy thật SỚM (trước khi coi là xong)

Mục đích: bắt các lỗi chỉ lộ trên máy thật (như cache mất khi mở lại bài ở RPG-02) khi sheet, PDF và menu đã chạy, **trước** khi viết báo cáo. Chỉ làm sau khi owner duyệt plan.

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveSheetWidgetTest.kt`
- Create: `/tmp/f01-backup.py`, `/tmp/f01-restore.py` (sao chép từ `/tmp/rpg02-backup.py`, `/tmp/rpg02-restore.py`, thay `rpg02` bằng `f01`, bỏ dòng kiểm `front04.invalid`/`front04-` trong restore vì không tạo dữ liệu giả; `SERIALS = {'pixel': '2B051FDH3006MU'}`)

**Interfaces:**
- Consumes: `DeepDiveSheetContent`, `DeepDiveUiState`, các thẻ `DEEP_DIVE_*` và khoá `R.string.deep_dive_*`. Dùng đúng cách của `BrainQuizCardWidgetTest`: `ActivityScenario<ComponentActivity>` + `ComposeView` + duyệt cây semantics (không dùng `ComposeTestRule` vì thỉnh thoảng báo "No compose hierarchies found" trên máy thật).
- Produces: 7 test.

- [ ] **Step 1: Viết `DeepDiveSheetWidgetTest`**

Sao chép nguyên lớp `Screen` (các hàm `texts`, `has`, `click`, `await`, `textsOf`, `collect`, `firstClickable`, `findComposeView`) từ `app/src/androidTest/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizCardWidgetTest.kt` (đã chạy ổn trên Pixel), rồi:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.deepdive

// imports: giống BrainQuizCardWidgetTest + DeepDiveReport/DeepDivePerspective/DeepDiveImpact/AiDeepDiveFailure

@RunWith(AndroidJUnit4::class)
class DeepDiveSheetWidgetTest {

    private val report = DeepDiveReport(
        "a", listOf("Rates were low", "Inflation rose", "The bank pivoted"),
        listOf(DeepDivePerspective("Monetarist", "Overdue"), DeepDivePerspective("Keynesian", "Risky")),
        listOf(DeepDiveImpact("Housing", "Costs rise", "Prices cool"), DeepDiveImpact("Exports", "Currency firms", "Margins shrink")),
    )

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // Screen: copied unchanged from BrainQuizCardWidgetTest.

    private fun <T> withSheet(
        state: DeepDiveUiState,
        calls: MutableList<String>,
        awaitText: String,
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        MaterialTheme {
                            DeepDiveSheetContent(
                                state = state, title = "Rates rise",
                                onDismiss = { calls += "dismiss" }, onConsent = { calls += "consent:$it" },
                                onConsentLater = { calls += "later" }, onRetry = { calls += "retry" },
                                onExportPdf = { calls += "export" },
                            )
                        }
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the sheet never rendered", screen.await { screen.has(awaitText) })
        block(screen)
    }

    @Test fun consent_agreeAndNotNowReportTheRightAnswers() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.AwaitingConsent, calls, str(R.string.deep_dive_consent_message)) { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_consent_agree)))
            assertTrue(screen.click(str(R.string.deep_dive_consent_later)))
        }
        assertEquals(listOf("consent:true", "later"), calls)
    }

    @Test fun consent_messageSaysTheTextIsSent() {
        withSheet(DeepDiveUiState.AwaitingConsent, mutableListOf(), str(R.string.deep_dive_consent_message)) { screen ->
            assertTrue(screen.has("6,000") || screen.has("6.000"))
        }
    }

    @Test fun loading_showsTheProgressText() {
        withSheet(DeepDiveUiState.Loading, mutableListOf(), str(R.string.deep_dive_loading)) { screen ->
            assertTrue(screen.has(str(R.string.deep_dive_loading)))
        }
    }

    @Test fun failed_showsTheMessageAndRetryReportsOnce() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.Failed(AiDeepDiveFailure.REJECTED), calls, str(R.string.deep_dive_failed)) { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_retry)))
        }
        assertEquals(listOf("retry"), calls)
    }

    @Test fun ready_showsTheLabelTheSectionsAndTheMatrix() {
        withSheet(DeepDiveUiState.Ready(report), mutableListOf(), "Rates were low") { screen ->
            listOf(
                str(R.string.deep_dive_label), str(R.string.deep_dive_section_context), str(R.string.deep_dive_section_critical),
                str(R.string.deep_dive_section_impact), str(R.string.deep_dive_col_short), "Prices cool", "Monetarist",
            ).forEach { assertTrue("missing: $it", screen.has(it)) }
        }
    }

    @Test fun ready_exportReportsOnce() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.Ready(report), calls, "Rates were low") { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_export_pdf)))
        }
        assertEquals(listOf("export"), calls)
    }

    @Test fun pdfRenderer_producesAOnePagePdfOnARealDevice() {
        val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More", "2026-11-11")
        val plan = DeepDivePdfLayout.plan(report, "Rates rise", texts, DeepDivePdfRenderer.measurer)
        val bytes = DeepDivePdfRenderer.render(plan, "Rates rise", texts)
        assertEquals("%PDF-", String(bytes, 0, 5, Charsets.US_ASCII))
        val file = java.io.File.createTempFile("dd", ".pdf")
        file.writeBytes(bytes)
        val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        assertEquals("exactly one page", 1, renderer.pageCount)
        renderer.openPage(0).use { page ->
            assertEquals(DeepDivePdfLayout.PAGE_WIDTH, page.width)
            assertEquals(DeepDivePdfLayout.PAGE_HEIGHT, page.height)
        }
        renderer.close(); fd.close(); file.delete()
    }
}
```

(Nếu `Page.use` không có ở API đang biên dịch, gọi `val page = renderer.openPage(0)` rồi `page.close()` trong `finally`.)

- [ ] **Step 2: Biên dịch androidTest, chưa chạm thiết bị**

Run: `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f01-t10-build.log 2>&1; echo exit=$? > /tmp/f01-t10-build.exit`
Expected `exit=0`. Lỗi biên dịch ở đây là lỗi thật của test hoặc mã: sửa rồi biên dịch lại, không bỏ test.

- [ ] **Step 3: Cổng thiết bị chỉ đọc (Pixel `2B051FDH3006MU`)**

```bash
S=2B051FDH3006MU
adb devices
adb -s $S shell dumpsys activity activities | grep topResumedActivity
adb -s $S shell dumpsys activity | grep -ci "Active instrumentation"
adb -s $S shell dumpsys window | grep isKeyguardShowing | head -1
adb -s $S shell ps -A | grep -E "bemytester|skyjoy|mckimquyen" | awk '{print $NF}'
```

Dừng và hỏi owner nếu có instrumentation hoặc app lạ ở foreground (`loyalty.skyjoy.vn.dev` ở foreground đã từng xảy ra: hỏi, không tự chạm). `bemytester` và `loyalty.skyjoy.vn.stg` nền là bình thường. Màn khoá thì hỏi owner mở khoá. Nếu Pixel không dùng được, hỏi owner có dùng Tecno KJ7 `115333744A005844` không (cùng cổng, không tự đổi).

- [ ] **Step 4: Backup byte-exact**

`python3 /tmp/f01-backup.py` (kiểm: cả Room, `-shm`, `-wal`, `settings.preferences_pb`, ba `shared_prefs` được đọc, kích thước khớp, `integrity ok`). Không bắt đầu Step 5 nếu bản sao rỗng hoặc lệch kích thước.

- [ ] **Step 5: Chạy androidTest bằng `am instrument` (KHÔNG `connected*`)**

```bash
S=2B051FDH3006MU
APK="$(ls -t app/build/outputs/apk/dev/debug/*.apk | head -1)"; TAPK=app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
stat -f '%Sm %N' -t '%T' "$APK" "$TAPK"      # phải mới hơn lần build cuối
adb -s $S install -r -t "$APK" && adb -s $S install -r -t "$TAPK"
timeout 300 adb -s $S shell am instrument -w -r -e class com.mckimquyen.reader.ui.page.home.read.deepdive.DeepDiveSheetWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/f01-t10-run.txt 2>&1; echo exit=$?
python3 /tmp/front04-instr-sum.py /tmp/f01-t10-run.txt
```

Expected: `OK (7 tests)`. Nếu `pdfRenderer_producesAOnePagePdfOnARealDevice` đỏ vì `StaticLayout`/`PdfDocument` thật khác Robolectric, đó là phát hiện thật: sửa mã, quay về TDD.

- [ ] **Step 6: Smoke tay có kiểm soát trên Pixel (tối đa ~5 request Gemini thật)**

Mở một bài thật trong app (một bài có nội dung, không phải bài chỉ có tiêu đề). Với mỗi bước, chụp màn hình và đọc log `adb -s $S logcat -d | grep -E "deep_dive|AiRequestGateway|AiDeepDive"` (không có tiêu đề hay nội dung trong log):
1. Menu ⋮ có mục `🔬 AI Deep Dive` → chạm. Sheet hiện câu hỏi consent nêu 6.000 ký tự. Log: **0** dòng `deep_dive` trước khi trả lời.
2. "Để sau": sheet đóng; `settings.preferences_pb` không có khoá `deep_dive_gemini_consent` (so kích thước với bản sao: phải bằng nhau).
3. Mở lại, "Đồng ý": khoá được ghi (DataStore lớn hơn bản sao), sheet hiện "AI đang soạn…", rồi báo cáo có nhãn AI, ba khối, bảng ma trận. Log: đúng **1** request `deep_dive`.
4. Đóng sheet, mở lại **cùng bài**: báo cáo hiện ngay, log **không** thêm request (đây là ca cache singleton; ca đã gây lỗi ở RPG-02).
5. Chạm Xuất PDF: chooser mở; tệp `cacheDir/deepdive/deepdive_*.pdf` tồn tại (`run-as ... ls`), kéo về máy dev bằng `exec-out run-as ... cat`, kiểm `pdfinfo`/đọc `%PDF-` và đúng 1 trang, xem ảnh trang (render bằng `pdftoppm` nếu có) để xác nhận bố cục không tràn.
6. Máy bay + một bài khác: sheet báo lỗi mạng, nút Thử lại; tắt máy bay, chạm Thử lại: báo cáo hiện (1 request). Nếu key chung `RateLimited`: **dừng**, ghi nguyên văn và hỏi owner đặt key riêng, không gọi dồn.
7. Quyền riêng tư: tắt công tắc Deep Dive, mở một bài mới: sheet hiện hướng dẫn bật trong Quyền riêng tư và nút mở cài đặt; log 0 request.
8. Một bài tiếng Việt và một bài tiếng khác nếu có: báo cáo theo ngôn ngữ máy; ghi lại ngôn ngữ nào đã kiểm.

Quảng cáo toàn màn hình xuất hiện: chỉ nhấn Back, không chạm "Tiếp tục đến ứng dụng", kiểm lại foreground. Ghi mọi lỗi tìm thấy, kể cả cái không hiểu nguyên nhân. Mỗi lỗi thật quay về TDD (test đỏ trước) rồi commit riêng.

- [ ] **Step 7: Mutation trên máy (3 cái)**

Sửa, `assembleDevDebug assembleDevDebugAndroidTest`, cài, chạy lớp, khôi phục `cmp`:
- M1 nút Đồng ý trong `ConsentPart` gọi `onConsent(false)` → `consent_agreeAndNotNowReportTheRightAnswers` đỏ.
- M2 `ReportPart` bỏ dòng nhãn `deep_dive_label` → `ready_showsTheLabelTheSectionsAndTheMatrix` đỏ.
- M3 `DeepDivePdfRenderer.render` gọi `doc.startPage` hai lần (thêm một trang) → `pdfRenderer_producesAOnePagePdfOnARealDevice` đỏ ("exactly one page").

- [ ] **Step 8: Dọn, khôi phục, commit**

`adb -s $S shell pm uninstall com.mckimquyen.reader.test`; xoá `cacheDir/deepdive` (`run-as ... rm -rf cache/deepdive`); `python3 /tmp/f01-restore.py pixel` (Room, `-shm`, `-wal`, DataStore, ba `shared_prefs` phải **BYTE IDENTICAL**, nghĩa là khoá consent biến mất); `am force-stop com.mckimquyen.reader`. Cài lại build HEAD sạch nếu máy đang giữ bản có đột biến.

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/deepdive/DeepDiveSheetWidgetTest.kt
git commit -m "test(front-01): cover the Deep Dive sheet and the PDF on a real device

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: Full suite, lint, build

**Files:** không sửa mã (chỉ sửa khi một bước tìm ra lỗi thật; khi đó quay về TDD cho lỗi đó).

- [ ] **Step 1: Cây sạch và cổng trạng thái**

`git status --short` chỉ được còn `.idea/workspace.xml` (không commit). `git log --oneline -12` thấy các commit task 1-10.

- [ ] **Step 2: Toàn bộ test JVM, chạy lại sạch**

Run: `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f01-full.log 2>&1; echo exit=$? > /tmp/f01-full.exit`
Nếu treo (tiến trình "Gradle Test Executor" mồ côi): `pkill -f "Gradle Test Executor"`, báo cáo rõ, chạy lại. Đọc XML: cộng `tests=`, `failures=`, `errors=`, `skipped=` qua `app/build/test-results/testDevDebugUnitTest/*.xml` bằng script; kiểm không có tệp cũ (so mtime với thời điểm chạy). Expected: 0 lỗi, 0 skip; số test = nền trước (2.791) + số test mới của FRONT-01 (ghi con số thật).

- [ ] **Step 3: Lint**

Run: `./gradlew lintDevDebug --rerun-tasks > /tmp/f01-lint.log 2>&1; echo exit=$? > /tmp/f01-lint.exit`
Expected: 0 lỗi (nền 2.463 cảnh báo); `grep -c "deepdive\|DeepDive\|agents/" app/build/reports/lint-results-devDebug.txt` ra các dòng mới thì ghi lại và xử lý các dòng thuộc tệp mới.

- [ ] **Step 4: Build đủ**

Run: `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f01-assemble.log 2>&1; echo exit=$? > /tmp/f01-assemble.exit` Expected `exit=0`.

- [ ] **Step 5: Soát tài nguyên và riêng tư (theo CLAUDE.md R5)**

- `grep -rn '!!\|lateinit' app/src/main/java/com/mckimquyen/reader/domain/ai app/src/main/java/com/mckimquyen/reader/ui/page/home/read/deepdive app/src/main/java/com/mckimquyen/reader/infrastructure/ai/DeepDive*` phải ra rỗng (hoặc có comment lý do).
- `grep -rn 'Log\.' ` trong các tệp mới: không có chuỗi nào nối `title`, `excerpt`, `bodyHtml`, `report`.
- Mọi `Job` trong `DeepDiveViewModel` bị huỷ ở `dismiss`/`onCleared`.
- `git diff --stat main..HEAD -- app/src/main/AndroidManifest.xml app/schemas` rỗng; `grep -n "version = " app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt` vẫn là 20; `git diff main..HEAD -- app/build.gradle` rỗng (không dependency mới).

---

### Task 12: Báo cáo DONE và tài liệu

**Files:**
- Create: `doc/task/done/FRONT-01_ai-multi-agent-deep-dive_DONE.md`
- Move: `doc/task/todo/FRONT-01_ai-multi-agent-deep-dive.md` → không xoá; ghi một dòng đầu "Đã làm (bản thu hẹp), xem báo cáo DONE" và giữ trong `todo/` nếu còn mục chưa làm (chuỗi 3 request thật), hoặc `git mv` sang `done/` nếu owner xác nhận bản thu hẹp là đủ. **Hỏi owner bằng AskUserQuestion** trước khi di chuyển.
- Modify: `doc/feature.md` (mục 4 và danh sách ✅)

- [ ] **Step 1: Viết báo cáo, nói thẳng**

Nội dung bắt buộc (tiếng Việt, số liệu lấy từ log thật, không ước lượng):
- Phạm vi: một request Gemini ba vai (không phải chuỗi agent), tiêu đề + trích ≤ 6.000 ký tự thân bài gửi lên Google khi có consent riêng, không tra cứu web, kiểm cấu trúc không kiểm sự thật, PDF A4 một trang.
- Đã làm: danh sách tệp, quy tắc kiểm cấu trúc, cache singleton, layout PDF (thu cỡ chữ rồi cắt từ cuối), 24 chuỗi × 38 locale, công tắc Quyền riêng tư.
- Kiểm chứng JVM: số test, mutation đã chạy (liệt kê từng cái và test bắt nó), lint, build.
- Kiểm chứng máy (Pixel `2B051FDH3006MU`): androidTest `n/n`, các bước smoke 1-8 với kết quả thật; bước nào không chạy được (quota `RateLimited`, thiếu bài, v.v.) ghi "chưa kiểm" kèm lý do.
- Phát hiện khi làm (cả lỗi của chính tôi), và lỗi chưa giải thích nếu có.
- Giới hạn: bối cảnh "lịch sử" chỉ là kiến thức mô hình; AI có thể bịa; PDF dùng font hệ thống, CJK/RTL chưa kiểm; cache chỉ trong bộ nhớ; thân bài rời máy; key mặc định chung và key #1 hỏng (FIX-09).
- An toàn dữ liệu thiết bị: backup/restore byte-identical, `cmp`, test package đã gỡ, cache `deepdive` đã xoá.
- Danh sách commit.

- [ ] **Step 2: Cập nhật `doc/feature.md`**

Mục 4 thành ✅ với tóm tắt ngắn và liên kết báo cáo; thêm dòng nợ (ca máy thật chưa kiểm, FIX-09). Không đề cập PR.

- [ ] **Step 3: Commit tài liệu (kiểm exit code của `git`)**

```bash
git add doc/task/done/FRONT-01_ai-multi-agent-deep-dive_DONE.md doc/feature.md doc/task/todo/FRONT-01_ai-multi-agent-deep-dive.md
git commit -m "docs(front-01): report the AI Deep Dive with its verified and unverified parts

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

Không push, không PR, không `.idea`.
