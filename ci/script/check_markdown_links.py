#!/usr/bin/env python3
"""Report local Markdown links newly broken by a merge candidate."""

from __future__ import annotations

import argparse
from collections import Counter
import os
import posixpath
import re
import subprocess
import urllib.parse
from dataclasses import dataclass

from check_output import Diagnostic, report

DEFINITION_RE = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*(.*)$")
URI_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


@dataclass(frozen=True)
class LinkIssue:
    path: str
    line: int
    target: str

    @property
    def identity(self) -> tuple[str, str]:
        return self.path, self.target


def tree_paths(commit: str) -> set[str]:
    result = subprocess.run(
        ["git", "ls-tree", "-r", "-z", "--name-only", commit],
        check=True,
        capture_output=True,
    )
    return {os.fsdecode(value) for value in result.stdout.split(b"\0") if value}


def read_blob(commit: str, path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{commit}:{path}"],
        check=True,
        capture_output=True,
    )
    return result.stdout.decode("utf-8")


def directory_paths(paths: set[str]) -> set[str]:
    directories = {"."}
    for path in paths:
        parent = posixpath.dirname(path)
        while parent:
            directories.add(parent)
            parent = posixpath.dirname(parent)
    return directories

def should_skip_target(target: str) -> bool:
    return (
        not target
        or target.startswith("#")
        or target.startswith("//")
        or bool(URI_SCHEME_RE.match(target))
    )


def resolved_target(source_path: str, target: str) -> str | None:
    parsed = urllib.parse.urlsplit(target)
    link_path = urllib.parse.unquote(parsed.path)
    if not link_path:
        return None
    if link_path.startswith("/"):
        return posixpath.normpath(link_path.lstrip("/"))
    return posixpath.normpath(posixpath.join(posixpath.dirname(source_path), link_path))


def mask_code_spans(line: str) -> str:
    characters = list(line)
    index = 0
    while index < len(line):
        if line[index] != "`":
            index += 1
            continue
        run_end = index
        while run_end < len(line) and line[run_end] == "`":
            run_end += 1
        delimiter = line[index:run_end]
        close = line.find(delimiter, run_end)
        if close == -1:
            index = run_end
            continue
        for masked_index in range(index, close + len(delimiter)):
            characters[masked_index] = " "
        index = close + len(delimiter)
    return "".join(characters)


def fence_marker(line: str) -> tuple[str, int, str] | None:
    indentation = len(line) - len(line.lstrip(" "))
    if indentation > 3:
        return None
    content = line[indentation:]
    if not content or content[0] not in {"`", "~"}:
        return None
    marker = content[0]
    run_length = len(content) - len(content.lstrip(marker))
    if run_length < 3:
        return None
    return marker, run_length, content[run_length:]


def parse_destination(value: str, start: int = 0) -> tuple[str | None, int]:
    index = start
    while index < len(value) and value[index].isspace():
        index += 1
    if index >= len(value):
        return None, index
    if value[index] == "<":
        close = value.find(">", index + 1)
        if close == -1:
            return None, index
        return value[index + 1 : close], close + 1

    destination: list[str] = []
    depth = 0
    while index < len(value):
        character = value[index]
        if character == "\\" and index + 1 < len(value):
            destination.append(value[index + 1])
            index += 2
            continue
        if character == "(":
            depth += 1
            destination.append(character)
            index += 1
            continue
        if character == ")":
            if depth == 0:
                break
            depth -= 1
            destination.append(character)
            index += 1
            continue
        if character.isspace() and depth == 0:
            break
        destination.append(character)
        index += 1
    return "".join(destination) or None, index


def inline_targets(line: str) -> list[str]:
    masked = mask_code_spans(line)
    targets: list[str] = []
    search_start = 0
    while True:
        marker = masked.find("](", search_start)
        if marker == -1:
            break
        target, end = parse_destination(masked, marker + 2)
        if target is not None:
            targets.append(target)
        search_start = max(end + 1, marker + 2)
    return targets


def check_file(path: str, text: str, existing_paths: set[str]) -> list[LinkIssue]:
    issues: list[LinkIssue] = []
    open_fence: tuple[str, int] | None = None
    for line_number, line in enumerate(text.splitlines(), start=1):
        marker = fence_marker(line)
        if open_fence is not None:
            if (
                marker is not None
                and marker[0] == open_fence[0]
                and marker[1] >= open_fence[1]
                and not marker[2].strip()
            ):
                open_fence = None
            continue
        if marker is not None and not (marker[0] == "`" and "`" in marker[2]):
            open_fence = (marker[0], marker[1])
            continue

        targets = inline_targets(line)
        definition = DEFINITION_RE.match(mask_code_spans(line))
        if definition:
            target, _ = parse_destination(definition.group(1))
            if target is not None:
                targets.append(target)

        for target in targets:
            if should_skip_target(target):
                continue
            resolved = resolved_target(path, target)
            if resolved is None:
                continue
            if resolved == ".." or resolved.startswith("../") or resolved not in existing_paths:
                issues.append(LinkIssue(path=path, line=line_number, target=target))
    return issues


def snapshot_issues(commit: str) -> list[LinkIssue]:
    files = tree_paths(commit)
    existing_paths = files | directory_paths(files)
    issues: list[LinkIssue] = []
    for path in sorted(value for value in files if value.endswith((".md", ".mdx"))):
        try:
            text = read_blob(commit, path)
        except UnicodeDecodeError:
            continue
        issues.extend(check_file(path, text, existing_paths))
    return issues


def renamed_markdown_paths(base: str, candidate: str) -> dict[str, str]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--name-status",
            "-z",
            "-M",
            base,
            candidate,
            "--",
            "*.md",
            "*.mdx",
        ],
        check=True,
        capture_output=True,
    )
    values = [os.fsdecode(value) for value in result.stdout.split(b"\0") if value]
    renames: dict[str, str] = {}
    index = 0
    while index < len(values):
        status = values[index]
        index += 1
        if status.startswith(("R", "C")):
            old_path, new_path = values[index], values[index + 1]
            index += 2
            if status.startswith("R"):
                renames[old_path] = new_path
        else:
            index += 1
    return renames


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    base_issues = snapshot_issues(args.base)
    candidate_issues = snapshot_issues(args.candidate)
    renames = renamed_markdown_paths(args.base, args.candidate)
    base_identities = Counter((renames.get(issue.path, issue.path), issue.target) for issue in base_issues)
    new_issues: list[LinkIssue] = []
    for issue in candidate_issues:
        if base_identities[issue.identity] > 0:
            base_identities[issue.identity] -= 1
        else:
            new_issues.append(issue)
    existing_count = len(candidate_issues) - len(new_issues)

    errors = [
        Diagnostic(
            code="markdown-link",
            path=issue.path,
            line=issue.line,
            message=f"missing local link target: {issue.target}",
        )
        for issue in new_issues
    ]
    return report(
        "Markdown links",
        errors,
        notes=[f"Ignored {existing_count} broken local link(s) already present in the base tree."],
    )


if __name__ == "__main__":
    raise SystemExit(main())
