#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Đọc backup FIX-19 chỉ đọc; chỉ xuất metadata, không xuất body."""
import hashlib
import json
import re
import sqlite3
import sys
from urllib.parse import urlsplit

DB_PATH = "/Users/loitran/podcast_bak_emulator/Reader"
ARTICLE_TITLE = "Hit a midlife wall? This test identifies your strengths and what matters to you"
FEED_URL = "https://www.npr.org/sections/news/"
EXPECTED = {
    "full_chars": 12_324_490,
    "full_utf8_bytes": 15_890_764,
    "sha256": "2ea6f03e5654eb3eda859d29a78e5111820c98b6fca14b0cf3461d8f752e18e1",
    "nul_chars": 63_610,
    "replacement_chars": 1_713_674,
    "control_chars": 518_528,
}
LOCAL_ID_PATTERN = r"1\$[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
IGNORED_C0_WHITESPACE = "\n\r\t"
C0_LIMIT = 32


def inspect_row(row):
    article_id, title, link, raw, full, feed_url = row
    is_text_body = isinstance(full, str)
    body = full if is_text_body else ""
    encoded = body.encode("utf-8")
    counters = {
        "full_chars": len(body),
        "full_utf8_bytes": len(encoded),
        "sha256": hashlib.sha256(encoded).hexdigest(),
        "nul_chars": body.count(chr(0)),
        "replacement_chars": body.count(chr(0xfffd)),
        # C0 bỏ LF/CR/tab, vẫn gồm NUL.
        "control_chars": sum(ord(ch) < C0_LIMIT and ch not in IGNORED_C0_WHITESPACE for ch in body),
    }
    local_id = isinstance(article_id, str) and re.fullmatch(LOCAL_ID_PATTERN, article_id) is not None
    valid = (
        local_id and title == ARTICLE_TITLE and raw == "" and is_text_body
        and feed_url == FEED_URL
        and all(counters[key] == value for key, value in EXPECTED.items())
    )

    def digest(value):
        return hashlib.sha256(value.encode("utf-8")).hexdigest() if isinstance(value, str) else None

    return {
        "article_id_sha256": digest(article_id),
        "id_is_local_uuid": local_id,
        "title_sha256": digest(title),
        "link_host": urlsplit(link).hostname if isinstance(link, str) else None,
        "feed_url_host": urlsplit(feed_url).hostname if isinstance(feed_url, str) else None,
        "raw_utf8_bytes": len(raw.encode("utf-8")) if isinstance(raw, str) else None,
        **counters,
        "expected_metadata_match": bool(valid),
        "writer_assessment": "local UUID consistent with RSS ingestion; later enrichment cannot be excluded; historical writer unproven",
    }


def main() -> int:
    con = None
    try:
        con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
        rows = con.execute(
            "SELECT a.id,a.title,a.link,a.rawDescription,a.fullContent,f.url "
            "FROM article a JOIN feed f ON f.id=a.feedId "
            "WHERE f.url=? AND a.title=?",
            (FEED_URL, ARTICLE_TITLE),
        ).fetchall()
        if len(rows) != 1:
            print(f"forensic_error=row_count count={len(rows)}", file=sys.stderr)
            return 2
        result = inspect_row(rows[0])
        print(json.dumps(result, ensure_ascii=True, sort_keys=True))
        return 0 if result["expected_metadata_match"] else 1
    except (sqlite3.Error, ValueError, TypeError) as exc:
        print(f"forensic_error={type(exc).__name__}", file=sys.stderr)
        return 2
    finally:
        if con is not None:
            con.close()


if __name__ == "__main__":
    raise SystemExit(main())
