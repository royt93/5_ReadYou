# FRONT-02w — Nhận `nprofile` khi thêm nguồn Nostr (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Dán `nprofile1…` (hoặc `nostr:nprofile1…`) vào ô Thêm nguồn thì app hiểu là cùng một người như `npub` và chạy đúng luồng `npub` đang chạy (xem trước, "Đã đăng ký" khi trùng, lưu `nostr:<npub>`); gợi ý relay trong `nprofile` bị bỏ qua hoàn toàn.

**Architecture:** `Bech32.decode` nhận thêm độ dài tối đa (mặc định 90, `npub`/`note1` không đổi). `NostrKeys` thêm `nprofileToHex` (đọc danh sách TLV của NIP-19, lấy đúng một khóa 32 byte, đọc rồi bỏ qua mọi loại khác) và `authorHexFrom` (nhận `npub` hoặc `nprofile`). `SubscribeViewModel.search()` gọi `authorHexFrom` thay `npubToHex`; mọi thứ sau đó không đổi. **Chỉ JVM, không chạm thiết bị nào.**

**Tech Stack:** Kotlin, JUnit4 + Robolectric (test ViewModel có sẵn), MockK (JVM được phép), Gradle `testDevDebugUnitTest --tests`, Python 3.9 (kiểm chéo độc lập và đột biến).

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không chạm thiết bị nào** (không Tecno, không Pixel, không `adb`).
- Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không thêm lệnh gọi mạng. Mã production đổi **ba tệp**: `Bech32.kt`, `NostrKeys.kt`, `SubscribeViewModel.kt` (một dòng gọi hàm). `LocalRssSv.queryNostrArticles` và `NostrRelayClient` **không đổi** (nguồn lưu luôn là `nostr:<npub>`).
- `Bech32.decode(text, maxLength = DEFAULT_MAX_LENGTH)`; `DEFAULT_MAX_LENGTH = 90`: `npub` và `note1` không đổi hành vi; `nprofile` dùng `NPROFILE_MAX_LENGTH = 1000`.
- Gợi ý relay (TLV loại 1) và mọi loại lạ **chỉ được đọc để bỏ qua**: giá trị không bao giờ dùng, lưu hay ghi log. `NostrRelayClient.fetch` vẫn chỉ nhận `authorHex: String`.
- Chỉ `nprofile` (có thể có tiền tố `nostr:`, mọi cỡ chữ cho phép của Bech32). **Không** link `njump.me/...`, `nevent`, `naddr`, `note1` làm nguồn, ảnh, trả lời, đăng nhập, đăng bài.
- `isSecretKey` vẫn chạy **trước** mọi bộ phân giải (kể cả `nostr:nsec…`). Không log khóa hay relay.
- R5: không magic number (hằng số có tên), test kèm mọi thay đổi, không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Gradle chạy NỀN, output ra file, chờ `.exit`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Chạy lớp test riêng (`--tests`) cho từng bước; full suite `--rerun-tasks` ở cuối, **đo lại số test**, không chép số cũ. Đỏ chập chờn: chạy riêng lớp đó 3 lần + chạy lại cả bộ, ghi thẳng cả hai kết quả.
- Mutation: một script nền duy nhất, không sửa tệp đột biến cho tới `ALLDONE`; khôi phục + `cmp` sau mỗi đột biến; mỗi đột biến phải làm đúng tập test dự định đỏ (so sánh tự động).
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- Nói thẳng phần không kiểm: không máy thật, không mạng thật, không dán thật vào ô trên màn hình, không link `njump.me`, lint không đổi.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **`Bech32.MAX_LENGTH` (private) đổi tên thành `DEFAULT_MAX_LENGTH`** (`const val`, công khai trong `internal object`) để test và đột biến `n1` trỏ vào một chỗ duy nhất.
2. **Test đọc mã nguồn `SubscribeViewModelNostrSourceTest` sẽ "xanh giả" nếu chỉ đổi hàm hoặc mất neo:** nó dùng `search.substringAfter("npubToHex(")`; khi chuỗi không còn trong mã, `substringAfter` **trả nguyên chuỗi** và `indexOf(...) = -1 < indexOf("formatUrl()")` đúng một cách vô nghĩa. Plan đổi neo sang `authorHexFrom(` **và thêm test chắc chắn neo tồn tại** (`theNostrAnchorExists`).
3. **Các chuỗi `nprofile` mẫu do bộ mã hóa Python độc lập tính** (checksum không do app tạo), khóa trùng với `npub` của `NostrKeysTest` (`7e7e9c42…`); test còn tự dựng thêm bằng `Bech32.encode` + TLV nhỏ và có một test khẳng định bộ dựng trong test **cho đúng chuỗi của Python**.
4. **Độ dài thật:** `nprofile` một relay = 110 ký tự, vector chính thức NIP-19 = 131, 20 relay = 934; tất cả vượt 90 nên chứng minh việc nới giới hạn là cần thiết.
5. **Bước "đỏ" của TDD ở Task 1 và 2 là lỗi biên dịch** (tham số `maxLength` và hàm `nprofileToHex`/`authorHexFrom` chưa tồn tại). Task 3 (ViewModel) đỏ bằng khẳng định thật.
6. **Đột biến `n8` của spec** (nhận relay) đổi thành kiểm cấu trúc: test phản chiếu khẳng định `NostrRelayClient.fetch` chỉ có một tham số `String` (cộng `Continuation` của `suspend`); đột biến `n10` thêm một tham số để chứng minh test đó không xanh giả.
7. **Đột biến thêm:** `n13` (nới 1000 thành 2000), `n16` (bỏ kiểm HRP `nprofile`; test dùng HRP `nevent` mang TLV loại 0 hợp lệ vì payload `npub` thô tình cờ không phân tích được thành TLV).

## Tập test đỏ mong đợi của đột biến (dự đoán; kiểm tự động ở Task 4)

Tệp mới `NostrNprofileTest` (lớp **N**): **P0** `helperAgreesWithTheIndependentEncoder`, **P1** `officialVector_decodesToTheKnownKey`, **P2** `anNpubAndAnNprofileOfOnePerson_giveOneHex`, **P3** `aProfileWithoutRelays_isAccepted`, **P4** `aProfileWithRelayHints_isAccepted_andTheHintsAreNotUsed`, **P5** `unknownTypesAreSkipped`, **P6** `theNostrPrefixTheCaseAndTheSpaces_areAccepted`, **P7** `aKeyThatIsNot32Bytes_isRejected`, **P8** `twoKeys_areRejected`, **P9** `noKey_isRejected`, **P10** `aCutTlv_isRejected`, **P11** `aStringOverAThousand_isRejected`, **P12** `aLongProfileUnderTheLimit_isAccepted`, **P13** `otherKindsOfIdentifier_areRejected`, **P14** `authorHexFrom_takesBothAndRefusesJunk`, **P15** `anNpubOverNinety_isStillRefused`, **P16** `theRelayClientCannotBeGivenARelay`.
`NostrKeysTest` (lớp **K**) thêm **B1** `bech32_customLimit_acceptsALongerStringThatTheDefaultRefuses`, **B2** `bech32_defaultLimitIsStillNinety`; sẵn có **B0** `bech32_rejectsValidButOverlongStrings`.
`SubscribeViewModelNostrNprofileTest` (lớp **V**): **V1** `anNprofile_showsTheSamePreviewAsItsNpub`, **V2** `anNprofileWithRelayHints_asksTheRelayClientForTheKeyOnly`, **V3** `anNprofileOfASubscribedNpub_isRefusedWithoutAskingTheRelay`, **V4** `aNostrUriNprofile_isRecognised`.
`SubscribeViewModelNostrSourceTest` (lớp **S**): sẵn có `npubIsResolvedBeforeFormatUrl`, `nostrFailureDoesNotFallThroughToWeb`, `newNostrCodeDoesNotLogKeysOrNotes` (cả ba dùng neo) và mới **A** `theNostrAnchorExists`.

