from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from contextlib import redirect_stdout

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/distribution/resources"))
import prepare_hub
import builtin_packages
import verify_packaged_resources
import verify_hub_catalog
import builtin_source_snapshot


class OfflineHubAssetPreparationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name)
        self.assets = self.repository / "assets"
        (self.assets / "packages").mkdir(parents=True)
        self.jar_paths = ["resources/apktool/" + name for name in prepare_hub.APK_FILES]

    def write_plugin(self, package_id: str, manifest_name: str = "manifest.json") -> Path:
        path = self.assets / "packages/example.toolpkg"
        manifest = {
            "toolpkg_id": package_id, "version": "1.1.0", "main": "dist/main.js",
            "resources": [{"path": name, "key": str(index)} for index, name in enumerate(self.jar_paths)],
        }
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(manifest_name, json.dumps(manifest))
            archive.writestr("dist/main.js", "module.exports = {};")
            archive.writestr("resources/notice.txt", "保留的插件说明")
            for name in self.jar_paths:
                archive.writestr(name, b"jar bytes")
        return path

    def test_slim_assets_removes_scripts_and_complete_plugins(self) -> None:
        javascript = self.assets / "packages/example.js"
        javascript.write_bytes(b"module.exports = {};")
        self.write_plugin("com.operit.apk_reverse_toolkit")
        with redirect_stdout(io.StringIO()):
            prepare_hub.slim_assets(self.assets)
        self.assertEqual([], list((self.assets / "packages").iterdir()))

    def test_jvm_assets_without_packages_do_not_require_generation(self) -> None:
        (self.assets / "packages").rmdir()
        with redirect_stdout(io.StringIO()):
            prepare_hub.slim_assets(self.assets)
        self.assertFalse((self.assets / "packages").exists())

    def test_reverse_distribution_preserves_identity_code_and_other_resources(self) -> None:
        source = self.write_plugin("com.operit.apk_reverse_toolkit")
        before = source.read_bytes()
        target = self.repository / "reverse.toolpkg"
        builtin_packages.normalize_plugin(source, target, True)
        self.assertEqual(before, source.read_bytes())
        with zipfile.ZipFile(target) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("com.operit.apk_reverse_toolkit", manifest["toolpkg_id"])
            self.assertEqual("1.1.0", manifest["version"])
            self.assertEqual(b"module.exports = {};", archive.read("dist/main.js"))
            self.assertEqual("保留的插件说明", archive.read("resources/notice.txt").decode())
            self.assertFalse(set(self.jar_paths).intersection(archive.namelist()))
            self.assertTrue(all(r["external_group"] == "apk-reverse" for r in manifest["resources"]))

    def test_other_plugin_preserves_all_content_and_has_stable_archive(self) -> None:
        source = self.write_plugin("com.operit.other")
        first = self.repository / "first.toolpkg"
        second = self.repository / "second.toolpkg"
        builtin_packages.normalize_plugin(source, first, False)
        builtin_packages.normalize_plugin(source, second, False)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(source) as original, zipfile.ZipFile(first) as result:
            self.assertEqual(set(original.namelist()), set(result.namelist()))
            for name in original.namelist():
                if name == "manifest.json":
                    self.assertEqual(json.loads(original.read(name)), json.loads(result.read(name)))
                else:
                    self.assertEqual(original.read(name), result.read(name))

    def test_reverse_distribution_requires_complete_runtime_jars(self) -> None:
        source = self.write_plugin("com.operit.apk_reverse_toolkit")
        replacement = self.repository / "missing.toolpkg"
        with zipfile.ZipFile(source) as original, zipfile.ZipFile(replacement, "w") as incomplete:
            for name in original.namelist():
                if name != self.jar_paths[0]:
                    incomplete.writestr(name, original.read(name))
        with self.assertRaisesRegex(ValueError, "缺少完整运行资源"):
            builtin_packages.normalize_plugin(replacement, self.repository / "result.toolpkg", True)

    def test_distribution_rejects_unexpected_manifest(self) -> None:
        source = self.write_plugin("com.operit.apk_reverse_toolkit", "manifest.hjson")
        with self.assertRaisesRegex(ValueError, "清单缺失"):
            builtin_packages.normalize_plugin(source, self.repository / "result.toolpkg", True)

    def verify_apk(self, bundled: bool = False, complete: bool = True) -> None:
        config = self.repository / "app/config"
        config.mkdir(parents=True)
        extensions = [{"extension": {"kind": "plugin" if i < 10 else "sandbox"}} for i in range(40)] if complete else []
        catalogs = {
            "offline-resources.properties": b"catalog",
            "local-speech-models.json": b"{}",
            "offline-packs.json": json.dumps({"packs": extensions}).encode(),
        }
        for name, data in catalogs.items():
            (config / name).write_bytes(data)
        vad = b"vad"
        (config / "stt-model-assets.properties").write_text(
            "models/silero_vad.onnx|https://example.invalid/vad|3|" + hashlib.sha256(vad).hexdigest())
        apk = self.repository / "app/build/outputs/apk/debug/example.apk"
        apk.parent.mkdir(parents=True)
        with zipfile.ZipFile(apk, "w") as archive:
            for name, data in catalogs.items():
                archive.writestr("assets/" + name, data)
            archive.writestr("assets/models/silero_vad.onnx", vad)
            if bundled:
                archive.writestr("assets/packages/example.js", b"code")
        with patch.object(verify_packaged_resources, "ROOT", self.repository), redirect_stdout(io.StringIO()):
            verify_packaged_resources.main()

    def test_final_apk_without_bundled_extensions_is_valid(self) -> None:
        self.verify_apk()

    def test_final_apk_rejects_bundled_extensions(self) -> None:
        with self.assertRaisesRegex(ValueError, "Large resources were bundled"):
            self.verify_apk(bundled=True)

    def test_final_apk_rejects_missing_extension_catalog(self) -> None:
        with self.assertRaisesRegex(ValueError, "内置扩展目录不完整"):
            self.verify_apk(complete=False)


class BuiltInSourceFingerprintTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name)
        self.sources = {
            "examples/sample.js": b"old generated script",
            "examples/sample.ts": b"export const value = 1;",
            "examples/plugin/src/main.ts": b"export const plugin = 1;",
            "examples/plugin/dist/main.js": b"old generated plugin",
            "examples/types/global.d.ts": b"declare const version: string;",
            "examples/tsconfig.json": b"{}",
            "tools/example_packages/sync_example_packages.py": b"sync input",
            "tools/example_packages/packages_whitelist.txt": b"sample.js\nplugin\n",
        }
        for name, content in self.sources.items():
            target = self.repository / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        self.git("init", "--quiet")
        self.git("add", "--all")
        root = patch.object(builtin_packages, "ROOT", self.repository)
        root.start()
        self.addCleanup(root.stop)

    def git(self, *arguments: str) -> None:
        subprocess.run(["git", *arguments], cwd=self.repository, check=True, capture_output=True)

    def test_regenerated_script_keeps_published_clean_checkout_fingerprint(self) -> None:
        expected_files = [(name, hashlib.sha256(data).hexdigest())
            for name, data in sorted(self.sources.items()) if not name.startswith("examples/plugin/")]
        expected = hashlib.sha256(json.dumps(expected_files, ensure_ascii=False).encode()).hexdigest()
        self.assertEqual(expected, builtin_packages.source_fingerprint("sample.js"))
        (self.repository / "examples/sample.js").write_bytes(b"new generated script")
        self.assertEqual(expected, builtin_packages.source_fingerprint("sample.js"))

    def test_regenerated_plugin_dist_keeps_indexed_fingerprint(self) -> None:
        expected = builtin_packages.source_fingerprint("plugin")
        (self.repository / "examples/plugin/dist/main.js").write_bytes(b"new generated plugin")
        (self.repository / "examples/plugin/dist/extra.js").write_bytes(b"new untracked output")
        self.assertEqual(expected, builtin_packages.source_fingerprint("plugin"))

    def test_indexed_typescript_change_still_invalidates_fingerprint(self) -> None:
        expected = builtin_packages.source_fingerprint("sample.js")
        (self.repository / "examples/sample.ts").write_bytes(b"export const value = 2;")
        self.git("add", "examples/sample.ts")
        self.assertNotEqual(expected, builtin_packages.source_fingerprint("sample.js"))

    def test_indexed_javascript_change_still_invalidates_fingerprint(self) -> None:
        expected = builtin_packages.source_fingerprint("sample.js")
        (self.repository / "examples/sample.js").write_bytes(b"updated source script")
        self.git("add", "examples/sample.js")
        self.assertNotEqual(expected, builtin_packages.source_fingerprint("sample.js"))


class FixedCatalogSourceSnapshotTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.config = self.root / "app/config"
        self.config.mkdir(parents=True)
        self.files = {"examples/sample.js": hashlib.sha256(b"published script").hexdigest()}
        self.source_fingerprint = builtin_source_snapshot.fingerprint(self.files, "sample.js")
        packs = []
        for group in ("sandbox-sample", "engine-sherpa-onnx", "ocr-google-mlkit"):
            identity = {"id": group, "files": [], "loadOrder": []}
            if group == "sandbox-sample":
                identity["files"] = [{"path": "packages/sample.js"}]
                identity["extension"] = {"kind": "sandbox", "path": "packages/sample.js",
                    "packageNames": ["sample"], "source": "sample.js", "sourceFingerprint": self.source_fingerprint}
            version = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()[:20]
            packs.append({**identity, "version": version, "archive": {"path": group + ".zip", "bytes": 1, "sha256": "0" * 64}})
        tag = "resources-v2-" + hashlib.sha256(json.dumps(packs, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:20]
        for pack in packs:
            pack["archive"]["url"] = f"https://github.com/{verify_hub_catalog.RESOURCE_REPOSITORY}/releases/download/{tag}/{pack['archive']['path']}"
        (self.config / "offline-packs.json").write_text(json.dumps({"schemaVersion": 1, "releaseTag": tag, "packs": packs}))
        self.snapshot = {"schemaVersion": 1, "releaseTag": tag, "sourceRevision": "a" * 40,
            "sources": ["sample.js"], "files": list(self.files.items())}
        self.write_snapshot()
        for target, name, value in ((verify_hub_catalog, "ROOT", self.root),
                (verify_hub_catalog, "TEMPLATES", [])):
            mocked = patch.object(target, name, value)
            mocked.start()
            self.addCleanup(mocked.stop)

    def write_snapshot(self) -> None:
        (self.config / "offline-pack-sources.json").write_text(json.dumps(self.snapshot))

    def test_development_verifies_published_snapshot_without_current_index_or_history(self) -> None:
        with patch.object(verify_hub_catalog, "source_fingerprint", side_effect=AssertionError("开发校验不应读取当前索引")), redirect_stdout(io.StringIO()):
            verify_hub_catalog.main()

    def test_publication_rejects_changed_current_source(self) -> None:
        with patch.object(verify_hub_catalog, "whitelist", return_value=["sample.js"]), \
                patch.object(verify_hub_catalog, "source_fingerprint", return_value="b" * 64):
            with self.assertRaisesRegex(ValueError, "源码变动后必须重新准备并发布"):
                verify_hub_catalog.main(current_source=True)

    def test_wrong_snapshot_tag_is_rejected(self) -> None:
        self.snapshot["releaseTag"] = "resources-v2-wrong"
        self.write_snapshot()
        with self.assertRaisesRegex(ValueError, "快照与资源标签不一致"):
            verify_hub_catalog.main()

    def test_changed_snapshot_file_hash_is_rejected(self) -> None:
        self.snapshot["files"] = [("examples/sample.js", "c" * 64)]
        self.write_snapshot()
        with self.assertRaisesRegex(ValueError, "快照与扩展指纹不一致"):
            verify_hub_catalog.main()


if __name__ == "__main__":
    unittest.main()
