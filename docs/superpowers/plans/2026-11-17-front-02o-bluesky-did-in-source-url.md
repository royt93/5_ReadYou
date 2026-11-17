# FRONT-02o — Nhớ DID của nguồn Bluesky mới để chặn đăng ký trùng cả khi chủ tài khoản đổi handle (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Nguồn Bluesky đăng ký mới lưu DID ở `sourceUrl` (giữ `url` là handle). Khi gõ một tên mà API trả về DID đã nằm ở `sourceUrl` của một nguồn có sẵn, báo "Đã đăng ký" dù `url` của nguồn đó là handle cũ.

**Architecture:** Ba mảnh nhỏ: (1) `FeedDao.queryBySourceUrl(accountId, sourceUrl)` (một truy vấn, không đổi bảng); (2) `AbstractRssRepository.isFeedSourceExist(sourceUrl)`; (3) trong `SubscribeViewModel.search()` nhánh `Found`: dựng `Feed` với `sourceUrl = feedUrl(found.did)` và thêm vào khối kiểm của 02n một lần hỏi `isFeedSourceExist(feedUrl(found.did))`.

**Tech Stack:** Room (DAO), Kotlin, JUnit4 + MockK + Robolectric (Room trong bộ nhớ). **Chỉ JVM, không chạm thiết bị nào.**

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **KHÔNG chạm thiết bị nào** (Pixel đang bận app khác của owner). Mọi kiểm chứng chạy trên máy tính.
- **Owner đã cho phép (2026-11-17) đúng một hàm truy vấn DAO mới** `queryBySourceUrl` (không đổi bảng, không migration, **không đổi `app/schemas`**, không đổi `@Database(version)`). Không thêm cột, không thêm chỉ mục (không đổi schema JSON). Không chuỗi, locale, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. Dùng chuỗi sẵn có `already_subscribed`.
- Chỉ đổi: `FeedDao.kt`, `AbstractRssRepository.kt`, `SubscribeViewModel.kt` (hai chỗ nhánh Bluesky) và test. Nhánh Nostr, khối 02i (trước API) và `LocalRssSv` **không đụng**.
- Gradle chạy NỀN, chờ `.exit`; đọc XML chỉ sau `.exit`. Đột biến: `jvm_mut.sh` qua **script Python** (bài học 02n: nháy trong shell làm hỏng cả batch), **một** batch nền, không sửa tệp tới `ALLDONE`, `cmp` cuối, **kiểm mẫu `old` độc nhất trước** (đã đếm lúc viết plan: dòng dựng Feed 1, `otherNames.any` 1, `isFeedExist` repo 1, `queryByLink` DAO 1).
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- R5: không magic number; không `!!`; không `late` không giải thích; mọi thứ gắn test; đóng `db` trong `@After`.
- Nói thẳng: nguồn cũ **không** được nâng cấp (ca đổi handle của nguồn cũ vẫn trùng); API lỗi vẫn không chặn; OPML; cặp trùng có sẵn; **`changeFeedUrl` chỉ đổi `url`, không đổi `sourceUrl`** (tật có sẵn, nay rõ hơn).
- Không tạo hai commit trùng tiêu đề (`git log --oneline | grep -c "<tiêu đề>"` phải là 0); `git diff --check` trước khi commit.

