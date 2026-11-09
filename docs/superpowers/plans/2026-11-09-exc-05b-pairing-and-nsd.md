# EXC-05b — Ghép đôi một lần và tự tìm máy qua NSD: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sau khi ghép đôi một lần bằng secret 128-bit, hai bản RSS Cat Hub trong cùng LAN tự tìm nhau qua NSD và dùng khoá đã lưu cho các lượt đồng bộ sau, không nhập URL hay secret nữa; đồng thời sửa lỗi LAN bị chặn trên Android 17.

**Architecture:** Thêm `ACCESS_LOCAL_NETWORK` (kiểm chứng bằng đo thật trước). Khoá ghép đôi nằm ở kho riêng `PairingStore`, secret được bọc bằng khoá AES-GCM không xuất được trong Android Keystore, tệp loại khỏi mọi backup. Token phiên dẫn xuất từ secret + nonce bằng HMAC-SHA256 (JCA), nonce quảng bá qua NSD (`_rsscathub._tcp`). Engine `MirrorServer` nhận thêm một overload nhận token từ ngoài; luồng nhập secret thủ công của EXC-05a giữ nguyên làm đường lui.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose / Material3 1.0.1, Hilt, DataStore (không đổi), `javax.crypto` (`Mac`, `Cipher`), Android Keystore, `NsdManager`, Robolectric / JUnit4 / MockK, Pixel 7 Pro `2B051FDH3006MU` (Android 17, API 37) cho kiểm chứng thật.

## Global Constraints

- **Giao tiếp:** tiếng Việt với người dùng; giữ nguyên ký hiệu code, đường dẫn, lệnh.
- **Git:** chỉ nhánh `dev`; không worktree; không push; không nhắc hay gợi ý PR; không commit `.idea/`. Mỗi commit kết thúc bằng `Co-Authored-By: Claude Code <noreply@anthropic.com>`. **Không** nối `git commit` sau lệnh build bằng `&&` trừ khi đã kiểm exit code của build.
- **Thiết bị:** chỉ Pixel 7 Pro `2B051FDH3006MU`. Pixel này **dùng chung với dự án khác** (Lens Launcher). Trước MỌI thao tác thiết bị phải chạy: `adb devices`, `adb -s 2B051FDH3006MU shell dumpsys activity | grep -A3 'Active instrumentation'`, `dumpsys activity activities | grep topResumedActivity`, và `ps -A | grep -E 'lenslauncher|bemytester|watermark'`; **dừng và hỏi người dùng** nếu có instrumentation hay tiến trình lạ đang chạy. Công cụ MCP chụp màn hình bị gắn với serial cũ: dùng `adb -s 2B051FDH3006MU exec-out screencap -p`. Không bao giờ chạy `connectedDevDebugAndroidTest`.
- **Dữ liệu thiết bị:** trước smoke phải sao lưu Room (3 file: `Reader`, `Reader-shm`, `Reader-wal`) và DataStore (`settings.preferences_pb`); sau smoke khôi phục và `cmp` **cả 4 file**. Không bao giờ xoá DataStore.
- **Quảng cáo:** mở `MainActivity` trực tiếp thay vì `SplashActivity` để tránh quảng cáo mở app. Nút "Tiếp tục đến ứng dụng" thuộc quảng cáo và mở app khác: **không bao giờ chạm**; dùng Back rồi kiểm lại foreground.
- **Ràng buộc kỹ thuật:** Room giữ v20 (không migration); không dependency mới; không tự viết crypto (chỉ JCA `Mac`/`Cipher`/Keystore); không PAKE; không route POST mới; không thông báo hệ thống mới.
- **Chuỗi:** mọi chuỗi mới có trong cả 38 file locale (EN và VI viết tay, 36 locale còn lại tiếng Anh); không đặt `%d` ngay trước danh từ số nhiều (tránh `PluralsCandidate`).
- **TDD:** test đỏ trước và xác nhận đỏ đúng lý do bằng cách đọc XML (`app/build/test-results/testDevDebugUnitTest/*.xml`), không tin output rút gọn; code tối thiểu; xanh; mutation check cho test chính; commit nhỏ cho mỗi task. Luôn ghi output Gradle ra file. Dùng `timeout` cho mọi lần chạy instrumentation (force-stop app trước).
- **Bài học từ phiên trước:** `DataStore.put` chuyển sang `Dispatchers.IO` nên test phải chờ bằng helper polling có timeout; `removeLast()` lỗi trên JDK này (dùng `removeAt(lastIndex)`); dòng `echo` bắt đầu bằng `====` làm bash hiểu là tuỳ chọn (dùng `printf`); `Modifier.clickable` gộp semantics của con nên test không thấy node.
- **Đây KHÔNG phải EXC-05 gốc:** giữ nguyên `doc/task/todo/EXC-05_local-p2p-sync.md`, không chuyển sang done.
- **Giá trị cố định (nguyên văn từ spec):** dịch vụ NSD `_rsscathub._tcp`; TXT chỉ `v=1` và `n=<nonce hex>`; `token = HMAC-SHA256(secret, "RCS-session-v1" || nonce)` cắt 16 byte, hex 32 ký tự; nonce 16 byte; `SyncLimits.SESSION_TTL_MILLIS = 5L * 60_000`; kho riêng tên `pairing_store`; trạng thái `UNCONFIRMED | CONFIRMED`; baseline lint 2.463 cảnh báo, 0 lỗi.

---

## File Structure

### Tạo mới
- `app/src/main/java/com/mckimquyen/reader/domain/p2psync/LanAccess.kt` — hàm thuần: từ API nào cần xin quyền LAN.
- `app/src/main/java/com/mckimquyen/reader/domain/p2psync/SessionTokenDerivation.kt` — nonce + HMAC → token; so sánh hằng thời gian.
- `app/src/main/java/com/mckimquyen/reader/domain/p2psync/PairedPeer.kt` — mô hình `PairedPeer` và `PairingStatus`.
- `app/src/main/java/com/mckimquyen/reader/domain/p2psync/NsdTxtCodec.kt` — mã hoá/giải mã TXT (`v`, `n`), từ chối khoá lạ.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/SecretWrapper.kt` — interface bọc/mở secret + bản Keystore thật `KeystoreSecretWrapper`.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/PairingStore.kt` — kho khoá, tệp riêng `pairing_store`.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/NsdPlatform.kt` — interface mỏng bọc `NsdManager` + bản thật `AndroidNsdPlatform`.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/PeerDiscovery.kt` — quảng bá và tìm máy, lọc RFC1918, huỷ khi dừng.
- `app/src/main/java/com/mckimquyen/reader/ui/ext/LanPermission.kt` — kiểm và xin quyền LAN ở tầng UI.
- Tests tương ứng dưới `app/src/test/...` và `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/p2psync/PairingNsdDeviceTest.kt`.

### Sửa
- `app/src/main/AndroidManifest.xml` — khai báo `ACCESS_LOCAL_NETWORK`.
- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` — loại `pairing_store`.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/mirror/SyncSessionEngine.kt` và `MirrorServer.kt` — thêm overload nhận token ngoài.
- `app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModelTest.kt` — cập nhật `FakeSyncEngine`.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/di/MirrorModule.kt` — đăng ký `PairingStore`, `PeerDiscovery`.
- `app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModel.kt`, `LocalSyncUiState.kt`, `LocalSyncPage.kt` — danh sách máy đã ghép, ghép mới, chọn máy, quyền.
- `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt` — nối quyền và danh sách máy.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/mirror/SecondScreenViewModel.kt` và nơi gọi `start()` trong `ReadingPage.kt` — chặn khi chưa có quyền.
- 38 file `app/src/main/res/values*/strings.xml`.

