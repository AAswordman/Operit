from __future__ import annotations

import sys
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from pr_check import candidate_context, changed_paths, classify_paths  # noqa: E402


def git(repository: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


class ScopeClassificationTest(unittest.TestCase):
    def test_translation_only_uses_resource_lane(self) -> None:
        plan = classify_paths(
            [
                "app/src/main/res/values-ro/strings.xml",
                "app/src/main/res/xml/locales_config.xml",
            ]
        )

        self.assertTrue(plan.localization)
        self.assertTrue(plan.android_resources)
        self.assertFalse(plan.android_jvm)
        self.assertFalse(plan.android_full)
        self.assertFalse(plan.docs)

    def test_default_strings_use_jvm_lane(self) -> None:
        plan = classify_paths(["app/src/main/res/values/strings.xml"])

        self.assertTrue(plan.localization)
        self.assertFalse(plan.android_resources)
        self.assertTrue(plan.android_jvm)
        self.assertFalse(plan.android_full)

    def test_native_change_uses_full_lane(self) -> None:
        plan = classify_paths(["cmake/operit_git_source.cmake"])

        self.assertFalse(plan.android_resources)
        self.assertFalse(plan.android_jvm)
        self.assertTrue(plan.android_full)

    def test_upstream_only_docs_are_not_part_of_candidate_paths(self) -> None:
        plan = classify_paths(["app/src/main/res/values-ro/strings.xml"])

        self.assertFalse(plan.docs)

    def test_rename_source_and_destination_cover_both_scopes(self) -> None:
        plan = classify_paths(
            [
                "web-chat/src/legacy.tsx",
                "app/src/main/java/com/example/Current.kt",
            ]
        )

        self.assertTrue(plan.web)
        self.assertTrue(plan.android_jvm)

    def test_type_change_path_is_classified_normally(self) -> None:
        plan = classify_paths(["app/src/main/java/com/example/Current.kt"])

        self.assertTrue(plan.android_jvm)

    def test_issue_form_yaml_uses_yaml_lane(self) -> None:
        plan = classify_paths([".github/ISSUE_TEMPLATE/bug.yml"])

        self.assertTrue(plan.yaml)
        self.assertFalse(plan.ci)

    def test_root_npm_configuration_uses_javascript_lanes(self) -> None:
        plan = classify_paths([".npmrc", "npm-shrinkwrap.json"])

        self.assertTrue(plan.web)
        self.assertTrue(plan.toolpkg)

    def test_android_test_source_uses_instrumentation_compile(self) -> None:
        plan = classify_paths(["app/src/androidTest/java/com/example/ExampleTest.kt"])

        self.assertTrue(plan.android_jvm)
        self.assertTrue(plan.android_instrumentation)


class CandidateContractTest(unittest.TestCase):
    def test_stale_branch_uses_candidate_first_parent_diff(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            git(repository, "init", "-b", "main")
            git(repository, "config", "user.name", "CI Test")
            git(repository, "config", "user.email", "ci@example.com")

            (repository / "README.md").write_text("base\n", encoding="utf-8")
            git(repository, "add", "README.md")
            git(repository, "commit", "-m", "base")

            git(repository, "switch", "-c", "feature")
            translation = repository / "app/src/main/res/values-ro/strings.xml"
            translation.parent.mkdir(parents=True)
            translation.write_text("<resources/>\n", encoding="utf-8")
            git(repository, "add", str(translation.relative_to(repository)))
            git(repository, "commit", "-m", "translation")
            head_sha = git(repository, "rev-parse", "HEAD")

            git(repository, "switch", "main")
            docs = repository / "docs/upstream.md"
            docs.parent.mkdir()
            docs.write_text("upstream\n", encoding="utf-8")
            git(repository, "add", str(docs.relative_to(repository)))
            git(repository, "commit", "-m", "upstream docs")
            base_sha = git(repository, "rev-parse", "HEAD")

            git(repository, "merge", "--no-ff", "feature", "-m", "candidate")
            candidate_sha = git(repository, "rev-parse", "HEAD")

            previous_directory = Path.cwd()
            try:
                os.chdir(repository)
                context = candidate_context(candidate_sha, base_sha, head_sha)
                paths = changed_paths(context.base_sha, context.candidate_sha)
                with self.assertRaisesRegex(ValueError, "first parent"):
                    candidate_context(candidate_sha, head_sha, head_sha)
                with self.assertRaisesRegex(ValueError, "second parent"):
                    candidate_context(candidate_sha, base_sha, base_sha)
            finally:
                os.chdir(previous_directory)

            self.assertEqual(paths, ["app/src/main/res/values-ro/strings.xml"])

    def test_non_merge_candidate_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            git(repository, "init", "-b", "main")
            git(repository, "config", "user.name", "CI Test")
            git(repository, "config", "user.email", "ci@example.com")
            (repository / "README.md").write_text("base\n", encoding="utf-8")
            git(repository, "add", "README.md")
            git(repository, "commit", "-m", "base")
            commit = git(repository, "rev-parse", "HEAD")

            previous_directory = Path.cwd()
            try:
                os.chdir(repository)
                with self.assertRaisesRegex(ValueError, "exactly two parents"):
                    candidate_context(commit, commit, commit)
            finally:
                os.chdir(previous_directory)


if __name__ == "__main__":
    unittest.main()
