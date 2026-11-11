# RPG-02 Quiz cuối bài do AI soạn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Card quiz cuối bài hiện câu hỏi 4 đáp án do Gemini soạn chỉ từ tiêu đề và `shortDescription` ≤ 250 ký tự, có consent riêng, nhãn "Do AI soạn", và trạng thái lỗi có nút Thử lại.

**Architecture:** Phần thuần (`AiQuizPolicy`: hằng số, sanitize, prompt, parse + kiểm tra + xáo trộn) tách khỏi I/O. `AiQuizService` gọi `AiRequestGateway`. `BrainRpgViewModel` giữ `QuizUiState` theo `articleId`, cache bộ nhớ LRU 20 bài, và là nơi duy nhất quyết định có gọi mạng hay không (chỉ khi consent `true`). `BrainQuizCard` nhận `QuizUiState` và vẽ năm trạng thái; XP và Rewarded Ad không đổi.

**Tech Stack:** Kotlin, Compose Material3, Hilt, DataStore, `AiRequestGateway` (OkHttp, key failover), org.json, JUnit4 + MockK + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-11-rpg-02-ai-micro-quiz-design.md` (commit `074aeafe`). Owner đã duyệt từng phần.
- **Dữ liệu gửi Gemini:** chỉ tiêu đề + mô tả ngắn ≤ **250** ký tự. Không thân bài, link, tên feed, account, tên người dùng. Mô tả lấy từ `article.shortDescription` (ReadingPage), **không** dựng từ `content`.
- Consent **riêng** cho quiz (khoá mới `quiz_gemini_consent`, `Boolean?`, null = chưa hỏi). Không đụng khoá `podcast_studio_gemini_consent`. "Để sau" không lưu gì.
- Gọi Gemini **một lần mỗi bài** khi card xuất hiện, cache bộ nhớ (~20 bài), không Room, không DataStore. Thử lại chỉ khi người dùng bấm.
- Không còn quiz tiêu đề dự phòng: xoá `generateQuiz`, `QuizPeer`, `queryQuizPeers`, `BrainRpgRepository.quizPeers` và test của chúng; giữ `detectCategory` và các hằng `CATEGORY_*` (khoá Room).
- Không đổi XP x3 (150), Rewarded Ad thử lại/nhân đôi, Room (giữ **v20**), không migration, không dependency mới, manifest không đổi.
- Không ghi log tiêu đề, mô tả hay câu hỏi.
- Chuỗi mới đủ **38 locale** (EN và VI viết riêng, 36 còn lại English); không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` phải xanh.
- Code: không `!!`/`late` thiếu lý do, không magic number (hằng có tên), soát mọi coroutine/job (huỷ khi ViewModel clear).
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML), code tối thiểu, xanh, mutation cho guard quan trọng (khôi phục bằng `cmp`), commit riêng từng task. Không chain `git commit` sau build nếu chưa kiểm exit code. Ghi output Gradle vào file, đọc XML. Dùng cờ `-r` và dấu nháy cho `grep`; không dùng `echo ====`.
- Bài học FRONT-04: (1) Robolectric không thực thi giới hạn CursorWindow, nên không có test nào ở đây dựa vào kích thước dữ liệu để chứng minh "không đọc cột"; (2) sau khi đổi constructor Hilt phải biên dịch lại sạch, `NoClassDefFoundError: RApp_GeneratedInjector` là lỗi môi trường chứ không phải đỏ về hành vi; (3) trước khi ghi lệnh xoá, `grep -r` mọi nơi dùng; (4) `Edit`/`str_replace` kiểm `count == 1` trước khi thay.
- Thiết bị: chỉ Pixel `2B051FDH3006MU` và Tecno KJ7 `115333744A005844`. Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation, `topResumedActivity`), `ps` tiến trình lạ; dừng và hỏi nếu có. Chỉ `force-stop com.mckimquyen.reader`. `run-as com.mckimquyen.reader` (không `--user`). Backup/restore **byte-exact** Room (+`-shm`/`-wal`), `settings.preferences_pb` và **mọi `shared_prefs/*.xml` mà smoke có thể đổi** (`commute_cast_prefs.xml`, `podcast_positions.xml`, `pairing_store.xml`). Không xoá DataStore. **Không** `connected*`: dùng `adb -s <serial> shell am instrument` có `timeout`. Quảng cáo toàn màn hình chỉ đóng bằng Back, không bấm "Tiếp tục đến ứng dụng". Màn khoá thì hỏi owner. Mọi thao tác thiết bị trước khi plan được duyệt chỉ đọc.
- Báo cáo DONE riêng `doc/task/done/RPG-02_ai-micro-quiz-engine_DONE.md`, cập nhật trạng thái task gốc và `doc/feature.md`.

---

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `ui/ext/ExtDataStore.kt` (sửa) | `DataStoreKeys.QuizGeminiConsent`, `Context.quizGeminiConsent`, `setQuizGeminiConsent` | 1 |
| `ui/page/setting/privacy/PrivacySettingsViewModel.kt`, `PrivacySettingsPage.kt` (sửa) | Công tắc consent quiz | 1 |
| `domain/model/rpg/AiQuizPolicy.kt` (mới) | Hằng số, `sanitizePreview`, `buildPrompt`, `parseAndValidate` (thuần) | 2, 3 |
| `domain/sv/AiQuizService.kt` (mới) | Gọi `AiRequestGateway`, trả `AiQuizResult` | 4 |
| `ui/page/rpg/QuizUiState.kt` (mới) | `QuizUiState`, `QuizCache` (LRU thuần) | 5 |
| `ui/page/rpg/BrainRpgViewModel.kt` (sửa) | `quizState`, `onQuizCardShown`, `onQuizConsent`, `retryQuiz`; bỏ `buildQuiz` | 5, 6 |
| `ui/component/rpg/BrainQuizCard.kt`, `BrainQuizPrompt.kt` (mới) | Năm trạng thái card, nhãn nguồn | 7 |
| `ui/page/home/read/Content.kt`, `ReadingPage.kt` (sửa) | Truyền `shortDescription`, đổi cách dựng card | 8 |
| `res/values*/strings.xml` ×38 | Chuỗi mới | 9 |
| `domain/sv/QuizGeneratorService.kt`, `QuizPeer.kt`, `ArticleDao.kt`, `BrainRpgRepository.kt` + test (xoá/sửa) | Gỡ quiz RPG-05 | 10 |
| `androidTest/.../BrainQuizCardWidgetTest.kt` (sửa) | Trạng thái card thật | 11 |
| doc, thiết bị | Full suite, lint, build, smoke thật, báo cáo | 12, 13 |

## Sự thật từ mã (đã đọc, không suy đoán)

- `BrainRpgViewModel(repository, quizGeneratorService, wrappedSharer, @DefaultDispatcher defaultDispatcher, @IODispatcher ioDispatcher)`; test dựng bằng 5 tham số vị trí ở `BrainRpgViewModelTest.kt` dòng 57 và 72, và `BrainRpgViewModelWrappedTest.kt` dòng 77. `buildQuiz(articleId, title, content, author)` ở dòng 50 gọi `repository.quizPeers` rồi `quizGeneratorService.generateQuiz`. `Content.kt` dùng `brainRpgViewModel?.quizGeneratorService?.detectCategory(title, content)` (dòng 116).
- `Content.kt`: `quiz` là `mutableStateOf<QuizQuestion?>` dựng trong `LaunchedEffect` (dòng 105–110); card ở dòng 247–265 gọi `submitQuizAnswer(articleId, category, isCorrect)`, `doubleQuizReward(act, articleId, category, 150L, onDone)`, `retryQuiz(act, onDone)`. `Content` **không có** tham số mô tả; `ReadingPage.kt` dòng 530–540 gọi `Content(content = target, feedName, title, author, link, publishedDate, isLoading, listState, articleId, brainRpgViewModel = hiltViewModel(), ...)`; `shortDescription` có ở `readingUiState.articleWithFeed.article.shortDescription` (dòng 661 đã dùng).
- `BrainQuizCard(quiz: QuizQuestion, doubleAlreadyClaimed, onAnswerSubmitted, onDoubleXpRequested, onRetryQuizRequested, modifier)`; `QuizQuestion(articleId, question, options, correctAnswerIndex, explanation, category)`. `BrainQuizCardWidgetTest` dựng card trong `ComposeView` qua `ActivityScenario<ComponentActivity>` (không ComposeTestRule), 4 test, `withCard(doubleAlreadyClaimed, onAnswer, block)`.
- `AiRequestGateway.execute(useCase: String, requestBody: String, keys: List<String>): String` (ném `GeminiSummaryService.SummaryException.MissingApiKey` khi không có key); `GeminiSummaryService.resolveApiKeys(): List<String>`. Mẫu: `PodcastStudioScriptService` (`USE_CASE`, `MAX_SNIPPET_CHARS = 250`, `responseMimeType = application/json`, `JSONObject`/`JSONArray`). Test mẫu: `PodcastStudioScriptServiceTest` (mock `AiRequestGateway`, `slot` để bắt `requestBody`).
- Consent mẫu: `DataStoreKeys.PodcastStudioGeminiConsent` (`booleanPreferencesKey("podcast_studio_gemini_consent")`), `Context.podcastStudioGeminiConsent: Boolean?`, `setPodcastStudioGeminiConsent`; `PrivacySettingsViewModel(context)` giữ `podcastStudioGeminiConsent` là `false` khi chưa hỏi hoặc từ chối, `setPodcastStudioGeminiConsent(agreed)` ghi qua `updateData`; trang có `SettingItem(title, desc, onClick, action = { BaseSwitch(...) })`. Test mẫu: `PrivacySettingsViewModelTest` (dùng `awaitReal { ... }`), `PodcastStudioViewModelTest` (`context.dataStore.putBlocking(...)`, `awaitState`, `awaitCondition`).
- Điều hướng: `RouteName.PRIVACY_SETTINGS = "privacy_settings"`; mẫu CompositionLocal: `LocalOpenRadarDiff = compositionLocalOf<(String) -> Unit> { {} }` (`ui/page/radar/RadarFlags.kt`), cung cấp trong `HomeEntry.kt` dòng ~244 qua `CompositionLocalProvider(... provides { ... navController.navigate(...) })`.
- Chuỗi hiện có: `brain_rpg_correct`, `brain_rpg_wrong`, `brain_rpg_retry_quiz` (neo `brain_rpg_retry_quiz` có ở cả 38 file; `values/strings.xml` dòng 568).
- `BrainRpgRepository.QUIZ_PEER_LIMIT = 60` (dòng 308) và `quizPeers` (dòng 56–57); `ArticleDao.queryQuizPeers` dòng ~116; `QuizPeer.kt`; test dính: `QuizGeneratorServiceTest` (các test `generateQuiz_*` và `detectCategory_*`), `ArticleDaoQuizPeersTest`, hai test `buildQuiz_*` trong `BrainRpgViewModelTest`.

## Lệnh dùng lại

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
# chạy lớp test, ghi log ra file, rồi đọc XML (không đọc stdout Gradle)
./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/rpg02-<ten>.log 2>&1; echo exit=$? > /tmp/rpg02-<ten>.exit
python3 /tmp/rpg02-xml.py <FQCN>...     # in tests/failures/errors và tên test đỏ ra /tmp/rpg02-xml.out
# mutation một file: /tmp/rpg02-mut.sh <file> <label> <old> <new> <fqcn>...
```

---

### Task 1: Consent riêng cho quiz (DataStore + công tắc Quyền riêng tư)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` (sau `setPodcastStudioGeminiConsent`, và cuối `DataStoreKeys` sau `PodcastStudioGeminiConsent`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt` (sau item Podcast Studio, dòng ~146)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt` (thêm 2 test), `app/src/test/java/com/mckimquyen/reader/ui/ext/QuizConsentTest.kt` (mới)
- Strings: dùng chuỗi ở Task 9 (`quiz_ai_title`, `quiz_ai_consent_message`); Task này tạm thời tham chiếu chúng, nên **thêm hai khoá vào `values/strings.xml` và `values-vi/strings.xml` ngay ở Step 3** để biên dịch được, 36 file còn lại ở Task 9.

**Interfaces:**
- Produces: `DataStoreKeys.QuizGeminiConsent` (`booleanPreferencesKey("quiz_gemini_consent")`); `val Context.quizGeminiConsent: Boolean?`; `suspend fun Context.setQuizGeminiConsent(agreed: Boolean)`; `PrivacySettingsViewModel.quizGeminiConsent: StateFlow<Boolean>` (true chỉ khi đã đồng ý) và `setQuizGeminiConsent(agreed: Boolean)`.

- [ ] **Step 1: Viết test đỏ**

`QuizConsentTest.kt`:

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
class QuizConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking { context.dataStore.edit { it.clear() } }

    @Test
    fun neverAsked_isNull_notFalse() {
        assertEquals(null, context.quizGeminiConsent)
    }

    @Test
    fun agreedAndDeclined_roundTrip() = runBlocking {
        context.setQuizGeminiConsent(true)
        assertEquals(true, context.quizGeminiConsent)

        context.setQuizGeminiConsent(false)
        assertEquals(false, context.quizGeminiConsent)
    }

    @Test
    fun quizConsentIsIndependentOfThePodcastStudioConsent() = runBlocking {
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("agreeing for Podcast Studio must not agree for the quiz", null, context.quizGeminiConsent)

        context.setQuizGeminiConsent(false)
        assertEquals("declining the quiz must not touch Podcast Studio", true, context.podcastStudioGeminiConsent)
    }

    @Test
    fun keyNameIsDistinctFromThePodcastStudioKey() {
        assertNotEquals(DataStoreKeys.PodcastStudioGeminiConsent.key.name, DataStoreKeys.QuizGeminiConsent.key.name)
        assertEquals("quiz_gemini_consent", DataStoreKeys.QuizGeminiConsent.key.name)
    }
}
```

`PrivacySettingsViewModelTest.kt` — thêm import `com.mckimquyen.reader.ui.ext.quizGeminiConsent` và hai test cuối lớp:

```kotlin
    @Test
    fun quizGeminiConsent_isOffUntilTheUserAgreesAndPersistsBothWays() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()
        assertFalse(viewModel.quizGeminiConsent.value)
        assertEquals(null, context.quizGeminiConsent)

        viewModel.setQuizGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.quizGeminiConsent.first { it } })
        assertEquals(true, context.quizGeminiConsent)

        viewModel.setQuizGeminiConsent(false)
        advanceUntilIdle()
        assertFalse(awaitReal { viewModel.quizGeminiConsent.first { !it } })
        assertEquals(false, context.quizGeminiConsent)
    }

    @Test
    fun quizAndPodcastStudioSwitchesDoNotAffectEachOther() = runTest(dispatcher) {
        context.dataStore.edit { it.clear() }
        val viewModel = PrivacySettingsViewModel(context)
        advanceUntilIdle()

        viewModel.setPodcastStudioGeminiConsent(true)
        advanceUntilIdle()
        assertTrue(awaitReal { viewModel.podcastStudioGeminiConsent.first { it } })
        assertFalse("the quiz switch must stay off", viewModel.quizGeminiConsent.value)
        assertEquals(null, context.quizGeminiConsent)
    }
```

- [ ] **Step 2: Chạy, đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.ext.QuizConsentTest" --tests "com.mckimquyen.reader.ui.page.setting.privacy.PrivacySettingsViewModelTest" > /tmp/rpg02-t1-red.log 2>&1; echo exit=$? > /tmp/rpg02-t1-red.exit`
Expected: `exit=1`, lỗi biên dịch `Unresolved reference: quizGeminiConsent` / `setQuizGeminiConsent` / `QuizGeminiConsent`.

- [ ] **Step 3: Cài đặt tối thiểu**

`ExtDataStore.kt` — thêm sau hàm `setPodcastStudioGeminiConsent`:

```kotlin
/** RPG-02: null until the user answers the quiz card's Gemini question; then true (AI quiz) or false (no AI quiz). */
val Context.quizGeminiConsent: Boolean?
    get() = this.dataStore.get(DataStoreKeys.QuizGeminiConsent)

suspend fun Context.setQuizGeminiConsent(agreed: Boolean) {
    this.dataStore.put(DataStoreKeys.QuizGeminiConsent, agreed)
}
```

và trong `DataStoreKeys` sau `PodcastStudioGeminiConsent`:

```kotlin
    /** RPG-02: answer to "send title and short preview to Gemini to write a quiz?". Absent means not asked yet. */
    object QuizGeminiConsent : DataStoreKeys<Boolean>() {
        override val key: Preferences.Key<Boolean>
            get() = booleanPreferencesKey("quiz_gemini_consent")
    }
```

`PrivacySettingsViewModel.kt` — thêm cạnh `_podcastStudioGeminiConsent`:

