# ORACLE-02 Betting Wallet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trên thẻ kèo của ORACLE-01, người dùng đặt cược điểm ảo Intel Points (50 / 100 / 500) vào Yes hoặc No bằng một ví cục bộ, rồi sau ngày đáo hạn tự báo kết quả để nhận hoặc mất điểm; không backend, không quảng cáo, không tiền.

**Architecture:** Luật điểm là phần thuần Kotlin (`OracleBettingPolicy`). Hai bảng Room mới (`user_wallet`, `oracle_bet`, Room v22, cược **không khóa ngoại**) được ghi qua `OracleWalletDao` với các hàm `@Transaction` (mẫu `BrainRpgDao.claimAndApplyReward`). `OracleBettingViewModel` quan sát ví và cược; `OracleBettingSection` vẽ 6 pha trong khe `footer` của `OracleClaimSheet`. Cược mở quá hạn 30 ngày được hoàn trong `OracleClaimExtractor.cleanUp`.

**Tech Stack:** Kotlin, Room v22, Compose Material3, Hilt, JUnit4 + MockK + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-12-oracle-02-betting-wallet-design.md` (commit `e72776c4`). Owner đã duyệt từng phần.
- **Room v22** + `MIGRATION_21_22` chỉ `CREATE TABLE/INDEX IF NOT EXISTS` (`user_wallet`, `oracle_bet`), không sửa bảng cũ, không `fallbackToDestructiveMigration`, xuất `app/schemas/.../22.json`; `21.json` không đổi. SQL migration phải khớp `createSql` trong `22.json` (đối chiếu bằng script).
- `oracle_bet` **không có khóa ngoại** (kèo bị dọn và `upsert` dùng `REPLACE`; bài có thể bị xóa; cược và điểm không được mất theo). `articleId` unique: mỗi bài một cược. Lưu bản chụp `question` và `expiresAt`.
- Hằng số (đặt tên, test số cứng): `STARTING_BALANCE = 1000`, `DAILY_GRANT = 100`, `BALANCE_CAP = 2000`, `STAKES = [50, 100, 500]`, `REFUND_AFTER_DAYS = 30`, `WIN_MULTIPLIER = 2`.
- Luật: thưởng ngày theo ngày UTC, không cộng dồn, không lùi khi đồng hồ chỉnh ngược, chỉ cộng khi `balance < 2000` (và không vượt trần); cược chỉ khi stake thuộc `STAKES`, `balance >= stake`, `now < expiresAt`, chưa cược bài đó; không đổi/hủy cược; chốt chỉ khi cược `OPEN` và `now >= expiresAt`: đúng cửa trả `stake × 2` (`WON`), sai cửa không trả (`LOST`), Không rõ trả `stake` (`REFUNDED`); quá `expiresAt + 30 ngày` mà còn `OPEN` thì tự hoàn `stake`. Số dư sau chốt không bị cắt bởi trần.
- **Không** quảng cáo/IAP/tiền, **không** nút xem quảng cáo, **không** AI Oracle Edge (ORACLE-04 hoãn), không tự chốt kèo (ORACLE-03), không tỷ lệ "cộng đồng" (không có backend), không âm thanh chip, không đụng Brain RPG (XP), không dependency mới, manifest không đổi.
- Thẻ ghi rõ: điểm ảo, không có giá trị, người dùng tự báo kết quả.
- Chuỗi mới đủ **38 locale** (EN và VI viết tay, 36 còn lại English), một script **all-or-none** (thư mục locale thật như `values-de-rDE`, không `values-de`); không `%d` ngay trước danh từ số nhiều (dùng `%1$s` dạng "Balance: %1$s"); chuỗi `oracle_sheet_ai_label` của ORACLE-01 đang nói "You cannot bet on it yet" nên **phải đổi** ở cả 38 file; `StringResourceParityTest` xanh.
- Đổi constructor Hilt (`OracleClaimExtractor` thêm `OracleWalletDao`, view model mới) thì biên dịch lại sạch: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks`.
- Code: không `!!`/`late` thiếu lý do, không magic number, mọi Job huỷ khi ViewModel clear, log không chứa nội dung kèo/câu hỏi (chỉ loại exception). Đích chạm ≥ 48 dp, mọi điều khiển có `contentDescription`/nhãn.
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML kết quả), code tối thiểu, xanh, mutation cho guard quan trọng (backup + `cmp`), commit riêng từng task, không chain `git commit` sau build nếu chưa kiểm exit code, ghi output Gradle vào file, `grep -r` có dấu nháy, không dùng `echo ====` trong zsh. Test giới hạn phải có test **số cứng**.
- Bài học đã gặp: (1) mutation sống sót làm lộ test yếu (thẻ `null` → Dialog rỗng cần `isDialog()`); (2) **zsh nội suy `$label` trong chuỗi nháy kép**: dùng nháy đơn cho `grep`/`sed` có `$`; (3) `sed -i` trên macOS cần đối số rỗng: dùng `python3` để sửa file; (4) `org.json` là stub trên JVM thuần nên test dùng `org.json` cần `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], application = Application::class)` (test luật thuần không dùng `org.json` thì JUnit thường); (5) test nguồn chỉ xét mã, bỏ dòng comment; (6) đếm kết quả từ **XML**, không chỉ exit code; (7) test ViewModel có Room/DataStore dùng dispatcher unconfined + chờ thời gian thực có hạn; (8) `*/` trong KDoc đóng comment; (9) khi một lệnh bị hủy giữa chừng, **kiểm file đột biến đã được khôi phục chưa** (`cmp` với bản gốc) trước khi làm tiếp; (10) quảng cáo toàn màn hình khi mở app cần nhiều lần Back, đôi khi sang trang Play Store của quảng cáo: tiếp tục chỉ Back, kiểm `topResumedActivity` sau mỗi lần.
- Thiết bị: **chỉ Tecno KJ7 `115333744A005844`** (Android 14). Không chạm Pixel `2B051FDH3006MU`, BG6, S24U, A50s. Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation, `topResumedActivity`), `ps` tiến trình lạ; dừng và hỏi nếu có. Chỉ `force-stop com.mckimquyen.reader`; `run-as` không `--user`; sao lưu byte-exact Room (+`-shm`/`-wal`), `files/datastore/settings.preferences_pb`, `shared_prefs/*.xml`; không xóa DataStore; không `connected*` (dùng `adb -s <serial> shell am instrument` với `timeout`); màn hình khóa thì nhờ owner mở. Quảng cáo: chỉ Back, không bấm "Continue to app". Máy có app lạ cùng họ `mckimquyen` (compass, lenslauncher, watermark): chỉ chạy runner `com.mckimquyen.reader.test`.
- **Không có key Gemini riêng**: không kiểm được kèo thật do AI sinh. Kèo mẫu để thử cược chèn bằng một instrumentation trợ giúp **không đưa vào repo**. Báo cáo phải nói rõ phần chưa kiểm.
- Báo cáo `doc/task/done/ORACLE-02_intel-points-betting-sheet_DONE.md`; task gốc xử lý theo owner chọn ở Task 9; cập nhật `doc/feature.md`. Không push, không PR, không `.idea`.

---

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `domain/model/oracle/OracleBetEnums.kt` (mới) | `OracleBetSide`, `OracleBetStatus`, `OracleOutcome` | 1 |
| `domain/oracle/OracleBettingPolicy.kt` (mới) | Hằng số, `checkBet`, `dailyGrant`, `settle`, `canSettle`, `shouldAutoRefund`, `epochDayUtc`, `phaseFor` (thuần) | 1 |
| `domain/model/oracle/UserWallet.kt`, `OracleBet.kt` (mới) | Entity + converters | 2 |
| `domain/repository/OracleWalletDao.kt` (mới) | Truy vấn và các hàm `@Transaction` | 2 |
| `infrastructure/db/AndroidDb.kt`, `infrastructure/di/DbModule.kt` (sửa), `app/schemas/.../22.json` (sinh) | Room v22, `MIGRATION_21_22`, DAO | 2 |
| `infrastructure/ai/OracleClaimExtractor.kt` (sửa) | Tự hoàn trước khi dọn kèo | 3 |
| `res/values*/strings.xml` ×38 (sửa) | 20 chuỗi `oracle_bet_*` + đổi `oracle_sheet_ai_label` | 4 |
| `ui/page/oracle/OracleBetting.kt` (mới) | `OracleBettingViewModel` (đồng hồ nhận qua tham số hàm, thay được trong test), `OracleBetUiState`, `buildOracleBetUiState` | 5 |
| `ui/component/oracle/OracleClaimSheet.kt` (sửa), `OracleBettingSection.kt` (mới), `ui/page/common/HomeEntry.kt` (sửa) | Khe `footer`, 6 pha, nối | 6 |
| `androidTest/.../OracleBettingMigration22Test.kt`, `OracleBettingSectionWidgetTest.kt` (mới) | Máy thật KJ7 | 7 |
| tài liệu | Báo cáo, feature.md, spec | 9 |

**Thứ tự thực thi:** Task 1 → 2 → 3 → **4 (chuỗi 38 locale) → 5 → 6** (chuỗi trước view model và giao diện để `R.string.oracle_bet_*` có sẵn mà parity không đỏ) → 7 → 8 → 9.

Quy ước lệnh test (mỗi lần chạy):
`./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/o02-<ten>.log 2>&1; echo exit=$? > /tmp/o02-<ten>.exit`
rồi đọc `app/build/test-results/testDevDebugUnitTest/TEST-<FQCN>.xml` (`tests=`, `failures=`, `errors=`, thông điệp lỗi) trước khi tin kết quả.

Chuẩn bị công cụ (một lần, trước Task 1):
```bash
sed 's/o01/o02/g' /tmp/o01_mut.sh > /tmp/o02_mut.sh && chmod +x /tmp/o02_mut.sh
sed 's/o01-/o02-/g; s#/tmp/o01-root#/tmp/o02-root#g' /tmp/o01-backup.py > /tmp/o02-backup.py
sed 's/o01-/o02-/g; s#/tmp/o01-root#/tmp/o02-root#g' /tmp/o01-restore.py > /tmp/o02-restore.py
```
`/tmp/o02_mut.sh <nhãn> <tệp> '<mẫu cũ>' '<mẫu mới>' <FQCN test>...` sao tệp, thay đúng một mẫu bằng `python3`, chạy test, **luôn khôi phục** và in `restored`/`NOT-RESTORED`; mẫu không tồn tại in `PATTERN-MISSING` (không tính là đã bắt). Truyền mẫu chứa `$` bằng **nháy đơn**.

---

### Task 1: Luật điểm thuần — `OracleBettingPolicy`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/oracle/OracleBetEnums.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/oracle/OracleBettingPolicy.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/oracle/OracleBettingPolicyTest.kt`

**Interfaces:**
- Produces:
  - `enum class OracleBetSide { YES, NO }`, `enum class OracleBetStatus { OPEN, WON, LOST, REFUNDED }`, `enum class OracleOutcome { YES, NO, UNCLEAR }`.
  - `enum class BetRejection { BAD_STAKE, EXPIRED, ALREADY_BET, INSUFFICIENT_BALANCE }`, `enum class OracleBetPhase { CAN_BET, OUT_OF_POINTS, BET_OPEN, AWAITING_RESULT, SETTLED, CLOSED_NO_BET }`, `data class WalletGrant(val balance: Long, val lastGrantEpochDay: Long)`, `data class OracleSettlement(val status: OracleBetStatus, val payout: Long)`.
  - `object OracleBettingPolicy`: `STARTING_BALANCE: Long`, `DAILY_GRANT: Long`, `BALANCE_CAP: Long`, `MIN_STAKE: Int`, `STAKES: List<Int>`, `REFUND_AFTER_DAYS: Int`, `WIN_MULTIPLIER: Int`, `DAY_MS: Long`; `checkBet(stake: Int, balance: Long, expiresAt: Long, now: Long, alreadyBet: Boolean): BetRejection?`; `dailyGrant(balance: Long, lastGrantEpochDay: Long, todayEpochDay: Long): WalletGrant`; `settle(side: OracleBetSide, stake: Int, outcome: OracleOutcome): OracleSettlement`; `canSettle(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean`; `shouldAutoRefund(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean`; `epochDayUtc(nowMillis: Long): Long`; `phaseFor(betStatus: OracleBetStatus?, expiresAt: Long, balance: Long, now: Long): OracleBetPhase`.

- [ ] **Step 1: Viết test đỏ** (JUnit thường, không `org.json`)

