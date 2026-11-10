# EXC-05c Hai lượt đồng bộ thủ công và đổi vai Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho hai máy đã ghép đôi đồng bộ hai chiều bằng hai lượt thủ công tuần tự (A gửi/B nhận, rồi B gửi lại/A nhận), mỗi máy tự xem preview và tự Apply vào DB của mình.

**Architecture:** Chỉ thêm điều phối trong `LocalSyncViewModel` và UI. Ngữ cảnh "peer vừa nhận" (`ReplyPeer`) sống trong ViewModel, gán sau khi nhận qua `receiveFromPaired` xác thực thành công; hành động gửi lại gọi `servePaired` hiện có nên snapshot luôn mới. Không đổi `LocalSyncPlanner`, `LocalSyncRepository`, `MirrorServer`, `SyncSessionTransport`, giao thức, Room (v20) hay dependency.

**Tech Stack:** Kotlin, Compose Material3, Hilt ViewModel, Room in-memory (Robolectric), JUnit4, MockK, thật `MirrorServer` + `SyncSessionTransport` qua loopback.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-10-exc-05c-manual-round-trip-design.md` (commit `86a9a364`). Owner đã duyệt từng phần.
- Nhánh `dev`. Không worktree, không push, không nhắc PR, không commit `.idea`.
- Không migration (Room giữ v20), không dependency mới, không POST/upload, không Apply từ xa, không ACK.
- Merge đơn điệu giữ nguyên: unread AND, starred OR, `readAt` sớm nhất. Chỉ tài khoản Local.
- Mỗi máy Apply riêng; UI không được khẳng định "hai máy đã đồng bộ xong". Chỉ nói "đã áp dụng trên máy này".
- Không lưu `ReplyPeer` vào DataStore/Room/SavedStateHandle. Không giữ secret trong UI state.
- Chuỗi mới đủ **38 locale** (EN và VI viết riêng, còn lại English); không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` phải xanh.
- Code: không `!!`/`late` thiếu lý do, không magic number, soát mọi coroutine/listener/socket.
- TDD: test đỏ đúng lý do trước, code tối thiểu, xanh, mutation cho guard quan trọng, commit riêng từng task. Không chain `git commit` sau build nếu chưa kiểm exit code. Ghi output Gradle vào file, đọc XML kết quả. Dùng `printf` cho dòng bắt đầu bằng `====`.
- Thiết bị: chỉ `BG6 118743744X002560` và `Pixel 2B051FDH3006MU` (owner đã cho phép). Trước mọi thao tác: `adb devices`, `dumpsys activity` (instrumentation/foreground), `ps` tiến trình lạ; dừng và hỏi nếu có. Chỉ force-stop `com.mckimquyen.reader`. Không `connected*`. `run-as com.mckimquyen.reader` (không `--user`). Không xoá DataStore. Chỉ chạm nút quảng cáo bằng Back; không bấm "Tiếp tục đến ứng dụng".
- Nếu test chứng minh cần đổi giao thức hoặc domain/transport: **dừng và hỏi lại owner**, không tự mở rộng.

## Giao diện hiện có (đã đọc, dùng nguyên)

```kotlin
// LocalSyncViewModel (ui/page/p2psync)
fun servePaired(localAccountId: Int, peerId: String)          // đã có; snapshot mới + secret đã lưu
fun receiveFromPaired(localAccountId: Int, peerId: String, host: String, port: Int, nonce: ByteArray)
fun cancelPreview()      // hiện được gọi cả bởi nút Huỷ và onScreenStopped()
fun applyPreview(localAccountId: Int)
fun forgetPeer(peerId: String)
fun stopSend(); fun onScreenStopped(); fun startFindingPeers(); fun stopFindingPeers()
val state: StateFlow<LocalSyncUiState>; val pairedPeers: StateFlow<List<PairedPeer>>
// LocalSyncUiState: Idle, Starting, Sending(url, secretHex?, expiresAtMillis, pairedPeerId?), Receiving, Preview(incoming, plan), Applying, Done(result), Error(kind)
// LocalSyncPage: tham số mặc định cho các callback pairing; tab SEND_TAB=0, RECEIVE_TAB=1 là state `remember` trong composable.
```

Test fakes có sẵn trong `LocalSyncPairingViewModelTest.kt` (PairFakeEngine/PairFakeLan/PairFakeNsd/PairFakeWrapper, `vm()`, `store`, `outgoing`, `plan`, `ACCOUNT`).

## File Structure

| File | Trách nhiệm |
|---|---|
| `ui/page/p2psync/LocalSyncUiState.kt` | thêm `data class ReplyPeer(accountId, peerId)` (không chứa secret) |
| `ui/page/p2psync/LocalSyncViewModel.kt` | `replyPeer: StateFlow<ReplyPeer?>`, tách huỷ chủ động/cleanup, `waitForReply()`, `replyToPeer()` |
| `ui/page/p2psync/LocalSyncPage.kt` | nút Dừng và chờ nhận lại, khối Gửi lại sau Apply/Huỷ, hướng dẫn, tab tự chuyển |
| `ui/page/common/HomeEntry.kt` | nối callback/`replyPeer` |
| `res/values*/strings.xml` | chuỗi mới ×38 |
| `test/.../LocalSyncReplyViewModelTest.kt` (mới) | test ViewModel của đổi vai |
| `test/.../LocalSyncPageReplyTest.kt` (mới) | test Compose |
| `test/.../infrastructure/p2psync/LocalSyncRoundTripIntegrationTest.kt` (mới) | hai DB + thật socket |
| `doc/task/done/EXC-05c_manual-round-trip_DONE.md` (mới), `doc/feature.md` | báo cáo và trạng thái |

---

### Task 1: ReplyPeer và quy tắc vòng đời trong ViewModel

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncUiState.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModel.kt`
- Test (tạo mới): `app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncReplyViewModelTest.kt`

**Interfaces:**
- Consumes: `LocalSyncViewModel` 8-arg constructor + `lanGate` lambda, các fake trong `LocalSyncPairingViewModelTest.kt` (private nên **copy** các class fake sang file test mới, không `import`).
- Produces: `data class ReplyPeer(val accountId: Int, val peerId: String)` và `val replyPeer: StateFlow<ReplyPeer?>` trên `LocalSyncViewModel`; `fun cancelPreview()` (chủ động, giữ `replyPeer`) và `private fun discardPreviewForLifecycle()` dùng trong `onScreenStopped()` (xoá `replyPeer`).

Quy tắc (theo spec 4.3):
- Gán `replyPeer` **chỉ** sau `fetchAndPreview` thành công khi đường vào là `receiveFromPaired(…, peerId)`.
- Giữ sau `cancelPreview()` và sau Apply thành công.
- Xoá khi: bắt đầu tác vụ gửi/nhận khác (`startSend/startPairedSend/servePaired/loadPreview/receiveFromPaired` sau khi guard busy đã qua), `forgetPeer(peerId)` đúng peer, `stopSend()`/`shutdown()` (mọi đường đóng phiên), `onScreenStopped()`, `onCleared()`, đổi `accountId` (khi `applyPreview` hoặc lượt mới dùng account khác), Apply thất bại (không giữ nút gửi lại).

- [ ] **Step 1: Viết test đỏ (ViewModel)**

Tạo `LocalSyncReplyViewModelTest.kt`: copy các class fake và phần `@Before/@After` từ `LocalSyncPairingViewModelTest.kt` (package, `ACCOUNT=1`, `TTL`, `PairFakeEngine`, `PairFakeLan`, `PairFakeNsd`, `PairFakeWrapper`, `outgoing`, `plan`, `vm()`), đổi tên fake thành `ReplyFake*` để tránh trùng tên top-level private trong cùng package. Thêm các test:

```kotlin
private val peerSecret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
private fun receivable(peerId: String, nonce: ByteArray = SessionTokenDerivation.newNonce()): String {
    val url = "http://192.168.1.30:4321/s/${SessionTokenDerivation.derive(peerSecret, nonce)}"
    every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), peerSecret)
    return url
}

