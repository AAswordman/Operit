from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_repo_hygiene import workspace_errors  # noqa: E402


def git(repository: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


class WorkspaceStateTest(unittest.TestCase):
    def test_workspace_changes_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            git(repository, "init", "-b", "main")
            git(repository, "config", "user.name", "CI Test")
            git(repository, "config", "user.email", "ci@example.com")
            tracked = repository / "tracked.txt"
            tracked.write_text("candidate\n", encoding="utf-8")
            git(repository, "add", "tracked.txt")
            git(repository, "commit", "-m", "candidate")
            candidate = git(repository, "rev-parse", "HEAD")
            tracked.write_text("different\n", encoding="utf-8")

            previous_directory = Path.cwd()
            try:
                os.chdir(repository)
                errors = workspace_errors(candidate)
            finally:
                os.chdir(previous_directory)

            self.assertEqual([error.code for error in errors], ["workspace-dirty"])

            tracked.write_text("candidate\n", encoding="utf-8")
            (repository / "untracked.txt").write_text("extra\n", encoding="utf-8")
            try:
                os.chdir(repository)
                errors = workspace_errors(candidate)
            finally:
                os.chdir(previous_directory)

            self.assertEqual([error.code for error in errors], ["workspace-dirty"])


if __name__ == "__main__":
    unittest.main()
