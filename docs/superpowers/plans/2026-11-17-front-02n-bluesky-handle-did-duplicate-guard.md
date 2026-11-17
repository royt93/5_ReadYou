# FRONT-02n — Chặn đăng ký trùng một tài khoản Bluesky khi một bên gõ handle, một bên gõ DID (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Sau khi API Bluesky trả `Found`, nếu tên **thứ hai** của cùng người (DID khi gõ handle, handle khi gõ DID) đã có nguồn ở dạng API hoặc RSS cũ, báo "Đã đăng ký" và không thêm nguồn.

**Architecture:** Một hàm thuần `BlueskyFeedMapper.otherNameAddresses(typedActor, did, handle)` trả các địa chỉ cần kiểm (bỏ tên đã gõ, lọc handle không đáng tin). Trong `SubscribeViewModel.search()`, ngay sau `val found = outcome.result`, hỏi `isFeedExist` cho từng địa chỉ; có bất kỳ cái `true` thì báo `already_subscribed`. 02i (kiểm hai địa chỉ **trước** API) giữ nguyên.

**Tech Stack:** Kotlin, JUnit4 + MockK + Robolectric (JVM). **Chỉ JVM, không chạm thiết bị nào.**

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **KHÔNG chạm thiết bị nào** (Pixel đang bận app khác của owner; việc này không cần máy). Mọi kiểm chứng chạy trên máy tính.
- Chỉ đổi `SubscribeViewModel.search()` (nhánh Bluesky, **sau** `val found = outcome.result`) và thêm **một hàm thuần** vào `BlueskyFeedMapper`. Dùng chuỗi **sẵn có** `R.string.already_subscribed`. **Không** chuỗi mới, locale, Room, DataStore, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. Nhánh Nostr (`isFeedExist(feedUrl)` ở dòng ~144) và khối 02i **không đụng**.
- Gradle chạy NỀN, chờ `.exit`; đọc XML chỉ sau `.exit`. Đột biến: `jvm_mut.sh` trong **một** batch nền, không sửa tệp tới `ALLDONE`, `cmp` sau batch, **kiểm mẫu `old` độc nhất trước** (đã đếm lúc viết plan: khối 02i 1, `val found = outcome.result` 1, hàm `feedUrl` 1).
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- R5: không magic number (hằng số có tên: `handle.invalid`); không `!!`; không `late` không giải thích; mọi thứ gắn test; không rò rỉ.
- Nói thẳng phần không chặn: API lỗi hoặc `Found` rỗng rơi về RSS; nguồn đã lưu bằng handle **cũ** khi chủ tài khoản đã đổi handle; OPML hoặc địa chỉ dạng khác; cặp trùng có sẵn; một máy chủ độc trả handle giả có thể chặn **một lần** đăng ký.
- Không tạo hai commit trùng tiêu đề (`git log --oneline | grep -c "<tiêu đề>"` phải là 0); `git diff --check` trước khi commit.

