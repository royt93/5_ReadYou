# STRAT-02 Curated Source Bundles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add five verified, static RSS bundles to the existing Add Sources page; a Local-account user can subscribe to each usable source in a bundle, skip existing sources and see per-source errors.

**Architecture:** A bundled JSON asset holds five bundles and dates checked 2026-11-02. Pure Kotlin loader/planner prepares sources and deduplicates against URLs already in the account. A `BundleSubscriber` creates one group, subscribes feeds with bounded concurrency, catches each feed's failure independently and removes the new group when none subscribe. `AddSourcesViewModel` and `AddSourcesPage` expose the bundles without introducing a new tab or Room schema.

**Tech Stack:** Kotlin, Android assets, Gson, Hilt, coroutines, Room v20 existing `GroupDao`/`AddedRssSourceDao`, Jetpack Compose Material3 1.0.1, JUnit4, MockK, Robolectric, AndroidX instrumentation.

## Global Constraints

- Branch `dev` only; no worktrees. Commit after each task; each commit message ends with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Never commit `.idea/*`; push only when owner asks.
- Room remains v20; no AI, API key, ad, IAP, money, or server of ours.
- Bundles use only the feeds in this plan; preserve `verifiedOn = "2026-11-02"`. This is a one-time reachability/format probe, not a promise of future availability.
- Only Local account can bundle-subscribe. Non-Local accounts may browse static bundles but show no subscribe control.
- No preview requests during browsing. On subscription, fetch each source and subscribe independently; never claim failed feeds succeeded.
- `AddSourcesViewModel` remains one `@Inject` constructor; do not add a second constructor.
- All long-running feed fetch/subscription on injected IO dispatcher; pure plan/JSON parsing can use Default. Concurrency bound is a named constant.
- Pixel lock `2B051FDH3006MU` only; never run `connectedDevDebugAndroidTest`.
- Smoke must back up Room `Reader`, `Reader-wal`, `Reader-shm` and `files/datastore/settings.preferences_pb`; require non-empty and `integrity_check` `ok` on a copy. Restore all four with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`, `cmp` all files, read Room using all three files; never delete DataStore. Bundle subscription writes real DB data, so always restore.
- Before every UI tap check `topResumedActivity`. For full-screen `AdActivity`, dump fresh hierarchy after `rm /sdcard/ui.xml`, tap only labelled `Tiếp tục đến ứng dụng`; never tap ad creative or stale coordinate. If label absent, stop and ask owner to close. Report ads.
- Read actual unit failures/counts from `app/build/test-results/testDevDebugUnitTest/*.xml`.

---

## File map

- Create `app/src/main/assets/curated_bundles.json`.
- Create `domain/curated/CuratedBundle.kt`, `CuratedBundleLoader.kt`, `BundleSubscribePlanner.kt`, `BundleSubscribeResult.kt`, `BundleSubscriber.kt`.
- Modify `AddSourcesViewModel.kt`, `AddSourcesPage.kt`; add a focused `CuratedBundlesSection.kt` if the existing 193-line page needs separation.
- Modify `app/src/main/res/values*/strings.xml` (38 files).
- Add unit tests under `app/src/test/java/com/mckimquyen/reader/domain/curated/` and `ui/page/home/addsources/`; Compose/Robolectric test `CuratedBundlesSectionTest.kt`; instrumentation test `CuratedBundlesDeviceTest.kt`.
- Create `doc/task/done/STRAT-02_curated-feed-marketplace_DONE.md`; update `doc/feature.md` and `doc/task/todo/11_AUTONOMOUS_COMMUNITY_PRIVACY.md`; remove `doc/task/todo/STRAT-02_curated-feed-marketplace.md`.

## Interfaces

```kotlin
data class CuratedSource(val name: String, val url: String)
data class CuratedBundle(
    val id: String,
    val titleEn: String,
    val titleVi: String,
    val verifiedOn: String,
    val sources: List<CuratedSource>,
)
object CuratedBundleLoader {
    fun load(input: InputStream): List<CuratedBundle>
}
data class BundleSubscribePlan(val toAdd: List<CuratedSource>, val alreadyHave: List<CuratedSource>)
object BundleSubscribePlanner {
    fun plan(sources: List<CuratedSource>, existingUrls: Set<String>): BundleSubscribePlan
}
data class BundleSubscribeResult(val added: Int, val skippedExisting: Int, val failed: Int)
class BundleSubscriber(...){ suspend fun subscribe(bundle: CuratedBundle): BundleSubscribeResult }
```

URL normalization for dedupe: trim; lowercase scheme + host; drop a single trailing `/`; keep path case/query/fragment unchanged. Compare bundle URLs to both `added_rss_source` and current account's feeds (`pullFeeds().first()`); `AddedRssSourceDao` alone is insufficient because feeds added outside Add Sources are not in its tracking table.

---

### Task 1: Bundled data and pure loader

**Files:** create `assets/curated_bundles.json`, `CuratedBundle.kt`, `CuratedBundleLoader.kt`, `CuratedBundleLoaderTest.kt`.

- [ ] **Step 1: Add test first.** Unit tests read the real asset with `FileInputStream(File("src/main/assets/curated_bundles.json"))` (the same working-directory convention `StringResourceParityTest` and `PodcastServiceManifestTest` use), so the shipped file is what gets validated; synthetic invalid JSON uses `ByteArrayInputStream`. Fixture tests valid data, malformed JSON, duplicate ids, blank fields, non-HTTPS URL and unverified date. Exact expected categories/source ids:

```kotlin
@Test fun load_returnsFiveNonEmptyBundles_withStableIdsAndVerificationDate() {
    val bundles = CuratedBundleLoader.load(fixture())
    assertEquals(listOf("tech_ai", "finance", "vn_news", "design", "dev"), bundles.map { it.id })
    assertTrue(bundles.all { it.sources.isNotEmpty() && it.verifiedOn == "2026-11-02" })
}
@Test fun load_rejectsDuplicateIdsAndNonHttpsUrls() {
    assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(jsonWithDuplicateIds()) }
    assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(jsonWithUrl("http://bad.example/rss")) }
}
@Test fun load_rejectsDuplicateSourceUrlsWithinBundle() {
    assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(jsonWithDuplicateUrls()) }
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*CuratedBundleLoaderTest"` → unresolved references.
- [ ] **Step 3: Add exact bundle asset.** Use these entries and exact URLs (title pairs shown EN / VI):

