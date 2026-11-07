"""验证分仓迁移的文件完整性、清单改址和无副作用预览。"""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "ci/distribution"))
sys.path.insert(0, str(ROOT / "ci/distribution/resources"))
from repositories import migrate_releases as migration
from repositories import check_publish_access
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY
import publish_resources


def asset(name, data=b"data"):
    return {"name": name, "size": len(data), "state": "uploaded", "digest": "sha256:" + hashlib.sha256(data).hexdigest()}


def release(tag, assets):
    return {"id": 1, "tag_name": tag, "draft": False, "prerelease": False,
            "assets": assets, "body": "发行说明", "name": tag}


class RepositoryMigrationTest(unittest.TestCase):
    def test_paginated_api_keeps_every_release(self):
        from types import SimpleNamespace
        response = SimpleNamespace(returncode=0, stdout='[{"id":1}]\n[{"id":2}]\n', stderr="")
        with patch.object(migration.subprocess, "run", return_value=response):
            self.assertEqual([{"id": 1}, {"id": 2}], migration.api("repos/example/releases", paginate=True))

    def test_plan_routes_resources_and_all_app_versions_without_mutation(self):
        releases = [release("resources-v1", [asset("model.bin")]),
                    release("v1.12.2", [asset("app-kong.apk")]),
                    release("v1.12.2+14", [asset("app-kong.apk")])]
        with tempfile.TemporaryDirectory() as folder:
            with patch("sys.argv", ["migrate_releases.py", "--output", folder]), patch.object(migration, "api", return_value=releases):
                with patch.object(migration, "initialize_repositories") as initialize, patch.object(migration, "migrate") as copy:
                    migration.main()
            initialize.assert_not_called()
            copy.assert_not_called()
            plan = json.loads((Path(folder) / "migration-plan.json").read_text())
            self.assertEqual([RESOURCE_REPOSITORY, UPDATE_REPOSITORY, UPDATE_REPOSITORY], [item["repository"] for item in plan])
            self.assertEqual(releases, json.loads((Path(folder) / "source-releases.json").read_text()))

    def test_unfinished_release_unknown_tag_and_missing_digest_are_rejected(self):
        values = [release("unrelated", [asset("app-kong.apk")]),
                  {**release("v1.12.2", [asset("app-kong.apk")]), "draft": True},
                  release("resources-v1", [{**asset("model.bin"), "digest": None}]),
                  release("resources-v1", [asset("../escape")])]
        for value in values:
            with self.subTest(tag=value["tag_name"]), self.assertRaises(ValueError):
                migration.migration_plan([value])

    def test_resource_relocation_preserves_archive_identity_and_rebuilds_checksums(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "source"
            source.mkdir()
            archive = source / "model.zip"
            archive.write_bytes(b"model archive bytes")
            catalog = {"releaseTag": "resources-v2-unchanged", "packs": [{"id": "model", "version": "unchanged",
                       "archive": {"path": archive.name, "sha256": "fixed", "bytes": archive.stat().st_size,
                       "url": f"https://github.com/{SOURCE_REPOSITORY}/releases/download/resources-v2-unchanged/model.zip"}}]}
            (source / "offline-packs.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
            (source / "SHA256SUMS.txt").write_text("old sums")
            output = root / "prepared"
            expected = migration.prepare_assets(source, output, release(catalog["releaseTag"], []), RESOURCE_REPOSITORY, "a" * 40)
            relocated = json.loads((output / "offline-packs.json").read_text())
            self.assertEqual(archive.read_bytes(), (output / archive.name).read_bytes())
            self.assertEqual(catalog["releaseTag"], relocated["releaseTag"])
            self.assertEqual("unchanged", relocated["packs"][0]["version"])
            self.assertIn(RESOURCE_REPOSITORY + "/releases/", relocated["packs"][0]["archive"]["url"])
            sums = (output / "SHA256SUMS.txt").read_text()
            self.assertIn(migration.sha256(output / "offline-packs.json").removeprefix("sha256:") + "  offline-packs.json", sums)
            migration.verify_files(output, expected)
            (output / archive.name).write_bytes(b"corrupted model archive")
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                migration.verify_files(output, expected)

    def test_app_relocation_keeps_apk_and_patch_bytes_and_pins_source_commit(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "source"
            source.mkdir()
            for name in ["app-kong.apk", "apkrawpatch_13_to_14.zip"]:
                (source / name).write_bytes(b"signed bytes")
            (source / "patch_13_to_14.json").write_text(json.dumps({"repo": SOURCE_REPOSITORY, "targetSha256": "signed digest"}))
            (source / "distribution-release.json").write_text(json.dumps({"releaseNotes": "说明", "targetSha256": "signed digest"}))
            output = root / "prepared"
            migration.prepare_assets(source, output, release("v1.12.2+14", []), UPDATE_REPOSITORY, "a" * 40)
            for name in ["app-kong.apk", "apkrawpatch_13_to_14.zip"]:
                self.assertEqual((source / name).read_bytes(), (output / name).read_bytes())
            self.assertEqual(UPDATE_REPOSITORY, json.loads((output / "patch_13_to_14.json").read_text())["repo"])
            manifest = json.loads((output / "distribution-release.json").read_text())
            self.assertEqual("a" * 40, manifest["sourceCommit"])
            self.assertEqual(SOURCE_REPOSITORY, manifest["sourceRepository"])
            self.assertEqual("signed digest", manifest["targetSha256"])

    def test_remote_missing_extra_or_changed_assets_are_rejected(self):
        expected = migration.asset_specs([asset("app-kong.apk")])
        for assets in [[], [asset("app-kong.apk", b"other")], [asset("app-kong.apk"), asset("extra")]]:
            with self.subTest(assets=assets), self.assertRaises(ValueError):
                migration.verify_remote(release("v1.12.2+14", assets), expected)
        migration.verify_remote(release("v1.12.2+14", [asset("app-kong.apk")]), expected)

    def test_json_history_remains_parseable_and_readable_notes_remain_markdown(self):
        old = release("v1.12.2+2", [])
        old["body"] = json.dumps({"releaseNotes": "旧版说明", "repo": SOURCE_REPOSITORY})
        moved = json.loads(migration.release_body(old, UPDATE_REPOSITORY, "a" * 40))
        self.assertEqual("旧版说明", moved["releaseNotes"])
        self.assertEqual(UPDATE_REPOSITORY, moved["repo"])
        self.assertEqual("a" * 40, moved["sourceCommit"])
        self.assertTrue(migration.release_body(release("v1.12.2+14", []), UPDATE_REPOSITORY, "a" * 40).startswith("发行说明"))

    def test_missing_cross_repository_credential_fails_before_network(self):
        with patch.dict("os.environ", {"GH_TOKEN": ""}), patch.object(check_publish_access.subprocess, "run") as execute:
            with self.assertRaisesRegex(ValueError, "OPERIT_DISTRIBUTION_TOKEN"):
                check_publish_access.check("release")
        execute.assert_not_called()

    def test_release_preflight_requires_resource_repository_write_access(self):
        def response(command, **kwargs):
            endpoint = command[2]
            if '/git/ref/' in endpoint:
                return SimpleNamespace(stdout='{}')
            info = {"default_branch": "main", "permissions": {"push": RESOURCE_REPOSITORY not in endpoint}}
            return SimpleNamespace(stdout=json.dumps(info))

        with patch.dict("os.environ", {"GH_TOKEN": "fixture"}), patch.object(check_publish_access.subprocess, "run", side_effect=response):
            with self.assertRaisesRegex(ValueError, RESOURCE_REPOSITORY):
                check_publish_access.check("release")


class ResourceArtifactTest(unittest.TestCase):
    def test_valid_flat_artifact_extracts_and_unexpected_path_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            config = root / "app/config"
            config.mkdir(parents=True)
            catalog = {"schemaVersion": 1, "releaseTag": "resources-v3-test",
                       "packs": [{"id": "template-test", "archive": {"path": "resource.zip", "bytes": 3}}]}
            (config / "offline-packs.json").write_text(json.dumps(catalog))
            (config / "builtin-extensions.json").write_text(json.dumps({**catalog, "packs": []}))
            for invalid in [False, True]:
                archive = root / ("invalid.zip" if invalid else "valid.zip")
                with zipfile.ZipFile(archive, "w") as output:
                    output.writestr("../resource.zip" if invalid else "resource.zip", b"zip")
                    output.writestr("offline-packs.json", (config / "offline-packs.json").read_bytes())
                    output.writestr("builtin-extensions.json", (config / "builtin-extensions.json").read_bytes())
                    output.writestr("SHA256SUMS.txt", b"checksums")
                destination = root / ("invalid" if invalid else "valid")
                destination.mkdir()
                with patch.object(publish_resources, "ROOT", root):
                    if invalid:
                        with self.assertRaises(ValueError):
                            publish_resources.prepare_artifact(archive, destination)
                    else:
                        publish_resources.prepare_artifact(archive, destination)
                        self.assertEqual(b"zip", (destination / "resource.zip").read_bytes())


if __name__ == "__main__":
    unittest.main()
