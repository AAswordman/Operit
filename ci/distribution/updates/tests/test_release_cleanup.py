"""验证旧发行清理必须等待全部迁移和 +15 附件核对完成。"""
import copy
import hashlib
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'ci/distribution'))
from repositories.cleanup_main_releases import cleanup_plan, PUBLISHED_TAG
from repositories.migrate_releases import asset_specs
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY


def asset(name, content=b'asset'):
    return {'name': name, 'state': 'uploaded', 'size': len(content), 'digest': 'sha256:' + hashlib.sha256(content).hexdigest()}


class ReleaseCleanupTest(unittest.TestCase):
    def setUp(self):
        self.commit = 'a' * 40
        self.source = [{'id': 10, 'tag_name': 'v1.12.2+14', 'draft': False, 'prerelease': False, 'assets': [asset('app-kong.apk')]}]
        self.target = {UPDATE_REPOSITORY: [copy.deepcopy(self.source[0])], RESOURCE_REPOSITORY: []}
        self.receipts = {'sourceRepository': SOURCE_REPOSITORY, 'updateRepository': UPDATE_REPOSITORY,
                         'resourceRepository': RESOURCE_REPOSITORY, 'releases': [
                             {'tag': 'v1.12.2+14', 'repository': UPDATE_REPOSITORY, 'sourceReleaseId': 10,
                              'sourceCommit': 'b' * 40, 'sourceFiles': asset_specs(self.source[0]['assets']),
                              'files': asset_specs(self.target[UPDATE_REPOSITORY][0]['assets'])}]}
        self.published = {'id': 11, 'tag_name': PUBLISHED_TAG, 'draft': False, 'prerelease': False,
                          'assets': [asset(name) for name in ['app-kong.apk', 'apkrawpatch_14_to_15.zip', 'patch_14_to_15.json', 'distribution-release.json']]}
        self.plan = {'tag': PUBLISHED_TAG, 'sourceCommit': self.commit, 'repositories': [UPDATE_REPOSITORY],
                     'assets': [{'name': item['name'], 'bytes': item['size'], 'sha256': item['digest'].removeprefix('sha256:')} for item in self.published['assets']]}

    def resolve(self):
        return cleanup_plan(self.source, self.target, self.receipts, self.published, self.plan, self.commit)

    def test_verified_releases_produce_exact_source_ids_and_preserve_source_commit(self):
        self.assertEqual([{'tag': 'v1.12.2+14', 'sourceReleaseId': 10, 'sourceCommit': 'b' * 40, 'repository': UPDATE_REPOSITORY}], self.resolve())

    def test_unpublished_or_different_release_cannot_trigger_deletion(self):
        for change in [{'draft': True}, {'prerelease': True}, {'tag_name': 'v1.12.2+16'}]:
            with self.subTest(change=change):
                original = self.published
                self.published = {**original, **change}
                with self.assertRaises(ValueError):
                    self.resolve()
                self.published = original

    def test_missing_or_changed_published_asset_blocks_cleanup(self):
        self.published['assets'][1] = asset('apkrawpatch_14_to_15.zip', b'changed')
        with self.assertRaisesRegex(ValueError, '公开附件'):
            self.resolve()

    def test_wrong_build_commit_or_publish_destination_blocks_cleanup(self):
        self.plan['sourceCommit'] = 'c' * 40
        with self.assertRaises(ValueError):
            self.resolve()
        self.plan['sourceCommit'] = self.commit
        self.plan['repositories'] = [SOURCE_REPOSITORY]
        with self.assertRaises(ValueError):
            self.resolve()

    def test_unexpected_source_release_and_replaced_id_block_cleanup(self):
        self.source.append({**self.source[0], 'id': 12, 'tag_name': 'v1.12.2+16'})
        with self.assertRaisesRegex(ValueError, '发行集合'):
            self.resolve()
        self.source.pop()
        self.source[0]['id'] = 13
        with self.assertRaisesRegex(ValueError, '源发行'):
            self.resolve()

    def test_missing_draft_or_corrupted_migration_target_blocks_cleanup(self):
        for target in [[], [{**self.target[UPDATE_REPOSITORY][0], 'draft': True}],
                       [{**self.target[UPDATE_REPOSITORY][0], 'assets': [asset('app-kong.apk', b'changed')]}]]:
            with self.subTest(target=target):
                self.target[UPDATE_REPOSITORY] = target
                with self.assertRaises(ValueError):
                    self.resolve()


if __name__ == '__main__':
    unittest.main()