"""在 +15 及全部迁移附件核对成功后清理主仓库旧 Release，保留源码标签。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'ci/distribution'))
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY
from repositories.migrate_releases import api, asset_specs

PUBLISHED_TAG = 'v1.12.2+15'


def cleanup_plan(source: list[dict], targets: dict[str, list[dict]], receipts: dict, published: dict, release_plan: dict, commit: str) -> list[dict]:
    if (receipts['sourceRepository'], receipts['updateRepository'], receipts['resourceRepository']) != (SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY):
        raise ValueError('迁移收据仓库不符合本次分仓')
    if published['tag_name'] != PUBLISHED_TAG or published['draft'] or published['prerelease']:
        raise ValueError('必须先公开本次 +15 正式签名发行')
    if re.fullmatch(r'[0-9a-f]{40}', commit) is None or release_plan['tag'] != PUBLISHED_TAG or release_plan['sourceCommit'] != commit:
        raise ValueError('本次发布计划与构建提交不符')
    if release_plan['repositories'] != [UPDATE_REPOSITORY]:
        raise ValueError('本次 +15 必须仅发布到统一更新仓库')
    expected_published = {item['name']: {'size': item['bytes'], 'digest': 'sha256:' + item['sha256']} for item in release_plan['assets']}
    if asset_specs(published['assets']) != expected_published:
        raise ValueError('+15 公开附件与本次签名发布计划不一致')
    if len(expected_published) != 4 or 'app-kong.apk' not in expected_published or 'distribution-release.json' not in expected_published:
        raise ValueError('+15 必须包含完整包、增量包、补丁元数据和发行摘要')
    entries = receipts['releases']
    expected_source = {item['tag']: item for item in entries}
    if len(expected_source) != len(entries) or {item['tag_name'] for item in source} != set(expected_source) or len(source) != len(entries):
        raise ValueError('主仓库发行集合发生变化，禁止按过期收据清理')
    result = []
    for release in source:
        receipt = expected_source[release['tag_name']]
        if release['draft'] or release['prerelease'] or release['id'] != receipt['sourceReleaseId'] or asset_specs(release['assets']) != receipt['sourceFiles']:
            raise ValueError('源发行与迁移快照不一致：' + release['tag_name'])
        repository = receipt['repository']
        if repository not in {UPDATE_REPOSITORY, RESOURCE_REPOSITORY}:
            raise ValueError('迁移目标不属于专用发行仓库')
        matches = [item for item in targets[repository] if item['tag_name'] == receipt['tag']]
        if len(matches) != 1 or matches[0]['draft'] or matches[0]['prerelease'] or asset_specs(matches[0]['assets']) != receipt['files']:
            raise ValueError('目标发行不完整或被修改：' + receipt['tag'])
        result.append({'tag': receipt['tag'], 'sourceReleaseId': release['id'], 'sourceCommit': receipt['sourceCommit'], 'repository': repository})
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipts', type=Path, required=True)
    parser.add_argument('--release-plan', type=Path, required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--output', type=Path, default=Path('.ci-release/cleanup-report.json'))
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    receipts = json.loads(args.receipts.read_text())
    plan = json.loads(args.release_plan.read_text())
    source = api(f'repos/{SOURCE_REPOSITORY}/releases?per_page=100', paginate=True)
    targets = {repository: api(f'repos/{repository}/releases?per_page=100', paginate=True) for repository in [UPDATE_REPOSITORY, RESOURCE_REPOSITORY]}
    published = api(f'repos/{UPDATE_REPOSITORY}/releases/tags/{quote(PUBLISHED_TAG, safe="")}')
    cleanup = cleanup_plan(source, targets, receipts, published, plan, args.commit)
    # 删除前核对所有源码标签，不能将不同提交的历史发行误当成迁移完成。
    for entry in cleanup:
        actual = api(f'repos/{SOURCE_REPOSITORY}/commits/{quote(entry["tag"], safe="")}')['sha']
        if actual != entry['sourceCommit']:
            raise ValueError('源标签提交不匹配：' + entry['tag'])
    if api(f'repos/{SOURCE_REPOSITORY}/commits/{quote(PUBLISHED_TAG, safe="")}')['sha'] != args.commit:
        raise ValueError('+15 源码标签与构建提交不符')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {'publishedTag': PUBLISHED_TAG, 'sourceCommit': args.commit, 'verified': len(cleanup), 'deleted': [], 'sourceTagsPreserved': True}
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    if args.apply:
        for entry in cleanup:
            result = subprocess.run(['gh', 'api', f'repos/{SOURCE_REPOSITORY}/releases/{entry["sourceReleaseId"]}', '--method', 'DELETE'], capture_output=True, text=True, timeout=45)
            if result.returncode:
                raise RuntimeError(result.stderr.strip())
            report['deleted'].append(entry['tag'])
            args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
            print('已清理旧 Release，源码标签保留：' + entry['tag'], flush=True)
        if api(f'repos/{SOURCE_REPOSITORY}/releases?per_page=100', paginate=True):
            raise ValueError('主仓库还有发行记录，请核对并发发布')
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
