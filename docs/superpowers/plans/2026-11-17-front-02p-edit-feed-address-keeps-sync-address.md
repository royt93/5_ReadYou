# FRONT-02p — Sửa địa chỉ nguồn trong menu phải đổi cả địa chỉ đồng bộ (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** `FeedOptionViewModel.changeFeedUrl()` ghi địa chỉ mới vào **cả `url` và `sourceUrl`** khi nguồn có `sourceUrl` (WEB, NOSTR, BLUESKY); nguồn RSS thường (`sourceUrl == null`) không đổi hành vi.

**Architecture:** Đổi **đúng một câu lệnh** trong `changeFeedUrl()`. Test mới đầu tiên cho `FeedOptionViewModel` (JVM, MockK, Robolectric không cần): bắt `Feed` truyền vào `updateFeed` bằng `slot`.

**Tech Stack:** Kotlin, JUnit4 + MockK + `kotlinx-coroutines-test`. **Chỉ JVM, không chạm thiết bị nào.**

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **KHÔNG chạm thiết bị nào.** Mọi kiểm chứng chạy trên máy tính.
- Chỉ đổi `FeedOptionVM.kt` (một câu lệnh trong `changeFeedUrl`). **Không** đổi giao diện, `ChangeUrlDialog`, `showFeedUrlDialog`, chuỗi, locale, Room, `app/schemas`, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. **Không thêm kiểm địa chỉ** (sẽ cần chuỗi mới, owner đã dặn không).
- Gradle chạy NỀN, chờ `.exit`; đọc XML chỉ sau `.exit`. Đột biến: `jvm_mut.sh` qua **script Python**, **một** batch nền, không sửa tệp tới `ALLDONE`, `cmp` cuối, **kiểm mẫu `old` độc nhất trước** (đã đếm lúc viết plan: câu `updateFeed(it.copy(url = …))` 1, `fun changeFeedUrl()` 1).
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- R5: không magic number; không `!!`; không `late` không giải thích; đóng/cleanup trong `@After` (`Dispatchers.resetMain()`).
- Nói thẳng: địa chỉ sai ở Bluesky/Nostr nay làm đồng bộ của nguồn đó lỗi (trước đây vô hại); nguồn Bluesky mới mất DID đã nhớ sau khi sửa; không kiểm địa chỉ.
- Không tạo hai commit trùng tiêu đề; `git diff --check` trước khi commit.

## Điều chỉnh so với spec (đọc mã thật, 2026-11-17)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "bắt `Feed` truyền vào `updateFeed`" | `FeedOptionViewModel` không có cách đặt `feed` trực tiếp: `feed` chỉ được nạp bởi `showDrawer(scope, feedId)` → `fetchFeed` (`findFeedById`) rồi **`drawerState.show()`** (Compose `ModalBottomSheetState`, có thể treo trong JVM) | Test gọi `showDrawer` bằng **`backgroundScope`** của `runTest` (không chờ nó kết thúc); `fetchFeed` chạy xong **trước** `show()` nên `feed` đã nạp khi `show()` còn treo. Nếu `show()` ném lỗi thay vì treo, bắt trong coroutine và ghi vào "Điều chỉnh khi thực thi", **không đổi mã production để lách** |
| 2 | `viewModelScope.launch` trong `changeFeedUrl` | `viewModelScope` dùng `Dispatchers.Main.immediate` | `Dispatchers.setMain(StandardTestDispatcher())` trong `@Before`, `resetMain()` trong `@After`, `advanceUntilIdle()` sau khi gọi |
| 3 | `init` của ViewModel | `init` thu thập `rssService.get().pullGroups()` trên `ioDispatcher` | Mock `pullGroups()` trả `flowOf(emptyList())`; truyền `ioDispatcher` là cùng `StandardTestDispatcher` |
| 4 | `rssService.get()` | `RssSv.get()` trả `LocalRssSv` (lớp thật, `open`?) | Mock `RssSv` (`every { get() } returns localService`), `localService = mockk<LocalRssSv>(relaxed = true)` như `SubscribeViewModelBlueskyTest` |
| 5 | Nút sửa địa chỉ chỉ cho `update == true` | Kiểm đó nằm ở `FeedOptionDrawer` (giao diện), **không** ở `changeFeedUrl` | Test ViewModel không kiểm cờ này; ghi vào giới hạn: Fever và Google Reader vẫn không sửa được vì giao diện chặn |
| 6 | Test đồng bộ | `LocalRssSvBlueskySyncTest` đã có ca nguồn dạng DID và chạy đúng | Thêm **một** ca: nguồn Bluesky mà cả `url` lẫn `sourceUrl` là địa chỉ mới đồng bộ bằng địa chỉ mới (bằng chứng cho quy tắc ghi cả hai cột) |

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/drawer/feed/FeedOptionVM.kt` | đổi một câu lệnh |
| Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/drawer/feed/FeedOptionViewModelTest.kt` | test ViewModel |
| Modify `app/src/test/java/com/mckimquyen/reader/domain/sv/LocalRssSvBlueskySyncTest.kt` | thêm một ca đồng bộ |
| Tạm, **không commit**: `/Users/loitran/.claude/tmp-f29/p02_batch.py` | đột biến |
| Create `doc/task/done/FRONT-02p_edit-feed-address_DONE.md`; Modify `doc/feature.md`, báo cáo 02o | tài liệu |

