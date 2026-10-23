# [FIX-20] Dừng ghi DataStore Preferences chứa secret vào log — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Ngăn `SettingsProvider` dump nội dung DataStore `Preferences` (chứa các key AI do người dùng nhập) vào logcat, giữ nguyên luồng cập nhật settings của Compose.

**Architecture:** Xoá lệnh `Log.i("RLog", "AppTheme: ${it}")` trong lambda map của `SettingsProvider` và bỏ import `android.util.Log` thừa. Thêm regression test Compose/Robolectric ghi sentinel vào DataStore `settings`, chứng minh settings cập nhật bình thường nhưng không log nào chứa sentinel hay tiền tố `AppTheme:`. Dọn dẹp DataStore sau test để tránh lan dữ liệu giả.

**Tech Stack:** Kotlin, Jetpack Compose UI Test (`createComposeRule`), Robolectric (`@Config(sdk = [34])`), AndroidX DataStore Preferences, `ShadowLog`, Gradle Java 17.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-fix-20-settings-secret-log-design.md`.
- Tuyệt đối không in, log, lưu hoặc paste giá trị key thật (kể cả một phần) vào chat, báo cáo, plan hoặc test fixture. Chỉ dùng sentinel giả rõ ràng (ví dụ `"FAKE_GEMINI_KEY_SENTINEL"`).
- Không tự commit/push. Mỗi bước commit theo mẫu workflow được thay bằng review diff tại chỗ.
- Không tạo git worktree, không dùng thiết bị thật/emulator. Giữ device lock Pixel 7 Pro cho các yêu cầu device riêng trong tương lai.
- Không sửa/xoá logcat lịch sử trên thiết bị; không đổi cách lưu trữ hay mã hoá key; không đụng `GeminiConfig.kt` hay mở lại FIX-09 (vẫn deferred).
- Không can thiệp vào `toSettings()`, `collectAsStateValue`, `CompositionLocalProvider` hay các `Local*` khác.
- Test regression phải RED trên mã hiện tại vì assertion phát hiện sentinel/`AppTheme:` trong log, không được coi lỗi biên dịch hoặc lỗi khởi tạo môi trường là RED.
- Test phải dọn sạch các key giả trong DataStore sau khi chạy (`tearDown`) để không làm ảnh hưởng test khác.

## Files và trách nhiệm

- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt:3,226` — xoá import `Log` và dòng `Log.i("RLog", "AppTheme: ${it}")`.
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/SettingsProviderLoggingTest.kt` — regression test Compose + DataStore: ghi key giả, render `SettingsProvider`, assert settings áp dụng nhưng log sạch.
- Modify: `doc/feature.md`, `doc/task/todo/01_FIX_BUGS_AND_STABILITY.md` (nếu thêm dòng index), tạo báo cáo `doc/task/done/FIX-20_settings-secret-log_DONE.md`.

---

### Task 1: Thêm failing regression test cho SettingsProvider

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/SettingsProviderLoggingTest.kt`

**Interfaces:**
- Consumes: `SettingsProvider` (`Settings.kt`), `Context.dataStore`, `DataStore.putBlocking`, `DataStoreKeys.CustomGeminiApiKey`, `DataStoreKeys.CustomOpenAiApiKey`, `DataStoreKeys.CustomDeepSeekApiKey`, `DataStoreKeys.CustomGroqApiKey`, `DataStoreKeys.ThemeIndex`, `LocalThemeIndex`.

- [x] **Step 1: Viết test regression (RED)**