@Test fun aVerifiedPairedReceiveRemembersThePeerAndAccount() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Preview)
    assertEquals(ReplyPeer(ACCOUNT, peer.peerId), vm.replyPeer.value)
}

@Test fun aFailedDecryptNeverCreatesAReplyPeer() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce()
    val url = "http://192.168.1.30:4321/s/${SessionTokenDerivation.derive(peerSecret, nonce)}"
    every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), PairingSecret.fromHex("f".repeat(32)))
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    assertNull(vm.replyPeer.value)
}

@Test fun aManualReceiveNeverCreatesAReplyPeer() = runTest(dispatcher) {
    val url = "http://192.168.1.30:4321/s/${"c".repeat(32)}"
    every { transport.fetch(url) } returns SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(outgoing), peerSecret)
    val vm = vm()
    vm.loadPreview(ACCOUNT, url, peerSecret.hex); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Preview)
    assertNull(vm.replyPeer.value)
}

@Test fun userCancelKeepsTheReplyPeerAndAppliesNothing() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    assertEquals(LocalSyncUiState.Idle, vm.state.value)
    assertEquals(ReplyPeer(ACCOUNT, peer.peerId), vm.replyPeer.value)
    coVerify(exactly = 0) { repository.apply(any(), any()) }
}

@Test fun leavingTheScreenClearsTheReplyPeer() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.onScreenStopped()
    assertNull(vm.replyPeer.value)
}

@Test fun aSuccessfulApplyKeepsTheReplyPeer() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.applyPreview(ACCOUNT); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Done)
    assertEquals(ReplyPeer(ACCOUNT, peer.peerId), vm.replyPeer.value)
}

@Test fun aFailedApplyClearsTheReplyPeerAndShowsTheDatabaseError() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    coEvery { repository.apply(ACCOUNT, any()) } throws IllegalStateException("db")
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.applyPreview(ACCOUNT); runCurrent()
    assertEquals(LocalSyncError.DATABASE, (vm.state.value as LocalSyncUiState.Error).kind)
    assertNull(vm.replyPeer.value)
}

@Test fun forgettingThatPeerClearsTheReplyPeerButForgettingAnotherDoesNot() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val other = store.add("Other", PairingSecret.generate(), PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    vm.forgetPeer(other.peerId)
    assertEquals(ReplyPeer(ACCOUNT, peer.peerId), vm.replyPeer.value)
    vm.forgetPeer(peer.peerId)
    assertNull(vm.replyPeer.value)
}

@Test fun startingAnotherTaskClearsTheReplyPeer() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    vm.startSend(ACCOUNT); runCurrent()
    assertNull(vm.replyPeer.value)
}

@Test fun clearingTheViewModelClearsTheReplyPeer() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.callOnCleared()
    assertNull(vm.replyPeer.value)
}
```

(Cần import `coVerify`, `coEvery`, `every`, `assertNull`, `assertTrue`, `assertEquals`, các kiểu domain như ở `LocalSyncPairingViewModelTest.kt`.)

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

```bash
./gradlew testDevDebugUnitTest --tests '*LocalSyncReplyViewModelTest' > /tmp/exc05c-t1-red.log 2>&1; echo exit=$?
grep -E '^e: ' /tmp/exc05c-t1-red.log | sed 's#file:///.*/##' | cut -c1-170 | head -4
```
Expected: exit≠0, lỗi biên dịch `Unresolved reference 'ReplyPeer'` / `'replyPeer'` (API chưa có). Đó là đỏ hợp lệ.

- [ ] **Step 3: Cài đặt tối thiểu**

`LocalSyncUiState.kt`:
```kotlin
/** The already paired device a successful receive came from; lets the user send our state back. Never holds a secret. */
data class ReplyPeer(val accountId: Int, val peerId: String)
```
`LocalSyncViewModel.kt`:
```kotlin
private val _replyPeer = MutableStateFlow<ReplyPeer?>(null)
val replyPeer: StateFlow<ReplyPeer?> = _replyPeer.asStateFlow()
```
- Trong `fetchAndPreview(...)`, sau khi `_state.value = LocalSyncUiState.Preview(...)` thành công **và** `confirmPeerId != null`: `_replyPeer.value = ReplyPeer(localAccountId, confirmPeerId)`. Trước khi bắt đầu fetch (đầu `fetchAndPreview`), đặt `_replyPeer.value = null` để lỗi giữa chừng không giữ peer cũ.
- Đầu các hàm bắt đầu tác vụ sau guard busy (`startSend`, `loadPreview`, `receiveFromPaired`, `startPairedSend`, `servePaired`/`canStartSession` đường đi thành công): `_replyPeer.value = null`. **Ngoại lệ:** `servePaired` gọi từ nút Gửi lại cũng xoá (lượt gửi bắt đầu thì ngữ cảnh nhận cũ hết hiệu lực).
- `cancelPreview()` không đụng `_replyPeer`.
- `onScreenStopped()`: sau khi xử lý trạng thái, `_replyPeer.value = null`; vì `Preview/Receiving` cần huỷ job mà không giữ peer, thay `cancelPreview()` bằng một hàm private `discardPreviewForLifecycle()` làm đúng phần huỷ (`receiveJob?.cancel(); receiveJob=null; activePackageId=null; _state.value=Idle`) rồi xoá `_replyPeer`.
- `applyPreview`: nhánh `catch (_: Exception)` đặt `_replyPeer.value = null`; nếu `activeAccountId != localAccountId` thì đã `return` sẵn, nhưng thêm `if (_replyPeer.value?.accountId != localAccountId) _replyPeer.value = null` đầu hàm để đổi account không giữ peer.
- `forgetPeer(peerId)`: `if (_replyPeer.value?.peerId == peerId) _replyPeer.value = null`.
- `onCleared()` và `shutdown(...)`: `_replyPeer.value = null`. (Lưu ý `shutdown(Idle)` do `stopSend()` cũng xoá; nếu test Task 2 cần giữ thì giữ nguyên quy tắc: gửi dừng thì ngữ cảnh nhận cũ đã bị xoá khi gửi bắt đầu, không ảnh hưởng.)

- [ ] **Step 4: Chạy, xác nhận xanh**

```bash
./gradlew testDevDebugUnitTest --tests '*LocalSyncReplyViewModelTest' --tests '*LocalSyncPairingViewModelTest' --tests '*LocalSyncViewModelTest' > /tmp/exc05c-t1-green.log 2>&1; echo exit=$?
```
Đọc XML: `app/build/test-results/testDevDebugUnitTest/TEST-*LocalSync*ViewModelTest.xml`; 0 failure. Các test cũ (29+39+31) phải còn xanh; nếu `cancelPreview` đổi hành vi ngoài ý muốn thì sửa chứ không sửa test cũ.

- [ ] **Step 5: Mutation**

1. Đổi `onScreenStopped()` thành dùng `cancelPreview()` (không xoá peer) → `leavingTheScreenClearsTheReplyPeer` phải đỏ.
2. Bỏ dòng gán `_replyPeer` sau Preview → `aVerifiedPairedReceiveRemembersThePeerAndAccount` đỏ.
3. Gán `_replyPeer` cả cho `loadPreview` → `aManualReceiveNeverCreatesAReplyPeer` đỏ.
Khôi phục từng lần bằng backup file; `cmp` xác nhận.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncUiState.kt app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncReplyViewModelTest.kt
git commit -m "feat(exc-05c): remember the paired peer a verified receive came from

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
(Chỉ commit khi bước 4 có exit 0.)

---

### Task 2: Dừng và chờ nhận lại, và gửi lại

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncReplyViewModelTest.kt` (thêm)

