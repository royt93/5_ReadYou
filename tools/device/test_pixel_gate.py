# -*- coding: utf-8 -*-
import unittest

from pixel_gate import evaluate

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


if __name__ == "__main__":
    unittest.main()