---

## Task 1: Kiểm chứng giả thuyết quyền LAN trên Android 17 (làm trước, quyết định phần còn lại)

**Mục đích:** Đo xem khai báo + cấp `ACCESS_LOCAL_NETWORK` có sửa được lỗi timeout hay không. Nếu **không** sửa được, DỪNG kế hoạch và báo người dùng; các task sau dựa trên giả thuyết này.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `docs/superpowers/notes/2026-11-09-exc-05b-lan-permission-measurement.md` (ghi số đo thật)

**Interfaces:**
- Produces: quyền `android.permission.ACCESS_LOCAL_NETWORK` được khai báo; một ghi chú đo đạc có số liệu trước/sau.

- [ ] **Step 1: Kiểm tra thiết bị an toàn (bắt buộc).**
  ```bash
  adb devices -l
  S=2B051FDH3006MU
  adb -s $S shell dumpsys activity | grep -A3 'Active instrumentation' || echo "no instrumentation"
  adb -s $S shell dumpsys activity activities | grep -E 'topResumedActivity' | head -1
  adb -s $S shell ps -A -o PID,ETIME,NAME | grep -E 'lenslauncher|bemytester|watermark|mckimquyen' || true
  ```
  Expected: đúng một thiết bị, không instrumentation. Nếu có tiến trình lạ đang chạy: **dừng và hỏi người dùng**.

- [ ] **Step 2: Sao lưu Room + DataStore của Pixel.**
  ```bash
  S=2B051FDH3006MU; B=/tmp/exc05b-pixel-backup; rm -rf $B; mkdir -p $B
  adb -s $S shell am force-stop com.mckimquyen.reader; sleep 1
  for f in Reader Reader-shm Reader-wal; do adb -s $S exec-out "run-as com.mckimquyen.reader cat databases/$f" > $B/$f; done
  adb -s $S exec-out "run-as com.mckimquyen.reader cat files/datastore/settings.preferences_pb" > $B/settings.preferences_pb
  ls -l $B | awk '{print $5,$9}'
  cp $B/Reader /tmp/b05.db; cp $B/Reader-wal /tmp/b05.db-wal; cp $B/Reader-shm /tmp/b05.db-shm
  sqlite3 /tmp/b05.db 'PRAGMA integrity_check; select count(*) from article;'
  ```
  Expected: `ok` và số bài khớp trên máy.

- [ ] **Step 3: Đo LẠI trạng thái "trước" với bản hiện tại (không khai báo quyền).** Mở `MainActivity` trực tiếp, vào Settings › Đồng bộ cục bộ › Bắt đầu gửi, đọc URL từ `uiautomator dump`, rồi từ Mac:
  ```bash
  curl -sS -m 10 -o /dev/null -w 'http=%{http_code} time=%{time_total}s\n' "<URL phiên>"
  ```
  Ghi kết quả (kỳ vọng từ phiên trước: `http=000 time=10.00s`). Tạo listener đối chứng cùng máy để chắc đường mạng thông: `adb -s $S shell 'printf "HTTP/1.0 200 OK\r\nContent-Length: 3\r\n\r\nok\n" > /data/local/tmp/ok.txt; (timeout 25 toybox nc -l -p 42800 < /data/local/tmp/ok.txt >/dev/null 2>&1 &)'` rồi `curl http://<IP Pixel>:42800/`.

- [ ] **Step 4: Khai báo quyền trong manifest.**
  Thêm vào `app/src/main/AndroidManifest.xml` ngay sau dòng `ACCESS_NETWORK_STATE`:
  ```xml
      <!-- EXC-05b: Android 17 chặn mọi kết nối LAN của app target SDK 37 nếu thiếu quyền này. -->
      <uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
  ```

- [ ] **Step 5: Build, kiểm exit code, cài, cấp quyền, đo "sau".**
  ```bash
  ./gradlew assembleDevDebug > /tmp/exc05b-t1-build.log 2>&1; ec=$?; echo "build_exit=$ec"
  if [ $ec -eq 0 ]; then
    APP="$(ls -t app/build/outputs/apk/dev/debug/*.apk | head -1)"
    adb -s $S install --user 0 -r "$APP" 2>&1 | tail -1
    adb -s $S shell pm grant com.mckimquyen.reader android.permission.ACCESS_LOCAL_NETWORK 2>&1 | head -2
    adb -s $S shell dumpsys package com.mckimquyen.reader | grep -i 'ACCESS_LOCAL_NETWORK'
  fi
  ```
  Expected: `build_exit=0`, và dòng `ACCESS_LOCAL_NETWORK: granted=true`.
  Rồi lặp Step 3 (mở app, Bắt đầu gửi, `curl` URL mới từ Mac) và ghi kết quả.

- [ ] **Step 6: Đối chiếu thu hồi.** `adb -s $S shell pm revoke com.mckimquyen.reader android.permission.ACCESS_LOCAL_NETWORK`, dừng và bắt đầu lại phiên, `curl` lần nữa. Kỳ vọng: timeout trở lại. Đây là cặp đối chiếu cấp/không cấp còn thiếu ở spec.

- [ ] **Step 7: Quyết định theo số đo.**
  - Nếu **cấp quyền → `http=200`** và **thu hồi → timeout**: giả thuyết được chứng minh, tiếp tục Task 2.
  - Nếu cấp quyền vẫn timeout: **DỪNG**, ghi số đo, báo người dùng, không làm Task 2 trở đi.

- [ ] **Step 8: Khôi phục dữ liệu Pixel và kiểm `cmp`.**
  ```bash
  adb -s $S shell am force-stop com.mckimquyen.reader; sleep 1
  for f in Reader Reader-shm Reader-wal; do adb -s $S exec-in "run-as com.mckimquyen.reader sh -c 'cat > databases/$f'" < $B/$f; done
  adb -s $S exec-in "run-as com.mckimquyen.reader sh -c 'cat > files/datastore/settings.preferences_pb'" < $B/settings.preferences_pb
  rc=0; for f in Reader Reader-shm Reader-wal; do adb -s $S exec-out "run-as com.mckimquyen.reader cat databases/$f" > /tmp/v_$f; cmp $B/$f /tmp/v_$f && echo "$f matches 100%" || rc=1; done
  adb -s $S exec-out "run-as com.mckimquyen.reader cat files/datastore/settings.preferences_pb" > /tmp/v_prefs; cmp $B/settings.preferences_pb /tmp/v_prefs && echo "prefs matches 100%" || rc=1
  echo "restore_rc=$rc"
  ```
  Expected: `restore_rc=0`.

- [ ] **Step 9: Ghi số đo và commit (chỉ khi Step 7 cho phép tiếp tục).**
  Viết `docs/superpowers/notes/2026-11-09-exc-05b-lan-permission-measurement.md` với bảng: `trước (không khai báo)`, `sau (cấp)`, `sau (thu hồi)`, mỗi dòng gồm `http`, `time`, và listener đối chứng.
  ```bash
  git add app/src/main/AndroidManifest.xml docs/superpowers/notes/2026-11-09-exc-05b-lan-permission-measurement.md
  git commit -m "fix(exc-05b): declare ACCESS_LOCAL_NETWORK and record the Android 17 measurement" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
  echo "commit_exit=$?"
  ```

---