---

### Task 1: Test và sửa `changeFeedUrl` (TDD)

- [ ] **Step 1: Đọc khuôn** `SubscribeViewModelBlueskyTest` dòng 56–95 (`setUp`, `Dispatchers.setMain`, `mockk<RssSv>()`, `every { rssService.get() } returns localService`) để chép đúng. Kiểm hàm mới cần thiết nào của `LocalRssSv` bị mock `relaxed`.
- [ ] **Step 2: Viết 8 test** (`FeedOptionViewModelTest`, `runTest(dispatcher)`, helper `open(feed)`: mock `localService.findFeedById(feed.id) returns feed`, `backgroundScope.launch`-ed `viewModel.showDrawer(backgroundScope, feed.id)` rồi `runCurrent()`; helper `change(address)`: `viewModel.inputNewUrl(address); viewModel.changeFeedUrl(); advanceUntilIdle()`; bắt `slot<Feed>` bằng `coVerify { localService.updateFeed(capture(slot)) }`):
  1. `anRssFeed_changesOnlyItsUrl_andKeepsAnEmptySourceUrl`: `sourceUrl = null` ⇒ `url = địa chỉ mới`, `sourceUrl == null`.
  2. `aWebFeed_changesBothColumns`: `sourceUrl = url cũ` ⇒ cả hai = địa chỉ mới.
  3. `aNewBlueskyFeed_changesBothColumns_andLosesTheStoredDid`: `url = "bsky:alice"`, `sourceUrl = "bsky:did:plc:abc"` ⇒ cả hai = `"bsky:bob"`; ghi nhận DID bị thay.
  4. `aNostrFeed_changesBothColumns`.
  5. `everyOtherField_isKept`: `id, name, icon, groupId, accountId, isNotification, isFullContent, sourceType` giữ nguyên.
  6. `theDialogCloses_afterSaving`: `feedOptionUiState.value.changeUrlDialogVisible == false` (đặt `true` trước bằng `showFeedUrlDialog()`).
  7. `withoutALoadedFeed_nothingIsSaved`: không gọi `showDrawer`, `changeFeedUrl()` ⇒ `coVerify(exactly = 0) { updateFeed(any()) }`.
  8. `theTypedAddressIsUsedAsIs`: không `trim`, không chuẩn hóa (ghi lại hành vi hiện tại, ví dụ `"  bsky:x "` giữ nguyên); đây là **ghi nhận**, không phải yêu cầu mới.
  **Bước đỏ phải đỏ đúng lý do:** dự kiến test 2, 3, 4 đỏ (chưa ghi `sourceUrl`), test 1, 5, 6, 7, 8 xanh ngay. Nếu số khác, **dừng, đọc lý do từng test, không sửa mã khi chưa hiểu**. Nếu `showDrawer` treo hay ném lỗi làm cả lớp không chạy được (điều chỉnh 1), **dừng và hỏi owner**, không đổi mã production.
- [ ] **Step 3: Sửa mã** (mẫu độc nhất):

