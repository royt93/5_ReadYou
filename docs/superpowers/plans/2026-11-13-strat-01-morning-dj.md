# STRAT-01 Bản tin sáng Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bản tin sáng (CommuteCast) hỏi đồng ý riêng trước khi gửi bài lên Gemini, cho người dùng chọn giờ nghe (soạn trước 30 phút), và có công tắc tự phát khi cắm tai nghe Bluetooth (bị chặn thì hiện thông báo có nút Phát).

**Architecture:** Ba luật thuần (`CommuteSchedulePolicy`, `CommuteAutoPlayPolicy`, cổng consent là tham số `allowGemini`) được test kỹ trên JVM; phần Android mỏng bọc quanh: DataStore (khóa mới, **không đổi Room**), `CommuteWorker` (đổi giờ → `UPDATE`), `BluetoothAudioReceiver` (manifest, `ACTION_ACL_CONNECTED`), thông báo có nút Phát, và các dòng cài đặt trong `PrivacySettingsPage`.

**Tech Stack:** Kotlin 2.1, WorkManager, DataStore, Hilt, Compose M3, Robolectric/JUnit4/MockK, `java.time`.

## Global Constraints

- Nhánh `dev` only; không worktree, không push, không PR; không commit `.idea`.
- Spec: `docs/superpowers/specs/2026-11-13-strat-01-morning-dj-design.md` (commit `a213e34f`). Task gốc `doc/task/todo/STRAT-01_zero-click-autonomous-podcast-dj.md`.
- **Room GIỮ v22.** Không migration, không dependency mới, không quảng cáo/IAP/tiền, không đụng `DJ-04` (quảng cáo Deep Dive).
- **Không điều kiện sạc/pin, không báo thức chính xác, không tự phát từ khởi động máy.**
- Mặc định: giờ nghe `07:00` (`DEFAULT_LISTEN_MINUTE_OF_DAY = 420`), soạn trước `PREPARE_LEAD_MINUTES = 30`; cửa sổ tự phát `AUTO_PLAY_WINDOW_MS = 2 giờ`; tối đa `AUTO_PLAY_MAX_PER_DAY = 1`; công tắc tự phát mặc định **TẮT**; consent Gemini mặc định **chưa đồng ý** (chưa đồng ý ⇒ soạn trong máy, **không request mạng, không đọc key**).
- Có **hai** nơi gọi `CommuteScriptService.generateScript` (`CommuteEpisodePreparer` của Worker và `CommuteCastViewModel`): **cả hai** phải qua cổng consent.
- `CommuteWorker.enqueueDailyWork` hiện dùng `ExistingPeriodicWorkPolicy.KEEP` và cố định 06:00: đổi giờ phải đặt lại lịch. **`ExistingPeriodicWorkPolicy.UPDATE` KHÔNG có trong WorkManager `2.8.0-alpha02` của dự án** (đã kiểm bằng `javap`: chỉ có `REPLACE` và `KEEP`): dùng `REPLACE` khi người dùng đổi giờ, giữ `KEEP` khi app khởi động (để mỗi lần mở app không đặt lại lịch). Không nâng WorkManager (không dependency mới).
- Tự phát chỉ là "cố gắng": bắt `ForegroundServiceStartNotAllowedException` (và mọi `Exception`, **không** nuốt `CancellationException`) rồi hiện thông báo nút Phát. Đánh dấu "đã tự phát hôm nay" **sau** khi phát thành công.
- Quyền `BLUETOOTH_CONNECT` chỉ xin khi người dùng **bật** công tắc tự phát; từ chối ⇒ công tắc về TẮT.
- Chuỗi mới ×38 locale (EN và VI viết tay, 36 bản tiếng Anh) bằng **một** script all-or-none vào thư mục locale thật (ví dụ `values-de-rDE`); kiểm lại số khóa; không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` xanh.
- R5: không magic number (hằng có tên), không `late`/`!!` không lý do, hủy mọi job/receiver, mỗi tính năng có test.
- TDD thật: test đỏ vì **đúng lý do** (đọc từ XML **sau khi** `.exit` tồn tại), code tối thiểu, xanh, mutation có backup + `cmp`, commit nhỏ sau khi kiểm exit code.
- Gradle chạy **nền**, đầu ra ra tệp, chờ `.exit`/`ALLDONE`; không đọc XML cũ; sau mỗi đợt mutation kiểm tệp đã khôi phục bằng `cmp`.
- Đổi constructor Hilt thì **clean rebuild**: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`.
- Môi trường shell: zsh nội suy `$` trong nháy kép (dùng nháy đơn); không `echo ====`; `grep -r` đặt mẫu trong nháy; `sed -i` macOS cần `''` nên **sửa tệp bằng python**; `org.json` là stub trên JVM thuần (cần Robolectric); test nguồn chỉ xét mã (bỏ comment và `import`); mỗi test Compose **một** `setContent`; hai luồng Room cập nhật riêng nên test chờ trạng thái cuối; khi cần dữ liệu lớn thì chèn trực tiếp, đừng dùng API bị giới hạn.
- **Thiết bị: CHỈ Tecno KJ7 `115333744A005844`** (owner: "chỉ dùng tecno cho mọi phiên"); **không chạm Pixel** `2B051FDH3006MU` hay máy nào khác. Cổng thiết bị chỉ đọc trước (`adb devices`, `dumpsys activity` tìm instrumentation và `topResumedActivity`, `ps`); dừng và hỏi nếu màn hình khóa, có hộp sinh trắc, hoặc app lạ ở foreground. Sao lưu byte-exact Room/`-shm`/`-wal`/`settings.preferences_pb`/`shared_prefs/*.xml` (`/tmp/o03p-backup.py` và `/tmp/o03p-restore.py` đã trỏ KJ7). Không `connected*` (dùng `adb -s … shell am instrument` với `timeout`); chỉ runner `com.mckimquyen.reader.test`; chỉ force-stop `com.mckimquyen.reader`; `run-as` không `--user`; không xóa DataStore.
- Quyền thông báo và Bluetooth của app trên KJ7 đang **tắt**: chỉ cấp tạm bằng `pm grant` **khi owner cho phép**, và thu hồi sau.
- Quảng cáo toàn màn hình khi mở app: chỉ **Back** (chờ rồi Back nhiều lần, kiểm `topResumedActivity` mỗi lần), không bấm "Continue to app", không cài gì.
- Không Gemini thật (key chung hỏng, FIX-09 hoãn); ghi thẳng điều này vào báo cáo.
- R2: cập nhật `doc/feature.md`. Task gốc: hỏi owner (AskUserQuestion) giữ trong `todo/` hay chuyển `done/`.

---

## Cấu trúc file

| Tệp | Việc | Task |
|---|---|---|
| `app/src/main/java/com/mckimquyen/reader/domain/commute/CommuteSchedulePolicy.kt` (mới) | luật thuần: giờ nghe → độ trễ soạn kế tiếp | 1 |
| `app/src/main/java/com/mckimquyen/reader/domain/commute/CommuteAutoPlayPolicy.kt` (mới) | luật thuần: có tự phát / hiện nút Phát / bỏ qua | 2 |
| `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` | 4 khóa mới + hàm đọc/ghi | 3 |
| `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteScriptService.kt` | tham số `allowGemini` | 4 |
| `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteEpisodePreparer.kt`, `ui/component/commute/CommuteCastViewModel.kt` | truyền consent | 4 |
| `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt`, `RApp.kt` | giờ nghe + `UPDATE` | 5 |
| `app/src/main/res/values*/strings.xml` (38) | chuỗi `commute_*` mới | 6 |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt` | thông báo có nút Phát | 7 |
| `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/BluetoothAudioReceiver.kt` (mới), `AndroidManifest.xml` | nhận biết tai nghe, tự phát | 8 |
| `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt`, `PrivacySettingsPage.kt` | consent, giờ nghe, công tắc, xin quyền | 9 |
| `app/src/androidTest/.../CommuteMorningSettingsWidgetTest.kt` (mới), báo cáo, docs | máy thật | 10 |

**Thứ tự thực thi:** 1 → 2 → 3 → 4 → 5 → **6 (chuỗi)** → 7 → 8 → 9 → 10. Chuỗi ở Task 6 phải xong trước Task 7 và 9 (thông báo và UI đọc chuỗi).

---

### Task 1: Luật lịch `CommuteSchedulePolicy` (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/commute/CommuteSchedulePolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/commute/CommuteSchedulePolicyTest.kt`

**Interfaces:**
- Consumes: không.
- Produces: `object CommuteSchedulePolicy { DEFAULT_LISTEN_MINUTE_OF_DAY = 420; PREPARE_LEAD_MINUTES = 30; MINUTES_PER_DAY = 1440; fun prepareMinuteOfDay(listenMinuteOfDay: Int): Int; fun initialDelayMillis(nowMillis: Long, listenMinuteOfDay: Int, zone: java.time.ZoneId): Long; fun clampListenMinute(raw: Int?): Int }`.

Quy tắc:
- `clampListenMinute(null)` = `DEFAULT_LISTEN_MINUTE_OF_DAY`; giá trị ngoài `0..1439` cũng trả về mặc định (không bao giờ ném lỗi, vì giá trị đến từ DataStore).
- `prepareMinuteOfDay(listen)` = `(listen - 30)` **modulo 1440** (07:00 → 06:30; 00:10 → 23:40 hôm trước).
- `initialDelayMillis(now, listen, zone)`: thời điểm soạn kế tiếp **lớn hơn hoặc bằng** `now` theo giờ địa phương của `zone`. Nếu giờ soạn hôm nay đã qua thì lấy ngày mai. Dùng `java.time` (`ZonedDateTime`), **không** cộng 24 giờ cứng, để đổi giờ mùa hè vẫn đúng. Kết quả luôn `>= 0`. Khi giờ soạn là 23:40 của hôm trước thì "hôm nay" là ngày của `now` (ví dụ nghe 00:10 ⇒ soạn lúc 23:40).

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.commute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CommuteSchedulePolicyTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = utc) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(420, CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY)
        assertEquals(30, CommuteSchedulePolicy.PREPARE_LEAD_MINUTES)
        assertEquals(1440, CommuteSchedulePolicy.MINUTES_PER_DAY)
    }

    @Test fun prepareMinute_isThirtyMinutesBeforeTheListenTime() {
        assertEquals(6 * 60 + 30, CommuteSchedulePolicy.prepareMinuteOfDay(7 * 60))
    }

    @Test fun prepareMinute_wrapsBackwardsAcrossMidnight() {
        assertEquals(23 * 60 + 40, CommuteSchedulePolicy.prepareMinuteOfDay(10))
        assertEquals(23 * 60 + 30, CommuteSchedulePolicy.prepareMinuteOfDay(0))
    }

    @Test fun clamp_nullAndOutOfRange_fallBackToTheDefault() {
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(null))
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(-1))
        assertEquals(420, CommuteSchedulePolicy.clampListenMinute(1440))
    }

    @Test fun clamp_validValues_areKept() {
        assertEquals(0, CommuteSchedulePolicy.clampListenMinute(0))
        assertEquals(1439, CommuteSchedulePolicy.clampListenMinute(1439))
        assertEquals(480, CommuteSchedulePolicy.clampListenMinute(480))
    }

    @Test fun delay_beforeTodaysPrepareTime_isTheGapToToday() {
        val now = at(2026, 11, 13, 5, 0)
        assertEquals(90 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_afterTodaysPrepareTime_rollsToTomorrow() {
        val now = at(2026, 11, 13, 6, 31)
        assertEquals(23 * hour + 59 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_exactlyAtThePrepareTime_isZero() {
        val now = at(2026, 11, 13, 6, 30)
        assertEquals(0L, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, utc))
    }

    @Test fun delay_listenJustAfterMidnight_preparesTheEveningBefore() {
        val now = at(2026, 11, 13, 22, 0)
        assertEquals(100 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 10, utc))
    }

    @Test fun delay_isNeverNegative_andNeverMoreThanADayPlusAnHour() {
        for (m in 0 until 1440 step 7) {
            val d = CommuteSchedulePolicy.initialDelayMillis(at(2026, 11, 13, 12, 0), m, utc)
            assertTrue("listen=$m delay=$d", d >= 0 && d <= 25 * hour)
        }
    }

    @Test fun delay_usesTheZone_notUtc() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at(2026, 11, 13, 5, 0, tokyo)
        assertEquals(90 * minute, CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, tokyo))
    }

    @Test fun delay_acrossTheSpringForwardGap_landsOnTheWallClockTime() {
        // New York, 2026-03-08: 02:00 jumps to 03:00. Listening at 07:00 prepares at 06:30 local.
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 3, 8, 0, 30, ny)
        val delay = CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, ny)
        assertEquals(at(2026, 3, 8, 6, 30, ny), now + delay)
    }

    @Test fun delay_acrossTheFallBack_landsOnTheWallClockTime() {
        // New York, 2026-11-01: 02:00 falls back to 01:00 (a 25 hour day).
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 11, 1, 0, 30, ny)
        val delay = CommuteSchedulePolicy.initialDelayMillis(now, 7 * 60, ny)
        assertEquals(at(2026, 11, 1, 6, 30, ny), now + delay)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (nền): `./gradlew :app:testDevDebugUnitTest --tests '*CommuteSchedulePolicyTest' > /tmp/s01-t1-red.log 2>&1; echo $? > /tmp/s01-t1.exit`. Chờ `.exit`; đọc `grep -E '^e: '`: `Unresolved reference 'CommuteSchedulePolicy'`.

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.commute

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the morning episode is prepared (STRAT-01). The user picks the time they want to LISTEN; the app prepares it a
 * fixed lead earlier. Pure: no Android, so every branch is testable.
 */
object CommuteSchedulePolicy {
    const val DEFAULT_LISTEN_MINUTE_OF_DAY = 420
    const val PREPARE_LEAD_MINUTES = 30
    const val MINUTES_PER_DAY = 1440
    private const val MINUTES_PER_HOUR = 60

    /** DataStore can hold anything; a bad or missing value must never break scheduling. */
    fun clampListenMinute(raw: Int?): Int =
        if (raw == null || raw !in 0 until MINUTES_PER_DAY) DEFAULT_LISTEN_MINUTE_OF_DAY else raw

    fun prepareMinuteOfDay(listenMinuteOfDay: Int): Int =
        Math.floorMod(listenMinuteOfDay - PREPARE_LEAD_MINUTES, MINUTES_PER_DAY)

    /** Milliseconds from [nowMillis] to the next prepare time on the wall clock of [zone]; never negative. */
    fun initialDelayMillis(nowMillis: Long, listenMinuteOfDay: Int, zone: ZoneId): Long {
        val prepare = prepareMinuteOfDay(clampListenMinute(listenMinuteOfDay))
        val time = LocalTime.of(prepare / MINUTES_PER_HOUR, prepare % MINUTES_PER_HOUR)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var target = now.toLocalDate().atTime(time).atZone(zone)
        if (target.toInstant().toEpochMilli() < nowMillis) target = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return target.toInstant().toEpochMilli() - nowMillis
    }
}
```

- [ ] **Step 4: Chạy xanh** (đọc XML **sau** `.exit`): 13 test, 0 lỗi.
- [ ] **Step 5: Mutation** (backup + `cmp`, qua `/tmp/s01_mut.sh`, chạy cả đợt một script nền, chờ `ALLDONE`): `PREPARE_LEAD_MINUTES = 30` → `45`; `Math.floorMod(...)` → `(listenMinuteOfDay - PREPARE_LEAD_MINUTES)` (đỏ ở qua nửa đêm); `target.toInstant().toEpochMilli() < nowMillis` → `<=` (đỏ ở `delay_exactlyAtThePrepareTime_isZero`: đúng giờ soạn thì phải trả 0, không phải 24 giờ); bỏ `plusDays(1)` (đỏ); thay `zone` bằng `ZoneOffset.UTC` (đỏ ở Tokyo); thay `java.time` bằng `+ 24 * 3600_000L` (đỏ ở đổi giờ mùa hè); bỏ nhánh `raw !in 0 until` (đỏ).
- [ ] **Step 6: Commit** (kiểm `exit=0`): `feat(strat-01): add the pure rule that turns a listen time into the next prepare time`.

---

### Task 2: Luật tự phát `CommuteAutoPlayPolicy` (thuần)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/commute/CommuteAutoPlayPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/commute/CommuteAutoPlayPolicyTest.kt`