```kotlin
package com.mckimquyen.reader.domain.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleBettingPolicyTest {

    private val day = 86_400_000L
    private val expires = 10 * day

    @Test
    fun constants_areHardCodedNumbers() {
        assertEquals(1000L, OracleBettingPolicy.STARTING_BALANCE)
        assertEquals(100L, OracleBettingPolicy.DAILY_GRANT)
        assertEquals(2000L, OracleBettingPolicy.BALANCE_CAP)
        assertEquals(listOf(50, 100, 500), OracleBettingPolicy.STAKES)
        assertEquals(50, OracleBettingPolicy.MIN_STAKE)
        assertEquals(30, OracleBettingPolicy.REFUND_AFTER_DAYS)
        assertEquals(2, OracleBettingPolicy.WIN_MULTIPLIER)
        assertEquals(86_400_000L, OracleBettingPolicy.DAY_MS)
    }

    // --- checkBet

    private fun check(stake: Int = 100, balance: Long = 1000, exp: Long = expires, now: Long = 5 * day, already: Boolean = false) =
        OracleBettingPolicy.checkBet(stake, balance, exp, now, already)

    @Test fun checkBet_validBetIsAccepted() = assertNull(check())

    @Test fun checkBet_onlyTheThreeStakesAreAllowed() {
        listOf(50, 100, 500).forEach { assertNull("$it", check(stake = it, balance = 1000)) }
        listOf(0, 1, 49, 51, 75, 200, 501, -50).forEach { assertEquals("$it", BetRejection.BAD_STAKE, check(stake = it)) }
    }

    @Test fun checkBet_balanceBoundary() {
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(stake = 50, balance = 49))
        assertNull(check(stake = 50, balance = 50))
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(stake = 500, balance = 499))
        assertNull(check(stake = 500, balance = 500))
    }

    @Test fun checkBet_deadlineBoundary_isExclusive() {
        assertNull(check(now = expires - 1))
        assertEquals(BetRejection.EXPIRED, check(now = expires))
        assertEquals(BetRejection.EXPIRED, check(now = expires + 1))
    }

    @Test fun checkBet_oneBetPerArticle() {
        assertEquals(BetRejection.ALREADY_BET, check(already = true))
    }

    @Test fun checkBet_precedence_badStake_thenExpired_thenAlready_thenBalance() {
        assertEquals(BetRejection.BAD_STAKE, check(stake = 75, now = expires, already = true, balance = 0))
        assertEquals(BetRejection.EXPIRED, check(now = expires, already = true, balance = 0))
        assertEquals(BetRejection.ALREADY_BET, check(already = true, balance = 0))
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(balance = 0))
    }

    // --- dailyGrant

    @Test fun dailyGrant_sameDay_changesNothing() {
        assertEquals(WalletGrant(500, 7), OracleBettingPolicy.dailyGrant(500, 7, 7))
    }

    @Test fun dailyGrant_newDay_adds100_andMovesTheDay() {
        assertEquals(WalletGrant(600, 8), OracleBettingPolicy.dailyGrant(500, 7, 8))
    }

    @Test fun dailyGrant_missedDaysDoNotAccumulate() {
        assertEquals(WalletGrant(600, 20), OracleBettingPolicy.dailyGrant(500, 7, 20))
    }

    @Test fun dailyGrant_clockMovedBackwards_changesNothing_andKeepsTheLaterDay() {
        assertEquals(WalletGrant(500, 7), OracleBettingPolicy.dailyGrant(500, 7, 3))
    }

    @Test fun dailyGrant_capBoundary() {
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(1950, 7, 8))
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(1900, 7, 8))
        assertEquals(WalletGrant(1999, 8), OracleBettingPolicy.dailyGrant(1899, 7, 8))
    }

    @Test fun dailyGrant_atOrAboveTheCap_addsNothing_andNeverLowersTheBalance() {
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(2000, 7, 8))
        assertEquals(WalletGrant(2500, 8), OracleBettingPolicy.dailyGrant(2500, 7, 8))
    }

    // --- settle

    @Test fun settle_correctSide_paysDouble() {
        assertEquals(OracleSettlement(OracleBetStatus.WON, 200), OracleBettingPolicy.settle(OracleBetSide.YES, 100, OracleOutcome.YES))
        assertEquals(OracleSettlement(OracleBetStatus.WON, 1000), OracleBettingPolicy.settle(OracleBetSide.NO, 500, OracleOutcome.NO))
    }

    @Test fun settle_wrongSide_losesTheStake() {
        assertEquals(OracleSettlement(OracleBetStatus.LOST, 0), OracleBettingPolicy.settle(OracleBetSide.YES, 100, OracleOutcome.NO))
        assertEquals(OracleSettlement(OracleBetStatus.LOST, 0), OracleBettingPolicy.settle(OracleBetSide.NO, 50, OracleOutcome.YES))
    }

    @Test fun settle_unclear_refundsTheStake() {
        assertEquals(OracleSettlement(OracleBetStatus.REFUNDED, 50), OracleBettingPolicy.settle(OracleBetSide.YES, 50, OracleOutcome.UNCLEAR))
        assertEquals(OracleSettlement(OracleBetStatus.REFUNDED, 500), OracleBettingPolicy.settle(OracleBetSide.NO, 500, OracleOutcome.UNCLEAR))
    }

    // --- canSettle / shouldAutoRefund

    @Test fun canSettle_onlyAnOpenBetAtOrAfterTheDeadline() {
        assertFalse(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires - 1))
        assertTrue(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires))
        assertTrue(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires + 99 * day))
        listOf(OracleBetStatus.WON, OracleBetStatus.LOST, OracleBetStatus.REFUNDED).forEach {
            assertFalse("$it", OracleBettingPolicy.canSettle(it, expires, expires + day))
        }
    }

    @Test fun shouldAutoRefund_after30DaysExactly() {
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.OPEN, expires, expires + 30 * day - 1))
        assertTrue(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.OPEN, expires, expires + 30 * day))
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.WON, expires, expires + 99 * day))
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.REFUNDED, expires, expires + 99 * day))
    }

    // --- epochDayUtc

    @Test fun epochDayUtc_floorsAtMidnightUtc() {
        assertEquals(0L, OracleBettingPolicy.epochDayUtc(0))
        assertEquals(0L, OracleBettingPolicy.epochDayUtc(86_399_999))
        assertEquals(1L, OracleBettingPolicy.epochDayUtc(86_400_000))
        assertEquals(-1L, OracleBettingPolicy.epochDayUtc(-1))
    }

    // --- phaseFor

    private fun phase(status: OracleBetStatus?, now: Long, balance: Long = 1000) =
        OracleBettingPolicy.phaseFor(status, expires, balance, now)

    @Test fun phase_noBet() {
        assertEquals(OracleBetPhase.CAN_BET, phase(null, expires - 1))
        assertEquals(OracleBetPhase.CAN_BET, phase(null, expires - 1, balance = 50))
        assertEquals(OracleBetPhase.OUT_OF_POINTS, phase(null, expires - 1, balance = 49))
        assertEquals(OracleBetPhase.CLOSED_NO_BET, phase(null, expires))
        assertEquals(OracleBetPhase.CLOSED_NO_BET, phase(null, expires, balance = 0))
    }

    @Test fun phase_openBet() {
        assertEquals(OracleBetPhase.BET_OPEN, phase(OracleBetStatus.OPEN, expires - 1))
        assertEquals(OracleBetPhase.AWAITING_RESULT, phase(OracleBetStatus.OPEN, expires))
    }

    @Test fun phase_closedBet_isSettled() {
        listOf(OracleBetStatus.WON, OracleBetStatus.LOST, OracleBetStatus.REFUNDED).forEach {
            assertEquals("$it", OracleBetPhase.SETTLED, phase(it, expires - 1))
            assertEquals("$it", OracleBetPhase.SETTLED, phase(it, expires + day))
        }
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (`Unresolved reference: OracleBettingPolicy`, `OracleBetStatus`, ... đọc trong `/tmp/o02-t1.log`).

- [ ] **Step 3: Code tối thiểu**

`domain/model/oracle/OracleBetEnums.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.oracle

enum class OracleBetSide { YES, NO }

/** OPEN: stake taken, result unknown; WON/LOST/REFUNDED are final. */
enum class OracleBetStatus { OPEN, WON, LOST, REFUNDED }

/** What the user reports once the prediction's date has passed. UNCLEAR returns the stake. */
enum class OracleOutcome { YES, NO, UNCLEAR }
```

`domain/oracle/OracleBettingPolicy.kt`:

```kotlin
package com.mckimquyen.reader.domain.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome

enum class BetRejection { BAD_STAKE, EXPIRED, ALREADY_BET, INSUFFICIENT_BALANCE }

enum class OracleBetPhase { CAN_BET, OUT_OF_POINTS, BET_OPEN, AWAITING_RESULT, SETTLED, CLOSED_NO_BET }

data class WalletGrant(val balance: Long, val lastGrantEpochDay: Long)

data class OracleSettlement(val status: OracleBetStatus, val payout: Long)

/**
 * Pure rules of the local prediction wallet (ORACLE-02). Virtual points only: nothing here touches money, ads or a server.
 */
object OracleBettingPolicy {
    const val STARTING_BALANCE = 1000L
    const val DAILY_GRANT = 100L
    const val BALANCE_CAP = 2000L
    const val MIN_STAKE = 50
    val STAKES = listOf(MIN_STAKE, 100, 500)
    const val REFUND_AFTER_DAYS = 30
    const val WIN_MULTIPLIER = 2
    const val DAY_MS = 86_400_000L

    /** First failing rule wins, in this order: stake, deadline, one bet per article, balance. */
    fun checkBet(stake: Int, balance: Long, expiresAt: Long, now: Long, alreadyBet: Boolean): BetRejection? = when {
        stake !in STAKES -> BetRejection.BAD_STAKE
        now >= expiresAt -> BetRejection.EXPIRED
        alreadyBet -> BetRejection.ALREADY_BET
        balance < stake -> BetRejection.INSUFFICIENT_BALANCE
        else -> null
    }

    /** One grant per UTC day; never accumulates, never goes back in time, never lowers a balance above the cap. */
    fun dailyGrant(balance: Long, lastGrantEpochDay: Long, todayEpochDay: Long): WalletGrant {
        if (todayEpochDay <= lastGrantEpochDay) return WalletGrant(balance, lastGrantEpochDay)
        val granted = if (balance >= BALANCE_CAP) balance else minOf(balance + DAILY_GRANT, BALANCE_CAP)
        return WalletGrant(granted, todayEpochDay)
    }

    fun settle(side: OracleBetSide, stake: Int, outcome: OracleOutcome): OracleSettlement {
        val won = (side == OracleBetSide.YES && outcome == OracleOutcome.YES) ||
            (side == OracleBetSide.NO && outcome == OracleOutcome.NO)
        return when {
            outcome == OracleOutcome.UNCLEAR -> OracleSettlement(OracleBetStatus.REFUNDED, stake.toLong())
            won -> OracleSettlement(OracleBetStatus.WON, stake.toLong() * WIN_MULTIPLIER)
            else -> OracleSettlement(OracleBetStatus.LOST, 0L)
        }
    }

