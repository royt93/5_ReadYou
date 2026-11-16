# FRONT-02i — Chặn đăng ký trùng một tài khoản Bluesky (RSS cũ và API mới) (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Dán một tài khoản Bluesky đã được đăng ký ở **một trong hai dạng** (nguồn RSS cũ hoặc nguồn API mới) thì báo "Đã đăng ký" ngay, không gọi mạng, không tạo nguồn thứ hai.

**Architecture:** Thêm một hàm thuần `BlueskyFeedMapper.rssUrl(actor)` (địa chỉ RSS chuẩn của một tài khoản, cùng cách chuẩn hóa với `feedUrl`) và đổi lần kiểm `isFeedExist` ở đầu nhánh Bluesky của `SubscribeViewModel.search()` thành kiểm **cả hai** địa chỉ (`bsky:<actor>` và `https://bsky.app/profile/<actor>/rss`), trước khi hiện "Đang tìm" và trước khi gọi API. Dùng chuỗi sẵn có `already_subscribed`.

**Tech Stack:** Kotlin 2.1, JUnit4 + Robolectric + MockK (chỉ JVM), `kotlinx-coroutines-test`.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Chỉ JVM, KHÔNG chạm thiết bị nào.** Pixel `2B051FDH3006MU` cấm kể cả đọc; Tecno KJ7 không cần.
- **Không** chuỗi mới, locale, Room (giữ v22), DataStore, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. Dùng chuỗi **sẵn có** `R.string.already_subscribed`.
- Chỉ đổi `SubscribeViewModel.search()` (nhánh Bluesky) và thêm **một** hàm vào `BlueskyFeedMapper`. Không `!!`, không `lateinit` (R5).
- Công cụ trong `/Users/loitran/.claude/tmp-f29/` (**không** `/tmp`); mutation bằng `jvm_mut.sh` (`usage: label file old new FQCN...`), một batch nền duy nhất, **không sửa tệp tới `ALLDONE`**, `cmp` sau mỗi đợt, `git diff` phải sạch.
- Gradle chạy NỀN, output ra file, chờ `.exit`; đọc XML chỉ sau khi `.exit` tồn tại; "completed" của lệnh nền chỉ là lệnh khởi chạy.
- zsh: không `echo ====`; `grep -r` quote mẫu và **không** `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i` cần `''`.
- Đếm test bằng **cùng một cách ở hai thời điểm** (tổng số test chạy từ XML), không so tổng `@Test` với số test chạy.
- R2: cập nhật `doc/feature.md`. R5: không magic number; mỗi thay đổi có test.
- Nói thẳng phần không kiểm.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

