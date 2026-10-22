# [FIX-19] Oversized RSS Body Forensics and Log Hygiene — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Determine which local ingestion path most plausibly produced the preserved 15.9 MB `fullContent`, reproduce the applicable writer with synthetic data, and stop RSS ingestion from logging article bodies.

**Architecture:** Treat `/Users/loitran/podcast_bak_emulator/Reader` as read-only; inspect only row metadata, byte counts, hashes and structural counters. Compare the row ID/source/fields with writer semantics, then make a controlled synthetic `SyndEntry.contents` replay through `RssHelper.article()` to verify FIX-18 rejection. Remove body-bearing RSS log fields and replace them with metadata-only diagnostics protected by tests. Do not alter any historical device/database row.

**Tech Stack:** Python stdlib `sqlite3` read-only forensic script; Kotlin, Rome 2.1.0, `RssHelper`, `NewsletterSanitizer`, FIX-18 `FullContentPolicy`, JUnit4, Robolectric, `ShadowLog`, `./gradlew`.

## Global Constraints

- The preserved DB `/Users/loitran/podcast_bak_emulator/Reader` is read-only evidence; do not modify it.
- Never print, log, snapshot, or commit body text from the 15.9 MB row. Only record ID format, field lengths, UTF-8 byte lengths, SHA-256, character-class counts, source link/feed metadata and timestamps.
- Do not claim exact historical writer attribution unless controlled evidence proves it. The local `1$UUID` ID and NPR feed make `RssHelper.article()` the leading hypothesis; ReadLaterWorker preserves ID and could update the body later, so row shape alone is not proof.
- Existing writers already enforce `FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES = 1_048_576`; do not re-open or weaken that policy.
- Remove logs containing full `desc`, `content`, raw or cleaned article text. Replace only with safe metadata: feed name/URL, entry link, title if it is already considered public metadata, description/content byte sizes, selected source (`content`/`description`), accepted/rejected outcome. Never log body excerpts.
- No database cleanup/migration, article deletion, provider security change, schema change or ad/VIP/IAP work.
- No device interaction required. If a smoke/device check is requested later, use the currently user-selected device only.

---

### Task 1: Record read-only forensic metadata and establish writer plausibility

**Files:**
- Create: `tools/fix19_forensic.py` (stdlib `sqlite3`, read-only URI); do not add a JVM SQLite JDBC dependency.
- Reference only (read-only): `/Users/loitran/podcast_bak_emulator/Reader`
- Reference: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Reference: `app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt`
- Reference: `app/src/main/java/com/mckimquyen/reader/domain/sv/GoogleReaderRssSv.kt`
- Reference: `app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt`

**Interfaces:**
- Script computes an in-memory metadata record only; output redacts article ID/title/link query by reporting hashes and host/path metadata. No body value is formatted or printed. Do not add a production forensic API.

- [x] **Step 1: Write/read forensic audit script (the backup already exists, so this is a passing characterization, not an artificial RED test)**
  - Open the backup with SQLite URI `mode=ro`; assert the query finds exactly one article by the known title and NPR feed URL, while output contains only hashes for private identifier/title and host names for URLs.
  - Assert ID matches local `accountId$UUID` shape; do not infer proof from ID alone.
  - Assert `rawDescription` byte length 0, `fullContent` character count 12,324,490, UTF-8 byte count 15,890,764, SHA-256 `2ea6f03e5654eb3eda859d29a78e5111820c98b6fca14b0cf3461d8f752e18e1`, NUL and replacement/control counts are recorded (numeric only).
  - Assert writer hypotheses are labelled: `RssHelper.article()` plausible from local UUID; Fever/Google Reader remote ID style differs; ReadLaterWorker reuses existing ID so cannot be excluded; exact source remains unproven.
  - If preserved backup path is unavailable, script exits with an explicit metadata-only error and does not create/copy/recreate body contents.

- [x] **Step 2: Run focused characterization**
  - Run: `python3 tools/fix19_forensic.py`
  - Expected: metadata assertions pass without printing body text; it exits nonzero if counts/hash drift or the preserved DB is absent.

- [x] **Step 3: Review writer data flow**
  - Confirm `RssHelper.article()` maps `SyndEntry.contents` to `cleanFull`, while `rawDescription` uses `content ?: description` and `shortDescription` may fall back to title when sanitizer rejects malformed content.
  - Confirm FIX-18 cap rejects over-limit body before persistence.
  - Compare Fever/Google Reader ID construction and ReadLaterWorker behavior; report evidence and limits, do not fabricate a replay of the original source.

- [x] **Step 4: Commit** — `test(rss): add controlled Rome replay and redact ingestion logs`.

---