**Interfaces:**
- Consumes: Task 1 (`ReplyPeer`, `replyPeer`), `servePaired`, `shutdown`, `startFindingPeers`.
- Produces: `fun waitForReply()` và `fun replyToPeer(localAccountId: Int)`.

Hành vi:
- `waitForReply()`: chỉ hợp lệ khi `state is Sending` và `pairedPeerId != null` (trong mọi trường hợp khác không làm gì). Gọi `shutdown(Idle)` (đóng server + discovery + job), rồi `startFindingPeers()` (kiểm quyền LAN). Không mở server mới, không đổi pairing, không xoá peer.
- `replyToPeer(localAccountId)`: lấy `replyPeer.value`; nếu null hoặc `accountId != localAccountId` thì không làm gì. Kiểm peer còn trong `pairingStore.list()` và `!needsRepair`; không thì `Error(PAIRING_BROKEN)` và xoá `replyPeer`. Ngược lại gọi `servePaired(localAccountId, peer.peerId)` (đã guard busy + LAN + snapshot mới).

- [ ] **Step 1: Viết test đỏ**

```kotlin
@Test fun waitingForAReplyClosesTheSessionAndStartsFindingPeers() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val vm = vm()
    vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Sending)
    vm.waitForReply()
    assertEquals(LocalSyncUiState.Idle, vm.state.value)
    assertFalse(engine.isRunning)
    assertEquals(1, nsd.unregisters)
    assertEquals(1, nsd.discoveries)
    assertEquals(1, engine.tokenCalls)          // no second server was opened
    assertEquals(listOf(peer.peerId), store.list().map { it.peerId })
}

@Test fun waitingForAReplyDoesNothingWithoutAPairedSession() = runTest(dispatcher) {
    val vm = vm()
    vm.waitForReply()
    assertEquals(LocalSyncUiState.Idle, vm.state.value)
    assertEquals(0, nsd.discoveries)
    vm.startSend(ACCOUNT); runCurrent()        // manual session, no peer
    vm.waitForReply()
    assertTrue(vm.state.value is LocalSyncUiState.Sending)
}

@Test fun waitingForAReplyWithoutLanPermissionShowsThePermissionErrorAfterClosing() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val vm = vm()
    vm.servePaired(ACCOUNT, peer.peerId); runCurrent()
    lanGranted = false
    vm.waitForReply()
    assertFalse(engine.isRunning)
    assertEquals(LocalSyncError.LAN_PERMISSION, error(vm))
    assertEquals(0, nsd.discoveries)
}

@Test fun replyingServesAFreshSnapshotUnderTheStoredSecretAndShowsNoSecret() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.applyPreview(ACCOUNT); runCurrent()
    clearMocks(repository, answers = false, recordedCalls = true)
    vm.replyToPeer(ACCOUNT); runCurrent()
    val sending = vm.state.value as LocalSyncUiState.Sending
    assertNull(sending.secretHex)
    assertEquals(peer.peerId, sending.pairedPeerId)
    coVerify(exactly = 1) { repository.snapshot(ACCOUNT) }   // snapshot taken now, after apply
    assertEquals(listOf(peer.peerId), store.list().map { it.peerId })
    coVerify(exactly = 0) { repository.apply(any(), any()) }  // no second apply
}

@Test fun replyingAfterUserCancelServesTheCurrentDatabaseNotTheReceivedPackage() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    vm.replyToPeer(ACCOUNT); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Sending)
    coVerify(exactly = 0) { repository.apply(any(), any()) }
    val plain = SyncEnvelopeCrypto.decrypt(engine.lastCiphertext!!, peerSecret)
    assertTrue(plain.isNotEmpty())
}

@Test fun replyingIsRefusedWhenThePeerWasForgottenOrNeedsRepair() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    wrapper.keyPresent = false
    vm.refreshPeers()
    vm.replyToPeer(ACCOUNT); runCurrent()
    assertEquals(LocalSyncError.PAIRING_BROKEN, error(vm))
    assertNull(vm.replyPeer.value)
    assertEquals(0, engine.tokenCalls)
}

@Test fun replyingForAnotherAccountOrWithoutAReplyPeerDoesNothing() = runTest(dispatcher) {
    val vm = vm()
    vm.replyToPeer(ACCOUNT); runCurrent()
    assertEquals(LocalSyncUiState.Idle, vm.state.value)
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    vm.cancelPreview()
    vm.replyToPeer(ACCOUNT + 1); runCurrent()
    assertEquals(LocalSyncUiState.Idle, vm.state.value)
    assertEquals(0, engine.tokenCalls)
}

@Test fun replyingWhileBusyDoesNotStartASecondSession() = runTest(dispatcher) {
    val peer = store.add("Tablet", peerSecret, PairingStatus.CONFIRMED)
    val nonce = SessionTokenDerivation.newNonce(); receivable(peer.peerId, nonce)
    val vm = vm()
    vm.receiveFromPaired(ACCOUNT, peer.peerId, "192.168.1.30", 4321, nonce); runCurrent()
    // state is Preview, not Idle: the guard must reject
    vm.replyToPeer(ACCOUNT); runCurrent()
    assertTrue(vm.state.value is LocalSyncUiState.Preview)
    assertEquals(0, engine.tokenCalls)
}
```
(Thêm import `io.mockk.clearMocks`. Nếu `clearMocks` làm rối stub, thay bằng đếm `coVerify(atLeast = 1)` rồi so sánh số lần gọi trước/sau; mục tiêu là chứng minh `snapshot` được gọi **sau** Apply.)

- [ ] **Step 2: Chạy, đỏ đúng lý do** — `Unresolved reference 'waitForReply'/'replyToPeer'`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
/** Close the current paired send session and start listening, so the other phone can send its state back. */
fun waitForReply() {
    val sending = _state.value as? LocalSyncUiState.Sending ?: return
    if (sending.pairedPeerId == null) return
    shutdown(LocalSyncUiState.Idle)
    startFindingPeers()
}