```json
[
  {"id":"tech_ai","titleEn":"Technology & AI","titleVi":"Công nghệ & AI","verifiedOn":"2026-11-02","sources":[
    {"name":"TechCrunch","url":"https://techcrunch.com/feed/"},
    {"name":"The Verge","url":"https://www.theverge.com/rss/index.xml"},
    {"name":"Ars Technica","url":"https://feeds.arstechnica.com/arstechnica/index"},
    {"name":"Wired","url":"https://www.wired.com/feed/rss"},
    {"name":"MIT Technology Review","url":"https://www.technologyreview.com/feed/"},
    {"name":"IEEE Spectrum","url":"https://spectrum.ieee.org/rss/fulltext"},
    {"name":"Hacker News","url":"https://hnrss.org/frontpage"},
    {"name":"OpenAI News","url":"https://openai.com/news/rss.xml"}
  ]},
  {"id":"finance","titleEn":"Economy & Investing","titleVi":"Kinh tế & Đầu tư","verifiedOn":"2026-11-02","sources":[
    {"name":"Bloomberg Markets","url":"https://feeds.bloomberg.com/markets/news.rss"},
    {"name":"Wall Street Journal","url":"https://feeds.a.dj.com/rss/RSSWorldNews.xml"},
    {"name":"The Economist","url":"https://www.economist.com/the-world-this-week/rss.xml"},
    {"name":"VnEconomy","url":"https://vneconomy.vn/rss/home.rss"},
    {"name":"Báo Đầu tư","url":"https://baodautu.vn/rss/home.rss"}
  ]},
  {"id":"vn_news","titleEn":"Vietnam News","titleVi":"Thời sự Việt Nam","verifiedOn":"2026-11-02","sources":[
    {"name":"VnExpress","url":"https://vnexpress.net/rss/tin-moi-nhat.rss"},
    {"name":"Tuổi Trẻ","url":"https://tuoitre.vn/rss/tin-moi-nhat.rss"}
  ]},
  {"id":"design","titleEn":"Design & Creativity","titleVi":"Thiết kế & Sáng tạo","verifiedOn":"2026-11-02","sources":[
    {"name":"Smashing Magazine","url":"https://www.smashingmagazine.com/feed/"},
    {"name":"A List Apart","url":"https://alistapart.com/main/feed/"},
    {"name":"CSS-Tricks","url":"https://css-tricks.com/feed/"},
    {"name":"Awwwards","url":"https://www.awwwards.com/blog/feed/"},
    {"name":"Behance","url":"https://www.behance.net/feeds/projects"},
    {"name":"Designboom","url":"https://www.designboom.com/feed/"},
    {"name":"Fast Company","url":"https://www.fastcompany.com/latest/rss"},
    {"name":"Muzli","url":"https://medium.muz.li/feed"}
  ]},
  {"id":"dev","titleEn":"Programming & Developer Tools","titleVi":"Lập trình & Công cụ phát triển","verifiedOn":"2026-11-02","sources":[
    {"name":"Dev.to","url":"https://dev.to/feed"},
    {"name":"GitHub Blog","url":"https://github.blog/feed/"},
    {"name":"Stack Overflow Blog","url":"https://stackoverflow.blog/feed/"},
    {"name":"freeCodeCamp","url":"https://www.freecodecamp.org/news/rss/"},
    {"name":"Hacker Noon","url":"https://hackernoon.com/feed"},
    {"name":"Android Developers Blog","url":"https://android-developers.googleblog.com/feeds/posts/default"},
    {"name":"Viblo","url":"https://viblo.asia/rss"},
    {"name":"TopDev","url":"https://topdev.vn/blog/feed/"},
    {"name":"Hugging Face Blog","url":"https://huggingface.co/blog/feed.xml"}
  ]}
]
```