## Điều chỉnh so với spec (đọc mã thật, 2026-11-17)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "đổi chỗ dựng Feed ở `subscribe`/nhánh `Found`" | `Feed` được dựng ở `search()` (nhánh `Found`, dòng ~268-278) với `sourceUrl = feedUrl`; `subscribe()` chỉ `feed.copy(groupId, isNotification, isFullContent)` nên **giữ nguyên `sourceUrl`** | Chỉ đổi **một dòng** ở `search()`; không đụng `subscribe()` |
| 2 | Khối kiểm sau `Found` | 02n đã có `val otherNames = …otherNameAddresses(blueskyActor, found.did, found.handle)` rồi `if (otherNames.any { isFeedExist(it) })` | Thêm điều kiện `|| rssService.get().isFeedSourceExist(feedUrl(found.did))` **vào cùng `if`**, không khối mới |
| 3 | `isFeedExist` | `AbstractRssRepository.isFeedExist(url)` ở dòng 377, công khai, `feedDao.queryByLink(context.currentAccountId, url).isNotEmpty()` | `isFeedSourceExist` viết **y hệt**, đặt ngay sau nó |
| 4 | Khi tên gõ đã là DID | `otherNameAddresses` bỏ DID (trùng tên gõ), nhưng `isFeedSourceExist(feedUrl(did))` vẫn **hỏi `sourceUrl`**: ca "gõ DID, nguồn mới lưu `sourceUrl = DID` và `url = handle`" **trước đây không bị chặn** vì `url` là handle và tên gõ là DID, nên đây chính là ca được chặn thêm | Test riêng cho ca này |
| 5 | Chỉ mục | `Feed` chỉ có index `groupId`, `accountId`; truy vấn `queryBySourceUrl` lọc `accountId` (có index) rồi so `sourceUrl` | Không thêm index (sẽ đổi schema JSON); ghi giới hạn: quét theo tài khoản, nhanh với vài trăm nguồn |
| 6 | Test cũ khẳng định `sourceUrl` | `SubscribeViewModelBlueskyTest` dòng 117 `assertEquals("bsky:alice.bsky.social", state.feed?.sourceUrl)` | **Đổi nghĩa**: nay là DID (`bsky:did:plc:z72i…`); đọc từng test đỏ, sửa dữ liệu kỳ vọng, không nới thiết kế |
| 7 | Test thiết bị `BackgroundSyncRig` | Tự dựng nguồn thử với `sourceUrl` handle (dòng 93), **không** dùng `search()` | Không đổi (không chạm máy); ghi giới hạn |
| 8 | Khuôn test Room | `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java).allowMainThreadQueries().build()` (`WebFeedPreviewWidgetTest`, `LocalSyncRoomIntegrationTest`); Robolectric `@Config(sdk = [34], application = Application::class)` | Dùng đúng khuôn này cho test DAO |

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/domain/repository/FeedDao.kt` | thêm `queryBySourceUrl` |
| Modify `app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt` | thêm `isFeedSourceExist` |
| Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt` | `sourceUrl = feedUrl(found.did)` + thêm điều kiện vào `if` của 02n |
| Create `app/src/test/java/com/mckimquyen/reader/domain/repository/FeedDaoSourceUrlTest.kt` | test DAO (Room trong bộ nhớ) |
| Modify `app/src/test/.../subs/SubscribeViewModelBlueskyTest.kt` | test ViewModel (+ chỉnh dữ liệu test cũ nếu đổi nghĩa, điều chỉnh 6) |
| Tạm, **không commit**: `/Users/loitran/.claude/tmp-f29/o02_batch.py` | đột biến |
| Create `doc/task/done/FRONT-02o_bluesky-did-in-source-url_DONE.md`; Modify `doc/feature.md`, báo cáo 02n | tài liệu |

---

### Task 1: DAO `queryBySourceUrl` và `isFeedSourceExist` (TDD)

**Files:** Create `FeedDaoSourceUrlTest.kt`; Modify `FeedDao.kt`, `AbstractRssRepository.kt`.

**Interfaces (Produces):**
```kotlin
// FeedDao
suspend fun queryBySourceUrl(accountId: Int, sourceUrl: String): List<Feed>
// AbstractRssRepository
suspend fun isFeedSourceExist(sourceUrl: String): Boolean
```

