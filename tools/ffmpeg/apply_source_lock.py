#!/usr/bin/env python3
"""Verify and pin FFmpegKit's source table to Operit's committed source lock."""

from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SourcePin:
    component: str
    repository: str
    upstream_ref: str
    commit: str


def read_lock(path: Path) -> dict[str, SourcePin]:
    pins: dict[str, SourcePin] = {}
    for line_number, raw_line in enumerate(path.read_text().splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) != 4:
            raise ValueError(f"{path}:{line_number}: expected four fields")
        component, repository, upstream_ref, commit = parts
        if component in pins:
            raise ValueError(f"{path}:{line_number}: duplicate component {component}")
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ValueError(f"{path}:{line_number}: invalid commit for {component}")
        pins[component] = SourcePin(component, repository, upstream_ref, commit)
    if "ffmpeg-kit" not in pins:
        raise ValueError(f"{path}: ffmpeg-kit pin is required")
    return pins


def git_head(repository: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip().lower()


def pin_source_table(source_table: Path, pins: dict[str, SourcePin]) -> None:
    text = source_table.read_text()
    for component, pin in pins.items():
        if component == "ffmpeg-kit":
            continue
        block_pattern = re.compile(
            rf"(?ms)^  {re.escape(component)}\)\n"
            r"(?P<body>.*?)"
            r"^    ;;$"
        )
        matches = list(block_pattern.finditer(text))
        if len(matches) != 1:
            raise ValueError(
                f"{source_table}: expected one source block for {component}, found {len(matches)}"
            )
        match = matches[0]
        body = match.group("body")
        repository = pin.repository.removesuffix(".git")
        body, repository_count = re.subn(
            r'(?m)^    SOURCE_REPO_URL="[^"]+"$',
            f'    SOURCE_REPO_URL="{repository}"',
            body,
        )
        body, id_count = re.subn(
            r'(?m)^    SOURCE_ID="[^"]+"$',
            f'    SOURCE_ID="{pin.commit}"',
            body,
        )
        body, type_count = re.subn(
            r'(?m)^    SOURCE_TYPE="[^"]+"$',
            '    SOURCE_TYPE="COMMIT"',
            body,
        )
        if (repository_count, id_count, type_count) != (1, 1, 1):
            raise ValueError(f"{source_table}: malformed source block for {component}")
        text = text[: match.start("body")] + body + text[match.end("body") :]

    source_table.write_text(text)

    verified = source_table.read_text()
    for component, pin in pins.items():
        if component == "ffmpeg-kit":
            continue
        block = re.search(
            rf"(?ms)^  {re.escape(component)}\)\n(?P<body>.*?)^    ;;$", verified
        )
        if block is None or f'SOURCE_ID="{pin.commit}"' not in block.group("body"):
            raise ValueError(f"{source_table}: failed to pin {component}")
        if 'SOURCE_TYPE="COMMIT"' not in block.group("body"):
            raise ValueError(f"{source_table}: failed to set COMMIT source for {component}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg-kit", type=Path, required=True)
    parser.add_argument(
        "--lock",
        type=Path,
        default=Path(__file__).with_name("source-lock.properties"),
    )
    args = parser.parse_args()

    repository = args.ffmpeg_kit.resolve()
    pins = read_lock(args.lock.resolve())
    expected_head = pins["ffmpeg-kit"].commit
    actual_head = git_head(repository)
    if actual_head != expected_head:
        raise ValueError(
            f"ffmpeg-kit HEAD mismatch: expected {expected_head}, found {actual_head}"
        )

    source_table = repository / "scripts" / "source.sh"
    if not source_table.is_file():
        raise FileNotFoundError(f"ffmpeg-kit source table is missing: {source_table}")
    pin_source_table(source_table, pins)
    print(f"Pinned ffmpeg-kit and {len(pins) - 1} source inputs from {args.lock}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())