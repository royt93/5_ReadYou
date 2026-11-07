# EXC-05a (partial; EXC-05 remains in progress)

**Date:** 2026-11-07  
**Branch:** `dev`  
**Scope:** Manual, one-way, user-confirmed sync over a trusted LAN. This does **not** complete the original EXC-05 acceptance criteria.

## Implemented

- Sender builds a strict allowlisted package: feed URL/name/group label and article link/read/star/readAt state only. No IDs, title, body, notes, read position, credentials, API keys, or DataStore values enter the payload.
- Random 128-bit pairing secret, shown on the sending screen and transferred separately from the session URL. PBKDF2-HMAC-SHA256 310,000 iterations, AES-GCM 256-bit key, 12-byte nonce, 128-bit tag; versioned `RCS1` envelope with 16-byte salt. Secret never enters the HTTP request or URL.
- Receiver strictly checks version, exact keys, URLs, duplicates, counts and package byte cap before DB writes. Feed URLs normalize without double-encoding percent escapes.
- Preview precedes explicit Apply confirmation. Merge is monotonic: unread uses AND, starred uses OR, readAt uses earliest non-null value. Apply recomputes the plan inside one Room transaction, updates only three article-state columns, creates missing feeds in the default group, and is Local-account-only. Room remains v20.
- Sync-only HTTP session reuses the bounded `MirrorServer` socket lifecycle but routes only to `/s/<token>` and returns raw ciphertext as `application/octet-stream`. 5-minute token, strict RFC1918 IPv4 receiver URL, redirects disabled, body cap, generic errors, and explicit Stop/lifecycle cleanup.
- Local Sync screen in Settings uses Material You, `BaseScaffold`/Back, safe drawing insets, preview and confirmation. New resource keys exist in all 38 maintained locale files (EN/VI authored; others English fallback).

## Verification

- Full unit suite after Stop-session fix: **2,219 tests, 0 failures/errors**.
- Focused UI/network/domain matrix after Task 8: **128 tests, 0 failures/errors**.
- Task 8 parity + Compose page tests: **17 tests, 0 failures/errors** (11 Compose + 6 existing parity tests).
- Lint: **0 errors, 2,463 warnings**. No lint hits for EXC-05a UI/files/strings, no new `UnusedResources` or `PluralsCandidate`. Local Android SDK 37 metadata still emits `NumberFormatException` (`api-versions.xml` contains `37.0`); lint process completed successfully.
- `assembleDevDebug` and `assembleDevDebugAndroidTest` completed successfully.
- BG6 `118743744X002560`, Android 13/API 33, instrumentation: **4/4 tests passed**. Covers real JCA crypto, loopback HTTP, in-memory Room, Local apply and Fever isolation, tampered/wrong secret rejection, expiry, and PBKDF2 timing. On BG6 PBKDF2 310k measured ~3,829 ms encrypt / ~4,178 ms decrypt in one clean run. A later clean run output `OK (4 tests)` but its per-test log was not retained. No persistent app database or DataStore was used by instrumentation.
- Mac peer is **ciphertext-transport-only**. Python `cryptography`/AESGCM was absent; no package was installed and no handwritten crypto was used. Real Mac `curl` fetched the 159-byte `RCS1` envelope from BG6 via Wi-Fi. Response was HTTP 200, `application/octet-stream`, `Content-Length: 159`, `Cache-Control: no-store`; repeated fetch returned identical bytes. No plaintext marker was found. Wrong token, mirror routes, unexpected paths/query and POST were rejected (404/405). Mac did not decrypt, preview or apply; this does not prove Android-to-Android compatibility.
- BG6 stop smoke initially exposed a real wiring bug: Stop showed Idle but server kept serving ciphertext. Fixed in `05cf9606`; subsequent BG6 Send -> Stop made three Mac GETs fail with HTTP `000`, and `mirror-accept` thread disappeared. Added regression Compose tests for Stop and cancel-while-starting; wrong callback mutation makes Stop test red.
- BG6 expiry real-device smoke is **inconclusive**. HiOS froze the background process while app was not foreground. Do not interpret the later HTTP `000` as token expiry proof.
- Fresh backup `/tmp/exc05a-bg6-backup` was made before smoke. `Reader` (274,432 bytes), `Reader-journal` (0), and DataStore (50) were restored and each `cmp` matched. Backup copy integrity check `ok`, foreign-key violations `0`, schema `20`. Original `stay_on_while_plugged_in=0` restored. No DataStore file was deleted.

## Security review

Static focused review found no confirmed HIGH/MEDIUM vulnerabilities meeting the review's confidence threshold. The preview lists counts, not individual feed URLs; reviewer filters rated this below threshold (6/10 initial, 2/10 after comparing existing subscribe/OPML behavior and the spec threat model). Secret keyboard type was dropped (1/10). This was a static review, not hostile-peer penetration testing.

## Explicitly incomplete vs EXC-05

- No NSD/mDNS discovery, automatic sync, background service, two-way/concurrent sync, or three-second two-Android acceptance test.
- No two-Android compatibility test. BG6 + Mac is a simulated transport peer only.
- Mac decrypt/validate/preview/apply remains unverified because no trusted AES-GCM implementation was installed on Mac.
- BG6 token expiry observed by instrumentation; real-device expiry smoke inconclusive due HiOS background freezing.
- No data is staged for missing receiver articles; they are skipped. HTTP exposes IP/session URL, timing, and transfer size; only payload contents are encrypted. Pair only on a trusted local network and transfer the secret separately.

**Conclusion:** EXC-05a is a partial manual encrypted LAN slice. Keep `doc/task/todo/EXC-05_local-p2p-sync.md` open and the epic **In progress**; do not mark original EXC-05 done.