```kotlin
    // RPG-02: true only when the user explicitly agreed; "never asked" and "declined" both read as off.
    private val _quizGeminiConsent = MutableStateFlow(false)
    val quizGeminiConsent = _quizGeminiConsent.asStateFlow()
```
trong khối `collect`: `_quizGeminiConsent.value = preferences[DataStoreKeys.QuizGeminiConsent.key] == true`; và hàm (cạnh `setPodcastStudioGeminiConsent`):

```kotlin
    fun setQuizGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply { this[DataStoreKeys.QuizGeminiConsent.key] = agreed }
            }
        }
    }
```

`PrivacySettingsPage.kt` — thêm `val quizGeminiConsent by viewModel.quizGeminiConsent.collectAsState()` cạnh dòng 44, và sau item Podcast Studio:

```kotlin
                item {
                    SettingItem(
                        title = stringResource(R.string.quiz_ai_title),
                        desc = stringResource(R.string.quiz_ai_consent_message),
                        onClick = { viewModel.setQuizGeminiConsent(!quizGeminiConsent) },
                        action = {
                            BaseSwitch(activated = quizGeminiConsent) {
                                viewModel.setQuizGeminiConsent(!quizGeminiConsent)
                            }
                        },
                    )
                }
```

Thêm hai khoá vào `values/strings.xml` và `values-vi/strings.xml` ngay sau `brain_rpg_retry_quiz`:

```xml
    <string name="quiz_ai_title">AI quiz questions</string>
    <string name="quiz_ai_consent_message">Only this article\'s title and a short preview (under 250 characters) are sent to Google Gemini to write a quiz question. The article text, links and your reading history are never sent.</string>
```
```xml
    <string name="quiz_ai_title">Câu hỏi trắc nghiệm do AI soạn</string>
    <string name="quiz_ai_consent_message">Chỉ tiêu đề và phần xem trước ngắn (dưới 250 ký tự) của bài được gửi tới Google Gemini để soạn một câu hỏi trắc nghiệm. Nội dung bài, đường dẫn và lịch sử đọc của bạn không bao giờ được gửi.</string>
```
(Lúc này `StringResourceParityTest` sẽ đỏ cho tới Task 9; đó là chủ ý, không chạy nó ở Task 1.)

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2. Expected `exit=0`; `QuizConsentTest tests="4"`, `PrivacySettingsViewModelTest` +2, `failures="0" errors="0"`. Nếu gặp `NoClassDefFoundError: RApp_GeneratedInjector`: chạy `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks` rồi chạy lại; đó là lỗi môi trường.

- [ ] **Step 5: Mutation**

