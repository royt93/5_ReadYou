import contextlib
import hashlib
import io
import json
import sqlite3
import unittest
from unittest.mock import patch

import fix19_forensic as audit


class ForensicTest(unittest.TestCase):
    def setUp(self):
        self.body = "\0\n\r\t�A"
        self.row = (
            "1$7f6946f8-09d6-4a88-a24a-5a3ce6f803a0",
            audit.ARTICLE_TITLE,
            "https://example.test/item?token=PRIVATE_LINK_SENTINEL",
            "", self.body, "https://www.npr.org/sections/news/",
        )
        expected = {
            "full_chars": len(self.body),
            "full_utf8_bytes": len(self.body.encode("utf-8")),
            "sha256": hashlib.sha256(self.body.encode("utf-8")).hexdigest(),
            "nul_chars": 1, "replacement_chars": 1, "control_chars": 1,
        }
        patcher = patch.object(audit, "EXPECTED", expected)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_control_count_excludes_lf_cr_tab_but_includes_nul(self):
        result = audit.inspect_row(self.row)
        self.assertEqual(1, result["control_chars"])
        self.assertEqual(1, result["nul_chars"])
        self.assertTrue(result["expected_metadata_match"])

    def test_wrong_metadata_is_rejected(self):
        changes = {
            0: ("not-a-uuid", "1$------------------------------------", "2$7f6946f8-09d6-4a88-a24a-5a3ce6f803a0"),
            1: ("wrong title",), 3: ("raw drift", None),
            4: (self.body + "drift", None), 5: ("https://wrong.example/feed",),
        }
        for index, alternatives in changes.items():
            for replacement in alternatives:
                with self.subTest(index=index):
                    row = list(self.row)
                    row[index] = replacement
                    self.assertFalse(audit.inspect_row(row)["expected_metadata_match"])

    def test_counter_drift_is_rejected(self):
        with patch.dict(audit.EXPECTED, control_chars=2):
            self.assertFalse(audit.inspect_row(self.row)["expected_metadata_match"])

    def run_main(self, rows):
        class Connection:
            def __init__(self):
                self.closed = False

            def execute(self, query, params):
                if not query.startswith("SELECT"):
                    raise AssertionError("Chỉ được SELECT")
                return self

            def fetchall(self):
                return rows

            def close(self):
                self.closed = True

        connection = Connection()
        out, err = io.StringIO(), io.StringIO()
        with patch.object(audit.sqlite3, "connect", return_value=connection) as connect:
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = audit.main()
        self.assertTrue(connect.call_args.kwargs["uri"])
        self.assertTrue(connect.call_args.args[0].endswith("?mode=ro"))
        self.assertTrue(connection.closed)
        return code, out.getvalue(), err.getvalue()

    def test_main_success_outputs_only_safe_metadata(self):
        code, out, err = self.run_main([self.row])
        self.assertEqual(0, code)
        self.assertTrue(json.loads(out)["expected_metadata_match"])
        for secret in (self.row[0], self.row[1], self.row[2], "PRIVATE_LINK_SENTINEL", self.body):
            self.assertNotIn(secret, out + err)

    def test_main_metadata_failure_returns_nonzero(self):
        row = list(self.row)
        row[3] = "PRIVATE_RAW_SENTINEL"
        code, out, err = self.run_main([row])
        self.assertNotEqual(0, code)
        self.assertNotIn("PRIVATE_RAW_SENTINEL", out + err)

    def test_main_missing_or_multiple_rows_returns_nonzero(self):
        for rows in ([], [self.row, self.row]):
            with self.subTest(count=len(rows)):
                code, _, _ = self.run_main(rows)
                self.assertNotEqual(0, code)

    def test_read_errors_do_not_print_exception_message(self):
        class FailingConnection:
            closed = False

            def execute(self, *args):
                raise sqlite3.DatabaseError("PRIVATE_DATABASE_SENTINEL")

            def close(self):
                self.closed = True

        for fail_open in (False, True):
            connection = FailingConnection()
            out, err = io.StringIO(), io.StringIO()
            with patch.object(audit.sqlite3, "connect", side_effect=sqlite3.OperationalError("PRIVATE_DATABASE_SENTINEL") if fail_open else None, return_value=connection):
                with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    code = audit.main()
            self.assertNotEqual(0, code)
            self.assertNotIn("PRIVATE_DATABASE_SENTINEL", out.getvalue() + err.getvalue())
            if not fail_open:
                self.assertTrue(connection.closed)


if __name__ == "__main__":
    unittest.main()