/** Send our current state back to the peer the last verified receive came from. */
fun replyToPeer(localAccountId: Int) {
    val reply = _replyPeer.value ?: return
    if (reply.accountId != localAccountId) return
    val peer = pairingStore.list().firstOrNull { it.peerId == reply.peerId }
    if (peer == null || peer.needsRepair) {
        _replyPeer.value = null
        _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
        return
    }
    servePaired(localAccountId, peer.peerId)
}
```
Lưu ý: `replyToPeer` chỉ được phép chạy khi `canStartSession()` đúng (guard busy) — `servePaired` đã kiểm; nhưng **không** được xoá `replyPeer` trước khi guard qua (test `replyingWhileBusy…`), nên chỉ xoá khi `servePaired` thực sự bắt đầu: đặt việc `_replyPeer.value = null` trong `startPairedSession` sau `canStartSession()` đã trả true (đã làm ở Task 1). Với nhánh Error `PAIRING_BROKEN`, kiểm tra guard busy trước: nếu `_state` không phải Idle/Error/Done thì `return` trước khi ghi Error.

- [ ] **Step 4: Chạy, xanh.** Lệnh như Task 1 Step 4, thêm `--tests '*LocalSyncReplyViewModelTest'`. 0 failure.

- [ ] **Step 5: Mutation**
1. `waitForReply` bỏ `shutdown` → test đầu đỏ (engine vẫn chạy).
2. `replyToPeer` tái dùng `Preview.incoming` thay vì `servePaired` → `…ServesAFreshSnapshot…` đỏ.
3. Bỏ kiểm `reply.accountId` → `replyingForAnotherAccount…` đỏ.
4. Bỏ guard busy (ghi Error khi state Preview) → `replyingWhileBusy…` đỏ.

- [ ] **Step 6: Commit** `feat(exc-05c): close the paired session and send back on request`. Chỉ khi exit 0.

---

### Task 3: UI đổi vai trong LocalSyncPage và HomeEntry

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/res/values*/strings.xml` (38 file)
- Test (tạo mới): `app/src/test/java/com/mckimquyen/reader/ui/page/p2psync/LocalSyncPageReplyTest.kt`

**Interfaces:**
- Consumes: `ReplyPeer`, `replyPeer`, `waitForReply`, `replyToPeer`.
- Produces: tham số mới của `LocalSyncPage` có **giá trị mặc định**: `replyPeerLabel: String? = null`, `onWaitForReply: () -> Unit = {}`, `onReplyToPeer: () -> Unit = {}`; hằng tag `SYNC_WAIT_REPLY_TAG = "sync_wait_reply"`, `SYNC_REPLY_TAG = "sync_reply"`, `SYNC_REPLY_NOTE_TAG = "sync_reply_note"`, `SYNC_REPLY_INSTRUCTION_TAG = "sync_reply_instruction"`, `SYNC_ROLE_HINT_TAG = "sync_role_hint"`, `SYNC_MERGE_LIMIT_TAG = "sync_merge_limit"` (cả sáu khai báo `const val` top-level trong `LocalSyncPage.kt`).

Hành vi UI (theo spec 4.1/4.2):
- `SendSession` với `state.pairedPeerId != null`: thêm đoạn hướng dẫn (`SYNC_ROLE_HINT_TAG`) và nút **Dừng và chờ nhận lại** (`SYNC_WAIT_REPLY_TAG`) gọi `onWaitForReply()` rồi `tab = RECEIVE_TAB`. Nút Stop thường giữ nguyên. Phiên manual (không `pairedPeerId`) không có nút mới.
- Khi `state is Done` hoặc (`state is Idle` và `replyPeerLabel != null`): hiện khối trả lời có nút **Gửi lại cho ‹nhãn›** (`SYNC_REPLY_TAG`) + ghi chú `SYNC_REPLY_NOTE_TAG`: sau Apply → "đã áp dụng trên máy này…", sau huỷ → "dữ liệu nhận chưa được áp dụng…". Phân biệt bằng `state is Done`.
- Khối trả lời luôn kèm `local_sync_reply_instruction` (nhờ máy kia dừng gửi và mở Nhận trước) gắn tag `SYNC_REPLY_INSTRUCTION_TAG`. Trên tab Gửi (SendIdle) và trong khối trả lời hiện `local_sync_merge_limit` (tag `SYNC_MERGE_LIMIT_TAG = "sync_merge_limit"`) để nói rõ giới hạn chưa đọc/bỏ sao.
- Không bao giờ hiện chữ ngụ ý "cả hai máy đã xong".
- Nút Gửi lại `enabled = isLocal && !busy`; bấm gọi `onReplyToPeer()`.
- `HomeEntry`: `val replyPeer by syncViewModel.replyPeer.collectAsState()`; nhãn = `pairedPeers.firstOrNull { it.peerId == replyPeer?.peerId }?.label` (null nếu không còn peer hoặc `needsRepair`); `onWaitForReply = syncViewModel::waitForReply`, `onReplyToPeer = { syncViewModel.replyToPeer(context.currentAccountId) }`.

Chuỗi mới (EN → VI), khoá và nội dung; tất cả không dùng `%d` trước danh từ số nhiều; chỉ placeholder `%1$s` cho nhãn:
- `local_sync_wait_reply` "Stop and wait to receive back" → "Dừng và chờ nhận lại"
- `local_sync_role_hint` "After the other phone has received, stop here and switch to Receive. It can then send its changes back." → "Sau khi máy kia đã nhận, hãy dừng ở đây và chuyển sang Nhận. Máy kia sau đó có thể gửi thay đổi của nó lại."
- `local_sync_reply_to` "Send back to %1$s" → "Gửi lại cho %1$s"
- `local_sync_reply_note_applied` "Changes were applied on this phone only. The other phone has its own preview and confirmation." → "Thay đổi chỉ được áp dụng trên máy này. Máy kia có bản xem trước và xác nhận riêng."
- `local_sync_reply_note_cancelled` "The received changes were not applied. You can still send this phone's data back." → "Thay đổi vừa nhận chưa được áp dụng. Bạn vẫn có thể gửi dữ liệu của máy này lại."
- `local_sync_reply_instruction` "Ask the other phone to stop sending and open Receive first." → "Hãy nhờ máy kia dừng gửi và mở mục Nhận trước."
- `local_sync_merge_limit` "Merging only keeps read and starred marks; marking unread or removing a star on one phone can come back after syncing." → "Việc gộp chỉ giữ dấu đã đọc và đã gắn sao; đánh dấu chưa đọc hoặc bỏ sao trên một máy có thể quay lại sau khi đồng bộ."

- [ ] **Step 1: Viết test đỏ (Compose)**

`LocalSyncPageReplyTest.kt` (cùng runner/config như `LocalSyncPagePairingTest`: `RobolectricTestRunner`, `@Config(sdk=[34], application=Application::class)`, `GraphicsMode.NATIVE`, `createAndroidComposeRule<ComponentActivity>()`), helper `show(state: LocalSyncUiState, replyPeerLabel: String? = null, peers: List<PairedPeer> = emptyList(), isLocal: Boolean = true)` truyền đủ callback (`onWaitForReply = { calls += "wait" }`, `onReplyToPeer = { calls += "reply" }`) và ghi vào `calls`. Khai báo `servedUrl`, `peerId = "a".repeat(32)`, `result = LocalSyncApplyResult(0, 1, 1, 0, 1)`:

