# EXC-05b (partial; EXC-05 remains in progress)

**Date:** 2026-11-09  
**Branch:** `dev`  
**Scope:** One-time pairing, NSD auto-discovery of paired peers, and the Android 17 local-network permission. This does **not** complete the original EXC-05 acceptance criteria. `doc/task/todo/EXC-05_local-p2p-sync.md` stays open.

## Implemented

- Pairing: 128-bit secret generated once, stored in `pairing_store` (SharedPreferences file) wrapped by a non-exportable Android Keystore AES-256-GCM key (`rsscathub_pairing_v1`). Excluded from `backup_rules.xml` and from both `data_extraction_rules.xml` paths (cloud backup, device transfer). Max 20 peers, label 1-60 chars without control characters.
- Session token: HMAC-SHA256(key = secret bytes, `"RCS-session-v1"` || 16-byte nonce) truncated to 16 bytes, 32 lowercase hex. The sender serves under that token (`startSyncWithToken`, TTL still 5 minutes) and advertises the nonce, never the token or secret.
- NSD: service type `_rsscathub._tcp`, random service name `rsscathub-<12 hex>`, TXT only `v=1` and `n=<nonce>`; hosts that are not RFC1918 IPv4 and malformed records are dropped. Real `NsdManager` adapter with API-level branching (API 26-33 legacy overloads, Executor overloads from 33, `registerServiceInfoCallback` from 34).
- Receiver marks a peer CONFIRMED only after the AES-GCM tag verified. A sender stores the peer UNCONFIRMED. "Forget" removes the entry.
- Android 17: `ACCESS_LOCAL_NETWORK` declared; `LanGate`/`AndroidLanGate` gate Local Sync and Second Screen, with a permission request and an open-settings button. New `ReadNewsManifestTest` allowlist entry (owner approved in the spec).
- UI: paired-device list, "Pair a new device" with label, nearby state, Receive / Mark as paired / Forget, permission error actions. 20+ new string keys in all 38 locale files (EN and VI authored, others English).
- Room stays v20. No new dependency. No custom crypto (JCA only), no PAKE. The EXC-05a manual URL + secret path is unchanged and its tests stay green.

## Verification

- Full unit suite, `--rerun-tasks`: **2,595 tests, 0 failures, 0 errors** (370 result files).
- Lint: **0 errors, 2,463 warnings** (same as baseline), 0 hits in EXC-05b files. `assembleDevDebug` and `assembleDevDebugAndroidTest` succeed.
- Mutation checks, each caught by exactly one test: confirm-before-decrypt, Second Screen permission gate off, always-visible confirm button, late registration failure from an older session.
- **BG6 `118743744X002560` (Android 13, API 33), real device:**
  - `KeystoreSecretWrapperDeviceTest`: **6/6** on the real Keystore (round trip, distinct wraps, no plain secret in output, tampered and truncated input rejected, key not exportable).
  - `PairingNsdDeviceTest`: **2/2** on real `NsdManager`: advertises, finds itself with the exact nonce and a private IPv4 host, and can advertise again after `stop()`.
  - Smoke through the real UI: Settings -> Local Sync -> "Pair a new device" produced a session URL and secret. Mac `dns-sd -B/-L` saw `_rsscathub._tcp` with TXT exactly `n=<32 hex>` and `v=1`. Mac `curl` with the token **computed independently in Python** from the secret and the advertised nonce returned **HTTP 200, 159 bytes**; a wrong token returned **404**. After tapping Stop, `curl` failed to connect (exit 7) and `dns-sd` saw no service. The paired entry appeared as "Waiting for the other device to receive once" and Forget removed it; `pairing_store.xml` held `{"entries":[]}` and no plain secret.
  - Backup before and `cmp` after: `Reader` (274,432), `Reader-journal` (0) and `settings.preferences_pb` (50) identical. DataStore never deleted.
- A real bug found by the device test, not by unit tests: `PeerDiscovery.advertise()` read the registration result synchronously, but `NsdManager` reports it later. The fake in the unit tests was synchronous and hid it. Fixed in `6561abf2` with three tests that went red first for the right reason.

## Not verified (honest limits)

- **Android 17 permission on a real S24U:** the Samsung S24 Ultra fell off adb during the run. The only LAN-permission measurement is the earlier Pixel one (undeclared or revoked: timeout; granted: HTTP 200, see `docs/superpowers/notes/2026-11-09-exc-05b-lan-permission-measurement.md`). The real permission dialog, revoke case and the API 37 `registerServiceInfoCallback` path were not run by the final code. BG6 is API 33, so it exercised only the Executor overloads.
- **NSD on API 26-32 and API 34+ branches** are compiled but untested.
- **No two real Androids.** The pair-then-receive round trip (`receiveFromPaired` against a real peer) was checked with fakes and the Mac, not with a second phone. The Mac is transport only and did not decrypt.
- The Settings UI "Receive from nearby peer" button was not tapped against a real second device.
- No forward secrecy: a leaked pairing secret decrypts any package captured while it is valid, until the user forgets the device. Stated in the UI.
- HTTP still exposes the IP, URL, timing and size. Pair only on a trusted network.
- Pixel `2B051FDH3006MU` still holds the measurement backup and is not restored (`/tmp/exc05b-pixel-backup`). S24U `R5CX613VZBR` still has the dev and test APK installed (`/tmp/exc05b-s24u-backup`). Neither was touched after the lock moved to BG6.
- Whole-EXC-05 gaps remain: automatic and background sync, two-way merge, the three-second two-Android acceptance test.
