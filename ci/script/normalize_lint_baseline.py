#!/usr/bin/env python3
"""Normalize environment-specific paths in the Android lint baseline."""

from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path


DEFAULT_BASELINE = Path("app/lint-baseline.xml")
HOME_VERSION_CATALOG_PATH = re.compile(
    r'file="\$HOME/[^\"]*/gradle/libs\.versions\.toml"'
)
NORMALIZED_VERSION_CATALOG_PATH = 'file="../gradle/libs.versions.toml"'
EXPECTED_SHA256 = "396e0383a86d7a46b2421d020c5c80efc82faf51ce926fb2864d0593c008d535"


def normalize(text: str) -> str:
    return HOME_VERSION_CATALOG_PATH.sub(NORMALIZED_VERSION_CATALOG_PATH, text)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("path", nargs="?", type=Path, default=DEFAULT_BASELINE)
    args = parser.parse_args()

    original = args.path.read_text(encoding="utf-8")
    normalized = normalize(original)

    if args.check:
        if original != normalized:
            print(
                f"{args.path}: environment-specific lint baseline paths found; "
                "run ci/script/normalize_lint_baseline.py"
            )
            return 1
        actual_sha256 = hashlib.sha256(original.encode("utf-8")).hexdigest()
        if actual_sha256 != EXPECTED_SHA256:
            print(
                f"{args.path}: checksum changed from {EXPECTED_SHA256} "
                f"to {actual_sha256}; review the baseline diff and update its recorded checksum"
            )
            return 1
        print(f"Lint baseline paths are normalized: {args.path}")
        return 0

    if original != normalized:
        args.path.write_text(normalized, encoding="utf-8")
        print(f"Normalized lint baseline paths: {args.path}")
    else:
        print(f"Lint baseline paths already normalized: {args.path}")
    actual_sha256 = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    print(f"Lint baseline SHA-256: {actual_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