```kotlin
@Test fun aPairedSendOffersStopAndWaitAndSwitchesToReceive() {
    show(LocalSyncUiState.Sending(servedUrl, null, 1_000L, peerId))
    compose.onNodeWithTag(SYNC_ROLE_HINT_TAG).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(SYNC_WAIT_REPLY_TAG).performScrollTo().performClick()
    compose.runOnIdle { assertEquals(listOf("wait"), calls) }
}
@Test fun aManualSendHasNoWaitForReplyButton() {
    show(LocalSyncUiState.Sending(servedUrl, "0123456789abcdef".repeat(2), 1_000L, null))
    compose.onAllNodesWithTag(SYNC_WAIT_REPLY_TAG).assertCountEquals(0)
    compose.onAllNodesWithTag(SYNC_ROLE_HINT_TAG).assertCountEquals(0)
}
@Test fun afterApplyTheReplyBlockShowsTheLabelAndTheLocalOnlyNote() {
    show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
    compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsDisplayed().assertTextContains("Tablet", substring = true)
    compose.onNodeWithTag(SYNC_REPLY_NOTE_TAG).assertTextContains(appliedNote)
}
@Test fun afterCancelTheNoteSaysTheChangesWereNotApplied() {
    show(LocalSyncUiState.Idle, replyPeerLabel = "Tablet")
    compose.onNodeWithTag(SYNC_REPLY_NOTE_TAG).assertTextContains(cancelledNote)
}
@Test fun replyingCallsBackExactlyOnce() {
    show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
    compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().performClick()
    compose.runOnIdle { assertEquals(listOf("reply"), calls) }
}
@Test fun noReplyBlockWithoutAReplyPeerLabel() {
    show(LocalSyncUiState.Done(result), replyPeerLabel = null)
    compose.onAllNodesWithTag(SYNC_REPLY_TAG).assertCountEquals(0)
}
@Test fun replyIsDisabledWhileBusy() {
    show(LocalSyncUiState.Receiving, replyPeerLabel = "Tablet")
    compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsNotEnabled()
}

@Test fun replyIsDisabledWhenTheAccountIsNotLocal() {
    show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet", isLocal = false)
    compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsNotEnabled()
}
@Test fun theReplyBlockShowsTheInstructionAndTheMergeLimit() {
    show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
    compose.onNodeWithTag(SYNC_REPLY_INSTRUCTION_TAG).performScrollTo().assertIsDisplayed()
        .assertTextContains(compose.activity.getString(R.string.local_sync_reply_instruction))
    compose.onNodeWithTag(SYNC_MERGE_LIMIT_TAG).performScrollTo().assertIsDisplayed()
        .assertTextContains(compose.activity.getString(R.string.local_sync_merge_limit))
}

@Test fun theSendTabShowsTheMergeLimit() {
    show(LocalSyncUiState.Idle)
    compose.onNodeWithTag(SYNC_MERGE_LIMIT_TAG).performScrollTo().assertIsDisplayed()
}
@Test fun noReplyStringClaimsBothPhonesAreDone() {
    val ids = listOf(
        R.string.local_sync_reply_note_applied, R.string.local_sync_reply_note_cancelled,
        R.string.local_sync_reply_instruction, R.string.local_sync_role_hint, R.string.local_sync_wait_reply,
    )
    val forbidden = listOf("both phones", "both devices", "fully synced", "hai máy đã", "đồng bộ hoàn tất")
    ids.map { compose.activity.getString(it).lowercase() }.forEach { text ->
        forbidden.forEach { assertFalse("'$text' contains '$it'", text.contains(it)) }
    }
}
```
(`servedUrl`, `result = LocalSyncApplyResult(0,1,1,0,1)`, lấy chuỗi bằng `compose.activity.getString(R.string…)`.)

- [ ] **Step 2: Chạy, đỏ đúng lý do** (`Unresolved reference` cho tag/tham số) rồi `StringResourceParityTest` đỏ sau khi thêm key ở Step 3 nhưng chưa đủ locale.

- [ ] **Step 3: Cài đặt tối thiểu** theo mô tả hành vi trên. Thêm khoá vào `values/strings.xml` và `values-vi/strings.xml` bằng nội dung đã liệt kê, còn **36 locale còn lại** chèn bản English bằng script (cùng cách các task trước: chèn sau khoá `local_sync_serve_peer`, chỉ khi chưa có). Giữ thứ tự và escape XML đúng (`%1$s` giữ nguyên). Dùng `StringResource` hiện có cho nút/nhãn khác.

- [ ] **Step 4: Chạy xanh**

```bash
./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.p2psync.*' --tests '*StringResourceParityTest' > /tmp/exc05c-t3-green.log 2>&1; echo exit=$?
```
Đọc XML từng class; 0 failure; kết quả mới (kiểm `mtime`).

- [ ] **Step 5: Mutation**
1. Hiện nút chờ cho cả manual send → `aManualSendHasNoWaitForReplyButton` đỏ.
2. Dùng cùng ghi chú cho Done và Idle → hai test note đỏ.
3. Bỏ `isLocal && !busy` khỏi nút Gửi lại → test disabled đỏ.

- [ ] **Step 6: Compile app + commit**

```bash
./gradlew assembleDevDebug > /tmp/exc05c-t3-build.log 2>&1; echo build=$?
```
Chỉ commit khi cả test và build exit 0: `feat(exc-05c): add role-switch controls to Local Sync`.

---

### Task 4: Integration hai DB và thật socket

**Files:**
- Test (tạo mới): `app/src/test/java/com/mckimquyen/reader/infrastructure/p2psync/LocalSyncRoundTripIntegrationTest.kt`

**Interfaces:**
- Consumes: public `LocalSyncRepository(db)` / `internal (db, newId)`, `MirrorServer()` (hai thể hiện riêng A và B), `SyncSessionTransport(5_000, 5_000) { it == "127.0.0.1" }`, `SyncEnvelopeCrypto`, `SyncPackageCodec`, `PairingSecret`.
- Produces: không có code production; chứng minh hai chiều trên DB thật.

Hằng và helper dùng chung trong file test:

```kotlin
private const val SHARED = "https://rt.example.invalid/shared.xml"
private const val A_ONLY = "https://rt.example.invalid/a-only.xml"
private const val B_ONLY = "https://rt.example.invalid/b-only.xml"
private const val A_ONLY_LINK = "https://rt.example.invalid/a-only-story"
private const val B_ONLY_LINK = "https://rt.example.invalid/b-only-story"

private suspend fun feedIdOn(db: AndroidDatabase, url: String): String =
    db.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == url }.id

private suspend fun state(db: AndroidDatabase, suffix: String): Triple<Boolean, Boolean, Long?> {
    val link = "https://rt.example.invalid/$suffix"
    val article = db.articleDao().queryArticleByLink(link, feedIdOn(db, SHARED), 1)!!
    return Triple(!article.isUnread, article.isStarred, article.readAt)
}

private suspend fun feverRows(db: AndroidDatabase): List<List<Any?>> =
    db.articleDao().querySyncSnapshots(2).map { listOf(it.id, it.isUnread, it.isStarred, it.readAt) }

private suspend fun dump(db: AndroidDatabase): List<List<Any?>> =
    db.feedDao().queryAll(1).sortedBy { it.url }.map { listOf("feed", it.url, it.name, it.groupId) } +
        db.articleDao().querySyncSnapshots(1).sortedBy { it.link }
            .map { listOf("article", it.link, it.isUnread, it.isStarred, it.readAt) } +
        feverRows(db)

private suspend fun ciphertextOf(db: AndroidDatabase): ByteArray =
    SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(LocalSyncRepository(db).snapshot(1)), secret)
```

