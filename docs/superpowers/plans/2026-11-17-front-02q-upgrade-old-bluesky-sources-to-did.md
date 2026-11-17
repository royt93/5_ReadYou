# FRONT-02q — Nâng cấp nguồn Bluesky cũ lên lưu DID khi đồng bộ thành công (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Khi một nguồn Bluesky có địa chỉ đồng bộ chưa phải DID đồng bộ thành công, ghi `bsky:<DID>` (DID lấy từ câu trả lời API vừa có) vào cột `sourceUrl`, bằng một câu `UPDATE` có điều kiện, không đổi cột nào khác và không làm hỏng đồng bộ.

**Architecture:** (1) `FeedDao.updateSourceUrlIfUnchanged(id, expectedSourceUrl, newSourceUrl): Int`; (2) trong `LocalRssSv.queryBlueskyArticles`, **sau** kiểm "có bài", gọi nó trong `runCatching` (ném lại `CancellationException`).

**Tech Stack:** Room (DAO), Kotlin, JUnit4 + MockK + Robolectric. **Chỉ JVM, không chạm thiết bị nào.**

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **KHÔNG chạm thiết bị nào** (Pixel đang bận app khác của owner).
- Owner đã duyệt (2026-11-17): cách làm này, **một** hàm DAO `UPDATE` có điều kiện, và chấp nhận rủi ro DID (đã đính chính ở spec: rủi ro **đã có từ 02o** cho nguồn mới, 02q mở rộng sang nguồn cũ).
- Chỉ đổi `FeedDao.kt` và `LocalRssSv.kt` (một khối trong `queryBlueskyArticles`) và test. **Không** đổi `url`, `name`, `icon`, `groupId`, `isNotification`, `isFullContent`; không đổi `queryNostrArticles`, `SubscribeViewModel`, `FeedOptionVM`. Không chuỗi, locale, `app/schemas` (băm `22.json` phải giữ `45d76f04…`), manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP.
- **Không log handle, DID, URL hay tên.** Tốt nhất không log gì.
- Gradle chạy NỀN, chờ `.exit`; đọc XML chỉ sau `.exit`. Đột biến: `jvm_mut.sh` qua **script Python**, **một** batch nền, không sửa tệp tới `ALLDONE`, `cmp` cuối, **kiểm mẫu `old` độc nhất trước** (đã đếm: `if (articles.isEmpty()) throw … return articles` 1, khối `fetch` 1).
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- R5: không magic number; không `!!`; không `lateinit` không giải thích; `db.close()` trong `@After`.
- Nói thẳng: nguồn đã đổi handle trước khi nâng cấp thì không nâng cấp được (đồng bộ lỗi `NotFound`).
- Không tạo hai commit trùng tiêu đề; `git diff --check` trước khi commit.