`finance` intentionally keeps WSJ/ Economist though their verified feeds are World News/World This Week; spec must retain that caveat.
- [ ] **Step 4: Implement.** `CuratedBundleLoader.load(input)` parses with the installed `Gson` instance via constructor injection, closes only if loader owns the stream (simplest: `input.use {}`), validates exactly 5 unique bundle IDs, nonblank title strings, `verifiedOn` regex `\\d{4}-\\d{2}-\\d{2}`, at least one source, per-bundle URL uniqueness after planner normalization, and every URL HTTPS with host. Avoid hardcoding array count in loader except `EXPECTED_BUNDLE_COUNT = 5` named constant.
- [ ] **Step 5: Green.** `./gradlew testDevDebugUnitTest --tests "*CuratedBundleLoaderTest"`; expected 4+ pass.
- [ ] **Step 6: Commit.** `feat(strat-02): add five dated curated source bundles` (+ trailer).

### Task 2: Pure URL planner

**Files:** create `BundleSubscribePlanner.kt`, `BundleSubscribePlannerTest.kt`.

- [ ] **Step 1: Write exact tests.**

```kotlin
@Test fun plan_normalizesSchemeHostCaseAndTrailingSlash_forExistingUrls() {
    val plan = BundleSubscribePlanner.plan(
        listOf(CuratedSource("A", "https://example.com/rss/"), CuratedSource("B", "https://other.example/feed")),
        setOf("HTTPS://EXAMPLE.COM/rss"),
    )
    assertEquals(listOf("B"), plan.toAdd.map { it.name })
    assertEquals(listOf("A"), plan.alreadyHave.map { it.name })
}
@Test fun plan_removesDuplicateUrlsInBundleAndKeepsFirstEntry() {
    val plan = BundleSubscribePlanner.plan(
        listOf(CuratedSource("first", "https://a.example/rss/"), CuratedSource("duplicate", "https://A.example/rss")), emptySet(),
    )
    assertEquals(listOf("first"), plan.toAdd.map { it.name })
}
@Test fun plan_emptyAndAllExistingAreSafe() {
    assertTrue(BundleSubscribePlanner.plan(emptyList(), emptySet()).toAdd.isEmpty())
    assertTrue(BundleSubscribePlanner.plan(listOf(CuratedSource("A", "https://a.example/rss")), setOf("https://a.example/rss")).toAdd.isEmpty())
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*BundleSubscribePlannerTest"`.
- [ ] **Step 3: Implement.** Add `BundleSubscribePlan(toAdd, alreadyHave)` and `normalizeUrl`. Parse with `java.net.URI`; lowercase only scheme/host, trim URL and remove one trailing slash from path; retain query and fragment. If URI malformed, planner treats it as alreadyHave so invalid catalog entries are never subscribed.
- [ ] **Step 4: Green.** Same command; expect 3/3.
- [ ] **Step 5: Commit.** `feat(strat-02): add a pure curated feed subscription planner` (+ trailer).