`beforeB` = `dump(dbB)` chụp ngay sau `setUp`. Feed chung đặt tên khác nhau mỗi bên (`"Shared on A"` / `"Shared on B"`) để chứng minh metadata của máy nhận được giữ. `queryArticleByLink(link, feedId, accountId)` và `querySyncSnapshots(accountId)` là DAO đã có (dùng ở `LocalSyncRoomIntegrationTest`/`LocalSyncRepository`).

Seed cho **mỗi** DB (Robolectric, `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`): account Local `id=1`, account Fever `id=2` (kiểm không bị đổi), group default `1.getDefaultGroupId()` (**bắt buộc**, nếu không `requireLocal` ném). Cùng feed chung `https://rt.example.invalid/shared.xml`, thêm feed riêng mỗi bên (`a-only.xml`, `b-only.xml`). Bài chung: `https://rt.example.invalid/s1` (A: read+star `readAt=100`; B: unread, `readAt=null`), `…/s2` (A: unread, `readAt=null`; B: starred `readAt=300`), `…/s3` (A: starred, `readAt=200`; B: read `readAt=150`), `…/a-only` chỉ ở A. Dùng URL `.invalid`, không request mạng.

- [ ] **Step 1: Viết test đỏ**

```kotlin
private val loopback = InetAddress.getByName("127.0.0.1")
private val secret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
private val serverA = MirrorServer(); private val serverB = MirrorServer()
private val transport = SyncSessionTransport(5_000, 5_000) { it == "127.0.0.1" }

private suspend fun leg(from: AndroidDatabase, server: MirrorServer, to: AndroidDatabase, apply: Boolean): LocalSyncApplyResult? {
    val snapshot = LocalSyncRepository(from).snapshot(1)               // taken now
    val started = server.startSync(loopback, SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(snapshot), secret)) as MirrorStartResult.Started
    try {
        val bytes = transport.fetch("http://127.0.0.1:${started.port}/s/${started.token.value}")
        val incoming = (SyncPackageCodec.decodeAndValidate(SyncEnvelopeCrypto.decrypt(bytes, secret)) as SyncPackageResult.Valid).data
        val repo = LocalSyncRepository(to)
        repo.preview(1, incoming)                                       // preview must not write
        return if (apply) repo.apply(1, incoming) else null
    } finally { server.stop() }
}

@Test fun theRoundTripConvergesSharedArticlesAndKeepsTheEarliestReadAt() = runBlocking {
    leg(dbA, serverA, dbB, apply = true)
    leg(dbB, serverB, dbA, apply = true)
    listOf("s1", "s2", "s3").forEach { id ->
        val a = state(dbA, id); val b = state(dbB, id)
        assertEquals("$id converge", a, b)
    }
    assertEquals(Triple(false, true, 100L), state(dbA, "s1"))   // read AND, star OR, earliest readAt
    assertEquals(Triple(false, true, 300L), state(dbA, "s2"))
    assertEquals(Triple(false, true, 150L), state(dbA, "s3"))
}
@Test fun feedsAreAddedOnceAndExistingMetadataIsKept() = runBlocking {
    leg(dbA, serverA, dbB, apply = true)
    leg(dbB, serverB, dbA, apply = true)
    listOf(dbA, dbB).forEach { db ->
        val urls = db.feedDao().queryAll(1).map { FeedUrlNormalizer.normalize(it.url) }
        assertEquals("no duplicate feed urls", urls.size, urls.toSet().size)
        assertTrue(urls.containsAll(listOf(SHARED, A_ONLY, B_ONLY)))
    }
    val sharedA = dbA.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == SHARED }
    assertEquals("Shared on A", sharedA.name)                         // receiver metadata kept
    val sharedB = dbB.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == SHARED }
    assertEquals("Shared on B", sharedB.name)
    val addedToB = dbB.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == A_ONLY }
    assertEquals(1.getDefaultGroupId(), addedToB.groupId)             // new feeds land in the default group
    val addedToA = dbA.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == B_ONLY }
    assertEquals(1.getDefaultGroupId(), addedToA.groupId)
}
@Test fun anArticleMissingOnTheReceiverIsSkippedInBothDirections() = runBlocking {
    leg(dbA, serverA, dbB, apply = true)
    assertNull(dbB.articleDao().queryArticleByLink(A_ONLY_LINK, feedIdOn(dbB, A_ONLY), 1))
    leg(dbB, serverB, dbA, apply = true)
    assertNull(dbA.articleDao().queryArticleByLink(B_ONLY_LINK, feedIdOn(dbA, B_ONLY), 1))
}
@Test fun otherAccountsAreNeverTouched() = runBlocking {
    val before = feverRows(dbA) to feverRows(dbB)
    leg(dbA, serverA, dbB, apply = true)
    leg(dbB, serverB, dbA, apply = true)
    assertEquals(before, feverRows(dbA) to feverRows(dbB))
}
@Test fun cancelOnTheFirstLegWritesNothingAndTheReturnLegSendsTheUnchangedDatabase() = runBlocking {
    leg(dbA, serverA, dbB, apply = false)
    assertEquals(beforeB, dump(dbB))                                  // preview wrote nothing
    leg(dbB, serverB, dbA, apply = true)
    /* A now has B's states merged; B unchanged from before the cancelled leg */
}
@Test fun aFailedFirstApplyDoesNotRollBackTheOtherPhoneOrBlockARetry() = runBlocking {
    leg(dbA, serverA, dbB, apply = true)
    leg(dbB, serverB, dbA, apply = true)
    val afterBoth = dump(dbA) to dump(dbB)
    leg(dbA, serverA, dbB, apply = true)                              // run again = idempotent
    assertEquals(afterBoth, dump(dbA) to dump(dbB))
}
@Test fun theReturnLegSnapshotReflectsTheApplyThatJustHappened() = runBlocking {
    leg(dbA, serverA, dbB, apply = true)
    val snapshotB = LocalSyncRepository(dbB).snapshot(1)
    // s1 in B is now read+star+100 because of leg 1: it must appear in B's outgoing snapshot
    assertTrue(snapshotB.articles.any { it.link.endsWith("/s1") && !it.isUnread && it.isStarred && it.readAt == 100L })
}
@Test fun aWrongTokenIsRejectedByTheRealServerAndNothingIsWritten() = runBlocking {
    val started = serverA.startSync(loopback, ciphertextOf(dbA)) as MirrorStartResult.Started
    try {
        val bad = "http://127.0.0.1:${started.port}/s/${"0".repeat(32)}"
        assertTrue(runCatching { transport.fetch(bad) }.isFailure)
        assertEquals(beforeB, dump(dbB))
    } finally { serverA.stop() }
}
@Test fun afterStopTheRealServerRefusesConnections() = runBlocking {
    val started = serverA.startSync(loopback, ciphertextOf(dbA)) as MirrorStartResult.Started
    val url = "http://127.0.0.1:${started.port}/s/${started.token.value}"
    serverA.stop()
    assertTrue(runCatching { transport.fetch(url) }.isFailure)
    assertFalse(serverA.isRunning)
}
```
`state(db,id)` trả `Triple(isUnread==false? ...)` thống nhất một định nghĩa: `Triple(isRead=!isUnread, isStarred, readAt)`. `dump(db)` = danh sách `(feedUrl, link, isUnread, isStarred, readAt)` đã sort + danh sách feed `(url,name,groupId)` + dòng account 2. `@After`: `serverA.stop(); serverB.stop(); dbA.close(); dbB.close()`.

