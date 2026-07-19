#!/usr/bin/env python3
"""Check relative Markdown links changed by a commit without network access."""

from __future__ import annotations

import argparse
import re
import subprocess
import urllib.parse
from pathlib import Path


LINK_RE = re.compile(r"!?(?:\[[^\]]*\])\(\s*(?:<([^>]+)>|([^\s)]+))")
def markdown_files(base_sha: str | None, head_sha: str) -> list[Path]:
    if base_sha:
        command = [
            "git",
            "diff",
            "--name-only",
            "--diff-filter=ACMR",
            base_sha,
            head_sha,
            "--",
            "*.md",
            "*.mdx",
        ]
    else:
        command = ["git", "ls-files", "*.md", "*.mdx"]

    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return [Path(line) for line in result.stdout.splitlines() if line]


def should_skip_target(target: str) -> bool:
    parsed = urllib.parse.urlsplit(target)
    return (
        not target
        or target.startswith("#")
        or parsed.scheme in {"http", "https", "mailto", "tel"}
        or target.startswith("//")
    )


def check_file(path: Path, repo_root: Path, errors: list[str]) -> None:
    if not path.is_file():
        return

    in_fence = False
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue

        for match in LINK_RE.finditer(line):
            target = match.group(1) or match.group(2) or ""
            if should_skip_target(target):
                continue

            parsed = urllib.parse.urlsplit(target)
            link_path = urllib.parse.unquote(parsed.path)
            if not link_path:
                continue

            if link_path.startswith("/"):
                resolved = repo_root / link_path.lstrip("/")
            else:
                resolved = path.parent / link_path

            if not resolved.exists():
                errors.append(f"{path}:{line_number}: missing local link target: {target}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-sha")
    parser.add_argument("--head-sha", default="HEAD")
    args = parser.parse_args()

    repo_root = Path.cwd()
    errors: list[str] = []
    files = markdown_files(args.base_sha, args.head_sha)
    for path in files:
        check_file(path, repo_root, errors)

    if errors:
        print("Markdown link check failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"Markdown link check passed for {len(files)} changed file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
