# [INGEST-03] Bản Tin Email & Newsletter Ingestion Bridge - DONE

- **Type:** Integration / Content Source
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [07. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau](../todo/07_UNIVERSAL_INGESTION_AND_SYNC.md)
- **Location:** `infrastructure/newsletter/`, `infrastructure/rss/RssHelper.kt`, `domain/sv/LocalRssSv.kt`, `ui/page/home/feed/subs/SubscribeViewModel.kt`
- **Status:** ✅ Completed / Done (2026-10-17)

## Completion Report

- **Audit Score:** `9.5 / 10`
- **Commit History:**
  - `40b9b3a3` — `feat(newsletter): add NewsletterSanitizer for email junk and tracking removal`
  - `76247577` — `feat(newsletter): add NewsletterUrlResolver for feed auto-discovery`
  - `ee55030c` — `feat(rss): integrate NewsletterSanitizer into RssHelper ingestion pipeline`
  - `318c9cc4` — `feat(subscribe): auto resolve newsletter URLs in SubscribeViewModel`
  - `a6119e11` — `fix(sync): skip network query for system read later feed`

## Tests Added

- `NewsletterSanitizerTest` (6 unit tests): tracking pixel removal, Substack/Ghost/Beehiiv widget removal, unsubscribe footer removal, content preservation, blank input.
- `NewsletterUrlResolverTest` (7 unit tests): platform detection, Substack/Medium/Ghost/Beehiiv URL → feed URL transform, HTML `<link rel="alternate">` auto-discovery.
- `RssHelperSanitizerTest` (2 Robolectric tests): `searchFeed` sanitizes rawDescription on ingestion, `parseFullContent` sanitizes full article after Readability4J.
- `SubscribeViewModelNewsletterTest` (1 Robolectric test): fallback resolver triggered when direct RSS search fails, second search with resolved URL succeeds.
- `LocalRssSvWebFeedSyncTest.systemReadLaterFeedIsNeverFetchedOverNetwork` (1 unit test): system Read Later feed is skipped during sync, no network call.

## Verification Summary

- Unit tests: **830 / 830 passing** (+17 new tests).
- Lint: `lintDevDebug` PASS (0 errors).
- Build: `assembleDevDebug` PASS.
- Live smoke test on `Pixel_10_Pro` (`emulator-5554`):
  - Nhập `https://platformer.substack.com` (link web, không phải RSS) vào Subscribe dialog: Resolver tự suy ra `https://platformer.substack.com/feed`, app hiện 20 bài viết của Platformer newsletter.
  - Bấm Subscribe: feed lưu vào Room DB, query confirm `platform=platformer.substack.com/feed`, 10 bài viết kiểm tra: `has_subwidget=0`, `has_pixel=0`, chỉ 2 bài có từ "unsubscribed" là nội dung bài báo thật (không false-positive).
  - Sau update `LocalRssSv`, log `queryRssXml[Read Later]: Unable to resolve host "local.rsscathub"` không còn xuất hiện trong APK mới.

## Audit Rubric (9.5 / 10)

- Correctness / Root cause: `3.0 / 3.0` — sanitizer đúng tầng Ingestion, resolver không phá vỡ RSS thường, fix Read Later sync không đụng logic bài viết.
- Test quality & RED/GREEN discipline: `2.0 / 2.0`
- Integration, UI & device evidence: `1.5 / 2.0` — smoke test đủ; không trực tiếp mở bài xem lên UI (gặp ad interstitial R4 nhiều lần, đã đóng bởi user).
- Regression & scope containment: `1.0 / 1.0`
- Maintainability & code standards: `1.0 / 1.0` — không magic strings, null-safe, tách `NewsletterPlatform` enum, secondary constructor cho testability.
- Verification & reproducibility: `1.0 / 1.0`
