# -*- coding: utf-8 -*-
"""Read-only gate for ONE phone. Sends only `adb -s <serial> shell dumpsys/settings get/wm size/ps`, never changes anything.

Usage: python3 pixel_gate.py <serial> <launcher-package> [<expected-size>]
Prints one line starting `GATE OK` or `STOP: <reason>`; exit 0 only on GATE OK. Run it ALONE, right before a launch.
The rule it encodes (owner, 2026-11): launcher on top, unlocked, awake, no instrumentation, accelerometer_rotation=1, user_rotation=0.
"""
import re
import subprocess
import sys

APP = "com.mckimquyen.reader"
LOCK_MARKERS = ("mDreamingLockscreen=true", "isStatusBarKeyguard=true", "mShowingLockscreen=true")


def adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, timeout=30).stdout


def foreground_line(dumpsys_activities: str) -> str:
    """The line that names the foreground activity. Android 12+ prints `topResumedActivity=`; Android 11 prints only `mResumedActivity:`."""
    lines = dumpsys_activities.splitlines()
    return next((l for l in lines if "topResumedActivity" in l), next((l for l in lines if "mResumedActivity" in l), ""))

def evaluate(top, window, power, processes, accelerometer, user_rotation, size, launcher, expected_size):
    """Pure decision from the raw text of each probe, so it can be tested without a phone. Returns (ok, message)."""
    if launcher not in top:
        return False, "STOP: foreground is not the launcher: " + top.strip()
    if any(m in window for m in LOCK_MARKERS):
        return False, "STOP: screen locked"
    if re.search(r"mWakefulness=(Dozing|Asleep)", power):
        return False, "STOP: screen asleep/dozing"
    if re.search(r"ActiveInstrumentation|instrumentationClass=", processes):
        return False, "STOP: some instrumentation is active"
    if accelerometer.strip() != "1":
        return False, "STOP: accelerometer_rotation=%s (expected 1)" % accelerometer.strip()
    if user_rotation.strip() != "0":
        return False, "STOP: user_rotation=%s (expected 0)" % user_rotation.strip()
    if expected_size and expected_size not in size:
        return False, "STOP: unexpected display size: " + size.strip()
    return True, "GATE OK: launcher on top, unlocked, awake, no instrumentation"


def main(argv):
    if len(argv) < 3:
        print("STOP: usage: pixel_gate.py <serial> <launcher-package> [<expected-size>]")
        return 2
    serial, launcher = argv[1], argv[2]
    expected_size = argv[3] if len(argv) > 3 else ""
    if serial not in subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.split():
        print("STOP: %s missing/offline" % serial)
        return 20
    top = foreground_line(adb(serial, "shell", "dumpsys", "activity", "activities"))
    ok, message = evaluate(
        top=top,
        window=adb(serial, "shell", "dumpsys", "window"),
        power=adb(serial, "shell", "dumpsys", "power"),
        processes=adb(serial, "shell", "dumpsys", "activity", "processes"),
        accelerometer=adb(serial, "shell", "settings", "get", "system", "accelerometer_rotation"),
        user_rotation=adb(serial, "shell", "settings", "get", "system", "user_rotation"),
        size=adb(serial, "shell", "wm", "size"),
        launcher=launcher,
        expected_size=expected_size,
    )
    print(message)
    return 0 if ok else 21


if __name__ == "__main__":
    sys.exit(main(sys.argv))