## Task 2: Quy tắc quyền LAN thuần (`LanAccess`) và chặn trước khi mở socket

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/p2psync/LanAccess.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/p2psync/LanAccessTest.kt`

**Interfaces:**
- Produces: `object LanAccess { const val REQUIRED_FROM_SDK = 37; fun required(sdkInt: Int): Boolean; const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK" }`

- [ ] **Step 1: Viết test đỏ.**
  ```kotlin
  package com.mckimquyen.reader.domain.p2psync

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class LanAccessTest {
      @Test fun permissionNameMatchesTheAndroidConstant() {
          assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LanAccess.PERMISSION)
      }

      @Test fun notRequiredBeforeAndroid17() {
          assertFalse(LanAccess.required(26))
          assertFalse(LanAccess.required(33))
          assertFalse(LanAccess.required(36))
      }

      @Test fun requiredFromAndroid17() {
          assertTrue(LanAccess.required(37))
          assertTrue(LanAccess.required(38))
      }

      @Test fun thresholdIsExactlyApi37() {
          assertEquals(37, LanAccess.REQUIRED_FROM_SDK)
          assertFalse(LanAccess.required(LanAccess.REQUIRED_FROM_SDK - 1))
          assertTrue(LanAccess.required(LanAccess.REQUIRED_FROM_SDK))
      }
  }
  ```

- [ ] **Step 2: Chạy và xác nhận đỏ (lỗi biên dịch do thiếu `LanAccess`).**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.p2psync.LanAccessTest' > /tmp/exc05b-t2-red.log 2>&1; echo "gradle_exit=$?"
  grep -E '^e: ' /tmp/exc05b-t2-red.log | sed 's#file:///.*/##' | head -3
  ```
  Expected: `gradle_exit=1` và `Unresolved reference 'LanAccess'`.

- [ ] **Step 3: Cài đặt tối thiểu.**
  ```kotlin
  package com.mckimquyen.reader.domain.p2psync

  /** Android 17 (API 37) gates all local-network traffic behind a runtime permission for apps that target it. */
  object LanAccess {
      const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
      const val REQUIRED_FROM_SDK = 37

      fun required(sdkInt: Int): Boolean = sdkInt >= REQUIRED_FROM_SDK
  }
  ```

- [ ] **Step 4: Chạy và xác nhận xanh; đọc XML.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.p2psync.LanAccessTest' > /tmp/exc05b-t2-green.log 2>&1; echo "gradle_exit=$?"
  python3 - <<'EOF'
  import re
  s=open('app/build/test-results/testDevDebugUnitTest/TEST-com.mckimquyen.reader.domain.p2psync.LanAccessTest.xml',encoding='utf-8').read()
  print(re.search(r'tests="\d+" skipped="\d+" failures="\d+" errors="\d+"',s).group(0))
  EOF
  ```
  Expected: `tests="4" ... failures="0" errors="0"`.

- [ ] **Step 5: Mutation.** Đổi `>= REQUIRED_FROM_SDK` thành `> REQUIRED_FROM_SDK`, chạy lại, xác nhận `requiredFromAndroid17` và `thresholdIsExactlyApi37` đỏ, rồi khôi phục (`cp` từ bản sao).

- [ ] **Step 6: Commit (kiểm exit code test trước).**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/p2psync/LanAccess.kt app/src/test/java/com/mckimquyen/reader/domain/p2psync/LanAccessTest.kt
  git commit -m "feat(exc-05b): add the pure LAN access rule" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"; echo "commit_exit=$?"
  ```

---

## Task 3: Token phiên dẫn xuất bằng HMAC (`SessionTokenDerivation`)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/p2psync/SessionTokenDerivation.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/p2psync/SessionTokenDerivationTest.kt`

**Interfaces:**
- Consumes: `PairingSecret` (`val hex: String`, `PairingSecret.fromHex(hex)`, `PairingSecret.generate()`).
- Produces:
  ```kotlin
  object SessionTokenDerivation {
      const val NONCE_BYTES = 16
      const val TOKEN_BYTES = 16
      fun newNonce(random: SecureRandom = SecureRandom()): ByteArray
      fun nonceHex(nonce: ByteArray): String
      fun nonceFromHex(hex: String): ByteArray?          // null khi sai độ dài/ký tự
      fun derive(secret: PairingSecret, nonce: ByteArray): String   // 32 ký tự hex thường
      fun matches(expected: String, candidate: String?): Boolean    // hằng thời gian
  }
  ```

- [ ] **Step 1: Viết test đỏ.**
  ```kotlin
  package com.mckimquyen.reader.domain.p2psync

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertNotEquals
  import org.junit.Assert.assertNull
  import org.junit.Assert.assertTrue
  import org.junit.Test
  import java.security.SecureRandom

  class SessionTokenDerivationTest {
      private val secretA = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
      private val secretB = PairingSecret.fromHex("ffeeddccbbaa99887766554433221100")
      private val nonce1 = ByteArray(16) { it.toByte() }
      private val nonce2 = ByteArray(16) { (it + 1).toByte() }

      @Test fun tokenIs32LowercaseHexCharacters() {
          val token = SessionTokenDerivation.derive(secretA, nonce1)
          assertEquals(32, token.length)
          assertTrue(token.all { it in "0123456789abcdef" })
      }

      @Test fun sameSecretAndNonceAlwaysGiveTheSameToken() {
          assertEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretA, nonce1))
      }

      @Test fun differentNonceGivesDifferentToken() {
          assertNotEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretA, nonce2))
      }

      @Test fun differentSecretGivesDifferentToken() {
          assertNotEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretB, nonce1))
      }

      @Test fun tokenDoesNotContainTheSecretOrTheNonce() {
          val token = SessionTokenDerivation.derive(secretA, nonce1)
          assertFalse(token.contains(secretA.hex))
          assertFalse(token.contains(SessionTokenDerivation.nonceHex(nonce1)))
      }

      @Test fun knownAnswerPinsTheDerivationSoItCannotDriftSilently() {
          // HMAC-SHA256(key = the 16 secret bytes, "RCS-session-v1" || nonce1), first 16 bytes, hex.
          // Computed independently with python3 hmac before writing this test (see the plan, Task 3 Step 2).
          assertEquals(KNOWN_TOKEN, SessionTokenDerivation.derive(secretA, nonce1))
      }

      @Test fun newNonceHasTheRightLengthAndDiffersEachTime() {
          val random = SecureRandom()
          val first = SessionTokenDerivation.newNonce(random)
          val second = SessionTokenDerivation.newNonce(random)
          assertEquals(16, first.size)
          assertNotEquals(SessionTokenDerivation.nonceHex(first), SessionTokenDerivation.nonceHex(second))
      }

      @Test fun nonceHexRoundTrips() {
          assertEquals(nonce1.toList(), SessionTokenDerivation.nonceFromHex(SessionTokenDerivation.nonceHex(nonce1))?.toList())
      }

      @Test fun nonceFromHexRejectsWrongLengthAndNonHex() {
          assertNull(SessionTokenDerivation.nonceFromHex(""))
          assertNull(SessionTokenDerivation.nonceFromHex("00".repeat(15)))
          assertNull(SessionTokenDerivation.nonceFromHex("00".repeat(17)))
          assertNull(SessionTokenDerivation.nonceFromHex("zz".repeat(16)))
          assertNull(SessionTokenDerivation.nonceFromHex("AA".repeat(16)))
      }

      @Test fun matchesAcceptsOnlyTheExactToken() {
          val token = SessionTokenDerivation.derive(secretA, nonce1)
          assertTrue(SessionTokenDerivation.matches(token, token))
          assertFalse(SessionTokenDerivation.matches(token, null))
          assertFalse(SessionTokenDerivation.matches(token, token.dropLast(1)))
          assertFalse(SessionTokenDerivation.matches(token, token.dropLast(1) + (if (token.last() == 'a') 'b' else 'a')))
      }

      private companion object {
          // Computed independently with python3 hmac, NOT with the app code: key = the 16 decoded secret bytes,
          // message = "RCS-session-v1" || nonce1 (bytes 0..15), first 16 bytes of the HMAC-SHA256, lowercase hex.
          const val KNOWN_TOKEN = "119c7584d5a38d54d4575d4987b96220"
      }
  }
  ```

- [ ] **Step 2: Kiểm lại known-answer độc lập bằng python3 (không dùng code của app); hằng số trong test đã điền sẵn và phải khớp.**
  ```bash
  python3 - <<'EOF'
  import hmac, hashlib
  key = bytes.fromhex("00112233445566778899aabbccddeeff")
  nonce = bytes(range(16))
  mac = hmac.new(key, b"RCS-session-v1" + nonce, hashlib.sha256).digest()[:16]
  print(mac.hex())
  EOF
  ```
  Kết quả phải đúng `119c7584d5a38d54d4575d4987b96220`; nếu khác, **dừng** và tìm lý do, không sửa số cho khớp. **Quan trọng:** key của HMAC là **16 byte giải mã từ hex của secret**, không phải chuỗi hex dạng text; test phải khẳng định đúng điều đó.

- [ ] **Step 3: Chạy và xác nhận đỏ vì thiếu class (không phải vì known-answer sai).**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.p2psync.SessionTokenDerivationTest' > /tmp/exc05b-t3-red.log 2>&1; echo "gradle_exit=$?"
  grep -E '^e: ' /tmp/exc05b-t3-red.log | sed 's#file:///.*/##' | head -3
  ```
  Expected: `Unresolved reference 'SessionTokenDerivation'`.

- [ ] **Step 4: Cài đặt tối thiểu.**
  ```kotlin
  package com.mckimquyen.reader.domain.p2psync

  import java.security.MessageDigest
  import java.security.SecureRandom
  import javax.crypto.Mac
  import javax.crypto.spec.SecretKeySpec

  /**
   * Derives the one-session token both paired devices can compute independently from the stored secret and the public
   * per-session nonce. Uses only the platform's JCA HMAC; no custom crypto. The token itself never goes into NSD or
   * any request except as the final path segment of the GET the paired peer computes for itself.
   */
  object SessionTokenDerivation {
      const val NONCE_BYTES = 16
      const val TOKEN_BYTES = 16
      private const val HMAC = "HmacSHA256"
      private val CONTEXT = "RCS-session-v1".toByteArray(Charsets.US_ASCII)
      private const val HEX = "0123456789abcdef"

      fun newNonce(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

      fun nonceHex(nonce: ByteArray): String = hex(nonce)

      fun nonceFromHex(hex: String): ByteArray? {
          if (hex.length != NONCE_BYTES * 2 || hex.any { it !in HEX }) return null
          return ByteArray(NONCE_BYTES) { i -> ((HEX.indexOf(hex[i * 2]) shl 4) or HEX.indexOf(hex[i * 2 + 1])).toByte() }
      }

      fun derive(secret: PairingSecret, nonce: ByteArray): String {
          require(nonce.size == NONCE_BYTES)
          val key = secretBytes(secret)
          return try {
              val mac = Mac.getInstance(HMAC)
              mac.init(SecretKeySpec(key, HMAC))
              mac.update(CONTEXT)
              mac.update(nonce)
              hex(mac.doFinal().copyOf(TOKEN_BYTES))
          } finally {
              key.fill(0)
          }
      }

      fun matches(expected: String, candidate: String?): Boolean {
          if (candidate == null || candidate.length != expected.length) return false
          return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), candidate.toByteArray(Charsets.US_ASCII))
      }

      private fun secretBytes(secret: PairingSecret): ByteArray {
          val text = secret.hex
          return ByteArray(text.length / 2) { i -> ((HEX.indexOf(text[i * 2]) shl 4) or HEX.indexOf(text[i * 2 + 1])).toByte() }
      }

      private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
          bytes.forEach {
              val v = it.toInt() and 0xFF
              append(HEX[v ushr 4]).append(HEX[v and 0x0F])
          }
      }
  }
  ```

- [ ] **Step 5: Chạy xanh, đọc XML.** Expected: `tests="11" ... failures="0"`. Nếu `knownAnswerPins…` đỏ thì kiểm lại rằng key là byte đã giải mã, không sửa test cho khớp.

- [ ] **Step 6: Mutation (hai lần, khôi phục sau mỗi lần):** (a) đổi `copyOf(TOKEN_BYTES)` thành `copyOf(8)` → phải đỏ `tokenIs32LowercaseHexCharacters` và `knownAnswerPinsTheDerivationSoItCannotDriftSilently`; (b) bỏ `mac.update(CONTEXT)` → phải đỏ `knownAnswerPinsTheDerivationSoItCannotDriftSilently`. Việc thay `MessageDigest.isEqual` bằng `==` không đổi hành vi quan sát được nên **không** tính là mutation test bắt được; chỉ ghi chú.

- [ ] **Step 7: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/p2psync/SessionTokenDerivation.kt app/src/test/java/com/mckimquyen/reader/domain/p2psync/SessionTokenDerivationTest.kt
  git commit -m "feat(exc-05b): derive session tokens with HMAC-SHA256 from the paired secret" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"; echo "commit_exit=$?"
  ```