1. **Sửa một chỗ sai của spec: chiều "đã đăng ký API rồi dán lại" ĐÃ được chặn từ trước.** Spec ghi (mục "Hệ quả hiện nay") rằng nếu API lỗi lần sau thì rơi về RSS và không thấy nguồn API. Không đúng: nhánh Bluesky (`SubscribeViewModel.kt:215`) kiểm `isFeedExist(bsky:<actor>)` **ngay đầu** và `return@launch` nếu có, nên không bao giờ tới lúc gọi API hay rơi về RSS. Chỉ chiều **đã có nguồn RSS rồi dán lại** là hở thật (nhánh chỉ kiểm địa chỉ API, không thấy `https://bsky.app/profile/<actor>/rss`, rồi gọi API và tạo nguồn API thứ hai). Hệ quả cho plan: các test của chiều API sẽ **xanh ngay từ đầu** (chúng khóa hành vi sẵn có, chứng minh bằng mutation, không bằng "đỏ rồi xanh"); chỉ các test của chiều RSS đỏ thật ở bước đỏ.
2. **Không dùng `socialUrl!!`.** Thay vì lấy lại `socialUrl`, thêm `BlueskyFeedMapper.rssUrl(actor)` dựng địa chỉ RSS từ **actor đã tách**. Khớp vì resolver và `feedUrl` chuẩn hóa giống nhau: handle chữ thường, DID giữ nguyên (`SocialFeedUrlResolver.blueskyLink`: `DID.matches(id) -> ".../profile/$id/rss"`, `isDomain(id) -> ".../${id.lowercase()}/rss"`; `feedUrl` lower-case chỉ khi không bắt đầu `did:`). Test vòng tròn ở Task 1 chứng minh `rssUrl(actorFromRssUrl(s)) == s` với mọi địa chỉ `s` mà resolver sinh ra.
3. **`isFeedExist` so đúng bằng, phân biệt hoa thường** (`WHERE accountId = :accountId and url = :url`, cột `url` không `COLLATE NOCASE`; đã đọc schema bảng `feed` trên bản sao lưu), nên chuẩn hóa chữ thường cho handle là điều làm hai dạng khớp nhau; DID `did:plc:AbC123` **không** khớp `did:plc:abc123` (đúng ý: hai DID khác nhau).
4. **`setUp` của `SubscribeViewModelBlueskyTest` mock `isFeedExist(any()) returns false`** (dòng 81) nên mọi test phải đặt `coEvery { localService.isFeedExist(<địa chỉ cụ thể>) } returns true` **sau** `setUp` (MockK lấy khai báo sau cùng cho cùng tham số khớp cụ thể hơn; test sẵn có ở dòng 196 đã làm vậy).
5. **Hai test sẵn có phải xanh nguyên vẹn:** `SubscribeViewModelSocialTest.theAlreadySubscribedCheck_usesTheRssAddress` (Mastodon, `coVerify(exactly = 1) isFeedExist(rss)`) và hai test Bluesky RSS cũ (`aBlueskyHandle_isNotMangledByFormatUrl_andIsSearchedAsItsRssAddress`, `aBlueskyProfileLink_isSearchedAsItsRssAddress`), trong đó `blueskyApiClient = mockk(relaxed = true)` nên API trả đối tượng giả và rơi về RSS; thêm một lần `isFeedExist` cho địa chỉ RSS ở đầu nhánh **không** làm hỏng chúng vì `setUp` của lớp đó mock `isFeedExist(any()) returns false`. Task 2 Step 5 chạy chúng.
6. **Câu `if (rssService.get().isFeedExist(feedUrl)) {` xuất hiện HAI lần trong `SubscribeViewModel.kt`:** dòng 144 (nhánh Nostr) và dòng 215 (nhánh Bluesky). Mẫu một dòng **không độc nhất**: sửa và đột biến phải dùng mẫu **hai dòng** bắt đầu bằng `val feedUrl = …BlueskyFeedMapper.feedUrl(blueskyActor)` (đã kiểm: xuất hiện đúng một lần). Nhánh Nostr **không** được đụng (đây là điều test Nostr hiện có bảo vệ).
7. **Mutant tương đương, KHÔNG đưa vào batch:** đưa lần kiểm xuống sau lệnh đặt `title = searching, lockLinkInput = true` cho trạng thái cuối giống hệt (nhánh "đã đăng ký" đặt lại `title` và `lockLinkInput = false`). Nó chỉ khác ở việc có hiện "Đang tìm" thoáng qua hay không, mà test JVM không quan sát được trạng thái trung gian. Vì vậy thứ tự "kiểm trước khi gọi API" được khóa bằng `coVerify(exactly = 0) { api.fetch(any()) }`, không bằng vị trí so với dòng `searching`.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt` | thêm `rssUrl(actor)` |
| Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` | đầu nhánh Bluesky kiểm cả hai địa chỉ |
| Modify `app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt` | test `rssUrl` và vòng tròn với resolver |
| Modify `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt` | 8 test chặn trùng |
| Tạm, **không commit**, trong `/Users/loitran/.claude/tmp-f29/`: `bsky_t6_batch.sh` | mutation JVM |
| Modify `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02g_bluesky-api_DONE.md`, spec | tài liệu |
| Create `doc/task/done/FRONT-02i_bluesky-duplicate-guard_DONE.md` | báo cáo |

---

### Task 1: `BlueskyFeedMapper.rssUrl(actor)`: địa chỉ RSS chuẩn của một tài khoản

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt`

**Interfaces:**
- Consumes: `BlueskyFeedMapper.isActor`, `actorFromRssUrl`, `feedUrl` (Task đã commit ở 02g), `SocialFeedUrlResolver.resolve(input): String?`.
- Produces: `BlueskyFeedMapper.rssUrl(actor: String): String` (handle chữ thường, DID giữ nguyên: `"https://bsky.app/profile/<actor>/rss"`).

- [ ] **Step 1: Đọc lại API thật**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 36,56p $J/infrastructure/social/bluesky/BlueskyFeedMapper.kt
sed -n 61,76p $J/infrastructure/social/SocialFeedUrlResolver.kt
grep -n "import java.util" $J/infrastructure/social/bluesky/BlueskyFeedMapper.kt
```
Expected: `RSS_PROFILE_PREFIX`, `RSS_SUFFIX`, `DID_PREFIX` là `private const` của mapper; `Locale` đã import; `feedUrl` lower-case khi không bắt đầu `did:`; `blueskyLink` giữ DID, hạ chữ thường handle. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết test đỏ** (thêm vào cuối lớp `BlueskyFeedMapperTest`, trước dấu `}` cuối)

