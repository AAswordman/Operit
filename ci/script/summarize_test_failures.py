#!/usr/bin/env python3
"""Summarize JUnit XML results into a Markdown step summary.

Reads Gradle's test-results XML directory and appends a compact list of failed
tests to the GitHub step summary so failures stay visible without log access.
"""

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET

MAX_FAILURES = 100
MESSAGE_LIMIT = 500


def collect_failures(results_dir: str) -> list:
    failures = []
    for path in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            failures.append(("unparsable-result-file", os.path.basename(path), str(error)))
            continue
        for testcase in root.iter("testcase"):
            for child in testcase:
                if child.tag in ("failure", "error"):
                    message = child.get("message") or (child.text or "").strip()
                    failures.append(
                        (testcase.get("classname") or "?", testcase.get("name") or "?", message)
                    )
    return failures


def render_markdown(failures: list) -> str:
    lines = ["", "## JVM test failures", ""]
    if not failures:
        lines.append("No failed JVM tests recorded.")
        return "\n".join(lines) + "\n"
    lines.append(f"{len(failures)} failed test(s):")
    lines.append("")
    for classname, name, message in failures[:MAX_FAILURES]:
        clipped = " ".join(message.split())[:MESSAGE_LIMIT]
        lines.append(f"- `{classname}.{name}`: {clipped}")
    if len(failures) > MAX_FAILURES:
        lines.append(f"- ... and {len(failures) - MAX_FAILURES} more")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", required=True)
    parser.add_argument("--step-summary", default="")
    args = parser.parse_args()

    if not os.path.isdir(args.results_dir):
        print(f"test results directory not found: {args.results_dir}")
        return 0

    failures = collect_failures(args.results_dir)
    markdown = render_markdown(failures)
    print(f"jvm test failures: {len(failures)}")

    if args.step_summary:
        with open(args.step_summary, "a", encoding="utf-8") as handle:
            handle.write(markdown)
    else:
        sys.stdout.write(markdown)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