---

## Task 4: Engine nhận token ngoài (overload `startSync`) và cập nhật mọi fake

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/mirror/SyncSessionEngine.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServer.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModelTest.kt` (class `FakeSyncEngine`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServerSyncSessionTest.kt`

**Interfaces:**
- Consumes: `MirrorToken(val value: String, val expiresAtMillis: Long)`; `MirrorStartResult.Started(port, token)`; `SyncLimits.SESSION_TTL_MILLIS`.
- Produces: thêm vào `SyncSessionEngine`:
  ```kotlin
  fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult
  ```
  `tokenValue` là 32 ký tự hex thường; hết hạn `clock() + SyncLimits.SESSION_TTL_MILLIS`. Biến thể cũ `startSync(bindAddress, ciphertext)` giữ nguyên hành vi.

- [ ] **Step 1: Đọc test hiện có để theo đúng mẫu.** `sed -n 1,60p app/src/test/java/com/mckimquyen/reader/infrastructure/mirror/MirrorServerSyncSessionTest.kt` (helper `startOk`, cách gọi `GET`).

- [ ] **Step 2: Viết test đỏ** trong `MirrorServerSyncSessionTest.kt`:
  - `externalTokenIsTheOneThatServesTheCiphertext`: `startSyncWithToken(loopback, bytes, "ab".repeat(16))`, GET `/s/<token>` trả đúng byte; GET `/s/` + token ngẫu nhiên khác trả 404.
  - `startedResultCarriesTheSuppliedTokenAndTheSessionTtl`: `Started.token.value == "ab".repeat(16)` và `expiresAtMillis == clockNow + SyncLimits.SESSION_TTL_MILLIS` (dùng clock giả của test hiện có).
  - `externalTokenIsRejectedWhenNotThirtyTwoLowercaseHex`: độ dài sai, chữ hoa, ký tự lạ → ném `IllegalArgumentException`, **không** mở socket (`isRunning == false`).
  - `startSyncWithTokenFailsWhenAlreadyRunning`: lần hai trả `Failed(ALREADY_RUNNING)`.
  - `legacyStartSyncStillGeneratesItsOwnRandomToken`: hai lần `startSync` liên tiếp (dừng giữa hai lần) cho hai token khác nhau, mỗi token 32 hex (hành vi cũ không đổi).