| Đột biến | Đổi gì (tệp) | Test đỏ mong đợi | Kiểu so |
|---|---|---|---|
| base | không | không | đúng |
| n1 | `DEFAULT_MAX_LENGTH` 90 thành 1000 (`Bech32.kt`) | B0, B2, P15 | đúng |
| n2 | bỏ kiểm "khóa đúng 32 byte" (`NostrKeys.kt`) | P7 | đúng |
| n3 | cho phép hai khóa | P8 | đúng |
| n4 | loại lạ (không phải 0 hoặc 1) thì trả `null` | P5 | đúng |
| n5 | bỏ kiểm độ dài TLV vượt phần còn lại | P10 | đúng |
| n6 | bỏ kiểm đủ 2 byte tiêu đề TLV | P10 | đúng |
| n7 | bỏ xử lý tiền tố `nostr:` của `nprofileToHex` | P6, P14, V4 | đúng |
| n8 | `authorHexFrom` chỉ gọi `npubToHex` | P14, V1, V2, V3, V4 | đúng |
| n9 | `nprofileToHex` quên truyền `NPROFILE_MAX_LENGTH` | P1, P4, P12 | ít nhất |
| n10 | `NostrRelayClient.fetch` thêm tham số thứ hai | P16 | đúng |
| n11 | `SubscribeViewModel` gọi lại `npubToHex(typed)` | V1, V2, V3, V4, A | ít nhất |
| n13 | `NPROFILE_MAX_LENGTH` 1000 thành 2000 | P11 | đúng |
| n16 | bỏ kiểm HRP `nprofile` | P13 | đúng |

"Ít nhất": tập đỏ thực tế phải **chứa** các test ghi, và mọi test đỏ phải nằm trong năm lớp N, K, V, S và `SubscribeViewModelNostrTest`. "Đúng": phải trùng chính xác. Dự đoán có thể sai; khi lệch phải **đọc lý do** và sửa bảng hoặc test (ghi trong báo cáo), không bỏ qua.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt` | tham số `maxLength` |
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt` | `nprofileToHex`, `authorHexFrom` |
| Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` | một dòng: `npubToHex(typed)` thành `authorHexFrom(typed)` |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt` | B1, B2 |
| Create `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrNprofileTest.kt` | P0 đến P16 |
| Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrNprofileTest.kt` | V1 đến V4 |
| Modify `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt` | neo mới + test A |
| Tạm, **không commit**: `/tmp/n_mut.sh`, `/tmp/n_batch.sh`, `/tmp/n_check.py` | đột biến JVM |
| Modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02w_nostr-nprofile_DONE.md` | báo cáo |

---

### Task 1: `Bech32.decode` nhận độ dài tối đa tùy chọn (TDD)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt` (test B1, B2)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt`

**Interfaces:**
- Consumes: `Bech32.decode(text: String): Decoded?`, `Bech32.encode(hrp: String, payload: ByteArray): String`.
- Produces: `Bech32.decode(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): Decoded?` và `const val Bech32.DEFAULT_MAX_LENGTH = 90`. Các lời gọi cũ `Bech32.decode(x)` giữ nguyên hành vi.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
B=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt
grep -n "MAX_LENGTH\|fun decode" $B
grep -rn "Bech32.decode" app/src/main | cut -c1-140
git status --short app
```
Expected: `MAX_LENGTH = 90` (khai báo) và dùng ở `if (text.length > MAX_LENGTH) return null`; `Bech32.decode` chỉ được gọi ở `NostrKeys.kt` (`npubToHex`); `git status` không có thay đổi trong `app`.

- [ ] **Step 2: Viết test đỏ (B1, B2) vào `NostrKeysTest.kt`, ngay sau test `bech32_rejectsValidButOverlongStrings`**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt"
s = open(T, encoding="utf-8").read()
anchor = '''    @Test fun bech32_encodeThenDecode_roundTrips() {'''
assert s.count(anchor) == 1
new = '''    // FRONT-02w: a longer string is only read when the caller asks for a longer limit (nprofile does, npub and note1 never).
    @Test fun bech32_customLimit_acceptsALongerStringThatTheDefaultRefuses() {
        val text = Bech32.encode("nprofile", ByteArray(64))
        assertTrue(text.length > 90)
        assertNull("the default limit still refuses it", Bech32.decode(text))
        assertEquals(ByteArray(64).toList(), Bech32.decode(text, maxLength = 1000)?.payload?.toList())
        assertNull("a custom limit is a limit too", Bech32.decode(text, maxLength = text.length - 1))
    }

    @Test fun bech32_defaultLimitIsStillNinety() {
        assertEquals(90, Bech32.DEFAULT_MAX_LENGTH)
        val atTheLimit = Bech32.encode("npub", ByteArray(49))
        val overTheLimit = Bech32.encode("npub", ByteArray(50))
        assertEquals(90, atTheLimit.length)
        assertEquals(91, overTheLimit.length)
        assertNotNull(Bech32.decode(atTheLimit))
        assertNull(Bech32.decode(overTheLimit))
    }

'''
open(T, "w", encoding="utf-8").write(s.replace(anchor, new + anchor, 1))
EOF
```
Hai độ dài 90 và 91 đã được bộ mã hóa Python độc lập xác nhận (49 byte cho `npub` là 90 ký tự, 50 byte là 91).