## Điều chỉnh so với spec (đọc mã thật, 2026-11-17)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "bọc `runCatching`, ném lại `CancellationException`" | `LocalRssSv.kt` chưa import `CancellationException` (không có kết quả `grep`) | Dùng tên đầy đủ `kotlinx.coroutines.CancellationException` (cùng kiểu ở `SubscribeViewModel`) hoặc thêm import; không đổi hành vi |
| 2 | Test đồng bộ có sẵn (`LocalRssSvBlueskySyncTest`) | `feedDao = mockk(relaxed = true)` **viết thẳng trong constructor** (dòng ~40), không có biến để `coVerify` | Tách `private val feedDao = mockk<FeedDao>(relaxed = true)` và truyền vào; `relaxed` nên `updateSourceUrlIfUnchanged` trả `0` mặc định (đủ cho test cũ) |
| 3 | Dữ liệu `found(...)` của test | `found()` trả `did = BlueskyTestPayloads.DID`, bài của DID đó; `feed` mặc định `url = sourceUrl = "bsky:alice.bsky.social"` | Ca "nguồn cũ" dùng đúng `feed` có sẵn: kỳ vọng `updateSourceUrlIfUnchanged(feed.id, "bsky:alice.bsky.social", "bsky:${DID}")` |
| 4 | "nguồn `sourceUrl == null`" | `Feed.sourceUrl` mặc định `null`; đồng bộ dùng `sourceUrl ?: url` | Với `sourceUrl = null`: `expected = null` (giá trị cột thật), **không** phải `url`; test kiểm đúng điều này (đây là lý do dùng `IS`) |
| 5 | Ca "Room thật" xuyên module | `LocalRssSv` thật cần nhiều mock (như `AbstractRssRepositorySourceExistTest`, đã dựng được với Room thật) | Dựng `LocalRssSvBlueskyUpgradeRoomTest` theo khuôn đó; `blueskyApiClient` là mock trả `Found`; kiểm cuối: `sourceUrl` trong Room là DID và `isFeedSourceExist` trả `true` |
| 6 | "ghi một lần" | Sau lần ghi đầu `sourceUrl == target` nên điều kiện "khác `target`" sai | Test: hai lần đồng bộ liên tiếp trên Room thật chỉ ghi **một** lần (kiểm bằng giá trị cột không đổi và bằng `coVerify` ở bản mock) |
| 7 | `NotFound` | `queryBlueskyArticles` ném `IOException` trước khi tới chỗ ghi | Không cần mã thêm; có test ghi lại (không gọi DAO) |

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/domain/repository/FeedDao.kt` | thêm `updateSourceUrlIfUnchanged` |
| Modify `app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt` | khối nâng cấp trong `queryBlueskyArticles` |
| Modify `app/src/test/java/com/mckimquyen/reader/domain/repository/FeedDaoSourceUrlTest.kt` | test DAO (cùng lớp, thêm test) |
| Modify `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvBlueskySyncTest.kt` | tách `feedDao` mock + test đồng bộ |
| Create `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvBlueskyUpgradeRoomTest.kt` | ca Room thật |
| Tạm, **không commit**: `/Users/loitran/.claude/tmp-f29/q02_batch.py`, `q03_batch.py` | đột biến |
| Create `doc/task/done/FRONT-02q_upgrade-old-bluesky-sources-to-did_DONE.md`; Modify `doc/feature.md`, báo cáo 02o | tài liệu |

---

### Task 1: DAO `updateSourceUrlIfUnchanged` (TDD)

**Interfaces (Produces):** `suspend fun updateSourceUrlIfUnchanged(id: String, expectedSourceUrl: String?, newSourceUrl: String): Int`.

- [ ] **Step 1: Viết test DAO đỏ** (7 test, thêm vào `FeedDaoSourceUrlTest`; helper `feed(...)` có sẵn):
  1. `writesWhenTheStoredValueIsTheExpectedOne`: `sourceUrl = "bsky:old"` ⇒ gọi `("f1", "bsky:old", "bsky:did:plc:abc")` trả `1`; `queryById` thấy `sourceUrl` mới.
  2. `doesNotWriteWhenTheStoredValueChanged`: lưu `"bsky:other"`, gọi với `expected = "bsky:old"` ⇒ trả `0`, giá trị không đổi.
  3. `matchesANullStoredValue_withANullExpectation`: `sourceUrl = null`, `expected = null` ⇒ trả `1` (chứng minh `IS`, không phải `=`).
  4. `aNullExpectationDoesNotMatchANonNullValue`: `sourceUrl = "bsky:x"`, `expected = null` ⇒ trả `0`.
  5. `onlyTheOneFeedChanges`: hai feed cùng `sourceUrl` cũ, gọi cho `f1` ⇒ `f2` không đổi.
  6. `noOtherColumnChanges`: sau khi ghi, `before.copy(sourceUrl = new) == after` (so cả `Feed`).
  7. `anUnknownIdWritesNothing`: id không tồn tại ⇒ trả `0`.
  Chạy đỏ (thiếu hàm là đỏ đúng lý do): `./gradlew :app:testDevDebugUnitTest --tests '*FeedDaoSourceUrlTest'` (nền).
- [ ] **Step 2: Thêm DAO** (sau `queryBySourceUrl`):

```kotlin
    /** Rewrites the sync address of one feed only if it still is what the caller read; returns the rows changed (0 or 1). */
    @Query(
        """
        UPDATE feed SET sourceUrl = :newSourceUrl
        WHERE id = :id
        AND sourceUrl IS :expectedSourceUrl
        """
    )
    suspend fun updateSourceUrlIfUnchanged(id: String, expectedSourceUrl: String?, newSourceUrl: String): Int
```
- [ ] **Step 3: Chạy xanh** (6 + 7 = 13 test DAO) và **kiểm `app/schemas` không đổi** (băm `22.json` giữ `45d76f04…`; `git status` ở `app/schemas` sạch). Nếu Room ghi lại schema thì dừng, đọc lý do, không commit.
- [ ] **Step 4: Mutation** (script Python): `IS` thành `=`; bỏ `AND sourceUrl IS …` (ghi không điều kiện); bỏ `WHERE id = :id` (ghi mọi hàng); đổi `SET sourceUrl` thành `SET url`. Mỗi mutant phải có test DAO đỏ.
- [ ] **Step 5: Commit** (DAO và test DAO): `feat(front-02q): rewrite a feed's sync address only if it still is what was read`.