- [ ] **Step 3: Chạy và xác nhận đỏ đúng lý do** (`Unresolved reference 'startSyncWithToken'`). Ghi log ra `/tmp/exc05b-t4-red.log`, đọc lỗi.

- [ ] **Step 4: Cài đặt.**
  Trong `SyncSessionEngine.kt` thêm phương thức vào interface. Trong `MirrorServer.kt` tách phần chung:
  ```kotlin
  override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult {
      require(tokenValue.length == SESSION_TOKEN_CHARS && tokenValue.all { it in LOWER_HEX }) { "Invalid session token" }
      return startSyncInternal(bindAddress, ciphertext) { MirrorToken(tokenValue, clock() + SyncLimits.SESSION_TTL_MILLIS) }
  }
  ```
  và để `startSync(bindAddress, ciphertext, random)` gọi `startSyncInternal` với nhà máy token sinh từ `random` như hiện nay. `startSyncInternal` giữ nguyên toàn bộ khối bind, `ALREADY_RUNNING`, kiểm kích thước ciphertext, tạo `Session`. Hằng `SESSION_TOKEN_CHARS = 32`, `LOWER_HEX = "0123456789abcdef"` đặt trong `companion` hoặc cùng file. Kiểm `require` **trước** khi bind socket.

- [ ] **Step 5: Cập nhật `FakeSyncEngine`** trong `LocalSyncViewModelTest.kt`: thêm `var lastToken: String?`, `var startWithTokenCalls = 0` và override `startSyncWithToken` ghi lại `tokenValue`, trả `result` như `startSync`.

- [ ] **Step 6: Chạy toàn bộ test liên quan** (`MirrorServerSyncSessionTest`, `MirrorServerTest`, `LocalSyncViewModelTest`, `LocalSyncTransportTest`), đọc XML từng suite: tất cả xanh.

- [ ] **Step 7: Mutation:** (a) bỏ `require` kiểm token (token sai phải còn mở socket → test `…RejectedWhenNot…` đỏ); (b) đặt `expiresAtMillis` bằng `clock() + 30*60_000` (test TTL đỏ). Khôi phục.

- [ ] **Step 8: Commit** các file engine, test, fake.

---

