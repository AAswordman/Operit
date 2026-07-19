from __future__ import annotations

import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_markdown_links import check_file, directory_paths, inline_targets  # noqa: E402


class MarkdownLinkParserTest(unittest.TestCase):
    def test_parentheses_in_destination_are_preserved(self) -> None:
        self.assertEqual(inline_targets("[English](README(E).md)"), ["README(E).md"])

    def test_inline_code_is_ignored(self) -> None:
        self.assertEqual(inline_targets("Use `[label](missing.md)` here."), [])

    def test_non_http_uri_scheme_is_ignored(self) -> None:
        issues = check_file("README.md", "[Open](vscode://settings)", {"README.md"})

        self.assertEqual(issues, [])

    def test_malformed_external_url_is_ignored(self) -> None:
        issues = check_file("README.md", "[Open](https://[)", {"README.md"})

        self.assertEqual(issues, [])

    def test_repository_root_is_a_valid_target(self) -> None:
        existing_paths = {"docs/README.md"} | directory_paths({"docs/README.md"})

        self.assertEqual(check_file("docs/README.md", "[root](../)", existing_paths), [])

    def test_duplicate_broken_links_remain_distinct_occurrences(self) -> None:
        issues = check_file(
            "README.md",
            "[one](missing.md) and [two](missing.md)",
            {"README.md"},
        )

        self.assertEqual(len(issues), 2)

    def test_other_fence_marker_does_not_close_code_block(self) -> None:
        text = "```text\n~~~\n```\n[outside](missing.md)\n"

        issues = check_file("README.md", text, {"README.md"})

        self.assertEqual([issue.target for issue in issues], ["missing.md"])


if __name__ == "__main__":
    unittest.main()
