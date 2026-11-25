# -*- coding: utf-8 -*-
"""Byte-exact backup and restore of the app's own state on ONE phone (Room + sidecars, DataStore, pairing store, commute prefs).

Usage:
  python3 device_backup.py backup  <serial> <dest-dir>
  python3 device_backup.py restore <serial> <dest-dir>
Touches only package com.mckimquyen.reader through `run-as`; never other apps. A backup writes manifest.json (name -> size, sha256, or
"absent"); a restore puts every file back, removes the ones that were absent, and compares every byte against the manifest.
Never deletes the DataStore on purpose: it is restored from the copy, and the restore fails loudly if the copy is missing.
"""
import hashlib
import json
import pathlib
import subprocess
import sys

APP = "com.mckimquyen.reader"
FILES = [
    "databases/Reader", "databases/Reader-shm", "databases/Reader-wal", "databases/Reader-journal",
    "files/datastore/settings.preferences_pb",
    "shared_prefs/pairing_store.xml", "shared_prefs/commute_cast_prefs.xml", "shared_prefs/podcast_positions.xml",
    # The language mirror read in attachBaseContext(): it must follow the DataStore copy, or the two disagree after a restore.
    "shared_prefs/locale_prefs.xml", "shared_prefs/self_signed_trust.xml",
    # Ad and VIP state: a VIP token, a rewarded ad or the consent form change these, so a restore must put them back too.
    "shared_prefs/loitp_admob.xml", "shared_prefs/vip_screen_prefs.xml", "shared_prefs/app_preferences.xml",
    "shared_prefs/com.mckimquyen.reader_preferences.xml", "shared_prefs/__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__.xml",
]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def manifest_entry(data):
    """None means the file was absent on the phone."""
    return "absent" if data is None else {"size": len(data), "sha256": sha256(data)}


def diff_against_manifest(manifest: dict, actual: dict) -> list:
    """Names whose actual content (bytes or None) does not match the manifest entry. Pure, so it is tested without a phone."""
    return [name for name, entry in manifest.items() if manifest_entry(actual.get(name)) != entry]


def _run(serial, *args, stdin=None):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, stdin=stdin, timeout=120)


def _present(serial, remote):
    return _run(serial, "shell", "run-as", APP, "test", "-f", remote).returncode == 0


def _read(serial, remote):
    if not _present(serial, remote):
        return None
    raw = _run(serial, "exec-out", "run-as", APP, "cat", remote).stdout
    size = int(_run(serial, "shell", "run-as", APP, "stat", "-c", "%s", remote).stdout.decode().strip())
    if len(raw) != size:
        raise RuntimeError("short read of %s: %d of %d bytes" % (remote, len(raw), size))
    return raw


def backup(serial, dest):
    _run(serial, "shell", "am", "force-stop", APP)
    if _run(serial, "shell", "pidof", APP).stdout.strip():
        raise RuntimeError("app still running, refusing to back up")
    dest = pathlib.Path(dest)
    dest.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for remote in FILES:
        data = _read(serial, remote)
        manifest[remote] = manifest_entry(data)
        if data is not None:
            (dest / remote.replace("/", "__")).write_bytes(data)
        print(remote, "absent" if data is None else len(data))
    (dest / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print("BACKUP OK", dest)


def restore(serial, dest):
    dest = pathlib.Path(dest)
    manifest = json.loads((dest / "manifest.json").read_text())
    _run(serial, "shell", "am", "force-stop", APP)
    for remote in FILES:
        entry = manifest[remote]
        if entry == "absent":
            if _present(serial, remote):
                _run(serial, "shell", "run-as", APP, "rm", "-f", remote)
            continue
        source = dest / remote.replace("/", "__")
        if not source.exists():
            raise RuntimeError("backup copy missing for %s: not restoring a hole" % remote)
        with open(source, "rb") as handle:
            _run(serial, "shell", "-T", 'run-as %s sh -c "cat > /data/user/0/%s/%s"' % (APP, APP, remote), stdin=handle)
    actual = {remote: _read(serial, remote) for remote in FILES}
    bad = diff_against_manifest(manifest, actual)
    for remote in FILES:
        print(remote, "DIFFERENT" if remote in bad else "BYTE IDENTICAL")
    print("RESTORE FAILED" if bad else "RESTORE OK")
    return 1 if bad else 0


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in ("backup", "restore"):
        print(__doc__)
        sys.exit(2)
    if sys.argv[1] == "backup":
        backup(sys.argv[2], sys.argv[3])
    else:
        sys.exit(restore(sys.argv[2], sys.argv[3]))