## Task 5: Mô hình và kho khoá (`PairedPeer`, `SecretWrapper`, `PairingStore`) + quy tắc backup

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/p2psync/PairedPeer.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/SecretWrapper.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/PairingStore.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/p2psync/PairingStoreTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/p2psync/BackupRulesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class PairingStatus { UNCONFIRMED, CONFIRMED }
  data class PairedPeer(val peerId: String, val label: String, val status: PairingStatus,
                        val createdAtMillis: Long, val needsRepair: Boolean)

  interface SecretWrapper {
      fun wrap(plain: ByteArray): ByteArray            // trả ciphertext tự mô tả (gồm nonce)
      fun unwrap(wrapped: ByteArray): ByteArray?       // null khi không mở được (khoá mất/hỏng)
  }

  class PairingStore(private val context: Context, private val wrapper: SecretWrapper, private val clock: () -> Long = System::currentTimeMillis) {
      companion object { const val FILE_NAME = "pairing_store"; const val MAX_LABEL_CHARS = 60; const val MAX_PEERS = 20 }
      fun list(): List<PairedPeer>
      fun add(label: String, secret: PairingSecret, status: PairingStatus): PairedPeer   // IllegalArgumentException khi nhãn rỗng/quá dài/đã đủ MAX_PEERS
      fun secretOf(peerId: String): PairingSecret?     // null khi không có hoặc mục hỏng
      fun markConfirmed(peerId: String)
      fun forget(peerId: String)
  }
  ```
  `PairingStore` lưu bằng `SharedPreferences` tên `FILE_NAME` (một tệp XML riêng, không phải DataStore chính) ở `context.getSharedPreferences(FILE_NAME, MODE_PRIVATE)`.

- [ ] **Step 1: Viết test đỏ cho `PairingStore`** (Robolectric, `SecretWrapper` giả đảo bit để chứng minh ciphertext khác plaintext):
  `addThenListShowsTheEntryWithItsStatusAndLabel`; `secretRoundTripsThroughTheWrapper`; `storedBytesNeverContainThePlainSecretHex` (đọc tệp XML thô của `pairing_store` qua `context.dataDir/shared_prefs/pairing_store.xml` và khẳng định không chứa `secret.hex`); `blankOrOverlongLabelIsRejected` (rỗng, toàn khoảng trắng, 61 ký tự, ký tự điều khiển); `labelIsTrimmed`; `forgetRemovesTheEntry`; `markConfirmedChangesOnlyThatEntry`; `unwrapFailureMarksTheEntryNeedsRepairAndSecretOfReturnsNull`; `moreThanMaxPeersIsRejected`; `peerIdsAreUniqueAndThirtyTwoHex`; `entriesSurviveANewStoreInstance`.

- [ ] **Step 2: Viết test đỏ `BackupRulesTest`** (JVM thuần, đọc `src/main/res/xml/*.xml`): khẳng định `backup_rules.xml` có `<exclude domain="sharedpref" path="pairing_store.xml"/>`; `data_extraction_rules.xml` có exclude tương tự trong **cả** `<cloud-backup>` và `<device-transfer>`; test cũng phải **đỏ trước khi sửa XML** (xác nhận bằng cách chạy trước Step 5).

- [ ] **Step 3: Chạy và xác nhận đỏ đúng lý do** (thiếu class cho `PairingStore`; `BackupRulesTest` đỏ vì chưa có exclude). Đọc XML kết quả.

- [ ] **Step 4: Cài đặt `PairedPeer.kt`, `SecretWrapper.kt` (chỉ interface ở bước này), `PairingStore.kt`** tối thiểu cho test xanh. `needsRepair` suy ra từ `unwrap == null` khi `list()`/`secretOf`. Không ném lỗi từ `list()`.

- [ ] **Step 5: Sửa hai file XML backup.**
  `backup_rules.xml`:
  ```xml
  <full-backup-content>
      <!-- EXC-05b: the pairing secrets are device-bound; a restored copy would be useless without the Keystore key. -->
      <exclude domain="sharedpref" path="pairing_store.xml" />
  </full-backup-content>
  ```
  `data_extraction_rules.xml`:
  ```xml
  <data-extraction-rules>
      <cloud-backup>
          <exclude domain="sharedpref" path="pairing_store.xml" />
      </cloud-backup>
      <device-transfer>
          <exclude domain="sharedpref" path="pairing_store.xml" />
      </device-transfer>
  </data-extraction-rules>
  ```

- [ ] **Step 6: Chạy xanh, đọc XML; mutation:** bỏ một exclude (từng cái một) → `BackupRulesTest` đỏ đúng test tương ứng; làm `unwrap` luôn thành công trả plaintext sai → test `needsRepair` đỏ. Khôi phục.

- [ ] **Step 7: Commit.**

---

## Task 6: `KeystoreSecretWrapper` thật (Android Keystore)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/p2psync/SecretWrapper.kt` (thêm `KeystoreSecretWrapper`)
- Test: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/p2psync/KeystoreSecretWrapperDeviceTest.kt` (thiết bị thật, không dùng MockK)

**Interfaces:**
- Consumes: `SecretWrapper`.
- Produces: `class KeystoreSecretWrapper(private val alias: String = "rsscathub_pairing_v1") : SecretWrapper` — khoá `AES` 256-bit, `KeyProperties.PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, `setRandomizedEncryptionRequired(true)`, **không** `setUserAuthenticationRequired`. Định dạng `wrapped = nonce(12) || ciphertext+tag`.

- [ ] **Step 1: Viết test thiết bị đỏ:** `wrapThenUnwrapRoundTrips`; `wrapTwiceGivesDifferentCiphertext` (nonce ngẫu nhiên); `tamperedCiphertextReturnsNull` (lật một bit); `truncatedInputReturnsNull`; `keyIsNotExportable` (`SecretKey.encoded == null` hoặc `KeyInfo.isInsideSecureHardware`/`getKey` không xuất được — khẳng định `key.encoded == null`); `deletedKeyMakesUnwrapReturnNull` (xoá alias khỏi `AndroidKeyStore` rồi `unwrap` bản cũ trả `null`, không ném).
- [ ] **Step 2: Xác nhận đỏ bằng build androidTest** (`./gradlew assembleDevDebugAndroidTest > /tmp/exc05b-t6-red.log`; lỗi `Unresolved reference 'KeystoreSecretWrapper'`).
- [ ] **Step 3: Cài đặt** bằng `KeyStore.getInstance("AndroidKeyStore")`, `KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")`, `Cipher.getInstance("AES/GCM/NoPadding")`. Bắt `GeneralSecurityException`/`IOException` trong `unwrap` và trả `null`; `wrap` có thể ném (không lưu được thì thất bại lớn tiếng).
- [ ] **Step 4: Build APK + APK test, kiểm exit code.** Chạy theo quy trình thiết bị an toàn của Task 1 (Step 1) rồi:
  ```bash
  adb -s $S shell am force-stop com.mckimquyen.reader
  timeout 120 adb -s $S shell am instrument --user 0 -w -r -e class com.mckimquyen.reader.infrastructure.p2psync.KeystoreSecretWrapperDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/exc05b-t6-inst.log 2>&1; echo "instrument_exit=$?"; tail -12 /tmp/exc05b-t6-inst.log
  ```
  Expected: `OK (6 tests)`. Nếu quá timeout: force-stop, kiểm tiến trình lạ, **không** báo test pass.
- [ ] **Step 5: Mutation:** đổi `GCM_TAG` hay bỏ `setRandomizedEncryptionRequired(true)` không dễ quan sát; thay vào đó đổi `unwrap` thành bỏ qua kiểm tag (`return plain nonce`) là không khả thi. Ghi rõ trong báo cáo rằng mutation cho lớp này chỉ ở mức: đổi alias giữa `wrap` và `unwrap` làm `unwrapReturnsNull` đỏ.
- [ ] **Step 6: Gỡ test APK khỏi Pixel** (`adb -s $S uninstall --user 0 com.mckimquyen.reader.test`) để không để lại rác trên máy dùng chung. Commit.

---

## Task 7: NSD (`NsdTxtCodec`, `NsdPlatform`, `PeerDiscovery`)

**Files:**
- Create: `domain/p2psync/NsdTxtCodec.kt`, `infrastructure/p2psync/NsdPlatform.kt`, `infrastructure/p2psync/PeerDiscovery.kt`
- Test: `NsdTxtCodecTest.kt`, `PeerDiscoveryTest.kt`, và `app/src/androidTest/.../PairingNsdDeviceTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object NsdTxtCodec {
      const val SERVICE_TYPE = "_rsscathub._tcp"
      const val KEY_VERSION = "v"; const val KEY_NONCE = "n"; const val VERSION = "1"
      fun encode(nonce: ByteArray): Map<String, ByteArray>          // đúng 2 khoá
      fun decode(attributes: Map<String, ByteArray?>): ByteArray?   // nonce, hoặc null nếu thiếu/lạ/sai
  }
  data class DiscoveredPeer(val serviceName: String, val host: String, val port: Int, val nonce: ByteArray)

  interface NsdPlatform {
      fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit)
      fun unregister()
      fun discover(onFound: (serviceName: String, host: String?, port: Int, attributes: Map<String, ByteArray?>) -> Unit, onLost: (String) -> Unit, onFailed: () -> Unit)
      fun stopDiscovery()
  }
  class PeerDiscovery(private val platform: NsdPlatform) {
      val peers: StateFlow<List<DiscoveredPeer>>
      fun advertise(port: Int, nonce: ByteArray): Boolean   // tên dịch vụ ngẫu nhiên, không chứa tên máy
      fun startDiscovery()
      fun stop()                                            // huỷ đăng ký và dừng tìm
  }
  ```

- [ ] **Step 1: Test đỏ `NsdTxtCodecTest`:** encode chỉ có `v` và `n`; `decode` từ chối khoá lạ (ví dụ `name`), thiếu `n`, `v != "1"`, nonce sai độ dài/ký tự; không bao giờ có khoá `token`/`secret`.
- [ ] **Step 2: Test đỏ `PeerDiscoveryTest`** với `NsdPlatform` giả: địa chỉ không phải RFC1918 (`8.8.8.8`, `127.0.0.1`, `169.254.1.1`, IPv6) bị bỏ; địa chỉ `192.168.x.x` được giữ; `stop()` gọi `unregister` **và** `stopDiscovery`; tên dịch vụ do `advertise` sinh **không** chứa chuỗi cố định nào dùng được làm định danh máy và khác nhau mỗi lần; dịch vụ mất (`onLost`) bị xoá khỏi `peers`; TXT sai bị bỏ không ném lỗi; trùng `serviceName` không nhân đôi.
- [ ] **Step 3: Xác nhận đỏ bằng log/XML.**
- [ ] **Step 4: Cài đặt codec và `PeerDiscovery`** (lọc bằng `LanAddress.isPrivateIpv4(host)`). Chưa viết `AndroidNsdPlatform`.
- [ ] **Step 5: Cài `AndroidNsdPlatform`:** `Build.VERSION.SDK_INT >= 34` dùng `NsdManager.registerServiceInfoCallback(serviceInfo, executor, callback)` và `serviceInfo.hostAddresses`; dưới 34 dùng `resolveService` (đánh dấu `@Suppress("DEPRECATION")` có chú thích vì sao). `NsdServiceInfo.serviceType = NsdTxtCodec.SERVICE_TYPE`; `setAttribute(key, String(bytes, UTF_8))` cho `v` và `n` chỉ khi giá trị là ASCII hợp lệ (nonce hex). Mọi callback bọc `try/catch` để không ném ra luồng NSD.
- [ ] **Step 6: Mutation:** bỏ kiểm RFC1918; bỏ `unregister()` trong `stop()`; để `decode` bỏ qua khoá lạ. Mỗi lần đúng test liên quan đỏ.
- [ ] **Step 7: Test thiết bị `PairingNsdDeviceTest`** (trên Pixel, API 37, sau Task 1 đã có quyền): đăng ký dịch vụ thật và tự tìm lại chính nó qua `PeerDiscovery`, khẳng định nhận lại đúng nonce và host là IPv4 riêng; `stop()` rồi `discover` lại không thấy dịch vụ. Chạy theo quy trình an toàn thiết bị + `timeout 120`. Nếu thiết bị cần cấp quyền: `pm grant` ở bước chuẩn bị, **thu hồi lại sau test**.
- [ ] **Step 8: Kiểm bằng Mac:** `dns-sd -B _rsscathub._tcp local.` rồi `dns-sd -L <tên> _rsscathub._tcp local.` để thấy dịch vụ do app quảng bá, chỉ có TXT `v` và `n`. Ghi kết quả vào báo cáo.
- [ ] **Step 9: Gỡ test APK, commit.**

---

## Task 8: Nối vào `LocalSyncViewModel`, DI, UI quyền, danh sách máy đã ghép, Second Screen

**Files:**
- Modify: `infrastructure/di/MirrorModule.kt`, `ui/page/p2psync/LocalSyncViewModel.kt`, `LocalSyncUiState.kt`, `LocalSyncPage.kt`, `ui/page/common/HomeEntry.kt`, `ui/page/home/read/mirror/MirrorUiState.kt` (thêm `data object NoPermission`), `ui/page/home/read/mirror/SecondScreenViewModel.kt`, `ui/page/home/read/mirror/SecondScreenSheetContent.kt` (nhánh `when` ở dòng ~96 phải xử lý `NoPermission`, nếu không biên dịch lỗi), và `ReadingPage.kt` dòng ~327 (`onToggle = { on -> if (on) secondScreenViewModel.start() ... }`) để xin quyền trước khi `start()`
- Create: `ui/ext/LanPermission.kt`
- Test: `LocalSyncViewModelTest.kt`, `LocalSyncPageTest.kt`, `SecondScreenViewModelTest.kt` (mở rộng), `LanPermissionTest.kt`

**Interfaces:**
- Consumes: Task 2–7.
- Produces:
  ```kotlin
  // LocalSyncUiState bổ sung:
  data class Sending(...)  // thêm: val pairedPeerId: String? = null
  enum class LocalSyncError { ..., LAN_PERMISSION, PAIRING_BROKEN, NO_PEER_FOUND }

  // LocalSyncViewModel bổ sung:
  val pairedPeers: StateFlow<List<PairedPeer>>
  val discovered: StateFlow<List<DiscoveredPeer>>
  fun startPairedSend(localAccountId: Int, label: String)       // sinh secret, lưu UNCONFIRMED, bật phiên + quảng bá
  fun receiveFromPaired(localAccountId: Int, peerId: String, peer: DiscoveredPeer)
  fun forgetPeer(peerId: String)
  fun confirmPeer(peerId: String)
  ```
  `LanPermission`: `fun hasLanAccess(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean` (true khi `!LanAccess.required(sdkInt)` hoặc quyền đã cấp), `@Composable fun rememberLanPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit` dùng `ActivityResultContracts.RequestPermission` với `LanAccess.PERMISSION`.

- [ ] **Step 1: Viết test đỏ cho ViewModel** (dùng `FakeSyncEngine`, `FakeLan`, `FakeNsdPlatform`, `FakeSecretWrapper`):
  `startPairedSendStoresAnUnconfirmedPeerBeforeServing`; `pairedSendUsesDerivedTokenNotRandom` (`engine.lastToken` bằng `SessionTokenDerivation.derive(secret, nonce)` với nonce đang quảng bá); `pairedSendAdvertisesNonceOnly` (TXT chỉ `v`,`n`; không có token/secret); `receiveFromPairedComputesTheTokenAndFetchesFromTheDiscoveredHost`; `receiveFromPairedWithBrokenEntryShowsPairingBroken` và không mở kết nối; `successfulPairedPreviewMarksReceiverEntryConfirmed` **chỉ sau** giải mã thành công; `decryptFailureDoesNotConfirm`; `forgetPeerRemovesAndStopsAdvertisingIfThatPeerIsActive`; `stopSendStopsAdvertising`; `networkLossStopsAdvertising`; `expiryStopsAdvertising`; `onScreenStoppedStopsAdvertisingAndDiscovery`; `lanPermissionMissingBlocksStartWithoutOpeningSockets` (không gọi `engine.startSyncWithToken`, không gọi `platform.register`).
- [ ] **Step 2: Viết test đỏ cho Compose** (`LocalSyncPageTest`): danh sách "Máy đã ghép" hiển thị nhãn và trạng thái; nút Quên gọi callback; nút "Ghép đôi máy mới" yêu cầu nhãn không rỗng; khi `LAN_PERMISSION` hiện thông báo và nút mở cài đặt; mục `needsRepair` hiện "cần ghép lại" và không có nút gửi/nhận; danh sách máy tìm thấy; **luồng nhập URL + secret của EXC-05a vẫn còn và các test cũ vẫn xanh**.
- [ ] **Step 3: Viết test đỏ cho Second Screen:** `startWithoutLanPermissionShowsNoPermissionAndOpensNoSocket` (ViewModel nhận một `LanGate` interface mỏng `fun granted(): Boolean` để test không cần Android; nếu chưa có quyền → trạng thái mới `MirrorUiState.NoPermission`, không gọi `engine.start`). Thêm một test Compose cho `SecondScreenSheetContent` khẳng định trạng thái `NoPermission` hiện chuỗi `second_screen_lan_permission_denied` và nút mở cài đặt. `SecondScreenViewModel` hiện có constructor `(engine, lan, defaultDispatcher)`; thêm tham số `lanGate` ở cuối và **cập nhật mọi nơi dựng nó trong test hiện có** (`grep -rn "SecondScreenViewModel(" app/src/test`).
- [ ] **Step 4: Xác nhận đỏ đúng lý do bằng XML.**
- [ ] **Step 5: Cài đặt** ViewModel, state, page, `HomeEntry` (truyền `lanGranted`, requester, danh sách), `MirrorModule` (cung cấp `PairingStore` với `KeystoreSecretWrapper()` và `PeerDiscovery(AndroidNsdPlatform(context))` — `@ViewModelScoped` để dừng khi ViewModel bị huỷ), `SecondScreenViewModel` + `LanPermission`. Giữ nguyên mọi đường cũ.
- [ ] **Step 6: Chạy toàn bộ suite liên quan** (`LocalSyncViewModelTest`, `LocalSyncPageTest`, `SecondScreenViewModelTest`, `MirrorServer*Test`, `LanPermissionTest`) và đọc XML: không test nào của EXC-05a/EXC-13 bị hỏng.
- [ ] **Step 7: Mutation:** bỏ kiểm quyền ở `startPairedSend`; đặt `confirmPeer` trước khi giải mã; bỏ `platform.unregister()` ở `shutdown`; để TXT chứa token. Mỗi lần đúng test đỏ. Khôi phục.
- [ ] **Step 8: Commit nhỏ theo từng khối (ViewModel+DI; Page+HomeEntry; SecondScreen+gate), mỗi commit chỉ sau khi test xanh.**

---

## Task 9: Chuỗi 38 locale

**Files:** 38 file `app/src/main/res/values*/strings.xml`; test `StringResourceParityTest` có sẵn.

**Chuỗi cần thêm (key — EN — VI):**
- `local_sync_lan_permission_denied` — "Local network access is needed to find and reach your other device. Allow it in Settings." — "Cần quyền truy cập mạng nội bộ để tìm và kết nối thiết bị còn lại. Hãy cho phép trong Cài đặt."
- `local_sync_open_settings` — "Open Settings" — "Mở Cài đặt"
- `local_sync_paired_title` — "Paired devices" — "Máy đã ghép"
- `local_sync_pair_new` — "Pair a new device" — "Ghép đôi máy mới"
- `local_sync_pair_label_hint` — "Name for this device" — "Tên cho máy này"
- `local_sync_peer_unconfirmed` — "Not confirmed yet" — "Chưa xác nhận"
- `local_sync_peer_confirmed` — "Paired" — "Đã ghép"
- `local_sync_peer_needs_repair` — "Pairing lost, pair again" — "Mất ghép đôi, hãy ghép lại"
- `local_sync_forget_peer` — "Forget this device" — "Quên máy này"
- `local_sync_confirm_peer` — "Mark as paired" — "Đánh dấu đã ghép"
- `local_sync_nearby_title` — "Devices nearby" — "Máy ở gần"
- `local_sync_no_peer_found` — "No paired device found on this network. Check both are on the same Wi-Fi." — "Không thấy máy đã ghép trên mạng này. Hãy kiểm tra cả hai cùng Wi-Fi."
- `local_sync_pairing_limits` — "Pairing keeps one fixed secret, so anyone who learns it can read past packages. Forget a device you no longer trust." — "Ghép đôi giữ một secret cố định, nên ai biết được nó có thể đọc các gói cũ. Hãy quên máy bạn không còn tin cậy."
- `second_screen_lan_permission_denied` — "Allow local network access to use Second Screen." — "Hãy cho phép truy cập mạng nội bộ để dùng Màn hình thứ hai."

- [ ] **Step 1:** Thêm EN vào `values/strings.xml` và chạy `StringResourceParityTest`; xác nhận **đỏ** do 37 file còn lại thiếu key (đọc XML).
- [ ] **Step 2:** Viết bản VI chuẩn tay vào `values-vi/strings.xml`; 36 locale còn lại dùng nguyên văn tiếng Anh (cùng kỹ thuật chèn sau một mỏ neo có sẵn trong mỗi file; kiểm `xmllint --noout` cả 38 file; apostrophe trong EN phải escape `\'`).
- [ ] **Step 3:** Chạy `StringResourceParityTest` xanh (6/6), `grep` xác nhận không có `%d` đứng trước danh từ số nhiều trong chuỗi mới.
- [ ] **Step 4:** Commit.