## Điều chỉnh so với spec (đọc mã thật, 2026-11-17)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "kiểm 4 địa chỉ" | Tên **đã gõ** đã được kiểm ở 02i (trước API), nên chỉ còn **2 địa chỉ** của tên thứ hai (API và RSS) là mới; "4" tính cả hai tên | Hàm trả tối đa **2** địa chỉ (hoặc 0 khi không có tên thứ hai hợp lệ); test kiểm đúng số này |
| 2 | Lọc `handle.invalid` | `BlueskyApiClient` chỉ kiểm `did` (`startsWith("did:") && isActor`); `handle = profile.optString("handle")` **không kiểm** (có thể `""`, `handle.invalid`, hoặc chuỗi lạ) | Hàm lọc: rỗng, `handle.invalid` (không phân biệt hoa thường), `!isActor`, bắt đầu bằng `did:` |
| 3 | "tên trùng tên gõ" | Tên gõ `blueskyActor` có thể hoa/thường lẫn lộn; `feedUrl` chuẩn hóa handle chữ thường | So **sau chuẩn hóa** (`feedUrl(name) == feedUrl(typed)`), không so chuỗi thô |
| 4 | Test `bothAddressesAreAskedAbout_exactlyOnceEach_andNothingElseIsAsked` (02i) | Dòng 312–317: `found(...)` trả `did = DID` (`did:plc:z72i…`) và `handle = HANDLE` (= `alice.bsky.social`); tên gõ `alice.bsky.social` | **Thêm hai lần hỏi** (`feedUrl(DID)`, `rssUrl(DID)`); test này chỉ kiểm hai địa chỉ của tên gõ bằng `exactly = 1`, nên **không đổi nghĩa**, nhưng ca "không hỏi gì thêm" phải đọc lại: viết **một test mới** thay vì sửa test 02i |
| 5 | Test 02i dùng `found(...)` với `HANDLE`/`DID` cố định | `aDifferentHandle_isNotBlockedByAnotherAccountsSource` (dòng 302–310): tên gõ `bob.bsky.social` nhưng `found` trả `did`/`handle` của **alice**, và có sẵn nguồn alice ⇒ hàm mới sẽ **chặn** nhầm (đúng theo thiết kế, vì API nói đây là alice) | Test này **đổi được nghĩa**: phải sửa dữ liệu `found` thành bob (hoặc nhận rằng kết quả mới là báo trùng) trong **cùng** commit, ghi vào "Điều chỉnh khi thực thi", **không** nới thiết kế để giữ test cũ xanh |
| 6 | `isFeedExist(any()) returns false` ở `setUp` | Dòng 81 mặc định `false` cho mọi địa chỉ | Ca "không có nguồn" tự xanh; mỗi test chặn đặt `true` đúng địa chỉ cần |
| 7 | Mutation `bỏ lọc tên trùng tên gõ` | Nếu không lọc, tên gõ bị kiểm **lần thứ hai** (đã kiểm ở 02i) | Test đếm `exactly = 1` cho địa chỉ tên gõ bắt được mutant này |

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyFeedMapper.kt` | thêm `otherNameAddresses` + hằng số `INVALID_HANDLE` |
| Modify `app/src/test/.../bluesky/BlueskyFeedMapperTest.kt` | test hàm thuần |
| Modify `app/src/main/.../ui/page/home/feed/subs/SubscribeViewModel.kt` | khối kiểm sau `Found` |
| Modify `app/src/test/.../subs/SubscribeViewModelBlueskyTest.kt` | test ViewModel (+ chỉnh dữ liệu test 02i nếu cần, điều chỉnh 5) |
| Tạm, **không commit**, `/Users/loitran/.claude/tmp-f29/`: `n02_batch.sh` | batch đột biến |
| Create `doc/task/done/FRONT-02n_bluesky-handle-did-duplicate-guard_DONE.md`; Modify `doc/feature.md`, báo cáo 02i (giới hạn), spec 02i nếu nhắc | tài liệu |

---

### Task 1: Hàm thuần `otherNameAddresses` (TDD)

**Files:** Modify `BlueskyFeedMapper.kt`, `BlueskyFeedMapperTest.kt`.

**Interfaces (Produces):**
```kotlin
fun otherNameAddresses(typedActor: String, did: String, handle: String): List<String>
```
Trả `[feedUrl(name), rssUrl(name)]` cho **mỗi tên khác tên đã gõ và hợp lệ** (DID, rồi handle), không trùng, theo thứ tự cố định: DID trước, handle sau; mỗi tên: API rồi RSS.

- [ ] **Step 1: Viết test đỏ** (thêm vào `BlueskyFeedMapperTest`; nhập `BlueskyFeedMapper`), 9 test:
  1. `typedHandle_gives_theDidAddresses`: `otherNameAddresses("alice.bsky.social", "did:plc:abc123", "alice.bsky.social")` = `["bsky:did:plc:abc123", "https://bsky.app/profile/did:plc:abc123/rss"]`.
  2. `typedDid_gives_theHandleAddresses_lowerCased`: tên gõ `did:plc:abc123`, handle `Alice.Bsky.Social` ⇒ `["bsky:alice.bsky.social", "https://bsky.app/profile/alice.bsky.social/rss"]`.
  3. `aTypedHandleInMixedCase_isNotAskedAgain`: tên gõ `ALICE.bsky.social`, handle `alice.bsky.social`, DID `did:plc:abc123` ⇒ chỉ địa chỉ của DID (handle chuẩn hóa trùng tên gõ nên bị bỏ).
  4. `aTypedDid_isNotAskedAgain_andKeepsItsCase`: tên gõ `did:plc:AbC123`, DID `did:plc:AbC123`, handle `alice.bsky.social` ⇒ chỉ địa chỉ handle (DID trùng tên gõ bị bỏ; DID không bị hạ chữ thường).
  5. `anInvalidHandle_isIgnored`: handle `handle.invalid` (và `HANDLE.INVALID`), `""`, `"did:plc:other"`, `"-bad"`, `"a b"` ⇒ chỉ còn địa chỉ của DID (tên gõ là handle khác) hoặc rỗng khi tên gõ là DID.
  6. `aTypedDidWithAnInvalidHandle_givesNothing`: tên gõ `did:plc:abc123`, DID cùng, handle `handle.invalid` ⇒ `[]`.
  7. `theTwoNamesOfOnePerson_giveAtMostTwoAddresses_andNoDuplicates`: tên gõ một chuỗi khác cả hai (ví dụ alias) ⇒ 4 địa chỉ? **Không**: chỉ DID và handle (hai tên), mỗi tên API và RSS = **4**; ghi test này để cố định số tối đa là 4 khi tên gõ khác cả hai (ca hiếm), và kiểm không lặp.
  8. `aDidThatIsNotValid_isIgnored`: DID rỗng hoặc không bắt đầu `did:` ⇒ bỏ.
  9. `theAddressesUseTheSameSpellingAsTheStoredSources`: `otherNameAddresses(...)` chứa đúng `feedUrl(did)` và `rssUrl(did)` (so với hai hàm có sẵn), chặn lệch chính tả.
  Chạy đỏ (biên dịch hỏng vì chưa có hàm là đỏ đúng lý do): `./gradlew :app:testDevDebugUnitTest --tests '*BlueskyFeedMapperTest'` (nền, đọc XML sau `.exit`).
- [ ] **Step 2: Viết hàm tối thiểu** (thêm sau `feedUrl`; hằng số `INVALID_HANDLE = "handle.invalid"` cạnh `DID_PREFIX`):

```kotlin
/** Addresses under which an already stored source of the SAME account could look under its OTHER name; the typed name is excluded (it was checked before the API call). */
fun otherNameAddresses(typedActor: String, did: String, handle: String): List<String> {
    val typed = feedUrl(typedActor)
    val names = buildList {
        if (did.startsWith(DID_PREFIX) && isActor(did)) add(did)
        if (isActor(handle) && !handle.startsWith(DID_PREFIX) && !handle.equals(INVALID_HANDLE, ignoreCase = true)) add(handle)
    }
    return names.filter { feedUrl(it) != typed }.distinctBy { feedUrl(it) }.flatMap { listOf(feedUrl(it), rssUrl(it)) }
}
```
- [ ] **Step 3: Chạy xanh** (33 + 9 = 42 test ở `BlueskyFeedMapperTest`; đọc số thật từ XML).
- [ ] **Step 4: Mutation JVM nhỏ cho hàm** (script nền `jvm_mut.sh`, mẫu độc nhất, `cmp` cuối): bỏ lọc `INVALID_HANDLE`; bỏ `ignoreCase`; bỏ `!handle.startsWith(DID_PREFIX)`; bỏ `filter` tên gõ; bỏ `distinctBy`; đổi thứ tự `listOf(rssUrl, feedUrl)`; đổi `isActor(did)` thành `true`. Mỗi mutant phải có test đỏ; mutant sống sót thì thêm test rồi chạy lại **đúng mutant đó**.
- [ ] **Step 5: Commit** (chỉ `BlueskyFeedMapper.kt` và test của nó): `feat(front-02n): list the addresses under which the other name of a Bluesky account could be stored`.

---

### Task 2: Khối kiểm trong `search()` (TDD)

**Files:** Modify `SubscribeViewModel.kt`, `SubscribeViewModelBlueskyTest.kt`.

- [ ] **Step 1: Viết test đỏ** (thêm vào `SubscribeViewModelBlueskyTest`), cần helper `foundAs(did, handle, vararg items)` (tạo `BlueskyOutcome.Found(BlueskyFetchResult(array(*items), did, handle, "Alice A", avatar))` rồi dùng cho các test mới; `found(...)` cũ giữ nguyên):
  1. `aHandleTypedAfterTheDidWasSubscribedAsApi_isReported`: có sẵn `bsky:did:plc:z72i…` (`isFeedExist` true); `api.fetch("alice.bsky.social")` ⇒ `foundAs(DID, HANDLE)`; `search("@alice.bsky.social")` ⇒ `assertReportedAsAlreadySubscribed()`, `coVerify(exactly = 1) { api.fetch("alice.bsky.social") }`, `state.feed == null`.
  2. `aHandleTypedAfterTheDidWasSubscribedAsRss_isReported`: có sẵn `https://bsky.app/profile/did:plc:z72i…/rss`.
  3. `aDidTypedAfterTheHandleWasSubscribedAsApi_isReported`: có sẵn `bsky:alice.bsky.social`; gõ `https://bsky.app/profile/did:plc:z72i…`; `api.fetch(DID)` ⇒ `foundAs(DID, HANDLE)`.
  4. `aDidTypedAfterTheHandleWasSubscribedAsRss_isReported`: có sẵn RSS của handle.
  5. `anInvalidHandleFromTheServer_isNotUsedToBlock`: có sẵn `bsky:handle.invalid` và RSS tương ứng (`isFeedExist` true cho chúng); API trả `foundAs(DID, "handle.invalid")`; gõ DID ⇒ **không** chặn, đăng ký bình thường (`state.feed != null`).
  6. `whenNothingIsStored_theSecondNameChangesNothing`: `isFeedExist` mặc định `false`; gõ handle ⇒ xem trước bình thường, `coVerify(exactly = 1)` cho từng địa chỉ DID, `exactly = 1` cho địa chỉ **tên gõ** (không hỏi lại).
  7. `anApiFailure_stillFallsBackToRss_andTheSecondNameIsNotAsked`: `api.fetch` ⇒ `Failed`; có sẵn nguồn theo DID; gõ handle ⇒ **không** báo trùng ở nhánh này (hành vi giới hạn đã ghi), `rssHelper.searchFeed` được gọi; **ghi test này như bằng chứng giới hạn**.
  8. `aNostrOrMastodonLink_doesNotAskAboutBlueskyNames`: (đã có test Mastodon `theAlreadySubscribedCheck_usesTheRssAddress`; thêm một kiểm `coVerify(exactly = 0) { isFeedExist(match { it.startsWith("bsky:") }) }`).
  9. `aSecondNameThatIsAlreadyStored_blocksWithoutBuildingAPreview`: có sẵn địa chỉ thứ hai ⇒ `state.articles` rỗng, `state.linkContent` là chuỗi đã gõ (không bị ghi đè bởi `feedUrl`).
  Đồng thời **kiểm các test 02i**: chạy cả lớp; test nào đỏ vì dữ liệu `found` (điều chỉnh 5: `aDifferentHandle_isNotBlockedByAnotherAccountsSource`) thì **dừng và đọc từng cái**, xác nhận đỏ là do thiết kế mới (API nói tên gõ là alice) chứ không do lỗi; rồi sửa **dữ liệu test** (`found` thành bob) cùng commit và ghi vào "Điều chỉnh khi thực thi". **Bước đỏ phải đỏ đúng lý do:** dự kiến test 1–4 và 9 đỏ (chưa có khối), 5–8 xanh ngay; nếu số khác, dừng, đọc lý do từng test, không sửa mã khi chưa hiểu.
