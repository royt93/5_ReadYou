# [FIX-19] Oversized RSS Body Forensics and Log Hygiene — Completion Report

- **Status:** Commit gốc đã push đến `origin/dev` (`b76d2234`, `d236f3a6`, `cff1472f`); bản sửa rà soát (2026-10-22) chưa commit/push.
- **Completed:** 2026-10-22 (bản sửa sau rà soát: 2026-10-22)
- **Audit:** 8/10. Backup forensics ran read-only and output only metadata/hash/counters; controlled Rome `SyndEntry.contents` replay confirmed the current RSS writer rejects synthetic oversize content; RSS ingestion no longer logs description/content or sensitive query values. **Exact historical writer remains unknown.**
- **Commits:** `b76d2234` plan; `d236f3a6` forensic script, Rome replay, RSS log redaction and report.

## Forensic evidence

Read-only script: `python3 tools/fix19_forensic.py`; SQLite database opened with URI `mode=ro`. It prints no article body, raw ID, title or complete link; only identifier/title hashes, URL hosts, field lengths, SHA-256 and character-shape counts.

Preserved row metadata:
- ID shape is local `accountId$UUID`, which is consistent with `RssHelper.article()` generating the ID.
- Feed URL is `https://www.npr.org/sections/news/`; article link host is `ondemand.npr.org` (audio asset).
- `rawDescription` is 0 UTF-8 bytes; `fullContent` is 12,324,490 chars / 15,890,764 UTF-8 bytes; SHA-256 `2ea6f03e5654eb3eda859d29a78e5111820c98b6fca14b0cf3461d8f752e18e1`.
- Body has 63,610 NULs, 1,713,674 U+FFFD replacement characters và 518,528 ký tự điều khiển C0 (loại LF/CR/tab, **bao gồm** NUL; loại thêm NUL còn 454,918). **Đính chính (rà soát 2026-10-22):** báo cáo gốc ghi 525,801 "other control chars" — đó là tổng mọi C0 kể cả LF/CR/tab do lỗi escape trong chuỗi loại trừ của script, không phải định nghĩa đã nêu. Body itself was never printed or copied into a fixture.

The local UUID makes `RssHelper.article()` plausible. Fever/Google Reader use remote-derived IDs, but `ReadLaterWorker` retains the same ID and can update `fullContent`. These facts do **not** prove which path wrote the historical row. FIX-18 now caps current writers; a synthetic Rome replay confirms that path, not historical attribution.

## Changes

- `RssHelper.article()` no longer interpolates `desc` or `content` in logcat. It logs selected source, feed metadata, content byte sizes and acceptance status. Entry URLs redact query/userinfo and truncate path.
- Oversize RSS rejection passes a sanitized URL to policy logging, so signed/query credentials are not included.
- `tools/fix19_forensic.py` reads the preserved backup strictly read-only and prints metadata only.
- No historical database was edited; no migration/cleanup was performed.

## Verification

- `python3 tools/fix19_forensic.py`: exit 0, metadata and stored hash matched expected backup record. Output did not contain body text or full title/identifier.
- `RssHelperAudioTest`: 6/6 pass, including synthetic direct-Rome oversize rejection and log sentinel/query-token redaction.
- Full `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin --rerun-tasks` with the configured test JVM agent: **BUILD SUCCESSFUL**, 1246 unit tests, 0 failures/errors/skipped. Instrumentation APK compile passed; no instrumentation suite was run (this change only affects log output and a synthetic unit path).
- No device test was needed or run for log redaction; historical emulator backup remains unchanged/read-only. No lint pass claimed.

## Sửa sau rà soát (2026-10-22)

- `RssHelper`: hai log từ chối oversized của `parseFullContent()` và log bỏ entry lỗi trong `searchFeed()` giờ dùng URL đã redact (bỏ userinfo/query/fragment); log entry lỗi chỉ ghi loại exception, không gắn throwable/message. Đây **không** phải scrub toàn ứng dụng; chỉ các nhánh đã xác định.
- `tools/fix19_forensic.py`: counter C0 đúng định nghĩa; kết quả thành công yêu cầu đồng thời ID local UUID, title, feed URL chính xác, `rawDescription` rỗng, độ dài/hash và các counter (NUL, U+FFFD, C0) khớp. Sai bất kỳ điều kiện nào hoặc lỗi đọc DB trả exit khác 0 và chỉ in loại lỗi. Output đổi sang JSON metadata.
- `tools/test_fix19_forensic.py`: self-test `unittest` stdlib (7 test) dùng dữ liệu tổng hợp, không cần backup lịch sử.
- Đã chạy `python3 tools/fix19_forensic.py` trên backup `mode=ro`: exit 0, `control_chars=518528`, hash khớp.
- Full: 1250 unit tests pass (`--rerun`, 0 failures/errors/skipped), `assembleDevDebug` và `compileDevDebugAndroidTestKotlin` thành công. Không chạy lint/instrumentation/device. Chưa commit/push bản sửa này.
- Plan FIX-19 gốc nhắc xoá `doc/task/todo/FIX-19_oversized-body-forensics.md`: file này chưa từng được tạo nên không có gì để xoá.

## Remaining uncertainty

The preserved byte pattern is consistent with malformed/binary-like content being stored as text, but this does not identify the producer. Exact historical origin remains open; any further source investigation needs a controlled reproduction from the original feed/provider or future metadata-only writer logs. No claim that this historical row was generated by `RssHelper.article()`.
