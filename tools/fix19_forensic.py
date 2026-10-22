#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read FIX-19's preserved incident DB read-only and print metadata only, never body text."""
import hashlib
import re
import sqlite3
import sys

DB_PATH = "/Users/loitran/podcast_bak_emulator/Reader"
ARTICLE_TITLE = "Hit a midlife wall? This test identifies your strengths and what matters to you"
EXPECTED = {
    "full_chars": 12_324_490,
    "full_utf8_bytes": 15_890_764,
    "sha256": "2ea6f03e5654eb3eda859d29a78e5111820c98b6fca14b0cf3461d8f752e18e1",
}

def main() -> int:
    try:
        con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    except sqlite3.Error as exc:
        print(f"cannot open preserved DB read-only: {exc}", file=sys.stderr)
        return 2
    try:
        rows = con.execute(
            "SELECT a.id,a.title,a.link,a.rawDescription,a.fullContent,f.url "
            "FROM article a JOIN feed f ON f.id=a.feedId "
            "WHERE f.name='NPR' AND a.title=?",
            (ARTICLE_TITLE,),
        ).fetchall()
        if len(rows) != 1:
            print(f"expected one matching row, found {len(rows)}", file=sys.stderr)
            return 2
        article_id, title, link, raw, full, feed_url = rows[0]
        body = full or ""
        encoded = body.encode("utf-8")
        hex_sha = lambda value: hashlib.sha256(value.encode("utf-8")).hexdigest()
        id_is_local_uuid = bool(re.fullmatch(r"1\$[0-9a-fA-F-]{36}", article_id))
        # Hash identifiers and title so even non-body metadata is not copied into routine logs.
        print(f"article_id_sha256={hex_sha(article_id)}")
        print(f"id_is_local_uuid={id_is_local_uuid}")
        print(f"title_sha256={hex_sha(title)}")
        print(f"link_host={__import__('urllib.parse', fromlist=['urlsplit']).urlsplit(link).hostname}")
        print(f"feed_url_host={__import__('urllib.parse', fromlist=['urlsplit']).urlsplit(feed_url).hostname}")
        print(f"raw_utf8_bytes={len((raw or '').encode('utf-8'))}")
        print(f"full_chars={len(body)}")
        print(f"full_utf8_bytes={len(encoded)}")
        print(f"full_sha256={hashlib.sha256(encoded).hexdigest()}")
        print(f"has_replacement_chars={body.count(chr(0xfffd)) > 0}")
        print(f"nul_chars={body.count(chr(0))}")
        print(f"replacement_chars={body.count(chr(0xfffd))}")
        control_chars = sum(ord(ch) < 32 and ch not in "\\n\\r\\t" for ch in body)
        print(f"control_chars={control_chars}")
        passed = len(body) == EXPECTED["full_chars"] and len(encoded) == EXPECTED["full_utf8_bytes"] and hashlib.sha256(encoded).hexdigest() == EXPECTED["sha256"]
        print(f"expected_metadata_match={passed}")
        print("writer_assessment=local accountId$UUID favors RssHelper.article; ReadLaterWorker preserves ID; exact historical writer unproven")
        return 0 if passed else 1
    finally:
        con.close()

if __name__ == "__main__":
    raise SystemExit(main())
