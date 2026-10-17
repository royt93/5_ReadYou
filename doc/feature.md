# Feature status

## 📋 Picked

_None._


## 🟡 In progress

- INGEST-03 — Bản Tin Email & Newsletter Ingestion Bridge: Giải mã và chuẩn hóa các nguồn bản tin Substack, Ghost, Beehiiv, Medium sang feed RSS sạch, tự động loại bỏ chữ ký email, tracking pixel, nút unsubscribe và form subscribe rác.

## ⏸️ Deferred

- FIX-09 — Gemini API key hardcoded in APK (P0 security). **Deferred on 2026-10-13 by the owner; re-raise around 2026-11-13.**
  - Fact: `GeminiConfig.kt` ships several default keys only XOR+Base64 with a fixed PAD, so anyone decompiling the APK can recover them. Every AI feature (summary, Deep Read, Mind Map, Daily Digest, CommuteCast) resolves keys through `GeminiSummaryService.resolveApiKeys()`: the user's BYOK key first, then these defaults.
  - Removing the defaults without a replacement disables AI for every user who has not entered their own key (BYOK users are unaffected; offline highlights still work).
  - Options discussed: (a) only reduce risk (restrict keys to package+SHA-1 and Generative Language API in Google Cloud, set quotas, document it), (b) BYOK only, (c) own proxy backend, (d) Firebase AI Logic + App Check. No option chosen yet.
  - Revoking/rotating keys in Google Cloud Console is a manual owner action, not done here.

## ✅ Implemented

- INGEST-02 — Android Share Target: Lưu bài đọc sau (Read Later) từ Chrome, Twitter/X và app ngoài qua Android Share Sheet. `ShareTargetActivity` trong suốt + Compose ModalBottomSheet xác nhận URL và tiêu đề, tự động đảm bảo System Feed "Đọc sau" (`id = "system_feed_read_later"`, `sourceType = "READ_LATER"`), lưu placeholder `Article(isReadLater = true)` vào Room và dispatch `ReadLaterWorker` (WorkManager) tải ngầm toàn văn qua `RssHelper.parseFullContent` (giới hạn 1 MiB, MIME validation, chống NUL byte). Đủ 37 locale strings, 813 unit tests pass, instrumentation test pass trên Pixel_10_Pro emulator-5554, live smoke test verified. Audit score 9.6/10. Committed locally, pushed.
- INGEST-01 — Web-to-RSS Generator (static HTTPS list pages -> local `WEB` feed with preview/confirm). MVP supports static HTML with at least 3 candidates. Scraper runs on isolated hardened `OkHttpClient` (no redirects, strict TLS, DNS-pinned, bounded 1 MiB streaming). Room schema v14 adds `sourceType`/`sourceUrl`. Local sync dispatches `WEB` feeds through `WebFeedScraper.refresh()` and reuses Room dedupe (`insertListIfNotExist`). Full-content fetch hardened with 1 MiB limit, HTML/XHTML MIME check, and NUL byte rejection; `WEB` feed image background prefetch skipped until per-image DNS pinning exists. Live smoke verified on Pixel_10_Pro emulator (NPR News, NPR Politics, 301-redirect reject on Guardian, cancel/confirm UI states). 802 unit tests, 5 Android instrumentation tests (Room preview persistence, full-content safety, Room v14 migration), lint, and builds pass. Audit score 9.4/10. Committed locally, pushed.
- RPG-07 — Brain RPG moved from SharedPreferences to Room (single device-wide profile; legacy prefs imported once, transactionally, and left untouched as a rollback copy). Unique reward ledger (READ/QUIZ/DOUBLE) applied atomically with XP; a repeat qualifying read/correct quiz refreshes practice time without re-awarding. Reading reward needs >= 80% of the Reader-appended body items AND 30 s foreground dwell (replaces the 75% rule). Category XP is shown decayed (floor(base x 0.9^fullWeeks)) while base XP/level never decay; one daily summary notification (fixed ID, opens Brain RPG) and a one-category rewarded recovery. Unit + widget + integration tests; Tecno automated runs green. Committed locally, not pushed.
- EXC-01 — AI Daily Smart Digest. Generation via Gemini, Room history, source links, unit/widget/integration tests, Tecno smoke verified. Committed locally (8c9301e0).
- EXC-08 — Second Brain Graph. On-device concept graph (strict concept matching), time slider, related articles, edge-to-edge safe. Unit + widget + integration + insets + perf tests; Tecno smoke verified.
- EXC-03 — Bionic Reading. Setting (default OFF) in Reading style; bolds leading ~40% of each word in article body, skips URLs/emails/code, keeps links and copy text intact. Unit + widget + integration + perf tests; Tecno smoke verified.
- EXC-07 — Bilingual Reader. ML Kit on-device translation (+language-id) shown italic under each paragraph; target = user choice in Reading style, default follows app locale (ML Kit lacks az/eu/lzh/ml/my/sr, so those pick a language manually). Model (~30 MB) auto-downloads on any network; per-paragraph cache + in-flight dedupe, failures not cached. Adds ~17 MB/ABI native lib (~67 MB universal APK) so ship as AAB. Unit + widget + integration tests; Tecno smoke verified.
- RPG-08 — Brain RPG XP can be earned only once per article (reading and correct quiz), persisted so killing the app or recreating the ViewModel cannot farm XP. A wrong quiz answer keeps the reward available for a retry. The repository decides (`awardReadingXpOnce` / `submitQuizResultOnce`) and the +XP toast only shows when XP was really awarded. Unit + ViewModel + device integration tests; mutation (guards removed) turns 4 repository tests red.
- RPG-08 follow-up — the "watch an ad to double quiz XP" bonus can be claimed once per article, only after a correct answer, persisted, and the button stays hidden after a restart (`awardDoubleXpForQuizOnce`, `isDoubleRewardClaimed`; the rewarded ad is not even offered when already claimed). Unit + ViewModel + card widget + device integration tests.