### Task 2: Controlled synthetic Rome/RssHelper replay

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperAudioTest.kt` (or create `RssHelperOversizedContentTest.kt` if separation is cleaner)

**Interfaces:**
- Consumes: `FullContentPolicy` and `RssHelper.article(feed, accountId, syndEntry)` from FIX-18.
- Produces: controlled evidence that a synthetic `SyndEntry` with large `contents` reaches the RSS writer boundary, is rejected above 1 MiB, and preserves safe metadata; this validates the current path, not historical attribution.

- [x] **Step 1: Add controlled replay test (RED)**
  - Construct a `SyndEntryImpl` and `SyndContentImpl` directly (avoid invalid XML control characters) with synthetic body data shaped by counts only; use a named 1 MiB + 1 UTF-8 payload fixture.
  - Set title, description and link to a synthetic test entry; invoke internal `RssHelper.article()`.
  - Assert `fullContent == null`, title/link preserved, raw/short descriptions remain safe, and no fixture body sentinel appears in logs.
  - Add an at-limit counterpart to confirm byte-identical storage at exactly the policy limit only if the helper’s sanitizer is structurally appropriate; otherwise let Task 1’s policy tests cover exact-cap acceptance.

- [x] **Step 2: Run focused test (RED)**
  - Run: `./gradlew testDevDebugUnitTest --tests "*RssHelperOversizedContentTest"` (or selected class name).
  - Expected: an assertion fails if the helper does not reject the synthetic body or if metadata changes.

- [x] **Step 3: Verify current FIX-18 behavior, do not add another cap**
  - The production writer already applies `FullContentPolicy.accept(cleanFull)`. If the test fails, inspect the synthetic `SyndContent` representation and sanitizer result; do not weaken the cap or print the body.

- [x] **Step 4: Run GREEN** and commit — `test(rss): replay oversized content through Rome writer boundary`.

---

### Task 3: Remove body leakage from RSS logs

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperAudioTest.kt` or `RssHelperDescriptionTest.kt`

**Interfaces:**
- Replaces the current `Log.i("RLog", "request rss: ... desc: $desc ... content: $content")` with metadata-only logging. No body payload is interpolated.

- [x] **Step 1: Add failing log-redaction test (RED)**
  - Use a synthetic entry with unique sentinel strings in both description and `SyndEntry.contents`.
  - Capture Robolectric `ShadowLog` output around `RssHelper.article()` (clear logs first using supported API).
  - Assert log contains non-sensitive diagnostics needed for debugging (entry/feed URL, title or byte lengths, whether content/description was selected) and does **not** contain either body sentinel, nor substrings long enough to expose content.
  - Ensure oversize rejection still logs the provider/source, byte count, action but not body (covered partly by `FullContentPolicyTest`; assert here at RSS boundary).

- [x] **Step 2: Run focused test (RED)**
  - Run: `./gradlew testDevDebugUnitTest --tests "*RssHelperAudioTest" --tests "*RssHelperDescriptionTest"`
  - Expected: current full-body `desc/content` log makes the sentinel assertion fail.

- [x] **Step 3: Implement minimal redaction**
  - Compute `contentBytes`/`descriptionBytes` via UTF-8 bytes; log only these sizes and selected source. Remove `desc` and `content` values from the log message.
  - Keep feed URL/link/title only if treated as public RSS metadata; never include query credentials from signed URLs—if URL contains userinfo or sensitive query keys (`key`, `token`, `auth`, `password`, `signature`), log origin/path without query or omit URL.
  - Do not change parsing, sanitization or article mapping.

- [x] **Step 4: Run focused tests GREEN**, then commit — `fix(rss): redact article bodies from ingestion logs`.

---

### Task 4: Documentation of conclusion and remaining uncertainty

**Files:**
- Create: `doc/task/done/FIX-19_oversized-body-forensics_DONE.md`
- Modify: `doc/task/todo/01_FIX_BUGS_AND_STABILITY.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/FIX-19_oversized-body-forensics.md`

- [ ] **Step 1: Record exact evidence**
  - Include preserved row metadata, byte counts, SHA-256, shape counters only; never body text.
  - Say `RssHelper.article()` is confirmed as a controlled synthetic route and plausible for the local UUID row, but not conclusively proven historical writer. Mention `ReadLaterWorker` preserves the ID and remains an alternative unless a source/call log proves otherwise.
  - Record log-redaction change and test outcome.
  - State historical database untouched; no auto cleanup.

- [ ] **Step 2: Validation**
  - Run: `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`.
  - Sum fresh JUnit result XML and report actual counts. Use repository MockK agent configuration; do not claim lint passed unless it is run successfully.

- [ ] **Step 3: Update indices**
  - Move FIX-19 to Recently implemented only when its investigation and logging fix are complete; otherwise leave it In progress with uncertainty clearly stated.
  - Change FIX epic row and remove todo only after tests/build pass.

- [ ] **Step 4: Commit** — `docs: finalize FIX-19 oversized body forensics report`. Do not push unless requested.

## Device note

No device run is needed to inspect the preserved backup or verify log redaction. If a future controlled feed smoke is requested, first check connected devices; prior session target was Tecno and a later user overrode to Pixel for an earlier task, so ask/obey the current target rather than assuming. Never open the 15.9 MB body in UI or copy it into a fixture.


## Progress log (2026-10-22)

- Read-only forensic script `python3 tools/fix19_forensic.py` matched the preserved backup metadata/hash. It emits only hashed identifiers/title, URL hosts and numeric shape counters; no body text. Writer attribution remains unknown.
- Controlled direct Rome `SyndEntry.contents` replay confirms FIX-18 rejects synthetic oversized content and preserves metadata. RSS ingestion logs now record source, byte counts and a sanitized URL; test proves description/content sentinels and token query are absent.
- Focused RSS tests pass. Full host verification: 1246 tests, 0 failures/errors/skipped; `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` pass.
- No device verification was necessary or run; no database was modified. Historical backup is still read-only.