### Task 3: Bundle subscription worker/service with per-feed failure isolation

**Files:** create `BundleSubscribeResult.kt`, `BundleSubscriber.kt`, `BundleSubscriberTest.kt`.

**Consumes:** `CuratedBundle`, `BundleSubscribePlan`, `RssSv`, `RssHelper`, `AddedRssSourceDao`, `AccountSv`, `GroupDao` access (or `RssSv.get().addGroup/deleteGroup/pullGroups`), `Dispatchers.IO` qualifier.

- [ ] **Step 1: Write tests first** using MockK, `runTest(StandardTestDispatcher)` and injected dispatcher. Required cases:
  - existing URL is skipped and DAO is not called for it;
  - `rssHelper.searchFeed` success → repository subscribe once and `added++`;
  - one URL throws, another succeeds → one `failed`, one `added`, the successful feed remains;
  - all URLs throw → result `added=0`, group deletion is invoked;
  - all URLs existing → no group created and `added=0, skippedExisting=N, failed=0`;
  - empty bundle → no group, zero counts;
  - `CancellationException` rethrown; group cleanup uses `NonCancellable` only if needed to ensure failed empty group does not remain.
- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*BundleSubscriberTest"` → missing class.
- [ ] **Step 3: Confirm the correct Local repository API before implementation.** `RssSv.get()` selects current account; `subscribe`/`addGroup` are backend methods. Only Local is permitted by design. `AbstractRssRepository.deleteGroup(group)` deletes articles, feeds and group; `GroupDao.delete` exists. Do not call private DAO directly. Check `AddSourcesViewModel` already creates feeds with `UUID.randomUUID()` but group IDs use `accountId.spacerDollar(UUID.randomUUID())`; use the repository `addGroup(bundle.localizedTitle)` to get the correct ID.
- [ ] **Step 4: Implement minimal API.** `BundleSubscriber.subscribe(bundle, existingUrls): BundleSubscribeResult`; caller supplies `existingUrls` from `AddedRssSourceDao` union `rssService.get().pullFeeds().first().flatMap { it.feeds }.map { it.url }`. Plan first; if `toAdd` empty return before group creation. Create one group. For each source in `toAdd`, use a coroutine `Semaphore(MAX_PARALLEL_FEEDS)` with `MAX_PARALLEL_FEEDS = 3`; each child `withContext(ioDispatcher)` calls `rssHelper.searchFeed(source.url)`, copies returned feed with new group ID and fallback catalog name, then `rssService.get(AccountType.Local.id).subscribe(feed, articles)`, then `addedRssSourceDao.insertAddedSource(AddedRssSource(url, name, accountId))`. Catch `CancellationException` and rethrow; catch other exceptions per source and count `failed++` atomically. If added==0, call `deleteGroup(group)` before returning (including all-existing path creates no group). Track and report skippedExisting from planner. Do not silently subscribe an unparsable feed with empty articles: unlike current `addRssSource`, bundle semantics explicitly skip/report failed.
- [ ] **Step 5: Green.** Test bundle subscriber; expect all six cases pass. Add `subscribe_neverRunsMoreThanMaxParallelFeedsAtOnce`: 10 sources, `searchFeed` is a MockK `coAnswers` that increments an `AtomicInteger active`, records `maxActive`, `delay(10)`, then decrements; assert `maxActive <= BundleSubscriber.MAX_PARALLEL_FEEDS` and `added == 10`.
- [ ] **Step 6: Commit.** `feat(strat-02): subscribe curated bundles with per-feed results` (+ trailer).

### Task 4: ViewModel state and bundle loading/subscription

**Files:** modify `AddSourcesViewModel.kt`, `AddSourcesUiState.kt`; create `AddSourcesViewModelBundleTest.kt`.

