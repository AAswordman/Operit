"""分支删除必须对应已公开且附件完整的本轮发行。"""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.cleanup_merged_branches import validate_release
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY


class MergedBranchCleanupTest(unittest.TestCase):
    def setUp(self):
        self.commit = "a" * 40
        self.cleanup = {"repository": SOURCE_REPOSITORY, "version": "1.12.2+22", "branches": ["fix/completed"]}
        self.plan = {"sourceCommit": self.commit, "tag": "v1.12.2+22", "repositories": [UPDATE_REPOSITORY],
            "assets": [{"name": name, "bytes": 10, "sha256": "b" * 64} for name in ["app-kong.apk", "distribution-release.json"]]}
        self.published = {"tag_name": "v1.12.2+22", "draft": False, "prerelease": False,
            "assets": [{"name": item["name"], "size": 10, "state": "uploaded", "digest": "sha256:" + item["sha256"]} for item in self.plan["assets"]]}

    def check(self):
        return validate_release(self.cleanup, self.plan, self.published, self.commit)

    def test_exact_published_release_accepts_only_the_declared_work_branch(self):
        self.assertEqual(["fix/completed"], self.check())

    def test_draft_prerelease_or_different_version_cannot_delete_branches(self):
        for values in [{"draft": True}, {"prerelease": True}, {"tag_name": "v1.12.2+21"}]:
            with self.subTest(values=values):
                original = self.published
                self.published = {**original, **values}
                with self.assertRaises(ValueError):
                    self.check()
                self.published = original

    def test_wrong_source_commit_or_changed_attachment_blocks_cleanup(self):
        self.plan["sourceCommit"] = "c" * 40
        with self.assertRaises(ValueError):
            self.check()
        self.plan["sourceCommit"] = self.commit
        self.published["assets"][0]["size"] = 11
        with self.assertRaises(ValueError):
            self.check()

    def test_protected_unlisted_or_duplicate_branch_names_are_rejected(self):
        for branches in [["main"], ["dev"], ["docs/1.13.0-changelog"], ["fix/a", "fix/a"], ["fix/../main"], []]:
            with self.subTest(branches=branches):
                self.cleanup["branches"] = branches
                with self.assertRaises(ValueError):
                    self.check()