**Interfaces:**
- Consumes: không.
- Produces:
  - `data class AutoPlayInput(val switchOn: Boolean, val episodeDateMillis: Long?, val episodePlayed: Boolean, val nowMillis: Long, val lastAutoPlayEpochDay: Long?, val isAudioDevice: Boolean, val otherAudioPlaying: Boolean)`.
  - `sealed interface AutoPlayDecision { data object PlayNow; data object OfferPlayButton; data class Skip(val reason: SkipReason) }`, `enum class SkipReason { SWITCH_OFF, NO_EPISODE, EPISODE_PLAYED, EPISODE_TOO_OLD, ALREADY_AUTO_PLAYED_TODAY, NOT_AN_AUDIO_DEVICE }`.
  - `object CommuteAutoPlayPolicy { AUTO_PLAY_WINDOW_MS = 2 * 60 * 60 * 1000L; MILLIS_PER_DAY = 86_400_000L; fun epochDayUtc(millis: Long): Long; fun decide(input: AutoPlayInput): AutoPlayDecision }`.

Thứ tự kiểm (đầu tiên khớp thắng): công tắc tắt → `SWITCH_OFF`; không có episode → `NO_EPISODE`; đã nghe → `EPISODE_PLAYED`; `now - episodeDate > 2 giờ` → `EPISODE_TOO_OLD` (đúng 2 giờ **vẫn còn** trong cửa sổ; `episodeDate > now` thì tính là tuổi 0, tức hợp lệ); `lastAutoPlayEpochDay == epochDayUtc(now)` → `ALREADY_AUTO_PLAYED_TODAY`; không phải thiết bị âm thanh → `NOT_AN_AUDIO_DEVICE`; đang phát audio khác → `OfferPlayButton`; còn lại → `PlayNow`. `epochDayUtc` dùng `Math.floorDiv(millis, MILLIS_PER_DAY)`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.commute

import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteAutoPlayPolicyTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val today = CommuteAutoPlayPolicy.epochDayUtc(now)

    private fun input(
        switchOn: Boolean = true,
        episodeDateMillis: Long? = now - hour,
        episodePlayed: Boolean = false,
        lastAutoPlayEpochDay: Long? = null,
        isAudioDevice: Boolean = true,
        otherAudioPlaying: Boolean = false,
    ) = AutoPlayInput(switchOn, episodeDateMillis, episodePlayed, now, lastAutoPlayEpochDay, isAudioDevice, otherAudioPlaying)

    private fun decide(i: AutoPlayInput) = CommuteAutoPlayPolicy.decide(i)

    @Test fun constants_areTheSpecifiedOnes() {
        assertEquals(2 * hour, CommuteAutoPlayPolicy.AUTO_PLAY_WINDOW_MS)
        assertEquals(86_400_000L, CommuteAutoPlayPolicy.MILLIS_PER_DAY)
    }

    @Test fun everythingRight_playsNow() = assertEquals(AutoPlayDecision.PlayNow, decide(input()))

    @Test fun switchOff_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.SWITCH_OFF), decide(input(switchOn = false)))

    @Test fun noEpisode_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.NO_EPISODE), decide(input(episodeDateMillis = null)))

    @Test fun alreadyPlayedEpisode_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.EPISODE_PLAYED), decide(input(episodePlayed = true)))

    @Test fun exactlyTwoHoursOld_stillInTheWindow() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(episodeDateMillis = now - 2 * hour)))

    @Test fun oneMillisecondOverTwoHours_isTooOld() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD), decide(input(episodeDateMillis = now - 2 * hour - 1)))

    @Test fun anEpisodeDatedInTheFuture_isNotTooOld() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(episodeDateMillis = now + hour)))

    @Test fun alreadyAutoPlayedToday_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.ALREADY_AUTO_PLAYED_TODAY), decide(input(lastAutoPlayEpochDay = today)))

    @Test fun autoPlayedYesterday_doesNotBlockToday() =
        assertEquals(AutoPlayDecision.PlayNow, decide(input(lastAutoPlayEpochDay = today - 1)))

    @Test fun notAnAudioDevice_skips() =
        assertEquals(AutoPlayDecision.Skip(SkipReason.NOT_AN_AUDIO_DEVICE), decide(input(isAudioDevice = false)))

    @Test fun otherAudioPlaying_offersTheButton_insteadOfInterrupting() =
        assertEquals(AutoPlayDecision.OfferPlayButton, decide(input(otherAudioPlaying = true)))

    @Test fun theUtcDayRollsOverAtMidnightUtc() {
        val justBefore = 20_000L * CommuteAutoPlayPolicy.MILLIS_PER_DAY - 1
        val justAfter = 20_000L * CommuteAutoPlayPolicy.MILLIS_PER_DAY
        assertEquals(19_999L, CommuteAutoPlayPolicy.epochDayUtc(justBefore))
        assertEquals(20_000L, CommuteAutoPlayPolicy.epochDayUtc(justAfter))
    }

    @Test fun epochDayUtc_ofNegativeMillis_usesFloorDivision() =
        assertEquals(-1L, CommuteAutoPlayPolicy.epochDayUtc(-1L))

    // The order matters: the cheaper, more fundamental reasons are reported first.
    @Test fun switchOff_winsOverEverythingElse() =
        assertEquals(
            AutoPlayDecision.Skip(SkipReason.SWITCH_OFF),
            decide(input(switchOn = false, episodeDateMillis = null, isAudioDevice = false, lastAutoPlayEpochDay = today)),
        )

    @Test fun anOldEpisode_isReportedBeforeAHeadsetThatIsNotAudio() =
        assertEquals(
            AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD),
            decide(input(episodeDateMillis = now - 3 * hour, isAudioDevice = false)),
        )
}
```

- [ ] **Step 2: Chạy đỏ** (`Unresolved reference`), đọc sau `.exit`.

- [ ] **Step 3: Code tối thiểu**

```kotlin
package com.mckimquyen.reader.domain.commute

data class AutoPlayInput(
    val switchOn: Boolean,
    val episodeDateMillis: Long?,
    val episodePlayed: Boolean,
    val nowMillis: Long,
    val lastAutoPlayEpochDay: Long?,
    val isAudioDevice: Boolean,
    val otherAudioPlaying: Boolean,
)

enum class SkipReason { SWITCH_OFF, NO_EPISODE, EPISODE_PLAYED, EPISODE_TOO_OLD, ALREADY_AUTO_PLAYED_TODAY, NOT_AN_AUDIO_DEVICE }

sealed interface AutoPlayDecision {
    data object PlayNow : AutoPlayDecision
    data object OfferPlayButton : AutoPlayDecision
    data class Skip(val reason: SkipReason) : AutoPlayDecision
}

/**
 * Decides what to do when a Bluetooth audio device connects (STRAT-01). Pure and ordered: the first rule that matches
 * wins. Playing sound unasked is intrusive, so every guard errs towards doing nothing or only offering a button.
 */
object CommuteAutoPlayPolicy {
    private const val HOURS = 2L
    private const val MILLIS_PER_HOUR = 3_600_000L
    const val AUTO_PLAY_WINDOW_MS = HOURS * MILLIS_PER_HOUR
    const val MILLIS_PER_DAY = 86_400_000L

    fun epochDayUtc(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY)