- [ ] **Step 2: Chèn khối** ngay sau `val found = outcome.result` (mẫu đã đếm độc nhất):

```kotlin
                        // The API told us the account's other name: a source stored under it (as API or as the old RSS form) is the same account.
                        val otherNames = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
                            .otherNameAddresses(blueskyActor, found.did, found.handle)
                        if (otherNames.any { rssService.get().isFeedExist(it) }) {
                            _subscribeUiState.update {
                                it.copy(
                                    title = androidStringsHelper.getString(R.string.subscribe),
                                    errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                                    lockLinkInput = false,
                                )
                            }
                            return@launch
                        }
```
  `git diff` phải cho thấy **một khối thêm**, không xóa dòng nào ở 02i hay nhánh Nostr.
- [ ] **Step 3: Chạy xanh** `BlueskyTest`, `MapperTest`, `SocialTest`, `NostrTest`, `DialogTypingTest` (đọc số thật từ XML, không đoán).
- [ ] **Step 4: Mutation** (một batch nền): bỏ khối (`if (otherNames.any {…})` thành `if (false)`); `any` thành `all`; chỉ hỏi địa chỉ đầu (`otherNames.take(1)`); đặt khối **trước** `val found` (sai vị trí, nên thay bằng kiểm trên `blueskyActor`); thay `already_subscribed` bằng chuỗi khác; `lockLinkInput = true`. Mỗi mutant phải có test đỏ.
- [ ] **Step 5: Commit** (mã và test): `feat(front-02n): block a Bluesky account typed as a handle after its DID, or the reverse, once the API names both`.

