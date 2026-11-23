# -*- coding: utf-8 -*-
import unittest

from pixel_gate import evaluate, foreground_line

LAUNCHER = "com.google.android.apps.nexuslauncher"
GOOD = dict(
    top="topResumedActivity=ActivityRecord{1 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t2}",
    window="mShowingDream=false mDreamingLockscreen=false",
    power="  mWakefulness=Awake",
    processes="",
    accelerometer="1\n",
    user_rotation="0\n",
    size="Physical size: 1440x3120",
    launcher=LAUNCHER,
    expected_size="1440x3120",
)


def run(**override):
    return evaluate(**{**GOOD, **override})


class PixelGateTest(unittest.TestCase):
    def test_everything_right_is_ok(self):
        ok, message = run()
        self.assertTrue(ok)
        self.assertTrue(message.startswith("GATE OK"))

    def test_another_app_on_top_stops_and_names_it(self):
        ok, message = run(top="topResumedActivity=ActivityRecord{1 u0 loyalty.skyjoy.vn.dev/com.loyalty.MainActivity t9}")
        self.assertFalse(ok)
        self.assertIn("loyalty.skyjoy.vn.dev", message)

    def test_a_locked_screen_stops(self):
        for marker in ("mDreamingLockscreen=true", "isStatusBarKeyguard=true", "mShowingLockscreen=true"):
            self.assertFalse(run(window=marker)[0], marker)

    def test_a_sleeping_screen_stops(self):
        self.assertFalse(run(power="mWakefulness=Asleep")[0])
        self.assertFalse(run(power="mWakefulness=Dozing")[0])

    def test_an_active_instrumentation_stops(self):
        self.assertFalse(run(processes="ProcessRecord ... ActiveInstrumentation{...}")[0])
        self.assertFalse(run(processes="instrumentationClass=androidx.test.runner")[0])

    def test_rotation_settings_must_be_exact(self):
        self.assertFalse(run(accelerometer="0")[0])
        self.assertFalse(run(user_rotation="1")[0])
        self.assertFalse(run(accelerometer="null")[0])

    def test_an_unexpected_display_size_stops_but_no_expectation_does_not(self):
        self.assertFalse(run(size="Physical size: 1080x2436")[0])
        self.assertTrue(run(size="Physical size: 1080x2436", expected_size="")[0])

    def test_the_first_failing_rule_wins_in_a_fixed_order(self):
        ok, message = run(top="topResumedActivity=com.other/.A", window="mShowingLockscreen=true")
        self.assertIn("foreground", message)


class ForegroundLineTest(unittest.TestCase):
    """Android 11 (API 30) prints `mResumedActivity:` and no `topResumedActivity`; Android 12+ prints both. The gate must read either."""
    A11 = "  mResumedActivity: ActivityRecord{10d188c u0 com.sec.android.app.launcher/.activities.LauncherActivity t26818}"
    A11_FULL = "\n".join([
        "  mFocusedApp=ActivityRecord{1 u0 x/.Y t1}",
        A11,
        "  ResumedActivity: ActivityRecord{10d188c u0 com.sec.android.app.launcher/.activities.LauncherActivity t26818}",
    ])

    def test_android11_resumed_line_is_found(self):
        self.assertIn("com.sec.android.app.launcher", foreground_line(self.A11_FULL))

    def test_android12_top_resumed_line_still_wins_when_both_exist(self):
        both = "\n".join([
            "  topResumedActivity=ActivityRecord{1 u0 com.mckimquyen.reader/.MainActivity t2}",
            self.A11,
        ])
        self.assertIn("com.mckimquyen.reader", foreground_line(both))

    def test_no_foreground_line_at_all_is_empty_not_a_crash(self):
        self.assertEqual("", foreground_line("nothing useful here"))

    def test_a_samsung_launcher_passes_the_gate_when_the_line_is_the_android11_one(self):
        ok, message = evaluate(
            top=foreground_line(self.A11_FULL), window="mShowingDream=false", power="mWakefulness=Awake", processes="",
            accelerometer="1", user_rotation="0", size="Physical size: 1080x2340",
            launcher="com.sec.android.app.launcher", expected_size="",
        )
        self.assertTrue(ok, message)

    def test_an_empty_line_still_stops(self):
        ok, message = evaluate(
            top="", window="", power="mWakefulness=Awake", processes="", accelerometer="1", user_rotation="0", size="",
            launcher="com.sec.android.app.launcher", expected_size="",
        )
        self.assertFalse(ok)

if __name__ == "__main__":
    unittest.main()