- [ ] **Step 3: Chạy test, phải ĐỎ (lỗi biên dịch: chưa có `maxLength` và `DEFAULT_MAX_LENGTH`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/n-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrKeysTest" > /tmp/n-t1.log 2>&1; echo $? > /tmp/n-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t1.exit && break; sleep 5; done; cat /tmp/n-t1.exit; grep -E "^e: |error:" /tmp/n-t1.log | head -5
```
Expected: `1` và lỗi `e: ... Unresolved reference: maxLength` hoặc `DEFAULT_MAX_LENGTH`.

- [ ] **Step 4: Sửa `Bech32.kt` (hai chỗ, mỗi mẫu đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
B = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt"
s = open(B, encoding="utf-8").read()
def sub(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)
sub("    private const val MAX_LENGTH = 90\n", "    /** BIP-173's limit, kept for npub and note1; a caller that reads a longer NIP-19 string (nprofile) says so. */\n    const val DEFAULT_MAX_LENGTH = 90\n")
sub("    fun decode(text: String): Decoded? {\n        if (text.length > MAX_LENGTH) return null\n",
    "    fun decode(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): Decoded? {\n        if (text.length > maxLength) return null\n")
open(B, "w", encoding="utf-8").write(s)
EOF
grep -n "MAX_LENGTH\|maxLength" app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt
```
Expected: `DEFAULT_MAX_LENGTH` (khai báo), `maxLength: Int = DEFAULT_MAX_LENGTH`, `text.length > maxLength`. Không còn `MAX_LENGTH` riêng lẻ.

- [ ] **Step 5: Chạy test, phải XANH (cả lớp `NostrKeysTest`, kể cả 20 test cũ)**

```bash
rm -f /tmp/n-t1.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrKeysTest" > /tmp/n-t1.log 2>&1; echo $? > /tmp/n-t1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t1.exit && break; sleep 5; done; cat /tmp/n-t1.exit; grep -E "FAILED|tests completed" /tmp/n-t1.log | head -5; tail -3 /tmp/n-t1.log
```
Expected: `0`, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/Bech32.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeysTest.kt
git commit -m "feat(front-02w): let Bech32.decode take a longer limit for nprofile, npub and note1 keep 90

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `NostrKeys.nprofileToHex` và `authorHexFrom` (TDD)

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrNprofileTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt`

**Interfaces:**
- Consumes: `Bech32.decode(text, maxLength)` và `Bech32.encode(hrp, payload)` (Task 1), `NostrKeys.npubToHex`, `NostrKeys.hexToNpub`, `NostrKeys.isSecretKey`, `NostrRelayClient.fetch(authorHex: String)`.
- Produces: `NostrKeys.nprofileToHex(input: String): String?` và `NostrKeys.authorHexFrom(input: String): String?` (= `npubToHex(input) ?: nprofileToHex(input)`).

> Các chuỗi mẫu trong test dưới đây do bộ mã hóa Python **độc lập** tính (checksum không do app tạo) và một mô hình tham chiếu Python của chính thuật toán đã được chạy trên cả 13 chuỗi, ra đúng kết quả. Khóa mẫu `7e7e9c42…` trùng với `npub` của `NostrKeysTest`.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
K=app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt
grep -n "const val\|fun \|private fun withoutUriPrefix" $K
git status --short app
```
Expected: có `NPUB_HRP`, `NOTE_HRP`, `SECRET_PREFIX`, `URI_PREFIX`, `KEY_BYTES`, `HEX_LENGTH`, `HEX_RADIX`, các hàm `npubToHex`, `hexToNpub`, `noteId`, `isSecretKey`, `withoutUriPrefix`, `fromHex`; `git status` sạch trong `app` (Task 1 đã commit).

- [ ] **Step 2: Viết test đỏ `NostrNprofileTest.kt`**

```bash
cat > app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrNprofileTest.kt <<'EOF'
package com.mckimquyen.reader.infrastructure.social.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An nprofile (NIP-19) names a person by public key, optionally with relay hints. The app reads ONLY the key: the hints are walked
 * over and never used, so whoever sent the text cannot choose which server the app talks to.
 * Sample strings below were computed by an independent encoder (checksums are not produced by the app).
 */
class NostrNprofileTest {
    private val hex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val noRelay = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsz6vqwn"
    private val oneRelay = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspzamhxue69uhhyetvv9ujuetcv9khqmr99e3k7mg3lnphh"
    private val relayFirst = "nprofile1qythwumn8ghj7un9d3shjtn90psk6urvv5hxxmmdqqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7ns6myyzp"
    private val unknownMiddle = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsfqfa85qg0waehxw309asjuetcv9khqmr9efyt4k"
    private val twoSameKey = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsqypl8a8zz4ydlauvl4y57tldpkuhqa0q6fsg5zee7y72zxnvx4h05us4erak"
    private val twoKeys = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsqyqalp33lewf5vdq847t6te0wvnags0gs0mu72kz8938tn24wlfze6420n09"
    private val onlyRelay = "nprofile1qythwumn8ghj7un9d3shjtn90psk6urvv5hxxmmdcxc3h6"
    private val key31 = "nprofile1qq0hul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7tflntw"
    private val key33 = "nprofile1qqshul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsq4am05q"
    private val cutValue = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspxfmhxue69uhhsvs4pjp"
    private val cutHeader = "nprofile1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsp5l4cu7"
    private val neventWithKey = "nevent1qqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nspqaj0w"
    private val official = "nprofile1qqsrhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8gpp4mhxue69uhhytnc9e3k7mgpz4mhxue69uhkg6nzv9ejuumpv34kytnrdaksjlyr9p"
    private val officialHex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val keyBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"

    /** Longest relay hint and type bytes the test encoder may use, so a TLV length always fits one byte. */
    private companion object {
        const val TLV_KEY = 0
        const val TLV_RELAY = 1
        const val MAX_TLV_VALUE = 255
        const val KEY_TLV_BYTES = 34
        const val PAYLOAD_FOR_999_CHARS = 615
        const val PAYLOAD_FOR_1001_CHARS = 616
    }

    private fun tlv(type: Int, value: ByteArray): ByteArray = byteArrayOf(type.toByte(), value.size.toByte()) + value

    private fun profile(vararg parts: ByteArray, hrp: String = "nprofile"): String = Bech32.encode(hrp, parts.fold(ByteArray(0)) { a, b -> a + b })

    /** A valid nprofile whose payload is exactly [payloadBytes] long: the key, then relay hints of "a" characters. */
    private fun profileOfPayload(payloadBytes: Int): String {
        val parts = mutableListOf(tlv(TLV_KEY, keyBytes))
        var rest = payloadBytes - KEY_TLV_BYTES
        while (rest > 0) {
            val size = minOf(rest - 2, MAX_TLV_VALUE)
            parts += tlv(TLV_RELAY, ByteArray(size) { 'a'.code.toByte() })
            rest -= size + 2
        }
        return profile(*parts.toTypedArray())
    }

    // ---- the helper used here must agree with the independent encoder, or no other expectation means anything
    @Test fun helperAgreesWithTheIndependentEncoder() {
        assertEquals(noRelay, profile(tlv(TLV_KEY, keyBytes)))
        assertEquals(oneRelay, profile(tlv(TLV_KEY, keyBytes), tlv(TLV_RELAY, "wss://relay.example.com".toByteArray())))
        assertEquals(npub, Bech32.encode("npub", keyBytes))
        assertEquals(110, oneRelay.length)
    }

    // ---- the official NIP-19 example
    @Test fun officialVector_decodesToTheKnownKey() {
        assertEquals(131, official.length)
        assertEquals(officialHex, NostrKeys.nprofileToHex(official))
    }

    @Test fun anNpubAndAnNprofileOfOnePerson_giveOneHex() {
        assertEquals(hex, NostrKeys.npubToHex(npub))
        assertEquals(hex, NostrKeys.nprofileToHex(noRelay))
        assertEquals(NostrKeys.npubToHex(npub), NostrKeys.nprofileToHex(oneRelay))
        assertEquals(npub, NostrKeys.hexToNpub(requireNotNull(NostrKeys.nprofileToHex(oneRelay))))
    }

    // ---- what is accepted
    @Test fun aProfileWithoutRelays_isAccepted() = assertEquals(hex, NostrKeys.nprofileToHex(noRelay))

    @Test fun aProfileWithRelayHints_isAccepted_andTheHintsAreNotUsed() {
        assertEquals(hex, NostrKeys.nprofileToHex(oneRelay))
        assertEquals("a hint before the key changes nothing", hex, NostrKeys.nprofileToHex(relayFirst))
        // The result is only the key: nothing of the hint can be in it.
        assertEquals(64, NostrKeys.nprofileToHex(oneRelay)?.length)
    }

    @Test fun unknownTypesAreSkipped() = assertEquals(hex, NostrKeys.nprofileToHex(unknownMiddle))

    @Test fun theNostrPrefixTheCaseAndTheSpaces_areAccepted() {
        assertEquals(hex, NostrKeys.nprofileToHex("nostr:$noRelay"))
        assertEquals(hex, NostrKeys.nprofileToHex("  NOSTR:${noRelay.uppercase()} \n"))
        assertEquals(hex, NostrKeys.nprofileToHex(noRelay.uppercase()))
        assertEquals(hex, NostrKeys.nprofileToHex("  $noRelay\t"))
    }

    // ---- what is refused
    @Test fun aKeyThatIsNot32Bytes_isRejected() {
        assertNull(NostrKeys.nprofileToHex(key31))
        assertNull(NostrKeys.nprofileToHex(key33))
    }

    @Test fun twoKeys_areRejected() {
        assertNull("two different keys", NostrKeys.nprofileToHex(twoKeys))
        assertNull("the same key twice", NostrKeys.nprofileToHex(twoSameKey))
    }

    @Test fun noKey_isRejected() {
        assertNull(NostrKeys.nprofileToHex(onlyRelay))
        assertNull(NostrKeys.nprofileToHex(Bech32.encode("nprofile", ByteArray(0))))
    }

    @Test fun aCutTlv_isRejected() {
        assertNull("a value longer than what is left", NostrKeys.nprofileToHex(cutValue))
        assertNull("a header with no length byte", NostrKeys.nprofileToHex(cutHeader))
    }

    @Test fun aStringOverAThousand_isRejected() {
        val text = profileOfPayload(PAYLOAD_FOR_1001_CHARS)
        assertEquals(1001, text.length)
        assertNull(NostrKeys.nprofileToHex(text))
    }

    @Test fun aLongProfileUnderTheLimit_isAccepted() {
        val text = profileOfPayload(PAYLOAD_FOR_999_CHARS)
        assertEquals(999, text.length)
        assertEquals(hex, NostrKeys.nprofileToHex(text))
        assertTrue("longer than an npub could ever be", text.length > Bech32.DEFAULT_MAX_LENGTH)
    }

    @Test fun otherKindsOfIdentifier_areRejected() {
        assertNull("a bare npub", NostrKeys.nprofileToHex(npub))
        assertNull("an nevent that carries a valid key entry", NostrKeys.nprofileToHex(neventWithKey))
        assertNull("a secret key", NostrKeys.nprofileToHex(nsec))
        assertNull(NostrKeys.nprofileToHex("nostr:$nsec"))
        listOf("", "   ", "nprofile1", "https://njump.me/$noRelay", "hello", "nostr:").forEach { assertNull(it, NostrKeys.nprofileToHex(it)) }
    }

    // ---- the one entry point the Add Source box uses
    @Test fun authorHexFrom_takesBothAndRefusesJunk() {
        assertEquals(hex, NostrKeys.authorHexFrom(npub))
        assertEquals(hex, NostrKeys.authorHexFrom("nostr:$npub"))
        assertEquals(hex, NostrKeys.authorHexFrom(noRelay))
        assertEquals(hex, NostrKeys.authorHexFrom(oneRelay))
        assertEquals(hex, NostrKeys.authorHexFrom("nostr:$oneRelay"))
        listOf("", "hello", nsec, "https://example.com", neventWithKey).forEach { assertNull(it, NostrKeys.authorHexFrom(it)) }
    }

    @Test fun anNpubOverNinety_isStillRefused() {
        val longNpub = Bech32.encode("npub", ByteArray(64))
        assertTrue(longNpub.length > Bech32.DEFAULT_MAX_LENGTH)
        assertNull(NostrKeys.npubToHex(longNpub))
        assertNull(NostrKeys.authorHexFrom(longNpub))
    }

    @Test fun isSecretKey_stillCatchesASecretKeyBeforeAnythingElse() {
        assertTrue(NostrKeys.isSecretKey(nsec))
        assertTrue(NostrKeys.isSecretKey("nostr:$nsec"))
        assertFalse(NostrKeys.isSecretKey(noRelay))
        assertFalse(NostrKeys.isSecretKey(oneRelay))
    }

    // ---- structure: nothing in the relay client can take a relay from a pasted text
    @Test fun theRelayClientCannotBeGivenARelay() {
        val fetches = NostrRelayClient::class.java.declaredMethods.filter { it.name == "fetch" }
        assertNotNull(fetches.singleOrNull())
        // fetch(authorHex: String) is a suspend function: one String plus the continuation, nothing else.
        val parameters = fetches.single().parameterTypes.toList()
        assertEquals(2, parameters.size)
        assertEquals(String::class.java, parameters.first())
    }
}
EOF
```

- [ ] **Step 3: Chạy test, phải ĐỎ (lỗi biên dịch: chưa có `nprofileToHex`, `authorHexFrom`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/n-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrNprofileTest" > /tmp/n-t2.log 2>&1; echo $? > /tmp/n-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t2.exit && break; sleep 5; done; cat /tmp/n-t2.exit; grep -E "^e: |error:" /tmp/n-t2.log | head -5
```
Expected: `1` và `Unresolved reference: nprofileToHex` / `authorHexFrom`.

- [ ] **Step 4: Sửa `NostrKeys.kt` (thêm hằng số và hai hàm; mỗi mẫu đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
K = "app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt"
s = open(K, encoding="utf-8").read()
def sub(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)

sub('''/** NIP-19 helpers: public keys (`npub`) and note ids (`note1`). Never handles a secret key beyond flagging it. */''',
    '''/**
 * NIP-19 helpers: public keys (`npub`, `nprofile`) and note ids (`note1`). Never handles a secret key beyond flagging it.
 * An `nprofile` may carry relay hints chosen by whoever shared it; they are read over and NEVER used, so a pasted text cannot
 * make the app talk to a server of its choosing.
 */''')
sub('''    private const val NOTE_HRP = "note"\n''',
    '''    private const val NOTE_HRP = "note"
    private const val NPROFILE_HRP = "nprofile"
    private const val NPROFILE_MAX_LENGTH = 1000
    private const val TLV_TYPE_PUBKEY = 0
    private const val TLV_HEADER_BYTES = 2
    private const val BYTE_MASK = 0xff
''')
sub('''    fun hexToNpub(hex: String): String? =''',
    '''    /**
     * The public key of an `nprofile` (NIP-19), as lower-case hex; null when the text is not a well-formed one. The payload is a list
     * of type-length-value entries: type 0 is the key (exactly 32 bytes, exactly once); relay hints and unknown types are skipped.
     * Every byte must belong to an entry, so a cut or padded payload is refused rather than half read.
     */
    fun nprofileToHex(input: String): String? {
        val decoded = Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH) ?: return null
        if (decoded.hrp != NPROFILE_HRP) return null
        val bytes = decoded.payload
        var key: ByteArray? = null
        var at = 0
        while (at < bytes.size) {
            if (at + TLV_HEADER_BYTES > bytes.size) return null
            val type = bytes[at].toInt() and BYTE_MASK
            val length = bytes[at + 1].toInt() and BYTE_MASK
            val start = at + TLV_HEADER_BYTES
            if (start + length > bytes.size) return null
            if (type == TLV_TYPE_PUBKEY) {
                if (length != KEY_BYTES || key != null) return null
                key = bytes.copyOfRange(start, start + length)
            }
            at = start + length
        }
        return key?.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    /** The one door the Add Source box uses: a public key typed as an `npub` or as an `nprofile`. */
    fun authorHexFrom(input: String): String? = npubToHex(input) ?: nprofileToHex(input)

    fun hexToNpub(hex: String): String? =''')
open(K, "w", encoding="utf-8").write(s)
EOF
grep -n "NPROFILE\|TLV_\|BYTE_MASK\|fun nprofileToHex\|fun authorHexFrom" app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt
```
Expected: bảy hằng số mới (`NPROFILE_HRP`, `NPROFILE_MAX_LENGTH`, `TLV_TYPE_PUBKEY`, `TLV_HEADER_BYTES`, `BYTE_MASK`), `fun nprofileToHex`, `fun authorHexFrom`.

- [ ] **Step 5: Chạy test, phải XANH (`NostrNprofileTest` và `NostrKeysTest`)**

```bash
rm -f /tmp/n-t2.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrNprofileTest" --tests "com.mckimquyen.reader.infrastructure.social.nostr.NostrKeysTest" > /tmp/n-t2.log 2>&1; echo $? > /tmp/n-t2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t2.exit && break; sleep 5; done; cat /tmp/n-t2.exit; grep -E "FAILED|tests completed" /tmp/n-t2.log | head -8; tail -3 /tmp/n-t2.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Nếu một test đỏ: đọc lý do thật (đừng đoán): có thể là mẫu chuỗi Python (đã kiểm) hoặc kiểu tham số của `fetch` (phản chiếu); sửa test hoặc mã theo bằng chứng.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrKeys.kt app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrNprofileTest.kt
git commit -m "feat(front-02w): read the public key of an nprofile, ignore its relay hints

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: `SubscribeViewModel` dùng `authorHexFrom`, và test đọc mã nguồn không xanh giả (TDD)

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrNprofileTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` (một dòng)

**Interfaces:**
- Consumes: `NostrKeys.authorHexFrom(input: String): String?` (Task 2); `SubscribeViewModel(context, opmlService, rssService, rssHelper, androidStringsHelper, webFeedScraper, newsletterUrlResolver, nostrRelayClient, blueskyApiClient)`; `NostrTestEvents.AUTHOR/PLAIN/OLDER`; `NostrRelayClient.fetch(authorHex: String)`.
- Produces: test V1 đến V4 và test neo A; dòng `val authorHex = ...NostrKeys.authorHexFrom(typed)` trong `search()`.

- [ ] **Step 1: Kiểm các đoạn sẽ sửa còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
V=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -n "NostrKeys.npubToHex(typed)" $V
grep -n "npubToHex(" app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt
git status --short app
```
Expected: đúng 1 dòng trong `SubscribeViewModel.kt`; 4 dòng trong `SubscribeViewModelNostrSourceTest.kt` (đã đếm: 4 lần xuất hiện); `git status` sạch trong `app`.

- [ ] **Step 2: Viết test đỏ V1 đến V4 (lớp mới, dựng ViewModel giống `SubscribeViewModelNostrTest`)**

Khóa `NostrTestEvents.AUTHOR = dff1d77f…`; các chuỗi `npub`/`nprofile` của khóa này do bộ mã hóa Python độc lập tính:

```bash
cat > app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrNprofileTest.kt <<'EOF'
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFetchResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pasting an nprofile into the Add Source box must do exactly what pasting the same person's npub does, and the relays named in the
 * nprofile must never be used. The strings were computed by an independent encoder (the app did not produce their checksums).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscribeViewModelNostrNprofileTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localService = mockk<LocalRssSv>(relaxed = true)
    private val rssService = mockk<RssSv>()
    private val rssHelper = mockk<RssHelper>()
    private val scraper = mockk<WebFeedScraper>()
    private val resolver = mockk<NewsletterUrlResolver>()
    private val relay = mockk<NostrRelayClient>()
    private val strings = AndroidStringsHelper(context)
    private val group = Group(id = "group_1", name = "Default", accountId = 1)
    private val npub = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"
    private val nprofile = "nprofile1qqsdluwh0u4xw8zlxcvrwfkmydqmuk874cw69hkwmppjgrmm2q46vkgv8s759"
    private val nprofileWithRelays = "nprofile1qqsdluwh0u4xw8zlxcvrwfkmydqmuk874cw69hkwmppjgrmm2q46vkgpzemhxue69uhk2anfdshx27rpd4cxcefwvdhk6qgnwaen5te0xyerwt3s9cczuvf68yunjwg4vju7n"
    private lateinit var viewModel: SubscribeViewModel
    private var originalAccountType = 1

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        originalAccountType = context.currentAccountType
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Local.id)
        every { rssService.get() } returns localService
        every { rssService.get(AccountType.Local.id) } returns localService
        coEvery { localService.pullGroups() } returns flowOf(mutableListOf(group))
        coEvery { localService.isFeedExist(any()) } returns false
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER), null)
        viewModel = SubscribeViewModel(
            context = context, opmlService = mockk<OpmlSv>(relaxed = true), rssService = rssService, rssHelper = rssHelper,
            androidStringsHelper = strings, webFeedScraper = scraper, newsletterUrlResolver = resolver, nostrRelayClient = relay,
            blueskyApiClient = mockk(relaxed = true),
        )
        viewModel.init()
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, originalAccountType)
        Dispatchers.resetMain()
    }

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }
    private val state get() = viewModel.subscribeUiState.value

    private fun TestScope.search(text: String) {
        viewModel.inputLink(text)
        viewModel.search()
        advanceUntilIdle()
    }

    private fun verifyNothingWentToTheWeb() {
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
        coVerify(exactly = 0) { resolver.resolveToRssUrl(any()) }
        coVerify(exactly = 0) { scraper.discover(any()) }
    }

    @Test fun anNprofile_showsTheSamePreviewAsItsNpub() = runTest(dispatcher) {
        search(nprofile)
        val feed = requireNotNull(state.feed)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, feed.sourceType)
        assertEquals("the stored address is the npub form, never the nprofile", "nostr:$npub", feed.url)
        assertEquals("nostr:$npub", feed.sourceUrl)
        assertEquals("nostr:$npub", state.linkContent)
        assertEquals(listOf("Hello nostr, first note", "Older note"), state.articles.map { it.title })
        assertTrue(state.lockLinkInput)
        assertEquals("", state.errorMessage)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNprofileWithRelayHints_asksTheRelayClientForTheKeyOnly() = runTest(dispatcher) {
        search(nprofileWithRelays)
        assertEquals("nostr:$npub", state.feed?.url)
        // fetch(authorHex) has no place for a relay: the only thing it can receive is the 64-character key.
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        coVerify(exactly = 1) { relay.fetch(any()) }
        verifyNothingWentToTheWeb()
    }

    @Test fun anNprofileOfASubscribedNpub_isRefusedWithoutAskingTheRelay() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("nostr:$npub") } returns true
        search(nprofileWithRelays)
        coVerify(exactly = 1) { localService.isFeedExist("nostr:$npub") }
        coVerify(exactly = 0) { relay.fetch(any()) }
        assertEquals(strings.getString(com.mckimquyen.reader.R.string.already_subscribed), state.errorMessage)
        assertFalse(state.lockLinkInput)
        verifyNothingWentToTheWeb()
    }

    @Test fun aNostrUriNprofile_isRecognised() = runTest(dispatcher) {
        search("  nostr:$nprofile ")
        assertEquals("nostr:$npub", state.feed?.url)
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
        verifyNothingWentToTheWeb()
    }
}
EOF
```

- [ ] **Step 3: Cập nhật test đọc mã nguồn: đổi neo sang `authorHexFrom(` và thêm test A (neo phải tồn tại)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
T = "app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt"
s = open(T, encoding="utf-8").read()
assert s.count("npubToHex(") == 4, s.count("npubToHex(")
s = s.replace("npubToHex(", "authorHexFrom(")
anchor = "    @Test fun npubIsResolvedBeforeFormatUrl() {"
assert s.count(anchor) == 1
new = '''    // substringAfter() returns the WHOLE text when its anchor is missing, which would make the order checks below pass for nothing.
    @Test fun theNostrAnchorExists() {
        assertTrue("the Nostr branch must call authorHexFrom(", search.contains("authorHexFrom("))
        assertTrue("an nprofile must not be read through npubToHex only", !search.contains("npubToHex("))
        assertTrue(search.indexOf("authorHexFrom(") >= 0 && search.indexOf("formatUrl()") > search.indexOf("authorHexFrom("))
    }

'''
open(T, "w", encoding="utf-8").write(s.replace(anchor, new + anchor, 1))
EOF
grep -c "authorHexFrom(" app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt
```
Expected: một số **lớn hơn 4** (4 neo cũ đã đổi tên cộng các chỗ trong test A), và `grep -c "npubToHex("` trên cùng tệp chỉ còn dòng trong chuỗi thông báo của test A.

- [ ] **Step 4: Chạy test, phải ĐỎ (V1 đến V4 và A đỏ vì ViewModel còn dùng `npubToHex`; test đọc mã cũ cũng đỏ vì neo không còn)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/n-t3.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostrNprofileTest" --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostrSourceTest" > /tmp/n-t3.log 2>&1; echo $? > /tmp/n-t3.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t3.exit && break; sleep 5; done; cat /tmp/n-t3.exit; grep -E "FAILED" /tmp/n-t3.log | head -12
```
Expected: `1`; lỗi gồm đúng `anNprofile_showsTheSamePreviewAsItsNpub`, `anNprofileWithRelayHints_asksTheRelayClientForTheKeyOnly`, `anNprofileOfASubscribedNpub_isRefusedWithoutAskingTheRelay`, `aNostrUriNprofile_isRecognised`, `theNostrAnchorExists`. **Các test neo cũ chưa đỏ ở bước này**: khi neo `authorHexFrom(` chưa có trong mã, `substringAfter` trả nguyên chuỗi và các kiểm thứ tự đúng một cách vô nghĩa; đó chính là lý do có test `theNostrAnchorExists`. Có test neo cũ đỏ vì lý do khác thì đọc lý do trước khi làm tiếp.

- [ ] **Step 5: Sửa một dòng trong `SubscribeViewModel.kt`**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
V = "app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt"
s = open(V, encoding="utf-8").read()
old = "val authorHex = com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.npubToHex(typed)"
assert s.count(old) == 1
open(V, "w", encoding="utf-8").write(s.replace(old, "val authorHex = com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.authorHexFrom(typed)", 1))
EOF
git diff --stat -- app/src/main
```
Expected: 1 tệp, 1 dòng thêm, 1 dòng bỏ.

- [ ] **Step 6: Chạy test, phải XANH (hai lớp mới/sửa và `SubscribeViewModelNostrTest` + `SubscribeViewModelNostrProfileTest` để chắc không gãy luồng `npub`)**

```bash
rm -f /tmp/n-t3.exit; ( ./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" > /tmp/n-t3.log 2>&1; echo $? > /tmp/n-t3.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/n-t3.exit && break; sleep 5; done; cat /tmp/n-t3.exit; grep -E "FAILED|tests completed" /tmp/n-t3.log | head -8; tail -3 /tmp/n-t3.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Nếu `SubscribeViewModelNostrTest` đỏ: đọc lý do (luồng `npub` không được đổi).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrNprofileTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelNostrSourceTest.kt
git commit -m "feat(front-02w): the Add Source box accepts an nprofile like an npub

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Mutation JVM (13 đột biến), so sánh tự động

**Files:** tạm, không commit: `/tmp/n_mut.sh`, `/tmp/n_batch.sh`, `/tmp/n_check.py`. Đột biến tạm trong `Bech32.kt`, `NostrKeys.kt`, `NostrRelayClient.kt`, `SubscribeViewModel.kt` (luôn khôi phục).

**Interfaces:** Consumes: mã sau Task 1 đến 3. Produces: bảng đột biến, tập test đỏ đo được, so với bảng mong đợi ở đầu plan.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất (mỗi mẫu đúng 1 lần), trên mã đã làm xong Task 1 đến 3**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/"
bech = open(J + "infrastructure/social/nostr/Bech32.kt", encoding="utf-8").read()
keys = open(J + "infrastructure/social/nostr/NostrKeys.kt", encoding="utf-8").read()
relay = open(J + "infrastructure/social/nostr/NostrRelayClient.kt", encoding="utf-8").read()
vm = open(J + "ui/page/home/feed/subs/SubscribeViewModel.kt", encoding="utf-8").read()
pats = [
 ("n1", bech, "    const val DEFAULT_MAX_LENGTH = 90\n"),
 ("n2", keys, "                if (length != KEY_BYTES || key != null) return null\n"),
 ("n3", keys, "                if (length != KEY_BYTES || key != null) return null\n"),
 ("n4", keys, "            if (type == TLV_TYPE_PUBKEY) {\n"),
 ("n5", keys, "            if (start + length > bytes.size) return null\n"),
 ("n6", keys, "            if (at + TLV_HEADER_BYTES > bytes.size) return null\n"),
 ("n7", keys, "        val decoded = Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH) ?: return null\n"),
 ("n8", keys, "    fun authorHexFrom(input: String): String? = npubToHex(input) ?: nprofileToHex(input)\n"),
 ("n9", keys, "Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH)"),
 ("n10", relay, "    suspend fun fetch(authorHex: String): NostrFetchResult = withContext(ioDispatcher) {\n"),
 ("n11", vm, "NostrKeys.authorHexFrom(typed)"),
 ("n13", keys, "    private const val NPROFILE_MAX_LENGTH = 1000\n"),
 ("n16", keys, "        if (decoded.hrp != NPROFILE_HRP) return null\n"),
]
print({k: p.count(x) if (p := s) else 0 for k, s, x in pats})
EOF
```
Expected: mọi nhãn `1`. Nhãn nào khác 1: đọc lại tệp và sửa mẫu ở `n_batch.sh` (không chạy batch).

- [ ] **Step 2: Dựng `/tmp/n_mut.sh` (một đột biến một lần, chỉ JVM, các lớp test liên quan)**

```bash
cat > /tmp/n_mut.sh <<'EOF'
#!/bin/zsh
# usage: n_mut.sh <label> <file> <old> <new>   ("base" = no change). JVM only, never touches a device.
label="$1"; FILE="${2-}"; old="${3-}"; new="${4-}"
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
BACKUP=/tmp/n-mutbackup.bin
rest=none
if [ "$label" != base ]; then
  cp -p "$FILE" "$BACKUP"
  restore() { cp -p "$BACKUP" "$FILE"; cmp -s "$BACKUP" "$FILE" && rest=restored || rest=NOT-RESTORED; }
  trap restore EXIT INT TERM
  python3 - "$FILE" "$old" "$new" <<'PY'
import sys
f, o, n = sys.argv[1:4]
s = open(f, encoding="utf-8").read()
assert s.count(o) == 1, "pattern missing or not unique"
open(f, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
  [ $? -eq 0 ] || { echo "$label: PATTERN-PROBLEM"; exit 91; }
fi
./gradlew testDevDebugUnitTest \
  --tests "com.mckimquyen.reader.infrastructure.social.nostr.*" \
  --tests "com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeViewModelNostr*" \
  > "/tmp/n-mut-$label.log" 2>&1
rc=$?
[ "$label" != base ] && { restore; trap - EXIT INT TERM; }
failing=$(grep -E "^com\.mckimquyen.* > .* FAILED" "/tmp/n-mut-$label.log" | sed -E 's/^.* > ([A-Za-z0-9_`. ,()-]+) FAILED.*/\1/' | sort -u | tr '\n' ',')
compile=$(grep -c "^e: " "/tmp/n-mut-$label.log")
echo "$label: gradle_exit=$rc compile_errors=$compile failing=[$failing] source=$rest"
EOF
chmod +x /tmp/n_mut.sh
```
Lưu ý: nếu một đột biến làm **lỗi biên dịch** (`compile_errors>0`) thì không đo được tập đỏ; nhãn đó phải đổi mẫu cho biên dịch được (ví dụ `n10`: thêm tham số có giá trị mặc định để lời gọi cũ vẫn biên dịch).

- [ ] **Step 3: Dựng `/tmp/n_batch.sh` (một script nền, khôi phục + `cmp` từng tệp, `ALLDONE`)**

```bash
cat > /tmp/n_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader
BE=$J/infrastructure/social/nostr/Bech32.kt; KE=$J/infrastructure/social/nostr/NostrKeys.kt
RE=$J/infrastructure/social/nostr/NostrRelayClient.kt; VM=$J/ui/page/home/feed/subs/SubscribeViewModel.kt
cp -p $BE /tmp/n-orig-be.kt; cp -p $KE /tmp/n-orig-ke.kt; cp -p $RE /tmp/n-orig-re.kt; cp -p $VM /tmp/n-orig-vm.kt
M=/tmp/n_mut.sh
zsh $M base
zsh $M n1 $BE '    const val DEFAULT_MAX_LENGTH = 90
' '    const val DEFAULT_MAX_LENGTH = 1000
'
zsh $M n2 $KE '                if (length != KEY_BYTES || key != null) return null
' '                if (key != null) return null
'
zsh $M n3 $KE '                if (length != KEY_BYTES || key != null) return null
' '                if (length != KEY_BYTES) return null
'
zsh $M n4 $KE '            if (type == TLV_TYPE_PUBKEY) {
' '            if (type != TLV_TYPE_PUBKEY && type != 1) return null
            if (type == TLV_TYPE_PUBKEY) {
'
zsh $M n5 $KE '            if (start + length > bytes.size) return null
' ''
zsh $M n6 $KE '            if (at + TLV_HEADER_BYTES > bytes.size) return null
' ''
zsh $M n7 $KE '        val decoded = Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH) ?: return null
' '        val decoded = Bech32.decode(input.trim(), NPROFILE_MAX_LENGTH) ?: return null
'
zsh $M n8 $KE '    fun authorHexFrom(input: String): String? = npubToHex(input) ?: nprofileToHex(input)
' '    fun authorHexFrom(input: String): String? = npubToHex(input)
'
zsh $M n9 $KE 'Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH)' 'Bech32.decode(withoutUriPrefix(input))'
zsh $M n10 $RE '    suspend fun fetch(authorHex: String): NostrFetchResult = withContext(ioDispatcher) {
' '    suspend fun fetch(authorHex: String, extraRelay: String? = null): NostrFetchResult = withContext(ioDispatcher) {
'
zsh $M n11 $VM 'NostrKeys.authorHexFrom(typed)' 'NostrKeys.npubToHex(typed)'
zsh $M n13 $KE '    private const val NPROFILE_MAX_LENGTH = 1000
' '    private const val NPROFILE_MAX_LENGTH = 2000
'
zsh $M n16 $KE '        if (decoded.hrp != NPROFILE_HRP) return null
' ''
zsh $M base
for p in "$BE:/tmp/n-orig-be.kt" "$KE:/tmp/n-orig-ke.kt" "$RE:/tmp/n-orig-re.kt" "$VM:/tmp/n-orig-vm.kt"; do
  cmp -s "${p#*:}" "${p%%:*}" && echo "final ${p%%:*}: identical" || echo "final ${p%%:*}: DIFFERENT"
done
echo "git diff vs HEAD (main): $(git diff --stat -- app/src/main | wc -l) lines"
echo ALLDONE
EOF
```

- [ ] **Step 4: Dựng `/tmp/n_check.py` (so sánh tự động tập test đỏ với bảng ở đầu plan)**

Tên test trong log Gradle có dạng `Lớp > tên test FAILED`; Gradle in tên hàm đúng như viết. Kiểm với tên hàm (không kèm lớp):

```bash
cat > /tmp/n_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re, sys
B0 = "bech32_rejectsValidButOverlongStrings"
B2 = "bech32_defaultLimitIsStillNinety"
P = {
 "P1": "officialVector_decodesToTheKnownKey", "P2": "anNpubAndAnNprofileOfOnePerson_giveOneHex",
 "P4": "aProfileWithRelayHints_isAccepted_andTheHintsAreNotUsed", "P5": "unknownTypesAreSkipped",
 "P6": "theNostrPrefixTheCaseAndTheSpaces_areAccepted", "P7": "aKeyThatIsNot32Bytes_isRejected",
 "P8": "twoKeys_areRejected", "P10": "aCutTlv_isRejected", "P11": "aStringOverAThousand_isRejected",
 "P12": "aLongProfileUnderTheLimit_isAccepted", "P13": "otherKindsOfIdentifier_areRejected",
 "P14": "authorHexFrom_takesBothAndRefusesJunk", "P15": "anNpubOverNinety_isStillRefused",
 "P16": "theRelayClientCannotBeGivenARelay",
}
V = {
 "V1": "anNprofile_showsTheSamePreviewAsItsNpub", "V2": "anNprofileWithRelayHints_asksTheRelayClientForTheKeyOnly",
 "V3": "anNprofileOfASubscribedNpub_isRefusedWithoutAskingTheRelay", "V4": "aNostrUriNprofile_isRecognised",
}
A = "theNostrAnchorExists"; ORDER = "npubIsResolvedBeforeFormatUrl"
# (kind, set) kind "exact": must equal; "atleast": must contain, and everything must be inside the allowed lists
EXPECTED = {
 "n1": ("exact", {B0, B2, P["P15"]}),
 "n2": ("exact", {P["P7"]}),
 "n3": ("exact", {P["P7"]}),
 "n4": ("exact", {P["P5"]}),
 "n5": ("exact", {P["P10"]}),
 "n6": ("exact", {P["P10"]}),
 "n7": ("exact", {P["P6"], P["P14"], V["V4"]}),
 "n8": ("exact", {P["P14"], V["V1"], V["V2"], V["V3"], V["V4"]}),
 "n9": ("atleast", {P["P1"], P["P4"], P["P12"]}),
 "n10": ("exact", {P["P16"]}),
 "n11": ("atleast", {V["V1"], V["V2"], V["V3"], V["V4"], A}),
 "n13": ("exact", {P["P11"]}),
 "n16": ("exact", {P["P13"]}),
}
bad = 0
for label, (kind, want) in EXPECTED.items():
    try:
        text = open(f"/tmp/n-mut-{label}.log", encoding="utf-8").read()
    except FileNotFoundError:
        print("BAD  ", label, "no log"); bad += 1; continue
    if re.search(r"^e: ", text, re.M):
        print("BAD  ", label, "did not compile"); bad += 1; continue
    got = set(re.findall(r"^com\.mckimquyen\.\S+ > (\S+) FAILED", text, re.M))
    ok = (got == want) if kind == "exact" else want <= got
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, kind, "expected", sorted(want), "got", sorted(got))
base = open("/tmp/n-mut-base.log", encoding="utf-8").read()
print("base:", "GREEN" if "BUILD SUCCESSFUL" in base else "NOT GREEN")
bad += 0 if "BUILD SUCCESSFUL" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```

- [ ] **Step 5: Chạy batch nền, chờ ALLDONE**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/n-batch.log; ( zsh /tmp/n_batch.sh > /tmp/n-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/n-batch.log 2>/dev/null && break; sleep 5; done; cut -c1-260 /tmp/n-batch.log
```
Không sửa bốn tệp production cho tới `ALLDONE`. Mỗi đột biến mất 1 đến 2 phút (biên dịch + chạy hai nhóm lớp); chưa xong sau 115×5 giây thì chạy lại **chỉ** vòng chờ.

- [ ] **Step 6: Đối chiếu tự động và xử lý lệch**

```bash
python3 /tmp/n_check.py
```
Expected: `ALL-MATCH`. Đột biến lệch: **đọc lý do** (tập đỏ thực tế và thông báo lỗi trong `/tmp/n-mut-<nhãn>.log`) rồi chọn: (a) dự đoán sai, sửa bảng ở đầu plan và ghi trong báo cáo; (b) test yếu (đột biến sống sót, tức mọi test vẫn xanh): thêm khẳng định (quay lại Task 2 hoặc 3) rồi chạy lại batch. Kiểm cuối batch: bốn dòng `final … identical`, `git diff vs HEAD (main): 0 lines` (nghĩa là không còn đột biến nào; nếu khác 0 đó là thay đổi của Task 1 đến 3 chưa commit, đã commit thì phải bằng 0).

---

### Task 5: JVM đầy đủ, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng FRONT-02b, FRONT-02c, dòng 02w mới), `doc/task/todo/FRONT-02_decentralized-web3-social.md` (mục "Cập nhật"), `docs/superpowers/specs/2026-11-18-front-02w-nostr-nprofile-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02w_nostr-nprofile_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/n-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/n-jvm.log 2>&1; echo $? > /tmp/n-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/n-jvm.exit && break; sleep 5; done; cat /tmp/n-jvm.exit; grep -E "FAILED|tests completed" /tmp/n-jvm.log | head -5; tail -3 /tmp/n-jvm.log
python3 - <<'EOF'
import glob, re
t = f = e = s = 0
for p in glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml'):
    h = open(p, encoding='utf-8').read(2000)
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', h)
    a, b, c, d = map(int, m.groups()); t += a; s += b; f += c; e += d
print('tests', t, 'skipped', s, 'failures', f, 'errors', e)
EOF
```
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi. Số test phải **lớn hơn 3.797 theo đúng số test thêm** (B1, B2 hai test; `NostrNprofileTest` 18 test; `SubscribeViewModelNostrNprofileTest` 4 test; test A một test: 25 test mới, **kỳ vọng 3.822**; chỉ để đối chiếu, không dùng làm bằng chứng: báo cáo ghi số đo được). Có test đỏ **không liên quan**: chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả (không bỏ qua, không giấu).

- [ ] **Step 2: Kiểm phạm vi diff (đúng ba tệp production)**

```bash
git status --short; git diff --stat HEAD~3 HEAD -- app/src/main app/build.gradle app/schemas
git diff --name-only HEAD~3 HEAD
```
Expected: `app/src/main` chỉ có `Bech32.kt`, `NostrKeys.kt`, `SubscribeViewModel.kt`; không có `app/build.gradle`, `app/schemas`, manifest, `res`, `LocalRssSv.kt`, `NostrRelayClient.kt`; test: `NostrKeysTest.kt`, `NostrNprofileTest.kt`, `SubscribeViewModelNostrNprofileTest.kt`, `SubscribeViewModelNostrSourceTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md`, task gốc và spec**

Báo cáo `doc/task/done/FRONT-02w_nostr-nprofile_DONE.md` (tiếng Việt đơn giản) gồm: điều đã làm (ba tệp production); vector chính thức NIP-19 (131 ký tự) và các chuỗi do bộ mã hóa Python độc lập tính; bảng 13 đột biến với tập test đỏ **đo được** (kèm kết quả `n_check.py`, ghi mọi dự đoán sai đã sửa); JVM số **đo lại** (kèm lần đỏ chập chờn nếu có); cách `nprofile` đi qua luồng `npub` (lưu `nostr:<npub>`); **gợi ý relay bị bỏ qua** (cấu trúc: `fetch(authorHex: String)`); không kiểm gì (máy thật, mạng thật, dán thật vào ô, link `njump.me`, `nevent`/`naddr`, ảnh, trả lời; lint không đổi); hệ quả cần biết (người dán `nprofile` của người chỉ có ở relay khác thấy xem trước trống như `npub` hiện nay).

`doc/feature.md`: dòng FRONT-02b thêm "nhận thêm `nprofile` (FRONT-02w)"; thêm dòng FRONT-02w sau dòng FRONT-02v (✅ ngày, đường dẫn báo cáo, số liệu thật, nói thẳng phần chưa làm). `doc/task/todo/FRONT-02_decentralized-web3-social.md`: ở mục "Cập nhật" ghi `nprofile` đã làm, còn lại ảnh và trả lời Nostr, đăng nhập, đăng bài (giữ task gốc trong `todo/`).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02w): report that the Add Source box now accepts an nprofile

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -5; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** nới độ dài riêng cho `nprofile`, `npub`/`note1` giữ 90 → Task 1 (B1, B2) và `P15`; đọc TLV, một khóa 32 byte, bỏ qua relay và loại lạ, từ chối cụt/hai khóa/không khóa/sai độ dài → Task 2 (P3 đến P13); `authorHexFrom` và `SubscribeViewModel` gọi nó, lưu `nostr:<npub>`, "Đã đăng ký" khi trùng, relay không dùng → Task 3 (V1 đến V4, test A); `isSecretKey` trước mọi đường → test `isSecretKey_stillCatchesASecretKeyBeforeAnythingElse` và test đọc mã sẵn có `nsecGuardIsBeforeEveryNetworkResolver` (chạy ở Task 3 Step 6 và Task 5 Step 1); `NostrRelayClient.fetch` chỉ nhận `authorHex` → P16 và đột biến `n10`; vector chính thức NIP-19 → P1; đột biến n1 đến n7 của spec → bảng đột biến (đổi `n8` thành kiểm cấu trúc, ghi ở điều chỉnh 6); JVM đo lại, báo cáo, `feature.md`, task gốc → Task 5; nói thẳng phần không kiểm → Global Constraints và Task 5.
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ (test, mã production, script, `n_check.py`). Một chỗ phụ thuộc thực tế: Task 4 Step 2 ghi chú rằng đột biến làm lỗi biên dịch phải đổi mẫu (ví dụ `n10` đã dùng tham số mặc định để biên dịch được).
3. **Nhất quán tên:** `Bech32.DEFAULT_MAX_LENGTH`, `Bech32.decode(text, maxLength)`, `NostrKeys.nprofileToHex`, `NostrKeys.authorHexFrom`, `NPROFILE_HRP`, `NPROFILE_MAX_LENGTH`, `TLV_TYPE_PUBKEY`, `TLV_HEADER_BYTES`, `BYTE_MASK` khớp giữa Task 1 đến 4; tên test P1 đến P16, V1 đến V4, A, B0, B2 trong bảng khớp tên hàm trong mã test và `n_check.py`. Chuỗi mẫu `nprofile` của khóa `7e7e9c42…` (Task 2) và `dff1d77f…` (Task 3) đều do Python độc lập tính; mô hình tham chiếu Python của thuật toán khớp cả 13 chuỗi của Task 2.
4. **Rủi ro còn lại (nói thẳng):** (a) bảng tập test đỏ là **dự đoán**, có thể lệch (đã nêu cách xử lý); (b) test phản chiếu `theRelayClientCannotBeGivenARelay` phụ thuộc cách Kotlin dịch hàm `suspend` (một `String` cộng `Continuation`); đột biến `n10` chứng minh nó không xanh giả; (c) test đọc mã nguồn vẫn dễ vỡ khi đổi tên, nay có test A bảo vệ; (d) số test mong đợi 3.822 chỉ để đối chiếu; (e) không kiểm trên máy hay mạng thật, theo thiết kế.