- M1 đổi `"quiz_gemini_consent"` thành `"podcast_studio_gemini_consent"`: `quizConsentIsIndependent...` và `keyNameIsDistinct...` đỏ.
- M2 trong `quizGeminiConsent` getter dùng `DataStoreKeys.PodcastStudioGeminiConsent`: `quizConsentIsIndependent...` đỏ.
- M3 `_quizGeminiConsent.value = preferences[...] != false` (null thành true): `quizGeminiConsent_isOffUntilTheUserAgrees...` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/test/java/com/mckimquyen/reader/ui/ext/QuizConsentTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModelTest.kt
git commit -m "feat(rpg-02): add a quiz-specific Gemini consent and its privacy switch

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 2: `AiQuizPolicy` phần 1 — hằng số, sanitize, prompt (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicyPromptTest.kt`

**Interfaces:**
- Produces: `object AiQuizPolicy { const val MAX_PREVIEW_CHARS = 250; const val USE_CASE = "quiz_micro"; const val OPTION_COUNT = 4; fun sanitizePreview(raw: String): String; fun hasContentToAsk(title: String, preview: String): Boolean; fun buildPrompt(title: String, preview: String, languageTag: String): String; fun buildRequestBody(prompt: String): String }`.
- Không phụ thuộc Android (dùng `org.json`, chạy được trên JVM qua Robolectric hoặc `org.json` của test classpath; xem ghi chú Step 4).

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

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
class AiQuizPolicyPromptTest {

    @Test
    fun previewIsCutToAtMost250CharactersAndTrimmed() {
        assertEquals(250, AiQuizPolicy.sanitizePreview("x".repeat(1_000)).length)
        assertEquals("short", AiQuizPolicy.sanitizePreview("  short  "))
        assertEquals("", AiQuizPolicy.sanitizePreview("   "))
    }

    @Test
    fun previewBoundaryIsExactly250() {
        assertEquals(250, AiQuizPolicy.sanitizePreview("y".repeat(250)).length)
        assertEquals(250, AiQuizPolicy.sanitizePreview("y".repeat(251)).length)
        assertEquals(249, AiQuizPolicy.sanitizePreview("y".repeat(249)).length)
    }

    @Test
    fun htmlTagsInThePreviewAreStrippedBeforeTheyLeaveTheDevice() {
        val cleaned = AiQuizPolicy.sanitizePreview("<p>Hello <b>world</b></p>")

        assertFalse(cleaned.contains("<"))
        assertEquals("Hello world", cleaned)
    }

    @Test
    fun nothingToAskWhenBothTitleAndPreviewAreBlank() {
        assertFalse(AiQuizPolicy.hasContentToAsk("", ""))
        assertFalse(AiQuizPolicy.hasContentToAsk("   ", "  \n "))
        assertTrue(AiQuizPolicy.hasContentToAsk("Title", ""))
        assertTrue(AiQuizPolicy.hasContentToAsk("", "Preview"))
    }

    @Test
    fun promptCarriesOnlyTheTitleAndThePreview() {
        val prompt = AiQuizPolicy.buildPrompt("My Title", "My preview text", "vi-VN")

        assertTrue(prompt.contains("Title: My Title"))
        assertTrue(prompt.contains("Preview: My preview text"))
        assertTrue(prompt.contains("vi-VN"))
        // Nothing else from the article may be reachable from the prompt builder: it only receives these three strings.
        assertFalse(prompt.contains("http"))
    }

    @Test
    fun promptAsksForStrictJsonWithFourOptionsAndForbidsInventing() {
        val prompt = AiQuizPolicy.buildPrompt("T", "P", "en-US")

        assertTrue(prompt.contains("STRICT JSON"))
        assertTrue(prompt.contains("question"))
        assertTrue(prompt.contains("options"))
        assertTrue(prompt.contains("correctIndex"))
        assertTrue(prompt.contains("explanation"))
        assertTrue("must say exactly four options", prompt.contains("4") || prompt.contains("four"))
        assertTrue(prompt.contains("do not invent", ignoreCase = true))
    }

    @Test
    fun requestBodyIsJsonModeAndHoldsOnlyThePrompt() {
        val prompt = AiQuizPolicy.buildPrompt("T", "P", "en-US")
        val body = JSONObject(AiQuizPolicy.buildRequestBody(prompt))

        assertEquals(setOf("contents", "generationConfig"), body.keys().asSequence().toSet())
        assertEquals(
            prompt,
            body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"),
        )
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
    }

    @Test
    fun useCaseNameIsStableAndDistinctFromOtherFeatures() {
        assertEquals("quiz_micro", AiQuizPolicy.USE_CASE)
    }
}
```

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.rpg.AiQuizPolicyPromptTest" > /tmp/rpg02-t2-red.log 2>&1; echo exit=$? > /tmp/rpg02-t2-red.exit`
Expected: `exit=1`, `Unresolved reference: AiQuizPolicy` (đúng lý do: kiểu chưa tồn tại).

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the AI quiz: what may leave the device, how the request looks, and (Task 3) which answers are accepted.
 * Privacy boundary: the only article data reachable from here is the title and a preview of at most [MAX_PREVIEW_CHARS].
 */
object AiQuizPolicy {
    const val USE_CASE = "quiz_micro"
    const val MAX_PREVIEW_CHARS = 250
    const val OPTION_COUNT = 4
    private const val TEMPERATURE = 0.4
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizePreview(raw: String): String =
        raw.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim().take(MAX_PREVIEW_CHARS).trim()

    fun hasContentToAsk(title: String, preview: String): Boolean = title.isNotBlank() || preview.isNotBlank()

    fun buildPrompt(title: String, preview: String, languageTag: String): String = buildString {
        append("You write ONE multiple-choice reading-comprehension question about ONE news article.\n")
        append("Use ONLY the title and preview below; do not invent details that they do not state.\n")
        append("Write exactly $OPTION_COUNT answer options: one correct, three plausible but clearly wrong.\n")
        append("Keep all options similar in length and style. Do not repeat the correct answer inside the question.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown: ")
        append("{\"question\": \"...\", \"options\": [\"...\", \"...\", \"...\", \"...\"], ")
        append("\"correctIndex\": 0, \"explanation\": \"one short sentence\"}\n")
        append("Target language tag: $languageTag.\n\n")
        append("Title: $title\n")
        append("Preview: $preview")
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
```

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2. Expected `exit=0`, `tests="8" failures="0" errors="0"`. (Robolectric được dùng chỉ để `org.json` là bản thật; nếu lớp test thuần JVM không có `org.json` thật, giữ `@RunWith(RobolectricTestRunner::class)` như trên.)

- [ ] **Step 5: Mutation**

- M1 `MAX_PREVIEW_CHARS = 250` thành `251`: `previewIsCutToAtMost250...` và `previewBoundaryIsExactly250` đỏ.
- M2 bỏ `.replace(HTML_TAG, " ")`: `htmlTagsInThePreview...` đỏ.
- M3 `hasContentToAsk` dùng `&&` thay `||`: `nothingToAskWhenBothTitleAndPreviewAreBlank` đỏ.
- M4 `buildRequestBody` bỏ `responseMimeType`: `requestBodyIsJsonMode...` đỏ.
- M5 thêm `append("Link: https://example.com\n")` vào `buildPrompt`: `promptCarriesOnlyTheTitleAndThePreview` đỏ (assert không có `http`). Giới hạn trung thực: `buildPrompt` chỉ nhận ba chuỗi nên không thể rò thêm dữ liệu bài; test này chỉ canh việc ai đó thêm chuỗi cố định có URL, không chứng minh gì về các trường khác. Ranh giới thật được chứng minh ở Task 4 (body request mà `AiQuizService` gửi, với `Article` chứa chuỗi bí mật).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicy.kt app/src/test/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicyPromptTest.kt
git commit -m "feat(rpg-02): define what may be sent for an AI quiz and how the request looks

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `AiQuizPolicy` phần 2 — parse, kiểm tra, xáo trộn (thuần)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicyParseTest.kt`

**Interfaces:**
- Consumes: `QuizQuestion(articleId, question, options, correctAnswerIndex, explanation, category)`.
- Produces: `sealed interface AiQuizParse { data class Valid(val quiz: QuizQuestion) : AiQuizParse; data class Rejected(val reason: AiQuizRejection) : AiQuizParse }`; `enum class AiQuizRejection { NOT_JSON, MISSING_FIELD, WRONG_OPTION_COUNT, EMPTY_OPTION, DUPLICATE_OPTIONS, BAD_CORRECT_INDEX, EMPTY_QUESTION, QUESTION_TOO_LONG, OPTION_TOO_LONG, OPTION_LENGTH_SKEW, ANSWER_LEAKED_IN_QUESTION, EMPTY_EXPLANATION }`; `fun AiQuizPolicy.parseAndValidate(raw: String, articleId: String, category: String): AiQuizParse`; hằng `MAX_QUESTION_CHARS = 200`, `MAX_OPTION_CHARS = 120`, `MAX_LENGTH_RATIO = 2.5`, `MAX_EXPLANATION_CHARS = 220`.

Quy tắc (mỗi cái có test biên): chuỗi sạch thì cắt khoảng trắng đầu/cuối và gộp khoảng trắng; "trùng" so sánh sau khi hạ chữ thường và bỏ ký tự không phải chữ/số; lệch độ dài = `lengthDài / lengthNgắn > MAX_LENGTH_RATIO` hoặc có lựa chọn dài hơn `MAX_OPTION_CHARS`; "lộ đáp án" = câu hỏi (đã chuẩn hoá) chứa nguyên đáp án đúng (đã chuẩn hoá) khi đáp án đúng dài ≥ 4 ký tự; xáo trộn bằng `java.util.Random(articleId.hashCode().toLong())` rồi quy đổi lại chỉ số đúng; `explanation` quá dài thì cắt về `MAX_EXPLANATION_CHARS`, không từ chối.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.model.rpg

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiQuizPolicyParseTest {

    private fun json(
        question: String = "Which body launched the rocket?",
        options: List<String> = listOf("The space agency", "A shipping firm", "A local council", "A film studio"),
        correct: Any = 0,
        explanation: String = "The preview names the agency.",
    ): String {
        val opts = options.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
        val c = if (correct is String) "\"$correct\"" else correct.toString()
        return """{"question":"${question.replace("\"", "\\\"")}","options":[$opts],"correctIndex":$c,"explanation":"${explanation.replace("\"", "\\\"")}"}"""
    }

    private fun parse(raw: String) = AiQuizPolicy.parseAndValidate(raw, articleId = "art-1", category = "General Knowledge")

    private fun rejected(raw: String): AiQuizRejection = (parse(raw) as AiQuizParse.Rejected).reason

    private fun valid(raw: String): QuizQuestion = (parse(raw) as AiQuizParse.Valid).quiz

    @Test
    fun aWellFormedAnswerIsAccepted_andKeepsTheCorrectTextAtTheRightIndex() {
        val quiz = valid(json())

        assertEquals("Which body launched the rocket?", quiz.question)
        assertEquals(4, quiz.options.size)
        assertEquals("The space agency", quiz.options[quiz.correctAnswerIndex])
        assertEquals("art-1", quiz.articleId)
        assertEquals("General Knowledge", quiz.category)
        assertEquals("The preview names the agency.", quiz.explanation)
    }

    @Test
    fun notJson_isRejected() {
        assertEquals(AiQuizRejection.NOT_JSON, rejected("Sure! Here is your question"))
        assertEquals(AiQuizRejection.NOT_JSON, rejected(""))
        assertEquals(AiQuizRejection.NOT_JSON, rejected("[1,2,3]"))
    }

    @Test
    fun missingFields_areRejected() {
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"options":["a","b","c","d"],"correctIndex":0,"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","correctIndex":0,"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","options":["a","b","c","d"],"explanation":"x"}"""))
        assertEquals(AiQuizRejection.MISSING_FIELD, rejected("""{"question":"q","options":["a","b","c","d"],"correctIndex":0}"""))
    }

    @Test
    fun anyOptionCountOtherThanFour_isRejected() {
        assertEquals(AiQuizRejection.WRONG_OPTION_COUNT, rejected(json(options = listOf("alpha one", "beta two", "gamma three"))))
        assertEquals(AiQuizRejection.WRONG_OPTION_COUNT, rejected(json(options = listOf("alpha one", "beta two", "gamma three", "delta four", "epsilon five"))))
    }

    @Test
    fun emptyOrBlankOption_isRejected() {
        assertEquals(AiQuizRejection.EMPTY_OPTION, rejected(json(options = listOf("alpha one", "beta two", "gamma three", "   "))))
        assertEquals(AiQuizRejection.EMPTY_OPTION, rejected(json(options = listOf("alpha one", "", "gamma three", "delta four"))))
    }

    @Test
    fun duplicateOptions_areRejected_ignoringCaseAndPunctuation() {
        assertEquals(AiQuizRejection.DUPLICATE_OPTIONS, rejected(json(options = listOf("The agency", "the agency.", "A firm", "A studio"))))
        assertEquals(AiQuizRejection.DUPLICATE_OPTIONS, rejected(json(options = listOf("The agency", "THE AGENCY!", "A firm", "A studio"))))
    }

    @Test
    fun correctIndexMustBeAnIntegerFromZeroToThree() {
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = -1)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = 4)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = 1.5)))
        assertEquals(AiQuizRejection.BAD_CORRECT_INDEX, rejected(json(correct = "two")))
        for (i in 0..3) assertTrue(parse(json(correct = i)) is AiQuizParse.Valid)
    }

    @Test
    fun blankQuestion_isRejected_andOverlongQuestionIsRejectedAtTheBoundary() {
        assertEquals(AiQuizRejection.EMPTY_QUESTION, rejected(json(question = "   ")))
        assertTrue(parse(json(question = "q".repeat(200))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.QUESTION_TOO_LONG, rejected(json(question = "q".repeat(201))))
    }

    @Test
    fun overlongOption_isRejectedAtTheBoundary() {
        val ok = "w".repeat(120)
        val long = "w".repeat(121)
        assertTrue(parse(json(options = listOf(ok, "x".repeat(110), "y".repeat(100), "z".repeat(90)))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.OPTION_TOO_LONG, rejected(json(options = listOf(long, "x".repeat(110), "y".repeat(100), "z".repeat(90)))))
    }

    @Test
    fun wildlyDifferentOptionLengths_areRejected_soTheLongestIsNotTheAnswer() {
        val skewed = json(options = listOf("This is a very long and detailed correct answer sentence", "No", "Yes", "Maybe"))

        assertEquals(AiQuizRejection.OPTION_LENGTH_SKEW, rejected(skewed))
    }

    @Test
    fun similarLengthsAreAccepted_atTheRatioBoundary() {
        // 25 vs 10 is exactly 2.5: accepted; 26 vs 10 is above: rejected.
        assertTrue(parse(json(options = listOf("a".repeat(25), "b".repeat(10), "c".repeat(15), "d".repeat(20)))) is AiQuizParse.Valid)
        assertEquals(AiQuizRejection.OPTION_LENGTH_SKEW, rejected(json(options = listOf("a".repeat(26), "b".repeat(10), "c".repeat(15), "d".repeat(20)))))
    }

    @Test
    fun questionThatContainsTheCorrectAnswerVerbatim_isRejected() {
        val leaky = json(
            question = "Was it the space agency that launched the rocket?",
            options = listOf("the space agency", "A shipping firm", "A local council", "A film studio"),
        )

        assertEquals(AiQuizRejection.ANSWER_LEAKED_IN_QUESTION, rejected(leaky))
    }

    @Test
    fun aShortCorrectAnswerIsNotTreatedAsLeakedJustBecauseItIsASubstring() {
        val shortAnswer = json(
            // "six" is a substring of "sixth" here, but a 3-letter answer is too short to count as a leak.
            question = "What was the sixth item on the list?",
            options = listOf("Six", "Four", "Nine", "Two"),
            correct = 0,
        )

        assertTrue(parse(shortAnswer) is AiQuizParse.Valid)
    }

    @Test
    fun blankExplanation_isRejected_andOverlongExplanationIsCutNotRejected() {
        assertEquals(AiQuizRejection.EMPTY_EXPLANATION, rejected(json(explanation = "   ")))
        val quiz = valid(json(explanation = "e".repeat(500)))
        assertEquals(220, quiz.explanation.length)
    }

    @Test
    fun optionOrderIsShuffledDeterministicallyFromTheArticleId_andTheCorrectIndexFollowsTheText() {
        val a1 = parse(json(correct = 0)) as AiQuizParse.Valid
        val a2 = parse(json(correct = 0)) as AiQuizParse.Valid
        assertEquals("same article, same order", a1.quiz.options, a2.quiz.options)

        val seen = mutableSetOf<Int>()
        for (id in 1..60) {
            val q = (AiQuizPolicy.parseAndValidate(json(correct = 0), "article-$id", "c") as AiQuizParse.Valid).quiz
            assertEquals("The space agency", q.options[q.correctAnswerIndex])
            seen += q.correctAnswerIndex
        }
        assertEquals("the AI always put the answer first; the shuffle must spread it over all four slots", setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun fencedJsonIsAccepted_becauseModelsOftenWrapIt() {
        val fenced = "```json\n" + json() + "\n```"

        assertTrue(parse(fenced) is AiQuizParse.Valid)
    }

    @Test
    fun stringsAreTrimmedAndWhitespaceCollapsed() {
        val quiz = valid(json(question = "  Which   body\nlaunched it?  ", options = listOf(" The space  agency ", "A shipping firm", "A local council", "A film studio")))

        assertEquals("Which body launched it?", quiz.question)
        assertTrue(quiz.options.contains("The space agency"))
    }
}
```

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.rpg.AiQuizPolicyParseTest" > /tmp/rpg02-t3-red.log 2>&1; echo exit=$? > /tmp/rpg02-t3-red.exit`
Expected: `exit=1`, `Unresolved reference: parseAndValidate` / `AiQuizParse` / `AiQuizRejection`.

- [ ] **Step 3: Cài đặt tối thiểu** (thêm vào `AiQuizPolicy.kt`; thêm import `java.util.Random`, `org.json.JSONException`)

```kotlin
enum class AiQuizRejection {
    NOT_JSON, MISSING_FIELD, WRONG_OPTION_COUNT, EMPTY_OPTION, DUPLICATE_OPTIONS, BAD_CORRECT_INDEX,
    EMPTY_QUESTION, QUESTION_TOO_LONG, OPTION_TOO_LONG, OPTION_LENGTH_SKEW, ANSWER_LEAKED_IN_QUESTION, EMPTY_EXPLANATION,
}

sealed interface AiQuizParse {
    data class Valid(val quiz: QuizQuestion) : AiQuizParse
    data class Rejected(val reason: AiQuizRejection) : AiQuizParse
}
```

và trong `object AiQuizPolicy` thêm hằng và hàm:

```kotlin
    const val MAX_QUESTION_CHARS = 200
    const val MAX_OPTION_CHARS = 120
    const val MAX_EXPLANATION_CHARS = 220
    const val MAX_LENGTH_RATIO = 2.5
    private const val MIN_LEAK_ANSWER_CHARS = 4
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

    fun parseAndValidate(raw: String, articleId: String, category: String): AiQuizParse {
        fun reject(r: AiQuizRejection): AiQuizParse = AiQuizParse.Rejected(r)
        val obj = try {
            JSONObject(raw.trim().replace(FENCE, "").trim())
        } catch (_: JSONException) {
            return reject(AiQuizRejection.NOT_JSON)
        }
        if (!obj.has("question") || !obj.has("options") || !obj.has("correctIndex") || !obj.has("explanation")) {
            return reject(AiQuizRejection.MISSING_FIELD)
        }
        val question = tidy(obj.optString("question", ""))
        val rawOptions = obj.optJSONArray("options") ?: return reject(AiQuizRejection.MISSING_FIELD)
        if (rawOptions.length() != OPTION_COUNT) return reject(AiQuizRejection.WRONG_OPTION_COUNT)
        val options = (0 until rawOptions.length()).map { tidy(rawOptions.optString(it, "")) }
        if (options.any { it.isEmpty() }) return reject(AiQuizRejection.EMPTY_OPTION)
        if (options.map(::fold).toSet().size != options.size) return reject(AiQuizRejection.DUPLICATE_OPTIONS)
        val correct = obj.opt("correctIndex").let { v ->
            when {
                v is Int -> v
                v is Long -> v.toInt()
                else -> null
            }
        }
        if (correct == null || correct !in 0 until OPTION_COUNT) return reject(AiQuizRejection.BAD_CORRECT_INDEX)
        if (question.isEmpty()) return reject(AiQuizRejection.EMPTY_QUESTION)
        if (question.length > MAX_QUESTION_CHARS) return reject(AiQuizRejection.QUESTION_TOO_LONG)
        if (options.any { it.length > MAX_OPTION_CHARS }) return reject(AiQuizRejection.OPTION_TOO_LONG)
        val longest = options.maxOf { it.length }
        val shortest = options.minOf { it.length }
        if (longest.toDouble() / shortest > MAX_LENGTH_RATIO) return reject(AiQuizRejection.OPTION_LENGTH_SKEW)
        val answer = options[correct]
        if (answer.length >= MIN_LEAK_ANSWER_CHARS && fold(question).contains(fold(answer))) {
            return reject(AiQuizRejection.ANSWER_LEAKED_IN_QUESTION)
        }
        val explanation = tidy(obj.optString("explanation", ""))
        if (explanation.isEmpty()) return reject(AiQuizRejection.EMPTY_EXPLANATION)

        val shuffled = options.mapIndexed { i, text -> text to (i == correct) }.shuffled(Random(articleId.hashCode().toLong()))
        return AiQuizParse.Valid(
            QuizQuestion(
                articleId = articleId,
                question = question,
                options = shuffled.map { it.first },
                correctAnswerIndex = shuffled.indexOfFirst { it.second },
                explanation = explanation.take(MAX_EXPLANATION_CHARS),
                category = category,
            )
        )
    }

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()

    private fun fold(value: String): String = value.lowercase().replace(NON_ALNUM, "")
```

Lưu ý khi cài: `shuffled(Random)` của Kotlin nhận `java.util.Random`; `fold` dùng để so trùng và so "lộ đáp án" nên "the agency." và "The Agency" trùng nhau. `obj.opt("correctIndex")` trả `Double` cho `1.5` và `String` cho `"two"`, cả hai bị `BAD_CORRECT_INDEX`.

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2. Expected `exit=0`, `tests="17" failures="0" errors="0"`. Nếu test xáo trộn đỏ vì `setOf(0,1,2,3)` thiếu một vị trí với 60 articleId, tăng số lần lặp trong test (đây là test phân bố, không phải sai code); ghi lại nếu phải đổi.

- [ ] **Step 5: Mutation**

- M1 bỏ dòng `if (options.map(::fold).toSet().size != options.size)`: `duplicateOptions_...` đỏ.
- M2 đổi `correct !in 0 until OPTION_COUNT` thành `correct !in 0..OPTION_COUNT`: `correctIndexMustBe...` đỏ.
- M3 đổi `MAX_LENGTH_RATIO = 2.5` thành `2.6`: `similarLengthsAreAccepted_atTheRatioBoundary` đỏ (26/10 = 2.6 không còn lớn hơn).
- M4 đổi `MIN_LEAK_ANSWER_CHARS = 4` thành `1`: `aShortCorrectAnswerIsNotTreatedAsLeaked...` đỏ.
- M5 bỏ `.shuffled(Random(...))`: `optionOrderIsShuffled...` đỏ (chỉ số đúng luôn 0).
- M6 đổi `MAX_QUESTION_CHARS = 200` thành `201`: `blankQuestion_isRejected_andOverlongQuestion...` đỏ.
- M7 bỏ `.replace(FENCE, "")`: `fencedJsonIsAccepted` đỏ.
- M8 `explanation.take(...)` thành `explanation`: `blankExplanation_...` đỏ (độ dài 500 ≠ 220).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicy.kt app/src/test/java/com/mckimquyen/reader/domain/model/rpg/AiQuizPolicyParseTest.kt
git commit -m "feat(rpg-02): validate and shuffle an AI-written quiz answer

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 4: `AiQuizService` — gọi Gemini qua `AiRequestGateway`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/AiQuizService.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/AiQuizServiceTest.kt`

**Interfaces:**
- Consumes: `AiRequestGateway.execute(useCase: String, requestBody: String, keys: List<String>): String`, `GeminiSummaryService.resolveApiKeys(): List<String>`, `AiQuizPolicy` (Task 2–3).
- Produces: `sealed interface AiQuizResult { data class Ready(val quiz: QuizQuestion) : AiQuizResult; data class Failed(val reason: AiQuizFailure) : AiQuizResult }`; `enum class AiQuizFailure { NOTHING_TO_ASK, NO_KEY, NETWORK, REJECTED }`; `@Singleton class AiQuizService @Inject constructor(gateway: AiRequestGateway, summaryService: GeminiSummaryService) { suspend fun generate(articleId: String, title: String, shortDescription: String, category: String, languageTag: String): AiQuizResult }`.
- Quy ước: **không** nhận `Article` hay `content`; chỉ nhận ba chuỗi đã chọn sẵn, nên không thể gửi thêm. Tầng gọi (ViewModel) chịu trách nhiệm chỉ gọi khi consent `true`; service này **không** kiểm consent (không đọc DataStore) để giữ một nơi duy nhất quyết định.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.sv

import android.app.Application
import com.mckimquyen.reader.domain.model.rpg.AiQuizPolicy
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
class AiQuizServiceTest {

    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var service: AiQuizService

    private val good = """{"question":"Which body launched the rocket?","options":["The space agency","A shipping firm","A local council","A film studio"],"correctIndex":0,"explanation":"The preview names the agency."}"""

    @Before
    fun setUp() {
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        service = AiQuizService(gateway, summaryService)
    }

    private fun run(
        title: String = "Rocket launch",
        preview: String = "An agency launched a rocket today.",
    ): AiQuizResult = runBlocking { service.generate("art-1", title, preview, "General Knowledge", "en-US") }

    @Test
    fun aValidAnswerBecomesAReadyQuiz_withTheArticleIdAndCategory() {
        coEvery { gateway.execute(any(), any(), any()) } returns good

        val result = run() as AiQuizResult.Ready

        assertEquals("art-1", result.quiz.articleId)
        assertEquals("General Knowledge", result.quiz.category)
        assertEquals("The space agency", result.quiz.options[result.quiz.correctAnswerIndex])
    }

    @Test
    fun theRequestBodyHoldsOnlyTheTitleAndThePreview_nothingElseOfTheArticle() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good

        run(title = "My Title", preview = "My preview")

        val text = JSONObject(body.captured).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("Title: My Title"))
        assertTrue(text.contains("Preview: My preview"))
        assertFalse("no link may be sent", body.captured.contains("http"))
        assertEquals(setOf("contents", "generationConfig"), JSONObject(body.captured).keys().asSequence().toSet())
    }

    @Test
    fun aPreviewLongerThan250CharactersIsCutBeforeItIsSent() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good

        run(preview = "p".repeat(1_000))

        assertFalse(body.captured.contains("p".repeat(251)))
        assertTrue(body.captured.contains("p".repeat(250)))
    }

    @Test
    fun usesTheQuizUseCaseAndTheResolvedKeys() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        every { summaryService.resolveApiKeys() } returns listOf("own-key", "default-key")

        run()

        coVerify(exactly = 1) { gateway.execute("quiz_micro", any(), listOf("own-key", "default-key")) }
    }

    @Test
    fun blankTitleAndPreview_neverCallsTheGateway() {
        val result = run(title = "  ", preview = "")

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK), result)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun noKeys_neverCallsTheGateway() {
        every { summaryService.resolveApiKeys() } returns emptyList()

        val result = run()

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NO_KEY), result)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun aNetworkOrKeyFailure_isAFailureNotACrash() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("offline")

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NETWORK), run())
    }

    @Test
    fun aMissingApiKeyException_isReportedAsNoKey() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.MissingApiKey

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NO_KEY), run())
    }

    @Test
    fun anAnswerThatFailsValidation_isRejected() {
        coEvery { gateway.execute(any(), any(), any()) } returns """{"question":"q","options":["a","b"],"correctIndex":0,"explanation":"x"}"""

        assertEquals(AiQuizResult.Failed(AiQuizFailure.REJECTED), run())
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")

        var thrown: Throwable? = null
        try {
            run()
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue("cancellation must propagate", thrown is CancellationException)
    }

    @Test
    fun noTitleOrPreviewTextIsEverAPartOfAnyFailureResult() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("SECRET-TITLE failed")

        val result = run(title = "SECRET-TITLE")

        assertFalse(result.toString().contains("SECRET-TITLE"))
    }
}
```

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.AiQuizServiceTest" > /tmp/rpg02-t4-red.log 2>&1; echo exit=$? > /tmp/rpg02-t4-red.exit`
Expected: `exit=1`, `Unresolved reference: AiQuizService` / `AiQuizResult` / `AiQuizFailure`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.sv

import android.util.Log
import com.mckimquyen.reader.domain.model.rpg.AiQuizParse
import com.mckimquyen.reader.domain.model.rpg.AiQuizPolicy
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

enum class AiQuizFailure { NOTHING_TO_ASK, NO_KEY, NETWORK, REJECTED }

sealed interface AiQuizResult {
    data class Ready(val quiz: QuizQuestion) : AiQuizResult
    data class Failed(val reason: AiQuizFailure) : AiQuizResult
}

/**
 * Writes one quiz question with Gemini (RPG-02). Privacy boundary: the only article data that can leave the device is
 * the title and a preview of at most [AiQuizPolicy.MAX_PREVIEW_CHARS]; this class is handed those strings, never an
 * article. It does not check the user's consent: the caller (BrainRpgViewModel) is the single place that decides
 * whether to call, and it only calls when the consent is `true`.
 */
@Singleton
class AiQuizService @Inject constructor(
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {

    suspend fun generate(
        articleId: String,
        title: String,
        shortDescription: String,
        category: String,
        languageTag: String,
    ): AiQuizResult {
        val cleanTitle = title.trim()
        val preview = AiQuizPolicy.sanitizePreview(shortDescription)
        if (!AiQuizPolicy.hasContentToAsk(cleanTitle, preview)) return AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK)
        val keys = summaryService.resolveApiKeys()
        if (keys.isEmpty()) return AiQuizResult.Failed(AiQuizFailure.NO_KEY)

        val raw = try {
            gateway.execute(AiQuizPolicy.USE_CASE, AiQuizPolicy.buildRequestBody(AiQuizPolicy.buildPrompt(cleanTitle, preview, languageTag)), keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiSummaryService.SummaryException) {
            // MissingApiKey is an object inside a sealed SummaryException, so it is matched by identity.
            Log.w(TAG, "AI quiz request failed: ${e.javaClass.simpleName}")
            return AiQuizResult.Failed(
                if (e === GeminiSummaryService.SummaryException.MissingApiKey) AiQuizFailure.NO_KEY else AiQuizFailure.NETWORK
            )
        } catch (e: Exception) {
            // Only the exception type is logged: its message could carry the title or the preview.
            Log.w(TAG, "AI quiz request failed: ${e.javaClass.simpleName}")
            return AiQuizResult.Failed(AiQuizFailure.NETWORK)
        }
        return when (val parsed = AiQuizPolicy.parseAndValidate(raw, articleId, category)) {
            is AiQuizParse.Valid -> AiQuizResult.Ready(parsed.quiz)
            is AiQuizParse.Rejected -> {
                Log.w(TAG, "AI quiz answer rejected: ${parsed.reason}")
                AiQuizResult.Failed(AiQuizFailure.REJECTED)
            }
        }
    }

    private companion object {
        const val TAG = "AiQuizService"
    }
}
```

Đã đối chiếu mã thật: `SummaryException` là lớp sealed (`GeminiSummaryService.kt` dòng ~50) và `MissingApiKey` là `object` (dòng 56), nên khớp bằng `===`, không bằng `catch` theo kiểu. Mọi `SummaryException` khác (ví dụ lỗi HTTP) được xếp vào `NETWORK`.

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2. Expected `exit=0`, `tests="11" failures="0" errors="0"`. Biên dịch lại sạch nếu gặp `RApp_GeneratedInjector` (thêm `@Singleton`/`@Inject` nên Hilt cần sinh lại).

- [ ] **Step 5: Mutation**

- M1 trong `AiQuizService` bỏ `AiQuizPolicy.sanitizePreview(...)` (dùng `shortDescription` thô): `aPreviewLongerThan250...` đỏ.
- M2 bỏ dòng `if (keys.isEmpty()) return ...`: `noKeys_neverCallsTheGateway` đỏ.
- M3 bỏ `catch (e: CancellationException) { throw e }`: `cancellationIsNeverSwallowed` đỏ.
- M4 đổi log thành `"... ${e.message}"` và để `IOException` mang tiêu đề: không có test bắt (Log không kiểm được); ghi trung thực vào báo cáo rằng "không log nội dung" chỉ được bảo vệ bằng đọc mã, và kiểm bằng `grep -rn "Log\\." app/src/main/java/com/mckimquyen/reader/domain/sv/AiQuizService.kt` ở Task 12.
- M5 truyền `title` thay `cleanTitle`: không bắt được (chỉ khác khoảng trắng); bỏ M5.
- M6 đổi `USE_CASE` trong lời gọi thành `"podcast_studio"`: `usesTheQuizUseCaseAndTheResolvedKeys` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/sv/AiQuizService.kt app/src/test/java/com/mckimquyen/reader/domain/sv/AiQuizServiceTest.kt
git commit -m "feat(rpg-02): write the quiz with Gemini from the title and a short preview only

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 5: `QuizUiState` và `QuizCache` (thuần, LRU)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/QuizUiState.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/QuizCacheTest.kt`

**Interfaces:**
- Produces: `sealed interface QuizUiState { object Hidden; object AwaitingConsent; object ConsentDeclined; object Loading; data class Ready(val quiz: QuizQuestion); data class Failed(val reason: AiQuizFailure) }`; `class QuizCache(private val capacity: Int = QuizCache.DEFAULT_CAPACITY) { fun get(articleId: String): QuizQuestion?; fun put(articleId: String, quiz: QuizQuestion); val size: Int }` với `const val DEFAULT_CAPACITY = 20`.
- `Hidden` là trạng thái ban đầu cho mọi bài (card chưa được yêu cầu). Chỉ `Ready` được lưu cache; `Failed`, `Loading`, consent không bao giờ được cache.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuizCacheTest {

    private fun quiz(id: String) = QuizQuestion(id, "q", listOf("a", "b", "c", "d"), 0, "e", "c")

    @Test
    fun missReturnsNull_hitReturnsTheSameQuiz() {
        val cache = QuizCache()
        assertNull(cache.get("a"))

        cache.put("a", quiz("a"))

        assertEquals(quiz("a"), cache.get("a"))
    }

    @Test
    fun defaultCapacityIsTwenty_andTheOldestUnusedEntryIsEvictedFirst() {
        val cache = QuizCache()
        (1..20).forEach { cache.put("a$it", quiz("a$it")) }
        assertEquals(20, cache.size)

        cache.put("a21", quiz("a21"))

        assertEquals(20, cache.size)
        assertNull("the oldest entry must go", cache.get("a1"))
        assertNotNull(cache.get("a2"))
        assertNotNull(cache.get("a21"))
    }

    @Test
    fun readingAnEntryKeepsItAlive() {
        val cache = QuizCache(capacity = 3)
        cache.put("a", quiz("a")); cache.put("b", quiz("b")); cache.put("c", quiz("c"))
        cache.get("a") // a is now the most recently used

        cache.put("d", quiz("d"))

        assertNotNull("a was just used", cache.get("a"))
        assertNull("b was the least recently used", cache.get("b"))
    }

    @Test
    fun puttingTheSameIdTwice_replacesAndDoesNotGrow() {
        val cache = QuizCache(capacity = 2)
        cache.put("a", quiz("a"))
        cache.put("a", quiz("a").copy(question = "new"))

        assertEquals(1, cache.size)
        assertEquals("new", cache.get("a")?.question)
    }

    @Test
    fun aZeroOrNegativeCapacityIsTreatedAsOne() {
        val cache = QuizCache(capacity = 0)
        cache.put("a", quiz("a")); cache.put("b", quiz("b"))

        assertEquals(1, cache.size)
        assertNotNull(cache.get("b"))
    }
}
```

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.rpg.QuizCacheTest" > /tmp/rpg02-t5-red.log 2>&1; echo exit=$? > /tmp/rpg02-t5-red.exit`
Expected: `exit=1`, `Unresolved reference: QuizCache`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure

/** What the end-of-article quiz card shows for the current article (RPG-02). */
sealed interface QuizUiState {
    /** The card has not been asked for yet (the reader has not reached the end). */
    object Hidden : QuizUiState
    object AwaitingConsent : QuizUiState
    object ConsentDeclined : QuizUiState
    object Loading : QuizUiState
    data class Ready(val quiz: QuizQuestion) : QuizUiState
    data class Failed(val reason: AiQuizFailure) : QuizUiState
}

/** In-memory, least-recently-used cache of ready quizzes by article id. Only [QuizUiState.Ready] results are stored. */
class QuizCache(capacity: Int = DEFAULT_CAPACITY) {
    private val limit = capacity.coerceAtLeast(1)

    // accessOrder = true: get() and put() move an entry to the end, so the eldest entry is the least recently used.
    private val entries = object : LinkedHashMap<String, QuizQuestion>(INITIAL_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizQuestion>?): Boolean = size > limit
    }

    val size: Int get() = entries.size

    @Synchronized
    fun get(articleId: String): QuizQuestion? = entries[articleId]

    @Synchronized
    fun put(articleId: String, quiz: QuizQuestion) {
        entries[articleId] = quiz
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
        private const val INITIAL_SIZE = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
```

- [ ] **Step 4: Chạy, xanh**

Expected `exit=0`, `tests="5" failures="0" errors="0"`.

- [ ] **Step 5: Mutation**

- M1 `accessOrder = true` thành `false`: `readingAnEntryKeepsItAlive` đỏ.
- M2 `size > limit` thành `size > limit + 1`: `defaultCapacityIsTwenty...` đỏ.
- M3 `capacity.coerceAtLeast(1)` thành `capacity`: `aZeroOrNegativeCapacityIsTreatedAsOne` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/QuizUiState.kt app/src/test/java/com/mckimquyen/reader/ui/page/rpg/QuizCacheTest.kt
git commit -m "feat(rpg-02): model the quiz card states and cache ready quizzes in memory

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: `BrainRpgViewModel` — điều phối consent, gọi, cache, thử lại

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModelTest.kt` (constructor + test mới), `BrainRpgViewModelWrappedTest.kt` (chỉ constructor)
- Test mới: `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModelQuizTest.kt`

**Interfaces:**
- Consumes: `AiQuizService.generate(articleId, title, shortDescription, category, languageTag): AiQuizResult`, `QuizCache`, `QuizUiState`, `Context.quizGeminiConsent`/`setQuizGeminiConsent` (Task 1), `QuizGeneratorService.detectCategory(title, content)`.
- Produces (thay `buildQuiz`): `val quizState: StateFlow<QuizUiState>`; `fun onQuizCardShown(articleId: String, title: String, shortDescription: String, content: String)`; `fun onQuizConsent(agreed: Boolean)`; `fun retryQuizGeneration()`. Constructor mới: `BrainRpgViewModel(@ApplicationContext context: Context, repository, quizGeneratorService, aiQuizService: AiQuizService, wrappedSharer, @DefaultDispatcher defaultDispatcher, @IODispatcher ioDispatcher)` — **thứ tự: `context, repository, quizGeneratorService, aiQuizService, wrappedSharer, defaultDispatcher, ioDispatcher`**.
- `retryQuiz(activity, onDone)` (Rewarded Ad thử lại sau khi trả lời sai) **giữ nguyên tên và hành vi**; hàm thử lại sinh câu hỏi mới tên `retryQuizGeneration()` để không nhầm.
- Quy tắc: `onQuizCardShown` đặt `Hidden` rồi: (1) cache có → `Ready`; (2) consent null → `AwaitingConsent`; (3) false → `ConsentDeclined`; (4) true → `Loading`, gọi `aiQuizService`, `Ready`/`Failed`. Gọi lại cho cùng `articleId` đang `Loading` thì **không** phát thêm request. Đổi bài (`articleId` khác) thì huỷ job cũ. `onQuizConsent(true)` lưu `true` rồi tiếp tục như (4) cho bài đang chờ; `onQuizConsent(false)` chỉ **không** có trong card (nút "Để sau" gọi `onQuizConsentLater()` — không lưu, giữ `AwaitingConsent`). Thử lại: chỉ từ `Failed`, đặt `Loading` và gọi lại một lần. `viewModelScope` huỷ job khi clear.

- [ ] **Step 1: Viết test đỏ** — `BrainRpgViewModelQuizTest.kt`

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.domain.sv.AiQuizResult
import com.mckimquyen.reader.domain.sv.AiQuizService
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.quizGeminiConsent
import com.mckimquyen.reader.ui.ext.setQuizGeminiConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class BrainRpgViewModelQuizTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var ai: AiQuizService
    private lateinit var viewModel: BrainRpgViewModel

    private val ready = QuizQuestion("art-1", "Which?", listOf("a one", "b two", "c three", "d four"), 1, "Because.", "General Knowledge")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.dataStore.edit { it.clear() } }
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        val dao = database.brainRpgDao()
        val importer = BrainRpgLegacyImporter(database, context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE), dao)
        val repository = BrainRpgRepository(context, database, dao, importer, CoroutineScope(dispatcher), dispatcher)
        ai = mockk()
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Ready(ready)
        viewModel = BrainRpgViewModel(context, repository, QuizGeneratorService(), ai, mockk(relaxed = true), dispatcher, dispatcher)
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun show(articleId: String = "art-1", title: String = "Rocket launch", preview: String = "An agency launched a rocket.") =
        viewModel.onQuizCardShown(articleId, title, preview, "BODY-THAT-MUST-NOT-BE-SENT")

    @Test fun beforeTheCardIsShown_theStateIsHidden() {
        assertEquals(QuizUiState.Hidden, viewModel.quizState.value)
    }

    @Test fun neverAsked_showsTheConsentQuestion_andMakesNoRequest() = runTest(dispatcher) {
        show()
        advanceUntilIdle()

        assertEquals(QuizUiState.AwaitingConsent, viewModel.quizState.value)
        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun declinedConsent_showsTheGuidance_andMakesNoRequest() = runTest(dispatcher) {
        context.setQuizGeminiConsent(false)
        show()
        advanceUntilIdle()

        assertEquals(QuizUiState.ConsentDeclined, viewModel.quizState.value)
        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun agreedConsent_makesExactlyOneRequest_andShowsTheQuiz() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        show()
        advanceUntilIdle()

        assertEquals(QuizUiState.Ready(ready), viewModel.quizState.value)
        coVerify(exactly = 1) { ai.generate("art-1", "Rocket launch", "An agency launched a rocket.", any(), any()) }
    }

    @Test fun theBodyOfTheArticleIsNeverPassedToTheService() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        show()
        advanceUntilIdle()

        coVerify(exactly = 0) { ai.generate(any(), any(), match { it.contains("BODY-THAT-MUST-NOT-BE-SENT") }, any(), any()) }
        coVerify(exactly = 0) { ai.generate(match { it.contains("BODY") }, match { it.contains("BODY") }, any(), any(), any()) }
    }

    @Test fun agreeingInTheCard_savesTrue_thenGeneratesForThePendingArticle() = runTest(dispatcher) {
        show()
        advanceUntilIdle()
        assertEquals(QuizUiState.AwaitingConsent, viewModel.quizState.value)

        viewModel.onQuizConsent(true)
        advanceUntilIdle()

        assertEquals(true, context.quizGeminiConsent)
        assertEquals(QuizUiState.Ready(ready), viewModel.quizState.value)
        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun decliningInTheCard_savesFalse_andShowsTheGuidance() = runTest(dispatcher) {
        show()
        advanceUntilIdle()

        viewModel.onQuizConsent(false)
        advanceUntilIdle()

        assertEquals(false, context.quizGeminiConsent)
        assertEquals(QuizUiState.ConsentDeclined, viewModel.quizState.value)
        coVerify(exactly = 0) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun later_savesNothing_andKeepsAsking() = runTest(dispatcher) {
        show()
        advanceUntilIdle()

        viewModel.onQuizConsentLater()
        advanceUntilIdle()

        assertEquals(null, context.quizGeminiConsent)
        assertEquals(QuizUiState.AwaitingConsent, viewModel.quizState.value)
    }

    @Test fun reopeningTheSameArticle_usesTheCache_withoutANewRequest() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        show()
        advanceUntilIdle()
        show()
        advanceUntilIdle()

        assertEquals(QuizUiState.Ready(ready), viewModel.quizState.value)
        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun aSecondCallWhileLoading_doesNotStartAnotherRequest() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        val gate = CompletableDeferred<AiQuizResult>()
        coEvery { ai.generate(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        show()
        advanceUntilIdle()
        assertEquals(QuizUiState.Loading, viewModel.quizState.value)

        show()
        advanceUntilIdle()
        gate.complete(AiQuizResult.Ready(ready))
        advanceUntilIdle()

        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
        assertEquals(QuizUiState.Ready(ready), viewModel.quizState.value)
    }

    @Test fun aFailure_isShownAndNotCached_andIsNotRetriedAutomatically() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Failed(AiQuizFailure.NETWORK)
        show()
        advanceUntilIdle()

        assertEquals(QuizUiState.Failed(AiQuizFailure.NETWORK), viewModel.quizState.value)
        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }

        show() // reopening after a failure asks again (a failure was never cached)
        advanceUntilIdle()
        coVerify(exactly = 2) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun retryAfterAFailure_makesOneNewRequest() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returnsMany
            listOf(AiQuizResult.Failed(AiQuizFailure.REJECTED), AiQuizResult.Ready(ready))
        show()
        advanceUntilIdle()
        assertEquals(QuizUiState.Failed(AiQuizFailure.REJECTED), viewModel.quizState.value)

        viewModel.retryQuizGeneration()
        advanceUntilIdle()

        assertEquals(QuizUiState.Ready(ready), viewModel.quizState.value)
        coVerify(exactly = 2) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun retryWhenNotFailed_doesNothing() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        show()
        advanceUntilIdle()

        viewModel.retryQuizGeneration()
        advanceUntilIdle()

        coVerify(exactly = 1) { ai.generate(any(), any(), any(), any(), any()) }
    }

    @Test fun movingToAnotherArticle_dropsTheStateOfTheFirst() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        val gate = CompletableDeferred<AiQuizResult>()
        coEvery { ai.generate("art-1", any(), any(), any(), any()) } coAnswers { gate.await() }
        coEvery { ai.generate("art-2", any(), any(), any(), any()) } returns AiQuizResult.Ready(ready.copy(articleId = "art-2"))
        show("art-1")
        advanceUntilIdle()

        show("art-2")
        advanceUntilIdle()
        gate.complete(AiQuizResult.Ready(ready)) // the late answer for art-1 must not overwrite art-2
        advanceUntilIdle()

        assertTrue((viewModel.quizState.value as QuizUiState.Ready).quiz.articleId == "art-2")
    }

    @Test fun anArticleWithNoTitleAndNoPreview_isShownAsFailedWithoutARequest() = runTest(dispatcher) {
        context.setQuizGeminiConsent(true)
        coEvery { ai.generate(any(), any(), any(), any(), any()) } returns AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK)

        show(title = "", preview = "")
        advanceUntilIdle()

        assertEquals(QuizUiState.Failed(AiQuizFailure.NOTHING_TO_ASK), viewModel.quizState.value)
    }
}
```

`BrainRpgViewModelTest.kt`/`BrainRpgViewModelWrappedTest.kt` — đổi mọi chỗ dựng `BrainRpgViewModel(...)` sang chữ ký mới (3 chỗ) thêm `context` đứng đầu và `io.mockk.mockk(relaxed = true)` làm `aiQuizService` sau `quizGenerator`; **xoá** hai test `buildQuiz_*` trong `BrainRpgViewModelTest` và hàm `seedArticles` nếu không còn dùng (Task 10 làm dứt điểm; ở Task 6 chỉ cần biên dịch được, nên để hai test đó **vô hiệu bằng cách xoá ngay** vì `buildQuiz` sẽ không còn tồn tại).

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.rpg.BrainRpgViewModelQuizTest" > /tmp/rpg02-t6-red.log 2>&1; echo exit=$? > /tmp/rpg02-t6-red.exit`
Expected: `exit=1`, lỗi biên dịch `Unresolved reference: quizState` / `onQuizCardShown` / `onQuizConsent` / `onQuizConsentLater` / `retryQuizGeneration` và `Too many arguments` ở constructor.

- [ ] **Step 3: Cài đặt tối thiểu** (`BrainRpgViewModel.kt`)

Thay constructor và bỏ `buildQuiz`; thêm import `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `kotlinx.coroutines.Job`, `com.mckimquyen.reader.domain.sv.AiQuizResult`, `com.mckimquyen.reader.domain.sv.AiQuizService`, `com.mckimquyen.reader.ui.ext.quizGeminiConsent`, `com.mckimquyen.reader.ui.ext.setQuizGeminiConsent`, `java.util.Locale`:

```kotlin
@HiltViewModel
class BrainRpgViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrainRpgRepository,
    val quizGeneratorService: QuizGeneratorService,
    private val aiQuizService: AiQuizService,
    private val wrappedSharer: BrainWrappedSharer,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
```

và thay khối `buildQuiz` bằng:

```kotlin
    private val quizCache = QuizCache()
    private val _quizState = MutableStateFlow<QuizUiState>(QuizUiState.Hidden)
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()
    private var quizJob: Job? = null
    private var pendingQuiz: PendingQuiz? = null

    /** What is needed to write a quiz for the article on screen: never the body, only title and short description. */
    private data class PendingQuiz(val articleId: String, val title: String, val shortDescription: String, val category: String)

    /**
     * The end-of-article card appeared. Decides what it shows: a cached quiz, the consent question, the guidance for a
     * declined consent, or a single Gemini request (only when the stored consent is exactly `true`).
     * [content] is used only to pick the XP category on this device; it is never sent anywhere.
     */
    fun onQuizCardShown(articleId: String, title: String, shortDescription: String, content: String) {
        val current = pendingQuiz
        if (current?.articleId == articleId && _quizState.value is QuizUiState.Loading) return
        quizJob?.cancel()
        val category = quizGeneratorService.detectCategory(title, content)
        pendingQuiz = PendingQuiz(articleId, title, shortDescription, category)
        quizCache.get(articleId)?.let {
            _quizState.value = QuizUiState.Ready(it)
            return
        }
        quizJob = viewModelScope.launch {
            val consent = withContext(ioDispatcher) { context.quizGeminiConsent }
            when (consent) {
                null -> _quizState.value = QuizUiState.AwaitingConsent
                false -> _quizState.value = QuizUiState.ConsentDeclined
                true -> generateQuiz(requireNotNull(pendingQuiz))
            }
        }
    }

    /** The user answered the in-card question. Agreeing continues with the article that is waiting. */
    fun onQuizConsent(agreed: Boolean) {
        val waiting = pendingQuiz
        quizJob?.cancel()
        quizJob = viewModelScope.launch {
            withContext(ioDispatcher) { context.setQuizGeminiConsent(agreed) }
            if (!agreed) {
                _quizState.value = QuizUiState.ConsentDeclined
            } else if (waiting != null) {
                generateQuiz(waiting)
            }
        }
    }

    /** "Not now": nothing is stored and the question stays, so it is asked again on another article. */
    fun onQuizConsentLater() {
        _quizState.value = QuizUiState.AwaitingConsent
    }

    /** Asks for a new question after a failure. Only the user's tap gets here; nothing retries by itself. */
    fun retryQuizGeneration() {
        val waiting = pendingQuiz ?: return
        if (_quizState.value !is QuizUiState.Failed) return
        quizJob?.cancel()
        quizJob = viewModelScope.launch { generateQuiz(waiting) }
    }

    private suspend fun generateQuiz(request: PendingQuiz) {
        _quizState.value = QuizUiState.Loading
        val result = withContext(ioDispatcher) {
            aiQuizService.generate(request.articleId, request.title, request.shortDescription, request.category, Locale.getDefault().toLanguageTag())
        }
        // A newer article may have taken over while this request was in flight: its state must not be overwritten.
        if (pendingQuiz?.articleId != request.articleId) return
        _quizState.value = when (result) {
            is AiQuizResult.Ready -> {
                quizCache.put(request.articleId, result.quiz)
                QuizUiState.Ready(result.quiz)
            }
            is AiQuizResult.Failed -> QuizUiState.Failed(result.reason)
        }
    }
```

Xoá import `QuizQuestion` nếu không còn dùng trong file (nó vẫn dùng ở `QuizUiState`, nhưng không ở ViewModel).

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2 kèm `--tests "...BrainRpgViewModelTest" --tests "...BrainRpgViewModelWrappedTest"`. Biên dịch lại sạch Hilt trước (`./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`) vì constructor `@HiltViewModel` đổi. Expected: `BrainRpgViewModelQuizTest tests="15" failures="0"`, các lớp cũ vẫn xanh.

- [ ] **Step 5: Mutation**

- M1 trong `onQuizCardShown` bỏ nhánh `null -> AwaitingConsent`, gộp vào `true`: `neverAsked_showsTheConsentQuestion_andMakesNoRequest` đỏ.
- M2 `false -> ...ConsentDeclined` đổi thành `generateQuiz(...)`: `declinedConsent_showsTheGuidance_andMakesNoRequest` đỏ.
- M3 bỏ `quizCache.get(articleId)?.let {...}`: `reopeningTheSameArticle_usesTheCache...` đỏ.
- M4 bỏ dòng `if (current?.articleId == articleId && _quizState.value is QuizUiState.Loading) return`: `aSecondCallWhileLoading...` đỏ.
- M5 trong `generateQuiz` bỏ dòng `if (pendingQuiz?.articleId != request.articleId) return`: `movingToAnotherArticle...` đỏ.
- M6 `quizCache.put` cho cả `Failed` (cache `QuizQuestion` rỗng): không có kiểu nên bỏ; thay M6 bằng: `is AiQuizResult.Failed -> QuizUiState.Failed(...)` đổi thành `QuizUiState.Hidden`: `aFailure_isShownAndNotCached...` đỏ.
- M7 `retryQuizGeneration` bỏ dòng `if (_quizState.value !is QuizUiState.Failed) return`: `retryWhenNotFailed_doesNothing` đỏ.
- M8 `onQuizConsentLater` ghi `context.setQuizGeminiConsent(false)`: `later_savesNothing_andKeepsAsking` đỏ (viết bản mutation là gọi trong `runBlocking`).
- M9 truyền `content` thay `shortDescription` vào `PendingQuiz`: `theBodyOfTheArticleIsNeverPassedToTheService` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/rpg
git commit -m "feat(rpg-02): decide consent, one request per article, cache and retry in the view model

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 7: UI — nhãn nguồn trên card và `BrainQuizHost` cho năm trạng thái

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizCard.kt` (thêm tham số `aiLabel`, dòng nhãn dưới header)
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizHost.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizHostTest.kt` (Robolectric Compose), cập nhật `app/src/androidTest/.../BrainQuizCardWidgetTest.kt` ở Task 11
- Strings (thêm vào `values/strings.xml` + `values-vi/strings.xml` ở Step 3; 36 file còn lại ở Task 9)

**Interfaces:**
- Produces: `BrainQuizCard(quiz, doubleAlreadyClaimed = false, aiLabel: Boolean = false, onAnswerSubmitted, onDoubleXpRequested, onRetryQuizRequested, modifier)` (chỉ **thêm** tham số có mặc định, mọi nơi gọi cũ giữ nguyên); `@Composable fun BrainQuizHost(state: QuizUiState, doubleRewardedIds: Set<String>, onConsent: (Boolean) -> Unit, onConsentLater: () -> Unit, onRetryGeneration: () -> Unit, onOpenPrivacySettings: () -> Unit, onAnswerSubmitted: (QuizQuestion, Boolean) -> Unit, onDoubleXpRequested: (Activity, QuizQuestion, (Boolean) -> Unit) -> Unit, onRetryQuizRequested: (Activity, (Boolean) -> Unit) -> Unit, modifier: Modifier = Modifier)`; `object BrainQuizTags { const val CONSENT_AGREE = "quiz_consent_agree"; const val CONSENT_LATER = "quiz_consent_later"; const val RETRY_GENERATION = "quiz_retry_generation"; const val OPEN_PRIVACY = "quiz_open_privacy"; const val LOADING = "quiz_loading"; const val AI_LABEL = "quiz_ai_label" }`.
- Hành vi: `Hidden` không vẽ gì; `AwaitingConsent` vẽ câu hỏi consent + hai nút; `ConsentDeclined` vẽ hướng dẫn + nút "Mở Quyền riêng tư"; `Loading` vẽ "AI đang soạn câu hỏi" kèm `CircularProgressIndicator`; `Failed` vẽ "Chưa có câu hỏi AI" + **Thử lại** (lý do `NO_KEY` và `NOTHING_TO_ASK` có câu giải thích riêng, mọi lý do khác dùng câu chung); `Ready` vẽ `BrainQuizCard(aiLabel = true)`.

- [ ] **Step 1: Viết test đỏ** — `BrainQuizHostTest.kt`

```kotlin
package com.mckimquyen.reader.ui.component.rpg

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertDoesNotExist
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.ui.page.rpg.QuizUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainQuizHostTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val quiz = QuizQuestion("art-1", "Which one is right?", listOf("Wrong a", "Right option", "Wrong c", "Wrong d"), 1, "Because.", "tech")
    private fun s(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private val calls = mutableListOf<String>()

    private fun show(state: QuizUiState) {
        rule.setContent {
            BrainQuizHost(
                state = state,
                doubleRewardedIds = emptySet(),
                onConsent = { calls += "consent:$it" },
                onConsentLater = { calls += "later" },
                onRetryGeneration = { calls += "retry" },
                onOpenPrivacySettings = { calls += "privacy" },
                onAnswerSubmitted = { _, ok -> calls += "answer:$ok" },
                onDoubleXpRequested = { _, _, _ -> },
                onRetryQuizRequested = { _, _ -> },
            )
        }
    }

    @Test fun hidden_drawsNothing() {
        show(QuizUiState.Hidden)

        rule.onNodeWithTag(BrainQuizTags.CONSENT_AGREE).assertDoesNotExist()
        rule.onNodeWithTag(BrainQuizTags.LOADING).assertDoesNotExist()
        rule.onNodeWithTag(BrainQuizTags.RETRY_GENERATION).assertDoesNotExist()
    }

    @Test fun awaitingConsent_asksInTheCard_andReportsBothAnswers() {
        show(QuizUiState.AwaitingConsent)

        rule.onNodeWithText(s(R.string.quiz_ai_consent_message)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.CONSENT_AGREE).performClick()
        rule.onNodeWithTag(BrainQuizTags.CONSENT_LATER).performClick()

        assertEquals(listOf("consent:true", "later"), calls)
    }

    @Test fun consentDeclined_pointsToPrivacySettings_andOpensThem() {
        show(QuizUiState.ConsentDeclined)

        rule.onNodeWithText(s(R.string.quiz_ai_declined_message)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.OPEN_PRIVACY).performClick()

        assertEquals(listOf("privacy"), calls)
    }

    @Test fun loading_saysTheAiIsWriting() {
        show(QuizUiState.Loading)

        rule.onNodeWithText(s(R.string.quiz_ai_loading)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.LOADING).assertIsDisplayed()
    }

    @Test fun failed_saysThereIsNoAiQuestion_andRetryIsReported() {
        show(QuizUiState.Failed(AiQuizFailure.NETWORK))

        rule.onNodeWithText(s(R.string.quiz_ai_failed)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.RETRY_GENERATION).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test fun failedForNothingToAsk_hasItsOwnExplanation_notTheGenericOne() {
        show(QuizUiState.Failed(AiQuizFailure.NOTHING_TO_ASK))

        rule.onNodeWithText(s(R.string.quiz_ai_nothing_to_ask)).assertIsDisplayed()
    }

    @Test fun failedForNoKey_hasItsOwnExplanation() {
        show(QuizUiState.Failed(AiQuizFailure.NO_KEY))

        rule.onNodeWithText(s(R.string.quiz_ai_no_key)).assertIsDisplayed()
    }

    @Test fun ready_showsTheQuestionTheOptionsAndTheAiLabel() {
        show(QuizUiState.Ready(quiz))

        rule.onNodeWithText("Which one is right?").assertIsDisplayed()
        rule.onNodeWithText("Right option").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.quiz_ai_label)).assertIsDisplayed()
    }

    @Test fun ready_answeringReportsTheQuizAndTheResult() {
        show(QuizUiState.Ready(quiz))

        rule.onNodeWithText("Right option").performClick()

        assertEquals(listOf("answer:true"), calls)
    }
}
```

- [ ] **Step 2: Chạy, đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.component.rpg.BrainQuizHostTest" > /tmp/rpg02-t7-red.log 2>&1; echo exit=$? > /tmp/rpg02-t7-red.exit`
Expected: `exit=1`, `Unresolved reference: BrainQuizHost` / `BrainQuizTags` / các `R.string.quiz_ai_*`.

- [ ] **Step 3: Cài đặt tối thiểu**

Thêm chuỗi (EN/VI) vào `values/strings.xml` và `values-vi/strings.xml` ngay sau `quiz_ai_consent_message`:

```xml
    <string name="quiz_ai_label">Written by AI from the title and preview; it can be wrong</string>
    <string name="quiz_ai_loading">AI is writing a question…</string>
    <string name="quiz_ai_failed">No AI question yet</string>
    <string name="quiz_ai_no_key">No Gemini key is available, so the AI could not write a question.</string>
    <string name="quiz_ai_nothing_to_ask">This article has no title or preview to ask about.</string>
    <string name="quiz_ai_retry">Try again</string>
    <string name="quiz_ai_consent_agree">Agree</string>
    <string name="quiz_ai_consent_later">Not now</string>
    <string name="quiz_ai_declined_message">AI quiz questions are off. Turn them on in Settings, Privacy, to get a question here.</string>
    <string name="quiz_ai_open_privacy">Open Privacy settings</string>
```
```xml
    <string name="quiz_ai_label">Do AI soạn từ tiêu đề và phần xem trước; có thể sai</string>
    <string name="quiz_ai_loading">AI đang soạn câu hỏi…</string>
    <string name="quiz_ai_failed">Chưa có câu hỏi AI</string>
    <string name="quiz_ai_no_key">Không có khoá Gemini nào, nên AI chưa soạn được câu hỏi.</string>
    <string name="quiz_ai_nothing_to_ask">Bài này không có tiêu đề hay phần xem trước để ra câu hỏi.</string>
    <string name="quiz_ai_retry">Thử lại</string>
    <string name="quiz_ai_consent_agree">Đồng ý</string>
    <string name="quiz_ai_consent_later">Để sau</string>
    <string name="quiz_ai_declined_message">Câu hỏi AI đang tắt. Bật trong Cài đặt, Quyền riêng tư để có câu hỏi ở đây.</string>
    <string name="quiz_ai_open_privacy">Mở cài đặt Quyền riêng tư</string>
```

`BrainQuizCard.kt` — thêm tham số `aiLabel: Boolean = false` (sau `doubleAlreadyClaimed`) và ngay sau khối header, trước `Spacer(modifier = Modifier.height(14.dp))` đầu tiên:

```kotlin
            if (aiLabel) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.quiz_ai_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(BrainQuizTags.AI_LABEL),
                )
            }
```
(import `androidx.compose.ui.platform.testTag`).

`BrainQuizHost.kt`:

```kotlin
package com.mckimquyen.reader.ui.component.rpg

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.ui.page.rpg.QuizUiState

object BrainQuizTags {
    const val CONSENT_AGREE = "quiz_consent_agree"
    const val CONSENT_LATER = "quiz_consent_later"
    const val RETRY_GENERATION = "quiz_retry_generation"
    const val OPEN_PRIVACY = "quiz_open_privacy"
    const val LOADING = "quiz_loading"
    const val AI_LABEL = "quiz_ai_label"
}

/** Draws the end-of-article quiz for each [QuizUiState]; a ready quiz is the existing [BrainQuizCard]. */
@Composable
fun BrainQuizHost(
    state: QuizUiState,
    doubleRewardedIds: Set<String>,
    onConsent: (Boolean) -> Unit,
    onConsentLater: () -> Unit,
    onRetryGeneration: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    onAnswerSubmitted: (QuizQuestion, Boolean) -> Unit,
    onDoubleXpRequested: (Activity, QuizQuestion, (Boolean) -> Unit) -> Unit,
    onRetryQuizRequested: (Activity, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuizUiState.Hidden -> Unit
        QuizUiState.AwaitingConsent -> QuizMessageCard(modifier, R.string.quiz_ai_consent_message) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConsent(true) }, modifier = Modifier.testTag(BrainQuizTags.CONSENT_AGREE)) {
                    Text(stringResource(R.string.quiz_ai_consent_agree))
                }
                OutlinedButton(onClick = onConsentLater, modifier = Modifier.testTag(BrainQuizTags.CONSENT_LATER)) {
                    Text(stringResource(R.string.quiz_ai_consent_later))
                }
            }
        }
        QuizUiState.ConsentDeclined -> QuizMessageCard(modifier, R.string.quiz_ai_declined_message) {
            OutlinedButton(onClick = onOpenPrivacySettings, modifier = Modifier.testTag(BrainQuizTags.OPEN_PRIVACY)) {
                Text(stringResource(R.string.quiz_ai_open_privacy))
            }
        }
        QuizUiState.Loading -> QuizMessageCard(modifier, R.string.quiz_ai_loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag(BrainQuizTags.LOADING))
        }
        is QuizUiState.Failed -> QuizMessageCard(modifier, failureText(state.reason)) {
            OutlinedButton(onClick = onRetryGeneration, modifier = Modifier.testTag(BrainQuizTags.RETRY_GENERATION)) {
                Text(stringResource(R.string.quiz_ai_retry))
            }
        }
        is QuizUiState.Ready -> BrainQuizCard(
            quiz = state.quiz,
            doubleAlreadyClaimed = state.quiz.articleId in doubleRewardedIds,
            aiLabel = true,
            onAnswerSubmitted = { ok -> onAnswerSubmitted(state.quiz, ok) },
            onDoubleXpRequested = { act, done -> onDoubleXpRequested(act, state.quiz, done) },
            onRetryQuizRequested = onRetryQuizRequested,
            modifier = modifier,
        )
    }
}

private fun failureText(reason: AiQuizFailure): Int = when (reason) {
    AiQuizFailure.NO_KEY -> R.string.quiz_ai_no_key
    AiQuizFailure.NOTHING_TO_ASK -> R.string.quiz_ai_nothing_to_ask
    AiQuizFailure.NETWORK, AiQuizFailure.REJECTED -> R.string.quiz_ai_failed
}

@Composable
private fun QuizMessageCard(modifier: Modifier, messageRes: Int, actions: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}
```

Với `Failed(NETWORK)` thì `quiz_ai_failed` ("Chưa có câu hỏi AI") là dòng duy nhất; `Failed(REJECTED)` cũng vậy, đúng yêu cầu spec.

- [ ] **Step 4: Chạy, xanh**

Cùng lệnh Step 2 và thêm `--tests` cho `BrainQuizCard` nếu có test JVM cũ (không có; chỉ androidTest). Expected `exit=0`, `BrainQuizHostTest tests="9" failures="0"`. Nếu Compose test trên Robolectric không tìm thấy cây (`No compose hierarchies found`), đây là vấn đề chạy trong JVM chứ không phải hành vi: thử lại một lần; nếu lặp lại, chuyển các assert sang androidTest ở Task 11 theo cách `BrainQuizCardWidgetTest` đã làm (ActivityScenario + semantics) và ghi vào báo cáo.

- [ ] **Step 5: Mutation**

- M1 `AwaitingConsent -> ...` gọi `onConsent(false)` ở nút Đồng ý: `awaitingConsent_asksInTheCard...` đỏ.
- M2 `Hidden -> Unit` thành vẽ `QuizMessageCard(... loading)`: `hidden_drawsNothing` đỏ.
- M3 `failureText` trả `quiz_ai_failed` cho `NOTHING_TO_ASK`: `failedForNothingToAsk...` đỏ.
- M4 `Ready` truyền `aiLabel = false`: `ready_showsTheQuestionTheOptionsAndTheAiLabel` đỏ.
- M5 `onOpenPrivacySettings` nối nhầm vào `onRetryGeneration`: `consentDeclined_pointsToPrivacySettings...` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/component/rpg app/src/test/java/com/mckimquyen/reader/ui/component/rpg app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(rpg-02): draw the quiz card for each AI state with an honest source label

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 8: Nối `Content`/`ReadingPage` — truyền mô tả ngắn và dùng `BrainQuizHost`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt` (tham số mới `shortDescription`, thay khối `quiz` dòng ~102–111 và khối `quiz?.let` dòng ~247–265)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt` (dòng ~530–541: truyền `shortDescription`)
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/rpg/QuizNavigation.kt` (`LocalOpenPrivacySettings`)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` (cung cấp `LocalOpenPrivacySettings` cạnh `LocalOpenRadarDiff`, dòng ~244)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/rpg/QuizWiringSourceTest.kt` (kiểm bằng nội dung nguồn, vì `Content` quá nặng để dựng trong JVM)

**Interfaces:**
- Consumes: `BrainRpgViewModel.quizState`, `onQuizCardShown`, `onQuizConsent`, `onQuizConsentLater`, `retryQuizGeneration`, `submitQuizAnswer`, `doubleQuizReward`, `retryQuiz` (Task 6); `BrainQuizHost` (Task 7); `RouteName.PRIVACY_SETTINGS`.
- Produces: `val LocalOpenPrivacySettings = compositionLocalOf<() -> Unit> { {} }` (no-op nơi không có điều hướng); `Content(..., shortDescription: String = "", ...)` (tham số có mặc định đặt **ngay sau `articleId`** để các chỗ gọi cũ không vỡ).
- Quyết định về "tới cuối bài": `BrainQuizHost` được đặt trong cùng `item { }` mà card hiện nay chiếm (cuối danh sách), và `LaunchedEffect(articleId)` nằm **bên trong** item đó, nên `onQuizCardShown` chỉ chạy khi item được compose; `LazyColumn` có thể compose item sớm do prefetch (giới hạn đã ghi trong spec §8).

- [ ] **Step 1: Viết test đỏ** — kiểm bằng nội dung nguồn (rẻ và chắc cho ràng buộc riêng tư; `Content` không dựng được trong JVM)

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the privacy boundary where it is wired: Content.kt must hand the quiz the article's short description, never a
 * slice of its body, and must not call the removed header-quiz generator. Reads the source because Content is too heavy
 * to compose in a JVM test.
 */
class QuizWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private val content = File("$root/ui/page/home/read/Content.kt").readText()
    private val readingPage = File("$root/ui/page/home/read/ReadingPage.kt").readText()
    private val homeEntry = File("$root/ui/page/common/HomeEntry.kt").readText()

    @Test
    fun contentTakesAShortDescriptionParameter() {
        assertTrue(Regex("""shortDescription:\s*String\s*=\s*""""").containsMatchIn(content))
    }

    @Test
    fun theQuizIsAskedWithTheShortDescription_notWithTheBodyAsThePreview() {
        val call = Regex("""onQuizCardShown\(([^)]*)\)""").find(content)?.groupValues?.get(1).orEmpty()

        assertTrue("onQuizCardShown must be called from Content", call.isNotBlank())
        val args = call.split(",").map { it.trim() }
        assertEquals("articleId", args[0])
        assertEquals("title", args[1])
        assertEquals("the third argument is the preview and must be the short description", "shortDescription", args[2])
        assertEquals("the body is only for choosing the XP category on the device", "content", args[3])
    }

    @Test
    fun readingPagePassesTheArticlesShortDescriptionDown() {
        assertTrue(readingPage.contains("shortDescription = readingUiState.articleWithFeed.article.shortDescription"))
    }

    @Test
    fun theOldHeadlineQuizPathIsGoneFromContent() {
        assertFalse(content.contains("buildQuiz"))
        assertFalse(content.contains("generateQuiz"))
        assertFalse(content.contains("BrainQuizCard("))
    }

    @Test
    fun contentUsesTheHostForEveryQuizState() {
        assertTrue(content.contains("BrainQuizHost("))
        assertTrue(content.contains("quizState"))
    }

    @Test
    fun theCallIsInsideTheLazyItemSoItOnlyRunsWhenTheCardIsComposed() {
        val hostAt = content.indexOf("BrainQuizHost(")
        assertTrue("the host must be used in Content", hostAt > 0)
        // Everything between the opening of the item that holds the host and the host itself.
        val sameItem = content.substring(0, hostAt).substringAfterLast("item {")
        assertTrue("LaunchedEffect with the article id must sit in the same item as the host", sameItem.contains("LaunchedEffect(articleId"))
    }

    @Test
    fun privacySettingsCanBeOpenedFromTheCard() {
        assertTrue(content.contains("LocalOpenPrivacySettings.current"))
        assertTrue(homeEntry.contains("LocalOpenPrivacySettings provides"))
        assertTrue(homeEntry.contains("RouteName.PRIVACY_SETTINGS"))
    }
}
```

- [ ] **Step 2: Chạy, đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.rpg.QuizWiringSourceTest" > /tmp/rpg02-t8-red.log 2>&1; echo exit=$? > /tmp/rpg02-t8-red.exit`
Expected: `exit=1` (biên dịch được, vì chỉ đọc tệp); XML: các test đỏ vì `Content.kt` chưa có tham số/gọi mới (ví dụ `contentTakesAShortDescriptionParameter`, `theQuizIsAskedWith...`, `contentUsesTheHost...` đều `AssertionError`). Đây là đỏ do tính năng thiếu, không phải typo: xác nhận bằng `grep -c "onQuizCardShown" app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt` ra `0`.

- [ ] **Step 3: Cài đặt tối thiểu**

`QuizNavigation.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rpg

import androidx.compose.runtime.compositionLocalOf

/** Opens Privacy settings from the quiz card; a no-op where navigation is unavailable (previews, tests). */
val LocalOpenPrivacySettings = compositionLocalOf<() -> Unit> { {} }
```

`HomeEntry.kt` — trong `CompositionLocalProvider(...)` cạnh `LocalOpenRadarDiff`, thêm (import `LocalOpenPrivacySettings`):

```kotlin
            LocalOpenPrivacySettings provides {
                navController.navigate(RouteName.PRIVACY_SETTINGS) { launchSingleTop = true }
            },
```

`ReadingPage.kt` — trong lời gọi `Content(` thêm sau `articleId = readingUiState.articleWithFeed.article.id,`:

```kotlin
                                shortDescription = readingUiState.articleWithFeed.article.shortDescription,
```

`Content.kt`:

1. Thêm tham số sau `articleId: String = "",`: `shortDescription: String = "",`.
2. **Xoá** khối dựng quiz cũ (comment + `var quiz by remember(...)` + `LaunchedEffect` gọi `buildQuiz`) và thay bằng:

```kotlin
    val quizState by (brainRpgViewModel?.quizState ?: remember { kotlinx.coroutines.flow.MutableStateFlow<QuizUiState>(QuizUiState.Hidden) })
        .collectAsState()
    val openPrivacySettings = LocalOpenPrivacySettings.current
```
3. Thay toàn bộ khối `quiz?.let { quiz -> item { BrainQuizCard(...) } }` bằng:

```kotlin
                if (brainRpgViewModel != null && articleId.isNotBlank() && (title.isNotBlank() || shortDescription.isNotBlank())) {
                    item {
                        // Inside the lazy item on purpose: this only runs when the card is actually composed at the end.
                        LaunchedEffect(articleId) {
                            brainRpgViewModel.onQuizCardShown(articleId, title, shortDescription, content)
                        }
                        BrainQuizHost(
                            state = quizState,
                            doubleRewardedIds = doubleRewardedIds,
                            onConsent = brainRpgViewModel::onQuizConsent,
                            onConsentLater = brainRpgViewModel::onQuizConsentLater,
                            onRetryGeneration = brainRpgViewModel::retryQuizGeneration,
                            onOpenPrivacySettings = openPrivacySettings,
                            onAnswerSubmitted = { quiz, isCorrect ->
                                brainRpgViewModel.submitQuizAnswer(quiz.articleId, quiz.category, isCorrect) { _, awarded ->
                                    if (isCorrect && awarded > 0) {
                                        Toast.makeText(context, "+$awarded XP! 🎉", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDoubleXpRequested = { act, quiz, onDone ->
                                brainRpgViewModel.doubleQuizReward(act, quiz.articleId, quiz.category, 150L, onDone)
                            },
                            onRetryQuizRequested = { act, onDone -> brainRpgViewModel.retryQuiz(act, onDone) },
                        )
                    }
                }
```
4. Imports: `BrainQuizHost`, `QuizUiState`, `LocalOpenPrivacySettings`; bỏ `QuizQuestion` và `BrainQuizCard` nếu không còn dùng. `articleCategory` (dòng ~116, dùng `detectCategory`) **giữ nguyên**.

- [ ] **Step 4: Chạy, xanh + biên dịch**

Chạy lại lệnh Step 2: `exit=0`, `QuizWiringSourceTest tests="7" failures="0"`. Sau đó biên dịch cả app: `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/rpg02-t8-build.log 2>&1; echo exit=$? > /tmp/rpg02-t8-build.exit` — Expected `exit=0`. Nếu có `Unresolved reference: buildQuiz` thì còn chỗ dùng sót; `grep -rn "buildQuiz" app/src` phải ra rỗng sau Task 6.

- [ ] **Step 5: Mutation**

- M1 trong `Content.kt` đổi đối số thứ ba của `onQuizCardShown` từ `shortDescription` sang `content`: `theQuizIsAskedWithTheShortDescription...` đỏ.
- M2 xoá dòng `shortDescription = readingUiState...` ở `ReadingPage.kt`: `readingPagePassesTheArticlesShortDescriptionDown` đỏ.
- M3 đưa `LaunchedEffect(articleId)` ra ngoài `item { }`: `theCallIsInsideTheLazyItem...` đỏ.
- M4 bỏ `LocalOpenPrivacySettings provides` ở `HomeEntry.kt`: `privacySettingsCanBeOpenedFromTheCard` đỏ.
- Giới hạn trung thực: test nguồn bắt được việc nối sai chỗ nhưng **không** chứng minh app chạy đúng; phần đó do Task 11 (androidTest) và Task 13 (máy thật).

- [ ] **Step 6: Commit** (chỉ sau khi `exit=0` của build)

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt app/src/main/java/com/mckimquyen/reader/ui/page/rpg/QuizNavigation.kt app/src/test/java/com/mckimquyen/reader/ui/page/rpg/QuizWiringSourceTest.kt
git commit -m "feat(rpg-02): pass the short description to the quiz and draw it through the host

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 9: Chuỗi quiz AI trong cả 38 locale

**Files:**
- Modify: `app/src/main/res/values*/strings.xml` (36 file còn lại; `values/` và `values-vi/` đã có từ Task 1 và Task 7)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java` (có sẵn, không sửa)

**Interfaces:**
- Produces: 12 khoá `quiz_ai_title`, `quiz_ai_consent_message`, `quiz_ai_label`, `quiz_ai_loading`, `quiz_ai_failed`, `quiz_ai_no_key`, `quiz_ai_nothing_to_ask`, `quiz_ai_retry`, `quiz_ai_consent_agree`, `quiz_ai_consent_later`, `quiz_ai_declined_message`, `quiz_ai_open_privacy` có mặt ở đủ 38 file; không chuỗi nào chứa `%d`.

- [ ] **Step 1: Test đỏ** — `StringResourceParityTest` đang đỏ từ Task 1 vì 36 locale thiếu khoá. Chạy để thấy đúng lý do:

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" > /tmp/rpg02-t9-red.log 2>&1; echo exit=$? > /tmp/rpg02-t9-red.exit
python3 /tmp/rpg02-xml.py com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest
```
Expected: `exit=1`; `everyLocale_hasNoMissingKeysComparedToBase` báo `missing 12 keys, e.g. quiz_ai_title` ở 36 file.

- [ ] **Step 2: Chèn** — script chạy một lần, chèn ngay sau khoá neo `brain_rpg_retry_quiz` (có ở cả 38 file), chỉ khi chưa có, và lấy bản EN cho 36 file còn lại:

```bash
python3 - <<'PY' > /tmp/rpg02-t9-insert.out 2>&1
import pathlib, re
res = pathlib.Path('app/src/main/res')
base = (res / 'values' / 'strings.xml').read_text(encoding='utf-8')
KEYS = ['quiz_ai_title', 'quiz_ai_consent_message', 'quiz_ai_label', 'quiz_ai_loading', 'quiz_ai_failed', 'quiz_ai_no_key',
        'quiz_ai_nothing_to_ask', 'quiz_ai_retry', 'quiz_ai_consent_agree', 'quiz_ai_consent_later', 'quiz_ai_declined_message',
        'quiz_ai_open_privacy']
lines = {}
for k in KEYS:
    m = re.search(r'^[ \t]*<string name="%s">.*</string>[ \t]*$' % k, base, re.M)
    assert m, 'missing in base: ' + k
    lines[k] = m.group(0)
block = '\n'.join(lines[k] for k in KEYS) + '\n'
assert '%d' not in block
changed = 0
for d in sorted(res.glob('values-*')):
    f = d / 'strings.xml'
    if not f.exists(): continue
    s = f.read_text(encoding='utf-8')
    if 'name="quiz_ai_title"' in s: continue
    m = re.search(r'^[ \t]*<string name="brain_rpg_retry_quiz">.*</string>[ \t]*\n', s, re.M)
    assert m, 'anchor missing in ' + str(f)
    f.write_text(s[:m.end()] + block + s[m.end():], encoding='utf-8'); changed += 1
print('files changed', changed)
PY
cat /tmp/rpg02-t9-insert.out
grep -L 'name="quiz_ai_open_privacy"' app/src/main/res/values*/strings.xml; echo "(files lacking the last key above; expect none)"
for f in app/src/main/res/values*/strings.xml; do xmllint --noout "$f" || echo BAD "$f"; done; echo xml-checked
grep -c 'name="quiz_ai_' app/src/main/res/values-vi/strings.xml app/src/main/res/values/strings.xml
```
Expected: `files changed 36`; không file nào thiếu khoá; không `BAD`; mỗi file `values`/`values-vi` đếm đúng 12.

- [ ] **Step 3: Xanh** — chạy lại lệnh Step 1: `exit=0`, `tests="6" failures="0"`.

- [ ] **Step 4: Mutation** — xoá `quiz_ai_retry` khỏi `values-ja/strings.xml` (lưu bản sao, `cmp` khôi phục): parity đỏ.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values*/strings.xml
git commit -m "feat(rpg-02): add the AI quiz strings in all 38 locales

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: Gỡ quiz tiêu đề của RPG-05 (xoá có kiểm chứng)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/QuizGeneratorService.kt` (xoá `generateQuiz`, `buildDistractors`, `unsupportedVariant`, `extractStatements`, `clip`, `uniquify`, `normalize`, `containsAny`, `Option` và hằng không còn dùng; **giữ** `detectCategory`, `matchesKeywords`, `cleanText` nếu `detectCategory` còn gọi, và toàn bộ `CATEGORY_*`)
- Delete: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/QuizPeer.kt`, `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoQuizPeersTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt` (xoá `queryQuizPeers` + import `QuizPeer`), `BrainRpgRepository.kt` (xoá `quizPeers`, `QUIZ_PEER_LIMIT`, import `QuizPeer`)
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/QuizGeneratorServiceTest.kt` (chỉ giữ 5 test `detectCategory_*`; xoá import `QuizPeer`, `distractors`, `comesFromArticle`, các test `generateQuiz_*`)

**Điều kiện trước khi xoá (đã `grep -r` ngày 2026-11-11):** `generateQuiz` chỉ còn được gọi trong `QuizGeneratorService` chính nó và (trước Task 6) `BrainRpgViewModel.buildQuiz`; `QuizPeer`/`queryQuizPeers`/`quizPeers`/`QUIZ_PEER_LIMIT` chỉ xuất hiện ở `QuizPeer.kt`, `ArticleDao.kt`, `BrainRpgRepository.kt`, `QuizGeneratorService.kt` và hai test nói trên. `QuizGeneratorService` **không** xoá: 10 file khác dùng hằng `CATEGORY_*` (`SkillTreeLayout`, `BrainRpgDecayProcessor`, `BrainRpgPage`, `SkillTreeConstellationCard`, `BrainRpgRepository` và test). `detectCategory` còn được `Content.kt` gọi.

**Interfaces:**
- Produces: `QuizGeneratorService` chỉ còn `detectCategory(title, content): String` và các hằng `CATEGORY_*` (giữ chữ ký và giá trị y nguyên).

- [ ] **Step 1: Chạy lại grep ngay trước khi xoá** (nếu có kết quả ngoài danh sách trên thì dừng và xem lại):

```bash
grep -rnE "generateQuiz|QuizPeer|queryQuizPeers|quizPeers|QUIZ_PEER_LIMIT|buildQuiz" app/src > /tmp/rpg02-t10-grep.txt; wc -l < /tmp/rpg02-t10-grep.txt
sed 's#^[^:]*/##' /tmp/rpg02-t10-grep.txt | cut -d: -f1 | sort | uniq -c
```
Expected (sau Task 6 và 8): chỉ `QuizGeneratorService.kt`, `QuizPeer.kt`, `ArticleDao.kt`, `BrainRpgRepository.kt`, `QuizGeneratorServiceTest.kt`, `ArticleDaoQuizPeersTest.kt`.

- [ ] **Step 2: Viết test đỏ** — thêm vào `QuizGeneratorServiceTest.kt` (sau khi đã xoá các test `generateQuiz_*`) một test canh bề mặt còn lại, để việc xoá không âm thầm làm mất hằng khoá Room:

```kotlin
    @Test
    fun categoryConstantsKeepTheirRoomKeys() {
        assertEquals("Tech & AI", QuizGeneratorService.CATEGORY_TECH)
        assertEquals("Business & Finance", QuizGeneratorService.CATEGORY_BUSINESS)
        assertEquals("Science & Nature", QuizGeneratorService.CATEGORY_SCIENCE)
        assertEquals("Health & Lifestyle", QuizGeneratorService.CATEGORY_HEALTH)
        assertEquals("Philosophy & Ideas", QuizGeneratorService.CATEGORY_PHILOSOPHY)
        assertEquals("General Knowledge", QuizGeneratorService.CATEGORY_GENERAL)
    }

    @Test
    fun theHeadlineQuizGeneratorIsGone() {
        val methods = QuizGeneratorService::class.java.declaredMethods.map { it.name }

        assertTrue("generateQuiz must be removed", methods.none { it == "generateQuiz" })
        assertTrue("detectCategory must stay", methods.contains("detectCategory"))
    }
```
Chạy `QuizGeneratorServiceTest`: `theHeadlineQuizGeneratorIsGone` **đỏ** (`generateQuiz must be removed`) vì chưa xoá; `categoryConstantsKeepTheirRoomKeys` xanh.

- [ ] **Step 3: Xoá** — theo đúng danh sách ở mục Files. Sau khi sửa `QuizGeneratorService.kt`, kiểm không còn import/hằng/hàm chết:

```bash
grep -nE "SENTENCE_BOUNDARY|DISTRACTOR_COUNT|MIN_STATEMENT_LENGTH|MIN_HEADLINE_LENGTH|MAX_STATEMENTS|EXPLANATION_LIMIT|LENGTH_SLACK|LENGTH_JITTER|java.util.Random|QuizPeer|QuizQuestion" app/src/main/java/com/mckimquyen/reader/domain/sv/QuizGeneratorService.kt
```
Expected: rỗng (kể cả `QuizQuestion`, vì `detectCategory` trả `String`). `HTML_TAG`/`WHITESPACE`/`cleanText` chỉ giữ nếu `detectCategory` còn gọi chúng (đọc `detectCategory` ở dòng 12–29: nó chỉ dùng `"$title $content".lowercase()` và `matchesKeywords`, nên `cleanText` và hai Regex đó cũng xoá).

`ArticleDao.kt`: xoá import `QuizPeer` (dòng 16) và khối KDoc + `@Query` + `suspend fun queryQuizPeers` (dòng ~101–116). `BrainRpgRepository.kt`: xoá import `QuizPeer` (dòng 12), hàm `quizPeers` (dòng ~56–57) và `const val QUIZ_PEER_LIMIT = 60` (dòng 308). `git rm app/src/main/java/com/mckimquyen/reader/domain/model/rpg/QuizPeer.kt app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoQuizPeersTest.kt`.

- [ ] **Step 4: Xanh + biên dịch sạch**

```bash
./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks > /tmp/rpg02-t10-compile.log 2>&1; echo exit=$? > /tmp/rpg02-t10-compile.exit
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.*" --tests "com.mckimquyen.reader.domain.repository.*" --tests "com.mckimquyen.reader.ui.page.rpg.*" > /tmp/rpg02-t10-green.log 2>&1; echo exit=$? > /tmp/rpg02-t10-green.exit
```
Expected: cả hai `exit=0`; `QuizGeneratorServiceTest` chỉ còn 7 test (5 + 2) và xanh; các lớp `BrainRpgRepository*`, `BrainRpgDecayProcessorTest`, `SkillTreeLayoutTest` vẫn xanh (chứng minh hằng `CATEGORY_*` không vỡ). `grep -rnE "generateQuiz|QuizPeer|queryQuizPeers|quizPeers|QUIZ_PEER_LIMIT|buildQuiz" app/src` ra rỗng.

- [ ] **Step 5: Mutation** — khôi phục tạm `fun generateQuiz(...)` rỗng vào `QuizGeneratorService`: `theHeadlineQuizGeneratorIsGone` đỏ; đổi `CATEGORY_TECH = "Tech and AI"`: `categoryConstantsKeepTheirRoomKeys` đỏ (và `BrainRpgRepositoryTest` nếu nó dùng khoá). Khôi phục bằng `cmp`.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/mckimquyen/reader/domain app/src/test/java/com/mckimquyen/reader/domain
git commit -m "refactor(rpg-02): remove the headline quiz generator and its peer query

The AI quiz replaces it and nothing else called it; category constants and
detectCategory stay because XP, the skill tree and decay still use them.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 11: androidTest — `BrainQuizCardWidgetTest` mở rộng cho các trạng thái AI

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizCardWidgetTest.kt`

**Interfaces:**
- Consumes: `BrainQuizHost`, `BrainQuizTags`, `QuizUiState`, `R.string.quiz_ai_*` (Task 7, 9). Dùng đúng cách cũ của lớp này: `ActivityScenario<ComponentActivity>` + `ComposeView` + duyệt cây semantics (không `ComposeTestRule`, vì nó đã được ghi nhận là chập chờn trên máy thật).
- Produces: 6 test mới (tổng 10).

Test cũ gọi `BrainQuizCard(...)` trực tiếp với các tham số có mặc định nên **không phải sửa** (kiểm bằng biên dịch ở Step 2). Helper `withCard` dựng `BrainQuizCard`; thêm helper `withHost(state, calls, block)` dựng `BrainQuizHost` theo cùng khuôn, và dùng `Screen.has(...)`/`Screen.click(...)` sẵn có (click qua tag không có trong helper hiện tại: thêm `clickTag(tag)` dựa trên `SemanticsProperties.TestTag`).

- [ ] **Step 1: Viết test (đỏ vì thiếu helper)** — thêm vào lớp:

```kotlin
    private fun <T> withHost(
        state: QuizUiState,
        calls: MutableList<String>,
        awaitText: String,
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        BrainQuizHost(
                            state = state,
                            doubleRewardedIds = emptySet(),
                            onConsent = { calls += "consent:$it" },
                            onConsentLater = { calls += "later" },
                            onRetryGeneration = { calls += "retry" },
                            onOpenPrivacySettings = { calls += "privacy" },
                            onAnswerSubmitted = { _, ok -> calls += "answer:$ok" },
                            onDoubleXpRequested = { _, _, _ -> },
                            onRetryQuizRequested = { _, _ -> },
                        )
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the host never rendered '$awaitText'", screen.await { screen.has(awaitText) })
        block(screen)
    }

    @Test
    fun awaitingConsent_showsTheQuestionAndBothButtons_andAgreeIsReported() {
        val calls = mutableListOf<String>()
        withHost(QuizUiState.AwaitingConsent, calls, str(R.string.quiz_ai_consent_message)) { screen ->
            assertTrue(screen.has(str(R.string.quiz_ai_consent_agree)))
            assertTrue(screen.has(str(R.string.quiz_ai_consent_later)))
            assertTrue(screen.click(str(R.string.quiz_ai_consent_agree)))
            assertTrue(screen.await { calls == listOf("consent:true") })
        }
    }

    @Test
    fun notNow_isReportedAndNothingElse() {
        val calls = mutableListOf<String>()
        withHost(QuizUiState.AwaitingConsent, calls, str(R.string.quiz_ai_consent_later)) { screen ->
            assertTrue(screen.click(str(R.string.quiz_ai_consent_later)))
            assertTrue(screen.await { calls == listOf("later") })
        }
    }

    @Test
    fun declined_pointsToPrivacySettings() {
        val calls = mutableListOf<String>()
        withHost(QuizUiState.ConsentDeclined, calls, str(R.string.quiz_ai_declined_message)) { screen ->
            assertTrue(screen.click(str(R.string.quiz_ai_open_privacy)))
            assertTrue(screen.await { calls == listOf("privacy") })
        }
    }

    @Test
    fun failed_saysThereIsNoAiQuestion_andRetryIsReported() {
        val calls = mutableListOf<String>()
        withHost(QuizUiState.Failed(AiQuizFailure.NETWORK), calls, str(R.string.quiz_ai_failed)) { screen ->
            assertTrue(screen.click(str(R.string.quiz_ai_retry)))
            assertTrue(screen.await { calls == listOf("retry") })
        }
    }

    @Test
    fun loading_saysTheAiIsWriting() {
        withHost(QuizUiState.Loading, mutableListOf(), str(R.string.quiz_ai_loading)) { screen ->
            assertTrue(screen.has(str(R.string.quiz_ai_loading)))
        }
    }

    @Test
    fun ready_showsTheAiLabel_andACorrectAnswerStillOffersTheDoubleXpButton() {
        val calls = mutableListOf<String>()
        withHost(QuizUiState.Ready(quiz), calls, "Right option") { screen ->
            assertTrue("the source label must be visible", screen.has(str(R.string.quiz_ai_label)))
            assertTrue(screen.click("Right option"))
            assertTrue(screen.await { calls == listOf("answer:true") })
            assertTrue(screen.await { screen.has(str(R.string.brain_rpg_double_xp)) })
        }
    }
```
Imports thêm: `com.mckimquyen.reader.domain.sv.AiQuizFailure`, `com.mckimquyen.reader.ui.component.rpg.BrainQuizHost` (cùng package nên không cần), `com.mckimquyen.reader.ui.page.rpg.QuizUiState`. `Screen.has(text)` so khớp theo văn bản nên không cần `clickTag`; nếu hai nút trùng chữ thì dùng văn bản đầy đủ như trên (đã khác nhau).

- [ ] **Step 2: Biên dịch (đỏ → xanh)** — trước khi thêm `withHost`: `./gradlew assembleDevDebugAndroidTest > /tmp/rpg02-t11-build.log 2>&1` ra lỗi `Unresolved reference: withHost`. Sau khi thêm: `exit=0`.

- [ ] **Step 3: Cổng thiết bị chỉ đọc (Pixel `2B051FDH3006MU`)**

```bash
S=2B051FDH3006MU
adb devices
adb -s $S shell dumpsys activity activities | grep topResumedActivity
adb -s $S shell dumpsys activity | grep -ci "Active instrumentation"
adb -s $S shell dumpsys window | grep isKeyguardShowing | head -1
adb -s $S shell ps -A | grep -E "bemytester|skyjoy|mckimquyen" | awk '{print $NF}'
```
Dừng và hỏi owner nếu có instrumentation hoặc app lạ ở foreground (`loyalty.skyjoy.vn.dev` ở foreground đã xảy ra ở FRONT-04: hỏi, không tự chạm). `bemytester` và `loyalty.skyjoy.vn.stg` nền là bình thường. Màn khoá thì hỏi owner mở khoá.

- [ ] **Step 4: Backup byte-exact** (chỉ Pixel): sao chép `/tmp/front04d-backup.py` thành `/tmp/rpg02-backup.py` với `SERIALS = {'pixel': '2B051FDH3006MU'}`, thư mục gốc `/tmp/rpg02-root`, và danh sách tệp gồm `databases/Reader`, `-shm`, `-wal`, `-journal`, `files/datastore/settings.preferences_pb`, `shared_prefs/pairing_store.xml`, `shared_prefs/commute_cast_prefs.xml`, `shared_prefs/podcast_positions.xml`; chạy, kiểm `integrity ok` và kích thước khớp. Tạo `/tmp/rpg02-restore.py` từ `/tmp/front04d-restore.py` tương tự. **Bài học:** DataStore chứa khoá consent mới sau smoke, nên restore `settings.preferences_pb` là bắt buộc và phải so byte-exact.

- [ ] **Step 5: Cài và chạy bằng `am instrument` (KHÔNG `connected*`)**

```bash
S=2B051FDH3006MU
APK="$(ls -t app/build/outputs/apk/dev/debug/*.apk | head -1)"; TAPK=app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
stat -f '%Sm %N' -t '%T' "$APK" "$TAPK"      # phải mới hơn lần build cuối; build lại nếu không
adb -s $S install -r -t "$APK" && adb -s $S install -r -t "$TAPK"
timeout 300 adb -s $S shell am instrument -w -r -e class com.mckimquyen.reader.ui.component.rpg.BrainQuizCardWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/rpg02-t11-run.txt 2>&1; echo exit=$?
python3 /tmp/front04-instr-sum.py /tmp/rpg02-t11-run.txt
```
Expected: `OK (10 tests)` (4 cũ + 6 mới). Test chạy trong tiến trình của app nên **không** đổi DataStore (chúng không đọc consent); dù vậy vẫn restore ở Task 13.

- [ ] **Step 6: Mutation trên máy** (mỗi lần: sửa, `assembleDevDebug assembleDevDebugAndroidTest`, cài, chạy lớp, khôi phục `cmp`):
- M1 trong `BrainQuizHost` nối nút Đồng ý vào `onConsent(false)`: `awaitingConsent_showsTheQuestionAndBothButtons...` đỏ.
- M2 `Ready` bỏ `aiLabel = true`: `ready_showsTheAiLabel...` đỏ.
- M3 `Failed` không gọi `onRetryGeneration`: `failed_saysThereIsNoAiQuestion...` đỏ.

- [ ] **Step 7: Dọn rồi commit**: `adb -s $S shell pm uninstall com.mckimquyen.reader.test`; `python3 /tmp/rpg02-restore.py pixel` (byte-identical); `force-stop com.mckimquyen.reader`.

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizCardWidgetTest.kt
git commit -m "test(rpg-02): cover the AI quiz card states on a real device

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 12: Full suite, lint, build

**Files:** không sửa mã (chỉ sửa nếu một bước tìm ra lỗi thật; khi đó quay về TDD cho lỗi đó).

- [ ] **Step 1: Cây sạch** — `git status --short | grep -v idea` rỗng; `git log --oneline -1` ghi lại HEAD.

- [ ] **Step 2: Full unit suite trên code cuối**

```bash
./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/rpg02-full.log 2>&1; echo exit=$? > /tmp/rpg02-full.exit
python3 /tmp/rpg02-summary.py > /tmp/rpg02-full.sum    # tổng tests/failures/errors/skipped, số tệp XML, số tệp cũ (mtime < thời điểm bắt đầu)
```
`/tmp/rpg02-summary.py` = bản sao của đoạn tổng hợp đã dùng ở FRONT-04 (đọc `app/build/test-results/testDevDebugUnitTest/*.xml`, in tổng và liệt kê test đỏ kèm lớp). Expected: `exit=0`, `failures 0 errors 0`, `stale 0`. Số test phải **khác** 2,733 theo hướng: `+` (QuizConsent 4 + Privacy 2 + Prompt 8 + Parse 17 + Service 11 + Cache 5 + ViewModelQuiz 15 + Host 9 + Wiring 7 + QuizGeneratorService 2 = 80) `−` (≈ 21 `generateQuiz_*` + `ArticleDaoQuizPeersTest` 4 + 2 `buildQuiz_*` ≈ 27); báo số thật, không làm tròn. Nếu có test đỏ **không liên quan** (ví dụ `MirrorServerTest` timing như ở FRONT-04), chạy lại lớp đó riêng 3 lần với `--rerun-tasks`, rồi chạy lại cả suite; ghi cả hai lần vào báo cáo.

- [ ] **Step 3: Lint**

```bash
./gradlew lintDevDebug --rerun-tasks > /tmp/rpg02-lint.log 2>&1; echo exit=$? > /tmp/rpg02-lint.exit
python3 - <<'PY' > /tmp/rpg02-lint.sum
import re
s = open('app/build/reports/lint-results-devDebug.xml', encoding='utf-8').read()
print('errors', len(re.findall(r'severity="Error"', s)), 'warnings', len(re.findall(r'severity="Warning"', s)))
for f in ['AiQuizPolicy', 'AiQuizService', 'QuizUiState', 'BrainQuizHost', 'BrainRpgViewModel', 'QuizNavigation']:
    print(f, len(re.findall(f + r'\.kt', s)))
PY
```
Expected: 0 lỗi, cảnh báo ≈ baseline 2,463 (báo số thật; xoá code thì có thể giảm), 0 hit ở file mới. Kiểm thêm: `grep -rn "Log\\." app/src/main/java/com/mckimquyen/reader/domain/sv/AiQuizService.kt` chỉ in loại ngoại lệ và lý do từ chối, **không** tiêu đề, mô tả hay câu hỏi (giới hạn M4 của Task 4).

- [ ] **Step 4: Build**: `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/rpg02-assemble.log 2>&1; echo exit=$? > /tmp/rpg02-assemble.exit` → `exit=0`.

- [ ] **Step 5: Nếu có sửa gì ở Step 1–4, commit riêng** (`fix(rpg-02): ...`); nếu không thì không commit.

---

### Task 13: Smoke thật trên Pixel, báo cáo DONE, cập nhật tài liệu

Quyết định thiết bị: **Pixel `2B051FDH3006MU`** (Android 17, đã có đủ dữ liệu 168+ bài thật và gate quen thuộc). KJ7 chỉ dùng nếu Pixel bận hoặc owner chọn. Đây là lần **đầu** tính năng gọi Gemini thật; kết quả phụ thuộc mạng và quota key mặc định (FIX-09 đã nêu key dùng chung), nên **báo đúng điều quan sát được**, không bịa.

**Files:**
- Create: `doc/task/done/RPG-02_ai-micro-quiz-engine_DONE.md`
- Modify: `doc/task/todo/RPG-02_ai-micro-quiz-engine.md` (đổi dòng "Trạng thái" thành đã làm, trỏ tới báo cáo; **không** di chuyển file, như các lát trước), `doc/feature.md`

- [ ] **Step 1: Cổng và backup** — như Task 11 Step 3–4 (gate chỉ đọc; backup byte-exact **gồm `settings.preferences_pb`**; `ping` Pixel → internet để biết có mạng: `adb -s $S shell "ping -c1 -W3 8.8.8.8"`).

- [ ] **Step 2: Cài bản dev debug của HEAD** (kiểm `stat` mới hơn build cuối). Quyền: không cần quyền mạng nội bộ (Gemini ở internet).

- [ ] **Step 3: Kịch bản thật** (đọc `uiautomator dump` để lấy toạ độ, không đoán; ảnh chụp bằng `adb exec-out screencap -p`):
1. Xoá khoá consent quiz trước khi thử (chỉ khoá quiz, không xoá DataStore): **không** làm; thay vào đó lần đầu dùng bản backup mà DataStore chưa có khoá `quiz_gemini_consent` (khoá mới nên chắc chắn chưa có). Xác nhận bằng đọc bản sao DataStore: không có chuỗi `quiz_gemini_consent`.
2. Mở một bài thật, cuộn tới cuối: thấy card hỏi consent trong card (hai nút). **Chụp.** Đếm request tới `generativelanguage.googleapis.com`: `adb logcat -d | grep -c "quiz_micro"` phải là **0** (gateway ghi `[useCase]`); ghi cả cách đếm.
3. Bấm **Để sau**: card vẫn hỏi, DataStore không đổi (so bản sao).
4. Mở lại, bấm **Đồng ý**: thấy "AI đang soạn câu hỏi…", rồi (nếu mạng/quota ổn) card câu hỏi có nhãn "Do AI soạn…". **Chụp.** `logcat` chứa đúng **một** dòng `[quiz_micro]` cho bài đó.
5. Mở lại **cùng bài**: không có request mới (số dòng `[quiz_micro]` không tăng), card hiện ngay (cache).
6. Trả lời đúng: thấy `+150 XP` và `Master Reader`/XP như cũ (đối chiếu `xp_event` trong bản sao DB). Trả lời sai ở bài khác: nút Rewarded Ad thử lại vẫn hiện (không bấm quảng cáo thật; nếu quảng cáo toàn màn hình hiện ra thì dừng theo quy tắc: chỉ Back, không bấm "Tiếp tục đến ứng dụng", báo owner).
7. Quyền riêng tư: vào Cài đặt → Quyền riêng tư, thấy công tắc "Câu hỏi trắc nghiệm do AI soạn" đang bật; tắt nó, mở bài mới: card hướng dẫn bật + nút mở cài đặt, **không** request mới; bấm nút, vào đúng trang. 
8. Tắt mạng (chế độ máy bay qua `adb shell cmd connectivity airplane-mode enable`, rồi **tắt lại**) và mở bài chưa có cache: card "Chưa có câu hỏi AI" + Thử lại; bật mạng và bấm Thử lại: một request mới. Nếu không bật/tắt được máy bay bằng adb, ghi "chưa kiểm" thay vì bỏ qua.
9. Bài chỉ có tiêu đề (hoặc mô tả trống): kiểm nội dung body request bằng `logcat` chỉ ghi `[quiz_micro]`, **không** có tiêu đề (đã bảo đảm không log nội dung); nếu cần xem body thật thì không có cách hợp lệ trong lát này, nên ghi giới hạn: ranh giới "chỉ title + preview" được chứng minh bằng test JVM, không bằng bắt gói tin.

- [ ] **Step 4: Dọn và restore** — tắt chế độ máy bay nếu đã bật, gỡ test APK nếu đã cài, `force-stop`, `python3 /tmp/rpg02-restore.py pixel` và xác nhận **BYTE IDENTICAL** cho `Reader`, `-shm`, `-wal`, `settings.preferences_pb`, `commute_cast_prefs.xml`, `pairing_store.xml` (và `podcast_positions.xml` vắng như backup). DataStore sẽ mất khoá consent quiz do restore: đó là mong muốn.

- [ ] **Step 5: Báo cáo** — `doc/task/done/RPG-02_ai-micro-quiz-engine_DONE.md` (EN như các báo cáo trước): Scope; Implemented (từng task); Verification JVM (số thật); Mutation (từng guard); Verification device (từng bước ở Step 3 **đã làm** và **không làm được**, nguyên văn lỗi); Found on device; **Not verified / limits** gồm: chỉ tiêu đề và ≤250 ký tự mô tả nên câu hỏi hẹp; không kiểm đúng/sai nội dung, chỉ cấu trúc; có thể có hai đáp án cùng đúng; offline/hết quota/từ chối consent thì mất card và XP quiz (owner đã chọn); cache chỉ trong bộ nhớ; prefetch của `LazyColumn` có thể gọi sớm một chút; key mặc định dùng chung quota (FIX-09); body request chỉ được chứng minh bằng test JVM; không log nội dung chỉ được bảo vệ bằng đọc mã; quiz tiêu đề RPG-05 bị xoá (khôi phục từ git nếu cần); chuỗi mới là English ở 36 locale.

- [ ] **Step 6: `doc/feature.md` và task gốc** — mục 3 Picked: "RPG-02 xong, xem báo cáo"; thêm dòng ✅ với số liệu thật; mục 4 FRONT-01 là bước kế tiếp. `RPG-02_ai-micro-quiz-engine.md`: cập nhật dòng "Trạng thái". Không đề nghị PR, worktree hay làm FRONT-01 cùng lúc.

- [ ] **Step 7: Commit**

```bash
git add doc/task/done/RPG-02_ai-micro-quiz-engine_DONE.md doc/task/todo/RPG-02_ai-micro-quiz-engine.md doc/feature.md
git commit -m "docs(rpg-02): report the AI micro quiz with its verified and unverified parts

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Tự soát plan (đối chiếu spec)

| Yêu cầu spec | Task |
|---|---|
| §2.1 / §5 chỉ gửi tiêu đề + mô tả ≤ 250, không thân bài/link/feed/account | 2 (sanitize, prompt), 4 (body request thật), 8 (`QuizWiringSourceTest`) |
| §5 mô tả lấy từ `shortDescription`, không dựng từ `content`; `Content` nhận tham số riêng | 8 (tham số, `ReadingPage`, test nguồn) |
| §2.2 / §4 AI viết câu hỏi + 4 đáp án + giải thích; kiểm tra cấu trúc nghiêm; xáo theo hạt giống | 3 |
| §2.3 / §3.5 lỗi → "chưa có câu hỏi AI" + Thử lại, không dự phòng | 4 (kết quả lỗi), 6 (`Failed`, retry), 7 (UI) |
| §2.4 / §3.2–3.3 consent riêng quiz, hỏi trong card, "Để sau" không lưu, từ chối → hướng dẫn | 1, 6, 7, 8 |
| §2.5 / §3.4 gọi một lần mỗi bài khi card xuất hiện, cache bộ nhớ ~20 | 5 (LRU), 6 (một request, cache, đổi bài), 8 (LaunchedEffect trong item) |
| §2.6 / §6 xoá `generateQuiz`/`QuizPeer`/`queryQuizPeers`/`quizPeers`, giữ `detectCategory` | 10 |
| §2.7 nhãn "Do AI soạn" | 7, 9 |
| §6 chuỗi 38 locale | 9 (và 1, 7 cho EN/VI) |
| §7 JVM, Compose, Robolectric DataStore, androidTest, mutation, full suite, lint, build | 1–11, 12 |
| §7 thiết bị thật, đếm request, trung thực khi không có mạng | 13 |
| §8 giới hạn nói thẳng | 13 (báo cáo) |
| §9 ngoài phạm vi (ad/IAP, XP, Room, thân bài, FRONT-01) | Global Constraints |

**Kiểm nhất quán kiểu giữa các task:** `AiQuizFailure` định nghĩa ở Task 4 (`domain/sv`), dùng ở Task 5 (`QuizUiState.Failed`), 6, 7, 11. `QuizUiState` (Task 5) khớp Task 6–8 và 11 (`Hidden, AwaitingConsent, ConsentDeclined, Loading, Ready, Failed`). `BrainRpgViewModel` constructor thứ tự `context, repository, quizGeneratorService, aiQuizService, wrappedSharer, defaultDispatcher, ioDispatcher` nhất quán ở Task 6 (test và code) và các test cũ phải đổi (3 chỗ). `AiQuizPolicy.USE_CASE = "quiz_micro"` dùng ở Task 2, 4. Hàm ViewModel `onQuizCardShown(articleId, title, shortDescription, content)` khớp Task 6 và 8. `BrainQuizHost` tham số khớp Task 7, 8, 11.

**Sửa sau soát:** (1) ở Task 6 ghi `onQuizConsentLater()` nhưng phần mô tả quy tắc nhắc nhầm `onQuizConsent(false)` cho "Để sau"; đã phân biệt rõ trong bản mã (nút "Để sau" gọi `onQuizConsentLater`); (2) Task 7 Step 5 M5 và Task 3 Step 5 M5/M8 có chỗ chỉ khẳng định bắt được khi nhánh mã khớp; đã ghi giới hạn rõ ở từng chỗ chứ không tuyên bố hơn mức kiểm; (3) `retryQuiz` (Rewarded Ad) và `retryQuizGeneration` (sinh lại) đã tách tên để không nhầm; (4) test `QuizWiringSourceTest.theCallIsInsideTheLazyItem...` được viết lại cho đúng (đã sửa trong plan).

**Rủi ro biết trước khi thực thi:** (a) Compose test trên Robolectric có thể chập chờn "No compose hierarchies"; đã có lối thoát (chuyển sang androidTest, Task 7 Step 4). (b) Đổi constructor `@HiltViewModel` và thêm `@Singleton AiQuizService` buộc biên dịch lại sạch Hilt (bài học FRONT-04). (c) Smoke thật phụ thuộc mạng và quota key mặc định dùng chung; nếu hết quota thì card ở `Failed` và báo cáo ghi đúng như vậy. (d) Phần "một request đúng một lần" được chứng minh bằng test ViewModel và đếm dòng `[quiz_micro]` trong logcat; không có bắt gói tin.