---

## Task 10: Toàn bộ suite, lint, build, kiểm chứng thiết bị, smoke, báo cáo

**Files:**
- Create: `doc/task/done/EXC-05b_pairing-and-nsd_DONE.md`
- Modify: `doc/feature.md`, `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`
- **Không** xoá/di chuyển `doc/task/todo/EXC-05_local-p2p-sync.md`.

- [ ] **Step 1: Full suite từ đầu.** `./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/exc05b-full.log 2>&1; echo "gradle_exit=$?"`, rồi đếm suite/test/lỗi/file cũ từ `app/build/test-results/testDevDebugUnitTest/*.xml` (cùng script đếm đã dùng cho EXC-02). Expected: 0 lỗi; số test tăng so với 2.494.
- [ ] **Step 2: Lint.** `./gradlew lintDevDebug`, đọc `app/build/reports/lint-results-devDebug.xml`: 0 lỗi, đúng 2.463 cảnh báo, 0 hit liên quan `local_sync_`/`PairingStore`/`PeerDiscovery`/`LanAccess`. Nếu có cảnh báo mới (ví dụ API cũ của NSD), sửa hoặc ghi rõ, **không** nới baseline.
- [ ] **Step 3: Build.** `./gradlew assembleDevDebug assembleDevDebugAndroidTest`, kiểm exit code.
- [ ] **Step 4: Kiểm thiết bị an toàn** (Task 1 Step 1), sao lưu Room + DataStore (Task 1 Step 2) vào `/tmp/exc05b-final-backup`.
- [ ] **Step 5: Instrumentation** của cả `KeystoreSecretWrapperDeviceTest` và `PairingNsdDeviceTest` trên Pixel với `timeout 120`, đọc kết quả, rồi gỡ test APK.
- [ ] **Step 6: Smoke Pixel (Android 17):**
  1. Cài APK mới (`-r`), mở `MainActivity` trực tiếp, cấp quyền LAN qua hộp thoại thật (không `pm grant`) để chứng minh luồng xin quyền; chụp màn hình bằng `adb exec-out screencap`.
  2. Local Sync › Ghép đôi máy mới (đặt nhãn) › xác nhận mục "Chưa xác nhận" xuất hiện.
  3. Từ Mac: `dns-sd -B _rsscathub._tcp local.` thấy dịch vụ; `dns-sd -L` cho TXT chỉ `v` và `n`.
  4. Tính token trên Mac bằng python3 `hmac` từ secret hiển thị và nonce trong TXT, rồi `curl http://<ip>:<port>/s/<token>` → `HTTP 200`, `application/octet-stream`, thân bắt đầu `RCS1`; `curl` với token sai → 404. (Mac chỉ kiểm vận chuyển; không giải mã.)
  5. Second Screen: mở một bài, bật Second Screen, `curl` URL → 200 sau khi có quyền.
  6. Thu hồi quyền (`pm revoke`), lặp phiên, xác nhận app hiện thông báo quyền thay vì treo.
  7. Nếu bất kỳ lúc nào quảng cáo toàn màn hình hoặc app lạ chiếm foreground: dừng, dùng Back, kiểm lại foreground; **không** chạm "Tiếp tục đến ứng dụng".
