# Feature status

## 📋 Picked

_None._


## 🟡 In progress

_None. Campaign (EXC-01, EXC-08, EXC-03, EXC-07) complete._

## ✅ Implemented

- EXC-01 — AI Daily Smart Digest. Generation via Gemini, Room history, source links, unit/widget/integration tests, Tecno smoke verified. Committed locally (8c9301e0).
- EXC-08 — Second Brain Graph. On-device concept graph (strict concept matching), time slider, related articles, edge-to-edge safe. Unit + widget + integration + insets + perf tests; Tecno smoke verified.
- EXC-03 — Bionic Reading. Setting (default OFF) in Reading style; bolds leading ~40% of each word in article body, skips URLs/emails/code, keeps links and copy text intact. Unit + widget + integration + perf tests; Tecno smoke verified.
- EXC-07 — Bilingual Reader. ML Kit on-device translation (+language-id) shown italic under each paragraph; target = user choice in Reading style, default follows app locale (ML Kit lacks az/eu/lzh/ml/my/sr, so those pick a language manually). Model (~30 MB) auto-downloads on any network; per-paragraph cache + in-flight dedupe, failures not cached. Adds ~17 MB/ABI native lib (~67 MB universal APK) so ship as AAB. Unit + widget + integration tests; Tecno smoke verified.