    fun decide(input: AutoPlayInput): AutoPlayDecision {
        if (!input.switchOn) return AutoPlayDecision.Skip(SkipReason.SWITCH_OFF)
        val date = input.episodeDateMillis ?: return AutoPlayDecision.Skip(SkipReason.NO_EPISODE)
        if (input.episodePlayed) return AutoPlayDecision.Skip(SkipReason.EPISODE_PLAYED)
        if (input.nowMillis - date > AUTO_PLAY_WINDOW_MS) return AutoPlayDecision.Skip(SkipReason.EPISODE_TOO_OLD)
        if (input.lastAutoPlayEpochDay == epochDayUtc(input.nowMillis)) {
            return AutoPlayDecision.Skip(SkipReason.ALREADY_AUTO_PLAYED_TODAY)
        }
        if (!input.isAudioDevice) return AutoPlayDecision.Skip(SkipReason.NOT_AN_AUDIO_DEVICE)
        if (input.otherAudioPlaying) return AutoPlayDecision.OfferPlayButton
        return AutoPlayDecision.PlayNow
    }
}
```

- [ ] **Step 4: Chạy xanh** (16 test).
- [ ] **Step 5: Mutation:** `> AUTO_PLAY_WINDOW_MS` → `>=` (đỏ ở "đúng 2 giờ"); `HOURS = 2L` → `3L`; bỏ nhánh `!input.switchOn`; hoán đổi nhánh `episodePlayed` và `EPISODE_TOO_OLD` (nếu sống sót thì **thêm** test "đã nghe và cũng quá cũ thì báo `EPISODE_PLAYED`" rồi chạy lại mutation đó); `==` → `!=` ở ngày tự phát; `Math.floorDiv` → `/` (đỏ ở số âm); bỏ nhánh `otherAudioPlaying`; `OfferPlayButton` → `PlayNow`.
- [ ] **Step 6: Commit:** `feat(strat-01): add the pure rule that decides auto-play, a play button, or nothing`.

---

### Task 3: Bốn khóa DataStore mới (consent, giờ nghe, công tắc tự phát, ngày tự phát cuối) và cờ "đã nghe"

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` (hàm đọc/ghi cạnh `oracleRemindedBets`; khóa trong `DataStoreKeys` cạnh `OracleRemindedBets`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteEpisodeStore.kt` (cờ `played`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/ext/CommuteMorningDataStoreTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommuteEpisodeStorePlayedTest.kt`

**Interfaces:**
- Consumes: `DataStoreKeys<T>`, `DataStore<Preferences>.get/put` (đã có); `CommuteSchedulePolicy` (Task 1).
- Produces:
  - `DataStoreKeys.CommuteScriptGeminiConsent` (`booleanPreferencesKey("commute_script_gemini_consent")`), `Context.commuteScriptGeminiConsent: Boolean?`, `suspend Context.setCommuteScriptGeminiConsent(agreed: Boolean)`.
  - `DataStoreKeys.CommuteListenMinuteOfDay` (`intPreferencesKey("commute_listen_minute_of_day")`), `Context.commuteListenMinuteOfDay: Int` (**đã qua** `CommuteSchedulePolicy.clampListenMinute`), `suspend Context.setCommuteListenMinuteOfDay(minute: Int)`.
  - `DataStoreKeys.CommuteAutoPlayOnHeadset` (`booleanPreferencesKey("commute_auto_play_on_headset")`), `Context.commuteAutoPlayOnHeadset: Boolean` (null ⇒ **false**), `suspend Context.setCommuteAutoPlayOnHeadset(on: Boolean)`.
  - `DataStoreKeys.CommuteLastAutoPlayEpochDay` (`longPreferencesKey("commute_last_auto_play_epoch_day")`), `Context.commuteLastAutoPlayEpochDay: Long?`, `suspend Context.setCommuteLastAutoPlayEpochDay(day: Long)`.
  - `CommuteEpisodeStore.markPlayed(episodeId: String)` và `suspend fun isPlayed(episodeId: String): Boolean` (cờ ở SharedPreferences cùng tệp với episode, khóa `played_episode_id`; chỉ **một** id được nhớ, id khác ⇒ chưa nghe).

- [ ] **Step 1: Viết test đỏ — DataStore**

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
class CommuteMorningDataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking { context.dataStore.edit { it.clear() } }

    @Test fun consent_neverAsked_isNull_notFalse() = assertEquals(null, context.commuteScriptGeminiConsent)

    @Test fun consent_roundTrips(): Unit = runBlocking {
        context.setCommuteScriptGeminiConsent(true)
        assertEquals(true, context.commuteScriptGeminiConsent)
        context.setCommuteScriptGeminiConsent(false)
        assertEquals(false, context.commuteScriptGeminiConsent)
    }

    @Test fun listenMinute_default_is0700() = assertEquals(420, context.commuteListenMinuteOfDay)

    @Test fun listenMinute_roundTrips(): Unit = runBlocking {
        context.setCommuteListenMinuteOfDay(8 * 60 + 15)
        assertEquals(495, context.commuteListenMinuteOfDay)
    }

    @Test fun listenMinute_aCorruptStoredValue_fallsBackToTheDefault(): Unit = runBlocking {
        context.dataStore.edit { it[DataStoreKeys.CommuteListenMinuteOfDay.key] = 99_999 }
        assertEquals(420, context.commuteListenMinuteOfDay)
    }

    @Test fun autoPlaySwitch_defaultsToOff() = assertEquals(false, context.commuteAutoPlayOnHeadset)

    @Test fun autoPlaySwitch_roundTrips(): Unit = runBlocking {
        context.setCommuteAutoPlayOnHeadset(true)
        assertEquals(true, context.commuteAutoPlayOnHeadset)
    }

    @Test fun lastAutoPlayDay_neverWritten_isNull() = assertEquals(null, context.commuteLastAutoPlayEpochDay)

    @Test fun lastAutoPlayDay_roundTrips(): Unit = runBlocking {
        context.setCommuteLastAutoPlayEpochDay(20_000L)
        assertEquals(20_000L, context.commuteLastAutoPlayEpochDay)
    }

    @Test fun keyNames_areHardCoded_andAllDistinct() {
        val names = listOf(
            DataStoreKeys.CommuteScriptGeminiConsent.key.name,
            DataStoreKeys.CommuteListenMinuteOfDay.key.name,
            DataStoreKeys.CommuteAutoPlayOnHeadset.key.name,
            DataStoreKeys.CommuteLastAutoPlayEpochDay.key.name,
        )
        assertEquals(
            listOf("commute_script_gemini_consent", "commute_listen_minute_of_day", "commute_auto_play_on_headset", "commute_last_auto_play_epoch_day"),
            names,
        )
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun theNewConsent_isIndependentOfEveryOtherAiConsent(): Unit = runBlocking {
        context.setOracleClaimsGeminiConsent(true)
        context.setQuizGeminiConsent(true)
        context.setDeepDiveGeminiConsent(true)
        context.setPodcastStudioGeminiConsent(true)
        assertEquals("agreeing to other AI features must not agree to this one", null, context.commuteScriptGeminiConsent)
        assertNotEquals(DataStoreKeys.OracleClaimsGeminiConsent.key.name, DataStoreKeys.CommuteScriptGeminiConsent.key.name)
    }
}
```

- [ ] **Step 2: Viết test đỏ — cờ "đã nghe"** (Robolectric; cờ nằm cạnh episode)

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteEpisodeStorePlayedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: CommuteEpisodeStore

    @Before
    fun setUp() {
        store = CommuteEpisodeStore(context, Gson(), Dispatchers.Unconfined)
        runBlocking { store.clear() }
    }

    @Test fun nothingMarked_isNotPlayed() = runBlocking { assertFalse(store.isPlayed("a")) }

    @Test fun markedEpisode_isPlayed() = runBlocking {
        store.markPlayed("a")
        assertTrue(store.isPlayed("a"))
    }

    @Test fun anotherEpisode_isNotPlayed_evenAfterOneWasMarked() = runBlocking {
        store.markPlayed("a")
        assertFalse(store.isPlayed("b"))
    }

    @Test fun markingANewEpisode_forgetsTheOldOne() = runBlocking {
        store.markPlayed("a")
        store.markPlayed("b")
        assertFalse(store.isPlayed("a"))
        assertTrue(store.isPlayed("b"))
    }

    @Test fun clear_forgetsThePlayedFlag() = runBlocking {
        store.markPlayed("a")
        store.clear()
        assertFalse(store.isPlayed("a"))
    }

    @Test fun savingANewEpisode_doesNotMarkItPlayed() = runBlocking {
        store.markPlayed("old")
        store.save(com.mckimquyen.reader.domain.model.commute.CommuteEpisode(
            id = "new", title = "t",
            dialogues = listOf(com.mckimquyen.reader.domain.model.commute.CommuteDialogue(com.mckimquyen.reader.domain.model.commute.CommuteSpeaker.ALEX, "hi")),
        ))
        assertFalse(store.isPlayed("new"))
    }
}
```

- [ ] **Step 3: Chạy đỏ** (nền, chờ `.exit`; biên dịch lỗi: `commuteScriptGeminiConsent`, `CommuteScriptGeminiConsent`, `markPlayed`, `isPlayed` chưa có).

- [ ] **Step 4: Code tối thiểu**

`ExtDataStore.kt` — sau `setOracleRemindedBets`:

```kotlin
/** STRAT-01: true only when the user agreed that the morning episode may be written with Gemini; null = never asked. */
val Context.commuteScriptGeminiConsent: Boolean?
    get() = this.dataStore.get(DataStoreKeys.CommuteScriptGeminiConsent)

suspend fun Context.setCommuteScriptGeminiConsent(agreed: Boolean) {
    this.dataStore.put(DataStoreKeys.CommuteScriptGeminiConsent, agreed)
}

val Context.commuteListenMinuteOfDay: Int
    get() = CommuteSchedulePolicy.clampListenMinute(this.dataStore.get(DataStoreKeys.CommuteListenMinuteOfDay))

suspend fun Context.setCommuteListenMinuteOfDay(minute: Int) {
    this.dataStore.put(DataStoreKeys.CommuteListenMinuteOfDay, minute)
}

val Context.commuteAutoPlayOnHeadset: Boolean
    get() = this.dataStore.get(DataStoreKeys.CommuteAutoPlayOnHeadset) ?: false

suspend fun Context.setCommuteAutoPlayOnHeadset(on: Boolean) {
    this.dataStore.put(DataStoreKeys.CommuteAutoPlayOnHeadset, on)
}

val Context.commuteLastAutoPlayEpochDay: Long?
    get() = this.dataStore.get(DataStoreKeys.CommuteLastAutoPlayEpochDay)

suspend fun Context.setCommuteLastAutoPlayEpochDay(day: Long) {
    this.dataStore.put(DataStoreKeys.CommuteLastAutoPlayEpochDay, day)
}
```

và `import com.mckimquyen.reader.domain.commute.CommuteSchedulePolicy`. Trong `sealed class DataStoreKeys`, sau `OracleRemindedBets`:

```kotlin
    /** STRAT-01: separate from every other AI consent; absent means the user was never asked. */
    object CommuteScriptGeminiConsent : DataStoreKeys<Boolean>() {
        override val key: Preferences.Key<Boolean>
            get() = booleanPreferencesKey("commute_script_gemini_consent")
    }

    object CommuteListenMinuteOfDay : DataStoreKeys<Int>() {
        override val key: Preferences.Key<Int>
            get() = intPreferencesKey("commute_listen_minute_of_day")
    }

    object CommuteAutoPlayOnHeadset : DataStoreKeys<Boolean>() {
        override val key: Preferences.Key<Boolean>
            get() = booleanPreferencesKey("commute_auto_play_on_headset")
    }

    object CommuteLastAutoPlayEpochDay : DataStoreKeys<Long>() {
        override val key: Preferences.Key<Long>
            get() = longPreferencesKey("commute_last_auto_play_epoch_day")
    }
```

(thêm `import androidx.datastore.preferences.core.longPreferencesKey` nếu chưa có.)

`CommuteEpisodeStore.kt` — thêm hằng `KEY_PLAYED_ID = "played_episode_id"` vào `companion object` (cạnh `KEY_EPISODE`), và hàm:

```kotlin
    /** Remembers that the user heard [episodeId]; only the latest one is kept. */
    suspend fun markPlayed(episodeId: String): Unit = withContext(ioDispatcher) {
        prefs.edit().putString(KEY_PLAYED_ID, episodeId).commit()
    }

    suspend fun isPlayed(episodeId: String): Boolean = withContext(ioDispatcher) {
        prefs.getString(KEY_PLAYED_ID, null) == episodeId
    }
```

và trong `clear()` thêm `.remove(KEY_PLAYED_ID)`.

- [ ] **Step 5: Chạy xanh** (11 + 6 test).
- [ ] **Step 6: Mutation:** đổi từng chuỗi tên khóa (đỏ ở `keyNames_...`); `?: false` → `?: true` ở công tắc (đỏ ở "mặc định tắt"); bỏ `clampListenMinute` ở `commuteListenMinuteOfDay` (đỏ ở "giá trị hỏng"); `commuteScriptGeminiConsent` đọc nhầm `OracleClaimsGeminiConsent` (đỏ ở "độc lập"); bỏ `.remove(KEY_PLAYED_ID)` ở `clear` (đỏ); `==` → `!=` ở `isPlayed` (đỏ).
- [ ] **Step 7: Commit:** `feat(strat-01): add the morning DJ settings keys and an episode played flag`.

---

### Task 4: Cổng consent: `allowGemini` ở `CommuteScriptService`, truyền từ cả hai nơi gọi

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteScriptService.kt` (`generateScript(articles, isDeepDive, languageTag, allowGemini)`)
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteEpisodePreparer.kt` (constructor thêm `@ApplicationContext context`, đọc consent)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastViewModel.kt` (đọc consent)
- Modify (test sẵn có, cho khớp): `CommuteEpisodePreparerTest.kt`, `CommuteCastViewModelTest.kt`
- Test mới: `app/src/test/java/com/mckimquyen/reader/domain/sv/CommuteScriptConsentGateTest.kt`, `app/src/test/java/com/mckimquyen/reader/domain/sv/CommuteConsentWiringSourceTest.kt`

**Interfaces:**
- Consumes: `Context.commuteScriptGeminiConsent` (Task 3).
- Produces: `suspend fun generateScript(articles: List<Article>, isDeepDive: Boolean = false, languageTag: String = Locale.getDefault().toLanguageTag(), allowGemini: Boolean = false): CommuteEpisode` — **mặc định `false`** (an toàn khi quên truyền). `allowGemini == false` ⇒ **không** đọc `GeminiConfig.API_KEYS`, **không** gọi `okHttpClient`, đi thẳng `generateHeuristicScript`. `CommuteEpisodePreparer(context, scriptService, episodeStore, audioPlayer, notificationHelper)`: gọi `scriptService.generateScript(articles, isDeepDive = isDeepDive, allowGemini = context.commuteScriptGeminiConsent == true)`. Trong `CommuteCastViewModel` cùng biểu thức (đọc trên `Dispatchers.IO`, vì `get` chặn).

- [ ] **Step 1: Viết test đỏ — cổng**

```kotlin
package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteScriptConsentGateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun article(i: Int) = Article(
        id = "a$i", date = Date(), title = "Title $i", rawDescription = "Raw $i",
        shortDescription = "Short $i", link = "https://example.invalid/$i", feedId = "f", accountId = 1,
    )

    private val articles = (1..3).map(::article)

    @Test fun withoutConsent_noRequestLeavesTheDevice() {
        val http = mockk<OkHttpClient>()
        val service = CommuteScriptService(context, http)
        val episode = runBlocking { service.generateScript(articles, allowGemini = false) }
        verify(exactly = 0) { http.newCall(any()) }
        assertTrue("the on-device script must still be a real episode", episode.dialogues.isNotEmpty())
    }

    @Test fun theDefault_isNoConsent() {
        val http = mockk<OkHttpClient>()
        val service = CommuteScriptService(context, http)
        runBlocking { service.generateScript(articles) }
        verify(exactly = 0) { http.newCall(any()) }
    }

    @Test fun withoutConsent_theEpisodeIsTheOnDeviceOne_withTheArticleIds() {
        val service = CommuteScriptService(context, mockk<OkHttpClient>())
        val episode = runBlocking { service.generateScript(articles, allowGemini = false, languageTag = "en-US") }
        assertEquals(listOf("a1", "a2", "a3"), episode.articleIds)
        assertEquals("CommuteCast Morning Briefing", episode.title)
    }

    @Test fun withConsent_theNetworkIsTried_andAFailureStillFallsBackToTheOnDeviceScript() {
        val http = mockk<OkHttpClient>()
        io.mockk.every { http.newCall(any()) } throws java.io.IOException("offline")
        val service = CommuteScriptService(context, http)
        val episode = runBlocking { service.generateScript(articles, allowGemini = true, languageTag = "en-US") }
        assertTrue("with consent the request is attempted", runCatching { verify(atLeast = 1) { http.newCall(any()) } }.isSuccess)
        assertEquals("CommuteCast Morning Briefing", episode.title)
    }
}
```

Ghi chú: test cuối kỳ vọng ít nhất một `newCall` khi có consent; nếu `GeminiConfig.API_KEYS` rỗng trong môi trường test thì `generateScriptWithGemini` ném lỗi **trước** khi gọi mạng và test này đỏ vì lý do sai. Khi đó **đo trước** (in `GeminiConfig.API_KEYS.size` bằng một test thăm dò tạm, không commit) và nếu rỗng thì bỏ khẳng định `verify(atLeast = 1)` và đổi tên test thành `withConsent_aFailureStillFallsBack...`, **ghi lý do vào báo cáo** (không hạ khẳng định "không gọi mạng khi chưa consent", đó mới là khẳng định chính).

- [ ] **Step 2: Viết test nguồn đỏ — cả hai nơi gọi qua cổng** (chỉ xét mã không comment/import)

```kotlin
package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteConsentWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val service = code("domain/sv/CommuteScriptService.kt")
    private val preparer = code("domain/sv/CommuteEpisodePreparer.kt")
    private val viewModel = code("ui/component/commute/CommuteCastViewModel.kt")

    @Test fun theServiceDefaultsToNoGemini() {
        assertTrue(service.contains("allowGemini: Boolean = false"))
    }

    @Test fun theServiceReadsTheKeysOnlyBehindTheGate() {
        val body = service.substringAfter("suspend fun generateScript(").substringBefore("private suspend fun generateScriptWithGemini")
        assertTrue(body.contains("if (allowGemini)"))
        assertEquals("the keys are touched in exactly one place", 1, Regex("API_KEYS").findAll(service).count())
    }

    @Test fun theWorkerPathPassesTheConsent() {
        assertTrue(preparer.contains("allowGemini = context.commuteScriptGeminiConsent == true"))
    }

    @Test fun theSheetPathPassesTheConsent_offTheMainThread() {
        assertTrue(viewModel.contains("commuteScriptGeminiConsent == true"))
        assertTrue(viewModel.substringBefore("commuteScriptGeminiConsent").takeLast(200).contains("Dispatchers.IO"))
    }

    @Test fun noOtherCallerOfGenerateScriptExists() {
        val callers = File(root).walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("""generateScript\(""").containsMatchIn(it.readText()) && !it.name.startsWith("CommuteScriptService") }
            .map { it.name }.sorted().toList()
        assertEquals(listOf("CommuteCastViewModel.kt", "CommuteEpisodePreparer.kt"), callers)
    }

    @Test fun neitherCallerHardCodesTrue() {
        assertFalse(preparer.contains("allowGemini = true"))
        assertFalse(viewModel.contains("allowGemini = true"))
    }
}
```

- [ ] **Step 3: Sửa test sẵn có cho khớp**

`CommuteEpisodePreparerTest.kt`: dựng `CommuteEpisodePreparer(ApplicationProvider.getApplicationContext(), scriptService, episodeStore, audioPlayer, notificationHelper)` và các `coEvery { scriptService.generateScript(articles, any(), any()) }` thành `generateScript(articles, any(), any(), any())` (thêm một `any()` cho `allowGemini`). Thêm hai test: consent chưa có ⇒ `coVerify { scriptService.generateScript(articles, any(), any(), false) }`; sau `context.setCommuteScriptGeminiConsent(true)` ⇒ `... true)`. (Dọn DataStore trong `@Before` như `CommuteMorningDataStoreTest`.)

`CommuteCastViewModelTest.kt`: các `scriptService.generateScript(...)` mock thêm tham số thứ tư `any()`; thêm hai test tương tự (consent chưa có ⇒ `false`; có ⇒ `true`).

- [ ] **Step 4: Chạy đỏ** (lỗi biên dịch: `allowGemini`, constructor `CommuteEpisodePreparer`).

- [ ] **Step 5: Code tối thiểu**

`CommuteScriptService.generateScript`:

```kotlin
    suspend fun generateScript(
        articles: List<Article>,
        isDeepDive: Boolean = false,
        languageTag: String = Locale.getDefault().toLanguageTag(),
        // STRAT-01: sending article titles and descriptions to Gemini needs the user's own agreement; the safe default is no.
        allowGemini: Boolean = false,
    ): CommuteEpisode = withContext(Dispatchers.Default) {
        if (articles.isEmpty()) {
            return@withContext createFallbackEmptyEpisode()
        }

        val topArticles = if (isDeepDive) articles.take(10) else articles.take(5)

        if (allowGemini) {
            val aiEpisode = runCatching {
                generateScriptWithGemini(topArticles, isDeepDive, languageTag)
            }.getOrNull()
            if (aiEpisode != null && aiEpisode.dialogues.isNotEmpty()) {
                return@withContext aiEpisode
            }
        }

        // No agreement, offline or out of quota: the on-device script always works.
        return@withContext generateHeuristicScript(topArticles, isDeepDive, languageTag)
    }
```

(**giữ** `import`/hằng/`generateScriptWithGemini` như cũ; `runCatching` ở đây vẫn nuốt `CancellationException`: đây là hành vi **có sẵn**, không đổi trong task này; ghi vào mục "Phát hiện" nếu thấy.)

`CommuteEpisodePreparer`: thêm `@ApplicationContext private val context: Context` làm tham số **đầu**; dòng gọi:

```kotlin
        val episode = scriptService.generateScript(
            articles,
            isDeepDive = isDeepDive,
            allowGemini = context.commuteScriptGeminiConsent == true,
        )
```

(`import android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `com.mckimquyen.reader.ui.ext.commuteScriptGeminiConsent`; việc đọc `get` dùng `runBlocking` bên trong nên chạy trên `Dispatchers.IO` của Worker, chấp nhận được.)

`CommuteCastViewModel.prepareOrPlay`: đọc consent **trên IO** rồi truyền:

```kotlin
                val allowGemini = withContext(Dispatchers.IO) { context.commuteScriptGeminiConsent == true }
                val episode = withContext(Dispatchers.Default) {
                    scriptService.generateScript(articles, isDeepDive = isDeepDive, allowGemini = allowGemini)
                }
```

- [ ] **Step 6: Biên dịch sạch Hilt** (đổi constructor `CommuteEpisodePreparer`), chờ `.exit`, `exit=0`; rồi chạy `CommuteScriptConsentGateTest`, `CommuteConsentWiringSourceTest`, `CommuteScriptServiceTest`, `CommuteEpisodePreparerTest`, `CommuteCastViewModelTest` (xanh; đọc XML sau `.exit`).
- [ ] **Step 7: Mutation:** bỏ `if (allowGemini)` (đỏ: gọi mạng khi chưa consent); `allowGemini: Boolean = false` → `true` (đỏ ở "mặc định"); `== true` → `!= false` ở Preparer (đỏ ở "chưa hỏi = không": `null != false` là true); truyền `allowGemini = true` cứng ở ViewModel (đỏ ở test nguồn và test "chưa có consent ⇒ false"); bỏ tham số ở Worker path (đỏ).
- [ ] **Step 8: Commit:** `fix(strat-01): ask before sending the morning episode's articles to Gemini`.

---

### Task 5: `CommuteWorker` theo giờ nghe, và đổi giờ thì đặt lại lịch

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt` (`enqueueDailyWork(workManager, listenMinuteOfDay, policy)`)
- Modify: `app/src/main/java/com/mckimquyen/reader/RApp.kt` (truyền giờ đã lưu khi khởi động)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/CommuteWorkerScheduleTest.kt`, `app/src/test/java/com/mckimquyen/reader/domain/sv/CommuteWorkerScheduleSourceTest.kt`

**Interfaces:**
- Consumes: `CommuteSchedulePolicy.initialDelayMillis` / `clampListenMinute` (Task 1), `Context.commuteListenMinuteOfDay` (Task 3).
- Produces: `CommuteWorker.enqueueDailyWork(workManager: WorkManager, listenMinuteOfDay: Int = CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault())`. Mọi chỗ gọi cũ (`enqueueDailyWork(workManager)`) vẫn biên dịch. Tên công việc duy nhất `WORK_NAME` **không đổi** (người dùng cũ không bị đặt đôi lịch). Chu kỳ `24 giờ`, ràng buộc mạng **giữ nguyên** (spec không thêm sạc/pin).

- [ ] **Step 1: Viết test đỏ** (khuôn `BrainRpgDecayWorkerTest`: `WorkManager` giả bằng MockK, bắt `PeriodicWorkRequest`)

