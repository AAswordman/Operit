"""固定资源内容未变时只核对并复用已公开资产，不重复上传或创建版本。"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

from publish_hub import find_resource_release, request


def verify_remote_assets(catalog_bytes: bytes, assets: list[dict], sums: str, builtin_bytes: bytes) -> dict:
    catalog = json.loads(catalog_bytes)
    builtins = json.loads(builtin_bytes)
    if builtins['releaseTag'] != catalog['releaseTag']:
        raise ValueError('内置市场与资源发行标签不一致')
    catalog['packs'] += builtins['packs']
    expected = {
        pack['archive']['path']: {
            'size': pack['archive']['bytes'], 'digest': 'sha256:' + pack['archive']['sha256'],
        }
        for pack in catalog['packs']
    }
    if len(expected) != len(catalog['packs']):
        raise ValueError('固定清单包含重复资源文件')
    expected['offline-packs.json'] = {
        'size': len(catalog_bytes), 'digest': 'sha256:' + hashlib.sha256(catalog_bytes).hexdigest(),
    }
    expected['builtin-extensions.json'] = {'size': len(builtin_bytes), 'digest': 'sha256:' + hashlib.sha256(builtin_bytes).hexdigest()}
    lines = sorted(f'{item["digest"].removeprefix("sha256:")}  {name}' for name, item in expected.items())
    if sorted(sums.splitlines()) != lines:
        raise ValueError('已公开资源的总校验清单与源码固定目录不符')
    sum_bytes = sums.encode('utf-8')
    expected['SHA256SUMS.txt'] = {
        'size': len(sum_bytes), 'digest': 'sha256:' + hashlib.sha256(sum_bytes).hexdigest(),
    }
    remote = {item['name']: item for item in assets}
    if len(remote) != len(assets) or set(remote) != set(expected):
        raise ValueError('已公开资源的附件集合与源码固定目录不符')
    for name, item in remote.items():
        if item['state'] != 'uploaded' or item['size'] != expected[name]['size'] or item.get('digest') != expected[name]['digest']:
            raise ValueError('已公开资源的大小或摘要不符，禁止覆盖：' + name)
    return {'tag': catalog['releaseTag'], 'files_verified': len(remote), 'published': True, 'action': 'reused'}


def existing_publication(pinned: Path, repository: str) -> dict | None:
    catalog_bytes = pinned.read_bytes()
    tag = json.loads(catalog_bytes)['releaseTag']
    release = find_resource_release(repository, tag)
    if release is None or release['draft']:
        return None
    assets = request(f'repos/{repository}/releases/{release["id"]}/assets?per_page=100')
    with tempfile.TemporaryDirectory(prefix='operit-existing-resource-check-') as directory:
        subprocess.run([
            'gh', 'release', 'download', tag, '--repo', repository,
            '--pattern', 'SHA256SUMS.txt', '--dir', directory,
        ], check=True, capture_output=True, text=True, timeout=60)
        sums = (Path(directory) / 'SHA256SUMS.txt').read_bytes().decode('utf-8')
    report = verify_remote_assets(catalog_bytes, assets, sums, (pinned.parent / 'builtin-extensions.json').read_bytes())
    report['url'] = release['html_url']
    return report