- [ ] **Step 2: Chạy.** Đây là test về hành vi **đã có**; khả năng xanh ngay. Theo skill TDD: không được coi xanh-ngay là bằng chứng. Thực hiện ngay **mutation tạm** (Step 5) để chứng minh test bắt lỗi; không sửa production để "ép đỏ".

```bash
./gradlew testDevDebugUnitTest --tests '*LocalSyncRoundTripIntegrationTest' > /tmp/exc05c-t4.log 2>&1; echo exit=$?
```
Nếu **đỏ**: đọc XML, xác định thật sự là lỗi production (báo owner, KHÔNG sửa vội) hay seed/test sai.

- [ ] **Step 3: Không có code production.** Nếu thất bại do production thiếu hành vi mà spec đã duyệt, dừng và hỏi owner (spec: không đổi domain/transport nếu không chứng minh cần).

- [ ] **Step 4: Xanh** — XML 0 failure, mtime mới.

- [ ] **Step 5: Mutation (tạm, khôi phục + `cmp`)**
1. `LocalSyncPlanner`: đổi `current.isUnread && state.isUnread` thành `||` → test converge đỏ.
2. `readAt` dùng `maxOrNull()` → test earliest đỏ.
3. Trong `leg`, snapshot trước Apply (dùng snapshot cũ) → `theReturnLegSnapshotReflects…` đỏ (mô phỏng bằng cách đổi thứ tự trong test helper, không đổi production).
4. `MirrorServer` bỏ kiểm token trong `SyncSessionRouter` → `aWrongTokenIsRejected…` đỏ.
Sau mỗi mutation `git checkout --` file production và `git diff --stat` phải rỗng.

- [ ] **Step 6: Commit** `test(exc-05c): prove the two-way round trip over a real socket and two databases`.

---

### Task 5: Chuỗi, toàn bộ kiểm thử, lint và build

**Files:** không đổi code, trừ sửa lỗi phát hiện.

- [ ] **Step 1: Full suite từ đầu**

```bash
./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/exc05c-full.log 2>&1; echo suite=$?
python3 - <<'PY'
import glob,re,os,time
t=f=e=0;bad=[];stale=0
fs=glob.glob('app/build/test-results/testDevDebugUnitTest/TEST-*.xml')
for x in fs:
    m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',open(x).read(700)).groups()
    t+=int(m[0]);f+=int(m[2]);e+=int(m[3])
    if int(m[2]) or int(m[3]): bad.append(x.split('TEST-')[1])
    if time.time()-os.path.getmtime(x)>2400: stale+=1
print('files',len(fs),'tests',t,'failures',f,'errors',e,bad,'stale',stale)
PY
```
Expected: 0 failure/error, `stale=0` (baseline trước lát này: 2,618 test).

- [ ] **Step 2: Lint + build APK**

```bash
./gradlew lintDevDebug --rerun-tasks > /tmp/exc05c-lint.log 2>&1; echo lint=$?
./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/exc05c-apk.log 2>&1; echo apk=$?
```
Đọc `app/build/reports/lint-results-devDebug.xml`: 0 lỗi, 2,463 cảnh báo (baseline), 0 hit file p2psync. Kiểm age của báo cáo.

- [ ] **Step 3: Chạy lại test 2 lần liên tiếp** các lớp `LocalSync*` để chắc không flake: lặp `--tests 'com.mckimquyen.reader.ui.page.p2psync.*' --tests '*LocalSyncRoundTripIntegrationTest'` hai lần, 0 failure.

- [ ] **Step 4: Commit** chỉ khi có sửa lỗi: message mô tả đúng sửa gì.

---

### Task 6: Smoke hai máy thật (BG6 + Pixel)

**Files:** script tạm trong `/tmp` (không commit): `/tmp/exc05c-fixture.py`, `/tmp/exc05c-restore.py` (copy mô hình từ `/tmp/exc05b-apply-fixture.py` và `/tmp/exc05b-apply-restore.py` đã chạy được).

- [ ] **Step 1: Gate chỉ đọc** cả hai máy

```bash
adb devices -l
for S in 118743744X002560 2B051FDH3006MU; do
  echo "== $S instr=$(adb -s $S shell dumpsys activity 2>/dev/null | grep -ciE 'Active instrumentation')"
  adb -s $S shell dumpsys activity 2>/dev/null | grep -E 'topResumedActivity' | head -1 | cut -c1-170
  adb -s $S shell ps -A | grep -iE 'lenslauncher|instrument|bemytester|loyalty.skyjoy|mckimquyen' | head
done
```
Dừng và hỏi owner nếu có instrumentation lạ hoặc app dự án khác đang ở foreground. Tiến trình nền không ở foreground thì chỉ ghi nhận, không động tới.

- [ ] **Step 2: Backup byte-exact** (force-stop **chỉ** `com.mckimquyen.reader`; `run-as com.mckimquyen.reader cat …`; kiểm size local == size trên máy; nếu `Reader-shm` đổi trong lúc app mở thì backup lại sau khi dừng app): `databases/Reader`, `-shm`, `-wal`, `-journal`, `files/datastore/settings.preferences_pb`, `shared_prefs/pairing_store.xml`. Ghi thư mục backup vào `/tmp/exc05c-root`.

- [ ] **Step 3: Seed fixture không rỗng** (có assert, trên **bản sao** rồi nạp bằng kênh nhị phân `adb shell -T run-as … sh -c "cat > …"` và đọc lại để so byte): mỗi máy có feed chung `https://exc05c.invalid/shared.xml`, bài chung `s1/s2/s3` với trạng thái khác nhau như Task 4, một bài chỉ có ở máy gửi. Prefix id `exc05c-`. Dùng `pragma wal_checkpoint(truncate)` + `journal_mode=delete` trên bản sao trước khi nạp; xoá sidecar `Reader-wal/-shm/-journal` trên máy trước khi ghi DB mới (không xoá DataStore).