Tạo file `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/SettingsProviderLoggingTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.pref

import android.app.Application
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsProviderLoggingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sentinels = listOf(
        "FAKE_GEMINI_KEY_SENTINEL_ALPHA",
        "FAKE_OPENAI_KEY_SENTINEL_BETA",
        "FAKE_DEEPSEEK_KEY_SENTINEL_GAMMA",
        "FAKE_GROQ_KEY_SENTINEL_DELTA",
    )

    @Before
    fun setUp() {
        ShadowLog.clear()
        // Ghi đồng thời 4 key AI giả và themeIndex để chứng minh DataStore flow hoạt động
        context.dataStore.putBlocking(DataStoreKeys.CustomGeminiApiKey, sentinels[0])
        context.dataStore.putBlocking(DataStoreKeys.CustomOpenAiApiKey, sentinels[1])
        context.dataStore.putBlocking(DataStoreKeys.CustomDeepSeekApiKey, sentinels[2])
        context.dataStore.putBlocking(DataStoreKeys.CustomGroqApiKey, sentinels[3])
        context.dataStore.putBlocking(DataStoreKeys.ThemeIndex, TEST_THEME_INDEX)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.CustomGeminiApiKey.key)
                prefs.remove(DataStoreKeys.CustomOpenAiApiKey.key)
                prefs.remove(DataStoreKeys.CustomDeepSeekApiKey.key)
                prefs.remove(DataStoreKeys.CustomGroqApiKey.key)
                prefs.remove(DataStoreKeys.ThemeIndex.key)
            }
        }
        ShadowLog.clear()
    }

    @Test
    fun settingsProvider_providesUpdatedThemeWithoutLoggingSecretsOrPreferencesDump() {
        var observedThemeIndex = -1

        composeRule.setContent {
            SettingsProvider {
                observedThemeIndex = LocalThemeIndex.current
                Text(text = "theme=$observedThemeIndex")
            }
        }

        // SettingsProvider thu thập flow trên Dispatchers.Default, giá trị ban đầu là mặc định
        // (ThemeIndexPref.default). Chờ tới khi giá trị DataStore thật đi qua provider; lúc đó
        // lambda map (nơi từng có Log.i) chắc chắn đã chạy cho chính emission này.
        composeRule.waitUntil(timeoutMillis = SETTINGS_FLOW_TIMEOUT_MS) {
            observedThemeIndex == TEST_THEME_INDEX
        }
        assertEquals(TEST_THEME_INDEX, observedThemeIndex)

        val rlogs = ShadowLog.getLogs()
            .filter { it.tag == "RLog" }
            .map { it.msg }

        // Trước khi sửa: dòng AppTheme: ${it} in toàn bộ preferences chứa cả 4 sentinel
        for (logMsg in rlogs) {
            assertFalse("Log không được chứa tiền tố AppTheme: dump", logMsg.contains("AppTheme:"))
            for (sentinel in sentinels) {
                assertFalse("Log không được chứa secret sentinel: $sentinel", logMsg.contains(sentinel))
            }
        }
    }

    private companion object {
        const val TEST_THEME_INDEX = 7
        const val SETTINGS_FLOW_TIMEOUT_MS = 5000L
    }
}
```

- [x] **Step 2: Chạy test để xác nhận RED**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*SettingsProviderLoggingTest' --rerun
```

Kỳ vọng: **FAIL** với `AssertionError` ("Log không được chứa tiền tố AppTheme: dump" hoặc "Log không được chứa secret sentinel"), chứng minh test bắt đúng dòng log rò rỉ trên code hiện tại.

---

### Task 2: Xoá dòng log dump Preferences trong SettingsProvider

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt:3,226`

- [x] **Step 1: Đọc kỹ đoạn code xung quanh dòng 226**

Kiểm tra `Settings.kt` từ dòng 220 đến 230 và dòng import ở đầu file:
- Dòng 3: `import android.util.Log`
- Dòng 226: `Log.i("RLog", "AppTheme: ${it}")`

- [x] **Step 2: Xoá dòng log và import**

Sửa `Settings.kt`:
1. Xoá dòng `import android.util.Log` ở đầu file (không còn chỗ nào khác trong `Settings.kt` gọi `Log`).
2. Sửa khối `context.dataStore.data.map`:

Trước:
```kotlin
    val settings = remember {
        context.dataStore.data.map {
            Log.i("RLog", "AppTheme: ${it}")
            it.toSettings()
        }
    }.collectAsStateValue(initial = Settings())
```

Sau:
```kotlin
    val settings = remember {
        context.dataStore.data.map {
            it.toSettings()
        }
    }.collectAsStateValue(initial = Settings())
```