- [ ] **Step 1: Test first.** Tests with MockK and `StandardTestDispatcher`:
  - state starts with empty bundles, no progress/result;
  - `loadBundles()` decodes the asset once, preserves current locale EN/VI titles;
  - subscribe invokes `BundleSubscriber` with bundle, account type Local, all `added_rss_source` URLs and `pullFeeds` URLs;
  - duplicate tap while `subscribingBundleId` same is ignored;
  - non-Local account returns without fetching/subscribing;
  - partial result is exposed as counts; error does not crash viewModelScope or leave busy forever.

```kotlin
@Test fun subscribeBundle_usesAddedSourcesAndCurrentFeedsForDeduplication() = runTest(dispatcher) {
    val vm = viewModel(accountType = AccountType.Local.id)
    vm.loadBundles(); advanceUntilIdle()
    vm.subscribeBundle(bundle)
    advanceUntilIdle()
    coVerify { subscriber.subscribe(bundle, match { "https://existing.example/rss" in it }) }
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*AddSourcesViewModelBundleTest"`.
- [ ] **Step 3: Implement state.** Add to `AddSourcesUiState`: `bundles: List<CuratedBundle>`, `loadingBundles: Boolean`, `subscribingBundleId: String?`, `bundleProgress: Pair<Int, Int>?`, `bundleResult: BundleSubscribeResult?`, `bundleError: String?`; methods `loadBundles()`, `subscribeBundle(bundle)`, `dismissBundleResult()`. Add dependencies to the existing single `@Inject` constructor; never create second `@Inject`. Inject `@IODispatcher ioDispatcher` and `@DefaultDispatcher defaultDispatcher` as needed. Derive Local from `context.currentAccountType`, not an argument from UI. Reuse `AddedRssSourceDao` and `rssService.get().pullFeeds().first()` for existing URLs. Localized bundle title selects `titleVi` iff `Locale.getDefault().language == "vi"`, else `titleEn`.
- [ ] **Step 4: Green.** `./gradlew testDevDebugUnitTest --tests "*AddSourcesViewModelBundleTest"`.
- [ ] **Step 5: Commit.** `feat(strat-02): load bundles and expose subscription progress in Add Sources` (+ trailer).

### Task 5: UI bundle section and 38 locale strings

**Files:** modify `AddSourcesPage.kt`; create `CuratedBundlesSection.kt`, `CuratedBundlesSectionTest.kt`; modify `values*/strings.xml` (38 files).

- [ ] **Step 1: Add failing Compose tests** (`@RunWith(RobolectricTestRunner::class) @Config(sdk=[34], application=Application::class) @GraphicsMode(NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`, one `setContent` per test):
  - five bundle titles render; each displays count and verification date;
  - expanding one bundle shows only its source names;
  - local account renders `subscribe_<id>` button, button click calls callback;
  - non-Local renders no subscribe buttons (`onAllNodesWithTag(...).assertCountEquals(0)`);
  - progress disables the active bundle button and displays `x/y`;
  - partial success shows "added / already present / failed" counts;
  - no source preview network call occurs when expanding.

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*CuratedBundlesSectionTest"`.
- [ ] **Step 3: Add 10 strings EN/VI; other 36 locales English.** Keys: `curated_bundles_title`, `curated_bundle_verified_on`, `curated_bundle_source_count`, `curated_bundle_subscribe_all`, `curated_bundle_subscribing`, `curated_bundle_progress`, `curated_bundle_result`, `curated_bundle_error`, `curated_bundle_no_sources_added`, `curated_bundle_account_local_only`. A script at `/tmp/strat02_strings.py` should use `glob('app/src/main/res/values*/strings.xml')`, insert only missing keys before `</resources>`, with EN and VI maps and escaping `&`, `'`, `"`; print locale file count, expect `38`.
- [ ] **Step 4: Implement UI.** In `AddSourcesPage.kt`, retain existing country cards below. Add `CuratedBundlesSection` at top; pass `uiState.bundles`, localized title, `verifiedOn`, existing source names, `canSubscribe = currentAccountType == AccountType.Local.id`, busy bundle ID/progress/result, and callbacks to ViewModel. Each bundle row expands locally; it does not call network. Subscribe button invokes `subscribeBundle` and the result appears inline; no separate server/community page. Keep all controls accessible with 48dp minimum targets/contentDescription.
- [ ] **Step 5: Green.** `./gradlew testDevDebugUnitTest --tests "*CuratedBundlesSectionTest" --tests "*AddSourcesViewModelBundleTest" --tests "*StringResourceParityTest"`; expected UI test cases + VM cases and parity 6/6.
- [ ] **Step 6: Commit.** `feat(strat-02): add curated bundles to existing Add Sources page` (+ trailer).