```kotlin
    // ---- the RSS address of an account, built the same way the resolver builds it
    @Test fun rssUrl_lowercasesAHandle_butKeepsADidExactly() {
        assertEquals("https://bsky.app/profile/alice.bsky.social/rss", BlueskyFeedMapper.rssUrl("Alice.Bsky.Social"))
        assertEquals("https://bsky.app/profile/did:plc:AbC123/rss", BlueskyFeedMapper.rssUrl("did:plc:AbC123"))
    }

    @Test fun rssUrl_trimsSpaces_likeFeedUrlDoes() {
        assertEquals("https://bsky.app/profile/alice.bsky.social/rss", BlueskyFeedMapper.rssUrl("  alice.bsky.social "))
    }

    // The two addresses of one account must name the same actor, whatever the person pasted.
    @Test fun theApiAddressAndTheRssAddress_ofOneAccount_nameTheSameActor() {
        listOf("alice.bsky.social", "Alice.Bsky.Social", "did:plc:AbC123", "did:web:Example.Com").forEach { actor ->
            val fromApi = BlueskyFeedMapper.feedUrl(actor).removePrefix(BlueskyFeedMapper.FEED_URL_PREFIX)
            val fromRss = BlueskyFeedMapper.actorFromRssUrl(BlueskyFeedMapper.rssUrl(actor))
            assertEquals(actor, fromApi, fromRss)
        }
    }

    // Everything the resolver can build for Bluesky must come back out of rssUrl unchanged: this is what lets the app
    // look for the old RSS source without keeping the resolver's output around.
    @Test fun rssUrl_ofTheActorInsideAResolverAddress_isThatSameAddress() {
        listOf(
            "@alice.bsky.social", "@Alice.Bsky.Social", "https://bsky.app/profile/alice.bsky.social",
            "https://bsky.app/profile/Alice.Bsky.Social/rss", "https://bsky.app/profile/did:plc:AbC123",
            "https://bsky.app/profile/did:plc:AbC123/rss", "https://bsky.app/profile/did:web:Example.Com",
        ).forEach { typed ->
            val resolved = requireNotNull(com.mckimquyen.reader.infrastructure.social.SocialFeedUrlResolver.resolve(typed)) { typed }
            val actor = requireNotNull(BlueskyFeedMapper.actorFromRssUrl(resolved)) { typed }
            assertEquals(typed, resolved, BlueskyFeedMapper.rssUrl(actor))
        }
    }
```

- [ ] **Step 3: Chạy đỏ** (nền, `--tests '*BlueskyFeedMapperTest'`): lệnh như dưới.

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/g6-red.exit; ( ./gradlew :app:compileDevDebugUnitTestKotlin > /tmp/g6-red.log 2>&1; echo $? > /tmp/g6-red.exit ) > /dev/null 2>&1 &
for i in $(seq 1 110); do test -f /tmp/g6-red.exit && break; sleep 5; done; echo exit=$(cat /tmp/g6-red.exit)
grep -nE "^e: " /tmp/g6-red.log | sed -E 's#file:///[^ ]*/##' | head -5 | cut -c1-200
```
Expected: `exit=1` và lỗi `Unresolved reference 'rssUrl'` (không phải lỗi cú pháp).

- [ ] **Step 4: Viết mã tối thiểu** (`BlueskyFeedMapper.kt`, ngay sau `actorFromRssUrl`)

```kotlin
    /** The RSS address of an account, spelled exactly as the resolver spells it (handle lower-cased, DID kept as given). */
    fun rssUrl(actor: String): String {
        val trimmed = actor.trim()
        val name = if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)
        return RSS_PROFILE_PREFIX + name + RSS_SUFFIX
    }
```

- [ ] **Step 5: Chạy xanh** (nền): `./gradlew :app:testDevDebugUnitTest --tests '*BlueskyFeedMapperTest' --rerun-tasks`; đọc XML sau `.exit`. Expected: exit 0, 0 lỗi, `BlueskyFeedMapperTest` = 29 + 4 = **33** test.

- [ ] **Step 6: Không commit riêng** (commit cùng Task 2, một thay đổi hành vi).

---

### Task 2: Đầu nhánh Bluesky kiểm cả hai địa chỉ (TDD, JVM)

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`

**Interfaces:**
- Consumes: `BlueskyFeedMapper.rssUrl(actor)`, `BlueskyFeedMapper.feedUrl(actor)` (Task 1 và 02g); `R.string.already_subscribed`.
- Produces: không có API mới; hành vi: `search()` báo `already_subscribed` khi `isFeedExist(bsky:<actor>)` **hoặc** `isFeedExist(https://bsky.app/profile/<actor>/rss)` là đúng, trước mọi cuộc gọi mạng.

