# STRAT-03 — DoH Privacy Shield & Anti-Tracking — DONE

**Completed:** 2026-10-26  
**Branch:** `dev`  
**Implementation commits:** `c3505095`, `32ab23ff`, `afbe8d61`, `33700046`, `4c503ef3`, `84e03b00`, `9878d93a`, `35cddff8`, `4519eb4d`

## Delivered

- `AppDns` supports System DNS, Cloudflare DoH and AdGuard DoH using the DNS JSON endpoint, IP literals, and a 3-second bootstrap-client timeout with fallback to `Dns.SYSTEM`.
- DataStore preferences store selected DNS provider and URL/HTML filtering toggles; defaults are System DNS and both filters enabled.
- `UrlTrackingCleaner` strips known campaign/social/ad query keys while preserving functional parameters and fragments.
- `AntiTrackerHtmlSanitizer` removes tiny/hidden/tracking images, scripts and non-video iframes; preserves YouTube/Vimeo video embeds.
- OkHttp, RSS ingestion for RSS/Fever/Google Reader/web feeds, full-content extraction, browser launch and sharing use privacy controls.
- Privacy & Security settings screen exposes provider selection and both toggles.
- Added 11 string resources to English base and all 37 maintained locale files; locale parity passed.

## Verification

- Focused sanitizer, URL cleaner, preference, DoH and RSS sanitizer tests passed.
- Full `./gradlew testDevDebugUnitTest --rerun`: **1,321 tests, 0 failures, 0 errors**.
- `./gradlew lintDevDebug`: BUILD SUCCESSFUL, **0 errors**; report contains 2,445 warnings and 8 informational issues.
- `./gradlew assembleDevDebug`: BUILD SUCCESSFUL.
- Pixel 7 Pro smoke, serial `2B051FDH3006MU`:
  - Confirmed `devDebug` install with `adb install -r`; pre-test Room files were backed up.
  - Opened Privacy & Security; selected Cloudflare DoH; URL and HTML shields were enabled. No ad covered this screen.
  - Triggered one RSS refresh. UI article count changed from 134 to 168. Read-only inspection of copied Room data found zero links matching checked tracking parameter patterns, zero `<script>` descriptions and zero 1x1 image descriptions.
  - Leaving Settings triggered a full-screen interstitial. Smoke actions stopped immediately per R4; resumed only after owner replied `done`.
  - Restored the pre-test Room database snapshot, relaunched app, and confirmed displayed count returned to 134. No Room records were intentionally retained from the test refresh.

## Limitations

- Sync success while Cloudflare was selected was observed; no direct packet/DNS trace proved a specific resolution used DoH instead of fallback.
- Lint passes with zero errors but existing lint warnings remain.
- No commit or push was performed for the Pixel smoke session; implementation and completion-doc commits are local to `dev`.