```kotlin
    fun changeFeedUrl() {
        _feedOptionUiState.value.feed?.let {
            viewModelScope.launch {
                val address = _feedOptionUiState.value.newUrl
                // The sync of a web, Nostr or Bluesky source reads sourceUrl before url, so an address typed here must reach both.
                rssService.get().updateFeed(it.copy(url = address, sourceUrl = if (it.sourceUrl != null) address else null))
                _feedOptionUiState.update { it.copy(changeUrlDialogVisible = false) }
            }
        }
    }
```
  `git diff -U0` phải cho thấy chỉ thân `changeFeedUrl` đổi (2 dòng thay, 2 dòng thêm).
- [ ] **Step 4: Thêm ca đồng bộ** vào `LocalRssSvBlueskySyncTest`: nguồn `url = sourceUrl = "bsky:bob.bsky.social"` đồng bộ gọi `api.fetch("bob.bsky.social")` đúng một lần (khuôn dòng 79).
- [ ] **Step 5: Chạy xanh** `FeedOptionViewModelTest`, `LocalRssSvBlueskySyncTest`, và bộ liên quan (`BlueskyTest`, `MapperTest`, `SocialTest`, `NostrTest`, `DialogTypingTest`, `FeedDaoSourceUrlTest`, `AbstractRssRepositorySourceExistTest`); đọc số thật từ XML.
- [ ] **Step 6: Mutation** (script Python, một batch): bỏ nhánh `sourceUrl` (về `it.copy(url = address)`); luôn ghi `sourceUrl = address` kể cả khi `null`; ghi `sourceUrl = it.url` (địa chỉ cũ); đổi `!= null` thành `== null`; bỏ dòng đóng hộp thoại; đổi `address` thành `it.url`. Mỗi mutant phải có test đỏ; mutant sống sót thì thêm test rồi chạy lại **đúng mutant đó**.
- [ ] **Step 7: Commit** (mã và test): `fix(front-02p): make the feed address menu change the address the sync reads too`.

---

### Task 2: Kiểm cuối, báo cáo, tài liệu

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền): **số đo thật** (cộng `tests` từ mọi XML như 02g–02o; kỳ vọng 3.767 + 8 + 1, không hứa con số), 0 lỗi; `:app:lintDevDebug` 0 lỗi (ghi số cảnh báo); `assembleDevDebug` thành công.
- [ ] **Step 2:** `git diff --stat <commit trước 02p> HEAD -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` rỗng; `git diff -U0 -- FeedOptionVM.kt` đúng như Step 3.
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02p_edit-feed-address_DONE.md` (việc đã làm, bước đỏ thật, số test thật, mutant và test đỏ thật, điều chỉnh khi thực thi, **hệ quả và điều chưa kiểm**); cập nhật `doc/feature.md` (R2); sửa dòng "menu sửa địa chỉ chỉ đổi `url`" ở báo cáo 02o thành đã sửa ở 02p.
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02p): report …`.

---

## Self-review

1. **Spec coverage:** cả ba loại có `sourceUrl` (test 2, 3, 4), RSS thường không đổi (test 1), các trường khác giữ (5), hộp thoại đóng (6), không có feed thì không lưu (7), ghi nhận không chuẩn hóa (8); quy tắc ghi cả hai cột có bằng chứng đồng bộ (Task 1 Step 4); mutation, full suite, lint, assemble, tài liệu → Task 1, 2. Hệ quả "địa chỉ sai làm đồng bộ lỗi" và "mất DID" nằm trong Global Constraints và báo cáo.
2. **Placeholder scan:** không TBD. Điều chưa chắc (`showDrawer` treo hay ném trong JVM; `LocalRssSv` mock được) có bước dừng và hỏi, kèm cách xử lý cụ thể (điều chỉnh 1, 4).
3. **Nhất quán tên:** `FeedOptionViewModelTest`, `open`, `change`, `address`; 8 test khớp danh sách; mutation khớp quy tắc ghi cả hai cột.
4. **Rủi ro nhận thấy khi rà:** (a) **rủi ro lớn nhất là dựng được `FeedOptionViewModel` trong JVM** vì `ModalBottomSheetState` dựng ở `FeedOptionUiState` mặc định cần runtime Compose: nếu khởi tạo ném lỗi, đó là blocker thật (dừng hỏi); (b) test 8 ghi nhận hành vi hiện tại không phải yêu cầu; (c) sau 02p, địa chỉ sai ở Bluesky/Nostr làm đồng bộ lỗi (trước đây vô hại): đã nêu ở spec và đã được owner duyệt.