- [x] **Step 3: Chạy test xác nhận GREEN**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*SettingsProviderLoggingTest' --rerun
```

Kỳ vọng: **BUILD SUCCESSFUL**, 1 test pass, 0 failures, 0 errors.

---

### Task 3: Full suite verification, build check và tài liệu

**Files:**
- Modify: `doc/feature.md`
- Create: `doc/task/done/FIX-20_settings-secret-log_DONE.md`

- [x] **Step 1: Chạy toàn bộ unit test với cờ `--rerun`**

```bash
./gradlew testDevDebugUnitTest --rerun
```

Kỳ vọng: **BUILD SUCCESSFUL**, tổng số test = 1251 (1250 cũ + 1 test mới), 0 failures/errors/skipped, không dùng `JAVA_TOOL_OPTIONS`. Đếm từ các file XML mới sinh trong `app/build/test-results/testDevDebugUnitTest`.

- [x] **Step 2: Chạy build APK và compile androidTest**

```bash
./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin
```

Kỳ vọng: **BUILD SUCCESSFUL**, cả hai task đều hoàn thành exit code 0.

- [x] **Step 3: Quét kiểm tra không còn Preferences/DataStore dump trong mã app**

```bash
python3 - <<'PY'
import subprocess
out = subprocess.check_output(['git', 'grep', '-n', 'AppTheme:.*it'], text=True)
print('matches=', out.strip())
assert not out.strip(), 'Vẫn còn log AppTheme dump'
PY
```

- [x] **Step 4: Viết báo cáo hoàn thành `doc/task/done/FIX-20_settings-secret-log_DONE.md`**

Nội dung báo cáo:
- Tóm tắt: xoá dòng log dump toàn bộ DataStore Preferences tại `Settings.kt:226`.
- Bằng chứng rò rỉ: phát hiện khi đọc logcat trong phiên smoke podcast 2026-10-23.
- Thay đổi: xoá `Log.i("RLog", "AppTheme: ${it}")` và `import android.util.Log`; thêm test `SettingsProviderLoggingTest`.
- Kết quả test: RED chứng minh 4 sentinel bị lọt log; GREEN sau sửa; full suite 1251 tests pass; assembleDevDebug và compileDevDebugAndroidTestKotlin pass.
- Giới hạn: không xoá logcat cũ trên thiết bị, không đổi cách lưu key, FIX-09 vẫn deferred, rotate key là thao tác thủ công của người dùng.
- Không in bất kỳ giá trị key thật nào vào báo cáo.

- [x] **Step 5: Cập nhật `doc/feature.md`**

Chuyển mục FIX-20 vào `## ✅ Recently implemented`:
```markdown
- **FIX-20 — Chặn secret lọt logcat từ SettingsProvider** (`doc/task/done/FIX-20_settings-secret-log_DONE.md`): xoá dòng log dump toàn bộ DataStore Preferences (`Log.i("RLog", "AppTheme: ${it}")`) tại `Settings.kt:226`. Ngăn rò rỉ các AI API key (Gemini, OpenAI, DeepSeek, Groq) người dùng nhập qua DataStore. 1251 unit tests pass (buộc chạy mới với `--rerun`), `assembleDevDebug` và androidTest compile pass. Không đổi cách lưu trữ hay key nhúng APK (FIX-09 vẫn deferred); không thu hồi/rotate key. Chưa commit/push.
```

- [x] **Step 6: Kiểm tra diff cuối**

```bash
git diff --check
git status -sb
git diff --stat
```

Xác nhận diff sạch sẽ, đúng phạm vi, không chạm `.idea/deviceManager.xml`.

---

## Tự kiểm plan

- **Spec coverage:** Xoá dòng dump (Task 2), xoá import (Task 2), test sentinel 4 key + theme index (Task 1), dọn dẹp DataStore (Task 1), full suite/build (Task 3), báo cáo không chứa secret (Task 3). Đầy đủ.
- **Placeholder scan:** Không có placeholder, việc chưa hoàn thành hoặc chuỗi tạm. Mọi đoạn code và command đều cụ thể, chạy được.
- **Type consistency:** Sử dụng đúng `DataStoreKeys.CustomGeminiApiKey`, `DataStoreKeys.ThemeIndex`, `LocalThemeIndex`, `SettingsProvider`, `composeRule`.
- **Ràng buộc:** Không commit/push tự động, không chạm thiết bị, không tạo worktree.