```kotlin
package com.mckimquyen.reader.domain.sv

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class CommuteWorkerScheduleTest {

    private val utc = ZoneOffset.UTC
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 13, h, m).atZone(utc).toInstant().toEpochMilli()

    private fun enqueue(
        listen: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
        now: Long = at(5, 0),
    ): Pair<PeriodicWorkRequest, ExistingPeriodicWorkPolicy> {
        val manager = mockk<WorkManager>(relaxed = true)
        val requests = mutableListOf<PeriodicWorkRequest>()
        val policies = mutableListOf<ExistingPeriodicWorkPolicy>()
        CommuteWorker.enqueueDailyWork(manager, listen, policy, now, utc)
        verify(exactly = 1) {
            manager.enqueueUniquePeriodicWork(CommuteWorker.WORK_NAME, io.mockk.capture(policies), io.mockk.capture(requests))
        }
        return requests.single() to policies.single()
    }

    @Test fun theDefaultCall_stillSchedulesAtTheOldCompatibleShape() {
        val manager = mockk<WorkManager>(relaxed = true)
        CommuteWorker.enqueueDailyWork(manager)
        verify(exactly = 1) { manager.enqueueUniquePeriodicWork(CommuteWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, any()) }
    }

    @Test fun theFirstRun_isThirtyMinutesBeforeTheListenTime() {
        val (request, _) = enqueue(listen = 7 * 60)
        assertEquals(90 * 60_000L, request.workSpec.initialDelay)
    }

    @Test fun aLaterListenTime_movesTheFirstRun() {
        val (request, _) = enqueue(listen = 8 * 60 + 30)
        assertEquals((3 * 60 + 0) * 60_000L, request.workSpec.initialDelay)
    }

    @Test fun theIntervalIsStillOneDay_andTheWorkerClassIsRight() {
        val (request, _) = enqueue(listen = 7 * 60)
        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(CommuteWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test fun theNetworkConstraintIsKept_andNoChargingOrBatteryConstraintIsAdded() {
        val c = enqueue(listen = 7 * 60).first.workSpec.constraints
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
        assertTrue("spec: no charging constraint", !c.requiresCharging())
        assertTrue("spec: no battery constraint", !c.requiresBatteryNotLow())
    }

    @Test fun theUniqueName_isUnchanged_soOldInstallsDoNotGetTwoSchedules() =
        assertEquals("CommuteCastDailyWork", CommuteWorker.WORK_NAME)

    @Test fun startup_keepsTheExistingSchedule() {
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, enqueue(listen = 7 * 60).second)
    }

    @Test fun changingTheTime_replacesTheSchedule_becauseKeepWouldIgnoreIt() {
        assertEquals(ExistingPeriodicWorkPolicy.REPLACE, enqueue(listen = 9 * 60, policy = ExistingPeriodicWorkPolicy.REPLACE).second)
    }

    @Test fun aCorruptListenTime_fallsBackToTheDefault() {
        val (request, _) = enqueue(listen = 99_999)
        assertEquals(90 * 60_000L, request.workSpec.initialDelay)
    }
}
```

Ghi chú: `PeriodicWorkRequest.workSpec` là API nội bộ nhưng `BrainRpgDecayWorkerTest` đã dùng nó; thêm `@RunWith(RobolectricTestRunner::class)` nếu `PeriodicWorkRequestBuilder` cần `Context` (khi đó dùng `@Config(sdk = [34], application = Application::class)` như các test Robolectric khác); **thử trước không Robolectric** như khuôn cũ.

- [ ] **Step 2: Viết test nguồn đỏ** (chỉ xét mã không comment/import)

```kotlin
package com.mckimquyen.reader.domain.sv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteWorkerScheduleSourceTest {

    private fun code(path: String) = File("src/main/java/com/mckimquyen/reader/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val worker = code("domain/sv/CommuteWorker.kt")
    private val app = code("RApp.kt")

    @Test fun theHardCodedSixAmIsGone() {
        assertFalse(worker.contains("Calendar.HOUR_OF_DAY, 6"))
        assertFalse(worker.contains("Calendar.getInstance"))
    }

    @Test fun theDelayComesFromThePolicy() {
        assertTrue(worker.contains("CommuteSchedulePolicy.initialDelayMillis("))
    }

    @Test fun startupPassesTheSavedListenTime_andKeepsTheSchedule() {
        val call = app.substringAfter("CommuteWorker.enqueueDailyWork(").substringBefore(")")
        assertTrue(call.contains("commuteListenMinuteOfDay"))
        assertFalse("startup must not replace the schedule on every launch", call.contains("REPLACE"))
    }

    @Test fun theWorkerStillTakesNoChargingOrBatteryConstraint() {
        assertFalse(worker.contains("setRequiresCharging"))
        assertFalse(worker.contains("setRequiresBatteryNotLow"))
    }

    @Test fun enqueueDailyWorkIsCalledFromExactlyOneStartupPlace() {
        assertEquals(1, Regex("""CommuteWorker\.enqueueDailyWork\(""").findAll(app).count())
    }
}
```

- [ ] **Step 3: Chạy đỏ** (biên dịch lỗi: tham số mới; hoặc test nguồn đỏ).

- [ ] **Step 4: Code tối thiểu**

`CommuteWorker.companion`:

```kotlin
        fun enqueueDailyWork(
            workManager: WorkManager,
            listenMinuteOfDay: Int = CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            nowMillis: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // The user picks the time they want to LISTEN; the episode is prepared a fixed lead earlier (STRAT-01).
            val initialDelay = CommuteSchedulePolicy.initialDelayMillis(nowMillis, listenMinuteOfDay, zone)

            val periodicRequest = PeriodicWorkRequestBuilder<CommuteWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, periodicRequest)
            Log.d(TAG, "CommuteCast daily work scheduled, listen minute $listenMinuteOfDay, initial delay: ${initialDelay / 1000}s")
        }
```

(giữ `24` dưới dạng hằng `REPEAT_INTERVAL_HOURS = 24L` nếu muốn tránh số cứng; xoá `import java.util.Calendar`, thêm `java.time.ZoneId` và `com.mckimquyen.reader.domain.commute.CommuteSchedulePolicy`.)

`RApp.workerInit`:

```kotlin
        CommuteWorker.enqueueDailyWork(workManager, listenMinuteOfDay = applicationContext.commuteListenMinuteOfDay)
```

(`import com.mckimquyen.reader.ui.ext.commuteListenMinuteOfDay`; chạy trong `workerInit` là coroutine IO có sẵn.)

- [ ] **Step 5: Chạy xanh** (9 + 5 test) và hồi quy `CommuteEpisodePreparerTest`, `BrainRpgDecayWorkerTest`.
- [ ] **Step 6: Mutation:** `KEEP` mặc định → `REPLACE` (đỏ ở "startup giữ lịch"); bỏ `policy` (cứng `KEEP`; đỏ ở "đổi giờ thì REPLACE"); `listenMinuteOfDay` → hằng 420 (đỏ ở "listen muộn hơn"); đổi `WORK_NAME`; thêm `.setRequiresCharging(true)` (đỏ ở ràng buộc); `24, TimeUnit.HOURS` → `12` (đỏ); ở `RApp` bỏ tham số (đỏ ở test nguồn).
- [ ] **Step 7: Commit:** `feat(strat-01): prepare the morning episode before the listen time the user picked`.

---

### Task 6: Chuỗi `commute_*` mới × 38 locale

