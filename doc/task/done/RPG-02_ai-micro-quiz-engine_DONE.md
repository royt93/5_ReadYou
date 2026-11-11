# RPG-02 (AI quiz at the end of an article)

**Date:** 2026-11-11  
**Branch:** `dev`  
**Scope:** The end-of-article quiz card now shows a question that Gemini writes from the article's **title and short description only** (at most 250 characters of description, never the body). It has its own consent, asks once per article, caches in memory, and says honestly when the AI could not write one. The XP rules (x3 = 150) and the Rewarded Ad retry/double buttons are unchanged.  
**Spec / plan:** `docs/superpowers/specs/2026-11-11-rpg-02-ai-micro-quiz-design.md`, `docs/superpowers/plans/2026-11-11-rpg-02-ai-micro-quiz.md`.

## Implemented

- `AiQuizPolicy` (pure): the 250-character preview limit and HTML stripping, the prompt (only `Title:` and `Preview:`), the JSON-mode request body, and the answer check. An answer is rejected for: not JSON, a missing field, anything other than 4 options, an empty or duplicate option (ignoring case and punctuation), a correct index that is not an integer in 0..3, an empty or over-long question (200) or option (120), options whose longest/shortest length ratio is above 2.5, a correct answer repeated verbatim inside the question (answers of 4+ characters), or an empty explanation. Option order is shuffled from the article id and the correct index follows the text.
- `AiQuizService`: calls the existing `AiRequestGateway` with use case `quiz_micro`. It is handed three strings, never an article, and logs only the exception type or the rejection reason.
- Consent: a new key `quiz_gemini_consent` (separate from Podcast Studio's), a switch in Settings → Privacy, and the question asked inside the card (**Agree** / **Not now**). "Not now" stores nothing.
- `BrainRpgViewModel`: states `Hidden`, `AwaitingConsent`, `ConsentDeclined`, `Loading`, `Ready`, `Failed`; one request per article; a second call while loading does nothing; a failure is not cached and is retried only when the user taps; a newer article cancels the older request.
- `QuizCache`: in-memory LRU of 20 ready quizzes, a Hilt singleton (see "Found on the phone").
- `BrainQuizHost` draws each state; `BrainQuizCard` gets the label "Written by AI from the title and preview; it can be wrong". `Content`/`ReadingPage` pass `shortDescription` (not a slice of the body) and the request is made from inside the lazy item that holds the card.
- Removed: `generateQuiz`, `QuizPeer`, `ArticleDao.queryQuizPeers`, `BrainRpgRepository.quizPeers` and their tests. `detectCategory` and the `CATEGORY_*` constants stay (XP, skill tree and decay use them).
- 12 new strings in all 38 locale files (English and Vietnamese written, 36 English). No Room change (still v20), no migration, no dependency, no manifest change.

## Verification (JVM)

- Full unit suite on the final code (`--rerun-tasks`, HEAD `7c941d57`, clean tree): **2,791 tests, 0 failures, 0 errors, 0 skipped**, 390 result files, none stale (2,733 before this work). Lint (`--rerun-tasks`): **0 errors, 2,463 warnings** (baseline), 0 hits in the new files. `assembleDevDebug` and `assembleDevDebugAndroidTest` succeeded.
- New tests: consent (4) and privacy switch (2), prompt/preview (8), answer check (17), service (11), cache (6), view model (16), card host (9), source wiring (7), plus 2 for what remains of the old generator.
- Mutation checks, each caught by the intended test and restored with `cmp`: consent key and getter, preview limit and tag stripping, content check, JSON mode, link in the prompt; every rejection rule and its boundary; the preview cut, no-key and no-content short-circuits, cancellation, use case, `MissingApiKey`; cache order, capacity, singleton; every view-model decision (no-consent request, declined request, cache, loading guard, failure state, retry guard, body-as-preview); each card state and the AI label; where the short description enters `Content`; the privacy route; the removed generator and the category keys.
- Things that did not go to plan:
  - My first view-model test used `StandardTestDispatcher`, but the DataStore write runs on the real IO dispatcher, so the test scheduler could not wait for it and the class **hung** (no result file). It was my test, not production. I rewrote it on the `PodcastStudioViewModelTest` pattern (unconfined dispatchers, bounded real-time waits).
  - One mutation survived: a stale-answer check in `generateQuiz` could never run, because a newer article cancels the job first. I removed the dead line; the cancel itself is still caught by a test.
  - The `@Singleton` on the cache could not be caught by a JVM test that shares one instance by hand; I added a test that checks the annotation.
  - Several "output" lines from my shell tool were wrong or empty at times; I re-read results from files before trusting them.

## Verification (Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-11-11)

Gate first (no instrumentation, no foreign app in front, screen unlocked after the owner unlocked it), byte-exact backup of Room, sidecars, DataStore and the shared preferences, app and test APK built from the then-HEAD.

- **androidTest `BrainQuizCardWidgetTest`** (run with `am instrument`, never `connected*`): 10 of 10 (4 existing + 6 new for consent, not now, privacy, retry, loading and the AI label). Three mutations of the card were each caught on the phone.
- **Real article, real Gemini** (a TechCrunch article, with the default keys):
  - Reaching the end of the article showed the consent question inside the card. No request was logged before the answer (0 lines for `quiz_micro`).
  - **Not now** left the DataStore at 20 bytes with no consent key and kept asking.
  - **Agree** wrote the key (DataStore 47 bytes), the card showed "AI is writing a question…", then a Vietnamese question with four options and the label "Do AI soạn từ tiêu đề và phần xem trước; có thể sai". The log shows exactly one request, with the first key answering `API_KEY_INVALID` and the second succeeding.
- **Restore:** at the end Room, `-shm`, `-wal`, `commute_cast_prefs.xml`, `pairing_store.xml` and DataStore compared **byte-identical** to the backup (so the quiz consent key is gone again), the clean HEAD build is installed, the test APK is gone and the app is stopped. Full-screen test ads that appeared when I opened the app were closed with Back only; I never tapped "Continue to app".

## Found on the phone (not by the JVM tests)

1. **The cache did not survive reopening an article.** Opening the same article again made a second Gemini request. The reader creates a new `BrainRpgViewModel` each time (`hiltViewModel()`), so a cache inside it was empty every time. Fixed in `7c941d57` by making `QuizCache` a Hilt singleton, with a test that a new view model reuses the cache and a test that the annotation is present. **Not re-verified on the phone after the fix**: by then the shared keys were rate-limited (below).
2. **Two answers out of about 25 requests were rejected as "not JSON"**, and the card then showed "No AI question yet" with a Try again button (which is the designed behaviour for a failed answer). In one of them the answer had the shape of a JSON object (starts with `{`, ends with `}`, one array) and parsed fine in the next five runs. I added a temporary diagnostic (shape only, no text) and could not catch it again before the shared keys hit `RateLimited`. **I do not know the cause.** My offline probe shows `org.json` rejects a trailing comma, leading prose and an unescaped inner quote; I did not see which of those it was. The cause is unexplained.
3. **The first default key returns `API_KEY_INVALID`** (HTTP 400) on every request, so each quiz costs one wasted attempt before the second key. This is existing configuration (see FIX-09), not changed here.

## Not verified / limits

- **Not run on the phone:** answering correctly and getting +150 XP and the XP events; the double-XP and Rewarded Ad buttons on an AI question; switching the Privacy switch off and opening a new article (the declined card); airplane mode and Try again; the cache fix on a real reopening; a second device; any language other than Vietnamese on screen. I stopped calling Gemini because about 25 requests of mine had pushed the shared keys into `RateLimited`, and I did not want to hammer shared keys. The JVM and androidTests cover the declined, failed and retry states, not the phone flow.
- Only the title and up to 250 characters of the description are sent, so questions are narrow and many articles have only a title. Nothing can check that a question is true to the article; the check is structural. The AI can write two correct options or a wrong "correct" one. The label says so.
- Offline, out of quota, or a declined consent now means no quiz card content and no quiz XP until the AI works (chosen by the owner, no fallback). The old headline quiz is gone; restore it from git if a fallback is wanted.
- The cache is only in memory: it is lost when the app closes.
- `LazyColumn` may compose the quiz item a little before the last pixel of the article, so the request can start slightly early.
- "Only the title and preview are sent" is proven by JVM tests on the request body, not by capturing packets. "No content in the log" is protected by reading the code (the service logs only an exception type or a rejection reason); the gateway's existing logs print key tails and API error bodies as before.
- The default keys are shared and ship in the APK (FIX-09, deferred by the owner).
- The new strings are English in 36 locales.
