#!/usr/bin/env python3
"""Shared GitHub Actions diagnostics for repository checks."""

from __future__ import annotations

import html
import os
from dataclasses import dataclass
from pathlib import Path


ANNOTATION_LIMIT = 20
LOG_LIMIT = 50


@dataclass(frozen=True)
class Diagnostic:
    code: str
    message: str
    path: str | None = None
    line: int | None = None


def escape_command(value: str, *, property_value: bool = False) -> str:
    escaped = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    if property_value:
        escaped = escaped.replace(":", "%3A").replace(",", "%2C")
    return escaped


def emit_annotation(level: str, title: str, diagnostic: Diagnostic) -> None:
    properties = [f"title={escape_command(title, property_value=True)}"]
    if diagnostic.path:
        properties.append(f"file={escape_command(diagnostic.path, property_value=True)}")
    if diagnostic.line:
        properties.append(f"line={diagnostic.line}")
    message = escape_command(diagnostic.message)
    print(f"::{level} {','.join(properties)}::{message}")


def append_summary(
    title: str,
    errors: list[Diagnostic],
    warnings: list[Diagnostic],
    notes: list[str],
) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    with Path(summary_path).open("a", encoding="utf-8") as stream:
        stream.write(f"## {title}\n\n")
        stream.write(f"Errors: `{len(errors)}` | Warnings: `{len(warnings)}`\n\n")
        for note in notes:
            stream.write(f"- {html.escape(note)}\n")
        for heading, diagnostics in (("Errors", errors), ("Warnings", warnings)):
            if not diagnostics:
                continue
            stream.write(f"\n### {heading}\n\n")
            for diagnostic in diagnostics[:ANNOTATION_LIMIT]:
                location = diagnostic.path or "repository"
                if diagnostic.line:
                    location += f":{diagnostic.line}"
                stream.write(
                    f"- <code>{html.escape(location)}</code>: {html.escape(diagnostic.message)}\n"
                )
            remaining = len(diagnostics) - ANNOTATION_LIMIT
            if remaining > 0:
                stream.write(f"- {remaining} additional diagnostic(s) are grouped in the job log.\n")


def report(
    title: str,
    errors: list[Diagnostic],
    warnings: list[Diagnostic] | None = None,
    notes: list[str] | None = None,
) -> int:
    warnings = warnings or []
    notes = notes or []

    print(f"{title}: errors={len(errors)} warnings={len(warnings)}")
    for note in notes:
        print(f"note: {note}")

    for level, diagnostics in (("error", errors), ("warning", warnings)):
        for diagnostic in diagnostics[:LOG_LIMIT]:
            location = diagnostic.path or "repository"
            if diagnostic.line:
                location += f":{diagnostic.line}"
            print(f"{level}: {location}: {diagnostic.message}")
        remaining = len(diagnostics) - LOG_LIMIT
        if remaining > 0:
            print(f"{level}: {remaining} additional diagnostic(s) omitted from the log")

    if os.environ.get("GITHUB_ACTIONS") == "true":
        for diagnostic in errors[:ANNOTATION_LIMIT]:
            emit_annotation("error", title, diagnostic)
        for diagnostic in warnings[:ANNOTATION_LIMIT]:
            emit_annotation("warning", title, diagnostic)

    append_summary(title, errors, warnings, notes)
    return 1 if errors else 0