**Files:**
- Modify: 38 tệp `app/src/main/res/values*/strings.xml` (một script all-or-none, khuôn `/tmp/o03_add_strings.py`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/CommuteMorningStringsTest.kt`

**Interfaces:** sinh các khóa dưới đây; Task 7 (thông báo) và 9 (UI) tham chiếu **đúng** các tên này.

| Khóa | EN | VI |
|---|---|---|
| `commute_consent_title` | `Write the morning episode with Gemini` | `Soạn bản tin sáng bằng Gemini` |
| `commute_consent_message` | `When you agree, the title and a short description (up to 250 characters each) of up to 10 articles are sent to Google Gemini to write the morning episode. Your reading history and the article text are never sent. If you do not agree, the episode is written on your device and nothing leaves it.` | `Khi bạn đồng ý, tiêu đề và mô tả ngắn (tối đa 250 ký tự mỗi bài) của tối đa 10 bài được gửi tới Google Gemini để soạn bản tin sáng. Lịch sử đọc và nội dung bài không bao giờ được gửi. Nếu bạn không đồng ý, bản tin được soạn ngay trên máy và không có gì rời khỏi máy.` |
| `commute_listen_time_title` | `Morning episode listen time` | `Giờ nghe bản tin sáng` |
| `commute_listen_time_desc` | `The episode is prepared 30 minutes earlier. Android may delay it by a few minutes.` | `Bản tin được soạn trước 30 phút. Android có thể trễ vài phút.` |
| `commute_autoplay_title` | `Play when Bluetooth headphones connect` | `Tự phát khi cắm tai nghe Bluetooth` |
| `commute_autoplay_desc` | `Tries to start the morning episode once a day, within 2 hours of it being ready. If Android blocks it, you get a notification with a Play button.` | `Thử phát bản tin sáng mỗi ngày một lần, trong 2 giờ sau khi bản tin sẵn sàng. Nếu Android chặn, bạn nhận thông báo có nút Phát.` |
| `commute_autoplay_permission_denied` | `Without the Nearby devices permission the app cannot see your headphones, so this stays off.` | `Không có quyền Thiết bị ở gần thì app không thấy tai nghe của bạn, nên công tắc này giữ tắt.` |
| `commute_play_action` | `Play` | `Phát` |
| `commute_ready_to_play_title` | `Your morning episode is ready` | `Bản tin sáng đã sẵn sàng` |
| `commute_ready_to_play_body` | `Your headphones are connected. Tap Play to listen.` | `Tai nghe đã kết nối. Chạm Phát để nghe.` |

(10 khóa; không có `%d`/`%s`; **không** nói "tự động chắc chắn phát".)

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteMorningStringsTest {

    private val keys = listOf(
        "commute_consent_title", "commute_consent_message", "commute_listen_time_title", "commute_listen_time_desc",
        "commute_autoplay_title", "commute_autoplay_desc", "commute_autoplay_permission_denied", "commute_play_action",
        "commute_ready_to_play_title", "commute_ready_to_play_body",
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun thereAreExactlyTenKeys() = assertEquals(10, keys.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun noStringHasAPlaceholder() {
        for (f in files) for (k in keys) {
            assertEquals("${f.parentFile.name} $k", 0, Regex("%\\d*\\$?[sd]").findAll(value(f, k)!!).count())
        }
    }

    @Test fun theConsentSaysWhatIsSent_andWhatIsNot_inEnglishAndVietnamese() {
        val en = value(File("src/main/res/values/strings.xml"), "commute_consent_message")!!
        val vi = value(File("src/main/res/values-vi/strings.xml"), "commute_consent_message")!!
        assertTrue(en.contains("250 characters") && en.contains("never sent") && en.contains("on your device"))
        assertTrue(vi.contains("250 ký tự") && vi.contains("không bao giờ được gửi") && vi.contains("trên máy"))
    }

    @Test fun theAutoPlayDescriptionIsHonest_itSaysAndroidMayBlockIt() {
        val en = value(File("src/main/res/values/strings.xml"), "commute_autoplay_desc")!!
        val vi = value(File("src/main/res/values-vi/strings.xml"), "commute_autoplay_desc")!!
        assertTrue(en.contains("blocks") && en.contains("Play button"))
        assertTrue(vi.contains("chặn") && vi.contains("nút Phát"))
    }

    @Test fun theListenTimeDescriptionSaysThirtyMinutes() {
        assertTrue(value(File("src/main/res/values/strings.xml"), "commute_listen_time_desc")!!.contains("30 minutes"))
        assertTrue(value(File("src/main/res/values-vi/strings.xml"), "commute_listen_time_desc")!!.contains("30 phút"))
    }

    @Test fun vietnameseIsReallyTranslated_notTheEnglishCopy() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        for (k in keys) assertTrue("vi must differ from en for $k", value(en, k) != value(vi, k))
    }
}
```

- [ ] **Step 2: Chạy đỏ** (thiếu khóa).

- [ ] **Step 3: Script all-or-none** `/tmp/s01_add_strings.py` (sao `/tmp/o03_add_strings.py`: đổi `EN`/`VI` thành bảng trên, `assert len(KEYS) == 10`, giữ `assert len(files) == 38`, kiểm "đã có khoá" và `</resources>` đúng một lần, **ghi sau khi kiểm hết**, `esc` dấu `'`). Chạy từ gốc repo: `python3 /tmp/s01_add_strings.py` → `ok 38`.

- [ ] **Step 4: Chạy xanh:** `CommuteMorningStringsTest` (8) và `StringResourceParityTest` (hồi quy).
- [ ] **Step 5: Mutation:** xóa một khóa ở một locale (đỏ); lặp một khóa (đỏ); thêm `%1$s` vào một chuỗi (đỏ); sửa câu consent của EN bỏ "never sent" (đỏ); sửa mô tả tự phát bỏ "Play button" (đỏ); đổi bản VI thành bản EN (đỏ). Mỗi ca khôi phục bằng backup và `cmp`.
- [ ] **Step 6: Commit** (kiểm `git diff --shortstat` = 38 tệp): `feat(strat-01): add the morning DJ strings in 38 locales`.

---

### Task 7: Thông báo "sẵn sàng" có nút Phát, và đường phát thật trong dịch vụ

**Phát hiện khi lập plan (đã đọc `CommuteMediaSessionService.onStartCommand`):** nhánh `ACTION_PLAY` chỉ gọi `publishMetadata` + `startForeground`, **không bật âm thanh**; âm thanh chỉ chạy qua `resumeOrPlayLatestEpisode()` (gọi từ callback MediaSession). Nút Phát gửi `ACTION_PLAY` sẽ **không phát gì**. Vì vậy thêm một action **mới** `ACTION_PLAY_LATEST` làm đúng việc đó (và là đường mà tự phát từ Bluetooth ở Task 8 cũng dùng), và đánh dấu "đã nghe" ở đó.

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaSessionService.kt` (`ACTION_PLAY_LATEST`, nhánh `onStartCommand`, `playLatest(context)`, đánh dấu đã nghe)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt` (`notifyCommuteReadyToPlay`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/android/NotificationHelperCommutePlayTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommutePlayLatestSourceTest.kt`

**Interfaces:**
- Consumes: chuỗi `commute_ready_to_play_*`, `commute_play_action` (Task 6); `CommuteEpisodeStore.markPlayed/isPlayed` (Task 3); `CommuteMediaSessionService` (`resumeOrPlayLatestEpisode`, `startForeground`, `buildNotification`).
- Produces (thêm): `CommuteMediaSessionService.ACTION_PLAY_LATEST = "com.mckimquyen.reader.commute.ACTION_PLAY_LATEST"` và `fun playLatest(context: Context): Boolean` (companion; `startForegroundService` có bắt `Exception`, trả `false` nếu Android từ chối). Nhánh `onStartCommand` cho `ACTION_PLAY_LATEST`: gọi `startForeground(NOTIFICATION_ID, buildNotification(title, subtitle, isPlaying = true))` **trước** (hạn vài giây của Android), rồi `resumeOrPlayLatestEpisode()`. `resumeOrPlayLatestEpisode` sau khi bắt đầu phát một episode (có sẵn trong bộ nhớ hoặc nạp từ kho) gọi `episodeStore.markPlayed(episode.id)` (đúng cả cho Android Auto vì cùng đường).
- Produces: `NotificationHelper.COMMUTE_READY_NOTIFICATION_ID = 9944`; `fun notifyCommuteReadyToPlay(episodeTitle: String)`: đăng một thông báo trên kênh `COMMUTE_CHANNEL_ID`, **một id cố định** (lần sau thay lần trước), nội dung `commute_ready_to_play_title`/`_body`, `setAutoCancel(true)`, chạm thông báo mở `MainActivity` với `EXTRA_START_COMMUTE=true` (giống `notifyCommuteCast`), và **một action** "Phát" (`R.string.commute_play_action`) bằng `PendingIntent.getForegroundService` (API 26+, `getService` dưới đó) tới `CommuteMediaSessionService` với `action = ACTION_PLAY_LATEST`, `FLAG_IMMUTABLE`. Bấm nút là thao tác của người dùng trên thông báo nên Android **cho phép** bật dịch vụ nền. Đi qua `post(...)` nên **không đăng khi thiếu quyền** (không crash).

- [ ] **Step 1: Viết test đỏ** (khuôn `NotificationHelperOracleDueTest`)

```kotlin
package com.mckimquyen.reader.infrastructure.android

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.audio.CommuteMediaSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationHelperCommutePlayTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    private fun posted() = shadowOf(manager).getNotification(NotificationHelper.COMMUTE_READY_NOTIFICATION_ID)

    @Test fun theIdIsHardCoded_andDistinctFromEveryOtherNotification() {
        assertEquals(9944, NotificationHelper.COMMUTE_READY_NOTIFICATION_ID)
        val others = listOf(
            NotificationHelper.COMMUTE_NOTIFICATION_ID, NotificationHelper.ZEN_DAILY_EDITION_NOTIFICATION_ID,
            NotificationHelper.RPG_DECAY_NOTIFICATION_ID, NotificationHelper.ORACLE_DUE_NOTIFICATION_ID,
        )
        others.forEach { assertNotEquals(NotificationHelper.COMMUTE_READY_NOTIFICATION_ID, it) }
    }

    @Test fun itPostsTheLocalisedTitleAndBody_onTheCommuteChannel() {
        helper.notifyCommuteReadyToPlay("Morning")
        val n = posted()
        assertEquals(context.getString(R.string.commute_ready_to_play_title), n.extras.getCharSequence("android.title").toString())
        assertEquals(context.getString(R.string.commute_ready_to_play_body), n.extras.getCharSequence("android.text").toString())
        assertEquals(NotificationHelper.COMMUTE_CHANNEL_ID, n.channelId)
    }

    @Test fun itCarriesExactlyOneAction_namedPlay() {
        helper.notifyCommuteReadyToPlay("Morning")
        val actions = posted().actions
        assertEquals(1, actions.size)
        assertEquals(context.getString(R.string.commute_play_action), actions[0].title.toString())
    }

    @Test fun thePlayAction_startsTheMediaServiceWithPlayLatest_notThePlainPlayThatOnlyShowsANotification() {
        helper.notifyCommuteReadyToPlay("Morning")
        val pending = posted().actions[0].actionIntent
        val shadow = shadowOf(pending)
        assertTrue(shadow.isService || shadow.isForegroundService)
        assertNotEquals(CommuteMediaSessionService.ACTION_PLAY, shadow.savedIntent.action)
        assertEquals(CommuteMediaSessionService::class.java.name, shadow.savedIntent.component?.className)
        assertEquals(CommuteMediaSessionService.ACTION_PLAY_LATEST, shadow.savedIntent.action)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun tappingTheBody_opensTheAppAndStartsTheCommuteSheet() {
        helper.notifyCommuteReadyToPlay("Morning")
        val intent = shadowOf(posted().contentIntent).savedIntent
        assertEquals(true, intent.getBooleanExtra(NotificationHelper.EXTRA_START_COMMUTE, false))
    }

    @Test fun aSecondPost_replacesTheFirst() {
        helper.notifyCommuteReadyToPlay("A")
        helper.notifyCommuteReadyToPlay("B")
        val n = shadowOf(manager).allNotifications.filter { it.channelId == NotificationHelper.COMMUTE_CHANNEL_ID }
        assertEquals(1, n.size)
    }

    @Test fun withoutPermission_nothingIsPosted_andNothingCrashes() {
        shadowOf(manager).setNotificationsEnabled(false)
        helper.notifyCommuteReadyToPlay("Morning")
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
```

- [ ] **Step 2: Chạy đỏ** (`COMMUTE_READY_NOTIFICATION_ID`, `notifyCommuteReadyToPlay` chưa có).

- [ ] **Step 3: Code tối thiểu** (cạnh `notifyCommuteCast`):

```kotlin
    /** STRAT-01: the morning episode is ready and headphones just connected; one tap on Play listens to it. */
    fun notifyCommuteReadyToPlay(@Suppress("UNUSED_PARAMETER") episodeTitle: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_START_COMMUTE, true)
        }
        val openPending = PendingIntent.getActivity(
            context, COMMUTE_READY_NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val play = Intent(context, CommuteMediaSessionService::class.java).apply { action = CommuteMediaSessionService.ACTION_PLAY_LATEST }
        val playPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, COMMUTE_READY_NOTIFICATION_ID, play, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                context, COMMUTE_READY_NOTIFICATION_ID, play, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, COMMUTE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.commute_ready_to_play_title))
            .setContentText(context.getString(R.string.commute_ready_to_play_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.commute_play_action), playPending)
            .build()
        post(COMMUTE_READY_NOTIFICATION_ID, notification)
    }
```

và `const val COMMUTE_READY_NOTIFICATION_ID = 9944` trong `companion object`; `import com.mckimquyen.reader.infrastructure.audio.CommuteMediaSessionService`. (Tham số `episodeTitle` **chưa dùng**: bỏ nó nếu không có test nào cần; YAGNI. Nếu bỏ, sửa chữ ký và các test về `notifyCommuteReadyToPlay()`.)

- [ ] **Step 4: Chạy xanh** (7 test) + hồi quy `NotificationHelperTest`, `NotificationHelperOracleDueTest`, `NotificationHelperRpgDecayTest`.
- [ ] **Step 5: Mutation:** id `9944` → `9977` (đỏ); bỏ `FLAG_IMMUTABLE` ở nút (đỏ); `ACTION_PLAY_LATEST` → `ACTION_PLAY` (đỏ: nút sẽ không phát gì); bỏ `addAction` (đỏ); đổi kênh sang `RPG_DECAY_CHANNEL_ID` (đỏ); bỏ `EXTRA_START_COMMUTE` (đỏ); `getForegroundService`/`getService` → `getActivity` (đỏ ở `isService`).
- [ ] **Step 6: Viết test nguồn đỏ — đường phát thật** (`CommutePlayLatestSourceTest`, chỉ xét mã không comment/import)

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommutePlayLatestSourceTest {

    private val service = File("src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaSessionService.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val playLatestBranch = service.substringAfter("ACTION_PLAY_LATEST ->").substringBefore("ACTION_PAUSE ->")

    @Test fun theActionIsDeclaredWithAUniqueName() {
        assertTrue(service.contains("const val ACTION_PLAY_LATEST = \"com.mckimquyen.reader.commute.ACTION_PLAY_LATEST\""))
        assertEquals(1, Regex("""ACTION_PLAY_LATEST =""").findAll(service).count())
    }

    @Test fun theBranchGoesForegroundFirst_thenStartsThePlayback() {
        assertTrue(playLatestBranch.contains("startForeground("))
        assertTrue(playLatestBranch.contains("resumeOrPlayLatestEpisode()"))
        assertTrue(
            "startForeground must come first: Android gives a service a few seconds to do it",
            playLatestBranch.indexOf("startForeground(") < playLatestBranch.indexOf("resumeOrPlayLatestEpisode()"),
        )
    }

    @Test fun startingAnEpisodeMarksItAsPlayed_onBothPaths() {
        val body = service.substringAfter("private fun resumeOrPlayLatestEpisode()").substringBefore("// --- MediaBrowserServiceCompat")
        assertEquals(2, Regex("""markPlayed\(""").findAll(body).count())
    }

    @Test fun thePlainPlayBranchIsUnchanged_itStillOnlyShowsTheNotification() {
        val plain = service.substringAfter("ACTION_PLAY ->").substringBefore("ACTION_PLAY_LATEST ->")
        assertTrue(!plain.contains("resumeOrPlayLatestEpisode"))
    }

    @Test fun theHelperCatchesAStartThatAndroidRefuses() {
        val helper = service.substringAfter("fun playLatest(").substringBefore("fun pause(")
        assertTrue(helper.contains("catch (e: Exception)"))
    }
}
```

- [ ] **Step 7: Chạy đỏ, rồi code tối thiểu** trong `CommuteMediaSessionService.kt`. Trong `companion object`:

```kotlin
        const val ACTION_PLAY_LATEST = "com.mckimquyen.reader.commute.ACTION_PLAY_LATEST"

        /** Starts the latest stored episode; used by the notification's Play button and by headset auto-play (STRAT-01). */
        fun playLatest(context: Context): Boolean {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply { action = ACTION_PLAY_LATEST }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                true
            } catch (e: Exception) {
                // Android may refuse to start a service from the background (ForegroundServiceStartNotAllowedException).
                Log.w("CommuteMediaSession", "Could not start playback: ${e.javaClass.simpleName}")
                false
            }
        }
```

(không suspend nên không có `CancellationException` để nuốt.) Nhánh `onStartCommand`, **ngay sau** nhánh `ACTION_PLAY ->` có sẵn:

```kotlin
            ACTION_PLAY_LATEST -> {
                // Go foreground first: a service started this way has only a few seconds to do so.
                startForeground(NOTIFICATION_ID, buildNotification(title, subtitle, isPlaying = true))
                resumeOrPlayLatestEpisode()
            }
```

`resumeOrPlayLatestEpisode` (cả hai đường đánh dấu đã nghe):

```kotlin
    private fun resumeOrPlayLatestEpisode() {
        val existing = audioPlayer.playerState.value.episode
        if (existing != null) {
            audioPlayer.resume()
            scope.launch { episodeStore.markPlayed(existing.id) }
            return
        }
        scope.launch {
            val stored = episodeStore.load()
            if (stored == null) {
                Log.w(TAG, "No CommuteCast episode available to play from a media control request.")
                return@launch
            }
            audioPlayer.playEpisode(stored)
            episodeStore.markPlayed(stored.id)
        }
    }
```

- [ ] **Step 8: Chạy xanh** (7 + 5 test) + hồi quy các test `AutoSessionCallback*`, `CommuteMediaSession*` đang có.
- [ ] **Step 9: Mutation (dịch vụ):** bỏ `startForeground(` ở nhánh mới (đỏ); đặt `resumeOrPlayLatestEpisode()` trước `startForeground` (đỏ ở thứ tự); bỏ một trong hai `markPlayed` (đỏ); bỏ `catch` ở `playLatest` (đỏ); đổi tên hằng (đỏ).
- [ ] **Step 10: Commit:** `feat(strat-01): add a ready-to-play notification whose Play button really plays`.

---

### Task 8: `BluetoothAudioReceiver` — tự phát khi cắm tai nghe, nếu bị chặn thì hiện nút Phát

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/HeadsetAutoPlayHandler.kt` (logic không phụ thuộc `BroadcastReceiver`, để test được)
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/BluetoothAudioReceiver.kt` (vỏ mỏng, `@AndroidEntryPoint`)
- Modify: `app/src/main/AndroidManifest.xml` (quyền + receiver)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/HeadsetAutoPlayHandlerTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/BluetoothAudioReceiverSourceTest.kt`

**Interfaces:**
- Consumes: `CommuteAutoPlayPolicy.decide` / `AutoPlayInput` / `AutoPlayDecision` / `epochDayUtc` (Task 2); `Context.commuteAutoPlayOnHeadset`, `commuteLastAutoPlayEpochDay`, `setCommuteLastAutoPlayEpochDay` (Task 3); `CommuteEpisodeStore.load/isPlayed` (Task 3); `CommuteMediaSessionService.playLatest(context): Boolean` (Task 7); `NotificationHelper.notifyCommuteReadyToPlay` (Task 7).
- Produces: `class HeadsetAutoPlayHandler(private val deps: Deps)` với
  - `interface Deps { suspend fun switchOn(): Boolean; suspend fun episode(): CommuteEpisode?; suspend fun episodePlayed(id: String): Boolean; suspend fun lastAutoPlayEpochDay(): Long?; suspend fun saveAutoPlayEpochDay(day: Long); fun otherAudioPlaying(): Boolean; fun hasBluetoothPermission(): Boolean; fun startPlayback(): Boolean; fun offerPlayButton(); fun nowMillis(): Long }`;
  - `suspend fun onAudioDeviceConnected(isAudioDevice: Boolean): Result` với `enum class Result { SKIPPED, PLAYED, OFFERED }`.
- Quy tắc `onAudioDeviceConnected`:
  1. Không có quyền Bluetooth ⇒ `SKIPPED` (không đọc gì khác).
  2. Dựng `AutoPlayInput` từ `deps` (episode, đã nghe, ngày tự phát cuối, công tắc, âm thanh khác, `isAudioDevice`) rồi `CommuteAutoPlayPolicy.decide`.
  3. `Skip` ⇒ `SKIPPED`. `OfferPlayButton` ⇒ `deps.offerPlayButton()`, `OFFERED` (**không** tính vào hạn mức ngày).
  4. `PlayNow` ⇒ `deps.startPlayback()`: `true` ⇒ **sau đó** `saveAutoPlayEpochDay(epochDayUtc(now))`, `PLAYED`; `false` (Android chặn) ⇒ `offerPlayButton()`, `OFFERED`, **không** ghi ngày.
  5. Mọi `Exception` trừ `CancellationException` ⇒ log tên lớp, `SKIPPED` (một receiver không được làm app sập).
- `BluetoothAudioReceiver : BroadcastReceiver` (manifest, `exported=false`, `ACTION_ACL_CONNECTED`): `goAsync()`, đọc `BluetoothDevice` từ `EXTRA_DEVICE` (API 33+: `getParcelableExtra(name, BluetoothDevice::class.java)`), `isAudioDevice` = lớp thiết bị chính `AUDIO_VIDEO` hoặc `BluetoothClass.Service.AUDIO`; chạy `handler.onAudioDeviceConnected` trên một `CoroutineScope(SupervisorJob() + Dispatchers.IO)` **riêng của lần nhận** và gọi `pendingResult.finish()` trong `finally` (hủy scope).

- [ ] **Step 1: Viết test đỏ — handler** (giả `Deps` bằng lớp thường để thấy đúng thứ tự gọi)

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.commute.CommuteAutoPlayPolicy
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class HeadsetAutoPlayHandlerTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private class Fake(val now: Long) : HeadsetAutoPlayHandler.Deps {
        var switch = true
        var episode: CommuteEpisode? = null
        var played = false
        var lastDay: Long? = null
        var otherAudio = false
        var permission = true
        var startResult = true
        var failWith: Throwable? = null
        val calls = mutableListOf<String>()
        var savedDay: Long? = null

        override suspend fun switchOn(): Boolean { calls += "switchOn"; failWith?.let { throw it }; return switch }
        override suspend fun episode() = episode.also { calls += "episode" }
        override suspend fun episodePlayed(id: String) = played.also { calls += "played" }
        override suspend fun lastAutoPlayEpochDay() = lastDay.also { calls += "lastDay" }
        override suspend fun saveAutoPlayEpochDay(day: Long) { calls += "save"; savedDay = day }
        override fun otherAudioPlaying() = otherAudio
        override fun hasBluetoothPermission() = permission.also { calls += "permission" }
        override fun startPlayback(): Boolean { calls += "start"; return startResult }
        override fun offerPlayButton() { calls += "offer" }
        override fun nowMillis() = now
    }

    private fun episode(ageMillis: Long) = CommuteEpisode(
        id = "ep", title = "t", date = Date(now - ageMillis),
        dialogues = listOf(CommuteDialogue(CommuteSpeaker.ALEX, "hi")),
    )

    private fun run(f: Fake, audioDevice: Boolean = true) =
        runBlocking { HeadsetAutoPlayHandler(f).onAudioDeviceConnected(audioDevice) }

    @Test fun everythingRight_startsPlayback_andThenRemembersToday() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.PLAYED, run(f))
        assertEquals(CommuteAutoPlayPolicy.epochDayUtc(now), f.savedDay)
        assertTrue("the day is saved only AFTER playback started", f.calls.indexOf("start") < f.calls.indexOf("save"))
    }

    @Test fun withoutTheBluetoothPermission_nothingElseIsRead() {
        val f = Fake(now).apply { episode = episode(hour); permission = false }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertEquals(listOf("permission"), f.calls)
    }

    @Test fun switchOff_skips_andNeverStartsOrOffers() {
        val f = Fake(now).apply { episode = episode(hour); switch = false }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls && "offer" !in f.calls)
    }

    @Test fun noEpisode_skips() {
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(Fake(now)))
    }

    @Test fun anEpisodeThatIsTooOld_skips() {
        val f = Fake(now).apply { episode = episode(3 * hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun anAlreadyHeardEpisode_skips() {
        val f = Fake(now).apply { episode = episode(hour); played = true }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun alreadyAutoPlayedToday_skips() {
        val f = Fake(now).apply { episode = episode(hour); lastDay = CommuteAutoPlayPolicy.epochDayUtc(now) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls)
    }

    @Test fun aDeviceThatIsNotAudio_skips() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f, audioDevice = false))
    }

    @Test fun otherAudioPlaying_offersTheButton_doesNotInterrupt_andDoesNotUseUpTheDay() {
        val f = Fake(now).apply { episode = episode(hour); otherAudio = true }
        assertEquals(HeadsetAutoPlayHandler.Result.OFFERED, run(f))
        assertTrue("start" !in f.calls)
        assertEquals(null, f.savedDay)
    }

    @Test fun androidRefusingTheStart_offersTheButton_andDoesNotUseUpTheDay() {
        val f = Fake(now).apply { episode = episode(hour); startResult = false }
        assertEquals(HeadsetAutoPlayHandler.Result.OFFERED, run(f))
        assertTrue("offer" in f.calls)
        assertEquals("a refused start must not count as today's auto-play", null, f.savedDay)
    }

    @Test fun anyFailure_isAbsorbed_andReportedAsSkipped() {
        val f = Fake(now).apply { failWith = IllegalStateException("boom") }
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
    }

    @Test fun cancellation_isRethrown_notSwallowed() {
        val f = Fake(now).apply { failWith = CancellationException("stop") }
        val thrown = runCatching { run(f) }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test fun theSecondConnectionTheSameDay_doesNotPlayAgain() {
        val f = Fake(now).apply { episode = episode(hour) }
        assertEquals(HeadsetAutoPlayHandler.Result.PLAYED, run(f))
        f.lastDay = f.savedDay
        f.calls.clear()
        assertEquals(HeadsetAutoPlayHandler.Result.SKIPPED, run(f))
        assertTrue("start" !in f.calls)
    }
}
```

- [ ] **Step 2: Viết test nguồn đỏ — receiver và manifest** (chỉ xét mã không comment/import)

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BluetoothAudioReceiverSourceTest {

    private fun code(path: String) = File(path).readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val receiver = code("src/main/java/com/mckimquyen/reader/infrastructure/audio/BluetoothAudioReceiver.kt")
    private val handler = code("src/main/java/com/mckimquyen/reader/infrastructure/audio/HeadsetAutoPlayHandler.kt")
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val receiverTag = manifest.substringAfter("android:name=\".infrastructure.audio.BluetoothAudioReceiver\"").substringBefore("</receiver>")

    @Test fun theReceiverIsDeclared_notExported_andListensOnlyForAclConnected() {
        assertTrue(manifest.contains(".infrastructure.audio.BluetoothAudioReceiver"))
        assertTrue(receiverTag.contains("android:exported=\"false\""))
        assertTrue(receiverTag.contains("android.bluetooth.device.action.ACL_CONNECTED"))
        assertEquals(1, Regex("<action ").findAll(receiverTag).count())
    }

    @Test fun theManifestAsksForNearbyDevices_onlyWhereTheOsRequiresIt() {
        assertTrue(Regex("""BLUETOOTH_CONNECT"[^>]*/>""").containsMatchIn(manifest))
        assertEquals("declared exactly once", 1, Regex("""android.permission.BLUETOOTH_CONNECT""").findAll(manifest).count())
    }

    @Test fun noBroaderBluetoothOrLocationPermissionIsAdded() {
        listOf("BLUETOOTH_SCAN", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM")
            .forEach { assertFalse("must not add $it", manifest.contains(it)) }
    }

    @Test fun theReceiverFinishesItsPendingResult_inAFinally_andCancelsItsScope() {
        assertTrue(receiver.contains("goAsync()"))
        assertTrue(receiver.contains("finally"))
        assertTrue(receiver.substringAfter("finally").contains("finish()"))
        assertTrue(receiver.contains("cancel()"))
    }

    @Test fun theReceiverNeverBlocksTheMainThread() {
        assertFalse(receiver.contains("runBlocking"))
        assertTrue(receiver.contains("Dispatchers.IO"))
    }

    @Test fun onlyAudioDevicesAreAccepted() {
        assertTrue(receiver.contains("BluetoothClass.Device.Major.AUDIO_VIDEO") || receiver.contains("BluetoothClass.Service.AUDIO"))
    }

    @Test fun theDecisionLivesInThePolicy_notInTheReceiver() {
        assertTrue(handler.contains("CommuteAutoPlayPolicy.decide("))
        assertFalse(receiver.contains("CommuteAutoPlayPolicy"))
    }

    @Test fun theHandlerSavesTheDayOnlyAfterAStartThatSucceeded() {
        val body = handler.substringAfter("AutoPlayDecision.PlayNow ->").substringBefore("catch (e: CancellationException)")
        assertTrue(body.contains("startPlayback()") && body.contains("saveAutoPlayEpochDay("))
        assertTrue(body.indexOf("startPlayback()") < body.indexOf("saveAutoPlayEpochDay("))
    }

    @Test fun theHandlerNeverSwallowsCancellation() {
        assertTrue(handler.contains("catch (e: CancellationException)"))
        assertTrue(handler.indexOf("catch (e: CancellationException)") < handler.indexOf("catch (e: Exception)"))
    }

    @Test fun nothingInThisFeatureTouchesTheNetworkGeminiOrAds() {
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "AdManager", "Rewarded", "http").forEach {
            assertFalse("receiver must not mention '$it'", receiver.contains(it, ignoreCase = true))
            assertFalse("handler must not mention '$it'", handler.contains(it, ignoreCase = true))
        }
    }
}
```

Ghi chú: hai test đầu của manifest dùng `src/main/AndroidManifest.xml` (đường dẫn tính từ thư mục `app`, nơi Gradle chạy test); kiểm bằng `File(...).exists()` nếu đỏ vì sai đường dẫn, **không** đổi thành mềm. Test `theManifestAsksForNearbyDevices_...` chỉ khẳng định quyền **có** khai báo; việc "chỉ xin khi bật công tắc" nằm ở Task 9 (UI).

- [ ] **Step 3: Chạy đỏ** (lỗi biên dịch: `HeadsetAutoPlayHandler`; test nguồn: tệp chưa có).

- [ ] **Step 4: Code tối thiểu — handler**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.util.Log
import com.mckimquyen.reader.domain.commute.AutoPlayDecision
import com.mckimquyen.reader.domain.commute.AutoPlayInput
import com.mckimquyen.reader.domain.commute.CommuteAutoPlayPolicy
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import kotlinx.coroutines.CancellationException

/**
 * What happens when a Bluetooth audio device connects (STRAT-01). Kept apart from the receiver so every branch is
 * reachable from a plain unit test. The rules live in [CommuteAutoPlayPolicy]; this class only gathers the facts, acts
 * on the decision and remembers today's auto-play AFTER playback really started.
 */
class HeadsetAutoPlayHandler(private val deps: Deps) {

    interface Deps {
        suspend fun switchOn(): Boolean
        suspend fun episode(): CommuteEpisode?
        suspend fun episodePlayed(id: String): Boolean
        suspend fun lastAutoPlayEpochDay(): Long?
        suspend fun saveAutoPlayEpochDay(day: Long)
        fun otherAudioPlaying(): Boolean
        fun hasBluetoothPermission(): Boolean
        fun startPlayback(): Boolean
        fun offerPlayButton()
        fun nowMillis(): Long
    }

    enum class Result { SKIPPED, PLAYED, OFFERED }

    suspend fun onAudioDeviceConnected(isAudioDevice: Boolean): Result = try {
        if (!deps.hasBluetoothPermission()) {
            Result.SKIPPED
        } else {
            val now = deps.nowMillis()
            val episode = deps.episode()
            val input = AutoPlayInput(
                switchOn = deps.switchOn(),
                episodeDateMillis = episode?.date?.time,
                episodePlayed = episode?.let { deps.episodePlayed(it.id) } ?: false,
                nowMillis = now,
                lastAutoPlayEpochDay = deps.lastAutoPlayEpochDay(),
                isAudioDevice = isAudioDevice,
                otherAudioPlaying = deps.otherAudioPlaying(),
            )
            when (CommuteAutoPlayPolicy.decide(input)) {
                is AutoPlayDecision.Skip -> Result.SKIPPED
                AutoPlayDecision.OfferPlayButton -> {
                    deps.offerPlayButton()
                    Result.OFFERED
                }
                AutoPlayDecision.PlayNow -> if (deps.startPlayback()) {
                    // Only a start that Android allowed uses up today's single automatic play.
                    deps.saveAutoPlayEpochDay(CommuteAutoPlayPolicy.epochDayUtc(now))
                    Result.PLAYED
                } else {
                    deps.offerPlayButton()
                    Result.OFFERED
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A receiver must never crash the app; only the type is logged.
        Log.w(TAG, "Headset auto-play failed: ${e.javaClass.simpleName}")
        Result.SKIPPED
    }

    private companion object {
        const val TAG = "HeadsetAutoPlay"
    }
}
```

- [ ] **Step 5: Code tối thiểu — receiver** (`@AndroidEntryPoint`, `Deps` thật)

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.commuteAutoPlayOnHeadset
import com.mckimquyen.reader.ui.ext.commuteLastAutoPlayEpochDay
import com.mckimquyen.reader.ui.ext.setCommuteLastAutoPlayEpochDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin shell: reads the connected device, hands the facts to [HeadsetAutoPlayHandler], and always finishes (STRAT-01). */
@AndroidEntryPoint
class BluetoothAudioReceiver : BroadcastReceiver() {

    // Hilt assigns both in `super.onReceive` (generated base class) before any use below; the standard way for an Android component.
    @Inject lateinit var episodeStore: CommuteEpisodeStore
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val permitted = hasPermission(context)
        val isAudio = permitted && device?.bluetoothClass?.let {
            it.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || it.hasService(BluetoothClass.Service.AUDIO)
        } == true

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                HeadsetAutoPlayHandler(RealDeps(context.applicationContext)).onAudioDeviceConnected(isAudio)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private inner class RealDeps(private val context: Context) : HeadsetAutoPlayHandler.Deps {
        private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        override suspend fun switchOn() = context.commuteAutoPlayOnHeadset
        override suspend fun episode(): CommuteEpisode? = episodeStore.load()
        override suspend fun episodePlayed(id: String) = episodeStore.isPlayed(id)
        override suspend fun lastAutoPlayEpochDay() = context.commuteLastAutoPlayEpochDay
        override suspend fun saveAutoPlayEpochDay(day: Long) = context.setCommuteLastAutoPlayEpochDay(day)
        override fun otherAudioPlaying() = audio?.isMusicActive == true
        override fun hasBluetoothPermission() = hasPermission(context)
        override fun startPlayback() = CommuteMediaSessionService.playLatest(context)
        override fun offerPlayButton() = notificationHelper.notifyCommuteReadyToPlay("")
        override fun nowMillis() = System.currentTimeMillis()
    }
}
```

**Lưu ý đúng sai (đọc trước khi chép):**
- `@AndroidEntryPoint` trên `BroadcastReceiver`: lớp cha do Hilt sinh ra tiêm các trường `@Inject` **trước** khi `onReceive` của ta chạy; **không** gọi `super.onReceive` (nó là hàm trừu tượng, gọi sẽ lỗi biên dịch `Abstract member cannot be accessed directly`, đã gặp khi thực thi). Comment `lateinit` nêu lý do chắc chắn không null (R5).
- `notifyCommuteReadyToPlay("")`: nếu Task 7 đã bỏ tham số `episodeTitle` (YAGNI) thì gọi `notifyCommuteReadyToPlay()`.
- `goAsync()` cho receiver có thời hạn khoảng 10 giây: đọc DataStore và kho episode đều nhanh; nếu quá hạn Android sẽ kết thúc process receiver, và cách xử lý là chấp nhận (không có gì mất dữ liệu: ngày chỉ ghi sau khi phát thành công).

- [ ] **Step 6: Manifest** (cạnh các `<uses-permission>` hiện có và trước `</application>`)

```xml
    <!-- STRAT-01: only needed on Android 12+ to read the class of a connecting headset; asked only when the user turns the switch on. -->
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

```xml
        <!-- STRAT-01: headset auto-play. Not exported; ACL_CONNECTED is one of the broadcasts a manifest receiver may still get. -->
        <receiver
            android:name=".infrastructure.audio.BluetoothAudioReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 7: Biên dịch sạch Hilt** (thêm `@AndroidEntryPoint` mới): `:app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`, chờ `.exit`, `exit=0`; rồi chạy `HeadsetAutoPlayHandlerTest` (13), `BluetoothAudioReceiverSourceTest` (10) xanh.
- [ ] **Step 8: Mutation (handler):** bỏ kiểm quyền (đỏ ở "không đọc gì khác"); ghi ngày **trước** `startPlayback()` (đỏ ở thứ tự); ghi ngày khi `startPlayback()` trả `false` (đỏ ở "Android từ chối"); `offerPlayButton` ở nhánh `Skip` (đỏ); bỏ `catch (e: CancellationException)` (đỏ); `epochDayUtc(now)` → `epochDayUtc(0)` (đỏ ở `PLAYED`); tính vào hạn mức khi `OFFERED` (đỏ). **(receiver/manifest):** `exported="false"` → `"true"` (đỏ); thêm action thứ hai (đỏ); thêm `BLUETOOTH_SCAN` (đỏ); bỏ `finally`/`finish()` (đỏ); `Dispatchers.IO` → `Dispatchers.Main` (đỏ); đưa `CommuteAutoPlayPolicy` vào receiver (đỏ).
- [ ] **Step 9: Commit:** `feat(strat-01): try to auto-play the morning episode when headphones connect`.

---

### Task 9: Cài đặt riêng tư: consent, giờ nghe, công tắc tự phát (xin quyền khi bật)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsViewModel.kt` (3 luồng trạng thái + 3 hàm đặt)
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy/PrivacySettingsPage.kt` (ba dòng, xin quyền)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/CommuteMorningSettingsViewModelTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/CommuteMorningSettingsSourceTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/CommuteMorningSettingsRowsTest.kt` (Compose)

**Interfaces:**
- Consumes: DataStore khóa mới (Task 3), `CommuteSchedulePolicy` (Task 1), `CommuteWorker.enqueueDailyWork(..., policy = REPLACE)` (Task 5), chuỗi `commute_*` (Task 6), `EditionTimeRow`, `formatTime`, `parseTime` (có sẵn trong `ZenSettingsPage.kt`, `internal`).
- Produces: trong `PrivacySettingsViewModel`: `commuteScriptGeminiConsent: StateFlow<Boolean>`, `commuteListenMinuteOfDay: StateFlow<Int>`, `commuteAutoPlayOnHeadset: StateFlow<Boolean>`; `fun setCommuteScriptGeminiConsent(agreed: Boolean)`, `fun setCommuteListenMinuteOfDay(minute: Int)` (ghi DataStore **rồi** gọi `CommuteWorker.enqueueDailyWork(WorkManager.getInstance(context), minute, ExistingPeriodicWorkPolicy.REPLACE)`), `fun setCommuteAutoPlayOnHeadset(on: Boolean)`. UI: công tắc consent (khuôn Oracle); dòng giờ nghe dùng `EditionTimeRow`; công tắc tự phát: **bật** ⇒ nếu Android 12+ và chưa có `BLUETOOTH_CONNECT` thì mở hộp xin quyền (`rememberLauncherForActivityResult(RequestPermission)`), được cấp ⇒ ghi `true`, từ chối ⇒ **không** ghi `true` và hiện `Toast`/dòng `commute_autoplay_permission_denied`; **tắt** ⇒ ghi `false` ngay.

- [ ] **Step 1: Viết test đỏ — view model** (Robolectric, khuôn `PrivacySettingsViewModel` hiện có: đọc `context.dataStore.data`)

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.commuteAutoPlayOnHeadset
import com.mckimquyen.reader.ui.ext.commuteListenMinuteOfDay
import com.mckimquyen.reader.ui.ext.commuteScriptGeminiConsent
import com.mckimquyen.reader.ui.ext.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommuteMorningSettingsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: PrivacySettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { context.dataStore.edit { it.clear() } }
        vm = PrivacySettingsViewModel(context)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun <T> eventually(read: () -> T, expected: T) = runBlocking {
        withTimeout(5_000) { while (read() != expected) delay(20) }
        assertEquals(expected, read())
    }

    @Test fun defaults_consentOff_0700_autoPlayOff() {
        assertEquals(false, vm.commuteScriptGeminiConsent.value)
        assertEquals(420, vm.commuteListenMinuteOfDay.value)
        assertEquals(false, vm.commuteAutoPlayOnHeadset.value)
    }

    @Test fun agreeing_writesTheConsent_andShowsItOn() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ context.commuteScriptGeminiConsent }, true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
    }

    @Test fun declining_afterAgreeing_writesFalse() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
        vm.setCommuteScriptGeminiConsent(false)
        eventually({ vm.commuteScriptGeminiConsent.value }, false)
        eventually({ context.commuteScriptGeminiConsent }, false)
    }

    @Test fun theNewConsent_doesNotTurnOnAnyOtherAiConsent() {
        vm.setCommuteScriptGeminiConsent(true)
        eventually({ vm.commuteScriptGeminiConsent.value }, true)
        assertEquals(false, vm.oracleClaimsGeminiConsent.value)
        assertEquals(false, vm.quizGeminiConsent.value)
        assertEquals(false, vm.deepDiveGeminiConsent.value)
        assertEquals(false, vm.podcastStudioGeminiConsent.value)
    }

    @Test fun changingTheListenTime_isSaved() {
        vm.setCommuteListenMinuteOfDay(8 * 60 + 15)
        eventually({ context.commuteListenMinuteOfDay }, 495)
        eventually({ vm.commuteListenMinuteOfDay.value }, 495)
    }

    @Test fun anOutOfRangeListenTime_isNotStored() {
        vm.setCommuteListenMinuteOfDay(99_999)
        eventually({ vm.commuteListenMinuteOfDay.value }, 420)
        assertEquals(420, context.commuteListenMinuteOfDay)
    }

    @Test fun turningAutoPlayOn_isSaved_andOffIsSaved() {
        vm.setCommuteAutoPlayOnHeadset(true)
        eventually({ context.commuteAutoPlayOnHeadset }, true)
        vm.setCommuteAutoPlayOnHeadset(false)
        eventually({ context.commuteAutoPlayOnHeadset }, false)
    }

    @Test fun savingTheListenTime_leavesTheOtherTwoSettingsAlone() {
        vm.setCommuteScriptGeminiConsent(true)
        vm.setCommuteAutoPlayOnHeadset(true)
        eventually({ vm.commuteAutoPlayOnHeadset.value }, true)
        vm.setCommuteListenMinuteOfDay(600)
        eventually({ vm.commuteListenMinuteOfDay.value }, 600)
        assertEquals(true, vm.commuteScriptGeminiConsent.value)
        assertEquals(true, vm.commuteAutoPlayOnHeadset.value)
    }
}
```

Ghi chú: giá trị ngoài `0..1439` phải bị **bỏ** ở `setCommuteListenMinuteOfDay` (`if (minute !in 0 until CommuteSchedulePolicy.MINUTES_PER_DAY) return`); test `anOutOfRangeListenTime_isNotStored` giữ điều đó. `CommuteWorker.enqueueDailyWork` trong test Robolectric dùng `WorkManager.getInstance(context)` thật: nếu chưa khởi tạo thì **khởi tạo bằng `WorkManagerTestInitHelper.initializeTestWorkManager(context)`** (đã có `work-testing` ở `androidTestImplementation`; nếu thiếu cho test JVM, **bọc lời gọi trong `runCatching` và log tên lớp** để lỗi lịch không làm mất việc lưu giờ, và thêm test "lưu giờ vẫn thành công khi WorkManager lỗi").

- [ ] **Step 2: Viết test nguồn đỏ — trang** (chỉ xét mã)

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommuteMorningSettingsSourceTest {

    private fun code(path: String) = File("src/main/java/com/mckimquyen/reader/$path").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    private val page = code("ui/page/setting/privacy/PrivacySettingsPage.kt")
    private val vm = code("ui/page/setting/privacy/PrivacySettingsViewModel.kt")

    @Test fun thePageShowsAllThreeSettings_fromStringResources() {
        listOf("commute_consent_title", "commute_consent_message", "commute_listen_time_title", "commute_listen_time_desc", "commute_autoplay_title", "commute_autoplay_desc")
            .forEach { assertTrue("page must use $it", page.contains("R.string.$it")) }
    }

    @Test fun theListenTimeRowReusesTheZenTimePicker() {
        assertTrue(page.contains("EditionTimeRow("))
    }

    @Test fun theBluetoothPermissionIsRequestedOnlyWhenTheSwitchIsTurnedOn() {
        assertEquals("one request site", 1, Regex("""BLUETOOTH_CONNECT""").findAll(page).count())
        val request = page.substringBefore("BLUETOOTH_CONNECT").takeLast(400)
        assertTrue("the request must sit inside the switch handler, not at page start", request.contains("commuteAutoPlayOnHeadset") || request.contains("setCommuteAutoPlayOnHeadset") || request.contains("onToggleAutoPlay"))
    }

    @Test fun aDeniedPermissionNeverSavesTheSwitchAsOn() {
        val denied = page.substringAfter("commute_autoplay_permission_denied").take(300)
        assertFalse(denied.contains("setCommuteAutoPlayOnHeadset(true)"))
    }

    @Test fun savingTheListenTimeReschedulesWithReplace_notKeep() {
        val body = vm.substringAfter("fun setCommuteListenMinuteOfDay(").substringBefore("fun setCommuteAutoPlayOnHeadset(")
        assertTrue(body.contains("ExistingPeriodicWorkPolicy.REPLACE"))
        assertFalse(body.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(body.contains("CommuteSchedulePolicy.MINUTES_PER_DAY"))
    }

    @Test fun aSchedulingFailureCannotLoseTheSavedTime() {
        val body = vm.substringAfter("fun setCommuteListenMinuteOfDay(").substringBefore("fun setCommuteAutoPlayOnHeadset(")
        assertTrue(body.indexOf("setCommuteListenMinuteOfDay") < body.indexOf("enqueueDailyWork("))
        assertTrue(body.contains("runCatching") || body.contains("catch (e: Exception)"))
    }

    @Test fun noAdsOrNetworkInTheseSettings() {
        val region = vm.substringAfter("commuteScriptGeminiConsent").take(2500)
        listOf("AdManager", "Rewarded", "OkHttp").forEach { assertFalse(region.contains(it, ignoreCase = true)) }
    }
}
```

