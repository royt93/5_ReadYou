# Feature status

## 📋 Picked

_None._


## 🟡 In progress

_None. Campaign (EXC-01, EXC-08, EXC-03, EXC-07) complete._

## ⏸️ Deferred

- FIX-09 — Gemini API key hardcoded in APK (P0 security). **Deferred on 2026-10-13 by the owner; re-raise around 2026-11-13.**
  - Fact: `GeminiConfig.kt` ships several default keys only XOR+Base64 with a fixed PAD, so anyone decompiling the APK can recover them. Every AI feature (summary, Deep Read, Mind Map, Daily Digest, CommuteCast) resolves keys through `GeminiSummaryService.resolveApiKeys()`: the user's BYOK key first, then these defaults.
  - Removing the defaults without a replacement disables AI for every user who has not entered their own key (BYOK users are unaffected; offline highlights still work).
  - Options discussed: (a) only reduce risk (restrict keys to package+SHA-1 and Generative Language API in Google Cloud, set quotas, document it), (b) BYOK only, (c) own proxy backend, (d) Firebase AI Logic + App Check. No option chosen yet.
  - Revoking/rotating keys in Google Cloud Console is a manual owner action, not done here.

## ✅ Implemented

- EXC-01 — AI Daily Smart Digest. Generation via Gemini, Room history, source links, unit/widget/integration tests, Tecno smoke verified. Committed locally (8c9301e0).
- EXC-08 — Second Brain Graph. On-device concept graph (strict concept matching), time slider, related articles, edge-to-edge safe. Unit + widget + integration + insets + perf tests; Tecno smoke verified.
- EXC-03 — Bionic Reading. Setting (default OFF) in Reading style; bolds leading ~40% of each word in article body, skips URLs/emails/code, keeps links and copy text intact. Unit + widget + integration + perf tests; Tecno smoke verified.
- EXC-07 — Bilingual Reader. ML Kit on-device translation (+language-id) shown italic under each paragraph; target = user choice in Reading style, default follows app locale (ML Kit lacks az/eu/lzh/ml/my/sr, so those pick a language manually). Model (~30 MB) auto-downloads on any network; per-paragraph cache + in-flight dedupe, failures not cached. Adds ~17 MB/ABI native lib (~67 MB universal APK) so ship as AAB. Unit + widget + integration tests; Tecno smoke verified.
- RPG-08 — Brain RPG XP can be earned only once per article (reading and correct quiz), persisted so killing the app or recreating the ViewModel cannot farm XP. A wrong quiz answer keeps the reward available for a retry. The repository decides (`awardReadingXpOnce` / `submitQuizResultOnce`) and the +XP toast only shows when XP was really awarded. Unit + ViewModel + device integration tests; mutation (guards removed) turns 4 repository tests red.
- RPG-08 follow-up — the "watch an ad to double quiz XP" bonus can be claimed once per article, only after a correct answer, persisted, and the button stays hidden after a restart (`awardDoubleXpForQuizOnce`, `isDoubleRewardClaimed`; the rewarded ad is not even offered when already claimed). Unit + ViewModel + card widget + device integration tests.