---

### Task 2: Khối nâng cấp trong `queryBlueskyArticles` (TDD)

- [ ] **Step 1: Chuẩn bị test mock:** trong `LocalRssSvBlueskySyncTest` tách `private val feedDao = mockk<FeedDao>(relaxed = true)` và truyền vào constructor (điều chỉnh 2). Chạy lại lớp: 10 test cũ phải **xanh nguyên vẹn**.
- [ ] **Step 2: Viết test đỏ** (thêm vào `LocalRssSvBlueskySyncTest`), 9 test (`target = "bsky:${BlueskyTestPayloads.DID}"`):
  1. `anOldSource_isUpgradedToTheDid_onceItSyncsWell`: `feed` (url = sourceUrl = handle) ⇒ `coVerify(exactly = 1) { feedDao.updateSourceUrlIfUnchanged(feed.id, "bsky:alice.bsky.social", target) }`.
  2. `aSourceWithNoSourceUrl_isUpgradedWithANullExpectation`: `feed.copy(sourceUrl = null)` ⇒ gọi với `expected = null`.
  3. `aSourceAlreadyStoredByDid_isNotWrittenAgain`: `sourceUrl = target` ⇒ `exactly = 0`.
  4. `aFailedSync_writesNothing`: `Failed`, `NotFound`, và `found(repost)` (không bài) ⇒ `exactly = 0` cho cả ba.
  5. `aWriteThatThrows_doesNotLoseThePosts`: `coEvery { feedDao.updateSourceUrlIfUnchanged(any(), any(), any()) } throws RuntimeException()` ⇒ `syncSingleFeed(feed)?.articles` vẫn 1 bài.
  6. `aCancellationDuringTheWrite_isRethrown`: ném `kotlinx.coroutines.CancellationException` ⇒ **ném lại** (`syncSingleFeed` bọc trong `runCatching` nên kiểm bằng cách gọi `queryFeedArticles(feed, null)` trực tiếp nếu công khai; nếu không, ghi lại cách kiểm đã dùng).
  7. `aNonBlueskyFeed_neverTouchesTheDao`: `Feed.RSS_SOURCE_TYPE` ⇒ `coVerify(exactly = 0) { feedDao.updateSourceUrlIfUnchanged(any(), any(), any()) }`.
  8. `theSyncResultKeepsTheNameTheIconAndTheArticles`: tên, icon, bài giống hệt như trước (không đổi).
  9. `theUpgradeNeverChangesTheShownAddress`: `result.feed.url` vẫn là `"bsky:alice.bsky.social"`.
  **Bước đỏ phải đỏ đúng lý do:** dự kiến 1, 2 đỏ (chưa có khối); 3, 4, 5, 7, 8, 9 xanh ngay; 6 xanh hoặc không áp dụng (ghi rõ). Số khác thì **dừng, đọc lý do từng test, không sửa mã khi chưa hiểu**.
- [ ] **Step 3: Chèn khối** ngay trước `return articles` ở cuối `queryBlueskyArticles` (mẫu độc nhất):

```kotlin
        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")
        // A source stored under a handle is remembered by its DID from now on, so a renamed handle is still recognised (FRONT-02o).
        // One column, only if nobody changed it meanwhile; a failure here must never cost the posts just read.
        val didAddress = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.result.did)
        if ((feed.sourceUrl ?: feed.url) != didAddress) {
            runCatching { feedDao.updateSourceUrlIfUnchanged(feed.id, feed.sourceUrl, didAddress) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
        return articles
```
  `git diff -U0 -- LocalRssSv.kt` phải là **một khối thêm**, không xóa dòng nào.