- [ ] **Step 3: Viết test Compose đỏ — ba dòng** (Robolectric Compose, `en-rUS`, **một** `setContent` mỗi test; dùng thành phần tách riêng nhỏ `CommuteMorningSettingsRows(consent, listenTime, autoPlay, onConsent, onPickTime, onAutoPlay, permissionDenied)` để test không cần Hilt)

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommuteMorningSettingsRowsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val consent = mutableListOf<Boolean>()
    private val autoPlay = mutableListOf<Boolean>()

    private fun show(consentOn: Boolean = false, autoPlayOn: Boolean = false, denied: Boolean = false) = compose.setContent {
        CommuteMorningSettingsRows(
            consent = consentOn, listenMinuteOfDay = 7 * 60, autoPlay = autoPlayOn, permissionDenied = denied,
            activity = compose.activity,
            onConsent = { consent += it }, onPickTime = { _, _ -> }, onAutoPlay = { autoPlay += it },
        )
    }

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun showsTheThreeSettings_withTheirExplanations() {
        show()
        compose.onNodeWithText(str(R.string.commute_consent_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.commute_listen_time_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.commute_autoplay_title)).assertIsDisplayed()
    }

    @Test fun theListenTimeIsShownAs0700() {
        show()
        compose.onNodeWithText("07:00").assertIsDisplayed()
    }

    @Test fun tappingTheConsentRow_reportsTheOppositeOfTheCurrentValue() {
        show(consentOn = false)
        compose.onNodeWithText(str(R.string.commute_consent_title)).performClick()
        assertEquals(listOf(true), consent)
    }

    @Test fun tappingTheConsentRowWhenOn_reportsOff() {
        show(consentOn = true)
        compose.onNodeWithText(str(R.string.commute_consent_title)).performClick()
        assertEquals(listOf(false), consent)
    }

    @Test fun tappingTheAutoPlayRow_reportsTheOppositeOfTheCurrentValue() {
        show(autoPlayOn = false)
        compose.onNodeWithText(str(R.string.commute_autoplay_title)).performClick()
        assertEquals(listOf(true), autoPlay)
    }

    @Test fun aDeniedPermission_showsTheExplanation() {
        show(denied = true)
        compose.onNodeWithText(str(R.string.commute_autoplay_permission_denied)).assertIsDisplayed()
    }

    @Test fun withoutADenial_theDeniedExplanationIsAbsent() {
        show(denied = false)
        assertEquals(0, compose.onAllNodesWithTextCount(str(R.string.commute_autoplay_permission_denied)))
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
```

(`consentOn` mặc định **false**: kiểm "mặc định chưa đồng ý" ở tầng dữ liệu đã có ở Task 3 và ở view model; ở đây chỉ kiểm hành vi bấm.)

- [ ] **Step 4: Chạy đỏ** (biên dịch lỗi: luồng/hàm mới, `CommuteMorningSettingsRows`).

- [ ] **Step 5: Code tối thiểu — view model** (khuôn Oracle: `_flow` + `.collect`, `put` bằng `dataStore.edit`)

Trong `PrivacySettingsViewModel`, cạnh `_oracleClaimsGeminiConsent`:

```kotlin
    // STRAT-01: true only when the user agreed to Gemini writing the morning episode; "never asked" reads as off.
    private val _commuteScriptGeminiConsent = MutableStateFlow(false)
    val commuteScriptGeminiConsent = _commuteScriptGeminiConsent.asStateFlow()

    private val _commuteListenMinuteOfDay = MutableStateFlow(CommuteSchedulePolicy.DEFAULT_LISTEN_MINUTE_OF_DAY)
    val commuteListenMinuteOfDay = _commuteListenMinuteOfDay.asStateFlow()

    private val _commuteAutoPlayOnHeadset = MutableStateFlow(false)
    val commuteAutoPlayOnHeadset = _commuteAutoPlayOnHeadset.asStateFlow()
```

Trong khối `init { ... collect { preferences -> ... } }`:

```kotlin
                _commuteScriptGeminiConsent.value = preferences[DataStoreKeys.CommuteScriptGeminiConsent.key] == true
                _commuteListenMinuteOfDay.value = CommuteSchedulePolicy.clampListenMinute(preferences[DataStoreKeys.CommuteListenMinuteOfDay.key])
                _commuteAutoPlayOnHeadset.value = preferences[DataStoreKeys.CommuteAutoPlayOnHeadset.key] == true
```

và các hàm (khuôn `setOracleClaimsGeminiConsent`):

```kotlin
    fun setCommuteScriptGeminiConsent(agreed: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[DataStoreKeys.CommuteScriptGeminiConsent.key] = agreed }
        }
    }

    fun setCommuteListenMinuteOfDay(minute: Int) {
        if (minute !in 0 until CommuteSchedulePolicy.MINUTES_PER_DAY) return
        viewModelScope.launch {
            context.dataStore.edit { it[DataStoreKeys.CommuteListenMinuteOfDay.key] = minute }
            // The saved time must survive a scheduling failure, so the reschedule comes second and cannot undo it.
            runCatching {
                CommuteWorker.enqueueDailyWork(
                    WorkManager.getInstance(context), minute, ExistingPeriodicWorkPolicy.REPLACE,
                )
            }.onFailure { Log.w("PrivacySettingsVM", "Could not reschedule: ${it.javaClass.simpleName}") }
        }
    }

    fun setCommuteAutoPlayOnHeadset(on: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[DataStoreKeys.CommuteAutoPlayOnHeadset.key] = on }
        }
    }