### Task 6: Instrumentation and full verification

- [ ] **Step 1: Instrumentation test** `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/addsources/CuratedBundlesDeviceTest.kt`: launch `CuratedBundlesSection` in `createAndroidComposeRule<ComponentActivity>()`; assert visible title "Technology & AI" and click the local subscribe button invokes callback with bundle ID `tech_ai`; no network/Room.
- [ ] **Step 2: Full suite/lint/build** (sequential): `./gradlew testDevDebugUnitTest --rerun`; `./gradlew lintDevDebug --rerun-tasks`; `./gradlew assembleDevDebug assembleDevDebugAndroidTest`. Read real test totals/failures from XML, lint counts from `lint-results-devDebug.xml`; no guessed numbers.
- [ ] **Step 3: Confirm locked device.** `adb devices -l` lists `2B051FDH3006MU`; `adb -s ... shell getprop ro.product.model` = Pixel 7 Pro. Install APKs via `find` (space-containing APK filename); run `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.addsources.CuratedBundlesDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; uninstall test package.
- [ ] **Step 4: Backup before smoke.** Force-stop. Export four files from device: Room main/`-wal`/`-shm`, DataStore `settings.preferences_pb`. Require non-empty. Copy all three Room files and `sqlite3 /tmp/strat02-check/Reader "PRAGMA integrity_check;"` → `ok`; record feed/article counts.
- [ ] **Step 5: Pixel smoke.** Launch; check foreground. If full-screen ad appears, fresh dump (`rm /sdcard/ui.xml`), tap only `Tiếp tục đến ứng dụng` when shown; if absent, stop and ask user to close. Open Add Sources; verify bundle names/source counts and verification date. Register a small real bundle `vn_news` (2 feeds) to limit network requests; verify per-feed result and new group in the app, confirm ECHO-02 can find a different-feed article if matching topic exists (do not require it if no matching title). Check foreground before every tap.
- [ ] **Step 6: Restore no matter outcome.** Force-stop, restore all four exact backups with `run-as sh -c 'cat > ...'`; read back each and `cmp`; on copy of all three Room files run `integrity_check`, verify original feed/article counts; verify DataStore identical; test package absent; delete only host temp dirs.

### Task 7: Completion docs

**Files:** create `doc/task/done/STRAT-02_curated-feed-marketplace_DONE.md`; update `doc/feature.md`; update row in `doc/task/todo/11_AUTONOMOUS_COMMUNITY_PRIVACY.md`; `git rm doc/task/todo/STRAT-02_curated-feed-marketplace.md`.

- [ ] **Step 1: Write completion report.** Include checked-on date 2026-11-02, exact bundle list, honest limits (one-time feed validation; 2 feeds in VN news; WSJ/Economist world feeds; no preview/community server; partial failures), test/lint/build/instrumentation results, actual Pixel smoke result, any ads, four-file restore/cmp/integrity proof and commits.
- [ ] **Step 2: Update index and epic.** Add STRAT-02 under Recently implemented in `doc/feature.md`; change epic 11 row to ✅ Done with link `../done/STRAT-02_curated-feed-marketplace_DONE.md`.
- [ ] **Step 3: Remove old todo.** `git rm doc/task/todo/STRAT-02_curated-feed-marketplace.md`.
- [ ] **Step 4: Commit.** `docs(strat-02): record curated source bundles completion` (+ trailer).

## Final verification checklist

- [ ] Five bundle IDs, HTTPS, unique IDs and unique source URLs; verified date is `2026-11-02`.
- [ ] Bundle browsing is offline; one button only subscribes on Local account.
- [ ] Existing sources are deduped across feed table + added-source tracker; normalization tested.
- [ ] Each feed failure is isolated; partial results accurate; all-failed removes empty group; cancellation is rethrown.
- [ ] No Room schema change; no community backend, no preview requests.
- [ ] 38 locale strings; `StringResourceParityTest` 6/6.
- [ ] Full unit suite, lint, build and Pixel instrumentation pass; smoke results truthful.
- [ ] Room 3 files + DataStore restored byte-for-byte; integrity check passes.
