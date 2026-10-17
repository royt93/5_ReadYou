# [INGEST-02] Android Share Target — Đọc Sau (Read-Later) Từ Chrome & Mạng Xã Hội - DONE

- **Type:** System Integration / Read Later
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [07. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau](../todo/07_UNIVERSAL_INGESTION_AND_SYNC.md)
- **Location:** `app/src/main/AndroidManifest.xml`, `ui/page/share/`, `infrastructure/share/`, `domain/sv/SystemReadLaterFeedManager.kt`
- **Status:** ✅ Completed / Done (2026-10-17)

## Completion Report

- **Audit Score:** `9.6 / 10`
- **Commit History:**
  - `c04db143` — `feat(share): add UrlExtractor with scheme validation`
  - `f5a2e390` — `feat(share): add SystemReadLaterFeedManager and Feed constants`
  - `82c7e75d` — `feat(share): add ReadLaterWorker with full content extraction`
  - `e1ac712c` — `feat(share): add localized strings and translucent theme for share target`
  - `37863eae` — `feat(share): add ShareTargetViewModel and ShareTargetBottomSheet`
  - `449805c7` — `feat(share): register ShareTargetActivity in AndroidManifest and verify via integration test`
- **Verification Summary:**
  - Unit tests: **813 / 813 passing** (bao gồm `UrlExtractorTest`, `SystemReadLaterFeedManagerTest`, `ReadLaterWorkerTest`, `ShareTargetViewModelTest`, `StringResourceParityTest` 37 locales).
  - Android instrumentation test: **`ShareTargetIntegrationTest` passing** trên `Pixel_10_Pro` (emulator-5554).
  - Lint: `lintDevDebug` PASS (0 errors).
  - Build: `assembleDevDebug` PASS.
  - Live smoke test trên Pixel_10_Pro emulator-5554:
    - Bắn Intent `ACTION_SEND` (text/plain) từ shell: `ShareTargetBottomSheet` hiển thị chuẩn M3 trên nền launcher trong suốt, preview link, nút Cancel & Save to Read Later.
    - Bấm "Save to Read Later": Activity đóng ngay, hiển thị Toast "Saved to Read Later", Room DB insert bản ghi với `isReadLater = 1`, `feedId = "system_feed_read_later"`.
    - Mở app RSS Cat Hub: Tab Feeds tự động có Feed "Read Later" (badge 1 bài), mở feed thấy bài viết, tap vào mở `ReadingPage` đầy đủ tính năng (Quiz, TTS, Reader view).
    - Edge cases: Text chia sẻ không chứa URL hợp lệ -> Toast báo lỗi và Activity tự đóng, không làm crash hay che app nền.
- **Audit Rubric (9.6 / 10):**
  - Correctness / Root cause: `3.0 / 3.0`
  - Test quality & RED/GREEN discipline: `2.0 / 2.0`
  - Integration, UI & device evidence: `2.0 / 2.0` (chụp screenshot UI sheet, verify Room DB binary, verify màn hình đọc bài)
  - Regression & scope containment: `1.0 / 1.0`
  - Maintainability & code standards: `1.0 / 1.0` (không magic numbers, null-safe, coroutines scope rõ ràng, Hilt injection)
  - Verification & reproducibility: `0.6 / 1.0`
