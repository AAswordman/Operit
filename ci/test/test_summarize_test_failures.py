from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from summarize_test_failures import collect_failures, render_markdown  # noqa: E402


class SummarizeTestFailuresTest(unittest.TestCase):
    def write_result(self, directory: Path, name: str, body: str) -> None:
        (directory / name).write_text(body, encoding="utf-8")

    def test_collect_failures_reports_failure_and_error_entries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory)
            self.write_result(
                results,
                "TEST-sample.xml",
                "<testsuite>"
                '<testcase classname="Sample" name="passes" />'
                '<testcase classname="Sample" name="fails">'
                '<failure message="expected:&lt;1&gt; but was:&lt;2&gt;" /></testcase>'
                '<testcase classname="Sample" name="crashes">'
                '<error message="NullPointerException" /></testcase>'
                "</testsuite>",
            )
            failures = collect_failures(str(results))
        self.assertEqual(
            failures,
            [
                ("Sample", "fails", "expected:<1> but was:<2>"),
                ("Sample", "crashes", "NullPointerException"),
            ],
        )

    def test_collect_failures_skips_unparsable_files_without_raising(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory)
            self.write_result(results, "TEST-broken.xml", "<not-xml")
            failures = collect_failures(str(results))
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0][0], "unparsable-result-file")

    def test_render_markdown_lists_each_failure(self) -> None:
        markdown = render_markdown([("Sample", "fails", "expected 1")])
        self.assertIn("`Sample.fails`: expected 1", markdown)
        self.assertIn("1 failed test(s)", markdown)

    def test_render_markdown_without_failures_states_none_recorded(self) -> None:
        markdown = render_markdown([])
        self.assertIn("No failed JVM tests recorded.", markdown)


if __name__ == "__main__":
    unittest.main()
