#!/usr/bin/env python3
"""Check the files introduced by a pull request merge candidate."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from check_output import Diagnostic, report

DIFF_CHECK_RE = re.compile(r"^(.*?):(\d+): (.*)$")


def changed_files(base_sha: str, candidate_sha: str) -> list[Path]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--name-only",
            "--no-renames",
            "--diff-filter=ACMRT",
            "-z",
            base_sha,
            candidate_sha,
        ],
        check=True,
        capture_output=True,
    )
    return [Path(os.fsdecode(value)) for value in result.stdout.split(b"\0") if value]


def whitespace_errors(base_sha: str, candidate_sha: str) -> list[Diagnostic]:
    result = subprocess.run(
        ["git", "diff", "--check", base_sha, candidate_sha],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode not in {0, 2}:
        raise subprocess.CalledProcessError(
            result.returncode,
            result.args,
            output=result.stdout,
            stderr=result.stderr,
        )

    diagnostics: list[Diagnostic] = []
    for line in result.stdout.splitlines():
        match = DIFF_CHECK_RE.match(line)
        if match:
            diagnostics.append(
                Diagnostic(
                    code="whitespace",
                    path=match.group(1),
                    line=int(match.group(2)),
                    message=match.group(3),
                )
            )
    return diagnostics


def workspace_errors(candidate_sha: str) -> list[Diagnostic]:
    expected = subprocess.run(
        ["git", "rev-parse", f"{candidate_sha}^{{commit}}"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    actual = subprocess.run(
        ["git", "rev-parse", "HEAD^{commit}"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    errors: list[Diagnostic] = []
    if actual != expected:
        errors.append(
            Diagnostic(
                code="workspace-commit",
                message=f"workspace HEAD is {actual}, expected candidate {expected}",
            )
        )

    status = subprocess.run(
        [
            "git",
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--ignore-submodules=all",
        ],
        check=True,
        capture_output=True,
    ).stdout
    if status:
        errors.append(
            Diagnostic(
                code="workspace-dirty",
                message="workspace files differ from the checked-out candidate",
            )
        )
    return errors


def check_text_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return

    conflict_start: int | None = None
    conflict_separator = False
    for line_number, line in enumerate(text.splitlines(), start=1):
        if line.startswith("<<<<<<< "):
            conflict_start = line_number
            conflict_separator = False
        elif conflict_start is not None and line.startswith("======="):
            conflict_separator = True
        elif conflict_start is not None and conflict_separator and line.startswith(">>>>>>> "):
            errors.append(
                Diagnostic(
                    code="merge-conflict",
                    path=str(path),
                    line=conflict_start,
                    message="merge conflict marker",
                )
            )
            conflict_start = None
            conflict_separator = False


def check_json_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        with path.open(encoding="utf-8") as stream:
            json.load(stream)
    except (OSError, json.JSONDecodeError) as error:
        errors.append(
            Diagnostic(
                code="json",
                path=str(path),
                line=getattr(error, "lineno", None),
                message=f"invalid JSON: {error}",
            )
        )


def check_xml_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        ET.parse(path)
    except (OSError, ET.ParseError) as error:
        line = error.position[0] if isinstance(error, ET.ParseError) else None
        errors.append(
            Diagnostic(
                code="xml",
                path=str(path),
                line=line,
                message=f"invalid XML: {error}",
            )
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    repo_root = Path.cwd()
    errors = whitespace_errors(args.base, args.candidate)
    state_errors = workspace_errors(args.candidate)
    errors.extend(state_errors)
    files = changed_files(args.base, args.candidate)

    if not state_errors:
        for relative_path in files:
            path = repo_root / relative_path
            if path.is_symlink():
                errors.append(
                    Diagnostic(
                        code="symlink",
                        path=str(relative_path),
                        message="changed symbolic links require explicit repository review",
                    )
                )
                continue
            if not path.is_file():
                continue

            check_text_file(relative_path, errors)

            if relative_path.suffix.lower() == ".json":
                check_json_file(relative_path, errors)
            elif relative_path.suffix.lower() == ".xml":
                check_xml_file(relative_path, errors)

    return report(
        "Repository hygiene",
        errors,
        notes=[f"Checked {len(files)} added or modified candidate file(s)."],
    )


if __name__ == "__main__":
    raise SystemExit(main())
