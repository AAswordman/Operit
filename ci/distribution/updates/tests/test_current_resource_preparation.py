"""用真实 Git 索引和资源打包器验证共享源码更新后的发行准备链路。"""
from contextlib import ExitStack
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'ci/distribution/resources'))
import builtin_packages
import prepare_current_resources as current
import prepare_hub
import verify_hub_catalog
from repositories.config import RESOURCE_REPOSITORY, SOURCE_REPOSITORY


class PrepareCurrentResourcesTest(unittest.TestCase):
    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.PIPE).strip()

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='operit-current-resources-test-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        stack = ExitStack()
        self.addCleanup(stack.close)
        for module in (current, prepare_hub, builtin_packages, verify_hub_catalog):
            stack.enter_context(patch.object(module, 'ROOT', self.root))
        for directory in ('examples/types', 'tools/example_packages', 'app/config'):
            (self.root / directory).mkdir(parents=True)
        self.source = self.root / 'examples/fixture.js'
        self.source.write_text('/* {"name": "fixture"} */\nexports.value = 1;\n')
        self.types = self.root / 'examples/types/index.d.ts'
        self.types.write_text('export type Result = string;\n')
        (self.root / 'examples/tsconfig.json').write_text('{}\n')
        (self.root / 'tools/example_packages/packages_whitelist.txt').write_text('fixture.js\n')
        (self.root / 'tools/example_packages/sync_example_packages.py').write_text('# 固定资源回归输入\n')
        for name in prepare_hub.TEMPLATES:
            folder = self.root / 'app/src/main/assets/templates' / name
            folder.mkdir(parents=True)
            (folder / 'README.md').write_text('固定模板\n')
        self.git('init', '-q')
        self.git('add', 'examples', 'tools', 'app/src')
        self.git('-c', 'user.name=资源回归', '-c', 'user.email=resource-test@example.invalid', 'commit', '-qm', '记录原资源源码')
        self.packages = self.root / 'built-packages'
        self.packages.mkdir()
        shutil.copyfile(self.source, self.packages / self.source.name)
        seed = self.root / 'seed-assets'
        seed.mkdir()
        shared = self.root / 'shared-model.dat'
        shared.write_bytes(b'fixed-model-content')
        groups = [prepare_hub.package(seed, name, name, [('model.dat', shared)]) for name in sorted(current.SHARED_GROUPS)]
        (seed / 'offline-packs.json').write_text(json.dumps({'releaseTag': 'seed', 'packs': groups}))
        self.pinned = self.root / 'app/config/offline-packs.json'
        self.snapshot = self.root / 'app/config/offline-pack-sources.json'
        initial_assets = self.root / 'initial-assets'
        self.old_catalog = prepare_hub.prepare(initial_assets, self.root / 'cache', self.packages, seed, self.snapshot)
        shutil.copyfile(initial_assets / self.pinned.name, self.pinned)
        self.old_pinned = self.pinned.read_bytes()
        self.old_snapshot = self.snapshot.read_bytes()
        self.blobs = {
            pack['archive']['url']: (initial_assets / pack['archive']['path']).read_bytes()
            for pack in self.old_catalog['packs'] if pack['id'] in current.SHARED_GROUPS
        }
        # 只更新已提交公共类型，复现扩展正文未改、发行指纹仍然变化的路径。
        self.types.write_text('export type Result = string | null;\n')
        self.git('add', 'examples/types')
        self.git('-c', 'user.name=资源回归', '-c', 'user.email=resource-test@example.invalid', 'commit', '-qm', '更新公共类型')
        self.revision = self.git('rev-parse', 'HEAD')
        self.requests = []

        def download(url, timeout):
            self.requests.append(url)
            return io.BytesIO(self.blobs[url])

        stack.enter_context(patch.object(current.urllib.request, 'urlopen', side_effect=download))
        self.publisher = stack.enter_context(patch.object(current, 'publish'))

    def test_changed_shared_types_refresh_the_real_catalog_and_snapshot(self):
        with self.assertRaisesRegex(ValueError, '内置扩展源码变动'):
            verify_hub_catalog.main(current_source=True)
        report = current.prepare_current(self.packages, self.root / 'candidate')
        verify_hub_catalog.main(current_source=True)
        self.assertNotEqual(self.old_catalog['releaseTag'], report['releaseTag'])
        self.assertEqual(self.revision, json.loads(self.snapshot.read_text())['sourceRevision'])
        self.assertFalse(report['published'])
        self.assertEqual(2, len(self.requests))
        self.publisher.assert_not_called()
        prepared = json.loads(self.pinned.read_text())
        old_shared = {pack['id']: pack['archive']['sha256'] for pack in self.old_catalog['packs'] if pack['id'] in current.SHARED_GROUPS}
        new_shared = {pack['id']: pack['archive']['sha256'] for pack in prepared['packs'] if pack['id'] in current.SHARED_GROUPS}
        self.assertEqual(old_shared, new_shared)

    def test_corrupted_download_stops_before_replacing_pinned_inputs(self):
        first = next(iter(self.blobs))
        self.blobs[first] = b'invalid'
        with self.assertRaisesRegex(ValueError, 'SHA-256 不匹配'):
            current.prepare_current(self.packages, self.root / 'candidate')
        self.assertEqual(self.old_pinned, self.pinned.read_bytes())
        self.assertEqual(self.old_snapshot, self.snapshot.read_bytes())
        self.publisher.assert_not_called()

    def test_missing_built_package_does_not_reuse_the_old_extension(self):
        (self.packages / self.source.name).unlink()
        with self.assertRaisesRegex(ValueError, '构建产物与内置白名单不一致'):
            current.prepare_current(self.packages, self.root / 'candidate')
        self.assertEqual(self.old_pinned, self.pinned.read_bytes())
        self.publisher.assert_not_called()

    def prepared_apk(self):
        output = self.root / 'candidate'
        current.prepare_current(self.packages, output)
        apk = self.root / 'app-kong.apk'
        with zipfile.ZipFile(apk, 'w') as package:
            package.writestr('assets/offline-packs.json', self.pinned.read_bytes())
        return output, apk

    def release(self, output, apk, *, commit=None, publish=True):
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': SOURCE_REPOSITORY, 'GITHUB_SHA': self.revision}):
            return current.release_prepared(apk, self.revision if commit is None else commit, output, publish_resources=publish)

    def test_publication_receives_the_verified_assets_and_fixed_commit(self):
        output, apk = self.prepared_apk()

        def publish(assets, repository, revision):
            verify_hub_catalog.main(current_source=True)
            self.assertEqual(self.pinned.read_bytes(), (assets / self.pinned.name).read_bytes())
            self.assertEqual(RESOURCE_REPOSITORY, repository)
            self.assertEqual(self.revision, revision)

        self.publisher.side_effect = publish
        report = self.release(output, apk)
        self.assertTrue(report['published'])
        self.publisher.assert_called_once()

    def test_strict_validation_failure_cannot_reach_publication(self):
        output, apk = self.prepared_apk()
        with patch.object(current, 'verify_catalog', side_effect=ValueError('严格校验失败')):
            with self.assertRaisesRegex(ValueError, '严格校验失败'):
                self.release(output, apk)
        self.publisher.assert_not_called()

    def test_wrong_dispatch_commit_stops_before_publication(self):
        output, apk = self.prepared_apk()
        with self.assertRaisesRegex(ValueError, '固定的源码提交'):
            self.release(output, apk, commit='0' * 40)
        self.publisher.assert_not_called()

    def test_generated_worktree_javascript_does_not_replace_committed_source_inputs(self):
        self.source.write_text('/* {"name": "fixture"} */\nexports.value = 2;\n')
        current.prepare_current(self.packages, self.root / 'candidate')
        verify_hub_catalog.main(current_source=True)
        snapshot = json.loads(self.snapshot.read_text())
        self.assertEqual(self.revision, snapshot['sourceRevision'])
        self.assertEqual(12, len(json.loads(self.pinned.read_text())['packs']))
        self.publisher.assert_not_called()

    def test_apk_with_a_different_catalog_cannot_publish_resources(self):
        output, apk = self.prepared_apk()
        with zipfile.ZipFile(apk, 'w') as package:
            package.writestr('assets/offline-packs.json', self.old_pinned)
        with self.assertRaisesRegex(ValueError, 'APK 资源目录'):
            self.release(output, apk)
        self.publisher.assert_not_called()

    def test_changed_preparation_receipt_cannot_publish_resources(self):
        output, apk = self.prepared_apk()
        receipt = output / 'resource-preparation.json'
        report = json.loads(receipt.read_text())
        report['catalogSha256'] = '0' * 64
        receipt.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError, '准备记录'):
            self.release(output, apk)
        self.publisher.assert_not_called()

    def test_prepare_only_verifies_the_apk_without_remote_writes(self):
        output, apk = self.prepared_apk()
        self.assertFalse(self.release(output, apk, publish=False)['published'])
        self.publisher.assert_not_called()

    def test_android_report_contains_catalog_snapshot_checksums_and_receipt(self):
        output, apk = self.prepared_apk()
        reports = self.root / 'app/build/reports/offline-resources'
        expected = {'offline-packs.json', 'offline-pack-sources.json', 'SHA256SUMS.txt', 'resource-preparation.json'}
        self.assertEqual(expected, {path.name for path in reports.iterdir()})
        self.assertEqual(self.pinned.read_bytes(), (reports / self.pinned.name).read_bytes())
        self.assertEqual(self.snapshot.read_bytes(), (reports / self.snapshot.name).read_bytes())
        self.assertEqual((output / 'assets/SHA256SUMS.txt').read_bytes(), (reports / 'SHA256SUMS.txt').read_bytes())


if __name__ == '__main__':
    unittest.main()