- [ ] **Step 7: Khôi phục và `cmp` cả 4 file** (Task 1 Step 8) với backup của Step 4; `PRAGMA integrity_check` trên bản khôi phục.
- [ ] **Step 8: Viết báo cáo `EXC-05b_pairing-and-nsd_DONE.md`** nêu trung thực: điểm audit; số test, lint, build; kết quả instrumentation thật; số đo quyền LAN trước/sau/thu hồi; kết quả smoke từng mục; và **những gì chưa làm**: hai Android thật, NSD API 26–33 trên thiết bị, giải mã trên Mac, tự động/hai chiều/ba giây, forward secrecy, ack giữa hai máy. Ghi rõ EXC-05 gốc **chưa** hoàn tất.
- [ ] **Step 9: Cập nhật `doc/feature.md`** (thêm EXC-05b vào "Recently implemented" nhưng giữ EXC-05 ở "In progress") và bảng `05_EXCLUSIVE_KILLER_FEATURES.md` (ghi chú EXC-05b, trạng thái EXC-05 vẫn In progress). Commit tài liệu; không push.

---

## Self-Review (đối chiếu spec)

| Yêu cầu spec | Task |
|---|---|
| Quyền LAN: khai báo, ngưỡng API 37, chặn trước khi mở socket, thông báo từ chối | 1, 2, 8, 9 |
| Xác nhận giả thuyết Android 17 bằng cấp/không cấp | 1 |
| Second Screen phải có quyền | 8 |
| `PairingStore` tệp riêng, Keystore bọc, mục hỏng → cần ghép lại, Quên máy | 5, 6 |
| Loại khỏi `backup_rules.xml` và `data_extraction_rules.xml` (cloud + device-transfer) | 5 |
| Token HMAC-SHA256, nonce trong TXT, không lộ token/secret, so sánh hằng thời gian | 3, 7, 8 |
| Engine overload nhận token ngoài, biến thể cũ không đổi, cập nhật fake | 4 |
| NSD `_rsscathub._tcp`, TXT chỉ `v`,`n`, lọc RFC1918, huỷ khi dừng/mất mạng/hết hạn/nền | 7, 8 |
| Ghép lần đầu: gửi lưu `UNCONFIRMED`; nhận chỉ lưu sau giải mã thành công | 8 |
| Các lần sau chọn máy đã ghép, vẫn xem trước + Apply | 8 |
| Giữ luồng nhập URL + secret EXC-05a | 8 |
| UI + chuỗi 38 locale, tránh `PluralsCandidate` | 8, 9 |
| Suite, lint 2.463, build, instrumentation thật, smoke, backup/restore `cmp` | 10 |
| EXC-05 gốc vẫn mở; ghi rõ giới hạn | 10 |

**Rủi ro đã nêu trong kế hoạch:** nếu Task 1 cho thấy cấp quyền không sửa được timeout, toàn bộ kế hoạch dừng để người dùng quyết định. Mutation cho `KeystoreSecretWrapper` bị hạn chế vì khó quan sát thuộc tính khoá; báo cáo phải nói thẳng.