```

(Dùng đúng kiểu ghi mà `setOracleClaimsGeminiConsent` đang dùng: `preferences.toMutablePreferences()` nếu mã hiện có không dùng `dataStore.edit`; **chép khuôn thật**, không đoán. `runCatching` ở đây bọc hàm **không suspend** nên không nuốt `CancellationException`; ghi chú vào mã. Thêm các `import`: `androidx.work.ExistingPeriodicWorkPolicy`, `androidx.work.WorkManager`, `com.mckimquyen.reader.domain.commute.CommuteSchedulePolicy`, `com.mckimquyen.reader.domain.sv.CommuteWorker`, `android.util.Log`.)

- [ ] **Step 6: Code tối thiểu — giao diện** `CommuteMorningSettingsRows` (cùng tệp `PrivacySettingsPage.kt` hoặc tệp riêng `CommuteMorningSettingsRows.kt` cùng gói; **tệp riêng** cho gọn) rồi chèn vào `PrivacySettingsPage` ngay sau dòng Oracle:

```kotlin
@Composable
internal fun CommuteMorningSettingsRows(
    consent: Boolean,
    listenMinuteOfDay: Int,
    autoPlay: Boolean,
    permissionDenied: Boolean,
    activity: android.app.Activity,
    onConsent: (Boolean) -> Unit,
    onPickTime: (hour: Int, minute: Int) -> Unit,
    onAutoPlay: (Boolean) -> Unit,
) {
    Column {
        SettingItem(
            title = stringResource(R.string.commute_consent_title),
            desc = stringResource(R.string.commute_consent_message),
            onClick = { onConsent(!consent) },
            action = { BaseSwitch(activated = consent) { onConsent(!consent) } },
        )
        EditionTimeRow(
            label = stringResource(R.string.commute_listen_time_title),
            time = formatTime(listenMinuteOfDay / 60, listenMinuteOfDay % 60),
            testTag = "commute_listen_time_row",
            activity = activity,
            onPick = onPickTime,
        )
        Text(
            stringResource(R.string.commute_listen_time_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingItem(
            title = stringResource(R.string.commute_autoplay_title),
            desc = stringResource(R.string.commute_autoplay_desc),
            onClick = { onAutoPlay(!autoPlay) },
            action = { BaseSwitch(activated = autoPlay) { onAutoPlay(!autoPlay) } },
        )
        if (permissionDenied) {
            Text(
                stringResource(R.string.commute_autoplay_permission_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
```

(Tiêu đề giờ nghe chỉ hiện **một lần**, ở `EditionTimeRow.label`; mô tả nằm ngay dưới.) Trong `PrivacySettingsPage`: thu các luồng mới bằng `collectAsState()`, giữ `var permissionDenied by remember { mutableStateOf(false) }`, và **bộ phát quyền**:

```kotlin
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            if (granted) viewModel.setCommuteAutoPlayOnHeadset(true)
        }
        val onToggleAutoPlay: (Boolean) -> Unit = { turnOn ->
            if (!turnOn) {
                permissionDenied = false
                viewModel.setCommuteAutoPlayOnHeadset(false)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            ) {
                permissionDenied = false
                viewModel.setCommuteAutoPlayOnHeadset(true)
            } else {
                // Asked only now, when the user turns the feature on; denying keeps the switch off.
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
```

- [ ] **Step 7: Chạy xanh** (8 + 7 + 7 test) + hồi quy `PrivacySettingsViewModel*Test`, `PrivacySettings*Test` hiện có.
- [ ] **Step 8: Mutation:** ghi `true` khi quyền bị từ chối (đỏ ở test nguồn "không ghi true"); đưa `launch(BLUETOOTH_CONNECT)` ra đầu trang (đỏ ở "chỉ khi bật"); `REPLACE` → `KEEP` (đỏ); bỏ `if (minute !in ...) return` (đỏ); đặt `enqueueDailyWork` **trước** khi lưu (đỏ ở thứ tự); bỏ `runCatching` (đỏ); consent ghi nhầm vào khóa Oracle (đỏ ở "không bật consent khác"); `!consent` → `consent` ở hàng consent (đỏ ở "bấm báo giá trị ngược"); bỏ dòng giờ nghe (đỏ).
- [ ] **Step 9: Commit:** `feat(strat-01): add the morning DJ consent, listen time and headset switch to privacy settings`.

---

### Task 10: Full suite, lint, androidTest, máy thật Tecno KJ7, báo cáo

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/setting/privacy/CommuteMorningSettingsWidgetTest.kt` (khuôn `OracleEvidenceWidgetTest`: `ActivityScenario` + `ComposeView` + duyệt cây semantics, **không** `ComposeTestRule`; dùng `CommuteMorningSettingsRows`; 4 test: ba dòng hiện; bấm dòng consent báo giá trị ngược; bấm dòng tự phát báo giá trị ngược; có từ chối quyền thì hiện lời giải thích và không có thì không)
- Tạm (không vào repo): `/tmp/s01-androidtest/MorningSeedTest.kt` copy vào `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/audio/MorningSeedTest.kt` **chỉ** khi chạy smoke rồi xóa. Đầu vào `-e mode`: `episodeFresh` (ghi một episode mẫu `date = now - 10 phút` vào `CommuteEpisodeStore` bằng `store.save(...)`, đúng đường mà Worker dùng), `episodeOld` (`date = now - 3 giờ`), `clearPlayed`, `clearAutoPlayDay` (xóa `commute_last_auto_play_epoch_day` bằng API DataStore, **không** xóa tệp), `setAutoPlayOn`, `setAutoPlayOff`, `setConsentOff`, `readState` (in `Log.i("S01", ...)` consent, giờ nghe, công tắc, ngày tự phát cuối, `isPlayed`). Mẫu: `/tmp/o03-androidtest/OracleDueSeedTest.kt`.
- Script: `/tmp/o03p-backup.py`, `/tmp/o03p-restore.py` (đã trỏ **KJ7**; chạy lại backup **mới** cho phiên này).

**Cổng thiết bị (chỉ đọc) trước mọi thao tác:**
1. `adb devices` (KJ7 `115333744A005844` phải có mặt; Pixel `2B051FDH3006MU` hay thiết bị khác nếu có mặt: **không chạm**).
2. `adb -s 115333744A005844 shell dumpsys activity activities | grep -E "Active instrumentation|topResumedActivity"`: không có instrumentation; foreground là launcher hoặc app của owner đã biết; app lạ ở foreground thì **dừng và hỏi**.
3. `adb -s ... shell ps -A | grep -iE "instrument|monkey|uiautomator"`: trống.
4. Màn hình mở khóa, **không** hộp sinh trắc (`dumpsys window | grep mCurrentFocus`); khóa hoặc có hộp thoại thì **nhờ owner xử lý trên máy**, không tự thao tác.

- [ ] **Step 1: Full suite sạch** (nền): `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/s01-full.log 2>&1; echo $? > /tmp/s01-full.exit`; **chờ `.exit`**; đếm từ XML (script Python: tổng `tests/skipped/failures/errors` trên `app/build/test-results/testDevDebugUnitTest/*.xml`) và `find ... -mmin +120` không ra tệp cũ. Kỳ vọng **0 lỗi** (nền trước **3.208**). Nếu treo thì kill "Gradle Test Executor" rồi chạy lại. Lỗi chỉ khi chạy chung = trạng thái tĩnh bị rò: tìm gốc, không loại test.
- [ ] **Step 2: Lint** (nền): `./gradlew lintDevDebug > /tmp/s01-lint.log 2>&1`; đếm `severity` từ `app/build/reports/lint-results-devDebug.xml` (nền **2.465** cảnh báo, 0 lỗi); liệt kê cảnh báo trong tệp STRAT-01 (kỳ vọng 0; nếu có thì sửa). **Chú ý riêng:** lint có thể đòi `@SuppressLint("MissingPermission")` hoặc kiểm quyền quanh `BluetoothDevice.bluetoothClass`; sửa đúng chỗ bằng kiểm quyền thật (đã có `hasPermission`), không bằng cách tắt cảnh báo bừa.
- [ ] **Step 3: Build** (nền): `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/s01-assemble.log 2>&1`; `exit=0`.
- [ ] **Step 4: Schema:** `git status --short app/schemas` **rỗng** (Room giữ v22).
- [ ] **Step 5: Viết `CommuteMorningSettingsWidgetTest`, build, commit riêng** trước khi chạm máy: `test(strat-01): cover the morning DJ settings rows on a real device`.
- [ ] **Step 6: Cổng thiết bị rồi sao lưu** (`python3 /tmp/o03p-backup.py`; ghi `integrity ok`, `user_version`, số bài). Cài APK dev + test APK: `adb -s 115333744A005844 install -r -t <apk>`.
- [ ] **Step 7: Chạy androidTest** (không `connected*`): `timeout 300 adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.page.setting.privacy.CommuteMorningSettingsWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; kỳ vọng `OK (4 tests)`. Hai mutation trên máy (sửa, build, cài, chạy, **phải đỏ**, khôi phục bằng `cmp`, cài lại HEAD sạch): bỏ dòng consent; đảo `!consent` thành `consent`.
- [ ] **Step 8: Smoke tay với app thật** (**không Gemini thật**; mọi thứ cục bộ). Quyền thông báo của app trên KJ7 đang tắt: **hỏi owner (AskUserQuestion) trước khi `pm grant POST_NOTIFICATIONS`** và thu hồi sau. Mỗi lần helper chạy xong app bị dừng nên mở lại bằng `SplashActivity`; quảng cáo **chỉ Back** (chờ rồi Back nhiều lần, kiểm `topResumedActivity`).
  1. Mở **Cài đặt riêng tư**: thấy hộp consent (**tắt**), dòng giờ nghe **07:00**, công tắc tự phát (**tắt**). `readState`: consent `null`, giờ `420`, công tắc `false`.
  2. **Consent tắt, soạn một bản tin** (nút trong sheet CommuteCast): bản tin phát được và là bản **soạn trong máy** ("CommuteCast Morning Briefing"). Đếm request ra mạng tới `generativelanguage.googleapis.com` trong khoảng thời gian đó bằng `adb logcat -d` hoặc `dumpsys netstats`/`tcpdump` nếu có: **0 request**. Nếu không đếm được request mạng một cách đáng tin trên KJ7, **ghi thẳng điều đó** (bằng chứng còn lại là test JVM "không có `newCall`" và test nguồn).
  3. **Bật consent** rồi soạn lại: với key chung hỏng, kết quả vẫn là bản soạn trong máy; **ghi rõ không thấy Gemini trả lời** (FIX-09); chỉ chứng minh được là consent được lưu (`readState`).
  4. **Đổi giờ nghe** sang giờ khác (ví dụ 08:30): `dumpsys jobscheduler | grep -A8 CommuteCastDailyWork` hoặc `adb shell dumpsys activity service SystemJobService` cho thấy **công việc duy nhất** `CommuteCastDailyWork` còn **một** bản và độ trễ khởi đầu khớp (giờ soạn = giờ nghe − 30 phút). Khởi động lại app: lịch **không** bị đặt lại về 07:00.
  5. **Bật công tắc tự phát**: Android hiện hộp xin quyền **Thiết bị ở gần**; **từ chối** ⇒ công tắc về tắt và hiện lời giải thích; bật lại và **cho phép** ⇒ công tắc bật (`readState`: `true`). **Quyền Bluetooth là quyền runtime: hỏi owner (AskUserQuestion) trước khi bấm cho phép trên máy**; bấm cho phép/từ chối là việc owner hoặc tôi **chỉ khi owner đồng ý**.
  6. `episodeFresh` + công tắc bật: **giả lập kết nối tai nghe** bằng `adb shell am broadcast -a android.bluetooth.device.action.ACL_CONNECTED -n com.mckimquyen.reader/.infrastructure.audio.BluetoothAudioReceiver`: tín hiệu giả này **không có `EXTRA_DEVICE`** nên receiver coi là không phải thiết bị âm thanh và **bỏ qua** (đó là hành vi **đúng**, dùng làm ca "thiết bị không phải âm thanh": không phát, không thông báo). **Nói thẳng trong báo cáo:** nhánh `PlayNow`/`OFFERED` với **thiết bị âm thanh thật** chỉ được kiểm bằng test JVM; nếu owner có tai nghe Bluetooth thì thử cắm thật (**hỏi owner**), nếu không thì ghi "chưa thử trên thiết bị thật".
  7. **Nút Phát:** gọi trực tiếp đường mà nút dùng: `adb shell am start-foreground-service -n com.mckimquyen.reader/.infrastructure.audio.CommuteMediaSessionService -a com.mckimquyen.reader.commute.ACTION_PLAY_LATEST` (hoặc chạm nút trên thông báo nếu thông báo hiện): thấy thông báo điều khiển media xuất hiện, `topResumedActivity` không bị chiếm, **âm thanh/`dumpsys media_session` cho thấy phiên đang phát**; `readState`: `isPlayed(episode.id) = true`.
  8. Hôm nay đã **tự phát** một lần (ghi `commute_last_auto_play_epoch_day` bằng `clearAutoPlayDay` rồi đặt lại, hoặc dựa vào test JVM): ghi rõ nhánh nào chỉ được kiểm bằng JVM.
  9. Thu hồi quyền: `pm revoke com.mckimquyen.reader android.permission.BLUETOOTH_CONNECT` và `POST_NOTIFICATIONS` nếu đã cấp.
- [ ] **Step 9: Khôi phục byte-identical** (`python3 /tmp/o03p-restore.py`): Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; quyền trả về trạng thái ban đầu (`granted=false`); gỡ test package (`adb -s 115333744A005844 uninstall com.mckimquyen.reader.test`); xóa `MorningSeedTest.kt` khỏi cây mã (`git status` sạch, **không** commit); `force-stop com.mckimquyen.reader`; cài lại bản HEAD sạch; `git diff --stat -- app/src/main` rỗng. **Lưu ý:** lịch WorkManager đã đổi nằm trong cơ sở dữ liệu của WorkManager (không nằm trong các tệp được sao lưu); ghi vào báo cáo rằng lịch trên máy có thể khác trước (đặt lại giờ về 07:00 trong Cài đặt để trả về mặc định).
- [ ] **Step 10:** Nếu bất kỳ ca nào đỏ: sửa mã bằng TDD (viết test JVM tái hiện trước), không sửa test cho khớp. Ghi mọi phát hiện vào báo cáo.
- [ ] **Step 11: Báo cáo và tài liệu**
  - Create `doc/task/done/STRAT-01_zero-click-autonomous-podcast-dj_DONE.md` (tiếng Việt, khuôn `ORACLE-03_automated-arbiter-agent_DONE.md`: Phạm vi nói thẳng, Đã làm, Kiểm chứng JVM, Kiểm chứng KJ7, Phát hiện khi làm, Chưa kiểm, Giới hạn, Commits). Chỉ số liệu thật từ XML, log và lệnh đã chạy. Nói thẳng: **không điều kiện sạc/pin** (Gherkin gốc có), **tự phát chỉ là cố gắng và bị Android chặn thì rơi về nút Phát**, "10 bài theo thói quen đọc" **không làm** (giữ bộ chọn DJ-08 hiện có), người dùng cũ chưa đồng ý nghe bản soạn trong máy, lỗi riêng tư đã có từ trước nay được sửa.
  - Update `doc/feature.md` (STRAT-01 sang ✅ bản thu hẹp với số liệu thật; nợ: nhánh tự phát với tai nghe thật, Gemini thật, lịch khi tiết kiệm pin, sạc/pin; ghi FIX-09 là việc kế tiếp).
  - Update spec nếu thực thi lệch (mục "Điều chỉnh khi thực thi").
  - Task gốc `doc/task/todo/STRAT-01_*.md`: **hỏi owner bằng AskUserQuestion** giữ trong `todo/` + ghi chú đầu file, hay chuyển sang `done/`. Gherkin gốc ("cắm sạc/pin > 50%", "tự chọn 10 bài theo thói quen đọc", "tự phát khi cắm tai nghe hoặc Android Auto lúc 07:00 mà không chạm màn hình") **chỉ làm một phần**: khuyến nghị giữ trong `todo/` với ghi chú rõ các điểm đó.
  - Commit `docs(strat-01): report the morning DJ with its verified and unverified parts`. Không push, không PR, không `.idea`.

**Không kiểm được (ghi thẳng vào báo cáo):** tự phát thật với tai nghe Bluetooth (trừ khi owner có và cho thử); Gemini thật; lịch đúng từng phút khi tiết kiệm pin; Android Auto qua tín hiệu này; TalkBack; ngôn ngữ ngoài EN và VI; Pixel và máy thứ hai (owner khóa chỉ Tecno).

---

## Self-review

**Spec coverage (từng mục của spec → task):**
- Consent riêng, mặc định chưa đồng ý, chưa đồng ý thì soạn trong máy, **không request, không đọc key**: Task 3 (khóa độc lập các consent khác), Task 4 (cổng `allowGemini`, test "không có `newCall`", test nguồn cho **cả hai** nơi gọi và "không còn nơi gọi nào khác"), Task 9 (công tắc + lời giải thích gửi gì).
- Giờ nghe tự chọn, mặc định 07:00, soạn trước 30 phút, đổi giờ có tác dụng: Task 1 (luật thuần, nửa đêm, đổi giờ mùa hè), Task 3 (khóa + clamp), Task 5 (`CommuteWorker` + `REPLACE` khi đổi, `KEEP` khi khởi động, kiểm bằng `javap` rằng `UPDATE` không tồn tại), Task 9 (dòng chọn giờ, lưu rồi mới đặt lại lịch).
- Tự phát khi cắm tai nghe, công tắc riêng mặc định TẮT, 2 giờ, 1 lần mỗi ngày, bỏ qua thiết bị không phải âm thanh, không chen ngang audio khác: Task 2 (luật), Task 3 (khóa công tắc, ngày cuối, cờ đã nghe), Task 8 (handler + receiver + manifest).
- Bị chặn thì hiện thông báo có nút Phát, **nút phải thật sự phát**: Task 7 (phát hiện `ACTION_PLAY` chỉ hiện thông báo; thêm `ACTION_PLAY_LATEST` + `playLatest` + đánh dấu đã nghe), Task 8 (`OFFERED` khi `startPlayback()` trả `false`, không dùng hạn mức ngày).
- Quyền `BLUETOOTH_CONNECT` chỉ khi bật công tắc, từ chối thì công tắc tắt: Task 8 (manifest, không thêm quyền rộng hơn), Task 9 (xin quyền trong handler công tắc, test nguồn "một chỗ xin", "không ghi true khi từ chối").
- Trạng thái ở DataStore, **Room v22 giữ**: Task 3, Task 10 Step 4.
- Chuỗi ×38: Task 6. Kiểm chứng JVM + androidTest + máy thật chỉ KJ7, thừa nhận không kiểm được tai nghe thật và Gemini thật: Task 10.
- Ngoài phạm vi (sạc/pin, báo thức chính xác, quảng cáo, Room v23, dependency mới, boot): Task 5 test "không ràng buộc sạc/pin", Task 8 test "không thêm quyền rộng hơn / không RECEIVE_BOOT_COMPLETED / không báo thức chính xác", test nguồn cấm mạng/Gemini/quảng cáo trong receiver và handler.

**Quét placeholder:** không còn "TBD"/"TODO"/"sau này". Ba chỗ có điều kiện được viết rõ cách xử lý (Task 4 Step 1 nếu `GeminiConfig.API_KEYS` rỗng trong test; Task 5 Step 1 nếu cần Robolectric; Task 9 Step 1 nếu `WorkManager` chưa khởi tạo) kèm quyết định cụ thể, không để ngỏ.

**Nhất quán kiểu và tên:** `CommuteSchedulePolicy.{DEFAULT_LISTEN_MINUTE_OF_DAY, PREPARE_LEAD_MINUTES, MINUTES_PER_DAY, clampListenMinute, prepareMinuteOfDay, initialDelayMillis}` (Task 1) khớp Task 3, 5, 9; `AutoPlayInput`/`AutoPlayDecision`/`SkipReason`/`CommuteAutoPlayPolicy.{decide, epochDayUtc, AUTO_PLAY_WINDOW_MS}` (Task 2) khớp Task 8; `commuteScriptGeminiConsent`, `commuteListenMinuteOfDay`, `commuteAutoPlayOnHeadset`, `commuteLastAutoPlayEpochDay` và `set*` (Task 3) khớp Task 4, 5, 8, 9; `CommuteEpisodeStore.{markPlayed, isPlayed}` (Task 3) khớp Task 7, 8; `generateScript(..., allowGemini)` và `CommuteEpisodePreparer(context, ...)` (Task 4); `CommuteWorker.enqueueDailyWork(workManager, listenMinuteOfDay, policy, nowMillis, zone)` (Task 5) khớp Task 9; khóa chuỗi `commute_*` (Task 6) khớp Task 7 và 9; `NotificationHelper.{COMMUTE_READY_NOTIFICATION_ID, notifyCommuteReadyToPlay}` và `CommuteMediaSessionService.{ACTION_PLAY_LATEST, playLatest}` (Task 7) khớp Task 8; `HeadsetAutoPlayHandler.{Deps, Result, onAudioDeviceConnected}` (Task 8). **Cần theo dõi khi thực thi:** `notifyCommuteReadyToPlay` có hoặc không có tham số `episodeTitle` (Task 7 cho phép bỏ do YAGNI; Task 8 phải gọi đúng).

**Rủi ro đã nêu:** Android có thể chặn tự phát (đã có nhánh nút Phát và nói thẳng); không có tai nghe Bluetooth để thử (ca giả lập `am broadcast` không có `EXTRA_DEVICE` nên chỉ chứng minh nhánh "không phải thiết bị âm thanh"); người dùng cũ chưa đồng ý sẽ nghe bản soạn trong máy; `runCatching` có sẵn ở `generateScript` vẫn nuốt `CancellationException` (hành vi có từ trước, không đổi trong task này, ghi vào "Phát hiện khi làm"); lịch WorkManager đổi không nằm trong sao lưu byte-exact (Task 10 Step 9 nói rõ); `goAsync()` có hạn khoảng 10 giây.
