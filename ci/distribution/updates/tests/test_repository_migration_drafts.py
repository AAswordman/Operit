"""验证无标签草稿的迁移、完整草稿复用和发布前后的附件校验。"""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories import migrate_releases as migration
from repositories.config import SOURCE_REPOSITORY, RESOURCE_REPOSITORY


class MigrationDraftExecutionTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)
        self.tag = "resources-v1"
        self.commit = "a" * 40
        data = b"verified model bytes"
        self.attachment = {"name": "model.bin", "size": len(data), "state": "uploaded",
                           "digest": "sha256:" + hashlib.sha256(data).hexdigest()}
        self.source = {"id": 1, "tag_name": self.tag, "name": "离线资源", "body": "说明",
                       "draft": False, "prerelease": False, "assets": [self.attachment]}
        folder = self.output / "assets" / self.tag / "source"
        folder.mkdir(parents=True)
        (folder / "model.bin").write_bytes(data)
        self.remote = {**copy.deepcopy(self.source), "id": 72, "draft": True}
        self.existing = []
        self.publication_result = "published"
        self.calls = []
        api_patch = patch.object(migration, "api", side_effect=self.api)
        api_patch.start()
        self.addCleanup(api_patch.stop)
        command_patch = patch.object(migration.subprocess, "run", side_effect=self.upload)
        self.commands = command_patch.start()
        self.addCleanup(command_patch.stop)

    def api(self, endpoint, payload=None, *, paginate=False, method=None):
        self.calls.append((endpoint, payload, method))
        collection = f"repos/{RESOURCE_REPOSITORY}/releases"
        if endpoint == f"repos/{SOURCE_REPOSITORY}/commits/{self.tag}":
            return {"sha": self.commit}
        if endpoint == collection + "?per_page=100":
            return copy.deepcopy(self.existing)
        if endpoint == collection and payload is not None:
            self.assertEqual(self.tag, payload["tag_name"])
            self.assertTrue(payload["draft"])
            self.remote["assets"] = []
            return copy.deepcopy(self.remote)
        if endpoint == collection + "/72":
            if method == "PATCH":
                self.assertEqual({"draft": False, "make_latest": "false"}, payload)
                self.remote["draft"] = self.publication_result == "draft"
                if self.publication_result == "changed":
                    self.remote["assets"][0]["digest"] = "sha256:" + "0" * 64
            return copy.deepcopy(self.remote)
        # 草稿没有对应 Git 标签，任何标签查询都应像真实接口一样不可用。
        raise AssertionError("不存在的发行接口：" + endpoint)

    def upload(self, command, **kwargs):
        self.assertEqual(["gh", "release", "upload", self.tag, "--repo", RESOURCE_REPOSITORY], command[:6])
        self.assertEqual(["model.bin"], [Path(value).name for value in command[6:]])
        self.remote["assets"] = [copy.deepcopy(self.attachment)]

    def migrate(self):
        migration.migrate([self.source], self.output)

    def assert_receipt(self):
        receipts = json.loads((self.output / "verified-receipts.json").read_text())
        self.assertEqual(1, len(receipts))
        self.assertEqual(self.tag, receipts[0]["tag"])
        self.assertEqual(self.commit, receipts[0]["sourceCommit"])
        self.assertEqual(self.source["id"], receipts[0]["sourceReleaseId"])
        self.assertEqual(RESOURCE_REPOSITORY, receipts[0]["repository"])
        self.assertEqual(receipts[0]["sourceFiles"], receipts[0]["files"])

    def test_new_draft_without_git_tag_is_verified_published_and_receipted(self):
        self.migrate()
        self.commands.assert_called_once()
        self.assertFalse(self.remote["draft"])
        self.assert_receipt()

    def test_existing_complete_draft_is_published_without_reupload(self):
        self.existing = [copy.deepcopy(self.remote)]
        self.migrate()
        self.commands.assert_not_called()
        self.assertFalse(self.remote["draft"])
        self.assert_receipt()

    def test_existing_public_release_is_verified_without_mutation(self):
        self.remote["draft"] = False
        self.existing = [copy.deepcopy(self.remote)]
        self.migrate()
        self.commands.assert_not_called()
        self.assertFalse(any(payload is not None for _, payload, _ in self.calls))
        self.assert_receipt()

    def test_draft_changed_since_listing_is_not_published(self):
        self.existing = [copy.deepcopy(self.remote)]
        self.remote["assets"] = []
        with self.assertRaisesRegex(ValueError, "没有附件"):
            self.migrate()
        self.commands.assert_not_called()
        self.assertFalse(any(method == "PATCH" for _, _, method in self.calls))
        self.assertFalse((self.output / "verified-receipts.json").exists())

    def test_unsuccessful_publication_or_changed_public_assets_has_no_receipt(self):
        self.existing = [copy.deepcopy(self.remote)]
        for state in ["draft", "changed"]:
            with self.subTest(state=state):
                self.remote = copy.deepcopy(self.existing[0])
                self.publication_result = state
                with self.assertRaises(ValueError):
                    self.migrate()
                self.assertFalse((self.output / "verified-receipts.json").exists())
        self.commands.assert_not_called()


if __name__ == "__main__":
    unittest.main()
