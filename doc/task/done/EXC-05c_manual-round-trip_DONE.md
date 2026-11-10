# EXC-05c (partial; EXC-05 remains in progress)

**Date:** 2026-11-10  
**Branch:** `dev`  
**Scope:** Two manual legs between two already paired phones (A sends / B receives, then B sends back / A receives), with a role-switch UI. Each phone previews and confirms Apply for its own database only. This does **not** complete the original EXC-05 acceptance criteria. `doc/task/todo/EXC-05_local-p2p-sync.md` stays open.  
**Spec / plan:** `docs/superpowers/specs/2026-11-10-exc-05c-manual-round-trip-design.md`, `docs/superpowers/plans/2026-11-10-exc-05c-manual-round-trip.md`.

## Implemented

- `ReplyPeer(accountId, peerId)` in `LocalSyncViewModel`: remembered only after a paired receive (or a labelled first receive) authenticated and its preview was built. Kept after a user Cancel and after a successful Apply. Cleared on lifecycle stop, a failed Apply, a different account, forgetting that peer, starting any other task, `shutdown` and `onCleared`. Never stored on disk and never holds a secret.
- `waitForReply()`: for a paired send session only, closes the server and advertising, cancels the jobs and starts looking for peers. Opens no new server.
- `replyToPeer(accountId)`: reuses `servePaired`, so the snapshot is taken **at that moment** (after the Apply or Cancel), under the stored secret, with no secret shown and no Apply on either phone. Refuses a missing or `needsRepair` peer without snapshotting.
- `LocalSyncPage`/`HomeEntry`: role hint and **Stop and wait to receive back** on a paired send; after Apply or Cancel a **Send back to ‹name›** block with a different note for each case, an instruction to open Receive on the other phone, and a line about the merge limit. 7 new strings in all 38 locale files. No string claims both phones are finished.
- Layout fix found on a phone: the actions of a paired-device row are stacked instead of three side by side.
- No change to `LocalSyncPlanner`, `LocalSyncRepository`, `MirrorServer`, `SyncSessionTransport`, the protocol, Room (still v20) or dependencies.

## Verification (JVM)

- Full unit suite on the **final code** (`--rerun-tasks`, HEAD `635f969a`, clean tree): **2,673 tests, 0 failures, 0 errors, 0 skipped**, 374 result files, none stale. Lint (`--rerun-tasks`): **0 errors, 2,463 warnings** (baseline), 0 hits in `p2psync`/`LocalSync` files. `assembleDevDebug` and `assembleDevDebugAndroidTest` succeeded. (An earlier full run before the two device fixes gave 2,666 tests.)
- New tests: 25 ViewModel (`LocalSyncReplyViewModelTest`), 14 Compose (`LocalSyncPageReplyTest`), 5 layout (`LocalSyncPeerRowLayoutTest`), 11 integration (`LocalSyncRoundTripIntegrationTest`).
- Integration test uses two real in-memory Room databases, two real `MirrorServer` sockets and the real `SyncSessionTransport`: shared stories converge with the earliest `readAt`, feeds are added once and keep the receiver's name and group, articles missing on the receiver are skipped both ways, other accounts and article content are untouched, preview writes nothing, a cancelled first leg leaves the database unchanged, an applied leg is not rolled back, a re-run changes nothing, a wrong token is refused, and a stopped server refuses connections.
- Mutation checks, each caught by the intended test: reply context kept on lifecycle stop / never remembered / remembered for a manual receive / wiped before the busy guard / wiped by forgetting another peer; wait keeps the server / wait allowed for a manual send / account check removed / busy guard removed / needsRepair check removed / reply uses a manual send; UI: wait button for a manual send, same note after cancel, reply always enabled, reply block in every state, tab not switched; production: unread merged with OR, `readAt` with max, stars with AND, token check removed, new feeds not inserted, snapshot dropping stars.
- Honest notes on the tests: the integration test describes existing behaviour so it passed at once; its value is the six production mutations it catches. Two of my first expectations were wrong (an article unread on both phones stays unread) and I fixed the test, not production. Two mutations survived at first (needsRepair check; tab switch). In each case I strengthened a test, saw the mutation caught, and restored the file with `cmp`. My first layout test was green on the broken layout because it measured height; I replaced it with structural checks (width, stacking, inside the screen). Robolectric does not measure text like a device, so that test checks the cause, not the clipping.

## Verification (real phones, 2026-11-10)

