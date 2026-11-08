"""验证未变化资源无写入复用，以及未公开草稿按 Release ID 处理。"""
from pathlib import Path
import copy
import hashlib
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'ci/distribution/resources'))
import publish_hub
import publish_resources
import published_resource_state as state


def fixture():
    catalog = {'releaseTag': 'resources-v3-test', 'packs': [
        {'archive': {'path': 'pack.zip', 'bytes': 7, 'sha256': 'a' * 64}}
    ]}
    builtins = {'releaseTag': 'resources-v3-test', 'packs': [
        {'archive': {'path': 'script.zip', 'bytes': 9, 'sha256': 'b' * 64}}
    ]}
    data = (json.dumps(catalog) + '\n').encode()
    builtin_bytes = (json.dumps(builtins) + '\n').encode()
    expected = {'pack.zip': (7, 'a' * 64), 'script.zip': (9, 'b' * 64),
        'offline-packs.json': (len(data), hashlib.sha256(data).hexdigest()),
        'builtin-extensions.json': (len(builtin_bytes), hashlib.sha256(builtin_bytes).hexdigest())}
    sums = '\n'.join(sorted(f'{digest}  {name}' for name, (_, digest) in expected.items())) + '\n'
    expected['SHA256SUMS.txt'] = (len(sums.encode()), hashlib.sha256(sums.encode()).hexdigest())
    assets = [{'name': name, 'size': size, 'digest': 'sha256:' + digest, 'state': 'uploaded'}
              for name, (size, digest) in expected.items()]
    return data, assets, sums, builtin_bytes


class PublishedResourceStateTest(unittest.TestCase):
    def test_complete_publication_is_reused(self):
        data, assets, sums, builtin = fixture()
        report = state.verify_remote_assets(data, assets, sums, builtin)
        self.assertEqual('reused', report['action'])
        self.assertEqual(5, report['files_verified'])

    def test_reordered_checksum_lines_are_valid_when_asset_digest_matches(self):
        data, assets, sums, builtin = fixture()
        reordered = '\n'.join(reversed(sums.splitlines())) + '\n'
        assets[-1]['digest'] = 'sha256:' + hashlib.sha256(reordered.encode()).hexdigest()
        self.assertEqual('reused', state.verify_remote_assets(data, assets, reordered, builtin)['action'])

    def test_changed_remote_bytes_cannot_be_overwritten(self):
        data, assets, sums, builtin = fixture()
        assets[0]['digest'] = 'sha256:' + 'b' * 64
        with self.assertRaisesRegex(ValueError, '禁止覆盖'):
            state.verify_remote_assets(data, assets, sums, builtin)

    def test_missing_and_extra_assets_are_not_complete(self):
        data, assets, sums, builtin = fixture()
        for invalid in [assets[:-1], assets + [{'name': 'extra'}]]:
            with self.assertRaisesRegex(ValueError, '附件集合'):
                state.verify_remote_assets(data, invalid, sums, builtin)

    def test_duplicate_asset_names_are_rejected(self):
        data, assets, sums, builtin = fixture()
        with self.assertRaisesRegex(ValueError, '附件集合'):
            state.verify_remote_assets(data, assets + [copy.deepcopy(assets[0])], sums, builtin)

    def test_checksum_manifest_must_describe_current_catalog(self):
        data, assets, sums, builtin = fixture()
        with self.assertRaisesRegex(ValueError, '总校验清单'):
            state.verify_remote_assets(data, assets, sums.replace('a' * 64, 'b' * 64), builtin)

    def test_unfinished_upload_is_not_reused(self):
        data, assets, sums, builtin = fixture()
        assets[0]['state'] = 'new'
        with self.assertRaises(ValueError):
            state.verify_remote_assets(data, assets, sums, builtin)

    def test_catalogs_from_different_releases_cannot_be_reused(self):
        data, assets, sums, builtin = fixture()
        wrong = json.loads(builtin)
        wrong['releaseTag'] = 'another-release'
        with self.assertRaisesRegex(ValueError, '发行标签不一致'):
            state.verify_remote_assets(data, assets, sums, json.dumps(wrong).encode())

    def test_missing_builtin_catalog_asset_is_not_a_complete_publication(self):
        data, assets, sums, builtin = fixture()
        without_catalog = [asset for asset in assets if asset['name'] != 'builtin-extensions.json']
        with self.assertRaisesRegex(ValueError, '附件集合'):
            state.verify_remote_assets(data, without_catalog, sums, builtin)

    def test_draft_without_a_tag_is_found_by_release_id(self):
        draft = {'id': 91, 'tag_name': 'resources-v2-test', 'draft': True}
        with patch.object(publish_hub, 'request', side_effect=[None, [draft]]) as request:
            self.assertEqual(91, publish_hub.find_resource_release('owner/resources', 'resources-v2-test')['id'])
            self.assertEqual(2, request.call_count)
            self.assertTrue(all('payload' not in call.kwargs for call in request.call_args_list))

    def test_multiple_matching_drafts_stop_instead_of_creating_another(self):
        drafts = [{'id': 1, 'tag_name': 'tag'}, {'id': 2, 'tag_name': 'tag'}]
        with patch.object(publish_hub, 'request', side_effect=[None, drafts]):
            with self.assertRaisesRegex(ValueError, '多个草稿'):
                publish_hub.find_resource_release('owner/resources', 'tag')

    def test_existing_publication_skips_artifact_download_and_publish(self):
        environment = {'GITHUB_REPOSITORY': publish_resources.SOURCE_REPOSITORY, 'GITHUB_SHA': 'a' * 40}
        with patch.dict(os.environ, environment), patch.object(publish_resources, 'verify_catalog'), \
                patch.object(publish_resources, 'existing_publication', return_value={'action': 'reused', 'url': 'url'}), \
                patch.object(publish_resources.subprocess, 'run') as execute, patch.object(publish_resources, 'publish') as publish:
            publish_resources.main(0)
            execute.assert_not_called()
            publish.assert_not_called()

    def test_new_resource_version_requires_an_artifact(self):
        environment = {'GITHUB_REPOSITORY': publish_resources.SOURCE_REPOSITORY, 'GITHUB_SHA': 'a' * 40}
        with patch.dict(os.environ, environment), patch.object(publish_resources, 'verify_catalog'), \
                patch.object(publish_resources, 'existing_publication', return_value=None), \
                patch.object(publish_resources.subprocess, 'run') as execute:
            with self.assertRaisesRegex(ValueError, '必须选择'):
                publish_resources.main(0)
            execute.assert_not_called()

    def test_asset_upload_uses_stable_release_id(self):
        with patch.object(publish_hub.subprocess, 'run') as execute:
            publish_hub.upload_resource_asset('owner/resources', 91, Path('/tmp/a b.zip'))
            command = execute.call_args.args[0]
            self.assertIn('/releases/91/assets?name=a%20b.zip', command[2])
            self.assertIn('--input', command)
            self.assertNotIn('release', command)


if __name__ == '__main__':
    unittest.main()