- [ ] **Step 4: Chạy luồng thật qua UI**, mỗi bước chụp UI bằng `uiautomator` (lấy tọa độ từ dump, không đoán). Dùng keyboard English (Pixel đang English) hoặc nhập bằng `input text` đã kiểm đếm ký tự.
  1. Cài APK mới lên hai máy (`adb -s <serial> install -r`), kiểm MD5 base APK khớp bản build.
  2. Pixel: cấp `ACCESS_LOCAL_NETWORK` qua hộp thoại thật hoặc `pm grant`; ghi nhận đã cấp.
  3. Ghép đôi: BG6 **Pair a new device** (nhãn `RoundTrip`), Pixel nhận thủ công lần đầu với nhãn để lưu CONFIRMED (đường EXC-05b, không phải đối tượng kiểm của lát này).
  4. **Lượt 1:** BG6 `Send to RoundTrip` → Pixel chọn máy ở gần → preview (số feed/bài đúng fixture) → **Apply trên Pixel**. Đọc DB Pixel (bản sao read-only): trạng thái hợp nhất đúng, feed thêm đúng, bài thiếu bị bỏ qua, bài/feed gốc không đổi.
  5. Trên BG6 (đang `Sending`): bấm **Dừng và chờ nhận lại**; kiểm `curl` tới URL cũ thất bại, `dns-sd` không còn dịch vụ, tab chuyển sang Nhận, BG6 đang tìm peer.
  6. **Lượt 2:** Pixel bấm **Gửi lại cho …** (sau Apply). Kiểm: Pixel chuyển sang Sending, không hiện secret, snapshot **sau Apply** (kiểm bằng DB/đọc preview ở BG6: bài trước đây chỉ có ở Pixel nay đã thấy trong preview). BG6 chọn máy ở gần → preview → **Apply trên BG6**. Đọc DB BG6: đã hợp nhất, không nhân bản feed.
  7. **Nhánh huỷ:** lặp lượt 1 với một cặp bài khác nhưng bấm **Huỷ** ở Pixel: DB Pixel không đổi (so byte với trước lượt); nút **Gửi lại** vẫn hiện với ghi chú "chưa được áp dụng"; gửi lại và BG6 Apply: snapshot của Pixel là DB chưa bị lượt 1 sửa.
  8. Rời màn Local Sync / đưa app xuống nền khi đang ở Preview: nút **Gửi lại** biến mất (cleanup xoá ngữ cảnh).
  9. Forget peer ở Pixel: nút biến mất; server cũ đóng.
- Nếu quảng cáo toàn màn hình che UI: dùng Back, kiểm foreground sau mỗi lần; **không** bấm "Tiếp tục đến ứng dụng".

- [ ] **Step 5: Khôi phục** (`/tmp/exc05c-restore.py`): force-stop app, nạp lại **tất cả** file backup bằng kênh nhị phân, so byte từng file (Room, sidecar, DataStore, pairing_store), `integrity_check`/`foreign_key_check` trên bản sao, xác nhận không còn fixture (`exc05c-`) và `grep` 0; `pm revoke` quyền LAN tạm; app dừng; không NSD còn lại (`dns-sd -B`). Ghi trung thực mọi file không khớp.

- [ ] **Step 6: Nếu thiếu thiết bị hoặc bị chặn:** ghi "chưa kiểm chứng" thay vì giả pass; không chuyển sang máy khác không được phép.

---

### Task 7: Báo cáo, trạng thái và đóng lát

**Files:**
- Create: `doc/task/done/EXC-05c_manual-round-trip_DONE.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Báo cáo DONE** theo mẫu EXC-05b: phạm vi, thay đổi, số test (unit/Compose/integration), mutation, full suite, lint, smoke (từng bước đã chạy và kết quả thật), restore (so byte), **giới hạn trung thực**:
  - chưa tự sync khi mở app, chưa chạy nền; EXC-05 gốc vẫn mở;
  - không ACK/transaction hai máy; một phía Apply còn phía kia huỷ thì lệch cho đến lần sau;
  - merge đơn điệu: chưa đọc/bỏ sao cục bộ có thể quay lại;
  - bài chưa có trên máy nhận bị bỏ qua; giới hạn kích thước/số lượng snapshot;
  - ngữ cảnh gửi lại chỉ trong bộ nhớ ViewModel, mất sau restart;
  - API NSD 31–32 và 34–36 chưa kiểm trên thiết bị (nợ EXC-05b còn nguyên).
- [ ] **Step 2: `doc/feature.md`**: chuyển EXC-05c khỏi "Picked" sang "Recently implemented" **chỉ khi** Task 1–6 đạt (nếu smoke chưa chạy thì ghi "JVM đạt, thiết bị chưa kiểm"). EXC-05 gốc (`doc/task/todo/EXC-05_local-p2p-sync.md`) **giữ ở todo**.
- [ ] **Step 3: Commit** `docs(exc-05c): record the manual round trip result`.
- [ ] **Step 4:** Hỏi owner qua AskUserQuestion bước tiếp theo theo thứ tự đã chọn (FRONT-04 Android Auto), không tự bắt đầu.

---

## Spec coverage (tự soát)

| Yêu cầu spec | Task |
|---|---|
| ReplyPeer gắn account+peer, chỉ sau xác thực (4.3) | 1 |
| Huỷ chủ động giữ, cleanup xoá, account đổi, forget, ViewModel clear (4.3) | 1 |
| Dừng và chờ nhận lại: đóng server/NSD/job, Idle, discovery, không mở server (4.1) | 2 |
| Gửi lại = `servePaired`, snapshot mới, không secret, không apply (4.2) | 2, 4 |
| Chặn peer quên/needsRepair/khoá lỗi, guard busy, LAN (5) | 2 |
| UI nút/ghi chú/hướng dẫn, không tuyên bố hoàn tất chung (4.1/4.2) | 3 |
| 38 locale + parity (6) | 3, 5 |
| Hai DB + thật socket + token sai + stop (7) | 4 |
| Huỷ/lỗi một phía không rollback, chạy lại idempotent (5, 7) | 4 |
| Smoke BG6+Pixel, backup/restore byte-exact, không connected* (7) | 6 |
| Giới hạn trung thực, EXC-05 gốc mở (1, 7) | 7 |

## Placeholder scan

Đã quét: không còn "TBD/TODO/xử lý sau" và không còn thân test chỉ là chú thích (bảy test ban đầu đã thay bằng mã). Còn hai chỗ **quyết định có điều kiện**, không phải bỏ trống: Task 4 Step 2 (integration test mô tả hành vi đã có nên có thể xanh ngay; khi đó chứng minh bằng mutation, khi đỏ thì dừng và báo owner chứ không sửa production) và Task 6 Step 6 (thiếu thiết bị thì ghi "chưa kiểm chứng"). Phần thân `replyToPeer` ghi rõ thứ tự guard trước khi ghi Error.

Giới hạn đã biết của plan này: các thân test Compose ở Task 3 và hai test cuối ở Task 4 dựa trên helper `show(...)`/`leg(...)` đã mô tả bằng chữ và chữ ký; người thực thi viết thân helper theo đúng mẫu `LocalSyncPagePairingTest.show` và hàm `leg` đã có mã đầy đủ ở Task 4.

## Nhất quán kiểu

`ReplyPeer(accountId: Int, peerId: String)`, `replyPeer: StateFlow<ReplyPeer?>`, `waitForReply()`, `replyToPeer(localAccountId: Int)` dùng đồng nhất ở Task 1, 2, 3. Tag `SYNC_WAIT_REPLY_TAG`, `SYNC_REPLY_TAG`, `SYNC_REPLY_NOTE_TAG`, `SYNC_ROLE_HINT_TAG` định nghĩa ở Task 3 và chỉ dùng ở Task 3.