Tecno KJ7 `115333744A005844` (Android 14) and Pixel 7 Pro `2B051FDH3006MU` (Android 17), same Wi-Fi. BG6 dropped off adb, so the owner chose KJ7 in its place. Every run started from a byte-exact backup of Room, sidecars, DataStore and the pairing store and a non-empty fixture (`https://exc05c.invalid/…`, three shared stories in different states, one feed only on each phone).

- **Permission gate (Android 17):** with the LAN permission revoked, Load preview showed the permission error and stored no pairing; granting it through the real system dialog allowed the receive.
- **Leg 1, KJ7 → Pixel:** preview showed 1 feed to add and 3 matched stories; confirmed Apply on the Pixel. Database copy: the three shared stories merged as expected (read-and-starred with `readAt` 100; starred, still unread, `readAt` 300; read-and-starred, `readAt` 150), all three fixture feeds present.
- **Role switch:** on KJ7 **Stop and wait to receive back** closed the old URL (`curl` HTTP 000) and left no NSD service; the Pixel's **Send back to KJ7** appeared after Apply, showed no secret block, and KJ7 listed the Pixel as a nearby paired device and received from it through NSD with no URL or secret typed.
- **Leg 2, Pixel → KJ7:** the preview came from the Pixel's state after its Apply (9 feeds to add, 6 matched stories, 4 states to update) and Apply on KJ7 succeeded.
- **Cancel branch:** cancelling leg 1 on the Pixel left its database unchanged (all 1,785 original articles and 9 feeds identical), the Pixel showed the "not applied" note and **Send back**, and KJ7 received the Pixel's unchanged database and applied it.
- **Lifecycle clear:** after KJ7 went to the home screen and came back, **Send back** was gone. Stopping the Pixel's own send session also removed its button.
- **Forget:** removing the peer on KJ7 emptied its pairing store.
- **Layout:** the paired-device actions were clipped on KJ7 (about 96 dp each, labels wrapped); after stacking them the screenshot shows three full-width buttons with one-line labels.
- **Restore:** at the end both phones were restored from backup and every file compared **byte-identical** (KJ7: Reader, -shm, -wal, DataStore, no pairing store; Pixel: also its pairing store). The temporary LAN permission on the Pixel was revoked, no NSD service remained and the app was stopped on both. The full-screen test ads were dismissed with Back only; nothing was installed from them.

## Found on real phones (not by the JVM tests)

1. A labelled first receive stored the peer but offered no **Send back** (the context was set only for an already stored peer). Fixed in `1a477f5a` with a test that failed first; two mutations caught.
2. Three action buttons in one row were clipped on a 360 dp phone. Fixed in `f258a0a4`.

## Not verified / limits

- **Unexplained data change:** in the original rows of the phones one or two BBC articles changed from unread to read (Pixel: 1 in two runs of leg 1, KJ7: 2 after leg 2; one KJ7 article also got a `readAt`). I computed from the backups that the merge rule predicts exactly one such article on the Pixel in leg 1 and two on KJ7 in leg 2 (51 BBC articles are present on both phones), so these match the approved "read wins" merge of real shared articles. The Apply dialog counts (4 and 4) are consistent with that. In a run where I only previewed and did not apply, no original row changed. I did not check each article individually, so I report this as consistent with the merge rule, not as proven.
- **Wrong expectation of mine:** after the cancel branch my check expected the KJ7 values from a different merge and printed MISMATCH; recomputing with the approved rule gives the values KJ7 showed, so that was my error, not a bug.
- The partial-apply case (one phone applies, the other cancels) was proven only in the JVM integration test, not by a deliberate device run.
- Not covered: reboot, a third phone, a non-Local account, the full **Send back** loop on a phone that was only paired by NSD, NSD on API 31–32 and 34–36 (debt from EXC-05b), another language for the new strings (only English and Vietnamese were read on screen).
- The new strings are English in 36 locales, as in earlier slices.
- Two phones, one shared secret, no acknowledgement: the user must stop and switch roles by hand; nothing tells either phone that the other has applied. Merging only moves read and starred marks forward, so unread or un-starred on one phone can return after a sync (the screen says so). Articles that do not exist on the receiver are skipped, and snapshot size limits still apply.
- Pixel device note: the Vietnamese keyboard corrupts typed URLs, so the keyboard was left on English; two typing mistakes in my input (35 characters in the secret) were caught by counting the field before pressing Load preview.
- Whole-EXC-05 gaps remain: automatic and background sync when the app opens, and the three-second two-phone acceptance test.