- [ ] **Step 4: Ca Room thật** `LocalRssSvBlueskyUpgradeRoomTest` (khuôn `AbstractRssRepositorySourceExistTest`: Room trong bộ nhớ, `LocalRssSv` thật, `blueskyApiClient` mock): chèn một nguồn cũ vào Room; đồng bộ một lần ⇒ `sourceUrl` trong Room là DID, `url` vẫn handle, `name` và `icon` không đổi; `isFeedSourceExist(didAddress)` trả `true`; **đồng bộ lần hai** ⇒ `sourceUrl` không đổi (ghi một lần); đồng bộ với `Failed` ⇒ `sourceUrl` không đổi.
- [ ] **Step 5: Chạy xanh** các lớp: `LocalRssSvBlueskySyncTest`, `LocalRssSvBlueskyUpgradeRoomTest`, `FeedDaoSourceUrlTest`, `AbstractRssRepositorySourceExistTest`, `SubscribeViewModelBlueskyTest`, `BlueskyFeedMapperTest`, `FeedOptionViewModelTest`, `SubscribeViewModelSocialTest`, `SubscribeViewModelNostrTest`, `LocalRssSvNostrSyncTest` (số thật từ XML).
- [ ] **Step 6: Mutation** (script Python, một batch): bỏ điều kiện `!= didAddress` (ghi mỗi lần); đặt ghi **trước** kiểm có bài (ghi cả khi lỗi); ghi `url` thay `sourceUrl` (qua đối số `expected = feed.url`); dùng `feed.url` thay `feed.sourceUrl` làm giá trị chờ; bỏ `runCatching`; nuốt `CancellationException` (bỏ `.onFailure`); bỏ kiểm `!= didAddress` và dùng `feed.url != didAddress` (sai cột so sánh); ghi `found.result.handle` thay DID. Mỗi mutant phải có test đỏ; mutant sống sót thì thêm test rồi chạy lại **đúng mutant đó**.
- [ ] **Step 7: Commit** (mã và test): `feat(front-02q): remember the DID of an old Bluesky source once it syncs well`.

---

### Task 3: Kiểm cuối, báo cáo, tài liệu

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền): **số đo thật** (cộng `tests` từ mọi XML như 02g–02p; kỳ vọng 3.776 + 7 + 9 + số test Room, không hứa con số), 0 lỗi; lint 0 lỗi (ghi số cảnh báo thật); `assembleDevDebug` thành công.
- [ ] **Step 2:** `git diff --stat <commit trước 02q> HEAD -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` rỗng; băm `22.json` giữ `45d76f04…`; `git diff -U0 -- LocalRssSv.kt` đúng như Task 2 Step 3.
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02q_upgrade-old-bluesky-sources-to-did_DONE.md` (việc đã làm, bước đỏ thật, số test thật, mutant và test đỏ thật, điều chỉnh khi thực thi, **đính chính rủi ro DID đã có từ 02o**, điều chưa chặn); cập nhật `doc/feature.md` (R2) và các dòng "nguồn cũ" ở báo cáo 02o và 02n: nay nguồn cũ **còn đồng bộ được** sẽ được nâng cấp; nguồn **đã đổi handle** thì không.
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02q): report …`.

---

## Self-review

1. **Spec coverage:** hàm DAO có điều kiện (Task 1, 7 test); khối nâng cấp chỉ khi đồng bộ thành công, chỉ khi chưa là DID, một cột, có điều kiện, lỗi không lan, hủy được ném lại (Task 2 test 1 đến 9); Room thật xuyên module và "ghi một lần" (Task 2 Step 4); mutation, full suite, lint, schema không đổi, tài liệu → Task 1, 2, 3. Rủi ro DID và đính chính nằm ở Global Constraints và báo cáo.
2. **Placeholder scan:** không TBD. Điều chưa chắc (test 6 `CancellationException`, cách gọi trực tiếp `queryFeedArticles`; `app/schemas` có đổi khi thêm `@Query` `UPDATE` không) có bước dừng hoặc ghi lại cách kiểm.
3. **Nhất quán tên:** `updateSourceUrlIfUnchanged`, `didAddress`, `LocalRssSvBlueskyUpgradeRoomTest`, `target`; số test DAO 7 và đồng bộ 9 khớp danh sách.
4. **Rủi ro nhận thấy khi rà:** (a) **sửa một đính chính**: ở bước hỏi owner tôi nói rủi ro DID "02o chưa có", sai; đã đính chính trong spec và sẽ nêu rõ trong báo cáo và với owner ở lần trả lời kế; (b) `syncSingleFeed` bọc `runCatching` nên `CancellationException` bị nuốt ở tầng đó, test 6 phải kiểm ở tầng thấp hơn; (c) hàng `feed` bị ghi từ nền đồng bộ lần đầu tiên: rủi ro cao hơn các việc trước, giảm bằng một cột, có điều kiện, ghi một lần; (d) chạy song song `async` 6 nguồn: mỗi nguồn một lần, vô hại.
