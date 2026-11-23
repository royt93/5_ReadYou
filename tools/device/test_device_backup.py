# -*- coding: utf-8 -*-
import unittest

from device_backup import diff_against_manifest, manifest_entry, FILES


class DeviceBackupTest(unittest.TestCase):
    def test_absent_and_present_entries_are_told_apart(self):
        self.assertEqual("absent", manifest_entry(None))
        self.assertEqual({"size": 3, "sha256": manifest_entry(b"abc")["sha256"]}, manifest_entry(b"abc"))

    def test_an_identical_phone_has_no_difference(self):
        data = {"a": b"one", "b": None}
        manifest = {name: manifest_entry(value) for name, value in data.items()}
        self.assertEqual([], diff_against_manifest(manifest, data))

    def test_one_flipped_byte_is_found(self):
        manifest = {"a": manifest_entry(b"one"), "b": manifest_entry(b"two")}
        self.assertEqual(["b"], diff_against_manifest(manifest, {"a": b"one", "b": b"twp"}))

    def test_a_file_that_should_be_absent_but_exists_is_found(self):
        self.assertEqual(["a"], diff_against_manifest({"a": "absent"}, {"a": b"x"}))

    def test_a_file_that_should_exist_but_is_gone_is_found(self):
        self.assertEqual(["a"], diff_against_manifest({"a": manifest_entry(b"x")}, {"a": None}))

    def test_the_file_list_covers_room_datastore_and_pairing(self):
        for needed in ("databases/Reader", "databases/Reader-wal", "files/datastore/settings.preferences_pb", "shared_prefs/pairing_store.xml"):
            self.assertIn(needed, FILES)

    def test_the_file_list_covers_the_ad_and_vip_state(self):
        # A VIP token creates vip_screen_prefs.xml and changes loitp_admob.xml; without them in the list a restore leaves the phone VIP.
        for needed in ("shared_prefs/loitp_admob.xml", "shared_prefs/vip_screen_prefs.xml", "shared_prefs/app_preferences.xml",
                       "shared_prefs/com.mckimquyen.reader_preferences.xml", "shared_prefs/__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__.xml"):
            self.assertIn(needed, FILES)

    def test_the_file_list_has_no_duplicates(self):
        self.assertEqual(len(FILES), len(set(FILES)))

    def test_the_file_list_names_only_our_own_package_state(self):
        for name in FILES:
            self.assertTrue(name.split("/")[0] in ("databases", "files", "shared_prefs"), name)


if __name__ == "__main__":
    unittest.main()