- [ ] **Step 1: Đọc lại mã thật ngay trước khi sửa**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "val socialUrl = SocialFeedUrlResolver.resolve(typed)" $J/ui/page/home/feed/subs/SubscribeViewModel.kt
sed -n 213,228p $J/ui/page/home/feed/subs/SubscribeViewModel.kt
```
Expected: dòng `socialUrl` đúng một lần; đoạn `val feedUrl = …feedUrl(blueskyActor)` rồi `if (rssService.get().isFeedExist(feedUrl)) {`. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết test đỏ**: thêm vào `SubscribeViewModelBlueskyTest` (trước dấu `}` cuối lớp). Dùng `rssUrl` sẵn có của lớp (`https://bsky.app/profile/alice.bsky.social/rss`).

```kotlin
    // ---- the same account, already subscribed in the other form

    private fun assertReportedAsAlreadySubscribed() {
        assertEquals(strings.getString(R.string.already_subscribed), state.errorMessage)
        assertFalse("the box must be editable again", state.lockLinkInput)
        assertNull(state.feed)
    }

    @Test fun anAccountAlreadySubscribedAsRss_isReported_withoutAnyNetworkCall() = runTest(dispatcher) {
        coEvery { localService.isFeedExist(rssUrl) } returns true
        search("@alice.bsky.social")
        assertReportedAsAlreadySubscribed()
        coVerify(exactly = 0) { api.fetch(any()) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun anAccountAlreadySubscribedAsApi_isReported_evenWhenTheApiWouldFailNow() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("bsky:alice.bsky.social") } returns true
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        search("@alice.bsky.social")
        assertReportedAsAlreadySubscribed()
        coVerify(exactly = 0) { api.fetch(any()) }
        coVerify(exactly = 0) { rssHelper.searchFeed(any()) }
    }

    @Test fun aHandleTypedInMixedCase_matchesTheLowerCaseSourceStoredInEitherForm() = runTest(dispatcher) {
        coEvery { localService.isFeedExist(rssUrl) } returns true
        search("@Alice.Bsky.Social")
        assertReportedAsAlreadySubscribed()
        coVerify(exactly = 0) { api.fetch(any()) }

        viewModel.reset(); viewModel.init()
        coEvery { localService.isFeedExist(rssUrl) } returns false
        coEvery { localService.isFeedExist("bsky:alice.bsky.social") } returns true
        search("https://bsky.app/profile/Alice.Bsky.Social")
        assertReportedAsAlreadySubscribed()
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aDidKeepsItsCase_soADifferentCaseIsAnotherAccountAndIsNotBlocked() = runTest(dispatcher) {
        coEvery { localService.isFeedExist("https://bsky.app/profile/did:plc:AbC123/rss") } returns true
        coEvery { localService.isFeedExist("bsky:did:plc:AbC123") } returns false
        coEvery { api.fetch("did:plc:abc123") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("https://bsky.app/profile/did:plc:abc123")
        assertEquals("", state.errorMessage)
        assertEquals("bsky:did:plc:abc123", state.feed?.url)

        viewModel.reset(); viewModel.init()
        search("https://bsky.app/profile/did:plc:AbC123")
        assertReportedAsAlreadySubscribed()
    }

    @Test fun aDifferentHandle_isNotBlockedByAnotherAccountsSource() = runTest(dispatcher) {
        coEvery { localService.isFeedExist(rssUrl) } returns true
        coEvery { localService.isFeedExist("bsky:alice.bsky.social") } returns true
        coEvery { api.fetch("bob.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@bob.bsky.social")
        assertEquals("", state.errorMessage)
        assertEquals("bsky:bob.bsky.social", state.feed?.url)
        coVerify(exactly = 1) { api.fetch("bob.bsky.social") }
    }

    @Test fun bothAddressesAreAskedAbout_exactlyOnceEach_andNothingElseIsAsked() = runTest(dispatcher) {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "hello"))
        search("@alice.bsky.social")
        coVerify(exactly = 1) { localService.isFeedExist("bsky:alice.bsky.social") }
        coVerify(exactly = 1) { localService.isFeedExist(rssUrl) }
    }

    @Test fun aNonLocalAccount_doesNotAskAboutTheBlueskyAddresses() = runTest(dispatcher) {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountType, AccountType.Fever.id)
        coEvery { rssHelper.searchFeed(rssUrl) } returns feedAt(rssUrl)
        search("@alice.bsky.social")
        coVerify(exactly = 0) { localService.isFeedExist("bsky:alice.bsky.social") }
        coVerify(exactly = 0) { api.fetch(any()) }
    }

    @Test fun aMastodonHandle_isStillCheckedOnlyAgainstItsOwnRssAddress() = runTest(dispatcher) {
        val rss = "https://mastodon.social/@Mastodon.rss"
        coEvery { localService.isFeedExist(rss) } returns true
        search("@Mastodon@mastodon.social")
        assertEquals(strings.getString(R.string.already_subscribed), state.errorMessage)
        coVerify(exactly = 1) { localService.isFeedExist(rss) }
        coVerify(exactly = 0) { localService.isFeedExist(match { it.startsWith("bsky:") || it.startsWith("https://bsky.app") }) }
    }
```
Điều xác nhận bằng lần chạy đầu (không đoán): (1) `viewModel.reset(); viewModel.init()` giữa hai lần `search` đưa ô về trạng thái ban đầu (đã dùng ở test `aProfileLink_andADidLink_alsoUseTheApi` có sẵn); (2) `coEvery` đặt **sau** `setUp` thắng `isFeedExist(any())` cho tham số cụ thể (đã dùng ở dòng 196); (3) `AccountType.Fever` tồn tại (đã dùng ở `aNonLocalAccount_neverUsesTheApi`). Khi một test cần "đặt lại" cuộc gọi `isFeedExist` đã `returns true`, dùng `coEvery … returns false` như trên, không `clearMocks`.