    fun canSettle(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean =
        status == OracleBetStatus.OPEN && now >= expiresAt

    fun shouldAutoRefund(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean =
        status == OracleBetStatus.OPEN && now >= expiresAt + REFUND_AFTER_DAYS * DAY_MS

    fun epochDayUtc(nowMillis: Long): Long = Math.floorDiv(nowMillis, DAY_MS)

    fun phaseFor(betStatus: OracleBetStatus?, expiresAt: Long, balance: Long, now: Long): OracleBetPhase = when {
        betStatus == null -> when {
            now >= expiresAt -> OracleBetPhase.CLOSED_NO_BET
            balance < MIN_STAKE -> OracleBetPhase.OUT_OF_POINTS
            else -> OracleBetPhase.CAN_BET
        }
        betStatus == OracleBetStatus.OPEN -> if (now < expiresAt) OracleBetPhase.BET_OPEN else OracleBetPhase.AWAITING_RESULT
        else -> OracleBetPhase.SETTLED
    }
}
```

- [ ] **Step 4: Chạy lại, xác nhận xanh** (`OracleBettingPolicyTest`: 19 test, `failures=0`).

- [ ] **Step 5: Mutation (backup + `cmp`)**, trên `OracleBettingPolicy.kt`, test `com.mckimquyen.reader.domain.oracle.OracleBettingPolicyTest`:
  - `const val BALANCE_CAP = 2000L` → `2001L` (số cứng đỏ).
  - `if (todayEpochDay <= lastGrantEpochDay)` → `if (todayEpochDay < lastGrantEpochDay)` (cùng ngày bị cộng lần hai).
  - `if (balance >= BALANCE_CAP) balance else` → `if (balance > BALANCE_CAP) balance else` (đỏ ở ca đúng 2000 hoặc 2500).
  - `now >= expiresAt -> BetRejection.EXPIRED` → `now > expiresAt -> BetRejection.EXPIRED` (biên hạn).
  - `balance < stake -> BetRejection.INSUFFICIENT_BALANCE` → `balance <= stake -> ...` (biên số dư).
  - `status == OracleBetStatus.OPEN && now >= expiresAt` ở `canSettle` → `now > expiresAt`.
  - `now >= expiresAt + REFUND_AFTER_DAYS * DAY_MS` → `now > expiresAt + REFUND_AFTER_DAYS * DAY_MS`.
  - `stake.toLong() * WIN_MULTIPLIER` → `stake.toLong() * 3`.
  - `Math.floorDiv(nowMillis, DAY_MS)` → `nowMillis / DAY_MS` (ca `-1`).
  - Đổi thứ tự `stake !in STAKES` xuống cuối (test precedence đỏ).

- [ ] **Step 6: Commit** (kiểm `exit=0` và XML): `feat(oracle-02): add the pure wallet and betting rules`.

---

### Task 2: Bảng `user_wallet` và `oracle_bet`, `OracleWalletDao` (transaction), Room v22

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/oracle/UserWallet.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/oracle/OracleBet.kt` (entity + converters)
- Create: `app/src/main/java/com/mckimquyen/reader/domain/repository/OracleWalletDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt` (entities, `version = 22`, `@TypeConverters`, `abstract fun oracleWalletDao()`, `allMigrations`, `MIGRATION_21_22`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/DbModule.kt` (provide DAO)
- Sinh: `app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/22.json`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration21To22Test.kt`, `app/src/test/java/com/mckimquyen/reader/domain/repository/OracleWalletDaoTest.kt`

**Interfaces:**
- Consumes: `OracleBettingPolicy`, `OracleBetSide`, `OracleBetStatus`, `OracleOutcome`, `BetRejection` (Task 1); `OracleMarketDao` (ORACLE-01).
- Produces:
  - `@Entity(tableName = "user_wallet") data class UserWallet(@PrimaryKey val id: Int = SINGLETON_ID, val balance: Long, val lastGrantEpochDay: Long)` với `const val SINGLETON_ID = 1`.
  - `@Entity(tableName = "oracle_bet", indices = [Index(value = ["articleId"], unique = true)]) data class OracleBet(@PrimaryKey(autoGenerate = true) val id: Long = 0, val articleId: String, val question: String, val expiresAt: Long, val side: OracleBetSide, val stake: Int, val status: OracleBetStatus, val placedAt: Long, val settledAt: Long? = null)`. **Không có `foreignKeys`.**
  - `class OracleBetConverters` (`OracleBetSide`, `OracleBetStatus` ↔ `String` bằng `name`).
  - `sealed interface PlaceBetResult { data class Placed(val bet: OracleBet, val balance: Long) ; data class Rejected(val reason: BetRejection) }` và `sealed interface SettleResult { data class Settled(val bet: OracleBet, val payout: Long, val balance: Long) ; data object NotSettleable }` (đặt trong `OracleWalletDao.kt`).
  - `OracleWalletDao` (abstract class, `@Dao`): `observeWallet(): Flow<UserWallet?>`; `observeBet(articleId: String): Flow<OracleBet?>`; `suspend fun wallet(): UserWallet?`; `suspend fun betFor(articleId: String): OracleBet?`; `suspend fun ensureWalletAndGrant(nowMillis: Long): UserWallet` (`@Transaction`); `suspend fun placeBet(articleId: String, question: String, expiresAt: Long, side: OracleBetSide, stake: Int, nowMillis: Long): PlaceBetResult` (`@Transaction`); `suspend fun settleBet(articleId: String, outcome: OracleOutcome, nowMillis: Long): SettleResult` (`@Transaction`); `suspend fun autoRefundStale(nowMillis: Long): Int` (`@Transaction`).
  - Các hàm bảo vệ (`protected`/`abstract`): `insertWalletIfAbsent(UserWallet): Long` (`IGNORE`), `updateWallet(balance, lastGrantEpochDay)`, `insertBetIfAbsent(OracleBet): Long` (`IGNORE`), `updateBetSettled(id, status, settledAt)` chỉ khi `status = 'OPEN'` (trả số dòng), `openBets(): List<OracleBet>`.

- [ ] **Step 1: Viết test migration đỏ** (khuôn `RoomMigration20To21Test`; bảng `article` giả; hai bảng mới **không** tham chiếu `article`)

```kotlin
package com.mckimquyen.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigration21To22Test {

    private fun openAtVersion21(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
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
    fun migration21to22_createsBothTablesAndKeepsArticles() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)

        assertEquals(1L, scalarLong(db, "SELECT COUNT(*) FROM article"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM user_wallet"))
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        assertEquals(listOf("id", "balance", "lastGrantEpochDay"), columns(db, "user_wallet"))
        assertEquals(
            listOf("id", "articleId", "question", "expiresAt", "side", "stake", "status", "placedAt", "settledAt"),
            columns(db, "oracle_bet"),
        )
        assertTrue(indices(db, "oracle_bet").contains("index_oracle_bet_articleId"))
        db.close()
    }

    @Test
    fun migration21to22_isIdempotent() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        MIGRATION_21_22.migrate(db)
        assertEquals(0L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        db.close()
    }

    @Test
    fun oneBetPerArticle() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'YES', 50, 'OPEN', 1)")
        val failed = runCatching {
            db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'NO', 50, 'OPEN', 2)")
        }.isFailure
        assertTrue("a second bet for the same article must be refused", failed)
        db.close()
    }

    @Test
    fun aBetSurvivesTheDeletionOfItsArticle_becauseItHasNoForeignKey() {
        val db = openAtVersion21()
        MIGRATION_21_22.migrate(db)
        db.execSQL("INSERT INTO oracle_bet (articleId, question, expiresAt, side, stake, status, placedAt) VALUES ('a1', 'Q?', 9, 'YES', 100, 'OPEN', 1)")
        db.execSQL("DELETE FROM article WHERE id='a1'")
        assertEquals("points staked must not vanish with the article", 1L, scalarLong(db, "SELECT COUNT(*) FROM oracle_bet"))
        assertFalse(foreignKeysOf(db, "oracle_bet").isNotEmpty())
        db.close()
    }

    @Test
    fun migrationTouchesNothingButTheTwoNewTables() {
        val db = openAtVersion21()
        val before = tableNames(db)
        MIGRATION_21_22.migrate(db)
        assertEquals(before + setOf("user_wallet", "oracle_bet"), tableNames(db))
        db.close()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun indices(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun foreignKeysOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("table"))) }
        }

    private fun tableNames(db: SupportSQLiteDatabase): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun scalarLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }
}
```

- [ ] **Step 2: Viết test DAO đỏ** (Room in-memory; **không cần** bảng `article` vì cược không có khóa ngoại, nhưng `AndroidDatabase` vẫn dựng đủ):

```kotlin
package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.BetRejection
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleWalletDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao

    private val day = 86_400_000L
    private val now = 100 * day + 3_600_000L // 01:00 UTC of epoch day 100
    private val expires = now + 10 * day

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
    }

    @After
    fun tearDown() = db.close()

    private fun balance(): Long = runBlocking { dao.wallet()?.balance ?: -1L }

    private fun bet(article: String = "a", stake: Int = 100, side: OracleBetSide = OracleBetSide.YES, exp: Long = expires, at: Long = now) =
        runBlocking { dao.placeBet(article, "Q-$article?", exp, side, stake, at) }

    // --- wallet

    @Test
    fun firstUse_createsTheWalletWithTheStartingBalance() = runBlocking {
        val w = dao.ensureWalletAndGrant(now)
        assertEquals(1000L, w.balance)
        assertEquals(100L, w.lastGrantEpochDay)
        assertEquals(1, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM user_wallet").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun sameDay_secondCallGrantsNothing() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(1000L, dao.ensureWalletAndGrant(now + 3_600_000L).balance)
    }

    @Test
    fun nextDay_grantsOnce_thenNothingMoreThatDay() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(1100L, dao.ensureWalletAndGrant(now + day).balance)
        assertEquals(1100L, dao.ensureWalletAndGrant(now + day + 1000).balance)
    }

    @Test
    fun grantStopsAtTheCap() = runBlocking {
        dao.ensureWalletAndGrant(now)
        var t = now
        repeat(15) { t += day; dao.ensureWalletAndGrant(t) }
        assertEquals(2000L, balance())
    }

    // --- placeBet

    @Test
    fun placeBet_takesThePointsAndWritesTheBet_inOneStep() = runBlocking {
        val r = bet(stake = 100)
        assertTrue(r is PlaceBetResult.Placed)
        r as PlaceBetResult.Placed
        assertEquals(900L, r.balance)
        assertEquals(900L, balance())
        val b = dao.betFor("a")!!
        assertEquals(OracleBetStatus.OPEN, b.status)
        assertEquals(100, b.stake)
        assertEquals(OracleBetSide.YES, b.side)
        assertEquals("Q-a?", b.question)
        assertEquals(expires, b.expiresAt)
        assertEquals(now, b.placedAt)
        assertNull(b.settledAt)
    }

    @Test
    fun placeBet_rejections_changeNothing() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(BetRejection.BAD_STAKE, (bet(stake = 75) as PlaceBetResult.Rejected).reason)
        assertEquals(BetRejection.EXPIRED, (bet(exp = now) as PlaceBetResult.Rejected).reason)
        assertEquals(1000L, balance())
        assertNull(dao.betFor("a"))
    }

    @Test
    fun placeBet_insufficientBalance_isRejected() = runBlocking {
        bet(article = "a1", stake = 500); bet(article = "a2", stake = 500) // 1000 -> 0
        assertEquals(0L, balance())
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, (bet(article = "a3", stake = 50) as PlaceBetResult.Rejected).reason)
        assertEquals(0L, balance())
    }

    @Test
    fun placeBet_secondBetOnTheSameArticle_isRejected_andChargesNothing() = runBlocking {
        bet(stake = 100)
        assertEquals(BetRejection.ALREADY_BET, (bet(stake = 50, side = OracleBetSide.NO) as PlaceBetResult.Rejected).reason)
        assertEquals(900L, balance())
        assertEquals(OracleBetSide.YES, dao.betFor("a")!!.side)
    }

    @Test
    fun placeBet_concurrentDoubleTap_chargesExactlyOnce() = runBlocking {
        dao.ensureWalletAndGrant(now)
        val results = (1..8).map { async(kotlinx.coroutines.Dispatchers.IO) { bet(stake = 100) } }.awaitAll()
        assertEquals(1, results.count { it is PlaceBetResult.Placed })
        assertEquals(900L, balance())
    }

    // --- settleBet

    @Test
    fun settle_beforeTheDeadline_isRefused() = runBlocking {
        bet()
        assertEquals(SettleResult.NotSettleable, dao.settleBet("a", OracleOutcome.YES, expires - 1))
        assertEquals(OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
    }

    @Test
    fun settle_correctSide_paysDouble() = runBlocking {
        bet(stake = 100, side = OracleBetSide.YES)
        val r = dao.settleBet("a", OracleOutcome.YES, expires) as SettleResult.Settled
        assertEquals(200L, r.payout)
        assertEquals(1100L, r.balance)
        assertEquals(1100L, balance())
        val b = dao.betFor("a")!!
        assertEquals(OracleBetStatus.WON, b.status)
        assertEquals(expires, b.settledAt)
    }

    @Test
    fun settle_wrongSide_losesTheStake() = runBlocking {
        bet(stake = 100, side = OracleBetSide.YES)
        val r = dao.settleBet("a", OracleOutcome.NO, expires) as SettleResult.Settled
        assertEquals(0L, r.payout)
        assertEquals(900L, balance())
        assertEquals(OracleBetStatus.LOST, dao.betFor("a")!!.status)
    }

    @Test
    fun settle_unclear_refundsTheStake() = runBlocking {
        bet(stake = 100)
        dao.settleBet("a", OracleOutcome.UNCLEAR, expires)
        assertEquals(1000L, balance())
        assertEquals(OracleBetStatus.REFUNDED, dao.betFor("a")!!.status)
    }

    @Test
    fun settle_twice_paysOnce() = runBlocking {
        bet(stake = 100)
        dao.settleBet("a", OracleOutcome.YES, expires)
        assertEquals(SettleResult.NotSettleable, dao.settleBet("a", OracleOutcome.YES, expires + 1))
        assertEquals(1100L, balance())
    }

    @Test
    fun settle_concurrentDoubleTap_paysExactlyOnce() = runBlocking {
        bet(stake = 100)
        val results = (1..8).map { async(kotlinx.coroutines.Dispatchers.IO) { dao.settleBet("a", OracleOutcome.YES, expires) } }.awaitAll()
        assertEquals(1, results.count { it is SettleResult.Settled })
        assertEquals(1100L, balance())
    }

    @Test
    fun settle_withoutABet_isRefused() = runBlocking {
        assertEquals(SettleResult.NotSettleable, dao.settleBet("nope", OracleOutcome.YES, expires))
    }

    @Test
    fun settledBalanceIsNotCutByTheCap() = runBlocking {
        dao.ensureWalletAndGrant(now)
        var t = now
        repeat(15) { t += day; dao.ensureWalletAndGrant(t) } // 2000
        val placed = runBlocking { dao.placeBet("big", "Q?", t + 10 * day, OracleBetSide.YES, 500, t) }
        assertTrue(placed is PlaceBetResult.Placed)
        val r = dao.settleBet("big", OracleOutcome.YES, t + 10 * day) as SettleResult.Settled
        assertEquals(2500L, r.balance)
    }

    // --- autoRefundStale

    @Test
    fun autoRefund_returnsStakesOfBetsOpenFor30DaysPastTheDeadline_only() = runBlocking {
        bet(article = "stale", stake = 100, exp = now + day)
        bet(article = "fresh", stake = 50, exp = now + day)
        dao.settleBet("fresh", OracleOutcome.YES, now + day) // settled, must not be touched again
        val t = now + day + 30 * day
        val n = dao.autoRefundStale(t)
        assertEquals(1, n)
        assertEquals(OracleBetStatus.REFUNDED, dao.betFor("stale")!!.status)
        assertEquals(OracleBetStatus.WON, dao.betFor("fresh")!!.status)
        // 1000 -100 -50 = 850; fresh WON pays 100 -> 950; stale refunded 100 -> 1050
        assertEquals(1050L, balance())
    }

    @Test
    fun autoRefund_justBeforeTheMark_doesNothing() = runBlocking {
        bet(stake = 100, exp = now + day)
        assertEquals(0, dao.autoRefundStale(now + day + 30 * day - 1))
        assertEquals(OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
    }

    @Test
    fun autoRefund_isIdempotent() = runBlocking {
        bet(stake = 100, exp = now + day)
        val t = now + day + 30 * day
        dao.autoRefundStale(t)
        assertEquals(0, dao.autoRefundStale(t + day))
        assertEquals(1000L, balance())
    }

    // --- observers and independence

    @Test
    fun observers_emitTheCurrentRows() = runBlocking {
        assertNull(dao.observeWallet().first())
        assertNull(dao.observeBet("a").first())
        bet()
        assertEquals(900L, dao.observeWallet().first()!!.balance)
        assertNotNull(dao.observeBet("a").first())
    }

    @Test
    fun betsDoNotDependOnOracleMarketRows() = runBlocking {
        // The prediction row is deleted by the ORACLE-01 cleanup; the bet and its points must remain.
        bet(stake = 100)
        db.openHelper.writableDatabase.execSQL("DELETE FROM oracle_market")
        assertNotNull(dao.betFor("a"))
        assertEquals(900L, balance())
    }
}
```

- [ ] **Step 3: Chạy, xác nhận đỏ** (`Unresolved reference: MIGRATION_21_22`, `oracleWalletDao`, `OracleWalletDao`, ...).

- [ ] **Step 4: Code tối thiểu**

`domain/model/oracle/UserWallet.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The one local wallet of virtual Intel Points (ORACLE-02). No money, ads or purchases ever feed it. */
@Entity(tableName = "user_wallet")
@Keep
data class UserWallet(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val balance: Long,
    val lastGrantEpochDay: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
```

`domain/model/oracle/OracleBet.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class OracleBetConverters {
    @TypeConverter
    fun sideToStorage(value: OracleBetSide): String = value.name

    @TypeConverter
    fun sideFromStorage(value: String): OracleBetSide = OracleBetSide.valueOf(value)

    @TypeConverter
    fun statusToStorage(value: OracleBetStatus): String = value.name

    @TypeConverter
    fun statusFromStorage(value: String): OracleBetStatus = OracleBetStatus.valueOf(value)
}

/**
 * One stake on one prediction. Deliberately NO foreign key: the prediction row is replaced and later cleaned up
 * (ORACLE-01) and an article can be deleted, and neither may make staked points disappear, so the question and the
 * deadline are copied here.
 */
@Entity(tableName = "oracle_bet", indices = [Index(value = ["articleId"], unique = true)])
@Keep
data class OracleBet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: String,
    val question: String,
    val expiresAt: Long,
    val side: OracleBetSide,
    val stake: Int,
    val status: OracleBetStatus,
    val placedAt: Long,
    val settledAt: Long? = null,
)
```

`domain/repository/OracleWalletDao.kt`:

```kotlin
package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.BetRejection
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import kotlinx.coroutines.flow.Flow

sealed interface PlaceBetResult {
    data class Placed(val bet: OracleBet, val balance: Long) : PlaceBetResult
    data class Rejected(val reason: BetRejection) : PlaceBetResult
}

sealed interface SettleResult {
    data class Settled(val bet: OracleBet, val payout: Long, val balance: Long) : SettleResult
    data object NotSettleable : SettleResult
}

/**
 * Every money-like change (grant, stake, payout, refund) is one `@Transaction` that re-checks its own preconditions
 * inside the transaction, so a double tap or a repeated call can never charge or pay twice (pattern of
 * BrainRpgDao.claimAndApplyReward).
 */
@Dao
abstract class OracleWalletDao {

    @Query("SELECT * FROM user_wallet WHERE id = 1")
    abstract fun observeWallet(): Flow<UserWallet?>

    @Query("SELECT * FROM oracle_bet WHERE articleId = :articleId")
    abstract fun observeBet(articleId: String): Flow<OracleBet?>

    @Query("SELECT * FROM user_wallet WHERE id = 1")
    abstract suspend fun wallet(): UserWallet?

    @Query("SELECT * FROM oracle_bet WHERE articleId = :articleId")
    abstract suspend fun betFor(articleId: String): OracleBet?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertWalletIfAbsent(wallet: UserWallet): Long

    @Query("UPDATE user_wallet SET balance = :balance, lastGrantEpochDay = :lastGrantEpochDay WHERE id = 1")
    protected abstract suspend fun updateWallet(balance: Long, lastGrantEpochDay: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBetIfAbsent(bet: OracleBet): Long

    @Query("UPDATE oracle_bet SET status = :status, settledAt = :settledAt WHERE id = :id AND status = 'OPEN'")
    protected abstract suspend fun closeOpenBet(id: Long, status: OracleBetStatus, settledAt: Long): Int

    @Query("SELECT * FROM oracle_bet WHERE status = 'OPEN'")
    protected abstract suspend fun openBets(): List<OracleBet>

    @Transaction
    open suspend fun ensureWalletAndGrant(nowMillis: Long): UserWallet {
        val today = OracleBettingPolicy.epochDayUtc(nowMillis)
        insertWalletIfAbsent(UserWallet(balance = OracleBettingPolicy.STARTING_BALANCE, lastGrantEpochDay = today))
        val current = requireNotNull(wallet()) { "wallet row must exist right after insertWalletIfAbsent" }
        val granted = OracleBettingPolicy.dailyGrant(current.balance, current.lastGrantEpochDay, today)
        if (granted.balance == current.balance && granted.lastGrantEpochDay == current.lastGrantEpochDay) return current
        updateWallet(granted.balance, granted.lastGrantEpochDay)
        return current.copy(balance = granted.balance, lastGrantEpochDay = granted.lastGrantEpochDay)
    }

    @Transaction
    open suspend fun placeBet(
        articleId: String,
        question: String,
        expiresAt: Long,
        side: OracleBetSide,
        stake: Int,
        nowMillis: Long,
    ): PlaceBetResult {
        val wallet = ensureWalletAndGrant(nowMillis)
        val rejection = OracleBettingPolicy.checkBet(stake, wallet.balance, expiresAt, nowMillis, betFor(articleId) != null)
        if (rejection != null) return PlaceBetResult.Rejected(rejection)
        val bet = OracleBet(
            articleId = articleId, question = question, expiresAt = expiresAt, side = side, stake = stake,
            status = OracleBetStatus.OPEN, placedAt = nowMillis,
        )
        // The unique index is the last line of defence; a lost race comes back as -1 and is reported as already bet.
        val id = insertBetIfAbsent(bet)
        if (id == -1L) return PlaceBetResult.Rejected(BetRejection.ALREADY_BET)
        val balance = wallet.balance - stake
        updateWallet(balance, wallet.lastGrantEpochDay)
        return PlaceBetResult.Placed(bet.copy(id = id), balance)
    }

    @Transaction
    open suspend fun settleBet(articleId: String, outcome: OracleOutcome, nowMillis: Long): SettleResult {
        val bet = betFor(articleId) ?: return SettleResult.NotSettleable
        if (!OracleBettingPolicy.canSettle(bet.status, bet.expiresAt, nowMillis)) return SettleResult.NotSettleable
        val settlement = OracleBettingPolicy.settle(bet.side, bet.stake, outcome)
        if (closeOpenBet(bet.id, settlement.status, nowMillis) == 0) return SettleResult.NotSettleable
        val wallet = requireNotNull(wallet()) { "a bet cannot exist without its wallet" }
        val balance = wallet.balance + settlement.payout
        updateWallet(balance, wallet.lastGrantEpochDay)
        return SettleResult.Settled(bet.copy(status = settlement.status, settledAt = nowMillis), settlement.payout, balance)
    }

    /** Returns how many stakes were given back. Safe to call on every sync. */
    @Transaction
    open suspend fun autoRefundStale(nowMillis: Long): Int {
        var refunded = 0
        for (bet in openBets()) {
            if (!OracleBettingPolicy.shouldAutoRefund(bet.status, bet.expiresAt, nowMillis)) continue
            if (closeOpenBet(bet.id, OracleBetStatus.REFUNDED, nowMillis) == 0) continue
            val wallet = wallet() ?: continue
            updateWallet(wallet.balance + bet.stake, wallet.lastGrantEpochDay)
            refunded++
        }
        return refunded
    }
}
```

`AndroidDb.kt`: thêm `UserWallet::class` và `OracleBet::class` vào `entities`, `version = 22`, `OracleBetConverters::class` vào `@TypeConverters`, `abstract fun oracleWalletDao(): OracleWalletDao`, `MIGRATION_21_22` cuối `allMigrations`, các `import` tương ứng, và:

```kotlin
@Suppress("ClassName")
object MIGRATION_21_22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_wallet` (
                `id` INTEGER NOT NULL,
                `balance` INTEGER NOT NULL,
                `lastGrantEpochDay` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `oracle_bet` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `articleId` TEXT NOT NULL,
                `question` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `side` TEXT NOT NULL,
                `stake` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `placedAt` INTEGER NOT NULL,
                `settledAt` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_oracle_bet_articleId` ON `oracle_bet` (`articleId`)")
    }
}
```

`DbModule.kt` (cạnh `provideOracleMarketDao`, thêm `import ...OracleWalletDao`):

```kotlin
    @Provides
    @Singleton
    fun provideOracleWalletDao(androidDatabase: AndroidDatabase): OracleWalletDao =
        androidDatabase.oracleWalletDao()
```

- [ ] **Step 5: Sinh schema và đối chiếu SQL với `22.json`**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.db.RoomMigration21To22Test" > /tmp/o02-t2.log 2>&1; echo exit=$?` (kapt sinh `22.json`). Rồi chạy script so `createSql` của **cả hai** bảng với SQL trong `MIGRATION_21_22` (bỏ khoảng trắng, dấu backtick và `IF NOT EXISTS`), và in danh sách chỉ mục:

```bash
python3 - <<'EOF'
import json,re
d=json.load(open("app/schemas/com.mckimquyen.reader.infrastructure.db.AndroidDatabase/22.json"))
print("version",d["database"]["version"])
src=open("app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt",encoding="utf-8").read()
mig_src=src[src.index("object MIGRATION_21_22"):]
norm=lambda t:re.sub(r"\s+","",t.replace("${TABLE_NAME}","T").replace("`","")).replace("IFNOTEXISTS","")
for table in ("user_wallet","oracle_bet"):
    e=[x for x in d["database"]["entities"] if x["tableName"]==table][0]
    m=re.search(r"CREATE TABLE IF NOT EXISTS `%s` \((.*?)\n            \)\n"%table,mig_src,re.S)
    schema=norm(e["createSql"]).replace("T(","(",1).replace("CREATETABLET","CREATETABLE"+table) if False else norm(e["createSql"])
    mig=norm("CREATE TABLE IF NOT EXISTS ${TABLE_NAME} ("+m.group(1)+")")
    print(table,"TABLE MATCH:",schema==mig)
    print(table,"INDEX:",[(i["name"],i["unique"],i["columnNames"]) for i in e["indices"]], "FK:",e.get("foreignKeys"))
EOF
```
Kỳ vọng: `TABLE MATCH: True` cho cả hai; `oracle_bet` có chỉ mục `index_oracle_bet_articleId` unique và `FK: []` (hoặc `None`). `21.json` không đổi (`git status --short app/schemas` chỉ thấy `22.json` mới).

- [ ] **Step 6: Chạy lại hai test, xác nhận xanh** (`RoomMigration21To22Test` 5, `OracleWalletDaoTest` 21). Đọc XML từng lớp. Nếu test đồng thời (`awaitAll` trên `Dispatchers.IO`) chập chờn trong Robolectric, **không** nới test: kiểm lại rằng mỗi hàm là một `@Transaction` và rằng chỉ mục unique còn trên bảng; thử lại 3 lần và báo số lần xanh.

- [ ] **Step 7: Mutation (backup + `cmp`)**:
  - `AndroidDb.kt`: `CREATE UNIQUE INDEX IF NOT EXISTS \`index_oracle_bet_articleId\`` → bỏ `UNIQUE` (test `oneBetPerArticle` đỏ).
  - `AndroidDb.kt` (trong `MIGRATION_21_22`): thêm `, FOREIGN KEY(\`articleId\`) REFERENCES \`article\`(\`id\`) ON DELETE CASCADE` trước `)` của `oracle_bet` (test "survives deletion" đỏ).
  - `OracleWalletDao.kt`: bỏ dòng `updateWallet(balance, wallet.lastGrantEpochDay)` trong `placeBet` (điểm không bị trừ, test đỏ).
  - `OracleWalletDao.kt`: `if (closeOpenBet(bet.id, settlement.status, nowMillis) == 0) return SettleResult.NotSettleable` → bỏ dòng (chốt đôi trả hai lần, test đỏ).
  - `OracleWalletDao.kt`: `AND status = 'OPEN'` trong `closeOpenBet` → bỏ (cùng test chốt đôi hoặc hoàn lặp đỏ).
  - `OracleWalletDao.kt`: bỏ khối `@Transaction` của `placeBet` (đổi `@Transaction\n    open suspend fun placeBet` thành `open suspend fun placeBet`): test đồng thời có thể không bắt được trên Robolectric; nếu mutation **sống sót**, ghi vào báo cáo và thêm test dùng `database.runInTransaction` giả lỗi giữa chừng thay thế (viết test trước, rồi chạy lại mutation đến khi bị bắt).
  - `OracleWalletDao.kt`: `OracleBettingPolicy.shouldAutoRefund(...)` trong `autoRefundStale` → `true` (bet chưa tới mốc bị hoàn, test đỏ).
  - `OracleWalletDao.kt`: `wallet.balance + settlement.payout` → `minOf(wallet.balance + settlement.payout, 2000L)` (test "not cut by the cap" đỏ).

- [ ] **Step 8: Commit** (kiểm exit code, gồm `22.json`): `feat(oracle-02): add the wallet and bet tables, DAO and Room v22 migration`.

---

### Task 3: Tự hoàn cược quá hạn trong `OracleClaimExtractor.cleanUp` (trước khi dọn kèo)

Cược không phụ thuộc hàng `oracle_market`, nên thứ tự chỉ để dọn dẹp luôn được làm: tự hoàn chạy **trước** `deleteStale` và **độc lập** với consent (chỉ động tới dữ liệu cục bộ).

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleClaimExtractor.kt` (thêm tham số constructor `private val walletDao: OracleWalletDao` sau `dao`; trong `cleanUp` gọi `walletDao.autoRefundStale(now)` trong `try` riêng, **trước** `dao.deleteStale`)
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/OracleClaimExtractorTest.kt` (dựng extractor với DAO ví thật; thêm 4 test)

**Interfaces:**
- Consumes: `OracleWalletDao.autoRefundStale(nowMillis: Long): Int` (Task 2).
- Produces: `OracleClaimExtractor(context, dao, walletDao, gateway, summaryService)`; hành vi: mỗi lần `extractRecent` (kể cả khi consent tắt) hoàn các cược `OPEN` đã quá `expiresAt + 30 ngày`; lỗi hoàn chỉ ghi log loại exception, không chặn dọn kèo và không chặn bước sau.

- [ ] **Step 1: Sửa test hiện có và thêm test đỏ**

Trong `OracleClaimExtractorTest`: mọi chỗ dựng `object : OracleClaimExtractor(context, db.oracleMarketDao(), gateway, summaryService)` đổi thành `OracleClaimExtractor(context, db.oracleMarketDao(), db.oracleWalletDao(), gateway, summaryService)` (có hai chỗ: trong `setUp` và trong test `realConsentReader_...`). Thêm các import `OracleBetSide`, `OracleBetStatus`, `OracleOutcome`... chỉ khi dùng. Thêm:

```kotlin
    private fun bet(article: String, expiresAt: Long, stake: Int = 100) = runBlocking {
        db.oracleWalletDao().placeBet(article, "Q?", expiresAt, com.mckimquyen.reader.domain.model.oracle.OracleBetSide.YES, stake, expiresAt - day)
    }

    private fun wallet() = runBlocking { db.oracleWalletDao().wallet()?.balance }

    @Test
    fun staleOpenBets_areRefundedWhenConsentIsOff() {
        bet("a", expiresAt = now - 31 * day)
        assertEquals(900L, wallet())
        consented = false
        run()
        assertEquals(1000L, wallet())
        assertEquals(OracleBetStatus.REFUNDED, runBlocking { db.oracleWalletDao().betFor("a") }!!.status)
    }

    @Test
    fun recentlyExpiredBets_areNotRefunded() {
        bet("a", expiresAt = now - 29 * day)
        run()
        assertEquals(OracleBetStatus.OPEN, runBlocking { db.oracleWalletDao().betFor("a") }!!.status)
        assertEquals(900L, wallet())
    }

    @Test
    fun refundHappensBeforeTheClaimRowIsCleanedUp() {
        // The claim row is dropped by the 30-day cleanup in the same run; the stake must already be back by then.
        put(article("a"))
        runBlocking {
            db.oracleMarketDao().upsert(
                OracleMarket(articleId = "a", status = OracleMarketStatus.HAS_CLAIM, question = "Q?", expiresAt = now - 31 * day,
                    resolutionCriteria = "c", createdAt = now - 40 * day)
            )
        }
        bet("a", expiresAt = now - 31 * day)
        run()
        assertNull(row("a"))
        assertEquals(1000L, wallet())
    }

    @Test
    fun aFailingRefund_doesNotStopTheClaimCleanup() {
        val failingWallet = mockk<com.mckimquyen.reader.domain.repository.OracleWalletDao>()
        coEvery { failingWallet.autoRefundStale(any()) } throws IllegalStateException("boom")
        val e = object : OracleClaimExtractor(context, db.oracleMarketDao(), failingWallet, gateway, summaryService) {
            override fun isConsented() = false
            override fun nowMillis() = now
        }
        put(article("old"))
        runBlocking { db.oracleMarketDao().upsert(OracleMarket(articleId = "old", status = OracleMarketStatus.NO_CLAIM, createdAt = now - 31 * day)) }
        runBlocking { e.extractRecent(1) }
        assertNull(row("old"))
    }
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (biên dịch lỗi: constructor chưa có tham số `walletDao`; sau khi thêm tham số mà chưa gọi hoàn thì 3 test hoàn đỏ vì `wallet()` vẫn `900`).

- [ ] **Step 3: Code tối thiểu** — trong `OracleClaimExtractor.kt`:

```kotlin
open class OracleClaimExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OracleMarketDao,
    private val walletDao: OracleWalletDao,
    private val gateway: AiRequestGateway,
    private val summaryService: GeminiSummaryService,
) {
```

và thay `cleanUp`:

```kotlin
    private suspend fun cleanUp(now: Long) {
        // Stakes first: a bet is independent of its prediction row, but nothing may be dropped while points are still out.
        try {
            walletDao.autoRefundStale(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bet refund failed: ${e.javaClass.simpleName}")
        }
        try {
            dao.deleteStale(now - TimeUnit.DAYS.toMillis(OracleClaimPolicy.RETENTION_DAYS.toLong()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Prediction cleanup failed: ${e.javaClass.simpleName}")
        }
    }
```
(thêm `import com.mckimquyen.reader.domain.repository.OracleWalletDao`).

- [ ] **Step 4: Biên dịch sạch Hilt** vì đổi constructor `@Inject`: `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks > /tmp/o02-t3-hilt.log 2>&1; echo exit=$?` (`0`). Rồi chạy `OracleClaimExtractorTest` xanh (20 test cũ + 4 mới = 24).

- [ ] **Step 5: Mutation (backup + `cmp`)**: bỏ khối `walletDao.autoRefundStale(now)` (3 test hoàn đỏ); đổi thứ tự (đặt khối hoàn **sau** `deleteStale`: test thứ tự không bắt được vì cược độc lập với kèo; ghi nhận đúng điều đó, không coi là lỗi, và đổi tên test `refundHappensBeforeTheClaimRowIsCleanedUp` thành `refundAndClaimCleanupBothRunInOneCall` để không hứa điều không kiểm); bỏ `try/catch` quanh hoàn (test `aFailingRefund_...` đỏ); đặt khối hoàn **sau** kiểm consent (test `staleOpenBets_areRefundedWhenConsentIsOff` đỏ).

- [ ] **Step 6: Commit** `feat(oracle-02): give back stakes left open for 30 days past the deadline`.

---

### Task 4: Chuỗi `oracle_bet_*` trong cả 38 locale và đổi nhãn AI (làm TRƯỚC Task 5)

**Files:**
- Modify: `app/src/main/res/values*/strings.xml` ×38 (một script all-or-none)
- Create: `/tmp/o02_add_strings.py` (sao khuôn `/tmp/o01_add_strings.py`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy/OracleBetStringsTest.kt`; sửa `OracleStringsTest.kt` (giữ nguyên các assert cũ: nhãn mới vẫn chứa `can be wrong` / `có thể sai`)

**Interfaces:**
- Produces: 20 khóa mới + **đổi nội dung** `oracle_sheet_ai_label` (không thêm khóa) ở 38 file. Thư mục locale thật: lấy bằng `glob("app/src/main/res/values*/strings.xml")` (ví dụ `values-de-rDE`, `values-fr-rFR`, `values-es-rES`, `values-ja`, `values-vi`), **không** đoán tên.

Bảng EN/VI (viết tay; ký tự `'` được script tự thoát thành `\'`; `%1$s` giữ nguyên ở mọi locale):

| Khóa | EN | VI |
|---|---|---|
| `oracle_bet_balance` | Balance: %1$s points | Số dư: %1$s điểm |
| `oracle_bet_pick_side` | Your pick | Lựa chọn của bạn |
| `oracle_bet_yes` | Yes | Có |
| `oracle_bet_no` | No | Không |
| `oracle_bet_pick_stake` | Points to stake | Số điểm đặt cược |
| `oracle_bet_stake_option` | %1$s points | %1$s điểm |
| `oracle_bet_place` | Place bet | Đặt cược |
| `oracle_bet_place_summary` | Bet %1$s points on %2$s | Cược %1$s điểm vào %2$s |
| `oracle_bet_out_of_points` | Not enough points. You get 100 new points each day (UTC), up to 2,000. | Không đủ điểm. Mỗi ngày (UTC) bạn nhận thêm 100 điểm, tối đa 2.000. |
| `oracle_bet_open` | You bet %1$s points on %2$s. | Bạn đã cược %1$s điểm vào %2$s. |
| `oracle_bet_open_hint` | You can report the result after the date above. | Bạn báo kết quả được sau ngày ghi ở trên. |
| `oracle_bet_awaiting_title` | What was the result? | Kết quả là gì? |
| `oracle_bet_outcome_unclear` | Unclear | Không rõ |
| `oracle_bet_awaiting_hint` | You bet %1$s points on %2$s. Unclear gives your points back. | Bạn đã cược %1$s điểm vào %2$s. Chọn Không rõ để nhận lại điểm. |
| `oracle_bet_settled_won` | You won %1$s points. | Bạn thắng %1$s điểm. |
| `oracle_bet_settled_lost` | You lost %1$s points. | Bạn mất %1$s điểm. |
| `oracle_bet_settled_refunded` | Your %1$s points were given back. | Bạn được hoàn %1$s điểm. |
| `oracle_bet_closed_no_bet` | Betting on this prediction is closed. | Đã đóng cược cho dự đoán này. |
| `oracle_bet_virtual_note` | Virtual points with no value. You report the result yourself. | Điểm ảo, không có giá trị. Bạn tự báo kết quả. |
| `oracle_bet_error` | Could not place the bet. Try again. | Không đặt được cược. Hãy thử lại. |

Nhãn AI mới (đổi cho cả 38 file, thay vì "You cannot bet on it yet"):
- EN: `Written by AI from the title and description; it can be wrong.`
- VI: `Do AI soạn từ tiêu đề và mô tả; có thể sai.`

Không có `%d`. Mỗi chuỗi có `%1$s` đúng số lượng như bảng (`oracle_bet_balance`, `oracle_bet_stake_option`: 1; `oracle_bet_place_summary`, `oracle_bet_open`, `oracle_bet_awaiting_hint`: 2; `oracle_bet_settled_*`: 1).

- [ ] **Step 1: Viết test đỏ `OracleBetStringsTest`**

```kotlin
package com.mckimquyen.reader.ui.page.setting.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleBetStringsTest {

    private val keys = listOf(
        "oracle_bet_balance", "oracle_bet_pick_side", "oracle_bet_yes", "oracle_bet_no", "oracle_bet_pick_stake",
        "oracle_bet_stake_option", "oracle_bet_place", "oracle_bet_place_summary", "oracle_bet_out_of_points",
        "oracle_bet_open", "oracle_bet_open_hint", "oracle_bet_awaiting_title", "oracle_bet_outcome_unclear",
        "oracle_bet_awaiting_hint", "oracle_bet_settled_won", "oracle_bet_settled_lost", "oracle_bet_settled_refunded",
        "oracle_bet_closed_no_bet", "oracle_bet_virtual_note", "oracle_bet_error",
    )
    private val placeholders = mapOf(
        "oracle_bet_balance" to 1, "oracle_bet_stake_option" to 1, "oracle_bet_place_summary" to 2, "oracle_bet_open" to 2,
        "oracle_bet_awaiting_hint" to 2, "oracle_bet_settled_won" to 1, "oracle_bet_settled_lost" to 1, "oracle_bet_settled_refunded" to 1,
    )
    private val files = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        .map { File(it, "strings.xml") }.sortedBy { it.path }

    private fun value(file: File, key: String): String? =
        Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.groupValues?.get(1)

    @Test fun thereAre38LocaleFiles() = assertEquals(38, files.size)

    @Test fun everyLocaleHasEveryKeyOnceAndNonBlank() {
        for (f in files) for (k in keys) {
            val v = value(f, k)
            assertTrue("${f.parentFile.name} lacks $k", v != null && v.isNotBlank())
            assertEquals("${f.parentFile.name} repeats $k", 1, Regex("""<string name="$k"""").findAll(f.readText()).count())
        }
    }

    @Test fun placeholdersAreOrdinalStringsWithTheRightCount_everywhere() {
        for (f in files) for ((k, n) in placeholders) {
            val v = value(f, k)!!
            for (i in 1..n) assertEquals("${f.parentFile.name} $k %$i\$s", 1, Regex("%$i\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k has extra placeholders", n, Regex("%\\d+\\\$s").findAll(v).count())
            assertEquals("${f.parentFile.name} $k must not use %d", 0, Regex("%\\d*d").findAll(v).count())
        }
    }

    @Test fun englishAndVietnameseStateTheRulesInNumbers() {
        val en = File("src/main/res/values/strings.xml")
        val vi = File("src/main/res/values-vi/strings.xml")
        listOf(en, vi).forEach {
            val v = value(it, "oracle_bet_out_of_points")!!
            assertTrue(it.parentFile.name, v.contains("100") && (v.contains("2,000") || v.contains("2.000")))
        }
        assertTrue(value(en, "oracle_bet_virtual_note")!!.contains("Virtual points"))
        assertTrue(value(vi, "oracle_bet_virtual_note")!!.contains("Điểm ảo"))
    }

    @Test fun theAiLabelNoLongerSaysBettingIsUnavailable_everywhere() {
        for (f in files) {
            val v = value(f, "oracle_sheet_ai_label")!!
            assertFalse("${f.parentFile.name} still says you cannot bet", v.contains("cannot bet") || v.contains("Chưa tham gia cược"))
        }
        assertTrue(value(File("src/main/res/values/strings.xml"), "oracle_sheet_ai_label")!!.contains("can be wrong"))
        assertTrue(value(File("src/main/res/values-vi/strings.xml"), "oracle_sheet_ai_label")!!.contains("có thể sai"))
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ** (khóa thiếu ở mọi locale; nhãn AI cũ còn "cannot bet").

- [ ] **Step 3: Viết `/tmp/o02_add_strings.py`** (all-or-none): kiểm đủ 38 file, kiểm **chưa file nào** có khóa mới, kiểm mỗi file có đúng một `oracle_sheet_ai_label` và một `</resources>`, rồi mới ghi; thêm 20 khóa trước `</resources>` và thay nội dung `oracle_sheet_ai_label` bằng regex trên **đúng một** khớp. `EN`/`VI` là hai dict như bảng trên; `assert len(KEYS) == 20`. Chạy từ thư mục gốc repo: kỳ vọng in `ok 38`.

```python
import glob, re
EN = {
    "oracle_bet_balance": "Balance: %1$s points",
    "oracle_bet_pick_side": "Your pick",
    "oracle_bet_yes": "Yes",
    "oracle_bet_no": "No",
    "oracle_bet_pick_stake": "Points to stake",
    "oracle_bet_stake_option": "%1$s points",
    "oracle_bet_place": "Place bet",
    "oracle_bet_place_summary": "Bet %1$s points on %2$s",
    "oracle_bet_out_of_points": "Not enough points. You get 100 new points each day (UTC), up to 2,000.",
    "oracle_bet_open": "You bet %1$s points on %2$s.",
    "oracle_bet_open_hint": "You can report the result after the date above.",
    "oracle_bet_awaiting_title": "What was the result?",
    "oracle_bet_outcome_unclear": "Unclear",
    "oracle_bet_awaiting_hint": "You bet %1$s points on %2$s. Unclear gives your points back.",
    "oracle_bet_settled_won": "You won %1$s points.",
    "oracle_bet_settled_lost": "You lost %1$s points.",
    "oracle_bet_settled_refunded": "Your %1$s points were given back.",
    "oracle_bet_closed_no_bet": "Betting on this prediction is closed.",
    "oracle_bet_virtual_note": "Virtual points with no value. You report the result yourself.",
    "oracle_bet_error": "Could not place the bet. Try again.",
}
VI = {
    "oracle_bet_balance": "Số dư: %1$s điểm",
    "oracle_bet_pick_side": "Lựa chọn của bạn",
    "oracle_bet_yes": "Có",
    "oracle_bet_no": "Không",
    "oracle_bet_pick_stake": "Số điểm đặt cược",
    "oracle_bet_stake_option": "%1$s điểm",
    "oracle_bet_place": "Đặt cược",
    "oracle_bet_place_summary": "Cược %1$s điểm vào %2$s",
    "oracle_bet_out_of_points": "Không đủ điểm. Mỗi ngày (UTC) bạn nhận thêm 100 điểm, tối đa 2.000.",
    "oracle_bet_open": "Bạn đã cược %1$s điểm vào %2$s.",
    "oracle_bet_open_hint": "Bạn báo kết quả được sau ngày ghi ở trên.",
    "oracle_bet_awaiting_title": "Kết quả là gì?",
    "oracle_bet_outcome_unclear": "Không rõ",
    "oracle_bet_awaiting_hint": "Bạn đã cược %1$s điểm vào %2$s. Chọn Không rõ để nhận lại điểm.",
    "oracle_bet_settled_won": "Bạn thắng %1$s điểm.",
    "oracle_bet_settled_lost": "Bạn mất %1$s điểm.",
    "oracle_bet_settled_refunded": "Bạn được hoàn %1$s điểm.",
    "oracle_bet_closed_no_bet": "Đã đóng cược cho dự đoán này.",
    "oracle_bet_virtual_note": "Điểm ảo, không có giá trị. Bạn tự báo kết quả.",
    "oracle_bet_error": "Không đặt được cược. Hãy thử lại.",
}
EN_LABEL = "Written by AI from the title and description; it can be wrong."
VI_LABEL = "Do AI soạn từ tiêu đề và mô tả; có thể sai."
KEYS = list(EN.keys())
assert len(KEYS) == 20 and set(KEYS) == set(VI.keys())
files = sorted(glob.glob("app/src/main/res/values*/strings.xml"))
assert len(files) == 38, len(files)
LABEL = re.compile(r'(<string name="oracle_sheet_ai_label">)(.*?)(</string>)', re.S)
esc = lambda t: t.replace("'", "\\'")
plan = []
for f in files:
    src = open(f, encoding="utf-8").read()
    assert not any(f'name="{k}"' in src for k in KEYS), f"đã có khoá ở {f}"
    assert src.count("</resources>") == 1, f
    assert len(LABEL.findall(src)) == 1, f"thiếu hoặc lặp oracle_sheet_ai_label ở {f}"
    is_vi = f.endswith("values-vi/strings.xml")
    table = VI if is_vi else EN
    out = LABEL.sub(lambda m: m.group(1) + esc(VI_LABEL if is_vi else EN_LABEL) + m.group(3), src, count=1)
    block = "".join(f'    <string name="{k}">{esc(table[k])}</string>\n' for k in KEYS)
    plan.append((f, out.replace("</resources>", block + "</resources>")))
for f, out in plan:
    open(f, "w", encoding="utf-8").write(out)
print("ok", len(plan))
```
(Hai dict đã đủ 20 khóa; `assert len(KEYS) == 20` chặn nếu ai đó xóa bớt.)

- [ ] **Step 4: Chạy script, rồi chạy** `OracleBetStringsTest`, `OracleStringsTest` (cũ), `StringResourceParityTest`, `OracleClaimSheetTest` xanh; đọc XML từng lớp.

- [ ] **Step 5: Mutation (backup + `cmp`)**, dùng thư mục locale thật: xóa `oracle_bet_place` khỏi `values-de-rDE/strings.xml` (đỏ); đổi `%1$s` thành `%s` trong `oracle_bet_open` của `values-fr-rFR` (đỏ); bỏ `%2$s` trong `oracle_bet_place_summary` của `values-ja` (đỏ); khôi phục `You cannot bet on it yet.` vào nhãn AI của `values-es-rES` (đỏ); bỏ `2,000` khỏi `oracle_bet_out_of_points` EN (đỏ).

- [ ] **Step 6: Commit** `feat(oracle-02): add the betting strings in 38 locales and update the AI label`.

---

### Task 5: `buildOracleBetUiState` (thuần) và `OracleBettingViewModel`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/oracle/OracleBetting.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleBetUiStateTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleBettingViewModelTest.kt`

**Interfaces:**
- Consumes: `OracleClaimCard` (ORACLE-01), `OracleWalletDao`, `UserWallet`, `OracleBet`, `OracleBettingPolicy`, `OracleBetPhase`, `OracleBetSide`, `OracleOutcome`, `PlaceBetResult`, `SettleResult` (Task 1-2); `@IODispatcher CoroutineDispatcher`.
- Produces:
  - `data class OracleBetUiState(val phase: OracleBetPhase, val balance: Long, val bet: OracleBet?)`.
  - `fun buildOracleBetUiState(card: OracleClaimCard, wallet: UserWallet?, bet: OracleBet?, now: Long): OracleBetUiState`: `balance = wallet?.balance ?: STARTING_BALANCE` (chưa có ví thì hiện số dư khởi đầu), `phase = OracleBettingPolicy.phaseFor(bet?.status, card.expiresAt, balance, now)`. Hạn của cược đã có lấy từ `bet.expiresAt` (bản chụp), không từ `card`, để khớp khi kèo bị đổi: `expiresAt = bet?.expiresAt ?: card.expiresAt`.
  - `@HiltViewModel class OracleBettingViewModel`: `val state: StateFlow<OracleBetUiState?>` (null khi chưa mở thẻ nào), `val failed: StateFlow<Boolean>`; `fun open(card: OracleClaimCard)`, `fun close()`, `fun placeBet(side: OracleBetSide, stake: Int)`, `fun settle(outcome: OracleOutcome)`. Constructor `@Inject (dao: OracleWalletDao, @IODispatcher io: CoroutineDispatcher)` ủy quyền cho constructor `internal (dao, io, clock: () -> Long)` để test thay đồng hồ.
  - Giới hạn biết trước: pha chỉ tính lại khi dữ liệu đổi hoặc khi mở thẻ; nếu hạn trôi qua lúc thẻ đang mở, pha cập nhật ở lần mở/đổi dữ liệu kế tiếp (không có bộ đếm giờ).

- [ ] **Step 1: Viết test đỏ (thuần)** `OracleBetUiStateTest` (JUnit thường):

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OracleBetUiStateTest {

    private val expires = 1_000_000L
    private val card = OracleClaimCard("a", "Q?", expires, "c")
    private fun wallet(balance: Long) = UserWallet(balance = balance, lastGrantEpochDay = 1)
    private fun bet(status: OracleBetStatus, exp: Long = expires) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = exp, side = OracleBetSide.YES, stake = 100, status = status, placedAt = 1)

    @Test fun noWalletYet_showsTheStartingBalance_andCanBet() {
        val s = buildOracleBetUiState(card, null, null, now = expires - 1)
        assertEquals(1000L, s.balance)
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
        assertNull(s.bet)
    }

    @Test fun lowBalance_isOutOfPoints_untilTheDeadline() {
        assertEquals(OracleBetPhase.OUT_OF_POINTS, buildOracleBetUiState(card, wallet(49), null, expires - 1).phase)
        assertEquals(OracleBetPhase.CAN_BET, buildOracleBetUiState(card, wallet(50), null, expires - 1).phase)
        assertEquals(OracleBetPhase.CLOSED_NO_BET, buildOracleBetUiState(card, wallet(0), null, expires).phase)
    }

    @Test fun openBet_isOpenUntilTheDeadline_thenAwaitsTheResult() {
        assertEquals(OracleBetPhase.BET_OPEN, buildOracleBetUiState(card, wallet(900), bet(OracleBetStatus.OPEN), expires - 1).phase)
        assertEquals(OracleBetPhase.AWAITING_RESULT, buildOracleBetUiState(card, wallet(900), bet(OracleBetStatus.OPEN), expires).phase)
    }

    @Test fun settledBet_isSettled() {
        OracleBetStatus.values().filter { it != OracleBetStatus.OPEN }.forEach {
            assertEquals("$it", OracleBetPhase.SETTLED, buildOracleBetUiState(card, wallet(900), bet(it), expires + 1).phase)
        }
    }

    @Test fun theDeadlineOfAnExistingBetComesFromTheBet_notFromTheCard() {
        // The prediction was re-dated after the bet: the bet keeps the date it was placed under.
        val s = buildOracleBetUiState(card.copy(expiresAt = expires + 1_000), wallet(900), bet(OracleBetStatus.OPEN, exp = expires), now = expires)
        assertEquals(OracleBetPhase.AWAITING_RESULT, s.phase)
    }

    @Test fun balanceComesFromTheWallet_andTheBetIsPassedThrough() {
        val b = bet(OracleBetStatus.WON)
        val s = buildOracleBetUiState(card, wallet(1234), b, expires + 1)
        assertEquals(1234L, s.balance)
        assertEquals(b, s.bet)
    }
}
```

- [ ] **Step 2: Viết test đỏ (ViewModel)** `OracleBettingViewModelTest` (Robolectric + Room in-memory; dispatcher unconfined; chờ thời gian thực có hạn; đồng hồ giả):

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleBettingViewModelTest {

    private lateinit var db: AndroidDatabase
    private val day = 86_400_000L
    private var nowMs = 100 * day + 3_600_000L
    private val expires get() = nowMs + 5 * day
    private val card get() = OracleClaimCard("a", "Will it ship?", expires, "Release notes.")
    private lateinit var vm: OracleBettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = OracleBettingViewModel(db.oracleWalletDao(), Dispatchers.Unconfined) { nowMs }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    private fun stateWhere(p: (OracleBetUiState) -> Boolean): OracleBetUiState = awaitReal { vm.state.first { it != null && p(it) }!! }

    @Test fun nothingOpen_meansNoState() {
        assertNull(vm.state.value)
        assertFalse(vm.failed.value)
    }

    @Test fun open_createsTheWallet_andOffersTheBet() {
        vm.open(card)
        val s = stateWhere { it.balance == 1000L }
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
        assertNull(s.bet)
    }

    @Test fun open_onANewDay_grantsTheDailyPoints() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        nowMs += day
        vm.close()
        vm.open(card)
        assertEquals(1100L, stateWhere { it.balance == 1100L }.balance)
    }

    @Test fun placeBet_takesThePoints_andShowsTheOpenBet() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        val s = stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        assertEquals(900L, s.balance)
        assertEquals(100, s.bet!!.stake)
        assertEquals(OracleBetSide.YES, s.bet!!.side)
        assertFalse(vm.failed.value)
    }

    @Test fun placeBet_withABadStake_failsAndChargesNothing() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        assertTrue(vm.failed.value)
        assertEquals(1000L, stateWhere { true }.balance)
    }

    @Test fun aSuccessfulBetClearsEarlierFailure() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        assertTrue(vm.failed.value)
        vm.placeBet(OracleBetSide.NO, 50)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        assertFalse(vm.failed.value)
    }

    @Test fun settle_afterTheDeadline_paysAndShowsTheResult() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        nowMs += 6 * day
        vm.close(); vm.open(card)
        stateWhere { it.phase == OracleBetPhase.AWAITING_RESULT }
        vm.settle(OracleOutcome.YES)
        val s = stateWhere { it.phase == OracleBetPhase.SETTLED }
        assertEquals(OracleBetStatus.WON, s.bet!!.status)
        assertEquals(1100L, s.balance)
    }

    @Test fun settle_beforeTheDeadline_failsAndPaysNothing() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        vm.settle(OracleOutcome.YES)
        assertTrue(vm.failed.value)
        assertEquals(OracleBetStatus.OPEN, stateWhere { true }.bet!!.status)
    }

    @Test fun close_clearsTheState_andTheFailure() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        vm.close()
        assertNull(vm.state.value)
        assertFalse(vm.failed.value)
    }

    @Test fun openingAnotherPrediction_switchesTheBetShown() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        vm.open(OracleClaimCard("b", "Other?", expires, "c"))
        val s = stateWhere { it.bet == null && it.balance == 900L }
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
    }
}
```

- [ ] **Step 3: Viết test nguồn đỏ** (thêm vào `OracleBetUiStateTest` hoặc tệp riêng `OracleBettingSourceTest`): đọc `ui/page/oracle/OracleBetting.kt`, bỏ dòng comment, khẳng định **không** chứa `AdManager`, `showRewarded`, `Rewarded`, `link`, `feedId`, `accountId`; và chứa `@HiltViewModel`, `@IODispatcher`, và mọi `launch` đều đi qua `viewModelScope`.

- [ ] **Step 4: Chạy, xác nhận đỏ** (`Unresolved reference: buildOracleBetUiState`, `OracleBettingViewModel`, `OracleBetUiState`).

- [ ] **Step 5: Code tối thiểu** `ui/page/oracle/OracleBetting.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.domain.repository.PlaceBetResult
import com.mckimquyen.reader.domain.repository.SettleResult
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OracleBetUiState(val phase: OracleBetPhase, val balance: Long, val bet: OracleBet?)

fun buildOracleBetUiState(card: OracleClaimCard, wallet: UserWallet?, bet: OracleBet?, now: Long): OracleBetUiState {
    val balance = wallet?.balance ?: OracleBettingPolicy.STARTING_BALANCE
    // An existing bet keeps the deadline it was placed under, even if the prediction row was re-dated later.
    val expiresAt = bet?.expiresAt ?: card.expiresAt
    return OracleBetUiState(OracleBettingPolicy.phaseFor(bet?.status, expiresAt, balance, now), balance, bet)
}

/**
 * Drives the betting part of the prediction card. The phase is recomputed when the data changes or a card is opened;
 * there is no timer, so a deadline that passes while the card is open shows at the next such moment.
 */
@HiltViewModel
class OracleBettingViewModel internal constructor(
    private val dao: OracleWalletDao,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(dao: OracleWalletDao, @IODispatcher io: CoroutineDispatcher) : this(dao, io, System::currentTimeMillis)

    private val openCard = MutableStateFlow<OracleClaimCard?>(null)
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<OracleBetUiState?> = openCard
        .flatMapLatest { card ->
            if (card == null) flowOf(null)
            else combine(dao.observeWallet(), dao.observeBet(card.articleId)) { wallet, bet ->
                buildOracleBetUiState(card, wallet, bet, clock())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun open(card: OracleClaimCard) {
        _failed.value = false
        openCard.value = card
        viewModelScope.launch(io) { guarded { dao.ensureWalletAndGrant(clock()) } }
    }

    fun close() {
        openCard.value = null
        _failed.value = false
    }

    fun placeBet(side: OracleBetSide, stake: Int) {
        val card = openCard.value ?: return
        viewModelScope.launch(io) {
            val placed = guarded { dao.placeBet(card.articleId, card.question, card.expiresAt, side, stake, clock()) }
            _failed.value = placed !is PlaceBetResult.Placed
        }
    }

    fun settle(outcome: OracleOutcome) {
        val card = openCard.value ?: return
        viewModelScope.launch(io) {
            val settled = guarded { dao.settleBet(card.articleId, outcome, clock()) }
            _failed.value = settled !is SettleResult.Settled
        }
    }

    /** Any failure becomes `null` (shown as one generic message); cancellation is never swallowed. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 6: Biên dịch sạch Hilt** (`@HiltViewModel` mới): `./gradlew :app:hiltJavaCompileDevDebug :app:transformDevDebugClassesWithAsm :app:compileDevDebugUnitTestKotlin --rerun-tasks > /tmp/o02-t5-hilt.log 2>&1` (`exit=0`). Nếu Hilt báo hai constructor `@Inject`/không tìm thấy constructor hợp lệ vì constructor chính `internal`: giữ constructor chính **public không có `@Inject`**, không đổi test.

- [ ] **Step 7: Chạy lại, xác nhận xanh** (`OracleBetUiStateTest` 6 + nguồn, `OracleBettingViewModelTest` 10).

- [ ] **Step 8: Mutation (backup + `cmp`)**: `wallet?.balance ?: OracleBettingPolicy.STARTING_BALANCE` → `?: 0L` (đỏ); `bet?.expiresAt ?: card.expiresAt` → `card.expiresAt` (test hạn của cược đỏ); bỏ `_failed.value = false` trong `open` hoặc trong `close` (đỏ); `placed !is PlaceBetResult.Placed` → `false` (test bad stake đỏ); `settled !is SettleResult.Settled` → `false` (test settle sớm đỏ); bỏ `throw e` của `CancellationException` (thêm test: khi hủy `viewModelScope` giữa chừng không ghi `failed`; viết test trước rồi mới chạy mutation); bỏ `guarded` quanh `ensureWalletAndGrant` (DAO mock ném lỗi → test sập; thêm test dùng DAO MockK ném `IllegalStateException`, kỳ vọng `failed` không bị bật khi chỉ lỗi cấp ví lúc mở, và state vẫn phát).

- [ ] **Step 9: Commit** `feat(oracle-02): add the betting view model and its pure UI state`.

---

### Task 6: Khe `footer` của thẻ, `OracleBettingSection` (6 pha) và nối `HomeEntry`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/oracle/OracleClaimSheet.kt` (thêm tham số `footer: @Composable (OracleClaimCard) -> Unit = {}` vào `OracleClaimSheet` và `OracleClaimSheetContent`, gọi `footer(card)` ngay sau nhãn AI)
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/oracle/OracleBettingSection.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` (thêm `OracleBettingViewModel`, mở/đóng theo `openOracleArticleId`, truyền `footer`)
- Test: `app/src/test/java/com/mckimquyen/reader/ui/component/oracle/OracleBettingSectionTest.kt`, `app/src/test/java/com/mckimquyen/reader/ui/page/oracle/OracleBettingWiringSourceTest.kt`

**Interfaces:**
- Consumes: `OracleBetUiState`, `OracleBettingViewModel` (Task 5); `OracleBetPhase`, `OracleBettingPolicy.STAKES`, `OracleBetSide`, `OracleOutcome`, `OracleBetStatus` (Task 1); chuỗi `oracle_bet_*` (Task 4).
- Produces:
  - `@Composable fun OracleBettingSection(state: OracleBetUiState, failed: Boolean, onPlaceBet: (OracleBetSide, Int) -> Unit, onSettle: (OracleOutcome) -> Unit)`.
  - Test tag: `ORACLE_BET_SECTION_TAG = "oracle_bet_section"`, `ORACLE_BET_BALANCE_TAG`, `ORACLE_BET_SIDE_YES_TAG`, `ORACLE_BET_SIDE_NO_TAG`, `ORACLE_BET_STAKE_TAG_PREFIX = "oracle_bet_stake_"` (ghép số tiền: `oracle_bet_stake_50`, ...), `ORACLE_BET_PLACE_TAG`, `ORACLE_BET_OUT_OF_POINTS_TAG`, `ORACLE_BET_OPEN_TAG`, `ORACLE_BET_RESULT_YES_TAG`, `ORACLE_BET_RESULT_NO_TAG`, `ORACLE_BET_RESULT_UNCLEAR_TAG`, `ORACLE_BET_SETTLED_TAG`, `ORACLE_BET_CLOSED_TAG`, `ORACLE_BET_ERROR_TAG`, `ORACLE_BET_NOTE_TAG`.
- Hiển thị theo pha:
  - Luôn có: dòng số dư (`oracle_bet_balance`) và ghi chú `oracle_bet_virtual_note`.
  - `CAN_BET`: hai nút Yes/No (chọn một, ban đầu **chưa chọn**), ba nút mức 50/100/500 (mức lớn hơn số dư bị vô hiệu; ban đầu chưa chọn mức), nút "Đặt cược" chỉ bật khi đã chọn cả cửa và mức hợp lệ; nút hiện tóm tắt `oracle_bet_place_summary` khi đủ lựa chọn.
  - `OUT_OF_POINTS`: chỉ chữ `oracle_bet_out_of_points`; **không có nút nào** (không quảng cáo).
  - `BET_OPEN`: `oracle_bet_open` (stake, tên cửa) + `oracle_bet_open_hint`; không có điều khiển.
  - `AWAITING_RESULT`: `oracle_bet_awaiting_title` + `oracle_bet_awaiting_hint` + ba nút Yes / No / Không rõ.
  - `SETTLED`: `oracle_bet_settled_won|lost|refunded` (số = stake; thắng hiển thị **lãi ròng = stake**).
  - `CLOSED_NO_BET`: `oracle_bet_closed_no_bet`.
  - `failed = true`: thêm `oracle_bet_error` ở mọi pha.
  - Số được định dạng theo locale bằng `NumberFormat.getIntegerInstance(locale)`. Tên cửa lấy từ `oracle_bet_yes` / `oracle_bet_no`. Đích chạm ≥ 48 dp, `contentDescription` cho nút mức ("%1$s points" qua `oracle_bet_stake_option`).

- [ ] **Step 1: Viết test Compose đỏ** `OracleBettingSectionTest` (khuôn `OracleClaimSheetTest`: Robolectric, `createAndroidComposeRule<ComponentActivity>()`, `@GraphicsMode(NATIVE)`, `qualifiers = "en-rUS"`):

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
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
class OracleBettingSectionTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val placed = mutableListOf<Pair<OracleBetSide, Int>>()
    private val settled = mutableListOf<OracleOutcome>()

    private fun bet(status: OracleBetStatus, side: OracleBetSide = OracleBetSide.YES, stake: Int = 100) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = side, stake = stake, status = status, placedAt = 1)

    private fun show(phase: OracleBetPhase, balance: Long = 1000, bet: OracleBet? = null, failed: Boolean = false) =
        compose.setContent {
            OracleBettingSection(
                state = OracleBetUiState(phase, balance, bet), failed = failed,
                onPlaceBet = { s, n -> placed += s to n }, onSettle = { settled += it },
            )
        }

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun tag(t: String) = compose.onNodeWithTag(t)
    private fun count(t: String) = compose.onAllNodesWithTag(t).fetchSemanticsNodes().size

    // --- always present

    private fun assertBalanceAndNote(phase: OracleBetPhase, bet: OracleBet?) {
        compose.setContent { OracleBettingSection(OracleBetUiState(phase, 1234, bet), false, { _, _ -> }, {}) }
        tag(ORACLE_BET_BALANCE_TAG).assertTextContains(str(R.string.oracle_bet_balance, "1,234"))
        tag(ORACLE_BET_NOTE_TAG).assertTextContains(str(R.string.oracle_bet_virtual_note))
    }

    @Test fun canBet_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.CAN_BET, null)
    @Test fun outOfPoints_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.OUT_OF_POINTS, null)
    @Test fun betOpen_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.BET_OPEN, bet(OracleBetStatus.OPEN))
    @Test fun awaiting_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.AWAITING_RESULT, bet(OracleBetStatus.OPEN))
    @Test fun settled_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.SETTLED, bet(OracleBetStatus.WON))
    @Test fun closedNoBet_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.CLOSED_NO_BET, null)

    // --- CAN_BET

    @Test fun canBet_startsWithNothingChosen_andThePlaceButtonIsOff() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_PLACE_TAG).assertIsNotEnabled()
        assertEquals(0, placed.size)
    }

    @Test fun canBet_sideAloneOrStakeAlone_isNotEnough() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_YES_TAG).performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsNotEnabled()
    }

    @Test fun canBet_sideAndStake_enableThePlaceButton_andReportTheChoice() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_NO_TAG).performClick()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "100").performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsEnabled().assertTextContains(str(R.string.oracle_bet_place_summary, "100", str(R.string.oracle_bet_no)))
        tag(ORACLE_BET_PLACE_TAG).performClick()
        assertEquals(listOf(OracleBetSide.NO to 100), placed)
    }

    @Test fun canBet_aStakeAboveTheBalanceIsDisabled() {
        show(OracleBetPhase.CAN_BET, balance = 120)
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "50").assertIsEnabled()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "100").assertIsEnabled()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "500").assertIsNotEnabled()
    }

    @Test fun canBet_offersExactlyTheThreeStakes() {
        show(OracleBetPhase.CAN_BET)
        listOf("50", "100", "500").forEach { assertEquals(it, 1, count(ORACLE_BET_STAKE_TAG_PREFIX + it)) }
        assertEquals(0, count(ORACLE_BET_STAKE_TAG_PREFIX + "75"))
    }

    @Test fun canBet_changingTheSideKeepsTheStake_andTheLastChoiceWins() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_YES_TAG).performClick()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "50").performClick()
        tag(ORACLE_BET_SIDE_NO_TAG).performClick()
        tag(ORACLE_BET_PLACE_TAG).performClick()
        assertEquals(listOf(OracleBetSide.NO to 50), placed)
    }

    // --- OUT_OF_POINTS: no control at all, and above all no ad button

    @Test fun outOfPoints_hasOnlyText_noButtons() {
        show(OracleBetPhase.OUT_OF_POINTS, balance = 10)
        tag(ORACLE_BET_OUT_OF_POINTS_TAG).assertTextContains(str(R.string.oracle_bet_out_of_points))
        listOf(ORACLE_BET_PLACE_TAG, ORACLE_BET_SIDE_YES_TAG, ORACLE_BET_SIDE_NO_TAG, ORACLE_BET_RESULT_YES_TAG).forEach { assertEquals(it, 0, count(it)) }
        assertEquals(0, count(ORACLE_BET_STAKE_TAG_PREFIX + "50"))
    }

    // --- BET_OPEN

    @Test fun betOpen_showsTheStakeAndTheSide_withNoControls() {
        show(OracleBetPhase.BET_OPEN, balance = 900, bet = bet(OracleBetStatus.OPEN, OracleBetSide.NO, 100))
        tag(ORACLE_BET_OPEN_TAG).assertTextContains(str(R.string.oracle_bet_open, "100", str(R.string.oracle_bet_no)))
        listOf(ORACLE_BET_PLACE_TAG, ORACLE_BET_RESULT_YES_TAG, ORACLE_BET_RESULT_UNCLEAR_TAG).forEach { assertEquals(it, 0, count(it)) }
    }

    // --- AWAITING_RESULT

    @Test fun awaiting_offersThreeOutcomes_andReportsEach() {
        show(OracleBetPhase.AWAITING_RESULT, balance = 900, bet = bet(OracleBetStatus.OPEN))
        tag(ORACLE_BET_RESULT_YES_TAG).performClick()
        tag(ORACLE_BET_RESULT_NO_TAG).performClick()
        tag(ORACLE_BET_RESULT_UNCLEAR_TAG).performClick()
        assertEquals(listOf(OracleOutcome.YES, OracleOutcome.NO, OracleOutcome.UNCLEAR), settled)
    }

    @Test fun awaiting_explainsThatUnclearGivesThePointsBack() {
        show(OracleBetPhase.AWAITING_RESULT, balance = 900, bet = bet(OracleBetStatus.OPEN, OracleBetSide.YES, 500))
        tag(ORACLE_BET_OPEN_TAG).assertTextContains(str(R.string.oracle_bet_awaiting_hint, "500", str(R.string.oracle_bet_yes)))
    }

    // --- SETTLED

    @Test fun settled_won_showsTheNetGain() {
        show(OracleBetPhase.SETTLED, balance = 1100, bet = bet(OracleBetStatus.WON, stake = 100))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_won, "100"))
    }

    @Test fun settled_lost_showsTheLoss() {
        show(OracleBetPhase.SETTLED, balance = 900, bet = bet(OracleBetStatus.LOST, stake = 500))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_lost, "500"))
    }

    @Test fun settled_refunded_showsTheRefund() {
        show(OracleBetPhase.SETTLED, balance = 1000, bet = bet(OracleBetStatus.REFUNDED, stake = 50))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_refunded, "50"))
    }

    // --- CLOSED_NO_BET and failure

    @Test fun closedWithoutABet_saysSo_withNoControls() {
        show(OracleBetPhase.CLOSED_NO_BET)
        tag(ORACLE_BET_CLOSED_TAG).assertTextContains(str(R.string.oracle_bet_closed_no_bet))
        assertEquals(0, count(ORACLE_BET_PLACE_TAG))
    }

    @Test fun failure_showsTheErrorInEveryPhase_andOnlyWhenFailed() {
        show(OracleBetPhase.CAN_BET, failed = true)
        tag(ORACLE_BET_ERROR_TAG).assertTextContains(str(R.string.oracle_bet_error))
    }

    @Test fun noFailure_noErrorText() {
        show(OracleBetPhase.CAN_BET, failed = false)
        assertEquals(0, count(ORACLE_BET_ERROR_TAG))
    }
}
```

- [ ] **Step 2: Viết test nguồn đỏ** `OracleBettingWiringSourceTest` (chỉ xét mã, bỏ dòng comment):

```kotlin
package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleBettingWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private fun code(path: String) = File("$root/$path").readLines()
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") || it.trimStart().startsWith("//") }
        .joinToString("\n")

    private val section = code("ui/component/oracle/OracleBettingSection.kt")
    private val sheet = code("ui/component/oracle/OracleClaimSheet.kt")
    private val home = code("ui/page/common/HomeEntry.kt")

    @Test fun theSheetHasAFooterSlotThatReceivesTheCard() {
        assertTrue(sheet.contains("footer(card)"))
        assertEquals("both the dialog and the body declare the slot", 2, Regex("""footer: @Composable \(OracleClaimCard\) -> Unit""").findAll(sheet).count())
    }

    @Test fun homeEntryHostsTheBettingViewModelAndOpensItWithTheSheet() {
        assertTrue(home.contains("val oracleBettingViewModel: OracleBettingViewModel = hiltViewModel()"))
        assertTrue(home.contains("oracleBettingViewModel.open("))
        assertTrue(home.contains("oracleBettingViewModel.close()"))
        assertTrue(home.contains("OracleBettingSection("))
        assertTrue(home.contains("oracleBettingViewModel.placeBet("))
        assertTrue(home.contains("oracleBettingViewModel.settle("))
    }

    @Test fun theSectionNeverMentionsAdsLinksOrMoneyEntryPoints() {
        listOf("AdManager", "showRewarded", "Rewarded", "Admob", "Applovin", "link", "feedId", "accountId", "Billing", "purchase").forEach {
            assertFalse("the betting section must not mention '$it'", section.contains(it, ignoreCase = true))
        }
    }

    @Test fun theSectionReadsEveryTextFromStringResources() {
        assertTrue(section.contains("R.string.oracle_bet_balance"))
        assertTrue(section.contains("R.string.oracle_bet_out_of_points"))
        assertFalse("no hard-coded sentence", Regex("""Text\(\s*"[A-Za-z ]{4,}"""").containsMatchIn(section))
    }

    @Test fun theStakesComeFromThePolicy_notALocalCopy() {
        assertTrue(section.contains("OracleBettingPolicy.STAKES"))
        assertFalse(Regex("""listOf\(\s*50\s*,\s*100\s*,\s*500""").containsMatchIn(section))
    }
}
```

- [ ] **Step 3: Chạy, xác nhận đỏ** (thiếu `OracleBettingSection`, thiếu tham số `footer`, thiếu thẻ).

- [ ] **Step 4: Code tối thiểu**

`OracleClaimSheet.kt`: thêm tham số `footer: @Composable (OracleClaimCard) -> Unit = {}` vào **cả hai** hàm; `OracleClaimSheet` truyền `footer` xuống `OracleClaimSheetContent(card, onDismiss, footer)`; trong thân, ngay sau `Text(...oracle_sheet_ai_label...)` gọi `footer(card)`. Test ORACLE-01 cũ không truyền `footer` nên vẫn đúng (giá trị mặc định rỗng).

`ui/component/oracle/OracleBettingSection.kt`:

```kotlin
package com.mckimquyen.reader.ui.component.oracle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import java.text.NumberFormat
import java.util.Locale

const val ORACLE_BET_SECTION_TAG = "oracle_bet_section"
const val ORACLE_BET_BALANCE_TAG = "oracle_bet_balance"
const val ORACLE_BET_SIDE_YES_TAG = "oracle_bet_side_yes"
const val ORACLE_BET_SIDE_NO_TAG = "oracle_bet_side_no"
const val ORACLE_BET_STAKE_TAG_PREFIX = "oracle_bet_stake_"
const val ORACLE_BET_PLACE_TAG = "oracle_bet_place"
const val ORACLE_BET_OUT_OF_POINTS_TAG = "oracle_bet_out_of_points"
const val ORACLE_BET_OPEN_TAG = "oracle_bet_open"
const val ORACLE_BET_RESULT_YES_TAG = "oracle_bet_result_yes"
const val ORACLE_BET_RESULT_NO_TAG = "oracle_bet_result_no"
const val ORACLE_BET_RESULT_UNCLEAR_TAG = "oracle_bet_result_unclear"
const val ORACLE_BET_SETTLED_TAG = "oracle_bet_settled"
const val ORACLE_BET_CLOSED_TAG = "oracle_bet_closed"
const val ORACLE_BET_ERROR_TAG = "oracle_bet_error"
const val ORACLE_BET_NOTE_TAG = "oracle_bet_note"

private val MIN_TOUCH_TARGET = 48.dp

/** The betting part of the prediction card (ORACLE-02). Virtual points only; there is no control that leaves the app. */
@Composable
fun OracleBettingSection(
    state: OracleBetUiState,
    failed: Boolean,
    onPlaceBet: (OracleBetSide, Int) -> Unit,
    onSettle: (OracleOutcome) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(Modifier.fillMaxWidth().testTag(ORACLE_BET_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.oracle_bet_balance, number.format(state.balance)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(ORACLE_BET_BALANCE_TAG),
        )
        when (state.phase) {
            OracleBetPhase.CAN_BET -> PlaceBetControls(state.balance, number, onPlaceBet)
            OracleBetPhase.OUT_OF_POINTS -> Text(
                stringResource(R.string.oracle_bet_out_of_points), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ORACLE_BET_OUT_OF_POINTS_TAG),
            )
            OracleBetPhase.BET_OPEN -> state.bet?.let { bet ->
                Text(stringResource(R.string.oracle_bet_open, number.format(bet.stake), sideName(bet.side)), modifier = Modifier.testTag(ORACLE_BET_OPEN_TAG))
                Text(stringResource(R.string.oracle_bet_open_hint), style = MaterialTheme.typography.bodySmall)
            }
            OracleBetPhase.AWAITING_RESULT -> state.bet?.let { bet ->
                Text(stringResource(R.string.oracle_bet_awaiting_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.oracle_bet_awaiting_hint, number.format(bet.stake), sideName(bet.side)),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(ORACLE_BET_OPEN_TAG),
                )
                ResultButtons(onSettle)
            }
            OracleBetPhase.SETTLED -> state.bet?.let { bet ->
                val res = when (bet.status) {
                    OracleBetStatus.WON -> R.string.oracle_bet_settled_won
                    OracleBetStatus.LOST -> R.string.oracle_bet_settled_lost
                    else -> R.string.oracle_bet_settled_refunded
                }
                Text(stringResource(res, number.format(bet.stake)), fontWeight = FontWeight.Bold, modifier = Modifier.testTag(ORACLE_BET_SETTLED_TAG))
            }
            OracleBetPhase.CLOSED_NO_BET -> Text(stringResource(R.string.oracle_bet_closed_no_bet), modifier = Modifier.testTag(ORACLE_BET_CLOSED_TAG))
        }
        if (failed) {
            Text(stringResource(R.string.oracle_bet_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(ORACLE_BET_ERROR_TAG))
        }
        Text(
            stringResource(R.string.oracle_bet_virtual_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(ORACLE_BET_NOTE_TAG),
        )
    }
}

@Composable
private fun sideName(side: OracleBetSide): String =
    stringResource(if (side == OracleBetSide.YES) R.string.oracle_bet_yes else R.string.oracle_bet_no)

@Composable
private fun PlaceBetControls(balance: Long, number: NumberFormat, onPlaceBet: (OracleBetSide, Int) -> Unit) {
    var side by remember { mutableStateOf<OracleBetSide?>(null) }
    var stake by remember { mutableStateOf<Int?>(null) }
    Text(stringResource(R.string.oracle_bet_pick_side), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SideChip(OracleBetSide.YES, side == OracleBetSide.YES, ORACLE_BET_SIDE_YES_TAG) { side = OracleBetSide.YES }
        SideChip(OracleBetSide.NO, side == OracleBetSide.NO, ORACLE_BET_SIDE_NO_TAG) { side = OracleBetSide.NO }
    }
    Text(stringResource(R.string.oracle_bet_pick_stake), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OracleBettingPolicy.STAKES.forEach { value ->
            val label = stringResource(R.string.oracle_bet_stake_option, number.format(value))
            FilterChip(
                selected = stake == value,
                enabled = balance >= value,
                onClick = { stake = value },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).semantics { contentDescription = label }.testTag(ORACLE_BET_STAKE_TAG_PREFIX + value),
            )
        }
    }
    val chosenSide = side
    val chosenStake = stake
    val ready = chosenSide != null && chosenStake != null && balance >= chosenStake
    Button(
        onClick = { if (chosenSide != null && chosenStake != null) onPlaceBet(chosenSide, chosenStake) },
        enabled = ready,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_PLACE_TAG),
    ) {
        Text(
            if (chosenSide != null && chosenStake != null) stringResource(R.string.oracle_bet_place_summary, number.format(chosenStake), sideName(chosenSide))
            else stringResource(R.string.oracle_bet_place),
        )
    }
}

@Composable
private fun SideChip(side: OracleBetSide, selected: Boolean, tag: String, onClick: () -> Unit) {
    val label = sideName(side)
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(tag),
    )
}

@Composable
private fun ResultButtons(onSettle: (OracleOutcome) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onSettle(OracleOutcome.YES) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_YES_TAG)) {
            Text(stringResource(R.string.oracle_bet_yes))
        }
        OutlinedButton(onClick = { onSettle(OracleOutcome.NO) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_NO_TAG)) {
            Text(stringResource(R.string.oracle_bet_no))
        }
        OutlinedButton(onClick = { onSettle(OracleOutcome.UNCLEAR) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).testTag(ORACLE_BET_RESULT_UNCLEAR_TAG)) {
            Text(stringResource(R.string.oracle_bet_outcome_unclear))
        }
    }
}
```

`HomeEntry.kt`: cạnh `oracleClaimsViewModel` thêm `val oracleBettingViewModel: OracleBettingViewModel = hiltViewModel()`, `val betState by oracleBettingViewModel.state.collectAsState()`, `val betFailed by oracleBettingViewModel.failed.collectAsState()`. `LocalOpenOracleClaim provides { articleId -> openOracleArticleId = articleId; oracleClaims[articleId]?.let(oracleBettingViewModel::open) }`. `OracleClaimSheet(card = ..., onDismiss = { openOracleArticleId = null; oracleBettingViewModel.close() }, footer = { betState?.let { s -> OracleBettingSection(s, betFailed, onPlaceBet = oracleBettingViewModel::placeBet, onSettle = oracleBettingViewModel::settle) } })`. Thêm các `import` tương ứng.

- [ ] **Step 5: Biên dịch sạch Hilt** (cùng lệnh `--rerun-tasks`; `exit=0`), rồi chạy `OracleBettingSectionTest` (~20), `OracleBettingWiringSourceTest` (5), **và** `OracleClaimSheetTest`, `OracleWiringSourceTest`, `OracleBadgeTest` (ORACLE-01) xanh để chắc không hồi quy.

- [ ] **Step 6: Mutation (backup + `cmp`)** trên `OracleBettingSection.kt` (test `OracleBettingSectionTest`, `OracleBettingWiringSourceTest`): bỏ `enabled = balance >= value` (đỏ); `enabled = ready` → `enabled = true` (đỏ); `balance >= chosenStake` → `balance > chosenStake` (đỏ ở ca số dư đúng bằng mức; **thêm** ca `balance = 100`, chọn 100, nút bật, vào Step 1 nếu chưa có); đổi tên cửa ở `sideName` (đỏ); bỏ nhánh `OUT_OF_POINTS` thành hiện nút (thêm `Button` giả, test "no buttons" đỏ); thêm chữ `Rewarded` vào mã (test nguồn đỏ); đổi `OracleBettingPolicy.STAKES` thành `listOf(50, 100, 500)` (test nguồn đỏ); đổi `OracleBetStatus.WON -> oracle_bet_settled_won` sang `lost` (đỏ); bỏ `if (failed)` (đỏ). Trên `OracleClaimSheet.kt`: bỏ `footer(card)` (test nguồn đỏ). Trên `HomeEntry.kt`: bỏ `oracleBettingViewModel.close()` (test nguồn đỏ).

- [ ] **Step 7: Commit** `feat(oracle-02): add the betting card with six phases and wire it into the prediction sheet`.

---

### Task 7: androidTest và kiểm máy thật Tecno KJ7 (SỚM, trước báo cáo)

Chỉ làm sau khi owner duyệt plan. Mục tiêu: bắt lỗi chỉ lộ trên máy thật (nâng cấp DB thật v21→v22, cược trong DB thật, thẻ trong cây semantics thật) trước khi viết báo cáo.

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/db/OracleBettingMigration22Test.kt` (khuôn `OracleMigration21Test`: dựng DB v21 từ `21.json`, mở bằng `allMigrations`; 3 test: nâng cấp giữ dữ liệu cũ và tạo hai bảng; đặt cược rồi xóa `article` thì cược còn nguyên; đặt cược + chốt đúng cửa qua DAO thật cho số dư `1000 → 900 → 1100`)
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/oracle/OracleBettingSectionWidgetTest.kt` (khuôn `OracleBadgeSheetWidgetTest`, **không** dùng `ComposeTestRule`: `ActivityScenario` + `ComposeView` + duyệt cây semantics; 4 test: chọn cửa + mức rồi bấm Đặt cược gọi đúng `(side, stake)`; mức vượt số dư không bấm được; hết điểm không có nút nào ngoài chữ; đến hạn có ba nút kết quả và bấm gọi đúng `OracleOutcome`)
- Tạm (không vào repo): `/tmp/OracleBetSeedTest.kt` copy vào `app/src/androidTest/.../OracleBetSeedTest.kt` **chỉ** khi chạy smoke, rồi xóa; đầu vào qua `-e`: `mode=claim` (ghi một kèo `HAS_CLAIM` mẫu cho bài mới nhất, `expiresAt` = +30 ngày), `mode=expireBet` (đặt `expiresAt` của cược mở về quá khứ gần để thử chốt), `mode=expireBetLong` (về quá khứ > 30 ngày để thử tự hoàn)
- Script: `/tmp/o02-backup.py`, `/tmp/o02-restore.py` (đã tạo ở phần "Chuẩn bị công cụ"; chỉ `SERIALS = {'tecno': '115333744A005844'}`)

**Cổng thiết bị (chỉ đọc) trước mọi thao tác:**
1. `adb devices` (KJ7 phải có mặt; Pixel nếu có mặt **không chạm**).
2. `adb -s 115333744A005844 shell dumpsys activity activities | grep -E "Active instrumentation|topResumedActivity"`: không có instrumentation; foreground là launcher (hoặc app của owner đã biết); app lạ ở foreground thì **dừng và hỏi**.
3. `adb -s ... shell ps -A | grep -iE "instrument|monkey|uiautomator"`: trống.
4. Màn hình mở khóa (khóa thì **nhờ owner mở**).

- [ ] **Step 1:** Viết hai androidTest, `./gradlew assembleDevDebugAndroidTest > /tmp/o02-t7-build.log 2>&1` (`exit=0`). Commit riêng hai tệp test (kiểm exit code): `test(oracle-02): cover the wallet migration and the betting card on a real device`, **trước** khi chạm máy.
- [ ] **Step 2:** Cổng thiết bị, rồi `python3 /tmp/o02-backup.py` (ghi lại `integrity ok`, kích thước DataStore, số bài). Cài APK dev + test APK: `adb -s 115333744A005844 install -r -t <apk>`.
- [ ] **Step 3:** `timeout 300 adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.infrastructure.db.OracleBettingMigration22Test,com.mckimquyen.reader.ui.component.oracle.OracleBettingSectionWidgetTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/o02-t7-instr.log 2>&1`; kỳ vọng `OK (7 tests)`. Chỉ chạy runner `com.mckimquyen.reader.test`.
- [ ] **Step 4: Mutation trên máy** (3 ca, mỗi ca: sửa, build, cài, chạy lớp liên quan, **phải đỏ**, khôi phục bằng `cmp`): bỏ `UNIQUE` của `index_oracle_bet_articleId`; thêm `FOREIGN KEY ... ON DELETE CASCADE` vào `oracle_bet` trong `MIGRATION_21_22`; bỏ `enabled = balance >= value` ở mức cược. Cài lại bản HEAD sạch sau cùng.
- [ ] **Step 5: Smoke tay với app thật (không Gemini thật):**
  1. Mở app trên DB đã nâng cấp: `user_wallet` rỗng, `oracle_bet` rỗng, `user_version = 22`, `integrity ok`, số bài nguyên vẹn (đọc DB chỉ đọc qua `run-as ... cat` rồi `sqlite3` trên máy dev).
  2. Chạy `OracleBetSeedTest mode=claim` (kèo mẫu cho bài mới nhất). Mở danh sách: huy hiệu "Prediction" → bấm → thẻ có **số dư 1.000**, ghi chú điểm ảo, Yes/No, ba mức 50/100/500, nút Đặt cược **tắt** cho tới khi chọn đủ.
  3. Chọn No + 100 → "Bet 100 points on No" → bấm: thẻ chuyển sang "You bet 100 points on No."; DB: `user_wallet.balance = 900`, một dòng `oracle_bet` `OPEN` `stake=100 side=NO`.
  4. Đóng và mở lại thẻ, bấm Đặt cược lần nữa không có (đã chuyển sang pha `BET_OPEN`): không thể cược đôi.
  5. `OracleBetSeedTest mode=expireBet` rồi mở lại thẻ: pha "What was the result?" với ba nút; bấm **No**: "You won 100 points.", `balance = 1100`, `oracle_bet.status = WON`, `settledAt` có giá trị; bấm lại không đổi gì.
  6. Lặp với một bài khác (`mode=claim` bài thứ hai): cược Yes 50, ép hạn, bấm **Unclear** → "Your 50 points were given back.", số dư trở lại.
  7. `mode=expireBetLong` trên một cược mở, rồi **kích sync** (`SyncWorker` gọi `OracleClaimExtractor.extractRecent`, `cleanUp` chạy ngay cả khi consent tắt): DB cho thấy cược thành `REFUNDED` và số dư đã cộng lại stake; log có `0` dòng `oracle_claim` (consent tắt).
  8. Hết điểm: đặt `user_wallet.balance` về `0` bằng instrumentation tạm, mở thẻ kèo chưa cược: chỉ chữ "Not enough points. You get 100 new points each day (UTC), up to 2,000." và **không có nút**.
  9. Thưởng ngày: ép `lastGrantEpochDay` về hôm qua bằng instrumentation tạm, mở thẻ: số dư +100 (hoặc về đúng trần 2.000).
- [ ] **Step 6: Khôi phục byte-identical** (`python3 /tmp/o02-restore.py`): Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; gỡ test package (`adb -s ... uninstall com.mckimquyen.reader.test`); xóa `OracleBetSeedTest.kt` khỏi cây mã (`git status` sạch, **không** commit); `force-stop com.mckimquyen.reader`; cài lại bản HEAD sạch.
- [ ] **Step 7:** Nếu bất kỳ ca nào đỏ: sửa mã bằng TDD (viết test JVM tái hiện trước), không sửa test cho khớp. Ghi mọi phát hiện vào báo cáo.

**Quảng cáo toàn màn hình khi mở app:** chỉ Back, nhiều lần, kiểm `topResumedActivity` sau mỗi lần; không bấm "Continue to app"; không cài gì từ trang Play Store của quảng cáo; banner nhỏ dưới màn đọc không che UI thì tiếp tục.

**Không kiểm được (ghi thẳng vào báo cáo):** kèo thật do AI sinh (chỉ kèo mẫu); "cộng đồng" (không có); TalkBack; ngôn ngữ ngoài EN/VI; Pixel và máy thứ hai; hành vi khi đồng hồ máy bị chỉnh tay.

---

### Task 8: Full suite, lint, build

**Files:** không sửa mã (chỉ sửa nếu phát hiện lỗi thật, theo TDD).

- [ ] **Step 1: Full suite sạch:** `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/o02-full.log 2>&1; echo exit=$? > /tmp/o02-full.exit`; đọc exit, rồi đếm từ XML: tổng `tests=`, `failures=`, `errors=`, `skipped=` trên `app/build/test-results/testDevDebugUnitTest/*.xml` (kỳ vọng 0 lỗi; nền trước 2.987) và `find -mmin +30` không ra tệp cũ. Nếu treo thì kill "Gradle Test Executor" rồi chạy lại. Lỗi chỉ khi chạy chung = trạng thái tĩnh bị rò: tìm gốc, không loại test.
- [ ] **Step 2: Lint:** `./gradlew lintDevDebug > /tmp/o02-lint.log 2>&1; echo exit=$?`; đếm `severity` từ `app/build/reports/lint-results-devDebug.xml` (nền 2.465 cảnh báo, 0 lỗi) và liệt kê cảnh báo trong tệp ORACLE-02 (kỳ vọng 0; nếu có thì sửa).
- [ ] **Step 3: Build:** `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/o02-assemble.log 2>&1; echo exit=$?` (`0`).
- [ ] **Step 4: Schema:** `git status --short app/schemas` chỉ thấy `22.json` mới; `21.json` không đổi.
- [ ] **Step 5: Không commit** (chỉ kiểm). Có sửa thì commit riêng theo TDD.

---

### Task 9: Báo cáo DONE và tài liệu

**Files:**
- Create: `doc/task/done/ORACLE-02_intel-points-betting-sheet_DONE.md` (tiếng Việt, khuôn `ORACLE-01_ai-predictive-claim-extractor_DONE.md`: Phạm vi nói thẳng, Đã làm, Kiểm chứng JVM, Kiểm chứng KJ7, Phát hiện khi làm, Chưa kiểm, Giới hạn, Commits)
- Modify: `doc/feature.md` (ORACLE-02 từ "đang brainstorm → spec" sang ✅ với số liệu thật; nợ: kèo thật từ AI, đồng hồ chỉnh tay, TalkBack; nhắc ORACLE-03/04 vẫn chưa làm)
- Modify: `docs/superpowers/specs/2026-11-12-oracle-02-betting-wallet-design.md` (ghi điều chỉnh nếu thực thi lệch spec)
- Task gốc `doc/task/todo/ORACLE-02_*.md`: **hỏi owner bằng AskUserQuestion** giữ trong `todo/` + ghi chú đầu file, hay chuyển sang `done/`. Gherkin có "% đồng thuận của cộng đồng" và "âm thanh chip" **không làm được/không làm**; khuyến nghị giữ trong `todo/` với ghi chú rõ hai điểm đó.

- [ ] **Step 1:** Viết báo cáo chỉ bằng số liệu thật từ XML, log và lệnh đã chạy ở Task 7 và 8 (không làm tròn, không ước đoán). "Chưa kiểm" phải liệt kê đúng danh sách ở Task 7. Nói thẳng: không có cộng đồng thật; tự báo kết quả; điểm ảo.
- [ ] **Step 2:** Cập nhật `doc/feature.md` và spec; hỏi owner về file task gốc; làm theo lựa chọn.
- [ ] **Step 3: Commit** `docs(oracle-02): report the betting wallet with its verified and unverified parts`. Không push, không PR, không `.idea`.

---

## Self-review

**Spec coverage:** ví 1.000 + 100/ngày + trần 2.000 (Task 1, 2); cược 50/100/500, một cược mỗi bài, trừ điểm trong transaction, không đổi/hủy (Task 1, 2); chốt Yes/No/Không rõ, hệ số 2, hoàn (Task 1, 2); tự hoàn sau 30 ngày trước khi dọn kèo (Task 3); cược không khóa ngoại (Task 2, có test sống sót khi xóa bài và khi xóa `oracle_market`); bỏ "% cộng đồng" và nhãn AI không còn nói "chưa cược được" (Task 4); chuỗi 38 locale (Task 4); thẻ 6 pha, hết điểm không có nút quảng cáo (Task 6, có test nguồn cấm từ khóa quảng cáo); máy thật chỉ KJ7 và giới hạn không kiểm kèo thật (Task 7); báo cáo trung thực (Task 9). Ngoài phạm vi (ORACLE-03/04, âm thanh chip, cộng đồng, bảng xếp hạng) không có task.

**Thứ tự thực thi:** 1 → 2 → 3 → **4** → 5 → 6 → 7 → 8 → 9 (chuỗi trước view model và giao diện để `R.string.oracle_bet_*` có sẵn mà parity không đỏ).

**Kiểu và tên đã kiểm nhất quán:** `OracleBettingPolicy.checkBet/dailyGrant/settle/canSettle/shouldAutoRefund/epochDayUtc/phaseFor` (Task 1) được `OracleWalletDao` (Task 2) và `buildOracleBetUiState` (Task 5) dùng đúng chữ ký; `PlaceBetResult.Placed/Rejected` và `SettleResult.Settled/NotSettleable` (Task 2) được view model (Task 5) khớp; `OracleBetUiState(phase, balance, bet)` (Task 5) được `OracleBettingSection` (Task 6) dùng; thẻ `ORACLE_BET_*` định nghĩa ở Task 6 và dùng trong test cùng task và androidTest Task 7; constructor `OracleClaimExtractor(context, dao, walletDao, gateway, summaryService)` (Task 3) khớp mọi chỗ dựng trong test.

**Rủi ro đã nêu:** test đồng thời trên Robolectric có thể chập chờn (Task 2 Step 6, có hướng xử lý); mutation `@Transaction` có thể sống sót và buộc thêm test lỗi-giữa-chừng (Task 2 Step 7); constructor `internal` + `@Inject` thứ hai của view model với Hilt (Task 5 Step 6, có phương án dự phòng); pha không có bộ đếm giờ khi hạn trôi qua lúc thẻ đang mở (ghi trong Interfaces Task 5 và sẽ ghi vào báo cáo); migration chạm DB người dùng thật (chỉ tạo bảng mới, đối chiếu `22.json`).