- [ ] **Step 1: Viết test DAO đỏ** (6 test, `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34], application = Application::class)`; `Room.inMemoryDatabaseBuilder(..., AndroidDatabase::class.java).allowMainThreadQueries().build()`, `@After db.close()`). Mỗi feed cần một `Group` có `id` tương ứng (khóa ngoại): chèn `Group` trước bằng `db.groupDao().insert(...)` (đọc chữ ký thật trước khi viết):
  1. `findsTheSourceWhoseSourceUrlMatches`: feed `url = "bsky:old-handle"`, `sourceUrl = "bsky:did:plc:abc"`, `accountId = 1` ⇒ `queryBySourceUrl(1, "bsky:did:plc:abc")` có đúng 1 phần tử và `url` đúng.
  2. `doesNotMatchOnUrl`: `queryBySourceUrl(1, "bsky:old-handle")` ⇒ rỗng (chứng minh truy vấn đọc `sourceUrl`, không phải `url`).
  3. `doesNotCrossAccounts`: cùng `sourceUrl` ở `accountId = 2` ⇒ `queryBySourceUrl(1, …)` không thấy nguồn của tài khoản 2.
  4. `isExactNotPartial`: `sourceUrl = "bsky:did:plc:abc123"` ⇒ `queryBySourceUrl(1, "bsky:did:plc:abc")` rỗng.
  5. `isCaseSensitive`: `sourceUrl = "bsky:did:plc:AbC"` ⇒ `queryBySourceUrl(1, "bsky:did:plc:abc")` rỗng (DID phân biệt hoa/thường).
  6. `aNullSourceUrlNeverMatches`: feed có `sourceUrl = null` ⇒ không khớp bất kỳ chuỗi nào, không ném lỗi.
  Chạy đỏ (biên dịch hỏng vì thiếu hàm là đỏ đúng lý do): `./gradlew :app:testDevDebugUnitTest --tests '*FeedDaoSourceUrlTest'` (nền).
- [ ] **Step 2: Thêm vào `FeedDao.kt`** (ngay sau `queryByLink`):

```kotlin
    @Query(
        """
        SELECT * FROM feed
        WHERE accountId = :accountId
        and sourceUrl = :sourceUrl
        """
    )
    suspend fun queryBySourceUrl(accountId: Int, sourceUrl: String): List<Feed>
```
  và vào `AbstractRssRepository.kt` (ngay sau `isFeedExist`):

```kotlin
    suspend fun isFeedSourceExist(sourceUrl: String): Boolean = feedDao.queryBySourceUrl(context.currentAccountId, sourceUrl).isNotEmpty()
```
- [ ] **Step 3: Chạy xanh** (6 test DAO). Kiểm `git diff --stat -- app/schemas` rỗng (Room xuất schema khi biên dịch: **thêm một `@Query` không đổi schema JSON**; nếu `app/schemas` đổi thì dừng và đọc lý do, không commit).
- [ ] **Step 4: Mutation** (script Python, một batch): bỏ `accountId = :accountId` trong truy vấn; đổi `sourceUrl = :sourceUrl` thành `url = :sourceUrl`; đổi `=` thành `LIKE` với `%`. Mỗi mutant phải có test DAO đỏ.
- [ ] **Step 5: Commit** (ba tệp: DAO, repo, test): `feat(front-02o): look a feed up by the address it is synced from`.

---

### Task 2: Lưu DID và kiểm theo DID trong `search()` (TDD)

**Files:** Modify `SubscribeViewModel.kt`, `SubscribeViewModelBlueskyTest.kt`.