- [ ] **Step 3: Chạy đỏ** (nền, `--tests '*SubscribeViewModelBlueskyTest'`), đọc XML sau `.exit`:

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/g6-t2red.exit; ( ./gradlew :app:testDevDebugUnitTest --tests '*SubscribeViewModelBlueskyTest' > /tmp/g6-t2red.log 2>&1; echo $? > /tmp/g6-t2red.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/g6-t2red.exit && break; sleep 5; done; echo exit=$(cat /tmp/g6-t2red.exit)
python3 - <<'PYEOF'
import glob, xml.etree.ElementTree as ET
for f in glob.glob('app/build/test-results/testDevDebugUnitTest/*SubscribeViewModelBlueskyTest.xml'):
    r = ET.parse(f).getroot(); print('tests', r.get('tests'), 'failures', r.get('failures'), 'errors', r.get('errors'))
    for t in r.iter('testcase'):
        fl = t.find('failure') if t.find('failure') is not None else t.find('error')
        if fl is not None: print('FAIL', t.get('name'), '|', (fl.get('message') or '')[:110].replace('\n', ' '))
PYEOF
```
Expected: **exit 1**; tests = 15 + 8 = 23. **Đỏ đúng 4 test** (chiều RSS): `anAccountAlreadySubscribedAsRss_isReported_withoutAnyNetworkCall`, `aHandleTypedInMixedCase_matchesTheLowerCaseSourceStoredInEitherForm` (nửa đầu), `aDidKeepsItsCase_...` (nửa sau: `did:plc:AbC123` đã có nguồn RSS), `bothAddressesAreAskedAbout_exactlyOnceEach_...` (thiếu lần hỏi RSS). **Xanh ngay** (khóa hành vi sẵn có, điều chỉnh 1): `anAccountAlreadySubscribedAsApi_...`, `aDifferentHandle_...`, `aNonLocalAccount_...`, `aMastodonHandle_...`. Nếu số đỏ khác 4: **dừng**, đọc lý do từng test (lỗi trong test, không phải trong mã), không sửa mã khi chưa hiểu.

- [ ] **Step 4: Viết mã tối thiểu** trong `SubscribeViewModel.search()`: đổi **một** điều kiện.

Tìm (mẫu **hai dòng**, độc nhất; **không** dùng riêng dòng `if (...isFeedExist(feedUrl)) {` vì nó cũng có ở nhánh Nostr):
```kotlin
                    val feedUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(blueskyActor)
                    if (rssService.get().isFeedExist(feedUrl)) {
```
Đổi thành:
```kotlin
                    val feedUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(blueskyActor)
                    val oldRssUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.rssUrl(blueskyActor)
                    // The same account may already be here as the old RSS source or as the API source; either one is "already subscribed".
                    if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {
```
Không đổi gì khác: khối `it.copy(... already_subscribed ...)` và `return@launch` giữ nguyên. **Không** thêm biến thừa, không refactor.

- [ ] **Step 4b: Kiểm nhánh Nostr không bị đụng**:

```bash
grep -c "if (rssService.get().isFeedExist(feedUrl)) {" app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
git diff -U0 -- app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt | grep "^[+-] " | cut -c1-150
```
Expected: số đếm là `1` (chỉ còn nhánh Nostr, vì Bluesky giờ có `|| ...oldRssUrl`); phần `git diff` chỉ có **hai** dòng thêm (`val oldRssUrl`, chú thích) và **một** dòng đổi (`if (...|| ...)`), không dòng nào khác. Nếu khác: dừng, hoàn lại.

- [ ] **Step 5: Chạy xanh** (nền, `--rerun-tasks`): `--tests '*SubscribeViewModelBlueskyTest' --tests '*SubscribeViewModelSocialTest' --tests '*SubscribeViewModelNostrTest' --tests '*BlueskyFeedMapperTest' --tests '*SubscribeDialogTypingTest'`. Expected: exit 0, 0 lỗi; `SubscribeViewModelBlueskyTest` 23, `BlueskyFeedMapperTest` 33; `SubscribeViewModelSocialTest` giữ nguyên số test (gồm `theAlreadySubscribedCheck_usesTheRssAddress` và hai test Bluesky RSS cũ, đều xanh).

- [ ] **Step 6: Mutation** (`bsky_t6_batch.sh`, một script nền, tệp `SubscribeViewModel.kt` và `BlueskyFeedMapper.kt`, lớp `*SubscribeViewModelBlueskyTest`, `*BlueskyFeedMapperTest`, `*SubscribeViewModelSocialTest`):

```bash
cat > /Users/loitran/.claude/tmp-f29/bsky_t6_batch.sh <<'SH'
#!/bin/zsh
D=/Users/loitran/.claude/tmp-f29
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader
V=$J/ui/page/home/feed/subs/SubscribeViewModel.kt; MP=$J/infrastructure/social/bluesky/BlueskyFeedMapper.kt
cp -p $V $D/t6_V.kt; cp -p $MP $D/t6_M.kt
M="zsh $D/jvm_mut.sh"
T1='*SubscribeViewModelBlueskyTest'; T2='*BlueskyFeedMapperTest'; T3='*SubscribeViewModelSocialTest'
$=M base $V 'val oldRssUrl = ' 'val oldRssUrl = ' $T1 $T2 $T3
$=M n01-only-api $V 'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {' 'if (rssService.get().isFeedExist(feedUrl)) {' $T1 $T2 $T3
$=M n02-only-rss $V 'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {' 'if (rssService.get().isFeedExist(oldRssUrl)) {' $T1 $T2 $T3
$=M n03-and-not-or $V 'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {' 'if (rssService.get().isFeedExist(feedUrl) && rssService.get().isFeedExist(oldRssUrl)) {' $T1 $T2 $T3
$=M n04-rss-with-api-address $V 'val oldRssUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.rssUrl(blueskyActor)' 'val oldRssUrl = feedUrl' $T1 $T2 $T3
$=M n05-rss-uses-typed $V 'val oldRssUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.rssUrl(blueskyActor)' 'val oldRssUrl = "https://bsky.app/profile/" + typed + "/rss"' $T1 $T2 $T3
$=M n06-no-lowercase $MP 'val name = if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)' 'val name = trimmed' $T1 $T2 $T3
$=M n07-lowercase-did $MP 'val name = if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)' 'val name = trimmed.lowercase(Locale.ROOT)' $T1 $T2 $T3
$=M n08-no-trim $MP '        val trimmed = actor.trim()
        val name = if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)
        return RSS_PROFILE_PREFIX' '        val trimmed = actor
        val name = if (trimmed.startsWith(DID_PREFIX)) trimmed else trimmed.lowercase(Locale.ROOT)
        return RSS_PROFILE_PREFIX' $T1 $T2 $T3
$=M n09-no-suffix $MP 'return RSS_PROFILE_PREFIX + name + RSS_SUFFIX' 'return RSS_PROFILE_PREFIX + name' $T1 $T2 $T3
$=M n10-wrong-error $V 'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                lockLinkInput = false,' 'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {
                        _subscribeUiState.update {
                            it.copy(
                                title = androidStringsHelper.getString(R.string.subscribe),
                                errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                lockLinkInput = true,' $T1 $T2 $T3
cmp -s $D/t6_V.kt $V && cmp -s $D/t6_M.kt $MP && echo "final: identical" || echo "final: DIFFERENT"
echo ALLDONE
SH
chmod +x /Users/loitran/.claude/tmp-f29/bsky_t6_batch.sh
```
Trước khi chạy, **kiểm mỗi mẫu `old` xuất hiện đúng một lần** trong tệp tương ứng bằng một đoạn Python đếm (như đã làm ở 02g Task 4); `jvm_mut.sh` dừng với `PATTERN-MISSING` nếu `count < 1`, nhưng **không** bắt `count > 1`, nên phải kiểm trước (mẫu đột biến `n01`, `n02`, `n03`, `n10` chứa `|| ...isFeedExist(oldRssUrl)` nên chỉ khớp nhánh Bluesky sau Step 4, độc nhất). Khởi chạy: `( zsh $D/bsky_t6_batch.sh > $D/bsky_t6.log 2>&1; echo $? > $D/bsky_t6.exit ) > /dev/null 2>&1 &`, chờ `ALLDONE` **và** `.exit`; **không sửa hai tệp tới `ALLDONE`**.
Mong đợi: `base` xanh; `n01` đỏ (chiều RSS); `n02` đỏ (chiều API đã khóa); `n03` đỏ; `n04` đỏ (địa chỉ RSS sai: `bsky:`); `n05` đỏ (chữ gõ tay thay vì actor chuẩn); `n06` đỏ (handle hoa thường); `n07` đỏ (DID bị hạ chữ thường); `n08` đỏ nếu có test `rssUrl_trimsSpaces`; `n09` đỏ; `n10` đỏ (ô không mở lại). **Mutant sống sót ⇒ test yếu**: thêm test rồi chạy lại đúng mutant đó; không bỏ qua. Mutant tương đương (điều chỉnh 6) **không** có trong danh sách.

- [ ] **Step 7: Commit**

```bash
git status --short | grep -v idea
git diff --check
git diff --name-only -- app/src/main/res app/schemas app/src/main/AndroidManifest.xml app/build.gradle | wc -l
git add app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt \
  app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt \
  app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapperTest.kt \
  app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelBlueskyTest.kt
git commit -m "feat(front-02i): report a Bluesky account as already subscribed when it is here as the old RSS source or the API source

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Expected: lệnh `wc -l` ra `0`; một commit; cây sạch.

---

### Task 3: Full suite, lint, tài liệu, commit

**Files:**
- Modify: `doc/feature.md`, `doc/task/todo/FRONT-02_decentralized-web3-social.md`, `doc/task/done/FRONT-02g_bluesky-api_DONE.md`, `docs/superpowers/specs/2026-11-16-front-02i-bluesky-duplicate-guard-design.md`, `docs/superpowers/specs/2026-11-16-front-02g-bluesky-api-design.md` (hai dòng giới hạn).
- Create: `doc/task/done/FRONT-02i_bluesky-duplicate-guard_DONE.md`.

- [ ] **Step 1: Full suite JVM, assemble, lint (nền, ra file)**

```bash
D=/Users/loitran/.claude/tmp-f29; cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f $D/f32-full.exit $D/f32-assemble.exit $D/f32-lint.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > $D/f32-full.log 2>&1; echo $? > $D/f32-full.exit
  ./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f32-assemble.log 2>&1; echo $? > $D/f32-assemble.exit
  ./gradlew lintDevDebug > $D/f32-lint.log 2>&1; echo $? > $D/f32-lint.exit ) > /dev/null 2>&1 &
```
Chờ cả ba `.exit` bằng `0`. Đếm từ XML **theo cùng cách đã dùng ở 02g/02h** (tổng thuộc tính `tests` của mọi `*.xml` trong `app/build/test-results/testDevDebugUnitTest/`): kỳ vọng **3.708 + 4 (`rssUrl`) + 8 (chặn trùng) = 3.720**, 0 lỗi, 0 bỏ qua. Lint: 0 lỗi, **2.465** cảnh báo, 0 mục trong `bluesky/` và `SubscribeViewModel.kt` do thay đổi này. `git diff --stat -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` rỗng.
Nếu tổng khác 3.720: **không** viết con số kỳ vọng vào báo cáo; ghi số đo thật và tìm lý do bằng cách đếm test **năm lớp** đã đổi ở hai commit (như đã giải thích chênh 3 test ở 02h).

- [ ] **Step 2: Tài liệu (tiếng Việt, số thật)**

Báo cáo `FRONT-02i_bluesky-duplicate-guard_DONE.md` theo khuôn `FRONT-02g`: phạm vi; **nói thẳng**: chặn theo **tên cùng dạng** (handle với handle, DID với DID); **không** chặn handle so với DID của cùng một người; **không** chặn nguồn RSS nhập qua **OPML** hay dán tay địa chỉ RSS dạng khác (so sánh chuỗi đúng bằng, phân biệt hoa thường); **không** gộp hay dọn các cặp trùng đã tồn tại; chỉ tài khoản Local; **không chạm thiết bị**. Ghi **sửa spec**: chiều "API rồi RSS" đã được chặn từ trước (điều chỉnh 1), chỉ chiều RSS hở; vì vậy 4 trong 8 test đỏ ở bước đỏ và 4 test xanh ngay (khóa hành vi sẵn có, chứng minh bằng mutation `n02`). Ghi số test, mutation (số mutant, mutant sống sót và test thêm), tổng test, lint.
`FRONT-02g_bluesky-api_DONE.md`: ở mục "Nói thẳng về phạm vi" (dòng 15) bỏ câu "có thể đăng ký trùng" và thay bằng "đăng ký trùng một tài khoản ở dạng RSS và dạng API được chặn từ FRONT-02i (cùng dạng tên); còn lại: handle với DID, nguồn từ OPML"; ở mục "Chưa kiểm" (dòng 50) bỏ dòng "Hai nguồn trùng… không được cảnh báo" và trỏ tới FRONT-02i. `docs/superpowers/specs/2026-11-16-front-02g-bluesky-api-design.md` hai dòng giới hạn (28, 59): thêm "(đã xử lý ở FRONT-02i)". `doc/feature.md`: thêm mục FRONT-02i trước `## 🟡 In progress`; trong mục FRONT-02g sửa phần "**Nợ:** …cảnh báo đăng ký trùng RSS và API" thành "đăng ký trùng… đã xử lý ở FRONT-02i". `doc/task/todo/FRONT-02_decentralized-web3-social.md`: thêm một câu vào ghi chú đầu tệp.

- [ ] **Step 3: Commit tài liệu**

```bash
git diff --check
git add doc/feature.md doc/task/todo/FRONT-02_decentralized-web3-social.md doc/task/done/FRONT-02g_bluesky-api_DONE.md \
  doc/task/done/FRONT-02i_bluesky-duplicate-guard_DONE.md \
  docs/superpowers/specs/2026-11-16-front-02g-bluesky-api-design.md docs/superpowers/specs/2026-11-16-front-02i-bluesky-duplicate-guard-design.md
git commit -m "docs(front-02i): report blocking a second subscription to the same Bluesky account, and what it still lets through

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
git log --oneline -3
```
Expected: một commit; cây sạch; không push. Nếu `git diff --check` báo dòng trống cuối tệp: sửa rồi thêm vào **cùng** commit (đừng tạo hai commit trùng tiêu đề như ở 02h).

---

## Self-review

**Phủ spec.** (1) chuỗi sẵn có `already_subscribed`: Task 2 Step 4 (không đổi khối thông báo). (2) hai chiều, cùng dạng tên, không hỏi mạng: Task 1 (`rssUrl`) và Task 2 (hai địa chỉ), các test hoa thường, DID, handle khác handle. (3) kiểm ngay đầu nhánh trước khi gọi API: `coVerify(exactly = 0) { api.fetch(any()) }` trong ba test; thứ tự so với dòng `searching` là mutant tương đương, nêu ở điều chỉnh 7 và không đưa vào batch. (4) các ca kiểm chứng của spec (đã RSS, đã API, API lỗi, hoa thường lẫn lộn, DID giữ hoa thường, handle khác handle, không phải Local, Mastodon): ánh xạ thành **đúng 8 hàm test** (đã đếm trong Step 2); trong đó `bothAddressesAreAskedAbout_exactlyOnceEach_...` khóa thêm "mỗi địa chỉ đúng một lần" và hai ca Mastodon/không phải Local được gộp ở hai hàm riêng; hai test cũ (Social, Mastodon) chạy ở Task 2 Step 5. (5) mutation 5 loại của spec: thành `n01` đến `n10`. (6) cập nhật báo cáo 02g, `feature.md`, task gốc: Task 3. **Lệch spec, đã ghi ở "Điều chỉnh":** chiều "API rồi RSS" thực ra đã chặn từ trước; hàm `rssUrl` thay cho `socialUrl!!`.

**Quét placeholder.** Không còn "TBD"/"TODO"/"tương tự Task N". Chỗ có điều kiện thật có hướng xử lý: số test kỳ vọng ở Step 5/Task 3 có cách xử khi lệch; số test đỏ ở bước đỏ phải đúng 4, nếu khác thì dừng và đọc.

**Nhất quán kiểu và tên.** `BlueskyFeedMapper.rssUrl(actor: String): String` (Task 1) dùng ở `SubscribeViewModel` (Task 2) và mutant `n04..n09`; biến `oldRssUrl`/`feedUrl` khớp giữa mã đổi, batch và mẫu đột biến; `rssUrl` là tên cả của hàm mapper lẫn của biến `private val rssUrl` trong `SubscribeViewModelBlueskyTest` (hằng chuỗi của lớp test): hai thứ khác phạm vi, **không** va chạm vì test gọi `BlueskyFeedMapper.rssUrl(...)` có tiền tố đối tượng, và dùng biến `rssUrl` của lớp cho địa chỉ cố định.

**Rủi ro còn lại, nói thẳng.** (1) So sánh chuỗi đúng bằng nên nguồn từ OPML hay địa chỉ dạng khác vẫn trùng. (2) Thêm một lần truy vấn Room mỗi lần dán tài khoản Bluesky. (3) Không chạm thiết bị nên **không** có bằng chứng trên máy; chỉ test JVM với dịch vụ giả. (4) Hai mutant sống sót ở các lần trước đều là test yếu, nên plan giữ `n08` (trim) và `n10` (ô mở lại) để bắt kiểu đó sớm.