---

### Task 3: Kiểm cuối, báo cáo, tài liệu

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền): ghi **số đo thật**, đếm cùng cách như 02g–02m (cộng `tests` từ mọi XML; kỳ vọng 3.731 + 9 + số test ViewModel mới, **không** hứa con số); 0 lỗi. Lint `:app:lintDevDebug` 0 lỗi (ghi số cảnh báo thật); `assembleDevDebug` thành công.
- [ ] **Step 2:** `git diff --stat <commit trước 02n> HEAD -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` rỗng; `git diff -U0 -- SubscribeViewModel.kt` chỉ **một khối thêm**.
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02n_bluesky-handle-did-duplicate-guard_DONE.md` (việc đã làm, bước đỏ thật, số test thật, mutant và test đỏ thật, điều chỉnh khi thực thi, **điều chưa chặn**); cập nhật `doc/feature.md` (R2); cập nhật các dòng "handle với DID" trong báo cáo 02i và bản tổng kết `FRONT-02i-02m_wave-summary_DONE.md` (nay chặn được khi API trả lời; vẫn chưa: API lỗi, đổi handle, OPML, cặp trùng có sẵn).
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02n): report …`.

---

## Self-review

1. **Spec coverage:** hàm thuần + lọc handle không đáng tin → Task 1; khối kiểm sau `Found` đủ hai chiều và hai dạng lưu → Task 2; ca API lỗi (không chặn) có test ghi lại → Task 2 test 7; số lần hỏi `isFeedExist` đúng → test 6; mutation, full suite, lint, assemble, tài liệu → Task 1, 2, 3. Giới hạn "đổi handle" và "máy chủ độc chặn một lần đăng ký" ghi trong Global Constraints và báo cáo.
2. **Placeholder scan:** không TBD; mọi bước có mã hoặc lệnh. Điều chưa chắc (test 02i nào đổi nghĩa) có bước dừng đọc từng test, kèm dự kiến cụ thể (điều chỉnh 5).
3. **Nhất quán tên:** `otherNameAddresses`, `INVALID_HANDLE`, `foundAs`, `otherNames`, `already_subscribed`; số test hàm thuần 9 khớp danh sách; `feedUrl`/`rssUrl` dùng đúng nghĩa của 02i.
4. **Rủi ro nhận thấy khi rà:** (a) test thứ 7 của hàm thuần (tên gõ khác cả hai tên) cho tối đa **4** địa chỉ chứ không phải 2: đã sửa lại câu chữ điều chỉnh 1 ("tối đa 2 khi tên gõ là một trong hai tên; 4 khi khác cả hai"); (b) test 02i `aDifferentHandle_…` sẽ đổi nghĩa vì `found` mặc định trả alice (điều chỉnh 5) nên phải đọc từng test đỏ chứ không đoán; (c) `foundAs` mới không được ảnh hưởng 13 chỗ dùng `found(...)`; (d) máy chủ độc có thể trả `handle` của người khác để chặn một lần đăng ký: đã ghi vào giới hạn, không có cách chặn khi không hỏi thêm mạng.