- [ ] **Step 1: Viết test đỏ** (thêm vào `SubscribeViewModelBlueskyTest`; `setUp` mặc định `isFeedExist(any()) returns false` và `relaxed` mock nên `isFeedSourceExist(any())` cũng trả `false` (kiểm bằng đọc `mockk(relaxed = true)` ở dòng 62: `localService` relaxed ⇒ Boolean mặc định `false`; **đã xác nhận** bằng đọc `private val localService = mockk<LocalRssSv>(relaxed = true)`)):
  1. `aNewBlueskySource_storesTheDidAsItsSyncAddress_andKeepsTheHandleAsItsUrl`: `foundAs(DID, HANDLE, post…)`, gõ `@alice.bsky.social` ⇒ `state.feed.url == "bsky:alice.bsky.social"` và `state.feed.sourceUrl == "bsky:${DID}"`.
  2. `aRenamedHandle_isStillCaughtThroughTheStoredDid`: `isFeedSourceExist("bsky:${DID}")` ⇒ `true` (nguồn cũ có `url` là handle cũ); gõ **handle mới** `@alice-new.bsky.social`, `api.fetch("alice-new.bsky.social") = foundAs(DID, "alice-new.bsky.social", post…)` ⇒ `assertReportedAsAlreadySubscribed()` (ca mục tiêu).
  3. `aDidTypedAfterASourceStoredThatDid_isCaught`: `isFeedSourceExist("bsky:${DID}")` ⇒ `true`; gõ `https://bsky.app/profile/${DID}`, `api.fetch(DID) = foundAs(DID, HANDLE, …)` ⇒ báo trùng (điều chỉnh 4).
  4. `anOldSourceWithTheHandleInBothColumns_isNotCaughtAfterARename_aKnownLimit`: không có nguồn nào ở `sourceUrl = DID`; nguồn cũ `url = sourceUrl = "bsky:old-handle"` (không mock `isFeedExist` trả `true` cho DID hay handle mới) ⇒ gõ handle mới, đăng ký **thành công** (ghi nhận giới hạn bằng test).
  5. `aDifferentDid_isNotBlocked`: `isFeedSourceExist("bsky:${DID}")` ⇒ `true`; gõ bob với `foundAs(OTHER_DID, "bob.bsky.social", post(did = OTHER_DID))` ⇒ không chặn, `state.feed.url == "bsky:bob.bsky.social"`.
  6. `theDidSourceIsAskedOnce_andOnlyWhenTheApiAnswered`: `coVerify(exactly = 1) { localService.isFeedSourceExist("bsky:${DID}") }` sau một lần đăng ký thành công; ở ca API `Failed`, `coVerify(exactly = 0) { localService.isFeedSourceExist(any()) }`.
  7. `aMastodonOrNostrLink_neverAsksAboutSourceUrl`: `coVerify(exactly = 0) { localService.isFeedSourceExist(any()) }` với một ca Mastodon (khuôn test 02i có sẵn) và không đổi.
  Test cũ dòng 117 (`sourceUrl == "bsky:alice.bsky.social"`) **sẽ đỏ**: đọc lý do, đổi kỳ vọng thành DID **trong cùng commit** (điều chỉnh 6). **Bước đỏ phải đỏ đúng lý do:** dự kiến test 1, 2, 3 đỏ, test cũ dòng 117 đỏ; 4, 5, 6 (ca `Failed`), 7 xanh ngay; ca "một lần hỏi" của test 6 đỏ. Số khác dự kiến thì **dừng, đọc lý do từng test, không sửa mã khi chưa hiểu**.
- [ ] **Step 2: Sửa mã** (hai chỗ, mẫu đã đếm độc nhất):
  a. Dòng dựng `Feed` ở nhánh `Found`: thay `sourceUrl = feedUrl,` (trong khối `Feed(id = feedId, … sourceType = Feed.BLUESKY_SOURCE_TYPE, …)`) bằng `sourceUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did),` kèm một dòng chú thích: `// The DID outlives a handle change; url stays the typed name.`
  b. `if (otherNames.any { rssService.get().isFeedExist(it) }) {` thành `if (otherNames.any { rssService.get().isFeedExist(it) } || rssService.get().isFeedSourceExist(com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did))) {`.
  `git diff -U0` phải cho thấy **2 dòng đổi và 1 dòng chú thích thêm** ở `SubscribeViewModel.kt`, không xóa nhánh Nostr hay khối 02i.
