# EXC-05b Task 1: Android 17 local-network permission measurement

**Date:** 2026-11-09 · **Device:** Pixel 7 Pro `2B051FDH3006MU`, Android 17 (API 37) · **App:** `targetSdk 37`, dev debug build

Method: start Local Sync › Send on the phone, read the session URL from the UI hierarchy, then `curl -m 10` that URL from the Mac (`192.168.21.83`, same Wi-Fi; phone `192.168.21.185`). A control listener started by `adb shell` (`toybox nc -l`) on the same phone is probed in the same run to separate "the network path is broken" from "the app is blocked".

| State | GET to the app listener | Control listener on the same phone |
|---|---|---|
| `ACCESS_LOCAL_NETWORK` **not declared** (build before this change) | `http=000`, timed out after 10.00 s | `http=200`, 0.027 s |
| Declared and **granted** (`pm grant`, `granted=true` confirmed with `dumpsys package`) | `http=200`, 0.128 s, `application/octet-stream` | not re-measured in this state |
| Declared and **revoked** (`granted=false`), **new** session | `http=000`, timed out after 10.00 s | `http=200`, 0.103 s |
| Declared and **revoked**, session started **while granted** and probed right after the revoke | `http=000`, 1.24 s | `http=200` |

In every state the app's socket was in LISTEN (`/proc/net/tcp`), so the failures are not a failed bind.

## What this establishes
- With `targetSdk 37`, the app's LAN listener is unreachable unless `ACCESS_LOCAL_NETWORK` is granted, and reachable once it is. The control listener shows the Wi-Fi path itself is fine. This turns the earlier hypothesis (documentation plus a single failing measurement) into a granted/not-granted comparison.
- Revoking while a session is already running also cuts reachability, so the app cannot assume a granted-at-start session stays reachable.

## What this does not establish
- The runtime prompt flow has not been exercised yet: the grant here was done with `pm grant`, not through the system dialog. That is covered by the smoke task.
- Only the outbound GET from a Mac was measured; the app's own outbound LAN connections (Receive) were not measured separately.
- Each state is a single run, not a repeated measurement.

## Device state at the time of writing
Room and DataStore on the Pixel were backed up to `/tmp/exc05b-pixel-backup` before the measurement and are **not yet restored**: a Lens Launcher instrumentation owned by another project was running on the shared phone, so no write was made. Restore (4 files, `cmp`) remains pending.