- [ ] **Step 3: Chạy xanh** `BlueskyTest`, `MapperTest`, `SocialTest`, `NostrTest`, `DialogTypingTest`, `FeedDaoSourceUrlTest`, `LocalRssSvBlueskySyncTest` (số thật từ XML).
- [ ] **Step 4: Mutation** (script Python, một batch): `sourceUrl = feedUrl(found.did)` về `feedUrl` (tên gõ); bỏ điều kiện `|| isFeedSourceExist(...)`; đổi `||` thành `&&`; dùng `isFeedExist` thay `isFeedSourceExist` cho DID (sai cột); dùng `found.handle` thay `found.did` trong hai chỗ. Mỗi mutant phải có test đỏ.
- [ ] **Step 5: Commit** (mã và test): `feat(front-02o): remember the DID of a new Bluesky source and catch a renamed handle through it`.

---

### Task 3: Kiểm cuối, báo cáo, tài liệu

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền): **số đo thật** (cộng `tests` từ mọi XML như 02g đến 02n; kỳ vọng 3.751 + 6 + số test ViewModel mới, không hứa con số), 0 lỗi; `:app:lintDevDebug` 0 lỗi (ghi số cảnh báo thật); `assembleDevDebug` thành công.
- [ ] **Step 2:** `git diff --stat <commit trước 02o> HEAD -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` **rỗng**; `git diff -U0 -- SubscribeViewModel.kt` đúng như Task 2 Step 2.
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02o_bluesky-did-in-source-url_DONE.md` (việc đã làm, bước đỏ thật, số test thật, mutant và test đỏ thật, điều chỉnh khi thực thi, **điều chưa chặn**); cập nhật `doc/feature.md` (R2) và các dòng "chủ tài khoản đã đổi handle" trong báo cáo 02n: nay chặn được **đối với nguồn đăng ký từ sau 02o**; nguồn cũ vẫn chưa.
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02o): report …`.

---

## Self-review

1. **Spec coverage:** lưu DID ở `sourceUrl` (Task 2 test 1) → chặn ca đổi handle (test 2) → chặn khi gõ DID (test 3); nguồn cũ không nâng cấp, có test ghi lại giới hạn (test 4); hàm DAO một truy vấn, đúng tài khoản, đúng từng ký tự, phân biệt hoa/thường, `null` không khớp (Task 1); `app/schemas` không đổi (Task 1 Step 3, Task 3 Step 2); đồng bộ không đổi (`LocalRssSvBlueskySyncTest` xanh); mutation, full suite, lint, assemble, tài liệu → Task 1, 2, 3.
2. **Placeholder scan:** không TBD; mọi bước có mã hoặc lệnh. Điều chưa chắc (chữ ký `groupDao().insert`; test cũ nào đổi nghĩa; `app/schemas` có đổi khi thêm `@Query` không) đều có bước đọc/dừng, kèm dự kiến cụ thể.
3. **Nhất quán tên:** `queryBySourceUrl`, `isFeedSourceExist`, `FeedDaoSourceUrlTest`, `foundAs`, `otherNames`, `already_subscribed`; số test DAO 6 và ViewModel 7 khớp danh sách.
4. **Rủi ro nhận thấy khi rà:** (a) Room có thể ghi lại `app/schemas/.../22.json` khi biên dịch dù chỉ thêm một `@Query`: kiểm bắt buộc ở Task 1 Step 3, và nếu đổi thì dừng; (b) `localService` là `relaxed` nên `isFeedSourceExist` mặc định `false` không cần mock cho các test cũ: đã đối chiếu dòng 62; (c) `changeFeedUrl` chỉ đổi `url` nên một nguồn mới sau khi người dùng sửa địa chỉ vẫn đồng bộ bằng DID cũ: ghi vào giới hạn, không sửa ở đây; (d) test 6 hai nửa (`exactly = 1` khi thành công, `exactly = 0` khi API lỗi) gom hai ý trong một test: tách thành hai test nếu khó đọc khi viết.
